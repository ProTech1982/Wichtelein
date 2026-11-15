import bpy, time, sys
sc = bpy.context.scene
res = {}
def run(name, engine, pct, samples, denoise=False):
    sc.render.engine = engine
    sc.render.resolution_percentage = pct
    if engine == 'CYCLES':
        sc.cycles.samples = samples
        sc.cycles.use_denoising = denoise
        sc.cycles.device = 'CPU'
    sc.render.filepath = r"C:/Users/keine/AppData/Local/Temp/claude/621edbc86207d79a/4e50b0a4-1af4-458f-9417-fb5d43f333eb/scratchpad/bench_%s.png" % name
    t = time.time()
    bpy.ops.render.render(write_still=True)
    print("BENCH", name, round(time.time() - t, 1), "s", flush=True)
run("cyc_small", 'CYCLES', 20, 8)
run("cyc_mid", 'CYCLES', 40, 16)
