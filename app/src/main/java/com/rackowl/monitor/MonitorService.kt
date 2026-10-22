package com.rackowl.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rackowl.MainActivity
import com.rackowl.R
import com.rackowl.RackowlApp
import com.rackowl.data.allServices
import com.rackowl.net.Lan
import com.rackowl.ui.t
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Surveillance continue (option « Toujours active ») : service de premier plan avec une notification
 * discrète et permanente, comme Gotify. Les vérifications suivent alors les intervalles réglés,
 * même application fermée, au lieu du minimum de 15 min imposé à WorkManager.
 */
class MonitorService : Service() {
    private var loop: Job? = null
    private var lastText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as RackowlApp
        if (!app.store.config.value.settings.keepAlive) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel(this)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(statusText(app)),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (loop?.isActive != true) loop = app.scope.launch {
            while (isActive) {
                runCatching { app.monitor.tick() }.onFailure { Log.w("Rackowl", "Surveillance", it) }
                val text = statusText(app)
                if (text != lastText && canNotify()) {
                    lastText = text
                    updateNotification(text)
                }
                delay(5_000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        super.onDestroy()
    }

    /** Appelée seulement après [canNotify]. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun updateNotification(text: String) =
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(text))

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun statusText(app: RackowlApp): String {
        val runtime = app.store.runtime.value
        val services = app.store.config.value.allServices()
        if (runtime.offLan) return t("Off local network · local services paused", "Hors réseau local · services locaux en pause")
        val statuses = Lan.visibleStatuses(runtime, services, app.store.config.value.settings.pingEnabled)
        val monitored = services.filter { it.monitor }
        val up = monitored.count { statuses[it.id]?.up == true }
        return t("$up/${monitored.size} services online", "$up/${monitored.size} services en ligne")
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(t("Monitoring active", "Surveillance active"))
        .setContentText(text)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(
            PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    companion object {
        private const val CHANNEL = "keepalive"
        private const val NOTIFICATION_ID = 42

        private fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, t("Continuous monitoring", "Surveillance continue"), NotificationManager.IMPORTANCE_MIN)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Démarre ou arrête le service selon le réglage. */
        fun sync(context: Context) {
            val app = context.applicationContext as RackowlApp
            val intent = Intent(context, MonitorService::class.java)
            if (app.store.config.value.settings.keepAlive) {
                // Refusé si l'app est en arrière-plan sur Android 12+ : on réessaiera à la prochaine ouverture.
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { Log.w("Rackowl", "Démarrage du service impossible", it) }
            } else {
                context.stopService(intent)
            }
        }
    }
}

/** Relance la surveillance continue au démarrage du téléphone. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            MonitorService.sync(context)
        }
    }
}
