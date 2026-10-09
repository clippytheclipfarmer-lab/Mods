#!/usr/bin/env python3
"""
Writes every JSON file of the addon: armor materials, suit sets, gadget items, creative tab, recipes, powers (one per hero),
suit-set power links, energy beams, trails, item models and the language file.

    python3 tools/generate_data.py

The JSON is committed, so building the mod does not need Python. Run generate_models.py and generate_icons.py for the art.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "fiskheroes"
ADDON = os.path.join(ROOT, "addon", NS)
DATA = os.path.join(ROOT, "data", NS)
ASSETS = os.path.join(ROOT, "assets", NS)

lang = {}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ---------------------------------------------------------------------------------------------------- conditions
def action(cooldown=0, key=None):
    c = {"type": "palladium:action", "cooldown": cooldown}
    if key:
        c["key_type"] = key
    return c


def toggle(key=None):
    c = {"type": "palladium:toggle", "cooldown": 0}
    if key:
        c["key_type"] = key
    return c


def held(key=None):
    c = {"type": "palladium:held", "cooldown": 0}
    if key:
        c["key_type"] = key
    return c


def enabled(ability):
    return {"type": "palladium:ability_enabled", "ability": ability}


def not_(*conds):
    return {"type": "palladium:not", "conditions": list(conds)}


def item_in(slot, item):
    return {"type": "palladium:item_in_slot", "slot": slot, "item": {"item": item}}


def has_effect(effect):
    return {"type": "palladium:has_effect", "effect": effect}


def energy(bar, minimum):
    return {"type": "palladium:energy_bar", "energy_bar": bar, "min": minimum}


SPRINTING = {"type": "palladium:sprinting"}
WELL = not_(has_effect(f"{NS}:kryptonite_poisoning"))     # Superman is not under kryptonite


class Power:
    def __init__(self, hero, name, icon, primary, secondary, bars=None):
        self.hero = hero
        self.data = {"name": {"translate": f"power.{NS}.{hero}"}, "icon": icon, "primary_color": primary, "secondary_color": secondary}
        if bars:
            self.data["energy_bars"] = bars
        self.data["abilities"] = {}
        lang[f"power.{NS}.{hero}"] = name
        self.n = 0
        self.code = sorted(HEROES).index(hero) + 1

    def uuid(self):
        self.n += 1
        return f"f15c{self.code:04x}-0000-4000-8000-{self.n:012d}"

    def add(self, key, type_, title=None, icon=None, index=None, hidden=False, cond=None, usage=None, **props):
        ab = {"type": type_}
        if title:
            ab["title"] = {"translate": f"ability.{NS}.{self.hero}.{key}"}
            lang[f"ability.{NS}.{self.hero}.{key}"] = title
        if icon:
            ab["icon"] = icon
        if index is not None:
            ab["list_index"] = index
        if hidden:
            ab["hidden"] = True
            ab["hidden_in_bar"] = True
        ab.update(props)
        if cond is not None:
            ab["conditions"] = {"enabling": cond}
        if usage:
            ab["energy_bar_usage"] = {"energy_bar": usage[0], "amount": usage[1]}
        self.data["abilities"][key] = ab
        return ab

    def attr(self, key, attribute, amount, operation=0, cond=None):
        return self.add(key, "palladium:attribute_modifier", hidden=True, cond=cond, attribute=attribute, amount=amount,
                        operation=operation, uuid=self.uuid())

    def flight(self, speed, sprint, flex, heroic, cond=None):
        self.attr("flight_speed", "palladium:flight_speed", speed, cond=cond)
        self.attr("flight_sprint", "palladium:flight_sprint_speed", sprint, cond=cond)
        self.attr("flight_flexibility", "palladium:flight_flexibility", flex, cond=cond)
        self.attr("flight_style", "palladium:heroic_flight_type", 1 if heroic else 0, cond=cond)

    def effect(self, key, effect, amplifier=0, cond=None, title=None, icon="minecraft:potion", index=None):
        return self.add(key, f"{NS}:effect", title=title, icon=icon, index=index, hidden=title is None, cond=cond, effect=effect, amplifier=amplifier)

    def write(self):
        write(os.path.join(DATA, "palladium", "powers", f"{self.hero}.json"), self.data)
        write(os.path.join(DATA, "palladium", "suit_set_powers", f"{self.hero}.json"), {"suit_set": f"{NS}:{self.hero}", "power": f"{NS}:{self.hero}"})


# ---------------------------------------------------------------------------------------------------- the heroes
HEROES = {
    "iron_man": dict(
        name="Iron Man", suit="Mark VII", icon="minecraft:iron_chestplate", primary=[168, 31, 31], secondary=[230, 181, 60],
        stats=dict(protection=[3, 6, 8, 3], toughness=3.0, knockback=0.1, durability=42, sound="minecraft:item.armor.equip_netherite", repair="minecraft:iron_ingot"),
        main="minecraft:iron_ingot", accent="minecraft:gold_ingot", core="minecraft:diamond",
        pieces=dict(helmet="Iron Man Helmet", chestplate="Iron Man Chestplate", leggings="Iron Man Leggings", boots="Iron Man Boots"),
        blurb="Powered flight, repulsors, a unibeam and missiles. Runs on the arc reactor."),
    "captain_america": dict(
        name="Captain America", suit="Stealth Suit", icon="minecraft:shield", primary=[42, 74, 140], secondary=[179, 32, 43],
        stats=dict(protection=[2, 5, 6, 2], toughness=1.0, knockback=0.0, durability=30, sound="minecraft:item.armor.equip_leather", repair="minecraft:leather"),
        main="minecraft:blue_wool", accent="minecraft:red_wool", core="minecraft:white_wool",
        pieces=dict(helmet="Captain America Cowl", chestplate="Captain America Tunic", leggings="Captain America Trousers", boots="Captain America Boots"),
        blurb="Super soldier strength and speed, and the vibranium shield."),
    "thor": dict(
        name="Thor", suit="Asgardian Armor", icon="fiskheroes:mjolnir", primary=[138, 144, 156], secondary=[142, 27, 28],
        stats=dict(protection=[3, 6, 8, 3], toughness=2.0, knockback=0.1, durability=40, sound="minecraft:item.armor.equip_iron", repair="minecraft:iron_ingot"),
        main="minecraft:iron_ingot", accent="minecraft:red_wool", core="minecraft:lightning_rod",
        pieces=dict(helmet="Thor Helmet", chestplate="Thor Armor", leggings="Thor Leggings", boots="Thor Boots"),
        blurb="Mjolnir, lightning, storms and flight with the hammer in hand."),
    "spider_man": dict(
        name="Spider-Man", suit="Classic Suit", icon="minecraft:cobweb", primary=[184, 28, 36], secondary=[32, 63, 148],
        stats=dict(protection=[1, 3, 4, 1], toughness=0.0, knockback=0.0, durability=22, sound="minecraft:item.armor.equip_leather", repair="minecraft:string"),
        main="minecraft:red_wool", accent="minecraft:blue_wool", core="minecraft:string",
        pieces=dict(helmet="Spider-Man Mask", chestplate="Spider-Man Suit Top", leggings="Spider-Man Suit Legs", boots="Spider-Man Boots"),
        blurb="Wall crawling, web zipping, web shots and spider sense."),
    "black_panther": dict(
        name="Black Panther", suit="Vibranium Suit", icon="minecraft:amethyst_shard", primary=[27, 27, 35], secondary=[181, 123, 255],
        stats=dict(protection=[2, 5, 7, 3], toughness=3.0, knockback=0.1, durability=38, sound="minecraft:item.armor.equip_netherite", repair="minecraft:netherite_scrap"),
        main="minecraft:black_wool", accent="minecraft:amethyst_shard", core="minecraft:netherite_scrap",
        pieces=dict(helmet="Black Panther Mask", chestplate="Black Panther Suit Top", leggings="Black Panther Suit Legs", boots="Black Panther Boots"),
        blurb="Kinetic energy absorption, vibranium claws, heightened senses."),
    "superman": dict(
        name="Superman", suit="Kryptonian Suit", icon="minecraft:blue_wool", primary=[31, 78, 176], secondary=[200, 22, 30],
        stats=dict(protection=[2, 5, 6, 2], toughness=2.0, knockback=0.2, durability=45, sound="minecraft:item.armor.equip_leather", repair="minecraft:leather"),
        main="minecraft:blue_wool", accent="minecraft:red_wool", core="minecraft:gold_ingot",
        pieces=dict(helmet="Superman Hair", chestplate="Superman Suit Top", leggings="Superman Suit Legs", boots="Superman Boots"),
        blurb="Flight, heat vision, freeze breath, near invulnerability. Kryptonite makes him sick."),
    "batman": dict(
        name="Batman", suit="Batsuit", icon="minecraft:black_wool", primary=[76, 81, 94], secondary=[242, 197, 0],
        stats=dict(protection=[2, 6, 7, 3], toughness=2.0, knockback=0.2, durability=36, sound="minecraft:item.armor.equip_netherite", repair="minecraft:leather"),
        main="minecraft:gray_wool", accent="minecraft:black_wool", core="minecraft:yellow_dye",
        pieces=dict(helmet="Batman Cowl", chestplate="Batsuit Top", leggings="Batsuit Legs", boots="Batsuit Boots"),
        blurb="Grapple, batarangs, smoke bombs, detective vision and a gliding cape."),
    "flash": dict(
        name="The Flash", suit="Speedster Suit", icon="minecraft:sugar", primary=[193, 18, 31], secondary=[255, 210, 63],
        stats=dict(protection=[1, 4, 5, 2], toughness=0.0, knockback=0.0, durability=24, sound="minecraft:item.armor.equip_leather", repair="minecraft:leather"),
        main="minecraft:red_wool", accent="minecraft:gold_ingot", core="minecraft:glowstone_dust",
        pieces=dict(helmet="Flash Cowl", chestplate="Flash Suit Top", leggings="Flash Suit Legs", boots="Flash Boots"),
        blurb="Five levels of super speed, wall and water running, phasing and a lightning trail."),
}
PIECES = ("helmet", "chestplate", "leggings", "boots")
SLOT_OF = {"helmet": "head", "chestplate": "chest", "leggings": "legs", "boots": "feet"}
TAB = f"{NS}:heroes"


# ---------------------------------------------------------------------------------------------------- armor, items, recipes
def armor():
    for hero, h in HEROES.items():
        s = h["stats"]
        write(os.path.join(ADDON, "armor_materials", f"{hero}.json"), {
            "durability_multiplier": s["durability"],
            "slot_protections": [s["protection"][0], s["protection"][1], s["protection"][2], s["protection"][3]],  # feet, legs, chest, head
            "enchantment_value": 15,
            "equip_sound": s["sound"],
            "toughness": s["toughness"],
            "knockback_resistance": s["knockback"],
            "repair_ingredient": {"item": s["repair"]},
        })
        suit = {
            "armor_material": f"{NS}:{hero}",
            "should_render_model": False,
            "rarity": "epic",
            "creative_mode_tab": TAB,
            "tooltip": [f"{h['name']} - {h['suit']}", h["blurb"]],
        }
        for piece in PIECES:
            slot = SLOT_OF[piece]
            suit[slot] = {
                "item_name": f"{hero}_{piece}",
                "render_layers": {slot: [f"{NS}:{hero}_{piece_slot(piece)}", f"{NS}:{hero}_{piece_slot(piece)}_glow"]},
            }
            lang[f"item.{NS}.{hero}_{piece}"] = h["pieces"][piece]
            write(os.path.join(ASSETS, "models", "item", f"{hero}_{piece}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{hero}_{piece}"}})
        write(os.path.join(ADDON, "suit_sets", f"{hero}.json"), suit)
        lang[f"suitset.{NS}.{hero}"] = f"{h['name']} ({h['suit']})"

        # recipes: M main, A accent, C core
        key = {"M": {"item": h["main"]}, "A": {"item": h["accent"]}, "C": {"item": h["core"]}}
        patterns = {"helmet": ["MMM", "MAM"], "chestplate": ["M M", "MCM", "MAM"], "leggings": ["MAM", "M M", "M M"], "boots": ["M M", "A A"]}
        for piece in PIECES:
            pat = patterns[piece]
            used = {c: key[c] for c in "".join(pat) if c in key}
            write(os.path.join(DATA, "recipes", f"{hero}_{piece}.json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pat, "key": used,
                "result": {"item": f"{NS}:{hero}_{piece}"},
            })


def piece_slot(piece):
    return {"helmet": "head", "chestplate": "chest", "leggings": "legs", "boots": "feet"}[piece]


def gadgets():
    items = {
        "vibranium_shield": ("Vibranium Shield", {"type": "palladium:shield", "max_damage": 2400, "use_duration": 72000,
                                                  "repair_ingredient": {"tag": "c:vibranium_ingots"}, "rarity": "epic",
                                                  "tooltip": ["Captain America's shield. Block with it, or throw it: it ricochets between enemies and comes back."]}),
        "mjolnir": ("Mjolnir", {"type": "palladium:sword", "tier": "minecraft:netherite", "base_damage": 9, "attack_speed": -2.9, "rarity": "epic", "is_fire_resistant": True,
                                "tooltip": ["Whosoever holds this hammer, if they be worthy, shall possess the power of Thor.", "Throw it, call lightning, fly."]}),
        "batarang": ("Batarang", {"max_stack_size": 16, "rarity": "uncommon", "tooltip": ["Batman's throwing weapon. The suit makes its own."]}),
        "kryptonite": ("Kryptonite", {"max_stack_size": 16, "rarity": "rare", "tooltip": ["Deadly to Kryptonians."]}),
    }
    for item, (name, data) in items.items():
        data = dict(data)
        data["creative_mode_tab"] = TAB
        write(os.path.join(ADDON, "items", f"{item}.json"), data)
        lang[f"item.{NS}.{item}"] = name
        parent = "minecraft:item/handheld" if item in ("mjolnir",) else "minecraft:item/generated"
        write(os.path.join(ASSETS, "models", "item", f"{item}.json"), {"parent": parent, "textures": {"layer0": f"{NS}:item/{item}"}})

    write(os.path.join(DATA, "recipes", "vibranium_shield.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["IVI", "VSV", "IVI"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "V": {"tag": "c:vibranium_ingots"}, "S": {"item": "minecraft:shield"}},
        "result": {"item": f"{NS}:vibranium_shield"}})
    write(os.path.join(DATA, "recipes", "mjolnir.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["TTT", "TLT", " B "],
        "key": {"T": {"tag": "c:titanium_ingots"}, "L": {"item": "minecraft:lightning_rod"}, "B": {"item": "minecraft:blaze_rod"}},
        "result": {"item": f"{NS}:mjolnir"}})
    write(os.path.join(DATA, "recipes", "batarang.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["IKI", " I "],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "K": {"item": "minecraft:black_dye"}},
        "result": {"item": f"{NS}:batarang", "count": 4}})
    write(os.path.join(DATA, "recipes", "kryptonite.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:emerald"}, {"item": "minecraft:glowstone_dust"}, {"item": "minecraft:slime_ball"}],
        "result": {"item": f"{NS}:kryptonite"}})

    write(os.path.join(ADDON, "creative_mode_tabs", "heroes.json"), {
        "icon": f"{NS}:iron_man_helmet", "title": {"translate": f"itemGroup.{NS}.heroes"}})
    lang[f"itemGroup.{NS}.heroes"] = "Fisk's Superheroes"


# ---------------------------------------------------------------------------------------------------- powers
def iron_man():
    p = Power("iron_man", "Iron Man", "minecraft:iron_chestplate", [168, 31, 31], [230, 181, 60],
              bars={"arc_reactor": {"max": 200, "color": [127, 233, 255], "auto_increase_per_tick": 1, "auto_increase_interval": 2}})
    bar = f"{NS}:iron_man#arc_reactor"
    p.flight(1.2, 3.0, 4, heroic=False)
    p.attr("armor", "generic.armor", 6)
    p.attr("toughness", "generic.armor_toughness", 4)
    p.attr("knockback_resistance", "generic.knockback_resistance", 0.4)
    p.attr("health", "generic.max_health", 10)
    p.attr("strength", "generic.attack_damage", 4)
    p.add("immunities", "palladium:damage_immunity", hidden=True, damage_sources=["minecraft:is_fall", "minecraft:is_fire"])
    p.effect("life_support", "minecraft:water_breathing")
    p.add("repulsor_aim", "palladium:aim", "Repulsor Blasts", "minecraft:end_rod", 0, cond=held(), time=2, arm="both")
    p.add("repulsor", "palladium:energy_beam", hidden=True, cond=[enabled("repulsor_aim"), energy(bar, 10)], usage=(bar, 2),
          energy_beam=f"{NS}:repulsor", damage=4.0, max_distance=40.0, speed=1.0, set_on_fire_seconds=0)
    p.add("unibeam", "palladium:dummy", "Unibeam", "minecraft:beacon", 1, cond=held(key="left_click"))
    p.add("unibeam_beam", "palladium:energy_beam", hidden=True, cond=[enabled("unibeam"), energy(bar, 40)], usage=(bar, 5),
          energy_beam=f"{NS}:unibeam", damage=12.0, max_distance=70.0, speed=0.5, set_on_fire_seconds=3, cause_fire=True)
    p.add("missile", "palladium:projectile", "Missile", "minecraft:firework_rocket", 2, cond=[energy(bar, 30), action(60)], usage=(bar, 30),
          entity_type="minecraft:fireball", entity_data={"ExplosionPower": 2}, velocity=2.6, inaccuracy=0.0)
    p.add("night_vision_toggle", "palladium:dummy", "HUD Night Vision", "minecraft:ender_eye", 3, cond=toggle())
    p.effect("night_vision", "minecraft:night_vision", cond=enabled("night_vision_toggle"))
    p.write()


def captain_america():
    p = Power("captain_america", "Captain America", "minecraft:shield", [42, 74, 140], [179, 32, 43])
    p.attr("strength", "generic.attack_damage", 4)
    p.attr("speed", "generic.movement_speed", 0.025)
    p.attr("jump", "palladium:jump_power", 0.3)
    p.attr("health", "generic.max_health", 10)
    p.attr("armor", "generic.armor", 4)
    p.attr("knockback_resistance", "generic.knockback_resistance", 0.3)
    p.add("regeneration", "palladium:healing", hidden=True, frequency=90, amount=1.0)
    p.add("shield_throw", f"{NS}:throw_gadget", "Shield Throw", f"{NS}:vibranium_shield", 0, cond=action(30),
          gadget="shield", item=f"{NS}:vibranium_shield", consume=True, damage=14.0, speed=1.8)
    p.write()


def thor():
    p = Power("thor", "Thor", f"{NS}:mjolnir", [138, 144, 156], [142, 27, 28])
    hammer = item_in("mainhand", f"{NS}:mjolnir")
    p.flight(1.5, 3.5, 4, heroic=True, cond=hammer)
    p.attr("strength", "generic.attack_damage", 5)
    p.attr("health", "generic.max_health", 20)
    p.attr("armor", "generic.armor", 4)
    p.attr("knockback_resistance", "generic.knockback_resistance", 0.4)
    p.add("regeneration", "palladium:healing", hidden=True, frequency=80, amount=1.0)
    p.add("thunder_god", "palladium:damage_immunity", hidden=True, damage_sources=["minecraft:is_lightning"])
    p.add("hammer_throw", f"{NS}:throw_gadget", "Throw Mjolnir", f"{NS}:mjolnir", 0, cond=action(40),
          gadget="hammer", item=f"{NS}:mjolnir", consume=True, damage=16.0, speed=1.9)
    p.add("lightning_strike", f"{NS}:lightning_strike", "Call Lightning", "minecraft:lightning_rod", 1, cond=[hammer, action(60)],
          range=90.0, damage=12.0, requires_item=f"{NS}:mjolnir")
    p.add("storm", f"{NS}:storm", "Summon Storm", "minecraft:trident", 2, cond=[hammer, action(600)], bolts=10, radius=22.0, damage=8.0)
    p.add("lightning_aim", "palladium:aim", "Lightning Bolt", "minecraft:lightning_rod", 3, cond=held(), time=2, arm="main_arm")
    p.add("lightning_beam", "palladium:energy_beam", hidden=True, cond=enabled("lightning_aim"),
          energy_beam=f"{NS}:thor_lightning", damage=4.0, max_distance=30.0, speed=1.0, set_on_fire_seconds=3, cause_fire=True)
    p.write()


def spider_man():
    p = Power("spider_man", "Spider-Man", "minecraft:cobweb", [184, 28, 36], [32, 63, 148])
    p.attr("strength", "generic.attack_damage", 3)
    p.attr("speed", "generic.movement_speed", 0.02)
    p.attr("jump", "palladium:jump_power", 0.5)
    p.attr("health", "generic.max_health", 6)
    p.attr("armor", "generic.armor", 2)
    p.add("agility", "palladium:damage_immunity", hidden=True, damage_sources=["minecraft:is_fall"])
    p.add("wall_climb", f"{NS}:wall_climb", "Wall Crawl", "minecraft:ladder", 0, cond=held(key="space_bar"), speed=0.28, needs_sprint=False)
    p.add("web_zip", f"{NS}:zip_line", "Web Zip", "minecraft:string", 1, cond=held(), range=42.0, speed=1.1, color="#f2f2f2")
    p.add("web_shot", f"{NS}:web_shot", "Web Shot", "minecraft:cobweb", 2, cond=action(20), range=28.0, web_ticks=160)
    p.add("spider_sense", f"{NS}:spider_sense", "Spider Sense", "minecraft:spider_eye", 3, radius=16.0)
    p.write()


def black_panther():
    p = Power("black_panther", "Black Panther", "minecraft:amethyst_shard", [27, 27, 35], [181, 123, 255],
              bars={"kinetic": {"max": 100, "color": [181, 123, 255], "auto_increase_per_tick": 0, "auto_increase_interval": 1}})
    p.attr("speed", "generic.movement_speed", 0.03)
    p.attr("jump", "palladium:jump_power", 0.35)
    p.attr("health", "generic.max_health", 10)
    p.attr("armor", "generic.armor", 6)
    p.attr("toughness", "generic.armor_toughness", 3)
    p.attr("knockback_resistance", "generic.knockback_resistance", 0.3)
    p.add("agility", "palladium:damage_immunity", hidden=True, damage_sources=["minecraft:is_fall"])
    p.add("silent_steps", "palladium:sculk_immunity", hidden=True)
    p.add("kinetic_absorb", f"{NS}:kinetic_absorb", "Kinetic Absorption", "minecraft:amethyst_shard", 2, energy_bar="kinetic", efficiency=2.5)
    p.add("kinetic_release", f"{NS}:kinetic_release", "Kinetic Release", "minecraft:firework_star", 0, cond=action(40), energy_bar="kinetic", damage_per_energy=0.35)
    p.add("claws", "palladium:dummy", "Vibranium Claws", "minecraft:iron_sword", 1, cond=toggle())
    p.add("claws_render", "palladium:render_layer", hidden=True, cond=enabled("claws"), render_layer=f"{NS}:panther_claws")
    p.attr("claws_damage", "generic.attack_damage", 6, cond=enabled("claws"))
    p.attr("claws_speed", "generic.attack_speed", 0.8, cond=enabled("claws"))
    p.add("senses_toggle", "palladium:dummy", "Enhanced Senses", "minecraft:ender_eye", 3, cond=toggle())
    p.effect("senses", "minecraft:night_vision", cond=enabled("senses_toggle"))
    p.write()


def superman():
    p = Power("superman", "Superman", "minecraft:blue_wool", [31, 78, 176], [200, 22, 30])
    p.add("kryptonite_weakness", f"{NS}:kryptonite_weakness", hidden=True)
    p.flight(2.0, 5.0, 5, heroic=True, cond=WELL)
    p.attr("strength", "generic.attack_damage", 9, cond=WELL)
    p.attr("punch", "palladium:punch_damage", 6, cond=WELL)
    p.attr("mining", "palladium:destroy_speed", 6, cond=WELL)
    p.attr("health", "generic.max_health", 20, cond=WELL)
    p.attr("armor", "generic.armor", 6, cond=WELL)
    p.attr("knockback_resistance", "generic.knockback_resistance", 0.6, cond=WELL)
    p.add("regeneration", "palladium:healing", hidden=True, cond=WELL, frequency=40, amount=1.0)
    p.add("invulnerability", "palladium:damage_immunity", "Invulnerability", "minecraft:netherite_chestplate", 3, cond=WELL,
          damage_sources=["minecraft:is_fire", "minecraft:is_projectile", "minecraft:is_explosion", "minecraft:is_fall",
                          "minecraft:is_drowning", "minecraft:is_freezing", "minecraft:is_lightning"])
    p.add("heat_vision", "palladium:energy_beam", "Heat Vision", "minecraft:blaze_powder", 0, cond=[held(), WELL],
          energy_beam=f"{NS}:heat_vision", damage=5.0, max_distance=80.0, speed=1.0, set_on_fire_seconds=4, cause_fire=True, smelt_blocks=True)
    p.add("freeze_breath", f"{NS}:freeze_breath", "Freeze Breath", "minecraft:snowball", 1, cond=[held(key="right_click"), WELL], range=13.0)
    p.write()


def batman():
    p = Power("batman", "Batman", "minecraft:black_wool", [76, 81, 94], [242, 197, 0])
    p.attr("strength", "generic.attack_damage", 5)
    p.attr("speed", "generic.movement_speed", 0.01)
    p.attr("armor", "generic.armor", 6)
    p.attr("toughness", "generic.armor_toughness", 2)
    p.attr("knockback_resistance", "generic.knockback_resistance", 0.2)
    p.attr("health", "generic.max_health", 6)
    p.add("grapple", f"{NS}:zip_line", "Grapple Hook", "minecraft:fishing_rod", 0, cond=held(), range=55.0, speed=1.5, color="#c9ccd4")
    p.add("batarang", f"{NS}:throw_gadget", "Batarang", f"{NS}:batarang", 1, cond=action(12),
          gadget="batarang", item=f"{NS}:batarang", consume=False, damage=7.0, speed=2.0)
    p.add("smoke_bomb", f"{NS}:smoke_bomb", "Smoke Bomb", "minecraft:gunpowder", 2, cond=action(400), radius=6.0)
    p.add("vision", "palladium:dummy", "Detective Vision", "minecraft:spyglass", 3, cond=toggle())
    p.effect("vision_night", "minecraft:night_vision", cond=enabled("vision"))
    p.add("vision_glow", "palladium:entity_glow", hidden=True, cond=enabled("vision"), mode="others", distance=40.0, color="#ffffff")
    p.add("glide", f"{NS}:glide", "Cape Glide", "minecraft:phantom_membrane", 4, cond=held(key="space_bar"))
    p.write()


def flash():
    p = Power("flash", "The Flash", "minecraft:sugar", [193, 18, 31], [255, 210, 63])
    p.add("speed_force", f"{NS}:speed_force", "Speed Force", "minecraft:sugar", 0, cond=toggle())
    p.add("speed_up", f"{NS}:speed_step", hidden=True, delta=1, cond=[enabled("speed_force"), action(0, "scroll_up")])
    p.add("speed_down", f"{NS}:speed_step", hidden=True, delta=-1, cond=[enabled("speed_force"), action(0, "scroll_down")])
    p.add("phase", "palladium:intangibility", "Vibrate Through Walls", "minecraft:glass", 1, cond=toggle(), vertical=False)
    p.add("phase_vibrate", "palladium:vibrate", hidden=True, cond=enabled("phase"), intensity=14)
    p.add("water_run", "palladium:fluid_walking", hidden=True, cond=[enabled("speed_force"), SPRINTING])
    p.add("wall_run", f"{NS}:wall_climb", hidden=True, cond=enabled("speed_force"), speed=0.34, needs_sprint=True)
    p.add("trail", "palladium:trail", hidden=True, cond=enabled("speed_force"), trail=f"{NS}:flash")
    p.attr("speed_punch", "generic.attack_damage", 5, cond=enabled("speed_force"))
    p.add("regeneration", "palladium:healing", hidden=True, frequency=30, amount=1.0)
    p.add("agility", "palladium:damage_immunity", hidden=True, damage_sources=["minecraft:is_fall"])
    p.write()


# ---------------------------------------------------------------------------------------------------- beams, trails, claws
def beam(name, parts):
    write(os.path.join(ASSETS, "palladium", "energy_beams", f"{name}.json"), parts)


def effects():
    beam("repulsor", [
        {"type": "palladium:laser", "body_part": "right_arm", "offset": [0, -11, 0], "glow_color": "#7fe9ff", "core_color": "#ffffff", "size": 1.3, "bloom": 3},
        {"type": "palladium:laser", "body_part": "left_arm", "offset": [0, -11, 0], "glow_color": "#7fe9ff", "core_color": "#ffffff", "size": 1.3, "bloom": 3}])
    beam("unibeam", [{"type": "palladium:laser", "body_part": "chest", "offset": [0, -3, -3], "glow_color": "#7fe9ff", "core_color": "#ffffff", "size": 4.0, "bloom": 5}])
    beam("heat_vision", [
        {"type": "palladium:laser", "body_part": "head", "offset": [-2, 4, -4], "glow_color": "#ff2a1a", "core_color": "#ffd8c8", "size": 0.7, "bloom": 3},
        {"type": "palladium:laser", "body_part": "head", "offset": [2, 4, -4], "glow_color": "#ff2a1a", "core_color": "#ffd8c8", "size": 0.7, "bloom": 3}])
    beam("thor_lightning", [{"type": "palladium:lightning", "body_part": "right_arm", "offset": [0, -11, 0], "segments": 9, "frequency": 1, "spread": 2,
                             "glow_color": "#7fd0ff", "core_color": "#ffffff", "bloom": 4, "size": [1.6, 1.6]}])
    write(os.path.join(ASSETS, "palladium", "trails", "flash.json"), {
        "type": "palladium:compound",
        "trails": [
            {"type": "palladium:lightning", "color": "#ffe14d", "spacing": 0.35, "lifetime": 8, "requires_movement": True, "amount": 3, "spread_x": 0.5, "spread_y": 0.9},
            {"type": "palladium:gradient", "color": "#ff3b30", "spacing": 0.3, "lifetime": 12, "requires_movement": True, "opacity": 0.45}]})
    write(os.path.join(ASSETS, "palladium", "render_layers", "panther_claws.json"), {
        "type": "geckolib:default",
        "model": f"{NS}:geo/claws.geo.json",
        "texture": f"{NS}:textures/models/claws.png",
        "bones": {"head": "armorHead", "body": "armorBody", "right_arm": "armorRightArm", "left_arm": "armorLeftArm", "right_leg": "armorRightLeg", "left_leg": "armorLeftLeg"},
        "render_full_model_in_first_person": False})


def messages():
    lang[f"effect.{NS}.kryptonite_poisoning"] = "Kryptonite Poisoning"
    lang[f"entity.{NS}.gadget"] = "Thrown Gadget"
    lang[f"message.{NS}.need_item"] = "You need %s in your hand."
    lang[f"message.{NS}.nothing_to_grab"] = "Nothing in reach."
    lang[f"message.{NS}.not_enough_energy"] = "Not enough kinetic energy stored."
    lang[f"message.{NS}.speed_level"] = "Speed level %s / %s"
    lang["energy_bar.fiskheroes.arc_reactor"] = "Arc Reactor"


if __name__ == "__main__":
    armor()
    gadgets()
    for build in (iron_man, captain_america, thor, spider_man, black_panther, superman, batman, flash):
        build()
    effects()
    messages()
    write(os.path.join(ASSETS, "lang", "en_us.json"), dict(sorted(lang.items())))
    print("wrote", len(lang), "lang entries")
