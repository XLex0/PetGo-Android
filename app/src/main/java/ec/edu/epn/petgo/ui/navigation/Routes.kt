package ec.edu.epn.petgo.ui.navigation

import kotlinx.serialization.Serializable

/*
 * Rutas type-safe de Navigation Compose. Equivalencias con la web en docs/MIGRACION_ANDROID.md §5.2.
 * No hay rutas compartidas entre roles: el grafo completo cambia según la sesión (ver PetGoRoot).
 */

// ---------- Sin sesión ----------
@Serializable data object WelcomeRoute          // index.jsp
@Serializable data object LoginRoute            // auth/login.jsp
@Serializable data class RegisterRoute(val role: String? = null) // auth/registro.jsp

// ---------- Compartida ----------
@Serializable data object ProfileRoute          // profile/profile.jsp + walker/profileWalker.jsp (unificados)
