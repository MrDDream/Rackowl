package com.rackowl.integrations

import com.rackowl.data.Resources
import com.rackowl.ui.t
import com.rackowl.net.Http
import com.rackowl.net.HttpException
import com.rackowl.net.arr
import com.rackowl.net.double
import com.rackowl.net.get
import com.rackowl.net.join
import com.rackowl.net.str

/** Lecture des ressources serveur via l'API REST de Glances (v4, repli v3). */
object GlancesClient {
    suspend fun read(http: Http, url: String, user: String = "", password: String = ""): Resources {
        val h = if (user.isNotBlank()) mapOf("Authorization" to Http.basic(user, password)) else emptyMap()
        val api = listOf("4", "3").firstOrNull { v ->
            runCatching { http.get(join(url, "/api/$v/quicklook"), h).ok }.getOrDefault(false)
        } ?: throw HttpException(0, t("Glances unreachable", "Glances injoignable"))
        val base = join(url, "/api/$api")
        val quick = http.getJson("$base/quicklook", h)
        val fs = runCatching { http.getJson("$base/fs", h).arr }.getOrDefault(emptyList())
        // Disque principal : « / » si présent, sinon le plus gros volume.
        val root = fs.firstOrNull { it["mnt_point"].str == "/" }
            ?: fs.maxByOrNull { it["size"].double ?: 0.0 }
        val uptime = runCatching { http.get("$base/uptime", h).body.trim().trim('"') }.getOrNull()
        val sensors = runCatching { http.getJson("$base/sensors", h).arr }.getOrDefault(emptyList())
        val temp = sensors.filter { it["type"].str == "temperature_core" }.mapNotNull { it["value"].double }.maxOrNull()
        return Resources(
            cpu = quick["cpu"].double,
            mem = quick["mem"].double,
            disk = root?.get("percent").double,
            uptime = uptime?.let(::shortUptime),
            tempC = temp,
            at = System.currentTimeMillis(),
        )
    }

    /** « 23 days, 4:12:01 » → « 23 j » ; « 4:12:01 » → « 4 h ». */
    private fun shortUptime(raw: String): String {
        Regex("(\\d+) day").find(raw)?.let { return it.groupValues[1] + " j" }
        Regex("^(\\d+):").find(raw)?.let { return it.groupValues[1] + " h" }
        return raw
    }
}
