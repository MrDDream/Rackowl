package com.rackowl.net

import com.rackowl.data.Category
import com.rackowl.ui.t
import com.rackowl.data.Service
import com.rackowl.data.slugify
import com.rackowl.integrations.Integrations

/** Accès direct au démon Docker (API TCP, ex. via docker-socket-proxy). */
object Docker {
    data class Container(val id: String, val name: String, val state: String, val labels: Map<String, String>)

    fun normalize(url: String): String =
        url.trim().replaceFirst(Regex("^tcp://"), "http://").let { if (it.startsWith("http")) it else "http://$it" }

    suspend fun containers(http: Http, url: String): List<Container> =
        http.getJson(join(normalize(url), "/containers/json?all=1")).arr.map { c ->
            Container(
                id = c["Id"].str.orEmpty(),
                name = c["Names"][0].str.orEmpty().trimStart('/'),
                state = c["State"].str.orEmpty(),
                labels = c["Labels"].obj.mapValues { it.value.str.orEmpty() },
            )
        }

    /**
     * Fusionne les conteneurs portant des labels homepage.* dans la configuration :
     * met à jour ceux déjà découverts, ajoute les nouveaux dans leur groupe.
     * Renvoie les catégories et le nombre de services ajoutés.
     */
    fun merge(categories: List<Category>, containers: List<Container>, sourceId: String? = null): Pair<List<Category>, Int> {
        var cats = categories
        var added = 0
        containers.filter { it.labels.keys.any { k -> k.startsWith("homepage.") } }.forEach { c ->
            val l = c.labels
            val name = l["homepage.name"] ?: c.name
            val group = l["homepage.group"] ?: "Docker"
            val href = l["homepage.href"] ?: return@forEach
            val existing = cats.flatMap { it.services }.firstOrNull { it.source == Service.SOURCE_DOCKER && it.container == c.name }
            val svc = (existing ?: Service(name = name, url = href, source = Service.SOURCE_DOCKER, container = c.name)).copy(
                name = name,
                url = href,
                description = l["homepage.description"] ?: existing?.description.orEmpty(),
                icon = l["homepage.icon"]?.removeSuffix(".png")?.removeSuffix(".svg") ?: existing?.icon ?: slugify(name),
                widget = Integrations.fromHomepageType(l["homepage.widget.type"]) ?: existing?.widget,
                apiKey = l["homepage.widget.key"] ?: existing?.apiKey.orEmpty(),
                username = l["homepage.widget.username"] ?: existing?.username.orEmpty(),
                password = l["homepage.widget.password"] ?: existing?.password.orEmpty(),
                containerSource = sourceId ?: existing?.containerSource,
            )
            cats = if (existing != null) {
                cats.map { cat -> cat.copy(services = cat.services.map { if (it.id == svc.id) svc else it }) }
            } else {
                added++
                if (cats.none { it.name.equals(group, true) }) cats = cats + Category(name = group)
                cats.map { cat -> if (cat.name.equals(group, true)) cat.copy(services = cat.services + svc) else cat }
            }
        }
        return cats to added
    }
}

/** Komodo Core : simple test de connexion (nombre de serveurs et de stacks). */
object Komodo {
    suspend fun summary(http: Http, url: String, key: String, secret: String): String {
        val h = mapOf("x-api-key" to key.trim(), "x-api-secret" to secret.trim())
        // Même forme que le client officiel : POST {url}/read/{Type} avec les paramètres en JSON.
        suspend fun list(type: String): Int {
            val r = http.postJson(join(url, "/read/$type"), "{}", h)
            if (!r.ok) {
                val msg = runCatching { r.json()["error"].str }.getOrNull()
                throw HttpException(r.code, if (msg != null) "$msg (HTTP ${r.code})" else "HTTP ${r.code}")
            }
            return r.json().arr.size
        }
        val servers = list("ListServers"); val stacks = list("ListStacks")
        return t("$servers servers · $stacks stacks", "$servers serveurs · $stacks stacks")
    }
}
