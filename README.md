# Wichtelein 🧝

Eine Android-App für Kinder: Man filmt sein echtes Zimmer, und danach kommt ein kleiner Wichtel ins Video. Er läuft herum,
räumt auf, macht Chaos, tanzt, purzelt, stolpert, nascht Kekse, versteckt sich und spricht dabei – auch mit dem Namen des Kindes.
Am Ende gibt es ein fertiges MP4-Video mit Bild und Ton zum Speichern und Teilen.

Namen, Sprüche, Stimme, Farbe und die erlaubten Szenen sind frei einstellbar, die App ist nicht an ein bestimmtes Kind gebunden.

## So funktioniert es

0. **Kleidung:** In den Einstellungen lassen sich Mütze, Jacke, Strumpfhose und Schuhe einzeln umfärben (11 Farben, Vorschau).
1. **Erster Start:** Ein Erwachsener gibt den Namen des Kindes ein (weitere Kinder später in den Einstellungen).
2. **Video aufnehmen** (Hoch- oder Querformat, bis 30 s, ohne Ton) oder **Video auswählen**.
3. **Editor:** Startpunkt antippen, Größe einstellen, optional einen Weg aus bis zu 8 Punkten antippen, bei einem **Wichtelhaus** die Tür markieren (Position und Größe), Kategorie
   (*Braver Wichtel*, *Frecher Wichtel*, *Überraschung* oder Zufall) und gewünschte Szene wählen oder **neu würfeln**.
   Die Vorschau läuft sofort über dem Video, die Sprache wird passend zum Zeitpunkt abgespielt.
4. **Als Video speichern:** Fortschrittsanzeige, danach liegt das MP4 in der Galerie (*Filme/Wichtelein*) und unter
   **Meine Wichtelvideos** (ansehen, teilen, löschen).
5. **Wichtelaktionen:** Hier lassen sich einzelne Szenen ein- und ausschalten.

## Was fertig ist, was nur Prototyp ist, was fehlt

**Umgesetzt und im Android-Emulator der CI geprüft** (11 Emulator-Tests und Unit-Tests, siehe unten):
- Startseite mit den fünf Bereichen, Namensabfrage beim ersten Start, Kinderprofile, Einstellungen, Wichtelaktionen,
  Meine Wichtelvideos (Darstellung), Editor mit Startpunkt, Größe, Weg, Szene und Würfeln.
- Aktionen: Laufen, Stehen, Winken, Fegen, Tanzen, Stolpern, Hinsetzen, Verstecken, Purzelbaum, Schlafen, Naschen, Chaos
  (Spielzeug werfen), Aufräumen, Zauber; Gegenstände: Besen, Geschenk, Keks, Spielzeug, Konfetti.
- **Wichtelhaus-Tür:** Im Editor wird die Tür im Video markiert; der Wichtel öffnet sie (Türblatt schwingt auf, warmes Licht), geht hinein, schließt sie,
  kommt wieder heraus oder fegt davor (4 Türszenen, nur wenn eine Tür markiert ist). Die Tür wird als Overlay gezeichnet.
- **Kleidungsfarben** je Kleidungsstück (Mütze, Jacke, Strumpfhose, Schuhe), zur Laufzeit per HSV-Umfärbung der Bildteile.
- 19 Szenen in 3 Kategorien, die Auswahl variiert; jede Aufführung ist über einen Startwert reproduzierbar und lässt sich neu erzeugen.
- Über 30 kindgerechte Sprüche plus Szenensprüche, Platzhalter `{name}` und `{wichtel}`, eigene Sätze.
- Sprache und Bewegung kommen aus **einer** Zeitleiste und passen daher zusammen.
- Export als MP4 mit Bild (Wichtel über dem Originalvideo, Schatten) und Ton (Sprache, ggf. Originalton), Fortschritt, Fehlerdialog;
  getestet bis zum fertigen Video: Video- und Tonspur vorhanden, Wichtel im Bild – auch bei Videos, die wie Handyaufnahmen
  gedreht gespeichert sind (Hochformat als gedrehtes Querformat).
- Offline, kein Konto, keine Werbung, keine Tracker. Berechtigung nur für die Kamera; das Internet wird nur für die
  Update-Suche und optionale Stimmen-Downloads benutzt.
- Signierte Release-APK per GitHub Actions, Update-Suche in der App.

**Prototypisch / mit Einschränkung:**
- Der Wichtel ist eine **2D-Puppe** aus ausgeschnittenen Teilen eines Referenzbildes (Frontansicht, wird gespiegelt), keine 3D-Figur:
  Beine, Arme, Körper und Kopf drehen sich um ihre Gelenke (Farben frei wählbar), dazu Blinzeln, Kopfnicken beim Sprechen, Mund, Schatten.
  Das ist bewusst gewählt, weil es deutlich besser aussieht als ein selbst modelliertes einfaches 3D-Modell.
  Die Bilder sind in `app/src/main/res/drawable-nodpi/`. **Herkunft und Nutzungsrechte des Referenzbildes sind vor einer
  Veröffentlichung der Figur zu klären.**
- Es gibt kein Tracking: Der Wichtel liegt als Overlay über einem Video, die Kamera sollte also ruhig stehen. Keine räumliche Erkennung.
- Im Export ist die Sprache der **Wichtelstimme** (offline) enthalten, geprüft im Emulator. Wird die **Systemstimme** gewählt, erzeugt die App
  die Sprache über `synthesizeToFile`; das ist umgesetzt und per Unit-Test des WAV-Einlesens abgesichert, konnte aber im Emulator
  nicht ausgeführt werden (kein deutsches TTS-Paket im Test-Image). Gelingt es nicht, zeigt das Video nur Sprechblasen und die App sagt es.
- Hoch-/Querformat-Aufnahme und das Abspielen über „Ansehen“/„Teilen“ sind implementiert, aber **nicht auf einem echten Handy getestet**
  (der Emulator hat keine brauchbare Kamera). Ebenso ist die **Klangqualität der Stimmen nur vom Nutzer beurteilbar**.

**Fehlt:**
- Blender-3D-Figur und vorgerenderte transparente Animationssequenzen: Blender war beim Bau nicht erreichbar (MCP-Verbindung
  schlug fehl). Die Figur ist über die Schnittstelle `ElfFigure` austauschbar; Szenen, Zeitleiste, Editor und Export bleiben dann unverändert.
- Echtes AR mit ARCore (laut Auftrag eine spätere Zusatzfunktion).
- Anpassbare Bewegungsabläufe pro Szene über die Wegpunkte hinaus; Mehrfach-Videos in einem Projekt.
- Weitere Kleidungsstücke (z. B. andere Mützen oder Schals) – bisher nur die Farben.
- Die Wichtelhaus-Tür ist ein gezeichnetes Türblatt über der echten Tür im Video; Aussehen und Farbe lassen sich noch nicht einstellen.

## Stimmen

Die Wichtelstimme ist ein neuronales deutsches Sprachmodell, das komplett offline auf dem Handy läuft
(Piper über sherpa-onnx, CC0/Apache-2.0). Eingebaut ist „Thorsten (mit Stimmungen)“. In den Einstellungen lassen sich weitere
Stimmen laden (Thorsten hoch, Miro, Dii, Ramona, Kerstin, Karlsson; Download von der offiziellen k2-fsa-Veröffentlichung, SHA-256-geprüft)
und mit „Stimme anhören“ ausprobieren. Der **Wichtel-Effekt** hebt die Tonhöhe dosiert an (0 = natürlich); Tempo und Stimmung sind einstellbar.
Fehlt das Modell, spricht die Android-Systemstimme, sonst bleiben nur die Sprechblasen.

## Technik und Aufbau

Kotlin, Jetpack Compose, CameraX, AndroidX Media3 Transformer, sherpa-onnx. Eine App, Aufgaben nach Paketen getrennt
(`dependabot.yml` schlägt wöchentlich Aktualisierungen der Abhängigkeiten vor):

| Bereich (Paket unter `app.wichtelein`) | Inhalt |
|---|---|
| `camera` | `CameraScreen.kt` (Aufnahme, Drehung), `VideoInfo.kt` |
| `anim` | `ElfEngine.kt` (Aktionen, Takt), `ElfDrawing.kt` (Figur `ElfFigure`/2D-Puppe), `Performance.kt` (Aufnahme der Zeitleiste), `ElfOverlay.kt` (eine Zeichenfunktion für Vorschau und Export) |
| `scene` | `Scenes.kt` (15 Szenen, Zufallsauswahl), `Texts.kt` (Sprüche, Platzhalter) |
| `speech` | `ElfVoice.kt`, `VoiceCatalog.kt`, `VoiceEffect.kt`, `Speaker.kt`, `SpeechRender.kt`, `VoiceUi.kt` |
| `videoexport` | `Export.kt` (Media3 Transformer, Galerie, Teilen) |
| `settings` | `Settings.kt` (Einstellungen, Profile), `Updater.kt` |
| `ui` | `HomeScreen.kt`, `EditorScreen.kt`, `SettingsScreen.kt`, `ActionsAndVideosScreens.kt` |
| Wurzel | `MainActivity.kt` (Navigation) |

Ablauf: Der Editor lässt die Engine mit einer virtuellen Uhr und festem Zufallsstartwert eine komplette Aufführung **aufnehmen**
(Bilder mit 30 fps + Sprach-Ereignisse). Vorschau und Export spielen dieselbe Zeitleiste ab.

## Bauen

- Zuerst einmalig `bash tools/fetch-voice.sh`: lädt sherpa-onnx 1.13.8 (Version 2.x enthält die nötige Phonemisierung nicht mehr)
  und das eingebaute Stimmenmodell (SHA-256-geprüft) nach `app/libs/` und `app/src/main/assets/voice/`. Beides liegt nicht im Repository.
- Lokal: Android Studio öffnen oder `./gradlew assembleDebug` (JDK 17, Android SDK 35, mindestens Android 10).
- **GitHub Actions:** `android.yml` baut bei jedem Push die signierte Release-APK (Release `build-<Nr>`),
  `ci.yml` führt Unit- und Emulator-Tests aus (App starten, Szenen rendern, Stimme erzeugen, Stimmen-Download,
  Editor bis zum fertigen MP4 prüfen); Screenshots, Videobilder und WAV-Dateien liegen als Artefakt `emulator-screenshots` bei.

## Releases und Updates

- Die Build-Nummer ist der `versionCode`; jede neue APK lässt sich über die alte installieren.
- Signiert wird mit dem gemeinsamen Release-Schlüssel aus den Repository-Secrets `ANDROID_KEYSTORE_BASE64`,
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Der Schlüssel liegt nie im Repo.
- „Nach Update suchen“ fragt die öffentliche GitHub-Releases-API ab (ohne Token).

## Datenschutz

Videos, Namen und Einstellungen bleiben lokal auf dem Gerät. Persönliche Videos gehören nicht in dieses Repository
(`*.apk` und Schlüsseldateien sind in `.gitignore`).
