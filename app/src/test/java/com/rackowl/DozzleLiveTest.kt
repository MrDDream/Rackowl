package com.rackowl

import com.rackowl.net.Dozzle
import com.rackowl.net.Http
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Test contre une vraie instance Dozzle : `DOZZLE_URL=http://… ./gradlew testDebugUnitTest`. */
class DozzleLiveTest {
    private val url = System.getenv("DOZZLE_URL")

    @Test
    fun liveDozzle() = runBlocking {
        assumeTrue("DOZZLE_URL non défini", !url.isNullOrBlank())
        val http = Http(insecure = false)
        val dozzle = Dozzle(http, url!!, "", "")
        val hosts = dozzle.hosts()
        println("hôtes : " + hosts.values.joinToString { "${it.name}=${it.address}" })
        assertTrue(hosts.isNotEmpty())

        val containers = dozzle.containers()
        val target = containers.first { it.state == "running" }
        println("conteneurs : ${containers.size}, logs de ${target.name}")
        val lines = withTimeout(10_000) { dozzle.logs(target).take(5).toList() }
        lines.forEach { println("  ${it.level} ${it.message.take(100)}") }
        assertTrue(lines.size == 5)

    }
}
