package app.pausa.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** The screens reachable from the bottom bar. Everything else (picker, editor, exceptions) is a step inside one. */
private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Inicio", Icons.Default.Home),
    Apps("apps", "Apps", Icons.Default.Menu),
    Stats("stats", "Estadísticas", Icons.Default.Info),
    Settings("settings", "Ajustes", Icons.Default.Settings),
    Lab("lab", "Laboratorio", Icons.Default.Build),
}

private object Routes {
    const val PICK = "apps/pick"
    const val EDIT = "apps/edit/{ruleId}"
    const val EXCEPTIONS = "settings/exceptions"
    fun edit(ruleId: Long) = "apps/edit/$ruleId"
}

@Composable
fun PausaRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val onboardingDone by rootViewModel.onboardingDone.collectAsStateWithLifecycle()

    // Nothing is drawn until the setting is read: no flash of the introduction for someone who already saw it.
    when (onboardingDone) {
        null -> Unit
        false -> OnboardingScreen(onFinish = rootViewModel::finishOnboarding)
        true -> MainNavigation()
    }
}

@Composable
private fun MainNavigation() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // The lab is a development tool: it does not exist in release builds.
    val destinations = Destination.entries.filter { it != Destination.Lab || BuildConfig.DEBUG }
    val showBottomBar = destinations.any { it.route == currentRoute }

    Scaffold(
        // Each screen deals with the system bars itself (top bars, status bar padding), so the outer scaffold
        // must not add them a second time.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Home.route) {
                DashboardScreen(
                    onOpenApps = {
                        navController.navigate(Destination.Apps.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
            composable(Destination.Lab.route) { LabScreen() }
        }
    }
}
