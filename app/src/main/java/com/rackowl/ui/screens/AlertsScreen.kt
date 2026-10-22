package com.rackowl.ui.screens

import androidx.compose.foundation.background
import com.rackowl.ui.t
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rackowl.data.AlertKind
import com.rackowl.data.allServices
import com.rackowl.data.fmtClock
import com.rackowl.data.fmtDay
import com.rackowl.data.fmtDuration
import com.rackowl.ui.LocalApp
import com.rackowl.ui.Routes
import com.rackowl.ui.components.Chip
import com.rackowl.ui.components.Divider
import com.rackowl.ui.components.HCard
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.SectionTitle
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.edit
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono

@Composable
fun AlertsScreen(nav: NavHostController) {
    val app = LocalApp.current
    val c = Home.colors
    val alerts by app.store.alerts.state.collectAsStateWithLifecycle()
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val runtime by app.store.runtime.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<AlertKind?>(null) }
    val now = System.currentTimeMillis()
    val muted = config.settings.muteUntil > now

    val statuses = com.rackowl.net.Lan.visibleStatuses(runtime, config.allServices(), config.settings.pingEnabled)
    val incidents = config.allServices().mapNotNull { s -> statuses[s.id]?.takeIf { it.up == false }?.let { s to it } }
    val unread = alerts.count { !it.read }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Alerts", "Alertes"), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Row {
                    Text("● ", fontSize = 13.sp, color = if (incidents.isEmpty()) c.up else c.down)
                    Text(
                        when (incidents.size) { 0 -> t("No ongoing incident", "Aucun incident en cours"); 1 -> t("1 ongoing incident", "1 incident en cours"); else -> t("${incidents.size} ongoing incidents", "${incidents.size} incidents en cours") } +
                            if (unread > 0) " · " + t("$unread unread", "$unread non lue${if (unread > 1) "s" else ""}") else "",
                        fontSize = 13.sp, color = c.muted,
                    )
                }
            }
            IconBtn(HIcons.CheckAll, t("Mark all as read", "Tout marquer comme lu"), { app.edit { markAllRead() } })
            IconBtn(HIcons.Sliders, t("Alert rules", "Règles d'alerte"), { nav.navigate(Routes.SETTINGS) })
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to t("All", "Toutes"), AlertKind.DOWN to t("Offline", "Hors ligne"), AlertKind.UP to t("Recovered", "Rétablis"), AlertKind.WARN to t("Warnings", "Avertissements")).forEach { (k, label) ->
                Chip(label, filter == k, { filter = k }, trailing = (if (k == null) alerts.size else alerts.count { it.kind == k }).toString())
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (filter == null || filter == AlertKind.DOWN) incidents.forEach { (s, st) ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.incidentBg)
                        .border(1.dp, c.incidentBorder, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ServiceIcon(s.icon, s.name, 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t("${s.name} offline", "${s.name} hors ligne"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(st.downSince?.let { t("For ", "Depuis ") + fmtDuration(now - it) }, st.httpCode?.let { "HTTP $it" } ?: st.error).joinToString(" · "),
                                fontSize = 12.sp, color = c.incidentText,
                            )
                        }
                        Row(
                            Modifier.height(24.dp).clip(RoundedCornerShape(50)).background(c.downBg).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(c.down))
                            Text(t("Ongoing", "En cours"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.down)
                        }
                    }
                    Text(
                        "${s.url} — " + t(
                            "${st.failedAttempts} failed attempt${if (st.failedAttempts > 1) "s" else ""}",
                            "${st.failedAttempts} tentative${if (st.failedAttempts > 1) "s" else ""} échouée${if (st.failedAttempts > 1) "s" else ""}",
                        ),
                        style = mono(12, c.textSecondary),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.incidentInner).padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IncidentButton(t("More info", "Plus d'infos"), null, Modifier.weight(1f)) { nav.navigate(Routes.detail(s.id)) }
                        IncidentButton(if (muted) t("Muted until ${fmtClock(config.settings.muteUntil)}", "En sourdine jusqu’à ${fmtClock(config.settings.muteUntil)}") else t("Mute 1 h", "Sourdine 1 h"), HIcons.BellOff, Modifier.weight(1f)) {
                            app.edit { updateSettings { it.copy(muteUntil = if (muted) 0 else System.currentTimeMillis() + 3600_000L) } }
                        }
                    }
                }
            }

            val shown = alerts.filter { filter == null || it.kind == filter }
            shown.groupBy { fmtDay(it.time, now) }.forEach { (day, events) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(day)
                    HCard(Modifier.fillMaxWidth()) {
                        events.forEachIndexed { i, e ->
                            if (i > 0) Divider()
                            val (color, glyph, label) = when (e.kind) {
                                AlertKind.DOWN -> Triple(c.down, "!", t("Offline", "Hors ligne"))
                                AlertKind.UP -> Triple(c.up, "✓", t("Recovered", "Rétabli"))
                                AlertKind.WARN -> Triple(c.warn, "!", t("Warning", "Avertissement"))
                            }
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .let { m -> if (e.serviceId != null) m.clickable(role = Role.Button) { nav.navigate(Routes.detail(e.serviceId)) } else m }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(28.dp)) {
                                    if (e.serviceId != null) ServiceIcon(e.icon, e.service, 28.dp)
                                    else Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(c.border), contentAlignment = Alignment.Center) {
                                        Icon(HIcons.Server, null, Modifier.size(16.dp), tint = c.textSecondary)
                                    }
                                    Box(
                                        Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(14.dp).clip(CircleShape).background(c.surface).padding(2.dp)
                                            .clip(CircleShape).background(color).semantics { contentDescription = label },
                                        contentAlignment = Alignment.Center,
                                    ) { Text(glyph, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = c.bg) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row {
                                        Text(e.service, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(" · ${e.title}", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(e.detail, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(fmtClock(e.time), style = mono(12, c.muted))
                                    if (!e.read) Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(c.accent).semantics { contentDescription = t("unread", "non lue") })
                                }
                            }
                        }
                    }
                }
            }

            if (shown.isEmpty() && (incidents.isEmpty() || filter != null && filter != AlertKind.DOWN)) {
                Text(
                    if (alerts.isEmpty()) t("No alerts yet. Outages and recoveries will show up here.", "Aucune alerte pour l’instant. Les pannes et retours en ligne apparaîtront ici.") else t("No alerts for this filter.", "Aucune alerte pour ce filtre."),
                    fontSize = 14.sp, color = c.muted, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun IncidentButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, modifier: Modifier, onClick: () -> Unit) {
    val c = Home.colors
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.bg).border(1.dp, c.incidentBorder, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = c.text)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
