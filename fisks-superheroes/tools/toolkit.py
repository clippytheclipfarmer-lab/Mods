"""
Authoring toolkit for the suit models: describe cubes and how to paint them, and it packs their texture regions, paints
a colour texture and a glow texture, and writes one GeckoLib `.geo.json` per armor slot.

Units are Bedrock units (16 = one block, one unit = one texel). Faces use the vanilla skin names:
north = front, south = back, east = the wearer's right side, west = the wearer's left side, up, down.
"""
import json
import math
import random

from PIL import Image

SIDES = ("north", "south", "east", "west")
ALL_FACES = SIDES + ("up", "down")
FACE_LIGHT = {"up": 1.14, "down": 0.62, "north": 1.0, "south": 0.88, "east": 0.94, "west": 0.94}
CLEAR = (0, 0, 0, 0)


def rgb(h):
    """'#rrggbb' or 'rrggbb' -> (r, g, b)."""
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


# ----------------------------------------------------------------------------- layers
# A layer is a function (face, x, y, w, h) -> colour or None, where w/h are the size of that face in texels.

def band(y0, y1, color, faces=SIDES):
    """Rows y0..y1 (inclusive; negative counts from the bottom) on the given faces."""
    def layer(face, x, y, w, h):
        a, b = (y0 if y0 >= 0 else h + y0), (y1 if y1 >= 0 else h + y1)
        return color if face in faces and a <= y <= b else None
    return layer


def rect(faces, x0, y0, x1, y1, color):
    """Inclusive rectangle on the given face(s); negative coordinates count from the far edge."""
    faces = (faces,) if isinstance(faces, str) else faces

    def layer(face, x, y, w, h):
        if face not in faces:
            return None
        ax, bx = (x0 if x0 >= 0 else w + x0), (x1 if x1 >= 0 else w + x1)
        ay, by = (y0 if y0 >= 0 else h + y0), (y1 if y1 >= 0 else h + y1)
        return color if ax <= x <= bx and ay <= y <= by else None
    return layer


def hole(faces, x0, y0, x1, y1):
    """Cuts a transparent window (lets the wearer's skin show through)."""
    return rect(faces, x0, y0, x1, y1, CLEAR)


def stripes_v(faces, colors, x0=0, x1=-1):
    """Vertical stripes cycling through colours across [x0, x1]."""
    faces = (faces,) if isinstance(faces, str) else faces

    def layer(face, x, y, w, h):
        if face not in faces:
            return None
        a, b = (x0 if x0 >= 0 else w + x0), (x1 if x1 >= 0 else w + x1)
        return colors[(x - a) % len(colors)] if a <= x <= b else None
    return layer


def stripes_h(faces, colors, y0=0, y1=-1, step=1):
    faces = (faces,) if isinstance(faces, str) else faces

    def layer(face, x, y, w, h):
        if face not in faces:
            return None
        a, b = (y0 if y0 >= 0 else h + y0), (y1 if y1 >= 0 else h + y1)
        return colors[((y - a) // step) % len(colors)] if a <= y <= b else None
    return layer


def stamp(face, x0, y0, rows, key):
    """A small bitmap: rows of characters looked up in `key`; '.' and unknown characters leave the texel alone."""
    faces = (face,) if isinstance(face, str) else face

    def layer(f, x, y, w, h):
        if f not in faces:
            return None
        ox = x0 if x0 >= 0 else w + x0
        oy = y0 if y0 >= 0 else h + y0
        row = y - oy
        col = x - ox
        if 0 <= row < len(rows) and 0 <= col < len(rows[row]):
            return key.get(rows[row][col])
        return None
    return layer


def checker(faces, a, b, y0=0, y1=-1, size=1):
    faces = (faces,) if isinstance(faces, str) else faces

    def layer(face, x, y, w, h):
        if face not in faces:
            return None
        lo, hi = (y0 if y0 >= 0 else h + y0), (y1 if y1 >= 0 else h + y1)
        if not lo <= y <= hi:
            return None
        return a if ((x // size) + (y // size)) % 2 == 0 else b
    return layer


def border(color, faces=ALL_FACES, width=1):
    def layer(face, x, y, w, h):
        if face in faces and (x < width or y < width or x >= w - width or y >= h - width):
            return color
        return None
    return layer


class Suit:
    """Cloth/armor surface: base colour, a stack of layers, face shading, a faint weave and optional edge darkening."""

    def __init__(self, base, layers=(), noise=4, edge=0.84, gradient=0.14, seed=1, metal=False):
        self.base = base
        self.layers = list(layers)
        self.noise = noise
        self.edge = edge
        self.gradient = gradient
        self.metal = metal
        self.rng = random.Random(seed)

    def pixel(self, face, x, y, w, h):
        color = self.base
        for layer in self.layers:
            c = layer(face, x, y, w, h)
            if c is not None:
                if len(c) == 4 and c[3] == 0:
                    return CLEAR
                color = c
        f = FACE_LIGHT[face]
        if face in SIDES:
            f *= 1.0 + self.gradient * (0.5 - y / max(1, h - 1))
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            f *= self.edge
        elif self.metal and (x == 1 or y == 1) and face in SIDES:
            f *= 1.12  # bevel highlight
        n = self.rng.randint(-self.noise, self.noise)
        c = shade(color, f)
        return (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255)


class Glow:
    """Emissive texels only (everything else transparent). Takes layers like Suit but paints them unshaded."""

    def __init__(self, layers=()):
        self.layers = list(layers)

    def pixel(self, face, x, y, w, h):
        out = CLEAR
        for layer in self.layers:
            c = layer(face, x, y, w, h)
            if c is not None:
                out = CLEAR if len(c) == 4 and c[3] == 0 else (c[0], c[1], c[2], 255)
        return out


NO_GLOW = Glow()


# ----------------------------------------------------------------------------- cubes and the builder
class Cube:
    def __init__(self, slot, bone, origin, size, painter, glow=None, inflate=0.0, variant=None, rotation=None, pivot=None, mirror=False):
        self.slot, self.bone, self.origin, self.size = slot, bone, list(origin), list(size)
        self.painter, self.glow, self.inflate, self.variant = painter, glow or NO_GLOW, inflate, variant
        self.rotation, self.pivot, self.mirror = rotation, pivot, mirror
        self.uv = (0, 0)

    def region(self):
        w, h, d = (math.ceil(v) for v in self.size)
        return 2 * (d + w), d + h

    def faces(self):
        w, h, d = (math.ceil(v) for v in self.size)
        u, v = self.uv
        return {
            "east": (u, v + d, d, h), "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h),
            "south": (u + d + w + d, v + d, w, h), "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        }


MAIN_BONES = {
    "armorHead": [0, 24, 0],
    "armorBody": [0, 24, 0],
    "armorRightArm": [-5, 22, 0],
    "armorLeftArm": [5, 22, 0],
    "armorRightLeg": [-1.9, 12, 0],
    "armorLeftLeg": [1.9, 12, 0],
}
SLOT_NAMES = ("head", "chest", "legs", "feet")


class SuitModel:
    def __init__(self, identifier, width=128, height=128):
        self.identifier, self.width, self.height = identifier, width, height
        self.bones = {n: {"name": n, "pivot": list(p)} for n, p in MAIN_BONES.items()}
        self.cubes = []

    def bone(self, name, pivot, parent, rotation=None):
        """An extra bone (cape, wings, ...) that follows its parent."""
        self.bones[name] = {"name": name, "pivot": list(pivot), "parent": parent}
        if rotation:
            self.bones[name]["rotation"] = list(rotation)
        return name

    def cube(self, slot, bone, origin, size, painter, glow=None, inflate=0.0, variant=None, rotation=None, pivot=None):
        c = Cube(slot, bone, origin, size, painter, glow, inflate, variant, rotation, pivot)
        self.cubes.append(c)
        return c

    def pack(self):
        x = y = shelf = 0
        for c in sorted(self.cubes, key=lambda c: (-c.region()[1], -c.region()[0])):
            w, h = c.region()
            if x + w > self.width:
                x, y, shelf = 0, y + shelf, 0
            if y + h > self.height:
                raise ValueError("%s: texture %dx%d too small for %d cubes" % (self.identifier, self.width, self.height, len(self.cubes)))
            c.uv = (x, y)
            x += w
            shelf = max(shelf, h)
        return y + shelf

    def _paint(self, attr):
        img = Image.new("RGBA", (self.width, self.height), CLEAR)
        px = img.load()
        for c in self.cubes:
            painter = getattr(c, attr)
            for face, (u, v, w, h) in c.faces().items():
                for yy in range(h):
                    for xx in range(w):
                        px[u + xx, v + yy] = painter.pixel(face, xx, yy, w, h)
        return img

    def textures(self):
        self.pack()
        return self._paint("painter"), self._paint("glow")

    def geo(self, slot, slim):
        """The geometry of one armor slot; the six main bones are always present, even when empty."""
        chosen = [c for c in self.cubes if (slot is None or c.slot == slot) and (c.variant is None or c.variant == ("slim" if slim else "wide"))]
        by_bone = {}
        for c in chosen:
            entry = {"origin": [round(v, 4) for v in c.origin], "size": [round(v, 4) for v in c.size], "uv": list(c.uv)}
            if c.inflate:
                entry["inflate"] = c.inflate
            if c.rotation:
                entry["rotation"] = c.rotation
                entry["pivot"] = c.pivot or self.bones[c.bone]["pivot"]
            by_bone.setdefault(c.bone, []).append(entry)
        # extra bones are kept only if they hold cubes for this slot
        bones = []
        for name, b in self.bones.items():
            if name in MAIN_BONES or name in by_bone:
                bone = dict(b)
                if name in by_bone:
                    bone["cubes"] = by_bone[name]
                bones.append(bone)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "%s_%s%s" % (self.identifier, slot or "all", "_slim" if slim else ""),
                            "texture_width": self.width, "texture_height": self.height,
                            "visible_bounds_width": 4, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.75, 0]},
            "bones": bones}]}


# ----------------------------------------------------------------------------- shared body geometry
def standard_cubes(m, inflate_head=0.5, inflate_body=0.3, inflate_arm=0.3, inflate_leg=0.3, inflate_boot=0.42, boot_h=5):
    """Cube templates for the vanilla body parts; each returns the painters' cubes for the caller to fill in.

    Returns a dict of lambdas taking (painter, glow) for: head, body, arm_r, arm_l, arm_r_slim, arm_l_slim, leg_r, leg_l, boot_r, boot_l.
    """
    def head(p, g=None):
        return m.cube("head", "armorHead", [-4, 24, -4], [8, 8, 8], p, g, inflate_head)

    def body(p, g=None):
        return m.cube("chest", "armorBody", [-4, 12, -2], [8, 12, 4], p, g, inflate_body)

    def arms(p_r, p_l, g_r=None, g_l=None):
        return [
            m.cube("chest", "armorRightArm", [-8, 12, -2], [4, 12, 4], p_r, g_r, inflate_arm, variant="wide"),
            m.cube("chest", "armorLeftArm", [4, 12, -2], [4, 12, 4], p_l, g_l, inflate_arm, variant="wide"),
            m.cube("chest", "armorRightArm", [-7, 12, -2], [3, 12, 4], p_r, g_r, inflate_arm, variant="slim"),
            m.cube("chest", "armorLeftArm", [4, 12, -2], [3, 12, 4], p_l, g_l, inflate_arm, variant="slim"),
        ]

    def legs(p_r, p_l, g_r=None, g_l=None):
        top = 12 - 0  # upper leg runs from y = boot_h to 12
        return [
            m.cube("legs", "armorRightLeg", [-3.9, boot_h, -2], [4, 12 - boot_h, 4], p_r, g_r, inflate_leg),
            m.cube("legs", "armorLeftLeg", [-0.1, boot_h, -2], [4, 12 - boot_h, 4], p_l, g_l, inflate_leg),
        ]

    def boots(p_r, p_l, g_r=None, g_l=None):
        return [
            m.cube("feet", "armorRightLeg", [-3.9, 0, -2], [4, boot_h, 4], p_r, g_r, inflate_boot),
            m.cube("feet", "armorLeftLeg", [-0.1, 0, -2], [4, boot_h, 4], p_l, g_l, inflate_boot),
        ]

    return head, body, arms, legs, boots
