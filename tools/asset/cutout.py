"""Schneidet den Wichtel aus dem Referenzbild (Schachbrett-Hintergrund ohne Alpha) frei.
Aufruf: python cutout.py referenz.avif ausgabe.png"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

im = Image.open(sys.argv[1]).convert("RGB")
a = np.array(im).astype(int)
sat = a.max(2) - a.min(2)
bright = a.min(2)
cand = (sat < 7) & (bright > 196)          # graue/weisse Schachbrettfelder
# Kandidaten mit Rand verbunden = Hintergrund
lab, n = ndi.label(cand)
border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
bg = np.isin(lab, list(border))
fg = ~bg
fg = ndi.binary_opening(fg, iterations=1)
lab2, n2 = ndi.label(fg)
sizes = ndi.sum(fg, lab2, range(1, n2 + 1))
fg = lab2 == (1 + int(np.argmax(sizes)))
fg = ndi.binary_fill_holes(fg)
out = np.dstack([a.astype(np.uint8), (fg * 255).astype(np.uint8)])
Image.fromarray(out, "RGBA").save(sys.argv[2])
print("fg px", int(fg.sum()))
