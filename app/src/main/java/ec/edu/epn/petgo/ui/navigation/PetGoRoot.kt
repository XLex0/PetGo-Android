package ec.edu.epn.petgo.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.repository.SessionState
import ec.edu.epn.petgo.ui.common.LocalSnackbarHostState
import ec.edu.epn.petgo.ui.feature.auth.LoginScreen
import ec.edu.epn.petgo.ui.feature.auth.RegisterScreen
import ec.edu.epn.petgo.ui.feature.auth.WelcomeScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * Raíz de la UI. El grafo depende de la sesión:
 *  - SignedOut → flujo de acceso
 *  - SignedIn(CLIENT) → ClientShell, SignedIn(WALKER) → WalkerShell
 * Login, registro, logout y "cuenta suspendida" cambian de grafo solos al cambiar la sesión,
 * sin manipular el back stack (la web hacía redirect por rol en LoginController).
 */
@Composable
fun PetGoRoot(session: StateFlow<SessionState>) {
    val state by session.collectAsStateWithLifecycle()
    when (val s = state) {
        SessionState.Loading -> Box(Modifier.fillMaxSize()) // la splash screen sigue visible
        SessionState.SignedOut -> AuthGraph()
        is SessionState.SignedIn -> key(s.user.id, s.user.role) {
            when (s.user.role) {
                Role.CLIENT -> ClientShell(user = s.user)
                Role.WALKER -> WalkerShell(user = s.user)
            }
        }
    }
}

@Composable
private fun AuthGraph() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            NavHost(nav, startDestination = WelcomeRoute, modifier = Modifier.padding(padding)) {
                composable<WelcomeRoute> {
                    WelcomeScreen(
                        onLogin = { nav.navigate(LoginRoute) },
                        onRegister = { role -> nav.navigate(RegisterRoute(role)) },
                    )
                }
                composable<LoginRoute> {
                    LoginScreen(
                        onBack = { nav.popBackStack() },
                        onRegister = { nav.navigate(RegisterRoute()) { popUpTo(WelcomeRoute) } },
                    )
                }
                composable<RegisterRoute> { entry ->
                    RegisterScreen(
                        initialRole = entry.toRoute<RegisterRoute>().role,
                        onBack = { nav.popBackStack() },
                        onLogin = { nav.navigate(LoginRoute) { popUpTo(WelcomeRoute) } },
                    )
                }
            }
        }
    }
}
