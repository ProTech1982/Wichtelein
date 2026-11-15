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
}

/** Ein Aktionspunkt im Bild: [action] wird an der Stelle [at] ausgeführt (bei Türaktionen ohne Stelle). */
/** [bubble] = eigene Sprechblase für diese Aktion (null = die Standardeinstellung). */
data class ScriptStep(val action: ElfAction, val at: Pt? = null, val bubble: BubbleSpec? = null)

/** Liefert der Engine den Takt. Beim Aufnehmen ist das eine virtuelle Uhr, kein echter Bildschirm-Takt. */
interface Ticker {
    /** Wartet auf das nächste Bild und liefert dessen Zeit in Nanosekunden. */
    suspend fun nextFrame(): Long
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
class SpeechEvent(val timeMs: Long, val text: String, val mood: Int?)

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
)

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
)

/** Nachlauf nach dem letzten Aktionspunkt im Foto-Film */
const val TAIL_MS = 2500L

object Recorder {
    /** Nimmt die Aufführung deterministisch auf (gleiche Eingaben ergeben die gleiche Zeitleiste). */
    fun record(req: PerfRequest): Performance {
        val ticker = ManualTicker()
        val speech = ArrayList<SpeechEvent>()
        val played = ArrayList<String>()
        val sounds = ArrayList<SoundEvent>()
        lateinit var engine: ElfEngine
        engine = ElfEngine(
            settings = { req.settings },
            speak = { text, mood -> speech.add(SpeechEvent(engine.timeMs, text, mood)) },
            rng = Random(req.seed),
            ticker = ticker,
            fx = { f: Fx, gain: Float -> sounds.add(SoundEvent(engine.timeMs, f, gain)) },
        )
        engine.configureDoor(req.door, req.doorScale, req.doorHingeLeft, req.doorRound)
        engine.configureScenery(req.scenery)
        engine.configureRoute(if (req.confineToPoints) listOf(req.start) + req.path else emptyList())
        engine.configureRoam(
            if (req.confineToPoints) listOf(req.start) + req.path + req.script.mapNotNull { it.at } else emptyList(),
            req.aspect,
        )
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch { engine.perform(req, played) }
        val frames = ArrayList<FrameState>()
        frames.add(engine.snapshot())
        val total = (req.durationMs * req.fps / 1000L).toInt()
        val dt = 1_000_000_000L / req.fps
        var scriptEnd = -1L
        for (i in 0 until total) {
            ticker.tick(dt)
            frames.add(engine.snapshot())
            if (req.script.isNotEmpty() && engine.scriptDoneMs >= 0) {
                scriptEnd = engine.scriptDoneMs
                if (req.stopAfterScript && engine.timeMs >= scriptEnd + TAIL_MS) break
            }
        }
        job.cancel()
        return Performance(req.fps, frames, speech, played, sounds, scriptEnd)
    }
}
