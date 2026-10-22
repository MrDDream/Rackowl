package com.rackowl

import android.Manifest
import com.rackowl.ui.t
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackowl.ui.AppNav
import com.rackowl.ui.theme.HomeTheme

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
            navigationBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
        )
        route.value = intent?.getStringExtra(EXTRA_ROUTE)

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as RackowlApp
        com.rackowl.ui.I18n.language = app.store.config.value.settings.language
        com.rackowl.monitor.MonitorService.sync(this)
        setContent {
            val config by app.store.config.state.collectAsStateWithLifecycle()
            HomeTheme(config.settings.theme) {
                AppNav(app, route.value) { route.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { route.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
