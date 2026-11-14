package app.wichtelein

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
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
            val sprites = rememberElfSprites(0)
            Box(Modifier.size(360.dp, 640.dp).background(bg)) {
                ElfOverlay(frame.value, sprites, 0.3f, Modifier.size(360.dp, 640.dp))
            }
        }
        val scenes = listOf("brav_fegen", "frech_chaos", "ueb_geschenk", "brav_gutenacht", "ueb_tanzparty", "frech_stolpern")
        for (id in scenes) {
            val perf = Recorder.record(
                PerfRequest(Pt(0.4f, 0.78f), listOf(Pt(0.7f, 0.7f), Pt(0.3f, 0.85f)), 0.3f, null, id, 5L, 22_000, null, testSettings),
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
        rule.onNodeWithText("😜 Frecher Wichtel").assertExists()
        rule.onNodeWithText("🎁 Überraschung").assertExists()
        rule.onNodeWithText("Zimmer fegen").assertExists()
        shot(rule.onAllNodes(isRoot()).onFirst(), "actions.png")
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
                if (System.currentTimeMillis() - t0 > timeoutMs) throw AssertionError("Zeitüberschreitung: $what")
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
