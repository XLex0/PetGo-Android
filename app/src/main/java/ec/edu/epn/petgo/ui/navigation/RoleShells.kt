package ec.edu.epn.petgo.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Star
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.ui.common.LocalSnackbarHostState
import ec.edu.epn.petgo.ui.feature.client.ClientHomeScreen
import ec.edu.epn.petgo.ui.feature.client.PetFormScreen
import ec.edu.epn.petgo.ui.feature.client.PetsScreen
import ec.edu.epn.petgo.ui.feature.client.TicketDetailScreen
import ec.edu.epn.petgo.ui.feature.client.TicketFormScreen
import ec.edu.epn.petgo.ui.feature.client.TicketsScreen
import ec.edu.epn.petgo.ui.feature.profile.ProfileScreen
import ec.edu.epn.petgo.ui.feature.walker.AvailableTicketsScreen
import ec.edu.epn.petgo.ui.feature.walker.WalkDetailScreen
import ec.edu.epn.petgo.ui.feature.walker.WalkerApplicationsScreen
import ec.edu.epn.petgo.ui.feature.walker.WalkerHomeScreen
import ec.edu.epn.petgo.ui.feature.walker.WalkerReviewsScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector, val badge: Int = 0)

/** Cliente: Inicio · Mascotas · Paseos · Perfil. Las postulaciones viven dentro de cada paseo (badge en "Paseos"). */
@Composable
fun ClientShell(user: User, vm: ClientShellViewModel = hiltViewModel()) {
    val pending by vm.pendingDecisions.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(ClientHomeRoute, ClientHomeRoute::class, "Inicio", Icons.Filled.Home),
        Tab(PetsRoute, PetsRoute::class, "Mascotas", Icons.Filled.Pets),
        Tab(TicketsRoute, TicketsRoute::class, "Paseos", Icons.Filled.ConfirmationNumber, badge = pending),
        Tab(ProfileRoute, ProfileRoute::class, "Perfil", Icons.Filled.Person),
    )
    RoleScaffold(nav, tabs) {
        NavHost(nav, startDestination = ClientHomeRoute, modifier = it) {
            composable<ClientHomeRoute> {
                ClientHomeScreen(
                    user = user,
                    onNewWalk = { nav.navigate(TicketFormRoute()) },
                    onOpenTicket = { id -> nav.navigate(TicketDetailRoute(id)) },
                    onOpenTickets = { nav.switchTab(TicketsRoute) },
                    onAddPet = { nav.navigate(PetFormRoute) },
                )
            }
            composable<PetsRoute> { PetsScreen(onAddPet = { nav.navigate(PetFormRoute) }) }
            composable<PetFormRoute> { PetFormScreen(onDone = { nav.popBackStack() }) }
            composable<TicketsRoute> {
                TicketsScreen(
                    onNewWalk = { nav.navigate(TicketFormRoute()) },
                    onOpenTicket = { id -> nav.navigate(TicketDetailRoute(id)) },
                )
            }
            composable<TicketFormRoute> {
                TicketFormScreen(
                    onBack = { nav.popBackStack() },
                    onAddPet = { nav.navigate(PetFormRoute) },
                    onCreated = { id -> nav.navigate(TicketDetailRoute(id)) { popUpTo<TicketFormRoute> { inclusive = true } } },
                )
            }
            composable<TicketDetailRoute> {
                TicketDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate(TicketFormRoute(id)) },
                )
            }
            composable<ProfileRoute> { ProfileScreen() }
        }
    }
}

/** Paseador: Inicio · Disponibles · Postulaciones · Reseñas · Perfil. */
@Composable
fun WalkerShell(user: User, vm: WalkerShellViewModel = hiltViewModel()) {
    val counterOffers by vm.counterOffers.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(WalkerHomeRoute, WalkerHomeRoute::class, "Inicio", Icons.Filled.Home),
        Tab(AvailableTicketsRoute, AvailableTicketsRoute::class, "Disponibles", Icons.AutoMirrored.Filled.DirectionsWalk),
        Tab(WalkerApplicationsRoute, WalkerApplicationsRoute::class, "Ofertas", Icons.Filled.LocalOffer, badge = counterOffers),
        Tab(WalkerReviewsRoute, WalkerReviewsRoute::class, "Reseñas", Icons.Filled.Star),
        Tab(ProfileRoute, ProfileRoute::class, "Perfil", Icons.Filled.Person),
    )
    RoleScaffold(nav, tabs) {
        NavHost(nav, startDestination = WalkerHomeRoute, modifier = it) {
            composable<WalkerHomeRoute> {
                WalkerHomeScreen(
                    user = user,
                    onOpenWalk = { id -> nav.navigate(WalkDetailRoute(id)) },
                    onBrowse = { nav.switchTab(AvailableTicketsRoute) },
                    onOpenOffers = { nav.switchTab(WalkerApplicationsRoute) },
                    onEditProfile = { nav.switchTab(ProfileRoute) },
                )
            }
            composable<AvailableTicketsRoute> { AvailableTicketsScreen(user = user, onEditProfile = { nav.switchTab(ProfileRoute) }) }
            composable<WalkerApplicationsRoute> { WalkerApplicationsScreen(onOpenWalk = { id -> nav.navigate(WalkDetailRoute(id)) }) }
            composable<WalkerReviewsRoute> { WalkerReviewsScreen() }
            composable<WalkDetailRoute> { WalkDetailScreen(onBack = { nav.popBackStack() }) }
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
