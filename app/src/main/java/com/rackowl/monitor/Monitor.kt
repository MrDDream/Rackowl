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
import com.rackowl.data.inMaintenance
import com.rackowl.data.inQuietHours
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
        // Synchronisation Homepage automatique, toutes les 6 h.
        if (cfg.settings.homepageUrl.isNotBlank() && cfg.settings.homepageAutoSync && now - store.runtime.value.homepageSyncAt > 6 * 3600_000L) {
            syncHomepage()
        }

        // Nouvelle version sur GitHub ? Une fois par jour.
        if (cfg.settings.checkUpdates && now - store.runtime.value.updateCheckedAt > 86_400_000L) checkUpdates()

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

    /** Interroge GitHub ; notifie une seule fois par nouvelle version. */
    suspend fun checkUpdates() {
        val release = runCatching { com.rackowl.net.Updates.check(http()) }.getOrNull()
        val previous = store.runtime.value.updateVersion
        store.runtime.update { it.copy(updateCheckedAt = System.currentTimeMillis(), updateVersion = release?.version, updateUrl = release?.url) }
        if (release != null && release.version != previous) notifier.update(release.version, release.url)
    }

    /** Synchronise la configuration avec Homepage ; renvoie le message de résultat. */
    suspend fun syncHomepage(): String {
        val s = store.config.value.settings
        val message = runCatching {
            val json = com.rackowl.data.HomepageSync.fetch(http(), s.homepageUrl, s.homepageUser, s.homepagePassword)
            var result: com.rackowl.data.HomepageSync.Result? = null
            store.config.update { cfg -> com.rackowl.data.HomepageSync.merge(cfg, json).also { result = it }.config }
            t("${result!!.added} added · ${result!!.updated} updated", "${result!!.added} ajouté(s) · ${result!!.updated} mis à jour")
        }.getOrElse { describe(it) }
        store.runtime.update { it.copy(homepageSyncAt = System.currentTimeMillis(), homepageSyncMessage = message) }
        return message
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
                if (s.url.startsWith("tcp://")) {
                    // Contrôle TCP : le port accepte-t-il une connexion ?
                    latency = tcpCheck(s.url); error = null; break
                }
                val r = http().get(s.url)
                code = r.code
                val textOk = s.expectText.isBlank() || r.body.contains(s.expectText)
                if (r.code < 500 && textOk) { latency = r.latencyMs; error = null; break }
                error = if (r.code < 500) t("Expected text missing", "Texte attendu absent") else "HTTP ${r.code}"
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
            val failures = if (up) 0 else prev.consecutiveFailures + 1
            // Échec pas encore confirmé (Service.confirmChecks) : on garde l'état précédent, sans alerte.
            if (!up && failures < s.confirmChecks && prev.up != false) {
                val pending = prev.copy(
                    checkedAt = now, consecutiveFailures = failures, error = error,
                    history = appendSample(prev.history, Sample(now, null), now),
                    buckets = appendBucket(prev.buckets, now, null),
                )
                return@update rt.copy(statuses = rt.statuses + (s.id to pending))
            }
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
                consecutiveFailures = failures,
                history = appendSample(prev.history, Sample(now, latency), now),
                buckets = appendBucket(prev.buckets, now, latency),
            )
            rt.copy(statuses = rt.statuses + (s.id to next))
        }
        // Maintenance : l'état est suivi, mais aucune alerte n'est émise.
        event?.takeIf { !s.inMaintenance(now) }?.let { emit(it, s.critical) }
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

    private suspend fun emit(e: AlertEvent, critical: Boolean = false) {
        store.addAlert(e)
        val settings = store.config.value.settings
        // Heures calmes : notification silencieuse, sauf pour un service critique.
        notifier.send(http(), settings, e, silent = settings.inQuietHours() && !critical)
    }

    companion object {
        private const val TAG = "RackowlApp"
        private const val ATTEMPTS = 3
        private const val BUCKET_MS = 5 * 60_000L
        private const val HISTORY_MS = 24 * 3600_000L

        /** Connexion TCP de 5 s vers tcp://hôte:port ; renvoie la latence en ms. */
        suspend fun tcpCheck(url: String): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val uri = java.net.URI(url)
            val start = System.nanoTime()
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(uri.host, uri.port), 5_000) }
            (System.nanoTime() - start) / 1_000_000
        }

        private const val LONG_BUCKET_MS = 6 * 3600_000L
        private const val LONG_HISTORY_MS = 30 * 86_400_000L

        /** Historique long : une tranche par 6 h sur 30 jours, comptant réussites et échecs. */
        fun appendBucket(buckets: List<com.rackowl.data.Bucket>, now: Long, latency: Long?): List<com.rackowl.data.Bucket> {
            val start = now - now % LONG_BUCKET_MS
            val kept = buckets.filter { now - it.t < LONG_HISTORY_MS }
            val last = kept.lastOrNull()
            val base = if (last != null && last.t == start) last else com.rackowl.data.Bucket(start)
            val next = base.copy(
                up = base.up + if (latency != null) 1 else 0,
                total = base.total + 1,
                latencySum = base.latencySum + (latency ?: 0),
            )
            return if (last != null && last.t == start) kept.dropLast(1) + next else kept + next
        }

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
