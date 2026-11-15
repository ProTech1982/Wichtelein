"""Schneidet den freigestellten Wichtel (cutout.py) in animierbare Teile und den Besen aus dem Besenfoto.
Aufruf: python make_parts.py wichtel_frei.png besen.jpg ausgabe_drawable ausgabe_raw [vorschau_ordner]"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

src, broom_src, out_draw, out_raw = sys.argv[1:5]
prev = sys.argv[5] if len(sys.argv) > 5 else None

im = Image.open(src).convert("RGBA")
a = np.array(im).astype(np.float32) / 255.0
H, W = a.shape[:2]
rgb = a[:, :, :3]
alpha = a[:, :, 3] > 0.5
mx, mn = rgb.max(2), rgb.min(2)
sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
d = np.maximum(mx - mn, 1e-6)
hue = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60.0
yy, xx = np.mgrid[0:H, 0:W]

# --- Aufräumen: Bodenschatten und Schachbrettreste unter dem Wichtel entfernen
grayish = (sat < 0.16) & (mn > 0.55)
alpha &= ~(grayish & (yy >= 622))
alpha &= ~((yy >= 600) & (xx > 356) & (xx < 382) & (sat < 0.22) & (mn > 0.5))
lab, n = ndi.label(alpha)
if n > 1:
    sizes = ndi.sum(alpha, lab, range(1, n + 1))
    alpha = lab == (1 + int(np.argmax(sizes)))
# Rand: 1 px abtragen (Schachbrett-Saum); Farben am Rand von innen auffüllen (kein heller Saum)
core = ndi.binary_erosion(alpha, iterations=1)
inner = ndi.binary_erosion(core, iterations=2)
idx = ndi.distance_transform_edt(~inner, return_distances=False, return_indices=True)
filled = rgb[idx[0], idx[1]]
edge = core & ~inner
rgb2 = np.where(edge[:, :, None], filled, rgb)

# kleine Lücke zwischen den Beinen (Schachbrettrest) mit der Umgebung füllen
patch = (xx >= 352) & (xx <= 374) & (yy >= 594) & (yy <= 612) & ~core
idx2 = ndi.distance_transform_edt(~core, return_distances=False, return_indices=True)
rgb2 = np.where(patch[:, :, None], rgb2[idx2[0], idx2[1]], rgb2)
core = core | patch

whitish = (mn > 0.66) & (sat < 0.2)
red = ((hue <= 22) | (hue >= 338)) & (sat > 0.45) & (mx > 0.25)
green = (hue >= 90) & (hue <= 200) & (sat > 0.2)
brown = (hue >= 8) & (hue <= 65) & (sat > 0.15) & (mx > 0.12)


def arm_mask(x0, x1):
    """Rote Manschette/Fäustling und der grüne Ärmel darüber."""
    box = (xx >= x0) & (xx <= x1) & (yy >= 410) & (yy <= 566)
    m = core & box & ~whitish & (red | (green & (yy < 482)))
    m = ndi.binary_closing(m, iterations=2) & core & box & ~whitish
    return ndi.binary_fill_holes(m)


arm_l = arm_mask(176, 250)
arm_r = arm_mask(480, 556)

# Beine: Hose und Stiefel unterhalb des Mantelsaums, getrennt links/rechts
legs_all = core & (yy >= 596) & ~whitish & (brown | (yy >= 612)) & ~green
legs_all = ndi.binary_opening(legs_all, iterations=1)
leg_l = legs_all & (xx < 369)
leg_r = legs_all & (xx >= 369)

# Kopf: Mütze, Gesicht, Schnurrbart; Rumpf: Bart und Mantel (reicht unter den Kopf, damit beim Nicken keine Lücke entsteht)
head = core & (yy < 412) & ~arm_l & ~arm_r
torso = core & (yy >= 366) & ~arm_l & ~arm_r & ~leg_l & ~leg_r & ~red


def largest(m):
    lab_, n_ = ndi.label(m)
    if n_ <= 1:
        return m
    sz = ndi.sum(m, lab_, range(1, n_ + 1))
    return lab_ == (1 + int(np.argmax(sz)))


torso = largest(torso)
head = largest(head)
leg_l = largest(leg_l)
leg_r = largest(leg_r)
head, torso, leg_l, leg_r = [ndi.binary_fill_holes(m) for m in (head, torso, leg_l, leg_r)]

parts = {"head": head, "torso": torso, "arml": arm_l, "armr": arm_r, "legl": leg_l, "legr": leg_r}
layout_parts = {}
os.makedirs(out_draw, exist_ok=True)
os.makedirs(out_raw, exist_ok=True)
imgs = {}
for name, m in parts.items():
    ys, xs = np.where(m)
    pad = 3
    y0 = max(0, ys.min() - pad)
    x0 = max(0, xs.min() - pad)
    y1 = min(H, ys.max() + 1 + pad)
    x1 = min(W, xs.max() + 1 + pad)
    al = ndi.gaussian_filter(m.astype(np.float32), 0.6)[y0:y1, x0:x1]
    imgs[name] = [rgb2[y0:y1, x0:x1].copy(), al, x0, y0]

# Beine: Hose nach oben verlängern (liegt hinter dem Mantel), damit beim Schwingen keine Lücke bleibt
for nm in ("legl", "legr"):
    col, al, x0, y0 = imgs[nm]
    top = int(np.where(al.max(1) > 0.5)[0].min())
    row = al[top + 1] > 0.5
    cols = np.where((al[top:top + 12] > 0.5).any(0))[0]
    c = col[top + 1][row].mean(0)
    ext = 34
    ncol = np.zeros((col.shape[0] + ext, col.shape[1], 3), np.float32)
    nal = np.zeros((al.shape[0] + ext, al.shape[1]), np.float32)
    ncol[ext:] = col
    nal[ext:] = al
    c0, c1 = cols.min(), cols.max() + 1
    ncol[:ext + top + 1, c0:c1] = np.where(nal[:ext + top + 1, c0:c1, None] > 0.2, ncol[:ext + top + 1, c0:c1], c)
    nal[:ext + top + 1, c0:c1] = 1.0
    imgs[nm] = [ncol, nal, x0, y0 - ext]

for name, (col, al, x0, y0) in imgs.items():
    out = np.dstack([col, al])
    Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(out_draw, f"elf_{name}.png"))
    layout_parts[name] = [int(x0), int(y0)]

# --- Besen aus dem Foto
bi = Image.open(broom_src).convert("RGB")
ba = np.array(bi).astype(np.float32) / 255
fg = (1 - ba.min(2)) > 0.09
fg = ndi.binary_closing(fg, iterations=4)
fg = ndi.binary_fill_holes(fg)
lab, n = ndi.label(fg)
sizes = ndi.sum(fg, lab, range(1, n + 1))
fg = lab == (1 + int(np.argmax(sizes)))
fg = ndi.binary_opening(fg, iterations=2)
fg = ndi.binary_erosion(fg, iterations=1)
ys, xs = np.where(fg)
y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
sm = ndi.gaussian_filter(fg.astype(np.float32), 1.0)[y0:y1, x0:x1]
bo = np.dstack([ba[y0:y1, x0:x1], sm])
b_img = Image.fromarray((bo * 255 + .5).astype(np.uint8), "RGBA")
bh = 700
b_img = b_img.resize((int(b_img.width * bh / b_img.height), bh), Image.LANCZOS)
b_img.save(os.path.join(out_draw, "prop_broom.png"))
top_cols = np.where(np.array(b_img)[:int(bh * 0.4), :, 3].max(0) > 128)[0]
handle_x = float(top_cols.mean() / b_img.width)

layout = {
    "feet": [365, 658], "height": 596,
    "parts": layout_parts,
    "piv": {"head": [365, 402], "arml": [226, 432], "armr": [504, 432], "legl": [315, 598], "legr": [425, 598], "body": [365, 540]},
    "eyes": [], "eyeW": 0, "eyeH": 0,
    "mouth": [365, 385], "mouthW": 0,
    "hand": [522, 533], "propK": 0.78, "bootRow": 0,
    "shadowW": 0.62,
    "skin": "#F1C3A3",
    "broom": {"h": 330, "aspect": b_img.width / b_img.height, "gripFromBottom": 0.37, "handleX": handle_x},
}
layout["bootRow"] = 619 - min(layout_parts["legl"][1], layout_parts["legr"][1])
json.dump(layout, open(os.path.join(out_raw, "elf_layout.json"), "w"), indent=1)
print("fertig", layout["bootRow"], layout["broom"])

if prev:
    os.makedirs(prev, exist_ok=True)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for name in ["legl", "legr", "torso", "arml", "armr", "head"]:
        canvas.alpha_composite(Image.open(os.path.join(out_draw, f"elf_{name}.png")), tuple(layout_parts[name]))
    bg = Image.new("RGBA", (W, H), (60, 150, 200, 255))
    bg.alpha_composite(canvas)
    bg.convert("RGB").save(os.path.join(prev, "zusammen.png"))
    sheet = Image.new("RGBA", (W * 2, H), (60, 150, 200, 255))
    for name in ["head", "torso", "arml", "armr", "legl", "legr"]:
        off = W if name in ("arml", "armr", "legl", "legr") else 0
        sheet.alpha_composite(Image.open(os.path.join(out_draw, f"elf_{name}.png")), (layout_parts[name][0] + off, layout_parts[name][1]))
    sheet.convert("RGB").save(os.path.join(prev, "teile.png"))
    bb = Image.new("RGBA", b_img.size, (60, 150, 200, 255))
    bb.alpha_composite(b_img)
    bb.convert("RGB").resize((b_img.width // 2, b_img.height // 2)).save(os.path.join(prev, "besen.png"))
