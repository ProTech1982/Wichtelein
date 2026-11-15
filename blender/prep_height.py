"""Höhenkarten (Wölbung) für die Wichtel-Teile: je weiter innen im Teil, desto höher.
Aufruf: python prep_height.py teile_ordner ausgabe_ordner"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

src, out = sys.argv[1:3]
os.makedirs(out, exist_ok=True)
for name in ["head", "torso", "arml", "armr", "legl", "legr"]:
    a = np.array(Image.open(os.path.join(src, f"elf_{name}.png")).convert("RGBA"))
    m = a[:, :, 3] > 128
    d = ndi.distance_transform_edt(m)
    d = np.minimum(d, 28.0) / 28.0
    h = np.sqrt(d)                     # Kissen-Form: steil am Rand, flach in der Mitte
    h = ndi.gaussian_filter(h, 2.0)
    Image.fromarray((h * 255).astype(np.uint8), "L").save(os.path.join(out, f"h_{name}.png"))
print("ok")
