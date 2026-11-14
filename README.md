# Wichtelein 🧝

Eine kleine Android-App für Kinder: Man filmt sein echtes Zimmer, und danach kommt ein Wichtel ins Video.
Er läuft herum, räumt auf, macht Chaos, tanzt, purzelt, nascht Kekse, versteckt sich, schläft ein
und spricht dabei zufällige Sätze – auch mit dem Namen des Kindes.

## So funktioniert es

1. **Name(n) einstellen**: Name des Wichtels, Name(n) des Kindes (mehrere mit Komma), Farbe, Stimme
   (Wichtelstimme oder Systemstimme, Stimmung, Tonhöhe, Tempo), Sprachausgabe an/aus und eigene Sätze
   (`{kind}` und `{wichtel}` als Platzhalter).
2. **Raum filmen** (5–10 Sekunden, Handy ruhig halten, maximal 30 s, ohne Ton) – oder ein vorhandenes Video aus der Galerie wählen.
3. Das Video läuft in Endlosschleife. **Auf den Boden tippen**: Dort erscheint der Wichtel und begrüßt das Kind.
4. Danach macht er zufällig immer wieder etwas anderes. Antippen schickt ihn an eine andere Stelle;
   Knöpfe: 🧹 Aufräumen, 🌀 Chaos, 🎲 Zufall, Größer/Kleiner.

Chaos wirft Socken, Klötze, Bälle und Papier herum, die liegen bleiben, bis der Wichtel sie beim Aufräumen einsammelt.

## Technik

- Kotlin, Jetpack Compose, CameraX (Video).
- **Wichtel:** Er besteht aus ausgeschnittenen Einzelteilen (Kopf mit Mütze, Körper, zwei Arme, zwei Beine mit Schuhen,
  `app/src/main/res/drawable-nodpi/elf*_*.png`, vier Farbvarianten). `ElfDrawing.kt` dreht sie um ihre Gelenke,
  zeichnet Mund, Lider und Besen darüber, `ElfEngine.kt` steuert die Aktionen. Es ist eine 2D-Puppe
  (Frontansicht, gespiegelt), keine echte 3D-Figur.
- **Wichtelstimme:** neuronales deutsches Sprachmodell (Piper „Thorsten emotional“, CC0) über sherpa-onnx (Apache-2.0),
  läuft komplett offline auf dem Handy. Die Tonhöhe wird mit einem eigenen WSOLA-Pitch-Shifter angehoben, ohne die Sprache
  schneller zu machen (`VoiceEffect.kt`). Stimmungen: fröhlich, überrascht, normal, müde, flüsternd. Fehlt das Modell,
  spricht die Android-Systemstimme, sonst bleiben nur die Sprechblasen.
- Die App ist durch das Sprachmodell groß (ca. 150 MB). Mindestens Android 8.0 (API 26), 64-Bit-Handys (arm64). Kamera-Berechtigung wird beim Filmen erfragt; Netzwerk wird nur für die Update-Prüfung bei GitHub genutzt (Knopf „Nach Update suchen“), sonst bleibt alles lokal.
- Reine Logik (Sätze, Namen, Zufallsauswahl) ist per Unit-Test geprüft: `gradle testDebugUnitTest`.

## Releases und Updates

- Jeder Push auf `main` baut über `android.yml` eine **signierte Release-APK** und veröffentlicht sie als GitHub-Release `build-<Nr>`.
  Die Build-Nummer ist der `versionCode`, deshalb lässt sich jede neue APK über die alte installieren.
- Signiert wird mit dem gemeinsamen Release-Schlüssel aus den Repository-Secrets
  `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Der Schlüssel liegt nie im Repo.
- Die App prüft per „Nach Update suchen“ die öffentliche GitHub-Releases-API (wie die anderen Projekte, ohne Token).
  Das funktioniert nur, solange das Repository öffentlich ist.

## Bauen

- **GitHub Actions**: `android.yml` baut die signierte Release-APK, `ci.yml` führt Unit-Tests und Emulator-Tests aus
  (App starten, Wichtel rendern, Stimme erzeugen; Screenshots und WAV-Dateien liegen als Artefakt `emulator-screenshots` bei).
- Zuerst einmalig `bash tools/fetch-voice.sh` ausführen: Es lädt sherpa-onnx und das Stimmenmodell (SHA-256-geprüft) nach
  `app/libs/` und `app/src/main/assets/voice/`. Beides liegt bewusst nicht im Repository.
- Lokal: Android Studio öffnen, oder `./gradlew assembleDebug` (JDK 17, Android SDK 35).
  Die APK liegt dann unter `app/build/outputs/apk/debug/app-debug.apk`.

## Bekannte Grenzen

- Der Wichtel ist ein Overlay auf einem festen Video, kein echtes AR: Die Kamera sollte beim Filmen ruhig stehen,
  sonst „rutscht“ der Wichtel gegenüber dem Raum. Echte Raumerkennung (ARCore) wäre ein späterer Ausbau.
- Der Wichtel wird nicht ins Video eingebrannt, es gibt also noch keinen Export als neues Video (Bildschirmaufnahme geht).
- Die Wichtelbilder stammen aus einem vom Auftraggeber bereitgestellten Referenzbild; Herkunft und Nutzungsrechte sind vor
  einer Veröffentlichung zu klären.
- Noch nicht umgesetzt: Videoexport mit Ton, Editor mit Bewegungsweg, Kategorien „Braver/Frecher Wichtel“, Kinderprofile,
  „Meine Wichtelvideos“, Blender-3D-Figur.
- Wie gut der Name ausgesprochen wird, hängt von der Sprachausgabe des Handys ab.
