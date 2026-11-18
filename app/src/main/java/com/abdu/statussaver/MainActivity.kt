package com.abdu.statussaver

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
private val Purple = Color(0xFF246BFF)
private val Navy = Color(0xFF050B2B)
private val CardBg = Color(0xAA123B83)
private val SoftText = Color(0xFFC4D9FF)

enum class Source(
    val key: String,
    val label: String,
    private val modern: String,
    private val legacy: String
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
    );

    /** المكان اللي يفتح فيه اختيار الفولدر مباشرة */
    fun initialUri(): Uri =
        if (Build.VERSION.SDK_INT >= 30)
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia/document/$modern")
        else
            Uri.parse("content://com.android.externalstorage.documents/document/$legacy")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

/** خلفية غامقة فيها دوائر ضوء بتتحرك ببطء */
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
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF030825), Color(0xFF082D75), Color(0xFF07133F))
            )
        )
        val w = size.width
        val h = size.height
        glow(Offset(w * (0.2f + 0.6f * a), h * (0.15f + 0.2f * b)), w * 0.65f, Color(0x6620D8FF))
        glow(Offset(w * (0.85f - 0.6f * b), h * (0.5f + 0.2f * c)), w * 0.75f, Color(0x554169FF))
        glow(Offset(w * (0.3f + 0.4f * c), h * (0.88f - 0.25f * a)), w * 0.65f, Color(0x4430C8FF))
    }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center
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
            val src = sources[tab]
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

@Composable
fun SourceScreen(source: Source, uriStr: String?, onPick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (uriStr == null) {
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
                "أول مرة بس: اضغط الزر تحت، وبعدين اضغط \"استخدام هذا المجلد\" وبعدها \"السماح\".\n" +
                    "لو المجلد ما ظهر، افتح ${source.label} وشوف أي حالة أول.",
                color = SoftText,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Button(onClick = onPick) { Text("اختيار مجلد حالات ${source.label}") }
        }
        return
    }

    var result by remember(uriStr) { mutableStateOf(ListResult(emptyList(), "")) }
    var loading by remember(uriStr) { mutableStateOf(true) }
    val items = result.items
    val lifecycleOwner = LocalLifecycleOwner.current

    // تحديث تلقائي كل ثانية تقريبًا طول ما التطبيق ظاهر قدامك،
    // وأول ما ترجع من الواتساب للتطبيق بيحدّث على طول.
    LaunchedEffect(uriStr, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val latest = withContext(Dispatchers.IO) {
                    StatusRepository.list(context, Uri.parse(uriStr))
                }
                // لو القراءة فشلت مرة (واتساب بيكتب ملف مثلًا) نحتفظ بالقائمة الحالية
                val failed = latest.folder == "؟" && result.items.isNotEmpty()
                if (!failed && latest != result) result = latest
                loading = false
                delay(1000)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = items.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            items.count { StatusRepository.saveToGallery(context, it) }
                        }
                        Toast.makeText(context, "تم حفظ $ok من ${items.size}", Toast.LENGTH_SHORT).show()
                    }
                }
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
                if (source == Source.WHATSAPP) {
                    Text(
                        "\nلو بتشوف حالاتك من نسخة واتساب المكررة (Dual apps)، لازم تكرر التطبيق ده كمان " +
                            "وتفتح النسخة المكررة منه.",
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
            itemsIndexed(items, key = { _, it -> it.uri.toString() }) { index, item ->
                StatusCard(item, index) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { StatusRepository.saveToGallery(context, item) }
                        Toast.makeText(
                            context,
                            if (ok) "تم الحفظ في المعرض" else "فشل الحفظ",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}

/** أيقونة الحفظ بتنط فوق وتحت */
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
fun StatusCard(item: StatusItem, index: Int, onSave: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
            .build()
    }

    // ظهور الكرت بالتدريج: يطلع من تحت ويكبر
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(450, delayMillis = (index % 8) * 70, easing = FastOutSlowInEasing))
    }

    Card(
        modifier = Modifier.graphicsLayer {
            alpha = appear.value
            scaleX = 0.85f + 0.15f * appear.value
            scaleY = 0.85f + 0.15f * appear.value
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
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(item.uri, if (item.isVideo) "video/*" else "image/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "ما لقيت تطبيق يفتح الملف", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isVideo) PulsingPlay()
            }
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                BouncingSaveIcon()
                Text("  حفظ")
            }
        }
    }
}

/** أيقونة التشغيل بتنبض على الفيديوهات */
@Composable
fun PulsingPlay() {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        0.9f, 1.15f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "s"
    )
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .background(Color(0xBB061A50)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Green, modifier = Modifier.size(36.dp))
    }
}
