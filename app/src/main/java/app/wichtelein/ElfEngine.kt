package app.wichtelein

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val PIf = Math.PI.toFloat()

enum class Prop { NONE, BROOM, COOKIE }

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
}

class MessItem(val kind: Int, var x: Float, var y: Float, var rot: Float)

object PK {
    const val POOF = 0
    const val DUST = 1
    const val CRUMB = 2
    const val SPARK = 3
    const val Z = 4
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
)

/**
 * Steuert den Wichtel: Position, Haltung, Sprechblase, Chaos-Gegenstände und die zufälligen Aktionen.
 * Alle Koordinaten sind relativ zum Video (0..1). Alles läuft auf dem UI-Thread innerhalb von Coroutinen,
 * die über den Compose-Frame-Takt getaktet werden.
 */
class ElfEngine(
    private val settings: () -> Settings,
    private val speak: (String, Int?) -> Unit,
) {
    var x = 0.5f
    var y = 0.8f
    var facing = 1
    var visible by mutableStateOf(false)
    val pose = Pose()
    var frame by mutableLongStateOf(0L)
        private set
    var bubble by mutableStateOf<String?>(null)
        private set
    val mess = mutableListOf<MessItem>()
    val particles = mutableListOf<Particle>()
    var flying: MessItem? = null

    private var bubbleUntil = 0L
    private var lastAction = -1
    private var lastNanos = 0L
    private var clock = 0f

    /** Höhe des Wichtels in Videohöhen (für Partikel-Positionen). */
    private val hNorm: Float get() = settings().sizeScale * (0.55f + 0.6f * y)

    // ---------------------------------------------------------------- Takt

    private suspend fun step(): Float {
        val now = withFrameNanos { it }
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastNanos = now
        clock += dt
        if (bubble != null && System.currentTimeMillis() > bubbleUntil) bubble = null
        pose.talk = if (bubble != null) 0.35f + 0.65f * abs(sin(clock * 14f)) else 0f
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
        }
        frame++
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

    private suspend fun idle(ms: Long) = anim(ms) { pose.bob = 0.006f * sin(clock * 3f) }

    // ---------------------------------------------------------------- Sprechen

    /** Zeigt die Sprechblase, spricht den Satz und liefert die Anzeigedauer. */
    fun say(template: String, mood: Int? = null): Long {
        val s = settings()
        val text = fill(template, s.kidList().randomOrNull() ?: "mein Freund", s.elfName)
        bubble = text
        val ms = bubbleDurationMs(text)
        bubbleUntil = System.currentTimeMillis() + ms
        speak(text, mood)
        return ms
    }

    private suspend fun sayWait(template: String, mood: Int? = null) {
        val ms = say(template, mood)
        idle(ms)
    }

    // ---------------------------------------------------------------- Effekte

    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    private fun burst(px: Float, py: Float, n: Int, kind: Int, speed: Float = 0.15f, life: Float = 0.7f, size: Float = 1f) {
        repeat(n) {
            val a = Random.nextFloat() * 2f * PIf
            val sp = speed * (0.4f + Random.nextFloat() * 0.6f)
            particles.add(Particle(px, py, cos(a) * sp, sin(a) * sp * 0.7f, life, life, kind, size * (0.7f + Random.nextFloat() * 0.6f)))
        }
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    // ---------------------------------------------------------------- Grundbewegungen

    suspend fun appear(px: Float, py: Float) {
        x = px
        y = py
        facing = 1
        pose.reset()
        pose.scale = 0f
        visible = true
        burst(x, y - hNorm * 0.4f, 12, PK.POOF, 0.18f, 0.8f, 1.3f)
        anim(500) { t -> pose.scale = easeOutBack(t).coerceAtLeast(0f) }
        pose.scale = 1f
    }

    suspend fun walkTo(tx: Float, ty: Float, speed: Float = 0.30f) {
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
            pose.armR = 10f + 22f * s
            pose.bob = abs(s) * 0.02f
            pose.lean = 3f * s
        }
        pose.relax()
    }

    // ---------------------------------------------------------------- Aktionen

    suspend fun greet() {
        val ms = say(Lines.greet.random())
        anim(ms) { pose.armR = 150f + 25f * sin(clock * 10f) }
        pose.relax()
    }

    private suspend fun doWave() {
        val ms = say(Lines.wave.random())
        anim(ms) { pose.armR = 150f + 25f * sin(clock * 10f) }
        pose.relax()
    }

    suspend fun doTidy() {
        say(Lines.tidy.random())
        for (item in mess.toList()) {
            walkTo(item.x, item.y)
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
        walkTo(rnd(0.2f, 0.8f), rnd(0.62f, 0.88f))
        pose.prop = Prop.BROOM
        var dustTimer = 0f
        anim(2800) {
            val s = sin(clock * 7f)
            pose.armR = 28f + 18f * s
            pose.armL = 12f
            pose.lean = 6f * s
            dustTimer += 0.016f
            if (dustTimer > 0.12f) {
                dustTimer = 0f
                burst(x + 0.04f * facing, y, 2, PK.DUST, 0.06f, 0.6f)
            }
        }
        pose.prop = Prop.NONE
        pose.relax()
        sayWait(Lines.tidyDone.random())
    }

    suspend fun doChaos() {
        walkTo(rnd(0.2f, 0.8f), rnd(0.62f, 0.88f))
        say(Lines.chaos.random())
        repeat(Random.nextInt(3, 6)) {
            val kind = Random.nextInt(4)
            val tx = rnd(0.1f, 0.9f)
            val ty = rnd(0.58f, 0.92f)
            val sx = x
            val sy = y - hNorm * 0.6f
            anim(260) { t ->
                pose.armL = 150f * t
                pose.armR = 150f * t
                pose.lean = -6f * t
            }
            val item = MessItem(kind, sx, sy, Random.nextFloat() * 360f)
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

    private suspend fun doDance() {
        val ms = say(Lines.dance.random())
        anim(maxOf(ms, 3200L)) {
            val s = sin(clock * 10f)
            pose.armL = 100f + 60f * s
            pose.armR = 100f - 60f * s
            pose.legL = 20f * s
            pose.legR = -20f * s
            pose.bob = abs(s) * 0.05f
            pose.lean = 8f * s
            pose.hatTilt = 0.05f * s
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

    private suspend fun doTumble() {
        say(Lines.tumble.random(), Mood.SURPRISED)
        anim(1000) { t ->
            pose.spin = 360f * t
            pose.bob = 0.28f * sin(t * PIf)
            pose.armL = 120f
            pose.armR = 120f
        }
        pose.relax()
        say(Lines.dizzy.random(), Mood.SLEEPY)
        anim(1800) {
            pose.lean = 9f * sin(clock * 12f)
            pose.hatTilt = 0.06f * sin(clock * 9f)
        }
        pose.relax()
    }

    private suspend fun doSleep() {
        say(Lines.sleep.random(), Mood.SLEEPY)
        anim(700) { t ->
            pose.spin = 90f * t
            pose.bob = -0.32f * t
            pose.eyesClosed = t > 0.6f
        }
        var zTimer = 0f
        anim(3200) {
            pose.bob = -0.32f + 0.008f * sin(clock * 2.5f)
            zTimer += 0.016f
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
        sayWait(Lines.wake.random(), Mood.SURPRISED)
    }

    private suspend fun doSnack() {
        say(Lines.snack.random())
        pose.prop = Prop.COOKIE
        var crumbTimer = 0f
        anim(3600) {
            val s = sin(clock * 9f)
            pose.armR = 195f + 8f * s
            pose.mouth = if (s > 0f) 0.5f else 0f
            crumbTimer += 0.016f
            if (crumbTimer > 0.25f && s > 0.8f) {
                crumbTimer = 0f
                burst(x, y - hNorm * 0.5f, 2, PK.CRUMB, 0.05f, 0.7f)
            }
        }
        pose.prop = Prop.NONE
        pose.relax()
    }

    private suspend fun doHide() {
        say(Lines.hide.random(), Mood.WHISPER)
        anim(300) { t -> pose.scale = 1f - t }
        burst(x, y - hNorm * 0.4f, 10, PK.POOF, 0.16f, 0.7f, 1.2f)
        idle(1800)
        x = rnd(0.15f, 0.85f)
        y = rnd(0.6f, 0.9f)
        burst(x, y - hNorm * 0.4f, 10, PK.POOF, 0.16f, 0.7f, 1.2f)
        anim(450) { t -> pose.scale = easeOutBack(t).coerceAtLeast(0f) }
        pose.scale = 1f
        sayWait(Lines.hideFound.random(), Mood.SURPRISED)
    }

    private suspend fun doChat() {
        val own = settings().customList()
        val pool = if (own.isNotEmpty() && Random.nextFloat() < 0.6f) own else Lines.chat
        val ms = say(pool.random())
        anim(ms) {
            pose.armL = 25f + 18f * sin(clock * 5f)
            pose.armR = 25f - 18f * sin(clock * 5f)
        }
        pose.relax()
    }

    // ---------------------------------------------------------------- Ablauf

    /** Zufällige Aktion, gewichtet. Sind viele Sachen verstreut, wird eher aufgeräumt. */
    suspend fun runRandom() {
        val weights = listOf(
            if (mess.size >= 3) 7 else 2, // 0 aufräumen
            if (mess.size >= 10) 0 else 4, // 1 Chaos
            2, // 2 tanzen
            2, // 3 Purzelbaum
            2, // 4 winken
            1, // 5 schlafen
            2, // 6 naschen
            2, // 7 verstecken
            3, // 8 reden
        )
        val pick = pickWeighted(weights, lastAction)
        lastAction = pick
        when (pick) {
            0 -> doTidy()
            1 -> doChaos()
            2 -> doDance()
            3 -> doTumble()
            4 -> doWave()
            5 -> doSleep()
            6 -> doSnack()
            7 -> doHide()
            else -> doChat()
        }
    }

    /** Endlosschleife: optional eine erste Aktion, danach immer wieder Pause und Zufallsaktion. */
    suspend fun loop(first: (suspend () -> Unit)?) {
        pose.reset()
        flying = null
        first?.invoke()
        while (true) {
            idle(Random.nextLong(1200, 3200))
            runRandom()
        }
    }
}
