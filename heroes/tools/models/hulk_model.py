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
    m.bone("armorHead", [0, 38, 0])
    m.bone("armorBody", [0, 38, 0])
    m.bone("armorRightArm", [-17, 40, 0])
    m.bone("armorLeftArm", [17, 40, 0])
    m.bone("armorRightLeg", [-9, 22, 0])
    m.bone("armorLeftLeg", [9, 22, 0])

    def mot(base=GREEN, seed=1, amount=0.14, **kw):
        return mottled(Skin(base, seed=seed, **kw), seed, amount)

    # ------------------------------------------------------------------ head (low between the traps, scowling)
    def face(face_name, x, y, w, h, base):
        if face_name != "north":
            return None
        for ex in (3, 9):
            if ex <= x <= ex + 2 and y in (4, 5):
                return (24, 44, 22, 255) if (y == 5 and x == ex + 1) else EYE + (255,)
        if y == 3 and 2 <= x <= 12:
            return (36, 76, 34, 255)           # furrowed brow shadow
        if y == 6 and x in (7, 8):
            return (40, 90, 36, 255)           # nostrils
        if y == 8 and 4 <= x <= 11:
            return (238, 234, 212, 255) if x % 2 == 0 else (62, 42, 36, 255)   # bared teeth
        return None

    HEAD = 2.0   # how far the head rises above the neck line so the face clears the chest
    head = lambda o, sz, painter: m.cube("armorHead", [o[0], o[1] + HEAD, o[2]], sz, painter)

    head([-7.5, 36, -6.5], [15, 11, 13], Callback(Skin(GREEN, seed=2, highlight=1.1), face))
    head([-7.6, 36.6, -7.4], [4.4, 4, 1.6], Skin(LIGHT, seed=30))  # cheeks
    head([3.2, 36.6, -7.4], [4.4, 4, 1.6], Skin(LIGHT, seed=31))
    head([-7.6, 41.0, -7.8], [15.2, 2.4, 2.0], Skin(DARK, seed=3))  # heavy scowling brow
    head([-1.6, 38.0, -8.2], [3.2, 3.6, 1.8], Skin(GREEN, seed=4))  # nose
    head([-6.4, 34.0, -7.0], [12.8, 3.6, 2.4], Skin(GREEN, seed=5))  # jutting jaw
    head([-7.6, 45.6, -7.2], [15.2, 2.6, 14.4], Flat(HAIR, seed=6))  # hair cap
    head([-7.6, 40.0, 6.0], [15.2, 6.0, 1.6], Flat(HAIR, seed=7))  # back of the hair
    head([-7.6, 43.4, -7.4], [5.0, 2.4, 1.2], Flat(HAIR, seed=8))  # fringe
    head([1.0, 44.0, -7.4], [6.6, 2.0, 1.2], Flat(HAIR, seed=9))
    head([-8.4, 38.0, -1.0], [1.0, 3.2, 2.4], Skin(GREEN, seed=10))  # ears
    head([7.4, 38.0, -1.0], [1.0, 3.2, 2.4], Skin(GREEN, seed=11))

    # ------------------------------------------------------------------ torso
    m.cube("armorBody", [-14, 29, -7], [28, 10, 14], mot(GREEN, 12))                                          # chest
    m.cube("armorBody", [-13.6, 30, -8.8], [13.6, 8, 2.4], mot(LIGHT, 14, highlight=1.1, lines=(("h", "north", 0.85),)))  # pecs
    m.cube("armorBody", [0, 30, -8.8], [13.6, 8, 2.4], mot(LIGHT, 15, highlight=1.1, lines=(("h", "north", 0.85),)))
    m.cube("armorBody", [-8.5, 23.5, -5.4], [17, 7.5, 10.8],
           mot(GREEN, 16, lines=(("v", "north", 0.5), ("h", "north", 0.33), ("h", "north", 0.66))))           # abs
    m.cube("armorBody", [-17, 26, -4.5], [3.4, 13, 9], mot(DARK, 17, 0.2))                                    # lats
    m.cube("armorBody", [13.6, 26, -4.5], [3.4, 13, 9], mot(DARK, 18, 0.2))
    m.cube("armorBody", [-14, 29, 6.4], [28, 10, 3], mot(DARK, 19, 0.25, lines=(("v", "south", 0.5),)))       # back
    m.cube("armorBody", [-14, 38.8, -5.5], [6.4, 5.6, 11], mot(DARK, 20, 0.2))                                # trapezius humps beside the head
    m.cube("armorBody", [7.6, 38.8, -5.5], [6.4, 5.6, 11], mot(DARK, 32, 0.2))
    m.cube("armorBody", [-17.5, 37.2, -5.5], [6, 4.2, 11], mot(GREEN, 21, 0.2))                               # traps step down to the shoulders
    m.cube("armorBody", [11.5, 37.2, -5.5], [6, 4.2, 11], mot(GREEN, 22, 0.2))
    m.cube("armorBody", [-13, 20.5, -6.4], [26, 4.2, 12.8], Torn((84, 54, 124), depth=1, seed=23, noise=6))   # shorts waist

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
        ([-15, 12, -6.6], [12, 10.4, 13.2], mot(GREEN, 28, 0.14)),                                            # thigh
        ([-14.4, 2.6, -5.8], [10.4, 10, 11.6], mot(DARK, 29, 0.16, veins=1)),                                 # calf
        ([-15, 0, -10.4], [11.4, 3, 17.4], mot(DARK, 30, 0.14, edge=0.7)),                                    # big bare foot
        ([-15.8, 13.6, -7.4], [13.6, 9.4, 14.8], Torn((88, 56, 130), depth=2, seed=31, noise=6)),             # shorts leg
    ]
    for origin, size, painter in legs:
        c = m.cube("armorRightLeg", origin, size, painter)
        m.mirror_x(c, "armorLeftLeg")
    return m


if __name__ == "__main__":
    root = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "hulk")
    os.makedirs(os.path.join(root, "geo"), exist_ok=True)
    os.makedirs(os.path.join(root, "textures", "models"), exist_ok=True)
    model = build()
    model.write(os.path.join(root, "geo", "hulk.geo.json"), os.path.join(root, "textures", "models", "hulk.png"))
    print("wrote the Hulk model and texture")
