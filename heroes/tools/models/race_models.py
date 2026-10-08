#!/usr/bin/env python3
"""
Generates the race models: a male and a female model each for the Kree, the Jotun (frost giants) and the Skrulls.
Writes assets/races/geo/<race>_<gender>.geo.json and assets/races/textures/models/<race>_<gender>.png,
the render layers (assets/races/palladium/render_layers) and the powers that wear them (data/races/palladium/powers).

Run from the repository's `heroes` folder:  python3 tools/models/race_models.py
Preview: python3 tools/modelpreview.py src/main/resources/assets/races/geo/kree_male.geo.json src/main/resources/assets/races/textures/models/kree_male.png out.png

Every model uses the six bones Palladium maps to the player's parts (armorHead, armorBody, armorRightArm, armorLeftArm,
armorRightLeg, armorLeftLeg) at vanilla player size; the Jotun are made tall by the race's height scale, not by the model.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mc_model import Callback, Flat, ModelBuilder, Skin, Torn  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources")


class Palette:
    def __init__(self, **kw):
        self.__dict__.update(kw)


KREE = Palette(skin=(72, 120, 196), skin_dark=(52, 90, 160), skin_light=(100, 150, 220), hair=(24, 24, 32),
               suit=(36, 112, 78), suit_dark=(22, 60, 46), trim=(200, 164, 60), boots=(26, 30, 30), eyes=(30, 24, 20), lips=(52, 80, 150))
JOTUN = Palette(skin=(120, 168, 206), skin_dark=(70, 112, 164), skin_light=(170, 212, 236), hair=(214, 230, 244),
                fur=(236, 240, 244), fur_dark=(150, 130, 108), leather=(86, 64, 48), eyes=(214, 24, 30), lips=(70, 108, 160))
SKRULL = Palette(skin=(118, 164, 62), skin_dark=(78, 122, 44), skin_light=(150, 196, 84), cloth=(58, 52, 66), cloth_dark=(34, 30, 42),
                 trim=(188, 150, 62), eyes=(236, 206, 60), pupil=(14, 14, 14), lips=(88, 128, 48))


def skin(base, seed, **kw):
    return Skin(base, noise=4, edge=0.88, seed=seed, **kw)


def humanoid(name, female, p, head, torso, arms, legs):
    """Builds the shared skeleton and lets each race fill in its own details through the four callbacks."""
    m = ModelBuilder("geometry." + name, 128, 128)
    m.bone("armorHead", [0, 24, 0])
    m.bone("armorBody", [0, 24, 0])
    m.bone("armorRightArm", [-5, 22, 0])
    m.bone("armorLeftArm", [5, 22, 0])
    m.bone("armorRightLeg", [-1.9, 12, 0])
    m.bone("armorLeftLeg", [1.9, 12, 0])
    head(m, female, p)
    torso(m, female, p)
    arms(m, female, p)
    legs(m, female, p)
    return m


def pair(m, bone_r, bone_l, origin, size, painter):
    """A cube on the right limb and its mirror image on the left."""
    c = m.cube(bone_r, origin, size, painter)
    m.mirror_x(c, bone_l)
    return c


# ============================================================================ KREE
def kree_head(m, female, p):
    def face(f, x, y, w, h, base):
        if f != "north":
            return None
        if y in (3, 4) and (1 <= x <= 2 or w - 3 <= x <= w - 2):
            return p.eyes + (255,) if y == 4 else (236, 236, 240, 255)
        if y == 2 and (1 <= x <= 3 or w - 4 <= x <= w - 2):
            return p.hair + (255,)                        # brows
        if y == 6 and 2 <= x <= w - 3:
            return p.lips + (255,)
        return None

    m.cube("armorHead", [-4, 24, -4], [8, 8, 8], Callback(skin(p.skin, 1, highlight=1.1), face))
    m.cube("armorHead", [-4.3, 29.8, -4.3], [8.6, 2.4, 8.6], Flat(p.hair, seed=2, noise=3))       # short dark hair
    m.cube("armorHead", [-4.3, 26, 3.2], [8.6, 4, 1.4], Flat(p.hair, seed=3, noise=3))
    if female:
        c = m.cube("armorHead", [-1.4, 25.5, 4.4], [2.8, 5.5, 2.4], Flat(p.hair, seed=4, noise=3))  # ponytail
    else:
        m.cube("armorHead", [-4.5, 30.2, -4.5], [9, 1.0, 1.2], Flat(p.hair, seed=4, noise=3))      # heavy hairline
        m.cube("armorHead", [-3.6, 24, -4.4], [7.2, 1.4, 0.6], skin(p.skin_dark, 5))                 # strong jaw
    m.cube("armorHead", [-4.7, 25.4, -0.8], [0.7, 2.2, 1.6], skin(p.skin, 6))                       # ears
    m.cube("armorHead", [4.0, 25.4, -0.8], [0.7, 2.2, 1.6], skin(p.skin, 7))


def kree_torso(m, female, p):
    def emblem(f, x, y, w, h, base):
        if f == "north" and w // 2 - 1 <= x <= w // 2 and 1 <= y <= h - 3:
            return p.trim + (255,)                          # gold stripe down the chest
        return None

    if female:
        m.cube("armorBody", [-3.9, 17, -2.1], [7.8, 7, 4.2], Callback(Flat(p.suit, seed=10, noise=4), emblem))
        m.cube("armorBody", [-3.2, 18.4, -3.0], [2.9, 2.2, 1.0], Flat(p.suit, seed=11, noise=4))
        m.cube("armorBody", [0.3, 18.4, -3.0], [2.9, 2.2, 1.0], Flat(p.suit, seed=12, noise=4))
        m.cube("armorBody", [-3.5, 12, -2.0], [7, 5.2, 4.0], Flat(p.suit_dark, seed=13, noise=4))        # waist
        m.cube("armorBody", [-4.3, 11.6, -2.4], [8.6, 3.2, 4.8], Flat(p.suit_dark, seed=14, noise=4))    # hips
    else:
        m.cube("armorBody", [-4.6, 15, -2.4], [9.2, 9.4, 4.8], Callback(Flat(p.suit, seed=10, noise=4), emblem))
        m.cube("armorBody", [-4.2, 11.6, -2.3], [8.4, 4, 4.6], Flat(p.suit_dark, seed=13, noise=4))
    m.cube("armorBody", [-4.8, 14.2, -2.5], [9.6, 1.0, 5.0], Flat(p.trim, seed=15, noise=2))             # belt
    m.cube("armorBody", [-1.6, 23.8, -1.6], [3.2, 1.2, 3.2], skin(p.skin, 16))                           # neck


def kree_arms(m, female, p):
    w = 3.2 if female else 4.2
    x0 = -(4 + w - 0.2) - (0.0 if female else 0.0)
    pair(m, "armorRightArm", "armorLeftArm", [-4.2 - w + 0.4, 15.6, -2.2], [w, 8.4, 4.4], Flat(p.suit, seed=20, noise=4))   # sleeve
    pair(m, "armorRightArm", "armorLeftArm", [-4.2 - w + 0.4, 12, -2.0], [w, 3.8, 4.0], skin(p.skin, 21))                     # hand
    pair(m, "armorRightArm", "armorLeftArm", [-4.4 - w + 0.2, 22.6, -2.6], [w + 0.4, 1.8, 5.2], Flat(p.suit_dark, seed=22))    # shoulder plate


def kree_legs(m, female, p):
    w = 3.9 if female else 4.1
    pair(m, "armorRightLeg", "armorLeftLeg", [-4.1 + (0.2 if female else 0), 4, -2.2], [w, 8, 4.4], Flat(p.suit, seed=30, noise=4))
    pair(m, "armorRightLeg", "armorLeftLeg", [-4.0 + (0.2 if female else 0), 0, -2.4], [w - 0.1, 4.2, 4.8], Flat(p.boots, seed=31, noise=3))


# ============================================================================ JOTUN
def ridge(p):
    """Raised ridge scars: diagonal darker bands, like a frost giant's markings."""
    def fn(f, x, y, w, h, base):
        if f in ("up", "down"):
            return None
        if (x * 2 + y * 3) % 9 in (0, 1):
            return tuple(int(base[i] * 0.78 + p.skin_dark[i] * 0.22) for i in range(3)) + (255,)
        return None
    return fn


def jotun_head(m, female, p):
    def face(f, x, y, w, h, base):
        if f != "north":
            return None
        if y in (3, 4) and (1 <= x <= 2 or w - 3 <= x <= w - 2):
            return p.eyes + (255,)                         # glowing red eyes
        if y == 2 and (1 <= x <= 3 or w - 4 <= x <= w - 2):
            return p.skin_dark + (255,)
        if y == 6 and 2 <= x <= w - 3:
            return p.lips + (255,)
        return None

    m.cube("armorHead", [-4, 24, -4], [8, 8, 8], Callback(skin(p.skin, 40, highlight=1.1), lambda f, x, y, w, h, b: face(f, x, y, w, h, b) or ridge(p)(f, x, y, w, h, b) if f != "north" else face(f, x, y, w, h, b)))
    m.cube("armorHead", [-4.4, 26.4, -4.6], [8.8, 1.6, 1.2], skin(p.skin_dark, 41))              # heavy brow
    if female:
        m.cube("armorHead", [-4.5, 28.6, -4.5], [9, 3.4, 9], Flat(p.hair, seed=42, noise=4))      # long white-blue hair
        m.cube("armorHead", [-4.5, 20, 3.0], [9, 9, 1.8], Flat(p.hair, seed=43, noise=4))
        pair(m, "armorHead", "armorHead", [-5.0, 23, -2.0], [1.0, 6, 4], Flat(p.hair, seed=44, noise=4)) if False else None
        m.cube("armorHead", [-5.2, 22, -2.2], [1.2, 7, 4.4], Flat(p.hair, seed=44, noise=4))
        m.cube("armorHead", [4.0, 22, -2.2], [1.2, 7, 4.4], Flat(p.hair, seed=45, noise=4))
    else:
        for i, x in enumerate((-3.0, -1.0, 1.0)):                                                 # crown ridges
            m.cube("armorHead", [x, 32, -1.5 + i * 0.4], [1.6, 1.8 + (i % 2), 3.2], skin(p.skin_dark, 46 + i))
        m.cube("armorHead", [-4.4, 31.6, -4.4], [8.8, 0.8, 8.8], skin(p.skin_dark, 49))
    m.cube("armorHead", [-4.9, 25.4, -0.8], [0.9, 2.6, 1.6], skin(p.skin, 50))                  # ears
    m.cube("armorHead", [4.0, 25.4, -0.8], [0.9, 2.6, 1.6], skin(p.skin, 51))


def jotun_torso(m, female, p):
    r = ridge(p)
    if female:
        m.cube("armorBody", [-3.9, 12, -2.1], [7.8, 12, 4.2], Callback(skin(p.skin, 52), r))
        m.cube("armorBody", [-4.2, 17.4, -2.5], [8.4, 3.6, 5.0], Flat(p.fur, seed=53, noise=6))              # fur wrap top
        m.cube("armorBody", [-4.5, 11.4, -2.6], [9, 4.6, 5.2], Torn(p.fur, depth=2, seed=54, noise=6))      # fur skirt top
    else:
        m.cube("armorBody", [-4.6, 12, -2.4], [9.2, 12.4, 4.8], Callback(skin(p.skin, 52, highlight=1.1, lines=(("v", "north", 0.5), ("h", "north", 0.5))), r))
        m.cube("armorBody", [-4.8, 11.4, -2.6], [9.6, 4.6, 5.2], Torn(p.fur, depth=2, seed=54, noise=6))
        m.cube("armorBody", [-5.0, 21.6, -2.6], [10, 2.4, 5.2], Flat(p.fur, seed=55, noise=6))               # fur mantle
    m.cube("armorBody", [-1.6, 23.8, -1.6], [3.2, 1.2, 3.2], skin(p.skin, 56))


def jotun_arms(m, female, p):
    w = 3.4 if female else 4.4
    r = ridge(p)
    pair(m, "armorRightArm", "armorLeftArm", [-4.0 - w + 0.2, 12, -2.2], [w, 12, 4.4], Callback(skin(p.skin, 60, veins=1), r))
    pair(m, "armorRightArm", "armorLeftArm", [-4.0 - w, 13.4, -2.5], [w + 0.2, 2.6, 5.0], Flat(p.leather, seed=61, noise=4))   # leather bracer


def jotun_legs(m, female, p):
    w = 3.9 if female else 4.1
    r = ridge(p)
    pair(m, "armorRightLeg", "armorLeftLeg", [-4.0, 0, -2.2], [w, 12, 4.4], Callback(skin(p.skin, 70), r))
    pair(m, "armorRightLeg", "armorLeftLeg", [-4.3, 3.6, -2.5], [w + 0.5, 4.4, 5.0], Flat(p.fur_dark, seed=71, noise=6))   # fur boot cuff


# ============================================================================ SKRULL
def skrull_head(m, female, p):
    def face(f, x, y, w, h, base):
        if f != "north":
            return None
        if y in (3, 4) and (1 <= x <= 2 or w - 3 <= x <= w - 2):
            return p.pupil + (255,) if y == 4 and x in (2, w - 3) else p.eyes + (255,)     # yellow eyes, dark pupils
        if y == 2 and (1 <= x <= 3 or w - 4 <= x <= w - 2):
            return p.skin_dark + (255,)
        if y == 7 and 2 <= x <= w - 3:
            return p.lips + (255,)
        return None

    def crease(f, x, y, w, h, base):
        if f == "north" and y in (0, 1) and x % 2 == 1:
            return tuple(int(c * 0.8) for c in base[:3]) + (255,)                       # forehead creases
        return face(f, x, y, w, h, base)

    m.cube("armorHead", [-4, 24, -4], [8, 8, 8], Callback(skin(p.skin, 80, highlight=1.1), crease))
    m.cube("armorHead", [-4.3, 29.0, -3.0], [8.6, 3.2, 7.6], skin(p.skin_dark, 81))              # ridged crown
    for i in range(3):
        m.cube("armorHead", [-2.8 + i * 2.4, 31.8, -2.6], [1.2, 1.4, 6.6], skin(p.skin_dark, 82 + i))
    m.cube("armorHead", [-1.9, 23.0, -4.7], [3.8, 2.2, 1.0], skin(p.skin_dark, 85))             # chin ridges
    for i in range(3):
        m.cube("armorHead", [-1.6 + i * 1.2, 21.8, -4.9], [0.8, 1.8 if not female else 1.2, 0.8], skin(p.skin_dark, 86 + i))
    ear_h = 5.2 if not female else 4.4
    for side in (-1, 1):                                                                         # long pointed ears
        x = -6.2 if side < 0 else 4.0
        m.cube("armorHead", [x, 25.6, -0.8], [2.2, 2.0, 1.6], skin(p.skin, 90))
        m.cube("armorHead", [x + (0.2 if side < 0 else 0.2), 27.4, -0.6], [1.8, ear_h - 1.8, 1.2], skin(p.skin_light, 91))
    m.cube("armorHead", [-4.4, 24.0, -4.6], [8.8, 1.4, 1.0], skin(p.skin_dark, 92))             # heavy cheekbones
    if female:
        m.cube("armorHead", [-3.8, 29.4, -3.8], [7.6, 1.2, 1.2], skin(p.skin_dark, 93))          # sculpted brow line


def skrull_torso(m, female, p):
    def plate(f, x, y, w, h, base):
        if f == "north" and (x in (0, w - 1) or y in (0, h - 1)):
            return p.trim + (255,)
        return None

    if female:
        m.cube("armorBody", [-3.9, 17, -2.1], [7.8, 7, 4.2], Callback(Flat(p.cloth, seed=100, noise=4), plate))
        m.cube("armorBody", [-3.2, 18.4, -3.0], [2.9, 2.0, 1.0], Flat(p.cloth, seed=101, noise=4))
        m.cube("armorBody", [0.3, 18.4, -3.0], [2.9, 2.0, 1.0], Flat(p.cloth, seed=102, noise=4))
        m.cube("armorBody", [-3.5, 12, -2.0], [7, 5.2, 4.0], skin(p.skin, 103))
        m.cube("armorBody", [-4.3, 11.6, -2.4], [8.6, 3.2, 4.8], Flat(p.cloth_dark, seed=104, noise=4))
    else:
        m.cube("armorBody", [-4.6, 15, -2.4], [9.2, 9.4, 4.8], Callback(Flat(p.cloth, seed=100, noise=4), plate))
        m.cube("armorBody", [-4.2, 11.6, -2.3], [8.4, 4, 4.6], Flat(p.cloth_dark, seed=104, noise=4))
        m.cube("armorBody", [-4.6, 12.0, -2.4], [9.2, 3.0, 0.8], skin(p.skin, 105))
    m.cube("armorBody", [-4.8, 14.2, -2.5], [9.6, 1.0, 5.0], Flat(p.trim, seed=106, noise=2))
    m.cube("armorBody", [-1.6, 23.8, -1.6], [3.2, 1.2, 3.2], skin(p.skin, 107))


def skrull_arms(m, female, p):
    w = 3.2 if female else 4.2
    pair(m, "armorRightArm", "armorLeftArm", [-4.2 - w + 0.4, 12, -2.0], [w, 12, 4.0], skin(p.skin, 110, veins=1))
    pair(m, "armorRightArm", "armorLeftArm", [-4.4 - w + 0.2, 21.6, -2.5], [w + 0.4, 2.4, 5.0], Flat(p.cloth_dark, seed=111))   # shoulder guard
    pair(m, "armorRightArm", "armorLeftArm", [-4.3 - w + 0.3, 14.6, -2.3], [w + 0.2, 3.0, 4.6], Flat(p.cloth, seed=112))          # bracer


def skrull_legs(m, female, p):
    w = 3.9 if female else 4.1
    pair(m, "armorRightLeg", "armorLeftLeg", [-4.0 + (0.2 if female else 0), 5, -2.2], [w, 7, 4.4], Flat(p.cloth, seed=120, noise=4))
    pair(m, "armorRightLeg", "armorLeftLeg", [-4.0 + (0.2 if female else 0), 0, -2.2], [w, 5.4, 4.4], skin(p.skin, 121))
    pair(m, "armorRightLeg", "armorLeftLeg", [-4.1 + (0.2 if female else 0), 0, -2.5], [w + 0.2, 2.6, 5.0], Flat(p.cloth_dark, seed=122))


RACES = {
    "kree": (KREE, kree_head, kree_torso, kree_arms, kree_legs),
    "jotun": (JOTUN, jotun_head, jotun_torso, jotun_arms, jotun_legs),
    "skrull": (SKRULL, skrull_head, skrull_torso, skrull_arms, skrull_legs),
}
GENDERS = {"male": False, "female": True}
HIDE_ALL = ["head", "head_overlay", "chest", "chest_overlay", "right_arm", "right_arm_overlay", "left_arm", "left_arm_overlay",
            "right_leg", "right_leg_overlay", "left_leg", "left_leg_overlay"]
POWER_COLORS = {"kree": ([72, 120, 196], [36, 112, 78]), "jotun": ([120, 168, 206], [236, 240, 244]), "skrull": ([118, 164, 62], [58, 52, 66])}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def main():
    lang = {}
    for race, (p, head, torso, arms, legs) in RACES.items():
        for gender, female in GENDERS.items():
            key = "%s_%s" % (race, gender)
            m = humanoid(key, female, p, head, torso, arms, legs)
            geo = os.path.join(ROOT, "assets", "races", "geo", key + ".geo.json")
            tex = os.path.join(ROOT, "assets", "races", "textures", "models", key + ".png")
            os.makedirs(os.path.dirname(geo), exist_ok=True)
            os.makedirs(os.path.dirname(tex), exist_ok=True)
            m.write(geo, tex)
            write_json(os.path.join(ROOT, "assets", "races", "palladium", "render_layers", key + ".json"), {
                "type": "geckolib:default",
                "model": "races:geo/%s.geo.json" % key,
                "texture": "races:textures/models/%s.png" % key,
                "bones": {"head": "armorHead", "body": "armorBody", "right_arm": "armorRightArm", "left_arm": "armorLeftArm",
                          "right_leg": "armorRightLeg", "left_leg": "armorLeftLeg"},
                "render_full_model_in_first_person": False})
            primary, secondary = POWER_COLORS[race]
            abilities = {
                "hide_body": {"type": "palladium:remove_body_part", "hidden": True, "hidden_in_bar": True,
                              "affects_first_person": False, "body_parts": HIDE_ALL},
                "render_layer": {"type": "palladium:render_layer", "hidden": True, "hidden_in_bar": True, "render_layer": "races:" + key}}
            if race == "skrull":
                # Shapeshifters turn their own look off while they wear another shape (the marker power races:morphed).
                not_morphed = {"enabling": {"type": "palladium:not", "conditions": [{"type": "palladium:has_power", "power": "races:morphed"}]}}
                abilities["hide_body"]["conditions"] = not_morphed
                abilities["render_layer"]["conditions"] = not_morphed
                abilities["shapeshift"] = {"type": "races:shapeshift", "list_index": 0, "title": {"translate": "ability.races.shapeshift"},
                                           "icon": "minecraft:ender_eye", "conditions": {"enabling": {"type": "palladium:action", "cooldown": 40}}}
            write_json(os.path.join(ROOT, "data", "races", "palladium", "powers", key + ".json"), {
                "name": {"translate": "power.races." + key},
                "icon": "minecraft:player_head",
                "primary_color": primary,
                "secondary_color": secondary,
                "abilities": abilities})
            lang["power.races." + key] = "%s (%s)" % (race.capitalize() if race != "jotun" else "Jotun", gender)
    lang["power.races.morphed"] = "Shapeshifted"
    lang["ability.races.shapeshift"] = "Shapeshift"
    write_json(os.path.join(ROOT, "data", "races", "palladium", "powers", "morphed.json"), {
        "name": {"translate": "power.races.morphed"}, "icon": "minecraft:ender_eye", "primary_color": [118, 164, 62], "secondary_color": [58, 52, 66],
        "abilities": {"flag": {"type": "palladium:dummy", "hidden": True, "hidden_in_bar": True}}})
    os.makedirs(os.path.join(ROOT, "assets", "races", "lang"), exist_ok=True)
    write_json(os.path.join(ROOT, "assets", "races", "lang", "en_us.json"), lang)
    print("wrote 6 race models, render layers and powers")


if __name__ == "__main__":
    main()
