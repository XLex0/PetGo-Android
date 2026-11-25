package ec.edu.epn.petgo.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.ui.common.LocalSnackbarHostState
import ec.edu.epn.petgo.ui.feature.profile.ProfileScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector, val badge: Int = 0)

/** Cliente: Perfil. */
@Composable
fun ClientShell(user: User) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(ProfileRoute, ProfileRoute::class, "Perfil", Icons.Filled.Person),
    )
    RoleScaffold(nav, tabs) {
        NavHost(nav, startDestination = ProfileRoute, modifier = it) {
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}

/** Paseador: Perfil. */
@Composable
fun WalkerShell(user: User) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(ProfileRoute, ProfileRoute::class, "Perfil", Icons.Filled.Person),
    )
    RoleScaffold(nav, tabs) {
        NavHost(nav, startDestination = ProfileRoute, modifier = it) {
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}

@Composable
private fun RoleScaffold(nav: NavHostController, tabs: List<Tab>, content: @Composable (Modifier) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { tab -> destination?.hasRoute(tab.routeClass) == true }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            // Los insets los maneja cada pantalla (ScreenScaffold) para no duplicarlos.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = destination?.hasRoute(tab.routeClass) == true,
                                onClick = { nav.switchTab(tab.route) },
                                icon = {
                                    BadgedBox(badge = { if (tab.badge > 0) Badge { Text(tab.badge.toString()) } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) },
                            )
                        }
                    }
                }
            },
        ) { padding -> content(Modifier.padding(padding)) }
    }
}

/** Cambio de pestaña conservando el estado de cada una. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
