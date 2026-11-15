"""Baut den Wichtel in Blender (Blender 4/5, Python). Läuft in der Blender-Konsole oder mit `blender -b -P`.

Maße: Der Wichtel ist 1,0 Einheiten hoch (Füße bei z = 0), schaut nach -Y (zur Kamera), x = rechts.
Jedes Objekt trägt die Eigenschaft `wpart` (head, torso, arml, armr, legl, legr); `render_parts.py` rendert die Teile einzeln.
"""
import math
import bpy
import bmesh
from mathutils import Vector

# ---------------------------------------------------------------- Aufräumen
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.curves, bpy.data.cameras, bpy.data.lights):
    for b in list(coll):
        coll.remove(b)

# ---------------------------------------------------------------- Materialien
def set_in(bsdf, name, value):
    try:
        bsdf.inputs[name].default_value = value
    except Exception:
        pass


def mat(name, color, rough=0.5, sss=0.0, sss_radius=(1.0, 0.4, 0.25), sheen=0.0, spec=0.5, metal=0.0, coat=0.0, bump=0.0, bump_scale=60.0):
    m = bpy.data.materials.new(name)
    try:
        m.use_nodes = True
    except Exception:
        pass
    nt = m.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    set_in(bsdf, "Base Color", (*color, 1.0))
    set_in(bsdf, "Roughness", rough)
    set_in(bsdf, "Metallic", metal)
    set_in(bsdf, "Subsurface Weight", sss)
    set_in(bsdf, "Subsurface Radius", sss_radius)
    set_in(bsdf, "Sheen Weight", sheen)
    set_in(bsdf, "Sheen Roughness", 0.5)
    set_in(bsdf, "Specular IOR Level", spec)
    set_in(bsdf, "Coat Weight", coat)
    set_in(bsdf, "Coat Roughness", 0.2)
    if bump > 0:
        tex = nt.nodes.new("ShaderNodeTexNoise")
        tex.inputs["Scale"].default_value = bump_scale
        tex.inputs["Detail"].default_value = 6
        b = nt.nodes.new("ShaderNodeBump")
        b.inputs["Strength"].default_value = bump
        nt.links.new(tex.outputs["Fac"], b.inputs["Height"])
        nt.links.new(b.outputs["Normal"], bsdf.inputs["Normal"])
    return m


def stripes_mat(name, c1, c2, count=26.0):
    m = mat(name, c1, rough=0.6, sheen=0.4, bump=0.15, bump_scale=90)
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Object"], sep.inputs["Vector"])
    wave = nt.nodes.new("ShaderNodeTexWave")
    wave.wave_type = 'BANDS'
    wave.bands_direction = 'Z'
    wave.inputs["Scale"].default_value = count
    wave.inputs["Distortion"].default_value = 0.0
    nt.links.new(tc.outputs["Object"], wave.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.interpolation = 'CONSTANT'
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (*c1, 1)
    ramp.color_ramp.elements[1].position = 0.62
    ramp.color_ramp.elements[1].color = (*c2, 1)
    nt.links.new(wave.outputs["Fac"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
    return m


SKIN = mat("skin", (0.93, 0.66, 0.52), rough=0.45, sss=0.35, sss_radius=(1.0, 0.35, 0.2), spec=0.4)
BLUSH = mat("blush", (0.95, 0.45, 0.45), rough=0.5, sss=0.3)
GREEN = mat("green", (0.07, 0.42, 0.13), rough=0.6, sheen=0.5, bump=0.12, bump_scale=120)
GREEN_DARK = mat("green_dark", (0.04, 0.27, 0.08), rough=0.6, sheen=0.4)
RED = mat("red", (0.62, 0.05, 0.05), rough=0.45, bump=0.05)
FUR = mat("fur", (0.96, 0.96, 0.98), rough=0.95, sheen=0.9, sss=0.2, sss_radius=(0.5, 0.5, 0.5))
BROWN = mat("brown", (0.30, 0.13, 0.07), rough=0.45, coat=0.3)
HAIR = mat("hair", (0.36, 0.16, 0.08), rough=0.5, sheen=0.3)
GOLD = mat("gold", (0.85, 0.62, 0.20), rough=0.25, metal=1.0)
BOOT = mat("boot", (0.45, 0.10, 0.06), rough=0.4, coat=0.3, bump=0.25, bump_scale=40)
EYE_W = mat("eye_white", (0.97, 0.97, 0.97), rough=0.1, coat=0.5)
IRIS = mat("iris", (0.12, 0.45, 0.85), rough=0.15, coat=0.6)
BLACK = mat("black", (0.01, 0.01, 0.01), rough=0.2)
MOUTH = mat("mouth", (0.45, 0.10, 0.10), rough=0.4)
LEGS = stripes_mat("leggings", (0.07, 0.42, 0.13), (0.55, 0.25, 0.05))

# ---------------------------------------------------------------- Helfer
def finish(o, part, m, smooth=True, subsurf=0):
    o["wpart"] = part
    o.data.materials.clear()
    o.data.materials.append(m)
    if smooth:
        for p in o.data.polygons:
            p.use_smooth = True
    if subsurf:
        s = o.modifiers.new("subsurf", 'SUBSURF')
        s.levels = subsurf
        s.render_levels = subsurf
    return o


def sphere(name, part, loc, scale, m, rot=(0, 0, 0), seg=48, rings=24, subsurf=0):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1, location=loc, rotation=rot, segments=seg, ring_count=rings)
    o = bpy.context.active_object
    o.name = name
    o.scale = scale
    return finish(o, part, m, subsurf=subsurf)


def cyl(name, part, loc, r, depth, m, rot=(0, 0, 0), verts=40, scale=(1, 1, 1), subsurf=0):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = scale
    return finish(o, part, m, subsurf=subsurf)


def cone(name, part, loc, r1, r2, depth, m, rot=(0, 0, 0), verts=32, scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r1, radius2=r2, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = scale
    return finish(o, part, m)


def torus(name, part, loc, major, minor, m, rot=(0, 0, 0), scale=(1, 1, 1), ms=64, ns=20):
    bpy.ops.mesh.primitive_torus_add(location=loc, rotation=rot, major_radius=major, minor_radius=minor, major_segments=ms, minor_segments=ns)
    o = bpy.context.active_object
    o.name = name
    o.scale = scale
    return finish(o, part, m)


def box(name, part, loc, size, m, bevel=0.003):
    bpy.ops.mesh.primitive_cube_add(location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = (size[0] / 2, size[1] / 2, size[2] / 2)
    bpy.ops.object.transform_apply(scale=True)
    b = o.modifiers.new("bevel", 'BEVEL')
    b.width = bevel
    b.segments = 3
    return finish(o, part, m)


def tube(name, part, pts, r0, r1, m, res=16):
    """Gebogene Röhre durch Punkte, Radius läuft von r0 nach r1 (Kurve mit Bevel und Verjüngung)."""
    cu = bpy.data.curves.new(name, 'CURVE')
    cu.dimensions = '3D'
    cu.bevel_depth = r0
    cu.bevel_resolution = 6
    cu.use_fill_caps = True
    sp = cu.splines.new('BEZIER')
    sp.bezier_points.add(len(pts) - 1)
    for p, v in zip(sp.bezier_points, pts):
        p.co = v
        p.handle_left_type = p.handle_right_type = 'AUTO'
        p.radius = 1.0
    sp.bezier_points[-1].radius = max(0.05, r1 / r0)
    o = bpy.data.objects.new(name, cu)
    bpy.context.collection.objects.link(o)
    o["wpart"] = part
    o.data.materials.append(m)
    return o


# ---------------------------------------------------------------- Kopf (head)
HEAD_Z = 0.645
sphere("skull", "head", (0, 0, HEAD_Z), (0.128, 0.118, 0.132), SKIN, subsurf=1)
# volle Wangen und Kinn
sphere("cheekL", "head", (-0.066, -0.062, HEAD_Z - 0.045), (0.062, 0.05, 0.055), SKIN, subsurf=1)
sphere("cheekR", "head", (0.066, -0.062, HEAD_Z - 0.045), (0.062, 0.05, 0.055), SKIN, subsurf=1)
sphere("chin", "head", (0, -0.05, HEAD_Z - 0.088), (0.06, 0.052, 0.04), SKIN, subsurf=1)
# Rouge
sphere("blushL", "head", (-0.078, -0.103, HEAD_Z - 0.04), (0.03, 0.009, 0.022), BLUSH)
sphere("blushR", "head", (0.078, -0.103, HEAD_Z - 0.04), (0.03, 0.009, 0.022), BLUSH)
# Nase
sphere("nose", "head", (0, -0.122, HEAD_Z - 0.035), (0.024, 0.026, 0.022), SKIN, subsurf=1)
# Augen
for side, sx in (("L", -1), ("R", 1)):
    sphere(f"eye{side}", "head", (sx * 0.047, -0.106, HEAD_Z - 0.01), (0.03, 0.022, 0.033), EYE_W)
    sphere(f"iris{side}", "head", (sx * 0.047, -0.124, HEAD_Z - 0.012), (0.019, 0.007, 0.021), IRIS)
    sphere(f"pupil{side}", "head", (sx * 0.047, -0.1285, HEAD_Z - 0.012), (0.0095, 0.004, 0.011), BLACK)
    sphere(f"glint{side}", "head", (sx * 0.0425, -0.1315, HEAD_Z - 0.004), (0.0048, 0.002, 0.0048), EYE_W)
    sphere(f"brow{side}", "head", (sx * 0.05, -0.108, HEAD_Z + 0.037), (0.034, 0.011, 0.0085), HAIR, rot=(0, math.radians(sx * 14), 0))
# Mund (geschlossenes Lächeln); beim Sprechen zeichnet die App einen offenen Mund darüber
tube("smile", "head", [Vector((-0.036, -0.114, HEAD_Z - 0.085)), Vector((-0.012, -0.124, HEAD_Z - 0.101)),
                        Vector((0.012, -0.124, HEAD_Z - 0.101)), Vector((0.036, -0.114, HEAD_Z - 0.085))], 0.0042, 0.0042, MOUTH)
# Ohren: lang, spitz, nach außen oben
for side, sx in (("L", -1), ("R", 1)):
    e = cone(f"ear{side}", "head", (sx * 0.185, -0.005, HEAD_Z + 0.025), 0.045, 0.0, 0.15, SKIN,
             rot=(0, math.radians(sx * 78), 0), scale=(1, 0.32, 1))
    cone(f"earIn{side}", "head", (sx * 0.178, -0.017, HEAD_Z + 0.022), 0.028, 0.0, 0.11, BLUSH,
         rot=(0, math.radians(sx * 78), 0), scale=(1, 0.2, 1))
# Haar: Stirnfransen unter dem Mützenrand und Koteletten
for i, (hx, hz, sc) in enumerate([(-0.082, 0.0, 1.0), (-0.042, 0.006, 1.1), (0.0, 0.01, 1.15), (0.042, 0.006, 1.1), (0.082, 0.0, 1.0)]):
    sphere(f"fringe{i}", "head", (hx, -0.104, HEAD_Z + 0.047 + hz), (0.042 * sc, 0.026, 0.022), HAIR, rot=(0, math.radians(hx * 220), 0))
for side, sx in (("L", -1), ("R", 1)):
    sphere(f"burn{side}", "head", (sx * 0.118, -0.045, HEAD_Z + 0.015), (0.018, 0.03, 0.055), HAIR)
# Mütze: aus Ringen gebaut (saubere Vierecke), Zipfel geknickt und hängend
SEG, RINGS = 56, 30
hat_mesh = bpy.data.meshes.new("hat")
hat = bpy.data.objects.new("hat", hat_mesh)
bpy.context.collection.objects.link(hat)
bm = bmesh.new()
rings = []
for i in range(RINGS + 1):
    t = i / RINGS
    r = 0.142 * (1 - t) + 0.0015
    cx = 0.15 * t * t * 0.9
    cy = 0.035 * t * t
    z = 0.70 + 0.31 * t - 0.05 * t ** 3
    rings.append([bm.verts.new((cx + r * math.cos(2 * math.pi * k / SEG), cy + r * math.sin(2 * math.pi * k / SEG), z)) for k in range(SEG)])
for i in range(RINGS):
    for k in range(SEG):
        bm.faces.new((rings[i][k], rings[i][(k + 1) % SEG], rings[i + 1][(k + 1) % SEG], rings[i + 1][k]))
bm.normal_update()
bm.to_mesh(hat_mesh)
bm.free()
finish(hat, "head", GREEN)
# Mützenband (rot) und Falte, Bommel
def hat_x(z):
    t = max(0.0, (z - 0.70) / 0.31)
    return 0.15 * t * t * 0.9, 0.035 * t * t


for zb, rr, minor in ((0.745, 0.131, 0.011), (0.795, 0.108, 0.0085)):
    hx, hy = hat_x(zb)
    t = (zb - 0.70) / 0.31
    radius = 0.142 * (1 - t) + 0.001
    torus("band", "head", (hx, hy, zb), radius, minor, RED if zb > 0.76 else GREEN_DARK, scale=(1, 1, 1))
hx, hy = hat_x(0.995)
sphere("bell", "head", (hx, hy - 0.002, 0.968), (0.018, 0.018, 0.018), GOLD)
torus("hatTrim", "head", (0, 0, 0.703), 0.1415, 0.0075, GREEN_DARK)

# ---------------------------------------------------------------- Körper (torso)
sphere("tunic", "torso", (0, 0, 0.385), (0.122, 0.092, 0.158), GREEN, subsurf=1)
# Zipfel-Saum
bpy.ops.mesh.primitive_cone_add(vertices=14, radius1=0.135, radius2=0.108, depth=0.085, location=(0, 0, 0.255))
hem = bpy.context.active_object
hem.name = "hem"
hem.scale = (1, 0.8, 1)
bm = bmesh.new()
bm.from_mesh(hem.data)
bottom = sorted([v for v in bm.verts if v.co.z < 0], key=lambda v: math.atan2(v.co.y, v.co.x))
for i, v in enumerate(bottom):
    v.co.z -= 0.035 if i % 2 == 0 else 0.0
bm.to_mesh(hem.data)
bm.free()
finish(hem, "torso", GREEN, smooth=False)
# Gürtel und Schnalle
torus("belt", "torso", (0, 0, 0.335), 0.118, 0.0165, BROWN, scale=(1.0, 0.77, 1.0))
box("buckle", "torso", (0, -0.093, 0.335), (0.056, 0.012, 0.046), GOLD, bevel=0.004)
box("buckleIn", "torso", (0, -0.1, 0.335), (0.034, 0.012, 0.026), BROWN, bevel=0.002)
box("prong", "torso", (0, -0.1055, 0.335), (0.006, 0.005, 0.03), GOLD, bevel=0.001)
# Fell-Kragen: Wulst plus viele Zotteln
torus("collarBase", "torso", (0, -0.005, 0.505), 0.088, 0.026, FUR, scale=(1.0, 0.82, 0.9))
import random
rnd = random.Random(7)
N = 24
for i in range(N):
    a = 2 * math.pi * i / N
    cx, cy = 0.094 * math.cos(a), 0.079 * math.sin(a)
    front = max(0.0, -math.sin(a))
    ln = 0.034 + 0.02 * front + rnd.uniform(-0.004, 0.004)
    z = 0.488 - 0.02 * front
    t = sphere(f"fur{i}", "torso", (cx, cy - 0.004, z), (0.024, 0.02, ln), FUR, seg=20, rings=10)
    t.rotation_euler = (math.radians(rnd.uniform(-10, 10) + 14 * math.sin(a)), math.radians(-18 * math.cos(a) + rnd.uniform(-8, 8)), 0)
for i in range(14):
    a = 2 * math.pi * (i + 0.5) / 14
    sphere(f"furTop{i}", "torso", (0.085 * math.cos(a), 0.07 * math.sin(a), 0.515), (0.026, 0.022, 0.026), FUR, seg=20, rings=10)

# ---------------------------------------------------------------- Arme
for side, sx, part in (("L", -1, "arml"), ("R", 1, "armr")):
    ang = math.radians(sx * 8)
    sphere(f"shoulder{side}", part, (sx * 0.112, 0, 0.472), (0.04, 0.038, 0.04), GREEN, subsurf=1)
    cyl(f"sleeve{side}", part, (sx * 0.1235, 0, 0.395), 0.031, 0.155, GREEN, rot=(0, ang, 0), subsurf=0, verts=40)
    # Fellmanschette
    torus(f"cuff{side}", part, (sx * 0.1345, 0, 0.3165), 0.031, 0.0155, FUR, rot=(0, ang, 0), scale=(1, 1, 1.0))
    for k in range(10):
        a = 2 * math.pi * k / 10
        cone(f"cuffFur{side}{k}", part, (sx * 0.1345 + 0.032 * math.cos(a), 0.032 * math.sin(a), 0.3165 - 0.006), 0.011, 0.0, 0.03, FUR,
             rot=(math.radians(180), 0, 0), verts=10)
    # Hand
    sphere(f"hand{side}", part, (sx * 0.1395, 0, 0.283), (0.0295, 0.027, 0.034), SKIN, subsurf=1)
    sphere(f"thumb{side}", part, (sx * 0.1195, -0.02, 0.292), (0.011, 0.012, 0.02), SKIN, rot=(0, math.radians(sx * 20), 0))
    for k in range(3):
        sphere(f"finger{side}{k}", part, (sx * (0.1395 + (k - 1) * 0.0001), -0.006 + (k - 1) * 0.011, 0.262), (0.0095, 0.0095, 0.019), SKIN)

# ---------------------------------------------------------------- Beine
for side, sx, part in (("L", -1, "legl"), ("R", 1, "legr")):
    x = sx * 0.055
    cyl(f"leg{side}", part, (x, 0, 0.17), 0.031, 0.19, LEGS, verts=40)
    cyl(f"shaft{side}", part, (x, 0, 0.082), 0.041, 0.072, BOOT, verts=40)
    torus(f"bootCuff{side}", part, (x, 0, 0.12), 0.042, 0.0075, BOOT)
    # Fuß in eigener Gruppe, nach außen gedreht, damit die gebogene Spitze von vorn zu sehen ist
    emp = bpy.data.objects.new(f"foot{side}Grp", None)
    bpy.context.collection.objects.link(emp)
    emp.location = (x, 0, 0)
    emp.rotation_euler = (0, 0, math.radians(sx * 24))
    parts_here = [
        sphere(f"foot{side}", part, (0, -0.045, 0.036), (0.046, 0.098, 0.036), BOOT, subsurf=1),
        sphere(f"sole{side}", part, (0, -0.045, 0.012), (0.047, 0.101, 0.014), BROWN),
        tube(f"toe{side}", part, [Vector((0, -0.115, 0.04)), Vector((0.0, -0.158, 0.05)),
                                  Vector((0.0, -0.176, 0.085)), Vector((0.0, -0.158, 0.118)), Vector((0.0, -0.13, 0.128))], 0.034, 0.011, BOOT),
    ]
    for o in parts_here:
        o.parent = emp

# ---------------------------------------------------------------- Licht, Welt, Kamera
sc = bpy.context.scene
sc.render.engine = 'CYCLES'
sc.cycles.samples = 64
sc.cycles.use_denoising = True
sc.cycles.device = 'CPU'
sc.render.film_transparent = True
try:
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
except Exception:
    pass
w = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
sc.world = w
try:
    w.use_nodes = True
except Exception:
    pass
bg = w.node_tree.nodes.get("Background")
if bg:
    bg.inputs["Color"].default_value = (0.9, 0.92, 0.96, 1)
    bg.inputs["Strength"].default_value = 0.35


def area(name, loc, target, energy, size, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy = energy
    ld.size = size
    ld.color = color
    o = bpy.data.objects.new(name, ld)
    bpy.context.collection.objects.link(o)
    o.location = loc
    d = Vector(target) - Vector(loc)
    o.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    return o


area("key", (-1.4, -2.2, 1.9), (0, 0, 0.5), 140, 2.4, (1.0, 0.96, 0.9))
area("fill", (2.0, -1.8, 0.9), (0, 0, 0.5), 40, 2.5, (0.9, 0.95, 1.0))
area("rim", (0.3, 1.8, 1.6), (0, 0, 0.6), 70, 1.5)

cam_data = bpy.data.cameras.new("cam")
cam_data.type = 'ORTHO'
cam_data.ortho_scale = 1.5          # Breite in Einheiten -> 1500 px = 1000 px pro Einheit
cam = bpy.data.objects.new("cam", cam_data)
bpy.context.collection.objects.link(cam)
cam.location = (0, -10, 0.45)
cam.rotation_euler = (math.radians(90), 0, 0)
sc.camera = cam
sc.render.resolution_x = 1500
sc.render.resolution_y = 1300
sc.render.resolution_percentage = 100
print("Wichtel gebaut:", len([o for o in bpy.data.objects if "wpart" in o.keys()]), "Objekte")
