package com.rackowl

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    private lateinit var prompt: BiometricPrompt

    /** Vrai pendant l'invite : la saisie du code de l'appareil peut mettre l'activité en arrière-plan. */
    private var authenticating = false

    /** Invite déjà proposée automatiquement pour ce verrouillage (pas de boucle si l'utilisateur l'annule). */
    private var prompted = false

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
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false
                unlocked = true
                locked.value = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticating = false
                // Invite fermée par le système (app quittée, écran éteint) : la reproposer au retour.
                // Annulée par l'utilisateur : rester sur l'écran de verrouillage, sans boucle.
                if (errorCode == BiometricPrompt.ERROR_CANCELED) prompted = false
            }
        })
        refreshLock()
        setContent {
            val config by app.store.config.state.collectAsStateWithLifecycle()
            // Verrouillage actif : l'aperçu de l'app est masqué dans les apps récentes.
            LaunchedEffect(config.settings.appLock) {
                if (config.settings.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            HomeTheme(config.settings.theme, config.settings.dynamicColor) {
                val showLock = locked.value && config.settings.appLock
                // L'app reste composée sous l'écran de verrouillage : on retrouve la même page, et les
                // résultats d'un sélecteur de fichier lancé avant le verrouillage ne sont pas perdus.
                Box(if (showLock) Modifier.clearAndSetSemantics {} else Modifier) {
                    AppNav(app, route.value) { route.value = null }
                }
                if (showLock) {
                    LockScreen { authenticate() }
                    BackHandler { moveTaskToBack(true) }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Une rotation ou la saisie du code de l'appareil ne comptent pas comme une sortie de l'app.
        if (!isChangingConfigurations && !authenticating) {
            backgroundedAt = SystemClock.elapsedRealtime()
            prompted = false
        }
    }

    override fun onStart() {
        super.onStart()
        refreshLock()
    }

    override fun onResume() {
        super.onResume()
        if (locked.value && !prompted && !authenticating) {
            prompted = true
            authenticate()
        }
    }

    /** Reverrouille dès que l'app est passée en arrière-plan (écran éteint compris). */
    private fun refreshLock() {
        val appLock = (application as RackowlApp).store.config.value.settings.appLock
        // Option désactivée : l'activer plus tard ne verrouille pas tout de suite, seulement à la prochaine sortie.
        if (!appLock) unlocked = true
        if (backgroundedAt > 0) {
            backgroundedAt = 0
            unlocked = false
        }
        val lock = appLock && !unlocked
        if (lock && !locked.value) prompted = false
        locked.value = lock
    }

    private fun authenticate() {
        authenticating = true
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

        // À l'échelle du processus : survit à une rotation ou à une nouvelle instance de l'activité
        // (widget, raccourci), et repart à zéro si Android tue le processus.
        private var unlocked = false
        private var backgroundedAt = 0L

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
        // Opaque et bloque les touches destinées à l'app en dessous.
        Modifier.fillMaxSize().background(c.bg).clickable(remember { MutableInteractionSource() }, null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Image(painterResource(R.drawable.ic_logo), null, Modifier.size(72.dp))
        Text("Rackowl", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(t("Locked", "Verrouillée"), fontSize = 14.sp, color = c.muted)
        PrimaryButton(t("Unlock", "Déverrouiller"), onUnlock)
    }
}
