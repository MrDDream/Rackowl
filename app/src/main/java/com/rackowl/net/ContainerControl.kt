package com.rackowl.net

import com.rackowl.data.Service
import com.rackowl.ui.t
import com.rackowl.data.Settings
import com.rackowl.data.SourceConfig
import com.rackowl.data.SourceType

/** État et actions (démarrer, redémarrer, arrêter) sur le conteneur d'un service, via Docker ou Komodo. */
object ContainerControl {
    enum class Action(val docker: String, val komodo: String) {
        START("start", "StartContainer"),
        RESTART("restart", "RestartContainer"),
        STOP("stop", "StopContainer");

        val label: String get() = when (this) {
            START -> t("Start", "Démarrer")
            RESTART -> t("Restart", "Redémarrer")
            STOP -> t("Stop", "Arrêter")
        }
    }

    /** La source qui gère le conteneur : celle mémorisée, sinon la seule source Docker/Komodo. */
    fun sourceFor(s: Service, settings: Settings): SourceConfig? {
        if (s.container == null) return null
        val candidates = settings.sources.filter { it.type == SourceType.DOCKER || it.type == SourceType.KOMODO }
        return candidates.firstOrNull { it.id == s.containerSource } ?: candidates.singleOrNull()
    }

    private fun komodoHeaders(src: SourceConfig) = mapOf("x-api-key" to src.key.trim(), "x-api-secret" to src.secret.trim())

    private fun komodoError(r: HttpResponse): Nothing =
        throw HttpException(r.code, runCatching { r.json()["error"].str }.getOrNull()?.let { "$it (HTTP ${r.code})" } ?: "HTTP ${r.code}")

    private suspend fun komodoContainers(http: Http, src: SourceConfig): List<kotlinx.serialization.json.JsonElement> {
        var r = http.postJson(join(src.url, "/read/ListAllContainers"), """{"limit":0}""", komodoHeaders(src))
        if (!r.ok) r = http.postJson(join(src.url, "/read/ListAllDockerContainers"), "{}", komodoHeaders(src))
        if (!r.ok) komodoError(r)
        return r.json().arr
    }

    /** Serveur Komodo du conteneur : celui mémorisé, sinon retrouvé par le nom du conteneur. */
    private suspend fun komodoServer(http: Http, src: SourceConfig, s: Service): String =
        s.containerHost ?: komodoContainers(http, src).firstOrNull { it["name"].str == s.container }?.let { it["server_name"].str ?: it["server_id"].str }
            ?: throw HttpException(404, t("Container ${s.container} not found in Komodo", "Conteneur ${s.container} introuvable dans Komodo"))

    /** État Docker du conteneur (« running », « exited »…), ou null s'il est introuvable. */
    suspend fun state(http: Http, settings: Settings, s: Service): String? {
        val src = sourceFor(s, settings) ?: return null
        val name = s.container ?: return null
        return when (src.type) {
            SourceType.DOCKER -> http.getJson(join(Docker.normalize(src.url), "/containers/$name/json"))["State"]["Status"].str
            else -> komodoContainers(http, src)
                .firstOrNull { it["name"].str == name && (s.containerHost == null || it["server_name"].str == s.containerHost) }
                ?.get("state")?.str?.lowercase()
        }
    }

    suspend fun run(http: Http, settings: Settings, s: Service, action: Action) {
        val src = sourceFor(s, settings) ?: throw HttpException(0, t("No Docker or Komodo source for this container", "Aucune source Docker ou Komodo pour ce conteneur"))
        val name = s.container ?: return
        when (src.type) {
            SourceType.DOCKER -> {
                val r = http.request(join(Docker.normalize(src.url), "/containers/$name/${action.docker}"), "POST")
                // 304 = déjà dans l'état demandé.
                if (r.code !in 200..299 && r.code != 304) throw HttpException(r.code, t("Docker rejected the action (HTTP ${r.code})", "Action refusée par Docker (HTTP ${r.code})"))
            }
            else -> {
                val server = komodoServer(http, src, s)
                val body = """{"server":${q(server)},"container":${q(name)}}"""
                val r = http.postJson(join(src.url, "/execute/${action.komodo}"), body, komodoHeaders(src))
                if (!r.ok) komodoError(r)
            }
        }
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
