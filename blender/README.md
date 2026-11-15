# Blender-Quellen des 3D-Wichtels

Der 3D-Wichtel entsteht vollständig aus Python-Skripten (Blender 4/5, Cycles) – es gibt keine fremden Modelle oder Texturen.

| Datei | Aufgabe |
|---|---|
| `make_wichtel.py` | baut Modell, Materialien, Licht und Kamera (Maße: 1,0 Einheiten hoch, Blick nach -Y) |
| `render_parts.py` | rendert Kopf, Körper, Arme und Beine einzeln als transparente PNGs (Cycles, 1000 px pro Einheit) |
| `slice_parts.py` | schneidet die Teile zu und schreibt `elf3d_*.png` und `elf3d_layout.json` in die App-Ressourcen |
| `render_preview.py`, `iter.sh` | schnelle Vorschau des ganzen Wichtels |
| `save_blend.py` | speichert `wichtel.blend` |

Ablauf (Blender-Pfad anpassen, immer mit `--factory-startup`, sonst startet der MCP-Server der Add-ons mit):

```
blender --factory-startup -b -P make_wichtel.py -P render_parts.py -- ordner 96 CYCLES
python slice_parts.py ordner ../app/src/main/res/drawable-nodpi ../app/src/main/res/raw
```

Die App bewegt die gerenderten Teile mit derselben Gelenk-Animation wie die Foto-Puppe (`ElfLayout` beschreibt Positionen und Gelenke).
Die Animationen entstehen also in der App aus den Posen, nicht als vorgerenderte Blender-Clips.
