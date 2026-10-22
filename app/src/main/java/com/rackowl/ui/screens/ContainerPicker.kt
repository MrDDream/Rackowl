package com.rackowl.ui.screens

import androidx.compose.foundation.background
import com.rackowl.ui.t
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackowl.data.SourceType
import com.rackowl.monitor.Monitor
import com.rackowl.net.Containers
import com.rackowl.net.PickHost
import com.rackowl.net.PickableContainer
import com.rackowl.ui.LocalApp
import com.rackowl.ui.components.Divider
import com.rackowl.ui.components.HField
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.PrimaryButton
import com.rackowl.ui.components.Segmented
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.edit
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono

/**
 * Sélecteur plein écran : on choisit d'abord l'hôte (source Docker ou serveur Komodo),
 * puis le conteneur. L'URL proposée est {scheme}://{IP de l'hôte}:{port publié}.
 */
@Composable
fun ContainerPicker(onDismiss: () -> Unit, onPick: (PickableContainer, PickHost, String?, String) -> Unit) {
    val app = LocalApp.current
    val c = Home.colors
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val settings = config.settings
    var result by remember { mutableStateOf<Containers.Result?>(null) }
    var hostKey by remember { mutableStateOf<String?>(null) }
    var editingIp by remember { mutableStateOf<PickHost?>(null) }
    val hasSource = settings.sources.any { it.type == SourceType.DOCKER || it.type == SourceType.KOMODO }

    LaunchedEffect(Unit) {
        if (hasSource) result = Containers.hosts(app.http(), settings, Monitor::describe)
    }
    val host = result?.hosts?.firstOrNull { it.key == hostKey }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (host == null) IconBtn(HIcons.Close, t("Close", "Fermer"), onDismiss) else IconBtn(HIcons.Back, t("Pick another host", "Choisir un autre hôte"), { hostKey = null })
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(if (host == null) t("Pick a host", "Choisir un hôte") else host.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (host != null) Text(
                        Containers.ipOf(host, settings) ?: t("IP not set", "IP non définie"), style = mono(12, if (Containers.ipOf(host, settings) != null) c.muted else c.warn),
                    )
                }
                if (host != null) IconBtn(HIcons.Edit, t("Edit ${host.name}’s IP", "Modifier l'IP de ${host.name}"), { editingIp = host }, iconSize = 20.dp)
            }

            val r = result
            when {
                !hasSource -> Message(t("No container source. Add a Docker or Komodo source in Settings › Sources.", "Aucune source de conteneurs. Ajoute une source Docker ou Komodo dans Réglages › Sources."))
                r == null -> Message(t("Loading hosts…", "Chargement des hôtes…"))
                host == null -> HostList(r, settings) { h ->
                    if (Containers.ipOf(h, settings) == null) editingIp = h
                    hostKey = h.key
                }
                else -> ContainerList(host, Containers.ipOf(host, settings), onPick)
            }
        }

        editingIp?.let { h ->
            IpDialog(h, Containers.ipOf(h, settings).orEmpty(), onDismiss = { editingIp = null }) { ip ->
                app.edit { updateSettings { it.copy(hostIps = if (ip.isBlank()) it.hostIps - h.key else it.hostIps + (h.key to ip.trim())) } }
                editingIp = null
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, fontSize = 14.sp, color = Home.colors.muted, modifier = Modifier.padding(24.dp))
}

@Composable
private fun HostList(r: Containers.Result, settings: com.rackowl.data.Settings, onPick: (PickHost) -> Unit) {
    val c = Home.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        r.errors.forEach { (source, err) ->
            item { Text("$source : $err", fontSize = 12.sp, color = c.down, modifier = Modifier.padding(bottom = 8.dp)) }
        }
        if (r.hosts.isEmpty() && r.errors.isEmpty()) item { Text(t("No host found.", "Aucun hôte trouvé."), fontSize = 14.sp, color = c.muted) }
        items(r.hosts, key = { it.key }) { h ->
            val ip = Containers.ipOf(h, settings)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onPick(h) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ServiceIcon(if (h.sourceType == SourceType.KOMODO) "komodo" else "docker", h.name, 28.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(h.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        (if (h.sourceType == SourceType.KOMODO) t("Komodo · ${h.sourceName}", "Komodo · ${h.sourceName}") else "Docker") +
                            " · ${h.containers.count { it.running }}/${h.containers.size} " + t("containers", "conteneurs"),
                        fontSize = 12.sp, color = c.muted,
                    )
                }
                Text(ip ?: t("IP needed", "IP à définir"), style = mono(12, if (ip != null) c.accent else c.warn))
                Icon(HIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.placeholder)
            }
            Divider()
        }
    }
}

@Composable
private fun ContainerList(host: PickHost, ip: String?, onPick: (PickableContainer, PickHost, String?, String) -> Unit) {
    val c = Home.colors
    var query by remember { mutableStateOf("") }
    var scheme by remember { mutableStateOf("auto") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HField(
                query, { query = it }, Modifier.weight(1f), placeholder = t("Name or image", "Nom ou image"), mono = true,
                leading = { Icon(HIcons.Search, null, Modifier.size(18.dp), tint = c.muted) },
            )
            Segmented(
                listOf("auto" to "Auto", "http" to "http", "https" to "https"), scheme, { scheme = it },
                Modifier.width(170.dp), bg = c.surface, height = 38.dp,
            )
        }
        val q = query.trim().lowercase()
        val shown = host.containers
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.image.lowercase().contains(q) }
            .sortedWith(compareBy({ !it.running }, { Containers.webPort(it) == null }, { it.name.lowercase() }))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            if (shown.isEmpty()) item { Text(t("No matching container.", "Aucun conteneur ne correspond."), fontSize = 14.sp, color = c.muted) }
            items(shown, key = { it.name }) { ct ->
                val sch = if (scheme == "auto") Containers.defaultScheme(ct) else scheme
                val url = ct.labels["homepage.href"] ?: Containers.urlFor(ct, ip, sch)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { onPick(ct, host, ip, sch) }.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(28.dp)) {
                        ServiceIcon(Containers.imageBase(ct.image).ifBlank { ct.name.lowercase() }, ct.name, 28.dp)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape).background(c.bg).padding(2.dp).clip(CircleShape)
                                .background(if (ct.running) c.up else c.muted)
                                .semantics { contentDescription = if (ct.running) t("Running", "En cours") else t("Stopped", "Arrêté") },
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ct.name, style = mono(14, c.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            url ?: if (Containers.webPort(ct) == null) t("No published port", "Aucun port publié") else t("Host IP needed", "IP de l’hôte à définir"),
                            style = mono(11, if (url != null) c.accent else c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun IpDialog(host: PickHost, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Home.colors
    var ip by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(t("${host.name} IP", "IP de ${host.name}"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Used to build service URLs: http(s)://{IP}:{PORT}", "Utilisée pour construire l’URL des services : http(s)://{IP}:{PORT}"), fontSize = 13.sp, color = c.muted)
                HField(ip, { ip = it }, mono = true, bg = c.field, keyboardType = KeyboardType.Uri, placeholder = "192.168.1.20")
                host.detectedIp?.let { Text(t("Detected: $it", "Détectée : $it"), fontSize = 12.sp, color = c.muted) }
            }
        },
        confirmButton = { PrimaryButton(t("Save", "Enregistrer"), { onSave(ip) }) },
        dismissButton = {
            Text(t("Cancel", "Annuler"), color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss).padding(12.dp))
        },
    )
}
