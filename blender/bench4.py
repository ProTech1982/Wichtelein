import bpy, time, os
part = os.environ["PART"]
sc = bpy.context.scene
sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.use_denoising = False
sc.render.resolution_percentage = 15; sc.cycles.samples = 4
for o in bpy.data.objects:
    if "wpart" in o.keys(): o.hide_render = (o["wpart"] != part)
sc.render.filepath = "C:/Users/keine/AppData/Local/Temp/claude/621edbc86207d79a/4e50b0a4-1af4-458f-9417-fb5d43f333eb/scratchpad/b4_%s.png" % part
t = time.time(); bpy.ops.render.render(write_still=True)
print("B4", part, round(time.time() - t, 1), flush=True)
