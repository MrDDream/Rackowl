package com.rackowl.net

import com.rackowl.BuildConfig

/** Vérification des nouvelles versions via l'API des releases GitHub du projet. */
object Updates {
    const val RELEASES_URL = "https://github.com/MrDDream/Rackowl/releases/latest"
    private const val API = "https://api.github.com/repos/MrDDream/Rackowl/releases/latest"

    data class Release(val version: String, val url: String)

    /** Dernière release publiée, ou null si elle n'est pas plus récente que la version installée. */
    suspend fun check(http: Http): Release? {
        val j = http.getJson(API, mapOf("Accept" to "application/vnd.github+json"))
        val tag = j["tag_name"].str?.removePrefix("v") ?: return null
        return if (isNewer(tag, BuildConfig.VERSION_NAME)) Release(tag, j["html_url"].str ?: RELEASES_URL) else null
    }

    /** Comparaison de versions « 0.10.2 » > « 0.9.9 », suffixes (-beta…) ignorés. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
