package app.wichtelein.anim

import app.wichtelein.camera.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import app.wichtelein.sound.Fx

private const val PIf = Math.PI.toFloat()

enum class Prop { NONE, BROOM, COOKIE, GIFT }

/** Körperhaltung des Wichtels. Winkel in Grad; arm/leg positiv = nach außen. */
/** „nie“ für Zeitpunkte der Engine-Uhr */
private const val NEVER = -1000f

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

    /** Blickrichtung in Grad für die 3D-Figur: 0 = zum Betrachter, 90 = nach rechts, 180 = vom Betrachter weg, 270 = nach links */
    var heading = 0f

    /** Schrittphase im Bogenmaß (3D-Figur: welches Laufbild) */
    var stepPhase = 0f

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
        heading = 0f
    }

    fun copy(): Pose = Pose().also {
        it.armL = armL; it.armR = armR; it.legL = legL; it.legR = legR
        it.lean = lean; it.spin = spin; it.bob = bob; it.squash = squash
        it.mouth = mouth; it.talk = talk; it.eyesClosed = eyesClosed; it.hatTilt = hatTilt
        it.blink = blink; it.nod = nod
        it.scale = scale; it.prop = prop
        it.heading = heading; it.stepPhase = stepPhase
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
    /** Geräusch abspielen bzw. aufzeichnen (Art, Lautstärke 0..1) */
    private val fx: (Fx, Float) -> Unit = { _, _ -> },
    /** Welche Figur: 0 = Wichtel, 1 = Elfin (Name und Stimme) */
    private val actor: Int = 0,
    /** Wetter, das beide Figuren teilen */
    private val weather: Weather = Weather(),
) {
    /** Die Sätze dieser Figur (Wichtel oder Elfin, jede mit eigenen Texten) */
    val lines: app.wichtelein.scene.LineBook get() = if (actor == 1) app.wichtelein.scene.ElfinLines else app.wichtelein.scene.Lines

    /** Abstimmung mit der anderen Figur bei gemeinsamen Aktionen (nur wenn beide auftreten) */
    var sync: StepSync? = null

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

    /** Tür des Wichtelhauses: Lage, Größe und Öffnungsgrad (0 = zu, 1 = offen). */
    var doorPos: Pt? = null
        private set
    var doorScale = 0.2f
        private set
    var doorOpen = 0f
        private set
    var doorHingeLeft = true
        private set
    var doorRound = 1f
        private set

    /** Wichtel ist im Haus (unsichtbar hinter der Tür) */
    var inHouse = false
        private set

    /** Zeitpunkt, zu dem das Drehbuch (Aktionspunkte) abgespielt war, sonst -1 */
    var scriptDoneMs = -1L
        private set

    /** Seitenverhältnis des Bildes (Breite / Höhe), damit Richtungen stimmen */
    var aspect = 1f
        private set

    /** Orte, um die sich der Wichtel bewegt (Start, Weg, Aktionspunkte); leer = ganzes Bild */
    private var roam: List<Pt> = emptyList()

    private var items: List<SceneryItem> = emptyList()

    /** Aufbaufortschritt der Gegenstände nach Nummer */
    val built = HashMap<Int, Float>()

    /** Gegenstand, auf dem der Wichtel liegt, sonst -1 */
    var onItem = -1
        private set

    /** Sprechblase der gerade laufenden Aktion (null = Standard aus den Einstellungen) */
    private var stepBubble: BubbleSpec? = null
    private val bubbleSpec: BubbleSpec get() = stepBubble ?: settings().bubble

    /** Geräusche, die zu einem späteren Zeitpunkt gespielt werden (Zeitpunkt der Engine-Uhr, Art, Lautstärke) */
    private val due = ArrayList<Triple<Float, Fx, Float>>()
    private var humUntil = 0f

    private fun scheduleFx(delaySec: Float, f: Fx, gain: Float) {
        due.add(Triple(clock + delaySec, f, gain))
    }

    private var bubbleUntil = 0f
    private var lastAction = -1
    private var lastNanos = 0L
    private var clock = 0f
    private var blinkIn = 1.8f
    private var blinkLeft = 0f

    /** Vergangene Zeit der Aufführung in Millisekunden. */
    val timeMs: Long get() = (clock * 1000f).toLong()

    private fun sfx(f: Fx, g: Float = 1f) = fx(f, g)

    /** Höhe des Wichtels in Videohöhen (für Partikel-Positionen). */
    private val hNorm: Float get() = settings().sizeScale * (0.55f + 0.6f * y)

    fun snapshot() = FrameState(
        x, y, facing, visible, pose.copy(), bubble,
        mess.map { it.copy() }, flying?.copy(), particles.map { it.copy() },
        doorPos, doorScale, doorOpen, doorHingeLeft, doorRound, clock,
        if (built.isEmpty()) emptyMap() else HashMap(built), bubbleSpec, onItem, snowLevel(), doorRot,
    )

    // ---------------------------------------------------------------- Schnee

    /** Zeitpunkt (Engine-Uhr), an dem es zu schneien beginnt bzw. aufhört; negativ = nie */
    private var snowOnAt: Float
        get() = weather.snowOnAt
        set(v) { weather.snowOnAt = v }
    private var snowOffAt: Float
        get() = weather.snowOffAt
        set(v) { weather.snowOffAt = v }

    private val isSnowing: Boolean get() = snowOnAt > NEVER && snowOffAt < snowOnAt

    /** Schnee von Anfang an (Schalter im Editor): fällt sofort in voller Stärke. */
    fun configureSnow(on: Boolean) {
        if (on) {
            snowOnAt = -3f
            snowOffAt = NEVER
        }
    }

    /** Schneestärke: der Schnee setzt langsam ein (3 s) und klingt nach dem Ende langsam ab (3 s). */
    private fun snowLevel(): Float {
        if (snowOnAt <= NEVER) return 0f
        val up = ((clock - snowOnAt) / 3f).coerceIn(0f, 1f)
        val down = if (snowOffAt >= snowOnAt) (1f - (clock - snowOffAt) / 3f).coerceIn(0f, 1f) else 1f
        return minOf(up, down)
    }

    /** Der Wichtel schaut nach oben, freut sich und es beginnt zu schneien (bleibt so, bis „Der Schnee hört auf“). */
    suspend fun doSnowOn() {
        if (isSnowing) return
        faceFront()
        say(lines.snowOn.random(rng), Mood.AMUSED)
        sfx(Fx.SPARKLE, 0.8f)
        snowOnAt = clock
        anim(2600) {
            pose.armR = 150f + 15f * sin(clock * 5f)
            pose.armL = 150f - 15f * sin(clock * 5f)
            pose.hatTilt = 0.05f * sin(clock * 3f)
        }
        pose.relax()
        sayWait(lines.snowLike.random(rng), Mood.AMUSED)
    }

    /** Der Schneefall hört auf. */
    suspend fun doSnowOff() {
        if (!isSnowing) return
        faceFront()
        snowOffAt = clock
        say(lines.snowOff.random(rng))
        anim(1800) { pose.armR = 120f + 15f * sin(clock * 6f) }
        pose.relax()
    }


    // ---------------------------------------------------------------- Takt

    private suspend fun step(): Float {
        val now = ticker.nextFrame()
        val dt = if (lastNanos == 0L) 0.033f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastNanos = now
        clock += dt
        if (due.isNotEmpty()) {
            val it = due.iterator()
            while (it.hasNext()) {
                val d = it.next()
                if (clock >= d.first) {
                    sfx(d.second, d.third)
                    it.remove()
                }
            }
        }
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

    suspend fun idle(ms: Long) {
        // Summen: in längeren Pausen summt der Wichtel eine kleine Weihnachtsmelodie vor sich hin
        if (settings().hum && ms >= 900L && clock >= humUntil && rng.nextFloat() < 0.45f) {
            val tune = listOf(Fx.HUM_TANNE, Fx.HUM_JINGLE, Fx.HUM_NACHT)[rng.nextInt(3)]
            scheduleFx(0.2f, tune, 0.5f)
            humUntil = clock + 7f
        }
        anim(ms) { pose.bob = 0.006f * sin(clock * 3f) }
    }

    // ---------------------------------------------------------------- Sprechen

    /** Zeigt die Sprechblase, lässt den Satz sprechen und liefert die Anzeigedauer. */
    fun say(template: String, mood: Int? = null): Long {
        val s = settings()
        val text = fill(template, s.kidList().randomOrNull(rng) ?: "mein Freund", s.nameFor(actor))
        bubble = text
        val ms = bubbleDurationMs(text)
        bubbleUntil = clock + ms / 1000f
        // Zischlaute („Psst“) nicht buchstabieren lassen, sondern als Geräusch abspielen
        val spoken = SpeechText.prepare(text)
        if (spoken.hiss) sfx(Fx.PSST, 0.35f)
        if (spoken.text.isNotBlank()) speak(spoken.text, mood)
        // Stimme aus, aber „Summen und Brummen“ an: statt der Sprache brummt der Wichtel eine Silbe pro Wort
        if (s.hum && !s.speak) {
            val words = text.split(' ').filter { it.isNotBlank() }.size.coerceIn(1, 14)
            for (i in 0 until words) {
                scheduleFx(ms / 1000f * 0.85f * i / words, listOf(Fx.MUMBLE1, Fx.MUMBLE2, Fx.MUMBLE3)[rng.nextInt(3)], 0.7f)
            }
        }
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
        val own = settings().customListFor(actor)
        return if (own.isNotEmpty() && rng.nextFloat() < 0.6f) own.random(rng) else (lines.general + lines.chat).random(rng)
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

    fun configureScenery(list: List<SceneryItem>) {
        items = list
        built.clear()
    }

    /** Das erste Bett im Bild, falls es eins gibt. */
    fun findBed(): SceneryItem? = items.firstOrNull { it.type.isBed }

    private fun halfWidth(it: SceneryItem) = it.scale * it.type.ratio / aspect / 2f

    /** Stelle neben dem Gegenstand, auf der der Wichtel arbeitet (auf der Seite mit mehr Platz). */
    private fun standBeside(it: SceneryItem): Pt {
        val side = if (it.at.x > 0.5f) -1f else 1f
        return Pt((it.at.x + side * (halfWidth(it) * 0.9f + 0.05f)).coerceIn(0.04f, 0.96f), (it.at.y + 0.005f).coerceAtMost(0.97f))
    }

    /** Stellt einen Gegenstand auf: hingehen, hämmern und werkeln, der Gegenstand wächst von unten nach oben. */
    suspend fun doBuild(item: SceneryItem) {
        walkRoute(standBeside(item))
        val toRight = item.at.x > x
        facing = if (toRight) 1 else -1
        turnTo(if (toRight) 90f else 270f)
        say(lines.build.random(rng).replace("{item}", item.type.spoken))
        sfx(Fx.WHOOSH, 0.6f)
        var hit = -1
        val hw = halfWidth(item)
        anim(2600) { t ->
            built[item.id] = (t * t * (3f - 2f * t)).coerceIn(0f, 1f)
            val s = sin(clock * 10f)
            pose.armR = 70f + 45f * s
            pose.armL = 30f
            pose.lean = 4f * s
            val h = (clock * 10f / PIf).toInt()
            if (h != hit) {
                hit = h
                if (h % 2 == 0) sfx(Fx.KNOCK, 0.8f)
                burst(item.at.x, item.at.y - 0.02f, 2, PK.DUST, 0.06f, 0.6f, 1.2f)
            }
            if (rng.nextFloat() < 0.35f) burst(item.at.x + rnd(-hw, hw), item.at.y - rnd(0f, item.scale), 1, PK.SPARK, 0.08f, 0.5f, 0.8f)
        }
        built[item.id] = 1f
        pose.relax()
        sfx(Fx.SPARKLE)
        sfx(Fx.JINGLE, 0.7f)
        burst(item.at.x, item.at.y - item.scale * 0.5f, 10, PK.POOF, 0.15f, 0.8f, 1.2f)
        sayWait(lines.buildDone.random(rng))
    }

    /** Geht zum Bett, legt sich hinein, schnarcht und steht danach wieder auf. */
    suspend fun doBed(bed: SceneryItem?) {
        if (bed == null) {
            say(lines.noBed.random(rng))
            idle(1500)
            return
        }
        val stand = standBeside(bed)
        walkRoute(stand)
        val toRight = bed.at.x > x
        facing = if (toRight) 1 else -1
        turnTo(if (toRight) 90f else 270f)
        sayWait(lines.bedNight.random(rng))
        // Liegepunkt: Mitte der Matratze; der Körper dreht sich um seine Mitte
        val sign = if (bed.flip) 1f else -1f
        val lieSpin = sign * 90f + bed.rot
        val centerY = bed.at.y - bed.scale * 0.40f
        val lie = Pt(bed.at.x, (centerY + 0.4f * hNorm).coerceAtMost(0.98f))
        val sx = x
        val sy = y
        onItem = bed.id
        anim(1400) { t ->
            x = sx + (lie.x - sx) * t
            y = sy + (lie.y - sy) * t - 0.07f * sin(t * PIf)
            pose.spin = lieSpin * t
            pose.legL = 18f * sin(t * PIf)
            pose.legR = -18f * sin(t * PIf)
        }
        x = lie.x
        y = lie.y
        pose.spin = lieSpin
        pose.relax()
        pose.spin = lieSpin
        sfx(Fx.THUD, 0.5f)
        var zTimer = 0f
        var snoreAt = -1
        anim(5600) {
            pose.eyesClosed = true
            pose.spin = lieSpin
            pose.squash = 0.012f * sin(clock * 2.6f)
            val n = (clock / 1.7f).toInt()
            if (n != snoreAt) {
                snoreAt = n
                sfx(Fx.SNORE, 0.9f)
            }
            zTimer += 0.033f
            if (zTimer > 0.6f) {
                zTimer = 0f
                particles.add(Particle(x - sign * 0.04f, y - hNorm * 0.5f, -sign * 0.02f, -0.07f, 1.6f, 1.6f, PK.Z, 1f))
            }
        }
        pose.eyesClosed = false
        pose.squash = 0f
        sayWait(lines.bedMorning.random(rng))
        anim(1000) { t ->
            x = lie.x + (stand.x - lie.x) * t
            y = lie.y + (stand.y - lie.y) * t - 0.06f * sin(t * PIf)
            pose.spin = lieSpin * (1f - t)
        }
        x = stand.x
        y = stand.y
        pose.spin = 0f
        onItem = -1
        pose.relax()
    }

    /** Der vom Nutzer gezeichnete Weg (Start + Wegpunkte): Wege zu Zielen führen, wenn möglich, entlang dieses Weges (z. B. die Treppe hoch). */
    private var route: List<Pt> = emptyList()

    fun configureRoute(points: List<Pt>) {
        route = points
    }

    private fun dist(a: Pt, b: Pt): Float {
        val dx = (a.x - b.x) * aspect
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Geht zu [target]. Gibt es einen gezeichneten Weg, läuft der Wichtel zum nächsten Wegpunkt, folgt dem Weg bis zum Wegpunkt
     * nahe am Ziel und geht von dort weiter – statt in gerader Linie durch Wände oder über Treppen zu schweben.
     */
    suspend fun walkRoute(target: Pt, speed: Float = 0.30f) {
        if (route.size < 2) {
            walkTo(target, speed)
            return
        }
        val here = Pt(x, y)
        val a = route.indices.minByOrNull { dist(route[it], here) } ?: 0
        val b = route.indices.minByOrNull { dist(route[it], target) } ?: 0
        if (a == b) {
            walkTo(target, speed)
            return
        }
        if (dist(route[a], here) > 0.03f) walkTo(route[a], speed)
        val step = if (b > a) 1 else -1
        var i = a
        while (i != b) {
            i += step
            walkTo(route[i], speed)
        }
        walkTo(target, speed)
    }

    fun configureRoam(points: List<Pt>, aspect: Float) {
        roam = points
        this.aspect = aspect.coerceIn(0.3f, 3f)
    }

    /** Zufälliger Ort: bleibt in der Nähe der gesetzten Punkte, ohne Punkte im ganzen Bild. */
    fun randomPoint(): Pt {
        if (roam.isEmpty()) return Pt(rnd(0.15f, 0.85f), rnd(0.62f, 0.9f))
        val a = roam[rng.nextInt(roam.size)]
        return Pt((a.x + rnd(-0.1f, 0.1f)).coerceIn(0.05f, 0.95f), (a.y + rnd(-0.05f, 0.05f)).coerceIn(0.03f, 0.97f))
    }

    // ---------------------------------------------------------------- Drehen (3D-Figur)

    private val animateTurns: Boolean get() = settings().figure3d

    private fun normAngle(a: Float) = ((a % 360f) + 360f) % 360f

    /** Dreht den Wichtel auf der Stelle (bei der 2D-Figur sofort, bei der 3D-Figur mit Schrittchen) auf die Blickrichtung [target]. */
    suspend fun turnTo(target: Float) {
        val d = (((target - pose.heading) % 360f) + 540f) % 360f - 180f
        if (abs(d) < 1f || !animateTurns) {
            pose.heading = normAngle(target)
            return
        }
        val start = pose.heading
        anim((140f + abs(d) * 2.4f).toLong()) { t ->
            pose.heading = normAngle(start + d * t)
            val s = sin(clock * 14f)
            pose.stepPhase = clock * 14f
            pose.legL = 14f * s
            pose.legR = -14f * s
            pose.bob = abs(s) * 0.008f
        }
        pose.relax()
        pose.heading = normAngle(target)
    }

    suspend fun faceFront() = turnTo(0f)

    /** Blickrichtung in x (-1..1) auf dem Bild */
    private fun lookX(): Float = if (animateTurns) sin(pose.heading * PIf / 180f) else facing.toFloat()

    /** Legt Spielzeug in die Szene (taucht mit kleinen Wölkchen auf). */
    fun spawnMess(n: Int) {
        sfx(Fx.POOF, 0.6f)
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
        sfx(Fx.POOF)
        sfx(Fx.JINGLE, 0.7f)
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
        turnTo(normAngle(atan2((tx - sx) * aspect, ty - sy) * 180f / PIf))
        val dur = dist / speed
        var t = 0f
        var phase = 0f
        var lastStep = 0
        while (t < 1f) {
            val dt = step()
            t = min(1f, t + dt / dur)
            x = sx + (tx - sx) * t
            y = sy + (ty - sy) * t
            phase += dt * 11f
            pose.stepPhase = phase
            val stepNo = (phase / PIf).toInt()
            if (stepNo != lastStep) {
                lastStep = stepNo
                sfx(Fx.STEP, 0.3f)
                if (stepNo % 3 == 0) sfx(Fx.TINKLE, 0.5f)
            }
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
        faceFront()
        val ms = say(lines.greet.random(rng))
        sfx(Fx.JINGLE, 0.6f)
        anim(ms) { pose.armR = 150f + 25f * sin(clock * 10f) }
        pose.relax()
    }

    suspend fun doWave() {
        faceFront()
        val ms = say(lines.wave.random(rng))
        sfx(Fx.JINGLE, 0.5f)
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
            sfx(Fx.TINKLE, 0.8f)
            burst(item.x, item.y - 0.02f, 5, PK.SPARK, 0.12f, 0.6f)
            pose.relax()
        }
    }

    /** Fegt etwa drei Sekunden mit dem Besen. */
    suspend fun doSweep(ms: Long = 2800) {
        // beim Fegen steht der Wichtel seitlich, damit man den Besenstrich sieht
        turnTo(if (lookX() >= 0f) 90f else 270f)
        pose.prop = Prop.BROOM
        var dustTimer = 0f
        var stroke = -1
        var lastCloud = -1
        anim(ms) {
            val s = sin(clock * 7f)
            val strokeNo = (clock * 7f / PIf).toInt()
            if (strokeNo != stroke) {
                stroke = strokeNo
                sfx(Fx.SWEEP, 0.8f)
            }
            pose.armR = 28f + 18f * s
            pose.armL = 12f
            pose.lean = 6f * s
            dustTimer += 0.033f
            if (dustTimer > 0.12f) {
                dustTimer = 0f
                burst(x + 0.05f * lookX(), y, 2, PK.DUST, 0.06f, 0.6f)
            }
            // am Ende jedes Besenstrichs stäubt eine Wolke auf
            if (strokeNo != lastCloud) {
                lastCloud = strokeNo
                val cx = x + 0.075f * lookX()
                repeat(3) {
                    particles.add(Particle(cx + rnd(-0.015f, 0.015f), y - 0.005f, lookX() * rnd(0.01f, 0.05f), rnd(-0.07f, -0.02f), 1.1f, 1.1f, PK.DUST, 2.6f))
                }
            }
        }
        pose.prop = Prop.NONE
        pose.relax()
    }

    suspend fun doTidy() {
        say(lines.tidy.random(rng))
        doCollect()
        walkTo(randomPoint())
        doSweep()
        sayWait(lines.tidyDone.random(rng))
    }

    suspend fun doChaos(walk: Boolean = true) {
        if (walk) walkTo(randomPoint())
        say(lines.chaos.random(rng))
        repeat(rng.nextInt(3, 6)) {
            val kind = rng.nextInt(4)
            val tp = if (roam.isEmpty()) Pt(rnd(0.1f, 0.9f), rnd(0.58f, 0.92f)) else randomPoint()
            val tx = tp.x
            val ty = tp.y
            val sx = x
            val sy = y - hNorm * 0.6f
            anim(260) { t ->
                pose.armL = 150f * t
                pose.armR = 150f * t
                pose.lean = -6f * t
            }
            val item = MessItem(kind, sx, sy, rng.nextFloat() * 360f)
            sfx(Fx.WHOOSH, 0.7f)
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
            sfx(Fx.THUD, 0.5f)
            burst(tx, ty, 3, PK.DUST, 0.05f, 0.4f)
            pose.relax()
        }
    }

    suspend fun doDance(withConfetti: Boolean = false) {
        faceFront()
        val ms = say(lines.dance.random(rng))
        sfx(Fx.TUNE, 0.8f)
        if (withConfetti) {
            confetti(x, y - hNorm * 0.8f)
            sfx(Fx.JINGLE, 0.6f)
        }
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
            pose.stepPhase = clock * 10f
            confettiTimer += 0.033f
            if (withConfetti && confettiTimer > 1.0f) {
                confettiTimer = 0f
                confetti(x, y - hNorm * 0.8f, 25)
                sfx(Fx.JINGLE, 0.5f)
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
        faceFront()
        say(lines.tumble.random(rng), Mood.SURPRISED)
        sfx(Fx.WHOOSH)
        anim(1000) { t ->
            pose.spin = 360f * t
            pose.bob = 0.28f * sin(t * PIf)
            pose.armL = 120f
            pose.armR = 120f
        }
        pose.relax()
        sfx(Fx.THUD, 0.5f)
        say(lines.dizzy.random(rng), Mood.SLEEPY)
        anim(1800) {
            pose.lean = 9f * sin(clock * 12f)
            pose.hatTilt = 0.06f * sin(clock * 9f)
        }
        pose.relax()
    }

    /** Stolpert über etwas, fällt hin und steht verlegen wieder auf. */
    suspend fun doTrip() {
        faceFront()
        anim(300) { t ->
            pose.lean = 22f * t
            pose.armL = 110f * t
            pose.armR = 110f * t
        }
        say(lines.trip.random(rng), Mood.SURPRISED)
        sfx(Fx.THUD)
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
        sayWait(lines.tripEmbarrassed.random(rng))
    }

    /** Hinsetzen (Hocke), [holdMs] lang, dann wieder aufstehen. */
    suspend fun doSit(holdMs: Long = 2500) {
        faceFront()
        anim(450) { t ->
            pose.squash = 0.22f * t
            pose.legL = 70f * t
            pose.legR = 70f * t
            pose.armL = 15f * t
            pose.armR = 15f * t
        }
        sfx(Fx.THUD, 0.4f)
        say(lines.sit.random(rng))
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
        faceFront()
        say(lines.sleep.random(rng), Mood.SLEEPY)
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
        sayWait(lines.wake.random(rng), Mood.SURPRISED)
    }

    suspend fun doSnack(sneaky: Boolean = false) {
        faceFront()
        say(if (sneaky) lines.sneaky.random(rng) else lines.snack.random(rng), if (sneaky) Mood.WHISPER else null)
        pose.prop = Prop.COOKIE
        var crumbTimer = 0f
        anim(3600) {
            val s = sin(clock * 9f)
            pose.armR = 195f + 8f * s
            pose.mouth = if (s > 0f) 0.5f else 0f
            crumbTimer += 0.033f
            if (crumbTimer > 0.25f && s > 0.8f) {
                crumbTimer = 0f
                sfx(Fx.CRUNCH, 0.8f)
                burst(x, y - hNorm * 0.5f, 2, PK.CRUMB, 0.05f, 0.7f)
            }
        }
        pose.prop = Prop.NONE
        pose.relax()
    }

    suspend fun doHide(reappear: Pt? = null) {
        say(lines.hide.random(rng), Mood.WHISPER)
        anim(300) { t -> pose.scale = 1f - t }
        burst(x, y - hNorm * 0.4f, 10, PK.POOF, 0.16f, 0.7f, 1.2f)
        sfx(Fx.POOF)
        idle(1800)
        val back = reappear ?: if (roam.isEmpty()) Pt(rnd(0.15f, 0.85f), rnd(0.6f, 0.9f)) else randomPoint()
        x = back.x
        y = back.y
        burst(x, y - hNorm * 0.4f, 10, PK.POOF, 0.16f, 0.7f, 1.2f)
        sfx(Fx.POOF)
        sfx(Fx.TINKLE, 0.6f)
        anim(450) { t -> pose.scale = easeOutBack(t).coerceAtLeast(0f) }
        pose.scale = 1f
        sayWait(lines.hideFound.random(rng), Mood.SURPRISED)
    }

    suspend fun doChat() {
        faceFront()
        val ms = say(chatLine())
        anim(ms) {
            pose.armL = 25f + 18f * sin(clock * 5f)
            pose.armR = 25f - 18f * sin(clock * 5f)
        }
        pose.relax()
    }

    /** Verbeugung nach einer Vorführung. */
    suspend fun doBow() {
        faceFront()
        say(lines.bow.random(rng), Mood.AMUSED)
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
        say(lines.gift.random(rng))
        walkTo(target)
        anim(450) { t ->
            val s = sin(t * PIf)
            pose.squash = 0.14f * s
            pose.lean = 14f * s
        }
        pose.prop = Prop.NONE
        if (mess.size >= 14) mess.removeAt(0)
        mess.add(MessItem(MessItem.GIFT, x + 0.05f * facing, y, 0f))
        sfx(Fx.GIFT)
        burst(x + 0.05f * facing, y - 0.03f, 6, PK.SPARK, 0.1f, 0.7f)
        pose.relax()
        sayWait(lines.giftPlaced.random(rng))
    }

    /** Ein Geschenk erscheint per Zauber neben dem Wichtel. */
    suspend fun doGiftPop() {
        faceFront()
        val gx = (x + 0.12f * facing).coerceIn(0.1f, 0.9f)
        anim(300) { t -> pose.armL = 140f * t; pose.armR = 140f * t }
        if (mess.size >= 14) mess.removeAt(0)
        mess.add(MessItem(MessItem.GIFT, gx, y, 0f))
        burst(gx, y - 0.04f, 14, PK.POOF, 0.15f, 0.8f, 1.2f)
        sfx(Fx.POOF)
        sfx(Fx.GIFT)
        confetti(gx, y - 0.05f, 50)
        say(lines.giftPop.random(rng), Mood.SURPRISED)
        idle(1800)
        pose.relax()
    }

    /** Zauberspruch: Funken fliegen, Spielzeug erscheint aus dem Nichts. */
    suspend fun doMagic() {
        faceFront()
        say(lines.magic.random(rng), Mood.AMUSED)
        sfx(Fx.SPARKLE)
        anim(1500) {
            pose.armR = 150f + 20f * sin(clock * 12f)
            pose.hatTilt = 0.04f * sin(clock * 6f)
            if (rng.nextFloat() < 0.5f) burst(x + 0.05f * facing, y - hNorm * 0.95f, 2, PK.SPARK, 0.12f, 0.7f)
        }
        pose.relax()
        spawnMess(3)
        sfx(Fx.JINGLE, 0.7f)
        confetti(x, y - hNorm * 0.8f, 25)
        sayWait(lines.surprised.random(rng), Mood.SURPRISED)
    }

    // ---------------------------------------------------------------- Wichtelhaus mit Tür

    /** Drehung der Tür um ihren Fußpunkt (Grad) */
    var doorRot = 0f
        private set

    fun configureDoor(p: Pt?, scale: Float, hingeLeft: Boolean = true, round: Float = 1f, rot: Float = 0f) {
        doorPos = p
        doorRot = rot
        doorScale = scale
        doorHingeLeft = hingeLeft
        doorRound = round.coerceIn(0f, 1f)
    }

    /** Ohne gesetzte Tür benutzen die Szenen eine Standardstelle. */
    private val door: Pt get() = doorPos ?: Pt(0.5f, 0.62f)

    private fun frontOfDoor() = Pt(door.x, (door.y + 0.02f).coerceAtMost(0.95f))

    suspend fun openDoor(ms: Long = 900) {
        sfx(Fx.DOOR_OPEN, 0.8f)
        val from = doorOpen
        anim(ms) { t -> doorOpen = from + (1f - from) * t }
        doorOpen = 1f
    }

    suspend fun closeDoor(ms: Long = 700) {
        val from = doorOpen
        anim(ms) { t -> doorOpen = from * (1f - t) }
        doorOpen = 0f
        sfx(Fx.DOOR_CLOSE)
    }

    /** Klopft an die Tür. */
    suspend fun doKnock(times: Int = 3) {
        turnTo(180f)
        say(lines.doorKnock.random(rng))
        repeat(times) {
            anim(150) { t -> pose.armR = 120f * t }
            sfx(Fx.KNOCK)
            anim(150) { t -> pose.armR = 120f * (1f - t) }
        }
        pose.relax()
    }

    /** Stelle neben der Tür (auf der Seite, zu der die Tür nicht aufschwingt), wo der Wichtel steht und die Tür bedient. */
    private fun doorStand(): Pt {
        val dw = doorScale * 0.56f / aspect
        val side = if (doorHingeLeft) 1f else -1f
        return Pt((door.x + side * (dw / 2f + 0.07f)).coerceIn(0.05f, 0.95f), (door.y + 0.012f).coerceAtMost(0.97f))
    }

    /** Hingehen, zur Tür drehen (Blickrichtung zur Tür); liefert die Blickrichtung in Grad (90 = nach rechts, 270 = nach links). */
    private suspend fun approachDoor(): Float {
        val stand = doorStand()
        walkRoute(stand)
        val toRight = door.x > x
        facing = if (toRight) 1 else -1
        val toDoor = if (toRight) 90f else 270f
        turnTo(toDoor)
        return toDoor
    }

    /** Dreht sich um (Rücken zur Tür) und schließt die Tür hinter sich – den Arm nach hinten gestreckt. */
    private suspend fun turnAndClose(toDoor: Float, fast: Boolean) {
        turnTo(if (toDoor == 90f) 270f else 90f)
        facing = -facing
        pose.armR = 55f
        pose.armL = 25f
        closeDoor(if (fast) 250 else 700)
        pose.relax()
    }

    /**
     * Öffnet die Tür seines Wichtelhauses, schaut in die kleine weihnachtliche Stube (sie erscheint hinter der Tür), dreht sich dann um
     * und schließt die Tür hinter sich. Der Wichtel selbst bleibt dabei draußen vor der Tür.
     */
    suspend fun doEnterHouse(fast: Boolean = false) {
        val toDoor = approachDoor()
        say(lines.doorHome.random(rng))
        anim(if (fast) 250L else 500L) { t ->
            pose.armR = 70f * t
            pose.lean = 3f * t
        }
        openDoor(if (fast) 350 else 900)
        // hineinschauen: Kopf wiegt leicht, ein Arm zeigt auf die Stube
        anim(if (fast) 700L else 2200L) {
            pose.armR = 25f + 8f * sin(clock * 4f)
            pose.hatTilt = 0.05f * sin(clock * 2.5f)
            pose.lean = 4f
        }
        pose.relax()
        turnAndClose(toDoor, fast)
    }

    /** Öffnet die Tür, holt ein Geschenk heraus (oder greift nur hinein), dreht sich um und schließt die Tür. */
    suspend fun doExitHouse(carryGift: Boolean = false, fast: Boolean = false) {
        val toDoor = approachDoor()
        openDoor(if (fast) 300 else 900)
        anim(if (fast) 500L else 1200L) { t ->
            pose.armR = 60f + 25f * sin(t * PIf)
            pose.lean = 6f * sin(t * PIf)
        }
        if (carryGift) {
            pose.prop = Prop.GIFT
            sfx(Fx.GIFT)
        }
        pose.relax()
        say(lines.doorExit.random(rng), Mood.AMUSED)
        turnAndClose(toDoor, fast)
    }

    /**
     * Geht zur Tür, öffnet sie, geht hinein (wird dabei nur so weit kleiner, wie es die Entfernung zur Tür verlangt), dreht sich drinnen
     * zum Betrachter um und schließt die Tür von innen. Danach bleibt er im Haus (unsichtbar) bis eine Aktion ihn wieder herausholt.
     */
    suspend fun doGoInside() {
        val front = Pt(door.x, (door.y + 0.02f).coerceAtMost(0.95f))
        walkRoute(front)
        turnTo(180f) // mit dem Rücken zum Betrachter zur Tür
        say(lines.doorHome.random(rng))
        openDoor(900)
        val sx = x
        val sy = y
        val ty = door.y - 0.005f
        anim(1300) { t ->
            val s = sin(clock * 11f)
            x = sx + (door.x - sx) * t
            y = sy + (ty - sy) * t
            pose.scale = 1f - 0.55f * t
            pose.legL = 25f * s
            pose.legR = -25f * s
            pose.bob = abs(s) * 0.012f
        }
        pose.relax()
        pose.scale = 0.45f
        // drinnen umdrehen, kurz winken und die Tür von innen zumachen
        turnTo(0f)
        anim(700) { pose.armR = 150f + 20f * sin(clock * 10f) }
        pose.relax()
        pose.armR = 55f
        closeDoor(700)
        pose.relax()
        pose.scale = 0f
        inHouse = true
    }

    /** Die Tür geht von innen auf, der Wichtel kommt heraus (wird größer), dreht sich um und schließt die Tür hinter sich. */
    suspend fun doComeOutside() {
        if (!inHouse) return
        x = door.x
        y = door.y - 0.005f
        pose.relax()
        pose.heading = 0f
        facing = 1
        pose.scale = 0.45f
        visible = true
        inHouse = false
        openDoor(900)
        val ty = (door.y + 0.02f).coerceAtMost(0.95f)
        val sy = y
        anim(1200) { t ->
            val s = sin(clock * 11f)
            y = sy + (ty - sy) * t
            pose.scale = 0.45f + 0.55f * t
            pose.legL = 25f * s
            pose.legR = -25f * s
            pose.bob = abs(s) * 0.012f
        }
        pose.relax()
        pose.scale = 1f
        say(lines.doorExit.random(rng), Mood.AMUSED)
        turnTo(180f) // zur Tür umdrehen und zumachen
        pose.armR = 55f
        closeDoor(700)
        pose.relax()
    }

    /** Vor der Tür sauber machen und dann einladend winken. */
    suspend fun doDoorSweep() {
        walkTo(Pt((door.x - 0.1f).coerceAtLeast(0.08f), frontOfDoor().y))
        doSweep(2200)
        openDoor()
        say(lines.doorWelcome.random(rng))
        anim(1800) { pose.armR = 150f + 22f * sin(clock * 10f) }
        pose.relax()
        closeDoor()
    }

    // ---------------------------------------------------------------- Ablauf

    /** Führt einen Aktionspunkt aus: erst hingehen, dann die Aktion. */
    suspend fun runStep(step: ScriptStep) {
        stepBubble = step.bubble
        try {
            runStepInner(step)
        } finally {
            stepBubble = null
        }
    }

    private suspend fun runStepInner(step: ScriptStep) {
        val a = step.action
        if (a == ElfAction.HOUSE_IN && inHouse) return
        // wer im Haus ist, kommt vor jeder anderen Aktion wieder heraus
        if (inHouse && a != ElfAction.HOUSE_OUT) doComeOutside()
        if (a.needsPoint && step.at != null && a != ElfAction.GIFT) walkRoute(step.at)
        when (a) {
            ElfAction.WALK -> Unit
            ElfAction.SWEEP -> {
                doSweep()
                sayFrom(lines.tidyDone)
            }
            ElfAction.WAVE -> doWave()
            ElfAction.DANCE -> doDance(withConfetti = true)
            ElfAction.SIT -> doSit(2000)
            ElfAction.SLEEP -> doSleep()
            ElfAction.SNACK -> doSnack()
            ElfAction.GIFT -> doGiftCarry(step.at ?: Pt(x, y))
            ElfAction.MAGIC -> doMagic()
            ElfAction.SNOW -> doSnowOn()
            ElfAction.SNOW_OFF -> doSnowOff()
            ElfAction.TUMBLE -> doTumble()
            ElfAction.TRIP -> doTrip()
            ElfAction.CHAOS -> doChaos(walk = false)
            ElfAction.HIDE -> doHide(Pt((x + rnd(-0.2f, 0.2f)).coerceIn(0.1f, 0.9f), y))
            ElfAction.BOW -> doBow()
            ElfAction.BED -> doBed(findBed())
            ElfAction.KNOCK -> {
                walkRoute(frontOfDoor())
                doKnock()
            }
            ElfAction.ENTER -> doEnterHouse()
            ElfAction.EXIT -> doExitHouse(carryGift = true)
            ElfAction.HOUSE_IN -> doGoInside()
            ElfAction.HOUSE_OUT -> doComeOutside()
            ElfAction.DOOR_SWEEP -> doDoorSweep()
        }
    }

    /**
     * Die gesamte Aufführung: erscheint am Startpunkt, begrüßt, spielt dann nacheinander Szenen (zuerst die
     * gewählte, danach zufällige aus der Kategorie) und folgt dabei dem Weg. Endet erst, wenn die Aufnahme abbricht.
     */
    suspend fun perform(req: PerfRequest, played: MutableList<String>) {
        appear(req.start)
        greet()
        // die platzierten Gegenstände stellt der Wichtel nacheinander selbst auf
        if (req.autoBuild) for (item in items) doBuild(item)
        if (req.script.isNotEmpty() && !req.idleOnly) {
            // Drehbuch: die Aktionspunkte der Reihe nach abarbeiten, danach verbeugen und stehen bleiben
            var joint = 0
            for (st in req.script) {
                if (st.who == 2 && sync != null) {
                    // gemeinsame Aktion: erst auf die andere Figur warten (höchstens eine Minute)
                    val n = joint++
                    sync?.arrive(n, actor)
                    var waited = 0
                    while (sync?.both(n) == false && waited++ < 600) idle(100)
                }
                played.add(st.action.label)
                runStep(st)
                idle(rng.nextLong(300, 700))
            }
            if (!inHouse) doBow()
            scriptDoneMs = timeMs
            while (true) idle(800)
        }
        val stops = Stops(req.path, this)
        var last: SceneDef? = null
        var first = req.sceneId?.let { Scenes.find(it) }
        while (true) {
            val scene = first ?: Scenes.pick(req.category, req.enabledScenes, last, rng, req.door != null && !req.idleOnly, findBed() != null && !req.idleOnly)
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

    /** Die gesetzten Punkte der Reihe nach, danach wieder von vorn (Rundgang); ohne Punkte ein Ort in der Nähe */
    fun next(): Pt = if (points.isEmpty()) engine.randomPoint() else points[i++ % points.size]
}
