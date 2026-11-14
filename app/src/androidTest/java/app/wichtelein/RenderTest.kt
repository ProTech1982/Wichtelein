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
        rule.onNodeWithText("Video aufnehmen").assertExists()
        rule.onNodeWithText("Video auswählen").assertExists()
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
                    val state = rule.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("
---
") { i ->
                        runCatching { rule.onAllNodes(isRoot())[i].printToString(maxDepth = 6) }.getOrDefault("?")
                    }
                    throw AssertionError("Zeitüberschreitung: $what
Zustand:
${state.take(1800)}")
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

    private fun count(bmp: Bitmap, match: (Float, Float, Float) -> Boolean): Int {
        val hsv = FloatArray(3)
        var n = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            if (AColor.alpha(c) < 200) continue
            AColor.colorToHSV(c, hsv)
            if (match(hsv[0], hsv[1], hsv[2])) n++
        }
        return n
    }

    private fun bmp(s: ElfSprites, part: String) = s.image(part).asAndroidBitmap()

    @Test fun onlyTheChosenGarmentChanges() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val base = ElfSprites.load(ctx)
        val green = { h: Float, s: Float, v: Float -> Recolor.isGreen(h, s, v) }
        val red = { h: Float, s: Float, v: Float -> (h <= 25f || h >= 325f) && s > 0.4f && v > 0.25f }
        val blue = { h: Float, s: Float, v: Float -> h in 180f..260f && s > 0.4f && v > 0.2f }

        val baseHatGreen = count(bmp(base, "head"), green)
        val baseJacketGreen = count(bmp(base, "torso"), green)
        val baseLegGreen = count(bmp(base, "legl"), green)
        assertTrue("Mütze hat zu wenig Grün: $baseHatGreen", baseHatGreen > 1500)
        assertTrue("Jacke hat zu wenig Grün: $baseJacketGreen", baseJacketGreen > 4000)

        // Nur die Mütze rot
        val hatRed = ElfSprites.load(ctx, ElfColors(hat = 1))
        assertTrue("Mütze noch grün", count(bmp(hatRed, "head"), green) < baseHatGreen / 10)
        assertTrue("Mütze nicht rot", count(bmp(hatRed, "head"), red) > baseHatGreen / 2)
        assertEquals("Jacke darf sich nicht ändern", baseJacketGreen, count(bmp(hatRed, "torso"), green))
        assertEquals("Beine dürfen sich nicht ändern", baseLegGreen, count(bmp(hatRed, "legl"), green))

        // Nur die Jacke blau (Körper und beide Arme)
        val jacketBlue = ElfSprites.load(ctx, ElfColors(jacket = 5))
        assertTrue("Jacke noch grün", count(bmp(jacketBlue, "torso"), green) < baseJacketGreen / 10)
        assertTrue("Jacke nicht blau", count(bmp(jacketBlue, "torso"), blue) > baseJacketGreen / 2)
        assertTrue("Arm nicht blau", count(bmp(jacketBlue, "armr"), blue) > 500)
        assertEquals("Mütze darf sich nicht ändern", baseHatGreen, count(bmp(jacketBlue, "head"), green))

        // Nur die Schuhe blau
        val bootsBlue = ElfSprites.load(ctx, ElfColors(boots = 4))
        val baseBootRed = count(bmp(base, "legl").let { Bitmap.createBitmap(it, 0, 110, it.width, it.height - 110) }, red)
        val newBootRed = count(bmp(bootsBlue, "legl").let { Bitmap.createBitmap(it, 0, 110, it.width, it.height - 110) }, red)
        assertTrue("Schuhe waren nicht rotbraun? $baseBootRed", baseBootRed > 800)
        assertTrue("Schuhe noch rot: $newBootRed", newBootRed < baseBootRed / 5)
        assertEquals("Strumpfhose darf sich nicht ändern", baseLegGreen, count(bmp(bootsBlue, "legl"), green))
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
