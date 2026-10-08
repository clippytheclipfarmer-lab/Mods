#!/usr/bin/env python3
"""
Generates the Hulk render layer: assets/hulk/geo/hulk.geo.json and assets/hulk/textures/models/hulk.png.

Run from the repository's `heroes` folder:  python3 tools/models/hulk_model.py
Preview:  python3 tools/modelpreview.py <geo> <png> out.png        (the model alone; the player body is removed in game)
The six bones keep the names the Palladium render layer maps to the player's parts, so animations keep working.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mc_model import Callback, Flat, ModelBuilder, Skin, Torn  # noqa: E402

GREEN = (78, 164, 58)
DARK = (52, 122, 44)
LIGHT = (96, 186, 72)
PURPLE = (112, 56, 140)
HAIR = (22, 22, 28)
WHITE = (240, 244, 240)


def build():
    m = ModelBuilder("geometry.hulk", 128, 128)
    m.bone("armorHead", [0, 24, 0])
    m.bone("armorBody", [0, 24, 0])
    m.bone("armorRightArm", [-5, 22, 0])
    m.bone("armorLeftArm", [5, 22, 0])
    m.bone("armorRightLeg", [-1.9, 12, 0])
    m.bone("armorLeftLeg", [1.9, 12, 0])

    skin = lambda base=GREEN, **kw: Skin(base, **kw)

    # ------------------------------------------------------------------ head
    def face(face_name, x, y, w, h, base):
        if face_name != "north":
            return None
        # Eyes (white with a glowing green pupil), set deep under the brow.
        for ex in (1, 5):
            if ex <= x <= ex + 2 and y in (3, 4):
                if y == 4 and x in (ex + 1, ex + 2) or (y == 3 and x == ex + 1 and False):
                    return (120, 255, 90, 255)
                return WHITE + (255,)
        if y == 2 and 1 <= x <= 7:
            return (30, 80, 30, 255)          # brow shadow
        if y == 5 and x in (4,):
            return (40, 100, 36, 255)         # nose shadow
        if y == 6 and 2 <= x <= 6:
            return (24, 50, 24, 255)          # mouth line
        return None

    m.cube("armorHead", [-4.5, 24.2, -4.5], [9, 8, 9], Callback(skin(GREEN, seed=2), face))
    m.cube("armorHead", [-4.8, 28.2, -5.4], [9.6, 2, 1.6], skin(DARK, seed=3))                    # heavy brow
    m.cube("armorHead", [-0.9, 25.8, -5.6], [1.8, 2.4, 1.2], skin(DARK, seed=4))                  # nose

    def teeth(face_name, x, y, w, h, base):
        if face_name == "north" and y == 0 and 1 <= x <= w - 2:
            return WHITE + (255,) if x % 2 == 0 else (30, 60, 30, 255)
        return None

    m.cube("armorHead", [-4.4, 23.4, -5.4], [8.8, 2.4, 1.8], Callback(skin(GREEN, seed=5), teeth))  # jutting jaw
    m.cube("armorHead", [-4.8, 31.8, -4.8], [9.6, 1.6, 9.6], Flat(HAIR, seed=6))                  # hair
    m.cube("armorHead", [-4.8, 27.2, 3.4], [9.6, 4.8, 1.6], Flat(HAIR, seed=7))
    m.cube("armorHead", [-5.4, 28.0, -4.2], [1.0, 3.6, 7.0], Flat(HAIR, seed=8))                  # temples
    m.cube("armorHead", [4.4, 28.0, -4.2], [1.0, 3.6, 7.0], Flat(HAIR, seed=9))
    m.cube("armorHead", [-5.2, 26.0, -0.8], [0.8, 2.2, 1.6], skin(GREEN, seed=10))                # ears
    m.cube("armorHead", [4.4, 26.0, -0.8], [0.8, 2.2, 1.6], skin(GREEN, seed=11))

    # ------------------------------------------------------------------ body
    m.cube("armorBody", [-3.8, 23.6, -2.8], [7.6, 3.2, 5.6], skin(GREEN, seed=12))                # neck and traps
    m.cube("armorBody", [-6.4, 17.0, -3.2], [12.8, 7.2, 6.4], skin(GREEN, seed=13))               # chest
    m.cube("armorBody", [-6.0, 18.4, -4.4], [5.8, 4.6, 1.4], skin(LIGHT, seed=14, highlight=1.1, veins=1, lines=(("h", "north", 0.8),)))  # pecs
    m.cube("armorBody", [0.2, 18.4, -4.4], [5.8, 4.6, 1.4], skin(LIGHT, seed=15, highlight=1.1, veins=1, lines=(("h", "north", 0.8),)))
    m.cube("armorBody", [-4.6, 11.0, -2.8], [9.2, 6.4, 5.6],
           skin(GREEN, seed=16, lines=(("v", "north", 0.5), ("h", "north", 0.33), ("h", "north", 0.66))))  # abs
    m.cube("armorBody", [-7.2, 14.2, -2.2], [2.0, 6.8, 4.4], skin(DARK, seed=17))                 # lats
    m.cube("armorBody", [5.2, 14.2, -2.2], [2.0, 6.8, 4.4], skin(DARK, seed=18))
    m.cube("armorBody", [-6.0, 16.6, 2.8], [12.0, 7.6, 1.6], skin(DARK, seed=19, lines=(("v", "south", 0.5),)))  # back
    m.cube("armorBody", [-5.4, 7.0, -3.3], [10.8, 5.0, 6.6], Flat(PURPLE, seed=20))      # shorts
    m.cube("armorBody", [-5.5, 11.2, -3.4], [11.0, 1.2, 6.8], Flat((84, 40, 108), seed=21))       # waistband

    # ------------------------------------------------------------------ arms (right arm is at -x; the left is mirrored)
    arm = [
        ([-9.6, 19.0, -3.6], [5.2, 6.4, 7.2], skin(GREEN, seed=22, veins=2, lines=(("h", "north", 0.55),))),                       # deltoid
        ([-9.2, 13.0, -3.2], [4.8, 6.2, 6.4], skin(LIGHT, seed=23, highlight=1.1, veins=1, lines=(("v", "north", 0.5), ("v", "south", 0.5)))),  # biceps and triceps
        ([-9.6, 6.4, -3.0], [5.6, 6.8, 6.0], skin(GREEN, seed=24, veins=2, lines=(("v", "north", 0.5), ("v", "south", 0.5)))),  # forearm
        ([-9.2, 2.6, -2.6], [4.8, 3.8, 5.2], skin(DARK, seed=25)),                          # fist
    ]
    for origin, size, painter in arm:
        c = m.cube("armorRightArm", origin, size, painter)
        m.mirror_x(c, "armorLeftArm")

    # ------------------------------------------------------------------ legs
    legs = [
        ([-5.9, 5.6, -3.4], [5.8, 7.0, 6.8], skin(GREEN, seed=26, veins=1, lines=(("v", "north", 0.5),))),                        # thigh
        ([-5.6, 0.8, -3.1], [5.2, 5.0, 6.2], skin(DARK, seed=27, veins=1, lines=(("h", "north", 0.1),))),                         # calf
        ([-5.8, 0.0, -4.6], [5.6, 1.8, 8.0], skin(DARK, seed=28, edge=0.7)),               # bare foot
        ([-6.2, 6.0, -3.7], [6.4, 6.4, 7.4], Torn(PURPLE, depth=3, seed=29)),               # shorts leg
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
