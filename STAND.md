# Stand (Arbeitsmerker für Agenten, aktueller Zustand wird ersetzt)

Auftrag: Wichtelein fertig bauen wie in `Prompt.txt` (Downloads) + Stimme verbessern.
Gebaut wird nur in GitHub Actions (kein Android-SDK lokal). Release = Push auf main (android.yml, Tag build-N).
Emulator-Job flackert gelegentlich (Boot-Timeout -> Job neu starten).

## Erledigt
- Stimme: natürlicher Standard, Effekt dosierbar, 7 Stimmen (Download), Anhören-Knopf. Klangqualität kann nur der Nutzer beurteilen.
- Engine deterministisch (virtuelle Uhr), `Performance`-Zeitleiste, 15 Szenen/3 Kategorien, 36 Sprüche, Aktionen inkl. Hinsetzen/Stolpern/Geschenk.
- UI: Start (5 Bereiche), Erststart, Profile, Editor (Start/Größe/Weg/Szene/Würfeln/Vorschau), Wichtelaktionen, Meine Wichtelvideos, Einstellungen.
- Export MP4 mit Ton (Media3 Transformer, Overlay + WAV-Sequenz), Galerie, Teilen. Emulator-Test prüft Video+Ton+Wichtel im Bild.
- Tests: JVM (Szenen, Zeitleiste, Einstellungen, Stimmen-Katalog, Pitch, WAV) + 11 Emulator-Tests (u. a. gedrehtes Video, Umfärbung).
- Kleidungsfarben je Kleidungsstück (Laufzeit-Umfärbung), Wichtelhaus-Tür (Editor-Modus, 4 Türszenen, gezeichnetes Türblatt), 19 Szenen.
- Code in Pakete gegliedert; `ElfFigure` macht die Figur austauschbar; Systemstimme im Export (ungetestet im Emulator).

## Offen / ungeprüft
- Blender-3D-Wichtel: Blender war nicht erreichbar; ARCore später. Wichtel ist 2D-Puppe aus Referenzbild (Rechte klären).
- Kamera im Querformat, Ansehen/Teilen, echte Klangqualität: nicht auf echtem Gerät geprüft.
- Systemstimme im Export: umgesetzt, im Emulator nicht ausführbar.

## Regeln
Kein Kindername im Repo/Code. Commits mit Noreply-Mail (62508861+ProTech1982@users.noreply.github.com), nie die private Mail.
Keystore nie ausgeben. Antworten auf Deutsch. Nichts als fertig melden, was nicht getestet ist.
