package com.rackowl.widget

import android.content.Context
import com.rackowl.ui.t
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rackowl.RackowlApp
import com.rackowl.MainActivity
import com.rackowl.data.Level
import com.rackowl.data.SourceType
import com.rackowl.data.allServices
import com.rackowl.data.fmtAgo
import com.rackowl.data.fmtPercent

private val BG = ColorProvider(Color(0xFF0F1115))
private val TEXT = ColorProvider(Color(0xFFE6E8EC))
private val MUTED = ColorProvider(Color(0xFF9AA1AD))
private val TRACK = ColorProvider(Color(0xFF252A34))
private val UP = Color(0xFF4ADE80)
private val DOWN = Color(0xFFF87171)
private val WARN = Color(0xFFF5A524)
private val ACCENT = Color(0xFF3DD6B5)
private val GREY = Color(0xFF9AA1AD)

object Widgets {
    suspend fun updateAll(context: Context) {
        StatusWidget().updateAll(context)
        ResourcesWidget().updateAll(context)
        ServiceWidget().updateAll(context)
    }
}

private fun mono(size: Int, color: ColorProvider = TEXT) = TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace)
private fun plain(size: Int, color: ColorProvider = TEXT, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)

@Composable
private fun Frame(content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(BG).cornerRadius(24.dp).padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) { content() }
}

@Composable
private fun Dot(color: Color) = Box(GlanceModifier.size(8.dp).background(ColorProvider(color)).cornerRadius(4.dp)) {}

// ---------------------------------------------------------------- 4×2 : état des services

class StatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RackowlApp
        provideContent {
            val config by app.store.config.state.collectAsState()
            val runtime by app.store.runtime.state.collectAsState()
            val services = config.allServices().filter { it.showInWidget && it.monitor }
            val visible = com.rackowl.net.Lan.visibleStatuses(runtime, services, config.settings.pingEnabled)
            val statuses = services.map { it to visible[it.id] }
            val online = statuses.count { it.second?.up == true }
            val last = statuses.mapNotNull { it.second?.checkedAt }.maxOrNull() ?: 0
            Frame {
                Column(GlanceModifier.fillMaxSize()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (runtime.offLan) {
                            Text(t("Off local network", "Hors réseau local"), style = plain(15, ColorProvider(WARN), bold = true), modifier = GlanceModifier.defaultWeight())
                        } else {
                            Text("$online/${services.size}", style = mono(24))
                            Spacer(GlanceModifier.width(8.dp))
                            Text(t("online", "en ligne"), style = plain(13, MUTED), modifier = GlanceModifier.defaultWeight())
                        }
                        Text(fmtAgo(last), style = plain(11, MUTED))
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    // Les services hors ligne d'abord.
                    val rows = statuses.sortedBy { if (it.second?.up == false) 0 else 1 }.take(8).chunked(2)
                    rows.forEach { pair ->
                        Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            pair.forEach { (s, st) ->
                                Row(GlanceModifier.defaultWeight().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Dot(when (st?.up) { true -> UP; false -> DOWN; null -> GREY })
                                    Spacer(GlanceModifier.width(6.dp))
                                    Text(s.name, style = plain(13), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                    Text(
                                        if (st?.up == false) "KO" else st?.latencyMs?.let { "$it ms" } ?: "—",
                                        style = mono(12, ColorProvider(if (st?.up == false) DOWN else GREY)),
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                    if (services.isEmpty()) Text(t("No service to show", "Aucun service à afficher"), style = plain(13, MUTED))
                }
            }
        }
    }
}

class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}

// ---------------------------------------------------------------- 2×2 : ressources serveur

class ResourcesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RackowlApp
        provideContent {
            val config by app.store.config.state.collectAsState()
            val runtime by app.store.runtime.state.collectAsState()
            val src = config.settings.sourcesOf(SourceType.GLANCES).firstOrNull()
            val r = src?.let { runtime.resourcesBySource[it.id] } ?: com.rackowl.data.Resources()
            val warnAt = config.settings.diskWarnPercent
            Frame {
                Column(GlanceModifier.fillMaxSize()) {
                    Text(src?.name ?: t("Server resources", "Ressources serveur"), style = plain(12, MUTED), maxLines = 1)
                    Spacer(GlanceModifier.defaultWeight())
                    if (src == null) {
                        Text(t("Set up Glances in Settings › Sources", "Configure Glances dans Réglages › Sources"), style = plain(12, MUTED))
                    } else listOf("CPU" to r.cpu, "RAM" to r.mem, t("Disk", "Disque") to r.disk).forEach { (label, v) ->
                        val warn = if (label == t("Disk", "Disque")) (v ?: 0.0) >= warnAt else (v ?: 0.0) >= 90
                        Row(GlanceModifier.fillMaxWidth()) {
                            Text(label, style = plain(12), modifier = GlanceModifier.defaultWeight())
                            Text(fmtPercent(v), style = mono(12))
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = ((v ?: 0.0) / 100).toFloat(),
                            modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                            color = ColorProvider(if (warn) WARN else ACCENT),
                            backgroundColor = TRACK,
                        )
                        Spacer(GlanceModifier.height(8.dp))
                    }
                }
            }
        }
    }
}

class ResourcesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResourcesWidget()
}

// ---------------------------------------------------------------- 2×2 : un service

class ServiceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as RackowlApp
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val config by app.store.config.state.collectAsState()
            val runtime by app.store.runtime.state.collectAsState()
            val serviceId = ServiceWidgetPrefs.get(context, widgetId)
            val s = config.allServices().firstOrNull { it.id == serviceId }
                ?: config.allServices().firstOrNull { it.showInWidget && it.widget != null }
            val st = s?.let { runtime.statuses[it.id] }
            Frame {
                if (s == null) {
                    Text(t("Pick a service (long press › Settings)", "Choisis un service (appui long › Paramètres)"), style = plain(12, MUTED))
                } else Column(GlanceModifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(when (st?.up) { true -> UP; false -> DOWN; null -> GREY })
                        Spacer(GlanceModifier.width(8.dp))
                        Text(s.name, style = plain(14, bold = true), maxLines = 1)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    val main = st?.stats?.firstOrNull { it.level != Level.NORMAL } ?: st?.stats?.firstOrNull()
                    val color = when (main?.level) { Level.WARN -> WARN; Level.DOWN -> DOWN; else -> Color(0xFFE6E8EC) }
                    when {
                        st?.up == false -> {
                            Text("KO", style = mono(30, ColorProvider(DOWN)))
                            Text(st.error ?: t("Offline", "Hors ligne"), style = plain(12, MUTED), maxLines = 1)
                        }
                        main != null -> {
                            Text(main.value, style = mono(30, ColorProvider(color)), maxLines = 1)
                            Text(main.label, style = plain(12, MUTED), maxLines = 1)
                        }
                        else -> {
                            Text(st?.latencyMs?.let { "$it" } ?: "—", style = mono(30))
                            Text("ms", style = plain(12, MUTED))
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    val other = st?.stats?.filter { it != main }?.firstOrNull()
                    Text(other?.let { "${it.value} ${it.label}" } ?: fmtAgo(st?.checkedAt ?: 0), style = plain(11, MUTED), maxLines = 1)
                }
            }
        }
    }
}

class ServiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ServiceWidget()
}

object ServiceWidgetPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("service_widgets", Context.MODE_PRIVATE)
    fun get(context: Context, widgetId: Int): String? = prefs(context).getString("w$widgetId", null)
    fun set(context: Context, widgetId: Int, serviceId: String) = prefs(context).edit().putString("w$widgetId", serviceId).apply()
}
