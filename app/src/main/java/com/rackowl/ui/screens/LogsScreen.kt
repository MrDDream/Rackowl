package com.rackowl.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import com.rackowl.ui.t
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackowl.data.fmtClockSec
import com.rackowl.data.fmtDuration
import com.rackowl.monitor.Monitor
import com.rackowl.net.Dozzle
import com.rackowl.ui.LocalApp
import com.rackowl.ui.components.Chip
import com.rackowl.ui.components.Divider
import com.rackowl.ui.components.HField
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.Segmented
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono
import com.rackowl.ui.toast
import kotlinx.coroutines.CancellationException
import java.time.OffsetDateTime

private const val MAX_LINES = 1000

@Composable
fun LogsScreen(initialContainer: String?) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val c = Home.colors
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val s = config.settings

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        if (s.dozzleUrl.isBlank()) {
            LogsHeader(null, follow = true, onFollow = {}, onDownload = {})
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("No Dozzle instance set up", "Aucune instance Dozzle configurée"), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(t("Add your Dozzle URL in Settings › Logs to follow your container logs live.", "Ajoute l’URL de Dozzle dans Réglages › Logs pour suivre les logs de tes conteneurs en direct."), fontSize = 13.sp, color = c.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            return@Column
        }

        val dozzle = remember(s.dozzleUrl, s.dozzleUser, s.dozzlePassword, s.allowInsecureTls) {
            Dozzle(app.http(), s.dozzleUrl, s.dozzleUser, s.dozzlePassword)
        }
        var containers by remember { mutableStateOf<List<Dozzle.Container>>(emptyList()) }
        var hosts by remember { mutableStateOf<Map<String, Dozzle.Host>>(emptyMap()) }
        fun hostName(id: String) = hosts[id]?.name ?: id.take(10)
        var loadError by remember { mutableStateOf<String?>(null) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf<String?>(null) }
        var hostFilter by rememberSaveable { mutableStateOf<String?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var level by rememberSaveable { mutableStateOf("all") }
        var follow by remember { mutableStateOf(true) }
        val lines = remember { mutableStateListOf<Dozzle.LogLine>() }
        var frozen by remember { mutableStateOf<List<Dozzle.LogLine>?>(null) }
        var streamError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(dozzle) {
            hosts = dozzle.hosts()
            runCatching { dozzle.containers() }
                .onSuccess { list ->
                    containers = list
                    loadError = null
                    if (selectedId == null) {
                        selectedId = (list.firstOrNull { it.name == initialContainer }
                            ?: list.firstOrNull { it.state != "running" && it.state != "exited" }
                            ?: list.firstOrNull { it.state == "running" })?.id
                    }
                }
                .onFailure { loadError = Monitor.describe(it) }
        }
        val current = containers.firstOrNull { it.id == selectedId }

        LaunchedEffect(current?.id) {
            lines.clear(); frozen = null; follow = true; streamError = null
            val cur = current ?: return@LaunchedEffect
            try {
                dozzle.logs(cur).collect { l ->
                    lines.add(l)
                    if (lines.size > MAX_LINES) lines.removeRange(0, lines.size - MAX_LINES)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                streamError = Monitor.describe(e)
            }
        }

        val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) runCatching {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    lines.forEach { w.write("${fmtClockSec(it.time)} ${it.level} ${it.message}\n") }
                }
            }.onSuccess { ctx.toast(t("Logs saved", "Logs enregistrés")) }.onFailure { ctx.toast(t("Couldn’t save", "Échec de l’enregistrement")) }
        }

        LogsHeader(
            s.dozzleUrl.substringAfter("://").trimEnd('/'),
            follow,
            onFollow = {
                follow = !follow
                frozen = if (follow) null else lines.toList()
            },
            onDownload = { saveLauncher.launch("${current?.name ?: "logs"}.log") },
        )

        // ---- Sélecteur de conteneur
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp).zIndex(2f)) {
            if (picking == null) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp)).clickable(role = Role.DropdownList) { picking = "" }
                        .padding(start = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ContainerIcon(current, c.surface)
                    Text(current?.name ?: loadError ?: t("Loading containers…", "Chargement des conteneurs…"), style = mono(14, if (current == null && loadError != null) c.down else c.text),
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) Text(hostName(current.host), fontSize = 11.sp, color = c.muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.selected).padding(horizontal = 8.dp, vertical = 2.dp))
                    Icon(HIcons.ChevronDown, null, Modifier.size(18.dp), tint = c.muted)
                }
            } else {
                Column {
                    HField(
                        picking.orEmpty(), { picking = it }, placeholder = t("Search containers (${containers.size})", "Rechercher un conteneur (${containers.size})"), mono = true,
                        borderColor = c.accent, height = 48.dp,
                        leading = { Icon(HIcons.Search, null, Modifier.size(18.dp), tint = c.muted) },
                        trailing = { IconBtn(HIcons.Close, t("Close search", "Fermer la recherche"), { picking = null }, size = 36.dp, iconSize = 16.dp, tint = c.muted) },
                    )
                    val q = picking.orEmpty().trim().lowercase()
                    val order = mapOf("running" to 2, "exited" to 1)
                    val hostIds = containers.map { it.host }.distinct().sortedBy { hostName(it).lowercase() }
                    // Filtre par serveur (puces), puis recherche texte ; erreurs en premier, puis par nom.
                    val found = containers
                        .filter { hostFilter == null || it.host == hostFilter }
                        .filter { q.isEmpty() || it.name.lowercase().contains(q) || hostName(it.host).lowercase().contains(q) }
                        .sortedWith(compareBy({ order[it.state] ?: 0 }, { it.name.lowercase() }))
                    if (hostIds.size > 1) Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Chip(t("All", "Tous"), hostFilter == null, { hostFilter = null }, trailing = containers.size.toString())
                        hostIds.forEach { h ->
                            Chip(hostName(h), hostFilter == h, { hostFilter = h }, trailing = containers.count { it.host == h }.toString())
                        }
                    }
                    Column(
                        Modifier.padding(top = 10.dp).fillMaxWidth().shadow(16.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
                            .background(c.surface).border(1.dp, c.borderStrong, RoundedCornerShape(12.dp)),
                    ) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            itemsIndexed(found, key = { _, ct -> ct.host + ct.id }) { i, ct ->
                                if (i > 0) Divider()
                                val on = ct.id == selectedId
                                val (label, color) = stateOf(ct)
                                Row(
                                    Modifier.fillMaxWidth().height(48.dp).background(if (on) c.selected else c.surface)
                                        .clickable(role = Role.RadioButton) { selectedId = ct.id; picking = null }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    ContainerIcon(ct, if (on) c.selected else c.surface)
                                    Text(ct.name, style = mono(13, c.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (hostFilter == null) Text(hostName(ct.host), fontSize = 11.sp, color = c.muted, maxLines = 1, softWrap = false)
                                    Text(label, fontSize = 11.sp, color = color, maxLines = 1, softWrap = false)
                                }
                            }
                            if (found.isEmpty()) item { Text(t("No matching container.", "Aucun conteneur ne correspond."), fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(16.dp)) }
                        }
                        Divider()
                        Text(
                            t("${found.size} container${if (found.size > 1) "s" else ""}", "${found.size} conteneur${if (found.size > 1) "s" else ""}") +
                                (if (hostFilter != null) t(" on ${hostName(hostFilter!!)}", " sur ${hostName(hostFilter!!)}") else t(" on ${hostIds.size} host(s)", " sur ${hostIds.size} hôte(s)")) + t(" · errors first", " · erreurs en premier"),
                            fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // ---- Filtres
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HField(
                query, { query = it }, Modifier.weight(1f), placeholder = t("Filter lines…", "Filtrer les lignes…"), mono = true, height = 40.dp,
                leading = { Icon(HIcons.Search, null, Modifier.size(16.dp), tint = c.muted) },
            )
            Segmented(
                listOf("all" to t("All", "Tout"), "WRN" to "W+", "ERR" to "E"), level, { level = it }, Modifier.width(140.dp),
                bg = c.surface, height = 32.dp, textStyle = mono(12),
            )
        }

        // ---- Flux
        val shownSource = frozen ?: lines
        val minRank = when (level) { "WRN" -> 1; "ERR" -> 2; else -> 0 }
        val q = query.trim().lowercase()
        val shown = shownSource.filter { it.level.ordinal >= minRank && (q.isEmpty() || it.message.lowercase().contains(q)) }
        val listState = rememberLazyListState()
        LaunchedEffect(shown.size, follow) { if (follow && shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex) }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).clip(RoundedCornerShape(14.dp))
                .background(c.logBg).border(1.dp, c.logBorder, RoundedCornerShape(14.dp)),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${current?.name ?: "—"} · ${current?.let(::metaOf) ?: ""}", style = mono(12, Color(0xFF9AA1AD)), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val liveColor = if (streamError != null) Color(0xFFF87171) else if (follow) Color(0xFF4ADE80) else Color(0xFFF5A524)
                Box(Modifier.size(7.dp).clip(CircleShape).background(liveColor))
                Spacer(Modifier.width(6.dp))
                Text(if (streamError != null) t("Disconnected", "Déconnecté") else if (follow) t("Live", "En direct") else t("Paused", "En pause"), fontSize = 12.sp, color = liveColor)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.logBorder))
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                    itemsIndexed(shown) { _, l -> LogRow(l) }
                }
                val msg = when {
                    streamError != null && shown.isEmpty() -> streamError
                    shown.isEmpty() && shownSource.isNotEmpty() -> t("No matching line.", "Aucune ligne ne correspond.")
                    shown.isEmpty() && current != null -> t("Waiting for logs…", "En attente de logs…")
                    else -> null
                }
                if (msg != null) Text(msg, fontSize = 13.sp, color = Color(0xFF9AA1AD), modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            if (!follow) {
                val pending = lines.size - (frozen?.size ?: lines.size)
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).height(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF171A21)).border(1.dp, Color(0xFF252A34), RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { follow = true; frozen = null },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Resume live", "Reprendre le direct") + if (pending > 0) t(" · $pending new lines", " · $pending nouvelles lignes") else "", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE6E8EC))
                }
            }
        }
    }
}

@Composable
private fun LogsHeader(host: String?, follow: Boolean, onFollow: () -> Unit, onDownload: () -> Unit) {
    val c = Home.colors
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Logs", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ServiceIcon("dozzle", "Dozzle", 14.dp)
                Text(if (host != null) "Dozzle · $host" else "Dozzle", fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (host != null) {
            IconBtn(if (follow) HIcons.Pause else HIcons.Play, if (follow) t("Pause", "Mettre en pause") else t("Resume live", "Reprendre le direct"), onFollow)
            IconBtn(HIcons.Download, t("Download logs", "Télécharger les logs"), onDownload)
        }
    }
}

@Composable
private fun ContainerIcon(ct: Dozzle.Container?, ring: Color) {
    val (label, color) = ct?.let { stateOf(it) } ?: ("" to Home.colors.muted)
    val name = ct?.name.orEmpty()
    // Icône déduite du nom du conteneur (nextcloud-db → nextcloud).
    val slug = name.substringBefore('-').substringBefore('_').lowercase()
    Box(Modifier.size(24.dp)) {
        ServiceIcon(if (ct == null) "dozzle" else slug, name.ifBlank { "?" }, 24.dp)
        if (ct != null) Box(
            Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(10.dp).clip(CircleShape).background(ring).padding(2.dp)
                .clip(CircleShape).background(color).semantics { contentDescription = label },
        )
    }
}

@Composable
private fun stateOf(ct: Dozzle.Container): Pair<String, Color> {
    val c = Home.colors
    return when {
        ct.health == "unhealthy" -> t("Error", "Erreur") to c.down
        ct.state == "running" -> t("Running", "En cours") to c.up
        ct.state == "exited" -> t("Stopped", "Arrêté") to c.muted
        ct.state == "restarting" -> t("Restarting", "Redémarre") to c.warn
        else -> ct.state.replaceFirstChar { it.uppercase() } to c.down
    }
}

private fun metaOf(ct: Dozzle.Container): String {
    val started = ct.startedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    return when {
        ct.state == "running" && started != null -> t("up for ", "actif depuis ") + fmtDuration(System.currentTimeMillis() - started)
        else -> ct.state
    }
}

@Composable
private fun LogRow(l: Dozzle.LogLine) {
    val (lc, mc, bg) = when (l.level) {
        Dozzle.LogLevel.INF -> Triple(Color(0xFF8FB4FF), Color(0xFFC9CED6), Color.Transparent)
        Dozzle.LogLevel.WRN -> Triple(Color(0xFFF5A524), Color(0xFFE8D3A8), Color(0xFF1A160C))
        Dozzle.LogLevel.ERR -> Triple(Color(0xFFF87171), Color(0xFFF2C2C2), Color(0xFF1F1012))
    }
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fmtClockSec(l.time), style = mono(11, Color(0xFF6B7280), FontWeight.Normal).copy(lineHeight = 16.sp), maxLines = 1, softWrap = false)
        Text(l.level.name, style = mono(11, lc, FontWeight.Normal).copy(lineHeight = 16.sp), maxLines = 1, softWrap = false)
        Text(l.message, style = mono(11, mc, FontWeight.Normal).copy(lineHeight = 16.sp), modifier = Modifier.weight(1f))
    }
}
