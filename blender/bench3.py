import bpy, time
sc = bpy.context.scene
sc.render.engine = 'CYCLES'; sc.cycles.device = 'CPU'; sc.cycles.use_denoising = False
sc.render.resolution_percentage = 15; sc.cycles.samples = 4
OUT = "C:/Users/keine/AppData/Local/Temp/claude/621edbc86207d79a/4e50b0a4-1af4-458f-9417-fb5d43f333eb/scratchpad/b3_%s.png"
def go(tag):
    sc.render.filepath = OUT % tag
    t = time.time(); bpy.ops.render.render(write_still=True)
    print("B3", tag, round(time.time() - t, 1), flush=True)
# (1) ohne Materialien mit SSS und Bump
for m in bpy.data.materials:
    b = m.node_tree.nodes.get("Principled BSDF")
    try: b.inputs["Subsurface Weight"].default_value = 0.0
    except Exception: pass
    for n in list(m.node_tree.nodes):
        if n.bl_idname in ("ShaderNodeBump", "ShaderNodeTexNoise"): m.node_tree.nodes.remove(n)
go("nosss_nobump")
# (2) zusätzlich ohne Zotteln und Röhren
for o in bpy.data.objects:
    if o.name.startswith(("fur", "cuffFur")) or o.type == 'CURVE': o.hide_render = True
go("nofur")
# (3) ohne Unterteilungsflächen
for o in bpy.data.objects:
    for md in list(o.modifiers):
        if md.type == 'SUBSURF': o.modifiers.remove(md)
go("nosubsurf")
