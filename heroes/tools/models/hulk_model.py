#!/usr/bin/env python3
"""
Generates the Hulk render layer (modelled on the look of the 2003 film: bright glossy green, round head, long torn purple pants): assets/hulk/geo/hulk.geo.json and assets/hulk/textures/models/hulk.png.

Run from the repository's `heroes` folder:  python3 tools/models/hulk_model.py
Preview:  python3 tools/modelpreview.py <geo> <png> out.png        (the model alone; the player body is removed in game)
The six bones keep the names the Palladium render layer maps to the player's parts, so animations keep working.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mc_model import Callback, Flat, ModelBuilder, Skin, Torn  # noqa: E402

GREEN = (88, 154, 66)
DARK = (58, 112, 48)
LIGHT = (112, 178, 84)
BROWN = (104, 84, 60)
PURPLE = (86, 66, 140)
HAIR = (34, 26, 22)
WHITE = (200, 224, 180)


def mottled(inner, seed, amount=0.3):
    amount *= 0.55
    """Skin with brown blotches (the weathered look of the film Hulk)."""
    def fn(face, x, y, w, h, base):
        r = (x // 2 * 7349 + y // 2 * 9151 + seed * 131 + sum(map(ord, face)) * 17) % 1000 / 1000.0
        if r < amount:
            k = 0.25 + 0.3 * ((x * 31 + y * 17 + seed) % 5) / 4
            return tuple(int(base[i] * (1 - k) + BROWN[i] * k) for i in range(3)) + (255,)
        return None
    return Callback(inner, fn)


def build():
    m = ModelBuilder("geometry.hulk", 256, 256)
    m.bone("armorHead", [0, 22, 0])
    m.bone("armorBody", [0, 24, 0])
    m.bone("armorRightArm", [-5, 22, 0])
    m.bone("armorLeftArm", [5, 22, 0])
    m.bone("armorRightLeg", [-1.9, 12, 0])
    m.bone("armorLeftLeg", [1.9, 12, 0])

    skin = lambda base=GREEN, **kw: Skin(base, **kw)
    mot = lambda base=GREEN, seed=1, amount=0.3, **kw: mottled(Skin(base, seed=seed, **kw), seed, amount)

    # ------------------------------------------------------------------ head (sunk low between huge traps, scowling)
    def face(face_name, x, y, w, h, base):
        if face_name != "north":
            return None
        for ex in (1, 6):
            if ex <= x <= ex + 2 and y in (3, 4):
                return (20, 40, 20, 255) if (y == 4 and x == ex + 1) else WHITE + (255,)
        if y == 2 and 1 <= x <= 8:
            return (34, 70, 32, 255)          # furrowed brow shadow
        if y == 5 and x in (4, 5):
            return (40, 90, 36, 255)          # nostrils
        if y == 6 and 2 <= x <= 7:
            return (236, 232, 210, 255) if x % 2 == 0 else (60, 40, 36, 255)   # bared teeth
        return None

    m.cube("armorHead", [-5.0, 21.0, -4.8], [10, 8, 9.6], Callback(skin(GREEN, seed=2, highlight=1.1), face))   # skull
    m.cube("armorHead", [-5.4, 21.4, -5.4], [3.4, 3.0, 1.2], skin(LIGHT, seed=30))                              # cheeks
    m.cube("armorHead", [2.0, 21.4, -5.4], [3.4, 3.0, 1.2], skin(LIGHT, seed=31))
    m.cube("armorHead", [-5.2, 24.8, -5.6], [10.4, 1.8, 1.6], skin(DARK, seed=3))                               # scowling brow
    m.cube("armorHead", [-1.2, 22.6, -5.8], [2.4, 2.6, 1.2], skin(GREEN, seed=4))                               # nose
    m.cube("armorHead", [-4.4, 19.8, -5.0], [8.8, 2.4, 1.6], skin(GREEN, seed=5))                               # heavy jaw
    m.cube("armorHead", [-5.2, 28.6, -5.0], [10.4, 1.8, 10.0], Flat(HAIR, seed=6))                              # hair cap
    m.cube("armorHead", [-5.2, 25.0, 4.2], [10.4, 4.0, 1.0], Flat(HAIR, seed=7))                                # back of hair
    m.cube("armorHead", [-5.2, 26.4, -5.2], [3.4, 2.2, 1.0], Flat(HAIR, seed=8))                                # fringe
    m.cube("armorHead", [0.6, 27.0, -5.2], [4.6, 1.8, 1.0], Flat(HAIR, seed=9))
    m.cube("armorHead", [-5.8, 22.6, -0.8], [0.8, 2.4, 1.6], skin(GREEN, seed=10))                              # ears
    m.cube("armorHead", [5.0, 22.6, -0.8], [0.8, 2.4, 1.6], skin(GREEN, seed=11))

    # ------------------------------------------------------------------ torso: wide, deep chest and traps up to the ears
    m.cube("armorBody", [-7.4, 14.6, -4.0], [14.8, 9.4, 8.0], mot(GREEN, 12))                                    # chest
    m.cube("armorBody", [-7.2, 17.2, -5.0], [7.0, 5.4, 1.4], mot(LIGHT, 14, highlight=1.1, lines=(("h", "north", 0.8),)))   # pecs
    m.cube("armorBody", [0.2, 17.2, -5.0], [7.0, 5.4, 1.4], mot(LIGHT, 15, highlight=1.1, lines=(("h", "north", 0.8),)))
    m.cube("armorBody", [-4.8, 11.0, -3.2], [9.6, 4.6, 6.4],
           skin(GREEN, seed=16, lines=(("v", "north", 0.5), ("h", "north", 0.33), ("h", "north", 0.66))))   # abs
    m.cube("armorBody", [-7.0, 22.0, -3.6], [14.0, 3.2, 7.2], mot(DARK, 17, 0.4))                                # trapezius top
    m.cube("armorBody", [-9.2, 21.0, -3.8], [4.4, 4.2, 7.6], mot(GREEN, 18, 0.4), rotation=[0, 0, 28], pivot=[-7, 22, 0])  # sloping traps
    m.cube("armorBody", [4.8, 21.0, -3.8], [4.4, 4.2, 7.6], mot(GREEN, 19, 0.4), rotation=[0, 0, -28], pivot=[7, 22, 0])
    m.cube("armorBody", [-7.6, 15.0, 3.6], [15.2, 9.4, 1.8], mot(DARK, 20, 0.4, lines=(("v", "south", 0.5),)))   # back
    m.cube("armorBody", [-6.6, 11.6, -3.9], [13.2, 3.4, 7.8], Torn((72, 54, 120), depth=1, seed=21, noise=6))      # shorts waist

    # ------------------------------------------------------------------ arms: huge, hanging low (right arm at -x; left mirrored)
    arm = [
        ([-12.8, 18.0, -4.4], [6.0, 7.2, 8.8], mot(GREEN, 22, 0.35, veins=1)),                                    # deltoid
        ([-12.4, 11.6, -4.0], [5.6, 6.8, 8.0], mot(LIGHT, 23, 0.3, highlight=1.1, lines=(("v", "north", 0.5),))),   # biceps and triceps
        ([-12.8, 5.0, -4.0], [6.4, 6.8, 8.0], mot(GREEN, 24, 0.3, veins=2, lines=(("v", "south", 0.5),))),         # massive forearm
        ([-12.4, 0.8, -3.4], [5.6, 4.4, 6.8], mot(DARK, 25, 0.25)),                                               # fist
    ]
    for origin, size, painter in arm:
        c = m.cube("armorRightArm", origin, size, painter)
        m.mirror_x(c, "armorLeftArm")

    # ------------------------------------------------------------------ legs: thick pillars, big bare feet, boxer shorts
    legs = [
        ([-7.4, 5.0, -3.8], [6.8, 8.0, 7.6], mot(GREEN, 26, 0.3)),                                                # thigh
        ([-7.0, 1.8, -3.5], [6.2, 6.0, 7.0], mot(DARK, 27, 0.35, veins=1)),                                       # calf
        ([-7.2, 0.0, -6.2], [6.6, 2.0, 10.6], mot(DARK, 28, 0.3, edge=0.7)),                                      # big bare foot
        ([-7.9, 7.6, -4.3], [7.8, 5.4, 8.6], Torn((80, 62, 130), depth=1, seed=29, noise=6)),                      # shorts leg
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
