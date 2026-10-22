package com.rackowl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rackowl.RackowlApp
import com.rackowl.ui.components.Divider
import com.rackowl.ui.components.HIcons
import com.rackowl.ui.screens.AlertsScreen
import com.rackowl.ui.screens.DashboardScreen
import com.rackowl.ui.screens.DetailScreen
import com.rackowl.ui.screens.EditServiceScreen
import com.rackowl.ui.screens.LogsScreen
import com.rackowl.ui.screens.SettingsScreen
import com.rackowl.ui.theme.Home

val LocalApp = staticCompositionLocalOf<RackowlApp> { error("RackowlApp non fourni") }

object Routes {
    const val DASHBOARD = "dashboard"
    const val ALERTS = "alerts"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
    fun detail(id: String) = "detail/$id"
    fun edit(id: String? = null, category: String? = null) = "edit?id=${id.orEmpty()}&cat=${category.orEmpty()}"
    fun logs(container: String) = "logs?c=$container"
}

@Composable
fun AppNav(app: RackowlApp, requestedRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            nav.navigate(requestedRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalApp provides app) {
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route.orEmpty()
        val showBar = route.substringBefore('?') in setOf(Routes.DASHBOARD, Routes.ALERTS, Routes.LOGS, Routes.SETTINGS)
        val rail = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
        val navHost: @Composable (Modifier) -> Unit = { m -> Box(m) {
                NavHost(nav, startDestination = Routes.DASHBOARD) {
                    composable(Routes.DASHBOARD) { DashboardScreen(nav) }
                    composable(Routes.ALERTS) { AlertsScreen(nav) }
                    composable(
                        "logs?c={c}",
                        arguments = listOf(navArgument("c") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    ) { LogsScreen(it.arguments?.getString("c")) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                    composable("detail/{id}") { DetailScreen(nav, it.arguments?.getString("id").orEmpty()) }
                    composable(
                        "edit?id={id}&cat={cat}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType; defaultValue = "" },
                            navArgument("cat") { type = NavType.StringType; defaultValue = "" },
                        ),
                    ) {
                        EditServiceScreen(
                            nav,
                            it.arguments?.getString("id").orEmpty().ifBlank { null },
                            it.arguments?.getString("cat").orEmpty().ifBlank { null },
                        )
                    }
                }
            }
        }
        if (rail) Row(Modifier.fillMaxSize().background(Home.colors.bg)) {
            if (showBar) MainNav(nav, route.substringBefore('?'), rail = true)
            navHost(Modifier.weight(1f))
        } else Column(Modifier.fillMaxSize().background(Home.colors.bg)) {
            navHost(Modifier.weight(1f))
            if (showBar) MainNav(nav, route.substringBefore('?'), rail = false)
        }
    }
}

/** Navigation principale : barre du bas sur téléphone, barre latérale sur écran large (≥ 600 dp). */
@Composable
private fun MainNav(nav: NavHostController, current: String, rail: Boolean) {
    val app = LocalApp.current
    val runtime by app.store.runtime.state.collectAsStateWithLifecycle()
    val config by app.store.config.state.collectAsStateWithLifecycle()
    val services = config.categories.flatMap { it.services }
    val ids = services.map { it.id }.toSet()
    val incidents = com.rackowl.net.Lan.visibleStatuses(runtime, services, config.settings.pingEnabled).values.count { it.up == false && it.id in ids }
    val c = Home.colors
    val items: @Composable (Modifier) -> Unit = { m ->
        NavItem(m, HIcons.Grid, t("Dashboard", "Tableau"), current == Routes.DASHBOARD) { nav.go(Routes.DASHBOARD) }
        NavItem(m, HIcons.Bell, t("Alerts", "Alertes"), current == Routes.ALERTS, badge = incidents) { nav.go(Routes.ALERTS) }
        NavItem(m, HIcons.Terminal, "Logs", current == "logs") { nav.go("logs") }
        NavItem(m, HIcons.Sliders, t("Settings", "Réglages"), current == Routes.SETTINGS) { nav.go(Routes.SETTINGS) }
    }
    if (rail) {
        Row(Modifier.fillMaxHeight().background(c.nav).windowInsetsPadding(WindowInsets.systemBars)) {
            Column(Modifier.width(88.dp).fillMaxHeight().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Modifier.fillMaxWidth())
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
        }
    } else {
        Column(Modifier.background(c.nav).windowInsetsPadding(WindowInsets.navigationBars)) {
            Divider()
            Row(Modifier.fillMaxWidth().height(72.dp)) { items(Modifier.weight(1f)) }
        }
    }
}

private fun NavHostController.go(route: String) = navigate(route) {
    popUpTo(Routes.DASHBOARD) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun NavItem(
    modifier: Modifier, icon: ImageVector, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit,
) {
    val c = Home.colors
    val color = if (selected) c.accent else c.muted
    Column(
        modifier
            .height(72.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { if (badge > 0) contentDescription = t("$label, $badge ongoing incident${if (badge > 1) "s" else ""}", "$label, $badge incident${if (badge > 1) "s" else ""} en cours") },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box {
            Icon(icon, null, Modifier.size(22.dp), tint = color)
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.down)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge.toString(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = c.bg)
                }
            }
        }
        Text(label, fontSize = 12.sp, color = color)
    }
}
