package com.rackowl.monitor

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rackowl.MainActivity
import com.rackowl.RackowlApp
import com.rackowl.data.allServices
import com.rackowl.net.Lan
import com.rackowl.ui.t

/** Tuile des réglages rapides : « 11/12 en ligne », active si tout va bien ; un appui ouvre l'app. */
class StatusTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val app = application as RackowlApp
        val config = app.store.config.value
        val runtime = app.store.runtime.value
        val services = config.allServices().filter { it.monitor }
        val statuses = Lan.visibleStatuses(runtime, services, config.settings.pingEnabled)
        val up = services.count { statuses[it.id]?.up == true }
        val down = services.count { statuses[it.id]?.up == false }
        tile.label = "Rackowl"
        val text = when {
            !config.settings.pingEnabled -> t("Pings off", "Pings coupés")
            runtime.offLan -> t("Off local network", "Hors réseau local")
            else -> t("$up/${services.size} online", "$up/${services.size} en ligne")
        }
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = text else tile.label = "Rackowl · $text"
        tile.state = if (down > 0 || !config.settings.pingEnabled) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }
}
