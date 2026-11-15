package app.wichtelein.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.wichtelein.anim.ElfColors
import app.wichtelein.anim.PerfRequest
import app.wichtelein.anim.Performance
import app.wichtelein.anim.Pt
import app.wichtelein.anim.Recorder
import app.wichtelein.anim.drawWichtelFrame
import app.wichtelein.anim.rememberElfFigure
import app.wichtelein.scene.Category
import app.wichtelein.settings.Settings
import app.wichtelein.speech.Speaker
import app.wichtelein.speech.SpeechRender
import app.wichtelein.speech.VoiceAudio
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val METERS_PER_UNIT = 0.9f
private const val ELF_METERS_PER_SIZE = 0.8f

/**
 * Echtes AR: Das Kamerabild kommt von ARCore, der Boden wird erkannt, ein Antippen setzt den Wichtel fest in den Raum.
 * Er führt dort seine Szenen mit Sprache aus und bleibt beim Bewegen des Handys an seinem Platz.
 * Hinweis: Ein Video-Export aus dem AR-Modus gibt es noch nicht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArScreen(settings: Settings, onSettings: (Settings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var availability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var installRequested by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        var a = ArCoreApk.getInstance().checkAvailability(context)
        while (a.isTransient) {
            delay(250)
            a = ArCoreApk.getInstance().checkAvailability(context)
        }
        availability = a
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            activity == null -> Message("AR ist hier nicht verfügbar.", onBack)
            !granted -> Message("Für AR braucht die App die Kamera.", onBack) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Kamera erlauben") }
            }
            availability == null -> Message("Prüfe, ob dein Handy AR kann …", onBack)
            availability == ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArContent(activity, settings, onSettings, onBack)
            availability!!.isSupported -> Message(
                "Für AR muss „Google Play-Dienste für AR“ installiert oder aktualisiert werden.",
                onBack,
            ) {
                Button(onClick = {
                    try {
                        val r = ArCoreApk.getInstance().requestInstall(activity, !installRequested)
                        installRequested = true
                        if (r == ArCoreApk.InstallStatus.INSTALLED) availability = ArCoreApk.Availability.SUPPORTED_INSTALLED
                    } catch (_: Exception) {
                    }
                }) { Text("Installieren") }
                OutlinedButton(onClick = { availability = ArCoreApk.getInstance().checkAvailability(context) }) { Text("Erneut prüfen") }
            }
            else -> Message("Dieses Handy unterstützt kein AR (ARCore). Der normale Wichtel-Editor funktioniert trotzdem.", onBack)
        }
    }
}

@Composable
private fun Message(text: String, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AR-Wichtel", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp))
        extra()
        TextButton(onClick = onBack) { Text("← Zurück", color = Color.White) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArContent(activity: Activity, settings: Settings, onSettings: (Settings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(settings)
    val sprites = rememberElfFigure(settings)
    val measurer = rememberTextMeasurer()
    val speaker = remember { Speaker(context) }
    val fxPlayer = remember { app.wichtelein.sound.SoundPlayer() }
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

    var snapshot by remember { mutableStateOf<ArSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val renderer = remember { ArRenderer(activity) { snapshot = it } }
    var glView by remember { mutableStateOf<GLSurfaceView?>(null) }

    // ARCore-Sitzung anlegen, steuern und wieder schließen
    var session by remember { mutableStateOf<Session?>(null) }
    DisposableEffect(Unit) {
        try {
            val s = Session(activity)
            s.configure(
                Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                    focusMode = Config.FocusMode.AUTO
                },
            )
            session = s
            renderer.session = s
        } catch (t: Throwable) {
            error = "AR konnte nicht gestartet werden: ${t.message ?: t.javaClass.simpleName}"
        }
        onDispose {
            renderer.session = null
            session?.close()
            session = null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        try {
            session?.resume()
            glView?.onResume()
        } catch (e: CameraNotAvailableException) {
            error = "Die Kamera wird gerade von einer anderen App benutzt."
        } catch (t: Throwable) {
            error = "AR konnte nicht fortgesetzt werden: ${t.message}"
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        glView?.onPause()
        session?.pause()
    }

    // Aufführung wie im Editor: aufnehmen, Stimme erzeugen, abspielen
    var category by remember { mutableStateOf<Category?>(null) }
    var seed by remember { mutableLongStateOf(System.nanoTime() and 0xFFFFFFL) }
    var perf by remember { mutableStateOf<Performance?>(null) }
    var clips by remember { mutableStateOf<List<VoiceAudio?>>(emptyList()) }
    var voiceStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings, category, seed) {
        val req = PerfRequest(
            start = Pt(0.5f, 0.78f), sizeScale = settings.sizeScale, category = category, seed = seed, durationMs = 40_000,
            enabledScenes = settings.enabledScenes()?.filter { id -> app.wichtelein.scene.Scenes.find(id)?.needsDoor != true }?.toSet()
                ?: app.wichtelein.scene.Scenes.all.filter { !it.needsDoor }.map { it.id }.toSet(),
            settings = settings,
            confineToPoints = false,
        )
        perf = withContext(Dispatchers.Default) { Recorder.record(req) }
    }
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
            voiceStatus = null
            return@LaunchedEffect
        }
        val out = ArrayList<VoiceAudio?>()
        for (ev in p.speech) out.add(withContext(Dispatchers.Default) { SpeechRender.render(settings, ev) })
        clips = out
        voiceStatus = null
    }

    // Zeit seit dem Platzieren; beim Neuwürfeln oder neuen Platz startet die Aufführung von vorn
    var startNanos by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var lastPlacement by remember { mutableLongStateOf(0L) }
    LaunchedEffect(perf, clips) {
        var last = -1L
        var seenPlacement = -1
        var restartAt = 0L
        while (true) {
            val now = withFrameNanos { it }
            val snap = snapshot
            val p = perf
            if (snap == null || p == null || snap.anchor == null) {
                elapsedMs = -1
                continue
            }
            if (snap.placement != seenPlacement) {
                seenPlacement = snap.placement
                restartAt = now
                last = -1L
            }
            val dur = p.durationMs.coerceAtLeast(1000L)
            val ms = (now - restartAt) / 1_000_000L
            val inLoop = ms % dur
            if (inLoop < last - 400) last = -1L
            for ((i, ev) in p.speech.withIndex()) {
                if (ev.timeMs > last && ev.timeMs <= inLoop) {
                    val clip = clips.getOrNull(i)
                    val s = latest
                    if (clip != null) speaker.playClip(clip) else if (s.speak && !s.elfVoice) speaker.speak(ev.text, ev.mood)
                }
            }
            for (fe in p.sounds) {
                if (fe.timeMs > last && fe.timeMs <= inLoop) fxPlayer.play(fe.fx, fe.gain)
            }
            last = inLoop
            elapsedMs = inLoop
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Zurück", color = Color.White) }
            Text("AR-Wichtel", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        preserveEGLContextOnPause = true
                        setEGLContextClientVersion(2)
                        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        glView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val snap = snapshot
            val p = perf
            val frame = if (p != null && elapsedMs >= 0) p.frameAt(elapsedMs) else null
            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { o -> renderer.tap(o.x, o.y) }
                },
            ) {
                if (snap != null && snap.anchor != null && frame != null) {
                    val proj = ArProjector(snap, 0.5f, 0.78f, METERS_PER_UNIT, latest.sizeScale * ELF_METERS_PER_SIZE)
                    drawWichtelFrame(frame, sprites, latest.sizeScale, measurer, proj)
                }
            }
            val hint = when {
                error != null -> error
                snapshot == null -> "Kamera startet …"
                snapshot?.tracking != true -> "Bewege das Handy langsam, damit die Umgebung erkannt wird."
                snapshot?.planeCount == 0 -> "Suche den Boden … richte die Kamera auf eine freie Fläche am Boden."
                snapshot?.anchor == null -> "Boden gefunden. Tippe auf den Boden, wo der Wichtel erscheinen soll."
                voiceStatus != null -> voiceStatus
                else -> null
            }
            hint?.let {
                Text(
                    it,
                    color = Color(0xFFFFE08A),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("🎲 Zufall") })
                Category.values().forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text("${c.emoji} ${c.label}") })
                }
            }
            Text("Größe", color = Color.White, fontSize = 12.sp)
            Slider(value = settings.sizeScale, onValueChange = { onSettings(settings.copy(sizeScale = it)) }, valueRange = 0.12f..0.6f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { seed = System.nanoTime() and 0xFFFFFFL }, modifier = Modifier.weight(1f)) { Text("🎲 Neu würfeln", color = Color.White) }
                OutlinedButton(onClick = { renderer.clearAnchor() }, modifier = Modifier.weight(1f)) { Text("📍 Neuer Platz", color = Color.White) }
            }
        }
    }
}
