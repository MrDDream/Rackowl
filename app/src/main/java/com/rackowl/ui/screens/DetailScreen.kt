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
import com.rackowl.data.inMaintenance
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
import com.rackowl.ui.edit
import com.rackowl.ui.openUrl
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono
import com.rackowl.ui.toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(nav: NavHostController, id: String, onClose: (() -> Unit)? = null) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val runtime by app.store.runtime.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val c = Home.colors
    val s = config.findService(id)
    if (s == null) {
        LaunchedEffect(Unit) { onClose?.invoke() ?: nav.popBackStack() }
        return
    }
    val st = runtime.statuses[id]
    val settings = config.settings
    LaunchedEffect(id) { app.monitor.refreshService(id) }
    var showDiagnostic by remember { mutableStateOf(false) }
    var maintenanceMenu by remember { mutableStateOf(false) }
    if (showDiagnostic) DiagnosticDialog(s) { showDiagnostic = false }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            IconBtn(HIcons.Back, t("Back", "Retour"), { onClose?.invoke() ?: nav.popBackStack() })
            Spacer(Modifier.weight(1f))
            IconBtn(HIcons.Bug, t("Diagnostic", "Diagnostic"), { showDiagnostic = true }, iconSize = 20.dp)
            Box {
                IconBtn(HIcons.Wrench, t("Maintenance", "Maintenance"), { maintenanceMenu = true }, iconSize = 20.dp,
                    tint = if (s.inMaintenance()) c.warn else c.textSecondary)
                // Maintenance ponctuelle : pas d'alerte pendant la durée choisie.
                androidx.compose.material3.DropdownMenu(maintenanceMenu, { maintenanceMenu = false }, containerColor = c.surface) {
                    fun set(until: Long) {
                        maintenanceMenu = false
                        app.edit { saveService(s.copy(maintenanceUntil = until), config.categoryOf(id)?.id ?: return@edit) }
                    }
                    val now = System.currentTimeMillis()
                    listOf(3600_000L to t("1 hour", "1 heure"), 4 * 3600_000L to t("4 hours", "4 heures")).forEach { (d, label) ->
                        androidx.compose.material3.DropdownMenuItem({ Text(t("Maintenance · $label", "Maintenance · $label")) }, { set(now + d) })
                    }
                    androidx.compose.material3.DropdownMenuItem({ Text(t("Until tomorrow 8:00", "Jusqu’à demain 8 h")) }, {
                        set(java.util.Calendar.getInstance().apply {
                            add(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 8)
                            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
                        }.timeInMillis)
                    })
                    if (s.maintenanceUntil > now) androidx.compose.material3.DropdownMenuItem({ Text(t("End maintenance", "Terminer la maintenance"), color = c.down) }, { set(0) })
                }
            }
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
                val server = ContainerControl.serverName(s, settings)
                if (s.container != null && server != null) Pill(server, c.textSecondary, c.selected)
            }

            if (s.inMaintenance()) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.warnBg).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.Icon(HIcons.Wrench, null, Modifier.size(18.dp), tint = c.warn)
                Text(
                    if (s.maintenanceUntil > System.currentTimeMillis()) t("Maintenance until ${com.rackowl.data.fmtClock(s.maintenanceUntil)} · no alerts", "Maintenance jusqu’à ${com.rackowl.data.fmtClock(s.maintenanceUntil)} · pas d’alerte")
                    else t("Daily maintenance window · no alerts", "Plage de maintenance quotidienne · pas d’alerte"),
                    fontSize = 13.sp, color = c.warn,
                )
            }

            val canControl = ContainerControl.canControl(s, settings)
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

            Availability(st?.history.orEmpty(), st?.buckets.orEmpty())


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

/**
 * Disponibilité : 24 h (barres horaires, hauteur = latence), 7 jours (tranches de 6 h) ou 30 jours (par jour).
 * Rouge = au moins une panne dans la barre ; la teinte des barres longues suit le taux de réussite.
 */
@Composable
private fun Availability(history: List<Sample>, buckets: List<com.rackowl.data.Bucket>) {
    val c = Home.colors
    val now = System.currentTimeMillis()
    var range by remember { mutableStateOf(1) }
    // Chaque barre : (fraction de hauteur, taux de réussite ou null si aucune mesure).
    val bars: List<Pair<Float, Double?>>
    val pct: Double?
    when (range) {
        1 -> {
            val maxLatency = history.mapNotNull { it.latency }.maxOrNull()?.coerceAtLeast(1) ?: 1
            bars = (0 until 24).map { h ->
                val from = now - (24 - h) * 3600_000L
                val b = history.filter { it.t >= from && it.t < from + 3600_000L }
                val lat = b.mapNotNull { it.latency }.average().takeIf { !it.isNaN() }
                when {
                    b.isEmpty() -> 0.06f to null
                    b.any { it.latency == null } -> 1f to b.count { it.latency != null }.toDouble() / b.size
                    else -> (0.25f + 0.75f * (lat!! / maxLatency).toFloat()).coerceIn(0.1f, 1f) to 1.0
                }
            }
            pct = history.takeIf { it.isNotEmpty() }?.let { h -> h.count { it.latency != null } * 100.0 / h.size }
        }
        else -> {
            val span = if (range == 7) 6 * 3600_000L else 86_400_000L
            val count = if (range == 7) 28 else 30
            bars = (0 until count).map { i ->
                val from = now - (count - i) * span
                val b = buckets.filter { it.t >= from && it.t < from + span }
                val total = b.sumOf { it.total }
                if (total == 0) 0.06f to null else {
                    val ok = b.sumOf { it.up }.toDouble() / total
                    (0.3f + 0.7f * ok.toFloat()) to ok
                }
            }
            val inRange = buckets.filter { it.t >= now - range * 86_400_000L }
            val total = inRange.sumOf { it.total }
            pct = if (total == 0) null else inRange.sumOf { it.up } * 100.0 / total
        }
    }
    HCard(Modifier.fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Availability", "Disponibilité"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    pct?.let { fmtDec(it, 1) + " %" } ?: "—",
                    style = mono(12, if (pct == null || pct >= 99.9) c.up else if (pct >= 95) c.warn else c.down),
                )
            }
            com.rackowl.ui.components.Segmented(listOf(1 to "24 h", 7 to t("7 days", "7 jours"), 30 to t("30 days", "30 jours")), range, { range = it }, height = 32.dp)
            val downBars = bars.count { (it.second ?: 1.0) < 1.0 }
            Row(
                Modifier.fillMaxWidth().height(56.dp).semantics {
                    contentDescription = t("Availability chart, $downBars bar(s) with an outage", "Graphique de disponibilité, $downBars barre(s) avec coupure")
                },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { (frac, ok) ->
                    val color = when {
                        ok == null -> c.border
                        ok >= 0.999 -> c.accent.copy(alpha = 0.55f)
                        ok >= 0.95 -> c.warn
                        else -> c.down
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(frac).clip(RoundedCornerShape(2.dp)).background(color))
                }
            }
            Row {
                Text(
                    when (range) { 1 -> t("24 h ago", "il y a 24 h"); 7 -> t("7 days ago", "il y a 7 jours"); else -> t("30 days ago", "il y a 30 jours") },
                    fontSize = 11.sp, color = c.muted, modifier = Modifier.weight(1f),
                )
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
                        ContainerControl.Action.START -> t("The container will be started on ${ContainerControl.serverName(s, settings).orEmpty()}.", "Le conteneur va être démarré sur ${ContainerControl.serverName(s, settings).orEmpty()}.")
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
