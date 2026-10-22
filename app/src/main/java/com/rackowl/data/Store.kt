package com.rackowl.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Un document JSON persisté dans filesDir et exposé en StateFlow.
 * Toutes les écritures passent par [update], sérialisées par un mutex.
 */
class JsonDocument<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    migrate: (T) -> T = { it },
    /** Chiffrement au repos (texte écrit ↔ texte lu) ; identité par défaut. */
    private val seal: (String) -> String = { it },
    private val unseal: (String) -> String = { it },
    default: () -> T,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(migrate(load() ?: default()))
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value

    private fun load(): T? = try {
        if (file.exists()) AppJson.decodeFromString(serializer, unseal(file.readText())) else null
    } catch (e: Exception) {
        Log.w("RackowlApp", "Lecture impossible de ${file.name}", e)
        null
    }

    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val next = transform(_state.value)
        if (next != _state.value) {
            _state.value = next
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(seal(AppJson.encodeToString(serializer, next)))
            tmp.renameTo(file)
        }
        next
    }
}

class Store(context: Context) {
    private val dir = context.filesDir

    /** Configuration chiffrée : elle contient les clés API et mots de passe des services. */
    val config = JsonDocument(
        File(dir, "config.json"), AppConfig.serializer(), { it.copy(settings = it.settings.migrated()) },
        seal = Crypto::encrypt, unseal = Crypto::decrypt,
    ) { defaultConfig() }
    val runtime = JsonDocument(File(dir, "runtime.json"), Runtime.serializer()) { Runtime() }
    val alerts = JsonDocument(File(dir, "alerts.json"), ListSerializer(AlertEvent.serializer())) { emptyList() }

    // ---- Configuration ----

    suspend fun updateSettings(transform: (Settings) -> Settings) =
        config.update { it.copy(settings = transform(it.settings)) }

    suspend fun updateCategories(transform: (List<Category>) -> List<Category>) =
        config.update { it.copy(categories = transform(it.categories)) }

    /** Ajoute ou remplace le service ; il garde sa place s'il reste dans la même catégorie. */
    suspend fun saveService(service: Service, categoryId: String) = updateCategories { cats ->
        cats.map { c ->
            val idx = c.services.indexOfFirst { it.id == service.id }
            when {
                c.id == categoryId && idx >= 0 -> c.copy(services = c.services.toMutableList().also { it[idx] = service })
                c.id == categoryId -> c.copy(services = c.services + service)
                idx >= 0 -> c.copy(services = c.services.filterNot { it.id == service.id })
                else -> c
            }
        }
    }

    suspend fun deleteService(id: String) {
        updateCategories { cats -> cats.map { c -> c.copy(services = c.services.filterNot { it.id == id }) } }
        runtime.update { it.copy(statuses = it.statuses - id) }
    }

    // ---- Alertes ----

    suspend fun addAlert(event: AlertEvent) = alerts.update { (listOf(event) + it).take(MAX_ALERTS) }

    suspend fun markAllRead() = alerts.update { list -> list.map { it.copy(read = true) } }

    companion object {
        const val MAX_ALERTS = 300
    }
}
