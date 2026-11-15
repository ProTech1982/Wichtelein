import bpy, time, os
grp = os.environ["GRP"]
groups = {
 "face": ("skull","cheek","chin","blush","nose"),
 "eyes": ("eye","iris","pupil","glint","brow"),
 "ears": ("ear",),
 "hair": ("fringe","burn"),
 "hat": ("hat",),
 "trim": ("band","bell","hatTrim"),
 "smile": ("smile",),
}
pre = groups[grp]
sc = bpy.context.scene
sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.use_denoising = False
sc.render.resolution_percentage = 15; sc.cycles.samples = 4
n = 0
for o in bpy.data.objects:
    if "wpart" in o.keys():
        vis = o["wpart"] == "head" and o.name.startswith(pre) and not (grp == "ears" and o.name.startswith("earIn") is False and False)
        o.hide_render = not vis
        n += vis
sc.render.filepath = "C:/Users/keine/AppData/Local/Temp/claude/621edbc86207d79a/4e50b0a4-1af4-458f-9417-fb5d43f333eb/scratchpad/b5_%s.png" % grp
t = time.time(); bpy.ops.render.render(write_still=True)
print("B5", grp, n, "Objekte", round(time.time() - t, 1), flush=True)
