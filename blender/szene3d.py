"""Gegenstände fürs Bild: Tannenbaum, Bett, Zaun, Laterne, Lichterkette, Neonschild – Cyberpunk-Weihnacht mit Neonlicht.

Aufruf:  blender --factory-startup -b -P szene3d.py -- ausgabe_ordner [samples] [nur_ein_gegenstand]

Jeder Gegenstand wird mit durchsichtigem Hintergrund und weichem Bodenschatten gerendert (ausgabe/<id>.png) und bekommt eine
<id>.json mit Fußpunkt, Größe und den Lichtern (Lampen, Neonspots), die die App zusätzlich leuchten und blinken lässt.
"""
import json
import math
import os
import random
import sys

import bmesh
import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Euler, Vector

args = sys.argv[sys.argv.index("--") + 1:]
OUT = args[0]
SAMPLES = int(args[1]) if len(args) > 1 else 48
ONLY = args[2] if len(args) > 2 else None
os.makedirs(OUT, exist_ok=True)
PITCH = 10.0
rnd = random.Random(5)

NEON = {
    "cyan": (0.0, 0.85, 1.0),
    "magenta": (1.0, 0.1, 0.65),
    "amber": (1.0, 0.62, 0.12),
    "violet": (0.55, 0.25, 1.0),
    "green": (0.15, 1.0, 0.45),
    "white": (1.0, 0.92, 0.85),
}


def hexcol(c):
    return "#%02X%02X%02X" % tuple(int(max(0, min(1, v)) ** (1 / 2.2) * 255) for v in c)


# ------------------------------------------------------------------ Aufbau-Hilfen
def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.cycles.samples = SAMPLES
    sc.cycles.use_denoising = True
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.view_settings.view_transform = "Standard"
    return sc


def M_pbr(name, color, rough=0.5, metal=0.0, bump=None, emit=None, emit_strength=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    pb = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    pb.inputs["Base Color"].default_value = (*color, 1)
    pb.inputs["Roughness"].default_value = rough
    pb.inputs["Metallic"].default_value = metal
    if emit is not None:
        pb.inputs["Emission Color"].default_value = (*emit, 1)
        pb.inputs["Emission Strength"].default_value = emit_strength
    if bump:
        scale, strength = bump[:2]
        stretch = bump[2] if len(bump) > 2 else 1.0
        tc = nt.nodes.new("ShaderNodeTexCoord")
        mp = nt.nodes.new("ShaderNodeMapping")
        mp.inputs["Scale"].default_value = (scale, scale, scale * stretch)
        nz = nt.nodes.new("ShaderNodeTexNoise")
        nz.inputs["Detail"].default_value = 8
        bp = nt.nodes.new("ShaderNodeBump")
        bp.inputs["Strength"].default_value = strength
        bp.inputs["Distance"].default_value = 0.02
        nt.links.new(tc.outputs["Object"], mp.inputs["Vector"])
        nt.links.new(mp.outputs["Vector"], nz.inputs["Vector"])
        nt.links.new(nz.outputs["Fac"], bp.inputs["Height"])
        nt.links.new(bp.outputs["Normal"], pb.inputs["Normal"])
    return m


def M_emit(name, color, strength=12.0):
    return M_pbr(name, (0.02, 0.02, 0.02), 0.4, emit=color, emit_strength=strength)


STEEL = None
LIGHTS = []   # (Position, Farbe, Radius in m, Blinkgeschwindigkeit, Phase)


def mats():
    global STEEL
    STEEL = M_pbr("Stahl", (0.025, 0.027, 0.04), 0.35, 0.9, bump=(80, 0.15))


def add(ob, mat=None, parent=None):
    bpy.context.scene.collection.objects.link(ob)
    if mat is not None and hasattr(ob.data, "materials"):
        ob.data.materials.append(mat)
    if parent is not None:
        ob.parent = parent
    return ob


def smooth(me):
    for p in me.polygons:
        p.use_smooth = True


def box(name, size, loc, mat, bevel=0.01, parent=None, rot=(0, 0, 0), sub=1):
    """Quader mit echten Maßen (Meter) und schmal abgerundeten Kanten."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x *= size[0]
        v.co.y *= size[1]
        v.co.z *= size[2]
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    ob.rotation_euler = Euler([math.radians(a) for a in rot])
    add(ob, mat, parent)
    b = ob.modifiers.new("Kante", "BEVEL")
    b.width = min(bevel, min(size) * 0.45)
    b.segments = 3
    b.limit_method = "ANGLE"
    smooth(me)
    return ob


def sphere(name, r, loc, mat, scale=(1, 1, 1), seg=24, parent=None):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=seg, v_segments=seg // 2, radius=r)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    smooth(me)
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    ob.scale = scale
    add(ob, mat, parent)
    return ob


def lathe(name, profile, mat, steps=48, loc=(0, 0, 0), subsurf=1, parent=None):
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
    smooth(me)
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    add(ob, mat, parent)
    if subsurf:
        s = ob.modifiers.new("glatt", "SUBSURF")
        s.levels = subsurf
        s.render_levels = subsurf
    return ob


def tube(name, pts, radius, mat, loc=(0, 0, 0), closed=False, parent=None):
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = radius
    cu.bevel_resolution = 3
    cu.resolution_u = 12
    sp = cu.splines.new("POLY")
    sp.points.add(len(pts) - 1)
    for p, c in zip(sp.points, pts):
        p.co = (c[0], c[1], c[2], 1)
    sp.use_cyclic_u = closed
    ob = bpy.data.objects.new(name, cu)
    ob.location = loc
    cu.materials.append(mat)
    add(ob, None, parent)
    return ob


def cyl(name, r, z0, z1, mat, loc=(0, 0, 0), seg=32, parent=None, r1=None):
    r1 = r if r1 is None else r1
    return lathe(name, [(0, z0), (r, z0), (r1, z1), (0, z1)], mat, seg, loc, 1, parent)


def light_mark(pos, color, radius, speed=0.0, phase=0.0):
    LIGHTS.append((Vector(pos), color, radius, speed, phase))


def area(name, loc, target, energy, size, color=(1, 1, 1)):
    li = bpy.data.lights.new(name, "AREA")
    li.energy = energy
    li.size = size
    li.color = color
    ob = bpy.data.objects.new(name, li)
    ob.location = loc
    bpy.context.scene.collection.objects.link(ob)
    ob.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()


def stage(sc, zc, vert_m, W, H, ground_r=3.0):
    """Kamera (von vorn, leicht von oben), Licht im Cyberpunk-Stil, Bodenschatten."""
    sc.render.resolution_x, sc.render.resolution_y = W, H
    cam = bpy.data.objects.new("Kamera", bpy.data.cameras.new("Kamera"))
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = vert_m * (W / H) if W > H else vert_m
    cam.data.clip_end = 80
    d = 12.0
    cam.location = (0, -d * math.cos(math.radians(PITCH)), zc + d * math.sin(math.radians(PITCH)))
    cam.rotation_euler = Euler((math.radians(90 - PITCH), 0, 0))
    sc.collection.objects.link(cam)
    sc.camera = cam
    world = bpy.data.worlds.new("Welt")
    sc.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.10, 0.12, 0.22, 1)
    bg.inputs[1].default_value = 0.55
    area("Haupt", (-2.5, -4.0, 4.5), (0, 0, zc), 520, 3.5, (0.85, 0.92, 1.0))
    area("Rand-Magenta", (-3.0, 3.0, 2.5), (0, 0, zc), 700, 3.0, NEON["magenta"])
    area("Rand-Cyan", (3.2, 2.5, 2.5), (0, 0, zc), 700, 3.0, NEON["cyan"])
    area("Auffuellen", (3.0, -3.0, 1.2), (0, 0, zc), 120, 3.0, (0.8, 0.85, 1.0))
    # Bodenschatten
    bpy.ops.mesh.primitive_plane_add(size=ground_r * 2, location=(0, 0, -0.002))
    g = bpy.context.active_object
    g.is_shadow_catcher = True
    return cam


def finish_render(sc, cam, name, W, H, vert_m, anchor_world=(0, 0, 0), ground=None):
    bpy.context.view_layer.update()
    sc.render.filepath = os.path.join(OUT, name + ".png")
    bpy.ops.render.render(write_still=True)
    a = world_to_camera_view(sc, cam, Vector(anchor_world))
    px_per_m = H / (vert_m)
    if W > H:
        px_per_m = H / vert_m
    lights = []
    for pos, color, radius, speed, phase in LIGHTS:
        c = world_to_camera_view(sc, cam, pos)
        lights.append({
            "x": round(c.x * W, 1), "y": round((1 - c.y) * H, 1), "r": round(radius * px_per_m, 1),
            "color": hexcol(color), "speed": round(speed, 2), "phase": round(phase, 2),
        })
    meta = {"size": [W, H], "anchor": [round(a.x * W, 1), round((1 - a.y) * H, 1)], "lights": lights}
    if ground:
        meta["ground"] = {"color": hexcol(ground[0]), "rx": round(ground[1] * px_per_m, 1)}
    json.dump(meta, open(os.path.join(OUT, name + ".json"), "w"), indent=1)
    print("fertig:", name, len(lights), "Lichter", flush=True)


# ------------------------------------------------------------------ Gegenstände
def build_tree():
    mats()
    needle = M_pbr("Nadeln", (0.012, 0.11, 0.055), 0.75, bump=(140, 0.9, 1.0))
    root = bpy.data.objects.new("Baum", None)
    bpy.context.scene.collection.objects.link(root)
    cyl("Topf", 0.24, 0.0, 0.26, STEEL, parent=root, r1=0.28)
    ring = M_emit("TopfRing", NEON["cyan"], 14)
    lathe("TopfLicht", [(0.2815, 0.10), (0.292, 0.11), (0.292, 0.14), (0.2815, 0.15)], ring, 64, subsurf=0, parent=root)
    cyl("Stamm", 0.05, 0.25, 0.7, M_pbr("Holz", (0.1, 0.05, 0.03), 0.8), parent=root)
    tiers = [(0.66, 0.24, 0.66), (0.54, 0.74, 0.60), (0.42, 1.14, 0.54), (0.30, 1.50, 0.48)]
    tier_info = []
    for ti, (R, zb, Ht) in enumerate(tiers):
        prof = [(0.0, zb + Ht)]
        n = 26
        for i in range(1, n + 1):
            t = i / n
            prof.append((R * t ** 1.05, zb + Ht * (1 - t) - 0.08 * t ** 3))
        prof.append((R * 0.55, zb - 0.02))
        ob = lathe("Etage%d" % ti, prof, needle, 110, subsurf=0, parent=root)
        for v in ob.data.vertices:
            a = math.atan2(v.co.y, v.co.x)
            r = math.hypot(v.co.x, v.co.y)
            k = 1 + 0.11 * math.sin(13 * a + ti) + 0.07 * math.sin(29 * a) * (r / R)
            v.co.x *= k
            v.co.y *= k
            v.co.z += 0.03 * math.sin(17 * a + ti * 2) * (r / R)
        tier_info.append((R, zb, Ht))
    # LED-Kette: Wendel um jede Etage
    bulb_cols = [NEON["cyan"], NEON["magenta"], NEON["amber"], NEON["violet"], NEON["green"]]
    wire = M_pbr("Draht", (0.01, 0.01, 0.01), 0.5)
    bi = 0
    for ti, (R, zb, Ht) in enumerate(tier_info):
        pts = []
        turns = 3.0
        steps = 64
        for i in range(steps + 1):
            t = i / steps
            a = turns * 2 * math.pi * t + ti
            zz = zb + Ht * (1 - t) * 0.92 + 0.02
            r = R * t ** 1.05 * 0.99 + 0.02
            pts.append((r * math.cos(a), r * math.sin(a), zz))
        tube("Kette%d" % ti, pts, 0.005, wire, parent=root)
        for k in range(1, 14):
            p = pts[int(k / 14 * steps)]
            if p[1] > 0.12:      # Rückseite verdeckt
                continue
            col = bulb_cols[bi % len(bulb_cols)]
            sphere("Lampe", 0.024, p, M_emit("Lampe%d" % bi, col, 30), parent=root)
            if bi % 2 == 0:
                light_mark(p, col, 0.045, 0.5 + 0.2 * (bi % 3), (bi * 0.137) % 1.0)
            bi += 1
    # Kugeln
    ball_cols = [(0.95, 0.05, 0.55), (0.0, 0.7, 0.95), (0.95, 0.65, 0.1), (0.5, 0.2, 0.95)]
    for i in range(26):
        ti = rnd.randrange(4)
        R, zb, Ht = tier_info[ti]
        a = rnd.uniform(-math.pi * 0.95, math.pi * 0.05)  # nur Vorderseite (y < 0)
        t = rnd.uniform(0.35, 0.8)
        r = R * t * 0.98
        p = (r * math.cos(a), r * math.sin(a), zb + Ht * (1 - t) - 0.06)
        c = ball_cols[i % 4]
        m = M_pbr("Kugel%d" % i, c, 0.12, 1.0, emit=c, emit_strength=0.6)
        sphere("Kugel", 0.05, p, m, parent=root)
        if i % 5 == 0:
            light_mark(p, c, 0.05, 0.0, 0)
    # Stern
    bm = bmesh.new()
    pts = []
    for i in range(10):
        r = 0.11 if i % 2 == 0 else 0.05
        a = math.pi / 2 + i * math.pi / 5
        pts.append(bm.verts.new((r * math.cos(a), 0, r * math.sin(a))))
    bm.faces.new(pts)
    bmesh.ops.solidify(bm, geom=bm.faces[:], thickness=0.04)
    me = bpy.data.meshes.new("Stern")
    bm.to_mesh(me)
    bm.free()
    star = bpy.data.objects.new("Stern", me)
    star.location = (0, 0, 1.96)
    add(star, M_emit("Stern", NEON["amber"], 25), root)
    light_mark((0, -0.03, 1.96), NEON["amber"], 0.12, 0.35, 0.0)
    return dict(zc=1.0, vert=2.35, W=620, H=760, ground=(NEON["cyan"], 0.9), glow=1.0, heading=0)


def build_bed():
    mats()
    root = bpy.data.objects.new("Bett", None)
    bpy.context.scene.collection.objects.link(root)
    under = M_emit("Unterlicht", NEON["magenta"], 10)
    box("Sockel", (1.04, 2.06, 0.20), (0, 0, 0.12), STEEL, 0.03, root)
    box("Unterlicht", (0.98, 2.0, 0.03), (0, 0, 0.025), under, 0.005, root, sub=0)
    fabric = M_pbr("Stoff", (0.62, 0.64, 0.7), 0.95, bump=(160, 0.5))
    box("Matratze", (0.98, 1.96, 0.17), (0, 0.0, 0.31), fabric, 0.06, root)
    navy = M_pbr("Decke", (0.02, 0.035, 0.12), 0.85, bump=(90, 0.5))
    box("Decke", (1.0, 1.28, 0.07), (0, -0.30, 0.43), navy, 0.05, root)
    cy = M_emit("DeckeLinie", NEON["cyan"], 9)
    for i in range(8):
        y = -0.82 + i * 0.17
        box("Linie", (0.92, 0.012, 0.012), (0, y, 0.472), cy, 0.003, root, sub=0)
    for sx in (-1, 1):
        box("Diagonale", (0.012, 1.1, 0.012), (sx * 0.34, -0.3, 0.474), M_emit("D", NEON["magenta"], 8), 0.003, root, sub=0)
    pil = M_pbr("Kissen", (0.78, 0.8, 0.86), 0.9, bump=(140, 0.4))
    for sx in (-1, 1):
        box("Kissen", (0.42, 0.3, 0.12), (sx * 0.24, 0.70, 0.46), pil, 0.05, root, rot=(-8, 0, sx * 4))
    # Kopfteil mit Neonröhren
    box("Kopfteil", (1.1, 0.1, 0.9), (0, 1.05, 0.55), STEEL, 0.03, root)
    glow = M_emit("Roehre", NEON["cyan"], 16)
    tube("Roehre1", [(-0.42, 0.99, 0.4), (-0.42, 0.99, 0.82), (0.0, 0.99, 0.96), (0.42, 0.99, 0.82), (0.42, 0.99, 0.4)], 0.014, glow, parent=root)
    tube("Roehre2", [(-0.28, 0.99, 0.5), (-0.28, 0.99, 0.76), (0.0, 0.99, 0.86), (0.28, 0.99, 0.76), (0.28, 0.99, 0.5)], 0.014, M_emit("Roehre2", NEON["magenta"], 16), parent=root)
    # Stern im Kopfteil
    sp = []
    for i in range(11):
        r = 0.09 if i % 2 == 0 else 0.038
        a = math.pi / 2 + i * math.pi / 5
        sp.append((r * math.cos(a), 0.99, 0.70 + r * math.sin(a)))
    tube("Stern", sp, 0.011, M_emit("SternR", NEON["amber"], 18), closed=True, parent=root)
    for sx in (-1, 1):
        for sy in (-1, 1):
            sphere("Fuss", 0.03, (sx * 0.5, sy * 1.0, 0.03), M_emit("Fuss", NEON["cyan"], 14), parent=root)
    light_mark((0, 0.99, 0.7), NEON["amber"], 0.10, 0.4, 0.0)
    light_mark((0, 0.99, 0.95), NEON["cyan"], 0.12, 0.0, 0.0)
    light_mark((0, -0.9, 0.05), NEON["magenta"], 0.25, 0.25, 0.3)
    root.rotation_euler.z = math.radians(32)
    return dict(zc=0.62, vert=1.5, W=900, H=640, ground=(NEON["magenta"], 1.5), heading=32)


def build_fence():
    mats()
    root = bpy.data.objects.new("Zaun", None)
    bpy.context.scene.collection.objects.link(root)
    cy = M_emit("ZaunCyan", NEON["cyan"], 14)
    for i in range(9):
        x = -0.8 + i * 0.2
        box("Latte", (0.1, 0.045, 0.72), (x, 0, 0.40), STEEL, 0.015, root)
        # Spitze
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, cap_ends=True, segments=4, radius1=0.07, radius2=0.0, depth=0.11)
        me = bpy.data.meshes.new("Spitze")
        bm.to_mesh(me)
        bm.free()
        tip = bpy.data.objects.new("Spitze", me)
        tip.location = (x, 0, 0.815)
        tip.rotation_euler.z = math.radians(45)
        tip.scale = (1, 0.65, 1)
        add(tip, STEEL, root)
        box("Neon", (0.02, 0.012, 0.55), (x, -0.027, 0.42), cy, 0.003, root, sub=0)
    for z, col in ((0.24, "magenta"), (0.60, "cyan")):
        box("Querholz", (1.86, 0.05, 0.07), (0, 0.03, z), STEEL, 0.015, root)
        box("QuerNeon", (1.8, 0.014, 0.018), (0, -0.002, z), M_emit("Q" + col, NEON[col], 16), 0.004, root, sub=0)
    for sx in (-1, 1):
        box("Pfosten", (0.1, 0.1, 0.95), (sx * 0.96, 0.02, 0.48), STEEL, 0.02, root)
        sphere("Kappe", 0.06, (sx * 0.96, 0.02, 1.0), M_emit("Kappe", NEON["magenta"] if sx < 0 else NEON["amber"], 25), parent=root)
        light_mark((sx * 0.96, 0.0, 1.0), NEON["magenta"] if sx < 0 else NEON["amber"], 0.09, 0.45 if sx < 0 else 0.7, 0.0 if sx < 0 else 0.5)
    light_mark((0, 0, 0.6), NEON["cyan"], 0.28, 0.0, 0.0)
    light_mark((0, 0, 0.24), NEON["magenta"], 0.28, 0.0, 0.0)
    return dict(zc=0.52, vert=1.25, W=900, H=420, ground=(NEON["cyan"], 1.0), heading=0)


def build_lamp():
    mats()
    root = bpy.data.objects.new("Laterne", None)
    bpy.context.scene.collection.objects.link(root)
    cy = M_emit("LaterneCyan", NEON["cyan"], 14)
    cyl("Fuss", 0.16, 0.0, 0.07, STEEL, parent=root, r1=0.11)
    cyl("Mast", 0.032, 0.05, 1.72, STEEL, parent=root, r1=0.026)
    for z in (0.25, 0.8, 1.35):
        lathe("Ring", [(0.034, z), (0.052, z + 0.012), (0.052, z + 0.03), (0.034, z + 0.042)], cy, 40, subsurf=0, parent=root)
    # Laternenkopf
    glass = M_pbr("Glas", (0.9, 0.95, 1.0), 0.05, 0.0)
    glass.node_tree.nodes["Principled BSDF"].inputs["Alpha"].default_value = 0.25
    cyl("Boden", 0.15, 1.70, 1.74, STEEL, parent=root, r1=0.12)
    core = M_emit("Kern", NEON["white"], 60)
    sphere("Kern", 0.085, (0, 0, 1.93), core, scale=(1, 1, 1.4), parent=root)
    for a in range(4):
        ang = a * math.pi / 2 + math.pi / 4
        box("Strebe", (0.016, 0.016, 0.42), (0.145 * math.cos(ang), 0.145 * math.sin(ang), 1.93), STEEL, 0.004, root, sub=0)
    cyl("Haube", 0.18, 2.14, 2.17, STEEL, parent=root, r1=0.16)
    lathe("Dach", [(0.0, 2.32), (0.05, 2.25), (0.17, 2.17), (0.0, 2.17)], STEEL, 40, parent=root)
    sphere("Spitze", 0.03, (0, 0, 2.34), M_emit("Spitze", NEON["magenta"], 30), parent=root)
    lathe("NeonRing", [(0.152, 1.78), (0.162, 1.78), (0.162, 1.80), (0.152, 1.80)], M_emit("NR", NEON["magenta"], 18), 48, subsurf=0, parent=root)
    # Kranz mit bunten Lichtern
    green = M_pbr("Kranz", (0.01, 0.1, 0.04), 0.8, bump=(120, 0.8))
    pts = [(0.17 * math.cos(a), 0.17 * math.sin(a), 0) for a in [i / 40 * 2 * math.pi for i in range(40)]]
    wreath = tube("Kranz", pts, 0.05, green, closed=True, parent=root)
    wreath.location = (0, -0.05, 1.12)
    wreath.rotation_euler.x = math.radians(90)
    cols = [NEON["magenta"], NEON["cyan"], NEON["amber"], NEON["violet"]]
    for i in range(10):
        a = math.pi + (i / 9 - 0.5) * 2.2 * math.pi * 0.9
        p = (0.17 * math.cos(a), -0.12, 1.12 + 0.17 * math.sin(a))
        sphere("Licht", 0.02, p, M_emit("KL%d" % i, cols[i % 4], 40), parent=root)
        light_mark(p, cols[i % 4], 0.04, 0.5 + 0.15 * (i % 3), (i * 0.19) % 1)
    light_mark((0, -0.02, 1.93), NEON["white"], 0.30, 0.0, 0)
    light_mark((0, 0, 2.34), NEON["magenta"], 0.07, 0.6, 0.2)
    return dict(zc=1.18, vert=2.75, W=520, H=860, ground=(NEON["white"], 0.9), heading=0)


def build_lights():
    mats()
    root = bpy.data.objects.new("Lichterkette", None)
    bpy.context.scene.collection.objects.link(root)
    for sx in (-1, 1):
        cyl("Mast", 0.03, 0.0, 1.15, STEEL, loc=(sx * 0.95, 0, 0), parent=root, r1=0.022)
        cyl("Fuss", 0.1, 0.0, 0.05, STEEL, loc=(sx * 0.95, 0, 0), parent=root, r1=0.06)
        sphere("Kappe", 0.035, (sx * 0.95, 0, 1.17), M_emit("Kappe", NEON["cyan"], 25), parent=root)
    wire = M_pbr("Draht", (0.01, 0.01, 0.012), 0.4)
    cols = [NEON["magenta"], NEON["cyan"], NEON["amber"], NEON["violet"], NEON["green"]]

    def strand(zt, sag, n, off):
        pts = []
        for i in range(41):
            t = i / 40
            x = -0.95 + 1.9 * t
            z = zt - sag * (1 - (2 * t - 1) ** 2)
            pts.append((x, 0, z))
        tube("Draht", pts, 0.006, wire, parent=root)
        for k in range(n):
            t = (k + 0.5) / n
            x = -0.95 + 1.9 * t
            z = zt - sag * (1 - (2 * t - 1) ** 2) - 0.045
            col = cols[(k + off) % 5]
            sphere("Lampe", 0.04, (x, 0, z), M_emit("L%d_%d" % (off, k), col, 40), scale=(1, 1, 1.35), parent=root)
            cyl("Fassung", 0.014, z + 0.045, z + 0.075, STEEL, loc=(x, 0, 0), parent=root)
            light_mark((x, -0.01, z), col, 0.07, 0.45 + 0.15 * ((k + off) % 3), ((k + off) * 0.17) % 1)
    strand(1.14, 0.30, 15, 0)
    return dict(zc=0.66, vert=1.5, W=900, H=520, ground=None, heading=0)


def build_sign():
    mats()
    root = bpy.data.objects.new("Schild", None)
    bpy.context.scene.collection.objects.link(root)
    box("Tafel", (1.7, 0.08, 0.8), (0, 0, 0.95), STEEL, 0.04, root)
    for sx in (-1, 1):
        box("Bein", (0.06, 0.06, 0.58), (sx * 0.62, 0.0, 0.29), STEEL, 0.015, root)
        box("Fuss", (0.22, 0.34, 0.05), (sx * 0.62, 0.0, 0.03), STEEL, 0.015, root)
    # Rahmen aus Neon
    fr = M_emit("Rahmen", NEON["cyan"], 16)
    tube("Rahmen", [(-0.78, -0.05, 0.62), (0.78, -0.05, 0.62), (0.78, -0.05, 1.28), (-0.78, -0.05, 1.28)], 0.014, fr, closed=True, parent=root)

    def text(body, size, loc, col, strength):
        cu = bpy.data.curves.new("Text", "FONT")
        cu.body = body
        cu.size = size
        cu.extrude = 0.012
        cu.bevel_depth = 0.004
        cu.align_x = "CENTER"
        cu.align_y = "CENTER"
        ob = bpy.data.objects.new("Text", cu)
        ob.location = loc
        ob.rotation_euler = Euler((math.radians(90), 0, 0))
        add(ob, M_emit("T" + body, col, strength), root)
        return ob

    text("FROHE", 0.2, (0, -0.062, 1.13), NEON["amber"], 18)
    text("WEIHNACHT", 0.17, (0, -0.062, 0.86), NEON["magenta"], 20)
    # Tannenbaum aus Neonlinien
    tree = [(0, -0.055, 0.73), (-0.09, -0.055, 0.68), (-0.04, -0.055, 0.68), (-0.13, -0.055, 0.63), (0.13, -0.055, 0.63), (0.04, -0.055, 0.68), (0.09, -0.055, 0.68), (0, -0.055, 0.73)]
    tube("Tanne", tree, 0.01, M_emit("Tanne", NEON["green"], 18), parent=root)
    sphere("Stern", 0.028, (0, -0.06, 0.755), M_emit("St", NEON["amber"], 40), parent=root)
    light_mark((0, -0.07, 0.755), NEON["amber"], 0.05, 0.6, 0.0)
    light_mark((-0.55, -0.07, 1.13), NEON["amber"], 0.22, 0.0, 0)
    light_mark((0.5, -0.07, 0.86), NEON["magenta"], 0.25, 0.3, 0.2)
    light_mark((0.0, -0.07, 0.62), NEON["cyan"], 0.3, 0.0, 0)
    return dict(zc=0.8, vert=1.8, W=860, H=780, ground=(NEON["magenta"], 0.9), heading=0)


BUILDERS = {"tree": build_tree, "bed": build_bed, "fence": build_fence, "lamp": build_lamp, "lights": build_lights, "sign": build_sign}

for name, builder in BUILDERS.items():
    if ONLY and ONLY != name:
        continue
    sc = reset()
    LIGHTS.clear()
    spec = builder()
    cam = stage(sc, spec["zc"], spec["vert"], spec["W"], spec["H"])
    finish_render(sc, cam, name, spec["W"], spec["H"], spec["vert"], (0, 0, 0), spec.get("ground"))
