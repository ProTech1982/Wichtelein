"""Rendert den Wichtel in Einzelteilen (transparente PNGs im Layout-Raster).

blender -b -P make_wichtel.py -P render_parts.py -- AUSGABEORDNER [samples] [engine]
Canvas 1500 x 1300 px, 1000 px pro Einheit; Fußpunkt bei (750, 1100).
"""
import os
import sys
import bpy

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
outdir = args[0] if args else "parts"
samples = int(args[1]) if len(args) > 1 else 64
engine = args[2] if len(args) > 2 else 'CYCLES'
os.makedirs(outdir, exist_ok=True)

sc = bpy.context.scene
sc.render.engine = engine
if engine == 'CYCLES':
    sc.cycles.samples = samples
    sc.cycles.use_denoising = True
    sc.cycles.device = 'CPU'
sc.render.resolution_x = 1500
sc.render.resolution_y = 1300
sc.render.resolution_percentage = 100
sc.render.film_transparent = True
sc.render.image_settings.file_format = 'PNG'
sc.render.image_settings.color_mode = 'RGBA'

parts = ["head", "torso", "arml", "armr", "legl", "legr"]
objs = [o for o in bpy.data.objects if "wpart" in o.keys()]
for name in parts + ["full"]:
    for o in objs:
        o.hide_render = not (name == "full" or o["wpart"] == name)
    sc.render.filepath = os.path.join(outdir, f"part_{name}.png")
    bpy.ops.render.render(write_still=True)
    print("TEIL", name, flush=True)
for o in objs:
    o.hide_render = False
