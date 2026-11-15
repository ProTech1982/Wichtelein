# Blender-Variante des Wichtels (Experiment)

Der Wichtel in der App ist der Gartenwichtel aus `Wichtel_bild1` (ChatGPT-Bild des Nutzers), in Teile geschnitten
(`tools/asset/`, siehe dort). Der frühere, selbst modellierte 3D-Wichtel wurde entfernt (sah nicht gut aus).

Hier liegt der Versuch, dieselben Textur-Teile in Blender zu wölben und mit Licht zu rendern:

| Datei | Aufgabe |
|---|---|
| `prep_height.py` | erzeugt je Teil eine Höhenkarte (Kissenform aus der Transparenz) |
| `render_textured.py` | baut je Teil ein feines Gitter, beklebt es mit der Teil-Textur, wölbt es mit der Höhenkarte und rendert es (Cycles) |

```
python prep_height.py ../app/src/main/res/drawable-nodpi hoehen
blender --factory-startup -b -P render_textured.py -- ../app/src/main/res/drawable-nodpi hoehen ../app/src/main/res/raw/elf_layout.json ausgabe 2 32
```

Ergebnis des Vergleichs: Die Blender-Teile sind weicher, aber flacher in Farbe und Kontrast als die direkt geschnittenen Teile
(grauerer Bart, weniger Leuchtkraft). Deshalb benutzt die App die geschnittenen Teile. Die Skripte bleiben als Ausgangspunkt für
eine echte 3D-Figur (Seitenansichten brauchen zusätzliche Texturen).
