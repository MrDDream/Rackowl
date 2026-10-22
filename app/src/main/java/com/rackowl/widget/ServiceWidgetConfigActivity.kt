package com.rackowl.widget

import android.appwidget.AppWidgetManager
import com.rackowl.ui.t
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.rackowl.RackowlApp
import com.rackowl.data.allServices
import com.rackowl.ui.components.Divider
import com.rackowl.ui.components.HCard
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.HomeTheme
import kotlinx.coroutines.launch

/** Choix du service affiché par un widget 2×2 « Un service ». */
class ServiceWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val app = application as RackowlApp
        val config = app.store.config.value
        setContent {
            HomeTheme(config.settings.theme) {
                val c = Home.colors
                Column(
                    Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(t("Widget service", "Service du widget"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    val services = config.allServices()
                    if (services.isEmpty()) Text(t("Add a service in the app first.", "Ajoute d’abord un service dans l’application."), color = c.muted)
                    HCard(Modifier.fillMaxWidth()) {
                        services.forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { pick(widgetId, s.id) }.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ServiceIcon(s.icon, s.name, 24.dp)
                                Text(s.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text(s.description, fontSize = 12.sp, color = c.muted)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pick(widgetId: Int, serviceId: String) {
        ServiceWidgetPrefs.set(this, widgetId, serviceId)
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@ServiceWidgetConfigActivity).getGlanceIdBy(widgetId)
                ServiceWidget().update(this@ServiceWidgetConfigActivity, glanceId)
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
