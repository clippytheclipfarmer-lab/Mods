#!/usr/bin/env python3
"""
Draws the 16x16 item icons: the four armor pieces of every hero (from shared silhouettes tinted per hero) and the gadgets.

    python3 tools/generate_icons.py [--sheet out.png]
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from toolkit import rgb, shade, mix  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "fiskheroes", "textures", "item")

# P primary, S secondary (trim), A accent, E emblem/eyes, F face area, D outline, L highlight
HELMET = [
    "................",
    "................",
    "....DDDDDDDD....",
    "...DPPPPPPPPD...",
    "..DPLLLLLLLLPD..",
    "..DPPPPPPPPPPD..",
    "..DPPPPPPPPPPD..",
    "..DPPEEPPEEPPD..",
    "..DPPFFFFFFPPD..",
    "..DPPFFFFFFPPD..",
    "..DPPFFSSFFPPD..",
    "..DDDDD..DDDDD..",
    "................",
    "................",
    "................",
    "................",
]
CHEST = [
    "................",
    "..DDDD....DDDD..",
    ".DPPPPDDDDPPPPD.",
    ".DPLPPPPPPPPLPD.",
    ".DPPPPPPPPPPPPD.",
    ".DPPPDPEEPDPPPD.",
    "..DDDDPEEPDDDD..",
    "....DPPEEPPD....",
    "....DPPPPPPD....",
    "....DSSSSSSD....",
    "....DPPPPPPD....",
    "....DPPPPPPD....",
    ".....DDDDDD.....",
    "................",
    "................",
    "................",
]
LEGS = [
    "................",
    "................",
    "...DDDDDDDDDD...",
    "...DSSSSSSSSD...",
    "...DPPPPPPPPD...",
    "...DPLPPPPLPD...",
    "...DPPPDDPPPD...",
    "...DPPPDDPPPD...",
    "...DPPD..DPPD...",
    "...DPPD..DPPD...",
    "...DPSD..DSPD...",
    "...DDDD..DDDD...",
    "................",
    "................",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..DDDD....DDDD..",
    "..DSSD....DSSD..",
    "..DPPD....DPPD..",
    "..DPPD....DPPD..",
    ".DPPPD....DPPPD.",
    ".DPPPPD..DPPPPD.",
    ".DPLPPD..DPPLPD.",
    ".DDDDDD..DDDDDD.",
    "................",
    "................",
    "................",
]
TEMPLATES = {"helmet": HELMET, "chestplate": CHEST, "leggings": LEGS, "boots": BOOTS}

# hero -> palette: P, S, A, E (emblem), F (face area; None leaves the face dark), eyes override
HEROES = {
    "iron_man": dict(P="#a81f1f", S="#e6b53c", E="#7fe9ff", F="#e6b53c", eye="#e5fbff"),
    "captain_america": dict(P="#2a4a8c", S="#b3202b", E="#eef0f6", F="#e0b48f", eye="#2a4a8c"),
    "thor": dict(P="#8a909c", S="#8e1b1c", E="#8fd8ff", F="#e0b48f", eye="#e0b48f"),
    "spider_man": dict(P="#b81c24", S="#203f94", E="#15151b", F="#b81c24", eye="#f4f6ff"),
    "black_panther": dict(P="#23232b", S="#b57bff", E="#c3c9d4", F="#23232b", eye="#eef0f6"),
    "superman": dict(P="#1f4eb0", S="#c8161e", E="#f4c80a", F="#e0b48f", eye="#e0b48f"),
    "batman": dict(P="#4c515e", S="#f2c500", E="#15151b", F="#e0b48f", eye="#eef0f6"),
    "flash": dict(P="#c1121f", S="#ffd23f", E="#ffd23f", F="#e0b48f", eye="#e0b48f"),
}
# Which pieces use the secondary colour as their main colour (legs/boots of some heroes), and per-piece tweaks.
PIECE_OVERRIDES = {
    ("captain_america", "leggings"): dict(S="#4a3223"),
    ("superman", "boots"): dict(P="#c8161e", S="#f4c80a"),
    ("superman", "leggings"): dict(S="#c8161e"),
    ("spider_man", "leggings"): dict(P="#203f94", S="#b81c24"),
    ("spider_man", "boots"): dict(P="#b81c24", S="#203f94"),
    ("iron_man", "boots"): dict(P="#a81f1f", S="#e6b53c"),
    ("thor", "chestplate"): dict(P="#454c5c", S="#4a3223", E="#8fd8ff"),
    ("thor", "leggings"): dict(P="#3a2d26", S="#8a909c"),
    ("thor", "boots"): dict(P="#3d2c20", S="#8a909c"),
    ("captain_america", "boots"): dict(P="#b3202b", S="#eef0f6"),
    ("batman", "boots"): dict(P="#1d2030", S="#4c515e"),
    ("batman", "helmet"): dict(P="#1d2030", S="#4c515e"),
    ("batman", "chestplate"): dict(P="#4c515e", S="#f2c500", E="#15151b"),
    ("black_panther", "boots"): dict(P="#1b1b23", S="#c3c9d4"),
    ("flash", "boots"): dict(P="#c1121f", S="#ffd23f"),
}


def render(template, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    P, S, E = rgb(pal["P"]), rgb(pal["S"]), rgb(pal["E"])
    F = rgb(pal["F"]) if pal.get("F") else shade(P, 0.5)
    colors = {"P": P, "S": S, "E": E, "F": F, "D": shade(P, 0.42), "L": tuple(min(255, int(c * 1.3) + 12) for c in P), "A": S}
    for y, row in enumerate(template):
        for x, ch in enumerate(row):
            if ch in colors:
                img.putpixel((x, y), colors[ch] + (255,))
    return img


def hero_icons():
    os.makedirs(OUT, exist_ok=True)
    for hero, base in HEROES.items():
        for piece, template in TEMPLATES.items():
            pal = dict(base)
            pal.update(PIECE_OVERRIDES.get((hero, piece), {}))
            img = render(template, pal)
            if piece == "helmet" and pal.get("eye"):
                # eyes sit in the "EE" cells of the helmet template
                for x in (5, 6, 9, 10):
                    img.putpixel((x, 7), rgb(pal["eye"]) + (255,))
            img.save(os.path.join(OUT, f"{hero}_{piece}.png"))


# ------------------------------------------------------------------------------------------ gadgets
def put(img, pts, c):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), tuple(c) + (255,))


def shield():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    star = ["..W..", "WWWWW", ".WWW.", ".W.W.", "W...W"]
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r > 7.6:
                continue
            c = rgb("#aab2c0") if r > 6.6 else rgb("#b3202b") if r > 5.0 else rgb("#eef0f6") if r > 3.8 else rgb("#24468c")
            put(img, [(x, y)], c)
    for yy, row in enumerate(star):
        for xx, ch in enumerate(row):
            if ch == "W":
                put(img, [(5 + xx, 5 + yy)], rgb("#eef0f6"))
    put(img, [(3, 3), (4, 2), (2, 4)], rgb("#e8ecf4"))
    img.save(os.path.join(OUT, "vibranium_shield.png"))


def mjolnir():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    steel, dark, light = rgb("#8d95a3"), rgb("#4a505c"), rgb("#d2d8e2")
    for y in range(2, 8):
        for x in range(2, 13):
            put(img, [(x, y)], steel)
    put(img, [(x, 2) for x in range(2, 13)] + [(x, 7) for x in range(2, 13)], dark)
    put(img, [(2, y) for y in range(2, 8)] + [(12, y) for y in range(2, 8)], dark)
    put(img, [(x, 3) for x in range(3, 12)], light)
    put(img, [(4, 4), (4, 5), (10, 4), (10, 5), (7, 4), (7, 5)], dark)
    for y in range(8, 15):
        put(img, [(6, y), (7, y)], rgb("#5b3a24"))
        put(img, [(8, y)], rgb("#3a2314"))
        if y % 2 == 0:
            put(img, [(6, y), (7, y)], rgb("#7a5233"))
    put(img, [(5, 14), (4, 13), (4, 12), (5, 11)], rgb("#c9a227"))
    put(img, [(6, 15), (7, 15)], rgb("#c9a227"))
    img.save(os.path.join(OUT, "mjolnir.png"))


def batarang():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rows = [
        "................",
        "................",
        "..K....KK....K..",
        "..KK..KKKK..KK..",
        "..KKKKKKKKKKKK..",
        "...KKKKKKKKKK...",
        "....KKKKKKKK....",
        ".....KKKKKK.....",
        "......KKKK......",
        ".......KK.......",
        "................",
    ]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "K":
                put(img, [(x, y + 2)], rgb("#23252e"))
    put(img, [(5, 6), (6, 6), (9, 6), (10, 6)], rgb("#5a5f70"))
    put(img, [(7, 7), (8, 7)], rgb("#5a5f70"))
    img.save(os.path.join(OUT, "batarang.png"))


def kryptonite():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    g, gl, gd = rgb("#3fdc5a"), rgb("#b8ffc4"), rgb("#177a2c")
    crystal = [
        ".......G........",
        "......GGG.......",
        "......GLGG..G...",
        ".....GGLGGGGG...",
        "...G.GGGLGGGGG..",
        "..GGGGGGGLGGGGG.",
        "..GGGGGGGGGGGGD.",
        "...GGGGGGGGGGD..",
        "...DGGGGGGGGDD..",
        "....DGGGGGGDD...",
        ".....DDGGGDD....",
        "......DDDD......",
    ]
    for y, row in enumerate(crystal):
        for x, ch in enumerate(row):
            if ch in "GLD":
                put(img, [(x, y + 2)], {"G": g, "L": gl, "D": gd}[ch])
    img.save(os.path.join(OUT, "kryptonite.png"))


def sheet(path):
    names = sorted(f for f in os.listdir(OUT) if f.endswith(".png"))
    cols = 8
    rows = (len(names) + cols - 1) // cols
    img = Image.new("RGBA", (cols * 80, rows * 80), (60, 62, 72, 255))
    for i, n in enumerate(names):
        icon = Image.open(os.path.join(OUT, n)).convert("RGBA").resize((72, 72), Image.NEAREST)
        img.alpha_composite(icon, ((i % cols) * 80 + 4, (i // cols) * 80 + 4))
    img.save(path)


if __name__ == "__main__":
    hero_icons()
    shield()
    mjolnir()
    batarang()
    kryptonite()
    if "--sheet" in sys.argv:
        sheet(sys.argv[sys.argv.index("--sheet") + 1])
    print("icons written to", OUT)
