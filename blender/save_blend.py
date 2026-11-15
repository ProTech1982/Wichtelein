"""Speichert die Szene als wichtel.blend: blender -b -P make_wichtel.py -P save_blend.py"""
import os
import bpy

path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wichtel.blend")
bpy.ops.wm.save_as_mainfile(filepath=path)
print("gespeichert:", path)
