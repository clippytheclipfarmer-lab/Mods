#!/usr/bin/env python3
"""Draws the 16x16 item icons for the Cosmi-Rod and the Double-Edged Sword (needs Pillow)."""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "heroes", "textures", "item")


def put(img, pixels, color):
    for x, y in pixels:
        img.putpixel((x, y), color)


def cosmi_rod():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    gold, gold_dark, gold_light = (232, 180, 40, 255), (150, 105, 20, 255), (255, 232, 130, 255)
    gem, gem_light, gem_dark = (150, 70, 235, 255), (215, 170, 255, 255), (85, 30, 150, 255)
    # shaft, diagonal from bottom-left to the head
    for i in range(10):
        put(img, [(1 + i, 14 - i)], gold)
        put(img, [(2 + i, 14 - i)], gold_dark)
        put(img, [(1 + i, 13 - i)], gold_light if i % 3 == 0 else gold)
    # head: a socket ring with a gem in it
    ring = [(10, 3), (11, 2), (12, 2), (13, 3), (13, 4), (13, 5), (12, 6), (11, 6), (10, 5), (10, 4)]
    put(img, ring, gold)
    put(img, [(11, 3), (12, 3), (11, 4), (12, 4), (11, 5), (12, 5)], gem)
    put(img, [(11, 3), (12, 3)], gem_light)
    put(img, [(12, 5), (11, 5)], gem_dark)
    put(img, [(10, 2), (14, 2), (14, 6), (10, 6)], gold_dark)
    put(img, [(0, 15), (1, 15), (0, 14)], gold_dark)
    img.save(os.path.join(OUT, "cosmi_rod.png"))


def sword():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    blade, blade_dark, edge = (70, 60, 90, 255), (35, 28, 50, 255), (190, 120, 255, 255)
    guard, grip, pommel = (150, 40, 40, 255), (70, 40, 25, 255), (200, 160, 50, 255)
    # blade, diagonal; edges glow on both sides (the "double edge")
    for i in range(10):
        x, y = 5 + i, 10 - i
        put(img, [(x, y)], blade)
        put(img, [(x - 1, y)], edge)
        put(img, [(x + 1, y)], blade_dark)
        put(img, [(x, y - 1)], edge if i % 2 == 0 else blade)
    put(img, [(14, 1), (15, 0), (15, 1), (14, 0)], edge)
    # crossguard
    put(img, [(3, 10), (4, 11), (5, 12), (6, 13), (2, 9), (7, 14), (4, 9), (3, 12), (2, 13)], guard)
    # grip and pommel
    put(img, [(2, 12), (1, 13), (1, 14)], grip)
    put(img, [(0, 14), (0, 15), (1, 15)], pommel)
    img.save(os.path.join(OUT, "double_edged_sword.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    cosmi_rod()
    sword()
