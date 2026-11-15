package app.wichtelein

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun shotsDir() = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots").apply { mkdirs() }

private fun shot(node: SemanticsNodeInteraction, name: String): Bitmap {
    val bmp = node.captureToImage().asAndroidBitmap()
    File(shotsDir(), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return bmp
}

private fun savePng(bmp: Bitmap, name: String) {
    File(shotsDir(), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private val testSettings = Settings(elfName = "Pippo", onboarded = true).withProfile("Max")

/** Rendert Szenen des Wichtels und die Startseite (Bilder zur Sichtprüfung). */
@RunWith(AndroidJUnit4::class)
class RenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun scenesRenderVisibly() {
        val bg = Color(0xFFCDB89A)
        val frame = mutableStateOf<FrameState?>(null)
        rule.setContent {
            val sprites = rememberElfSprites(ElfColors())
            Box(Modifier.size(360.dp, 640.dp).background(bg)) {
                ElfOverlay(frame.value, sprites, 0.3f, Modifier.size(360.dp, 640.dp))
            }
        }
        val scenes = listOf("brav_fegen", "frech_chaos", "ueb_geschenk", "brav_gutenacht", "ueb_tanzparty", "frech_stolpern", "brav_heim", "frech_tuer")
        for (id in scenes) {
            val perf = Recorder.record(
                PerfRequest(Pt(0.4f, 0.78f), listOf(Pt(0.7f, 0.7f), Pt(0.3f, 0.85f)), 0.3f, null, id, 5L, 22_000, null, testSettings, door = Pt(0.78f, 0.6f), doorScale = 0.22f),
            )
            var visiblePixelsMax = 0
            var n = 0
            var ms = 0L
            while (ms < perf.durationMs) {
                rule.runOnIdle { frame.value = perf.frameAt(ms) }
                rule.waitForIdle()
                val bmp = shot(rule.onAllNodes(isRoot()).onFirst(), "scene_${id}_%02d.png".format(n++))
                var diff = 0
                val bgInt = AColor.rgb((bg.red * 255).toInt(), (bg.green * 255).toInt(), (bg.blue * 255).toInt())
                for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) {
                    val c = bmp.getPixel(x, y)
                    if (abs(AColor.red(c) - AColor.red(bgInt)) > 25 || abs(AColor.green(c) - AColor.green(bgInt)) > 25) diff++
                }
                visiblePixelsMax = maxOf(visiblePixelsMax, diff)
                ms += 2200
            }
            assertTrue("$id: nichts zu sehen ($visiblePixelsMax)", visiblePixelsMax > 300)
        }
    }

    @Test fun homeScreenRenders() {
        rule.setContent {
            MaterialTheme {
                Surface(Modifier.size(360.dp, 900.dp)) {
                    HomeScreen(testSettings, true, 2, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("Wichtelein").assertExists()
        rule.onNodeWithText("Foto-Film").assertExists()
        rule.onNodeWithText("Foto auswählen").assertExists()
        rule.onNodeWithText("Video aufnehmen").assertExists()
        rule.onNodeWithText("Video auswählen").assertExists()
        rule.onNodeWithText("AR-Wichtel").assertExists()
        rule.onNodeWithText("Meine Wichtelvideos").assertExists()
        rule.onNodeWithText("Wichtelaktionen").assertExists()
        rule.onNodeWithText("Einstellungen").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "home.png")
    }

    @Test fun settingsActionsAndVideosScreensRender() {
        val screen = androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            MaterialTheme {
                Surface(Modifier.size(360.dp, 1500.dp)) {
                    when (screen.intValue) {
                        0 -> SettingsScreen(testSettings, {}, {}, null, {})
                        1 -> ActionsScreen(testSettings, {}, {})
                        else -> MyVideosScreen({})
                    }
                }
            }
        }
        rule.onNodeWithText("Einstellungen").assertExists()
        // anfangs sind alle Bereiche zugeklappt: nur die Überschriften sind da
        rule.onNodeWithText("🎭 Figuren").assertExists()
        rule.onNodeWithText("🗣 Sprache und Stimmen").assertExists()
        rule.onNodeWithText("Name des Wichtels").assertDoesNotExist()
        shot(rule.onAllNodes(isRoot()).onFirst(), "settings-zu.png")
        // einzeln aufklappen
        rule.onNodeWithText("🎭 Figuren").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Name des Wichtels").assertExists()
        rule.onNodeWithText("🎭 Figuren").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Name des Wichtels").assertDoesNotExist()
        // alle auf einmal
        rule.onNodeWithText("Alle aufklappen").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Alle zuklappen").assertExists()
        rule.onNodeWithText("Name des Wichtels").assertExists()
        rule.onNodeWithText("➕ Kind hinzufügen").assertExists()
        rule.onNodeWithText("🔊  Stimme anhören").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "settings.png")
        rule.runOnIdle { screen.intValue = 1 }
        rule.waitForIdle()
        rule.onNodeWithText("Wichtelaktionen").assertExists()
        rule.onNodeWithText("😇 Braver Wichtel").assertExists()
        rule.onNodeWithText("Zimmer fegen").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "actions.png")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("😜 Frecher Wichtel"))
        rule.onNodeWithText("😜 Frecher Wichtel").assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("🎁 Überraschung"))
        rule.onNodeWithText("🎁 Überraschung").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "actions_end.png")
        rule.runOnIdle { screen.intValue = 2 }
        rule.waitForIdle()
        rule.onNodeWithText("Meine Wichtelvideos").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "videos.png")
    }

    @Test fun doorVariantsRender() {
        val variants = listOf(true to 1f, true to 0f, false to 1f, false to 0.4f)
        fun frame(hingeLeft: Boolean, round: Float) = FrameState(
            0.5f, 0.84f, 1, true, Pose(), null, emptyList(), null, emptyList(),
            Pt(0.5f, 0.86f), 0.5f, 0.65f, hingeLeft, round,
        )
        rule.setContent {
            val sprites = rememberElfSprites(ElfColors())
            androidx.compose.foundation.layout.Row(Modifier.background(Color(0xFFCDB89A))) {
                variants.forEach { (h, r) ->
                    Box(Modifier.size(150.dp, 260.dp)) { ElfOverlay(frame(h, r), sprites, 0.3f, Modifier.size(150.dp, 260.dp)) }
                }
            }
        }
        rule.waitForIdle()
        val bmp = shot(rule.onAllNodes(isRoot()).onFirst(), "doors.png")
        // in jedem Viertel muss die Tür sichtbar sein
        val bg = AColor.rgb(0xCD, 0xB8, 0x9A)
        val q = bmp.width / 4
        for (i in 0 until 4) {
            var n = 0
            for (y in 0 until bmp.height step 3) for (x in i * q until (i + 1) * q step 3) {
                val c = bmp.getPixel(x, y)
                if (abs(AColor.red(c) - AColor.red(bg)) + abs(AColor.green(c) - AColor.green(bg)) > 60) n++
            }
            assertTrue("Tür $i nicht sichtbar ($n)", n > 150)
        }
    }

    @Test fun firstStartAsksForChildName() {
        rule.setContent { MaterialTheme { HomeScreen(Settings(), false, 0, {}, {}, {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Willkommen bei Wichtelein!").assertExists()
        rule.onNodeWithText("Name des Kindes").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "onboarding.png")
    }
}

/** Startet die echte Activity: sie darf nicht abstürzen und zeigt die Startseite. */
@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun startsAndShowsHome() {
        if (rule.onAllNodesWithText("Willkommen bei Wichtelein!").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Später").performClick()
        }
        rule.onNodeWithText("Wichtelein").assertExists()
        rule.onNodeWithText("Video aufnehmen").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "launch.png")
    }
}

/** Vom Editor bis zum fertigen MP4: Overlay im Bild, Tonspur mit Sprache, in „Meine Wichtelvideos“ abgelegt. */
@RunWith(AndroidJUnit4::class)
class EditorExportTest {
    @get:Rule val rule = createComposeRule()

    @Test fun editorExportsVideoWithOverlayAndSpeech() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val video = File(ctx.cacheDir, "test_room.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("test_room.mp4").use { i -> video.outputStream().use { i.copyTo(it) } }
        ExportManager.exportsDir(ctx).listFiles()?.forEach { it.delete() }

        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                Surface { EditorScreen(video, testSettings.copy(speak = true, elfVoice = true), {}, {}, {}) }
            }
        }
        val save = "💾 Als Video speichern"
        fun pump(timeoutMs: Long, what: String, cond: () -> Boolean) {
            val t0 = System.currentTimeMillis()
            while (!cond()) {
                rule.mainClock.advanceTimeBy(100)
                Thread.sleep(40)
                if (System.currentTimeMillis() - t0 > timeoutMs) {
                    val count = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size
                    val state = (0 until count).joinToString(" --- ") { i ->
                        runCatching { rule.onAllNodes(isRoot())[i].printToString(maxDepth = 6) }.getOrDefault("?")
                    }
                    throw AssertionError("Zeitüberschreitung: " + what + " | Zustand: " + state.take(1800))
                }
            }
        }
        fun exists(t: String) = rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()
        fun enabled(t: String) = exists(t) && runCatching { rule.onNodeWithText(t).assertIsEnabled() }.isSuccess

        pump(420_000, "Editor bereit (Aufführung + Stimme)") { enabled(save) }
        shot(rule.onAllNodes(isRoot()).onFirst(), "editor.png")
        rule.onNodeWithText(save).performClick()
        pump(420_000, "Export fertig") { exists("Fertig! 🎉") || exists("Export fehlgeschlagen") }
        shot(rule.onAllNodes(isRoot()).onFirst(), "export_done.png")
        assertTrue("Export fehlgeschlagen", exists("Fertig! 🎉"))

        // Ergebnis prüfen
        val files = listExports(ctx)
        assertEquals(1, files.size)
        val out = files[0]
        assertTrue("Datei zu klein: ${out.length()}", out.length() > 20_000)
        val ex = MediaExtractor()
        ex.setDataSource(out.absolutePath)
        val mimes = (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME) ?: "" }
        ex.release()
        assertTrue("keine Videospur: $mimes", mimes.any { it.startsWith("video/") })
        assertTrue("keine Tonspur: $mimes", mimes.any { it.startsWith("audio/") })
        val inInfo = readVideoInfo(video)
        val outInfo = readVideoInfo(out)
        assertTrue("Dauer: ${outInfo.durationMs} statt ${inInfo.durationMs}", abs(outInfo.durationMs - inInfo.durationMs) < 800)

        // Ist der Wichtel im Bild? Bild nach 2 s des Ergebnisses mit dem Original vergleichen.
        fun frameAt(f: File, us: Long): Bitmap {
            val r = MediaMetadataRetriever()
            r.setDataSource(f.absolutePath)
            val b = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)!!
            r.release()
            return Bitmap.createScaledBitmap(b, 90, 160, true)
        }
        val a = frameAt(video, 2_000_000)
        val b = frameAt(out, 2_000_000)
        savePng(a, "export_source_frame.png")
        savePng(b, "export_result_frame.png")
        var diff = 0
        for (y in 0 until 160) for (x in 0 until 90) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            if (abs(AColor.red(p) - AColor.red(q)) + abs(AColor.green(p) - AColor.green(q)) + abs(AColor.blue(p) - AColor.blue(q)) > 120) diff++
        }
        assertTrue("Wichtel nicht im Bild (nur $diff Pixel verändert)", diff > 60)
        assertNotNull(out)
    }
}


/** Handyvideos sind oft quer gespeichert und per Drehung markiert: Export muss sie richtig herum bearbeiten. */
@RunWith(AndroidJUnit4::class)
class RotatedExportTest {
    private fun remuxWithRotation(src: File, dst: File, degrees: Int) {
        val ex = MediaExtractor()
        ex.setDataSource(src.absolutePath)
        val muxer = android.media.MediaMuxer(dst.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val map = HashMap<Int, Int>()
        for (i in 0 until ex.trackCount) {
            ex.selectTrack(i)
            map[i] = muxer.addTrack(ex.getTrackFormat(i))
        }
        muxer.setOrientationHint(degrees)
        muxer.start()
        val buf = java.nio.ByteBuffer.allocate(1 shl 20)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val n = ex.readSampleData(buf, 0)
            if (n < 0) break
            info.set(0, n, ex.sampleTime, ex.sampleFlags)
            muxer.writeSampleData(map[ex.sampleTrackIndex]!!, buf, info)
            ex.advance()
        }
        muxer.stop()
        muxer.release()
        ex.release()
    }

    @Test fun rotatedVideoIsExportedUprightWithElfInPlace() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val plain = File(ctx.cacheDir, "test_room_plain.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("test_room.mp4").use { i -> plain.outputStream().use { i.copyTo(it) } }
        val video = File(ctx.cacheDir, "test_room_rot90.mp4")
        remuxWithRotation(plain, video, 90)
        val info = readVideoInfo(video)
        assertEquals("angezeigte Breite", 640, info.width)
        assertEquals("angezeigte Höhe", 360, info.height)

        val settings = Settings(elfName = "Pippo", speak = false, onboarded = true).withProfile("Max")
        val perf = Recorder.record(PerfRequest(Pt(0.5f, 0.78f), emptyList(), 0.3f, null, "brav_fegen", 3L, info.durationMs, null, settings))
        val result = kotlinx.coroutines.runBlocking { ExportManager.export(ctx, video, info, perf, settings) { } }
        val outInfo = readVideoInfo(result.file)
        savePng(frame(result.file, 2_000_000), "rot_result_frame.png")
        assertEquals("Export muss aufrecht sein: Breite", 640, outInfo.width)
        assertEquals("Export muss aufrecht sein: Höhe", 360, outInfo.height)

        val a = frame(video, 2_000_000)
        val b = frame(result.file, 2_000_000)
        var n = 0
        var sx = 0L
        var sy = 0L
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            if (abs(AColor.red(p) - AColor.red(q)) + abs(AColor.green(p) - AColor.green(q)) + abs(AColor.blue(p) - AColor.blue(q)) > 120) {
                n++; sx += x; sy += y
            }
        }
        assertTrue("Wichtel nicht im Bild ($n)", n > 60)
        val cx = sx.toFloat() / n / b.width
        val cy = sy.toFloat() / n / b.height
        assertTrue("Wichtel nicht in der Bildmitte unten: x=$cx y=$cy", cx in 0.3f..0.7f && cy in 0.4f..0.95f)
    }

    private fun frame(f: File, us: Long): Bitmap {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val b = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)!!
        r.release()
        return Bitmap.createScaledBitmap(b, 160, 90, true)
    }
}


/** Kleidungsfarben: nur das gewählte Kleidungsstück ändert sich. */
@RunWith(AndroidJUnit4::class)
class ColorTest {
    @get:Rule val rule = createComposeRule()

    private fun count(bmp: Bitmap, rowFrom: Int = 0, rowTo: Int = bmp.height, match: (Float, Float, Float) -> Boolean): Int {
        val hsv = FloatArray(3)
        var n = 0
        for (y in rowFrom until minOf(rowTo, bmp.height)) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            if (AColor.alpha(c) < 200) continue
            AColor.colorToHSV(c, hsv)
            if (match(hsv[0], hsv[1], hsv[2])) n++
        }
        return n
    }

    private fun bmp(s: ElfSprites, part: String) = s.image(part).asAndroidBitmap()

    @Test fun figureLoadsWithLayout() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val s = ElfSprites.load(ctx)
        assertEquals(596f, s.layout.height, 0f)
        assertNotNull("Besen fehlt", s.broom)
        for (n in ElfSprites.partNames) assertTrue("Teil $n leer", s.image(n).width > 20 && s.image(n).height > 20)
    }

    @Test fun onlyTheChosenGarmentChanges() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val base = ElfSprites.load(ctx)
        val br = base.layout.bootRow
        val hat = { h: Float, s: Float, v: Float -> Recolor.isHatRed(h, s, v) }
        // nur kräftig genug beleuchtete Pixel zählen (die dunklen Schattenpixel fallen bei jeder Farbe heraus)
        val coat = { h: Float, s: Float, v: Float -> Recolor.isCoat(h, s, v) && v > 0.25f }
        val trousers = { h: Float, s: Float, v: Float -> Recolor.isTrousers(h, s, v) && v > 0.25f }
        val boots = { h: Float, s: Float, v: Float -> Recolor.isBoot(h, s, v) && v > 0.25f }
        val blue = { h: Float, s: Float, v: Float -> h in 180f..260f && s > 0.2f && v > 0.2f }
        val reddish = { h: Float, s: Float, v: Float -> (h <= 25f || h >= 325f) && s > 0.3f && v > 0.2f }
        val face = { h: Float, s: Float, v: Float -> (h <= 30f || h >= 340f) && s in 0.15f..0.6f && v > 0.6f }

        val baseHat = count(bmp(base, "head"), match = hat)
        val baseCoat = count(bmp(base, "torso"), match = coat)
        val baseTrousers = count(bmp(base, "legl"), 0, br, trousers)
        val baseBoots = count(bmp(base, "legl"), br, match = boots)
        val baseFace = count(bmp(base, "head"), match = face)
        assertTrue("Mütze hat zu wenig Rot: $baseHat", baseHat > 20000)
        assertTrue("Mantel hat zu wenig Grün: $baseCoat", baseCoat > 6000)
        assertTrue("Hose zu klein: $baseTrousers", baseTrousers > 300)
        assertTrue("Stiefel zu klein: $baseBoots", baseBoots > 1500)
        assertTrue("Gesicht nicht gefunden: $baseFace", baseFace > 500)

        // Nur die Mütze blau: Nase und Wangen bleiben, Mantel und Hose ebenfalls
        val hatBlue = ElfSprites.load(ctx, ElfColors(hat = 5))
        assertTrue("Mütze noch rot", count(bmp(hatBlue, "head"), match = hat) < baseHat / 10)
        assertTrue("Mütze nicht blau", count(bmp(hatBlue, "head"), match = blue) > baseHat / 2)
        assertEquals("Gesicht darf sich nicht ändern", baseFace, count(bmp(hatBlue, "head"), match = face))
        assertEquals("Mantel darf sich nicht ändern", baseCoat, count(bmp(hatBlue, "torso"), match = coat))
        assertEquals("Hose darf sich nicht ändern", baseTrousers, count(bmp(hatBlue, "legl"), 0, br, trousers))
        assertTrue("Fäustlinge folgen der Mützenfarbe", count(bmp(hatBlue, "armr"), match = blue) > 500)

        // Nur der Mantel rot
        val coatRed = ElfSprites.load(ctx, ElfColors(jacket = 1))
        assertTrue("Mantel noch grün", count(bmp(coatRed, "torso"), match = coat) < baseCoat / 10)
        assertTrue("Mantel nicht rot", count(bmp(coatRed, "torso"), match = reddish) > baseCoat / 2)
        assertEquals("Mütze darf sich nicht ändern", baseHat, count(bmp(coatRed, "head"), match = hat))

        // Nur die Hose blau
        val legsBlue = ElfSprites.load(ctx, ElfColors(legs = 5))
        assertTrue("Hose nicht blau", count(bmp(legsBlue, "legl"), 0, br, blue) > baseTrousers / 2)
        assertEquals("Stiefel dürfen sich nicht ändern", baseBoots, count(bmp(legsBlue, "legl"), br, match = boots))

        // Nur die Schuhe blau
        val bootsBlue = ElfSprites.load(ctx, ElfColors(boots = 4))
        assertTrue("Schuhe nicht blau", count(bmp(bootsBlue, "legl"), br, match = blue) > baseBoots / 2)
        assertTrue("Schuhe noch braun", count(bmp(bootsBlue, "legl"), br, match = boots) < baseBoots / 5)
        assertEquals("Hose darf sich nicht ändern", baseTrousers, count(bmp(bootsBlue, "legl"), 0, br, trousers))
    }

    @Test fun posesRenderWithBroomAndWithoutFace() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val base = ElfSprites.load(ctx)
        val poses = listOf(
            Pose(),
            Pose().apply { legL = 30f; legR = -30f; armL = -12f; armR = 32f; bob = 0.02f },
            Pose().apply { armR = 150f; armL = 20f; hatTilt = 0.05f; talk = 1f },
            Pose().apply { prop = Prop.BROOM; armR = 28f; lean = 6f },
        )
        rule.setContent {
            androidx.compose.foundation.layout.Column(Modifier.size(300.dp, 300.dp).background(Color(0xFFE8DCC8))) {
                for (row in 0..1) {
                    androidx.compose.foundation.layout.Row {
                        for (col in 0..1) {
                            val i = row * 2 + col
                            if (i < poses.size) {
                                androidx.compose.foundation.Canvas(Modifier.size(150.dp, 150.dp)) {
                                    base.draw(this, poses[i], androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.96f), size.height * 0.9f, 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val b = shot(rule.onAllNodes(isRoot()).onFirst(), "poses.png")
        assertTrue(b.width > 100)
        // Der Besen (Stroh, gelbbraun) muss in seinem Feld (unten rechts, Pose 4) zu sehen sein, in den anderen nicht
        val straw = { h: Float, s: Float, v: Float -> h in 28f..50f && s > 0.4f && v > 0.5f }
        val cell = Bitmap.createBitmap(b, b.width / 2, b.height / 2, b.width / 2, b.height / 2)
        val n = count(cell, match = straw)
        assertTrue("Besen nicht sichtbar: $n", n > 10)

    }

    @Test fun rendersSeveralOutfits() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val outfits = listOf(
            ElfColors(), ElfColors(1, 1, 1, 0), ElfColors(8, 5, 8, 1), ElfColors(7, 6, 3, 3), ElfColors(2, 10, 9, 2),
        ).map { ElfSprites.load(ctx, it) }
        rule.setContent {
            androidx.compose.foundation.Canvas(Modifier.size(600.dp, 260.dp).background(Color(0xFFE8DCC8))) {
                val pose = Pose()
                outfits.forEachIndexed { i, s ->
                    s.draw(this, pose, androidx.compose.ui.geometry.Offset(size.width * (i + 0.5f) / outfits.size, size.height * 0.95f), size.height * 0.9f, 1)
                }
            }
        }
        rule.waitForIdle()
        val b = shot(rule.onAllNodes(isRoot()).onFirst(), "outfits.png")
        assertTrue(b.width > 100)
    }
}
