package app.wichtelein

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun shot(node: SemanticsNodeInteraction, name: String) {
    val bmp = node.captureToImage().asAndroidBitmap()
    val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots").apply { mkdirs() }
    File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** Rendert den Wichtel mit seinen zufälligen Aktionen und speichert Bilder zur Sichtprüfung. */
@RunWith(AndroidJUnit4::class)
class ElfRenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun elfAppearsAnimatesAndSpeaks() {
        val spoken = mutableListOf<String>()
        val engine = ElfEngine({ Settings(kidNames = "Max", elfName = "Pippo") }, { text, _ -> spoken += text })
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val sprites = rememberElfSprites(0)
            LaunchedEffect(Unit) {
                engine.loop {
                    engine.appear(0.5f, 0.75f)
                    engine.greet()
                }
            }
            Box(Modifier.size(360.dp, 640.dp).background(Color(0xFFCDB89A))) {
                ElfOverlay(engine, sprites, 0.3f, { _, _ -> }, Modifier.size(360.dp, 640.dp))
            }
        }
        var sawBubble = false
        repeat(40) { i ->
            rule.mainClock.advanceTimeBy(700)
            if (engine.bubble != null) sawBubble = true
            shot(rule.onRoot(), "frame_%02d.png".format(i))
        }
        assertTrue("Wichtel nicht sichtbar", engine.visible)
        assertTrue("Keine Sprechblase", sawBubble)
        assertTrue("Nichts gesprochen", spoken.isNotEmpty())
        assertTrue("Begrüßung fehlt", spoken.first().contains("Max") || spoken.first().contains("Pippo"))
    }

    @Test fun chaosThrowsItemsAndTidyCollectsThem() {
        val engine = ElfEngine({ Settings() }, { _, _ -> })
        rule.mainClock.autoAdvance = false
        var phase = 0
        rule.setContent {
            val sprites = rememberElfSprites(1)
            LaunchedEffect(Unit) {
                engine.appear(0.5f, 0.7f)
                engine.doChaos()
                phase = 1
                engine.doTidy()
                phase = 2
            }
            Box(Modifier.size(360.dp, 640.dp).background(Color(0xFFCDB89A))) {
                ElfOverlay(engine, sprites, 0.3f, { _, _ -> }, Modifier.size(360.dp, 640.dp))
            }
        }
        var maxMess = 0
        var i = 0
        while (phase < 2 && i < 200) {
            rule.mainClock.advanceTimeBy(400)
            maxMess = maxOf(maxMess, engine.mess.size)
            if (i % 4 == 0) shot(rule.onRoot(), "chaos_%02d.png".format(i / 4))
            i++
        }
        assertTrue("Chaos hat nichts verstreut", maxMess >= 3)
        assertTrue("Aufräumen wurde nicht fertig", phase == 2)
        assertTrue("Nach dem Aufräumen liegt noch etwas herum", engine.mess.isEmpty())
    }

    @Test fun homeScreenRenders() {
        rule.setContent {
            MaterialTheme {
                Surface(Modifier.size(360.dp, 900.dp)) {
                    HomeScreen(Settings(kidNames = "Max, Lena"), true, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithText("Wichtelein").assertExists()
        shot(rule.onRoot(), "home.png")
    }
}

/** Startet die echte Activity: die App darf nicht abstürzen und zeigt die Startseite. */
@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun startsAndShowsHome() {
        rule.onNodeWithText("Wichtelein").assertExists()
        rule.onNodeWithText("🎥  Raum filmen").assertExists()
        shot(rule.onRoot(), "launch.png")
    }
}
