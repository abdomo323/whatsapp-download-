package com.abdu.statussaver

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF20D8FF)
private val Navy = Color(0xFF050B2B)
private val CardBg = Color(0xAA123B83)
private val SoftText = Color(0xFFC4D9FF)
private val DialogBg = Color(0xFF0B1F52)

enum class Source(
    val key: String,
    val label: String,
    private val modern: String,
    private val legacy: String,
    private val tree: String = "primary%3AAndroid%2Fmedia"
) {
    WHATSAPP(
        "wa", "واتساب",
        "primary%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses",
        "primary%3AWhatsApp%2FMedia%2F.Statuses"
    ),
    BUSINESS(
        "w4b", "واتساب بزنس",
        "primary%3AAndroid%2Fmedia%2Fcom.whatsapp.w4b%2FWhatsApp%20Business%2FMedia%2F.Statuses",
        "primary%3AWhatsApp%20Business%2FMedia%2F.Statuses"
    ),
    // نسخة Dual (Twin App) بتخزن في: Twin App data/Android/media/com.whatsapp/WhatsApp/Media/.Statuses
    DUAL(
        "dual", "واتساب Dual",
        "primary%3ATwin%20App%20data%2FAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses",
        "primary%3ATwin%20App%20data%2FAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses",
        "primary%3ATwin%20App%20data"
    );

    /** المكان اللي يفتح فيه اختيار الفولدر مباشرة */
    fun initialUri(): Uri =
        if (Build.VERSION.SDK_INT >= 30)
            Uri.parse("content://com.android.externalstorage.documents/tree/$tree/document/$modern")
        else
            Uri.parse("content://com.android.externalstorage.documents/document/$legacy")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF18CFFF),
                    onPrimary = Color(0xFF04210F),
                    secondary = Color(0xFF4169FF),
                    background = Navy,
                    surface = Color(0xCC102D68),
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize()) {
                        AnimatedBackground()
                        App()
                    }
                }
            }
        }
    }

    /** يطلب من الشاشة أعلى معدل تحديث متاح (90/120Hz) بنفس الدقة الحالية */
    private fun requestHighRefreshRate() {
        try {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val cur = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            lp.preferredDisplayModeId = best.modeId
            window.attributes = lp
        } catch (_: Exception) {
        }
    }
}

/**
 * خلفية فيها دوائر ضوء بتتحرك ببطء.
 * الدوائر مرسومة مرة واحدة وبتتحرك بـ graphicsLayer (على الـ GPU)
 * عشان ما تتعبش السكرول.
 */
@Composable
fun AnimatedBackground() {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val b by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b"
    )
    val c by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(31000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c"
    )
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF030825), Color(0xFF082D75), Color(0xFF07133F))
                )
            )
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        Glow(maxWidth * 1.3f, Color(0x6620D8FF), Modifier.graphicsLayer {
            translationX = wPx * (0.2f + 0.6f * a) - size.width / 2
            translationY = hPx * (0.15f + 0.2f * b) - size.height / 2
        })
        Glow(maxWidth * 1.5f, Color(0x554169FF), Modifier.graphicsLayer {
            translationX = wPx * (0.85f - 0.6f * b) - size.width / 2
            translationY = hPx * (0.5f + 0.2f * c) - size.height / 2
        })
        Glow(maxWidth * 1.3f, Color(0x4430C8FF), Modifier.graphicsLayer {
            translationX = wPx * (0.3f + 0.4f * c) - size.width / 2
            translationY = hPx * (0.88f - 0.25f * a) - size.height / 2
        })
    }
}

@Composable
private fun Glow(diameter: Dp, color: Color, modifier: Modifier) {
    Box(
        modifier
            .size(diameter)
            .background(Brush.radialGradient(listOf(color, Color.Transparent)))
    )
}

/** زرار بينط لما تضغط عليه */
@Composable
fun BounceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = src,
        colors = if (containerColor == Color.Unspecified) ButtonDefaults.buttonColors()
        else ButtonDefaults.buttonColors(containerColor = containerColor),
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val sources = Source.values()
    var tab by remember { mutableIntStateOf(0) }
    var pendingKey by rememberSaveable { mutableStateOf<String?>(null) }

    val uris = remember {
        mutableStateMapOf<String, String>().apply {
            sources.forEach { s -> prefs.getString(s.key, null)?.let { put(s.key, it) } }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val key = pendingKey
        if (uri != null && key != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            prefs.edit().putString(key, uri.toString()).apply()
            uris[key] = uri.toString()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("Story Downloader", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x331A8CFF),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color(0x331A8CFF),
                contentColor = Green
            ) {
                sources.forEachIndexed { i, s ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        selectedContentColor = Green,
                        unselectedContentColor = SoftText,
                        text = { Text(s.label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
            // انتقال بالسحب والتلاشي بين التبويبات
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { it * dir / 3 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it * dir / 3 } + fadeOut(tween(200)))
                },
                label = "tabs"
            ) { idx ->
                val src = sources[idx]
                SourceScreen(
                    source = src,
                    uriStr = uris[src.key],
                    onPick = {
                        pendingKey = src.key
                        picker.launch(src.initialUri())
                    }
                )
            }
        }
    }
}

@Composable
fun SourceScreen(source: Source, uriStr: String?, onPick: () -> Unit) {
    if (uriStr == null) PickFolderScreen(source, onPick) else SourceContent(source, uriStr, onPick)
}

@Composable
fun PickFolderScreen(source: Source, onPick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "float")
    val up by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "up"
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = Green,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer { translationY = -16f * up * density }
        )
        Text(
            if (source == Source.DUAL)
                "أول مرة بس: اضغط الزر تحت، وهيفتح على مجلد .Statuses بتاع الـ Dual " +
                    "(Twin App data ← Android ← media ← com.whatsapp ← WhatsApp ← Media ← .Statuses).\n" +
                    "اضغط \"استخدام هذا المجلد\" وبعدها \"السماح\".\n" +
                    "لو ما فتحش عليه لوحده، روح للمسار ده يدوي، أو اختار مجلد Media وأنا هلاقي .Statuses جواه."
            else
                "أول مرة بس: اضغط الزر تحت، وبعدين اضغط \"استخدام هذا المجلد\" وبعدها \"السماح\".\n" +
                    "لو المجلد ما ظهر، افتح ${source.label} وشوف أي حالة أول.",
            color = SoftText,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        BounceButton(onClick = onPick) { Text("اختيار مجلد حالات ${source.label}") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceContent(source: Source, uriStr: String, onPick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var result by remember(uriStr) { mutableStateOf(ListResult(emptyList(), "")) }
    var loading by remember(uriStr) { mutableStateOf(true) }
    var knownCount by remember(uriStr) { mutableIntStateOf(-1) }
    val animatedKeys = remember(uriStr) { HashSet<String>() }
    val items = result.items

    var previewItem by remember { mutableStateOf<StatusItem?>(null) }
    var confirmSaveAll by remember { mutableStateOf(false) }
    var bannerText by remember { mutableStateOf("") }
    var bannerVisible by remember { mutableStateOf(false) }
    var bannerTick by remember { mutableIntStateOf(0) }

    val showBanner: (String) -> Unit = { text ->
        bannerText = text
        bannerVisible = true
        bannerTick++
    }
    LaunchedEffect(bannerTick) {
        if (bannerTick > 0) {
            delay(2200)
            bannerVisible = false
        }
    }

    // تحديث تلقائي كل نص ثانية طول ما التطبيق ظاهر،
    // وأول ما ترجع من الواتساب بيحدّث على طول.
    LaunchedEffect(uriStr, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val latest = withContext(Dispatchers.IO) {
                    StatusRepository.list(context, Uri.parse(uriStr))
                }
                // لو القراءة فشلت مرة (واتساب بيكتب ملف مثلًا) نحتفظ بالقائمة الحالية
                val failed = latest.folder == "؟" && result.items.isNotEmpty()
                if (!failed && latest != result) {
                    result = latest
                    if (latest.folder != "؟") {
                        val prev = knownCount
                        knownCount = latest.items.size
                        if (prev >= 0 && latest.items.size > prev) {
                            showBanner("وصلت ${latest.items.size - prev} حالة جديدة ✨")
                        }
                    }
                }
                loading = false
                delay(500)
            }
        }
    }

    val saveOne: (StatusItem) -> Unit = { item ->
        scope.launch {
            val ok = withContext(Dispatchers.IO) { StatusRepository.saveToGallery(context, item) }
            showBanner(if (ok) "تم الحفظ في المعرض ✓" else "فشل الحفظ")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BounceButton(
                    modifier = Modifier.weight(1f),
                    enabled = items.isNotEmpty(),
                    onClick = { confirmSaveAll = true }
                ) {
                    BouncingSaveIcon()
                    Text("  حفظ الكل")
                }
                FilledTonalIconButton(
                    onClick = onPick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0x3324BFFF),
                        contentColor = Color.White
                    )
                ) { Icon(Icons.Rounded.FolderOpen, contentDescription = "تغيير المجلد") }
            }

            if (result.skipped.isNotEmpty()) {
                Text(
                    "ملفات موجودة بس ما قدرت أعرف نوعها (${result.skipped.size}): " +
                        result.skipped.take(3).joinToString(" ، "),
                    color = SoftText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (!loading && items.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "ما في حالات هنا لسه. افتح أي حالة في ${source.label} وهتظهر هنا لوحدها.",
                        color = Color.White
                    )
                    Text(
                        "\nالمجلد المختار: ${result.folder}",
                        color = SoftText
                    )
                    if (source != Source.DUAL) {
                        Text(
                            "\nلو حالاتك من نسخة واتساب المكررة (Dual)، استخدم تبويب \"واتساب Dual\".",
                            color = SoftText
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items,
                    key = { _, it -> it.uri.toString() },
                    contentType = { _, _ -> "status" }
                ) { index, item ->
                    // الأنيميشن بتاع الظهور بيشتغل مرة واحدة بس لكل كرت (مش كل ما تسكرول)
                    val fresh = remember { animatedKeys.add(item.uri.toString()) }
                    StatusCard(
                        item = item,
                        index = index,
                        fresh = fresh,
                        modifier = Modifier.animateItemPlacement(),
                        onOpen = { previewItem = item },
                        onSave = { saveOne(item) }
                    )
                }
            }
        }

        // بوب أب صغير بيطلع من تحت: "حالة جديدة" / "تم الحفظ"
        AnimatedVisibility(
            visible = bannerVisible,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = slideInVertically(spring(dampingRatio = 0.6f)) { it * 2 } + fadeIn() + scaleIn(initialScale = 0.8f),
            exit = slideOutVertically { it * 2 } + fadeOut()
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xEE0759D8))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) { Text(bannerText, fontWeight = FontWeight.Bold, color = Color.White) }
        }
    }

    // بوب أب معاينة الحالة
    previewItem?.let { item ->
        PreviewDialog(
            item = item,
            onDismiss = { previewItem = null },
            onSave = {
                previewItem = null
                saveOne(item)
            },
            onOpen = { openExternally(context, item) }
        )
    }

    // بوب أب تأكيد "حفظ الكل"
    if (confirmSaveAll) {
        val snapshot = items
        AlertDialog(
            onDismissRequest = { confirmSaveAll = false },
            containerColor = DialogBg,
            titleContentColor = Color.White,
            textContentColor = SoftText,
            shape = RoundedCornerShape(28.dp),
            title = { Text("حفظ الكل؟", fontWeight = FontWeight.Bold) },
            text = { Text("هتنزل ${snapshot.size} حالة في المعرض (مجلد StatusSaver).") },
            confirmButton = {
                BounceButton(onClick = {
                    confirmSaveAll = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            snapshot.count { StatusRepository.saveToGallery(context, it) }
                        }
                        showBanner("تم حفظ $ok من ${snapshot.size} ✓")
                    }
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSaveAll = false }) { Text("إلغاء", color = SoftText) }
            }
        )
    }
}

private fun openExternally(context: Context, item: StatusItem) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, if (item.isVideo) "video/*" else "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "ما لقيت تطبيق يفتح الملف", Toast.LENGTH_SHORT).show()
    }
}

private fun statusRequest(context: Context, item: StatusItem, w: Int, h: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(item.uri)
        .size(w, h) // نفك الصورة بحجم العرض بس، عشان السكرول يفضل خفيف
        .crossfade(300)
        .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
        .build()

/** أيقونة الحفظ بتنط فوق وتحت (مستخدمة في زر "حفظ الكل" بس) */
@Composable
fun BouncingSaveIcon() {
    val t = rememberInfiniteTransition(label = "bounce")
    val y by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "y"
    )
    Icon(
        Icons.Rounded.SaveAlt,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { translationY = -6f * y * density }
    )
}

@Composable
fun StatusCard(
    item: StatusItem,
    index: Int,
    fresh: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val request = remember(item.uri) { statusRequest(context, item, 400, 533) }

    // ظهور الكرت بالتدريج (مرة واحدة بس): يطلع من تحت ويكبر
    val appear = remember { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (fresh) {
            appear.animateTo(1f, tween(450, delayMillis = (index % 8) * 60, easing = FastOutSlowInEasing))
        }
    }

    // الكرت بينكمش شوية لما تضغط عليه ويرجع ببونس
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardPress"
    )

    Card(
        modifier = modifier.graphicsLayer {
            alpha = appear.value
            val s = (0.85f + 0.15f * appear.value) * pressScale
            scaleX = s
            scaleY = s
            translationY = (1f - appear.value) * 60f
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg, contentColor = Color.White)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clickable(interactionSource = src, indication = null) { onOpen() },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isVideo) PlayBadge()
            }
            BounceButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                Text("  حفظ")
            }
        }
    }
}

/** علامة التشغيل على الفيديوهات */
@Composable
fun PlayBadge() {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0xBB061A50)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Green, modifier = Modifier.size(36.dp))
    }
}

/** مشغّل فيديو جوه البوب أب: بيشتغل على طول، وضغطة على الفيديو توقفه/تكمله */
@Composable
fun InlineVideoPlayer(item: StatusItem, poster: ImageRequest, onError: () -> Unit) {
    val context = LocalContext.current
    val videoView = remember { VideoView(context) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(item.uri) {
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            ready = true
            playing = true
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(context, "ما قدرت أشغل الفيديو هنا، هفتحه في مشغل تاني", Toast.LENGTH_SHORT).show()
            onError()
            true
        }
        videoView.setVideoURI(item.uri)
        onDispose { videoView.stopPlayback() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())
        // صورة الفيديو لحد ما يجهز
        if (!ready) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        AnimatedVisibility(
            visible = ready && !playing,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) { PlayBadge() }
        // طبقة شفافة فوق الفيديو لاستقبال الضغط
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (ready) {
                        if (videoView.isPlaying) {
                            videoView.pause()
                            playing = false
                        } else {
                            videoView.start()
                            playing = true
                        }
                    }
                }
        )
    }
}

/** بوب أب معاينة: بيظهر بحجم صغير ويكبر ببونس، والفيديو بيشتغل فيه على طول */
@Composable
fun PreviewDialog(item: StatusItem, onDismiss: () -> Unit, onSave: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item.uri) { statusRequest(context, item, 900, 1200) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            if (shown) 1f else 0.7f,
            spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
            label = "dlgScale"
        )
        val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "dlgAlpha")

        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // الفيديو (SurfaceView) ما بيتعاملش كويس مع الشفافية، فنسيبها للصور بس
                    this.alpha = if (item.isVideo) 1f else alpha
                },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = DialogBg, contentColor = Color.White)
        ) {
            Column {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.8f),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isVideo) {
                        InlineVideoPlayer(item, request, onError = onOpen)
                    } else {
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BounceButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                        Text("  حفظ")
                    }
                    FilledTonalIconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0x3324BFFF),
                            contentColor = Color.White
                        )
                    ) { Icon(Icons.Rounded.Close, contentDescription = "إغلاق") }
                }
            }
        }
    }
}
