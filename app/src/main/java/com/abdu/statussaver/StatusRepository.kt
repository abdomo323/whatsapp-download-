package com.abdu.statussaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile

data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val lastModified: Long
)

object StatusRepository {

    private val imageExt = setOf("jpg", "jpeg", "png", "gif")
    private val videoExt = setOf("mp4")

    /** يقرأ ملفات الحالات من الفولدر اللي اختاره المستخدم */
    fun list(context: Context, tree: Uri): List<StatusItem> {
        return try {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
            dir.listFiles()
                .filter { it.isFile }
                .mapNotNull { f ->
                    val name = f.name ?: return@mapNotNull null
                    val ext = name.substringAfterLast('.', "").lowercase()
                    when {
                        ext in videoExt -> StatusItem(f.uri, name, true, f.lastModified())
                        ext in imageExt -> StatusItem(f.uri, name, false, f.lastModified())
                        else -> null
                    }
                }
                .sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            emptyList()
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
