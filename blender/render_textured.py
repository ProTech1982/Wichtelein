"""Baut den Wichtel in Blender aus den Textur-Teilen (Wichtel_bild1) und rendert jedes Teil mit Volumen und Licht.
Aufruf: blender --factory-startup -b -P render_textured.py -- teile_ordner hoehen_ordner layout.json ausgabe_ordner [skalierung] [samples]
Jedes Teil ist ein feines Gitter, das mit dem Teilbild (Farbe + Transparenz) beklebt und mit der Höhenkarte gewölbt wird."""
import json
import math
import os
import sys

import bpy
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
parts_dir, h_dir, layout_path, out_dir = args[:4]
SS = int(args[4]) if len(args) > 4 else 2
SAMPLES = int(args[5]) if len(args) > 5 else 48
PX = 0.01            # eine Bildpunkt-Einheit = 0,01 Blender-Einheiten
CANVAS = 740
BULGE = 9.0 * PX     # Höhe der Wölbung
os.makedirs(out_dir, exist_ok=True)
layout = json.load(open(layout_path))

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "CYCLES"
sc.cycles.samples = SAMPLES
sc.cycles.use_denoising = True
sc.render.film_transparent = True
sc.render.resolution_x = sc.render.resolution_y = CANVAS * SS
sc.render.resolution_percentage = 100
sc.render.image_settings.file_format = "PNG"
sc.render.image_settings.color_mode = "RGBA"
sc.view_settings.view_transform = "Standard"

# Kamera: von vorn, orthografisch, Bildpunkt (px, py) -> X = px, Z = -py
cam = bpy.data.objects.new("Kamera", bpy.data.cameras.new("Kamera"))
cam.data.type = "ORTHO"
cam.data.ortho_scale = CANVAS * PX
cam.location = (CANVAS * PX / 2, -20, -CANVAS * PX / 2)
cam.rotation_euler = (math.radians(90), 0, 0)
sc.collection.objects.link(cam)
sc.camera = cam

# Licht: großes weiches Flächenlicht oben links vorn plus helle Umgebung
world = bpy.data.worlds.new("Welt")
sc.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (1, 1, 1, 1)
bg.inputs[1].default_value = 0.55
li = bpy.data.lights.new("Licht", "AREA")
li.energy = 900
li.size = 12
lo = bpy.data.objects.new("Licht", li)
lo.location = (CANVAS * PX * 0.2, -9, 4)
lo.rotation_euler = (math.radians(70), 0, math.radians(-15))
sc.collection.objects.link(lo)


def load(path):
    img = bpy.data.images.load(path)
    img.colorspace_settings.name = "sRGB"
    return img


def make_part(name, pos):
    tex = load(os.path.join(parts_dir, f"elf_{name}.png"))
    hm = bpy.data.images.load(os.path.join(h_dir, f"h_{name}.png"))
    w, h = tex.size
    nx = max(8, min(256, w // 2))
    ny = max(8, min(256, h // 2))
    hp = np.array(hm.pixels[:], dtype=np.float32).reshape(h, w, 4)[:, :, 0][::-1]  # Zeile 0 = oben
    mesh = bpy.data.meshes.new(name)
    verts, faces, uvs = [], [], []
    for j in range(ny + 1):
        for i in range(nx + 1):
            u, v = i / nx, j / ny
            px = pos[0] + u * w
            py = pos[1] + v * h
            hv = hp[min(h - 1, int(v * (h - 1))), min(w - 1, int(u * (w - 1)))]
            verts.append((px * PX, -hv * BULGE, -py * PX))
            uvs.append((u, 1 - v))
    for j in range(ny):
        for i in range(nx):
            a = j * (nx + 1) + i
            faces.append((a, a + 1, a + nx + 2, a + nx + 1))
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    uv = mesh.uv_layers.new(name="UV")
    for loop in mesh.loops:
        uv.data[loop.index].uv = uvs[loop.vertex_index]
    for p in mesh.polygons:
        p.use_smooth = True
    ob = bpy.data.objects.new(name, mesh)
    sc.collection.objects.link(ob)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    mix_a = nt.nodes.new("ShaderNodeMixShader")
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = tex
    t.interpolation = "Cubic"
    t.extension = "EXTEND"
    pb = nt.nodes.new("ShaderNodeBsdfPrincipled")
    em = nt.nodes.new("ShaderNodeEmission")
    transp = nt.nodes.new("ShaderNodeBsdfTransparent")
    mix_c = nt.nodes.new("ShaderNodeMixShader")
    nt.links.new(t.outputs["Color"], pb.inputs["Base Color"])
    pb.inputs["Roughness"].default_value = 0.85
    for k in ("Specular IOR Level", "Specular"):
        if k in pb.inputs:
            pb.inputs[k].default_value = 0.15
    nt.links.new(t.outputs["Color"], em.inputs["Color"])
    em.inputs["Strength"].default_value = 0.55
    mix_c.inputs[0].default_value = 0.5
    nt.links.new(pb.outputs["BSDF"], mix_c.inputs[1])
    nt.links.new(em.outputs["Emission"], mix_c.inputs[2])
    nt.links.new(t.outputs["Alpha"], mix_a.inputs[0])
    nt.links.new(transp.outputs["BSDF"], mix_a.inputs[1])
    nt.links.new(mix_c.outputs["Shader"], mix_a.inputs[2])
    nt.links.new(mix_a.outputs["Shader"], out.inputs["Surface"])
    ob.data.materials.append(mat)
    return ob


objs = {n: make_part(n, layout["parts"][n]) for n in ["head", "torso", "arml", "armr", "legl", "legr"]}
for name, ob in objs.items():
    for o in objs.values():
        o.hide_render = o is not ob
    sc.render.filepath = os.path.join(out_dir, f"full_{name}.png")
    bpy.ops.render.render(write_still=True)
    print("gerendert", name, flush=True)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out_dir, "wichtel_textur.blend"))
