package com.rackowl

import com.rackowl.data.Service
import com.rackowl.integrations.Integrations
import com.rackowl.net.Http
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Widget Sonarr contre un faux serveur : compteurs et liste des séries triée. */
class SonarrTest {
    @Test
    fun seriesList() = runBlocking {
        val routes = mapOf(
            "/api/v3/wanted/missing" to """{"totalRecords":3}""",
            "/api/v3/queue/status" to """{"totalCount":1}""",
            "/api/v3/series" to """[
                {"title":"The Expanse","statistics":{"episodeFileCount":62,"episodeCount":62}},
                {"title":"Andor","statistics":{"episodeFileCount":10,"episodeCount":24}}
            ]""",
        )
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("X-Api-Key") != "key") return MockResponse().setResponseCode(401)
                return MockResponse().setBody(routes[request.requestUrl!!.encodedPath] ?: "{}")
            }
        }
        server.start()
        try {
            val s = Service(name = "Sonarr", url = server.url("/").toString(), widget = "sonarr", apiKey = "key")
            val d = Integrations.byId("sonarr")!!.fetch(Http(false), s)
            assertEquals(listOf("3", "1", "2"), d.stats.map { it.value })
            assertEquals(listOf("Andor", "The Expanse"), d.details.map { it.title })
            assertEquals("10 / 24", d.details[0].subtitle)
        } finally {
            server.shutdown()
        }
    }
}
