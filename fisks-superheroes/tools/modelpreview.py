#!/usr/bin/env python3
"""
Renders a Minecraft/Bedrock-style cube model (a GeckoLib `.geo.json` plus its texture) to a PNG contact sheet, so a
model can be looked at without launching the game.

    python3 modelpreview.py model.geo.json texture.png out.png [--views front,back,left,right,34front,34back] [--size 360]

It handles bones with pivots, parents and rotations, cubes with box UVs, `inflate` and per-face `uv` dictionaries.
It is a flat-shaded orthographic software renderer: good for judging shape, proportions and texture placement,
not a replacement for seeing the model in game. Needs numpy and Pillow.
"""
import argparse
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw


# ----------------------------------------------------------------------------- transforms
def rot_matrix(rx, ry, rz):
    """Bedrock/GeckoLib bone rotation in degrees. Applied X, then Y, then Z."""
    ax, ay, az = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def about_pivot(rot, pivot):
    """4x4 matrix rotating by `rot` (3x3) around `pivot`."""
    m = np.eye(4)
    m[:3, :3] = rot
    m[:3, 3] = np.array(pivot) - rot @ np.array(pivot)
    return m


# ----------------------------------------------------------------------------- geometry
FACES = ("north", "east", "south", "west", "up", "down")


def box_uv_rects(u, v, w, h, d):
    """Default (box) UV rectangle of each face: (u, v, width, height)."""
    return {
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
    }


def face_corners(face, lo, hi):
    """3D corners (top-left, top-right, bottom-left) of a face as seen from outside, with the texture upright."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    if face == "north":   # normal -z, seen from -z: left is +x
        return (x1, y1, z0), (x0, y1, z0), (x1, y0, z0)
    if face == "south":   # normal +z: left is -x
        return (x0, y1, z1), (x1, y1, z1), (x0, y0, z1)
    if face == "east":    # normal +x: left is +z
        return (x1, y1, z1), (x1, y1, z0), (x1, y0, z1)
    if face == "west":    # normal -x: left is -z
        return (x0, y1, z0), (x0, y1, z1), (x0, y0, z0)
    if face == "up":      # normal +y, top edge is the south side, bottom edge touches the north face
        return (x1, y1, z1), (x0, y1, z1), (x1, y1, z0)
    # down: top edge is the north side
    return (x1, y0, z0), (x0, y0, z0), (x1, y0, z1)


class Quad:
    def __init__(self, tl, tr, bl, uv_rect, tex_size):
        self.tl, self.tr, self.bl = (np.array(p, dtype=float) for p in (tl, tr, bl))
        self.uv = uv_rect
        self.tex_size = tex_size


def load_quads(geo, tex_size_override=None):
    data = geo["minecraft:geometry"][0] if "minecraft:geometry" in geo else geo["geometry"][0]
    desc = data.get("description", {})
    tw = desc.get("texture_width", 64)
    th = desc.get("texture_height", 64)
    if tex_size_override:
        tw, th = tex_size_override
    bones = {b["name"]: b for b in data["bones"]}
    cache = {}

    def bone_matrix(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        m = np.eye(4)
        if b.get("parent") in bones:
            m = bone_matrix(b["parent"])
        r = b.get("rotation", [0, 0, 0])
        if any(r):
            m = m @ about_pivot(rot_matrix(*r), b.get("pivot", [0, 0, 0]))
        cache[name] = m
        return m

    quads = []
    for name, b in bones.items():
        bm = bone_matrix(name)
        for cube in b.get("cubes", []):
            m = bm
            cr = cube.get("rotation")
            if cr and any(cr):
                m = m @ about_pivot(rot_matrix(*cr), cube.get("pivot", b.get("pivot", [0, 0, 0])))
            o = np.array(cube["origin"], dtype=float)
            s = np.array(cube["size"], dtype=float)
            inf = float(cube.get("inflate", b.get("inflate", 0)))
            lo, hi = o - inf, o + s + inf
            uv = cube.get("uv", [0, 0])
            rects = {}
            if isinstance(uv, dict):
                for face, spec in uv.items():
                    rects[face] = (spec["uv"][0], spec["uv"][1], spec["uv_size"][0], spec["uv_size"][1])
            else:
                rects = box_uv_rects(uv[0], uv[1], s[0], s[1], s[2])
            for face, rect in rects.items():
                tl, tr, bl = face_corners(face, lo, hi)
                pts = []
                for p in (tl, tr, bl):
                    v4 = m @ np.array([p[0], p[1], p[2], 1.0])
                    pts.append(v4[:3])
                quads.append(Quad(pts[0], pts[1], pts[2], rect, (tw, th)))
    return quads


# ----------------------------------------------------------------------------- rendering
def view_rotation(yaw, pitch):
    """Rotation applied to the model: yaw about Y, then pitch about X."""
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    return rx @ ry


VIEWS = {
    "front": (0, 0), "back": (180, 0), "left": (90, 0), "right": (-90, 0),
    "34front": (35, 14), "34back": (215, 14), "top": (0, 60),
}


def render(quads, texture, yaw, pitch, size, bounds):
    """Orthographic render of the quads. Camera looks along +z after the view rotation; screen right is -x."""
    img_w, img_h = size
    rgb = np.full((img_h, img_w, 3), (58, 62, 70), dtype=np.uint8)
    zbuf = np.full((img_h, img_w), 1e9)
    tex = np.asarray(texture.convert("RGBA"))
    th, tw = tex.shape[:2]
    R = view_rotation(yaw, pitch)
    (cx, cy, cz), extent = bounds
    scale = 0.9 * min(img_w, img_h) / extent
    light = np.array([0.35, 0.8, -0.6])
    light = light / np.linalg.norm(light)

    def project(p):
        q = R @ (p - np.array([cx, cy, cz]))
        return np.array([img_w / 2 - q[0] * scale, img_h / 2 - q[1] * scale, q[2]])

    xs, ys = np.meshgrid(np.arange(img_w) + 0.5, np.arange(img_h) + 0.5)
    for quad in quads:
        a, b, c = project(quad.tl), project(quad.tr), project(quad.bl)
        # Face normal (towards the outside) for back-face culling and lighting.
        n = np.cross(quad.bl - quad.tl, quad.tr - quad.tl)
        if np.linalg.norm(n) < 1e-9:
            continue
        n = n / np.linalg.norm(n)
        nv = R @ n
        if nv[2] > 0:   # facing away from the camera (camera looks along +z)
            continue
        # Affine map from screen to (s, t) in the face.
        e1, e2 = b[:2] - a[:2], c[:2] - a[:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        minx = int(max(0, math.floor(min(a[0], b[0], c[0], b[0] + e2[0]))))
        maxx = int(min(img_w, math.ceil(max(a[0], b[0], c[0], b[0] + e2[0]))))
        miny = int(max(0, math.floor(min(a[1], b[1], c[1], b[1] + e2[1]))))
        maxy = int(min(img_h, math.ceil(max(a[1], b[1], c[1], b[1] + e2[1]))))
        if minx >= maxx or miny >= maxy:
            continue
        px = xs[miny:maxy, minx:maxx] - a[0]
        py = ys[miny:maxy, minx:maxx] - a[1]
        s = (px * e2[1] - py * e2[0]) / det
        t = (e1[0] * py - e1[1] * px) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not inside.any():
            continue
        depth = a[2] + s * (b[2] - a[2]) + t * (c[2] - a[2])
        u0, v0, uw, vh = quad.uv
        tex_u = np.clip(((u0 + s * uw) / quad.tex_size[0] * tw).astype(int), 0, tw - 1)
        tex_v = np.clip(((v0 + t * vh) / quad.tex_size[1] * th).astype(int), 0, th - 1)
        px_rgba = tex[tex_v, tex_u]
        opaque = px_rgba[..., 3] > 8
        region = zbuf[miny:maxy, minx:maxx]
        write = inside & opaque & (depth < region)
        if not write.any():
            continue
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(n, light)))
        color = (px_rgba[..., :3].astype(float) * shade).clip(0, 255).astype(np.uint8)
        region[write] = depth[write]
        sub = rgb[miny:maxy, minx:maxx]
        sub[write] = color[write]
    return Image.fromarray(rgb)


def model_bounds(quads):
    pts = np.array([p for q in quads for p in (q.tl, q.tr, q.bl, q.tr + q.bl - q.tl)])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    centre = (lo + hi) / 2
    extent = float(np.linalg.norm(hi - lo)) * 0.85
    return (tuple(centre), extent)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("geo")
    ap.add_argument("texture")
    ap.add_argument("out")
    ap.add_argument("--views", default="front,34front,right,back")
    ap.add_argument("--size", type=int, default=360, help="width of one view in pixels (height is 1.25x)")
    ap.add_argument("--under", help="optional second texture drawn first (for example a skin under an armour layer)")
    args = ap.parse_args()

    geo = json.load(open(args.geo))
    quads = load_quads(geo)
    tex = Image.open(args.texture)
    if args.under:
        base = Image.open(args.under).convert("RGBA")
        base.alpha_composite(tex.convert("RGBA"))
        tex = base
    bounds = model_bounds(quads)
    w, h = args.size, int(args.size * 1.25)
    views = args.views.split(",")
    sheet = Image.new("RGB", (w * len(views), h + 22), (30, 32, 38))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(views):
        yaw, pitch = VIEWS[name]
        sheet.paste(render(quads, tex, yaw, pitch, (w, h), bounds), (i * w, 22))
        d.text((i * w + 6, 5), name, fill=(220, 220, 230))
    sheet.save(args.out)
    print("wrote", args.out, sheet.size)


if __name__ == "__main__":
    main()
