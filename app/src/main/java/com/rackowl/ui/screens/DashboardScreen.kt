package com.rackowl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.animateDpAsState
import com.rackowl.ui.components.levelColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import com.rackowl.ui.t
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rackowl.data.Category
import com.rackowl.data.Service
import com.rackowl.data.ServiceStatus
import com.rackowl.data.SourceType
import com.rackowl.net.Lan
import com.rackowl.data.allServices
import com.rackowl.data.fmtDec
import com.rackowl.data.fmtPercent
import com.rackowl.ui.LocalApp
import com.rackowl.ui.Routes
import com.rackowl.ui.components.HCard
import com.rackowl.ui.components.HField
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.components.IconBtn
import com.rackowl.ui.components.ProgressBar
import com.rackowl.ui.components.Segmented
import com.rackowl.ui.components.SectionTitle
import com.rackowl.ui.components.ServiceIcon
import com.rackowl.ui.components.StatusDot
import com.rackowl.ui.edit
import com.rackowl.ui.openUrl
import com.rackowl.ui.swap
import com.rackowl.ui.theme.Home
import com.rackowl.ui.theme.mono
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(nav: NavHostController) {
    val app = LocalApp.current
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val runtime by app.store.runtime.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    /** Catégorie à afficher dès qu'elle apparaît (après création). */
    var pendingTab by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val c = Home.colors

    val cats = config.categories
    val listed = if (editing) cats else cats.filter { !it.hidden }
    val pager = rememberPagerState { listed.size.coerceAtLeast(1) }
    val active = listed.getOrNull(pager.currentPage) ?: listed.firstOrNull()
    LaunchedEffect(pendingTab, listed.size) {
        val idx = listed.indexOfFirst { it.id == pendingTab }
        if (idx >= 0) { pager.scrollToPage(idx); pendingTab = null }
    }
    val all = config.allServices()
    val statuses = Lan.visibleStatuses(runtime, all, config.settings.pingEnabled)
    val online = all.count { statuses[it.id]?.up == true }
    val settings = config.settings

    // Écran large (≥ 840 dp) : le détail s'ouvre dans un panneau à droite.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxSize()) {
    androidx.compose.runtime.CompositionLocalProvider(LocalOpenDetail provides if (wide) ({ id: String -> selected = id }) else null) {
    Column(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars)) {
        // ---- En-tête
        when {
            editing -> Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t("Edit dashboard", "Modifier l'accueil"), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(t("Arrows to reorder, eye to hide", "Flèches pour réordonner, œil pour masquer"), fontSize = 13.sp, color = c.muted)
                }
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.accent)
                        .clickable(role = Role.Button) { editing = false }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t("Done", "Terminé"), color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
            query != null -> Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HField(
                    query.orEmpty(), { query = it }, Modifier.weight(1f), placeholder = t("Search a service", "Rechercher un service"),
                    leading = { Icon(HIcons.Search, null, Modifier.size(18.dp), tint = c.muted) },
                    borderColor = c.accent,
                )
                IconBtn(HIcons.Close, t("Close search", "Fermer la recherche"), { query = null })
            }
            else -> Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.foundation.Image(
                            androidx.compose.ui.res.painterResource(com.rackowl.R.drawable.ic_logo), null, Modifier.size(30.dp),
                        )
                        Text("Rackowl", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
                    }
                    // Pings coupés : pas de ligne d'état sous le titre.
                    if (config.settings.pingEnabled) Row(verticalAlignment = Alignment.CenterVertically) {
                        if (runtime.offLan) {
                            Text("● ", fontSize = 13.sp, color = c.warn)
                            Text(t("Off local network · connect your VPN", "Hors réseau local · connecte le VPN"), fontSize = 13.sp, color = c.warn)
                        } else {
                            Text("● ", fontSize = 13.sp, color = if (online == all.size) c.accent else c.down)
                            Text(t("$online / ${all.size} services online", "$online / ${all.size} services en ligne"), fontSize = 13.sp, color = c.muted)
                        }
                    }
                }
                IconBtn(HIcons.Search, t("Search", "Rechercher"), { query = "" })
                IconBtn(HIcons.Edit, t("Edit dashboard", "Modifier l'accueil"), { editing = true }, iconSize = 20.dp)
                IconBtn(HIcons.Plus, t("Add a service", "Ajouter un service"), { nav.navigate(Routes.edit(category = active?.id)) })
            }
        }

        androidx.compose.runtime.CompositionLocalProvider(LocalShowStatus provides config.settings.pingEnabled) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch { runCatching { app.monitor.tick(force = true) }; refreshing = false }
            },
            modifier = Modifier.weight(1f),
        ) {
            val resources: @Composable () -> Unit = {
                if (!editing && settings.showResources) settings.sourcesOf(SourceType.GLANCES).forEach { src ->
                    ResourcesCard(src.name, runtime.resourcesBySource[src.id] ?: com.rackowl.data.Resources(), settings.diskWarnPercent)
                }
            }
            when {
                query != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SearchResults(nav, all, statuses, query.orEmpty())
                }
                cats.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    resources()
                    EmptyDashboard(nav)
                }
                // Une page par catégorie, qu'on fait glisser du doigt ; titre et points de page en haut.
                else -> Column(Modifier.fillMaxSize()) {
                    CategoryHeader(
                        listed, pager.currentPage, editing, config.settings.categoryNav,
                        // Catégories ayant un service hors ligne : repère rouge dans la navigation.
                        listed.filter { cat -> cat.services.any { !it.hidden && statuses[it.id]?.up == false } }.map { it.id }.toSet(),
                        onPick = { i -> scope.launch { pager.animateScrollToPage(i) } },
                        onAdd = {
                            val cat = Category(name = t("New category", "Nouvelle catégorie"))
                            app.edit { updateCategories { it + cat } }
                            pendingTab = cat.id; editing = true
                        },
                    )
                    HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top, key = { listed.getOrNull(it)?.id ?: it }) { page ->
                        val cat = listed.getOrNull(page) ?: return@HorizontalPager
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Spacer(Modifier.height(8.dp))
                            resources()
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (editing) EditCategory(nav, cat, cats, settings.showResources, onDeleted = { scope.launch { pager.scrollToPage(0) } })
                                else {
                                    val shown = cat.services.filter { !it.hidden }
                                    if (shown.isEmpty()) EmptyCategory(nav, cat.id)
                                    else ServiceGrid(nav, shown, statuses, cat.cols)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
    }
    if (wide && selected != null && all.any { it.id == selected }) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
        Box(Modifier.width(420.dp).fillMaxHeight()) { DetailScreen(nav, selected!!, onClose = { selected = null }) }
    }
    }
}

@Composable
private fun ResourcesCard(name: String, r: com.rackowl.data.Resources, warnAt: Int) {
    val c = Home.colors
    HCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).fillMaxWidth(), radius = 16.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val meta = listOfNotNull(r.uptime?.let { "Uptime $it" }, r.tempC?.let { fmtDec(it, 0) + " °C" }).joinToString(" · ")
                Text(if (r.error != null && r.cpu == null) r.error else meta, fontSize = 12.sp, color = c.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("CPU" to r.cpu, "RAM" to r.mem, t("Disk", "Disque") to r.disk).forEach { (label, v) ->
                    val warn = label == t("Disk", "Disque") && (v ?: 0.0) >= warnAt || label != t("Disk", "Disque") && (v ?: 0.0) >= 90
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(label, fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f))
                            Text(fmtPercent(v), style = mono(12, c.text))
                        }
                        ProgressBar(((v ?: 0.0) / 100).toFloat(), if (warn) c.warn else c.accent)
                    }
                }
            }
        }
    }
}

/**
 * Navigation entre catégories, selon le style choisi dans les réglages :
 * titre + points, puces, onglets soulignés ou titre à menu. Le glissement entre pages marche dans tous les cas.
 */
@Composable
private fun CategoryHeader(
    listed: List<Category>, current: Int, editing: Boolean, style: com.rackowl.data.CategoryNav,
    alerts: Set<String>, onPick: (Int) -> Unit, onAdd: () -> Unit,
) {
    val c = Home.colors
    val add: @Composable (Modifier) -> Unit = { m -> if (editing) IconBtn(HIcons.Plus, t("New category", "Nouvelle catégorie"), onAdd, m, tint = c.muted, iconSize = 18.dp) }
    fun name(cat: Category) = cat.name.ifBlank { t("Untitled", "Sans nom") }
    val alertDot: @Composable (Category) -> Unit = { cat ->
        if (cat.id in alerts) Box(Modifier.padding(start = 6.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(c.down))
    }
    when (style) {
        com.rackowl.data.CategoryNav.DOTS -> Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val cat = listed.getOrNull(current)
            Column(
                Modifier.align(Alignment.Center).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (cat?.hidden == true) 0.55f else 1f)) {
                    Text(cat?.let(::name).orEmpty(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cat?.hidden == true && editing) {
                        Spacer(Modifier.width(6.dp))
                        Icon(HIcons.EyeOff, t("hidden", "masquée"), Modifier.size(16.dp), tint = c.muted)
                    }
                }
                if (listed.size > 1) Row(
                    Modifier.semantics { contentDescription = t("Page ${current + 1} of ${listed.size}", "Page ${current + 1} sur ${listed.size}") },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listed.forEachIndexed { i, cat ->
                        val on = i == current
                        val w by animateDpAsState(if (on) 18.dp else 6.dp, label = "dot")
                        val col by animateColorAsState(if (on) c.accent else if (cat.id in alerts) c.down else c.trackOff, label = "dotColor")
                        // Zone tactile plus large que le point lui-même.
                        Box(
                            Modifier.height(20.dp).clickable(onClickLabel = name(cat)) { onPick(i) }.padding(horizontal = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(w, 6.dp).clip(RoundedCornerShape(3.dp)).background(col)) }
                    }
                }
            }
            add(Modifier.align(Alignment.CenterEnd))
        }

        com.rackowl.data.CategoryNav.PILLS -> {
            val rowState = rememberLazyListState()
            LaunchedEffect(current) { if (current in listed.indices) rowState.animateScrollToItem((current - 1).coerceAtLeast(0)) }
            LazyRow(
                Modifier.fillMaxWidth().padding(vertical = 4.dp), state = rowState,
                contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(listed, key = { _, cat -> cat.id }) { i, cat ->
                    val on = i == current
                    val bg by animateColorAsState(if (on) c.accent else c.surface, label = "pill")
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(bg)
                            .border(1.dp, if (on) c.accent else c.border, RoundedCornerShape(50))
                            .clickable(role = Role.Tab) { onPick(i) }.padding(horizontal = 14.dp).alpha(if (cat.hidden) 0.55f else 1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name(cat), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (on) c.onAccent else c.textSecondary, maxLines = 1)
                        Text(
                            "  ${cat.services.count { !it.hidden }}",
                            style = mono(11, if (on) c.onAccent.copy(alpha = 0.7f) else c.muted),
                        )
                        alertDot(cat)
                    }
                }
                item { add(Modifier) }
            }
        }

        com.rackowl.data.CategoryNav.TABS -> {
            val rowState = rememberLazyListState()
            LaunchedEffect(current) { if (current in listed.indices) rowState.animateScrollToItem((current - 1).coerceAtLeast(0)) }
            Column {
                LazyRow(Modifier.fillMaxWidth(), state = rowState, contentPadding = PaddingValues(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    itemsIndexed(listed, key = { _, cat -> cat.id }) { i, cat ->
                        val on = i == current
                        val bar by animateColorAsState(if (on) c.accent else Color.Transparent, label = "tab")
                        Column(Modifier.height(44.dp).clickable(role = Role.Tab) { onPick(i) }.alpha(if (cat.hidden) 0.55f else 1f), verticalArrangement = Arrangement.Bottom) {
                            Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name(cat), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (on) c.text else c.muted)
                                alertDot(cat)
                            }
                            Box(Modifier.fillMaxWidth().height(2.dp).background(bar))
                        }
                    }
                    item { add(Modifier) }
                }
                com.rackowl.ui.components.Divider()
            }
        }

        com.rackowl.data.CategoryNav.MENU -> Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)) {
            var open by remember { mutableStateOf(false) }
            val cat = listed.getOrNull(current)
            Row(
                Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(10.dp)).clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(cat?.let(::name).orEmpty(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Icon(HIcons.ChevronDown, null, Modifier.size(18.dp), tint = c.muted)
                androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = c.surface) {
                    listed.forEachIndexed { i, item ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name(item), fontSize = 15.sp, color = if (i == current) c.accent else c.text, modifier = Modifier.weight(1f, fill = false))
                                    alertDot(item)
                                    Text("   ${item.services.count { !it.hidden }}", style = mono(12, c.muted))
                                }
                            },
                            onClick = { open = false; onPick(i) },
                        )
                    }
                }
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (listed.size > 1) Text("${current + 1} / ${listed.size}", style = mono(12, c.muted), modifier = Modifier.padding(end = 6.dp))
                add(Modifier)
            }
        }
    }
}

/** Ouvre le détail dans le panneau latéral (écran large) ; null = navigation classique. */
private val LocalOpenDetail = androidx.compose.runtime.compositionLocalOf<((String) -> Unit)?> { null }

@Composable
private fun detailOpener(nav: NavHostController): (String) -> Unit {
    val local = LocalOpenDetail.current
    return { id -> local?.invoke(id) ?: nav.navigate(Routes.detail(id)) }
}

/** Faux quand les pings sont désactivés : les tuiles n'affichent alors pas de point d'état. */
private val LocalShowStatus = androidx.compose.runtime.compositionLocalOf { true }

/** Grille de tuiles : 1 = liste détaillée, 2 = grille, 3 = compact, 4 = icônes. */
@Composable
private fun ServiceGrid(nav: NavHostController, services: List<Service>, statuses: Map<String, ServiceStatus>, colsSetting: Int) {
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    // « Auto » : 2 colonnes sur téléphone, 3 sur petite tablette, 4 au-delà.
    val cols = if (colsSetting == 0) (if (width >= 840) 4 else if (width >= 600) 3 else 2) else colsSetting
    Column(verticalArrangement = Arrangement.spacedBy(if (cols >= 3) 8.dp else 12.dp)) {
        services.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (cols >= 3) 8.dp else 12.dp)) {
                row.forEach { s ->
                    val st = statuses[s.id]
                    val m = Modifier.weight(1f)
                    when {
                        cols == 1 -> WideTile(nav, s, st, m)
                        cols == 2 -> NormalTile(nav, s, st, m)
                        else -> CompactTile(nav, s, st, cols, m)
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Statistiques choisies pour la tuile (Service.tileStats), dans l'ordre du widget. */
private fun tileStats(s: Service, st: ServiceStatus?): List<com.rackowl.data.Stat> =
    s.tileStats.sorted().mapNotNull { st?.stats?.getOrNull(it) }

/** Petits blocs valeur + libellé, comme les widgets de Homepage. */
@Composable
private fun StatBlocks(stats: List<com.rackowl.data.Stat>, max: Int, modifier: Modifier = Modifier) {
    val c = Home.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.take(max).forEach { st ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(c.field).padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(st.value, style = mono(13, levelColor(st.level)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.label, fontSize = 10.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Ligne secondaire d'une tuile : description, sinon erreur ou latence (les infos du widget se choisissent à part). */
private fun subtitle(s: Service, st: ServiceStatus?, showStatus: Boolean): String = when {
    s.description.isNotBlank() -> s.description
    // Pings désactivés : ni erreur ni latence sous le nom.
    !showStatus -> ""
    st?.up == false -> st.error ?: t("Offline", "Hors ligne")
    st?.latencyMs != null -> "${st.latencyMs} ms"
    else -> ""
}

/** Bouton « Ouvrir » des tuiles : ouvre le site du service (la tuile elle-même mène au détail). */
@Composable
private fun InfoButton(nav: NavHostController, s: Service, modifier: Modifier, compact: Boolean = false) {
    val c = Home.colors
    val ctx = LocalContext.current
    Row(
        modifier
            .height(if (compact) 36.dp else 40.dp)
            .clip(RoundedCornerShape(if (compact) 9.dp else 10.dp))
            .background(c.field)
            .border(1.dp, c.border, RoundedCornerShape(if (compact) 9.dp else 10.dp))
            .clickable(role = Role.Button, onClickLabel = t("Open ${s.name}", "Ouvrir ${s.name}")) { ctx.openUrl(s.url) }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HIcons.External, if (compact) t("Open ${s.name}", "Ouvrir ${s.name}") else null, Modifier.size(15.dp), tint = c.textSecondary)
        if (!compact) Text(t("Open", "Ouvrir"), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun NormalTile(nav: NavHostController, s: Service, st: ServiceStatus?, modifier: Modifier) {
    val openDetail = detailOpener(nav)
    val c = Home.colors
    HCard(modifier, radius = 16.dp) {
        Box {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = t("More info on ${s.name}", "Plus d'infos sur ${s.name}")) { openDetail(s.id) }
                    .padding(start = 12.dp, end = 28.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ServiceIcon(s.icon, s.name, 32.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle(s, st, LocalShowStatus.current).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (LocalShowStatus.current) StatusDot(st?.up, 10.dp, Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 9.dp))
        }
        val picked = tileStats(s, st)
        if (picked.isNotEmpty()) StatBlocks(picked, 3, Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp))
        InfoButton(nav, s, Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp))
    }
}

@Composable
private fun WideTile(nav: NavHostController, s: Service, st: ServiceStatus?, modifier: Modifier) {
    val openDetail = detailOpener(nav)
    val c = Home.colors
    HCard(modifier, radius = 16.dp) {
        Box {
            Row(Modifier.fillMaxWidth().padding(end = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable(onClickLabel = t("More info on ${s.name}", "Plus d'infos sur ${s.name}")) { openDetail(s.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ServiceIcon(s.icon, s.name, 36.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle(s, st, LocalShowStatus.current).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                InfoButton(nav, s, Modifier)
            }
            if (LocalShowStatus.current) StatusDot(st?.up, 8.dp, Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 6.dp))
        }
        val picked = tileStats(s, st)
        if (picked.isNotEmpty()) StatBlocks(picked, 4, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp))
    }
}

@Composable
private fun CompactTile(nav: NavHostController, s: Service, st: ServiceStatus?, cols: Int, modifier: Modifier) {
    val openDetail = detailOpener(nav)
    HCard(modifier, radius = 14.dp) {
        Box {
            Column(
                Modifier.fillMaxWidth().clickable(onClickLabel = t("More info on ${s.name}", "Plus d'infos sur ${s.name}")) { openDetail(s.id) }
                    .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ServiceIcon(s.icon, s.name, if (cols == 3) 30.dp else 26.dp)
                Text(s.name, fontSize = if (cols == 3) 13.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Vue compacte : seulement la première info choisie.
                tileStats(s, st).firstOrNull()?.let {
                    Text(it.value, style = mono(12, levelColor(it.level)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (LocalShowStatus.current) StatusDot(st?.up, 8.dp, Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp))
        }
        InfoButton(nav, s, Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp), compact = true)
    }
}

@Composable
private fun SearchResults(nav: NavHostController, all: List<Service>, statuses: Map<String, ServiceStatus>, query: String) {
    val q = query.trim().lowercase()
    val found = all.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.url.lowercase().contains(q) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (found.isEmpty()) Text(t("No matching service.", "Aucun service ne correspond."), fontSize = 14.sp, color = Home.colors.muted, modifier = Modifier.padding(top = 24.dp))
        found.forEach { WideTile(nav, it, statuses[it.id], Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun EmptyCategory(nav: NavHostController, categoryId: String) {
    val c = Home.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("This category is empty", "Cette catégorie est vide"), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(t("Add a service or move one here from another category.", "Ajoute un service ou déplaces-en un depuis une autre catégorie."), fontSize = 13.sp, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        com.rackowl.ui.components.SecondaryButton(t("Add a service", "Ajouter un service"), { nav.navigate(Routes.edit(category = categoryId)) }, height = 44.dp, radius = 12.dp, fontSize = 14)
    }
}

@Composable
private fun EmptyDashboard(nav: NavHostController) {
    val c = Home.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("Your dashboard is empty", "Ton tableau de bord est vide"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            t("Add a service, import your Homepage config (services.yaml) or connect Docker or Komodo from Settings.", "Ajoute un service, importe ta config Homepage (services.yaml) ou branche la découverte Docker depuis les Réglages."),
            fontSize = 13.sp, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.rackowl.ui.components.SecondaryButton(t("Settings", "Réglages"), { nav.navigate(Routes.SETTINGS) }, height = 44.dp, radius = 12.dp, fontSize = 14)
            com.rackowl.ui.components.PrimaryButton(t("Add a service", "Ajouter un service"), { nav.navigate(Routes.edit()) })
        }
    }
}

// ---------------------------------------------------------------- Mode édition

@Composable
private fun EditCategory(nav: NavHostController, cat: Category, cats: List<Category>, showResources: Boolean, onDeleted: () -> Unit) {
    val app = LocalApp.current
    val c = Home.colors
    val ci = cats.indexOfFirst { it.id == cat.id }
    var confirmDelete by remember { mutableStateOf(false) }
    fun update(transform: (Category) -> Category) = app.edit { updateCategories { list -> list.map { if (it.id == cat.id) transform(it) else it } } }

    HCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Category name", "Nom de la catégorie"), fontSize = 12.sp, color = c.muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                var name by remember(cat.id) { mutableStateOf(cat.name) }
                HField(name, { name = it; update { c2 -> c2.copy(name = it) } }, Modifier.weight(1f), bg = c.field, borderColor = c.borderStrong)
                IconBtn(HIcons.ChevronLeft, t("Move category left", "Déplacer la catégorie à gauche"), { app.edit { updateCategories { it.swap(ci, ci - 1) } } }, size = 40.dp, iconSize = 20.dp, enabled = ci > 0)
                IconBtn(HIcons.ChevronRight, t("Move category right", "Déplacer la catégorie à droite"), { app.edit { updateCategories { it.swap(ci, ci + 1) } } }, size = 40.dp, iconSize = 20.dp, enabled = ci < cats.lastIndex)
                IconBtn(
                    if (cat.hidden) HIcons.EyeOff else HIcons.Eye,
                    if (cat.hidden) t("Show category", "Afficher la catégorie") else t("Hide category", "Masquer la catégorie"),
                    { update { it.copy(hidden = !it.hidden) } }, size = 40.dp, iconSize = 20.dp,
                    tint = if (cat.hidden) c.warn else c.textSecondary,
                )
                IconBtn(HIcons.Trash, t("Delete category", "Supprimer la catégorie"), { confirmDelete = true }, size = 40.dp, iconSize = 20.dp, tint = c.down, enabled = cats.size > 1)
            }
            if (confirmDelete) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.downBg).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t("Delete “${cat.name}” and its ${cat.services.size} service(s)?", "Supprimer « ${cat.name} » et ses ${cat.services.size} service(s) ?"),
                        fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    com.rackowl.ui.components.SecondaryButton(t("Cancel", "Annuler"), { confirmDelete = false }, height = 36.dp, radius = 9.dp, fontSize = 13)
                    Spacer(Modifier.width(6.dp))
                    com.rackowl.ui.components.SecondaryButton(t("Delete", "Supprimer"), {
                        confirmDelete = false
                        app.edit { updateCategories { list -> list.filterNot { it.id == cat.id } } }
                        onDeleted()
                    }, height = 36.dp, radius = 9.dp, fontSize = 13, border = c.down)
                }
            }
            com.rackowl.ui.components.Divider()
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(t("Tiles per row", "Blocs par ligne"), fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f))
                Text(
                    when (cat.cols) { 0 -> t("Fits the width", "Selon la largeur"); 1 -> t("Detailed list", "Liste détaillée"); 2 -> t("Grid", "Grille"); 3 -> "Compact"; else -> t("Icons", "Icônes") },
                    fontSize = 12.sp, color = c.muted,
                )
            }
            Segmented(
                listOf(0 to "Auto", 1 to "1", 2 to "2", 3 to "3", 4 to "4"), cat.cols, { v -> update { it.copy(cols = v) } },
                height = 38.dp, textStyle = mono(14),
            )
        }
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (showResources) 1f else 0.5f)) {
            Text(t("Server resources block", "Bloc ressources serveur"), fontSize = 14.sp)
            Text(if (showResources) t("CPU, RAM and disk above every tab", "CPU, RAM, disque en haut de chaque onglet") else t("Hidden from dashboard", "Masqué de l’accueil"), fontSize = 12.sp, color = if (showResources) c.muted else c.warn)
        }
        IconBtn(
            if (showResources) HIcons.Eye else HIcons.EyeOff,
            if (showResources) t("Hide resources block", "Masquer le bloc ressources") else t("Show resources block", "Afficher le bloc ressources"),
            { app.edit { updateSettings { it.copy(showResources = !it.showResources) } } },
            iconSize = 20.dp, tint = if (showResources) c.textSecondary else c.warn,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Services · ${cat.name}")
        // Glisser-déposer : appui long sur une ligne, puis glisser ; l'ordre est enregistré au relâchement.
        var dragOrder by remember(cat.id) { mutableStateOf<List<String>?>(null) }
        var dragId by remember(cat.id) { mutableStateOf<String?>(null) }
        var dragOffset by remember(cat.id) { mutableStateOf(0f) }
        val rowHeights = remember(cat.id) { mutableMapOf<String, Int>() }
        val spacing = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
        val byId = cat.services.associateBy { it.id }
        val ordered = (dragOrder ?: cat.services.map { it.id }).mapNotNull { byId[it] }
        ordered.forEachIndexed { i, s -> androidx.compose.runtime.key(s.id) {
            val dragging = dragId == s.id
            Row(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { rowHeights[s.id] = it.size.height }
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        shadowElevation = if (dragging) 12f else 0f
                        scaleX = if (dragging) 1.02f else 1f; scaleY = scaleX
                    }
                    .pointerInput(s.id, cat.services) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragOrder = cat.services.map { it.id }; dragId = s.id; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val order = dragOrder?.toMutableList() ?: return@detectDragGesturesAfterLongPress
                                val idx = order.indexOf(s.id)
                                val next = order.getOrNull(idx + 1)?.let { rowHeights[it] }
                                val prev = order.getOrNull(idx - 1)?.let { rowHeights[it] }
                                // Échange avec le voisin quand on dépasse sa moitié.
                                if (next != null && dragOffset > (next + spacing) / 2) {
                                    order.swapInPlace(idx, idx + 1); dragOffset -= next + spacing; dragOrder = order
                                } else if (prev != null && dragOffset < -(prev + spacing) / 2) {
                                    order.swapInPlace(idx, idx - 1); dragOffset += prev + spacing; dragOrder = order
                                }
                            },
                            onDragEnd = {
                                val order = dragOrder
                                if (order != null && order != cat.services.map { it.id }) app.edit {
                                    updateCategories { list -> list.map { cc -> if (cc.id == cat.id) cc.copy(services = order.mapNotNull { id -> cc.services.firstOrNull { it.id == id } }) else cc } }
                                }
                                dragId = null; dragOffset = 0f; dragOrder = null
                            },
                            onDragCancel = { dragId = null; dragOffset = 0f; dragOrder = null },
                        )
                    }
                    .clip(RoundedCornerShape(12.dp)).background(if (dragging) c.selected else c.surface)
                    .border(1.dp, if (dragging) c.accent else c.border, RoundedCornerShape(12.dp))
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(HIcons.Drag, null, Modifier.size(16.dp), tint = c.handle)
                Row(
                    Modifier.weight(1f).clickable(onClickLabel = t("Edit ${s.name}", "Modifier ${s.name}")) { nav.navigate(Routes.edit(s.id)) }.alpha(if (s.hidden) 0.5f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ServiceIcon(s.icon, s.name, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (s.hidden) t("Hidden from dashboard", "Masqué de l’accueil") else s.description.ifBlank { s.url }, fontSize = 12.sp, color = if (s.hidden) c.warn else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                fun move(to: Int) = app.edit { updateCategories { list -> list.map { if (it.id == cat.id) it.copy(services = it.services.swap(i, to)) else it } } }
                IconBtn(HIcons.ChevronUp, t("Move ${s.name} up", "Monter ${s.name}"), { move(i - 1) }, size = 40.dp, iconSize = 20.dp, enabled = i > 0)
                IconBtn(HIcons.ChevronDown, t("Move ${s.name} down", "Descendre ${s.name}"), { move(i + 1) }, size = 40.dp, iconSize = 20.dp, enabled = i < cat.services.lastIndex)
                IconBtn(
                    if (s.hidden) HIcons.EyeOff else HIcons.Eye, (if (s.hidden) t("Show ", "Afficher ") else t("Hide ", "Masquer ")) + s.name,
                    { app.edit { updateCategories { list -> list.map { cc -> if (cc.id == cat.id) cc.copy(services = cc.services.map { if (it.id == s.id) it.copy(hidden = !it.hidden) else it }) else cc } } } },
                    size = 40.dp, iconSize = 20.dp, tint = if (s.hidden) c.warn else c.textSecondary,
                )
            }
        } }
        Text(t("Long-press a row to drag it.", "Appui long sur une ligne pour la déplacer."), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
        DashedButton(t("Add a service here", "Ajouter un service ici")) { nav.navigate(Routes.edit(category = cat.id)) }
    }
}

@Composable
fun DashedButton(text: String, onClick: () -> Unit) {
    val c = Home.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .drawDashedBorder(c.trackOff, 12.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HIcons.Plus, null, Modifier.size(16.dp), tint = c.textSecondary)
        Text(text, fontSize = 14.sp, color = c.textSecondary)
    }
}

fun Modifier.drawDashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.drawBehind {
        drawRoundRect(
            color = color,
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        )
    },
)

private fun <T> MutableList<T>.swapInPlace(i: Int, j: Int) { val tmp = this[i]; this[i] = this[j]; this[j] = tmp }
