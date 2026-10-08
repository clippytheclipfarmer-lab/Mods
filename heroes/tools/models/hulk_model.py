#!/usr/bin/env python3
"""
Generates the Hulk render layer: assets/hulk/geo/hulk.geo.json and assets/hulk/textures/models/hulk.png.

The model is about 3 blocks (48 units) tall: very wide shoulders, long legs set well apart, a head sunk between
huge trapezius muscles, massive arms, torn purple shorts and big bare feet.

Run from the repository's `heroes` folder:  python3 tools/models/hulk_model.py
Preview:  python3 tools/modelpreview.py <geo> <png> out.png        (the model alone; the player body is removed in game)
The six bones keep the names the Palladium render layer maps to the player's parts, so animations keep working.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mc_model import Callback, Flat, ModelBuilder, Skin, Torn  # noqa: E402

GREEN = (88, 162, 64)
DARK = (58, 118, 48)
LIGHT = (116, 184, 86)
BROWN = (104, 90, 62)
PURPLE = (96, 60, 140)
HAIR = (30, 24, 22)
EYE = (210, 236, 190)


WIDTH = 0.8   # horizontal scale of the body, arms and legs


def mottled(inner, seed, amount):
    """Skin with light brown blotches (the weathered look of the film Hulk)."""
    def fn(face, x, y, w, h, base):
        r = (x // 2 * 7349 + y // 2 * 9151 + seed * 131 + sum(map(ord, face)) * 17) % 1000 / 1000.0
        if r < amount:
            k = 0.25 + 0.3 * ((x * 31 + y * 17 + seed) % 5) / 4
            return tuple(int(base[i] * (1 - k) + BROWN[i] * k) for i in range(3)) + (255,)
        return None
    return Callback(inner, fn)


def build():
    m = ModelBuilder("geometry.hulk", 512, 512)
    m.bone("armorHead", [0, 40, 0])
    m.bone("armorBody", [0, 38, 0])
    m.bone("armorRightArm", [-17, 40, 0])
    m.bone("armorLeftArm", [17, 40, 0])
    m.bone("armorRightLeg", [-7.6, 22, 0])
    m.bone("armorLeftLeg", [7.6, 22, 0])

    def mot(base=GREEN, seed=1, amount=0.14, **kw):
        return mottled(Skin(base, seed=seed, **kw), seed, amount)

    # ------------------------------------------------------------------ head (a small cube sitting on top of the torso)
    def face(face_name, x, y, w, h, base):
        if face_name != "north":
            return None
        for ex in (2, 6):
            if ex <= x <= ex + 1 and y in (3, 4):
                return (24, 44, 22, 255) if (y == 4 and x == ex + 1) else EYE + (255,)
        if y == 2 and 1 <= x <= 8:
            return (36, 76, 34, 255)           # furrowed brow shadow
        if y == 5 and x in (4, 5):
            return (40, 90, 36, 255)           # nostrils
        if y == 7 and 2 <= x <= 7:
            return (238, 234, 212, 255) if x % 2 == 0 else (62, 42, 36, 255)   # bared teeth
        return None

    m.cube("armorHead", [-5, 40.4, -5], [10, 9, 10], Callback(Skin(GREEN, seed=2, highlight=1.1), face))
    m.cube("armorHead", [-5.5, 41.0, -5.6], [3, 3, 1.2], Skin(LIGHT, seed=30))                # cheeks
    m.cube("armorHead", [2.5, 41.0, -5.6], [3, 3, 1.2], Skin(LIGHT, seed=31))
    m.cube("armorHead", [-5.6, 44.2, -5.9], [11.2, 2, 1.8], Skin(DARK, seed=3))               # heavy scowling brow
    m.cube("armorHead", [-1.2, 42.2, -6.2], [2.4, 2.8, 1.4], Skin(GREEN, seed=4))             # nose
    m.cube("armorHead", [-4.6, 39.6, -5.4], [9.2, 2.8, 2.0], Skin(GREEN, seed=5))             # jutting jaw
    m.cube("armorHead", [-5.6, 48.4, -5.6], [11.2, 2.2, 11.2], Flat(HAIR, seed=6))            # hair cap
    m.cube("armorHead", [-5.6, 44.0, 4.6], [11.2, 5.0, 1.2], Flat(HAIR, seed=7))              # back of the hair
    m.cube("armorHead", [-5.6, 46.8, -5.8], [4.0, 2.0, 1.0], Flat(HAIR, seed=8))              # fringe
    m.cube("armorHead", [0.6, 47.4, -5.8], [5.0, 1.6, 1.0], Flat(HAIR, seed=9))
    m.cube("armorHead", [-6.0, 42.0, -0.8], [1.0, 2.6, 2.0], Skin(GREEN, seed=10))            # ears
    m.cube("armorHead", [5.0, 42.0, -0.8], [1.0, 2.6, 2.0], Skin(GREEN, seed=11))
    m.cube("armorBody", [-3.8, 38.4, -3.8], [7.6, 3.0, 7.6], mot(GREEN, 13))                  # short thick neck

    # ------------------------------------------------------------------ torso
    m.cube("armorBody", [-14, 29, -7], [28, 10, 14], mot(GREEN, 12))                                          # chest
    m.cube("armorBody", [-13.6, 30, -8.8], [13.6, 8, 2.4], mot(LIGHT, 14, highlight=1.1, lines=(("h", "north", 0.85),)))  # pecs
    m.cube("armorBody", [0, 30, -8.8], [13.6, 8, 2.4], mot(LIGHT, 15, highlight=1.1, lines=(("h", "north", 0.85),)))
    m.cube("armorBody", [-8.5, 23.5, -5.4], [17, 7.5, 10.8],
           mot(GREEN, 16, lines=(("v", "north", 0.5), ("h", "north", 0.33), ("h", "north", 0.66))))           # abs
    m.cube("armorBody", [-17, 26, -4.5], [3.4, 13, 9], mot(DARK, 17, 0.2))                                    # lats
    m.cube("armorBody", [13.6, 26, -4.5], [3.4, 13, 9], mot(DARK, 18, 0.2))
    m.cube("armorBody", [-14, 29, 6.4], [28, 10, 3], mot(DARK, 19, 0.25, lines=(("v", "south", 0.5),)))       # back
    m.cube("armorBody", [-14, 38.8, -5.5], [6.6, 2.8, 11], mot(DARK, 20, 0.2))                                # trapezius humps beside the head
    m.cube("armorBody", [7.4, 38.8, -5.5], [6.6, 2.8, 11], mot(DARK, 32, 0.2))
    m.cube("armorBody", [-17.5, 37.2, -5.5], [6, 4.2, 11], mot(GREEN, 21, 0.2))                               # traps step down to the shoulders
    m.cube("armorBody", [11.5, 37.2, -5.5], [6, 4.2, 11], mot(GREEN, 22, 0.2))
    m.cube("armorBody", [-12.4, 20.5, -6.2], [24.8, 4.2, 12.4], Torn((84, 54, 124), depth=1, seed=23, noise=6))   # shorts waist

    # ------------------------------------------------------------------ arms: huge, hanging low (right arm at -x; left mirrored)
    arm = [
        ([-25, 32.5, -7], [11, 10.5, 14], mot(GREEN, 24, 0.16, veins=1)),                                     # deltoid
        ([-24.4, 23.5, -6.4], [9.4, 9.5, 12.8], mot(LIGHT, 25, 0.14, highlight=1.1, lines=(("v", "north", 0.5),))),  # biceps and triceps
        ([-25.4, 11, -6.8], [11.4, 12.5, 13.6], mot(GREEN, 26, 0.14, veins=2, lines=(("v", "south", 0.5),))), # massive forearm
        ([-24.6, 4.6, -5.6], [9.6, 7, 11.2], mot(DARK, 27, 0.12)),                                            # fist
    ]
    for origin, size, painter in arm:
        c = m.cube("armorRightArm", origin, size, painter)
        m.mirror_x(c, "armorLeftArm")

    # ------------------------------------------------------------------ legs: long pillars set well apart, big bare feet
    legs = [
        ([-12.0, 12, -6.4], [10.6, 10.4, 12.8], mot(GREEN, 28, 0.14)),                                            # thigh
        ([-13.0, 2.6, -5.8], [10.4, 10, 11.6], mot(DARK, 29, 0.16, veins=1)),                                 # calf
        ([-13.6, 0, -10.4], [11.4, 3, 17.4], mot(DARK, 30, 0.14, edge=0.7)),                                    # big bare foot
        ([-12.6, 13.6, -7.0], [11.8, 9.4, 14.0], Torn((88, 56, 130), depth=2, seed=31, noise=6)),             # shorts leg
    ]
    for origin, size, painter in legs:
        c = m.cube("armorRightLeg", origin, size, painter)
        m.mirror_x(c, "armorLeftLeg")

    # Narrow everything except the head (x only), so the proportions can be tuned in one place.
    for c in m.cubes:
        if c.bone != "armorHead":
            c.origin = [c.origin[0] * WIDTH, c.origin[1], c.origin[2]]
            c.size = [c.size[0] * WIDTH, c.size[1], c.size[2]]
            if c.pivot:
                c.pivot = [c.pivot[0] * WIDTH, c.pivot[1], c.pivot[2]]
    for name in ("armorRightArm", "armorLeftArm", "armorRightLeg", "armorLeftLeg"):
        m.bones[name]["pivot"][0] *= WIDTH
    return m


def build_eyes():
    """Just the two eyes, drawn by a second render layer with the `glow` render type so they shine in the dark."""
    m = ModelBuilder("geometry.hulk_eyes", 16, 16)
    m.bone("armorHead", [0, 40, 0])
    m.bone("armorBody", [0, 38, 0])
    m.bone("armorRightArm", [-17 * WIDTH, 40, 0])
    m.bone("armorLeftArm", [17 * WIDTH, 40, 0])
    m.bone("armorRightLeg", [-7.6 * WIDTH, 22, 0])
    m.bone("armorLeftLeg", [7.6 * WIDTH, 22, 0])
    glow = Flat((120, 255, 90), noise=0, edge=1.0)
    m.cube("armorHead", [1.0, 43.2, -6.2], [2.4, 1.4, 0.8], glow)
    m.cube("armorHead", [-3.4, 43.2, -6.2], [2.4, 1.4, 0.8], glow)
    return m


if __name__ == "__main__":
    root = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "hulk")
    os.makedirs(os.path.join(root, "geo"), exist_ok=True)
    os.makedirs(os.path.join(root, "textures", "models"), exist_ok=True)
    model = build()
    model.write(os.path.join(root, "geo", "hulk.geo.json"), os.path.join(root, "textures", "models", "hulk.png"))
    eyes = build_eyes()
    eyes.write(os.path.join(root, "geo", "hulk_eyes.geo.json"), os.path.join(root, "textures", "models", "hulk_eyes.png"))
    print("wrote the Hulk model and texture")
