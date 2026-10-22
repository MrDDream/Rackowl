package com.rackowl.integrations

import com.rackowl.data.DetailItem
import com.rackowl.ui.t
import com.rackowl.data.Level
import com.rackowl.data.Service
import com.rackowl.data.Stat
import com.rackowl.data.fmtBytes
import com.rackowl.data.fmtCompact
import com.rackowl.data.fmtDec
import com.rackowl.data.fmtInt
import com.rackowl.data.fmtPercent
import com.rackowl.data.fmtSpeed
import com.rackowl.data.slugify
import com.rackowl.net.FORM_MEDIA
import com.rackowl.net.Http
import com.rackowl.net.HttpException
import com.rackowl.net.arr
import com.rackowl.net.bool
import com.rackowl.net.double
import com.rackowl.net.get
import com.rackowl.net.join
import com.rackowl.net.long
import com.rackowl.net.str
import okhttp3.RequestBody.Companion.toRequestBody

data class WidgetData(
    val stats: List<Stat>,
    val detailsTitle: String? = null,
    val details: List<DetailItem> = emptyList(),
)

/** Ce qu'il faut demander à l'utilisateur pour une intégration. */
enum class Auth { NONE, KEY, LOGIN, KEY_OR_LOGIN, KEY_SECRET }

/**
 * Une intégration (« widget » au sens de Homepage) : lit l'API d'un service
 * pour en tirer 2 à 4 statistiques et, éventuellement, une liste de détails.
 */
abstract class Integration(
    val id: String,
    val name: String,
    /** Catégorie affichée ; une fonction pour suivre la langue courante. */
    private val tagText: () -> String,
    val auth: Auth = Auth.KEY,
    private val keyLabel: () -> String = { t("API key", "Clé API") },
) {
    val tag: String get() = tagText()
    val keyText: String get() = keyLabel()

    val icon: String get() = slugify(name)

    open suspend fun fetch(http: Http, s: Service): WidgetData = WidgetData(emptyList())

    /** Détection sans authentification. Renvoie une courte explication si reconnu. */
    open suspend fun detect(http: Http, url: String, title: String): String? =
        if (title.contains(name, ignoreCase = true)) t("Recognised from the page title", "Reconnu via le titre de la page") else null
}

private fun warnIf(cond: Boolean) = if (cond) Level.WARN else Level.NORMAL
private fun base(s: Service) = s.url.trimEnd('/')

// ---------------------------------------------------------------- *arr

/** Liste détaillée d'une appli *arr : (chemin API, titre de section, nom, fichiers présents, total). */
private class ArrList(
    val path: String,
    val title: () -> String,
    val countLabel: () -> String,
    val name: (kotlinx.serialization.json.JsonElement) -> String?,
    val have: (kotlinx.serialization.json.JsonElement) -> Long?,
    val total: (kotlinx.serialization.json.JsonElement) -> Long?,
)

private class Arr(
    id: String, name: String, tag: () -> String, val api: String, val missingLabel: () -> String, val list: ArrList? = null,
) : Integration(id, name, tag) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val h = mapOf("X-Api-Key" to s.apiKey)
        val missing = http.getJson(join(base(s), "/api/$api/wanted/missing?pageSize=1"), h)["totalRecords"].long
        val queue = http.getJson(join(base(s), "/api/$api/queue/status"), h)["totalCount"].long
        val stats = mutableListOf(
            Stat(fmtInt(missing), missingLabel(), warnIf((missing ?: 0) > 0)),
            Stat(fmtInt(queue), t("Queued", "En file")),
        )
        val l = list ?: return WidgetData(stats)
        // Liste complète (séries, artistes…), triée par nom, avec la proportion de fichiers présents.
        val items = http.getJson(join(base(s), "/api/$api/${l.path}"), h).arr
        stats += Stat(fmtInt(items.size), l.countLabel())
        val details = items.mapNotNull { e ->
            val title = l.name(e) ?: return@mapNotNull null
            val have = l.have(e) ?: 0; val total = l.total(e) ?: 0
            DetailItem(title, "$have / $total", if (total > 0) (have.toFloat() / total).coerceIn(0f, 1f) else null)
        }.sortedBy { it.title.lowercase() }
        return WidgetData(stats, l.title(), details)
    }
}

private object Radarr : Integration("radarr", "Radarr", { t("Movies", "Films") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val h = mapOf("X-Api-Key" to s.apiKey)
        val movies = http.getJson(join(base(s), "/api/v3/movie"), h).arr
        val missing = movies.count { it["monitored"].bool == true && it["hasFile"].bool == false && it["isAvailable"].bool != false }
        val queue = http.getJson(join(base(s), "/api/v3/queue/status"), h)["totalCount"].long
        val list = movies.mapNotNull { m ->
            val title = m["title"].str ?: return@mapNotNull null
            val year = m["year"].long?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
            DetailItem(title + year, if (m["hasFile"].bool == true) t("Downloaded", "Téléchargé") else t("Missing", "Manquant"))
        }.sortedBy { it.title.lowercase() }
        return WidgetData(listOf(
            Stat(fmtInt(missing), t("Missing", "Manquants"), warnIf(missing > 0)),
            Stat(fmtInt(queue), t("Queued", "En file")),
            Stat(fmtInt(movies.size), t("Movies", "Films")),
        ), t("Movies", "Films"), list)
    }
}

private object Prowlarr : Integration("prowlarr", "Prowlarr", { t("Indexers", "Indexeurs") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val h = mapOf("X-Api-Key" to s.apiKey)
        val indexers = http.getJson(join(base(s), "/api/v1/indexer"), h).arr
        val failing = http.getJson(join(base(s), "/api/v1/indexerstatus"), h).arr.size
        return WidgetData(listOf(
            Stat(fmtInt(indexers.count { it["enable"].bool == true }), t("Active", "Actifs")),
            Stat(fmtInt(failing), t("Failing", "En échec"), warnIf(failing > 0)),
        ))
    }
}

private object Bazarr : Integration("bazarr", "Bazarr", { t("Subtitles", "Sous-titres") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val j = http.getJson(join(base(s), "/api/badges"), mapOf("X-API-KEY" to s.apiKey))
        return WidgetData(listOf(
            Stat(fmtInt(j["episodes"].long), t("Episodes w/o subs", "Épisodes sans ST")),
            Stat(fmtInt(j["movies"].long), t("Movies w/o subs", "Films sans ST")),
        ))
    }
}

// ---------------------------------------------------------------- Médias

private object Jellyfin : Integration("jellyfin", "Jellyfin", { t("Media", "Médias") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val h = mapOf("X-Emby-Token" to s.apiKey)
        val sessions = http.getJson(join(base(s), "/Sessions?activeWithinSeconds=600"), h).arr
            .filter { it["NowPlayingItem"] != null }
        val counts = http.getJson(join(base(s), "/Items/Counts"), h)
        val playing = sessions.map { se ->
            val item = se["NowPlayingItem"]
            val series = item["SeriesName"].str
            val title = if (series != null) {
                val sn = item["ParentIndexNumber"].long; val en = item["IndexNumber"].long
                "$series " + (if (sn != null && en != null) "S%02dE%02d".format(sn, en) else item["Name"].str.orEmpty())
            } else item["Name"].str.orEmpty()
            val pos = se["PlayState"]["PositionTicks"].double ?: 0.0
            val total = item["RunTimeTicks"].double ?: 0.0
            DetailItem(title, se["DeviceName"].str ?: se["Client"].str.orEmpty(), if (total > 0) (pos / total).toFloat() else null)
        }
        return WidgetData(
            listOf(
                Stat(fmtInt(sessions.size), t("Now playing", "Lectures en cours")),
                Stat(fmtInt(counts["MovieCount"].long), t("Movies", "Films")),
                Stat(fmtInt(counts["SeriesCount"].long), t("Series", "Séries")),
                Stat(fmtInt(counts["EpisodeCount"].long), t("Episodes", "Épisodes")),
            ),
            detailsTitle = t("Now playing", "Lectures en cours"), details = playing,
        )
    }

    override suspend fun detect(http: Http, url: String, title: String): String? {
        val r = runCatching { http.get(join(url, "/System/Info/Public")) }.getOrNull()
        val product = runCatching { r?.json()?.get("ProductName").str }.getOrNull()
        if (r?.ok == true && product?.contains("Jellyfin") == true) {
            return t("Recognised via /System/Info/Public · Jellyfin ", "Reconnu via /System/Info/Public · Jellyfin ") + (runCatching { r.json()["Version"].str }.getOrNull() ?: "")
        }
        return null
    }
}

private object Jellyseerr : Integration("jellyseerr", "Jellyseerr", { t("Requests", "Demandes") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val j = http.getJson(join(base(s), "/api/v1/request/count"), mapOf("X-Api-Key" to s.apiKey))
        val pending = j["pending"].long ?: 0
        return WidgetData(listOf(
            Stat(fmtInt(pending), t("Pending", "En attente"), warnIf(pending > 0)),
            Stat(fmtInt(j["processing"].long), t("In progress", "En cours")),
            Stat(fmtInt(j["available"].long), t("Available", "Disponibles")),
        ))
    }
}

private object Tautulli : Integration("tautulli", "Tautulli", { t("Plex stats", "Stats Plex") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val d = http.getJson(join(base(s), "/api/v2?apikey=${s.apiKey}&cmd=get_activity"))["response"]["data"]
        val details = d["sessions"].arr.map {
            DetailItem(it["full_title"].str.orEmpty(), it["player"].str.orEmpty(), it["progress_percent"].double?.let { p -> (p / 100).toFloat() })
        }
        return WidgetData(listOf(Stat(fmtInt(d["stream_count"].long), t("Now playing", "Lectures en cours"))), t("Now playing", "Lectures en cours"), details)
    }
}

// ---------------------------------------------------------------- Téléchargements

private object QBittorrent : Integration("qbittorrent", "qBittorrent", { "Torrent" }, Auth.LOGIN) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val login = http.request(
            join(base(s), "/api/v2/auth/login"), "POST", mapOf("Referer" to base(s)),
            "username=${enc(s.username)}&password=${enc(s.password)}".toRequestBody(FORM_MEDIA),
        )
        val sid = login.headers.entries.firstOrNull { it.key.equals("set-cookie", true) }?.value
            ?.firstOrNull { it.startsWith("SID=") }?.substringBefore(';')
        if (login.code == 403 || login.body.trim() == "Fails.") throw HttpException(403, t("Credentials rejected", "Identifiants refusés"))
        val h = if (sid != null) mapOf("Cookie" to sid) else emptyMap()
        val t = http.getJson(join(base(s), "/api/v2/transfer/info"), h)
        val dl = fmtSpeed(t["dl_info_speed"].long); val up = fmtSpeed(t["up_info_speed"].long)
        val active = http.getJson(join(base(s), "/api/v2/torrents/info?filter=downloading"), h).arr
        return WidgetData(
            listOf(Stat(dl.first, "↓ ${dl.second}"), Stat(up.first, "↑ ${up.second}"), Stat(fmtInt(active.size), t("In progress", "En cours"))),
            t("Downloads", "Téléchargements"),
            active.take(6).map { DetailItem(it["name"].str.orEmpty(), fmtSpeed(it["dlspeed"].long).let { sp -> "${sp.first} ${sp.second}" }, it["progress"].double?.toFloat()) },
        )
    }
}

private object Transmission : Integration("transmission", "Transmission", { "Torrent" }, Auth.LOGIN) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val url = join(base(s), "/transmission/rpc")
        val auth = if (s.username.isNotBlank()) mapOf("Authorization" to Http.basic(s.username, s.password)) else emptyMap()
        val body = """{"method":"session-stats"}"""
        var r = http.postJson(url, body, auth)
        if (r.code == 409) {
            val sid = r.header("X-Transmission-Session-Id").orEmpty()
            r = http.postJson(url, body, auth + ("X-Transmission-Session-Id" to sid))
        }
        if (!r.ok) throw HttpException(r.code, "HTTP ${r.code}")
        val a = r.json()["arguments"]
        val dl = fmtSpeed(a["downloadSpeed"].long); val up = fmtSpeed(a["uploadSpeed"].long)
        return WidgetData(listOf(Stat(dl.first, "↓ ${dl.second}"), Stat(up.first, "↑ ${up.second}"), Stat(fmtInt(a["activeTorrentCount"].long), t("Active", "Actifs"))))
    }
}

private object Sabnzbd : Integration("sabnzbd", "SABnzbd", { "Usenet" }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val q = http.getJson(join(base(s), "/api?mode=queue&output=json&apikey=${s.apiKey}"))["queue"]
        return WidgetData(listOf(
            Stat(q["speed"].str ?: "0", t("Speed", "Débit")),
            Stat(fmtInt(q["noofslots"].long), t("Queued", "En file")),
            Stat(q["timeleft"].str ?: "—", t("Remaining", "Restant")),
        ))
    }
}

// ---------------------------------------------------------------- Infra

private object Portainer : Integration("portainer", "Portainer", { "Docker" }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val envs = http.getJson(join(base(s), "/api/endpoints"), mapOf("X-API-Key" to s.apiKey)).arr
        var running = 0L; var stopped = 0L; var unhealthy = 0L
        envs.forEach { e ->
            val snap = e["Snapshots"][0]
            running += snap["RunningContainerCount"].long ?: 0
            stopped += snap["StoppedContainerCount"].long ?: 0
            unhealthy += snap["UnhealthyContainerCount"].long ?: 0
        }
        return WidgetData(listOf(
            Stat(fmtInt(running), t("Active", "Actifs")),
            Stat(fmtInt(stopped), t("Stopped", "Arrêtés"), warnIf(stopped > 0)),
            Stat(fmtInt(unhealthy), t("Unhealthy", "En erreur"), if (unhealthy > 0) Level.DOWN else Level.NORMAL),
        ))
    }

    override suspend fun detect(http: Http, url: String, title: String): String? {
        val r = runCatching { http.get(join(url, "/api/system/status")) }.getOrNull()
        val v = runCatching { r?.json()?.get("Version").str }.getOrNull()
        return if (r?.ok == true && v != null) t("Recognised via /api/system/status · Portainer $v", "Reconnu via /api/system/status · Portainer $v") else super.detect(http, url, title)
    }
}

private object Proxmox : Integration("proxmox", "Proxmox", { "Virtualisation" }, keyLabel = { t("API token (user@pam!id=secret)", "Jeton API (user@pam!id=secret)") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val res = http.getJson(join(base(s), "/api2/json/cluster/resources"), mapOf("Authorization" to "PVEAPIToken=${s.apiKey}"))["data"].arr
        fun count(type: String) = res.filter { it["type"].str == type && it["template"].long != 1L }
        val vms = count("qemu"); val lxc = count("lxc"); val nodes = count("node").filter { it["status"].str == "online" }
        // Jeton à « Privilege Separation » sans droits : Proxmox ne renvoie que le nom des nœuds.
        if (vms.isEmpty() && lxc.isEmpty() && nodes.isNotEmpty() && nodes.all { it["maxmem"].double == null }) {
            throw HttpException(403, t(
                "Token has no permissions: give it the PVEAuditor role on /",
                "Jeton sans droits : donne-lui le rôle PVEAuditor sur /",
            ))
        }
        // CPU et RAM des nœuds : visibles même avec un jeton limité aux nœuds.
        val cpu = nodes.mapNotNull { it["cpu"].double }.takeIf { it.isNotEmpty() }?.average()?.times(100)
        val mem = nodes.sumOf { it["mem"].double ?: 0.0 }; val maxMem = nodes.sumOf { it["maxmem"].double ?: 0.0 }
        val stats = mutableListOf(
            Stat("${vms.count { it["status"].str == "running" }}/${vms.size}", "VM"),
            Stat("${lxc.count { it["status"].str == "running" }}/${lxc.size}", "LXC"),
            Stat(fmtPercent(cpu), "CPU", warnIf((cpu ?: 0.0) > 90)),
            Stat(fmtPercent(if (maxMem > 0) mem / maxMem * 100 else null), "RAM", warnIf(maxMem > 0 && mem / maxMem > 0.9)),
        )
        if (count("node").size > 1) stats += Stat("${nodes.size}/${count("node").size}", t("Nodes", "Nœuds"))
        val guests = (vms + lxc).sortedBy { it["name"].str?.lowercase() }.map { g ->
            val running = g["status"].str == "running"
            val mem = g["mem"].double; val maxMem = g["maxmem"].double
            DetailItem(
                "${g["name"].str ?: g["vmid"].long} · ${if (g["type"].str == "lxc") "LXC" else "VM"}",
                if (running) "CPU " + fmtPercent(g["cpu"].double?.times(100)) + " · RAM " + fmtBytes(mem?.toLong(), binary = true) + " / " + fmtBytes(maxMem?.toLong(), binary = true)
                else t("Stopped", "Arrêtée"),
                // Barre : part de la RAM allouée réellement utilisée.
                if (running && mem != null && maxMem != null && maxMem > 0) (mem / maxMem).toFloat() else null,
            )
        }
        return WidgetData(stats, t("Guests", "Machines"), guests)
    }
}

private object PiHole : Integration("pihole", "Pi-hole", { "DNS" }, keyLabel = { t("Password / API key", "Mot de passe / clé API") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        // Pi-hole v6 : session puis /api/stats/summary
        val auth = runCatching { http.postJson(join(base(s), "/api/auth"), """{"password":${quote(s.apiKey)}}""") }.getOrNull()
        if (auth != null && auth.ok) {
            val sid = auth.json()["session"]["sid"].str
            val q = http.getJson(join(base(s), "/api/stats/summary"), sid?.let { mapOf("X-FTL-SID" to it) } ?: emptyMap())["queries"]
            runCatching { http.request(join(base(s), "/api/auth"), "DELETE", sid?.let { mapOf("X-FTL-SID" to it) } ?: emptyMap()) }
            return WidgetData(listOf(
                Stat(fmtCompact(q["total"].long), t("Queries", "Requêtes")),
                Stat(fmtPercent(q["percent_blocked"].double), t("Blocked", "Bloquées")),
            ))
        }
        // Pi-hole v5
        val j = http.getJson(join(base(s), "/admin/api.php?summaryRaw&auth=${s.apiKey}"))
        return WidgetData(listOf(
            Stat(fmtCompact(j["dns_queries_today"].long), t("Queries", "Requêtes")),
            Stat(fmtPercent(j["ads_percentage_today"].double), t("Blocked", "Bloquées")),
        ))
    }
}

private object AdGuard : Integration("adguard", "AdGuard Home", { "DNS" }, Auth.LOGIN) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val j = http.getJson(join(base(s), "/control/stats"), mapOf("Authorization" to Http.basic(s.username, s.password)))
        val total = j["num_dns_queries"].long ?: 0; val blocked = j["num_blocked_filtering"].long ?: 0
        return WidgetData(listOf(
            Stat(fmtCompact(total), t("Queries", "Requêtes")),
            Stat(fmtPercent(if (total > 0) blocked * 100.0 / total else 0.0), t("Blocked", "Bloquées")),
        ))
    }
}

private object Traefik : Integration("traefik", "Traefik", { "Proxy" }, Auth.NONE) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val h = if (s.username.isNotBlank()) mapOf("Authorization" to Http.basic(s.username, s.password)) else emptyMap()
        val j = http.getJson(join(base(s), "/api/overview"), h)["http"]
        val errors = (j["routers"]["errors"].long ?: 0) + (j["services"]["errors"].long ?: 0)
        return WidgetData(listOf(
            Stat(fmtInt(j["routers"]["total"].long), t("Routers", "Routeurs")),
            Stat(fmtInt(j["services"]["total"].long), "Services"),
            Stat(fmtInt(errors), t("Errors", "Erreurs"), warnIf(errors > 0)),
        ))
    }
}

private object Glances : Integration("glances", "Glances", { t("System", "Système") }, Auth.NONE) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val r = GlancesClient.read(http, base(s), s.username, s.password)
        return WidgetData(listOf(
            Stat(fmtPercent(r.cpu), "CPU"),
            Stat(fmtPercent(r.mem), "RAM", warnIf((r.mem ?: 0.0) > 90)),
            Stat(fmtPercent(r.disk), t("Disk", "Disque"), warnIf((r.disk ?: 0.0) > 80)),
        ))
    }
}

// ---------------------------------------------------------------- Fichiers / outils

private object Nextcloud : Integration("nextcloud", "Nextcloud", { t("Files", "Fichiers") }, Auth.LOGIN) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val status = http.getJson(join(base(s), "/status.php"))
        val stats = mutableListOf(
            Stat(status["versionstring"].str ?: "—", "Version"),
            Stat(if (status["maintenance"].bool == true) t("Yes", "Oui") else t("No", "Non"), "Maintenance", warnIf(status["maintenance"].bool == true)),
        )
        if (s.username.isNotBlank()) {
            val d = http.getJson(
                join(base(s), "/ocs/v2.php/apps/serverinfo/api/v1/info?format=json"),
                mapOf("Authorization" to Http.basic(s.username, s.password), "OCS-APIRequest" to "true"),
            )["ocs"]["data"]
            stats += Stat(fmtCompact(d["nextcloud"]["storage"]["num_files"].long), t("Files", "Fichiers"))
            stats += Stat(fmtInt(d["activeUsers"]["last5minutes"].long), t("Active (5 min)", "Actifs (5 min)"))
        }
        return WidgetData(stats)
    }

    override suspend fun detect(http: Http, url: String, title: String): String? {
        val r = runCatching { http.get(join(url, "/status.php")) }.getOrNull()
        val v = runCatching { r?.json()?.get("versionstring").str }.getOrNull()
        return if (r?.ok == true && v != null) t("Recognised via /status.php · Nextcloud $v", "Reconnu via /status.php · Nextcloud $v") else super.detect(http, url, title)
    }
}

private object Immich : Integration("immich", "Immich", { "Photos" }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val j = http.getJson(join(base(s), "/api/server/statistics"), mapOf("x-api-key" to s.apiKey))
        return WidgetData(listOf(
            Stat(fmtCompact(j["photos"].long), "Photos"),
            Stat(fmtCompact(j["videos"].long), t("Videos", "Vidéos")),
            Stat(fmtBytes(j["usage"].long), t("Storage", "Stockage")),
        ))
    }
}

private object Paperless : Integration("paperless", "Paperless-ngx", { "Documents" }, keyLabel = { t("API token", "Jeton API") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val j = http.getJson(join(base(s), "/api/statistics/"), mapOf("Authorization" to "Token ${s.apiKey}"))
        val inbox = j["documents_inbox"].long ?: 0
        return WidgetData(listOf(
            Stat(fmtInt(j["documents_total"].long), "Documents"),
            Stat(fmtInt(inbox), t("Inbox", "Boîte de réception"), warnIf(inbox > 0)),
        ))
    }
}

private object HomeAssistant : Integration("homeassistant", "Home Assistant", { t("Home automation", "Domotique") }, keyLabel = { t("Long-lived token", "Jeton longue durée") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val states = http.getJson(join(base(s), "/api/states"), mapOf("Authorization" to "Bearer ${s.apiKey}")).arr
        fun on(domain: String) = states.count { it["entity_id"].str?.startsWith("$domain.") == true && it["state"].str == "on" }
        val unavailable = states.count { it["state"].str == "unavailable" }
        return WidgetData(listOf(
            Stat(fmtInt(states.size), t("Entities", "Entités")),
            Stat(fmtInt(on("light")), t("Lights on", "Lumières allumées")),
            Stat(fmtInt(unavailable), t("Unavailable", "Indisponibles"), warnIf(unavailable > 0)),
        ))
    }
}

private object Gotify : Integration("gotify", "Gotify", { "Notifications" }, keyLabel = { t("Client token", "Jeton client") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val h = mapOf("X-Gotify-Key" to s.apiKey)
        val apps = http.getJson(join(base(s), "/application"), h).arr
        val msgs = http.getJson(join(base(s), "/message?limit=100"), h)["messages"].arr
        return WidgetData(listOf(Stat(fmtInt(apps.size), "Apps"), Stat(fmtInt(msgs.size), "Messages")))
    }

    override suspend fun detect(http: Http, url: String, title: String): String? {
        val r = runCatching { http.get(join(url, "/version")) }.getOrNull()
        val v = runCatching { r?.json()?.get("version").str }.getOrNull()
        return if (r?.ok == true && v != null && runCatching { r.json()["buildDate"] }.getOrNull() != null)
            t("Recognised via /version · Gotify $v", "Reconnu via /version · Gotify $v") else super.detect(http, url, title)
    }
}

private object UptimeKuma : Integration("uptimekuma", "Uptime Kuma", { "Monitoring" }, keyLabel = { t("API key (metrics)", "Clé API (métriques)") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val r = http.get(join(base(s), "/metrics"), mapOf("Authorization" to Http.basic("", s.apiKey)))
        if (!r.ok) throw HttpException(r.code, "HTTP ${r.code}")
        val values = r.body.lineSequence().filter { it.startsWith("monitor_status{") }.map { it.substringAfterLast(' ').trim() }.toList()
        val down = values.count { it == "0" }
        return WidgetData(listOf(
            Stat(fmtInt(values.count { it == "1" }), t("Online", "En ligne")),
            Stat(fmtInt(down), t("Offline", "Hors ligne"), if (down > 0) Level.DOWN else Level.NORMAL),
        ))
    }
}

private object SpeedtestTracker : Integration("speedtest", "Speedtest Tracker", { t("Network", "Réseau") }, keyLabel = { t("API token (v1+)", "Jeton API (v1+)") }) {
    override suspend fun fetch(http: Http, s: Service): WidgetData {
        val h = if (s.apiKey.isNotBlank()) mapOf("Authorization" to "Bearer ${s.apiKey}", "Accept" to "application/json") else emptyMap()
        val r = http.get(join(base(s), "/api/v1/results/latest"), h).takeIf { it.ok } ?: http.get(join(base(s), "/api/speedtest/latest"), h)
        if (!r.ok) throw HttpException(r.code, "HTTP ${r.code}")
        val d = r.json()["data"]
        // v1 : octets/s (download_bits en bits), legacy : Mbit/s
        val down = d["download_bits"].double?.let { it / 1e6 } ?: d["download"].double
        val up = d["upload_bits"].double?.let { it / 1e6 } ?: d["upload"].double
        return WidgetData(listOf(
            Stat(down?.let { fmtDec(it, 0) } ?: "—", "↓ Mbit/s"),
            Stat(up?.let { fmtDec(it, 0) } ?: "—", "↑ Mbit/s"),
            Stat(d["ping"].double?.let { fmtDec(it, 0) + " ms" } ?: "—", "Ping"),
        ))
    }
}

/** Services reconnus mais sans API exploitée : surveillés par ping uniquement. */
private class PingOnly(id: String, name: String, tag: () -> String) : Integration(id, name, tag, Auth.NONE)

object Integrations {
    const val NONE_ID = "none"

    val catalog: List<Integration> = listOf(
        Arr(
            "sonarr", "Sonarr", { t("Series", "Séries") }, "v3", { t("Missing", "Manquants") },
            ArrList(
                "series", { t("Series · episodes", "Séries · épisodes") }, { t("Series", "Séries") },
                { it["title"].str }, { it["statistics"]["episodeFileCount"].long }, { it["statistics"]["episodeCount"].long },
            ),
        ),
        Radarr,
        Arr(
            "lidarr", "Lidarr", { t("Music", "Musique") }, "v1", { t("Missing", "Manquants") },
            ArrList(
                "artist", { t("Artists · tracks", "Artistes · titres") }, { t("Artists", "Artistes") },
                { it["artistName"].str }, { it["statistics"]["trackFileCount"].long }, { it["statistics"]["totalTrackCount"].long },
            ),
        ),
        Prowlarr, Bazarr, Jellyfin, Jellyseerr,
        Tautulli, QBittorrent, Transmission, Sabnzbd,
        Portainer, Proxmox, PiHole, AdGuard,
        Nextcloud, Immich, Paperless, HomeAssistant, Gotify, UptimeKuma,
        Glances, Traefik,
        PingOnly("npm", "Nginx Proxy Manager", { "Proxy" }),
        SpeedtestTracker,
        PingOnly("n8n", "n8n", { t("Automations", "Automatisations") }),
        PingOnly("ntfy", "ntfy", { "Notifications" }),
        PingOnly("dozzle", "Dozzle", { "Logs" }),
    ) + widgetsA + widgetsB + widgetsC + widgetsD +
        (pingOnlyA + pingOnlyB + pingOnlyC + pingOnlyD).map { (id, name) -> PingOnly(id, name) { t("Ping only", "Ping seul") } }

    fun byId(id: String?): Integration? = catalog.firstOrNull { it.id == id }

    fun search(q: String): List<Integration> {
        val t = q.trim().lowercase()
        return if (t.isEmpty()) catalog
        else catalog.filter { it.name.lowercase().contains(t) || it.tag.lowercase().contains(t) || it.id.contains(t) }
    }

    /** Correspondance avec les types de widget de Homepage (services.yaml). */
    fun fromHomepageType(type: String?): String? = when (type?.lowercase()) {
        null -> null
        "pihole" -> "pihole"
        "adguard" -> "adguard"
        "uptimekuma" -> "uptimekuma"
        "homeassistant" -> "homeassistant"
        "speedtest" -> "speedtest"
        "npm" -> "npm"
        "paperlessngx" -> "paperless"
        "seerr" -> "jellyseerr"
        else -> byId(type.lowercase())?.id
    }

    data class Detection(val integration: Integration, val how: String)

    /** Essaie de reconnaître le service derrière une URL, sans clé. */
    suspend fun detect(http: Http, url: String): Detection? {
        if (url.isBlank()) return null
        val page = runCatching { http.get(url) }.getOrNull()
        val title = page?.body?.let { Regex("<title>([^<]*)</title>", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }.orEmpty()
        // Les sondes spécifiques d'abord (plus fiables que le titre).
        for (i in listOf(Jellyfin, Nextcloud, Portainer, Gotify)) i.detect(http, url, "")?.let { return Detection(i, it) }
        if (title.isNotBlank()) for (i in catalog.sortedByDescending { it.name.length }) {
            i.detect(http, url, title)?.let { return Detection(i, t("Recognised from the page title “${title.trim()}”", "Reconnu via le titre de la page « ${title.trim()} »")) }
        }
        return null
    }
}

private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
