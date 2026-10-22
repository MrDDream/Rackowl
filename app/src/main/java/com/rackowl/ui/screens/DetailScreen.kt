package com.rackowl.ui.screens

import androidx.compose.foundation.background
import com.rackowl.ui.t
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rackowl.data.Sample
import com.rackowl.data.categoryOf
import com.rackowl.data.findService
import com.rackowl.data.fmtAgo
import com.rackowl.data.fmtDec
import com.rackowl.data.fmtDuration
import com.rackowl.monitor.Monitor
import com.rackowl.net.ContainerControl
import com.rackowl.ui.LocalApp
import com.rackowl.ui.Routes
import com.rackowl.ui.components.HCard
import com.rackowl.ui.components.HField
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.Pill
import com.rackowl.ui.components.PrimaryButton
import com.rackowl.ui.components.ProgressBar
import com.rackowl.ui.components.SecondaryButton
import com.rackowl.ui.components.SectionTitle
import com.rackowl.ui.components.StatusDot
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.components.levelColor
import com.rackowl.ui.openUrl
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono
import com.rackowl.ui.toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(nav: NavHostController, id: String) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val runtime by app.store.runtime.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val c = Home.colors
    val s = config.findService(id)
    if (s == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val st = runtime.statuses[id]
    val settings = config.settings
    LaunchedEffect(id) { app.monitor.refreshService(id) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            IconBtn(HIcons.Back, t("Back", "Retour"), { nav.popBackStack() })
            Spacer(Modifier.weight(1f))
            IconBtn(HIcons.Edit, t("Edit service", "Modifier le service"), { nav.navigate(Routes.edit(id)) }, iconSize = 20.dp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ServiceIcon(s.icon, s.name, 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.url, style = mono(12, c.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // État et ping, à droite du nom (masqués quand les pings sont désactivés).
                if (settings.pingEnabled) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(st?.up, 8.dp)
                    Text(
                        when (st?.up) {
                            true -> "${st.latencyMs} ms"
                            false -> st.downSince?.let { fmtDuration(System.currentTimeMillis() - it) } ?: "KO"
                            null -> "—"
                        },
                        style = mono(13, when (st?.up) { true -> c.up; false -> c.down; null -> c.muted }), maxLines = 1, softWrap = false,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                config.categoryOf(id)?.let { Pill(it.name, c.textSecondary, c.selected) }
                // Serveur qui héberge le conteneur (serveur Komodo, ou nom de la source Docker).
                val server = s.containerHost ?: ContainerControl.sourceFor(s, settings)?.name
                if (s.container != null && server != null) Pill(server, c.textSecondary, c.selected)
            }

            val canControl = s.container != null && ContainerControl.sourceFor(s, settings) != null
            val canLogs = s.container != null && settings.dozzleUrl.isNotBlank()
            if (canControl || canLogs) QuickActions(s, canControl, canLogs) { nav.navigate(Routes.logs(s.container!!)) }
            if (st?.up == false && st.error != null) {
                Text(st.error + (st.httpCode?.let { " · HTTP $it" } ?: ""), style = mono(12, c.incidentText))
            }

            if (st != null && st.stats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    st.stats.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { stat ->
                                HCard(Modifier.weight(1f)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stat.value, style = mono(22, levelColor(stat.level)), maxLines = 1)
                                        Text(stat.label, fontSize = 12.sp, color = c.muted)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (st?.statsError != null) {
                Text(t("Widget: ${st.statsError} (${fmtAgo(st.statsAt)})", "Widget : ${st.statsError} (${fmtAgo(st.statsAt)})"), fontSize = 12.sp, color = c.warn)
            }

            Availability(st?.history.orEmpty())


            if (st != null && st.details.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("${st.detailsTitle ?: t("Details", "Détails")} · ${st.details.size}")
                    // Longue liste (séries, films…) : champ de recherche.
                    var filter by remember(id) { mutableStateOf("") }
                    if (st.details.size > 10) HField(
                        filter, { filter = it }, placeholder = t("Search", "Rechercher"),
                        leading = { androidx.compose.material3.Icon(HIcons.Search, null, Modifier.size(18.dp), tint = c.muted) },
                    )
                    val q = filter.trim().lowercase()
                    st.details.filter { q.isEmpty() || it.title.lowercase().contains(q) }.forEach { d ->
                        HCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row {
                                    Text(d.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(d.subtitle, fontSize = 13.sp, color = c.muted)
                                }
                                d.progress?.let { ProgressBar(it, c.accent, height = 4.dp) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(t("Open site", "Ouvrir le site"), { ctx.openUrl(s.url) }, Modifier.weight(1f), icon = HIcons.External)
        }
    }
}

/** Barres horaires de disponibilité sur 24 h (rouge = au moins une panne dans l'heure). */
@Composable
private fun Availability(history: List<Sample>) {
    val c = Home.colors
    val now = System.currentTimeMillis()
    val hours = (0 until 24).map { h ->
        val from = now - (24 - h) * 3600_000L
        history.filter { it.t >= from && it.t < from + 3600_000L }
    }
    val total = history.size
    val upCount = history.count { it.latency != null }
    val maxLatency = history.mapNotNull { it.latency }.maxOrNull()?.coerceAtLeast(1) ?: 1
    HCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(t("24 h availability", "Disponibilité 24 h"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (total == 0) "—" else fmtDec(upCount * 100.0 / total, 1) + " %",
                    style = mono(12, if (total == 0 || upCount == total) c.up else c.warn),
                )
            }
            val downHours = hours.count { b -> b.any { it.latency == null } }
            Row(
                Modifier.fillMaxWidth().height(56.dp).semantics {
                    contentDescription = t("Response time over 24 hours, $downHours hour(s) with an outage", "Temps de réponse sur 24 heures, $downHours heure(s) avec coupure")
                },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                hours.forEach { bucket ->
                    val down = bucket.any { it.latency == null }
                    val lat = bucket.mapNotNull { it.latency }.average().takeIf { !it.isNaN() }
                    val frac = when {
                        down -> 1f
                        lat == null -> 0.06f
                        else -> (0.25f + 0.75f * (lat / maxLatency).toFloat()).coerceIn(0.1f, 1f)
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight(frac).clip(RoundedCornerShape(2.dp))
                            .background(if (down) c.down else if (lat == null) c.border else c.accent.copy(alpha = 0.55f)),
                    )
                }
            }
            Row {
                Text(t("24 h ago", "il y a 24 h"), fontSize = 11.sp, color = c.muted, modifier = Modifier.weight(1f))
                Text(t("now", "maintenant"), fontSize = 11.sp, color = c.muted)
            }
        }
    }
}

/**
 * Actions rapides sur le conteneur du service : démarrer, redémarrer, arrêter (avec confirmation),
 * et à droite l'accès à ses logs.
 */
@Composable
private fun QuickActions(s: com.rackowl.data.Service, canControl: Boolean, canLogs: Boolean, onLogs: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val c = Home.colors
    val scope = rememberCoroutineScope()
    val settings = app.store.config.value.settings
    val source = ContainerControl.sourceFor(s, settings)
    var state by remember { mutableStateOf<String?>(null) }
    var stateError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<ContainerControl.Action?>(null) }
    var confirm by remember { mutableStateOf<ContainerControl.Action?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(s.id, reload) {
        if (!canControl) return@LaunchedEffect
        runCatching { ContainerControl.state(app.http(), settings, s) }
            .onSuccess { state = it; stateError = if (it == null) t("Container not found", "Conteneur introuvable") else null }
            .onFailure { stateError = Monitor.describe(it) }
    }

    val running = state == "running" || state == "restarting"
    val ready = busy == null && state != null
    HCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Quick actions", "Actions rapides"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (canControl) when {
                    busy != null -> Pill("${busy!!.label}…", c.warn, c.warnBg, dot = true)
                    stateError != null -> Pill(t("Error", "Erreur"), c.down, c.downBg, dot = true)
                    state == null -> Pill("…", c.muted, c.selected)
                    running -> Pill(if (state == "restarting") t("Restarting", "Redémarre") else t("Running", "En cours"), c.up, c.upBg, dot = true)
                    else -> Pill(if (state == "exited") t("Stopped", "Arrêté") else state!!.replaceFirstChar { it.uppercase() }, c.muted, c.selected, dot = true)
                }
            }
            stateError?.let { Text(it, fontSize = 12.sp, color = c.down) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canControl) {
                    ActionIcon(HIcons.Play, ContainerControl.Action.START.label, ready && !running) { confirm = ContainerControl.Action.START }
                    ActionIcon(HIcons.Refresh, ContainerControl.Action.RESTART.label, ready && running) { confirm = ContainerControl.Action.RESTART }
                    ActionIcon(HIcons.Power, ContainerControl.Action.STOP.label, ready && running, tint = c.down) { confirm = ContainerControl.Action.STOP }
                }
                Spacer(Modifier.weight(1f))
                if (canLogs) ActionIcon(HIcons.Terminal, t("Container logs", "Logs du conteneur"), true, onClick = onLogs)
            }
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = c.surface,
            title = { Text("${action.label} ${s.container} ?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
            text = {
                Text(
                    when (action) {
                        ContainerControl.Action.START -> t("The container will be started on ${s.containerHost ?: source?.name.orEmpty()}.", "Le conteneur va être démarré sur ${s.containerHost ?: source?.name.orEmpty()}.")
                        ContainerControl.Action.RESTART -> t("The service will be unavailable for a few seconds while it restarts.", "Le service sera indisponible quelques secondes pendant le redémarrage.")
                        ContainerControl.Action.STOP -> t("${s.name} will stay unreachable until you start the container again.", "Le service ${s.name} ne sera plus accessible jusqu’à ce que tu redémarres le conteneur.")
                    },
                    fontSize = 14.sp, color = c.textSecondary,
                )
            },
            confirmButton = {
                PrimaryButton(action.label, {
                    confirm = null
                    busy = action
                    scope.launch {
                        runCatching { ContainerControl.run(app.http(), settings, s, action) }
                            .onSuccess { ctx.toast("${action.label} : ${s.container}") }
                            .onFailure { ctx.toast(Monitor.describe(it)) }
                        // Laisse le temps au conteneur de changer d'état avant de relire.
                        kotlinx.coroutines.delay(2_500)
                        busy = null
                        reload++
                        app.monitor.refreshService(s.id)
                    }
                })
            },
            dismissButton = {
                Text(t("Cancel", "Annuler"), color = c.textSecondary, fontSize = 14.sp,
                    modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { confirm = null }.padding(12.dp))
            },
        )
    }
}

/** Bouton carré à icône seule des actions rapides. */
@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color = Home.colors.textSecondary, onClick: () -> Unit,
) {
    val c = Home.colors
    IconBtn(
        icon, label, onClick,
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(12.dp)),
        tint = tint, size = 44.dp, iconSize = 20.dp, enabled = enabled,
    )
}
