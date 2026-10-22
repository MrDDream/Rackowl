package com.rackowl.monitor

import android.content.Context
import com.rackowl.ui.t
import android.util.Log
import com.rackowl.data.AlertEvent
import com.rackowl.data.AlertKind
import com.rackowl.data.Resources
import com.rackowl.data.Sample
import com.rackowl.data.SourceConfig
import com.rackowl.data.SourceType
import com.rackowl.data.Service
import com.rackowl.data.ServiceStatus
import com.rackowl.data.Store
import com.rackowl.data.allServices
import com.rackowl.data.fmtDuration
import com.rackowl.data.fmtPercent
import com.rackowl.integrations.GlancesClient
import com.rackowl.integrations.Integrations
import com.rackowl.net.Http
import com.rackowl.net.Lan
import com.rackowl.widget.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le cœur de la surveillance : ping des services, lecture des intégrations,
 * ressources Glances, détection des changements d'état et envoi des alertes.
 */
class Monitor(
    private val context: Context,
    private val store: Store,
    private val notifier: AlertNotifier,
    private val http: () -> Http,
) {
    private val running = Collections.synchronizedSet(mutableSetOf<String>())
    private val tickLock = Mutex()
    private var lastWidgetUpdate = 0L

    /** Vérifie tout ce qui est dû. [force] ignore les intervalles (tirer pour actualiser). */
    suspend fun tick(force: Boolean = false) = tickLock.withLock {
        val cfg = store.config.value
        val now = System.currentTimeMillis()
        val statuses = store.runtime.value.statuses
        val stateChanged = AtomicBoolean(false)

        // Réseau local joignable (Wi-Fi maison ou VPN) ? Sinon, on ne surveille que les services publics.
        val target = if (cfg.settings.pauseOffLan) Lan.target(cfg.settings, cfg.allServices()) else null
        val offLan = target != null && !Lan.reachable(target, fresh = force)
        val wasOffLan = store.runtime.value.offLan
        if (offLan != wasOffLan) {
            store.runtime.update { it.copy(offLan = offLan, offLanSince = if (offLan) now else null) }
            stateChanged.set(true)
        }
        // De retour sur le réseau : tout revérifier tout de suite.
        val forceAll = force || (wasOffLan && !offLan)

        coroutineScope {
            cfg.allServices().forEach { s ->
                if (offLan && Lan.isLocalUrl(s.url)) return@forEach
                val force = forceAll
                val st = statuses[s.id]
                val pingDue = cfg.settings.pingEnabled && s.monitor && (force || st == null || now - st.checkedAt >= s.pingIntervalSec * 1000L - 500)
                val statsDue = s.widget != null && (force || st == null || now - st.statsAt >= cfg.settings.refreshSec * 1000L - 500)
                if ((pingDue || statsDue) && running.add(s.id)) launch {
                    try {
                        if (pingDue && ping(s)) stateChanged.set(true)
                        if (statsDue) stats(s)
                    } finally {
                        running.remove(s.id)
                    }
                }
            }
            cfg.settings.sourcesOf(SourceType.GLANCES).forEach { src ->
                if (offLan && Lan.isLocalUrl(src.url)) return@forEach
                val force = forceAll
                val at = store.runtime.value.resourcesBySource[src.id]?.at ?: 0
                if (force || now - at >= cfg.settings.refreshSec * 1000L - 500) launch { resources(src) }
            }
        }
        if (stateChanged.get() || force || now - lastWidgetUpdate > 60_000) {
            lastWidgetUpdate = now
            runCatching { Widgets.updateAll(context) }.onFailure { Log.w(TAG, "Widgets", it) }
        }
    }

    suspend fun refreshService(id: String) {
        val s = store.config.value.allServices().firstOrNull { it.id == id } ?: return
        if (s.monitor && store.config.value.settings.pingEnabled) ping(s)
        if (s.widget != null) stats(s)
    }

    /** Ping HTTP avec 3 tentatives. Renvoie true si l'état en ligne/hors ligne a changé. */
    private suspend fun ping(s: Service): Boolean {
        var code: Int? = null
        var error: String? = null
        var latency: Long? = null
        var attempts = 0
        for (i in 1..ATTEMPTS) {
            attempts = i
            try {
                val r = http().get(s.url)
                code = r.code
                if (r.code < 500) { latency = r.latencyMs; error = null; break }
                error = "HTTP ${r.code}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = describe(e)
            }
            if (i < ATTEMPTS) delay(1_500)
        }
        val up = latency != null
        // Échec d'un service local : si le réseau local vient de disparaître (VPN coupé), ce n'est pas une panne.
        if (!up && Lan.isLocalUrl(s.url)) {
            val settings = store.config.value.settings
            val target = if (settings.pauseOffLan) Lan.target(settings, store.config.value.allServices()) else null
            if (target != null && !Lan.reachable(target, fresh = true)) return false
        }
        val now = System.currentTimeMillis()
        var event: AlertEvent? = null
        store.runtime.update { rt ->
            val prev = rt.statuses[s.id] ?: ServiceStatus(s.id)
            val wasUp = prev.up
            val downSince = when {
                up -> null
                wasUp == false -> prev.downSince ?: now
                else -> now
            }
            if (!up && wasUp != false) {
                event = AlertEvent(
                    serviceId = s.id, service = s.name, icon = s.icon, kind = AlertKind.DOWN,
                    title = t("Offline", "Hors ligne"), detail = (error ?: t("Unreachable", "Injoignable")) + t(" · $attempts attempts", " · $attempts tentatives"),
                )
            } else if (up && wasUp == false) {
                val outage = prev.downSince?.let { fmtDuration(now - it) }
                event = AlertEvent(
                    serviceId = s.id, service = s.name, icon = s.icon, kind = AlertKind.UP,
                    title = t("Back online", "De nouveau en ligne"), detail = outage?.let { t("Down for $it", "Coupure de $it") } ?: t("Service restored", "Service rétabli"),
                )
            }
            val next = prev.copy(
                up = up, latencyMs = latency, httpCode = code, error = if (up) null else error,
                checkedAt = now, downSince = downSince, failedAttempts = if (up) 0 else attempts,
                history = appendSample(prev.history, Sample(now, latency), now),
            )
            rt.copy(statuses = rt.statuses + (s.id to next))
        }
        event?.let { emit(it) }
        return event != null
    }

    private suspend fun stats(s: Service) {
        val integration = Integrations.byId(s.widget) ?: return
        val now = System.currentTimeMillis()
        val result = runCatching { integration.fetch(http(), s) }
        store.runtime.update { rt ->
            val prev = rt.statuses[s.id] ?: ServiceStatus(s.id)
            val next = result.fold(
                { prev.copy(stats = it.stats, detailsTitle = it.detailsTitle, details = it.details, statsAt = now, statsError = null) },
                { prev.copy(statsAt = now, statsError = describe(it)) },
            )
            rt.copy(statuses = rt.statuses + (s.id to next))
        }
    }

    private suspend fun resources(src: SourceConfig) {
        val settings = store.config.value.settings
        val result = runCatching { GlancesClient.read(http(), src.url, src.key, src.secret) }
        var event: AlertEvent? = null
        store.runtime.update { rt ->
            result.fold(
                { res ->
                    val over = (res.disk ?: 0.0) >= settings.diskWarnPercent
                    if (over && src.id !in rt.diskWarnedSources) {
                        event = AlertEvent(
                            serviceId = null, service = src.name, kind = AlertKind.WARN,
                            title = t("Disk at ${fmtPercent(res.disk)}", "Disque à ${fmtPercent(res.disk)}"), detail = t("Above the ${settings.diskWarnPercent}% threshold", "Seuil de ${settings.diskWarnPercent} % dépassé"),
                        )
                    }
                    rt.copy(
                        resourcesBySource = rt.resourcesBySource + (src.id to res),
                        diskWarnedSources = if (over) rt.diskWarnedSources + src.id else rt.diskWarnedSources - src.id,
                    )
                },
                {
                    val prev = rt.resourcesBySource[src.id] ?: Resources()
                    rt.copy(resourcesBySource = rt.resourcesBySource + (src.id to prev.copy(error = describe(it), at = System.currentTimeMillis())))
                },
            )
        }
        event?.let { emit(it) }
    }

    private suspend fun emit(e: AlertEvent) {
        store.addAlert(e)
        notifier.send(http(), store.config.value.settings, e)
    }

    companion object {
        private const val TAG = "RackowlApp"
        private const val ATTEMPTS = 3
        private const val BUCKET_MS = 5 * 60_000L
        private const val HISTORY_MS = 24 * 3600_000L

        /** Un point par tranche de 5 min sur 24 h ; une panne dans la tranche l'emporte. */
        fun appendSample(history: List<Sample>, s: Sample, now: Long): List<Sample> {
            val kept = history.filter { now - it.t < HISTORY_MS }
            val last = kept.lastOrNull()
            return if (last != null && last.t / BUCKET_MS == s.t / BUCKET_MS) {
                val merged = if (last.latency == null || s.latency == null) Sample(last.t, null)
                else Sample(last.t, maxOf(last.latency, s.latency))
                kept.dropLast(1) + merged
            } else kept + s
        }

        fun describe(e: Throwable): String = when (e) {
            is java.net.UnknownHostException -> t("Host not found", "Hôte introuvable")
            is java.net.ConnectException -> t("Connection refused", "Connexion refusée")
            is java.net.SocketTimeoutException -> t("Timed out (5 s)", "Délai dépassé (5 s)")
            is javax.net.ssl.SSLException -> t("TLS error (certificate?)", "Erreur TLS (certificat ?)")
            is com.rackowl.net.HttpException -> e.message ?: "HTTP ${e.code}"
            is kotlinx.serialization.SerializationException -> t("Unexpected response", "Réponse inattendue")
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}
