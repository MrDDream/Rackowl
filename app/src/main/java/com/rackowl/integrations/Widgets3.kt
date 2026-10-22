package com.rackowl.integrations

import com.rackowl.data.DetailItem
import com.rackowl.data.Stat
import com.rackowl.data.fmtBytes
import com.rackowl.data.fmtDec
import com.rackowl.data.fmtDuration
import com.rackowl.data.fmtInt
import com.rackowl.data.fmtPercent
import com.rackowl.net.HttpException
import com.rackowl.net.HttpResponse
import com.rackowl.net.arr
import com.rackowl.net.bool
import com.rackowl.net.double
import com.rackowl.net.get
import com.rackowl.net.long
import com.rackowl.net.obj
import com.rackowl.net.str
import com.rackowl.ui.t
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.security.MessageDigest

/** Tous les cookies d'une réponse, prêts pour l'en-tête Cookie. */
internal fun cookies(r: HttpResponse): String =
    r.headers.entries.filter { it.key.equals("set-cookie", true) }.flatMap { it.value }.joinToString("; ") { it.substringBefore(';') }

/** Liste renvoyée directement, ou sous une clé « data » (formats variables selon les versions). */
internal fun JsonElement?.listOrData(): List<JsonElement> = if (this is JsonArray) this else this["data"].arr

private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

// ------------------------------------------------------------------ lot N → S

internal val widgetsC: List<Integration> = listOf(
    W("navidrome", "Navidrome", { t("Music", "Musique") }, Auth.LOGIN) {
        val salt = System.nanoTime().toString(36)
        val q = "u=${urlEnc(s.username)}&t=${md5(s.password + salt)}&s=$salt&v=1.16.1&c=rackowl&f=json"
        val resp = get("/rest/getNowPlaying?$q")["subsonic-response"]
        val entries = resp["nowPlaying"]["entry"].arr
        WidgetData(
            listOf(num(entries.size, "Now playing", "En écoute")),
            t("Now playing", "En écoute"),
            entries.map { DetailItem("${it["artist"].str.orEmpty()} — ${it["title"].str.orEmpty()}", it["username"].str.orEmpty()) },
        )
    },
    W("netalertx", "NetAlertX", { t("Network", "Réseau") }, keyLabel = { t("API token", "Jeton API") }) {
        val totals = runCatching { get("/devices/totals", bearer).arr }.getOrElse { get("/php/server/devices.php?action=getDevicesTotals", bearer).arr }
        fun at(i: Int) = totals.getOrNull(i).long
        stats(num(at(0), "Total", "Total"), num(at(1), "Connected", "Connectés"), num(at(3), "New", "Nouveaux", warnIfPositive(at(3))), num(at(4), "Down alerts", "Alertes", downIfPositive(at(4))))
    },
    W("netbird", "NetBird", { "VPN" }, keyLabel = { t("Access token", "Jeton d’accès") }) {
        val h = mapOf("Authorization" to "Token ${s.apiKey}")
        val peers = get("/api/peers", h).arr
        val users = get("/api/users", h).arr.count { it["is_service_user"].bool != true }
        stats(Stat("${peers.count { it["connected"].bool == true }} / ${peers.size}", t("Online", "En ligne")), num(users, "Users", "Utilisateurs"))
    },
    W("netdata", "Netdata", { "Monitoring" }, Auth.NONE) {
        val a = get("/api/v1/info")["alarms"]
        stats(num(a["warning"].long, "Warnings", "Avertissements", warnIfPositive(a["warning"].long)), num(a["critical"].long, "Critical", "Critiques", downIfPositive(a["critical"].long)))
    },
    W("nzbget", "NZBGet", { "Usenet" }, Auth.LOGIN) {
        val st = post("/jsonrpc", """{"method":"status","params":[],"id":1}""", basic)["result"]
        stats(
            rate(st["DownloadRate"].long, "↓"),
            Stat(fmtBytes(((st["RemainingSizeMB"].double ?: 0.0) * 1024 * 1024).toLong()), t("Remaining", "Restant")),
            Stat(fmtBytes(((st["DownloadedSizeMB"].double ?: 0.0) * 1024 * 1024).toLong()), t("Downloaded", "Téléchargé")),
        )
    },
    W("octoprint", "OctoPrint", { t("3D printer", "Imprimante 3D") }) {
        val p = get("/api/printer?apikey=${s.apiKey}")
        val job = runCatching { get("/api/job?apikey=${s.apiKey}") }.getOrNull()
        stats(
            Stat(p["state"]["text"].str ?: "—", t("State", "État")),
            Stat(p["temperature"]["tool0"]["actual"].double?.let { fmtDec(it, 0) + " °C" } ?: "—", t("Nozzle", "Buse")),
            Stat(p["temperature"]["bed"]["actual"].double?.let { fmtDec(it, 0) + " °C" } ?: "—", t("Bed", "Plateau")),
            Stat(job["progress"]["completion"].double?.let { fmtPercent(it) } ?: "—", t("Progress", "Progression")),
        )
    },
    W("ombi", "Ombi", { t("Requests", "Demandes") }) {
        val c = get("/api/v1/Request/count", key("ApiKey"))
        stats(num(c["pending"].long, "Pending", "En attente", warnIfPositive(c["pending"].long)), num(c["approved"].long, "Approved", "Approuvées"), num(c["available"].long, "Available", "Disponibles"))
    },
    W("opendtu", "OpenDTU", { t("Solar", "Solaire") }, Auth.NONE) {
        val tot = get("/api/livedata/status")["total"]
        stats(
            Stat("${fmtInt(tot["Power"]["v"].double?.toLong())} ${tot["Power"]["u"].str.orEmpty()}", t("Power", "Puissance")),
            Stat("${fmtInt(tot["YieldDay"]["v"].double?.toLong())} ${tot["YieldDay"]["u"].str.orEmpty()}", t("Today", "Aujourd’hui")),
        )
    },
    W("openmediavault", "OpenMediaVault", { "NAS" }, Auth.LOGIN) {
        val login = check(http.postJson(u("/rpc.php"), """{"service":"session","method":"login","params":{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}}"""))
        if (login.json()["response"]["authenticated"].bool == false) throw HttpException(401, t("Login rejected", "Connexion refusée"))
        val h = mapOf("Cookie" to cookies(login))
        suspend fun rpc(service: String, method: String, params: String = """{"limit":-1,"start":0}""") =
            post("/rpc.php", """{"service":"$service","method":"$method","params":$params}""", h)["response"]
        val info = runCatching { rpc("system", "getInformation", "null") }.getOrNull()
        val memTotal = info["memTotal"].double; val memUsed = info["memUsed"].double
        val services = runCatching { rpc("services", "getStatus").listOrData() }.getOrDefault(emptyList())
        val smart = runCatching { rpc("smart", "getList").listOrData().filter { it["monitor"].bool == true } }.getOrDefault(emptyList())
        val failedDisks = smart.count { it["overallstatus"].str != "GOOD" }
        val stopped = services.count { it["enabled"].bool != false && it["running"].bool != true }
        // OMV 7 : cpuUtilization ; versions précédentes : cpuUsage.
        val cpu = info["cpuUtilization"].double ?: info["cpuUsage"].double
        val updates = info["availablePkgUpdates"].long
        stats(
            Stat(fmtPercent(cpu), "CPU"),
            Stat(fmtPercent(info["memUtilization"].double?.let { if (it <= 1) it * 100 else it } ?: if (memTotal != null && memUsed != null && memTotal > 0) memUsed / memTotal * 100 else null), "RAM"),
            if (smart.isNotEmpty()) num(failedDisks, "Failing disks", "Disques en alerte", downIfPositive(failedDisks))
            else num(stopped, "Stopped services", "Services arrêtés", warnIfPositive(stopped)),
            if (updates != null) num(updates, "Updates", "Mises à jour", warnIfPositive(updates))
            else Stat(info["version"].str ?: "—", "Version"),
        )
    },
    W("openwrt", "OpenWrt", { t("Router", "Routeur") }, Auth.LOGIN) {
        val login = post("/ubus", """{"jsonrpc":"2.0","id":1,"method":"call","params":["00000000000000000000000000000000","session","login",{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}]}""")
        val session = login["result"][1]["ubus_rpc_session"].str ?: throw HttpException(401, t("Login rejected", "Connexion refusée"))
        val info = post("/ubus", """{"jsonrpc":"2.0","id":2,"method":"call","params":["$session","system","info",{}]}""")["result"][1]
        val total = info["memory"]["total"].double; val free = info["memory"]["free"].double
        stats(
            Stat(info["uptime"].long?.let { fmtDuration(it * 1000) } ?: "—", "Uptime"),
            Stat(info["load"][0].double?.let { fmtDec(it / 65536.0, 2) } ?: "—", t("Load", "Charge")),
            Stat(fmtPercent(if (total != null && free != null && total > 0) 100 - free / total * 100 else null), "RAM"),
        )
    },
    W("opnsense", "OPNsense", { "Firewall" }, Auth.KEY_SECRET) {
        val basic = mapOf("Authorization" to com.rackowl.net.Http.basic(s.apiKey, s.password))
        val act = get("/api/diagnostics/activity/getActivity", basic)["headers"].arr.mapNotNull { it.str }
        val idle = act.getOrNull(2)?.let { Regex(" ([0-9.]+)% idle").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
        val mem = act.getOrNull(3)?.let { Regex("Mem: (.+?) Active,").find(it)?.groupValues?.get(1) }
        val wan = runCatching { get("/api/diagnostics/traffic/interface", basic)["interfaces"]["wan"] }.getOrNull()
        stats(
            Stat(fmtPercent(idle?.let { 100 - it }), "CPU"),
            Stat(mem ?: "—", t("Memory", "Mémoire")),
            Stat(fmtBytes(wan["bytes received"].str?.toLongOrNull()), "↓ WAN"),
            Stat(fmtBytes(wan["bytes transmitted"].str?.toLongOrNull()), "↑ WAN"),
        )
    },
    W("peanut", "PeaNUT", { "UPS" }, keyLabel = { t("UPS name", "Nom de l’onduleur") }) {
        val d = get("/api/v1/devices/${s.apiKey}")
        fun v(a: String, b: String) = d[a].takeUnless { it.isNull } ?: d[b]
        stats(
            Stat(fmtPercent(v("battery.charge", "battery_charge").double), t("Battery", "Batterie")),
            Stat(fmtPercent(v("ups.load", "ups_load").double), t("Load", "Charge")),
            Stat(v("ups.status", "ups_status").str ?: "—", t("Status", "État")),
        )
    },
    W("pfsense", "pfSense", { "Firewall" }) {
        val sys = runCatching { get("/api/v2/status/system", key("X-API-Key"))["data"] }.getOrElse { get("/api/v1/status/system", basic)["data"] }
        val load = sys["cpu_load_avg"][0].double ?: sys["load_avg"][0].double
        stats(
            Stat(load?.let { fmtDec(it, 2) } ?: "—", t("Load", "Charge")),
            Stat(fmtPercent(sys["mem_usage"].double?.let { if (it <= 1) it * 100 else it }), "RAM"),
            Stat(sys["temp_c"].double?.let { fmtDec(it, 0) + " °C" } ?: "—", t("Temperature", "Température")),
        )
    },
    W("photoprism", "PhotoPrism", { "Photos" }, Auth.KEY_OR_LOGIN) {
        val r = if (s.username.isNotBlank()) post("/api/v1/session", """{"username":${quoteJson(s.username)},"password":${quoteJson(s.password)}}""")
        else post("/api/v1/session", "{}", bearer)
        val c = r["config"]["count"]
        stats(num(c["photos"].long, "Photos", "Photos"), num(c["videos"].long, "Videos", "Vidéos"), num(c["albums"].long, "Albums", "Albums"), num(c["people"].long, "People", "Personnes"))
    },
    W("plantit", "Plant-it", { t("Plants", "Plantes") }) {
        val st = get("/api/stats", key("Key"))
        stats(num(st["plantCount"].long, "Plants", "Plantes"), num(st["botanicalInfoCount"].long, "Species", "Espèces"), num(st["diaryEntryCount"].long, "Events", "Évènements"))
    },
    W("prometheus", "Prometheus", { "Monitoring" }, Auth.NONE) {
        val tg = get("/api/v1/targets?state=active")["data"]["activeTargets"].arr
        val down = tg.count { it["health"].str == "down" }
        stats(num(tg.count { it["health"].str == "up" }, "Up", "OK"), num(down, "Down", "En échec", downIfPositive(down)), num(tg.size, "Targets", "Cibles"))
    },
    W("proxmoxbackupserver", "Proxmox Backup Server", { t("Backup", "Sauvegarde") }, Auth.KEY_SECRET, { t("Token ID (user@pbs!name)", "ID du jeton (user@pbs!nom)") }) {
        val h = mapOf("Authorization" to "PBSAPIToken=${s.apiKey}:${s.password}")
        val ds = get("/api2/json/status/datastore-usage", h)["data"].arr.firstOrNull()
        val since = System.currentTimeMillis() / 1000 - 86_400
        val failed = get("/api2/json/nodes/localhost/tasks?errors=true&limit=100&since=$since", h)["total"].long
        val node = get("/api2/json/nodes/localhost/status", h)["data"]
        val used = ds["used"].double; val total = ds["total"].double
        val memU = node["memory"]["used"].double; val memT = node["memory"]["total"].double
        stats(
            Stat(fmtPercent(if (used != null && total != null && total > 0) used / total * 100 else null), t("Datastore", "Datastore")),
            num(failed, "Failed tasks (24 h)", "Échecs (24 h)", downIfPositive(failed)),
            Stat(fmtPercent(node["cpu"].double?.let { it * 100 }), "CPU"),
            Stat(fmtPercent(if (memU != null && memT != null && memT > 0) memU / memT * 100 else null), "RAM"),
        )
    },
    W("pterodactyl", "Pterodactyl", { t("Game servers", "Serveurs de jeu") }, keyLabel = { t("Application API key", "Clé API application") }) {
        val nodes = get("/api/application/nodes?include=servers", bearer)["data"].arr
        stats(num(nodes.size, "Nodes", "Nœuds"), num(nodes.sumOf { it["attributes"]["relationships"]["servers"]["data"].arr.size }, "Servers", "Serveurs"))
    },
    W("pyload", "pyLoad", { t("Downloads", "Téléchargements") }, Auth.LOGIN) {
        val st = get("/api/status_server", basic)
        stats(rate(st["speed"].long, "↓"), num(st["active"].long, "Active", "Actifs"), num(st["queue"].long, "Queue", "File"), num(st["total"].long, "Total", "Total"))
    },
    W("qui", "qui", { "Torrent" }) {
        val st = get("/api/torrents/cross-instance?limit=1", key("X-API-Key"))["stats"]
        stats(rate(st["totalDownloadSpeed"].long, "↓"), rate(st["totalUploadSpeed"].long, "↑"), num(st["downloading"].long, "Leeching", "En cours"), num(st["seeding"].long, "Seeding", "En partage"))
    },
    W("readarr", "Readarr", { t("Books", "Livres") }) {
        val h = key("X-Api-Key")
        val missing = get("/api/v1/wanted/missing?pageSize=1", h)["totalRecords"].long
        stats(
            num(missing, "Missing", "Manquants", warnIfPositive(missing)),
            num(get("/api/v1/queue/status", h)["totalCount"].long, "Queued", "En file"),
            num(get("/api/v1/book", h).arr.count { (it["statistics"]["bookFileCount"].long ?: 0) > 0 }, "Books", "Livres"),
        )
    },
    W("romm", "RomM", { t("Games", "Jeux") }, Auth.LOGIN) {
        val st = get("/api/stats", basic)
        stats(num(st["PLATFORMS"].long, "Platforms", "Plateformes"), num(st["ROMS"].long, "ROMs", "ROMs"), num(st["SAVES"].long, "Saves", "Sauvegardes"))
    },
    W("scrutiny", "Scrutiny", { t("Disks", "Disques") }, Auth.NONE) {
        val devices = get("/api/summary")["data"]["summary"].obj.values.filter { it["device"]["archived"].bool != true }
        val failed = devices.count { (it["device"]["device_status"].long ?: 0) != 0L }
        stats(num(devices.size - failed, "Passed", "OK"), num(failed, "Failed", "En échec", downIfPositive(failed)))
    },
    W("slskd", "slskd", { "Soulseek" }) {
        val h = key("X-API-Key")
        val app = get("/api/v0/application", h)
        stats(
            Stat(if (app["server"]["isConnected"].bool == true) t("Connected", "Connecté") else t("Disconnected", "Déconnecté"), "Soulseek"),
            num(get("/api/v0/transfers/downloads", h).arr.size, "Downloads", "Téléchargements"),
            num(get("/api/v0/transfers/uploads", h).arr.size, "Uploads", "Envois"),
            num(app["shares"]["files"].long, "Shared files", "Fichiers partagés"),
        )
    },
    W("spoolman", "Spoolman", { t("3D printing", "Impression 3D") }, Auth.NONE) {
        val spools = get("/api/v1/spool").arr
        val low = spools.count { (it["remaining_weight"].double ?: 1e9) < 100 }
        stats(num(spools.size, "Spools", "Bobines"), num(low, "Under 100 g", "Moins de 100 g", warnIfPositive(low)))
    },
    W("sportarr", "Sportarr", { t("Sports", "Sports") }) {
        val missing = get("/api/wanted/missing?page=1&pageSize=1&apikey=${s.apiKey}")["totalRecords"].long
        stats(
            num(missing, "Missing", "Manquants", warnIfPositive(missing)),
            num(get("/api/queue?apikey=${s.apiKey}").arr.size, "Queued", "En file"),
            num(get("/api/leagues?apikey=${s.apiKey}").arr.size, "Leagues", "Ligues"),
        )
    },
    W("stash", "Stash", { t("Media", "Médias") }) {
        val st = post("/graphql?apikey=${s.apiKey}", """{"query":"{ stats { scene_count image_count scenes_played } }"}""")["data"]["stats"]
        stats(num(st["scene_count"].long, "Scenes", "Scènes"), num(st["image_count"].long, "Images", "Images"), num(st["scenes_played"].long, "Played", "Vues"))
    },
)

internal val pingOnlyC: List<Pair<String, String>> = listOf(
    "nextdns" to "NextDNS", "omada" to "Omada", "pangolin" to "Pangolin", "proxmoxvm" to "Proxmox VM", "pulse" to "Pulse",
    "qnap" to "QNAP", "rutorrent" to "ruTorrent", "sparkyfitness" to "SparkyFitness",
)
