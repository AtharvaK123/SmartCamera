package com.hack.smartcam.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.hack.smartcam.data.Food
import com.hack.smartcam.data.Health
import com.hack.smartcam.data.Repo
import com.hack.smartcam.food.AsyncImage
import com.hack.smartcam.food.BarcodeReader
import com.hack.smartcam.food.Images
import com.hack.smartcam.net.FoodFacts
import com.hack.smartcam.net.Gemini
import com.hack.smartcam.net.num
import com.hack.smartcam.net.str
import com.hack.smartcam.ui.BigNumber
import com.hack.smartcam.ui.Brand
import com.hack.smartcam.ui.Btn
import com.hack.smartcam.ui.Chip
import com.hack.smartcam.ui.Eyebrow
import com.hack.smartcam.ui.FactTeller
import com.hack.smartcam.ui.Fmt
import com.hack.smartcam.ui.GeminiLogo
import com.hack.smartcam.ui.Haptics
import com.hack.smartcam.ui.Ic
import com.hack.smartcam.ui.Level
import com.hack.smartcam.ui.LocalWide
import com.hack.smartcam.ui.Notice
import com.hack.smartcam.ui.P
import com.hack.smartcam.ui.ScButton
import com.hack.smartcam.ui.ScCard
import com.hack.smartcam.ui.ScField
import com.hack.smartcam.ui.ScSheet
import com.hack.smartcam.ui.T
import com.hack.smartcam.ui.Toasts
import com.hack.smartcam.ui.glass
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject


private sealed interface AddFlow {
    data object Choose : AddFlow
    data object Scan : AddFlow
    data class Busy(val text: String) : AddFlow
    data class Review(val food: Food, val note: String? = null) : AddFlow
}

@Composable
fun FoodScreen() {
    val s by Repo.state.collectAsState()
    val wide = LocalWide.current
    var flow by remember { mutableStateOf<AddFlow?>(null) }
    var detail by remember { mutableStateOf<Food?>(null) }
    val foods = s.foods.sortedByDescending { it.t }
    val today = Fmt.todaysFoods(s)
    val score = FoodFacts.dayScore(today)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 132.dp else 100.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = if (wide) 24.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Eyebrow("Today's score")
                    if (score != null) BigNumber("$score", "/ 100") else Text("—", style = T.big, color = P.ink3)
                }
                if (score != null) Fmt.foodLevel(score).let { (l, w) -> Chip(w, l) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Eyebrow("Logged")
                    Text("${today.size} today", style = T.mono, color = P.ink)
                }
            }
        }
        item {
            Tile(
                face = { Box(Modifier.fillMaxSize().background(P.accent.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) { Icon(Ic.plus, null, Modifier.size(38.dp), tint = P.accentInk) } },
                name = "Add food", meta = "barcode / photo",
            ) { flow = AddFlow.Choose }
        }
        items(foods, key = { it.id }) { f ->
            Tile(
                face = { FoodFace(f) },
                name = f.name,
                meta = "${f.kcal?.roundToInt()?.let { "$it kcal" } ?: "—"} · ${Fmt.clock(f.t)}",
                badge = "${FoodFacts.score(f)}",
            ) { detail = f }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (foods.isEmpty()) "Tap + to log your first meal. Barcodes use Open Food Facts; photos are read by Gemini."
                else "Barcode items show values per 100 g. Everything you log also appears on the SmartCam website.",
                style = T.small, color = P.ink2, modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }

    flow?.let { f -> AddFoodFlow(f, onChange = { flow = it }) }
    detail?.let { DetailSheet(it) { detail = null } }
}

@Composable
private fun Tile(face: @Composable () -> Unit, name: String, meta: String, badge: String? = null, onClick: () -> Unit) {
    val p = P
    Column(Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).glass(RoundedCornerShape(18.dp))) {
            face()
            if (badge != null) Text(
                badge, style = T.mono.copy(fontSize = 10.sp), color = Brand.midnight,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).glass(RoundedCornerShape(99.dp), fill = Color.White.copy(alpha = 0.8f), elevated = false)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Text(name, style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, style = T.mono.copy(fontSize = 11.sp), color = P.ink2, maxLines = 1)
    }
}

@Composable
private fun FoodFace(f: Food) {
    if (f.image != null) {
        Box(Modifier.fillMaxSize()) {
            GeneratedFace(f.name)
            AsyncImage(f.image, Modifier.fillMaxSize())
        }
    } else GeneratedFace(f.name)
}

@Composable
private fun GeneratedFace(name: String) {
    val color = Color(listOf(0xFFE5C9D7, 0xFFC48CB3, 0xFF83A6CE, 0xFFB9CBE3, 0xFFD9B8CB, 0xFFA9C1DE)[(name.hashCode() and 0x7fffffff) % 6])
    val word = name.split(' ', '—', '-').firstOrNull { it.length > 2 } ?: name
    Box(Modifier.fillMaxSize().background(color).padding(6.dp), contentAlignment = Alignment.Center) {
        Text(word.take(8), style = T.h2.copy(fontSize = 19.sp), color = Brand.midnight, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/* ---------------- adding food ---------------- */

@Composable
private fun AddFoodFlow(flow: AddFlow, onChange: (AddFlow?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoFile = remember { File(ctx.cacheDir, "photos").apply { mkdirs() }.let { File(it, "capture.jpg") } }
    val photoUri = remember { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", photoFile) }

    fun handlePhoto(uri: Uri) {
        onChange(AddFlow.Busy("Gemini is looking at your food…"))
        scope.launch {
            val id = Repo.newId("f")
            val big = withContext(Dispatchers.IO) { Images.decode(ctx, uri, 768) }
            if (big == null) { Toasts.show("Couldn't open that photo", true); onChange(null); return@launch }
            val thumb = withContext(Dispatchers.IO) { Images.saveThumb(ctx, Images.decode(ctx, uri, 360) ?: big, id) }
            val base = Food(id = id, t = System.currentTimeMillis(), name = "", source = "photo", image = thumb)
            if (!Gemini.available) { onChange(AddFlow.Review(base, "Add the nutrition yourself.")); return@launch }
            val reviewed = runCatching {
                val r = Gemini.generateJson(
                    "Identify the food in this photo and estimate nutrition for the portion shown. Return JSON: " +
                        "{\"name\": string, \"kcal\": number, \"protein\": number, \"carbs\": number, \"sugar\": number, \"fat\": number, " +
                        "\"satFat\": number, \"fiber\": number, \"salt\": number, \"confidence\": \"low\"|\"medium\"|\"high\"}. Grams for everything except kcal. " +
                        "If it isn't food, return {\"name\": \"\"}.",
                    imageJpegBase64 = withContext(Dispatchers.Default) { Images.jpegBase64(big) },
                ).jsonObject
                val note = if (r.str("name").isNullOrBlank()) "That doesn't look like food — fill it in yourself."
                else if (r.str("confidence") == "low") "Gemini isn't sure about this one — check the numbers." else null
                AddFlow.Review(
                    base.copy(
                        name = r.str("name").orEmpty(), kcal = r.num("kcal"), protein = r.num("protein"), carbs = r.num("carbs"),
                        sugar = r.num("sugar"), fat = r.num("fat"), satFat = r.num("satFat"), fiber = r.num("fiber"), salt = r.num("salt"),
                    ),
                    note,
                )
            }.getOrElse { AddFlow.Review(base, "Gemini couldn't read it (${it.message}). Fill it in yourself.") }
            onChange(reviewed)
        }
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) handlePhoto(photoUri) }
    val askCamForPhoto = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) takePhoto.launch(photoUri) }

    when (flow) {
        AddFlow.Choose -> ScSheet("Log food", { onChange(null) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceCard(Ic.barcode, "Barcode", "Scan a packaged food", Modifier.weight(1f)) { onChange(AddFlow.Scan) }
                ChoiceCard(Ic.image, "Photo", "Snap your plate", Modifier.weight(1f)) {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto.launch(photoUri)
                    else askCamForPhoto.launch(Manifest.permission.CAMERA)
                }
            }
            ScButton("Type it in", { onChange(AddFlow.Review(Food(Repo.newId("f"), System.currentTimeMillis(), "", "manual"))) }, Modifier.fillMaxWidth(), icon = Ic.pencil, style = Btn.Ghost)
        }
        AddFlow.Scan -> ScanSheet(
            onClose = { onChange(null) },
            onCode = { code ->
                onChange(AddFlow.Busy("Looking up $code…"))
                scope.launch {
                    val found = runCatching { FoodFacts.lookup(code) }
                    onChange(
                        found.getOrNull()?.let { AddFlow.Review(it) }
                            ?: AddFlow.Review(
                                Food(Repo.newId("f"), System.currentTimeMillis(), "", "barcode", barcode = code),
                                if (found.isFailure) "Lookup failed — check your connection, or fill it in." else "Not in Open Food Facts yet — fill it in yourself.",
                            ),
                    )
                }
            },
        )
        is AddFlow.Busy -> ScSheet("One moment", { onChange(null) }) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GeminiLogo(20.dp)
                Text(flow.text, style = T.body, color = P.ink2)
            }
            ScCard(Modifier.fillMaxWidth(), shadow = false) { FactTeller() }
        }
        is AddFlow.Review -> ReviewSheet(flow.food, flow.note, onClose = { onChange(null) })
    }
}

@Composable
private fun ChoiceCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    ScCard(modifier, onClick = onClick, padding = 16.dp) {
        Icon(icon, null, Modifier.size(30.dp), tint = P.accent)
        Text(title, style = T.h2, color = P.ink, modifier = Modifier.padding(top = 8.dp))
        Text(sub, style = T.small, color = P.ink2)
    }
}

/** Live barcode scanning: CameraX back camera + ML Kit. Also accepts a typed number or a photo of a barcode. */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun ScanSheet(onClose: () -> Unit, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    var typed by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    fun found(code: String) { if (!done) { done = true; Haptics.click(); provider?.unbindAll(); onCode(code) } }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val code = runCatching { BarcodeReader.read(InputImage.fromFilePath(ctx, uri)) }.getOrNull()
            if (code != null) found(code) else Toasts.show("No barcode found in that picture", true)
        }
    }

    DisposableEffect(Unit) { onDispose { provider?.unbindAll(); executor.shutdown() } }

    ScSheet("Scan a barcode", onClose) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).glass(RoundedCornerShape(18.dp), fill = Brand.midnight),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCam) {
                AndroidView(
                    factory = { c ->
                        PreviewView(c).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // stays inside the sheet while it animates
                            val pv = this
                            val future = ProcessCameraProvider.getInstance(c)
                            future.addListener({
                                runCatching {
                                    val p = future.get()
                                    val preview = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                    analysis.setAnalyzer(executor) { proxy ->
                                        val media = proxy.image
                                        if (media == null || done) { proxy.close(); return@setAnalyzer }
                                        BarcodeReader.client.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                                            .addOnSuccessListener { list ->
                                                list.mapNotNull { it.rawValue }.firstOrNull { BarcodeReader.valid(it) }?.let { code ->
                                                    ContextCompat.getMainExecutor(c).execute { found(code) }
                                                }
                                            }
                                            .addOnCompleteListener { proxy.close() }
                                    }
                                    p.unbindAll()
                                    p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                    provider = p
                                }.onFailure { Toasts.show("Camera unavailable: ${it.message}", true) }
                            }, ContextCompat.getMainExecutor(c))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxWidth(0.76f).height(2.dp).background(P.pop))
            } else {
                ScButton("Allow camera", { askCam.launch(Manifest.permission.CAMERA) }, style = Btn.Pop)
            }
        }
        Text("Point at the barcode — it scans by itself.", style = T.small, color = P.ink2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScField(typed, { typed = it.filter(Char::isDigit).take(14) }, Modifier.weight(1f), placeholder = "Or type the number", number = true)
            ScButton("Look up", {
                if (typed.length >= 8) found(typed) else Toasts.show("Enter the full number under the barcode")
            }, small = true)
        }
        ScButton("Scan from a photo", { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth(), icon = Ic.image, style = Btn.Ghost)
    }
}

private val FIELDS = listOf(
    "kcal" to "Calories (kcal)", "protein" to "Protein (g)", "carbs" to "Carbs (g)", "sugar" to "Sugar (g)",
    "fat" to "Fat (g)", "satFat" to "Sat. fat (g)", "fiber" to "Fiber (g)", "salt" to "Salt (g)",
)

private fun Food.field(k: String): Double? = when (k) {
    "kcal" -> kcal; "protein" -> protein; "carbs" -> carbs; "sugar" -> sugar
    "fat" -> fat; "satFat" -> satFat; "fiber" -> fiber; else -> salt
}

private fun Food.with(values: Map<String, Double?>) = copy(
    kcal = values["kcal"], protein = values["protein"], carbs = values["carbs"], sugar = values["sugar"],
    fat = values["fat"], satFat = values["satFat"], fiber = values["fiber"], salt = values["salt"],
)

private fun fmtNum(d: Double?) = d?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""

@Composable
private fun ReviewSheet(food: Food, note: String?, onClose: () -> Unit) {
    val s by Repo.state.collectAsState()
    var name by remember { mutableStateOf(food.name) }
    val values = remember { FIELDS.associate { (k, _) -> k to mutableStateOf(fmtNum(food.field(k))) } }
    val per100 = food.basis == "per 100 g"
    var grams by remember { mutableStateOf("100") }

    // What actually gets logged: values scaled to the portion eaten.
    fun logged(): Food {
        val factor = if (per100) (grams.toDoubleOrNull() ?: 100.0) / 100.0 else 1.0
        val v = values.mapValues { e -> e.value.value.toDoubleOrNull()?.let { Math.round(it * factor * 10) / 10.0 } }
        return food.copy(name = name.trim(), t = System.currentTimeMillis(), basis = if (per100) "${grams.ifBlank { "100" }} g portion" else food.basis).with(v)
    }
    val warnings = Health.warnings(s, logged())

    ScSheet(if (food.source == "barcode") "Scanned" else "Check & save", onClose) {
        if (food.image != null) AsyncImage(food.image, Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(14.dp)))
        if (note != null) Notice(note, Level.Warn, Ic.alert)
        ScField(name, { name = it }, label = "Name")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nutrition ${food.basis}", style = T.small, color = P.ink2, modifier = Modifier.weight(1f))
            food.grade?.let { Chip("Nutri-Score ${it.uppercase()}") }
            food.barcode?.let { Text("  $it", style = T.mono.copy(fontSize = 11.sp), color = P.ink3) }
        }
        FIELDS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (k, label) ->
                    val st = values[k]!!
                    ScField(st.value, { v -> st.value = v.filter { it.isDigit() || it == '.' } }, Modifier.weight(1f), label = label, number = true)
                }
            }
        }
        if (per100) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScField(grams, { grams = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, Modifier.weight(1f), label = "How much did you have? (g)", number = true)
            logged().kcal?.let { Text("= ${it.roundToInt()} kcal", style = T.mono, color = P.ink, modifier = Modifier.padding(bottom = 14.dp)) }
        }
        if (warnings.isNotEmpty()) Notice(
            "This takes you over ${if (Health.limits(s).fromPlan) "your plan's" else "today's"} limits:\n" + warnings.joinToString("\n") { "• $it" },
            Level.Bad, Ic.alert,
        )
        ScButton(if (warnings.isEmpty()) "Add to today" else "Add anyway", {
            if (name.isBlank()) { Toasts.show("Give it a name first"); return@ScButton }
            val f = logged()
            Repo.addFood(f)
            if (warnings.isEmpty()) Haptics.success() else Haptics.warning()
            Toasts.show(if (warnings.isEmpty()) "${f.name} added" else "${f.name} added — over your limit for ${warnings.size} thing${if (warnings.size == 1) "" else "s"}", warnings.isNotEmpty())
            onClose()
        }, Modifier.fillMaxWidth(), icon = Ic.check, style = if (warnings.isEmpty()) Btn.Primary else Btn.Pop)
    }
}

@Composable
private fun DetailSheet(f: Food, onClose: () -> Unit) {
    val score = FoodFacts.score(f)
    ScSheet(f.name, onClose) {
        if (f.image != null) AsyncImage(f.image, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Food score")
                BigNumber("$score", "/ 100")
            }
            Fmt.foodLevel(score).let { (l, w) -> Chip(w, l) }
        }
        ScCard(shadow = false, padding = 0.dp) {
            FIELDS.forEachIndexed { i, (k, label) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(label, style = T.body, color = P.ink, modifier = Modifier.weight(1f))
                    Text(f.field(k)?.let { fmtNum(it) } ?: "—", style = T.mono, color = P.ink)
                }
                if (i < FIELDS.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))
            }
        }
        Text("Values ${f.basis} · from ${f.source}${f.barcode?.let { " · $it" } ?: ""} · ${Fmt.ago(f.t)}", style = T.small, color = P.ink2)
        ScButton("Remove", { Repo.removeFood(f.id); Toasts.show("Removed"); onClose() }, Modifier.fillMaxWidth(), icon = Ic.trash, style = Btn.Danger)
    }
}
