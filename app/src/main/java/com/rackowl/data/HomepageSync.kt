package com.rackowl.data

import com.rackowl.integrations.Integrations
import com.rackowl.net.Http
import com.rackowl.net.HttpException
import com.rackowl.net.arr
import com.rackowl.net.get
import com.rackowl.net.join
import com.rackowl.net.str
import com.rackowl.ui.t
import kotlinx.serialization.json.JsonElement

/**
 * Synchronisation avec une instance Homepage via son API /api/services.
 * Homepage n'y expose pas les clés des widgets : on met à jour nom, lien, description, icône,
 * type de widget et conteneur, on ajoute les nouveaux services, et on garde tout le reste en local
 * (clés, infos de tuile, réglages d'alerte). Rien n'est supprimé.
 */
object HomepageSync {
    data class Result(val config: AppConfig, val added: Int, val updated: Int)

    private data class Remote(val group: String, val name: String, val href: String, val description: String, val icon: String, val widget: String?, val container: String?)

    suspend fun fetch(http: Http, url: String, user: String, password: String): JsonElement {
        val h = if (user.isNotBlank()) mapOf("Authorization" to Http.basic(user, password)) else emptyMap()
        val r = http.get(join(url, "/api/services"), h)
        if (!r.ok) throw HttpException(r.code, t("Homepage answered HTTP ${r.code}", "Homepage a répondu HTTP ${r.code}"))
        return r.json()
    }

    private fun flatten(groups: List<JsonElement>, out: MutableList<Remote>) {
        groups.forEach { g ->
            val group = g["name"].str ?: return@forEach
            g["services"].arr.forEach { s ->
                val href = s["href"].str ?: s["siteMonitor"].str ?: return@forEach
                out += Remote(
                    group = group,
                    name = s["name"].str ?: return@forEach,
                    href = href,
                    description = s["description"].str.orEmpty(),
                    icon = s["icon"].str.orEmpty().removeSuffix(".png").removeSuffix(".svg").removeSuffix(".webp"),
                    widget = Integrations.fromHomepageType((s["widgets"].arr.firstOrNull() ?: s["widget"])["type"].str),
                    container = s["container"].str,
                )
            }
            flatten(g["groups"].arr, out)
        }
    }

    /** Fusionne la réponse de /api/services dans [base]. */
    fun merge(base: AppConfig, json: JsonElement): Result {
        val remote = mutableListOf<Remote>().also { flatten(json.arr, it) }
        var cats = base.categories
        var added = 0; var updated = 0
        remote.forEach { r ->
            val existing = cats.flatMap { it.services }.firstOrNull { it.name.equals(r.name, true) }
            if (existing != null) {
                val next = existing.copy(
                    url = r.href, description = r.description.ifBlank { existing.description },
                    icon = r.icon.ifBlank { existing.icon }, widget = r.widget ?: existing.widget,
                    container = r.container ?: existing.container,
                )
                if (next != existing) {
                    updated++
                    cats = cats.map { c -> c.copy(services = c.services.map { if (it.id == existing.id) next else it }) }
                }
            } else {
                added++
                val svc = Service(name = r.name, url = r.href, description = r.description, icon = r.icon.ifBlank { slugify(r.name) }, widget = r.widget, container = r.container)
                if (cats.none { it.name.equals(r.group, true) }) cats = cats + Category(name = r.group)
                cats = cats.map { c -> if (c.name.equals(r.group, true)) c.copy(services = c.services + svc) else c }
            }
        }
        return Result(base.copy(categories = cats), added, updated)
    }
}
