"""Der 3D-Wichtel: modelliert in Blender (nur aus Python), mit Bewegungen gerendert, 360° drehbar.

Aufruf:
  blender --factory-startup -b -P wichtel3d.py -- vorschau ausgabe_ordner [samples]
  blender --factory-startup -b -P wichtel3d.py -- final ausgabe_ordner [samples]

vorschau: ein Kontaktbogen mit wenigen Bildern (zum Prüfen des Aussehens).
final:    alle Bilder (Stehen, Laufen, Fegen, Winken, jeweils aus 8 bzw. 16 Blickrichtungen) plus elf3d.json.
Das Modell blickt nach -Y (zur Kamera); die Blickrichtung ("heading") dreht es um die Hochachse:
0 = zum Betrachter, 90 = nach rechts, 180 = vom Betrachter weg, 270 = nach links.
"""
import json
import math
import os
import sys

import bmesh
import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Euler, Vector

args = sys.argv[sys.argv.index("--") + 1:]
MODE = args[0]
OUT = args[1]
SAMPLES = int(args[2]) if len(args) > 2 else 32
os.makedirs(OUT, exist_ok=True)

CELL_W, CELL_H = 450, 600
ORTHO = 1.30            # Meter über die Bildhöhe
PITCH = 10.0            # Kamera schaut leicht von oben

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "CYCLES"
sc.cycles.samples = SAMPLES
sc.cycles.use_denoising = True
sc.render.film_transparent = True
sc.render.resolution_x, sc.render.resolution_y = CELL_W, CELL_H
sc.render.resolution_percentage = 100
sc.render.image_settings.file_format = "PNG"
sc.render.image_settings.color_mode = "RGBA"
sc.view_settings.view_transform = "Standard"


# ------------------------------------------------------------------ Material
def mat(name, color, rough=0.7, bump=None, extra=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    pb = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    pb.inputs["Base Color"].default_value = (*color, 1)
    pb.inputs["Roughness"].default_value = rough
    if bump:
        scale, strength, stretch = bump
        tc = nt.nodes.new("ShaderNodeTexCoord")
        mp = nt.nodes.new("ShaderNodeMapping")
        mp.inputs["Scale"].default_value = (scale, scale, scale * stretch)
        nz = nt.nodes.new("ShaderNodeTexNoise")
        nz.inputs["Detail"].default_value = 6
        bp = nt.nodes.new("ShaderNodeBump")
        bp.inputs["Strength"].default_value = strength
        bp.inputs["Distance"].default_value = 0.01
        nt.links.new(tc.outputs["Object"], mp.inputs["Vector"])
        nt.links.new(mp.outputs["Vector"], nz.inputs["Vector"])
        nt.links.new(nz.outputs["Fac"], bp.inputs["Height"])
        nt.links.new(bp.outputs["Normal"], pb.inputs["Normal"])
        if extra == "streaks":  # Haarsträhnen: Helligkeit folgt der Struktur
            cr = nt.nodes.new("ShaderNodeValToRGB")
            cr.color_ramp.elements[0].color = (0.80, 0.79, 0.75, 1)
            cr.color_ramp.elements[1].color = (1.0, 1.0, 0.98, 1)
            cr.color_ramp.elements[0].position = 0.35
            cr.color_ramp.elements[1].position = 0.6
            nt.links.new(nz.outputs["Fac"], cr.inputs["Fac"])
            nt.links.new(cr.outputs["Color"], pb.inputs["Base Color"])
    if extra == "snow":  # kleine weiße Punkte auf dem Mantel
        tc = nt.nodes.new("ShaderNodeTexCoord")
        vo = nt.nodes.new("ShaderNodeTexVoronoi")
        vo.inputs["Scale"].default_value = 160
        mx = nt.nodes.new("ShaderNodeMix")
        mx.data_type = "RGBA"
        cr = nt.nodes.new("ShaderNodeValToRGB")
        cr.color_ramp.elements[0].position = 0.0
        cr.color_ramp.elements[1].position = 0.06
        cr.color_ramp.elements[0].color = (1, 1, 1, 1)
        cr.color_ramp.elements[1].color = (0, 0, 0, 1)
        nt.links.new(tc.outputs["Object"], vo.inputs["Vector"])
        nt.links.new(vo.outputs["Distance"], cr.inputs["Fac"])
        mx.inputs[6].default_value = (*color, 1)
        mx.inputs[7].default_value = (0.9, 0.95, 0.92, 1)
        nt.links.new(cr.outputs["Color"], mx.inputs[0])
        nt.links.new(mx.outputs[2], pb.inputs["Base Color"])
    return m


M = {
    "coat": mat("Mantel", (0.06, 0.21, 0.14), 0.85, bump=(55, 0.25, 1.0), extra="snow"),
    "hat": mat("Muetze", (0.74, 0.02, 0.04), 0.6, bump=(9, 0.35, 1.0)),
    "red": mat("Rot", (0.80, 0.06, 0.07), 0.55, bump=(40, 0.15, 1.0)),
    "stud": mat("Niete", (0.95, 0.15, 0.15), 0.35),
    "skin": mat("Haut", (0.95, 0.74, 0.62), 0.55),
    "nose": mat("Nase", (0.95, 0.56, 0.48), 0.45),
    "cheek": mat("Wange", (0.96, 0.45, 0.45), 0.8),
    "eye": mat("Auge", (0.18, 0.10, 0.07), 0.5),
    "beard": mat("Bart", (0.95, 0.94, 0.90), 0.75, bump=(120, 0.5, 0.05), extra="streaks"),
    "trouser": mat("Hose", (0.42, 0.32, 0.20), 0.85, bump=(60, 0.2, 1.0)),
    "boot": mat("Stiefel", (0.30, 0.15, 0.07), 0.35),
    "sole": mat("Sohle", (0.12, 0.07, 0.04), 0.5),
    "straw": mat("Stroh", (0.80, 0.56, 0.20), 0.9, bump=(90, 0.6, 0.08)),
    "wood": mat("Holz", (0.88, 0.68, 0.45), 0.6, bump=(60, 0.1, 0.1)),
    "string": mat("Schnur", (0.96, 0.94, 0.88), 0.9),
}


def link(ob, parent=None):
    sc.collection.objects.link(ob)
    if parent is not None:
        ob.parent = parent
    return ob


def finish(me, smooth=True):
    if smooth:
        for p in me.polygons:
            p.use_smooth = True


def lathe(name, profile, steps=48, material=None, parent=None, subsurf=0, thick=0.0):
    bm = bmesh.new()
    vs = [bm.verts.new((r, 0, z)) for r, z in profile]
    for a, b in zip(vs, vs[1:]):
        bm.edges.new((a, b))
    bmesh.ops.spin(bm, geom=bm.verts[:] + bm.edges[:], cent=(0, 0, 0), axis=(0, 0, 1), angle=2 * math.pi, steps=steps)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    finish(me)
    ob = bpy.data.objects.new(name, me)
    link(ob, parent)
    if material:
        me.materials.append(material)
    if thick:
        s = ob.modifiers.new("dick", "SOLIDIFY")
        s.thickness = thick
    if subsurf:
        s = ob.modifiers.new("glatt", "SUBSURF")
        s.levels = subsurf
        s.render_levels = subsurf
    return ob


def sphere(name, r, loc, scale=(1, 1, 1), material=None, parent=None, rot=(0, 0, 0), seg=32):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=seg, v_segments=max(8, seg // 2), radius=r)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    finish(me)
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    ob.scale = scale
    ob.rotation_euler = Euler([math.radians(a) for a in rot])
    link(ob, parent)
    if material:
        me.materials.append(material)
    return ob


def empty(name, loc, parent=None):
    ob = bpy.data.objects.new(name, None)
    ob.location = loc
    link(ob, parent)
    return ob


def cylinder(name, r, z0, z1, material, parent=None, r1=None, seg=24):
    return lathe(name, [(0, z0), (r, z0), (r1 if r1 is not None else r, z1), (0, z1)], seg, material, parent, subsurf=1)


# ------------------------------------------------------------------ Figur
root = empty("Wichtel", (0, 0, 0))

# Mantel: Glockenform
coat = lathe("Mantel", [(0.0, 0.135), (0.186, 0.135), (0.192, 0.148), (0.186, 0.162), (0.172, 0.22), (0.148, 0.30),
                        (0.118, 0.372), (0.075, 0.41), (0.0, 0.425)], 56, M["coat"], root, subsurf=1)
coat.scale = (1.0, 0.88, 1.0)

# Kopf, Nase, Wangen, Augen
head = empty("Kopf", (0, 0, 0), root)
sphere("Gesicht", 0.09, (0, 0, 0.455), (1, 0.96, 0.96), M["skin"], head)
sphere("Nase", 0.037, (0, -0.088, 0.443), (1, 1.05, 0.95), M["nose"], head)
for sx in (-1, 1):
    sphere("Wange", 0.026, (sx * 0.058, -0.07, 0.448), (1, 0.45, 1), M["cheek"], head, rot=(0, 0, sx * -25))
    cu = bpy.data.curves.new("Auge", "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = 0.0028
    sp = cu.splines.new("POLY")
    pts = [(math.sin(a) * 0.016, 0, -math.cos(a) * 0.007) for a in [i / 6 * math.pi - math.pi / 2 for i in range(7)]]
    sp.points.add(len(pts) - 1)
    for p, c in zip(sp.points, pts):
        p.co = (c[0], c[1], c[2], 1)
    eye = bpy.data.objects.new("Auge", cu)
    eye.location = (sx * 0.04, -0.081, 0.476)
    eye.rotation_euler = Euler((0, 0, math.radians(sx * 22)))
    cu.materials.append(M["eye"])
    link(eye, head)

sphere("Haar", 0.095, (0, 0.03, 0.452), (1.02, 0.9, 0.85), M["beard"], head)

# Mütze: Kegel, nach hinten gebogen, mit Band und Nieten
hat_profile = [(0.158, 0.512), (0.165, 0.520), (0.168, 0.549), (0.163, 0.574)]
for i in range(1, 36):
    t = i / 35
    z = 0.574 + t * 0.426
    hat_profile.append((0.152 * (1 - t) ** 1.08 + 0.004, z))
hat = lathe("Muetze", hat_profile, 56, M["hat"], head, thick=0.006)
for v in hat.data.vertices:
    z = v.co.z
    t = max(0.0, (z - 0.60) / 0.40)
    v.co.y += 0.20 * t * t
    v.co.x += 0.08 * t ** 3
    v.co.z -= 0.07 * t * t
for i in range(16):
    a = i / 16 * 2 * math.pi
    sphere("Niete", 0.0085, (math.sin(a) * 0.1685, -math.cos(a) * 0.1685, 0.546), (1, 1, 1), M["stud"], head, seg=12)

# Bart und Schnurrbart
bm = bmesh.new()
bmesh.ops.create_uvsphere(bm, u_segments=64, v_segments=40, radius=1.0)
for v in bm.verts:
    x, y, z = v.co
    if z < 0:
        k = (1 + z) ** 0.85
        x *= k
        y *= k * 0.9
    zz = 0.285 + (z * 0.14 if z >= 0 else z * 0.17)
    v.co = (x * 0.15, -0.09 + y * 0.09, zz)
me = bpy.data.meshes.new("Bart")
bm.to_mesh(me)
bm.free()
finish(me)
beard = bpy.data.objects.new("Bart", me)
me.materials.append(M["beard"])
link(beard, head)
for sx in (-1, 1):
    sphere("Schnurrbart", 0.045, (sx * 0.05, -0.108, 0.425), (1.2, 0.5, 0.5), M["beard"], head, rot=(0, sx * -18, sx * 10))

# Arme (Gelenk an der Schulter) mit Manschette und Fäustling
arms = {}
for name, sx in (("L", -1), ("R", 1)):
    a = empty("Arm" + name, (sx * 0.148, 0.0, 0.375), root)
    cylinder("Aermel", 0.040, -0.105, 0.0, M["coat"], a, r1=0.046)
    cylinder("Manschette", 0.047, -0.14, -0.10, M["red"], a)
    sphere("Faeustling", 0.043, (0, 0, -0.175), (1, 0.95, 1.15), M["red"], a)
    sphere("Daumen", 0.019, (-sx * 0.036, -0.012, -0.16), (1, 1, 1.3), M["red"], a)
    hand = empty("Hand" + name, (0, 0, -0.175), a)
    arms[name] = (a, hand)

# Beine mit Hose und Stiefel
legs = {}
for name, sx in (("L", -1), ("R", 1)):
    l = empty("Bein" + name, (sx * 0.058, 0.0, 0.15), root)
    cylinder("Hose", 0.040, -0.085, 0.0, M["trouser"], l)
    sphere("Stiefel", 0.05, (0, -0.02, -0.105), (1.0, 1.5, 0.95), M["boot"], l)
    sphere("Sohle", 0.05, (0, -0.02, -0.142), (1.02, 1.54, 0.28), M["sole"], l)
    legs[name] = l

# Besen: Griff in der Hand, Borsten unten
broom = empty("Besen", (0, 0, 0), root)
cylinder("Stiel", 0.0085, -0.10, 0.22, M["wood"], broom, seg=16)
bris = lathe("Borsten", [(0.0, -0.10), (0.030, -0.11), (0.045, -0.15), (0.065, -0.24), (0.082, -0.33), (0.0, -0.33)], 40, M["straw"], broom, subsurf=1)
for z, r in ((-0.14, 0.040), (-0.225, 0.062)):
    lathe("Schnur", [(r - 0.004, z - 0.008), (r + 0.004, z - 0.008), (r + 0.004, z + 0.008), (r - 0.004, z + 0.008)], 32, M["string"], broom)
BROOM_REACH = 0.33   # vom Griff (Hand) bis zur Borstenspitze


# ------------------------------------------------------------------ Kamera und Licht
cam = bpy.data.objects.new("Kamera", bpy.data.cameras.new("Kamera"))
cam.data.type = "ORTHO"
cam.data.ortho_scale = ORTHO
cam.data.clip_end = 50
d = 8.0
cam.location = (0, -d * math.cos(math.radians(PITCH)), 0.50 + d * math.sin(math.radians(PITCH)))
cam.rotation_euler = Euler((math.radians(90 - PITCH), 0, 0))
sc.collection.objects.link(cam)
sc.camera = cam

world = bpy.data.worlds.new("Welt")
sc.world = world
world.use_nodes = True
bg = world.node_tree.nodes["Background"]
bg.inputs[0].default_value = (0.80, 0.84, 0.92, 1)
bg.inputs[1].default_value = 0.30


def light(name, loc, energy, size, color=(1, 1, 1)):
    li = bpy.data.lights.new(name, "AREA")
    li.energy = energy
    li.size = size
    li.color = color
    ob = bpy.data.objects.new(name, li)
    ob.location = loc
    sc.collection.objects.link(ob)
    direction = Vector((0, 0, 0.45)) - Vector(loc)
    ob.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


light("Haupt", (-2.0, -3.0, 3.2), 170, 2.5, (1.0, 0.96, 0.9))
light("Auffuellen", (3.0, -2.5, 1.5), 45, 3.0, (0.9, 0.95, 1.0))
light("Kante", (0.5, 3.0, 2.8), 90, 2.0)


# ------------------------------------------------------------------ Bewegungen
def swing(ob, fwd=0.0, side=0.0):
    """Gelenk drehen: fwd = nach vorn (Grad), side = zur Seite abspreizen (Grad, positiv = nach außen links)."""
    ob.rotation_euler = Euler((-math.radians(fwd), math.radians(side), 0))


def show_broom(flag):
    for o in [broom] + list(broom.children_recursive):
        o.hide_render = not flag
        o.hide_viewport = not flag


def pose(clip, k, n):
    """Setzt das Modell in Bild k (von n) der Bewegung."""
    swing(arms["L"][0], 0, 7)
    swing(arms["R"][0], 0, -7)
    swing(legs["L"], 0, 0)
    swing(legs["R"], 0, 0)
    root.location = (0, 0, 0)
    root.rotation_euler = Euler((0, 0, 0))
    head.rotation_euler = Euler((0, 0, 0))
    show_broom(False)
    if clip == "walk":
        p = 2 * math.pi * k / n
        s = math.sin(p)
        swing(legs["L"], 28 * s)
        swing(legs["R"], -28 * s)
        swing(arms["L"][0], -20 * s, 7)
        swing(arms["R"][0], 20 * s, -7)
        head.rotation_euler = Euler((0, math.radians(2.5 * s), 0))
    elif clip == "wave":
        u = k / (n - 1)
        swing(arms["R"][0], 0, -(125 + 50 * u))
        swing(arms["L"][0], 0, 7)
        head.rotation_euler = Euler((0, math.radians(4 * math.sin(u * math.pi)), 0))
    elif clip == "sweep":
        u = k / (n - 1)
        fwd = 28 + 34 * u
        swing(arms["R"][0], fwd, 4)
        swing(arms["L"][0], fwd + 6, -26)
        root.rotation_euler = Euler((math.radians(-4 - 3 * u), 0, 0))
        show_broom(True)


def place_broom():
    """Besen am Fäustling (rechte Hand) so ausrichten, dass die Borsten den Boden berühren."""
    bpy.context.view_layer.update()
    g = root.matrix_world.inverted() @ arms["R"][1].matrix_world.translation
    drop = max(0.05, g.z - 0.012)
    horiz = math.sqrt(max(0.0, BROOM_REACH ** 2 - drop ** 2))
    direction = Vector((0, -horiz, -drop)).normalized()
    broom.location = g
    broom.rotation_mode = "QUATERNION"
    broom.rotation_quaternion = direction.to_track_quat("-Z", "Y")


def render(path, heading):
    root.rotation_euler.z = math.radians(heading)
    bpy.context.view_layer.update()
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)


CLIPS = {"idle": (16, 1), "walk": (16, 8), "sweep": (8, 9), "wave": (8, 6)}

if MODE == "vorschau":
    plan = [("idle", 0, 0, 0), ("idle", 90, 0, 0), ("idle", 180, 0, 0), ("walk", 90, 2, 8), ("walk", 45, 5, 8),
            ("sweep", 90, 6, 9), ("sweep", 270, 2, 9), ("wave", 0, 3, 6)]
    files = []
    for i, (clip, heading, k, n) in enumerate(plan):
        pose(clip, k, max(n, 1) if n else 1)
        if clip == "sweep":
            place_broom()
        p = os.path.join(OUT, f"v{i}.png")
        render(p, heading)
        files.append(p)
    print("vorschau fertig", files)
else:
    # Bildpunkt des Fußpunkts und der Höhe (1 m) in der Zeichnung
    bpy.context.view_layer.update()
    f = world_to_camera_view(sc, cam, Vector((0, 0, 0)))
    t = world_to_camera_view(sc, cam, Vector((0, 0, 1.0)))
    feet = [round(f.x * CELL_W, 1), round((1 - f.y) * CELL_H, 1)]
    height = round((t.y - f.y) * CELL_H, 1)
    meta = {"cell": [CELL_W, CELL_H], "feet": feet, "height": height, "clips": {}}
    for clip, (nh, nf) in CLIPS.items():
        meta["clips"][clip] = {"headings": nh, "frames": nf}
        for hi in range(nh):
            for k in range(nf):
                pose(clip, k, nf)
                if clip == "sweep":
                    place_broom()
                render(os.path.join(OUT, f"{clip}_{hi:02d}_{k:02d}.png"), 360.0 * hi / nh)
        print("fertig:", clip, flush=True)
    json.dump(meta, open(os.path.join(OUT, "elf3d.json"), "w"), indent=1)
