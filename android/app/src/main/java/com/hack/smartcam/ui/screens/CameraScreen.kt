package com.hack.smartcam.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hack.smartcam.data.Repo
import com.hack.smartcam.data.Vitals
import com.hack.smartcam.games.GameHost
import com.hack.smartcam.media.Mood
import com.hack.smartcam.media.Music
import com.hack.smartcam.ui.Brand
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Chip
import com.hack.smartcam.ui.FactTeller
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.LocalWide
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.PoweredByPresage
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import com.hack.smartcam.ui.glass
import com.hack.smartcam.vitals.LiveVitals
import com.hack.smartcam.vitals.Presage
import com.presagetech.smartspectra.ProcessingStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SCAN_MS = 35_000L // pulse ~12 s, breathing ~10–30 s, HRV ~30 s
private const val GIVE_UP_MS = 60_000L

private enum class Phase { Permission, Scanning, Result, Failed }

private val Night = Color(0xFF0B1B32)
private val Cream = Color(0xFFF5ECF2)

@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val s by Repo.state.collectAsState()
    val demo = s.settings.demoVitals || !Presage.available
    val wide = LocalWide.current

    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }

    var phase by remember { mutableStateOf(if (hasCam) Phase.Scanning else Phase.Permission) }
    var failure by remember { mutableStateOf("") }
    var startedAt by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<Vitals?>(null) }
    var mood by remember { mutableStateOf<Mood?>(null) }
    var alertDismissed by remember { mutableStateOf(false) }
    var musicSheet by remember { mutableStateOf(false) }
    val live = remember { LiveVitals() }

    val sdk = remember { Presage.sdk }
    val status by sdk.processingStatus.observeAsState()
    val metrics by sdk.metrics.observeAsState()
    val validation by sdk.validationStatus.observeAsState()
    val sdkError by sdk.error.observeAsState()

    LaunchedEffect(metrics) { if (!demo) metrics?.let { live.absorb(it) } }

    suspend fun stopSdk() { if (!demo) runCatching { if (sdk.processingStatus.value in setOf(ProcessingStatus.RUNNING, ProcessingStatus.STARTING)) sdk.stop() } }
    suspend fun startSdk() { if (!demo) runCatching { sdk.start() }.onFailure { failure = it.message ?: "Couldn't start the camera"; phase = Phase.Failed } }

    fun beginScan() {
        if (!hasCam) { askCam.launch(Manifest.permission.CAMERA); return }
        live.reset(); result = null; progress = 0f; alertDismissed = false
        phase = Phase.Scanning
        startedAt = System.currentTimeMillis()
        scope.launch { stopSdk(); startSdk() }
    }

    LaunchedEffect(hasCam) { if (hasCam) beginScan() }

    // Scan timer: finish once enough time has passed and we have a pulse.
    LaunchedEffect(phase, startedAt) {
        if (phase != Phase.Scanning) return@LaunchedEffect
        while (true) {
            val e = System.currentTimeMillis() - startedAt
            val scanMs = if (demo) 9_000L else SCAN_MS
            progress = (e.toFloat() / scanMs).coerceAtMost(1f)
            if (demo && e >= scanMs && live.hr == null) live.fakeFill()
            if (e >= scanMs && live.hr != null) {
                val v = live.toVitals()!!.let { if (demo) it.copy(source = "demo") else it }
                Repo.addVitals(v)
                result = v
                if (v.hr > s.settings.hrHigh || v.hr < s.settings.hrLow) Haptics.warning() else Haptics.success()
                phase = Phase.Result // camera keeps running → live pulse in the D bar, preview moves to B
                mood = Music.moodFor(v)
                if (s.settings.autoMusic) scope.launch { Toasts.show(Music.playForMe(ctx, mood!!)) }
                break
            }
            if (e >= GIVE_UP_MS) {
                failure = "Couldn't read a pulse. Face the camera in even light and keep still."
                phase = Phase.Failed
                stopSdk()
                break
            }
            delay(200)
        }
    }

    // Keep heart-rate samples (every 5 s at most) for the 24-hour chart on Home.
    var lastHrLog by remember { mutableLongStateOf(0L) }
    LaunchedEffect(live.hr) {
        val hr = live.hr ?: return@LaunchedEffect
        if (phase != Phase.Permission && phase != Phase.Failed && System.currentTimeMillis() - lastHrLog >= 5000) {
            lastHrLog = System.currentTimeMillis()
            Repo.addHr(hr)
        }
    }

    LaunchedEffect(sdkError) { sdkError?.let { if (phase == Phase.Scanning) { failure = it.message ?: "Presage error"; phase = Phase.Failed } } }

    // Stop the camera when leaving the tab or the app; resume when coming back.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_PAUSE -> scope.launch { stopSdk() }
                Lifecycle.Event.ON_RESUME -> if (hasCam && phase == Phase.Result) scope.launch { startSdk() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            Repo.scope.launch(kotlinx.coroutines.Dispatchers.Main) { runCatching { if (!demo) sdk.stop() } }
        }
    }

    val hrHigh = s.settings.hrHigh
    val hrLow = s.settings.hrLow
    val shownHr = live.hr ?: result?.hr

    Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 24.dp else 16.dp).padding(bottom = 6.dp)) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth()
                .glass(RoundedCornerShape(26.dp), fill = Night),
        ) {
            val inB = phase == Phase.Result
            val bW = (maxWidth * 0.24f).coerceAtMost(112.dp)
            val bH = bW * 4f / 3f
            val w by animateDpAsState(if (inB) bW else maxWidth, tween(450), label = "w")
            val h by animateDpAsState(if (inB) bH else maxHeight, tween(450), label = "h")
            val x by animateDpAsState(if (inB) maxWidth - bW - 12.dp else 0.dp, tween(450), label = "x")
            val y by animateDpAsState(if (inB) 12.dp else 0.dp, tween(450), label = "y")

            // A: the game sits under the small camera window.
            if (phase == Phase.Result && result != null) {
                GameHost(result!!.stress, Modifier.fillMaxSize().padding(bottom = 66.dp))
            }

            if (hasCam && phase != Phase.Failed) {
                Box(
                    Modifier.offset(x, y).size(w, h).clip(RoundedCornerShape(if (inB) 14.dp else 0.dp))
                        .then(if (inB) Modifier.border(2.dp, Cream, RoundedCornerShape(14.dp)) else Modifier),
                ) {
                    CameraPreview(demo = demo, onPreview = { pv -> if (!demo) sdk.config.previewSurfaceProvider = pv.surfaceProvider })
                }
            }

            when (phase) {
                Phase.Permission -> CenterPrompt(
                    "Allow camera access",
                    "SmartCam reads tiny colour changes in your face to measure pulse, breathing and stress. Video stays on your phone.",
                    "Allow camera",
                ) { askCam.launch(Manifest.permission.CAMERA) }
                Phase.Failed -> CenterPrompt("No reading this time", failure, "Try again") { beginScan() }
                Phase.Scanning -> ScanOverlay(progress, if (demo) 9_000L else SCAN_MS, live, validation?.code?.name, demo)
                Phase.Result -> {}
            }

            // C: heart-rate warning (live while the camera keeps running)
            val out = shownHr?.let { if (it > hrHigh) "Heart rate looks high ($it bpm). Sit and breathe slowly." else if (it < hrLow) "Heart rate looks low ($it bpm)." else null }
            if (phase == Phase.Result && out != null && !alertDismissed) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp, start = 16.dp, end = 16.dp)
                        .glass(CircleShape, fill = Color(0xEEB8325F))
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Ic.alert, null, Modifier.size(18.dp), tint = Color.White)
                    Text(out, style = T.bodyStrong.copy(fontSize = 14.sp), color = Color.White, modifier = Modifier.weight(1f, fill = false))
                    Box(Modifier.size(30.dp).clip(CircleShape).clickable { alertDismissed = true }, contentAlignment = Alignment.Center) {
                        Icon(Ic.close, "Dismiss", Modifier.size(16.dp), tint = Color.White)
                    }
                }
            }

            // D: vitals + mood music
            if (phase == Phase.Result) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                        .glass(CircleShape, fill = Color.White.copy(alpha = 0.82f))
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VitalText("${shownHr ?: "—"}", "bpm")
                    VitalText("${live.br ?: result?.br ?: "—"}", "br/min")
                    Spacer(Modifier.weight(1f))
                    MusicChip(onClick = { musicSheet = true }, modifier = Modifier.weight(2f, fill = false))
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PoweredByPresage()
                Text("Wellness estimates, not a medical diagnosis.", style = T.small, color = P.ink2)
                if (demo) Chip("Practice mode — readings are simulated", dashed = true)
                else if (status == ProcessingStatus.RUNNING && phase == Phase.Result) Text("Live · Presage", style = T.mono.copy(fontSize = 11.sp), color = P.good)
            }
            if (phase == Phase.Result || phase == Phase.Failed) ScButton("Scan again", { beginScan() }, icon = Ic.refresh, style = Btn.Ghost, small = true)
        }
    }

    if (musicSheet) MusicSheet(suggested = mood, onDismiss = { musicSheet = false })
}

@Composable
private fun VitalText(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = T.big.copy(fontSize = 19.sp), color = Brand.midnight)
        Text(" $unit", style = T.mono.copy(fontSize = 12.sp), color = Brand.midnight, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** Live camera preview. Presage drives it during real scans; plain CameraX in demo mode. */
@Composable
private fun CameraPreview(demo: Boolean, onPreview: (PreviewView) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    AndroidView(
        factory = { c ->
            PreviewView(c).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE // TextureView: can be clipped and resized
                if (!demo) onPreview(this) else {
                    val pv = this
                    val future = ProcessCameraProvider.getInstance(c)
                    future.addListener({
                        runCatching {
                            val p = future.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                            p.unbindAll()
                            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
                            provider = p
                        }
                    }, ContextCompat.getMainExecutor(c))
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }
}

@Composable
private fun ScanOverlay(progress: Float, totalMs: Long, live: LiveVitals, validation: String?, demo: Boolean) {
    Box(Modifier.fillMaxSize()) {
        OvalProgress(progress, totalMs, Modifier.align(Alignment.Center).offset(y = (-46).dp).width(214.dp).height(280.dp))
        Row(
            Modifier.padding(14.dp).clip(CircleShape).background(Night.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(P.pop))
            Text(if (demo) "DEMO SCAN" else "MEASURING", style = T.eyebrow, color = Cream)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                .glass(RoundedCornerShape(20.dp), fill = Night.copy(alpha = 0.55f), elevated = false).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hint = validation?.takeIf { it != "OK" && it != "VALID" && !it.startsWith("UNRECOGNIZED") }
                ?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() }
            Text(hint ?: "Hold still, face in the oval", style = T.small, color = Cream.copy(alpha = 0.8f))
            FactTeller(ink = Cream, ink2 = Cream.copy(alpha = 0.65f))
            Text(
                "Pulse ${live.hr?.let { "$it bpm" } ?: "…"}  ·  Breathing ${live.br?.let { "$it/min" } ?: "…"}  ·  HRV ${live.rmssd?.let { "${it.toInt()} ms" } ?: "…"}",
                style = T.mono.copy(fontSize = 11.sp), color = Cream.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * The face oval doubles as the timer: a soft gradient line glides around it as the scan runs,
 * with a glowing bead at its head and the seconds left underneath.
 */
@Composable
private fun OvalProgress(progress: Float, totalMs: Long, modifier: Modifier) {
    val smooth by animateFloatAsState(progress, tween(260, easing = LinearEasing), label = "scan")
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val left = kotlin.math.ceil((1 - progress) * totalMs / 1000f).toInt()
    val colors = listOf(Brand.sky, Brand.mauve, Brand.blush, Brand.sky)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 8.dp.toPx()
            val oval = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
            // Start at the top of the oval and go clockwise.
            val full = Path().apply {
                arcTo(oval, -90f, 180f, true)
                arcTo(oval, 90f, 180f, false)
            }
            val measure = PathMeasure().apply { setPath(full, false) }
            // A soft dark halo keeps the ring readable over bright backgrounds.
            drawPath(full, Night.copy(alpha = 0.28f), style = Stroke(11.dp.toPx()))
            drawPath(full, Cream.copy(alpha = 0.22f), style = Stroke(5.dp.toPx()))
            if (smooth > 0f) {
                val seg = Path()
                measure.getSegment(0f, measure.length * smooth, seg, true)
                val brush = Brush.sweepGradient(colors, center)
                drawPath(seg, brush, alpha = 0.25f * glow, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
                drawPath(seg, brush, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                val head = measure.getPosition(measure.length * smooth)
                drawCircle(Color.White.copy(alpha = 0.35f * glow), 11.dp.toPx(), head)
                drawCircle(Color.White, 4.5.dp.toPx(), head)
            }
        }
        Text(
            if (left > 0) "$left s" else "almost…",
            style = T.mono.copy(fontSize = 12.sp), color = Cream.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 22.dp),
        )
    }
}

@Composable
private fun CenterPrompt(title: String, text: String, cta: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Ic.camera, null, Modifier.size(44.dp), tint = P.pop)
        Spacer(Modifier.height(12.dp))
        Text(title, style = T.h1, color = Cream, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = T.body, color = Cream.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        ScButton(cta, onClick, style = Btn.Pop)
    }
}
