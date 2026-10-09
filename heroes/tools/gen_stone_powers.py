#!/usr/bin/env python3
"""
Writes the six Infinity Stone powers (data/stones/palladium/powers/*.json) and the stones lang file.

    python3 tools/gen_stone_powers.py

The kits follow Pugmeowla's Infinity Stone Core (see README). Run it after editing the tables below; the JSON is
committed, so building the mod does not need Python.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
POWERS = os.path.join(ROOT, "data", "stones", "palladium", "powers")
LANG = os.path.join(ROOT, "assets", "stones", "lang", "en_us.json")
BEAMS = os.path.join(ROOT, "assets", "stones", "palladium", "energy_beams")

lang = {}


# ---------------------------------------------------------------- conditions
def action(cooldown=0, key=None):
    c = {"type": "palladium:action", "cooldown": cooldown}
    if key:
        c["key_type"] = key
    return c


def toggle():
    return {"type": "palladium:toggle", "cooldown": 0}


def held(cooldown=0):
    return {"type": "palladium:held", "cooldown": cooldown}


def enabled(ability):
    return {"type": "palladium:ability_enabled", "ability": ability}


def not_(*conds):
    return {"type": "palladium:not", "conditions": list(conds)}


def has_power(power):
    return {"type": "palladium:has_power", "power": power}


# ---------------------------------------------------------------- builders
class Power:
    def __init__(self, key, name, icon, primary, secondary):
        self.key = key
        self.data = {
            "name": {"translate": f"power.stones.{key}"},
            "icon": icon,
            "primary_color": primary,
            "secondary_color": secondary,
            "abilities": {},
        }
        lang[f"power.stones.{key}"] = name
        self.uuid_counter = 0

    def uuid(self):
        self.uuid_counter += 1
        return f"1f57{self.key_code():04x}-0000-4000-8000-{self.uuid_counter:012d}"

    def key_code(self):
        return ["space", "mind", "reality", "power", "time", "soul"].index(self.key) + 0x10

    def add(self, key, type_, title=None, icon=None, index=None, hidden=False, cond=None, **props):
        ab = {"type": type_}
        if title:
            ab["title"] = {"translate": f"ability.stones.{key}"}
            lang[f"ability.stones.{key}"] = title
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
        self.data["abilities"][key] = ab
        return ab

    def attribute(self, key, attribute, amount, operation=0, cond=None, title=None, icon=None, index=None):
        return self.add(key, "palladium:attribute_modifier", title=title, icon=icon, index=index,
                        hidden=title is None, cond=cond, attribute=attribute, amount=amount, operation=operation,
                        uuid=self.uuid())

    def effect(self, key, effect, amplifier=0, title=None, icon="minecraft:potion"):
        return self.add(key, "stones:effect", title=title, icon=icon, hidden=title is None, effect=effect, amplifier=amplifier)

    def write(self):
        os.makedirs(POWERS, exist_ok=True)
        with open(os.path.join(POWERS, f"{self.key}.json"), "w") as f:
            json.dump(self.data, f, indent=2)
            f.write("\n")


# ---------------------------------------------------------------- Space
def space():
    p = Power("space", "Space Stone", "minecraft:ender_pearl", [40, 110, 255], [255, 255, 255])
    p.add("blink", "stones:blink", "Teleport", "minecraft:ender_pearl", 0, cond=action(60))
    p.add("telekinesis", "stones:telekinesis", "Telekinesis", "minecraft:ender_eye", 1, cond=action(0))
    p.add("force_field", "stones:force_field", "Force Field", "minecraft:heart_of_the_sea", 2, cond=toggle())
    p.add("black_hole", "stones:black_hole", "Black Hole", "minecraft:obsidian", 3, cond=toggle())
    p.attribute("speed", "minecraft:generic.movement_speed", 0.05)
    p.add("step_assist", "stones:step_assist", hidden=True)
    p.add("fall_immunity", "palladium:damage_immunity", hidden=True, damage_sources=["minecraft:is_fall"])
    p.effect("water_breathing", "minecraft:water_breathing")
    p.write()


# ---------------------------------------------------------------- Mind
def mind():
    p = Power("mind", "Mind Stone", "minecraft:glowstone_dust", [245, 212, 66], [255, 255, 255])
    p.add("flight_toggle", "palladium:dummy", "Flight Toggle", "minecraft:elytra", 0, cond=toggle())
    awake = not_(enabled("flight_toggle"))
    p.attribute("flight", "palladium:flight_speed", 1.5, cond=awake)
    p.attribute("flight_sprint", "palladium:flight_sprint_speed", 3.0, cond=awake)
    p.attribute("flight_flexibility", "palladium:flight_flexibility", 5, cond=awake)
    p.attribute("heroic_flight", "palladium:heroic_flight_type", 1, cond=awake)
    p.add("intangibility", "palladium:intangibility", "Intangibility", "minecraft:glass", 1, cond=toggle(), vertical=False)
    p.add("intangibility_flight", "palladium:intangibility", hidden=True, vertical=True,
          cond=[enabled("intangibility"), {"type": "palladium:is_hovering_or_flying"}])
    p.add("vibrate", "palladium:vibrate", hidden=True, cond=enabled("intangibility"))
    p.add("aim", "palladium:aim", "Mind Beam", "minecraft:blaze_rod", 2, cond=held(), time=1, arm="main_arm")
    p.add("mind_beam", "palladium:energy_beam", hidden=True, cond=enabled("aim"), energy_beam="stones:mind_beam",
          damage=4.0, max_distance=50.0, speed=0.5, set_on_fire_seconds=1)
    p.add("pacify", "stones:pacify", "Pacify", "minecraft:golden_carrot", 3, cond=action(20))
    p.add("recipes", "stones:unlock_recipes", hidden=True)
    p.effect("hero", "minecraft:hero_of_the_village")
    p.effect("night_vision", "minecraft:night_vision")
    p.write()


# ---------------------------------------------------------------- Power
def power():
    p = Power("power", "Power Stone", "minecraft:amethyst_shard", [155, 63, 224], [255, 255, 255])
    p.add("beam_aim", "palladium:aim", "Power Beam", "minecraft:end_crystal", 0, cond=held(), time=1, arm="main_arm")
    p.add("power_beam", "stones:power_beam", hidden=True, cond=enabled("beam_aim"))
    p.add("beam_visual", "palladium:energy_beam", hidden=True, cond=[enabled("beam_aim"), not_(has_power("stones:snap"))],
          energy_beam="stones:power_beam", damage=0.0, max_distance=100.0, speed=1.0)
    p.add("beam_visual_rainbow", "palladium:energy_beam", hidden=True, cond=[enabled("beam_aim"), has_power("stones:snap")],
          energy_beam="stones:power_beam_rainbow", damage=0.0, max_distance=100.0, speed=1.0)
    p.add("beam_up", "stones:beam_level", hidden=True, delta=1, cond=[enabled("beam_aim"), action(0, "scroll_up")])
    p.add("beam_down", "stones:beam_level", hidden=True, delta=-1, cond=[enabled("beam_aim"), action(0, "scroll_down")])
    p.add("explosion", "stones:controlled_explosion", "Controlled Explosion", "minecraft:tnt", 1, cond=held())
    p.add("empower", "stones:empower", "Empower", "minecraft:enchanted_golden_apple", 2, cond=action(2400))
    p.add("meteor_storm", "stones:meteor_storm", "Meteor Storm", "minecraft:fire_charge", 3, cond=action(600))
    p.add("punch", "stones:power_punch", "Power Punch", "minecraft:iron_sword", 4)
    p.attribute("knockback", "minecraft:generic.knockback_resistance", 1.0)
    p.write()


# ---------------------------------------------------------------- Reality
def reality():
    p = Power("reality", "Reality Stone", "minecraft:redstone", [224, 48, 48], [255, 255, 255])
    p.add("resize", "stones:resize", "Resize", "minecraft:brown_mushroom", 0, cond=toggle())
    p.add("resize_up", "stones:resize_step", hidden=True, delta=1, cond=[enabled("resize"), action(0, "scroll_up")])
    p.add("resize_down", "stones:resize_step", hidden=True, delta=-1, cond=[enabled("resize"), action(0, "scroll_down")])
    p.add("invisibility", "palladium:invisibility", "Invisibility", "minecraft:glass_bottle", 1, cond=toggle())
    p.add("block_copy", "stones:block_copy", "Block Duplication", "minecraft:grass_block", 2, cond=toggle())
    p.add("effects", "stones:reality_effects", "Warp Reality", "minecraft:fermented_spider_eye", 3, cond=action(200))
    p.add("bubble", "stones:bubble", "Bubble", "minecraft:prismarine_crystals", 4, cond=action(20))
    p.add("weather_clear", "stones:weather", "Clear Skies", "minecraft:sunflower", 5, cond=action(100), weather="clear")
    p.add("weather_rain", "stones:weather", "Summon Rain", "minecraft:water_bucket", 6, cond=action(100), weather="rain")
    p.add("weather_thunder", "stones:weather", "Summon Storm", "minecraft:lightning_rod", 7, cond=action(100), weather="thunder")
    p.write()


# ---------------------------------------------------------------- Time
def time():
    p = Power("time", "Time Stone", "minecraft:clock", [60, 207, 90], [255, 255, 255])
    p.add("time_freeze", "stones:time_freeze", "Time Freeze", "minecraft:clock", 0, cond=toggle(), projectiles_only=False, radius=40)
    p.add("projectile_stop", "stones:time_freeze", "Stop Projectiles", "minecraft:arrow", 1, cond=toggle(), projectiles_only=True, radius=28)
    p.add("time_rate", "stones:time_rate", "Time Rate", "minecraft:redstone", 2, cond=toggle())
    p.add("rate_up", "stones:time_rate_step", hidden=True, delta=1, cond=[enabled("time_rate"), action(0, "scroll_up")])
    p.add("rate_down", "stones:time_rate_step", hidden=True, delta=-1, cond=[enabled("time_rate"), action(0, "scroll_down")])
    p.add("fast_forward", "stones:fast_forward", "Fast Forward", "minecraft:sunflower", 3, cond=held())
    p.add("daylight", "stones:daylight_toggle", "Stop / Start Daylight", "minecraft:daylight_detector", 4, cond=action(20))
    p.add("water_walk", "palladium:water_walk", hidden=True)
    p.write()


# ---------------------------------------------------------------- Soul
def soul():
    p = Power("soul", "Soul Stone", "minecraft:soul_lantern", [255, 138, 31], [255, 255, 255])
    p.add("glow", "palladium:entity_glow", "Soul Sight", "minecraft:spyglass", 0, cond=held(), color="#ff8800", mode="others", distance=100.0)
    p.add("summon_army", "stones:summon_army", "Soul Army", "minecraft:zombie_head", 1, cond=action(600))
    p.add("immortal", "palladium:immortality", "Immortality", "minecraft:totem_of_undying", 2, cond=has_power("stones:snap"))
    p.attribute("health", "minecraft:generic.max_health", 380, title="Soul Vitality", icon="minecraft:golden_apple", index=3)
    p.effect("saturation", "minecraft:saturation", 1)
    p.effect("regeneration", "minecraft:regeneration", 2)
    p.add("cleanse", "stones:cleanse", hidden=True)
    p.write()


# ---------------------------------------------------------------- beams
def beam(name, glow, core, size=2.0, rainbow=False, body_part="right_arm", offset=(0, -11, 0)):
    """Offsets are in pixels from the body part's pivot; +y is up, so the palm of an arm is at y = -11."""
    os.makedirs(BEAMS, exist_ok=True)
    data = {
        "type": "palladium:laser",
        "body_part": body_part,
        "offset": list(offset),
        "glow_color": glow,
        "core_color": core,
        "size": size,
        "bloom": 3,
        "rainbow": rainbow,
        "visibility": True,
    }
    with open(os.path.join(BEAMS, f"{name}.json"), "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for build in (space, mind, power, reality, time, soul):
        build()
    beam("mind_beam", "#ffd21f", "#fff7c2", 1.6, body_part="head", offset=(0, 5, -5))
    beam("power_beam", "#9b3fe0", "#f1d9ff", 2.2)
    beam("power_beam_rainbow", "#ffffff", "#ffffff", 3.0, rainbow=True)

    # Keep the existing lang entries (the Snap, older ability names) and merge ours on top.
    existing = {}
    if os.path.exists(LANG):
        with open(LANG) as f:
            existing = json.load(f)
    existing.update(lang)
    with open(LANG, "w") as f:
        json.dump(existing, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("wrote", len(lang), "lang entries")
