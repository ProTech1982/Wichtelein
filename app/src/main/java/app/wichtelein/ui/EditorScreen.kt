package app.wichtelein.ui

import app.wichtelein.camera.*
import app.wichtelein.anim.*
import app.wichtelein.scene.*
import app.wichtelein.speech.*
import app.wichtelein.videoexport.*
import app.wichtelein.settings.*
import app.wichtelein.sound.SoundPlayer
import app.wichtelein.sound.allowsFx

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
enum class Tool { START, PATH, ACTION, DOOR, OBJECTS, MUSIC, SCENE }

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
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val sceneryAssets = remember(settings.sceneryStyles) { SceneryAssets.load(context, settings.sceneryStyles) }
    val speaker = remember { Speaker(context) }
    val fxPlayer = remember { SoundPlayer() }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    SideEffect {
        speaker.enabled = settings.speak
        fxPlayer.enabled = settings.soundFx || settings.hum
        speaker.effect = settings.effect
        speaker.rate = settings.rate
        speaker.mood = settings.mood
        speaker.useElfVoice = settings.elfVoice
        speaker.voiceId = settings.voiceFor(if (settings.elfin) 1 else 0)
    }
    val latestSettings by rememberUpdatedState(settings)

    // ---- Zustand des Editors mit Rückgängig/Wiederholen
    var st by remember { mutableStateOf(EditState()) }
    val history = remember { EditHistory() }
    var histTick by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Sel?>(null) }
    var tool by remember { mutableStateOf(Tool.START) }
    var action by remember { mutableStateOf(ElfAction.SWEEP) }
    // welche Figur eine neue Aktion macht (nur wichtig, wenn Wichtel und Elfin zusammen auftreten)
    var who by remember { mutableIntStateOf(0) }
    val whoNow = if (settings.both) who else 0
    var objType by remember { mutableStateOf(SceneryType.TREE_CLASSIC) }
    var sliderBase by remember { mutableStateOf<EditState?>(null) }
    var fine by remember { mutableStateOf(false) }

    // Hintergrundmusik: eingebaute Melodien oder eigene Datei (MP3 …); läuft in der Vorschau als Schleife, im Video mit Ausblenden am Ende
    val musicPlayer = remember { app.wichtelein.sound.MusicPlayer() }
    DisposableEffect(Unit) { onDispose { musicPlayer.stop() } }
    var musicVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.music, musicVersion) {
        if (settings.music == 0) {
            musicPlayer.stop()
        } else {
            val pcm = withContext(Dispatchers.Default) { app.wichtelein.sound.MusicLoader.load(context, latestSettings, 90_000L) }
            if (pcm != null) musicPlayer.play(pcm, latestSettings.musicVolume) else musicPlayer.stop()
        }
    }
    LaunchedEffect(settings.musicVolume) { musicPlayer.setVolume(settings.musicVolume) }
    val musicPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                var name = "Eigene Musik"
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
                        }
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            app.wichtelein.sound.MusicLoader.customFile(context).outputStream().use { input.copyTo(it) }
                        } != null
                    } catch (_: Exception) {
                        false
                    }
                }
                if (ok) {
                    musicVersion++
                    onSettings(latestSettings.copy(music = app.wichtelein.sound.MusicChoice.CUSTOM, musicName = name))
                }
            }
        }
    }

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
    // Der Wichtel stellt Gegenstände zuerst auf (ca. 6 s je Gegenstand), das verlängert den Film; mit Aktionspunkten endet er
    // automatisch kurz nach der letzten Aktion (bis zu 4 Minuten), und ein kurzes Video wird dabei wiederholt.
    val buildExtra = 6_000L * st.scenery.size
    val duration = when {
        scriptMode -> 240_000L
        isPhoto -> (photoSeconds * 1000f).toLong() + buildExtra
        else -> min(info.durationMs, 90_000L).coerceAtLeast(3_000L) + buildExtra
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
            doorRot = st.doorRot,
            script = st.script,
            scenery = st.scenery,
            snowFromStart = st.snow,
            stopAfterScript = st.script.isNotEmpty(),
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
    LaunchedEffect(perf, settings.voiceId, settings.elfinVoiceId, settings.cast, settings.effect, settings.rate, settings.mood, settings.elfVoice, settings.speak) {
        val p = perf ?: return@LaunchedEffect
        clips = emptyList()
        if (!settings.speak || !settings.elfVoice) {
            voiceStatus = null
            return@LaunchedEffect
        }
        voiceStatus = "Stimme wird vorbereitet …"
        val res = withContext(Dispatchers.Default) {
            SpeechRender.renderAll(context, settings, p.speech) { n -> voiceStatus = "Stimme wird erzeugt ($n/${p.speech.size}) …" }
        }
        if (res.clips.all { it == null } && p.speech.isNotEmpty()) {
            voiceStatus = "Wichtelstimme nicht verfügbar – Systemstimme oder nur Text"
            return@LaunchedEffect
        }
        clips = res.clips
        voiceStatus = if (res.missing) "Die Stimme einer Figur ist nicht geladen – bitte unter Einstellungen laden" else null
    }

    // 3) Wiedergabeposition verfolgen (Video oder Foto-Uhr) und Sprache und Geräusche zum richtigen Zeitpunkt abspielen
    LaunchedEffect(perf, clips) {
        var last = -1L
        var t0 = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (t0 < 0) t0 = now
            // eigene Uhr für die Aufführung (sie kann länger sein als das Video, das sich dann wiederholt)
            val d = maxOf(perf?.durationMs ?: 1000L, if (isPhoto) 0L else info.durationMs).coerceAtLeast(1000L)
            val pos = ((now - t0) / 1_000_000L) % d
            posMs = pos
            val p = perf ?: continue
            if (pos < last - 400) last = -1L
            for (fe in p.sounds) {
                if (fe.timeMs > last && fe.timeMs <= pos && latestSettings.allowsFx(fe.fx)) fxPlayer.play(fe.fx, fe.gain)
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

    /** Jedes Werkzeug fasst nur seine eigenen Dinge an: so wird beim Weg-Setzen nichts anderes versehentlich ausgewählt oder verschoben. */
    fun kindsFor(t: Tool): Set<SelKind> = when (t) {
        Tool.START -> setOf(SelKind.START)
        Tool.PATH -> setOf(SelKind.PATH)
        Tool.ACTION -> setOf(SelKind.STEP)
        Tool.DOOR -> setOf(SelKind.DOOR)
        Tool.OBJECTS -> setOf(SelKind.ITEM)
        Tool.MUSIC -> emptySet()
        Tool.SCENE -> emptySet()
    }

    fun selectTool(t: Tool) {
        tool = t
        sel = sel?.takeIf { it.kind() in kindsFor(t) }
    }

    fun hitAt(px: Float, py: Float, w: Float, h: Float): Sel? =
        EditOps.hit(st, px, py, hitPx / w, hitPx / h, ::itemBox, doorBox(), kindsFor(tool))

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
                val n = EditOps.addStep(st, action, p, whoNow)
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
            Tool.SCENE, Tool.MUSIC -> sel = null
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
            // Größe des Wichtels: immer sichtbar, in jedem Werkzeug
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Größe", color = Color.White, fontSize = 13.sp)
                Slider(
                    value = settings.sizeScale,
                    onValueChange = { onSettings(settings.copy(sizeScale = it)) },
                    valueRange = 0.12f..0.6f,
                    modifier = Modifier.weight(1f),
                )
                Text("${(settings.sizeScale * 100).toInt()} %", color = Hint, fontSize = 12.sp)
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                FilterChip(selected = tool == Tool.START, onClick = { selectTool(Tool.START) }, label = { Text("📍 Start") })
                FilterChip(selected = tool == Tool.PATH, onClick = { selectTool(Tool.PATH) }, label = { Text("🛤 Weg (${st.path.size})") })
                FilterChip(selected = tool == Tool.ACTION, onClick = { selectTool(Tool.ACTION) }, label = { Text("🎯 Aktionen (${st.script.size})") })
                FilterChip(selected = tool == Tool.DOOR, onClick = { selectTool(Tool.DOOR) }, label = { Text(if (st.door == null) "🚪 Tür" else "🚪 Tür ✓") })
                FilterChip(selected = tool == Tool.OBJECTS, onClick = { selectTool(Tool.OBJECTS) }, label = { Text("🎄 Objekte (${st.scenery.size})") })
                FilterChip(selected = tool == Tool.MUSIC, onClick = { selectTool(Tool.MUSIC) }, label = { Text("🎵 Musik") })
                FilterChip(selected = tool == Tool.SCENE, onClick = { selectTool(Tool.SCENE) }, label = { Text("🎬 Szene") })
                FilterChip(selected = st.snow, onClick = { commit(st.copy(snow = !st.snow)) }, label = { Text(if (st.snow) "❄ Schnee: an" else "❄ Schnee: aus") })
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
                        // Drehen um den Fußpunkt
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Drehen ${item.rot.toInt()}°", color = Color.White, fontSize = 13.sp)
                            OutlinedButton(onClick = { commit(EditOps.rotateItem(st, s.id, item.rot - 5f)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("⟲", color = Color.White) }
                            Slider(
                                value = item.rot,
                                onValueChange = { slide(EditOps.rotateItem(st, s.id, it)) },
                                onValueChangeFinished = { slideDone() },
                                valueRange = -180f..180f,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { commit(EditOps.rotateItem(st, s.id, item.rot + 5f)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("⟳", color = Color.White) }
                            TextButton(onClick = { commit(EditOps.rotateItem(st, s.id, 0f)) }, enabled = item.rot != 0f) { Text("0°") }
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
                    if (settings.both) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Wer macht es?", color = Color.White, fontSize = 13.sp)
                            FilterChip(selected = who == 0, onClick = { who = 0 }, label = { Text("🧙 ${settings.elfName.ifBlank { "Wichtel" }}") })
                            FilterChip(selected = who == 1, onClick = { who = 1 }, label = { Text("🧝‍♀️ ${settings.elfinName.ifBlank { "Elfin" }}") })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElfAction.values().forEach { a ->
                            FilterChip(
                                selected = action == a,
                                onClick = {
                                    action = a
                                    if (!a.needsPoint && (!a.needsDoor || st.door != null) && (!a.needsBed || st.scenery.any { it.type.isBed })) {
                                        val n = EditOps.addStep(st, a, null, whoNow)
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
                    if (action.needsBed && st.scenery.none { it.type.isBed }) {
                        Text("Stelle zuerst ein Bett auf (Chip „🎄 Objekte“), dann lässt sich diese Aktion hinzufügen.", color = Warn, fontSize = 12.sp)
                    }
                    if (action.needsDoor && st.door == null) {
                        Text("Setze zuerst die Tür (Chip „🚪 Tür“), dann lässt sich diese Aktion hinzufügen.", color = Warn, fontSize = 12.sp)
                    }
                    // Sprechblase der ausgewählten Aktion
                    val selStep = (sel as? Sel.Step)?.index?.takeIf { it in st.script.indices }
                    if (selStep != null) {
                        val own = st.script[selStep].bubble
                        Text("Sprechblase dieser Aktion${if (own == null) " (Standard)" else ""}", color = Color(0xFF80D8FF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        BubbleControls(
                            spec = own ?: settings.bubble,
                            onChange = { commit(EditOps.setBubble(st, selStep, it)) },
                            light = true,
                            onReset = if (own != null) ({ commit(EditOps.setBubble(st, selStep, null)) }) else null,
                        )
                    }
                    if (st.script.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            st.script.forEachIndexed { i, step ->
                                FilterChip(
                                    selected = sel == Sel.Step(i),
                                    onClick = { sel = if (step.at != null) Sel.Step(i) else null; if (step.at == null) commit(EditOps.remove(st, Sel.Step(i))) },
                                    label = { Text("${i + 1}. ${if (settings.both) (if (step.who == 1) "🧝‍♀️ " else "🧙 ") else ""}${step.action.emoji} ${step.action.label}") },
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Drehen ${st.doorRot.toInt()}°", color = Color.White, fontSize = 13.sp)
                            OutlinedButton(onClick = { commit(st.copy(doorRot = EditOps.wrapDeg(st.doorRot - 5f))) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("⟲", color = Color.White) }
                            Slider(
                                value = st.doorRot,
                                onValueChange = { slide(st.copy(doorRot = it)) },
                                onValueChangeFinished = { slideDone() },
                                valueRange = -180f..180f,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { commit(st.copy(doorRot = EditOps.wrapDeg(st.doorRot + 5f))) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text("⟳", color = Color.White) }
                            TextButton(onClick = { commit(st.copy(doorRot = 0f)) }, enabled = st.doorRot != 0f) { Text("0°") }
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
                    for (g in SceneryGroup.values()) {
                        Text(g.label, color = Hint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SceneryType.values().filter { it.group == g }.forEach { t ->
                                FilterChip(selected = objType == t, onClick = { objType = t }, label = { Text("${t.emoji} ${t.label}") })
                            }
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
                Tool.MUSIC -> {
                    Text(
                        "Hintergrundmusik für das Video: eine der kleinen Melodien oder eine eigene Datei (z. B. MP3). Sie läuft hier in der Vorschau, " +
                            "wiederholt sich im Video und wird am Ende ausgeblendet.",
                        color = Hint,
                        fontSize = 12.sp,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = settings.music == 0, onClick = { onSettings(settings.copy(music = 0)) }, label = { Text("🔇 Aus") })
                        app.wichtelein.sound.MusicTrack.values().forEachIndexed { i, t ->
                            FilterChip(selected = settings.music == i + 1, onClick = { onSettings(settings.copy(music = i + 1)) }, label = { Text("🎶 ${t.label}") })
                        }
                        FilterChip(
                            selected = settings.music == app.wichtelein.sound.MusicChoice.CUSTOM,
                            onClick = { musicPicker.launch("audio/*") },
                            label = { Text(if (settings.music == app.wichtelein.sound.MusicChoice.CUSTOM && settings.musicName.isNotBlank()) "📂 ${settings.musicName}" else "📂 Eigene Datei …") },
                        )
                    }
                    if (settings.music != 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Lautstärke ${(settings.musicVolume * 100).toInt()} %", color = Color.White, fontSize = 13.sp)
                            Slider(value = settings.musicVolume, onValueChange = { onSettings(settings.copy(musicVolume = it)) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Tool.SCENE -> {
                    if (isPhoto) {
                        if (scriptMode) {
                            Text(
                                "Länge: automatisch, etwa ${(perf?.durationMs ?: 0L) / 1000L} s (endet kurz nach der letzten Aktion; ein kürzeres Video wiederholt sich)",
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
