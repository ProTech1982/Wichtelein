package app.wichtelein.anim

import app.wichtelein.camera.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.ui.*

import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Punkt im Video, relativ (0..1). */
data class Pt(val x: Float, val y: Float)

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
)

/** Eine fertig aufgenommene Wichtel-Aufführung: Bilder mit festem Takt plus Sprach-Ereignisse. */
class Performance(val fps: Int, val frames: List<FrameState>, val speech: List<SpeechEvent>, val sceneNames: List<String>) {
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
)

object Recorder {
    /** Nimmt die Aufführung deterministisch auf (gleiche Eingaben ergeben die gleiche Zeitleiste). */
    fun record(req: PerfRequest): Performance {
        val ticker = ManualTicker()
        val speech = ArrayList<SpeechEvent>()
        val played = ArrayList<String>()
        lateinit var engine: ElfEngine
        engine = ElfEngine(
            settings = { req.settings },
            speak = { text, mood -> speech.add(SpeechEvent(engine.timeMs, text, mood)) },
            rng = Random(req.seed),
            ticker = ticker,
        )
        engine.configureDoor(req.door, req.doorScale)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch { engine.perform(req, played) }
        val frames = ArrayList<FrameState>()
        frames.add(engine.snapshot())
        val total = (req.durationMs * req.fps / 1000L).toInt()
        val dt = 1_000_000_000L / req.fps
        repeat(total) {
            ticker.tick(dt)
            frames.add(engine.snapshot())
        }
        job.cancel()
        return Performance(req.fps, frames, speech, played)
    }
}
