import bpy, time, sys
sc = bpy.context.scene
sc.render.engine = 'CYCLES'
sc.cycles.device = 'CPU'
sc.cycles.samples = 4
sc.render.resolution_x = sc.render.resolution_y = 64
sc.render.filepath = r"C:/Users/keine/AppData/Local/Temp/claude/621edbc86207d79a/4e50b0a4-1af4-458f-9417-fb5d43f333eb/scratchpad/trivial.png"
t = time.time()
bpy.ops.render.render(write_still=True)
print("TRIVIAL", round(time.time() - t, 1), flush=True)
