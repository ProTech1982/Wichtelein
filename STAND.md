# Stand (Arbeitsmerker für Agenten, aktueller Zustand wird ersetzt)

Auftrag: Wichtelein fertig bauen wie in `Prompt.txt` (Downloads, gehört zu einem anderen Projekt) + Stimme verbessern.
Gebaut wird nur in GitHub Actions (kein Android-SDK lokal). Release = Push auf main (android.yml, Tag build-N).
Entwicklungszweige per `gh workflow run ci.yml --ref <zweig>` testen (ci.yml hat workflow_dispatch). Emulator-Job flackert gelegentlich (Boot-Timeout -> neu starten).

## Version 0.8.0
- Sprechblase: `anim/Bubble.kt` (5 Formen), je Aktion (`ScriptStep.bubble`), Standard in `Settings.bubble`; UI `ui/BubbleControls.kt`.
- Summen/Brummen: `Settings.hum`, `Fx.MUMBLE1-3`, `Fx.HUM_*`; Engine plant Geräusche (`scheduleFx`); `Settings.allowsFx` entscheidet, was gespielt/exportiert wird.
- Musik: `sound/Music.kt` (4 Spieluhr-Melodien, `AudioDecoder` für MP3/AAC, `MusicPlayer`, `MusicLoader`), Editor-Werkzeug „🎵 Musik“, Export mischt Musik mit Ausblenden.
- Vorschaubilder in `docs/vorschau/` (README-Abschnitt „Vorschau“). Tests: 26 Emulator-Tests grün.
- Letzter Wunsch des Nutzers: die Hinweis-Meldung von GitHub „main ist nicht geschützt“ ist nur eine Empfehlung (Branch-Schutz gegen Force-Push/Löschen), keine Pflicht.

## Version 0.7.0 (enthält 0.6.1 und 0.6.2)
- Tür: Wichtel bleibt draußen, öffnet die Tür, man sieht eine winzige Weihnachtsstube (Tannenbaum, Kamin, Lichterkette, Geschenke), er dreht sich um und schließt die Tür (`doEnterHouse`, `turnAndClose`); „Geschenk aus dem Haus holen“ (`doExitHouse`).
- Einstellungen: Farbe (Farbton), Licht an/aus und Lichtfarbe je Gegenstand (`SceneryStyle`, `tintArt`).
- 0.6.2: Wege zu Aufbau/Bett/Tür folgen dem gezeichneten Weg (`walkRoute`, Treppe). 0.6.1: Größe-Regler immer sichtbar, Werkzeuge umbrechen.

## Version 0.6.0
- Gegenstände stellt der Wichtel selbst auf (`doBuild`, wächst von unten nach oben, im Editor blass als Geist); Bett: Aktion/Szene „Ins Bett & schnarchen“ (`doBed`, Fx.SNORE); „Psst/Schhh/Brrr/Hmmm“ werden nicht mehr buchstabiert (`scene/SpeechText.kt`), „Psst“ = leises Zischen (Fx.PSST). Enthält 0.5.1 (Wegpunkte im ganzen Bild, Feineinstellung, Perspektive).

## Version 0.5.1
- Wegpunkte/Objekte dürfen fast im ganzen Bild liegen (MIN_Y 0,03 statt 0,4, z. B. Treppe); Feineinstellung: Pfeil-Knöpfe (0,4 % Schritt) und „Ziehen langsam“; Einstellung „Perspektive“ (0 = überall gleich groß).

## Version 0.5.0
- Editor neu (`ui/EditorScreen.kt`, Logik `ui/EditState.kt`): Auswahl, Ziehen, Löschen je Punkt, Rückgängig/Wiederholen (`EditHistory`), Werkzeug-Chips.
- Gegenstände (`anim/Scenery.kt`, Bilder `assets/scenery/`, erzeugt mit `blender/szene3d.py`): Tannenbaum, Bett, Zaun, Laterne, Lichterkette, Neonschild; additive Lichter, Tiefensortierung vor/hinter dem Wichtel, im Export enthalten (`ExportManager.export(scenery=…)`).
- Update-Fenster (`ui/UpdateDialog.kt`) mit Änderungsprotokoll; Download erst nach „Jetzt updaten“.
- 3D-Wichtel neu modelliert (Haarsträhnen, Zwergenproportionen), Bilder neu gerendert.

## Version 0.4.0 (Kurzfassung)
- Laufweg: Wichtel bleibt in der Nähe von Start/Weg/Aktionspunkten (`roam` in der Engine, `PerfRequest.confineToPoints`, AR ausgenommen); Wegpunkte als Rundgang (`Stops`).
- 3D-Wichtel (`blender/wichtel3d.py` -> `pack3d.py` -> `app/src/main/assets/elf3d/`, 264 Bilder, 15 MB): Stehen/Laufen/Fegen/Winken aus 16 bzw. 8 Richtungen. `Elf3D.kt` wählt das Bild aus `Pose.heading`/`stepPhase`; Einstellung `figure3d` (Einstellungen -> Figur). 2D-Wichtel bleibt.
- Engine: `Pose.heading`, `turnTo()` (nur bei 3D animiert), Fegen seitlich + Staubwolken, Haus: Drehung mit Rücken zum Betrachter.
- Tests (CI, grün): JVM `Figure3DTest` (Richtung, Drehen, Laufweg, Staub, Bildauswahl) u. a.; 19 Emulator-Tests inkl. `Figure3DRenderTest`.

## Version 0.3.0 (Kurzfassung)
- Wichtel aus `Wichtel_bild1` (tools/asset), Besen aus Foto, Weihnachtsgeräusche (`sound/SoundFx.kt`), Foto-Film + Aktionspunkte.

## Offen / ungeprüft
- AR läuft laut Nutzer; kein Videoexport aus AR.
- 2D-Wichtel nur Vorderansicht; 3D-Wichtel: Hose/Schuhe nicht umfärbbar, Hinsetzen/Purzeln/Schlafen nur aus dem Stehbild, Aussehen Geschmackssache (Toy-Look).
- Kamera (Foto/Video) auf echtem Gerät, Foto-Film-Bedienung per Touch, Klangqualität (Stimme + synthetische Geräusche): nur der Nutzer kann das beurteilen.
- Systemstimme im Export: im Emulator nicht ausführbar.
- Blender nur mit `--factory-startup` im Hintergrund rendern (Pfad siehe `blender/README.md`).

## Regeln
Kein Kindername im Repo/Code. Commits mit Noreply-Mail (62508861+ProTech1982@users.noreply.github.com), nie die private Mail.
Keystore nie ausgeben. Antworten auf Deutsch. Nichts als fertig melden, was nicht getestet ist.
