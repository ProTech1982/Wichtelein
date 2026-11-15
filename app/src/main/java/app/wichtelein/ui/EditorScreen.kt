package app.wichtelein.ui

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.sound.SoundPlayer

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Werkzeuge des Editors: was ein Tippen ins Bild bewirkt. */
enum class Tool { START, PATH, ACTION, DOOR, OBJECTS, SCENE }

private val Hint = Color(0xFFCCCCCC)
private val Warn = Color(0xFFFFE08A)

/**
 * Editor: Startpunkt, Weg, Aktionspunkte, Tür und Gegenstände (Tannenbaum, Bett, Zaun, Beleuchtung …) festlegen, Szene wählen
 * oder würfeln, Vorschau ansehen und als Video speichern. [file] ist ein Video oder (bei [isPhoto]) ein Foto, aus dem ein Film
 * entsteht.
 *
 * Bedienung: Antippen wählt einen vorhandenen Punkt oder Gegenstand aus (Ziehen verschiebt ihn, „Löschen“ entfernt ihn),
 * Antippen einer leeren Stelle setzt je nach Werkzeug etwas Neues. „↶ / ↷“ nimmt jeden Schritt zurück bzw. stellt ihn wieder her.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    file: File,
    settings: Settings,
    onSettings: (Settings) -> Unit,
    onBack: () -> Unit,
    onOpenVideos: () -> Unit,
    isPhoto: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember(file.path, file.lastModified(), isPhoto) { if (isPhoto) readPhotoInfo(file) else readVideoInfo(file) }
    val photo by produceState<ImageBitmap?>(null, file.path, file.lastModified(), isPhoto) {
        value = if (isPhoto) withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() } else null
    }
    val sprites = rememberElfFigure(settings)
    val sceneryAssets = remember { SceneryAssets.load(context) }
    val speaker = remember { Speaker(context) }
    val fxPlayer = remember { SoundPlayer() }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    SideEffect {
        speaker.enabled = settings.speak
        fxPlayer.enabled = settings.soundFx
        speaker.effect = settings.effect
        speaker.rate = settings.rate
        speaker.mood = settings.mood
        speaker.useElfVoice = settings.elfVoice
        speaker.voiceId = settings.voiceId
    }
    val latestSettings by rememberUpdatedState(settings)

    // ---- Zustand des Editors mit Rückgängig/Wiederholen
    var st by remember { mutableStateOf(EditState()) }
    val history = remember { EditHistory() }
    var histTick by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Sel?>(null) }
    var tool by remember { mutableStateOf(Tool.START) }
    var action by remember { mutableStateOf(ElfAction.SWEEP) }
    var objType by remember { mutableStateOf(SceneryType.TREE) }
    var sliderBase by remember { mutableStateOf<EditState?>(null) }
    var fine by remember { mutableStateOf(false) }

    fun commit(n: EditState) {
        if (n != st) {
            history.push(st)
            st = n
            histTick++
        }
    }

    fun fixSelection() {
        sel = sel?.takeIf { EditOps.contains(st, it) }
    }

    fun undo() {
        history.undo(st)?.let {
            st = it
            histTick++
            fixSelection()
        }
    }

    fun redo() {
        history.redo(st)?.let {
            st = it
            histTick++
            fixSelection()
        }
    }

    /** Regler: während des Ziehens laufend ändern, am Ende als ein Schritt merken. */
    fun slide(n: EditState) {
        if (sliderBase == null) sliderBase = st
        st = n
    }

    fun slideDone() {
        sliderBase?.let {
            if (it != st) {
                history.push(it)
                histTick++
            }
        }
        sliderBase = null
    }

    var photoSeconds by remember { mutableFloatStateOf(12f) }
    var category by remember { mutableStateOf<Category?>(null) }
    var sceneId by remember { mutableStateOf<String?>(null) }
    var seed by remember { mutableLongStateOf(System.nanoTime() and 0xFFFFFFL) }
    var perf by remember { mutableStateOf<Performance?>(null) }
    var clips by remember { mutableStateOf<List<VoiceAudio?>>(emptyList()) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    var posMs by remember { mutableLongStateOf(0L) }
    var export by remember { mutableStateOf<ExportUi?>(null) }
    val videoRef = remember { arrayOfNulls<VideoView>(1) }
    val scriptMode = st.script.isNotEmpty()
    val duration = when {
        isPhoto && scriptMode -> 60_000L // endet automatisch kurz nach dem letzten Aktionspunkt
        isPhoto -> (photoSeconds * 1000f).toLong()
        else -> min(info.durationMs, 90_000L).coerceAtLeast(3_000L)
    }
    // die Gegenstände ändern die Aufführung nicht: ohne sie neu aufnehmen
    val perfKey = st.copy(nextId = 0)

    // 1) Aufführung aufnehmen, sobald sich etwas ändert
    LaunchedEffect(settings, perfKey, category, sceneId, seed, duration) {
        val req = PerfRequest(
            start = st.start ?: Pt(0.5f, 0.78f),
            path = st.path,
            door = st.door,
            doorScale = st.doorScale,
            doorHingeLeft = st.doorHingeLeft,
            doorRound = st.doorRound,
            script = st.script,
            scenery = st.scenery,
            stopAfterScript = isPhoto && st.script.isNotEmpty(),
            aspect = info.aspect,
            sizeScale = settings.sizeScale,
            category = category,
            sceneId = sceneId,
            seed = seed,
            durationMs = duration,
            enabledScenes = settings.enabledScenes(),
            settings = settings,
        )
        perf = withContext(Dispatchers.Default) { Recorder.record(req) }
    }

    // 2) Sprachclips für die Vorschau erzeugen
    LaunchedEffect(perf, settings.voiceId, settings.effect, settings.rate, settings.mood, settings.elfVoice, settings.speak) {
        val p = perf ?: return@LaunchedEffect
        clips = emptyList()
        if (!settings.speak || !settings.elfVoice) {
            voiceStatus = null
            return@LaunchedEffect
        }
        voiceStatus = "Wichtelstimme wird vorbereitet …"
        val ok = withContext(Dispatchers.Default) { SpeechRender.awaitVoice(context, settings) }
        if (!ok) {
            voiceStatus = "Wichtelstimme nicht verfügbar – Systemstimme oder nur Text"
            return@LaunchedEffect
        }
        val out = ArrayList<VoiceAudio?>()
        for ((i, ev) in p.speech.withIndex()) {
            voiceStatus = "Stimme wird erzeugt (${i + 1}/${p.speech.size}) …"
            out.add(withContext(Dispatchers.Default) { SpeechRender.render(settings, ev) })
        }
        clips = out
        voiceStatus = null
    }

    // 3) Wiedergabeposition verfolgen (Video oder Foto-Uhr) und Sprache und Geräusche zum richtigen Zeitpunkt abspielen
    LaunchedEffect(perf, clips) {
        var last = -1L
        var t0 = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (t0 < 0) t0 = now
            val pos = if (isPhoto) {
                val d = (perf?.durationMs ?: 1000L).coerceAtLeast(1000L)
                ((now - t0) / 1_000_000L) % d
            } else {
                videoRef[0]?.currentPosition?.toLong() ?: 0L
            }
            posMs = pos
            val p = perf ?: continue
            if (pos < last - 400) last = -1L
            if (latestSettings.soundFx) {
                for (fe in p.sounds) {
                    if (fe.timeMs > last && fe.timeMs <= pos) fxPlayer.play(fe.fx, fe.gain)
                }
            }
            for ((i, ev) in p.speech.withIndex()) {
                if (ev.timeMs > last && ev.timeMs <= pos) {
                    val clip = clips.getOrNull(i)
                    val s = latestSettings
                    if (clip != null) speaker.playClip(clip)
                    else if (s.speak && !s.elfVoice) speaker.speak(ev.text, ev.mood)
                }
            }
            last = pos
        }
    }

    val frame = perf?.frameAt(posMs)
    val layer = remember(st.scenery, sceneryAssets) { SceneryLayer(st.scenery, sceneryAssets) }
    val density = LocalDensity.current
    val hitPx = with(density) { 30.dp.toPx() }

    fun itemBox(it: SceneryItem) = sceneryBox(it, sceneryAssets.art(it.type), info.aspect)
    fun doorBox(): Pair<Float, Float>? = if (st.door == null) null else Pair(st.doorScale * 0.56f / info.aspect, st.doorScale)

    fun hitAt(px: Float, py: Float, w: Float, h: Float): Sel? =
        EditOps.hit(st, px, py, hitPx / w, hitPx / h, ::itemBox, doorBox())

    /** Tippen: vorhandenes auswählen, sonst je nach Werkzeug etwas Neues setzen. */
    fun tapAt(px: Float, py: Float, w: Float, h: Float) {
        val hit = hitAt(px, py, w, h)
        if (hit != null) {
            sel = hit
            return
        }
        val p = EditOps.clamp(Pt(px, py))
        when (tool) {
            Tool.START -> {
                commit(st.copy(start = p))
                sel = Sel.Start
            }
            Tool.PATH -> {
                val n = EditOps.addPath(st, p)
                if (n != st) {
                    commit(n)
                    sel = Sel.Path(n.path.lastIndex)
                }
            }
            Tool.ACTION -> if (action.needsPoint) {
                val n = EditOps.addStep(st, action, p)
                if (n != st) {
                    commit(n)
                    sel = Sel.Step(n.script.lastIndex)
                }
            }
            Tool.DOOR -> {
                commit(st.copy(door = p))
                sel = Sel.Door
            }
            Tool.OBJECTS -> {
                val n = EditOps.addItem(st, SceneryItem(0, objType, p, objType.defaultScale))
                if (n != st) {
                    commit(n)
                    sel = Sel.Item(n.nextId - 1)
                }
            }
            Tool.SCENE -> sel = null
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF101010)).safeDrawingPadding()) {
        val canUndo = remember(histTick) { history.canUndo }
        val canRedo = remember(histTick) { history.canRedo }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück", color = Color.White) }
            Text(if (isPhoto) "Foto-Film" else "Wichtel-Editor", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { undo() }, enabled = canUndo) { Text("↶ Rückgängig", color = if (canUndo) Color.White else Color(0xFF666666)) }
            TextButton(onClick = { redo() }, enabled = canRedo) { Text("↷", color = if (canRedo) Color.White else Color(0xFF666666)) }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.aspectRatio(info.aspect)) {
                if (isPhoto) {
                    photo?.let {
                        Image(bitmap = it, contentDescription = "Foto", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.fromFile(file))
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                                videoRef[0] = this
                            }
                        },
                        onRelease = {
                            it.stopPlayback()
                            videoRef[0] = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ElfOverlay(
                    frame = frame,
                    sprites = sprites,
                    sizeScale = settings.sizeScale,
                    modifier = Modifier.fillMaxSize(),
                    scenery = layer,
                    perspective = settings.perspective,
                    showUnbuilt = true,
                )
                EditMarkers(st, sel, ::itemBox, ::doorBox, Modifier.fillMaxSize())
                // Bedienfläche: Tippen wählt/setzt, Ziehen verschiebt das Getroffene
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("bildflaeche")
                        .pointerInput(Unit) {
                            detectTapGestures { o -> tapAt(o.x / size.width, o.y / size.height, size.width.toFloat(), size.height.toFloat()) }
                        }
                        .pointerInput(Unit) {
                            var dragging: Sel? = null
                            var before: EditState? = null
                            detectDragGestures(
                                onDragStart = { o ->
                                    val hit = hitAt(o.x / size.width, o.y / size.height, size.width.toFloat(), size.height.toFloat())
                                    dragging = hit
                                    if (hit != null) {
                                        sel = hit
                                        before = st
                                        history.push(st)
                                    }
                                },
                                onDrag = { change, amount ->
                                    val d = dragging
                                    if (d != null) {
                                        change.consume()
                                        val cur = EditOps.position(st, d)
                                        val k = if (fine) 0.2f else 1f
                                        if (cur != null) st = EditOps.move(st, d, Pt(cur.x + k * amount.x / size.width, cur.y + k * amount.y / size.height))
                                    }
                                },
                                onDragEnd = {
                                    if (dragging != null) {
                                        if (before == st) history.discardLast()
                                        histTick++
                                    }
                                    dragging = null
                                    before = null
                                },
                                onDragCancel = {
                                    if (dragging != null) {
                                        if (before == st) history.discardLast()
                                        histTick++
                                    }
                                    dragging = null
                                    before = null
                                },
                            )
                        },
                )
            }
            voiceStatus?.let {
                Text(
                    it,
                    color = Warn,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 250.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = tool == Tool.START, onClick = { tool = Tool.START }, label = { Text("📍 Start") })
                FilterChip(selected = tool == Tool.PATH, onClick = { tool = Tool.PATH }, label = { Text("🛤 Weg (${st.path.size})") })
                FilterChip(selected = tool == Tool.ACTION, onClick = { tool = Tool.ACTION }, label = { Text("🎯 Aktionen (${st.script.size})") })
                FilterChip(selected = tool == Tool.DOOR, onClick = { tool = Tool.DOOR }, label = { Text(if (st.door == null) "🚪 Tür" else "🚪 Tür ✓") })
                FilterChip(selected = tool == Tool.OBJECTS, onClick = { tool = Tool.OBJECTS }, label = { Text("🎄 Objekte (${st.scenery.size})") })
                FilterChip(selected = tool == Tool.SCENE, onClick = { tool = Tool.SCENE }, label = { Text("🎬 Szene") })
            }

            // Ausgewähltes: verschieben (Ziehen im Bild), Größe, Löschen
            sel?.let { s ->
                if (EditOps.contains(st, s)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ausgewählt: ${EditOps.label(st, s)}", color = Color(0xFF80D8FF), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { commit(EditOps.remove(st, s)); sel = null }) { Text("🗑 Löschen", color = Color(0xFFFF8A80)) }
                        TextButton(onClick = { sel = null }) { Text("Abwählen") }
                    }
                    // Feineinstellung: kleine Schritte per Knopf, oder Ziehen im Zeitlupen-Modus
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Fein:", color = Color.White, fontSize = 13.sp)
                        for ((label, dx, dy) in listOf(Triple("◀", -1f, 0f), Triple("▶", 1f, 0f), Triple("▲", 0f, -1f), Triple("▼", 0f, 1f))) {
                            OutlinedButton(
                                onClick = { commit(EditOps.nudge(st, s, dx * 0.004f, dy * 0.004f)) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                            ) { Text(label, color = Color.White) }
                        }
                        FilterChip(selected = fine, onClick = { fine = !fine }, label = { Text("Ziehen langsam") })
                    }
                    if (s is Sel.Item) {
                        val item = st.scenery.first { it.id == s.id }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Größe", color = Color.White, fontSize = 13.sp)
                            Slider(
                                value = item.scale,
                                onValueChange = { slide(EditOps.resizeItem(st, s.id, it)) },
                                onValueChangeFinished = { slideDone() },
                                valueRange = 0.05f..0.9f,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { commit(EditOps.flipItem(st, s.id)) }) { Text("↔ Spiegeln", color = Color.White) }
                        }
                    }
                }
            }

            when (tool) {
                Tool.START -> Text("Tippe ins Bild: Dort erscheint der Wichtel. Den Punkt kannst du mit dem Finger verschieben.", color = Hint, fontSize = 12.sp)
                Tool.PATH -> {
                    Text(
                        "Tippe nacheinander Stellen, die der Wichtel ablaufen soll (höchstens ${EditOps.MAX_WAYPOINTS}). Punkte lassen sich verschieben, " +
                            "antippen und löschen. Er läuft den Weg als Rundgang ab und bleibt in der Nähe der Punkte.",
                        color = Hint,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { if (st.path.isNotEmpty()) commit(EditOps.remove(st, Sel.Path(st.path.lastIndex))); fixSelection() },
                            enabled = st.path.isNotEmpty(),
                        ) { Text("Letzten Punkt entfernen", color = Color.White) }
                        TextButton(onClick = { commit(st.copy(path = emptyList())); fixSelection() }, enabled = st.path.isNotEmpty()) { Text("Weg löschen") }
                    }
                }
                Tool.ACTION -> {
                    Text(
                        "Wähle eine Aktion und tippe ins Bild, wo sie stattfinden soll. Der Wichtel geht die Punkte der Reihe nach ab. " +
                            "Aktionen mit Tür (🚪) brauchen das markierte Wichtelhaus.",
                        color = Hint,
                        fontSize = 12.sp,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElfAction.values().forEach { a ->
                            FilterChip(
                                selected = action == a,
                                onClick = {
                                    action = a
                                    if (!a.needsPoint && (!a.needsDoor || st.door != null) && (!a.needsBed || st.scenery.any { it.type == SceneryType.BED })) {
                                        val n = EditOps.addStep(st, a, null)
                                        if (n != st) {
                                            commit(n)
                                            sel = null
                                        }
                                    }
                                },
                                label = { Text("${a.emoji} ${a.label}") },
                            )
                        }
                    }
                    if (action.needsBed && st.scenery.none { it.type == SceneryType.BED }) {
                        Text("Stelle zuerst ein Bett auf (Chip „🎄 Objekte“), dann lässt sich diese Aktion hinzufügen.", color = Warn, fontSize = 12.sp)
                    }
                    if (action.needsDoor && st.door == null) {
                        Text("Setze zuerst die Tür (Chip „🚪 Tür“), dann lässt sich diese Aktion hinzufügen.", color = Warn, fontSize = 12.sp)
                    }
                    if (st.script.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            st.script.forEachIndexed { i, step ->
                                FilterChip(
                                    selected = sel == Sel.Step(i),
                                    onClick = { sel = if (step.at != null) Sel.Step(i) else null; if (step.at == null) commit(EditOps.remove(st, Sel.Step(i))) },
                                    label = { Text("${i + 1}. ${step.action.emoji} ${step.action.label}") },
                                )
                            }
                            TextButton(onClick = { commit(st.copy(script = emptyList())); fixSelection() }) { Text("Alle löschen") }
                        }
                        Text("Tipp auf einen Punkt im Bild oder hier wählt ihn aus (Türaktionen erscheinen mit Tipp wieder weg).", color = Hint, fontSize = 11.sp)
                    }
                }
                Tool.DOOR -> {
                    if (st.door == null) {
                        Text("Wichtelhaus: Tippe auf die Stelle am Boden direkt unter der Tür. Dort öffnet, betritt und schließt der Wichtel die Tür.", color = Hint, fontSize = 12.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Türgröße", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { commit(EditOps.remove(st, Sel.Door)); fixSelection() }) { Text("Tür entfernen") }
                        }
                        Slider(
                            value = st.doorScale,
                            onValueChange = { slide(st.copy(doorScale = it)) },
                            onValueChangeFinished = { slideDone() },
                            valueRange = 0.06f..0.5f,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Öffnet nach", color = Color.White, fontSize = 13.sp)
                            FilterChip(selected = st.doorHingeLeft, onClick = { commit(st.copy(doorHingeLeft = true)) }, label = { Text("◀ links") })
                            FilterChip(selected = !st.doorHingeLeft, onClick = { commit(st.copy(doorHingeLeft = false)) }, label = { Text("rechts ▶") })
                        }
                        Text("Form: eckig ↔ rund", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = st.doorRound,
                            onValueChange = { slide(st.copy(doorRound = it)) },
                            onValueChangeFinished = { slideDone() },
                            valueRange = 0f..1f,
                        )
                    }
                }
                Tool.OBJECTS -> {
                    Text(
                        "Wähle einen Gegenstand und tippe ins Bild (Fußpunkt = Stelle am Boden). Jeder lässt sich verschieben, in der Größe ändern, " +
                            "spiegeln und löschen. Hier siehst du ihn nur blass angedeutet: Im Film stellt der Wichtel ihn selbst auf. " +
                            "Mit einem Bett kann er sich hineinlegen und schnarchen (Aktion „🛏 Ins Bett“).",
                        color = Hint,
                        fontSize = 12.sp,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SceneryType.values().forEach { t ->
                            FilterChip(selected = objType == t, onClick = { objType = t }, label = { Text("${t.emoji} ${t.label}") })
                        }
                    }
                    if (sceneryAssets.art(objType) == null) Text("Dieser Gegenstand ist in dieser App-Version nicht enthalten.", color = Warn, fontSize = 12.sp)
                    if (st.scenery.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            st.scenery.forEach { it2 ->
                                FilterChip(selected = sel == Sel.Item(it2.id), onClick = { sel = Sel.Item(it2.id) }, label = { Text("${it2.type.emoji} ${it2.type.label}") })
                            }
                            TextButton(onClick = { commit(st.copy(scenery = emptyList())); fixSelection() }) { Text("Alle entfernen") }
                        }
                    }
                }
                Tool.SCENE -> {
                    if (isPhoto) {
                        if (scriptMode) {
                            Text(
                                "Länge: automatisch, etwa ${(perf?.durationMs ?: 0L) / 1000L} s (endet kurz nach der letzten Aktion)",
                                color = Hint,
                                fontSize = 12.sp,
                            )
                        } else {
                            Text("Länge des Films: ${photoSeconds.toInt()} s", color = Color.White, fontSize = 13.sp)
                            Slider(value = photoSeconds, onValueChange = { photoSeconds = it }, valueRange = 6f..40f)
                        }
                    }
                    Text("Perspektive: ${(settings.perspective * 100).toInt()} %  (0 = überall gleich groß, z. B. Treppe)", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = settings.perspective,
                        onValueChange = { onSettings(settings.copy(perspective = it)) },
                        valueRange = 0f..1.2f,
                    )
                    Text("Größe des Wichtels", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = settings.sizeScale,
                        onValueChange = { onSettings(settings.copy(sizeScale = it)) },
                        valueRange = 0.12f..0.6f,
                    )
                    if (scriptMode) {
                        Text(
                            "Aktionspunkte aktiv: Der Wichtel spielt genau deine Aktionen der Reihe nach. Zum Würfeln der Szenen die Aktionen löschen.",
                            color = Hint,
                            fontSize = 12.sp,
                        )
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = category == null, onClick = { category = null; sceneId = null }, label = { Text("🎲 Zufall") })
                            Category.values().forEach { c ->
                                FilterChip(
                                    selected = category == c,
                                    onClick = { category = c; sceneId = null },
                                    label = { Text("${c.emoji} ${c.label}") },
                                )
                            }
                        }
                        val shown = if (category == null) Scenes.all else Scenes.inCategory(category!!)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = sceneId == null, onClick = { sceneId = null }, label = { Text("Automatisch") })
                            shown.forEach { s ->
                                FilterChip(
                                    selected = sceneId == s.id,
                                    onClick = { sceneId = s.id; category = s.category },
                                    label = { Text(if (s.needsDoor) "🚪 ${s.name}" else s.name) },
                                )
                            }
                        }
                        val chosen = sceneId?.let { Scenes.find(it) }
                        if (chosen != null) Text(chosen.description, color = Hint, fontSize = 12.sp)
                        if (chosen?.needsDoor == true && st.door == null) {
                            Text("Diese Szene braucht die Tür deines Wichtelhauses: markiere sie mit „🚪 Tür“. Bis dahin nimmt der Wichtel eine Standardstelle.", color = Warn, fontSize = 12.sp)
                        }
                    }
                    if (settings.profiles.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            settings.profiles.forEach { pr ->
                                FilterChip(
                                    selected = settings.activeProfile()?.id == pr.id,
                                    onClick = { onSettings(settings.copy(activeProfileId = pr.id)) },
                                    label = { Text("👦 ${pr.name}") },
                                )
                            }
                        }
                    }
                }
            }
        }

        // feste Leiste: immer sichtbar, auch wenn das Bedienfeld scrollt
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { seed = System.nanoTime() and 0xFFFFFFL }, modifier = Modifier.weight(1f)) { Text("🎲 Neu würfeln", color = Color.White) }
                Button(
                    onClick = {
                        val p = perf ?: return@Button
                        export = ExportUi.Running(0f)
                        val items = st.scenery
                        scope.launch {
                            export = try {
                                val result = ExportManager.export(
                                    context = context,
                                    video = file,
                                    info = if (isPhoto) VideoInfo(info.width, info.height, p.durationMs) else info,
                                    perf = p,
                                    settings = settings,
                                    onProgress = { export = ExportUi.Running(it) },
                                    isPhoto = isPhoto,
                                    scenery = items,
                                )
                                ExportUi.Done(result)
                            } catch (t: Throwable) {
                                ExportUi.Failed(t.message ?: "Unbekannter Fehler")
                            }
                        }
                    },
                    enabled = perf != null && voiceStatus == null && export == null,
                    modifier = Modifier.weight(1f),
                ) { Text("💾 Als Video speichern") }
            }
            Text(
                perf?.sceneNames?.joinToString(" → ") ?: "Szene wird erzeugt …",
                color = Color(0xFF9E9E9E),
                fontSize = 11.sp,
            )
        }
    }

    ExportDialog(export, onClose = { export = null }, onOpenVideos = { export = null; onOpenVideos() })
}

private val SelColor = Color(0xFF80D8FF)

/** Markierungen im Editor (nicht im exportierten Video): Start, Weg, Aktionspunkte, Tür, Auswahlrahmen. */
@Composable
private fun EditMarkers(
    st: EditState,
    sel: Sel?,
    itemBox: (SceneryItem) -> Pair<Float, Float>,
    doorBox: () -> Pair<Float, Float>?,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // drawText braucht Platz unter und neben der Position (sonst Absturz): in die Fläche klemmen
        fun safe(o: Offset) = Offset(o.x.coerceIn(0f, (w - 40f).coerceAtLeast(0f)), o.y.coerceIn(0f, (h - 40f).coerceAtLeast(0f)))
        fun at(p: Pt) = Offset(p.x * w, p.y * h)

        // Auswahlrahmen für Gegenstände und Tür
        for (item in st.scenery) {
            val (bw, bh) = itemBox(item)
            val selected = sel == Sel.Item(item.id)
            val tl = Offset((item.at.x - bw / 2) * w, (item.at.y - bh) * h)
            val sz = Size(bw * w, bh * h)
            if (selected) {
                drawRect(SelColor.copy(alpha = 0.12f), tl, sz)
                drawRect(SelColor, tl, sz, style = Stroke(3f))
            }
            // Fußpunkt
            drawCircle(if (selected) SelColor else Color(0x88FFFFFF), if (selected) 9f else 6f, at(item.at))
        }
        st.door?.let {
            val dh = h * st.doorScale
            val dw = dh * 0.56f
            val c = if (sel == Sel.Door) SelColor else Color(0xCCFF7043)
            drawPath(doorPath(it.x * w, it.y * h, dw, dh, st.doorRound), c, style = Stroke(if (sel == Sel.Door) 7f else 5f))
        }

        val pts = listOfNotNull(st.start) + st.path
        for (i in 0 until pts.size - 1) {
            drawLine(Color(0xCCFFC107), at(pts[i]), at(pts[i + 1]), strokeWidth = 4f)
        }
        st.start?.let {
            drawCircle(Color(0xFF43A047), 18f, at(it))
            drawCircle(if (sel == Sel.Start) SelColor else Color.White, 18f, at(it), style = Stroke(if (sel == Sel.Start) 6f else 3f))
        }
        st.path.forEachIndexed { i, p ->
            val c = at(p)
            drawCircle(Color(0xFFFFC107), 16f, c)
            if (sel == Sel.Path(i)) drawCircle(SelColor, 22f, c, style = Stroke(5f))
            drawText(measurer, "${i + 1}", safe(c - Offset(6f, 12f)), style = TextStyle(fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold))
        }
        // Aktionspunkte: blaue Pins, durch eine Linie in der Reihenfolge verbunden
        val stepPts = st.script.mapIndexedNotNull { i, step -> step.at?.let { Triple(i, step, at(it)) } }
        for (k in 0 until stepPts.size - 1) {
            drawLine(Color(0xAA42A5F5), stepPts[k].third, stepPts[k + 1].third, strokeWidth = 3f)
        }
        stepPts.forEach { (i, step, c) ->
            drawCircle(Color(0xFF1E88E5), 20f, c)
            drawCircle(if (sel == Sel.Step(i)) SelColor else Color.White, 20f, c, style = Stroke(if (sel == Sel.Step(i)) 6f else 2.5f))
            drawText(measurer, "${i + 1}", safe(c - Offset(6f, 12f)), style = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold))
            drawText(measurer, step.action.emoji, safe(c + Offset(-10f, 22f)), style = TextStyle(fontSize = 16.sp))
        }
    }
}
