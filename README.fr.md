<p align="center">
  <img src="docs/logo.svg" alt="" width="96" height="96">
</p>

<h1 align="center">Rackowl</h1>

<p align="center">
  Un tableau de bord homelab pour Android, inspiré de <a href="https://gethomepage.dev">Homepage</a>.<br>
  <a href="README.md">Read in English</a>
</p>

---

Rackowl surveille tes services auto-hébergés depuis ton téléphone : disponibilité, statistiques en direct depuis l’API de chaque service, pilotage des conteneurs, logs Docker et widgets d’écran d’accueil. Tout tourne sur le téléphone : aucun serveur à installer, aucun compte à créer.

## Fonctionnalités

- **Tableau de bord.** Une catégorie par page : on glisse de l’une à l’autre ou on touche les points de page. Les tuiles s’affichent sur 1 à 4 colonnes, avec un point d’état, et tu choisis les infos du widget que chaque tuile montre. Tirer pour actualiser.
- **156 widgets Homepage.** Tous les widgets de service de gethomepage.dev sont au catalogue. 126 lisent des statistiques en direct : Sonarr, Radarr, Jellyfin, Proxmox, OpenMediaVault, Pi-hole, qBittorrent, Portainer, TrueNAS, Synology, UniFi, Home Assistant, Immich, Komodo, etc. Les autres sont surveillés par ping.
- **Détail d’un service.** Temps de réponse sur 24 h, statistiques, listes (séries, films, VM et LXC Proxmox avec CPU et RAM…) et actions rapides pour démarrer, redémarrer ou arrêter le conteneur, avec confirmation.
- **Ajout depuis un conteneur.** On choisit un hôte, puis un conteneur Docker ou Komodo. Rackowl remplit le nom, l’icône, l’intégration et l’URL `http(s)://{IP de l’hôte}:{port}`.
- **Alertes.** Pannes, retours en ligne, avertissements, incidents en cours et sourdine d’une heure. Les notifications passent par Android, Gotify ou ntfy.
- **Logs.** Logs des conteneurs en direct via Dozzle, filtrés par serveur, par texte et par niveau, avec pause et export.
- **Hors de chez toi.** Sans Wi-Fi maison ni VPN, les services locaux sont mis en pause au lieu d’être signalés en panne. Les services publics restent surveillés.
- **Toujours active.** Un service de premier plan optionnel, avec une notification permanente comme Gotify, vérifie les services à l’intervalle choisi, même application fermée.
- **Widgets d’écran d’accueil.** État des services (4×2), ressources serveur via Glances (2×2) et un service au choix (2×2).
- **Import et export Homepage.** `services.yaml`, `widgets.yaml` et `settings.yaml`.
- **Labels Docker.** Les conteneurs portant des labels `homepage.*` sont ajoutés automatiquement.
- Anglais et français, thème sombre, clair ou système.

## Installation

1. Télécharge l’APK de la [dernière version](https://github.com/MrDDream/Rackowl/releases/latest).
2. Ouvre-le sur ton téléphone et autorise l’installation depuis cette source.

Android 8.0 ou plus récent.

## Prise en main

1. **Réglages › Sources** : ajoute tes hôtes Docker (API TCP, idéalement via docker-socket-proxy), Komodo et Glances. Tu peux en ajouter plusieurs de chaque.
2. **Réglages › Logs** : ajoute ton instance Dozzle.
3. **Tableau de bord › +** : ajoute un service, à la main ou depuis un conteneur. L’intégration est détectée à partir de l’URL. Saisis sa clé API, puis **Tester**.
4. **Réglages › Alertes** : choisis Android, Gotify ou ntfy.

Certains services demandent des identifiants particuliers :

| Service | À renseigner |
| --- | --- |
| Proxmox VE | URL `https://IP:8006`, jeton API `user@pam!nom=secret`, rôle **PVEAuditor** sur `/` |
| Proxmox Backup Server | ID du jeton `user@pbs!nom` et son secret |
| Komodo | Clé et secret API |
| OpenMediaVault | Utilisateur et mot de passe de l’interface web |
| HTTPS auto-signé | **Réglages › Réseau › Accepter les certificats auto-signés** |

## Compiler

Il faut le JDK 17 et le SDK Android (plateforme 34).

```bash
./gradlew assembleDebug
```

L’APK est créé dans `app/build/outputs/apk/debug/`. Les tests se lancent avec `./gradlew testDebugUnitTest`.

Réalisé en Kotlin, avec Jetpack Compose, OkHttp, kotlinx.serialization, Glance (widgets d’écran d’accueil) et WorkManager.

## Remerciements

- [Homepage](https://github.com/gethomepage/homepage), dont les widgets ont servi de référence pour l’API de chaque service.
- [selfh.st icons](https://selfh.st/icons/) pour les icônes des services.
- [Dozzle](https://dozzle.dev) pour les logs des conteneurs.
- Polices [IBM Plex Sans](https://github.com/IBM/plex) et [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), sous licence SIL Open Font License.
