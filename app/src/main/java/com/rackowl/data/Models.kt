package com.rackowl.data

import kotlinx.serialization.Serializable
import com.rackowl.ui.t
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

/** Un service affiché sur le tableau de bord (équivalent d'une entrée de services.yaml). */
@Serializable
data class Service(
    val id: String = newId(),
    val name: String,
    val url: String,
    val description: String = "",
    /** Slug d'icône selfh.st (ex. « sonarr »), une URL complète, ou vide. */
    val icon: String = "",
    /** Identifiant d'intégration (voir Integrations.catalog), null = ping seul. */
    val widget: String? = null,
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val monitor: Boolean = true,
    val pingIntervalSec: Int = 30,
    val showInWidget: Boolean = true,
    /** Statistiques du widget affichées sur la tuile (positions dans la liste du widget) ; vide = description. */
    val tileStats: List<Int> = emptyList(),
    val hidden: Boolean = false,
    /** Nom du conteneur Docker associé (découverte Docker / labels homepage.*). */
    val container: String? = null,
    /** Source (Docker ou Komodo) qui héberge [container], pour le redémarrage. */
    val containerSource: String? = null,
    /** Serveur Komodo du conteneur (inutile pour une source Docker, qui est un seul hôte). */
    val containerHost: String? = null,
    val source: String = SOURCE_MANUAL,
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_DOCKER = "docker"
    }
}

@Serializable
data class Category(
    val id: String = newId(),
    val name: String,
    val hidden: Boolean = false,
    /** 0 = auto (selon la largeur), sinon 1 à 4 blocs par ligne. */
    val cols: Int = 0,
    val services: List<Service> = emptyList(),
)

@Serializable
enum class Notifier { ANDROID, GOTIFY, NTFY }

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
enum class Language { EN, FR }

@Serializable
enum class SourceType { DOCKER, KOMODO, GLANCES }

/** Une source configurée dans Réglages › Sources (plusieurs par type possibles). */
@Serializable
data class SourceConfig(
    val id: String = newId(),
    val type: SourceType,
    val name: String,
    val url: String,
    /** Komodo : clé API. Glances : utilisateur (optionnel). */
    val key: String = "",
    /** Komodo : secret API. Glances : mot de passe (optionnel). */
    val secret: String = "",
)

@Serializable
data class Settings(
    val refreshSec: Int = 30,
    val notifier: Notifier = Notifier.ANDROID,
    val gotifyUrl: String = "",
    val gotifyToken: String = "",
    val ntfyUrl: String = "https://ntfy.sh",
    val ntfyTopic: String = "",
    val ntfyToken: String = "",
    val theme: ThemeMode = ThemeMode.DARK,
    val sources: List<SourceConfig> = emptyList(),
    /** IP à utiliser pour chaque hôte de conteneurs (clé = PickHost.key), quand la détection ne suffit pas. */
    val hostIps: Map<String, String> = emptyMap(),
    // Anciens champs (une seule source par type), migrés vers [sources] au chargement.
    val dockerUrl: String = "",
    val komodoUrl: String = "",
    val komodoKey: String = "",
    val komodoSecret: String = "",
    val dozzleUrl: String = "",
    val dozzleUser: String = "",
    val dozzlePassword: String = "",
    val glancesUrl: String = "",
    val glancesName: String = "",
    val showResources: Boolean = true,
    val diskWarnPercent: Int = 80,
    val allowInsecureTls: Boolean = false,
    /** Hors du réseau local (ni Wi-Fi maison ni VPN), ne pas surveiller les services locaux. */
    val pauseOffLan: Boolean = true,
    /** Service de premier plan : surveillance continue avec notification permanente (comme Gotify). */
    val keepAlive: Boolean = false,
    /** Interrupteur général des pings (disponibilité et alertes hors ligne). */
    val pingEnabled: Boolean = true,
    val language: Language = Language.EN,
    /** Hôte de référence du réseau local (« ip:port ») ; vide = détection automatique. */
    val lanProbe: String = "",
    val muteUntil: Long = 0,
) {
    fun sourcesOf(type: SourceType) = sources.filter { it.type == type }

    /** Convertit les anciens champs à source unique en entrées de [sources]. */
    fun migrated(): Settings {
        if (dockerUrl.isBlank() && komodoUrl.isBlank() && glancesUrl.isBlank()) return this
        val extra = listOfNotNull(
            dockerUrl.takeIf { it.isNotBlank() }?.let { SourceConfig(type = SourceType.DOCKER, name = "Docker", url = it) },
            komodoUrl.takeIf { it.isNotBlank() }?.let { SourceConfig(type = SourceType.KOMODO, name = "Komodo", url = it, key = komodoKey, secret = komodoSecret) },
            glancesUrl.takeIf { it.isNotBlank() }?.let { SourceConfig(type = SourceType.GLANCES, name = glancesName.ifBlank { t("Main server", "Serveur principal") }, url = it) },
        )
        return copy(sources = sources + extra, dockerUrl = "", komodoUrl = "", komodoKey = "", komodoSecret = "", glancesUrl = "", glancesName = "")
    }
}

@Serializable
data class AppConfig(
    val categories: List<Category> = emptyList(),
    val settings: Settings = Settings(),
)

// ---- État d'exécution (résultats de surveillance) ----

@Serializable
enum class Level { NORMAL, WARN, DOWN }

@Serializable
data class Stat(val value: String, val label: String, val level: Level = Level.NORMAL)

@Serializable
data class DetailItem(val title: String, val subtitle: String = "", val progress: Float? = null)

/** Un point d'historique : latence en ms, ou null si le service était hors ligne. */
@Serializable
data class Sample(val t: Long, val latency: Long?)

@Serializable
data class ServiceStatus(
    val id: String,
    val up: Boolean? = null,
    val latencyMs: Long? = null,
    val httpCode: Int? = null,
    val error: String? = null,
    val checkedAt: Long = 0,
    val downSince: Long? = null,
    val failedAttempts: Int = 0,
    val stats: List<Stat> = emptyList(),
    val detailsTitle: String? = null,
    val details: List<DetailItem> = emptyList(),
    val statsAt: Long = 0,
    val statsError: String? = null,
    val history: List<Sample> = emptyList(),
)

@Serializable
data class Resources(
    val cpu: Double? = null,
    val mem: Double? = null,
    val disk: Double? = null,
    val uptime: String? = null,
    val tempC: Double? = null,
    val at: Long = 0,
    val error: String? = null,
)

@Serializable
enum class AlertKind { DOWN, UP, WARN }

@Serializable
data class AlertEvent(
    val id: String = newId(),
    val serviceId: String?,
    val service: String,
    val icon: String = "",
    val kind: AlertKind,
    val title: String,
    val detail: String,
    val time: Long = System.currentTimeMillis(),
    val read: Boolean = false,
)

@Serializable
data class Runtime(
    val statuses: Map<String, ServiceStatus> = emptyMap(),
    /** Ressources Glances par source (clé = SourceConfig.id). */
    val resourcesBySource: Map<String, Resources> = emptyMap(),
    val diskWarnedSources: Set<String> = emptySet(),
    /** Vrai quand l'hôte de référence du réseau local ne répond pas. */
    val offLan: Boolean = false,
    val offLanSince: Long? = null,
)

fun AppConfig.allServices(): List<Service> = categories.flatMap { it.services }

fun AppConfig.findService(id: String): Service? = allServices().firstOrNull { it.id == id }

fun AppConfig.categoryOf(id: String): Category? = categories.firstOrNull { c -> c.services.any { it.id == id } }

/** Exemple de départ, pour que le premier lancement montre la structure du tableau de bord. */
fun defaultConfig(): AppConfig = AppConfig(
    categories = listOf(
        Category(name = t("Media", "Médias")),
        Category(name = "Infra", cols = 3),
        Category(name = t("Tools", "Outils"), cols = 1),
    ),
)
