"""
A small toolkit for authoring Minecraft/Bedrock cube models from code: describe cubes and how to paint them, and it
packs their texture regions, paints the texture, and writes a GeckoLib `.geo.json` plus a PNG.

    m = ModelBuilder("geometry.hulk", 128, 128)
    m.bone("armorHead", pivot=[0, 24, 0])
    m.cube("armorHead", [-4.5, 24, -4.5], [9, 8, 9], Skin((60, 150, 50)))
    m.write("hulk.geo.json", "hulk.png")

Units are Bedrock units (16 = one block). One unit is one texel, like vanilla.
"""
import json
import math
import random

from PIL import Image


# ----------------------------------------------------------------------------- painters
def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color[:3]) + ((color[3],) if len(color) > 3 else (255,))


class Painter:
    """Decides the colour of each texel of a cube. `face` is north/south/east/west/up/down."""

    def pixel(self, face, x, y, w, h):
        raise NotImplementedError


FACE_LIGHT = {"up": 1.15, "down": 0.6, "north": 1.0, "south": 0.9, "east": 0.95, "west": 0.95}


class Skin(Painter):
    """Shaded skin: lighter on top, darker underneath, darker edges and light noise.

    `lines` adds dark definition lines on a face: ("v", face, fraction) for a vertical line at that share of the width,
    ("h", face, fraction) for a horizontal line at that share of the height.
    """

    def __init__(self, base, noise=5, edge=0.8, lines=(), seed=1, highlight=None, veins=0):
        self.veins = veins
        self._vein_cells = {}
        self.base = base
        self.noise = noise
        self.edge = edge
        self.lines = lines
        self.highlight = highlight
        self.rng = random.Random(seed)

    def _veins_for(self, face, w, h):
        key = (face, w, h)
        if key not in self._vein_cells:
            cells = set()
            r = random.Random(hash((self.base, face, w, h, self.veins)) & 0xFFFF)
            for _ in range(self.veins if face in ("north", "south", "east", "west") else 0):
                vx, vy = r.randrange(1, max(2, w - 1)), r.randrange(0, max(1, h - 4))
                for k in range(r.randint(3, 5)):
                    cells.add((max(0, min(w - 1, vx + (1 if k % 2 else 0))), min(h - 1, vy + k)))
            self._vein_cells[key] = cells
        return self._vein_cells[key]

    def pixel(self, face, x, y, w, h):
        f = FACE_LIGHT[face]
        if self.veins and (x, y) in self._veins_for(face, w, h):
            f *= 0.8
        # vertical gradient on the sides: a touch lighter at the top
        if face in ("north", "south", "east", "west"):
            f *= 1.08 - 0.18 * (y / max(1, h - 1))
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            f *= self.edge
        for kind, lface, frac in self.lines:
            if lface == face:
                if kind == "v" and x == int(frac * w):
                    f *= 0.7
                if kind == "h" and y == int(frac * h):
                    f *= 0.7
        if self.highlight and face == "north" and y == 1 and 0 < x < w - 1:
            f *= self.highlight
        n = self.rng.randint(-self.noise, self.noise)
        c = shade(self.base, f)
        return (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255)


class Flat(Skin):
    def __init__(self, base, **kw):
        super().__init__(base, **kw)


class Torn(Skin):
    """Cloth that ends in a ragged edge: the bottom rows have random holes on every side face."""

    def __init__(self, base, depth=2, **kw):
        super().__init__(base, **kw)
        self.depth = depth

    def pixel(self, face, x, y, w, h):
        if face in ("north", "south", "east", "west"):
            rows_from_bottom = h - 1 - y
            if rows_from_bottom < self.depth and self.rng.random() < 0.35 + 0.3 * (self.depth - rows_from_bottom) / self.depth:
                return (0, 0, 0, 0)
        return super().pixel(face, x, y, w, h)


class Callback(Painter):
    """Wraps another painter and lets a function override texels: fn(face, x, y, w, h, base_colour) -> colour or None."""

    def __init__(self, inner, fn):
        self.inner = inner
        self.fn = fn

    def pixel(self, face, x, y, w, h):
        base = self.inner.pixel(face, x, y, w, h)
        over = self.fn(face, x, y, w, h, base)
        return base if over is None else over


# ----------------------------------------------------------------------------- the builder
class Cube:
    def __init__(self, bone, origin, size, painter, inflate=0.0, rotation=None, pivot=None):
        self.bone, self.origin, self.size, self.painter = bone, origin, size, painter
        self.inflate, self.rotation, self.pivot = inflate, rotation, pivot
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


class ModelBuilder:
    def __init__(self, identifier, width, height):
        self.identifier, self.width, self.height = identifier, width, height
        self.bones = {}
        self.cubes = []

    def bone(self, name, pivot, parent=None, rotation=None):
        self.bones[name] = {"name": name, "pivot": pivot}
        if parent:
            self.bones[name]["parent"] = parent
        if rotation:
            self.bones[name]["rotation"] = rotation
        return name

    def cube(self, bone, origin, size, painter, inflate=0.0, rotation=None, pivot=None):
        c = Cube(bone, origin, size, painter, inflate, rotation, pivot)
        self.cubes.append(c)
        return c

    def mirror_x(self, cube, to_bone, painter=None):
        """Adds the left/right mirror image of a cube (x -> -x) to another bone."""
        ox, oy, oz = cube.origin
        sx, sy, sz = cube.size
        return self.cube(to_bone, [-(ox + sx), oy, oz], list(cube.size), painter or cube.painter, cube.inflate)

    def pack(self):
        """Shelf-packs every cube's texture region into the texture."""
        x = y = shelf_h = 0
        for c in sorted(self.cubes, key=lambda c: -c.region()[1]):
            w, h = c.region()
            if x + w > self.width:
                x, y, shelf_h = 0, y + shelf_h, 0
            if y + h > self.height:
                raise ValueError("texture %dx%d is too small for the model" % (self.width, self.height))
            c.uv = (x, y)
            x += w
            shelf_h = max(shelf_h, h)

    def paint(self):
        self.pack()
        img = Image.new("RGBA", (self.width, self.height), (0, 0, 0, 0))
        px = img.load()
        for c in self.cubes:
            for face, (u, v, w, h) in c.faces().items():
                for yy in range(h):
                    for xx in range(w):
                        px[u + xx, v + yy] = c.painter.pixel(face, xx, yy, w, h)
        return img

    def geo(self):
        by_bone = {}
        for c in self.cubes:
            entry = {"origin": [round(v, 4) for v in c.origin], "size": [round(v, 4) for v in c.size], "uv": list(c.uv)}
            if c.inflate:
                entry["inflate"] = c.inflate
            if c.rotation:
                entry["rotation"] = c.rotation
                entry["pivot"] = c.pivot or self.bones[c.bone]["pivot"]
            by_bone.setdefault(c.bone, []).append(entry)
        bones = []
        for name, b in self.bones.items():
            bone = dict(b)
            if name in by_bone:
                bone["cubes"] = by_bone[name]
            bones.append(bone)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": self.identifier, "texture_width": self.width, "texture_height": self.height,
                            "visible_bounds_width": 4, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.75, 0]},
            "bones": bones}]}

    def write(self, geo_path, texture_path):
        img = self.paint()
        with open(geo_path, "w") as f:
            json.dump(self.geo(), f, indent=2)
        img.save(texture_path)
