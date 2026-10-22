package com.rackowl.ui.screens

import android.annotation.SuppressLint
import com.rackowl.ui.t
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rackowl.BuildConfig
import com.rackowl.data.HomepageYaml
import com.rackowl.data.allServices
import com.rackowl.data.Notifier
import com.rackowl.data.Settings
import com.rackowl.data.SourceConfig
import com.rackowl.data.SourceType
import com.rackowl.data.ThemeMode
import com.rackowl.data.fmtInterval
import com.rackowl.integrations.GlancesClient
import com.rackowl.monitor.Monitor
import com.rackowl.net.Docker
import com.rackowl.net.Dozzle
import com.rackowl.net.Komodo
import com.rackowl.ui.LocalApp
import com.rackowl.ui.components.Divider
import com.rackowl.ui.components.FieldLabel
import com.rackowl.ui.components.HCard
import com.rackowl.ui.components.HField
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.HSwitch
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.PrimaryButton
import com.rackowl.ui.components.SecondaryButton
import com.rackowl.ui.components.SectionTitle
import com.rackowl.ui.components.Segmented
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.components.SettingRow
import com.rackowl.ui.edit
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono
import com.rackowl.ui.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Réglages à instance unique (logs et canaux d'alerte) ; les sources multiples passent par SourceConfig. */
private enum class Source { DOZZLE, GOTIFY, NTFY }

/** État d'une source : null = non testée, sinon (ok, texte). */
private typealias SourceState = Pair<Boolean, String>?

@Composable
fun SettingsScreen(@Suppress("UNUSED_PARAMETER") nav: NavHostController) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val c = Home.colors
    val scope = rememberCoroutineScope()
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val s = config.settings
    var dialog by remember { mutableStateOf<Source?>(null) }
    var chooser by remember { mutableStateOf(false) }
    var lanDialog by remember { mutableStateOf(false) }
    /** Source en cours d'édition, et si elle est nouvelle. */
    var editing by remember { mutableStateOf<Pair<SourceConfig, Boolean>?>(null) }
    /** États de test, par id de source (« dozzle » pour Dozzle). */
    var states by remember { mutableStateOf(mapOf<String, SourceState>()) }
    var reload by remember { mutableIntStateOf(0) }

    // Teste les sources configurées à l'ouverture et après chaque modification.
    LaunchedEffect(reload, s.sources, s.dozzleUrl) {
        val http = app.http()
        states = coroutineScope {
            val jobs = s.sources.map { src -> async { src.id to testSource(http, src, force = false) } } +
                listOfNotNull(s.dozzleUrl.takeIf { it.isNotBlank() }?.let {
                    async {
                        "dozzle" to runCatching { Dozzle(http, s.dozzleUrl, s.dozzleUser, s.dozzlePassword).containers() }
                            .fold({ true to t("${it.map { c -> c.host }.distinct().size} host(s) · ${it.size} containers", "${it.map { c -> c.host }.distinct().size} hôte(s) · ${it.size} conteneurs") }, { false to Monitor.describe(it) })
                    }
                })
            jobs.awaitAll().toMap()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val files = uris.map { uri ->
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "fichier"
                        name to (ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "")
                    }
                    var res: HomepageYaml.ImportResult? = null
                    app.store.config.update { cfg -> HomepageYaml.import(cfg, files).also { res = it }.config }
                    res!!
                }
            }
            result.onSuccess {
                ctx.toast(if (it.files.isEmpty()) t("No Homepage file recognised", "Aucun fichier Homepage reconnu") else t("${it.services} service(s) imported from ${it.files.joinToString()}", "${it.services} service(s) importé(s) depuis ${it.files.joinToString()}"))
                app.monitor.tick(force = true)
            }.onFailure { ctx.toast(t("Import failed: ${it.message}", "Import impossible : ${it.message}")) }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/yaml")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(HomepageYaml.exportServices(app.store.config.value)) }
        }.onSuccess { ctx.toast(t("services.yaml exported", "services.yaml exporté")) }.onFailure { ctx.toast(t("Export failed", "Export impossible")) }
    }

    val pm = ctx.getSystemService(PowerManager::class.java)
    var battIgnored by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { battIgnored = pm.isIgnoringBatteryOptimizations(ctx.packageName) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(t("Settings", "Réglages"), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Section("Sources") {
                s.sources.forEach { src ->
                    SourceRow(iconOf(src.type), src.name, src.url, states[src.id], true) { editing = src to false }
                    Divider()
                }
                AddRow(t("Add a source", "Ajouter une source")) { chooser = true }
            }

            Section("Logs") {
                if (s.dozzleUrl.isNotBlank()) SourceRow("dozzle", "Dozzle", s.dozzleUrl, states["dozzle"], true) { dialog = Source.DOZZLE }
                else AddRow(t("Add a Dozzle instance", "Ajouter une instance Dozzle")) { dialog = Source.DOZZLE }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(t("Refresh", "Actualisation"))
                Segmented(
                    listOf(15, 30, 60, 300).map { it to fmtInterval(it) }, s.refreshSec,
                    { v -> app.edit { updateSettings { it.copy(refreshSec = v) } } },
                    bg = c.surface, height = 40.dp,
                )
                HCard(Modifier.fillMaxWidth()) {
                    SettingRow(
                        t("Ping services", "Pinger les services"),
                        t("Availability checks and offline alerts", "Vérification de disponibilité et alertes hors ligne"),
                    ) {
                        HSwitch(s.pingEnabled, { v -> app.edit { updateSettings { it.copy(pingEnabled = v) } } }, t("Ping services", "Pinger les services"))
                    }
                    Divider()
                    SettingRow(
                        t("Always on", "Toujours active"),
                        t("Continuous checks with a permanent notification, like Gotify", "Vérifications en continu avec une notification permanente, comme Gotify"),
                    ) {
                        HSwitch(s.keepAlive, { v ->
                            scope.launch {
                                app.store.updateSettings { it.copy(keepAlive = v) }
                                com.rackowl.monitor.MonitorService.sync(ctx)
                            }
                        }, t("Always on", "Toujours active"))
                    }
                    Divider()
                    SettingRow(t("Ignore battery optimisation", "Ignorer l'optimisation de batterie")) {
                        HSwitch(battIgnored, { requestBatteryExemption(ctx, battIgnored) }, t("Ignore battery optimisation", "Ignorer l'optimisation de batterie"))
                    }
                }
            }

            Section(t("Offline alerts", "Alertes hors ligne")) {
                NotifierRow(Notifier.ANDROID, s, "Android", t("Local phone notification", "Notification locale du téléphone"), null, null) { app.edit { updateSettings { it.copy(notifier = Notifier.ANDROID) } } }
                Divider()
                NotifierRow(Notifier.GOTIFY, s, "Gotify", s.gotifyUrl.ifBlank { t("Not set up", "Non configuré") }, "gotify", { dialog = Source.GOTIFY }) {
                    app.edit { updateSettings { it.copy(notifier = Notifier.GOTIFY) } }
                }
                Divider()
                NotifierRow(Notifier.NTFY, s, "ntfy", if (s.ntfyTopic.isBlank()) t("Not set up", "Non configuré") else "${s.ntfyUrl.substringAfter("://")}/${s.ntfyTopic}", "ntfy", { dialog = Source.NTFY }) {
                    app.edit { updateSettings { it.copy(notifier = Notifier.NTFY) } }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(t("Appearance", "Apparence"))
                Segmented(
                    listOf(ThemeMode.DARK to t("Dark", "Sombre"), ThemeMode.LIGHT to t("Light", "Clair"), ThemeMode.SYSTEM to t("System", "Système")), s.theme,
                    { v -> app.edit { updateSettings { it.copy(theme = v) } } }, bg = c.surface, height = 40.dp,
                )
                SectionTitle(t("Language", "Langue"), Modifier.padding(top = 10.dp))
                Segmented(
                    listOf(com.rackowl.data.Language.EN to "English", com.rackowl.data.Language.FR to "Français"), s.language,
                    { v -> com.rackowl.ui.I18n.language = v; app.edit { updateSettings { it.copy(language = v) } } }, bg = c.surface, height = 40.dp,
                )
            }

            Section(t("Network", "Réseau")) {
                SettingRow(
                    t("Pause away from home", "Pause hors de chez toi"),
                    t("No Wi-Fi at home and no VPN: local services aren’t checked", "Ni Wi-Fi maison ni VPN : les services locaux ne sont pas vérifiés"),
                ) {
                    IconBtn(HIcons.Settings, t("Reference host", "Hôte de référence"), { lanDialog = true }, iconSize = 20.dp, enabled = s.pauseOffLan)
                    HSwitch(s.pauseOffLan, { v -> app.edit { updateSettings { it.copy(pauseOffLan = v) } } }, t("Pause away from home", "Pause hors de chez toi"))
                }
                Divider()
                SettingRow(t("Accept self-signed certificates", "Accepter les certificats auto-signés"), t("Only turn on for your home network", "À activer seulement sur ton réseau local")) {
                    HSwitch(s.allowInsecureTls, { v -> app.edit { updateSettings { it.copy(allowInsecureTls = v) } } }, t("Accept self-signed certificates", "Accepter les certificats auto-signés"))
                }
            }

            Section(t("Import & backup", "Import & Sauvegarde")) {
                ActionRow(HIcons.Download, t("Import a Homepage config", "Importer une config Homepage"), "services.yaml, widgets.yaml, settings.yaml") {
                    importLauncher.launch(arrayOf("*/*"))
                }
                Divider()
                ActionRow(HIcons.Upload, t("Export configuration", "Exporter la configuration"), t("Homepage-compatible services.yaml file", "Fichier services.yaml compatible Homepage")) {
                    exportLauncher.launch("services.yaml")
                }
            }

            Text("Rackowl ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    dialog?.let { src ->
        SourceDialog(src, s, onDismiss = { dialog = null }, onSaved = { reload++ })
    }
    if (lanDialog) LanProbeDialog(s, config.allServices()) { lanDialog = false }
    if (chooser) SourceChooser(onDismiss = { chooser = false }) { type ->
        chooser = false
        val n = s.sourcesOf(type).size + 1
        val name = when (type) {
            SourceType.DOCKER -> "Docker" + if (n > 1) " $n" else ""
            SourceType.KOMODO -> "Komodo" + if (n > 1) " $n" else ""
            SourceType.GLANCES -> if (n == 1) t("Main server", "Serveur principal") else t("Server $n", "Serveur $n")
        }
        val url = when (type) {
            SourceType.DOCKER -> "tcp://192.168.1.20:2375"
            SourceType.KOMODO -> "http://192.168.1.20:9120"
            SourceType.GLANCES -> "http://192.168.1.20:61208"
        }
        editing = SourceConfig(type = type, name = name, url = url) to true
    }
    editing?.let { (src, isNew) ->
        SourceConfigDialog(src, isNew, onDismiss = { editing = null }, onSaved = { reload++ })
    }
}

/** Hôte de référence du réseau local : détecté automatiquement ou saisi, avec test de joignabilité. */
/** Pop-up de l'hôte de référence : détecté automatiquement ou saisi, avec test de joignabilité. */
@Composable
private fun LanProbeDialog(s: Settings, services: List<com.rackowl.data.Service>, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val c = Home.colors
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(s.lanProbe) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    val auto = com.rackowl.net.Lan.target(s.copy(lanProbe = ""), services)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(t("Reference host", "Hôte de référence"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    t(
                        "A machine on your home network. If it doesn’t answer, you’re away from home without VPN: local services are paused, with no alerts.",
                        "Une machine de ton réseau maison. Si elle ne répond pas, c’est que tu es dehors sans VPN : les services locaux sont mis en pause, sans alerte.",
                    ),
                    fontSize = 13.sp, color = c.textSecondary,
                )
                FieldLabel(t("Address (ip:port)", "Adresse (ip:port)"))
                HField(
                    value, { value = it; result = null }, mono = true, bg = c.field,
                    placeholder = auto?.let { "$it (auto)" } ?: "192.168.1.1:80",
                )
                Text(
                    when {
                        result == true -> t("Reachable: you’re on your home network", "Joignable : tu es sur ton réseau maison")
                        result == false -> t("Unreachable: away from home, or wrong address", "Injoignable : hors de chez toi, ou adresse incorrecte")
                        value.isBlank() && auto == null -> t("No local host found: pausing won’t apply", "Aucun hôte local trouvé : la pause ne s’appliquera pas")
                        value.isBlank() -> t("Leave empty to use $auto, detected from your sources", "Laisse vide pour utiliser $auto, détecté depuis tes sources")
                        else -> ""
                    },
                    fontSize = 12.sp, color = when (result) { true -> c.up; false -> c.warn; null -> c.muted },
                )
                SecondaryButton(if (busy) t("Testing…", "Test…") else t("Test", "Tester"), {
                    val target = com.rackowl.net.Lan.target(s.copy(lanProbe = value), services)
                    if (target != null && !busy) {
                        busy = true
                        scope.launch { result = com.rackowl.net.Lan.reachable(target, fresh = true); busy = false }
                    }
                }, Modifier.fillMaxWidth(), height = 44.dp, radius = 12.dp, bg = c.field, fontSize = 14)
            }
        },
        confirmButton = {
            PrimaryButton(t("Save", "Enregistrer"), {
                app.edit { updateSettings { it.copy(lanProbe = value.trim()) } }
                onDismiss()
            })
        },
        dismissButton = {
            Text(t("Cancel", "Annuler"), color = c.textSecondary, fontSize = 14.sp,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss).padding(12.dp))
        },
    )
}

private fun iconOf(type: SourceType) = when (type) {
    SourceType.DOCKER -> "docker"
    SourceType.KOMODO -> "komodo"
    SourceType.GLANCES -> "glances"
}

/** Choix du type de source à ajouter ; chaque type peut être ajouté plusieurs fois. */
@Composable
private fun SourceChooser(onDismiss: () -> Unit, onPick: (SourceType) -> Unit) {
    val c = Home.colors
    val options = listOf(
        Triple(SourceType.DOCKER, "Docker", t("A host’s Docker API: containers, homepage.* labels, restart", "API Docker d’un hôte : conteneurs, labels homepage.*, redémarrage")),
        Triple(SourceType.KOMODO, "Komodo", t("Containers from every server in a Komodo", "Conteneurs de tous les serveurs d’un Komodo")),
        Triple(SourceType.GLANCES, "Glances", t("A server’s CPU, RAM and disk", "CPU, RAM et disque d’un serveur")),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(t("Add a source", "Ajouter une source"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column {
                options.forEachIndexed { i, (type, title, hint) ->
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { onPick(type) }.padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ServiceIcon(iconOf(type), title, 28.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, fontSize = 15.sp, color = c.text)
                            Text(hint, fontSize = 12.sp, color = c.muted)
                        }
                        Icon(HIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.placeholder)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(t("Cancel", "Annuler"), color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss).padding(12.dp))
        },
    )
}

/**
 * Identifiants Komodo refusés pendant cette session : Komodo bloque après plusieurs échecs,
 * on ne les renvoie donc plus automatiquement (seul « Tester » réessaie).
 */
private val komodoRejected = mutableMapOf<String, String>()

/** Teste une source. [force] = false : ne renvoie pas des identifiants Komodo déjà refusés. */
private suspend fun testSource(http: com.rackowl.net.Http, src: SourceConfig, force: Boolean): Pair<Boolean, String> = when (src.type) {
    SourceType.DOCKER -> runCatching { Docker.containers(http, src.url) }.fold(
        { list ->
            val labelled = list.count { c -> c.labels.keys.any { k -> k.startsWith("homepage.") } }
            true to t("${list.size} containers · $labelled with homepage.* labels", "${list.size} conteneurs · $labelled avec labels homepage.*")
        },
        { false to Monitor.describe(it) },
    )
    SourceType.GLANCES -> runCatching { GlancesClient.read(http, src.url, src.key, src.secret) }.fold(
        { true to t("CPU ${it.cpu?.toInt()}% · RAM ${it.mem?.toInt()}% · disk ${it.disk?.toInt()}%", "CPU ${it.cpu?.toInt()} % · RAM ${it.mem?.toInt()} % · disque ${it.disk?.toInt()} %") },
        { false to Monitor.describe(it) },
    )
    SourceType.KOMODO -> {
        val id = listOf(src.url, src.key, src.secret).joinToString("|")
        val rejected = komodoRejected[id]
        if (!force && rejected != null) false to t("$rejected — fix the key, then “Test”", "$rejected — corrige la clé puis « Tester »")
        else runCatching { Komodo.summary(http, src.url, src.key, src.secret) }.fold(
            { komodoRejected.remove(id); true to it },
            { e ->
                val msg = Monitor.describe(e)
                if ((e as? com.rackowl.net.HttpException)?.code == 401) komodoRejected[id] = msg
                false to msg
            },
        )
    }
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(ctx: android.content.Context, alreadyIgnored: Boolean) {
    val intent = if (alreadyIgnored) Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    else Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    runCatching { ctx.startActivity(intent) }.onFailure {
        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        HCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SourceRow(icon: String, title: String, detail: String?, state: SourceState, configured: Boolean, onClick: () -> Unit) {
    val c = Home.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServiceIcon(icon, title, 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp)
            if (state != null && !state.first) {
                Text(state.second, style = mono(11, c.down), maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else Text(
                state?.second?.let { listOfNotNull(detail?.substringAfter("://"), it).joinToString(" · ") } ?: detail?.substringAfter("://") ?: t("Not set up", "Non configuré"),
                style = mono(11, c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            when { !configured -> t("Set up", "Configurer"); state == null -> t("Testing…", "Test…"); state.first -> t("Connected", "Connecté"); else -> t("Error", "Erreur") },
            fontSize = 12.sp,
            color = when { !configured -> c.accent; state == null -> c.muted; state.first -> c.up; else -> c.down },
        )
    }
}

@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    val c = Home.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(HIcons.Plus, null, Modifier.size(20.dp), tint = c.textSecondary)
        Text(text, fontSize = 14.sp, color = c.textSecondary)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    val c = Home.colors
    SettingRow(title, hint, onClick = onClick, leading = { Icon(icon, null, Modifier.size(20.dp), tint = c.textSecondary) }) {
        Icon(HIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.placeholder)
    }
}

@Composable
private fun NotifierRow(
    kind: Notifier, s: Settings, label: String, hint: String, icon: String?, onConfigure: (() -> Unit)?, onSelect: () -> Unit,
) {
    val c = Home.colors
    val on = s.notifier == kind
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (on) c.accent else c.handle, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) c.accent else Color.Transparent))
        }
        if (icon != null) ServiceIcon(icon, label, 22.dp) else Icon(HIcons.Phone, null, Modifier.size(22.dp), tint = c.textSecondary)
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 14.sp)
            Text(hint, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onConfigure != null) IconBtn(HIcons.Settings, t("Set up $label", "Configurer $label"), onConfigure, iconSize = 20.dp)
    }
}

/** Configuration d'une source Docker, Komodo ou Glances, avec test et (Docker) synchronisation des labels. */
@Composable
private fun SourceConfigDialog(initial: SourceConfig, isNew: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val c = Home.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var key by remember { mutableStateOf(initial.key) }
    var secret by remember { mutableStateOf(initial.secret) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun current() = initial.copy(name = name.trim().ifBlank { initial.name }, url = url.trim().trimEnd('/'), key = key.trim(), secret = secret.trim())
    fun save(cfg: com.rackowl.data.Settings) = cfg.copy(
        sources = if (cfg.sources.any { it.id == initial.id }) cfg.sources.map { if (it.id == initial.id) current() else it } else cfg.sources + current(),
    )

    val (title, keyLabel, secretLabel) = when (initial.type) {
        SourceType.DOCKER -> Triple("Docker", null, null)
        SourceType.KOMODO -> Triple("Komodo", t("API key", "Clé API"), t("API secret", "Secret API"))
        SourceType.GLANCES -> Triple("Glances", t("Username (optional)", "Utilisateur (optionnel)"), t("Password", "Mot de passe"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(if (isNew) t("New $title source", "Nouvelle source $title") else title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel(t("Name", "Nom"))
                HField(name, { name = it }, bg = c.field, placeholder = "nas")
                FieldLabel("URL")
                HField(url, { url = it; result = null }, mono = true, bg = c.field)
                if (keyLabel != null) { FieldLabel(keyLabel); HField(key, { key = it; result = null }, bg = c.field, password = initial.type == SourceType.KOMODO) }
                if (secretLabel != null) { FieldLabel(secretLabel); HField(secret, { secret = it; result = null }, bg = c.field, password = true) }
                if (initial.type == SourceType.DOCKER) Text(
                    t("Docker API exposed over TCP (ideally through docker-socket-proxy). “Sync” adds containers with homepage.* labels to your dashboard.", "API Docker exposée en TCP (idéalement via docker-socket-proxy). « Synchroniser » ajoute au tableau de bord les conteneurs portant des labels homepage.*."),
                    fontSize = 12.sp, color = c.muted,
                )
                result?.let { (ok, msg) -> Text(msg, fontSize = 13.sp, color = if (ok) c.up else c.down) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(if (busy) t("Testing…", "Test…") else t("Test", "Tester"), {
                        if (!busy) { busy = true; scope.launch { result = testSource(app.http(), current(), force = true); busy = false } }
                    }, Modifier.weight(1f), height = 44.dp, radius = 12.dp, bg = c.field, fontSize = 14)
                    if (initial.type == SourceType.DOCKER) SecondaryButton(t("Sync", "Synchroniser"), {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                result = runCatching {
                                    val containers = Docker.containers(app.http(), current().url)
                                    var added = 0
                                    app.store.config.update { cfg ->
                                        val (cats, n) = Docker.merge(cfg.categories, containers, initial.id); added = n
                                        cfg.copy(categories = cats, settings = save(cfg.settings))
                                    }
                                    t("$added service(s) added from labels", "$added service(s) ajouté(s) depuis les labels")
                                }.fold({ true to it }, { false to Monitor.describe(it) })
                                busy = false
                                onSaved()
                            }
                        }
                    }, Modifier.weight(1f), height = 44.dp, radius = 12.dp, bg = c.field, fontSize = 14)
                }
                if (confirmRemove) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.downBg).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(t("Remove “${initial.name}”?", "Retirer « ${initial.name} » ?"), fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f))
                    SecondaryButton(t("Remove", "Retirer"), {
                        app.edit { updateSettings { cfg -> cfg.copy(sources = cfg.sources.filterNot { it.id == initial.id }) } }
                        app.edit { runtime.update { it.copy(resourcesBySource = it.resourcesBySource - initial.id) } }
                        ctx.toast(t("Source removed", "Source retirée")); onSaved(); onDismiss()
                    }, height = 36.dp, radius = 9.dp, fontSize = 13, border = c.down)
                }
            }
        },
        confirmButton = {
            PrimaryButton(t("Save", "Enregistrer"), {
                app.edit { updateSettings { save(it) } }
                onSaved(); onDismiss()
            }, enabled = url.isNotBlank())
        },
        dismissButton = {
            if (!isNew) Text(t("Remove", "Retirer"), color = c.down, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) { confirmRemove = true }.padding(12.dp))
            else Text(t("Cancel", "Annuler"), color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss).padding(12.dp))
        },
    )
}

/** Configuration de Dozzle ou d'un canal d'alerte (Gotify, ntfy), avec test. */
@Composable
private fun SourceDialog(src: Source, s: Settings, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val c = Home.colors
    val scope = rememberCoroutineScope()
    var url by remember {
        mutableStateOf(
            when (src) {
                Source.DOZZLE -> s.dozzleUrl.ifBlank { "http://192.168.1.20:8080" }
                Source.GOTIFY -> s.gotifyUrl.ifBlank { "https://" }
                Source.NTFY -> s.ntfyUrl
            },
        )
    }
    var a by remember { mutableStateOf(when (src) { Source.DOZZLE -> s.dozzleUser; Source.GOTIFY -> s.gotifyToken; Source.NTFY -> s.ntfyTopic }) }
    var b by remember { mutableStateOf(when (src) { Source.DOZZLE -> s.dozzlePassword; Source.NTFY -> s.ntfyToken; else -> "" }) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val (title, aLabel, bLabel) = when (src) {
        Source.DOZZLE -> Triple("Dozzle", t("Username (optional)", "Utilisateur (optionnel)"), t("Password", "Mot de passe"))
        Source.GOTIFY -> Triple("Gotify", t("App token", "Jeton d’application"), null)
        Source.NTFY -> Triple("ntfy", t("Topic", "Sujet"), t("Access token (optional)", "Jeton d’accès (optionnel)"))
    }

    fun settingsWith(base: Settings) = when (src) {
        Source.DOZZLE -> base.copy(dozzleUrl = url.trim().trimEnd('/'), dozzleUser = a.trim(), dozzlePassword = b)
        Source.GOTIFY -> base.copy(gotifyUrl = url.trim().trimEnd('/'), gotifyToken = a.trim(), notifier = Notifier.GOTIFY)
        Source.NTFY -> base.copy(ntfyUrl = url.trim().trimEnd('/'), ntfyTopic = a.trim(), ntfyToken = b.trim(), notifier = Notifier.NTFY)
    }

    suspend fun test(): Pair<Boolean, String> {
        val http = app.http()
        val t = settingsWith(s)
        return runCatching {
            when (src) {
                Source.DOZZLE -> Dozzle(http, t.dozzleUrl, t.dozzleUser, t.dozzlePassword).containers().let { t("${it.size} containers", "${it.size} conteneurs") }
                Source.GOTIFY, Source.NTFY -> {
                    com.rackowl.monitor.AlertNotifier(ctx).send(
                        http, t.copy(muteUntil = 0),
                        com.rackowl.data.AlertEvent(serviceId = null, service = "Rackowl", kind = com.rackowl.data.AlertKind.UP, title = "Test", detail = t("Alerts will arrive here.", "Les alertes arrivent bien ici.")),
                    )
                    t("Test notification sent", "Notification de test envoyée")
                }
            }
        }.fold({ true to it }, { false to Monitor.describe(it) })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel(if (src == Source.NTFY) t("Server", "Serveur") else "URL")
                HField(url, { url = it; result = null }, mono = true, bg = c.field)
                FieldLabel(aLabel); HField(a, { a = it; result = null }, bg = c.field, password = src == Source.GOTIFY)
                if (bLabel != null) { FieldLabel(bLabel); HField(b, { b = it; result = null }, bg = c.field, password = true) }
                result?.let { (ok, msg) -> Text(msg, fontSize = 13.sp, color = if (ok) c.up else c.down) }
                SecondaryButton(if (busy) t("Testing…", "Test…") else t("Test", "Tester"), {
                    if (!busy) { busy = true; scope.launch { result = test(); busy = false } }
                }, Modifier.fillMaxWidth(), height = 44.dp, radius = 12.dp, bg = c.field, fontSize = 14)
            }
        },
        confirmButton = {
            PrimaryButton(t("Save", "Enregistrer"), {
                app.edit { updateSettings { settingsWith(it) } }
                onSaved(); onDismiss()
            })
        },
        dismissButton = {
            if (src == Source.DOZZLE && s.dozzleUrl.isNotBlank()) {
                Text(t("Remove", "Retirer"), color = c.down, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button) {
                    app.edit { updateSettings { it.copy(dozzleUrl = "", dozzleUser = "", dozzlePassword = "") } }
                    ctx.toast(t("Dozzle removed", "Dozzle retiré")); onSaved(); onDismiss()
                }.padding(12.dp))
            } else {
                Text(t("Cancel", "Annuler"), color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss).padding(12.dp))
            }
        },
    )
}
