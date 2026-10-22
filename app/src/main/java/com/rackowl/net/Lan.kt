package com.rackowl.net

import com.rackowl.data.Service
import com.rackowl.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Détection du réseau local : quand l'hôte de référence ne répond pas (ni Wi-Fi maison ni VPN),
 * les services locaux ne sont pas surveillés, pour ne pas confondre « hors du réseau » et « en panne ».
 */
object Lan {
    data class Target(val host: String, val port: Int, val auto: Boolean) {
        override fun toString() = "$host:$port"
    }

    private val LOCAL_SUFFIXES = listOf(".lan", ".local", ".home", ".internal", ".localdomain", ".home.arpa")

    private fun uriOf(url: String): URI? = runCatching { URI(Docker.normalize(url)) }.getOrNull()

    /** Vrai si l'URL vise une IP privée ou un nom d'hôte local (sans point, ou en .lan/.local…). */
    fun isLocalUrl(url: String): Boolean {
        val host = uriOf(url)?.host?.lowercase() ?: return false
        return Containers.isPrivateIp(host) || !host.contains('.') || LOCAL_SUFFIXES.any { host.endsWith(it) }
    }

    private fun targetOf(url: String): Target? {
        val u = uriOf(url) ?: return null
        val host = u.host ?: return null
        if (!Containers.isPrivateIp(host)) return null
        val port = if (u.port > 0) u.port else if (u.scheme == "https") 443 else 80
        return Target(host, port, auto = true)
    }

    /** Hôte de référence : celui réglé, sinon le premier hôte privé des sources, de Dozzle puis des services. */
    fun target(settings: Settings, services: List<Service>): Target? {
        settings.lanProbe.trim().takeIf { it.isNotEmpty() }?.let { raw ->
            val host = raw.substringBeforeLast(':').takeIf { raw.contains(':') } ?: raw
            val port = raw.substringAfterLast(':', "").toIntOrNull() ?: 80
            return Target(host, port, auto = false)
        }
        val urls = settings.sources.map { it.url } + listOf(settings.dozzleUrl) + services.map { it.url }
        return urls.filter { it.isNotBlank() }.firstNotNullOfOrNull(::targetOf)
    }

    /**
     * Statuts à afficher : hors réseau local, l'état des services locaux est inconnu (up = null),
     * plutôt que « hors ligne ».
     */
    fun visibleStatuses(
        runtime: com.rackowl.data.Runtime, services: List<Service>, pingEnabled: Boolean = true,
    ): Map<String, com.rackowl.data.ServiceStatus> {
        // Pings désactivés : aucun état en ligne / hors ligne à montrer.
        if (!pingEnabled) return runtime.statuses.mapValues { it.value.copy(up = null) }
        if (!runtime.offLan) return runtime.statuses
        val local = services.filter { isLocalUrl(it.url) }.map { it.id }.toSet()
        return runtime.statuses.mapValues { (id, st) -> if (id in local) st.copy(up = null) else st }
    }

    @Volatile private var cache: Triple<Target, Boolean, Long>? = null

    /** Connexion TCP de 2 s ; résultat gardé 30 s sauf si [fresh]. */
    suspend fun reachable(t: Target, fresh: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        cache?.let { (ct, ok, at) -> if (!fresh && ct == t && now - at < 30_000) return ok }
        val ok = withContext(Dispatchers.IO) {
            runCatching { Socket().use { it.connect(InetSocketAddress(t.host, t.port), 2_000) } }.isSuccess
        }
        cache = Triple(t, ok, now)
        return ok
    }
}
