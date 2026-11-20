package app.pausa.ui

import androidx.annotation.StringRes
import app.pausa.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import app.pausa.ui.theme.LocalDarkTheme
import app.pausa.ui.common.glassSurface
import app.pausa.ui.common.GlassStage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pausa.BuildConfig
import app.pausa.ui.apps.AppsScreen
import app.pausa.ui.apps.EditRuleScreen
import app.pausa.ui.apps.PickAppScreen
import app.pausa.ui.dashboard.DashboardScreen
import app.pausa.ui.lab.LabScreen
import app.pausa.ui.onboarding.OnboardingScreen
import app.pausa.ui.settings.ExceptionsScreen
import app.pausa.ui.settings.SettingsScreen
import app.pausa.ui.stats.StatsScreen

/** The screens reachable from the main navigation. Everything else (picker, editor, exceptions) is a step inside one. */
private enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.ui_nav_home, Icons.Default.Home),
    Apps("apps", R.string.ui_nav_apps, Icons.Default.Menu),
    Stats("stats", R.string.ui_nav_stats, Icons.Default.Info),
    Settings("settings", R.string.ui_nav_settings, Icons.Default.Settings),
    Lab("lab", R.string.ui_nav_lab, Icons.Default.Build),
}

private object Routes {
    const val PICK = "apps/pick"
    const val EDIT = "apps/edit/{ruleId}"
    const val EXCEPTIONS = "settings/exceptions"
    fun edit(ruleId: Long) = "apps/edit/$ruleId"
}

/** A line of text longer than about 75 characters is hard to read, and a settings row 1000 dp wide is hard to use. */
private val MAX_CONTENT_WIDTH = 720.dp

@Composable
fun PausaRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val onboardingDone by rootViewModel.onboardingDone.collectAsStateWithLifecycle()

    // Nothing is drawn until the setting is read: no flash of the introduction for someone who already saw it.
    if (onboardingDone != null) {
        // Everything sits on the backdrop of the current look; the screens themselves are transparent.
        GlassStage(video = true) {
            if (onboardingDone == false) OnboardingScreen(onFinish = rootViewModel::finishOnboarding) else MainNavigation()
        }
    }
}

/**
 * Whether the phone is held on its side (little height) or the screen is very wide (tablet, unfolded). There the
 * navigation moves from the bottom to the side: a bottom bar would take a quarter of a landscape phone's height.
 */
@Composable
fun rememberUsesSideLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenHeightDp < COMPACT_HEIGHT_DP || config.screenWidthDp >= EXPANDED_WIDTH_DP
}

private const val COMPACT_HEIGHT_DP = 480
private const val EXPANDED_WIDTH_DP = 840

@Composable
private fun MainNavigation() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // The lab is a development tool: it does not exist in release builds.
    val destinations = Destination.entries.filter { it != Destination.Lab || BuildConfig.DEBUG }
    val showNavigation = destinations.any { it.route == currentRoute }
    val sideLayout = rememberUsesSideLayout()

    Scaffold(
        // Each screen deals with the system bars itself (top bars, status bar padding), so the outer scaffold
        // must not add them a second time.
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showNavigation && !sideLayout) {
                GlassDock(destinations, currentRoute) { navController.goToTopLevel(it) }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (showNavigation && sideLayout) {
                SideRail(destinations, currentRoute) { navController.goToTopLevel(it) }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.Home.route,
                    modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(),
                ) {
                    composable(Destination.Home.route) {
                        DashboardScreen(onOpenApps = { navController.goToTopLevel(Destination.Apps.route) })
                    }
                    composable(Destination.Apps.route) {
                        AppsScreen(
                            onAdd = { navController.navigate(Routes.PICK) },
                            onEdit = { navController.navigate(Routes.edit(it)) },
                        )
                    }
                    composable(Destination.Stats.route) { StatsScreen() }
                    composable(Destination.Settings.route) {
                        SettingsScreen(onOpenExceptions = { navController.navigate(Routes.EXCEPTIONS) })
                    }
                    composable(Routes.EXCEPTIONS) { ExceptionsScreen(onBack = { navController.popBackStack() }) }
                    composable(Routes.PICK) {
                        PickAppScreen(
                            onBack = { navController.popBackStack() },
                            // Replace the picker with the editor so Back from the editor returns to the list.
                            onPicked = { id ->
                                navController.navigate(Routes.edit(id)) { popUpTo(Routes.PICK) { inclusive = true } }
                            },
                        )
                    }
                    composable(
                        route = Routes.EDIT,
                        arguments = listOf(navArgument("ruleId") { type = NavType.LongType }),
                    ) {
                        EditRuleScreen(
                            onClose = { navController.popBackStack() },
                            onOpenRule = { navController.navigate(Routes.edit(it)) },
                        )
                    }
                    if (BuildConfig.DEBUG) composable(Destination.Lab.route) { LabScreen() }
                }
            }
        }
    }
}

/**
 * Side navigation for a phone on its side or a wide screen. With so little height the labels are shown only for the
 * selected item, and each icon still carries its name for screen readers.
 */
@Composable
private fun SideRail(destinations: List<Destination>, currentRoute: String?, onSelect: (String) -> Unit) {
    NavigationRail(containerColor = Color.Transparent) {
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination.route) },
                icon = { Icon(destination.icon, contentDescription = stringResource(destination.label)) },
                label = { Text(stringResource(destination.label)) },
                alwaysShowLabel = false,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
    }
}

/**
 * The bottom navigation, floating over the screen as glass: a capsule in Oscuro, a rounded bar in Claro. The selected
 * destination is lit; labels stay on one line so five tabs (debug) or large text never break a word in two.
 */
@Composable
private fun GlassDock(destinations: List<Destination>, currentRoute: String?, onSelect: (String) -> Unit) {
    val dark = LocalDarkTheme.current
    val shape = if (dark) CircleShape else RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().glassSurface(shape, dark).padding(6.dp)) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val ink = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(if (dark) CircleShape else RoundedCornerShape(20.dp))
                        .then(if (selected) Modifier.background(if (dark) Color.White.copy(alpha = 0.14f) else Color.White) else Modifier)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(destination.icon, contentDescription = null, tint = ink)
                    Text(stringResource(destination.label), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp), color = ink, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Goes to a main screen the usual way: back to the start, keeping each screen's state, without piling copies up. */
private fun NavController.goToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
