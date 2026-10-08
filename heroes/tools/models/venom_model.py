#!/usr/bin/env python3
"""
Generates the symbiote (Venom) suit: assets/symbiote/geo/suit_{torso,arms,legs,head}.geo.json (the parts that spread
over the player one after another), the full model suit.geo.json for previews, and textures/models/suit.png.

Run from the repository's `heroes` folder:  python3 tools/models/venom_model.py
Preview:  python3 tools/modelpreview.py src/main/resources/assets/symbiote/geo/suit.geo.json src/main/resources/assets/symbiote/textures/models/suit.png out.png
The six bones keep the names Palladium maps to the player's parts. Slightly bigger than the player so it covers the skin.
"""
import copy
import json
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from mc_model import Callback, ModelBuilder, Skin  # noqa: E402

BLACK = (20, 20, 26)
SHEEN = (66, 72, 96)
WHITE = (240, 242, 246)
RED = (170, 24, 44)


def sheen(inner, seed, amount=0.06):
    """Black suit with faint blue-grey streaks, like wet latex."""
    def fn(face, x, y, w, h, base):
        r = (x * 7349 + y // 2 * 9151 + seed * 131 + sum(map(ord, face)) * 17) % 1000 / 1000.0
        if r < amount:
            return tuple(int(base[i] * 0.4 + SHEEN[i] * 0.6) for i in range(3)) + (255,)
        return None
    return Callback(inner, fn)


SPIDER = [
    "..#...#..",
    "#..#.#..#",
    ".#.###.#.",
    "..#####..",
    ".#.###.#.",
    "#..#.#..#",
    "..#...#..",
]


def build():
    m = ModelBuilder("geometry.symbiote_suit", 128, 128)
    m.bone("armorHead", [0, 24, 0])
    m.bone("armorBody", [0, 24, 0])
    m.bone("armorRightArm", [-7, 22, 0])
    m.bone("armorLeftArm", [7, 22, 0])
    m.bone("armorRightLeg", [-2.9, 12, 0])
    m.bone("armorLeftLeg", [2.9, 12, 0])
    suit = lambda seed, **kw: sheen(Skin(BLACK, noise=3, edge=0.88, seed=seed, **kw), seed)

    # ---- head: a huge toothy grin, big white slanted eyes, a long tongue
    def face(face_name, x, y, w, h, base):
        if face_name != "north":
            return None
        eye = {(3, 2), (2, 3), (3, 3), (1, 4), (2, 4), (3, 4), (4, 4), (2, 5), (3, 5)}
        if (x, y) in eye or (w - 1 - x, y) in eye:
            return WHITE + (255,)
        return None

    def teeth(face_name, x, y, w, h, base):
        if face_name != "north":
            return None
        if y == 0 and x % 2 == 0 and 0 < x < w - 1:
            return WHITE + (255,)
        if y == 1 and x % 2 == 1 and 0 < x < w - 1:
            return WHITE + (255,)
        if y == 2 and x % 2 == 0 and 0 < x < w - 1:
            return WHITE + (255,)
        return (8, 4, 6, 255)

    m.cube("armorHead", [-4.8, 24.4, -4.8], [9.6, 9, 9.6], Callback(suit(2), face))
    m.cube("armorHead", [-5.2, 31.0, -5.2], [10.4, 1.8, 10.4], suit(3))                          # heavy brow ridge
    m.cube("armorHead", [-4.4, 24.0, -6.0], [8.8, 3, 1.4], Callback(suit(4), teeth))           # jaw full of teeth
    m.cube("armorHead", [-1.1, 24.2, -7.4], [2.2, 0.9, 2.2], Skin(RED, noise=4, edge=0.9, seed=5))  # tongue
    m.cube("armorHead", [-1.0, 23.0, -6.4], [2.0, 1.4, 1.0], Skin(RED, noise=4, edge=0.9, seed=6))
    # ---- torso
    def emblem(face_name, x, y, w, h, base):
        if face_name == "north":
            return WHITE + (255,) if SPIDER[y][x] == "#" else (0, 0, 0, 0)
        return None

    m.cube("armorBody", [-6.2, 12, -3.4], [12.4, 12.4, 6.8], suit(7))
    m.cube("armorBody", [-4.5, 16.6, -4.9], [9, 7, 0.6], Callback(suit(21), emblem))            # white spider emblem
    m.cube("armorBody", [-6.0, 17.2, -4.4], [5.9, 5, 1.2], suit(8))                            # pecs
    m.cube("armorBody", [0.1, 17.2, -4.4], [5.9, 5, 1.2], suit(9))
    m.cube("armorBody", [-4.6, 12.2, -4.0], [9.2, 5, 0.8], suit(10, lines=(("v", "north", 0.5), ("h", "north", 0.5))))  # abs
    m.cube("armorBody", [-7.8, 21.2, -3.8], [15.6, 3.4, 7.6], suit(11))                        # broad traps and shoulders
    m.cube("armorBody", [-6.0, 14.0, 3.4], [12.0, 9.6, 1.4], suit(12))                         # back
    # ---- arms (right at -x; the left is mirrored)
    arm = [
        ([-11.2, 18.4, -3.8], [5.2, 6.2, 7.6], 13),     # deltoid
        ([-11.0, 12.0, -3.5], [5.0, 6.8, 7.0], 14),     # biceps
        ([-11.4, 5.6, -3.6], [5.6, 7.2, 7.2], 15),      # forearm
        ([-11.0, 2.4, -3.4], [5.0, 3.4, 6.8], 16),      # fist
    ]
    for origin, size, seed in arm:
        c = m.cube("armorRightArm", origin, size, suit(seed))
        m.mirror_x(c, "armorLeftArm")
    for dx in (-10.6, -9.0, -7.4):                       # claws
        c = m.cube("armorRightArm", [dx, 0.4, -4.1], [1.0, 2.4, 1.0], Skin(WHITE, noise=2, edge=0.9, seed=17))
        m.mirror_x(c, "armorLeftArm")
    # ---- legs
    legs = [
        ([-6.0, 6.2, -3.8], [5.8, 7.2, 7.6], 18),       # thigh
        ([-5.8, 1.4, -3.5], [5.4, 5.6, 7.0], 19),       # shin
        ([-6.0, 0.0, -5.4], [5.8, 1.8, 9.0], 20),       # foot
    ]
    for origin, size, seed in legs:
        c = m.cube("armorRightLeg", origin, size, suit(seed))
        m.mirror_x(c, "armorLeftLeg")
    return m


STAGES = {
    "torso": ("armorBody",),
    "arms": ("armorRightArm", "armorLeftArm"),
    "legs": ("armorRightLeg", "armorLeftLeg"),
    "head": ("armorHead",),
}

if __name__ == "__main__":
    root = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "symbiote")
    os.makedirs(os.path.join(root, "geo"), exist_ok=True)
    os.makedirs(os.path.join(root, "textures", "models"), exist_ok=True)
    model = build()
    model.write(os.path.join(root, "geo", "suit.geo.json"), os.path.join(root, "textures", "models", "suit.png"))
    full = model.geo()
    for stage, bones in STAGES.items():
        g = copy.deepcopy(full)
        for b in g["minecraft:geometry"][0]["bones"]:
            if b["name"] not in bones:
                b.pop("cubes", None)
        g["minecraft:geometry"][0]["description"]["identifier"] = "geometry.symbiote_suit_" + stage
        with open(os.path.join(root, "geo", "suit_%s.geo.json" % stage), "w") as f:
            json.dump(g, f, indent=2)
    print("wrote the symbiote suit model, its four spreading parts and the texture")
