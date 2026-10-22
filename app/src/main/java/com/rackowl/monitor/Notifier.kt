package com.rackowl.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rackowl.MainActivity
import com.rackowl.R
import com.rackowl.data.AlertEvent
import com.rackowl.data.AlertKind
import com.rackowl.data.Notifier
import com.rackowl.data.Settings
import com.rackowl.net.Http
import com.rackowl.net.join
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Envoie une alerte par le canal choisi dans les réglages (Android, Gotify ou ntfy). */
class AlertNotifier(private val context: Context) {

    init {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    suspend fun send(http: Http, settings: Settings, e: AlertEvent) {
        if (System.currentTimeMillis() < settings.muteUntil) return
        val title = "${e.service} · ${e.title}"
        try {
            when (settings.notifier) {
                Notifier.ANDROID -> android(e, title)
                Notifier.GOTIFY -> {
                    val body = """{"title":${q(title)},"message":${q(e.detail)},"priority":${if (e.kind == AlertKind.DOWN) 8 else 5}}"""
                    http.postJson(join(settings.gotifyUrl, "/message?token=${settings.gotifyToken}"), body)
                }
                Notifier.NTFY -> {
                    val h = buildMap {
                        put("Title", title)
                        put("Priority", if (e.kind == AlertKind.DOWN) "high" else "default")
                        put("Tags", when (e.kind) { AlertKind.DOWN -> "red_circle"; AlertKind.UP -> "green_circle"; AlertKind.WARN -> "warning" })
                        if (settings.ntfyToken.isNotBlank()) put("Authorization", "Bearer ${settings.ntfyToken}")
                    }
                    http.request(join(settings.ntfyUrl, settings.ntfyTopic), "POST", h, e.detail.toRequestBody("text/plain".toMediaType()))
                }
            }
        } catch (ex: Exception) {
            Log.w("RackowlApp", "Envoi de l'alerte impossible, repli sur Android", ex)
            android(e, title)
        }
    }

    private fun android(e: AlertEvent, title: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "alerts"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(e.detail)
            .setColor(0xFF3DD6B5.toInt())
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setPriority(if (e.kind == AlertKind.DOWN) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(e.serviceId?.hashCode() ?: e.id.hashCode(), n)
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    companion object {
        const val CHANNEL = "alerts"
    }
}
