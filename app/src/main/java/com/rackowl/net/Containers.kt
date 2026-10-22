package com.rackowl.net

import com.rackowl.data.Service
import com.rackowl.data.Settings
import com.rackowl.data.SourceConfig
import com.rackowl.data.SourceType
import com.rackowl.data.slugify
import com.rackowl.integrations.Integrations
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Un conteneur proposé à l'ajout d'un service. */
data class PickableContainer(
    val name: String,
    val image: String,
    val running: Boolean,
    /** Paires (port publié, port interne) en TCP. */
    val ports: List<Pair<Int, Int>>,
    val labels: Map<String, String>,
)

/** Un hôte Docker : une source Docker, ou un serveur d'une source Komodo. */
data class PickHost(
    /** Clé stable, utilisée pour mémoriser l'IP choisie (Settings.hostIps). */
    val key: String,
    val name: String,
    val sourceId: String,
    val sourceName: String,
    val sourceType: SourceType,
    /** IP déduite de la source, si elle est exploitable. */
    val detectedIp: String?,
    val containers: List<PickableContainer>,
)

/** Rassemble, hôte par hôte, les conteneurs des sources Docker et Komodo configurées. */
object Containers {
    data class Result(val hosts: List<PickHost>, val errors: Map<String, String>)

    suspend fun hosts(http: Http, s: Settings, describe: (Throwable) -> String): Result = coroutineScope {
        val sources = s.sources.filter { it.type == SourceType.DOCKER || it.type == SourceType.KOMODO }
        // Dozzle connaît l'IP locale de chaque hôte (agents) : elle sert de référence par nom d'hôte.
        val dozzleIps = async {
            if (s.dozzleUrl.isBlank()) emptyMap()
            else Dozzle(http, s.dozzleUrl, s.dozzleUser, s.dozzlePassword).hosts().values
                .mapNotNull { h -> h.address?.let { h.name.lowercase() to it } }.toMap()
        }
        val results = sources.map { src ->
            async {
                src to runCatching { if (src.type == SourceType.DOCKER) listOf(fromDocker(http, src)) else fromKomodo(http, src, dozzleIps.await()) }
            }
        }.awaitAll()
        val errors = results.mapNotNull { (src, r) -> r.exceptionOrNull()?.let { src.name to describe(it) } }.toMap()
        val hosts = results.flatMap { it.second.getOrDefault(emptyList()) }.sortedBy { it.name.lowercase() }
        Result(hosts, errors)
    }

    /** IP finale d'un hôte : celle réglée par l'utilisateur, sinon celle détectée. */
    fun ipOf(host: PickHost, s: Settings): String? = s.hostIps[host.key]?.takeIf { it.isNotBlank() } ?: host.detectedIp

    /** IPv4 de réseau local (RFC 1918, ou 100.64/10 pour Tailscale / CGNAT). */
    fun isPrivateIp(host: String?): Boolean {
        val p = host?.split('.')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 } ?: return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31) || (p[0] == 100 && p[1] in 64..127)
    }

    private fun isIp(host: String) = host.split('.').let { it.size == 4 && it.all { n -> n.toIntOrNull() != null } }

    fun hostOf(url: String): String? =
        runCatching { java.net.URI(Docker.normalize(url)).host }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "localhost" && it != "127.0.0.1" && !it.startsWith("periphery") }

    private suspend fun fromDocker(http: Http, src: SourceConfig): PickHost {
        val containers = http.getJson(join(Docker.normalize(src.url), "/containers/json?all=1")).arr.map { c ->
            PickableContainer(
                name = c["Names"][0].str.orEmpty().trimStart('/'),
                image = c["Image"].str.orEmpty(),
                running = c["State"].str == "running",
                ports = c["Ports"].arr.filter { it["Type"].str == "tcp" }
                    .mapNotNull { p -> p["PublicPort"].long?.let { it.toInt() to (p["PrivatePort"].long?.toInt() ?: it.toInt()) } }
                    .distinct(),
                labels = c["Labels"].obj.mapValues { it.value.str.orEmpty() },
            )
        }
        return PickHost("docker:${src.id}", src.name, src.id, src.name, SourceType.DOCKER, hostOf(src.url), containers)
    }

    private suspend fun fromKomodo(http: Http, src: SourceConfig, dozzleIps: Map<String, String>): List<PickHost> {
        val h = mapOf("x-api-key" to src.key.trim(), "x-api-secret" to src.secret.trim())
        suspend fun read(type: String, body: String): HttpResponse = http.postJson(join(src.url, "/read/$type"), body, h)
        fun fail(r: HttpResponse): Nothing =
            throw HttpException(r.code, runCatching { r.json()["error"].str }.getOrNull()?.let { "$it (HTTP ${r.code})" } ?: "HTTP ${r.code}")

        val serversResp = read("ListServers", "{}")
        if (!serversResp.ok) fail(serversResp)
        val coreHost = hostOf(src.url)
        val servers = serversResp.json().arr.associate { sv ->
            val info = sv["info"]
            val name = sv["name"].str.orEmpty()
            val rawAddress = info["address"].str.orEmpty()
            val address = hostOf(rawAddress)
            val external = info["external_address"].str?.let(::hostOf)
            val publicIp = info["public_ip"].str?.let(::hostOf)
            // Priorité à une IP locale : celle de Komodo, puis celle connue par Dozzle pour ce nom d'hôte,
            // puis celle du Core si le serveur est le Periphery local ; l'IP publique en dernier recours.
            val ip = listOfNotNull(address, external).firstOrNull(::isPrivateIp)
                ?: dozzleIps[name.lowercase()]?.takeIf(::isPrivateIp)
                ?: coreHost?.takeIf { address == null && rawAddress.isNotBlank() && isPrivateIp(it) }
                ?: address?.takeIf { !isIp(it) }
                ?: external ?: address ?: publicIp
            sv["id"].str.orEmpty() to (name to ip)
        }
        // Komodo ≥ 2.3 : ListAllContainers ; avant : ListAllDockerContainers.
        var r = read("ListAllContainers", """{"limit":0}""")
        if (!r.ok) r = read("ListAllDockerContainers", "{}")
        if (!r.ok) fail(r)
        return r.json().arr.groupBy { it["server_id"].str.orEmpty() }.map { (serverId, list) ->
            val (serverName, ip) = servers[serverId] ?: ((list.first()["server_name"].str ?: serverId) to null)
            PickHost(
                key = "komodo:${src.id}:$serverName",
                name = serverName,
                sourceId = src.id,
                sourceName = src.name,
                sourceType = SourceType.KOMODO,
                detectedIp = ip ?: hostOf(src.url).takeIf { servers.size <= 1 },
                containers = list.map { c ->
                    PickableContainer(
                        name = c["name"].str.orEmpty(),
                        image = c["image"].str.orEmpty(),
                        running = c["state"].str.equals("running", true),
                        ports = c["ports"].arr.filter { it["Type"].str != "udp" }
                            .mapNotNull { p -> p["PublicPort"].long?.let { it.toInt() to (p["PrivatePort"].long?.toInt() ?: it.toInt()) } }
                            .distinct(),
                        labels = c["labels"].obj.mapValues { it.value.str.orEmpty() },
                    )
                },
            )
        }
    }

    private val WEB_PORTS = listOf(80, 443, 8080, 8000, 3000, 5000, 8443, 9000, 8096, 8989, 7878, 8686, 9696, 32400, 5055, 8123, 2283, 3001, 5678)

    /** Port le plus probablement « web » parmi les ports publiés. */
    fun webPort(c: PickableContainer): Pair<Int, Int>? =
        c.ports.minByOrNull { (pub, priv) -> WEB_PORTS.indexOf(priv).takeIf { it >= 0 } ?: WEB_PORTS.indexOf(pub).takeIf { it >= 0 }?.plus(100) ?: 1000 }

    /** « https » si le port interne est un port TLS classique, sinon « http ». */
    fun defaultScheme(c: PickableContainer): String =
        if (webPort(c)?.second.let { it == 443 || it == 8443 }) "https" else "http"

    /** Nom de base de l'image : « lscr.io/linuxserver/qbittorrent:5.2 » → « qbittorrent ». */
    fun imageBase(image: String): String = image.substringBefore('@').substringAfterLast('/').substringBefore(':').lowercase()

    /** URL au format {scheme}://{IP de l'hôte}:{port publié}. */
    fun urlFor(c: PickableContainer, hostIp: String?, scheme: String): String? {
        val port = webPort(c) ?: return null
        return "$scheme://${hostIp ?: return null}:${port.first}"
    }

    /** Pré-remplit un service à partir d'un conteneur (labels homepage.* prioritaires). */
    fun toService(c: PickableContainer, host: PickHost, hostIp: String?, scheme: String, base: Service? = null): Service {
        val l = c.labels
        val img = imageBase(c.image)
        val integration = Integrations.fromHomepageType(l["homepage.widget.type"])?.let(Integrations::byId)
            ?: Integrations.catalog.firstOrNull { i -> img == i.id || img == slugify(i.name) || img.replace("-", "") == i.id || c.name.equals(i.name, true) }
        val name = l["homepage.name"] ?: integration?.name ?: c.name.replaceFirstChar { it.uppercase() }
        val url = l["homepage.href"] ?: urlFor(c, hostIp, scheme) ?: base?.url ?: "$scheme://${hostIp.orEmpty()}"
        return (base ?: Service(name = name, url = url)).copy(
            name = name,
            url = url,
            description = l["homepage.description"] ?: base?.description.orEmpty(),
            icon = l["homepage.icon"]?.removeSuffix(".png")?.removeSuffix(".svg") ?: integration?.icon ?: slugify(img.ifBlank { c.name }),
            widget = integration?.id ?: base?.widget,
            apiKey = l["homepage.widget.key"] ?: base?.apiKey.orEmpty(),
            container = c.name,
            containerSource = host.sourceId,
            containerHost = host.name.takeIf { host.sourceType == SourceType.KOMODO },
        )
    }
}
