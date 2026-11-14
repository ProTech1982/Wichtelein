# Stand (Arbeitsmerker für Agenten, aktueller Zustand wird ersetzt)

Auftrag: Wichtelein fertig bauen wie in `Prompt.txt` (Downloads) + Stimme verbessern (Nutzer: Stimme "grausam, nicht wie echte Stimme").
Gebaut wird nur in GitHub Actions (kein Android-SDK lokal). Release = Push auf main (android.yml, Tag build-N). Emulator-Job flackert (Boot-Timeout -> neu starten).

## Erledigt (v0.1.0 alpha, Build 9)
Kamera/Import, Wichtel aus Bildteilen (res/drawable-nodpi/elf*_*.png), Live-Overlay, Aktionen, Offline-Stimme (Piper+sherpa-onnx 1.13.8, NICHT 2.x), Updater, Signierung.

## Plan
A. Stimme: natürlicher Standard, Effekt dosierbar, Stimmenkatalog mit Download (commons-compress), Anhören-Knopf.
B. Engine deterministisch aufnehmen (virtuelle Frame-Uhr, seeded Random) -> `Performance` (Frames + Sprach-Ereignisse); Szenen in 3 Kategorien
   (Brav/Frech/Überraschung), neue Aktionen (Hinsetzen, Stolpern, Geschenk), >=30 Sprüche mit {name}.
C. UI: Start (5 Bereiche), Erststart-Name, Profile, Editor (Start/Größe/Weg/Szene/Neu würfeln/Vorschau), Wichtelaktionen, Meine Wichtelvideos, Einstellungen, Quer/Hochformat.
D. Export MP4 mit Ton: Media3 Transformer, OverlayEffect (Canvas-Frames), Sprache als WAV-Sequenz; Galerie (MediaStore, minSdk 29) + Teilen (FileProvider).
E. Tests (JVM + Emulator inkl. Export-Prüfung), README ehrlich: fertig / prototypisch / fehlt.

## Aktuell
Stufe A in Arbeit.

## Regeln
Kein Kindername im Repo/Code. Commits mit Noreply-Mail (62508861+ProTech1982@users.noreply.github.com), nie die private Mail. Keystore nie ausgeben.
Texte/Antworten auf Deutsch. Nichts als fertig melden, was nicht getestet ist; Hörtests kann nur der Nutzer.
