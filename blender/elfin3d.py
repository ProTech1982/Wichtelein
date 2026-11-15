"""Die 3D-Elfin (Wichtelfrau): erzeugt aus wichtel3d.py (Kleid, Zöpfe, Ringelstrumpfhose, Mütze mit Bommel), gleiche Bewegungen und Blickrichtungen.

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
import random

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "textur")


def load_img(name):
    img = bpy.data.images.load(os.path.join(TEX, name))
    img.colorspace_settings.name = "sRGB"
    return img


def mat(name, color, rough=0.7, bump=None, extra=None, image=None, image_mix=0.0, sheen=0.0, sss=0.0):
    """Material mit Rauschen als Struktur; optional ein echtes Foto-Muster (aus dem Referenzbild) als Stoff."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    pb = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    pb.inputs["Base Color"].default_value = (*color, 1)
    pb.inputs["Roughness"].default_value = rough
    for key, val in (("Sheen Weight", sheen), ("Subsurface Weight", sss)):
        if key in pb.inputs and val:
            pb.inputs[key].default_value = val
    if sss and "Subsurface Radius" in pb.inputs:
        pb.inputs["Subsurface Radius"].default_value = (0.9, 0.4, 0.3)
    tc = nt.nodes.new("ShaderNodeTexCoord")
    nz = None
    if bump:
        scale, strength, stretch = bump
        mp = nt.nodes.new("ShaderNodeMapping")
        mp.inputs["Scale"].default_value = (scale, scale, scale * stretch)
        nz = nt.nodes.new("ShaderNodeTexNoise")
        nz.inputs["Detail"].default_value = 7
        bp = nt.nodes.new("ShaderNodeBump")
        bp.inputs["Strength"].default_value = strength
        bp.inputs["Distance"].default_value = 0.01
        nt.links.new(tc.outputs["Object"], mp.inputs["Vector"])
        nt.links.new(mp.outputs["Vector"], nz.inputs["Vector"])
        nt.links.new(nz.outputs["Fac"], bp.inputs["Height"])
        nt.links.new(bp.outputs["Normal"], pb.inputs["Normal"])
    color_out = None
    if image:
        # Foto-Muster ringsum aufgetragen (Kasten-Projektion, keine UV nötig)
        tx = nt.nodes.new("ShaderNodeTexImage")
        tx.image = image
        tx.projection = "BOX"
        tx.projection_blend = 0.3
        tx.interpolation = "Cubic"
        mp2 = nt.nodes.new("ShaderNodeMapping")
        mp2.inputs["Scale"].default_value = (5, 5, 5)
        nt.links.new(tc.outputs["Object"], mp2.inputs["Vector"])
        nt.links.new(mp2.outputs["Vector"], tx.inputs["Vector"])
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        mix.blend_type = "MULTIPLY"
        mix.inputs[0].default_value = image_mix
        mix.inputs[6].default_value = (1, 1, 1, 1)
        mix.inputs[7].default_value = (1, 1, 1, 1)
        nt.links.new(tx.outputs["Color"], mix.inputs[7])
        base = nt.nodes.new("ShaderNodeRGB")
        base.outputs[0].default_value = (*color, 1)
        mul = nt.nodes.new("ShaderNodeMix")
        mul.data_type = "RGBA"
        mul.blend_type = "MIX"
        mul.inputs[0].default_value = image_mix
        mul.inputs[6].default_value = (*color, 1)
        nt.links.new(tx.outputs["Color"], mul.inputs[7])
        nt.links.new(mul.outputs[2], pb.inputs["Base Color"])
        color_out = mul
    if extra == "streaks" and nz is not None:
        cr = nt.nodes.new("ShaderNodeValToRGB")
        cr.color_ramp.elements[0].color = (0.78, 0.77, 0.73, 1)
        cr.color_ramp.elements[1].color = (1.0, 1.0, 0.98, 1)
        cr.color_ramp.elements[0].position = 0.30
        cr.color_ramp.elements[1].position = 0.62
        nt.links.new(nz.outputs["Fac"], cr.inputs["Fac"])
        nt.links.new(cr.outputs["Color"], pb.inputs["Base Color"])
    if extra == "snow":  # kleine helle Punkte auf dem Mantel (wie im Bild)
        vo = nt.nodes.new("ShaderNodeTexVoronoi")
        vo.inputs["Scale"].default_value = 170
        mx = nt.nodes.new("ShaderNodeMix")
        mx.data_type = "RGBA"
        cr = nt.nodes.new("ShaderNodeValToRGB")
        cr.color_ramp.elements[0].position = 0.0
        cr.color_ramp.elements[1].position = 0.05
        cr.color_ramp.elements[0].color = (1, 1, 1, 1)
        cr.color_ramp.elements[1].color = (0, 0, 0, 1)
        nt.links.new(tc.outputs["Object"], vo.inputs["Vector"])
        nt.links.new(vo.outputs["Distance"], cr.inputs["Fac"])
        if color_out is not None:
            nt.links.new(color_out.outputs[2], mx.inputs[6])
        else:
            mx.inputs[6].default_value = (*color, 1)
        mx.inputs[7].default_value = (0.9, 0.95, 0.92, 1)
        nt.links.new(cr.outputs["Color"], mx.inputs[0])
        nt.links.new(mx.outputs[2], pb.inputs["Base Color"])
    return m


IMG_HAT = load_img("hat_patch.png")
IMG_COAT = load_img("coat_patch.png")
IMG_BEARD = load_img("beard_patch.png")

M = {
    "coat": mat("Mantel", (0.07, 0.24, 0.17), 0.85, bump=(55, 0.35, 1.0), extra="snow", image=IMG_COAT, image_mix=0.45, sheen=0.3),
    "hat": mat("Muetze", (0.62, 0.015, 0.035), 0.65, bump=(14, 0.55, 1.0), image=IMG_HAT, image_mix=0.25, sheen=0.4),
    "red": mat("Rot", (0.78, 0.05, 0.06), 0.6, bump=(45, 0.25, 1.0), image=IMG_HAT, image_mix=0.3, sheen=0.3),
    "stud": mat("Niete", (0.95, 0.15, 0.15), 0.3),
    "skin": mat("Haut", (0.95, 0.72, 0.60), 0.5, bump=(120, 0.1, 1.0), sss=0.25),
    "nose": mat("Nase", (0.95, 0.55, 0.47), 0.4, bump=(160, 0.15, 1.0), sss=0.3),
    "cheek": mat("Wange", (0.96, 0.45, 0.45), 0.8, sss=0.2),
    "eye": mat("Auge", (0.18, 0.10, 0.07), 0.5),
    "beard": mat("Bart", (0.96, 0.95, 0.91), 0.5, bump=(150, 0.4, 0.04), extra="streaks", sheen=0.8),
    "beardbase": mat("BartKern", (0.93, 0.92, 0.88), 0.7, bump=(150, 0.5, 0.04), image=IMG_BEARD, image_mix=0.8),
    "trouser": mat("Hose", (0.40, 0.30, 0.18), 0.85, bump=(70, 0.35, 1.0), sheen=0.3),
    "boot": mat("Stiefel", (0.26, 0.12, 0.06), 0.35, bump=(90, 0.2, 1.0)),
    "sole": mat("Sohle", (0.10, 0.06, 0.04), 0.5),
    "belt": mat("Guertel", (0.12, 0.07, 0.04), 0.4, bump=(90, 0.2, 1.0)),
    "gold": mat("Gold", (0.95, 0.72, 0.25), 0.25),
    "straw": mat("Stroh", (0.80, 0.56, 0.20), 0.9, bump=(90, 0.6, 0.08)),
    "wood": mat("Holz", (0.88, 0.68, 0.45), 0.6, bump=(60, 0.1, 0.1)),
    "string": mat("Schnur", (0.96, 0.94, 0.88), 0.9),
}
M["gold"].node_tree.nodes["Principled BSDF"].inputs["Metallic"].default_value = 1.0
M["hair"] = mat("Haar", (0.36, 0.17, 0.07), 0.45, bump=(150, 0.3, 0.05), sheen=0.6)
M["fur"] = mat("Pelz", (0.97, 0.96, 0.93), 0.9, bump=(90, 0.6, 1.0), sheen=0.8)
M["apron"] = mat("Schuerze", (0.96, 0.92, 0.82), 0.85, bump=(80, 0.25, 1.0))


def stripe_mat(name, c1, c2, freq=95.0):
    """Ringel entlang der Höhe (z) des Objekts."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    pb = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    pb.inputs["Roughness"].default_value = 0.8
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sp = nt.nodes.new("ShaderNodeSeparateXYZ")
    mu = nt.nodes.new("ShaderNodeMath")
    mu.operation = "MULTIPLY"
    mu.inputs[1].default_value = freq
    si = nt.nodes.new("ShaderNodeMath")
    si.operation = "SINE"
    cr = nt.nodes.new("ShaderNodeValToRGB")
    cr.color_ramp.interpolation = "CONSTANT"
    cr.color_ramp.elements[0].position = 0.0
    cr.color_ramp.elements[0].color = (*c1, 1)
    cr.color_ramp.elements.new(0.5)
    cr.color_ramp.elements[1].color = (*c2, 1)
    nt.links.new(tc.outputs["Object"], sp.inputs["Vector"])
    nt.links.new(sp.outputs["Z"], mu.inputs[0])
    nt.links.new(mu.outputs[0], si.inputs[0])
    nt.links.new(si.outputs[0], cr.inputs["Fac"])
    nt.links.new(cr.outputs["Color"], pb.inputs["Base Color"])
    return m


M["stripe"] = stripe_mat("Ringel", (0.78, 0.05, 0.06), (0.97, 0.96, 0.94))


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


def sphere(name, r, loc, scale=(1, 1, 1), material=None, parent=None, rot=(0, 0, 0), seg=40):
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


def cylinder(name, r, z0, z1, material, parent=None, r1=None, seg=32):
    """Abgerundeter Zylinder (Profil mit Kappen, geglättet)."""
    r1 = r if r1 is None else r1
    e = min(r, r1) * 0.5
    prof = [(0, z0), (r * 0.6, z0 - e * 0.3), (r, z0 + e * 0.8), (r1, z1 - e * 0.8), (r1 * 0.6, z1 + e * 0.3), (0, z1)]
    return lathe(name, prof, seg, material, parent, subsurf=2)


# Haarsträhnen: ein Kurvenobjekt mit vielen Strähnen, zur Spitze hin dünner
def taper_object():
    cu = bpy.data.curves.new("Verjuengung", "CURVE")
    cu.dimensions = "3D"
    sp = cu.splines.new("POLY")
    pts = [(0.0, 1.0), (0.25, 0.95), (0.5, 0.75), (0.75, 0.45), (1.0, 0.05)]
    sp.points.add(len(pts) - 1)
    for p, (x, y) in zip(sp.points, pts):
        p.co = (x, y, 0, 1)
    ob = bpy.data.objects.new("Verjuengung", cu)
    sc.collection.objects.link(ob)
    ob.hide_render = True
    return ob


TAPER = taper_object()


def strands(name, strand_list, material, parent, radius=0.0035):
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.resolution_u = 2
    cu.bevel_depth = radius
    cu.bevel_resolution = 1
    cu.taper_object = TAPER
    for pts in strand_list:
        sp = cu.splines.new("POLY")
        sp.points.add(len(pts) - 1)
        for p, c in zip(sp.points, pts):
            p.co = (c[0], c[1], c[2], 1)
    ob = bpy.data.objects.new(name, cu)
    cu.materials.append(material)
    link(ob, parent)
    return ob


# ------------------------------------------------------------------ Figur (gedrungen wie ein Zwerg, Maße in Metern, 1,0 hoch)
rnd = random.Random(11)
root = empty("Elfin", (0, 0, 0))

# Kleid: schlanker Oberkörper, ausgestellter Rock, Schürze, Gürtel, weißer Saum
DRESS = [(0.0, 0.15), (0.205, 0.15), (0.215, 0.165), (0.205, 0.20), (0.170, 0.25), (0.120, 0.30), (0.092, 0.345), (0.098, 0.385), (0.086, 0.418), (0.05, 0.432), (0.0, 0.434)]
dress = lathe("Kleid", DRESS, 64, M["coat"], root, subsurf=2)
dress.scale = (1.0, 0.92, 1.0)
lathe("Saum", [(0.205, 0.146), (0.222, 0.154), (0.222, 0.176), (0.205, 0.184)], 64, M["fur"], root, subsurf=1).scale = (1.0, 0.92, 1.0)
lathe("Guertel", [(0.112, 0.292), (0.130, 0.292), (0.130, 0.322), (0.112, 0.322)], 64, M["red"], root, subsurf=1).scale = (1.0, 0.92, 1.0)
sphere("Schnalle", 0.016, (0, -0.122, 0.307), (1, 0.5, 1), M["gold"], root, seg=16)
# helle Schürze vorn (flache Scheibe auf dem Rock)
ap = sphere("Schuerze", 0.15, (0, -0.094, 0.24), (0.62, 0.20, 1.0), M["apron"], root)
ap.scale = (0.50, 0.16, 0.50)
# Kragen
lathe("Kragen", [(0.062, 0.412), (0.092, 0.414), (0.096, 0.432), (0.062, 0.436)], 48, M["fur"], root, subsurf=1)

# Kopf: groß, runde Wangen, dicke Nase
head = empty("Kopf", (0, 0, 0), root)
sphere("Gesicht", 0.105, (0, 0, 0.457), (1.02, 0.96, 0.96), M["skin"], head)
sphere("Nase", 0.028, (0, -0.1, 0.446), (1, 1.0, 0.9), M["nose"], head)
for sx in (-1, 1):
    sphere("Wange", 0.032, (sx * 0.066, -0.082, 0.448), (1, 0.5, 1), M["cheek"], head, rot=(0, 0, sx * -25))
    cu = bpy.data.curves.new("Auge", "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = 0.0032
    sp = cu.splines.new("POLY")
    pts = [(math.sin(a) * 0.02, 0, -math.cos(a) * 0.009) for a in [i / 6 * math.pi - math.pi / 2 for i in range(7)]]
    sp.points.add(len(pts) - 1)
    for p, c in zip(sp.points, pts):
        p.co = (c[0], c[1], c[2], 1)
    eye = bpy.data.objects.new("Auge", cu)
    eye.location = (sx * 0.047, -0.0955, 0.478)
    eye.rotation_euler = Euler((0, 0, math.radians(sx * 22)))
    cu.materials.append(M["eye"])
    link(eye, head)

# Mütze: Zipfel nach hinten gekippt, weißer Pelzrand, Bommel
hat_profile = [(0.150, 0.535), (0.160, 0.55), (0.158, 0.58), (0.152, 0.608)]
for i in range(1, 44):
    t = i / 43
    z = 0.608 + t * 0.30
    hat_profile.append((0.142 * (1 - t) ** 1.15 + 0.003, z))
hat = lathe("Muetze", hat_profile, 64, M["hat"], head, thick=0.007)
for v in hat.data.vertices:
    z = v.co.z
    t = max(0.0, (z - 0.61) / 0.30)
    v.co.y += 0.16 * t * t
    v.co.x += 0.08 * t ** 3 - (0.02 * math.sin(t * 5) * t)
    v.co.z -= 0.05 * t * t
sub = hat.modifiers.new("glatt", "SUBSURF")
sub.levels = 1
sub.render_levels = 1
rim = lathe("Pelzrand", [(0.150, 0.512), (0.166, 0.518), (0.172, 0.536), (0.166, 0.560), (0.150, 0.566)], 64, M["fur"], head, subsurf=2)
sphere("Bommel", 0.046, (0.083, 0.16, 0.855), (1, 1, 1), M["fur"], head, seg=24)
sphere("Stern", 0.012, (-0.045, -0.152, 0.553), (1, 0.4, 1), M["gold"], head, seg=12)

# Haare: lange Strähnen von der Mütze nach hinten und zu den Seiten, dazu zwei Zöpfe mit Schleifen
hair = []
for i in range(1500):
    al = math.radians(rnd.uniform(95, 265))      # von vorn gemessen, 180 = Hinterkopf
    z0 = 0.52 + rnd.uniform(-0.02, 0.004)
    length = 0.15 + 0.12 * rnd.uniform(0, 1) * (1.0 if 100 < math.degrees(al) < 260 else 0.55)
    pts = []
    for s_ in range(9):
        t = s_ / 8
        r = 0.106 + 0.030 * math.sqrt(t) + rnd.uniform(0, 0.004)
        pts.append((r * math.sin(al) * (1 + 0.08 * t), -r * math.cos(al) * (1 + 0.08 * t), z0 - length * t))
    hair.append(pts)
strands("Haar", hair, M["hair"], head, 0.0052)
for sx in (-1, 1):
    # Zopf: drei Glieder pro Stück, schwingen leicht nach außen
    for k in range(6):
        sphere("Zopf", 0.027 - 0.0015 * k, (sx * (0.116 + 0.004 * k), 0.012, 0.40 - 0.042 * k), (1.0, 1.0, 0.85), M["hair"], head, seg=16)
    sphere("Zopfband", 0.022, (sx * 0.136, 0.012, 0.40 - 0.042 * 6), (1.1, 1.0, 0.7), M["red"], head, seg=14)
    # spitzes Ohr
    ear = sphere("Ohr", 0.05, (sx * 0.108, -0.004, 0.462), (0.18, 0.55, 1.0), M["skin"], head, rot=(0, sx * 50, sx * -10), seg=20)
# Mund: kleines Lächeln
cu = bpy.data.curves.new("Mund", "CURVE")
cu.dimensions = "3D"
cu.bevel_depth = 0.0028
sp = cu.splines.new("POLY")
pts = [(math.sin(a) * 0.022, 0, -math.cos(a) * 0.010) for a in [i / 6 * math.pi * 0.8 - math.pi * 0.4 for i in range(7)]]
sp.points.add(len(pts) - 1)
for p, c in zip(sp.points, pts):
    p.co = (c[0], c[1], -c[2], 1)
mouth = bpy.data.objects.new("Mund", cu)
mouth.location = (0, -0.1, 0.402)
mouth.rotation_euler = Euler((0, 0, 0))
cu.materials.append(M["nose"])
link(mouth, head)

# Arme (Gelenk an der Schulter): schmale Ärmel, weiße Manschette, Hand
arms = {}
for name, sx in (("L", -1), ("R", 1)):
    a = empty("Arm" + name, (sx * 0.112, 0.0, 0.392), root)
    cylinder("Aermel", 0.036, -0.110, 0.012, M["coat"], a, r1=0.042)
    cylinder("Manschette", 0.044, -0.150, -0.104, M["fur"], a)
    sphere("Hand", 0.040, (0, 0, -0.190), (1, 0.95, 1.1), M["skin"], a)
    sphere("Daumen", 0.015, (-sx * 0.032, -0.012, -0.178), (1, 1, 1.3), M["skin"], a)
    hand = empty("Hand" + name, (0, 0, -0.195), a)
    arms[name] = (a, hand)

# Beine: Ringelstrumpfhose und Stiefel mit Zipfel
legs = {}
for name, sx in (("L", -1), ("R", 1)):
    l = empty("Bein" + name, (sx * 0.058, 0.0, 0.15), root)
    cylinder("Strumpf", 0.034, -0.098, 0.0, M["stripe"], l)
    sphere("Stiefel", 0.050, (0, -0.020, -0.102), (1.0, 1.45, 0.92), M["boot"], l)
    sphere("Sohle", 0.050, (0, -0.020, -0.146), (1.03, 1.5, 0.26), M["sole"], l)
    sphere("Zipfel", 0.014, (0, -0.088, -0.118), (1, 1.2, 1), M["gold"], l, seg=12)
    legs[name] = l

# Besen: Griff in der Hand, Borsten unten
broom = empty("Besen", (0, 0, 0), root)
cylinder("Stiel", 0.0095, -0.10, 0.24, M["wood"], broom, seg=16)
bris = lathe("Borsten", [(0.0, -0.10), (0.034, -0.11), (0.050, -0.15), (0.072, -0.24), (0.090, -0.33), (0.0, -0.33)], 40, M["straw"], broom, subsurf=1)
for z, r in ((-0.14, 0.044), (-0.225, 0.068)):
    lathe("Schnur", [(r - 0.004, z - 0.008), (r + 0.004, z - 0.008), (r + 0.004, z + 0.008), (r - 0.004, z + 0.008)], 32, M["string"], broom)
BROOM_REACH = 0.33


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
