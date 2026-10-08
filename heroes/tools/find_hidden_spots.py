#!/usr/bin/env python3
"""
Finds secret/hidden spots in a Minecraft world to use for the Infinity Stone hunt.

It reads the world's region files (Anvil format, 1.18+ chunk layout) and looks at block entities: chests, barrels,
shulker boxes, trapped chests, ender chests, spawners, lecterns, brewing stands, decorated pots, command blocks.
A container is a good hiding spot if it is deep under the surface and far from other containers
(dungeon-style loot nobody walks past). Candidates are scored, spread out (at least --spacing blocks apart),
and printed as JSON ready to paste into  config/heroes/stone_hunt_locations.json.

Usage:   python3 find_hidden_spots.py <path to world folder> [--count 10] [--spacing 400] [--dimension overworld]
No third-party packages are needed. Run it on a COPY of the world or with the game closed.
"""
import argparse
import json
import math
import os
import struct
import sys
import zlib

CONTAINERS = {"minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:shulker_box",
              "minecraft:ender_chest", "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper",
              "minecraft:brewing_stand", "minecraft:chiseled_bookshelf", "minecraft:decorated_pot"}
INTERESTING = CONTAINERS | {"minecraft:mob_spawner", "minecraft:lectern", "minecraft:command_block",
                            "minecraft:sign", "minecraft:hanging_sign", "minecraft:suspicious_sand",
                            "minecraft:suspicious_gravel", "minecraft:brushable_block"}


# ----------------------------------------------------------------------------- minimal NBT reader
class Reader:
    def __init__(self, data):
        self.d = data
        self.p = 0

    def take(self, n):
        v = self.d[self.p:self.p + n]
        self.p += n
        return v

    def u8(self): return self.take(1)[0]
    def i16(self): return struct.unpack(">h", self.take(2))[0]
    def i32(self): return struct.unpack(">i", self.take(4))[0]
    def i64(self): return struct.unpack(">q", self.take(8))[0]
    def f32(self): return struct.unpack(">f", self.take(4))[0]
    def f64(self): return struct.unpack(">d", self.take(8))[0]
    def string(self): return self.take(struct.unpack(">H", self.take(2))[0]).decode("utf-8", "replace")


def read_payload(r, t):
    if t == 1: return struct.unpack(">b", r.take(1))[0]
    if t == 2: return r.i16()
    if t == 3: return r.i32()
    if t == 4: return r.i64()
    if t == 5: return r.f32()
    if t == 6: return r.f64()
    if t == 7: return r.take(r.i32())
    if t == 8: return r.string()
    if t == 9:
        et = r.u8()
        n = r.i32()
        return [read_payload(r, et) for _ in range(n)]
    if t == 10:
        out = {}
        while True:
            et = r.u8()
            if et == 0:
                return out
            name = r.string()
            out[name] = read_payload(r, et)
    if t == 11:
        n = r.i32()
        return list(struct.unpack(">%di" % n, r.take(4 * n)))
    if t == 12:
        n = r.i32()
        return list(struct.unpack(">%dq" % n, r.take(8 * n)))
    raise ValueError("bad tag %d" % t)


def parse_nbt(data):
    r = Reader(data)
    t = r.u8()
    r.string()
    return read_payload(r, t)


# ----------------------------------------------------------------------------- region files
def chunks_in_region(path):
    with open(path, "rb") as f:
        header = f.read(8192)
        if len(header) < 8192:
            return
        for i in range(1024):
            entry = struct.unpack(">I", header[i * 4:i * 4 + 4])[0]
            offset, count = entry >> 8, entry & 0xFF
            if offset == 0 or count == 0:
                continue
            f.seek(offset * 4096)
            length, comp = struct.unpack(">IB", f.read(5))
            raw = f.read(length - 1)
            try:
                data = zlib.decompress(raw) if comp == 2 else (raw if comp == 3 else None)
            except zlib.error:
                continue
            if data is None:
                continue
            try:
                yield parse_nbt(data)
            except Exception:
                continue


def surface_heights(chunk):
    """WORLD_SURFACE heightmap -> 256 absolute heights (x + z*16), or None."""
    hm = chunk.get("Heightmaps", {}).get("WORLD_SURFACE")
    if not hm:
        return None
    bits = 9
    per_long = 64 // bits
    min_y = chunk.get("yPos", -4) * 16
    out = []
    for i in range(256):
        word = hm[i // per_long] & 0xFFFFFFFFFFFFFFFF
        value = (word >> ((i % per_long) * bits)) & ((1 << bits) - 1)
        out.append(value + min_y - 1)
    return out


def scan(world, dimension):
    base = {"overworld": "", "nether": "DIM-1", "end": "DIM1"}[dimension]
    region_dir = os.path.join(world, base, "region")
    if not os.path.isdir(region_dir):
        sys.exit("No region folder at %s" % region_dir)
    found = []
    files = sorted(f for f in os.listdir(region_dir) if f.endswith(".mca"))
    for n, name in enumerate(files, 1):
        print("scanning %s (%d/%d)" % (name, n, len(files)), file=sys.stderr)
        for chunk in chunks_in_region(os.path.join(region_dir, name)):
            bes = chunk.get("block_entities") or chunk.get("Level", {}).get("TileEntities") or []
            if not bes:
                continue
            heights = surface_heights(chunk)
            cx, cz = chunk.get("xPos", 0), chunk.get("zPos", 0)
            for be in bes:
                bid = be.get("id", "")
                if bid not in INTERESTING:
                    continue
                x, y, z = be["x"], be["y"], be["z"]
                surface = heights[(x & 15) + (z & 15) * 16] if heights else y + 5
                found.append({"id": bid, "x": x, "y": y, "z": z, "depth": surface - y,
                              "items": (5 if "LootTable" in be else len(be.get("Items", []))) if bid in CONTAINERS else 0})
    return found


def score(spots):
    containers = [s for s in spots if s["id"] in CONTAINERS]
    for s in containers:
        # isolation: distance to the nearest other interesting block entity
        nearest = min((math.dist((s["x"], s["y"], s["z"]), (o["x"], o["y"], o["z"])) for o in spots if o is not s), default=200)
        s["isolation"] = min(nearest, 200)
        # deep under the surface, far from others, a real stash (has items), below sea level is a bonus
        s["score"] = min(max(s["depth"], 0), 60) * 1.5 + s["isolation"] * 0.5 + min(s["items"], 8) * 2 + (10 if s["y"] < 50 else 0)
    return sorted(containers, key=lambda s: -s["score"])


def pick(ranked, count, spacing):
    chosen = []
    for s in ranked:
        if s["depth"] < 8:
            continue  # lying in the open is not hidden
        if all(math.hypot(s["x"] - c["x"], s["z"] - c["z"]) >= spacing for c in chosen):
            chosen.append(s)
        if len(chosen) == count:
            break
    return chosen


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("world")
    ap.add_argument("--count", type=int, default=10)
    ap.add_argument("--spacing", type=int, default=400, help="minimum distance between picked spots (blocks)")
    ap.add_argument("--dimension", default="overworld", choices=["overworld", "nether", "end"])
    args = ap.parse_args()

    spots = scan(args.world, args.dimension)
    ranked = score(spots)
    chosen = pick(ranked, args.count, args.spacing)
    if len(chosen) < args.count:
        print("Only found %d suitable spots (try a smaller --spacing)." % len(chosen), file=sys.stderr)
    out = {"locations": [
        {"id": "spot_%02d" % (i + 1), "x": s["x"], "y": s["y"], "z": s["z"],
         "hint": "%s, %d blocks below the surface%s" % (s["id"].replace("minecraft:", ""), s["depth"], ", unopened loot" if s["items"] == 5 else "")}
        for i, s in enumerate(chosen)]}
    print(json.dumps(out, indent=2))


if __name__ == "__main__":
    main()
