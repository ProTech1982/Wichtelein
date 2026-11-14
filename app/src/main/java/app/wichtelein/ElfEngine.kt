package app.wichtelein

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val PIf = Math.PI.toFloat()

enum class Prop { NONE, BROOM, COOKIE, GIFT }

/** Körperhaltung des Wichtels. Winkel in Grad; arm/leg positiv = nach außen. */
class Pose {
    var armL = 0f
    var armR = 0f
    var legL = 0f
    var legR = 0f
    var lean = 0f
    var spin = 0f
    var bob = 0f
    var squash = 0f
    var mouth = 0f
    var talk = 0f
    var eyesClosed = false
    /** kurzes Blinzeln (unabhängig von den Aktionen) */
    var blink = false
    /** Kopfnicken in Grad (beim Sprechen) */
    var nod = 0f
    var hatTilt = 0f
    var scale = 1f
    var prop = Prop.NONE

    /** Alles zurück in die Ruhehaltung, außer Gegenstand und Größe. */
    fun relax() {
        armL = 0f; armR = 0f; legL = 0f; legR = 0f
        lean = 0f; spin = 0f; bob = 0f; squash = 0f
        mouth = 0f; eyesClosed = false; hatTilt = 0f
    }

    fun reset() {
        relax()
        scale = 1f
        prop = Prop.NONE
    }

    fun copy(): Pose = Pose().also {
        it.armL = armL; it.armR = armR; it.legL = legL; it.legR = legR
        it.lean = lean; it.spin = spin; it.bob = bob; it.squash = squash
        it.mouth = mouth; it.talk = talk; it.eyesClosed = eyesClosed; it.hatTilt = hatTilt
        it.blink = blink; it.nod = nod
        it.scale = scale; it.prop = prop
    }
}

/** Gegenstand auf dem Boden. kind 0..3 = Spielzeug/Chaos, [GIFT] = Geschenk (bleibt beim Aufräumen liegen). */
class MessItem(val kind: Int, var x: Float, var y: Float, var rot: Float) {
    fun copy() = MessItem(kind, x, y, rot)

    companion object {
        const val GIFT = 10
    }
}

object PK {
    const val POOF = 0
    const val DUST = 1
    const val CRUMB = 2
    const val SPARK = 3
    const val Z = 4
    const val CONFETTI = 5
}

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val kind: Int,
    val size: Float,
    val color: Int = 0,
) {
    fun copy() = Particle(x, y, vx, vy, life, maxLife, kind, size, color)
}

/**
 * Steuert den Wichtel: Position, Haltung, Sprechblase, Gegenstände und alle Aktionen. Läuft ohne Android-Bildschirm,
 * nur über einen [Ticker] und einen festen Zufallsgenerator, damit sich Aufführungen exakt aufnehmen lassen.
 * Alle Koordinaten sind relativ zum Video (0..1).
 */
class ElfEngine(
    private val settings: () -> Settings,
    private val speak: (String, Int?) -> Unit,
    private val rng: Random,
    private val ticker: Ticker,
) {
    var x = 0.5f
    var y = 0.8f
    var facing = 1
    var visible = false
    val pose = Pose()
    var bubble: String? = null
        private set
    val mess = mutableListOf<MessItem>()
    val particles = mutableListOf<Particle>()
    var flying: MessItem? = null

    private var bubbleUntil = 0f
    private var lastAction = -1
    private var lastNanos = 0L
    private var clock = 0f
    private var blinkIn = 1.8f
    private var blinkLeft = 0f

    /** Vergangene Zeit der Aufführung in Millisekunden. */
    val timeMs: Long get() = (clock * 1000f).toLong()

    /** Höhe des Wichtels in Videohöhen (für Partikel-Positionen). */
    private val hNorm: Float get() = settings().sizeScale * (0.55f + 0.6f * y)

    fun snapshot() = FrameState(
        x, y, facing, visible, pose.copy(), bubble,
        mess.map { it.copy() }, flying?.copy(), particles.map { it.copy() },
    )

    // ---------------------------------------------------------------- Takt

    private suspend fun step(): Float {
        val now = ticker.nextFrame()
        val dt = if (lastNanos == 0L) 0.033f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastNanos = now
        clock += dt
        if (bubble != null && clock > bubbleUntil) bubble = null
        pose.talk = if (bubble != null) 0.35f + 0.65f * abs(sin(clock * 14f)) else 0f
        pose.nod = if (bubble != null) 3f * sin(clock * 7f) else 0f
        blinkIn -= dt
        if (blinkIn <= 0f) {
            blinkIn = 2.2f + rng.nextFloat() * 3.5f
            blinkLeft = 0.13f
        }
        if (blinkLeft > 0f) blinkLeft -= dt
        pose.blink = blinkLeft > 0f
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0f) {
                iter.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.kind == PK.CRUMB) p.vy += 1.2f * dt
            if (p.kind == PK.CONFETTI) p.vy += 0.9f * dt
        }
        return dt
    }

    private suspend fun anim(ms: Long, block: (Float) -> Unit) {
        var t = 0f
        while (t < 1f) {
            val dt = step()
            t = min(1f, t + dt * 1000f / ms)
            block(t)
        }
    }

    suspend fun idle(ms: Long) = anim(ms) { pose.bob = 0.006f * sin(clock * 3f) }

    // ---------------------------------------------------------------- Sprechen

    /** Zeigt die Sprechblase, lässt den Satz sprechen und liefert die Anzeigedauer. */
    fun say(template: String, mood: Int? = null): Long {
        val s = settings()
        val text = fill(template, s.kidList().randomOrNull(rng) ?: "mein Freund", s.elfName)
        bubble = text
        val ms = bubbleDurationMs(text)
        bubbleUntil = clock + ms / 1000f
        speak(text, mood)
        return ms
    }

    /** Sagt einen zufälligen Satz aus [pool]. */
    fun sayFrom(pool: List<String>, mood: Int? = null): Long = say(pool.random(rng), mood)

    private suspend fun sayWait(template: String, mood: Int? = null) {
        val ms = say(template, mood)
        idle(ms)
    }

    /** Eigene Sätze (falls vorhanden) oder die allgemeinen Wichtelsprüche. */
    private fun chatLine(): String {
        val own = settings().customList()
        return if (own.isNotEmpty() && rng.nextFloat() < 0.6f) own.random(rng) else (Lines.general + Lines.chat).random(rng)
    }

    // ---------------------------------------------------------------- Effekte

    private fun rnd(a: Float, b: Float) = a + rng.nextFloat() * (b - a)

    private fun burst(px: Float, py: Float, n: Int, kind: Int, speed: Float = 0.15f, life: Float = 0.7f, size: Float = 1f) {
        repeat(n) {
            val a = rng.nextFloat() * 2f * PIf
            val sp = speed * (0.4f + rng.nextFloat() * 0.6f)
            particles.add(Particle(px, py, cos(a) * sp, sin(a) * sp * 0.7f, life, life, kind, size * (0.7f + rng.nextFloat() * 0.6f)))
        }
    }

    fun confetti(px: Float, py: Float, n: Int = 40) {
        repeat(n) {
            val a = rng.nextFloat() * 2f * PIf
            val sp = 0.25f * (0.3f + rng.nextFloat() * 0.7f)
            val life = 1.6f + rng.nextFloat() * 0.8f
            particles.add(Particle(px, py, cos(a) * sp, -abs(sin(a)) * sp * 1.4f, life, life, PK.CONFETTI, 0.8f + rng.nextFloat() * 0.6f, rng.nextInt(6)))
        }
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    fun randomPoint() = Pt(rnd(0.15f, 0.85f), rnd(0.62f, 0.9f))

    /** Legt Spielzeug in die Szene (taucht mit kleinen Wölkchen auf). */
    fun spawnMess(n: Int) {
        repeat(n) {
            val p = randomPoint()
            if (mess.size >= 14) mess.removeAt(0)
            mess.add(MessItem(rng.nextInt(4), p.x, p.y, rng.nextFloat() * 360f))
            burst(p.x, p.y - 0.02f, 4, PK.POOF, 0.08f, 0.5f, 0.7f)
        }
    }

    // ---------------------------------------------------------------- Grundbewegungen

    suspend fun appear(p: Pt) {
        x = p.x
        y = p.y
        facing = 1
        pose.reset()
        pose.scale = 0f
        visible = true
        burst(x, y - hNorm * 0.4f, 12, PK.POOF, 0.18f, 0.8f, 1.3f)
        anim(500) { t -> pose.scale = easeOutBack(t).coerceAtLeast(0f) }
        pose.scale = 1f
    }

    suspend fun walkTo(p: Pt, speed: Float = 0.30f) {
        val tx = p.x
        val ty = p.y
        val sx = x
        val sy = y
        val dist = hypot(tx - sx, ty - sy)
        if (dist < 0.01f) return
        if (abs(tx - sx) > 0.01f) facing = if (tx > sx) 1 else -1
        val dur = dist / speed
        var t = 0f
        var phase = 0f
        while (t < 1f) {
            val dt = step()
            t = min(1f, t + dt / dur)
            x = sx + (tx - sx) * t
            y = sy + (ty - sy) * t
            phase += dt * 11f
            val s = sin(phase)
            pose.legL = 30f * s
            pose.legR = -30f * s
            pose.armL = 10f - 22f * s
            pose.armR = if (pose.prop == Prop.NONE) 10f + 22f * s else 12f
            pose.bob = abs(s) * 0.02f
            pose.lean = 3f * s
        }
        pose.relax()
    }

    // ---------------------------------------------------------------- Aktionen

    suspend fun greet() {
        val ms = say(Lines.greet.random(rng))
        anim(ms) { pose.armR = 150f + 25f * sin(clock * 10f) }
        pose.relax()
    }

    suspend fun doWave() {
        val ms = say(Lines.wave.random(rng))
        anim(ms) { pose.armR = 150f + 25f * sin(clock * 10f) }
        pose.relax()
    }

    /** Sammelt herumliegendes Spielzeug ein (Geschenke bleiben liegen). */
    suspend fun doCollect() {
        for (item in mess.filter { it.kind != MessItem.GIFT }) {
            walkTo(Pt(item.x, item.y))
            anim(380) { t ->
                val s = sin(t * PIf)
                pose.squash = 0.12f * s
                pose.armL = 70f * s
                pose.lean = 12f * s
            }
            mess.remove(item)
            burst(item.x, item.y - 0.02f, 5, PK.SPARK, 0.12f, 0.6f)
            pose.relax()
        }
    }

    /** Fegt etwa drei Sekunden mit dem Besen. */
    suspend fun doSweep(ms: Long = 2800) {
        pose.prop = Prop.BROOM
        var dustTimer = 0f
        anim(ms) {
            val s = sin(clock * 7f)
            pose.armR = 28f + 18f * s
            pose.armL = 12f
            pose.lean = 6f * s
            dustTimer += 0.033f
            if (dustTimer > 0.12f) {
                dustTimer = 0f
                burst(x + 0.04f * facing, y, 2, PK.DUST, 0.06f, 0.6f)
            }
        }
        pose.prop = Prop.NONE
        pose.relax()
    }

    suspend fun doTidy() {
        say(Lines.tidy.random(rng))
        doCollect()
        walkTo(randomPoint())
        doSweep()
        sayWait(Lines.tidyDone.random(rng))
    }

    suspend fun doChaos() {
        walkTo(randomPoint())
        say(Lines.chaos.random(rng))
        repeat(rng.nextInt(3, 6)) {
            val kind = rng.nextInt(4)
            val tx = rnd(0.1f, 0.9f)
            val ty = rnd(0.58f, 0.92f)
            val sx = x
            val sy = y - hNorm * 0.6f
            anim(260) { t ->
                pose.armL = 150f * t
                pose.armR = 150f * t
                pose.lean = -6f * t
            }
            val item = MessItem(kind, sx, sy, rng.nextFloat() * 360f)
            flying = item
            val rot0 = item.rot
            anim(600) { t ->
                item.x = sx + (tx - sx) * t
                item.y = sy + (ty - sy) * t - 0.18f * sin(t * PIf)
                item.rot = rot0 + 720f * t
                pose.armL = 150f * (1f - t)
                pose.armR = 150f * (1f - t)
                pose.lean = -6f * (1f - t)
            }
            flying = null
            if (mess.size >= 14) mess.removeAt(0)
            mess.add(MessItem(kind, tx, ty, item.rot))
            burst(tx, ty, 3, PK.DUST, 0.05f, 0.4f)
            pose.relax()
        }
    }

    suspend fun doDance(withConfetti: Boolean = false) {
        val ms = say(Lines.dance.random(rng))
        if (withConfetti) confetti(x, y - hNorm * 0.8f)
        var confettiTimer = 0f
        anim(maxOf(ms, 3200L)) {
            val s = sin(clock * 10f)
            pose.armL = 100f + 60f * s
            pose.armR = 100f - 60f * s
            pose.legL = 20f * s
            pose.legR = -20f * s
            pose.bob = abs(s) * 0.05f
            pose.lean = 8f * s
            pose.hatTilt = 0.05f * s
            confettiTimer += 0.033f
            if (withConfetti && confettiTimer > 1.0f) {
                confettiTimer = 0f
                confetti(x, y - hNorm * 0.8f, 25)
            }
        }
        pose.relax()
        anim(700) { t ->
            pose.spin = 360f * t
            pose.bob = 0.1f * sin(t * PIf)
            pose.armL = 90f
            pose.armR = 90f
        }
        pose.relax()
    }

    suspend fun doTumble() {
        say(Lines.tumble.random(rng), Mood.SURPRISED)
        anim(1000) { t ->
            pose.spin = 360f * t
            pose.bob = 0.28f * sin(t * PIf)
            pose.armL = 120f
            pose.armR = 120f
        }
        pose.relax()
        say(Lines.dizzy.random(rng), Mood.SLEEPY)
        anim(1800) {
            pose.lean = 9f * sin(clock * 12f)
            pose.hatTilt = 0.06f * sin(clock * 9f)
        }
        pose.relax()
    }

    /** Stolpert über etwas, fällt hin und steht verlegen wieder auf. */
    suspend fun doTrip() {
        anim(300) { t ->
            pose.lean = 22f * t
            pose.armL = 110f * t
            pose.armR = 110f * t
        }
        say(Lines.trip.random(rng), Mood.SURPRISED)
        anim(420) { t ->
            pose.lean = 22f + 0f * t
            pose.spin = 90f * t
            pose.bob = -0.32f * t
            pose.armL = 110f * (1f - t)
            pose.armR = 110f * (1f - t)
        }
        burst(x, y - 0.02f, 6, PK.SPARK, 0.1f, 0.6f)
        idle(900)
        anim(500) { t ->
            pose.spin = 90f * (1f - t)
            pose.bob = -0.32f * (1f - t)
            pose.lean = 22f * (1f - t)
        }
        pose.relax()
        sayWait(Lines.tripEmbarrassed.random(rng))
    }

    /** Hinsetzen (Hocke), [holdMs] lang, dann wieder aufstehen. */
    suspend fun doSit(holdMs: Long = 2500) {
        anim(450) { t ->
            pose.squash = 0.22f * t
            pose.legL = 70f * t
            pose.legR = 70f * t
            pose.armL = 15f * t
            pose.armR = 15f * t
        }
        say(Lines.sit.random(rng))
        idle(holdMs)
        anim(350) { t ->
            pose.squash = 0.22f * (1f - t)
            pose.legL = 70f * (1f - t)
            pose.legR = 70f * (1f - t)
            pose.armL = 15f * (1f - t)
            pose.armR = 15f * (1f - t)
        }
        pose.relax()
    }

    suspend fun doSleep() {
        say(Lines.sleep.random(rng), Mood.SLEEPY)
        anim(700) { t ->
            pose.spin = 90f * t
            pose.bob = -0.32f * t
            pose.eyesClosed = t > 0.6f
        }
        var zTimer = 0f
        anim(3200) {
            pose.bob = -0.32f + 0.008f * sin(clock * 2.5f)
            zTimer += 0.033f
            if (zTimer > 0.55f) {
                zTimer = 0f
                particles.add(Particle(x + 0.05f * facing, y - hNorm * 0.45f, 0.02f * facing, -0.07f, 1.6f, 1.6f, PK.Z, 1f))
            }
        }
        anim(400) { t ->
            pose.spin = 90f * (1f - t)
            pose.bob = -0.32f * (1f - t)
        }
        pose.relax()
        sayWait(Lines.wake.random(rng), Mood.SURPRISED)
    }

    suspend fun doSnack(sneaky: Boolean = false) {
        say(if (sneaky) Lines.sneaky.random(rng) else Lines.snack.random(rng), if (sneaky) Mood.WHISPER else null)
        pose.prop = Prop.COOKIE
        var crumbTimer = 0f
        anim(3600) {
            val s = sin(clock * 9f)
            pose.armR = 195f + 8f * s
            pose.mouth = if (s > 0f) 0.5f else 0f
            crumbTimer += 0.033f
            if (crumbTimer > 0.25f && s > 0.8f) {
                crumbTimer = 0f
                burst(x, y - hNorm * 0.5f, 2, PK.CRUMB, 0.05f, 0.7f)
            }
        }
        pose.prop = Prop.NONE
        pose.relax()
    }

    suspend fun doHide() {
        say(Lines.hide.random(rng), Mood.WHISPER)
        anim(300) { t -> pose.scale = 1f - t }
        burst(x, y - hNorm * 0.4f, 10, PK.POOF, 0.16f, 0.7f, 1.2f)
        idle(1800)
        x = rnd(0.15f, 0.85f)
        y = rnd(0.6f, 0.9f)
        burst(x, y - hNorm * 0.4f, 10, PK.POOF, 0.16f, 0.7f, 1.2f)
        anim(450) { t -> pose.scale = easeOutBack(t).coerceAtLeast(0f) }
        pose.scale = 1f
        sayWait(Lines.hideFound.random(rng), Mood.SURPRISED)
    }

    suspend fun doChat() {
        val ms = say(chatLine())
        anim(ms) {
            pose.armL = 25f + 18f * sin(clock * 5f)
            pose.armR = 25f - 18f * sin(clock * 5f)
        }
        pose.relax()
    }

    /** Verbeugung nach einer Vorführung. */
    suspend fun doBow() {
        say(Lines.bow.random(rng), Mood.AMUSED)
        anim(500) { t ->
            pose.lean = 25f * t
            pose.squash = 0.06f * t
        }
        idle(600)
        anim(400) { t ->
            pose.lean = 25f * (1f - t)
            pose.squash = 0.06f * (1f - t)
        }
        pose.relax()
    }

    /** Trägt ein Geschenk zu [target] und stellt es dort ab. */
    suspend fun doGiftCarry(target: Pt) {
        pose.prop = Prop.GIFT
        say(Lines.gift.random(rng))
        walkTo(target)
        anim(450) { t ->
            val s = sin(t * PIf)
            pose.squash = 0.14f * s
            pose.lean = 14f * s
        }
        pose.prop = Prop.NONE
        if (mess.size >= 14) mess.removeAt(0)
        mess.add(MessItem(MessItem.GIFT, x + 0.05f * facing, y, 0f))
        burst(x + 0.05f * facing, y - 0.03f, 6, PK.SPARK, 0.1f, 0.7f)
        pose.relax()
        sayWait(Lines.giftPlaced.random(rng))
    }

    /** Ein Geschenk erscheint per Zauber neben dem Wichtel. */
    suspend fun doGiftPop() {
        val gx = (x + 0.12f * facing).coerceIn(0.1f, 0.9f)
        anim(300) { t -> pose.armL = 140f * t; pose.armR = 140f * t }
        if (mess.size >= 14) mess.removeAt(0)
        mess.add(MessItem(MessItem.GIFT, gx, y, 0f))
        burst(gx, y - 0.04f, 14, PK.POOF, 0.15f, 0.8f, 1.2f)
        confetti(gx, y - 0.05f, 50)
        say(Lines.giftPop.random(rng), Mood.SURPRISED)
        idle(1800)
        pose.relax()
    }

    /** Zauberspruch: Funken fliegen, Spielzeug erscheint aus dem Nichts. */
    suspend fun doMagic() {
        say(Lines.magic.random(rng), Mood.AMUSED)
        anim(1500) {
            pose.armR = 150f + 20f * sin(clock * 12f)
            pose.hatTilt = 0.04f * sin(clock * 6f)
            if (rng.nextFloat() < 0.5f) burst(x + 0.05f * facing, y - hNorm * 0.95f, 2, PK.SPARK, 0.12f, 0.7f)
        }
        pose.relax()
        spawnMess(3)
        confetti(x, y - hNorm * 0.8f, 25)
        sayWait(Lines.surprised.random(rng), Mood.SURPRISED)
    }

    // ---------------------------------------------------------------- Ablauf

    /**
     * Die gesamte Aufführung: erscheint am Startpunkt, begrüßt, spielt dann nacheinander Szenen (zuerst die
     * gewählte, danach zufällige aus der Kategorie) und folgt dabei dem Weg. Endet erst, wenn die Aufnahme abbricht.
     */
    suspend fun perform(req: PerfRequest, played: MutableList<String>) {
        appear(req.start)
        greet()
        val stops = Stops(req.path, this)
        var last: SceneDef? = null
        var first = req.sceneId?.let { Scenes.find(it) }
        while (true) {
            val scene = first ?: Scenes.pick(req.category, req.enabledScenes, last, rng)
            first = null
            last = scene
            played.add(scene.name)
            scene.run(this, stops)
            if (rng.nextFloat() < 0.6f) doChat()
            idle(rng.nextLong(900, 1800))
        }
    }
}

/** Die vom Nutzer gesetzten Wegpunkte; sind sie aufgebraucht, nimmt die Szene zufällige Stellen. */
class Stops(private val points: List<Pt>, private val engine: ElfEngine) {
    private var i = 0

    fun next(): Pt = points.getOrNull(i++) ?: engine.randomPoint()
}
