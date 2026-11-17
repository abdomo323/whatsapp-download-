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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
                }
            }
        }
    }
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

    Scaffold(topBar = { TopAppBar(title = { Text("حافظ الحالات") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                sources.forEachIndexed { i, s ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(s.label) })
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
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "أول مرة بس: اضغط الزر تحت، وبعدين اضغط \"استخدام هذا المجلد\" وبعدها \"السماح\".\n" +
                    "لو المجلد ما ظهر، افتح ${source.label} وشوف أي حالة أول.",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onPick, modifier = Modifier.padding(top = 16.dp)) {
                Text("اختيار مجلد حالات ${source.label}")
            }
        }
        return
    }

    var items by remember(uriStr) { mutableStateOf<List<StatusItem>>(emptyList()) }
    var loading by remember(uriStr) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(uriStr, refresh) {
        loading = true
        items = withContext(Dispatchers.IO) { StatusRepository.list(context, Uri.parse(uriStr)) }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { refresh++ }) { Text("تحديث") }
            Button(
                enabled = items.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            items.count { StatusRepository.saveToGallery(context, it) }
                        }
                        Toast.makeText(context, "تم حفظ $ok من ${items.size}", Toast.LENGTH_SHORT).show()
                    }
                }
            ) { Text("حفظ الكل") }
            OutlinedButton(onClick = onPick) { Text("تغيير المجلد") }
        }

        if (!loading && items.isEmpty()) {
            Text(
                "ما في حالات هنا. افتح ${source.label} وشوف الحالات أول، وبعدين اضغط تحديث.",
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.uri.toString() }) { item ->
                StatusCard(item) {
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

@Composable
fun StatusCard(item: StatusItem, onSave: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
            .build()
    }
    Card {
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
                if (item.isVideo) Text("▶", style = MaterialTheme.typography.displaySmall)
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Text("حفظ")
            }
        }
    }
}
