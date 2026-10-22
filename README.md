<p align="center">
  <img src="docs/logo.svg" alt="" width="96" height="96">
</p>

<h1 align="center">Rackowl</h1>

<p align="center">
  A homelab dashboard for Android, inspired by <a href="https://gethomepage.dev">Homepage</a>.<br>
  <a href="README.fr.md">Lire en français</a>
</p>

---

Rackowl keeps an eye on your self-hosted services from your phone: availability, live stats from each service's API, container control, Docker logs and home-screen widgets. Everything runs on the phone. There is no server to install and no account to create.

## Features

- **Dashboard.** One category per page: swipe between them or tap the page dots. Tiles come in 1 to 4 columns, with a status dot, and you choose which widget info each tile shows. Pull to refresh.
- **156 Homepage widgets.** Every service widget from gethomepage.dev is in the catalogue. 126 of them read live stats: Sonarr, Radarr, Jellyfin, Proxmox, OpenMediaVault, Pi-hole, qBittorrent, Portainer, TrueNAS, Synology, UniFi, Home Assistant, Immich, Komodo, and more. The rest are monitored by ping.
- **Service details.** Response time over 24 h, stats, lists (series, movies, Proxmox VMs and LXC with CPU and RAM…) and quick actions to start, restart or stop the container, with a confirmation.
- **Add from a container.** Pick a host, then a container from Docker or Komodo. Rackowl fills in the name, icon, integration and `http(s)://{host IP}:{port}` URL.
- **Alerts.** Offline and back-online events, warnings, ongoing incidents and 1-hour mute. Notifications go through Android, Gotify or ntfy.
- **Logs.** Live container logs through Dozzle, filtered by server, text and level, with pause and export.
- **Away from home.** Without home Wi-Fi or VPN, local services are paused instead of being reported as down. Public services stay monitored.
- **Always on.** An optional foreground service with a permanent notification, like Gotify, checks services at the chosen interval even when the app is closed.
- **Home-screen widgets.** Service status (4×2), server resources from Glances (2×2) and a single service (2×2).
- **Homepage import and export.** `services.yaml`, `widgets.yaml` and `settings.yaml`.
- **Docker labels.** Containers with `homepage.*` labels are added automatically.
- English and French, with dark, light or system theme.

## Install

1. Download the APK from the [latest release](https://github.com/MrDDream/Rackowl/releases/latest).
2. Open it on your phone and allow installs from that source.

Requires Android 8.0 or later.

## Getting started

1. **Settings › Sources**: add your Docker hosts (TCP API, ideally through docker-socket-proxy), Komodo and Glances. You can add several of each.
2. **Settings › Logs**: add your Dozzle instance.
3. **Dashboard › +**: add a service, either by hand or from a container. The integration is detected from the URL. Enter its API key, then **Test**.
4. **Settings › Alerts**: choose Android, Gotify or ntfy.

Some services need specific credentials:

| Service | What to enter |
| --- | --- |
| Proxmox VE | URL `https://IP:8006`, API token `user@pam!name=secret`, role **PVEAuditor** on `/` |
| Proxmox Backup Server | Token ID `user@pbs!name` and its secret |
| Komodo | API key and secret |
| OpenMediaVault | Web UI username and password |
| Self-signed HTTPS | **Settings › Network › Accept self-signed certificates** |

## Build from source

You need JDK 17 and the Android SDK (platform 34).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Run the tests with `./gradlew testDebugUnitTest`.

Built with Kotlin, Jetpack Compose, OkHttp, kotlinx.serialization, Glance (home-screen widgets) and WorkManager.

## Credits

- [Homepage](https://github.com/gethomepage/homepage), whose widgets served as the reference for each service's API.
- [selfh.st icons](https://selfh.st/icons/) for service icons.
- [Dozzle](https://dozzle.dev) for container logs.
- [IBM Plex Sans](https://github.com/IBM/plex) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) fonts, under the SIL Open Font License.
