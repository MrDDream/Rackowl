package com.rackowl.integrations

import com.rackowl.data.DetailItem
import com.rackowl.data.Level
import com.rackowl.data.Service
import com.rackowl.data.Stat
import com.rackowl.data.fmtBytes
import com.rackowl.data.fmtDec
import com.rackowl.data.fmtInt
import com.rackowl.data.fmtPercent
import com.rackowl.data.fmtSpeed
import com.rackowl.net.FORM_MEDIA
import com.rackowl.net.Http
import com.rackowl.net.HttpException
import com.rackowl.net.HttpResponse
import com.rackowl.net.arr
import com.rackowl.net.bool
import com.rackowl.net.double
import com.rackowl.net.get
import com.rackowl.net.join
import com.rackowl.net.long
import com.rackowl.net.obj
import com.rackowl.net.str
import com.rackowl.ui.t
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.RequestBody.Companion.toRequestBody

// ------------------------------------------------------------------ moteur compact

/** Contexte d'appel d'un widget : URL de base, en-têtes d'authentification et requêtes JSON. */
internal class Ctx(val http: Http, val s: Service) {
    val base = s.url.trimEnd('/')
    fun u(path: String) = join(base, path)

    val bearer get() = mapOf("Authorization" to "Bearer ${s.apiKey}")
    val basic get() = if (s.username.isNotBlank()) mapOf("Authorization" to Http.basic(s.username, s.password)) else emptyMap()
    fun key(header: String) = mapOf(header to s.apiKey)

    suspend fun get(path: String, h: Map<String, String> = emptyMap()): JsonElement = http.getJson(u(path), h)

    suspend fun post(path: String, body: String = "{}", h: Map<String, String> = emptyMap()): JsonElement = check(http.postJson(u(path), body, h)).json()

    suspend fun form(path: String, body: String, h: Map<String, String> = emptyMap()): HttpResponse =
        http.request(u(path), "POST", h, body.toRequestBody(FORM_MEDIA))

    fun check(r: HttpResponse): HttpResponse = if (r.ok) r else throw HttpException(r.code, "HTTP ${r.code}")

    /** Cookie de session (ex. « SID=… ») renvoyé par une réponse de connexion. */
    fun cookie(r: HttpResponse, name: String): String? =
        r.headers.entries.firstOrNull { it.key.equals("set-cookie", true) }?.value
            ?.firstOrNull { it.startsWith("$name=") }?.substringBefore(';')
}

/** Widget défini par une simple fonction, sur le modèle des widgets de Homepage. */
internal class W(
    id: String,
    name: String,
    tag: () -> String,
    auth: Auth = Auth.KEY,
    keyLabel: () -> String = { t("API key", "Clé API") },
    private val fetchFn: suspend Ctx.() -> WidgetData,
) : Integration(id, name, tag, auth, keyLabel) {
    override suspend fun fetch(http: Http, s: Service): WidgetData = Ctx(http, s).fetchFn()
}

internal fun stats(vararg s: Stat) = WidgetData(s.toList())
internal fun num(v: Number?, en: String, fr: String, level: Level = Level.NORMAL) = Stat(fmtInt(v?.toLong()), t(en, fr), level)
internal fun warnIfPositive(v: Number?) = if ((v?.toLong() ?: 0) > 0) Level.WARN else Level.NORMAL
internal fun downIfPositive(v: Number?) = if ((v?.toLong() ?: 0) > 0) Level.DOWN else Level.NORMAL
internal fun rate(bytesPerSec: Long?, arrow: String) = fmtSpeed(bytesPerSec).let { Stat(it.first, "$arrow ${it.second}") }
internal fun kw(watts: Double?) = (watts?.let { fmtDec(it / 1000.0, 1) } ?: "—") + " kW"
internal val JsonElement?.isNull get() = this == null || this is JsonNull

internal fun quoteJson(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
internal fun urlEnc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

// ------------------------------------------------------------------ Synology (DSM)

/** Session DSM : connexion SYNO.API.Auth, puis appels avec _sid. */
internal suspend fun Ctx.synoSid(session: String): String {
    val j = get("/webapi/auth.cgi?api=SYNO.API.Auth&version=6&method=login&account=${urlEnc(s.username)}&passwd=${urlEnc(s.password)}&session=$session&format=sid")
    return j["data"]["sid"].str ?: throw HttpException(401, t("Synology login rejected", "Connexion Synology refusée"))
}

// ------------------------------------------------------------------ lot A → F

internal val widgetsA: List<Integration> = listOf(
    W("argocd", "Argo CD", { "GitOps" }, keyLabel = { t("API token", "Jeton API") }) {
        val apps = get("/api/v1/applications", bearer)["items"].arr
        val outOfSync = apps.count { it["status"]["sync"]["status"].str == "OutOfSync" }
        stats(
            num(apps.size, "Apps", "Apps"),
            num(apps.count { it["status"]["sync"]["status"].str == "Synced" }, "Synced", "Synchronisées"),
            num(outOfSync, "Out of sync", "Désynchronisées", warnIfPositive(outOfSync)),
            num(apps.count { it["status"]["health"]["status"].str == "Healthy" }, "Healthy", "Saines"),
        )
    },
    W("atsumeru", "Atsumeru", { t("Manga", "Mangas") }, Auth.LOGIN) {
        val st = get("/api/server/info", basic)["stats"]
        stats(
            num(st["total_series"].long, "Series", "Séries"),
            num(st["total_archives"].long, "Archives", "Archives"),
            num(st["total_chapters"].long, "Chapters", "Chapitres"),
        )
    },
    W("audiobookshelf", "Audiobookshelf", { t("Audiobooks", "Livres audio") }) {
        val libs = get("/api/libraries", bearer).let { it["libraries"].takeUnless { l -> l.isNull } ?: it }.arr
        var books = 0L; var podcasts = 0L; var bookSec = 0.0
        libs.forEach { l ->
            val st = runCatching { get("/api/libraries/${l["id"].str}/stats", bearer) }.getOrNull()
            val items = st["totalItems"].long ?: 0
            if (l["mediaType"].str == "podcast") podcasts += items else { books += items; bookSec += st["totalDuration"].double ?: 0.0 }
        }
        stats(
            num(books, "Books", "Livres"),
            Stat(fmtInt((bookSec / 3600).toLong()) + " h", t("Book duration", "Durée des livres")),
            num(podcasts, "Podcasts", "Podcasts"),
        )
    },
    W("authentik", "authentik", { t("Identity", "Identité") }, keyLabel = { t("API token", "Jeton API") }) {
        val users = get("/api/v3/core/users/?page_size=1", bearer)["pagination"]["count"].long
        val events = runCatching {
            get("/api/v3/events/events/volume/?actions=login&actions=login_failed&history_days=1", bearer).arr
        }.getOrDefault(emptyList())
        fun sum(action: String) = events.filter { it["action"].str == action }.sumOf { it["count"].long ?: 0 }
        val failed = sum("login_failed")
        stats(
            num(users, "Users", "Utilisateurs"),
            num(sum("login"), "Logins (24 h)", "Connexions (24 h)"),
            num(failed, "Failed (24 h)", "Échecs (24 h)", warnIfPositive(failed)),
        )
    },
    W("autobrr", "autobrr", { t("Automation", "Automatisation") }) {
        val h = key("X-API-Token")
        val st = get("/api/release/stats", h)
        stats(
            num(st["push_approved_count"].long, "Approved", "Approuvées"),
            num(st["push_rejected_count"].long, "Rejected", "Rejetées"),
            num(get("/api/filters", h).arr.size, "Filters", "Filtres"),
            num(get("/api/release/indexers", h).arr.size, "Indexers", "Indexeurs"),
        )
    },
    W("beszel", "Beszel", { "Monitoring" }, Auth.LOGIN) {
        val body = """{"identity":${quoteJson(s.username)},"password":${quoteJson(s.password)}}"""
        val auth = runCatching { post("/api/collections/_superusers/auth-with-password", body) }
            .getOrElse { post("/api/admins/auth-with-password", body) }
        val token = auth["token"].str ?: throw HttpException(401, t("Login rejected", "Connexion refusée"))
        val systems = get("/api/collections/systems/records?page=1&perPage=500", mapOf("Authorization" to "Bearer $token"))
        val items = systems["items"].arr
        val down = items.count { it["status"].str != "up" }
        stats(
            num(systems["totalItems"].long ?: items.size.toLong(), "Systems", "Systèmes"),
            num(items.count { it["status"].str == "up" }, "Up", "En ligne"),
            num(down, "Down", "Hors ligne", downIfPositive(down)),
        )
    },
    W("caddy", "Caddy", { "Proxy" }, Auth.NONE) {
        val ups = get("/reverse_proxy/upstreams").arr
        val fails = ups.sumOf { it["fails"].long ?: 0 }
        stats(
            num(ups.size, "Upstreams", "Upstreams"),
            num(ups.sumOf { it["num_requests"].long ?: 0 }, "Requests", "Requêtes"),
            num(fails, "Failed", "Échecs", warnIfPositive(fails)),
        )
    },
    W("calibreweb", "Calibre-Web", { t("E-books", "Livres") }, Auth.LOGIN) {
        val j = get("/opds/stats", basic)
        stats(num(j["books"].long, "Books", "Livres"), num(j["authors"].long, "Authors", "Auteurs"), num(j["series"].long, "Series", "Séries"))
    },
    W("changedetectionio", "changedetection.io", { t("Web watching", "Veille web") }) {
        val watches = get("/api/v1/watch", key("x-api-key")).obj.values
        val diffs = watches.count { (it["last_changed"].long ?: 0) > (it["last_viewed"].long ?: 0) && (it["last_changed"].long ?: 0) > 0 }
        stats(num(diffs, "Changes", "Changements", warnIfPositive(diffs)), num(watches.size, "Watched", "Surveillés"))
    },
    W("channelsdvrserver", "Channels DVR", { "DVR" }, Auth.NONE) {
        val st = get("/dvr")["stats"]
        stats(
            num(st["groups"].long, "Shows", "Émissions"),
            num(st["files"].long, "Recordings", "Enregistrements"),
            num(st["jobs"].long, "Scheduled", "Programmés"),
        )
    },
    W("crowdsec", "CrowdSec", { t("Security", "Sécurité") }, Auth.LOGIN) {
        val login = post("/v1/watchers/login", """{"machine_id":${quoteJson(s.username)},"password":${quoteJson(s.password)},"scenarios":[]}""")
        val h = mapOf("Authorization" to "Bearer ${login["token"].str}")
        val alerts = get("/v1/alerts?limit=500&since=24h&with_decisions=false&include_capi=false", h).arr.size
        val bans = get("/v1/alerts?decision_type=ban&include_capi=false&has_active_decision=1&with_decisions=false", h).arr.size
        stats(num(alerts, "Alerts (24 h)", "Alertes (24 h)", warnIfPositive(alerts)), num(bans, "Bans", "Bannissements"))
    },
    W("deluge", "Deluge", { "Torrent" }, Auth.LOGIN, { t("Password", "Mot de passe") }) {
        val login = check(http.postJson(u("/json"), """{"method":"auth.login","params":[${quoteJson(s.password)}],"id":1}"""))
        val sid = cookie(login, "_session_id")
        val r = check(http.postJson(
            u("/json"),
            """{"method":"web.update_ui","params":[["progress","download_payload_rate","upload_payload_rate"],{}],"id":2}""",
            sid?.let { mapOf("Cookie" to it) } ?: emptyMap(),
        )).json()["result"]
        val torrents = r["torrents"].obj.values
        val seeding = torrents.count { (it["progress"].double ?: 0.0) >= 100.0 }
        stats(
            rate(torrents.sumOf { it["download_payload_rate"].long ?: 0 }, "↓"),
            rate(torrents.sumOf { it["upload_payload_rate"].long ?: 0 }, "↑"),
            num(torrents.size - seeding, "Leeching", "En cours"),
            num(seeding, "Seeding", "En partage"),
        )
    },
    W("develancacheui", "DeveLanCacheUI", { "Cache" }, Auth.NONE) {
        val j = get("/DownloadStats/GetTotalDownloadStats")
        stats(Stat(fmtBytes(j["totalCacheHitBytes"].long), t("Cache hits", "Servi par le cache")), Stat(fmtBytes(j["totalCacheMissBytes"].long), t("Cache misses", "Hors cache")))
    },
    W("diskstation", "Synology DSM", { "NAS" }, Auth.LOGIN) {
        val sid = synoSid("Core")
        val util = get("/webapi/entry.cgi?api=SYNO.Core.System.Utilization&version=1&method=get&_sid=$sid")["data"]
        val cpu = (util["cpu"]["user_load"].double ?: 0.0) + (util["cpu"]["system_load"].double ?: 0.0)
        val vol = runCatching { get("/webapi/entry.cgi?api=SYNO.Storage.CGI.Storage&version=1&method=load_info&_sid=$sid")["volumes"][0] }.getOrNull()
            ?: runCatching { get("/webapi/entry.cgi?api=SYNO.Storage.CGI.Storage&version=1&method=load_info&_sid=$sid")["data"]["volumes"][0] }.getOrNull()
        val total = vol["size"]["total"].double; val used = vol["size"]["used"].double
        stats(
            Stat(fmtPercent(cpu), "CPU"),
            Stat(fmtPercent(util["memory"]["real_usage"].double), "RAM"),
            Stat(if (total != null && used != null) fmtBytes((total - used).toLong()) else "—", t("Free", "Libre")),
        )
    },
    W("dispatcharr", "Dispatcharr", { "IPTV" }, Auth.LOGIN) {
        val token = post("/api/accounts/token/", """{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}""")["access"].str
        val h = mapOf("Authorization" to "Bearer $token")
        stats(num(get("/api/channels/channels/", h).arr.size, "Channels", "Chaînes"), num(get("/proxy/ts/status", h)["count"].long ?: 0, "Streams", "Flux"))
    },
    W("downloadstation", "Download Station", { t("Downloads", "Téléchargements") }, Auth.LOGIN) {
        val sid = synoSid("DownloadStation")
        val tasks = get("/webapi/DownloadStation/task.cgi?api=SYNO.DownloadStation.Task&version=1&method=list&additional=transfer&_sid=$sid")["data"]["tasks"].arr
        val done = tasks.count { it["additional"]["transfer"]["size_downloaded"].long == it["size"].long }
        stats(
            rate(tasks.sumOf { it["additional"]["transfer"]["speed_download"].long ?: 0 }, "↓"),
            rate(tasks.sumOf { it["additional"]["transfer"]["speed_upload"].long ?: 0 }, "↑"),
            num(tasks.size - done, "Leeching", "En cours"),
            num(done, "Done", "Terminés"),
        )
    },
    W("duplicati", "Duplicati", { t("Backup", "Sauvegarde") }, Auth.LOGIN, { t("Password", "Mot de passe") }) {
        val token = post("/api/v1/auth/login", """{"Password":${quoteJson(s.password)},"RememberMe":false}""")["AccessToken"].str
        val backups = get("/api/v1/backups", mapOf("Authorization" to "Bearer $token")).arr
        val errors = backups.count { (it["Backup"]["Metadata"]["LastErrorDate"].str ?: "").isNotBlank() }
        stats(num(backups.size, "Jobs", "Tâches"), num(errors, "With errors", "En erreur", warnIfPositive(errors)))
    },
    W("emby", "Emby", { t("Media", "Médias") }) {
        val counts = get("/emby/Items/Counts?api_key=${s.apiKey}")
        val sessions = runCatching { get("/emby/Sessions?api_key=${s.apiKey}").arr.filter { !it["NowPlayingItem"].isNull } }.getOrDefault(emptyList())
        WidgetData(
            listOf(
                num(sessions.size, "Now playing", "Lectures en cours"),
                num(counts["MovieCount"].long, "Movies", "Films"),
                num(counts["SeriesCount"].long, "Series", "Séries"),
                num(counts["EpisodeCount"].long, "Episodes", "Épisodes"),
            ),
            t("Now playing", "Lectures en cours"),
            sessions.map { se ->
                val item = se["NowPlayingItem"]
                val pos = se["PlayState"]["PositionTicks"].double ?: 0.0
                val total = item["RunTimeTicks"].double ?: 0.0
                DetailItem(item["SeriesName"].str?.let { "$it · ${item["Name"].str}" } ?: item["Name"].str.orEmpty(), se["DeviceName"].str.orEmpty(), if (total > 0) (pos / total).toFloat() else null)
            },
        )
    },
    W("esphome", "ESPHome", { t("Home automation", "Domotique") }, Auth.KEY_OR_LOGIN, { t("Cookie (optional)", "Cookie (optionnel)") }) {
        val h = when {
            s.username.isNotBlank() -> basic
            s.apiKey.isNotBlank() -> mapOf("Cookie" to "authenticated=${s.apiKey}")
            else -> emptyMap()
        }
        val devices = get("/ping", h).obj
        val offline = devices.values.count { it.bool == false }
        stats(
            num(devices.values.count { it.bool == true }, "Online", "En ligne"),
            num(offline, "Offline", "Hors ligne", downIfPositive(offline)),
            num(devices.size, "Total", "Total"),
        )
    },
    W("evcc", "evcc", { t("Energy", "Énergie") }, Auth.NONE) {
        val st = get("/api/state").let { it["result"].takeUnless { r -> r.isNull } ?: it }
        val grid = st["gridPower"].double ?: st["grid"]["power"].double
        val charge = st["loadpoints"].arr.sumOf { it["chargePower"].double ?: 0.0 }
        stats(
            Stat(kw(st["pvPower"].double), t("Solar", "Solaire")),
            Stat(kw(grid), t("Grid", "Réseau")),
            Stat(kw(st["homePower"].double), t("Home", "Maison")),
            Stat(kw(charge), t("Charging", "Recharge")),
        )
    },
    W("filebrowser", "File Browser", { t("Files", "Fichiers") }, Auth.LOGIN) {
        val token = check(http.postJson(u("/api/login"), """{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}""")).body.trim()
        val usage = get("/api/usage", mapOf("X-Auth" to token))
        val total = usage["total"].long ?: 0; val used = usage["used"].long ?: 0
        stats(Stat(fmtBytes(total - used), t("Available", "Disponible")), Stat(fmtBytes(used), t("Used", "Utilisé")), Stat(fmtBytes(total), "Total"))
    },
    W("fileflows", "FileFlows", { t("Transcoding", "Transcodage") }, Auth.NONE) {
        val j = get("/api/status")
        stats(num(j["queue"].long, "Queue", "File"), num(j["processing"].long, "Processing", "En cours"), num(j["processed"].long, "Processed", "Traités"))
    },
)

/** Widgets Homepage reconnus mais sans statistiques exploitables sans configuration spécifique. */
internal val pingOnlyA: List<Pair<String, String>> = listOf(
    "apcups" to "APC UPS", "arcane" to "Arcane", "azuredevops" to "Azure DevOps", "backrest" to "Backrest",
    "booklore" to "BookLore", "bookorbit" to "BookOrbit", "checkmk" to "Checkmk", "cloudflared" to "Cloudflare Tunnel",
    "dockhand" to "Dockhand", "firefly" to "Firefly III",
)
