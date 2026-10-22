<p align="center">
  <img src="docs/logo.svg" alt="Rackowl logo" width="104" height="104">
</p>

<h1 align="center">Rackowl</h1>

<p align="center">
  <b>Your homelab, in your pocket.</b><br>
  A night-owl dashboard for Android that keeps an eye on your self-hosted services.
</p>

<p align="center">
  <a href="https://github.com/MrDDream/Rackowl/releases/latest"><img src="https://img.shields.io/github/v/release/MrDDream/Rackowl?color=3DD6B5&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DD6B5" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Homepage%20widgets-156-3DD6B5" alt="156 Homepage widgets">
  <a href="https://mrddream.github.io/Rackowl/"><img src="https://img.shields.io/badge/website-online-3DD6B5" alt="Website"></a>
</p>

<p align="center">
  <a href="https://mrddream.github.io/Rackowl/">Website</a> ·
  <a href="https://github.com/MrDDream/Rackowl/releases/latest">Download</a> ·
  <a href="README.fr.md">Version française</a>
</p>

<p align="center">
  <img src="docs/assets/dashboard.svg" alt="Dashboard" width="200">
  <img src="docs/assets/detail.svg" alt="Service details" width="200">
  <img src="docs/assets/alerts.svg" alt="Alerts" width="200">
  <img src="docs/assets/logs.svg" alt="Logs" width="200">
</p>

---

## 🦉 Why Rackowl?

You love [Homepage](https://gethomepage.dev) on your desktop. On the phone, though, you want more than a web page: a notification when Jellyfin goes down, a quick look at the NAS from the couch, a container restart without opening a laptop.

Rackowl is that. It runs entirely on your phone: **no server to host, no account, no cloud.** Point it at your services and it does the rest.

## ✨ What it does

| | |
| --- | --- |
| 🧩 **156 Homepage widgets** | Sonarr, Jellyfin, Proxmox, OpenMediaVault, Pi-hole, Komodo, TrueNAS, UniFi… 126 of them with live stats. You pick what each tile shows. |
| 🐳 **Container control** | Add a service straight from a Docker or Komodo container, then start, restart or stop it, with a confirmation first. |
| 🔔 **Alerts that make sense** | Offline and back-online notifications through Android, Gotify or ntfy. Away from home without VPN? Local services pause instead of spamming you. |
| 📜 **Live logs** | Follow any container through Dozzle, filtered by server, text or level. |
| 📱 **Home-screen widgets** | Service status, server resources (Glances) and a single service. |
| 🔋 **Always on, if you want** | An optional foreground service, Gotify-style, for checks at your own pace even with the app closed. |
| 🗂️ **Homepage-friendly** | Import and export `services.yaml`, and `homepage.*` Docker labels are picked up automatically. |
| 🌍 **English & French** | Dark, light or system theme, plus four ways to swipe between categories. |

## 🚀 Get started

1. **Download** the APK from the [latest release](https://github.com/MrDDream/Rackowl/releases/latest) and open it on your phone (Android 8.0+).
2. **Settings › Sources**: add your Docker hosts, Komodo and Glances, as many as you like.
3. **Dashboard › +**: add a service by hand or pick a container. Rackowl detects the integration; enter its API key and hit **Test**.
4. **Settings › Alerts**: choose Android, Gotify or ntfy. Done.

## 🙋 FAQ

<details>
<summary><b>Proxmox shows 0 VMs and no CPU or RAM</b></summary>

Your API token probably has *Privilege Separation* enabled and no rights. In Proxmox, go to **Datacenter › Permissions › Add › API Token Permission**, path `/`, role **PVEAuditor** (read-only), with **Propagate** checked. Use `https://IP:8006` as the URL.
</details>

<details>
<summary><b>I get “unreachable” alerts when I’m away from home</b></summary>

That’s what **Settings › Network › Pause away from home** is for. When your home network (or VPN) isn’t reachable, local services are paused instead of reported as down.
</details>

<details>
<summary><b>My service uses a self-signed certificate</b></summary>

Turn on **Settings › Network › Accept self-signed certificates**. Only do it for services on your own network.
</details>

<details>
<summary><b>Which credentials does each service need?</b></summary>

The same as in Homepage: an API key for most services, a username and password for some (OpenMediaVault, qBittorrent…), and a key plus secret for Komodo, Proxmox Backup Server and OPNsense. The service form asks for the right fields once you pick the integration.
</details>

## 🛠️ Build from source

You need JDK 17 and the Android SDK (platform 34).

```bash
./gradlew assembleDebug       # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest   # unit tests
```

<details>
<summary><b>Signed release build</b></summary>

Create a `keystore.properties` file at the project root (it is ignored by git):

```properties
storeFile=/path/to/rackowl-release.jks
storePassword=…
keyAlias=rackowl
keyPassword=…
```

Then run `./gradlew assembleRelease`, which writes `app/build/outputs/apk/release/Rackowl-v<version>-release.apk`. On CI, set the `RACKOWL_STORE_FILE`, `RACKOWL_STORE_PASSWORD`, `RACKOWL_KEY_ALIAS` and `RACKOWL_KEY_PASSWORD` environment variables instead.
</details>

Built with Kotlin, Jetpack Compose, OkHttp, kotlinx.serialization, Glance and WorkManager.

## 💚 Credits

- [Homepage](https://github.com/gethomepage/homepage): its widgets are the reference for every service API.
- [selfh.st icons](https://selfh.st/icons/) for the service icons, and [Dozzle](https://dozzle.dev) for container logs.
- [IBM Plex Sans](https://github.com/IBM/plex) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), under the SIL Open Font License.
