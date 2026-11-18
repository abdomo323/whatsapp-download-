package com.abdu.statussaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val lastModified: Long
)

/** skipped = أسماء ملفات موجودة في المجلد بس ما قدرنا نعرف إنها صورة أو فيديو */
@Immutable
data class ListResult(
    val items: List<StatusItem>,
    val folder: String,
    val skipped: List<String> = emptyList()
)

object StatusRepository {

    private val imageExt = setOf("jpg", "jpeg", "jpe", "png", "gif", "webp", "heic", "heif")
    private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov")

    private enum class Kind { IMAGE, VIDEO, UNKNOWN }

    /** نتيجة التعرف على نوع كل ملف (بالمسار + وقت التعديل) عشان ما نعيد الفحص كل ثانية */
    private val kindCache = ConcurrentHashMap<String, Kind>()

    /** مجلدات .Statuses اللي اتلقت قبل كده (بنحفظها لأن البحث عنها بطيء) */
    private val dirCache = ConcurrentHashMap<String, List<DocumentFile>>()

    private val candidatePaths = listOf(
        listOf(".Statuses"),
        listOf("Media", ".Statuses"),
        listOf("WhatsApp", "Media", ".Statuses"),
        listOf("WhatsApp Business", "Media", ".Statuses"),
        // لو المستخدم اختار Android/media نفسه (مفيد خصوصًا مع واتساب Dual)
        listOf("com.whatsapp", "WhatsApp", "Media", ".Statuses"),
        listOf("com.whatsapp.w4b", "WhatsApp Business", "Media", ".Statuses")
    )

    /** لو المستخدم اختار مجلد أعلى (مثلاً Media أو WhatsApp أو Android/media) ندور جواه على كل .Statuses */
    private fun resolveStatusesDirs(tree: Uri, root: DocumentFile): List<DocumentFile> {
        if (root.name == ".Statuses") return listOf(root)
        dirCache[tree.toString()]?.let { return it }

        val found = mutableListOf<DocumentFile>()
        for (path in candidatePaths) {
            var cur: DocumentFile? = root
            for (seg in path) {
                cur = cur?.findFile(seg)
            }
            val dir = cur
            if (dir != null && dir.isDirectory && found.none { it.uri == dir.uri }) found.add(dir)
        }
        if (found.isEmpty()) return listOf(root)
        dirCache[tree.toString()] = found
        return found
    }

    /** نحدد نوع الملف: بالامتداد، وبعدها بنوع الملف اللي بيقوله النظام، وبعدها بقراءة أول بايتات منه */
    private fun kindOf(context: Context, f: DocumentFile, name: String, modified: Long): Kind {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in videoExt) return Kind.VIDEO
        if (ext in imageExt) return Kind.IMAGE

        val key = "${f.uri}|$modified"
        kindCache[key]?.let { return it }

        val mime = f.type ?: ""
        val kind = when {
            mime.startsWith("video/") -> Kind.VIDEO
            mime.startsWith("image/") -> Kind.IMAGE
            else -> sniff(context, f.uri)
        }
        kindCache[key] = kind
        return kind
    }

    private fun sniff(context: Context, uri: Uri): Kind {
        return try {
            val buf = ByteArray(16)
            val n = context.contentResolver.openInputStream(uri)?.use { it.read(buf) } ?: -1
            if (n < 4) return Kind.UNKNOWN
            fun b(i: Int) = buf[i].toInt() and 0xFF
            when {
                b(0) == 0xFF && b(1) == 0xD8 && b(2) == 0xFF -> Kind.IMAGE // JPEG
                b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47 -> Kind.IMAGE // PNG
                b(0) == 0x47 && b(1) == 0x49 && b(2) == 0x46 -> Kind.IMAGE // GIF
                n >= 12 && b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 &&
                    b(8) == 0x57 && b(9) == 0x45 && b(10) == 0x42 && b(11) == 0x50 -> Kind.IMAGE // WEBP
                n >= 12 && b(4) == 0x66 && b(5) == 0x74 && b(6) == 0x79 && b(7) == 0x70 -> {
                    // ftyp: mp4/mov/3gp، أو HEIC لو الـ brand بتاعه heic/heif
                    val brand = String(buf, 8, 4, Charsets.ISO_8859_1)
                    if (brand.startsWith("hei") || brand.startsWith("mif1") || brand.startsWith("msf1"))
                        Kind.IMAGE else Kind.VIDEO
                }
                else -> Kind.UNKNOWN
            }
        } catch (e: Exception) {
            Kind.UNKNOWN
        }
    }

    /** يقرأ ملفات الحالات (صور وفيديو) من الفولدر اللي اختاره المستخدم */
    fun list(context: Context, tree: Uri): ListResult {
        return try {
            val root = DocumentFile.fromTreeUri(context, tree)
                ?: return ListResult(emptyList(), "؟")
            val dirs = resolveStatusesDirs(tree, root)
            val skipped = mutableListOf<String>()
            val items = dirs
                .flatMap { it.listFiles().toList() }
                .filter { it.isFile }
                .mapNotNull { f ->
                    val name = f.name ?: return@mapNotNull null
                    if (name.startsWith(".")) return@mapNotNull null // مثل .nomedia
                    val modified = f.lastModified()
                    when (kindOf(context, f, name, modified)) {
                        Kind.VIDEO -> StatusItem(f.uri, name, true, modified)
                        Kind.IMAGE -> StatusItem(f.uri, name, false, modified)
                        Kind.UNKNOWN -> {
                            skipped.add(name)
                            null
                        }
                    }
                }
                .sortedByDescending { it.lastModified }
            ListResult(items, dirs.first().name ?: "؟", skipped)
        } catch (e: Exception) {
            ListResult(emptyList(), "؟")
        }
    }

    /** ينسخ الحالة للمعرض (Pictures/StatusSaver أو Movies/StatusSaver) */
    fun saveToGallery(context: Context, item: StatusItem): Boolean {
        return try {
            val resolver = context.contentResolver
            val ext = item.name.substringAfterLast('.', "").lowercase()
            val mime = when {
                item.isVideo -> "video/mp4"
                ext == "png" -> "image/png"
                ext == "gif" -> "image/gif"
                ext == "webp" -> "image/webp"
                ext == "heic" || ext == "heif" -> "image/heic"
                else -> "image/jpeg"
            }
            val collection = if (item.isVideo)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val folder = if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "status_${item.name}")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/StatusSaver")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val target = resolver.insert(collection, values) ?: return false
            resolver.openInputStream(item.uri).use { input ->
                resolver.openOutputStream(target).use { output ->
                    if (input == null || output == null) return false
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
