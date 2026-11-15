# Stand (Arbeitsmerker für Agenten, aktueller Zustand wird ersetzt)

Auftrag: Wichtelein fertig bauen wie in `Prompt.txt` (Downloads) + Stimme verbessern.
Gebaut wird nur in GitHub Actions (kein Android-SDK lokal). Release = Push auf main (android.yml, Tag build-N).
Emulator-Job flackert gelegentlich (Boot-Timeout -> Job neu starten).

## Version 0.2.0 (Release): AR (ARCore, ungeprüft auf Gerät), 3D-Wichtel aus Blender (Teile, Stil wählbar), Tür links/rechts + Rundung, Kleidungsfarben, Tür-Szenen.

## Erledigt
- Stimme: natürlicher Standard, Effekt dosierbar, 7 Stimmen (Download), Anhören-Knopf. Klangqualität kann nur der Nutzer beurteilen.
- Engine deterministisch (virtuelle Uhr), `Performance`-Zeitleiste, 15 Szenen/3 Kategorien, 36 Sprüche, Aktionen inkl. Hinsetzen/Stolpern/Geschenk.
- UI: Start (5 Bereiche), Erststart, Profile, Editor (Start/Größe/Weg/Szene/Würfeln/Vorschau), Wichtelaktionen, Meine Wichtelvideos, Einstellungen.
- Export MP4 mit Ton (Media3 Transformer, Overlay + WAV-Sequenz), Galerie, Teilen. Emulator-Test prüft Video+Ton+Wichtel im Bild.
- Tests: JVM (Szenen, Zeitleiste, Einstellungen, Stimmen-Katalog, Pitch, WAV) + 11 Emulator-Tests (u. a. gedrehtes Video, Umfärbung).
- Kleidungsfarben je Kleidungsstück (Laufzeit-Umfärbung), Wichtelhaus-Tür (Editor-Modus, 4 Türszenen, gezeichnetes Türblatt), 19 Szenen.
- Code in Pakete gegliedert; `ElfFigure` macht die Figur austauschbar; Systemstimme im Export (ungetestet im Emulator).

## Offen / ungeprüft
- AR (ARCore) nicht auf Gerät geprüft; kein Videoexport aus AR. 3D-Wichtel = gerenderte Teile (Blender, `blender/`), nur Vorderansicht. Foto-Wichtel: Rechte des Referenzbildes klären.
- Blender nur mit `--factory-startup` im Hintergrund rendern (Add-ons/GPU blockieren sonst); Mütze nie per subdivide_edges auf Kegel bauen (hängt).
- Kamera im Querformat, Ansehen/Teilen, echte Klangqualität: nicht auf echtem Gerät geprüft.
- Systemstimme im Export: umgesetzt, im Emulator nicht ausführbar.

## Regeln
Kein Kindername im Repo/Code. Commits mit Noreply-Mail (62508861+ProTech1982@users.noreply.github.com), nie die private Mail.
Keystore nie ausgeben. Antworten auf Deutsch. Nichts als fertig melden, was nicht getestet ist.
