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


## Der 3D-Wichtel (seit 0.4.0)

`wichtel3d.py` modelliert den Wichtel komplett in Blender (Mütze mit Nieten, Bart, Mantel, Arme, Beine, Besen – nur aus Python, ohne fremde Modelle)
und rendert ihn mit den Bewegungen Stehen, Laufen (8 Bilder), Fegen (9) und Winken (6) aus 16 bzw. 8 Blickrichtungen.
`pack3d.py` verkleinert die Bilder und legt sie nach `app/src/main/assets/elf3d/` (mit `elf3d.json`).

```
blender --factory-startup -b -P wichtel3d.py -- vorschau ordner 24      # Kontaktbogen zum Ansehen
blender --factory-startup -b -P wichtel3d.py -- final ordner 32         # alle Bilder (etwa 15 Minuten)
python pack3d.py ordner ../app/src/main/assets/elf3d 360
```

Die App wählt je Haltung das passende Bild (`Elf3D.kt`): Laufrichtung bestimmt die Blickrichtung, beim Fegen steht er seitlich, beim Betreten
des Hauses dreht er sich mit dem Rücken zum Betrachter zur Tür. Andere Bewegungen entstehen aus dem Stehbild (Verschieben, Stauchen, Drehen).
