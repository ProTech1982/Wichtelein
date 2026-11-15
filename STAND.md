# Stand (Arbeitsmerker für Agenten, aktueller Zustand wird ersetzt)

Auftrag: Wichtelein fertig bauen wie in `Prompt.txt` (Downloads, gehört zu einem anderen Projekt) + Stimme verbessern.
Gebaut wird nur in GitHub Actions (kein Android-SDK lokal). Release = Push auf main (android.yml, Tag build-N).
Entwicklungszweige per `gh workflow run ci.yml --ref <zweig>` testen (ci.yml hat workflow_dispatch). Emulator-Job flackert gelegentlich (Boot-Timeout -> neu starten).

## Version 0.9.3 (Release 1.0 erst, wenn der Nutzer „ok“ sagt)
- Einstellungen: aufklappbare Bereiche (`Fold`, Zustand in einer `mutableStateMapOf`), „Alle aufklappen/zuklappen“.
- Besetzung `Settings.cast` (0 Wichtel, 1 Elfin, 2 beide; `elfin`/`both` sind berechnet). Zwei Figuren: `Recorder` lässt zwei `ElfEngine` (Parameter `actor`) gleichzeitig laufen, die zweite hängt als
  `FrameState.partner` am Bild; `ScriptStep.who` bestimmt, wer eine Aktion macht; ohne eigene Aktionen steht die andere Figur nur da (`PerfRequest.idleOnly`). `CastFigure(main, partner)` trägt beide Figuren,
  `drawWichtelFrame` sortiert sie mit den Gegenständen nach Tiefe.
- Stimmen nach Geschlecht: `VoiceInfo.female`, `VoiceCatalog.forActor`, `Settings.voiceFor/forActor/nameFor`, `SpeechEvent.actor`, `SpeechRender.renderAll` (wechselt die Stimme je Figur).
  Die Geschlechter von Dii (weiblich) und Miro (vermutlich männlich) sind aus Recherche bzw. Annahme, bitte beim Anhören prüfen.
- Elfin komplett mit eigenen Texten: `scene/ElfinTexts.kt` (`LineBook`, `ElfinLines`), `ElfEngine.lines` wählt je Figur; eigene Sätze je Figur (`Settings.customListFor`), eigene Kleiderfarben (`Settings.colorsFor(1)`).
- Eigener Startpunkt der Elfin (`EditState.start2`, `Sel.Start2`, `PerfRequest.start2`); gemeinsame Aktionen (`ScriptStep.who == 2`, `StepSync` stimmt beide ab, Elfin steht 9 % daneben).
- Treffer bei gedrehten Gegenständen/Türen (`EditOps.toLocal`, `hit(..., aspect)`); Gegenstands-Filter Klassisch/Neon in den Einstellungen.
- 3D-Bilder als WebP (`elf3d/`, `elfin3d/`, `*.webp`); `blender/pack3d.py` schreibt WebP.
- Tür drehbar: `EditState.doorRot` → `PerfRequest.doorRot` → `FrameState.doorRot`; gezeichnet gedreht um den Fußpunkt.

## Version 0.9.2
- Gegenstände drehen: `SceneryItem.rot` (Grad um den Fußpunkt), `EditOps.rotateItem`, Editor: ⟲ ⟳, Schieber und „0°“; ein gedrehtes Bett dreht den liegenden Wichtel mit (`lieSpin`), die Bettkante vorn entfällt dann.
- Schnee an/aus: Chip „❄ Schnee“ im Editor (`EditState.snow` → `PerfRequest.snowFromStart` → `ElfEngine.configureSnow`), die Aktionen gehen zusätzlich (Schnee hört auf, fängt an).
- 3D-Elfin: `blender/elfin3d.py` wird aus `wichtel3d.py` erzeugt (Kleid, Zöpfe, Ringelstrumpfhose, Mütze mit Bommel); Bilder in `assets/elfin3d/` (gleiche Clips und Blickrichtungen wie `elf3d`),
  `Elf3D(context, colors, dir)` und `Elf3D.available(context, dir)` kennen den Ordner; Einstellung: Elfin + Figur 3D.

## Version 0.9.1
- Gegenstände in zwei Stilen: `SceneryGroup.CLASSIC` (Holz, warmes Licht) und `NEON` (alte Cyberpunk-Variante, jetzt „Neon-…“). Neu: Schneemann, Geschenke, Schlitten, Zuckerstangen, Bank, Lagerfeuer,
  Holzzaun, Holzbett, klassische Laterne/Lichterkette/Tannenbaum. Quelle `blender/szene3d.py` (Aufruf mit Liste der Namen, **absoluter** Ausgabepfad!), Bilder in `assets/scenery/`.
- Betten (`SceneryType.isBed`) sind jetzt Seitenansicht (Kopf links, gespiegelt = rechts); der Wichtel liegt quer, die vordere Bettkante wird über ihn gezeichnet (`frontOnlyFrac`).
- Schnee: Aktionen `SNOW`/`SNOW_OFF` (`doSnowOn/doSnowOff`, `FrameState.snow`, `drawSnow` aus der Zeit berechnet), Szene „brav_schnee“.
- App-Aussehen: `Settings.themeMode` (0 System, 1 hell, 2 dunkel), `AppTheme.mode`, `WichtelLight/WichtelDark` in MainActivity.
- Elfin: `anim/ElfinDrawing.kt` (gezeichnete 2D-Figur, `Settings.elfin`), wählbar in den Einstellungen; benutzt dieselbe `Pose` und die Farbwahl (Mantel = Kleid, Hose = Strumpfhose).

## Version 0.9.0 (enthält 0.8.1)
- Neue Aktionen `HOUSE_IN`/`HOUSE_OUT` (`doGoInside`/`doComeOutside`), Szene „brav_zuhause“; im Haus ist der Wichtel unsichtbar (`inHouse`).
- 0.8.1: Export verlängert kurze Videos per Schleife (`needLoop`, `EditedMediaItemSequence(…, true)`), Editor-Dauer = Aufführung (bis 4 min), Aufbau zählt mit.

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
