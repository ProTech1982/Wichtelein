"""Schneidet die gerenderten Teile zu und schreibt Bilder plus Layout für die App.

python slice_parts.py RENDERORDNER ZIELORDNER_DRAWABLE ZIELORDNER_RAW
(Benötigt opencv-python und numpy.) Koordinaten: x = 750 + X * 1000, y = 1100 - Z * 1000 (Canvas 1500 x 1300).
"""
import json
import os
import sys

import cv2
import numpy as np

src, drawable, raw = sys.argv[1:4]
os.makedirs(drawable, exist_ok=True)
os.makedirs(raw, exist_ok=True)


def px(X, Z):
    return [round(750 + X * 1000, 1), round(1100 - Z * 1000, 1)]


parts = {}
bbox = {}
for name in ["head", "torso", "arml", "armr", "legl", "legr"]:
    img = cv2.imdecode(np.fromfile(os.path.join(src, f"part_{name}.png"), np.uint8), cv2.IMREAD_UNCHANGED)
    a = img[..., 3]
    ys, xs = np.where(a > 6)
    x0, x1, y0, y1 = int(xs.min()), int(xs.max()) + 1, int(ys.min()), int(ys.max()) + 1
    crop = img[y0:y1, x0:x1]
    cv2.imencode(".png", crop, [cv2.IMWRITE_PNG_COMPRESSION, 9])[1].tofile(os.path.join(drawable, f"elf3d_{name}.png"))
    parts[name] = [x0, y0]
    bbox[name] = (x0, y0, x1 - x0, y1 - y0)
    print(name, bbox[name])

# Hautfarbe (für die Lider) aus dem Kopfbild nahe der Nase
head = cv2.imdecode(np.fromfile(os.path.join(src, "part_head.png"), np.uint8), cv2.IMREAD_UNCHANGED)
patch = head[505:512, 716:724, :3].reshape(-1, 3).mean(axis=0)
skin = "#%02X%02X%02X" % (int(patch[2]), int(patch[1]), int(patch[0]))

layout = {
    "feet": [750, 1100],
    "height": 1000,
    "parts": parts,
    "piv": {
        "head": px(0, 0.52),
        "arml": px(-0.112, 0.472),
        "armr": px(0.112, 0.472),
        "legl": px(-0.052, 0.2),
        "legr": px(0.052, 0.2),
        "body": px(0, 0.39),
    },
    "eyes": [px(-0.047, 0.635), px(0.047, 0.635)],
    "eyeW": 70,
    "eyeH": 62,
    "mouth": px(0, 0.563),
    "mouthW": 72,
    "hand": px(0.1395, 0.283),
    "propK": round(1000 / 769, 4),
    "bootRow": int(975 - bbox["legl"][1]),
    "skin": skin,
}
with open(os.path.join(raw, "elf3d_layout.json"), "w", encoding="utf-8") as f:
    json.dump(layout, f, indent=1)
print("Layout geschrieben, Haut", skin, "bootRow", layout["bootRow"])
