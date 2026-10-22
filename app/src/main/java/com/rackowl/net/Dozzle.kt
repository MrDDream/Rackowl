package com.rackowl.net

import com.rackowl.data.AppJson
import com.rackowl.ui.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Client minimal pour Dozzle : liste des conteneurs et flux de logs (Server-Sent Events). */
class Dozzle(private val http: Http, baseUrl: String, private val user: String, private val password: String) {
    private val base = baseUrl.trimEnd('/')
    private var cookie: String? = null

    data class Container(
        val id: String, val name: String, val host: String, val state: String,
        val health: String?, val image: String, val startedAt: String?,
        /** Ports publiés au format Dozzle, ex. « :8080->8080/tcp ». */
        val ports: List<String> = emptyList(),
        val labels: Map<String, String> = emptyMap(),
    )

    /** Un hôte Docker vu par Dozzle (agent ou local). [address] = IP sans port, si connue. */
    data class Host(val id: String, val name: String, val address: String?)

    enum class LogLevel { INF, WRN, ERR }

    data class LogLine(val time: Long, val level: LogLevel, val message: String)

    private suspend fun authHeaders(): Map<String, String> {
        if (user.isBlank()) return emptyMap()
        if (cookie == null) {
            val r = http.request(join(base, "/api/token"), "POST", body =
                "username=${enc(user)}&password=${enc(password)}".toRequestBody(FORM_MEDIA))
            cookie = r.headers.entries.firstOrNull { it.key.equals("set-cookie", true) }?.value
                ?.firstOrNull { it.startsWith("jwt=") }?.substringBefore(';')
            if (cookie == null) throw HttpException(r.code, t("Dozzle login rejected", "Connexion Dozzle refusée"))
        }
        return mapOf("Cookie" to cookie!!)
    }

    /**
     * Action sur un conteneur (start, stop, restart). Dozzle doit être lancé avec DOZZLE_ENABLE_ACTIONS=true,
     * sinon la route n'existe pas (404) ou est refusée (403).
     */
    suspend fun action(c: Container, action: String) {
        val r = http.request(join(base, "/api/hosts/${c.host}/containers/${c.id}/actions/$action"), "POST", authHeaders())
        if (r.code == 404 || r.code == 403 || r.code == 405) throw HttpException(r.code, t(
            "Actions are disabled in Dozzle (set DOZZLE_ENABLE_ACTIONS=true)",
            "Actions désactivées dans Dozzle (active DOZZLE_ENABLE_ACTIONS=true)",
        ))
        if (!r.ok) throw HttpException(r.code, "HTTP ${r.code}")
    }

    /** Lit les premiers évènements de /api/events/stream jusqu'à la liste des conteneurs. */
    suspend fun containers(): List<Container> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(join(base, "/api/events/stream")).apply {
            authHeaders().forEach { (k, v) -> header(k, v) }
        }.build()
        val call = http.streaming.newCall(req)
        try {
            withTimeoutOrNull(8_000) {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code}")
                    val src = resp.body!!.source()
                    var event = ""
                    while (isActive) {
                        val line = src.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                            line.startsWith("data:") && event == "containers-changed" ->
                                return@withTimeoutOrNull parseContainers(line.removePrefix("data:").trim())
                        }
                    }
                    null
                }
            } ?: throw HttpException(0, t("Dozzle sent no container list", "Aucune liste de conteneurs reçue de Dozzle"))
        } finally {
            call.cancel()
        }
    }

    private fun parseContainers(json: String) = AppJson.parseToJsonElement(json).arr.map { c ->
        Container(
            id = c["id"].str.orEmpty(),
            name = c["name"].str.orEmpty(),
            host = c["host"].str.orEmpty(),
            state = c["state"].str.orEmpty(),
            health = c["health"].str,
            image = c["image"].str.orEmpty(),
            startedAt = c["startedAt"].str,
            ports = c["ports"].arr.mapNotNull { it.str },
            labels = c["labels"].obj.mapValues { it.value.str.orEmpty() },
        )
    }

    /**
     * Noms et adresses des hôtes. Dozzle ne les expose pas dans son API :
     * ils sont dans le JSON de configuration embarqué dans sa page d'accueil.
     */
    suspend fun hosts(): Map<String, Host> = runCatching {
        val html = http.get(base + "/", authHeaders()).body
        val json = Regex("<script[^>]*id=\"config__json\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return@runCatching emptyMap()
        AppJson.parseToJsonElement(json)["hosts"].arr.associate { h ->
            val id = h["id"].str.orEmpty()
            val endpoint = h["endpoint"].str.orEmpty()
            val address = endpoint.substringBeforeLast(':').takeIf { endpoint.contains(':') && it.isNotBlank() && it != "local" }
            id to Host(id, h["name"].str ?: id.take(8), address)
        }
    }.getOrDefault(emptyMap())

    /**
     * Flux continu des lignes de log d'un conteneur. Dozzle v9+ : /api/containers/logs/stream
     * (les niveaux doivent être listés, sinon aucune ligne n'est renvoyée) ; repli sur l'ancienne route v8.
     */
    fun logs(c: Container): Flow<LogLine> = callbackFlow {
        val levels = listOf("info", "debug", "trace", "warn", "error", "fatal", "unknown").joinToString("") { "&levels=$it" }
        val urls = listOf(
            join(base, "/api/containers/logs/stream?container=${c.host}/${c.id}&stdout=1&stderr=1$levels"),
            join(base, "/api/hosts/${c.host}/containers/${c.id}/logs/stream?stdout=1&stderr=1"),
        )
        val headers = authHeaders()
        var call: okhttp3.Call? = null
        // La lecture bloque : elle tourne à part, et awaitClose annule l'appel HTTP à l'arrêt du flux.
        launch(Dispatchers.IO) {
            try {
                for ((i, url) in urls.withIndex()) {
                    val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                    val current = http.streaming.newCall(req).also { call = it }
                    val done = current.execute().use { resp ->
                        if (resp.code == 404 && i < urls.lastIndex) return@use false
                        if (!resp.isSuccessful) throw HttpException(resp.code, "HTTP ${resp.code}")
                        val src = resp.body!!.source()
                        var event = ""
                        while (isActive) {
                            val line = src.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> event = ""
                                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                                // Lignes de log : évènement par défaut, ou lot d'historique (logs-backfill).
                                line.startsWith("data:") && (event.isEmpty() || event == "message" || event == "logs-backfill") ->
                                    parseData(line.removePrefix("data:").trim()).forEach { trySend(it) }
                            }
                        }
                        true
                    }
                    if (done) break
                }
                channel.close()
            } catch (e: Exception) {
                channel.close(if (call?.isCanceled() == true) null else e)
            }
        }
        awaitClose { call?.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun parseData(json: String): List<LogLine> = runCatching {
        val j = AppJson.parseToJsonElement(json)
        if (j is kotlinx.serialization.json.JsonArray) j.mapNotNull { parseEvent(it) } else listOfNotNull(parseEvent(j))
    }.getOrDefault(emptyList())

    private fun parseEvent(j: kotlinx.serialization.json.JsonElement): LogLine? {
        val m = j["m"] ?: return null
        // Logs JSON (« complex ») : on montre le message plutôt que l'objet brut.
        val text = when (m) {
            is JsonPrimitive -> m.content
            else -> listOf("message", "msg", "text").firstNotNullOfOrNull { m[it].str } ?: m.toString()
        }
        val msg = text.replace(ANSI, "")
        return LogLine(j["ts"].long ?: System.currentTimeMillis(), levelOf(j["l"].str.orEmpty(), msg), msg)
    }

    companion object {
        private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
        private val ERR = Regex("\\b(error|err|fatal|panic|critical|exception)\\b", RegexOption.IGNORE_CASE)
        private val WRN = Regex("\\b(warn|warning|wrn)\\b", RegexOption.IGNORE_CASE)

        fun levelOf(level: String, msg: String): LogLevel = when (level.lowercase()) {
            "error", "fatal", "critical", "panic" -> LogLevel.ERR
            "warn", "warning" -> LogLevel.WRN
            "info", "debug", "trace" -> LogLevel.INF
            else -> when {
                ERR.containsMatchIn(msg) -> LogLevel.ERR
                WRN.containsMatchIn(msg) -> LogLevel.WRN
                else -> LogLevel.INF
            }
        }

        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    }
}
