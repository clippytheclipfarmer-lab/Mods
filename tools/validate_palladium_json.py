#!/usr/bin/env python3
"""
Static check of Palladium JSON against Palladium's own source, so typos are caught without starting Minecraft.

    python3 tools/validate_palladium_json.py <project-dir> [--mod-java <java-root> ...]

Checks every power under data/*/palladium/powers: ability types exist (Palladium's or the project's own), condition types
exist, every property key is one the ability/condition actually reads, attribute ids are known, referenced icons/items
exist, scroll/key types are valid. Also checks suit sets, items, armor materials and creative tabs under addon/*.
It reads Palladium's sources jar (downloaded once into a cache directory) and is deliberately strict about unknown keys.
"""
import io
import json
import math
import os
import re
import sys
import urllib.request
import zipfile

SOURCES_URL = "https://maven.threetag.net/net/threetag/palladium-fabric/4.5.9+1.20.1/palladium-fabric-4.5.9+1.20.1-sources.jar"
CACHE = os.path.join(os.environ.get("TMPDIR", "/tmp"), "palladium-sources-4.5.9")

BASE_ABILITY_KEYS = {"type", "conditions", "energy_bar_usage", "title", "icon", "description", "bar_color", "hidden",
                     "hidden_in_bar", "list_index", "gui_position", "gui_frame_type", "persistent_data", "dependencies",
                     "ability", "unlocking", "enabling"}
KEY_TYPES = {"key_bind", "left_click", "right_click", "space_bar", "scroll_up", "scroll_down", "scroll_either"}
VANILLA_ATTRIBUTES = {"generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
                      "generic.attack_damage", "generic.armor", "generic.armor_toughness", "generic.attack_knockback",
                      "generic.attack_speed", "generic.luck", "generic.flying_speed", "forge:step_height_addition",
                      "forge:swim_speed", "forge:nametag_distance", "forge:entity_gravity", "forge:reach_distance"}

errors, warnings = [], []


def fetch_sources():
    if not os.path.isdir(CACHE):
        os.makedirs(CACHE)
        print("downloading Palladium sources ...", file=sys.stderr)
        request = urllib.request.Request(SOURCES_URL, headers={"User-Agent": "palladium-json-validator"})
        with urllib.request.urlopen(request) as r:
            zipfile.ZipFile(io.BytesIO(r.read())).extractall(CACHE)
    return CACHE


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def props_in(source):
    """Property keys a class declares: new XProperty("name") ..."""
    return set(re.findall(r'new\s+\w*Property(?:<[^>]*>)?\(\s*"([a-z_0-9]+)"', source))


def collect_registered(source, regex):
    return dict(re.findall(regex, source))


class Index:
    def __init__(self, src, mod_java_roots):
        base = os.path.join(src, "net", "threetag", "palladium")
        self.base = base
        abilities = read(os.path.join(base, "power", "ability", "Abilities.java"))
        self.abilities = {"palladium:" + k: ("palladium", v) for k, v in
                          re.findall(r'register\("([a-z_0-9]+)",\s*(?:\(\)\s*->\s*new\s+)?(\w+)(?:::new|\(\))', abilities)}
        # Anonymous/dummy abilities registered inline have no class: allow any base keys.
        for name in re.findall(r'register\("([a-z_0-9]+)"', abilities):
            self.abilities.setdefault("palladium:" + name, ("palladium", None))
        # GeckoLib compat abilities are registered in a platform class.
        for name, cls in (("render_layer_animation", "RenderLayerAnimationAbility"), ("armor_animation", "ArmorAnimationAbility")):
            self.abilities["geckolib:" + name] = ("geckolib", cls)
        conditions = read(os.path.join(base, "condition", "ConditionSerializers.java"))
        self.conditions = {"palladium:" + k: v for k, v in
                           re.findall(r'register\("([a-z_0-9]+)",\s*(\w+)\.Serializer::new\)', conditions)}
        self.mod_abilities = {}
        for root in mod_java_roots:
            for dirpath, _, files in os.walk(root):
                for f in files:
                    if f.endswith(".java"):
                        text = read(os.path.join(dirpath, f))
                        for name, cls in re.findall(r'ABILITIES\.register\("([a-z_0-9]+)",\s*([\w.]+)::new\)', text):
                            self.mod_abilities[name] = (cls, dirpath)
        self.mod_sources = ""
        for root in mod_java_roots:
            for dirpath, _, files in os.walk(root):
                for f in files:
                    if f.endswith(".java"):
                        self.mod_sources += read(os.path.join(dirpath, f)) + "\n"

    def ability_keys(self, ability_id):
        """Allowed keys for an ability type, or None if unknown."""
        if ability_id in self.abilities:
            cls = self.abilities[ability_id][1]
            if cls is None:
                return set(BASE_ABILITY_KEYS)
            path = os.path.join(self.base, "power", "ability", cls + ".java")
            if not os.path.exists(path):
                path = os.path.join(self.base, "compat", "geckolib", "ability", cls + ".java")
            if not os.path.exists(path):
                return set(BASE_ABILITY_KEYS)
            text = read(path)
            keys = props_in(text)
            parent = re.search(r"class\s+\w+\s+extends\s+(\w+)", text)
            if parent and parent.group(1) not in ("Ability",):
                ppath = os.path.join(self.base, "power", "ability", parent.group(1) + ".java")
                if os.path.exists(ppath):
                    keys |= props_in(read(ppath))
            return keys | BASE_ABILITY_KEYS
        ns, _, name = ability_id.partition(":")
        if name in self.mod_abilities:
            cls = self.mod_abilities[name][0].split(".")[0]
            # find the class body in the mod sources and collect its properties (plus the Action base if used)
            m = re.search(r"class\s+" + re.escape(self.mod_abilities[name][0].split(".")[-1]) + r"\b[^{]*\{(.*?)\n    \}\n", self.mod_sources, re.S)
            body = m.group(1) if m else ""
            return props_in(body) | BASE_ABILITY_KEYS
        return None

    def condition_keys(self, cond_id):
        if cond_id not in self.conditions:
            return None
        cls = self.conditions[cond_id]
        path = os.path.join(self.base, "condition", cls + ".java")
        text = read(path)
        keys = props_in(text) | {"type"}
        if "KeyCondition" in text or "HeldCondition" in text or "ToggleCondition" in text or "ActionCondition" in text:
            keys |= {"key_type", "needs_empty_hand", "allow_scrolling_when_crouching", "cooldown"}
        # Properties referenced from other classes (e.g. HeldCondition.Serializer.COOLDOWN)
        for ref in re.findall(r"(\w+)\.Serializer\.(\w+)", text) + re.findall(r"(KeyCondition)\.(\w+)", text):
            other = os.path.join(self.base, "condition", ref[0] + ".java")
            if os.path.exists(other):
                keys |= props_in(read(other))
        return keys


def check_condition(idx, cond, where):
    if isinstance(cond, list):
        for c in cond:
            check_condition(idx, c, where)
        return
    if not isinstance(cond, dict):
        errors.append(f"{where}: condition must be an object or list, got {cond!r}")
        return
    ctype = cond.get("type")
    keys = idx.condition_keys(ctype)
    if keys is None:
        errors.append(f"{where}: unknown condition type {ctype}")
        return
    for k in cond:
        if k not in keys:
            errors.append(f"{where}: condition {ctype} has unknown key '{k}' (known: {sorted(keys)})")
    if "key_type" in cond and cond["key_type"] not in KEY_TYPES:
        errors.append(f"{where}: bad key_type {cond['key_type']}")
    for sub in ("conditions", "condition"):
        if sub in cond and ctype in ("palladium:not", "palladium:and", "palladium:or", "palladium:xor"):
            check_condition(idx, cond[sub], where)


def check_power(idx, path, project, ability_names_by_power):
    data = json.load(open(path))
    power_id = os.path.relpath(path, project)
    abilities = data.get("abilities", {})
    for key, ab in abilities.items():
        where = f"{power_id}:{key}"
        atype = ab.get("type")
        keys = idx.ability_keys(atype)
        if keys is None:
            errors.append(f"{where}: unknown ability type {atype}")
            continue
        for k in ab:
            if k not in keys:
                errors.append(f"{where}: ability {atype} has unknown key '{k}'")
        if atype == "palladium:attribute_modifier":
            attr = ab.get("attribute", "").replace("minecraft:", "")
            if not (attr in VANILLA_ATTRIBUTES or attr.startswith("palladium:")):
                warnings.append(f"{where}: attribute {attr} is not a known vanilla/palladium attribute")
            if "uuid" not in ab:
                errors.append(f"{where}: attribute_modifier needs a uuid")
        for cname, cond in (ab.get("conditions") or {}).items():
            if cname not in ("enabling", "unlocking"):
                errors.append(f"{where}: unknown condition slot {cname}")
            check_condition(idx, cond, f"{where}.{cname}")
            # ability_enabled references must exist in this power (when no power given)
            for c in (cond if isinstance(cond, list) else [cond]):
                if isinstance(c, dict) and c.get("type") in ("palladium:ability_enabled", "palladium:ability_unlocked") and not c.get("power"):
                    if c.get("ability") not in abilities:
                        errors.append(f"{where}: references ability '{c.get('ability')}' which is not in this power")
    ability_names_by_power[power_id] = set(abilities)




# ---------------------------------------------------------------------------------------------- addon content
ITEM_BASE_KEYS = {"type", "max_stack_size", "max_damage", "rarity", "is_fire_resistant", "tooltip", "should_render_model",
                  "creative_mode_tab", "attribute_modifiers", "render_layers", "food", "item_name"}
ITEM_TYPE_KEYS = {
    None: set(), "palladium:default": set(), "palladium:armor": {"slot", "armor_material", "armor_renderer", "openable", "opening_time", "opened_sound", "closed_sound", "opening_toggle_sound"},
    "palladium:sword": {"tier", "base_damage", "attack_speed"}, "palladium:shield": {"use_duration", "repair_ingredient"},
    "palladium:pickaxe": {"tier", "base_damage", "attack_speed"}, "palladium:axe": {"tier", "base_damage", "attack_speed"},
    "palladium:shovel": {"tier", "base_damage", "attack_speed"}, "palladium:hoe": {"tier", "base_damage", "attack_speed"},
}
ARMOR_MATERIAL_KEYS = {"durability_multiplier", "slot_protections", "enchantment_value", "equip_sound", "toughness", "knockback_resistance", "repair_ingredient"}
SLOTS = {"head", "chest", "legs", "feet", "mainhand", "offhand"}


def load(path):
    with open(path) as f:
        return json.load(f)


def files(directory, suffix=".json"):
    if not os.path.isdir(directory):
        return []
    return sorted(f for f in os.listdir(directory) if f.endswith(suffix))


def check_geo(path, texture_path, where):
    geo = load(path)["minecraft:geometry"][0]
    tw, th = geo["description"]["texture_width"], geo["description"]["texture_height"]
    try:
        from PIL import Image
        with Image.open(texture_path) as im:
            if im.size != (tw, th):
                errors.append(f"{where}: texture is {im.size} but the geo says {tw}x{th}")
    except ImportError:
        pass
    names = {b["name"] for b in geo["bones"]}
    for bone in geo["bones"]:
        if bone.get("parent") and bone["parent"] not in names:
            errors.append(f"{where}: bone {bone['name']} has unknown parent {bone['parent']}")
        for cube in bone.get("cubes", []):
            u, v = cube["uv"]
            w, h, d = (math.ceil(x) for x in cube["size"])
            if u + 2 * (d + w) > tw or v + d + h > th:
                errors.append(f"{where}: cube in {bone['name']} at uv {cube['uv']} size {cube['size']} leaves the {tw}x{th} texture")


def check_addon(project, ns):
    res = os.path.join(project, "src", "main", "resources")
    addon, data, assets = (os.path.join(res, k, ns) for k in ("addon", "data", "assets"))
    lang = load(os.path.join(assets, "lang", "en_us.json")) if os.path.exists(os.path.join(assets, "lang", "en_us.json")) else {}
    materials = {f[:-5] for f in files(os.path.join(addon, "armor_materials"))}
    item_names = set()

    for f in files(os.path.join(addon, "armor_materials")):
        m = load(os.path.join(addon, "armor_materials", f))
        for k in m:
            if k not in ARMOR_MATERIAL_KEYS:
                errors.append(f"armor_materials/{f}: unknown key {k}")
        for need in ("durability_multiplier", "slot_protections", "enchantment_value", "equip_sound"):
            if need not in m:
                errors.append(f"armor_materials/{f}: missing {need}")

    def check_item(name, item, where):
        if name in item_names:
            errors.append(f"{where}: duplicate item {name}")
        item_names.add(name)
        itype = item.get("type")
        allowed = ITEM_BASE_KEYS | ITEM_TYPE_KEYS.get(itype, set())
        if itype not in ITEM_TYPE_KEYS:
            errors.append(f"{where}: unknown item type {itype}")
        for k in item:
            if k not in allowed and k not in ("head", "chest", "legs", "feet", "armor_material", "slot"):
                errors.append(f"{where}: unknown item key '{k}' for type {itype}")
        if not os.path.exists(os.path.join(assets, "models", "item", name + ".json")):
            errors.append(f"{where}: missing models/item/{name}.json")
        if not os.path.exists(os.path.join(assets, "textures", "item", name + ".png")):
            errors.append(f"{where}: missing textures/item/{name}.png")
        if f"item.{ns}.{name}" not in lang:
            errors.append(f"{where}: no lang entry item.{ns}.{name}")
        for slot, layers in (item.get("render_layers") or {}).items():
            for lid in layers:
                check_layer(lid, f"{where} layer")

    layer_cache = set()

    def check_layer(lid, where):
        if lid in layer_cache:
            return
        layer_cache.add(lid)
        lns, _, lname = lid.partition(":")
        path = os.path.join(res, "assets", lns, "palladium", "render_layers", lname + ".json")
        if not os.path.exists(path):
            errors.append(f"{where}: render layer {lid} does not exist")
            return
        layer = load(path)
        models = layer.get("model")
        for model in (models.values() if isinstance(models, dict) else [models]):
            mns, _, mpath = model.partition(":")
            geo_path = os.path.join(res, "assets", mns, mpath)
            tex = layer.get("texture")
            tex = tex if isinstance(tex, str) else tex.get("normal")
            tns, _, tpath = tex.partition(":")
            tex_path = os.path.join(res, "assets", tns, tpath)
            if not os.path.exists(geo_path):
                errors.append(f"{where}: layer {lid} model {model} is missing")
            elif not os.path.exists(tex_path):
                errors.append(f"{where}: layer {lid} texture {tex} is missing")
            else:
                check_geo(geo_path, tex_path, f"{where}: {lid} ({mpath})")

    for f in files(os.path.join(addon, "items")):
        check_item(f[:-5], load(os.path.join(addon, "items", f)), f"items/{f}")

    for f in files(os.path.join(addon, "suit_sets")):
        suit = load(os.path.join(addon, "suit_sets", f))
        where = f"suit_sets/{f}"
        mat = suit.get("armor_material", "")
        if mat.partition(":")[2] not in materials and mat.partition(":")[0] == ns:
            errors.append(f"{where}: armor material {mat} not found")
        slots = [k for k in suit if k in SLOTS]
        if not slots:
            errors.append(f"{where}: no slots")
        for slot in slots:
            merged = {**{k: v for k, v in suit.items() if k not in SLOTS}, **suit[slot]}
            name = merged.get("item_name", f[:-5] + "_" + slot)
            check_item(name, merged, f"{where}:{slot}")
        if not os.path.exists(os.path.join(data, "palladium", "suit_set_powers", f)):
            warnings.append(f"{where}: no suit_set_powers link, the suit gives no power")

    for f in files(os.path.join(data, "palladium", "suit_set_powers")):
        link = load(os.path.join(data, "palladium", "suit_set_powers", f))
        for sid in ([link["suit_set"]] if isinstance(link["suit_set"], str) else link["suit_set"]):
            if sid.partition(":")[0] == ns and not os.path.exists(os.path.join(addon, "suit_sets", sid.partition(":")[2] + ".json")):
                errors.append(f"suit_set_powers/{f}: suit set {sid} does not exist")
        for pid in ([link["power"]] if isinstance(link["power"], str) else link["power"]):
            if pid.partition(":")[0] == ns and not os.path.exists(os.path.join(data, "palladium", "powers", pid.partition(":")[2] + ".json")):
                errors.append(f"suit_set_powers/{f}: power {pid} does not exist")

    # references from powers to beams, trails and render layers
    for f in files(os.path.join(data, "palladium", "powers")):
        power = load(os.path.join(data, "palladium", "powers", f))
        for key, ab in power["abilities"].items():
            for prop, folder in (("energy_beam", "energy_beams"), ("trail", "trails")):
                ref = ab.get(prop)
                if ref and ref.partition(":")[0] == ns and not os.path.exists(os.path.join(assets, "palladium", folder, ref.partition(":")[2] + ".json")):
                    errors.append(f"powers/{f}:{key}: {prop} {ref} does not exist")
            if ab.get("render_layer"):
                check_layer(ab["render_layer"], f"powers/{f}:{key}")
            if ab.get("icon", "").startswith(ns + ":") and ab["icon"].partition(":")[2] not in item_names:
                errors.append(f"powers/{f}:{key}: icon {ab['icon']} is not one of this addon's items")
            for cond_slot in (ab.get("conditions") or {}).values():
                for c in (cond_slot if isinstance(cond_slot, list) else [cond_slot]):
                    if isinstance(c, dict) and c.get("type") == "palladium:item_in_slot":
                        ing = c.get("item", {}).get("item", "")
                        if ing.startswith(ns + ":") and ing.partition(":")[2] not in item_names:
                            errors.append(f"powers/{f}:{key}: item_in_slot refers to unknown item {ing}")

    for f in files(os.path.join(data, "recipes")):
        recipe = load(os.path.join(data, "recipes", f))
        result = recipe.get("result", {}).get("item", "")
        if result.startswith(ns + ":") and result.partition(":")[2] not in item_names:
            errors.append(f"recipes/{f}: result {result} is not one of this addon's items")
        for ing in list((recipe.get("key") or {}).values()) + list(recipe.get("ingredients") or []):
            ref = ing.get("item", "") if isinstance(ing, dict) else ""
            if ref.startswith(ns + ":") and ref.partition(":")[2] not in item_names:
                errors.append(f"recipes/{f}: ingredient {ref} is not one of this addon's items")
        if recipe.get("type") == "minecraft:crafting_shaped":
            pattern = "".join(recipe["pattern"])
            for ch in set(pattern) - {" "}:
                if ch not in recipe["key"]:
                    errors.append(f"recipes/{f}: pattern uses {ch} with no key")

    for f in files(os.path.join(addon, "creative_mode_tabs")):
        tab = load(os.path.join(addon, "creative_mode_tabs", f))
        icon = tab.get("icon", "")
        if icon.startswith(ns + ":") and icon.partition(":")[2] not in item_names:
            errors.append(f"creative_mode_tabs/{f}: icon {icon} is not one of this addon's items")
    print(f"checked addon {ns}: {len(item_names)} items, {len(layer_cache)} render layers")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    project = os.path.abspath(sys.argv[1])
    java_roots = []
    args = sys.argv[2:]
    while args:
        if args[0] == "--mod-java":
            java_roots.append(os.path.abspath(args[1]))
            args = args[2:]
        else:
            args = args[1:]
    if not java_roots:
        java_roots = [os.path.join(project, "src", "main", "java")]
    idx = Index(fetch_sources(), java_roots)
    res = os.path.join(project, "src", "main", "resources")
    count = 0
    names = {}
    for dirpath, _, files in os.walk(os.path.join(res, "data")):
        if dirpath.replace("\\", "/").endswith("/palladium/powers"):
            for f in sorted(files):
                if f.endswith(".json"):
                    check_power(idx, os.path.join(dirpath, f), project, names)
                    count += 1
    print(f"checked {count} powers, {len(idx.mod_abilities)} mod ability types registered")
    for ns in sorted(os.listdir(os.path.join(res, "addon"))) if os.path.isdir(os.path.join(res, "addon")) else []:
        check_addon(project, ns)
    for w in warnings:
        print("warning:", w)
    for e in errors:
        print("ERROR:", e)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
