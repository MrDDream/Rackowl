package com.rackowl

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackowl.ui.AppNav
import com.rackowl.ui.components.PrimaryButton
import com.rackowl.ui.t
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.HomeTheme

class MainActivity : FragmentActivity() {
    private val route = mutableStateOf<String?>(null)

    /** Verrouillé tant que l'utilisateur ne s'est pas authentifié (si l'option est active). */
    private val locked = mutableStateOf(false)
    private var backgroundedAt = 0L

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
        locked.value = app.store.config.value.settings.appLock
        setContent {
            val config by app.store.config.state.collectAsStateWithLifecycle()
            // Verrouillage actif : l'aperçu de l'app est masqué dans les apps récentes.
            LaunchedEffect(config.settings.appLock) {
                if (config.settings.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            HomeTheme(config.settings.theme, config.settings.dynamicColor) {
                if (locked.value && config.settings.appLock) {
                    LockScreen { authenticate() }
                    LaunchedEffect(Unit) { authenticate() }
                } else {
                    AppNav(app, route.value) { route.value = null }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Reverrouille après 30 s en arrière-plan.
        val app = application as RackowlApp
        if (app.store.config.value.settings.appLock && backgroundedAt > 0 && SystemClock.elapsedRealtime() - backgroundedAt > 30_000) {
            locked.value = true
        }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(t("Unlock Rackowl", "Déverrouiller Rackowl"))
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                else @Suppress("DEPRECATION") setDeviceCredentialAllowed(true)
            }
            .build()
        prompt.authenticate(info)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { route.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        /** Vrai si le téléphone a une biométrie ou un code configuré. */
        fun canLock(activity: android.content.Context): Boolean {
            val auth = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK
            return BiometricManager.from(activity).canAuthenticate(auth) == BiometricManager.BIOMETRIC_SUCCESS ||
                (Build.VERSION.SDK_INT < 30 && activity.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val c = Home.colors
    Column(
        Modifier.fillMaxSize().background(c.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(72.dp))
        Text("Rackowl", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(t("Locked", "Verrouillée"), fontSize = 14.sp, color = c.muted)
        PrimaryButton(t("Unlock", "Déverrouiller"), onUnlock)
    }
}
