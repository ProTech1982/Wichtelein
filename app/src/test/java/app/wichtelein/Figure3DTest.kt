package app.wichtelein

import app.wichtelein.anim.*
import app.wichtelein.settings.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Blickrichtung, Drehen, Laufwege (Wichtel bleibt bei den gesetzten Punkten), Staubwolken und die Bildauswahl des 3D-Wichtels. */
class Figure3DTest {
    private val s3d = Settings(elfName = "Pippo", figure3d = true).withProfile("Max")
    private val s2d = Settings(elfName = "Pippo").withProfile("Max")

    private fun req(
        script: List<ScriptStep> = emptyList(),
        path: List<Pt> = emptyList(),
        start: Pt = Pt(0.3f, 0.8f),
        door: Pt? = null,
        settings: Settings = s3d,
        ms: Long = 60_000L,
        stop: Boolean = true,
        seed: Long = 5L,
        only: String? = null,
    ) = PerfRequest(start, path, 0.3f, null, only, seed, ms, only?.let { setOf(it) }, settings, door = door, script = script, stopAfterScript = stop, aspect = 1f)

    private fun angleDiff(a: Float, b: Float) = abs((((a - b) % 360f) + 540f) % 360f - 180f)

    // ---------------------------------------------------------------- Blickrichtung

    @Test fun headingFollowsTheWalkingDirection() {
        val start = Pt(0.5f, 0.7f)
        val steps = listOf(
            ScriptStep(ElfAction.WALK, Pt(0.8f, 0.7f)), // nach rechts
            ScriptStep(ElfAction.WALK, Pt(0.8f, 0.9f)), // auf den Betrachter zu
            ScriptStep(ElfAction.WALK, Pt(0.4f, 0.9f)), // nach links
            ScriptStep(ElfAction.WALK, Pt(0.4f, 0.6f)), // vom Betrachter weg
        )
        val p = Recorder.record(req(steps, start = start))
        fun walkingHeadings(dx: Int, dy: Int): List<Float> {
            // Bilder, in denen sich der Wichtel in die Richtung bewegt
            val out = ArrayList<Float>()
            for (i in 1 until p.frames.size) {
                val a = p.frames[i - 1]
                val b = p.frames[i]
                if (abs(b.x - a.x) + abs(b.y - a.y) < 0.0005f || !b.visible) continue
                val mx = b.x - a.x
                val my = b.y - a.y
                val match = (dx > 0 && mx > 0 && abs(my) < abs(mx)) || (dx < 0 && mx < 0 && abs(my) < abs(mx)) ||
                    (dy > 0 && my > 0 && abs(mx) < abs(my)) || (dy < 0 && my < 0 && abs(mx) < abs(my))
                if (match) out.add(b.pose.heading)
            }
            return out
        }
        assertTrue("rechts: 90°", walkingHeadings(1, 0).let { it.isNotEmpty() && it.all { h -> angleDiff(h, 90f) < 8f } })
        assertTrue("zum Betrachter: 0°", walkingHeadings(0, 1).let { it.isNotEmpty() && it.all { h -> angleDiff(h, 0f) < 8f } })
        assertTrue("links: 270°", walkingHeadings(-1, 0).let { it.isNotEmpty() && it.all { h -> angleDiff(h, 270f) < 8f } })
        assertTrue("weg vom Betrachter: 180°", walkingHeadings(0, -1).let { it.isNotEmpty() && it.all { h -> angleDiff(h, 180f) < 8f } })
        // Schrittphase läuft beim Gehen
        assertTrue(p.frames.map { it.pose.stepPhase }.distinct().size > 30)
    }

    @Test fun turningIsSmoothThroughAllIntermediateDirections() {
        // erst nach rechts (90°), dann vom Betrachter weg (180°): dazwischen liegen Zwischenrichtungen
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.WALK, Pt(0.7f, 0.8f)), ScriptStep(ElfAction.WALK, Pt(0.7f, 0.6f)))))
        val hs = p.frames.map { it.pose.heading }
        assertTrue("Zwischenrichtungen beim Drehen: ${hs.distinct().size}", hs.any { it in 110f..170f })
        // keine Sprünge größer als 25° pro Bild
        for (i in 1 until hs.size) assertTrue("Sprung ${hs[i - 1]} -> ${hs[i]}", angleDiff(hs[i], hs[i - 1]) < 25f)
    }

    @Test fun twoDimensionalFigureTurnsInstantlyWithoutExtraTime() {
        val steps = listOf(ScriptStep(ElfAction.WALK, Pt(0.7f, 0.8f)), ScriptStep(ElfAction.WALK, Pt(0.7f, 0.6f)))
        val a = Recorder.record(req(steps, settings = s2d))
        val hs = a.frames.map { it.pose.heading }.filter { it > 1f && it < 359f }.distinct()
        assertTrue("2D dreht sofort: $hs", hs.all { angleDiff(it, 90f) < 1f || angleDiff(it, 180f) < 1f })
    }

    @Test fun enteringTheHouseTurnsToTheDoorBackToTheViewer() {
        val door = Pt(0.6f, 0.62f)
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.ENTER)), door = door, start = Pt(0.2f, 0.85f)))
        // beim Hineingehen (Wichtel schrumpft) blickt er vom Betrachter weg
        val entering = p.frames.filter { it.visible && it.pose.scale in 0.1f..0.9f && abs(it.x - door.x) < 0.1f }
        assertTrue("Hineingehen sichtbar", entering.isNotEmpty())
        assertTrue("Rücken zum Betrachter: ${entering.map { it.pose.heading }.distinct()}", entering.all { angleDiff(it.pose.heading, 180f) < 10f })
        // vorher hat er sich dorthin gedreht (kommt von links: erst 90°, dann 180°)
        val turnFrames = p.frames.filter { it.pose.heading in 100f..170f }
        assertTrue("Drehung zur Tür", turnFrames.isNotEmpty())
    }

    @Test fun exitingTheHouseFacesTheViewer() {
        val door = Pt(0.5f, 0.62f)
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.ENTER), ScriptStep(ElfAction.EXIT)), door = door))
        val leaving = p.frames.filter { it.visible && it.pose.scale in 0.2f..0.9f && it.y > door.y && angleDiff(it.pose.heading, 0f) < 10f }
        assertTrue("kommt mit dem Gesicht zum Betrachter heraus", leaving.isNotEmpty())
    }

    // ---------------------------------------------------------------- Laufweg

    @Test fun elfStaysNearTheStartPointWhenNothingElseIsSet() {
        val start = Pt(0.3f, 0.78f)
        for (seed in 1L..6L) {
            val p = Recorder.record(req(start = start, seed = seed, stop = false, ms = 40_000L, settings = s2d))
            for (f in p.frames) {
                if (!f.visible) continue
                assertTrue("seed $seed: x=${f.x}", abs(f.x - start.x) <= 0.13f)
                assertTrue("seed $seed: y=${f.y}", abs(f.y - start.y) <= 0.07f)
            }
        }
    }

    @Test fun elfFollowsThePathAndStaysAroundIt() {
        val start = Pt(0.15f, 0.8f)
        val path = listOf(Pt(0.4f, 0.75f), Pt(0.75f, 0.85f))
        val p = Recorder.record(req(path = path, start = start, stop = false, ms = 60_000L, settings = s2d, only = "brav_fegen"))
        assertTrue("erreicht den ersten Punkt", p.frames.any { abs(it.x - path[0].x) < 0.02f && abs(it.y - path[0].y) < 0.02f })
        assertTrue("erreicht den zweiten Punkt", p.frames.any { abs(it.x - path[1].x) < 0.02f && abs(it.y - path[1].y) < 0.02f })
        for (f in p.frames) {
            if (!f.visible) continue
            assertTrue("x=${f.x}", f.x in (0.15f - 0.13f)..(0.75f + 0.13f))
            assertTrue("y=${f.y}", f.y in (0.75f - 0.07f)..(0.85f + 0.07f))
        }
    }

    @Test fun pathIsWalkedAsARoundTrip() {
        val path = listOf(Pt(0.3f, 0.8f), Pt(0.7f, 0.8f))
        val p = Recorder.record(req(path = path, start = Pt(0.5f, 0.8f), stop = false, ms = 60_000L, settings = s2d, seed = 9L, only = "brav_fegen"))
        // Punkt 1 wird mehrfach besucht (Rundgang), nicht nur einmal
        var visits = 0
        var near = false
        for (f in p.frames) {
            val n = abs(f.x - path[0].x) < 0.02f && abs(f.y - path[0].y) < 0.02f
            if (n && !near) visits++
            near = n
        }
        assertTrue("Rundgang: erster Punkt $visits-mal erreicht", visits >= 2)
    }

    // ---------------------------------------------------------------- Staub

    @Test fun sweepingStirsUpDustClouds() {
        val p = Recorder.record(req(listOf(ScriptStep(ElfAction.SWEEP, Pt(0.5f, 0.8f)))))
        val clouds = p.frames.flatMap { it.particles }.filter { it.kind == PK.DUST && it.size >= 2.0f }
        assertTrue("Staubwolken: ${clouds.size}", clouds.size >= 6)
        // sie steigen auf und lösen sich auf
        val one = clouds.first()
        assertTrue(one.maxLife > 0.8f)
        // beim Fegen steht er seitlich
        val sweeping = p.frames.filter { it.pose.prop == Prop.BROOM }
        assertTrue(sweeping.isNotEmpty() && sweeping.all { angleDiff(it.pose.heading, 90f) < 5f || angleDiff(it.pose.heading, 270f) < 5f })
    }

    // ---------------------------------------------------------------- Bildauswahl des 3D-Wichtels

    private val layout = Elf3DLayout.fromJson(
        """{"cell":[360,480],"feet":[180,430],"height":380,
            "clips":{"idle":{"headings":16,"frames":1},"walk":{"headings":16,"frames":8},
                     "sweep":{"headings":8,"frames":9},"wave":{"headings":8,"frames":6}}}""",
    )

    @Test fun frameChoiceFollowsPose() {
        val idle = Pose()
        assertEquals("idle", layout.choose(idle).clip)

        val side = Pose().apply { heading = 90f }
        assertEquals(4, layout.choose(side).heading) // 16 Richtungen: 90° = Nr. 4
        assertEquals(0, layout.choose(Pose().apply { heading = 355f }).heading)
        assertEquals(8, layout.choose(Pose().apply { heading = 180f }).heading)
        assertEquals(12, layout.choose(Pose().apply { heading = 270f }).heading)
        assertEquals(15, layout.choose(Pose().apply { heading = -22.5f }).heading)

        val walk = Pose().apply { legL = 25f; legR = -25f; stepPhase = 0f }
        assertEquals("walk", layout.choose(walk).clip)
        val seen = (0 until 64).map { layout.choose(Pose().apply { legL = 25f; stepPhase = it * 0.1f }).frame }.toSet()
        assertEquals((0 until 8).toSet(), seen)

        val sweepA = Pose().apply { prop = Prop.BROOM; armR = 10f; heading = 90f }
        val sweepB = Pose().apply { prop = Prop.BROOM; armR = 46f; heading = 90f }
        assertEquals("sweep", layout.choose(sweepA).clip)
        assertEquals(0, layout.choose(sweepA).frame)
        assertEquals(8, layout.choose(sweepB).frame)
        assertEquals(2, layout.choose(sweepB).heading) // 8 Richtungen: 90° = Nr. 2

        val wave = Pose().apply { armR = 150f }
        assertEquals("wave", layout.choose(wave).clip)
        assertEquals("Winken nur ohne Laufen", "walk", layout.choose(Pose().apply { armR = 150f; legL = 20f }).clip)
        assertEquals("Keks (Arm ganz oben) bleibt Stehbild", "idle", layout.choose(Pose().apply { armR = 195f }).clip)
    }

    @Test fun fileNamesMatchWhatTheRendererWrites() {
        assertEquals("elf3d/walk_03_05.png", Elf3DLayout.fileName(FrameChoice("walk", 3, 5)))
        assertEquals("elf3d/idle_15_00.png", Elf3DLayout.fileName(FrameChoice("idle", 15, 0)))
    }
}
