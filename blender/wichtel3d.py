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
root = empty("Wichtel", (0, 0, 0))

# Mantel: Tonnenform mit Saum
coat = lathe("Mantel", [(0.0, 0.14), (0.200, 0.14), (0.214, 0.155), (0.216, 0.20), (0.204, 0.27), (0.170, 0.345),
                        (0.128, 0.392), (0.080, 0.418), (0.0, 0.43)], 64, M["coat"], root, subsurf=2)
coat.scale = (1.0, 0.92, 1.0)
lathe("Saum", [(0.205, 0.138), (0.222, 0.146), (0.222, 0.166), (0.205, 0.172)], 64, M["coat"], root, subsurf=1).scale = (1.0, 0.92, 1.0)
lathe("Guertel", [(0.206, 0.232), (0.224, 0.232), (0.224, 0.262), (0.206, 0.262)], 64, M["belt"], root, subsurf=1).scale = (1.0, 0.92, 1.0)

# Kopf: groß, runde Wangen, dicke Nase
head = empty("Kopf", (0, 0, 0), root)
sphere("Gesicht", 0.105, (0, 0, 0.457), (1.02, 0.96, 0.96), M["skin"], head)
sphere("Nase", 0.046, (0, -0.104, 0.442), (1, 1.1, 0.95), M["nose"], head)
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

# Mütze: weit, mit Faltenwurf, nach hinten gekippt und eingerollt, dickes Band mit Nieten
hat_profile = [(0.178, 0.515), (0.186, 0.525), (0.188, 0.56), (0.182, 0.592)]
for i in range(1, 44):
    t = i / 43
    z = 0.592 + t * 0.408
    hat_profile.append((0.168 * (1 - t) ** 1.12 + 0.004, z))
hat = lathe("Muetze", hat_profile, 64, M["hat"], head, thick=0.007)
for v in hat.data.vertices:
    z = v.co.z
    t = max(0.0, (z - 0.62) / 0.38)
    v.co.y += 0.21 * t * t
    v.co.x += 0.09 * t ** 3 - (0.03 * math.sin(t * 5) * t)
    v.co.z -= 0.075 * t * t
folds = bpy.data.textures.new("Falten", "CLOUDS")
folds.noise_scale = 0.18
disp = hat.modifiers.new("Falten", "DISPLACE")
disp.texture = folds
disp.strength = 0.016
disp.mid_level = 0.5
sub = hat.modifiers.new("glatt", "SUBSURF")
sub.levels = 1
sub.render_levels = 1
for i in range(18):
    a = i / 18 * 2 * math.pi
    sphere("Niete", 0.0095, (math.sin(a) * 0.1885, -math.cos(a) * 0.1885, 0.557), (1, 1, 1), M["stud"], head, seg=14)

# Bart: Kern aus einer Masse (Textur aus dem Referenzbild) plus über tausend einzelne, gewellte Strähnen
bm = bmesh.new()
bmesh.ops.create_uvsphere(bm, u_segments=48, v_segments=32, radius=1.0)
for v in bm.verts:
    x, y, z = v.co
    if z < 0:
        k = (1 + z) ** 0.9
        x *= k
        y *= k * 0.9
    zz = 0.30 + (z * 0.12 if z >= 0 else z * 0.16)
    v.co = (x * 0.125, -0.115 + y * 0.065, zz)
me = bpy.data.meshes.new("BartKern")
bm.to_mesh(me)
bm.free()
finish(me)
bcore = bpy.data.objects.new("BartKern", me)
me.materials.append(M["beardbase"])
link(bcore, head)

COAT_PROFILE = [(0.14, 0.200), (0.155, 0.214), (0.20, 0.216), (0.27, 0.204), (0.345, 0.170), (0.392, 0.128), (0.418, 0.080)]


def coat_r(z):
    """Halbmesser des Mantels in Höhe z (x-Richtung); in y-Richtung ist er auf 92 % gestaucht."""
    for (z0, r0), (z1, r1) in zip(COAT_PROFILE, COAT_PROFILE[1:]):
        if z0 <= z <= z1:
            return r0 + (r1 - r0) * (z - z0) / (z1 - z0)
    return 0.2 if z < 0.14 else 0.07


beard = []
for i in range(2200):
    u = rnd.uniform(-1, 1)
    th = u * math.radians(86)
    zc = 0.432 - 0.03 * abs(u) + rnd.uniform(-0.01, 0.012)
    R0 = 0.100
    length = (0.09 + 0.25 * max(0.0, math.cos(th)) ** 1.3) * rnd.uniform(0.85, 1.12)
    ph = rnd.uniform(0, 6.28)
    wob = rnd.uniform(0.006, 0.016)
    pts = []
    steps = 10
    for s in range(steps + 1):
        t = s / steps
        z = zc - length * t - 0.02 * t * t
        r = R0 + (0.11 + 0.05 * abs(u) + rnd.uniform(0, 0.006)) * math.sqrt(t)
        a = th * (1 - 0.3 * t)
        x = r * math.sin(a) + wob * math.sin(5 * t + ph) * t + (-0.02 * u * t)
        y = -r * math.cos(a) * 0.92 - 0.006 * math.sin(3 * t + ph) * t
        # nie in den Mantel hinein: vor dem Bauch liegt der Bart auf dem Mantel
        ymin = -(coat_r(z) * 0.92 + 0.016 + rnd.uniform(0, 0.012) * t)
        if abs(x) < coat_r(z) * 0.95 and y > ymin:
            y = ymin
        pts.append((x, y, z))
    beard.append(pts)
strands("BartStraehnen", beard, M["beard"], head, 0.0062)

mus = []
for sx in (-1, 1):
    for i in range(160):
        t0 = rnd.uniform(0, 1)
        x0 = sx * (0.014 + 0.004 * rnd.uniform(0, 1))
        z0 = 0.428 - 0.018 * rnd.uniform(0, 1)
        pts = []
        L = 0.105 + 0.02 * rnd.uniform(-1, 1)
        for s in range(9):
            t = s / 8
            x = x0 + sx * L * t * (1 - 0.1 * t)
            y = -0.109 - 0.03 * math.sin(math.pi * t * 0.8) - 0.006 * rnd.uniform(0, 1)
            z = z0 - 0.02 * math.sin(math.pi * t) + 0.045 * t ** 2.2 - 0.012 * t0
            pts.append((x, y, z))
        mus.append(pts)
strands("Schnurrbart", mus, M["beard"], head, 0.0058)

brow = []
for sx in (-1, 1):
    for i in range(40):
        pts = []
        x0 = sx * rnd.uniform(0.018, 0.034)
        z0 = 0.497 + rnd.uniform(-0.004, 0.006)
        for s in range(6):
            t = s / 5
            pts.append((x0 + sx * 0.046 * t, -0.093 + 0.012 * t - rnd.uniform(0, 0.004), z0 + 0.012 * math.sin(math.pi * t * 0.9) + 0.006 * t))
        brow.append(pts)
strands("Augenbrauen", brow, M["beard"], head, 0.0036)

hair = []
for i in range(520):
    al = math.radians(rnd.uniform(70, 290))      # von vorn gemessen, 180 = Hinterkopf
    z0 = 0.505 + rnd.uniform(-0.01, 0.003)
    length = 0.13 + 0.07 * rnd.uniform(0, 1)
    pts = []
    for s in range(8):
        t = s / 7
        r = 0.108 + 0.028 * math.sqrt(t) + rnd.uniform(0, 0.004)
        pts.append((r * math.sin(al) * (1 + 0.1 * t), -r * math.cos(al) * (1 + 0.1 * t), z0 - length * t))
    hair.append(pts)
strands("Haar", hair, M["beard"], head, 0.0058)

# Arme (Gelenk an der Schulter): dicke Ärmel, breite rote Manschette, Fäustling mit Daumen
arms = {}
for name, sx in (("L", -1), ("R", 1)):
    a = empty("Arm" + name, (sx * 0.172, 0.0, 0.385), root)
    cylinder("Aermel", 0.052, -0.115, 0.01, M["coat"], a, r1=0.058)
    cylinder("Manschette", 0.060, -0.162, -0.108, M["red"], a)
    sphere("Faeustling", 0.056, (0, 0, -0.205), (1, 0.95, 1.12), M["red"], a)
    sphere("Daumen", 0.024, (-sx * 0.048, -0.014, -0.188), (1, 1, 1.3), M["red"], a)
    hand = empty("Hand" + name, (0, 0, -0.205), a)
    arms[name] = (a, hand)

# Beine: kurz und kräftig, große Stiefel mit Sohle
legs = {}
for name, sx in (("L", -1), ("R", 1)):
    l = empty("Bein" + name, (sx * 0.075, 0.0, 0.15), root)
    cylinder("Hose", 0.052, -0.092, 0.0, M["trouser"], l)
    sphere("Stiefel", 0.062, (0, -0.024, -0.102), (1.0, 1.5, 0.92), M["boot"], l)
    sphere("Sohle", 0.062, (0, -0.024, -0.146), (1.03, 1.54, 0.26), M["sole"], l)
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
