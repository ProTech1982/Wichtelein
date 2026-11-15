"""Rendert den ganzen Wichtel als Vorschau: blender -b -P make_wichtel.py -P render_preview.py -- ausgabe.png [prozent] [samples]"""
import sys
import bpy

args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
out = args[0] if args else "preview.png"
pct = int(args[1]) if len(args) > 1 else 50
samples = int(args[2]) if len(args) > 2 else 32
sc = bpy.context.scene
sc.render.resolution_percentage = pct
engine = args[3] if len(args) > 3 else 'EEVEE'
if engine == 'CYCLES':
    sc.render.engine = 'CYCLES'
    sc.cycles.samples = samples
else:
    for name in ('BLENDER_EEVEE', 'BLENDER_EEVEE_NEXT'):
        try:
            sc.render.engine = name
            break
        except TypeError:
            continue
    try:
        sc.eevee.taa_render_samples = samples
    except Exception:
        pass
sc.render.filepath = out
sc.render.image_settings.file_format = 'PNG'
sc.render.image_settings.color_mode = 'RGBA'
bpy.ops.render.render(write_still=True)
print("gerendert:", out)
