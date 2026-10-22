package com.rackowl.ui.screens

import androidx.compose.foundation.background
import com.rackowl.ui.t
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.rackowl.data.Category
import com.rackowl.data.Service
import com.rackowl.data.categoryOf
import com.rackowl.data.findService
import com.rackowl.data.fmtInterval
import com.rackowl.data.slugify
import com.rackowl.integrations.Auth
import com.rackowl.integrations.Integrations
import com.rackowl.monitor.Monitor
import com.rackowl.net.Containers
import com.rackowl.ui.LocalApp
import com.rackowl.ui.components.Chip
import com.rackowl.ui.components.Divider
import com.rackowl.ui.components.FieldLabel
import com.rackowl.ui.components.HCard
import com.rackowl.ui.components.HField
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.HSwitch
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.PrimaryButton
import com.rackowl.ui.components.SecondaryButton
import com.rackowl.ui.components.Segmented
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.components.SettingRow
import com.rackowl.ui.edit
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val INTERVALS = listOf(15, 30, 60, 300)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditServiceScreen(nav: NavHostController, serviceId: String?, categoryId: String?) {
    val app = LocalApp.current
    val c = Home.colors
    val scope = rememberCoroutineScope()
    val config = app.store.config.value
    val original = serviceId?.let { config.findService(it) }

    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(original?.url ?: "http://") }
    var description by rememberSaveable { mutableStateOf(original?.description.orEmpty()) }
    var widget by rememberSaveable { mutableStateOf(original?.widget) }
    var apiKey by rememberSaveable { mutableStateOf(original?.apiKey.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(original?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(original?.password.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(original?.icon.orEmpty()) }
    var iconTouched by rememberSaveable { mutableStateOf(original != null) }
    var monitor by rememberSaveable { mutableStateOf(original?.monitor ?: true) }
    var showInWidget by rememberSaveable { mutableStateOf(original?.showInWidget ?: true) }
    var confirmChecks by rememberSaveable { mutableStateOf(original?.confirmChecks ?: 1) }
    var critical by rememberSaveable { mutableStateOf(original?.critical ?: false) }
    var expectText by rememberSaveable { mutableStateOf(original?.expectText.orEmpty()) }
    val origWindow = original?.maintenanceWindow?.let { com.rackowl.data.splitWindow(it) }
    var windowStart by rememberSaveable { mutableStateOf(origWindow?.first.orEmpty()) }
    var windowEnd by rememberSaveable { mutableStateOf(origWindow?.second.orEmpty()) }
    var interval by rememberSaveable { mutableStateOf(original?.pingIntervalSec ?: 30) }
    var custom by rememberSaveable { mutableStateOf(original != null && original.pingIntervalSec !in INTERVALS) }
    var customValue by rememberSaveable { mutableStateOf(((original?.pingIntervalSec ?: 90).let { if (it % 60 == 0) it / 60 else it }).toString()) }
    var customUnit by rememberSaveable { mutableStateOf(if ((original?.pingIntervalSec ?: 90) % 60 == 0) "min" else "s") }
    var category by rememberSaveable { mutableStateOf(original?.let { config.categoryOf(it.id)?.id } ?: categoryId ?: config.categories.firstOrNull()?.id) }
    var newCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var container by rememberSaveable { mutableStateOf(original?.container) }
    var containerSource by rememberSaveable { mutableStateOf(original?.containerSource) }
    var containerHost by rememberSaveable { mutableStateOf(original?.containerHost) }
    var tileStats by remember { mutableStateOf(original?.tileStats.orEmpty()) }
    /** Statistiques disponibles : celles du dernier test, sinon les dernières relevées. */
    var testStats by remember { mutableStateOf<List<com.rackowl.data.Stat>?>(null) }
    var picking by remember { mutableStateOf(false) }

    var detected by remember { mutableStateOf<Integrations.Detection?>(null) }
    var manual by rememberSaveable { mutableStateOf(original?.widget != null) }
    var searching by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val integration = Integrations.byId(widget)
    val autoIcon = slugify(integration?.name ?: name)

    // Détection automatique de l'intégration quand l'URL change.
    LaunchedEffect(url) {
        if (manual || url.length < 10) return@LaunchedEffect
        delay(800)
        val d = runCatching { Integrations.detect(app.http(), url) }.getOrNull()
        detected = d
        if (d != null && !manual) {
            widget = d.integration.id
            if (name.isBlank()) name = d.integration.name
        }
    }
    LaunchedEffect(autoIcon) { if (!iconTouched) icon = autoIcon }

    fun intervalSec(): Int = if (custom) {
        val v = customValue.toIntOrNull()?.coerceAtLeast(1) ?: 90
        (v * when (customUnit) { "min" -> 60; "h" -> 3600; else -> 1 }).coerceAtLeast(5)
    } else interval

    fun build() = (original ?: Service(name = name, url = url)).copy(
        name = name.trim(), url = url.trim(), description = description.trim(), widget = widget,
        apiKey = apiKey.trim(), username = username.trim(), password = password,
        icon = icon.trim(), monitor = monitor, pingIntervalSec = intervalSec(), showInWidget = showInWidget,
        confirmChecks = confirmChecks, critical = critical, expectText = expectText.trim(),
        maintenanceWindow = if (com.rackowl.data.parseHm(windowStart) != null && com.rackowl.data.parseHm(windowEnd) != null) "${windowStart.trim()}-${windowEnd.trim()}" else "",
        container = container, containerSource = containerSource, containerHost = containerHost,
        tileStats = if (widget == original?.widget || testStats != null) tileStats.sorted() else emptyList(),
    )

    val valid = name.isNotBlank() && (Regex("^https?://.+").matches(url.trim()) || Regex("^tcp://[^:/]+:\\d+$").matches(url.trim()))

    fun save() {
        val s = build()
        val newCat = newCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { Category(name = it) }
        app.edit {
            if (newCat != null) updateCategories { it + newCat }
            val target = newCat?.id ?: category ?: config.categories.firstOrNull()?.id
                ?: Category(name = "Services").also { cat -> updateCategories { it + cat } }.id
            saveService(s, target)
            app.monitor.refreshService(s.id)
        }
        nav.popBackStack()
    }

    fun test() {
        if (testing) return
        testing = true; testResult = null
        scope.launch {
            val s = build()
            if (s.url.startsWith("tcp://")) {
                val r = runCatching { Monitor.tcpCheck(s.url) }
                testResult = r.isSuccess to r.fold({ t("TCP port open in $it ms", "Port TCP ouvert en $it ms") }, { Monitor.describe(it) })
                testing = false
                return@launch
            }
            val ping = runCatching { app.http().get(s.url) }
            val textMissing = ping.getOrNull()?.let { s.expectText.isNotBlank() && !it.body.contains(s.expectText) } == true
            val pingMsg = ping.fold({ t("HTTP ${it.code} in ${it.latencyMs} ms", "HTTP ${it.code} en ${it.latencyMs} ms") }, { Monitor.describe(it) }) +
                if (textMissing) t(" · expected text missing", " · texte attendu absent") else ""
            val widgetResult = Integrations.byId(s.widget)?.let { i -> runCatching { i.fetch(app.http(), s) } }
            widgetResult?.getOrNull()?.let { testStats = it.stats }
            val widgetMsg = widgetResult?.fold(
                { d -> if (d.stats.isEmpty()) null else d.stats.joinToString(" · ") { "${it.value} ${it.label}" } },
                { "Widget : " + Monitor.describe(it) },
            )
            val ok = ping.getOrNull()?.code?.let { it < 500 } == true && !textMissing && widgetResult?.isFailure != true
            testResult = ok to listOfNotNull(pingMsg, widgetMsg).joinToString("\n")
            testing = false
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(HIcons.Close, t("Close", "Fermer"), { nav.popBackStack() })
            Text(if (original == null) t("New service", "Nouveau service") else t("Edit service", "Modifier le service"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            if (original != null) IconBtn(HIcons.Trash, t("Delete service", "Supprimer le service"), { confirmDelete = true }, tint = c.down, iconSize = 20.dp)
            // Toujours visible, même clavier ouvert.
            Box(
                Modifier.padding(start = 4.dp, end = 8.dp).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (valid) c.accent else c.border)
                    .clickable(enabled = valid, role = Role.Button, onClick = ::save).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t("Save", "Enregistrer"), color = if (valid) c.onAccent else c.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (confirmDelete && original != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.downBg).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(t("Delete “${original.name}”?", "Supprimer « ${original.name} » ?"), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    SecondaryButton(t("Cancel", "Annuler"), { confirmDelete = false }, height = 36.dp, radius = 9.dp, fontSize = 13)
                    SecondaryButton(t("Delete", "Supprimer"), {
                        app.edit { deleteService(original.id) }
                        nav.popBackStack(com.rackowl.ui.Routes.DASHBOARD, inclusive = false)
                    }, height = 36.dp, radius = 9.dp, fontSize = 13, border = c.down)
                }
            }

            if (container == null) {
                SecondaryButton(
                    t("Pick a container (Docker, Komodo)", "Choisir un conteneur (Docker, Komodo)"), { picking = true }, Modifier.fillMaxWidth(),
                    icon = HIcons.Docker, height = 44.dp, radius = 12.dp, fontSize = 14,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = t("Change container", "Changer de conteneur")) { picking = true }.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(HIcons.Docker, null, Modifier.size(18.dp), tint = c.textSecondary)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(t("Container", "Conteneur"), fontSize = 12.sp, color = c.muted)
                        Text(container.orEmpty(), style = mono(13, c.text))
                    }
                    IconBtn(HIcons.Close, t("Unlink container", "Dissocier le conteneur"), { container = null; containerSource = null; containerHost = null }, iconSize = 18.dp, tint = c.muted)
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { ServiceIcon(icon, name.ifBlank { "?" }, 34.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel(t("Name", "Nom"))
                    HField(name, { name = it }, placeholder = "Sonarr")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("URL")
                HField(url, { url = it; testResult = null }, mono = true, keyboardType = KeyboardType.Uri, placeholder = "http://192.168.1.20:8989")
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Description")
                HField(description, { description = it }, placeholder = t("Series", "Séries"))
            }

            // ---- Widget d'informations (intégration)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(t("Info widget", "Widget d'informations"))
                if (searching == null) {
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.surface)
                            .border(1.dp, c.border, RoundedCornerShape(12.dp)).clickable(role = Role.DropdownList) { searching = "" }
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (integration != null) ServiceIcon(integration.icon, integration.name, 20.dp)
                        Text(integration?.name ?: t("No widget (ping only)", "Aucun widget (ping seul)"), fontSize = 15.sp, modifier = Modifier.weight(1f), color = if (integration == null) c.muted else c.text)
                        if (integration != null && detected?.integration?.id == widget) {
                            Row(
                                Modifier.height(24.dp).clip(RoundedCornerShape(50)).background(c.accentBg).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(HIcons.Check, null, Modifier.size(12.dp), tint = c.accent)
                                Text(t("Detected", "Détecté"), fontSize = 11.sp, color = c.accent, fontWeight = FontWeight.Medium)
                            }
                        }
                        Icon(HIcons.ChevronDown, null, Modifier.size(18.dp), tint = c.muted)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val d = detected
                        Text(
                            when {
                                widget == null -> t("Ping monitoring only.", "Surveillance par ping uniquement.")
                                d != null && d.integration.id == widget -> d.how
                                manual -> t("Picked manually.", "Choisi manuellement.")
                                else -> ""
                            },
                            fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f),
                        )
                        if (d != null && widget != d.integration.id) {
                            Text(
                                t("Back to ${d.integration.name}", "Revenir à ${d.integration.name}"), fontSize = 12.sp, color = c.accent,
                                modifier = Modifier.clickable(role = Role.Button) { widget = d.integration.id; manual = false }.padding(vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    HField(
                        searching.orEmpty(), { searching = it }, placeholder = t("Search ${Integrations.catalog.size} integrations", "Rechercher parmi ${Integrations.catalog.size} intégrations"),
                        borderColor = c.accent,
                        leading = { Icon(HIcons.Search, null, Modifier.size(18.dp), tint = c.muted) },
                        trailing = { IconBtn(HIcons.Close, t("Close search", "Fermer la recherche"), { searching = null }, size = 32.dp, iconSize = 16.dp, tint = c.muted) },
                    )
                    HCard(Modifier.fillMaxWidth(), radius = 12.dp) {
                        val results = Integrations.search(searching.orEmpty()).sortedBy { it.name.lowercase() }.let { r ->
                            val det = detected?.integration
                            if (searching.isNullOrBlank() && det != null) listOf(det) + r.filter { it != det } else r
                        }
                        // Liste complète (plus de 160 widgets), défilante dans sa propre zone.
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                            item {
                                IntegrationOption("", t("No widget", "Aucun widget"), t("Ping only", "Ping seul"), widget == null, false) { widget = null; manual = true; searching = null }
                            }
                            items(results, key = { it.id }) {
                                Divider()
                                IntegrationOption(it.icon, it.name, if (it == detected?.integration) t("Detected", "Détecté") else it.tag, it.id == widget, it == detected?.integration) {
                                    widget = it.id; manual = true; searching = null
                                }
                            }
                        }
                        Divider()
                        Text(
                            t("${results.size} widgets", "${results.size} widgets"),
                            fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            if (integration != null && integration.auth != Auth.NONE) {
                if (integration.auth == Auth.KEY || integration.auth == Auth.KEY_OR_LOGIN) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel("${integration.keyText} ${integration.name}")
                        HField(apiKey, { apiKey = it }, password = true, placeholder = "••••••••")
                    }
                }
                if (integration.auth == Auth.KEY_SECRET) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(integration.keyText); HField(apiKey, { apiKey = it }, mono = true)
                        FieldLabel(t("API secret", "Secret API")); HField(password, { password = it }, password = true)
                    }
                }
                if (integration.auth == Auth.LOGIN || integration.auth == Auth.KEY_OR_LOGIN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel(t("Username", "Utilisateur")); HField(username, { username = it })
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel(t("Password", "Mot de passe")); HField(password, { password = it }, password = true)
                        }
                    }
                }
            }

            if (integration != null) {
                val available = testStats ?: original?.let { o ->
                    if (o.widget == widget) app.store.runtime.value.statuses[o.id]?.stats else null
                }.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(t("Shown on the tile", "Affiché sur la tuile"))
                    if (available.isEmpty()) {
                        Text(
                            t("Test the widget to pick which info appears on the dashboard.", "Teste le widget pour choisir les infos affichées sur le tableau de bord."),
                            fontSize = 12.sp, color = c.muted,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            available.forEachIndexed { i, st ->
                                Chip("${st.label} · ${st.value}", i in tileStats, { tileStats = if (i in tileStats) tileStats - i else tileStats + i })
                            }
                        }
                        Text(
                            if (tileStats.isEmpty()) t("Nothing picked: the tile shows the description.", "Rien de coché : la tuile affiche la description.")
                            else t("Up to 3 on a grid tile, 4 in list view, 1 in compact view.", "Jusqu’à 3 sur une tuile en grille, 4 en liste, 1 en compact."),
                            fontSize = 12.sp, color = c.muted,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(t("selfh.st icon", "Icône selfh.st"))
                HField(
                    icon, { icon = it; iconTouched = true }, mono = true,
                    leading = { Text("sh-", style = mono(13, c.muted)) },
                    trailing = {
                        if (icon == autoIcon) Text("Auto", fontSize = 11.sp, color = c.accent, modifier = Modifier.padding(end = 6.dp))
                        else Text("Auto", fontSize = 11.sp, color = c.muted, modifier = Modifier.clickable { icon = autoIcon; iconTouched = false }.padding(6.dp))
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(t("Category", "Catégorie"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.categories.forEach { cat -> Chip(cat.name, cat.id == category, { category = cat.id; newCategory = null }) }
                    if (newCategory == null) Chip(t("+ New", "+ Nouvelle"), false, { newCategory = "" }, dashed = true)
                }
                if (newCategory != null) {
                    HField(newCategory.orEmpty(), { newCategory = it }, placeholder = t("New category name", "Nom de la nouvelle catégorie"), borderColor = c.accent)
                }
            }

            HCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    t("Monitor availability", "Surveiller la disponibilité"),
                    if (monitor) t("Ping every ${fmtInterval(intervalSec())}, alert when offline", "Ping toutes les ${fmtInterval(intervalSec())}, alerte si hors ligne") else t("No ping, no alert", "Aucun ping ni alerte"),
                ) { HSwitch(monitor, { monitor = it }, t("Monitor availability", "Surveiller la disponibilité")) }
                if (monitor) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Segmented(
                            INTERVALS.map { it to fmtInterval(it) } + (-1 to t("Custom", "Perso")),
                            if (custom) -1 else interval,
                            { v -> if (v == -1) custom = true else { custom = false; interval = v } },
                        )
                        if (custom) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(t("Check every", "Vérifier toutes les"), fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
                                HField(customValue, { customValue = it.filter(Char::isDigit).take(4) }, Modifier.width(72.dp), mono = true, keyboardType = KeyboardType.Number, bg = c.field, height = 40.dp)
                                Segmented(listOf("s" to "s", "min" to "min", "h" to "h"), customUnit, { customUnit = it }, Modifier.width(150.dp))
                            }
                        }
                    }
                }
                Divider()
                SettingRow(t("Show in the Android widget", "Afficher dans le widget Android"), t("Phone home screen", "Écran d’accueil du téléphone")) {
                    HSwitch(showInWidget, { showInWidget = it }, t("Show in the Android widget", "Afficher dans le widget Android"))
                }
            }

            // ---- Alertes
            if (monitor) HCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Alert after", "Alerter après"), fontSize = 14.sp)
                    Segmented(
                        listOf(1 to t("1 check", "1 échec"), 2 to "2", 3 to "3", 5 to "5"), confirmChecks, { confirmChecks = it },
                    )
                    Text(
                        t("Failed checks in a row before reporting the service offline. Avoids alerts for a short blip.",
                            "Vérifications ratées d’affilée avant de déclarer le service hors ligne. Évite les alertes pour une coupure brève."),
                        fontSize = 12.sp, color = c.muted,
                    )
                }
                Divider()
                SettingRow(t("Critical", "Critique"), t("Alerts ring even during quiet hours", "Les alertes sonnent même pendant les heures calmes")) {
                    HSwitch(critical, { critical = it }, t("Critical", "Critique"))
                }
                Divider()
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("Daily maintenance", "Maintenance quotidienne"), fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HField(windowStart, { windowStart = it.take(5) }, Modifier.weight(1f), mono = true, bg = c.field, placeholder = "02:00", height = 40.dp)
                        Text("→", color = c.muted)
                        HField(windowEnd, { windowEnd = it.take(5) }, Modifier.weight(1f), mono = true, bg = c.field, placeholder = "04:00", height = 40.dp)
                    }
                    Text(t("No alerts during this window, every day (e.g. nightly backups).", "Aucune alerte dans cette plage, chaque jour (ex. sauvegardes nocturnes)."), fontSize = 12.sp, color = c.muted)
                }
                if (!url.trim().startsWith("tcp://")) {
                    Divider()
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("Expected text", "Texte attendu"), fontSize = 14.sp)
                        HField(expectText, { expectText = it }, bg = c.field, placeholder = t("Optional, e.g. Welcome", "Optionnel, ex. Bienvenue"), height = 40.dp)
                        Text(
                            t("The page must contain this text to count as online. Use tcp://host:port as the URL to check a port instead.",
                                "La page doit contenir ce texte pour être considérée en ligne. Mets tcp://hôte:port comme URL pour vérifier un port à la place."),
                            fontSize = 12.sp, color = c.muted,
                        )
                    }
                }
            }

            SecondaryButton(
                if (testing) t("Testing…", "Test…") else t("Test", "Tester"), ::test, Modifier.fillMaxWidth(),
                icon = HIcons.Activity, height = 44.dp, radius = 12.dp, fontSize = 14, enabled = valid,
            )
            testResult?.let { (ok, msg) ->
                Text(msg, fontSize = 13.sp, color = if (ok) c.up else c.down, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
        }

    }

    if (picking) ContainerPicker(onDismiss = { picking = false }) { picked, host, ip, scheme ->
        picking = false
        val svc = Containers.toService(picked, host, ip, scheme, build())
        name = svc.name; url = svc.url; description = svc.description
        icon = svc.icon; iconTouched = true
        widget = svc.widget; manual = svc.widget != null
        apiKey = svc.apiKey; container = svc.container; containerSource = svc.containerSource; containerHost = svc.containerHost
        testResult = null
    }
}

@Composable
private fun IntegrationOption(icon: String, name: String, tag: String, selected: Boolean, detected: Boolean, onClick: () -> Unit) {
    val c = Home.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(if (selected) c.selected else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ServiceIcon(icon, name, 20.dp)
        Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(tag, fontSize = 12.sp, color = if (detected) c.accent else c.muted)
    }
}
