package app.wichtelein.anim

import app.wichtelein.camera.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import app.wichtelein.sound.Fx
import app.wichtelein.sound.SoundEvent
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Punkt im Video, relativ (0..1). */
data class Pt(val x: Float, val y: Float)

/** Was der Wichtel an einem Aktionspunkt tut. [needsPoint] = er läuft vorher dorthin; [needsDoor] = braucht die Tür des Wichtelhauses. */
enum class ElfAction(val label: String, val emoji: String, val needsDoor: Boolean = false, val needsPoint: Boolean = true, val needsBed: Boolean = false) {
    WALK("Hingehen", "🚶"),
    SWEEP("Fegen", "🧹"),
    WAVE("Winken", "👋"),
    DANCE("Tanzen", "💃"),
    SIT("Hinsetzen", "🪑"),
    SLEEP("Schlafen", "😴"),
    SNACK("Keks naschen", "🍪"),
    GIFT("Geschenk bringen", "🎁"),
    MAGIC("Zaubern", "✨"),
    TUMBLE("Purzelbaum", "🤸"),
    TRIP("Stolpern", "💥"),
    CHAOS("Chaos machen", "🧸"),
    HIDE("Verstecken", "🙈"),
    BOW("Verbeugen", "🙇"),
    BED("Ins Bett & schnarchen", "🛏", needsPoint = false, needsBed = true),
    KNOCK("Anklopfen", "✊", needsDoor = true, needsPoint = false),
    ENTER("Tür öffnen & reinschauen", "🏠", needsDoor = true, needsPoint = false),
    EXIT("Geschenk aus dem Haus holen", "🎁", needsDoor = true, needsPoint = false),
    HOUSE_IN("Ins Haus gehen & drinnen bleiben", "🏠", needsDoor = true, needsPoint = false),
    HOUSE_OUT("Aus dem Haus kommen", "🚪", needsDoor = true, needsPoint = false),
    DOOR_SWEEP("Vor der Tür fegen", "🧹", needsDoor = true, needsPoint = false),
    SNOW("Es fängt an zu schneien", "❄", needsPoint = false),
    SNOW_OFF("Der Schnee hört auf", "☀", needsPoint = false),
}

/** Ein Aktionspunkt im Bild: [action] wird an der Stelle [at] ausgeführt (bei Türaktionen ohne Stelle). */
/** [bubble] = eigene Sprechblase für diese Aktion (null = die Standardeinstellung). */
/** [who] = welche Figur die Aktion macht, wenn beide auftreten (0 = Wichtel, 1 = Elfin, 2 = beide zusammen); bei nur einer Figur egal. */
data class ScriptStep(val action: ElfAction, val at: Pt? = null, val bubble: BubbleSpec? = null, val who: Int = 0)

/** Liefert der Engine den Takt. Beim Aufnehmen ist das eine virtuelle Uhr, kein echter Bildschirm-Takt. */
interface Ticker {
    /** Wartet auf das nächste Bild und liefert dessen Zeit in Nanosekunden. */
    suspend fun nextFrame(): Long
}

/** Wetter, das beide Figuren teilen (Schnee): Zeitpunkte auf der gemeinsamen Uhr. */
class Weather {
    var snowOnAt = -1000f
    var snowOffAt = -1000f
}

/** Treffpunkt für gemeinsame Aktionen: jede Figur meldet sich, die erste wartet auf die zweite. */
class StepSync {
    private val arrived = HashMap<Int, MutableSet<Int>>()

    fun arrive(step: Int, actor: Int) {
        arrived.getOrPut(step) { HashSet() }.add(actor)
    }

    fun both(step: Int): Boolean = (arrived[step]?.size ?: 0) >= 2
}

class ManualTicker : Ticker {
    private var waiting: CancellableContinuation<Long>? = null
    private var nanos = 0L

    override suspend fun nextFrame(): Long = suspendCancellableCoroutine { waiting = it }

    fun tick(dtNanos: Long) {
        nanos += dtNanos
        val w = waiting
        waiting = null
        w?.resume(nanos)
    }
}

/** Ein Satz, der zu einem Zeitpunkt der Aufführung gesprochen wird. */
/** [actor] = wer spricht (0 = Wichtel, 1 = Elfin); bestimmt die Stimme. */
class SpeechEvent(val timeMs: Long, val text: String, val mood: Int?, val actor: Int = 0)

/** Alles, was zu einem Zeitpunkt zu sehen ist (unabhängige Kopie, damit Zeitleisten stabil bleiben). */
class FrameState(
    val x: Float,
    val y: Float,
    val facing: Int,
    val visible: Boolean,
    val pose: Pose,
    val bubble: String?,
    val mess: List<MessItem>,
    val flying: MessItem?,
    val particles: List<Particle>,
    /** Position der Tür des Wichtelhauses (Fußpunkt, relativ) oder null; [doorOpen] 0 = zu, 1 = ganz offen. */
    val doorPos: Pt? = null,
    val doorScale: Float = 0.2f,
    val doorOpen: Float = 0f,
    /** Türanschlag (true = links angeschlagen, öffnet nach links) und Rundung oben (0 = eckig, 1 = Rundbogen). */
    val doorHingeLeft: Boolean = true,
    val doorRound: Float = 1f,
    /** Zeit in Sekunden (für blinkende Lichter der Gegenstände) */
    val timeSec: Float = 0f,
    /** Aufbaufortschritt (0..1) der Gegenstände nach Nummer; fehlt eine Nummer, steht der Gegenstand noch nicht */
    val built: Map<Int, Float> = emptyMap(),
    /** Form, Größe und Sichtbarkeit der Sprechblase in diesem Moment */
    val bubbleSpec: BubbleSpec = BubbleSpec(),
    /** Gegenstand, auf dem der Wichtel gerade liegt (er wird dann hinter ihm gezeichnet), sonst -1 */
    val onItem: Int = -1,
    /** Stärke des Schneefalls 0..1 (0 = kein Schnee) */
    val snow: Float = 0f,
    /** Drehung der Tür um ihren Fußpunkt in Grad */
    val doorRot: Float = 0f,
) {
    /** Die zweite Figur im gleichen Moment, wenn Wichtel und Elfin zusammen auftreten */
    var partner: FrameState? = null
}

/** Eine fertig aufgenommene Wichtel-Aufführung: Bilder mit festem Takt plus Sprach-Ereignisse. */
class Performance(
    val fps: Int,
    val frames: List<FrameState>,
    val speech: List<SpeechEvent>,
    val sceneNames: List<String>,
    /** Geräusche mit Zeitpunkt (Glöckchen, Tür, Besen, …) */
    val sounds: List<SoundEvent> = emptyList(),
    /** Zeitpunkt, an dem alle Aktionspunkte abgespielt waren (-1 = kein Drehbuch oder noch nicht fertig) */
    val scriptEndMs: Long = -1L,
    /** Fehler, die eine Figur während der Aufnahme gestoppt haben (sie steht danach nur noch da) */
    val errors: List<String> = emptyList(),
) {
    val durationMs: Long get() = (frames.size - 1).coerceAtLeast(0) * 1000L / fps

    fun frameAt(ms: Long): FrameState {
        val i = (ms * fps / 1000L).toInt().coerceIn(0, frames.size - 1)
        return frames[i]
    }
}

/** Wunsch an die Aufführung: wo, wie groß, welcher Weg, welche Szene, wie lange. */
class PerfRequest(
    val start: Pt,
    val path: List<Pt> = emptyList(),
    val sizeScale: Float = 0.30f,
    /** null = jede Kategorie gemischt (Zufall) */
    val category: Category? = null,
    /** feste erste Szene (manuell gewählt) */
    val sceneId: String? = null,
    val seed: Long = 1L,
    val durationMs: Long = 15_000L,
    /** null = alle Szenen erlaubt */
    val enabledScenes: Set<String>? = null,
    val settings: Settings = Settings(),
    val fps: Int = 30,
    /** Tür des Wichtelhauses im Video (null = keine) und ihre Höhe in Videohöhen. */
    val door: Pt? = null,
    val doorScale: Float = 0.2f,
    val doorHingeLeft: Boolean = true,
    val doorRound: Float = 1f,
    val doorRot: Float = 0f,
    /** Aktionspunkte: Wenn gesetzt, spielt der Wichtel genau diese Schritte der Reihe nach (statt zufälliger Szenen). */
    val script: List<ScriptStep> = emptyList(),
    /** Aufnahme endet kurz nach dem letzten Aktionspunkt (Foto-Film), sonst nach [durationMs] */
    val stopAfterScript: Boolean = false,
    /** Seitenverhältnis des Bildes (Breite / Höhe) */
    val aspect: Float = 1f,
    /** Gegenstände im Bild; der Wichtel stellt sie am Anfang nacheinander auf (siehe [autoBuild]) */
    val scenery: List<SceneryItem> = emptyList(),
    val autoBuild: Boolean = true,
    /** Der Wichtel bleibt in der Nähe der gesetzten Punkte (Start, Weg, Aktionen); false = ganzes Bild (AR) */
    val confineToPoints: Boolean = true,
    /** Es schneit von Anfang an */
    val snowFromStart: Boolean = false,
    /** Startpunkt der Elfin, wenn beide auftreten (null = neben dem Wichtel) */
    val start2: Pt? = null,
    /** Wegpunkte der Elfin, wenn beide auftreten (der Wichtel geht [path]) */
    val path2: List<Pt> = emptyList(),
    /** Die Figur steht nur da (zweite Figur, wenn die erste ein Drehbuch spielt und diese keine Aktionen hat) */
    val idleOnly: Boolean = false,
) {
    /** Dieselbe Anfrage mit anderem Drehbuch, Startpunkt und Zufall (für die zweite Figur). */
    fun derive(script: List<ScriptStep>, start: Pt, seed: Long, autoBuild: Boolean, sceneId: String?, idleOnly: Boolean = false, path: List<Pt> = this.path) = PerfRequest(
        start, path, sizeScale, category, sceneId, seed, durationMs, enabledScenes, settings, fps, door, doorScale, doorHingeLeft, doorRound, doorRot,
        script, stopAfterScript, aspect, scenery, autoBuild, confineToPoints, snowFromStart, start2, path2, idleOnly,
    )
}

/** Nachlauf nach dem letzten Aktionspunkt im Foto-Film */
const val TAIL_MS = 2500L

object Recorder {
    /** Fängt Fehler einer Figur ab: sie steht dann einfach da, statt die ganze App zum Absturz zu bringen. */
    private suspend fun safely(engine: ElfEngine, errors: MutableList<String>, body: suspend () -> Unit) {
        try {
            body()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            errors.add("Figur ${engine.actor} bei ${engine.timeMs} ms: " + t.stackTraceToString().take(700))
            try {
                while (true) engine.idle(800)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private class Actor(val engine: ElfEngine, val ticker: ManualTicker, val req: PerfRequest, val actor: Int) {
        lateinit var job: kotlinx.coroutines.Job
    }

    /** Nimmt die Aufführung deterministisch auf (gleiche Eingaben ergeben die gleiche Zeitleiste). Bei zwei Figuren laufen beide gleichzeitig. */
    fun record(req: PerfRequest, cancelled: () -> Boolean = { false }): Performance {
        val speech = ArrayList<SpeechEvent>()
        val played = ArrayList<String>()
        val errors = ArrayList<String>()
        val sounds = ArrayList<SoundEvent>()
        val both = req.settings.both
        val weather = Weather()
        val first = if (both) 0 else if (req.settings.elfin) 1 else 0

        fun build(r: PerfRequest, actor: Int, seed: Long): Actor {
            val ticker = ManualTicker()
            lateinit var engine: ElfEngine
            engine = ElfEngine(
                settings = { req.settings },
                speak = { text, mood -> speech.add(SpeechEvent(engine.timeMs, text, mood, actor)) },
                rng = Random(seed),
                ticker = ticker,
                fx = { f: Fx, gain: Float -> sounds.add(SoundEvent(engine.timeMs, f, gain)) },
                actor = actor,
                weather = weather,
            )
            engine.configureDoor(r.door, r.doorScale, r.doorHingeLeft, r.doorRound, r.doorRot)
            engine.configureScenery(r.scenery)
            engine.configureSnow(r.snowFromStart)
            engine.configureRoute(if (r.confineToPoints) listOf(r.start) + r.path else emptyList())
            engine.configureRoam(
                if (r.confineToPoints) listOf(r.start) + r.path + r.script.mapNotNull { it.at } else emptyList(),
                r.aspect,
            )
            return Actor(engine, ticker, r, actor)
        }

        val a: Actor
        val b: Actor?
        if (both) {
            val scripted = req.script.isNotEmpty()
            val stepsA = req.script.filter { it.who != 1 }
            // gemeinsame Aktionen macht die Elfin ein Stück neben dem Wichtel
            val stepsB = req.script.filter { it.who != 0 }.map { st ->
                if (st.who == 2 && st.at != null) st.copy(at = Pt((st.at.x + 0.09f).coerceAtMost(0.96f), st.at.y)) else st
            }
            val reqA = req.derive(stepsA, req.start, req.seed, req.autoBuild, req.sceneId, idleOnly = scripted && stepsA.isEmpty())
            val startB = req.start2 ?: Pt((req.start.x + 0.13f).coerceAtMost(0.95f).let { if (it - req.start.x < 0.05f) (req.start.x - 0.13f).coerceAtLeast(0.05f) else it }, req.start.y)
            val reqB = req.derive(stepsB, startB, req.seed + 101L, false, null, idleOnly = scripted && stepsB.isEmpty(), path = req.path2)
            a = build(reqA, 0, req.seed)
            b = build(reqB, 1, req.seed + 101L)
            val sync = StepSync()
            a.engine.sync = sync
            b.engine.sync = sync
        } else {
            a = build(req, first, req.seed)
            b = null
        }

        val scope = CoroutineScope(Dispatchers.Unconfined)
        a.job = scope.launch { safely(a.engine, errors) { a.engine.perform(a.req, played) } }
        if (b != null) b.job = scope.launch { safely(b.engine, errors) { b.engine.perform(b.req, ArrayList()) } }
        val frames = ArrayList<FrameState>()
        fun snap(): FrameState = a.engine.snapshot().also { f -> f.partner = b?.engine?.snapshot() }
        frames.add(snap())
        val total = (req.durationMs * req.fps / 1000L).toInt()
        val dt = 1_000_000_000L / req.fps
        var scriptEnd = -1L
        for (i in 0 until total) {
            if (cancelled()) break
            a.ticker.tick(dt)
            b?.ticker?.tick(dt)
            frames.add(snap())
            if (req.script.isNotEmpty()) {
                val doneA = a.req.script.isEmpty() || a.engine.scriptDoneMs >= 0
                val doneB = b == null || b.req.script.isEmpty() || b.engine.scriptDoneMs >= 0
                if (doneA && doneB) {
                    scriptEnd = maxOf(a.engine.scriptDoneMs, b?.engine?.scriptDoneMs ?: -1L)
                    if (scriptEnd >= 0 && req.stopAfterScript && a.engine.timeMs >= scriptEnd + TAIL_MS) break
                }
            }
        }
        a.job.cancel()
        b?.job?.cancel()
        // Sprache und Geräusche beider Figuren in zeitlicher Reihenfolge
        val speechSorted = speech.sortedBy { it.timeMs }
        val soundsSorted = sounds.sortedBy { it.timeMs }
        return Performance(req.fps, frames, speechSorted, played, soundsSorted, scriptEnd, errors)
    }
}
