package com.rackowl.integrations

import com.rackowl.data.Level
import com.rackowl.data.Stat
import com.rackowl.data.fmtBytes
import com.rackowl.data.fmtDec
import com.rackowl.data.fmtDuration
import com.rackowl.data.fmtPercent
import com.rackowl.net.arr
import com.rackowl.net.bool
import com.rackowl.net.double
import com.rackowl.net.get
import com.rackowl.net.long
import com.rackowl.net.obj
import com.rackowl.net.str
import com.rackowl.ui.t
import java.time.Instant
import java.time.LocalDate

// ------------------------------------------------------------------ lot S → Z, et widgets Homepage déjà connus de Rackowl

internal val widgetsD: List<Integration> = listOf(
    W("komodo", "Komodo", { t("Deployment", "Déploiement") }, Auth.KEY_SECRET) {
        val h = mapOf("x-api-key" to s.apiKey, "x-api-secret" to s.password)
        val servers = post("/read/GetServersSummary", "{}", h)
        val stacks = post("/read/GetStacksSummary", "{}", h)
        val containers = post("/read/GetDockerContainersSummary", "{}", h)
        val unhealthy = (stacks["unhealthy"].long ?: 0) + (containers["unhealthy"].long ?: 0)
        stats(
            Stat("${servers["healthy"].long ?: 0} / ${servers["total"].long ?: 0}", t("Servers", "Serveurs")),
            Stat("${stacks["running"].long ?: 0} / ${stacks["total"].long ?: 0}", "Stacks"),
            Stat("${containers["running"].long ?: 0} / ${containers["total"].long ?: 0}", t("Containers", "Conteneurs")),
            num(unhealthy, "Unhealthy", "En erreur", downIfPositive(unhealthy)),
        )
    },
    W("plex", "Plex", { t("Media", "Médias") }, keyLabel = { "X-Plex-Token" }) {
        val h = mapOf("X-Plex-Token" to s.apiKey, "Accept" to "application/json")
        stats(
            num(get("/status/sessions", h)["MediaContainer"]["size"].long, "Now playing", "Lectures en cours"),
            num(get("/library/sections", h)["MediaContainer"]["size"].long, "Libraries", "Bibliothèques"),
        )
    },
    W("overseerr", "Overseerr", { t("Requests", "Demandes") }) {
        val j = get("/api/v1/request/count", key("X-Api-Key"))
        stats(num(j["pending"].long, "Pending", "En attente", warnIfPositive(j["pending"].long)), num(j["processing"].long, "Processing", "En cours"), num(j["available"].long, "Available", "Disponibles"))
    },
    W("strelaysrv", "Syncthing relay", { t("Relay", "Relais") }, Auth.NONE) {
        val st = get("/status")
        stats(num(st["numActiveSessions"].long, "Sessions", "Sessions"), num(st["numConnections"].long, "Connections", "Connexions"), Stat(fmtBytes(st["bytesProxied"].long), t("Relayed", "Relayé")))
    },
    W("swagdashboard", "SWAG dashboard", { "Proxy" }, Auth.NONE) {
        val st = get("/?stats=true")
        stats(num(st["proxied"].long, "Proxied", "Proxifiés"), num(st["auth"].long, "With auth", "Authentifiés"), num(st["outdated"].long, "Outdated", "Obsolètes", warnIfPositive(st["outdated"].long)), num(st["banned"].long, "Banned", "Bannis"))
    },
    W("syncthing", "Syncthing", { t("Sync", "Synchronisation") }) {
        val h = key("X-API-Key")
        val conns = get("/rest/system/connections", h)["connections"].obj.values
        val comp = get("/rest/db/completion", h)
        val errors = get("/rest/system/error", h)["errors"].arr.size
        stats(
            Stat("${conns.count { it["connected"].bool == true }} / ${conns.size}", t("Connected", "Connectés")),
            Stat(fmtPercent(comp["completion"].double), t("Synced", "Synchronisé")),
            num(errors, "Errors", "Erreurs", warnIfPositive(errors)),
            Stat(fmtBytes(comp["globalBytes"].long), t("Storage", "Stockage")),
        )
    },
    W("tandoor", "Tandoor", { t("Recipes", "Recettes") }, keyLabel = { t("API token", "Jeton API") }) {
        val space = get("/api/space/", bearer).let { it["results"][0].takeUnless { r -> r.isNull } ?: it[0] }
        stats(num(space["recipe_count"].long, "Recipes", "Recettes"), num(space["user_count"].long, "Users", "Utilisateurs"), num(get("/api/keyword/", bearer)["count"].long, "Keywords", "Mots-clés"))
    },
    W("tdarr", "Tdarr", { t("Transcoding", "Transcodage") }, Auth.NONE) {
        val d = post("/api/v2/cruddb", """{"data":{"collection":"StatisticsJSONDB","mode":"getById","docID":"statistics"}}""")
        fun count(n: Int) = d["table${n}ViewableCount"].long ?: d["table${n}Count"].long ?: 0
        val errored = count(3) + count(6)
        stats(
            num(count(1) + count(4), "Queue", "File"),
            num(count(2) + count(5), "Processed", "Traités"),
            num(errored, "Errored", "En erreur", warnIfPositive(errored)),
            Stat(fmtBytes(((d["sizeDiff"].double ?: 0.0) * 1e9).toLong()), t("Saved", "Économisé")),
        )
    },
    W("technitium", "Technitium DNS", { "DNS" }, keyLabel = { t("API token", "Jeton API") }) {
        val st = get("/api/dashboard/stats/get?token=${s.apiKey}&type=LastDay&utc=true")["response"]["stats"]
        stats(num(st["totalQueries"].long, "Queries (24 h)", "Requêtes (24 h)"), num(st["totalBlocked"].long, "Blocked", "Bloquées"), num(st["totalCached"].long, "Cached", "En cache"))
    },
    W("tracearr", "Tracearr", { t("Media stats", "Stats médias") }, keyLabel = { t("API key", "Clé API") }) {
        val sum = get("/api/v1/public/streams", bearer)["summary"]
        stats(num(sum["total"].long, "Streams", "Flux"), num(sum["transcodes"].long, "Transcodes", "Transcodages"), num(sum["directPlays"].long, "Direct play", "Lecture directe"))
    },
    W("trilium", "Trilium", { t("Notes", "Notes") }, keyLabel = { t("ETAPI token", "Jeton ETAPI") }) {
        val m = get("/etapi/metrics?format=json", mapOf("Authorization" to s.apiKey))
        stats(num(m["database"]["activeNotes"].long, "Notes", "Notes"), Stat(fmtBytes(m["statistics"]["databaseSizeBytes"].long), t("Database", "Base")), Stat(m["version"]["app"].str ?: "—", "Version"))
    },
    W("truenas", "TrueNAS", { "NAS" }, Auth.KEY_OR_LOGIN) {
        val h = if (s.apiKey.isNotBlank()) bearer else basic
        val info = get("/api/v2.0/system/info", h)
        val alerts = runCatching { get("/api/v2.0/alert/list", h).arr.count { it["dismissed"].bool != true } }.getOrNull()
        stats(
            Stat(info["loadavg"][0].double?.let { fmtDec(it, 2) } ?: "—", t("Load", "Charge")),
            Stat(info["uptime_seconds"].double?.let { fmtDuration((it * 1000).toLong()) } ?: "—", "Uptime"),
            num(alerts, "Alerts", "Alertes", warnIfPositive(alerts)),
        )
    },
    W("tubearchivist", "Tube Archivist", { "YouTube" }) {
        val h = mapOf("Authorization" to "Token ${s.apiKey}")
        stats(
            num(get("/api/stats/download/", h)["pending"].long, "Pending", "En attente"),
            num(get("/api/stats/video/", h)["doc_count"].long, "Videos", "Vidéos"),
            num(get("/api/stats/channel/", h)["doc_count"].long, "Channels", "Chaînes"),
        )
    },
    W("unifi", "UniFi", { t("Network", "Réseau") }, Auth.LOGIN) {
        val body = """{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}"""
        // Console UniFi OS (UDM, Cloud Key Gen2+) puis contrôleur classique.
        val os = http.postJson(u("/api/auth/login"), body)
        val (prefix, login) = if (os.ok) "/proxy/network" to os else "" to check(http.postJson(u("/api/login"), body))
        val h = buildMap {
            put("Cookie", cookies(login))
            login.header("x-csrf-token")?.let { put("x-csrf-token", it) }
        }
        val site = get("$prefix/api/stat/sites", h)["data"].arr.firstOrNull { it["name"].str == "default" } ?: get("$prefix/api/stat/sites", h)["data"][0]
        val health = site["health"].arr
        fun sub(n: String) = health.firstOrNull { it["subsystem"].str == n }
        val wan = sub("wan")
        stats(
            Stat(if (wan["status"].str == "ok") t("Up", "OK") else t("Down", "Coupé"), "WAN", if (wan != null && wan["status"].str != "ok") Level.DOWN else Level.NORMAL),
            num(sub("lan")["num_user"].long, "LAN users", "Clients LAN"),
            num(sub("wlan")["num_user"].long, "Wi-Fi users", "Clients Wi-Fi"),
            num((sub("lan")["num_adopted"].long ?: 0) + (sub("wlan")["num_adopted"].long ?: 0), "Devices", "Appareils"),
        )
    },
    W("unmanic", "Unmanic", { t("Transcoding", "Transcodage") }, Auth.NONE) {
        val workers = get("/unmanic/api/v2/workers/status")["workers_status"].arr
        val pending = runCatching { post("/unmanic/api/v2/pending/tasks", """{"start":0,"length":1}""")["recordsTotal"].long }.getOrNull()
        stats(Stat("${workers.count { it["idle"].bool == false }} / ${workers.size}", t("Active workers", "Workers actifs")), num(pending, "Pending", "En attente"))
    },
    W("uptimerobot", "UptimeRobot", { "Monitoring" }, keyLabel = { t("Read-only API key", "Clé API lecture seule") }) {
        val j = check(form("/v2/getMonitors", "api_key=${urlEnc(s.apiKey)}&format=json")).json()
        val monitors = j["monitors"].arr
        val down = monitors.count { it["status"].long == 8L || it["status"].long == 9L }
        stats(num(monitors.count { it["status"].long == 2L }, "Up", "En ligne"), num(down, "Down", "Hors ligne", downIfPositive(down)), num(monitors.size, "Monitors", "Sondes"))
    },
    W("vikunja", "Vikunja", { t("Tasks", "Tâches") }, keyLabel = { t("API token", "Jeton API") }) {
        val projects = get("/api/v1/projects", bearer).arr.count { (it["id"].long ?: 0) > 0 }
        val tasks = runCatching { get("/api/v1/tasks/all?filter=done%3Dfalse", bearer).arr }.getOrElse { get("/api/v1/tasks?filter=done%3Dfalse", bearer).arr }
        val now = Instant.now()
        val due = tasks.mapNotNull { tk -> tk["due_date"].str?.takeIf { !it.startsWith("0001") }?.let { runCatching { Instant.parse(it) }.getOrNull() } }
        val overdue = due.count { it.isBefore(now) }
        stats(num(projects, "Projects", "Projets"), num(due.count { it.isBefore(now.plusSeconds(7 * 86_400)) }, "Due in 7 d", "Échéance 7 j"), num(overdue, "Overdue", "En retard", warnIfPositive(overdue)))
    },
    W("wallos", "Wallos", { t("Subscriptions", "Abonnements") }) {
        val subs = get("/api/subscriptions/get_subscriptions.php?api_key=${s.apiKey}&state=0&sort=next_payment")["subscriptions"].arr
        val today = LocalDate.now()
        val cost = runCatching { get("/api/subscriptions/get_monthly_cost.php?api_key=${s.apiKey}&month=${today.monthValue}&year=${today.year}")["localized_monthly_cost"].str }.getOrNull()
        stats(num(subs.size, "Active", "Actifs"), Stat(subs.firstOrNull()["name"].str ?: "—", t("Next renewal", "Prochain")), Stat(cost ?: "—", t("This month", "Ce mois-ci")))
    },
    W("watchtower", "Watchtower", { t("Updates", "Mises à jour") }, keyLabel = { t("API token", "Jeton API") }) {
        val text = check(http.get(u("/v1/metrics"), bearer)).body
        fun metric(n: String) = Regex("^$n\\s+([0-9.]+)", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
        val failed = metric("watchtower_containers_failed")
        stats(num(metric("watchtower_containers_scanned"), "Scanned", "Analysés"), num(metric("watchtower_containers_updated"), "Updated", "Mis à jour"), num(failed, "Failed", "Échecs", warnIfPositive(failed)))
    },
    W("wgeasy", "wg-easy", { "WireGuard" }, Auth.LOGIN) {
        val clients = runCatching { get("/api/client", basic).arr }.getOrElse {
            val session = check(http.postJson(u("/api/session"), """{"password":${quoteJson(s.password)}}"""))
            get("/api/wireguard/client", mapOf("Cookie" to cookies(session))).arr
        }
        val recent = Instant.now().minusSeconds(120)
        val connected = clients.count { c -> c["latestHandshakeAt"].str?.let { runCatching { Instant.parse(it).isAfter(recent) }.getOrNull() } == true }
        stats(num(connected, "Connected", "Connectés"), num(clients.count { it["enabled"].bool == true }, "Enabled", "Activés"), num(clients.size, "Total", "Total"))
    },
    W("whatsupdocker", "What's Up Docker", { t("Updates", "Mises à jour") }, Auth.KEY_OR_LOGIN) {
        val h = if (s.apiKey.isNotBlank()) bearer else basic
        val containers = get("/api/containers", h).arr
        val updates = containers.count { it["updateAvailable"].bool == true }
        stats(num(containers.size, "Monitored", "Surveillés"), num(updates, "Updates", "Mises à jour", warnIfPositive(updates)))
    },
    W("zabbix", "Zabbix", { "Monitoring" }, keyLabel = { t("API token", "Jeton API") }) {
        val r = post(
            "/api_jsonrpc.php",
            """{"jsonrpc":"2.0","method":"trigger.get","params":{"output":["priority"],"filter":{"value":1},"skipDependent":1,"monitored":1,"active":1},"id":1}""",
            mapOf("Authorization" to "Bearer ${s.apiKey}"),
        )["result"].arr
        fun p(n: Long) = r.count { it["priority"].str?.toLongOrNull() == n }
        stats(num(p(2), "Warning", "Avertissement", warnIfPositive(p(2))), num(p(3), "Average", "Moyenne", warnIfPositive(p(3))), num(p(4), "High", "Haute", downIfPositive(p(4))), num(p(5), "Disaster", "Désastre", downIfPositive(p(5))))
    },
)

internal val pingOnlyD: List<Pair<String, String>> = listOf(
    "suwayomi" to "Suwayomi", "tailscale" to "Tailscale", "unifi_drive" to "UniFi Drive", "unraid" to "Unraid",
    "urbackup" to "UrBackup", "xteve" to "xTeVe", "yourspotify" to "Your Spotify", )
