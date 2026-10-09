#!/usr/bin/env python3
"""
Builds every suit: geo files, colour + glow textures and the Palladium render layer JSON.

    python3 tools/generate_models.py            # writes into src/main/resources
    python3 tools/generate_models.py --preview /tmp/out   # also renders contact sheets (needs numpy + Pillow)
"""
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from suits import HEROES  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "fiskheroes")
NS = "fiskheroes"
SLOTS = ("head", "chest", "legs", "feet")
BONES = {"head": "armorHead", "body": "armorBody", "right_arm": "armorRightArm", "left_arm": "armorLeftArm",
         "right_leg": "armorRightLeg", "left_leg": "armorLeftLeg"}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def layer(hero, slot, glow):
    model = {"normal": f"{NS}:geo/{hero}/{slot}.geo.json", "slim": f"{NS}:geo/{hero}/{slot}_slim.geo.json"} if slot == "chest" \
        else f"{NS}:geo/{hero}/{slot}.geo.json"
    data = {
        "type": "geckolib:default",
        "model": model,
        "texture": f"{NS}:textures/models/{hero}{'_glow' if glow else ''}.png",
        "bones": BONES,
        "render_full_model_in_first_person": False,
    }
    if glow:
        data["render_type"] = "glow"
    return data


def build(hero, preview_dir=None):
    model = HEROES[hero]()
    color, glow = model.textures()
    os.makedirs(os.path.join(ASSETS, "textures", "models"), exist_ok=True)
    color.save(os.path.join(ASSETS, "textures", "models", f"{hero}.png"))
    glow.save(os.path.join(ASSETS, "textures", "models", f"{hero}_glow.png"))
    for slot in SLOTS:
        write_json(os.path.join(ASSETS, "geo", hero, f"{slot}.geo.json"), model.geo(slot, False))
        if slot == "chest":
            write_json(os.path.join(ASSETS, "geo", hero, "chest_slim.geo.json"), model.geo(slot, True))
        write_json(os.path.join(ASSETS, "palladium", "render_layers", f"{hero}_{slot}.json"), layer(hero, slot, False))
        write_json(os.path.join(ASSETS, "palladium", "render_layers", f"{hero}_{slot}_glow.json"), layer(hero, slot, True))
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        geo = os.path.join(preview_dir, f"{hero}.geo.json")
        write_json(geo, model.geo(None, False))
        tex = os.path.join(preview_dir, f"{hero}.png")
        # preview texture: colour with the glow composited on top
        from PIL import Image
        base = color.copy()
        base.alpha_composite(glow)
        base.save(tex)
        out = os.path.join(preview_dir, f"{hero}_preview.png")
        subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "modelpreview.py"), geo, tex, out,
                        "--views", "front,back,34front,left", "--size", "300"], check=True)
    return model


def claws():
    """Vibranium claws for the Black Panther: three blades under each hand."""
    from toolkit import SuitModel, Suit, Glow, rgb, band
    m = SuitModel("geometry.fiskheroes.claws", 32, 32)
    blade = Suit(rgb("#c9d0dc"), [band(0, 1, rgb("#b57bff"))], metal=True, noise=2, edge=0.9)
    glow = Glow([band(0, 1, rgb("#d9b8ff"))])
    for bone, cx in (("armorRightArm", -6.0), ("armorLeftArm", 6.0)):
        for z in (-1.1, 0.0, 1.1):
            m.cube("chest", bone, [cx - 0.25, 7.4, z - 0.25], [0.5, 4.8, 0.5], blade, glow)
    color, glow_img = m.textures()
    color.alpha_composite(glow_img)
    os.makedirs(os.path.join(ASSETS, "textures", "models"), exist_ok=True)
    color.save(os.path.join(ASSETS, "textures", "models", "claws.png"))
    write_json(os.path.join(ASSETS, "geo", "claws.geo.json"), m.geo(None, False))


if __name__ == "__main__":
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
    wanted = [a for a in sys.argv[1:] if a in HEROES] or list(HEROES)
    for h in wanted:
        build(h, preview)
        print("built", h)
    claws()
    print("built claws")
