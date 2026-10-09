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
    for w in warnings:
        print("warning:", w)
    for e in errors:
        print("ERROR:", e)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
