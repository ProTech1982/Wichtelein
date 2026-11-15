# Stand (Arbeitsmerker für Agenten, aktueller Zustand wird ersetzt)

Auftrag: Wichtelein fertig bauen wie in `Prompt.txt` (Downloads, gehört zu einem anderen Projekt) + Stimme verbessern.
Gebaut wird nur in GitHub Actions (kein Android-SDK lokal). Release = Push auf main (android.yml, Tag build-N).
Entwicklungszweige per `gh workflow run ci.yml --ref <zweig>` testen (ci.yml hat workflow_dispatch). Emulator-Job flackert gelegentlich (Boot-Timeout -> neu starten).

## Version 0.3.0
- Neuer Wichtel aus `Wichtel_bild1` (Downloads, ChatGPT-Bild, Rechte beim Nutzer): `tools/asset/cutout.py` (Freistellen vom Schachbrett) und `make_parts.py` (Kopf, Rumpf, Arme, Beine + `elf_layout.json` + Besen aus `wichtel-besen-1`). Alter Foto-Elf und 3D-Wichtel entfernt.
- Blender-Versuch (`blender/render_textured.py`): gewölbte Textur-Teile, wirkte flacher als die Ausschnitte -> nicht benutzt, Skripte bleiben.
- Weihnachtsgeräusche (`sound/SoundFx.kt`, selbst erzeugt), Engine meldet Geräusche, Vorschau/AR spielen live, Export mischt sie in die Tonspur. Schalter in den Einstellungen.
- Foto-Film: Foto machen/wählen (`PhotoScreen.kt`), Editor mit Aktionspunkten (`ScriptStep`/`ElfAction`), Export Standbild -> MP4.
- Tests (CI Zweig v0.3.0-entwicklung, grün): JVM-Tests inkl. `SoundAndScriptTest`, `LayoutTest`; 17 Emulator-Tests inkl. `PhotoFilmTest` (Foto aufbereiten, Foto-Film-Export mit Ton, Bedienung des Foto-Editors), `ColorTest`, `RenderTest`.
- Beim Testen gefundene und behobene Fehler: Absturz beim Zeichnen von Pin-Beschriftungen am Bildrand, Absturz bei sehr schmaler Sprechblase, Modus-Chips im Editor nicht scrollbar.

## Offen / ungeprüft
- AR läuft laut Nutzer; kein Videoexport aus AR.
- Nur Vorderansicht des Wichtels; Mund/Augen unter dem Bart (Sprechen = Kopfnicken/Stauchen).
- Kamera (Foto/Video) auf echtem Gerät, Foto-Film-Bedienung per Touch, Klangqualität (Stimme + synthetische Geräusche): nur der Nutzer kann das beurteilen.
- Systemstimme im Export: im Emulator nicht ausführbar.
- Blender nur mit `--factory-startup` im Hintergrund rendern (Pfad siehe `blender/README.md`).

## Regeln
Kein Kindername im Repo/Code. Commits mit Noreply-Mail (62508861+ProTech1982@users.noreply.github.com), nie die private Mail.
Keystore nie ausgeben. Antworten auf Deutsch. Nichts als fertig melden, was nicht getestet ist.
