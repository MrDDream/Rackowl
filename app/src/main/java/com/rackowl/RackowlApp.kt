package com.rackowl

import android.app.Application
import com.rackowl.ui.t
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rackowl.data.Store
import com.rackowl.monitor.AlertNotifier
import com.rackowl.monitor.Monitor
import com.rackowl.monitor.MonitorWorker
import com.rackowl.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Conteneur d'application : stockage, client HTTP et moteur de surveillance partagés. */
class RackowlApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var store: Store
        private set
    lateinit var monitor: Monitor
        private set

    @Volatile private var httpClient: Http? = null
    @Volatile private var httpInsecure = false

    /** Client HTTP, recréé si le réglage « certificats auto-signés » change. */
    fun http(): Http {
        val insecure = store.config.value.settings.allowInsecureTls
        val current = httpClient
        if (current != null && httpInsecure == insecure) return current
        return Http(insecure).also { httpClient = it; httpInsecure = insecure }
    }

    private var foregroundLoop: Job? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        com.rackowl.ui.I18n.language = store.config.value.settings.language
        scope.launch { store.config.state.collect { com.rackowl.ui.I18n.language = it.settings.language } }
        monitor = Monitor(this, store, AlertNotifier(this), ::http)
        MonitorWorker.schedule(this)

        // Application visible : on vérifie toutes les 5 s ce qui est dû selon les intervalles réglés.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foregroundLoop?.cancel()
                foregroundLoop = scope.launch {
                    while (isActive) {
                        runCatching { monitor.tick() }
                        delay(5_000)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                foregroundLoop?.cancel()
                foregroundLoop = null
            }
        })
    }
}
