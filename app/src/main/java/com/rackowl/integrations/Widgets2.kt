package com.rackowl.integrations

import com.rackowl.data.Stat
import com.rackowl.data.fmtBytes
import com.rackowl.data.fmtDec
import com.rackowl.data.fmtDuration
import com.rackowl.data.fmtPercent
import com.rackowl.net.HttpException
import com.rackowl.net.arr
import com.rackowl.net.bool
import com.rackowl.net.double
import com.rackowl.net.get
import com.rackowl.net.long
import com.rackowl.net.obj
import com.rackowl.net.str
import com.rackowl.ui.t

// ------------------------------------------------------------------ lot F → M

internal val widgetsB: List<Integration> = listOf(
    W("flood", "Flood", { "Torrent" }, Auth.LOGIN) {
        val auth = check(http.postJson(u("/api/auth/authenticate"), """{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}"""))
        val jwt = cookie(auth, "jwt")
        val torrents = get("/api/torrents", jwt?.let { mapOf("Cookie" to it) } ?: emptyMap())["torrents"].obj.values
        val done = torrents.count { (it["percentComplete"].double ?: 0.0) >= 100.0 }
        stats(
            rate(torrents.sumOf { it["downRate"].long ?: 0 }, "↓"),
            rate(torrents.sumOf { it["upRate"].long ?: 0 }, "↑"),
            num(torrents.size - done, "Leeching", "En cours"),
            num(done, "Seeding", "En partage"),
        )
    },
    W("freshrss", "FreshRSS", { "RSS" }, Auth.LOGIN) {
        val login = check(form("/api/greader.php/accounts/ClientLogin", "Email=${urlEnc(s.username)}&Passwd=${urlEnc(s.password)}"))
        val token = Regex("Auth=(\\S+)").find(login.body)?.groupValues?.get(1) ?: throw HttpException(401, t("Login rejected", "Connexion refusée"))
        val h = mapOf("Authorization" to "GoogleLogin auth=$token")
        val subs = get("/api/greader.php/reader/api/0/subscription/list?output=json", h)["subscriptions"].arr.size
        val unread = get("/api/greader.php/reader/api/0/unread-count?output=json", h)["max"].long
        stats(num(unread, "Unread", "Non lus"), num(subs, "Subscriptions", "Abonnements"))
    },
    W("frigate", "Frigate", { t("Cameras", "Caméras") }, Auth.NONE) {
        val st = get("/api/stats")
        stats(
            num(st["cameras"].obj.size, "Cameras", "Caméras"),
            Stat(st["service"]["uptime"].long?.let { fmtDuration(it * 1000) } ?: "—", "Uptime"),
            Stat(st["service"]["version"].str ?: "—", "Version"),
        )
    },
    W("gatus", "Gatus", { "Monitoring" }, Auth.NONE) {
        val sites = get("/api/v1/endpoints/statuses").arr
        val up = sites.count { s -> s["results"].arr.lastOrNull()?.get("success").bool == true }
        val results = sites.flatMap { it["results"].arr }
        val pct = if (results.isEmpty()) null else results.count { it["success"].bool == true } * 100.0 / results.size
        stats(num(up, "Up", "En ligne"), num(sites.size - up, "Down", "Hors ligne", downIfPositive(sites.size - up)), Stat(fmtPercent(pct), t("Uptime", "Disponibilité")))
    },
    W("ghostfolio", "Ghostfolio", { t("Finance", "Finances") }, keyLabel = { t("Access token", "Jeton d’accès") }) {
        suspend fun pct(range: String) = runCatching {
            get("/api/v2/portfolio/performance?range=$range", bearer)["performance"]["netPerformancePercentage"].double?.let { fmtDec(it * 100, 2) + " %" }
        }.getOrNull() ?: "—"
        stats(Stat(pct("1d"), t("Today", "Aujourd’hui")), Stat(pct("1y"), t("1 year", "1 an")), Stat(pct("max"), "Max"))
    },
    W("gitea", "Gitea", { "Git" }, keyLabel = { t("Access token", "Jeton d’accès") }) {
        suspend fun total(path: String) = check(http.get(u(path), bearer)).header("X-Total-Count")?.toLongOrNull()
        stats(
            num(total("/api/v1/notifications?limit=1"), "Notifications", "Notifications"),
            num(total("/api/v1/repos/issues/search?type=issues&limit=1"), "Issues", "Tickets"),
            num(total("/api/v1/repos/issues/search?type=pulls&limit=1"), "Pull requests", "Pull requests"),
            num(total("/api/v1/repos/search?limit=1"), "Repositories", "Dépôts"),
        )
    },
    W("gitlab", "GitLab", { "Git" }, Auth.KEY_OR_LOGIN, { t("Access token (user ID as username)", "Jeton d’accès (ID utilisateur en identifiant)") }) {
        val c = get("/api/v4/users/${s.username}/associations_count", mapOf("PRIVATE-TOKEN" to s.apiKey))
        stats(
            num(c["projects_count"].long, "Projects", "Projets"),
            num(c["issues_count"].long, "Issues", "Tickets"),
            num(c["merge_requests_count"].long, "Merge requests", "Merge requests"),
        )
    },
    W("gluetun", "Gluetun", { "VPN" }, keyLabel = { t("API key (optional)", "Clé API (optionnelle)") }) {
        val ip = get("/v1/publicip/ip", if (s.apiKey.isNotBlank()) key("X-API-Key") else emptyMap())
        stats(Stat(ip["public_ip"].str ?: "—", t("Public IP", "IP publique")), Stat(ip["country"].str ?: "—", t("Country", "Pays")), Stat(ip["region"].str ?: "—", t("Region", "Région")))
    },
    W("grafana", "Grafana", { "Monitoring" }, Auth.LOGIN) {
        val st = get("/api/admin/stats", basic)
        val firing = runCatching { get("/api/alertmanager/grafana/api/v2/alerts", basic).arr.size }.getOrNull()
        stats(
            num(st["dashboards"].long, "Dashboards", "Tableaux"),
            num(st["datasources"].long, "Data sources", "Sources"),
            num(st["alerts"].long, "Alerts", "Alertes"),
            num(firing, "Firing", "Déclenchées", downIfPositive(firing)),
        )
    },
    W("hdhomerun", "HDHomeRun", { "TV" }, Auth.NONE) {
        val lineup = get("/lineup.json").arr
        val tuners = runCatching { get("/status.json").arr }.getOrDefault(emptyList())
        stats(
            num(lineup.size, "Channels", "Chaînes"),
            num(lineup.count { it["HD"].long == 1L }, "HD", "HD"),
            Stat("${tuners.count { !it["VctNumber"].isNull }} / ${tuners.size}", t("Tuners in use", "Tuners utilisés")),
        )
    },
    W("healthchecks", "Healthchecks", { "Monitoring" }) {
        val checks = get("/api/v3/checks/", key("X-Api-Key"))["checks"].arr
        val down = checks.count { it["status"].str == "down" }
        stats(num(checks.count { it["status"].str == "up" }, "Up", "OK"), num(down, "Down", "En échec", downIfPositive(down)), num(checks.size, "Checks", "Contrôles"))
    },
    W("homebox", "Homebox", { t("Inventory", "Inventaire") }, Auth.LOGIN) {
        val login = form("/api/v1/users/login", "username=${urlEnc(s.username)}&password=${urlEnc(s.password)}")
        val token = runCatching { check(login).json()["token"].str }.getOrNull() ?: throw HttpException(login.code, t("Login rejected", "Connexion refusée"))
        val st = get("/api/v1/groups/statistics", mapOf("Authorization" to token))
        stats(
            num(st["totalItems"].long, "Items", "Objets"),
            num(st["totalLocations"].long, "Locations", "Emplacements"),
            Stat(st["totalItemPrice"].double?.let { fmtDec(it, 0) } ?: "—", t("Total value", "Valeur totale")),
        )
    },
    W("homebridge", "Homebridge", { t("Home automation", "Domotique") }, Auth.LOGIN) {
        val token = post("/api/auth/login", """{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}""")["access_token"].str
        val h = mapOf("Authorization" to "Bearer $token")
        val status = get("/api/status/homebridge", h)["status"].str ?: "—"
        val updates = runCatching { get("/api/plugins", h).arr.count { it["updateAvailable"].bool == true } }.getOrNull()
        stats(Stat(status, t("Status", "État")), num(updates, "Plugin updates", "Mises à jour", warnIfPositive(updates)))
    },
    W("jackett", "Jackett", { t("Indexers", "Indexeurs") }) {
        val idx = get("/api/v2.0/indexers?apikey=${s.apiKey}&configured=true").arr
        val errored = idx.count { !it["last_error"].isNull && (it["last_error"].str ?: "").isNotBlank() }
        stats(num(idx.size, "Configured", "Configurés"), num(errored, "Errored", "En erreur", warnIfPositive(errored)))
    },
    W("jellystat", "Jellystat", { t("Media stats", "Stats médias") }) {
        val v = get("/stats/getViewsByLibraryType?days=30", key("X-API-Token"))
        stats(num(v["Movie"].long, "Movies (30 d)", "Films (30 j)"), num(v["Series"].long, "Episodes (30 d)", "Épisodes (30 j)"), num(v["Audio"].long, "Songs (30 d)", "Titres (30 j)"))
    },
    W("karakeep", "Karakeep", { t("Bookmarks", "Favoris") }, keyLabel = { t("API key", "Clé API") }) {
        val st = get("/api/v1/users/me/stats", bearer)
        stats(num(st["numBookmarks"].long, "Bookmarks", "Favoris"), num(st["numFavorites"].long, "Favourites", "Préférés"), num(st["numArchived"].long, "Archived", "Archivés"))
    },
    W("kavita", "Kavita", { t("Reading", "Lecture") }, Auth.KEY_OR_LOGIN) {
        val body = if (s.apiKey.isNotBlank()) """{"username":"","password":"","apiKey":${quoteJson(s.apiKey)}}"""
        else """{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)},"apiKey":""}"""
        val token = post("/api/Account/login", body)["token"].str
        val st = get("/api/Stats/server/stats", mapOf("Authorization" to "Bearer $token"))
        stats(num(st["seriesCount"].long, "Series", "Séries"), num(st["totalFiles"].long, "Files", "Fichiers"))
    },
    W("komga", "Komga", { t("Comics", "BD") }, Auth.KEY_OR_LOGIN) {
        val h = if (s.username.isNotBlank()) basic else key("X-API-Key")
        val libs = get("/api/v1/libraries", h).arr.size
        val series = runCatching { post("/api/v1/series/list?size=1", "{}", h) }.getOrElse { get("/api/v1/series?size=1", h) }["totalElements"].long
        val books = runCatching { post("/api/v1/books/list?size=1", "{}", h) }.getOrElse { get("/api/v1/books?size=1", h) }["totalElements"].long
        stats(num(libs, "Libraries", "Bibliothèques"), num(series, "Series", "Séries"), num(books, "Books", "Livres"))
    },
    W("kopia", "Kopia", { t("Backup", "Sauvegarde") }, Auth.LOGIN) {
        val sources = get("/api/v1/sources", basic)["sources"].arr
        val failed = sources.count { it["lastSnapshot"]["stats"]["errorCount"].long?.let { e -> e > 0 } == true }
        stats(
            num(sources.size, "Sources", "Sources"),
            Stat(fmtBytes(sources.sumOf { it["lastSnapshot"]["stats"]["totalSize"].long ?: 0 }), t("Size", "Taille")),
            num(failed, "With errors", "En erreur", warnIfPositive(failed)),
        )
    },
    W("linkwarden", "Linkwarden", { t("Bookmarks", "Favoris") }, keyLabel = { t("Access token", "Jeton d’accès") }) {
        val cols = get("/api/v1/collections", bearer).let { it["response"].takeUnless { r -> r.isNull } ?: it }.arr
        val tags = get("/api/v1/tags", bearer).let { it["response"].takeUnless { r -> r.isNull } ?: it }.arr
        stats(num(cols.sumOf { it["_count"]["links"].long ?: 0 }, "Links", "Liens"), num(cols.size, "Collections", "Collections"), num(tags.size, "Tags", "Tags"))
    },
    W("lubelogger", "LubeLogger", { t("Vehicles", "Véhicules") }, Auth.LOGIN) {
        val v = get("/api/vehicle/info", basic).arr
        stats(
            num(v.size, "Vehicles", "Véhicules"),
            num(v.sumOf { it["serviceRecordCount"].long ?: 0 }, "Service records", "Entretiens"),
            num(v.sumOf { (it["veryUrgentReminderCount"].long ?: 0) + (it["urgentReminderCount"].long ?: 0) }, "Urgent reminders", "Rappels urgents"),
        )
    },
    W("mailcow", "mailcow", { t("Mail", "Mail") }) {
        val d = get("/api/v1/get/domain/all", key("X-API-Key")).arr
        stats(
            num(d.size, "Domains", "Domaines"),
            num(d.sumOf { it["mboxes_in_domain"].long ?: 0 }, "Mailboxes", "Boîtes"),
            num(d.sumOf { it["msgs_total"].long ?: 0 }, "Mails", "Mails"),
            Stat(fmtBytes(d.sumOf { it["bytes_total"].long ?: 0 }), t("Storage", "Stockage")),
        )
    },
    W("maintainerr", "Maintainerr", { t("Media cleanup", "Nettoyage médias") }, Auth.NONE) {
        val c = get("/api/storage-metrics")["cleanupTotals"]
        stats(num(c["itemsHandled"].long, "Handled", "Traités"), num(c["moviesHandled"].long, "Movies", "Films"), num(c["episodesHandled"].long, "Episodes", "Épisodes"))
    },
    W("mastodon", "Mastodon", { t("Social", "Social") }, Auth.NONE) {
        val st = get("/api/v1/instance")["stats"]
        stats(num(st["user_count"].long, "Users", "Utilisateurs"), num(st["status_count"].long, "Posts", "Messages"), num(st["domain_count"].long, "Domains", "Domaines"))
    },
    W("mealie", "Mealie", { t("Recipes", "Recettes") }, keyLabel = { t("API token", "Jeton API") }) {
        val st = runCatching { get("/api/households/statistics", bearer) }.getOrElse { get("/api/groups/statistics", bearer) }
        stats(num(st["totalRecipes"].long, "Recipes", "Recettes"), num(st["totalUsers"].long, "Users", "Utilisateurs"), num(st["totalCategories"].long, "Categories", "Catégories"))
    },
    W("medusa", "Medusa", { t("Series", "Séries") }) {
        val st = get("/api/v1/${s.apiKey}/?cmd=shows.stats")["data"]
        val f = get("/api/v1/${s.apiKey}/?cmd=future")["data"]
        val wanted = listOf("later", "missed", "soon", "today").sumOf { f[it].arr.size }
        stats(num(wanted, "Wanted", "Recherchés"), num(st["ep_snatched"].long, "Queued", "En file"), num(st["shows_active"].long, "Series", "Séries"))
    },
    W("mikrotik", "MikroTik", { t("Router", "Routeur") }, Auth.LOGIN) {
        val sys = get("/rest/system/resource", basic)
        val free = sys["free-memory"].double; val total = sys["total-memory"].double
        stats(
            Stat(fmtPercent(sys["cpu-load"].double), "CPU"),
            Stat(fmtPercent(if (free != null && total != null && total > 0) 100 - free / total * 100 else null), "RAM"),
            num(runCatching { get("/rest/ip/dhcp-server/lease?.proplist=address", basic).arr.size }.getOrNull(), "DHCP leases", "Baux DHCP"),
            Stat(sys["uptime"].str ?: "—", "Uptime"),
        )
    },
    W("miniflux", "Miniflux", { "RSS" }) {
        val c = get("/v1/feeds/counters", key("X-Auth-Token"))
        stats(num(c["unreads"].obj.values.sumOf { it.long ?: 0 }, "Unread", "Non lus"), num(c["reads"].obj.values.sumOf { it.long ?: 0 }, "Read", "Lus"))
    },
    W("moonraker", "Moonraker", { t("3D printer", "Imprimante 3D") }, Auth.NONE) {
        val st = get("/printer/objects/query?print_stats&display_status&webhooks")["result"]["status"]
        val progress = st["display_status"]["progress"].double
        stats(
            Stat(st["webhooks"]["state"].str ?: "—", t("Printer", "Imprimante")),
            Stat(st["print_stats"]["state"].str ?: "—", t("Print", "Impression")),
            Stat(progress?.let { fmtPercent(it * 100) } ?: "—", t("Progress", "Progression")),
        )
    },
    W("mylar", "Mylar3", { t("Comics", "BD") }) {
        val series = get("/api?cmd=seriesjsonListing&apikey=${s.apiKey}")["data"].arr.size
        val issues = get("/api?cmd=getIndex&apikey=${s.apiKey}")["data"].arr.sumOf { it["totalIssues"].long ?: 0 }
        val wanted = get("/api?cmd=getWanted&apikey=${s.apiKey}")["issues"].arr.size
        stats(num(series, "Series", "Séries"), num(issues, "Issues", "Numéros"), num(wanted, "Wanted", "Recherchés", warnIfPositive(wanted)))
    },
    W("myspeed", "MySpeed", { t("Network", "Réseau") }, Auth.LOGIN, { t("Password", "Mot de passe") }) {
        val last = get("/api/speedtests?limit=1", mapOf("Password" to s.password)).arr.firstOrNull()
        stats(
            Stat(last["download"].double?.let { fmtDec(it, 0) } ?: "—", "↓ Mbit/s"),
            Stat(last["upload"].double?.let { fmtDec(it, 0) } ?: "—", "↑ Mbit/s"),
            Stat(last["ping"].double?.let { fmtDec(it, 0) + " ms" } ?: "—", "Ping"),
        )
    },
)

internal val pingOnlyB: List<Pair<String, String>> = listOf(
    "fritzbox" to "FRITZ!Box", "headscale" to "Headscale", "jdownloader" to "JDownloader",
)
