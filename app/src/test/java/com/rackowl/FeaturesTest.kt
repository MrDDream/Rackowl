package com.rackowl

import com.rackowl.data.AppConfig
import com.rackowl.data.AppJson
import com.rackowl.data.Category
import com.rackowl.data.HomepageSync
import com.rackowl.data.Service
import com.rackowl.data.allServices
import com.rackowl.data.inDailyWindow
import com.rackowl.data.parseHm
import com.rackowl.monitor.Monitor
import com.rackowl.net.Updates
import com.rackowl.net.maskHeaders
import com.rackowl.net.maskSecrets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FeaturesTest {
    private fun at(h: Int, m: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0)
    }.timeInMillis

    @Test
    fun dailyWindows() {
        assertEquals(22 * 60, parseHm("22:00")); assertNull(parseHm("25:00")); assertNull(parseHm("abc"))
        assertTrue(inDailyWindow(at(2, 30), "02:00", "04:00"))
        assertFalse(inDailyWindow(at(4, 0), "02:00", "04:00"))
        // Plage à cheval sur minuit
        assertTrue(inDailyWindow(at(23, 15), "22:00", "07:00"))
        assertTrue(inDailyWindow(at(6, 59), "22:00", "07:00"))
        assertFalse(inDailyWindow(at(12, 0), "22:00", "07:00"))
    }

    @Test
    fun longHistoryBuckets() {
        val t0 = 1_800_000_000_000L - 1_800_000_000_000L % (6 * 3600_000L)
        var b = Monitor.appendBucket(emptyList(), t0 + 1000, 40)
        b = Monitor.appendBucket(b, t0 + 2000, null)
        b = Monitor.appendBucket(b, t0 + 3000, 60)
        assertEquals(1, b.size); assertEquals(2, b[0].up); assertEquals(3, b[0].total); assertEquals(100L, b[0].latencySum)
        b = Monitor.appendBucket(b, t0 + 6 * 3600_000L, 10)
        assertEquals(2, b.size)
        b = Monitor.appendBucket(b, t0 + 31 * 86_400_000L, 10)
        assertEquals("plus de 30 jours purgés", 1, b.size)
    }

    @Test
    fun versions() {
        assertTrue(Updates.isNewer("0.3.0", "0.2.0"))
        assertTrue(Updates.isNewer("0.10.0", "0.9.9"))
        assertTrue(Updates.isNewer("1.0", "0.9.9"))
        assertFalse(Updates.isNewer("0.2.0", "0.2.0"))
        assertFalse(Updates.isNewer("0.2.0-beta", "0.2.0"))
    }

    @Test
    fun secretsAreMasked() {
        assertEquals("http://h/api?apikey=•••&x=1", maskSecrets("http://h/api?apikey=abc123&x=1"))
        assertEquals("/rpc?_sid=•••", maskSecrets("/rpc?_sid=zzz"))
        assertEquals("""{"password":"•••","user":"bob"}""", maskSecrets("""{"password":"hunter2","user":"bob"}"""))
        val h = maskHeaders(mapOf("X-Api-Key" to "k", "Authorization" to "Bearer t", "Accept" to "json"))
        assertEquals(mapOf("X-Api-Key" to "•••", "Authorization" to "•••", "Accept" to "json"), h)
    }

    @Test
    fun homepageSyncKeepsLocalSettings() {
        val local = Service(name = "Sonarr", url = "http://old:8989", widget = "sonarr", apiKey = "secret", tileStats = listOf(0))
        val base = AppConfig(categories = listOf(Category(name = "Media", services = listOf(local))))
        val json = AppJson.parseToJsonElement(
            """[{"name":"Media","services":[
                 {"name":"Sonarr","href":"http://nas:8989","icon":"sonarr.png","widgets":[{"type":"sonarr"}]},
                 {"name":"Radarr","href":"http://nas:7878","description":"Movies","widgets":[{"type":"radarr"}]}],
               "groups":[{"name":"Tools","services":[{"name":"n8n","href":"http://nas:5678"}]}]}]""",
        )
        val r = HomepageSync.merge(base, json)
        assertEquals(2, r.added); assertEquals(1, r.updated)
        val sonarr = r.config.allServices().first { it.name == "Sonarr" }
        assertEquals("http://nas:8989", sonarr.url)
        assertEquals("secret", sonarr.apiKey)
        assertEquals(listOf(0), sonarr.tileStats)
        assertEquals("radarr", r.config.allServices().first { it.name == "Radarr" }.widget)
        assertEquals(listOf("Media", "Tools"), r.config.categories.map { it.name })
        assertEquals("resynchroniser ne change rien", 0, HomepageSync.merge(r.config, json).let { it.added + it.updated })
    }
}
