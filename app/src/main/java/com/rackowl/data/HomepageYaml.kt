package com.rackowl.data

import com.rackowl.integrations.Integrations
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.common.FlowStyle

/** Import / export des fichiers de configuration de Homepage (gethomepage.dev). */
object HomepageYaml {

    data class ImportResult(val config: AppConfig, val services: Int, val files: List<String>)

    /** Fusionne un ou plusieurs fichiers (services.yaml, settings.yaml, widgets.yaml) dans [base]. */
    fun import(base: AppConfig, files: List<Pair<String, String>>): ImportResult {
        var cfg = base
        var count = 0
        val recognized = mutableListOf<String>()
        for ((name, text) in files) {
            val doc = Load(LoadSettings.builder().build()).loadFromString(text) ?: continue
            when {
                doc is List<*> && doc.any { it is Map<*, *> && it.values.firstOrNull() is List<*> } -> {
                    val (c, n) = importServices(cfg, doc); cfg = c; count += n; recognized += name
                }
                doc is List<*> -> { cfg = importWidgets(cfg, doc); recognized += name }
                doc is Map<*, *> && doc["layout"] is Map<*, *> -> { cfg = importLayout(cfg, doc["layout"] as Map<*, *>); recognized += name }
            }
        }
        return ImportResult(cfg, count, recognized)
    }

    private fun importServices(base: AppConfig, groups: List<*>): Pair<AppConfig, Int> {
        var cats = base.categories
        var count = 0
        for (g in groups) {
            val group = g as? Map<*, *> ?: continue
            for ((groupName, entries) in group) {
                val gName = groupName.toString()
                val services = (entries as? List<*>).orEmpty().mapNotNull { e ->
                    val (sName, props) = (e as? Map<*, *>)?.entries?.firstOrNull() ?: return@mapNotNull null
                    val p = props as? Map<*, *> ?: return@mapNotNull null
                    val w = p["widget"] as? Map<*, *>
                    val href = (p["href"] ?: p["siteMonitor"] ?: w?.get("url"))?.toString() ?: return@mapNotNull null
                    Service(
                        name = sName.toString(),
                        url = href,
                        description = p["description"]?.toString().orEmpty(),
                        icon = p["icon"]?.toString()?.removeSuffix(".png")?.removeSuffix(".svg")?.removeSuffix(".webp")
                            ?: slugify(sName.toString()),
                        widget = Integrations.fromHomepageType(w?.get("type")?.toString()),
                        apiKey = w?.get("key")?.toString().orEmpty(),
                        username = w?.get("username")?.toString().orEmpty(),
                        password = w?.get("password")?.toString().orEmpty(),
                        container = p["container"]?.toString(),
                    )
                }
                // Ne pas dupliquer un service déjà présent (même nom dans le même groupe).
                val existing = cats.firstOrNull { it.name.equals(gName, true) }
                val fresh = services.filter { s -> existing?.services?.none { it.name.equals(s.name, true) } ?: true }
                count += fresh.size
                cats = if (existing != null) cats.map { if (it === existing) it.copy(services = it.services + fresh) else it }
                else cats + Category(name = gName, services = fresh)
            }
        }
        return base.copy(categories = cats) to count
    }

    private fun importWidgets(base: AppConfig, widgets: List<*>): AppConfig {
        val glances = widgets.mapNotNull { (it as? Map<*, *>)?.get("glances") as? Map<*, *> }.firstOrNull()
        val url = glances?.get("url")?.toString() ?: return base
        if (base.settings.sources.any { it.type == SourceType.GLANCES && it.url == url }) return base
        val source = SourceConfig(type = SourceType.GLANCES, name = "Serveur principal", url = url)
        return base.copy(settings = base.settings.copy(sources = base.settings.sources + source, showResources = true))
    }

    private fun importLayout(base: AppConfig, layout: Map<*, *>): AppConfig = base.copy(
        categories = base.categories.map { c ->
            val l = layout[c.name] as? Map<*, *> ?: return@map c
            val cols = (l["columns"] as? Number)?.toInt()?.coerceIn(1, 4)
            if (l["style"] == "row" && cols != null) c.copy(cols = cols) else c
        },
    )

    /** services.yaml au format Homepage. */
    fun exportServices(cfg: AppConfig): String {
        val doc = cfg.categories.map { c ->
            mapOf(c.name to c.services.map { s ->
                val props = linkedMapOf<String, Any>("href" to s.url)
                if (s.description.isNotBlank()) props["description"] = s.description
                if (s.icon.isNotBlank()) props["icon"] = if (s.icon.startsWith("http")) s.icon else "sh-${s.icon}.png"
                if (s.container != null) props["container"] = s.container
                if (s.monitor) props["siteMonitor"] = s.url
                Integrations.byId(s.widget)?.let { i ->
                    val w = linkedMapOf<String, Any>("type" to i.id, "url" to s.url)
                    if (s.apiKey.isNotBlank()) w["key"] = s.apiKey
                    if (s.username.isNotBlank()) w["username"] = s.username
                    if (s.password.isNotBlank()) w["password"] = s.password
                    props["widget"] = w
                }
                mapOf(s.name to props)
            })
        }
        val settings = DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).setIndent(2).setIndicatorIndent(2)
            .setIndentWithIndicator(true).build()
        return "---\n# Exporté par Rackowl\n" + Dump(settings).dumpToString(doc)
    }
}
