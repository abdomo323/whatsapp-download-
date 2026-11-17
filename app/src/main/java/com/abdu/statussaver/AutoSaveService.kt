package com.abdu.statussaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Best-effort monitor for the user-selected WhatsApp .Statuses folder.
 * WhatsApp creates the status file after it starts loading/playing the story.
 */
class AutoSaveService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rawUri = intent?.getStringExtra(EXTRA_URI)
        if (rawUri.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        startForeground(NOTIFICATION_ID, notification("مراقبة حالات واتساب وحفظ الجديد تلقائياً"))
        if (!started) {
            started = true
            scope.launch {
                val tree = Uri.parse(rawUri)
                val prefs = getSharedPreferences("auto_save", Context.MODE_PRIVATE)
                val seenKey = "seen_${rawUri.hashCode()}"
                // Ignore files that were already present when monitoring began.
                val seen = prefs.getStringSet(seenKey, emptySet())?.toMutableSet() ?: mutableSetOf()
                val initial = StatusRepository.list(this@AutoSaveService, tree)
                initial.items.forEach { seen.add(it.uri.toString()) }
                prefs.edit().putStringSet(seenKey, seen).apply()

                while (isActive) {
                    delay(1800)
                    val latest = StatusRepository.list(this@AutoSaveService, tree)
                    var savedCount = 0
                    for (item in latest.items.sortedBy { it.lastModified }) {
                        val id = item.uri.toString()
                        if (id !in seen) {
                            // Give WhatsApp a short moment to finish writing the media file.
                            delay(900)
                            if (StatusRepository.saveToGallery(this@AutoSaveService, item)) {
                                savedCount++
                                seen.add(id)
                            }
                        }
                    }
                    prefs.edit().putStringSet(seenKey, seen).apply()
                    if (savedCount > 0) {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIFICATION_ID, notification("تم حفظ $savedCount حالة جديدة"))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Story Downloader - Auto Save",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Story Downloader")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "story_downloader_auto_save"
        private const val NOTIFICATION_ID = 7341
        private const val EXTRA_URI = "statuses_tree_uri"

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoSaveService::class.java))
            android.widget.Toast.makeText(
                context, "تم إيقاف الحفظ التلقائي", android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        fun start(context: Context, uriStr: String?) {
            if (uriStr.isNullOrBlank()) return
            val intent = Intent(context, AutoSaveService::class.java)
                .putExtra(EXTRA_URI, uriStr)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            android.widget.Toast.makeText(
                context, "بدأت مراقبة الحالات الجديدة", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
