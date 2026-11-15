"""Verkleinert die gerenderten Bilder des 3D-Wichtels auf Handygröße und legt sie in die App-Assets.
Aufruf: python pack3d.py render_ordner ../app/src/main/assets/elf3d [breite]"""
import glob
import json
import os
import sys

from PIL import Image

src, dst = sys.argv[1:3]
new_w = int(sys.argv[3]) if len(sys.argv) > 3 else 360
meta = json.load(open(os.path.join(src, "elf3d.json")))
k = new_w / meta["cell"][0]
new_h = round(meta["cell"][1] * k)
os.makedirs(dst, exist_ok=True)
for f in os.listdir(dst):
    if f.endswith(".png") or f.endswith(".json"):
        os.remove(os.path.join(dst, f))
n = 0
total = 0
for f in sorted(glob.glob(os.path.join(src, "*.png"))):
    im = Image.open(f).convert("RGBA").resize((new_w, new_h), Image.LANCZOS)
    out = os.path.join(dst, os.path.basename(f))
    im.save(out, optimize=True)
    total += os.path.getsize(out)
    n += 1
meta["cell"] = [new_w, new_h]
meta["feet"] = [round(meta["feet"][0] * k, 1), round(meta["feet"][1] * k, 1)]
meta["height"] = round(meta["height"] * k, 1)
json.dump(meta, open(os.path.join(dst, "elf3d.json"), "w"), indent=1)
print(n, "Bilder,", round(total / 1e6, 1), "MB;", meta["cell"], meta["feet"], meta["height"])
