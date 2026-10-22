package com.rackowl

import com.rackowl.data.AppConfig
import com.rackowl.data.Category
import com.rackowl.data.HomepageYaml
import com.rackowl.data.Sample
import com.rackowl.data.allServices
import com.rackowl.monitor.Monitor
import com.rackowl.net.Docker
import com.rackowl.net.Dozzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val servicesYaml = """
        - Médias:
            - Sonarr:
                href: http://192.168.1.20:8989
                description: Séries
                icon: sonarr.png
                widget:
                  type: sonarr
                  url: http://192.168.1.20:8989
                  key: abc123
            - Jellyfin:
                href: https://jellyfin.maison.lan
                icon: sh-jellyfin.png
        - Infra:
            - Pi-hole:
                href: http://pi.hole/admin
                widget:
                  type: pihole
                  key: secret
    """.trimIndent()

    @Test
    fun importServicesYaml() {
        val r = HomepageYaml.import(AppConfig(), listOf("services.yaml" to servicesYaml))
        assertEquals(3, r.services)
        assertEquals(listOf("Médias", "Infra"), r.config.categories.map { it.name })
        val sonarr = r.config.allServices().first { it.name == "Sonarr" }
        assertEquals("sonarr", sonarr.widget)
        assertEquals("abc123", sonarr.apiKey)
        assertEquals("sonarr", sonarr.icon)
        assertEquals("pihole", r.config.allServices().first { it.name == "Pi-hole" }.widget)
        assertNull(r.config.allServices().first { it.name == "Jellyfin" }.widget)
    }

    @Test
    fun importIsIdempotentAndExportRoundTrips() {
        val once = HomepageYaml.import(AppConfig(), listOf("services.yaml" to servicesYaml)).config
        val twice = HomepageYaml.import(once, listOf("services.yaml" to servicesYaml))
        assertEquals(0, twice.services)
        val exported = HomepageYaml.exportServices(once)
        val back = HomepageYaml.import(AppConfig(), listOf("services.yaml" to exported)).config
        assertEquals(once.allServices().map { it.name to it.widget }, back.allServices().map { it.name to it.widget })
    }

    @Test
    fun importLayoutAndGlances() {
        val base = HomepageYaml.import(AppConfig(), listOf("services.yaml" to servicesYaml)).config
        val r = HomepageYaml.import(
            base,
            listOf(
                "settings.yaml" to "layout:\n  Infra:\n    style: row\n    columns: 3\n",
                "widgets.yaml" to "- glances:\n    url: http://192.168.1.20:61208\n",
            ),
        )
        assertEquals(3, r.config.categories.first { it.name == "Infra" }.cols)
        assertEquals(listOf("http://192.168.1.20:61208"), r.config.settings.sourcesOf(com.rackowl.data.SourceType.GLANCES).map { it.url })
    }

    @Test
    fun historyBucketsKeepOutages() {
        val t0 = 1_000_000_000_000L - 1_000_000_000_000L % 300_000L
        var h = Monitor.appendSample(emptyList(), Sample(t0, 40), t0)
        h = Monitor.appendSample(h, Sample(t0 + 30_000, null), t0 + 30_000)
        h = Monitor.appendSample(h, Sample(t0 + 60_000, 50), t0 + 60_000)
        assertEquals(1, h.size)
        assertNull("une panne dans la tranche l'emporte", h[0].latency)
        h = Monitor.appendSample(h, Sample(t0 + 300_000, 30), t0 + 300_000)
        assertEquals(2, h.size)
        h = Monitor.appendSample(h, Sample(t0 + 25 * 3600_000L, 30), t0 + 25 * 3600_000L)
        assertEquals("plus de 24 h d'historique est purgé", 1, h.size)
    }

    @Test
    fun dockerLabelsMerge() {
        val containers = listOf(
            Docker.Container("1", "sonarr", "running", mapOf(
                "homepage.group" to "Médias", "homepage.name" to "Sonarr", "homepage.href" to "http://sonarr.lan",
                "homepage.widget.type" to "sonarr", "homepage.widget.key" to "k",
            )),
            Docker.Container("2", "postgres", "running", emptyMap()),
        )
        val (cats, added) = Docker.merge(listOf(Category(name = "Médias")), containers)
        assertEquals(1, added)
        assertEquals("sonarr", cats.single().services.single().widget)
        val (again, addedAgain) = Docker.merge(cats, containers)
        assertEquals(0, addedAgain)
        assertEquals(1, again.single().services.size)
    }

    @Test
    fun legacySettingsMigrateToSources() {
        val old = com.rackowl.data.Settings(dockerUrl = "tcp://10.0.0.2:2375", komodoUrl = "http://10.0.0.2:9120", komodoKey = "k", komodoSecret = "s", glancesUrl = "http://10.0.0.2:61208")
        val m = old.migrated()
        assertEquals(listOf(com.rackowl.data.SourceType.DOCKER, com.rackowl.data.SourceType.KOMODO, com.rackowl.data.SourceType.GLANCES), m.sources.map { it.type })
        assertEquals("k", m.sources[1].key)
        assertEquals("", m.dockerUrl)
        assertEquals(m, m.migrated())
    }

    @Test
    fun containerUrlUsesHostIpAndPublishedPort() {
        val host = com.rackowl.net.PickHost("docker:x", "nas", "x", "nas", com.rackowl.data.SourceType.DOCKER, null, emptyList())
        val qbit = com.rackowl.net.PickableContainer("QbitTorrent", "lscr.io/linuxserver/qbittorrent:5.2", true, listOf(6881 to 6881, 8080 to 8080), emptyMap())
        val s = com.rackowl.net.Containers.toService(qbit, host, "192.168.1.20", "http")
        assertEquals("http://192.168.1.20:8080", s.url)
        assertEquals("qbittorrent", s.widget)
        assertEquals("x", s.containerSource)
        val tls = qbit.copy(ports = listOf(9443 to 443))
        assertEquals("https", com.rackowl.net.Containers.defaultScheme(tls))
        assertEquals("https://10.0.0.9:9443", com.rackowl.net.Containers.urlFor(tls, "10.0.0.9", "https"))
    }

    @Test
    fun privateIpDetection() {
        val c = com.rackowl.net.Containers
        assertTrue(c.isPrivateIp("192.168.1.20") && c.isPrivateIp("10.0.0.1") && c.isPrivateIp("172.20.0.3") && c.isPrivateIp("100.101.2.3"))
        assertTrue(!c.isPrivateIp("203.0.113.10") && !c.isPrivateIp("198.51.100.20") && !c.isPrivateIp("nas.lan") && !c.isPrivateIp(null))
    }

    @Test
    fun localUrlsAndProbeTarget() {
        val lan = com.rackowl.net.Lan
        assertTrue(lan.isLocalUrl("http://192.168.1.20:8080") && lan.isLocalUrl("https://nas.lan") && lan.isLocalUrl("http://jellyfin:8096") && lan.isLocalUrl("tcp://10.0.0.2:2375"))
        assertTrue(!lan.isLocalUrl("https://cloud.example.com") && !lan.isLocalUrl("http://198.51.100.20:3000"))
        val settings = com.rackowl.data.Settings(dozzleUrl = "http://192.168.1.10:8080")
        assertEquals("192.168.1.10:8080", lan.target(settings, emptyList()).toString())
        assertEquals("10.0.0.1:443", lan.target(settings.copy(lanProbe = "10.0.0.1:443"), emptyList()).toString())
        assertEquals(null, lan.target(com.rackowl.data.Settings(), listOf(com.rackowl.data.Service(name = "x", url = "https://x.example.com"))))
    }

    /** Widgets de gethomepage.dev (hors calendrier, iframe, bourse, Kubernetes…) : tous doivent exister dans Rackowl. */
    @Test
    fun coversHomepageWidgets() {
        val homepage = listOf("adguard", "apcups", "arcane", "argocd", "atsumeru", "audiobookshelf", "authentik", "autobrr", "azuredevops", "backrest", "bazarr", "beszel", "booklore", "bookorbit", "caddy", "calibreweb", "changedetectionio", "channelsdvrserver", "checkmk", "cloudflared", "crowdsec", "deluge", "develancacheui", "diskstation", "dispatcharr", "dockhand", "downloadstation", "duplicati", "emby", "esphome", "evcc", "filebrowser", "fileflows", "firefly", "flood", "freshrss", "frigate", "fritzbox", "gatus", "ghostfolio", "gitea", "gitlab", "glances", "gluetun", "gotify", "grafana", "hdhomerun", "headscale", "healthchecks", "homeassistant", "homebox", "homebridge", "immich", "jackett", "jdownloader", "jellyfin", "jellystat", "karakeep", "kavita", "komga", "komodo", "kopia", "lidarr", "linkwarden", "lubelogger", "mailcow", "maintainerr", "mastodon", "mealie", "medusa", "mikrotik", "miniflux", "moonraker", "mylar", "myspeed", "navidrome", "netalertx", "netbird", "netdata", "nextcloud", "nextdns", "npm", "ntfy", "nzbget", "octoprint", "omada", "ombi", "opendtu", "openmediavault", "openwrt", "opnsense", "overseerr", "pangolin", "paperlessngx", "peanut", "pfsense", "photoprism", "pihole", "plantit", "plex", "portainer", "prometheus", "prowlarr", "proxmox", "proxmoxbackupserver", "proxmoxvm", "pterodactyl", "pulse", "pyload", "qbittorrent", "qnap", "qui", "radarr", "readarr", "romm", "rutorrent", "sabnzbd", "scrutiny", "seerr", "slskd", "sonarr", "sparkyfitness", "speedtest", "spoolman", "sportarr", "stash", "strelaysrv", "suwayomi", "swagdashboard", "syncthing", "tailscale", "tandoor", "tautulli", "tdarr", "technitium", "tracearr", "traefik", "transmission", "trilium", "truenas", "tubearchivist", "unifi", "unifi_drive", "unmanic", "unraid", "uptimekuma", "uptimerobot", "urbackup", "vikunja", "wallos", "watchtower", "wgeasy", "whatsupdocker", "xteve", "yourspotify", "zabbix")
        val catalog = com.rackowl.integrations.Integrations.catalog
        assertEquals("identifiants en double", catalog.size, catalog.map { it.id }.toSet().size)
        val missing = homepage.filter { com.rackowl.integrations.Integrations.fromHomepageType(it) == null }
        assertTrue("widgets Homepage absents : $missing", missing.isEmpty())
    }

    @Test
    fun dozzleLevels() {
        assertEquals(Dozzle.LogLevel.ERR, Dozzle.levelOf("unknown", "PHP Fatal error: Allowed memory size exhausted"))
        assertEquals(Dozzle.LogLevel.WRN, Dozzle.levelOf("warn", "x"))
        assertEquals(Dozzle.LogLevel.INF, Dozzle.levelOf("", "GET /status.php 200"))
        assertTrue(Dozzle.levelOf("error", "") == Dozzle.LogLevel.ERR)
    }
}
