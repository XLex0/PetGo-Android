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

// ---------- Cliente ----------
@Serializable data object ClientHomeRoute       // client/client.jsp → ahora dashboard
@Serializable data object PetsRoute             // client/pet/list.jsp
@Serializable data object PetFormRoute          // client/pet/register.jsp
@Serializable data object TicketsRoute          // client/ticket/list.jsp
@Serializable data class TicketFormRoute(val ticketId: String? = null) // client/ticket/register.jsp + modal editar
@Serializable data class TicketDetailRoute(val ticketId: String)       // NUEVO: detalle + postulaciones (client/listApplications.jsp)

// ---------- Paseador ----------
@Serializable data object WalkerHomeRoute       // walker/home.jsp → dashboard con disponibilidad y paseos
@Serializable data object AvailableTicketsRoute // walker/listTickets.jsp
@Serializable data object WalkerApplicationsRoute // walker/listApplications.jsp
@Serializable data object WalkerReviewsRoute    // walker/reviews.jsp
@Serializable data class WalkDetailRoute(val ticketId: String)         // NUEVO: detalle de un paseo asignado
