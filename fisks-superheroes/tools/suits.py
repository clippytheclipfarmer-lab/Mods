"""
The eight suits: geometry plus painters. Each build_* returns a SuitModel; generate.py turns them into geo + textures.

Face sizes (texels), for reference when placing details: head 8x8 on every face; body front/back 8x12, sides 4x12,
top/bottom 8x4; wide arm 4x12 (slim 3x12); upper leg 4x7; boot 4x5. Coordinates are x right, y down, in the face's own
texture (so on 'north' x grows towards the wearer's left). Negative numbers count from the far edge.
"""
from toolkit import *

WHITE = rgb("#eef0f6")
BLACK = rgb("#15151b")
STEEL = rgb("#8a909c")
SILVER = rgb("#c3c9d4")
GOLD = rgb("#e6b53c")
GOLD_D = rgb("#a9791b")
LEATHER = rgb("#4a3223")


def web(faces, cx, cy, gap=3, color=BLACK):
    """Spider web lines: radial spokes and rings around (cx, cy)."""
    faces = (faces,) if isinstance(faces, str) else faces

    def layer(face, x, y, w, h):
        if face not in faces:
            return None
        dx, dy = x - cx, y - cy
        if dx == 0 or dy == 0 or abs(dx) == abs(dy):
            return color if (dx or dy) else None
        ring = round(math.hypot(dx, dy))
        return color if ring % gap == 0 and abs(math.hypot(dx, dy) - ring) < 0.5 else None
    return layer


def mesh(faces, step, color, ox=0, oy=0):
    """A sparse diamond mesh (web-like lines) across the given faces."""
    faces = (faces,) if isinstance(faces, str) else faces

    def layer(face, x, y, w, h):
        if face in faces and ((x + y + ox + oy) % step == 0 or (x - y + ox - oy) % step == 0):
            return color
        return None
    return layer


def scallop(face_list, depth=2):
    """Ragged cape hem: the bottom rows lose every other column."""
    def layer(face, x, y, w, h):
        if face in face_list and y >= h - depth and (x + (h - y)) % 3 == 0:
            return CLEAR
        return None
    return layer


def cape(m, slot_bone, color, color2, length=21, hem=None, glow=None, clasp=None):
    """A cape hanging behind the body from the neck, flared slightly outward."""
    m.bone("cape", [0, 23.5, 2.4], "armorBody", rotation=[7, 0, 0])
    layers = [stripes_v(("north", "south"), [color, color2, color, color], 0, -1)]
    if hem:
        layers.append(hem)
    return m.cube("chest", "cape", [-4.6, 24 - length, 2.35], [9.2, length, 1.0],
                  Suit(color, layers, noise=3, edge=0.9), glow, 0.0)


def belt(m, color, buckle, pouches=None, trim=None, height=2):
    """A belt around the waist (belongs to the legs piece)."""
    layers = [rect("north", 3, 0, 4, height - 1, buckle), rect("north", 3, 0, 4, 0, trim or buckle)]
    if pouches:
        layers.append(pouches)
    return m.cube("legs", "armorBody", [-4, 12, -2], [8, height, 4], Suit(color, layers, metal=True), None, 0.36)


def eyes(color_layers_face="north", lens=WHITE):
    return None


# ============================================================================================ IRON MAN
def build_iron_man():
    RED, RED_D = rgb("#a81f1f"), rgb("#6a1010")
    CYAN, CYAN_L = rgb("#6fe7ff"), rgb("#e5fbff")
    m = SuitModel("geometry.fiskheroes.iron_man")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.55, inflate_body=0.36, inflate_arm=0.34, inflate_leg=0.34, inflate_boot=0.46)

    plate = rgb("#d9a634")
    head(Suit(RED, [
        rect("north", 1, 1, 6, 7, plate),                       # gold faceplate
        rect("north", 0, 0, 7, 0, RED_D),                       # brow
        rect("north", 1, 3, 2, 3, BLACK), rect("north", 5, 3, 6, 3, BLACK),
        rect("north", 3, 3, 4, 4, GOLD_D),                      # nose ridge
        rect("north", 3, 6, 4, 7, BLACK),                       # mouth vent
        rect(("east", "west"), 2, 2, 6, 6, plate), rect(("east", "west"), 3, 3, 5, 5, GOLD_D),
        rect("up", 3, 0, 4, 7, plate),                          # crest
        rect("south", 2, 1, 5, 6, RED_D), rect("south", 3, 2, 4, 5, plate),
    ], metal=True), Glow([rect("north", 1, 3, 2, 3, CYAN_L), rect("north", 5, 3, 6, 3, CYAN_L)]))

    reactor = ["..oo..", ".oGGo.", "oGWWGo", "oGWWGo", ".oGGo.", "..oo.."]
    key = {"o": rgb("#3b3f48"), "G": CYAN, "W": CYAN_L}
    body(Suit(RED, [
        rect("north", 0, 0, 7, 1, plate),
        stamp("north", 1, 2, reactor, key),
        band(9, 9, GOLD_D), band(10, 11, plate),
        rect("north", 2, 8, 5, 8, RED_D), rect("north", 1, 7, 6, 7, RED_D),
        rect("south", 3, 0, 4, 11, RED_D), rect("south", 1, 2, 2, 4, BLACK), rect("south", 5, 2, 6, 4, BLACK),
        band(0, 1, plate, ("east", "west")),
    ], metal=True), Glow([stamp("north", 1, 2, reactor, {"G": CYAN, "W": CYAN_L})]))

    def arm(outer):
        return Suit(RED, [
            band(0, 1, plate, ALL_FACES), band(2, 2, GOLD_D),
            band(6, 6, GOLD_D), band(10, 11, plate), band(9, 9, GOLD_D),
            rect(outer, 1, 3, 2, 5, RED_D),
        ], metal=True)
    arms(arm("east"), arm("west"), Glow([rect("down", 1, 1, 2, 2, CYAN_L)]), Glow([rect("down", 1, 1, 2, 2, CYAN_L)]))

    thigh = lambda: Suit(RED, [band(6, 6, plate), band(5, 5, GOLD_D), rect(("east", "west"), 1, 0, 2, 3, RED_D)], metal=True)
    legs(thigh(), thigh())
    boot = lambda: Suit(RED, [band(0, 1, plate), band(2, 2, GOLD_D), rect("north", 0, 3, 3, 4, plate), band(-1, -1, GOLD_D)], metal=True)
    boots(boot(), boot(), Glow([rect("down", 1, 1, 2, 2, CYAN)]), Glow([rect("down", 1, 1, 2, 2, CYAN)]))
    belt(m, plate, RED_D)
    # back thrusters
    m.cube("chest", "armorBody", [-3.6, 14, 2.3], [2.4, 4.5, 1.6], Suit(rgb("#3b3f48"), [band(0, 0, plate)], metal=True),
           Glow([rect("south", 0, -1, 1, -1, CYAN)]))
    m.cube("chest", "armorBody", [1.2, 14, 2.3], [2.4, 4.5, 1.6], Suit(rgb("#3b3f48"), [band(0, 0, plate)], metal=True),
           Glow([rect("south", 0, -1, 1, -1, CYAN)]))
    return m


# ============================================================================================ CAPTAIN AMERICA
def build_captain_america():
    BLUE, BLUE_D, RED = rgb("#2a4a8c"), rgb("#18305f"), rgb("#b3202b")
    m = SuitModel("geometry.fiskheroes.captain_america")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.45, inflate_body=0.34, inflate_arm=0.32, inflate_leg=0.32, inflate_boot=0.42)

    letter = [".W.", "W.W", "WWW", "W.W"]
    head(Suit(BLUE, [
        hole("north", 1, 3, 6, 7),                              # face shows through
        stamp("north", 2, 0, letter, {"W": WHITE}),
        band(0, 0, BLUE_D, ("north",)),
        rect(("east", "west"), 0, 3, 1, 7, BLUE),
        rect(("east", "west"), 3, 2, 5, 3, WHITE),              # wing emblem
        rect("south", 0, 5, 7, 7, BLUE_D),
    ], edge=0.9), NO_GLOW)
    for sx in (-4.95, 4.25):
        m.cube("head", "armorHead", [sx, 27.5, -1.5], [0.7, 2.6, 3.2], Suit(WHITE, noise=2), None, 0.0)

    star = ["..W..", "WWWWW", ".WWW.", ".W.W.", "W...W"]
    body(Suit(BLUE, [
        stamp("north", 2, 1, star, {"W": WHITE}),
        stripes_h("north", [RED, WHITE], 7, 11),
        band(11, 11, BLUE_D, ("east", "west", "south")),
        rect("south", 1, 0, 2, 6, LEATHER), rect("south", 5, 0, 6, 6, LEATHER), rect("south", 3, 4, 4, 5, rgb("#8a8a92")),
        band(0, 0, BLUE_D, SIDES),
    ]))

    def arm():
        return Suit(BLUE, [band(0, 1, BLUE_D, ALL_FACES), band(7, 7, WHITE), band(8, 11, LEATHER), band(11, 11, rgb("#2f1f15"))])
    arms(arm(), arm())

    leg = lambda: Suit(BLUE, [rect(("east", "west"), 0, 0, 3, 6, BLUE), band(6, 6, BLUE_D)])
    legs(leg(), leg())
    boot = lambda: Suit(RED, [band(0, 0, LEATHER), band(1, 1, WHITE), band(-1, -1, rgb("#2f1f15")), rect(("north",), 0, 3, 3, 3, rgb("#8e1a22"))], noise=3)
    boots(boot(), boot())
    belt(m, LEATHER, rgb("#9a9aa2"), trim=rgb("#c9c9d2"))
    return m


# ============================================================================================ THOR
def build_thor():
    ARMOR, ARMOR_D = rgb("#454c5c"), rgb("#2c3140")
    CAPE, CAPE_D = rgb("#8e1b1c"), rgb("#701415")
    SLEEVE = rgb("#2c3f66")
    m = SuitModel("geometry.fiskheroes.thor")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.5, inflate_body=0.38, inflate_arm=0.34, inflate_leg=0.32, inflate_boot=0.44)

    head(Suit(SILVER, [
        hole("north", 2, 3, 5, 7),                              # open face
        band(0, 1, STEEL, ("north",)), rect("north", 1, 2, 1, 7, SILVER), rect("north", 6, 2, 6, 7, SILVER),
        rect(("east", "west"), 2, 2, 5, 5, STEEL), rect(("east", "west"), 3, 3, 4, 4, GOLD),
        rect("up", 3, 0, 4, 7, STEEL),
        rect("south", 0, 5, 7, 7, STEEL),
    ], metal=True))
    for sx, rot in ((-5.15, 18), (4.45, -18)):
        m.cube("head", "armorHead", [sx, 28.0, -1.4], [0.7, 4.6, 3.0], Suit(SILVER, [band(0, 1, STEEL)], metal=True), None, 0.0, rotation=[0, 0, rot], pivot=[sx + 0.35, 28.0, 0])

    disc = ["oSSo", "SGGS", "SGGS", "oSSo"]
    dkey = {"o": ARMOR_D, "S": SILVER, "G": rgb("#8fd8ff")}
    body(Suit(ARMOR, [
        stamp("north", 0, 2, disc, dkey), stamp("north", 4, 2, disc, dkey),
        stripes_h("north", [ARMOR, ARMOR_D], 7, 11, 2),
        band(0, 0, SILVER, SIDES), band(11, 11, LEATHER),
        rect("south", 0, 0, 7, 1, STEEL),
    ], metal=True), Glow([rect("north", 1, 3, 2, 4, rgb("#8fd8ff")), rect("north", 5, 3, 6, 4, rgb("#8fd8ff"))]))
    arm = lambda: Suit(SLEEVE, [band(0, 2, STEEL, ALL_FACES), band(5, 11, STEEL), stripes_h(SIDES, [STEEL, SILVER], 5, 11, 2), band(11, 11, LEATHER)], metal=True)
    arms(arm(), arm())
    leg = lambda: Suit(rgb("#3a2d26"), [band(5, 6, STEEL), band(4, 4, LEATHER)])
    legs(leg(), leg())
    boot = lambda: Suit(rgb("#3d2c20"), [band(0, 1, STEEL), band(-1, -1, rgb("#25190f")), stripes_v("north", [LEATHER, rgb("#302013")], 0, 3)])
    boots(boot(), boot())
    belt(m, LEATHER, SILVER, trim=GOLD)
    cape(m, "cape", CAPE, CAPE_D, length=21, hem=scallop(("north", "south"), 1))
    for sx in (-4.5, 3.1):
        m.cube("chest", "armorBody", [sx, 21.3, -2.45], [1.4, 1.4, 0.7], Suit(SILVER, metal=True), None, 0.0)
    return m


# ============================================================================================ SPIDER-MAN
def build_spider_man():
    RED, BLUE = rgb("#b81c24"), rgb("#203f94")
    WEB, WEB_B = rgb("#5a0f16"), rgb("#16286a")
    m = SuitModel("geometry.fiskheroes.spider_man")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.4, inflate_body=0.28, inflate_arm=0.26, inflate_leg=0.26, inflate_boot=0.36)
    lens = rgb("#f4f6ff")
    lens_l, lens_r = ["WW", "WW"], ["WW", "WW"]
    eye = [stamp("north", 1, 3, lens_l, {"W": lens}), stamp("north", 5, 3, lens_r, {"W": lens})]
    head(Suit(RED, [
        mesh(("north", "east", "west", "south", "up"), 6, WEB),
        rect("north", 0, 2, 7, 5, BLACK), rect("north", 0, 2, 0, 5, RED), rect("north", 7, 2, 7, 5, RED),
        rect("north", 3, 2, 4, 5, RED), *eye,
    ], noise=3, edge=0.88), Glow([stamp("north", 1, 3, lens_l, {"W": WHITE}), stamp("north", 5, 3, lens_r, {"W": WHITE})]))

    spider = [".K...K.", "..K.K..", "K.KKK.K", "..KKK..", ".K.K.K.", "K.....K"]
    body(Suit(RED, [
        mesh(("north", "south", "east", "west"), 6, WEB),
        rect("north", 0, 0, 7, 0, RED), stamp("north", 0, 1, spider, {"K": BLACK}),
        rect(("east", "west"), 0, 5, 3, 11, BLUE), rect("north", 0, 7, 1, 11, BLUE), rect("north", 6, 7, 7, 11, BLUE),
        stripes_h("north", [BLUE, rgb("#1a3380")], 11, 11),
    ], noise=3, edge=0.88))
    arm = lambda: Suit(RED, [mesh(SIDES, 6, WEB), band(9, 11, RED), rect("up", 0, 0, 3, 3, RED)], noise=3, edge=0.9)
    arms(arm(), arm())
    leg = lambda: Suit(BLUE, [mesh(SIDES, 6, WEB_B), band(0, 0, RED, SIDES)], noise=3, edge=0.9)
    legs(leg(), leg())
    boot = lambda: Suit(RED, [band(0, 0, BLUE), mesh(SIDES, 6, rgb("#7a1218"))], noise=3, edge=0.88)
    boots(boot(), boot())
    belt(m, BLUE, RED, trim=RED)
    return m


# ============================================================================================ BLACK PANTHER
def build_black_panther():
    SUITB, SUITB2 = rgb("#1b1b23"), rgb("#272731")
    VIB, VIB_L = rgb("#b57bff"), rgb("#e9d6ff")
    m = SuitModel("geometry.fiskheroes.black_panther")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.45, inflate_body=0.3, inflate_arm=0.28, inflate_leg=0.28, inflate_boot=0.38)
    slit = [rect("north", 1, 3, 2, 3, WHITE), rect("north", 5, 3, 6, 3, WHITE)]
    head(Suit(SUITB, [
        rect("north", 1, 2, 2, 2, SILVER), rect("north", 5, 2, 6, 2, SILVER), rect("north", 3, 4, 4, 5, SUITB2),
        rect("north", 1, 6, 6, 6, SILVER), rect("north", 3, 5, 4, 7, SILVER),                      # chin line
        rect(("east", "west"), 2, 4, 5, 4, SILVER), rect("up", 3, 0, 4, 7, SILVER),
        *slit,
    ], noise=3, metal=True), Glow(list(slit)))
    for sx, rot in ((-3.9, 8), (2.2, -8)):
        m.cube("head", "armorHead", [sx, 32.0, -1.0], [1.7, 2.6, 1.3], Suit(SUITB, [rect("north", 0, 0, 1, 1, SILVER)], metal=True),
               None, 0.0, rotation=[0, 0, rot], pivot=[sx + 0.85, 32.0, 0])

    neck = ["VVVVVVVV", ".VVVVVV."]
    body(Suit(SUITB, [
        band(0, 1, SILVER), stamp("north", 0, 2, ["V......V", ".V....V.", "..V..V..", "...VV..."], {"V": VIB}),
        stripes_h("north", [SUITB, SUITB2], 6, 11), rect("north", 3, 5, 4, 11, SUITB2),
        band(0, 1, SILVER, ("south",)), rect("south", 3, 2, 4, 9, SUITB2),
    ], metal=True, noise=3), Glow([stamp("north", 0, 2, ["V......V", ".V....V.", "..V..V..", "...VV..."], {"V": VIB_L})]))
    arm = lambda: Suit(SUITB, [band(0, 1, SUITB2, ALL_FACES), band(6, 7, SILVER), band(8, 11, SUITB2), band(11, 11, SILVER)], metal=True, noise=3)
    arms(arm(), arm(), Glow([band(7, 7, VIB, ("east", "west", "north", "south"))]), Glow([band(7, 7, VIB, ("east", "west", "north", "south"))]))
    leg = lambda: Suit(SUITB, [band(5, 6, SUITB2), rect(("east", "west"), 1, 0, 2, 5, SUITB2)], noise=3)
    legs(leg(), leg())
    boot = lambda: Suit(SUITB, [band(0, 0, SILVER), band(-1, -1, SUITB2), rect("north", 1, 2, 2, 3, SUITB2)], noise=3, metal=True)
    boots(boot(), boot(), Glow([band(1, 1, VIB_L, ("east", "west"))]), Glow([band(1, 1, VIB_L, ("east", "west"))]))
    belt(m, SUITB2, SILVER, trim=VIB)
    return m


# ============================================================================================ SUPERMAN
def build_superman():
    BLUE, RED, YEL = rgb("#1f4eb0"), rgb("#c8161e"), rgb("#f4c80a")
    HAIR = rgb("#14141a")
    m = SuitModel("geometry.fiskheroes.superman")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.38, inflate_body=0.3, inflate_arm=0.26, inflate_leg=0.28, inflate_boot=0.4)

    head(Suit(HAIR, [hole("north", 0, 2, 7, 7), band(0, 1, HAIR, ("north",)), rect("north", 0, 2, 0, 3, HAIR), rect("north", 7, 2, 7, 3, HAIR),
                     hole(("east", "west"), 2, 3, 7, 7), band(0, 2, HAIR, ("east", "west", "south", "up")), rect("south", 0, 0, 7, 7, HAIR)],
                edge=0.92, noise=5), NO_GLOW)
    m.cube("head", "armorHead", [0.4, 30.4, -4.8], [1.1, 1.5, 0.9], Suit(HAIR, noise=3), None, 0.0)    # the curl

    shield = ["YYYYYYY", "YRRRRRY", "YRYYYRY", "YRYRRRY", "YRYYYRY", "YRRRYRY", "YRYYYRY", ".YRRRY.", "..YRY.."]
    body(Suit(BLUE, [
        stamp("north", 0, 0, shield, {"Y": YEL, "R": RED}),
        stripes_h("north", [BLUE, rgb("#1a45a0")], 9, 11),
        band(11, 11, BLUE, ("north", "south", "east", "west")),
        rect("south", 3, 0, 4, 11, rgb("#1a45a0")),
    ], noise=3, edge=0.9))
    arm = lambda: Suit(BLUE, [band(0, 1, rgb("#1a45a0"), ALL_FACES), band(10, 11, rgb("#1a45a0"))], noise=3, edge=0.92)
    arms(arm(), arm())
    leg = lambda: Suit(BLUE, [band(0, 2, RED), rect(("east", "west"), 1, 3, 2, 6, rgb("#1a45a0"))], noise=3, edge=0.92)
    legs(leg(), leg())
    boot = lambda: Suit(RED, [band(0, 0, YEL), band(-1, -1, rgb("#7b0d12")), rect("north", 1, 2, 2, 3, rgb("#a8121a"))], noise=3)
    boots(boot(), boot())
    belt(m, YEL, YEL, trim=rgb("#fff0a0"))
    cape(m, "cape", RED, rgb("#a8121a"), length=22, hem=stamp("south", 1, 3, shield, {"Y": YEL, "R": RED}))
    return m


# ============================================================================================ BATMAN
def build_batman():
    GRAY, GRAY_D, COWL = rgb("#4c515e"), rgb("#363a45"), rgb("#1d2030")
    YEL = rgb("#f2c500")
    m = SuitModel("geometry.fiskheroes.batman")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.45, inflate_body=0.3, inflate_arm=0.28, inflate_leg=0.28, inflate_boot=0.42)
    slits = [rect("north", 1, 3, 2, 3, WHITE), rect("north", 5, 3, 6, 3, WHITE)]
    head(Suit(COWL, [hole("north", 1, 5, 6, 7), band(0, 0, GRAY_D, ("north",)), *slits,
                     rect("north", 0, 5, 0, 7, COWL), rect("north", 7, 5, 7, 7, COWL), rect(("east", "west"), 0, 5, 1, 7, COWL),
                     rect("up", 2, 2, 5, 5, GRAY_D), hole(("east", "west"), 2, 6, 7, 7)], noise=3, edge=0.9), Glow(list(slits)))
    for sx, rot in ((-3.6, 9), (1.9, -9)):
        m.cube("head", "armorHead", [sx, 32.0, -1.2], [1.7, 3.8, 1.3], Suit(COWL, [band(0, 0, GRAY_D)], noise=2), None, 0.0, rotation=[0, 0, rot], pivot=[sx + 0.85, 32.0, 0])

    oval = [".YYYYY.", "YYYYYYY", "YYYYYYY", ".YYYYY."]
    bat = ["K.K.K", "KKKKK", ".K.K."]
    body(Suit(GRAY, [
        stamp("north", 0, 2, oval, {"Y": YEL}), stamp("north", 1, 3, bat, {"K": BLACK}),
        stripes_h("north", [GRAY, GRAY_D], 7, 11, 2),
        band(0, 1, COWL, SIDES), rect("south", 3, 0, 4, 11, GRAY_D),
    ], noise=3, metal=False))
    arm = lambda: Suit(COWL, [band(0, 1, GRAY_D, ALL_FACES), band(5, 11, rgb("#14161f")), band(6, 6, GRAY_D), band(9, 11, BLACK)], noise=2)
    arms(arm(), arm())
    for sign in (-1, 1):                                   # gauntlet fins
        for k in range(3):
            x0 = -9.35 if sign < 0 else 8.65
            m.cube("chest", "armorRightArm" if sign < 0 else "armorLeftArm", [x0 + (0.0), 16.2 + k * 1.5, -1.2], [0.7, 0.5, 2.4],
                   Suit(rgb("#14161f"), noise=2), None, 0.0, rotation=[0, 0, -sign * 25], pivot=[x0 + 0.35, 16.2 + k * 1.5, 0])
    leg = lambda: Suit(GRAY, [band(5, 6, GRAY_D), rect(("east", "west"), 0, 0, 3, 4, GRAY_D)], noise=3)
    legs(leg(), leg())
    boot = lambda: Suit(COWL, [band(0, 0, GRAY_D), band(-1, -1, BLACK), band(1, 1, rgb("#14161f"))], noise=2, metal=True)
    boots(boot(), boot())
    pouches = stripes_v("north", [YEL, YEL, rgb("#9c7d00"), rgb("#d6ac00")], 0, -1)
    belt(m, YEL, rgb("#fff0a0"), height=2, pouches=pouches, trim=rgb("#fff0a0"))
    for sx in (-3.9, -1.9, 1.5, 3.4):
        m.cube("legs", "armorBody", [sx - 0.5, 12.0, -2.75], [1.2, 1.6, 0.9], Suit(rgb("#b99300")), None, 0.0)
    cape(m, "cape", rgb("#15151d"), rgb("#1c2540"), length=22, hem=scallop(("north", "south"), 3))
    return m


# ============================================================================================ THE FLASH
def build_flash():
    RED, RED_D = rgb("#c1121f"), rgb("#8a0c16")
    YEL = rgb("#ffd23f")
    bolt = [".YYY.", ".YY..", "YYYY.", "..YY.", "..Y.."]
    m = SuitModel("geometry.fiskheroes.flash")
    head, body, arms, legs, boots = standard_cubes(m, inflate_head=0.4, inflate_body=0.28, inflate_arm=0.25, inflate_leg=0.26, inflate_boot=0.38)
    head(Suit(RED, [hole("north", 1, 3, 6, 7), band(0, 2, RED, ("north",)),
                    rect(("east", "west"), 0, 3, 1, 7, RED), rect(("east", "west"), 3, 2, 5, 4, YEL),
                    rect("south", 0, 0, 7, 7, RED_D), rect("up", 3, 0, 4, 7, RED_D)], noise=3, edge=0.9),
         Glow([rect(("east", "west"), 3, 2, 5, 4, YEL)]))
    for sx in (-4.95, 4.25):
        m.cube("head", "armorHead", [sx, 27.2, -1.6], [0.7, 3.0, 3.4], Suit(WHITE, [rect("east", 0, 0, 3, 2, YEL), rect("west", 0, 0, 3, 2, YEL)], noise=2), Glow([rect("east", 0, 0, 3, 2, YEL), rect("west", 0, 0, 3, 2, YEL)]), 0.0)

    body(Suit(RED, [
        stamp("north", 1, 1, [".WWWWW.", "WWWWWWW", "WWWWWWW", "WWWWWWW", "WWWWWWW", ".WWWWW."], {"W": WHITE}),
        stamp("north", 2, 2, bolt, {"Y": YEL}),
        band(8, 8, YEL), band(9, 11, RED_D),
        rect("south", 3, 0, 4, 11, RED_D), band(0, 0, YEL, ("south",)),
    ], noise=3, edge=0.9), Glow([stamp("north", 2, 2, bolt, {"Y": YEL})]))
    arm = lambda: Suit(RED, [band(0, 1, RED_D, ALL_FACES), band(9, 10, YEL), band(5, 5, YEL, ("east", "west"))], noise=3, edge=0.9)
    arms(arm(), arm(), Glow([band(9, 10, YEL)]), Glow([band(9, 10, YEL)]))
    zig = ["Y.", ".Y", "Y.", ".Y", "Y.", ".Y", "Y."]
    leg = lambda: Suit(RED, [stamp(("east", "west"), 1, 0, zig, {"Y": YEL}), band(6, 6, RED_D)], noise=3, edge=0.9)
    legs(leg(), leg(), Glow([stamp(("east", "west"), 1, 0, zig, {"Y": YEL})]), Glow([stamp(("east", "west"), 1, 0, zig, {"Y": YEL})]))
    boot = lambda: Suit(RED, [band(0, 0, YEL), band(1, 1, RED_D), rect("north", 1, 2, 2, 4, YEL), band(-1, -1, RED_D)], noise=3, edge=0.9)
    boots(boot(), boot(), Glow([band(0, 0, YEL)]), Glow([band(0, 0, YEL)]))
    for sx in (-4.6, 3.9):
        m.cube("feet", "armorRightLeg" if sx < 0 else "armorLeftLeg", [sx, 1.5, -1.5], [0.7, 2.4, 3.0],
               Suit(WHITE, noise=2), Glow([rect("east", 0, 0, 2, 1, YEL), rect("west", 0, 0, 2, 1, YEL)]), 0.0)
    belt(m, YEL, WHITE, trim=WHITE)
    return m


HEROES = {
    "iron_man": build_iron_man,
    "captain_america": build_captain_america,
    "thor": build_thor,
    "spider_man": build_spider_man,
    "black_panther": build_black_panther,
    "superman": build_superman,
    "batman": build_batman,
    "flash": build_flash,
}
