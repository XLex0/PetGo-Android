package ec.edu.epn.petgo.ui.feature.walker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.usecase.application.ApplyToTicketUseCase
import ec.edu.epn.petgo.domain.usecase.application.ObserveWalkerApplicationsUseCase
import ec.edu.epn.petgo.domain.usecase.application.RespondToApplicationUseCase
import ec.edu.epn.petgo.domain.usecase.profile.GetUserProfileUseCase
import ec.edu.epn.petgo.domain.usecase.profile.ToggleAvailabilityUseCase
import ec.edu.epn.petgo.domain.usecase.review.ObserveWalkerReviewsUseCase
import ec.edu.epn.petgo.domain.usecase.review.WalkerReviews
import ec.edu.epn.petgo.domain.usecase.ticket.ObserveAvailableTicketsUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.ObserveTicketUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.ObserveWalkerWalksUseCase
import ec.edu.epn.petgo.domain.validation.ValidationResult
import ec.edu.epn.petgo.ui.common.ListState
import ec.edu.epn.petgo.ui.common.PetGoViewModel
import ec.edu.epn.petgo.ui.common.userIdFlow
import ec.edu.epn.petgo.ui.navigation.WalkDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private fun <T> Flow<T>.toState(vm: PetGoViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

private fun <T> Flow<List<T>>.asListState(vm: PetGoViewModel): StateFlow<ListState<T>> =
    map<List<T>, ListState<T>> { ListState.Ready(it) }
        .catch { emit(ListState.Ready(emptyList())) }
        .toState(vm, ListState.Loading)

// ============================================================ Inicio del paseador

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalkerHomeViewModel @Inject constructor(
    session: SessionRepository,
    observeWalks: ObserveWalkerWalksUseCase,
    observeApplications: ObserveWalkerApplicationsUseCase,
    observeReviews: ObserveWalkerReviewsUseCase,
    private val toggleAvailability: ToggleAvailabilityUseCase,
) : PetGoViewModel() {
    val walks: StateFlow<ListState<Ticket>> = session.userIdFlow().flatMapLatest { observeWalks(it) }.asListState(this)
    val applications: StateFlow<ListState<Application>> = session.userIdFlow().flatMapLatest { observeApplications(it) }.asListState(this)
    val reviews: StateFlow<WalkerReviews?> = session.userIdFlow()
        .flatMapLatest { observeReviews(it) }
        .map<WalkerReviews, WalkerReviews?> { it }
        .catch { emit(null) }
        .toState(this, null)

    fun setAvailable(available: Boolean) = runAction(
        successMessage = if (available) "¡Estás disponible! Los dueños pueden recibir tus ofertas." else "Pausaste tu disponibilidad.",
    ) { toggleAvailability(available) }
}

// ============================================================ Solicitudes disponibles

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AvailableTicketsViewModel @Inject constructor(
    session: SessionRepository,
    observeAvailable: ObserveAvailableTicketsUseCase,
    observeApplications: ObserveWalkerApplicationsUseCase,
    private val applyToTicket: ApplyToTicketUseCase,
    private val toggleAvailability: ToggleAvailabilityUseCase,
) : PetGoViewModel() {
    val tickets: StateFlow<ListState<Ticket>> = observeAvailable().asListState(this)

    /** ticketId → mi postulación (para mostrar "Ya ofertaste $X" en vez del botón). */
    val myOffers: StateFlow<Map<String, Application>> = session.userIdFlow()
        .flatMapLatest { observeApplications(it) }
        .map { list -> list.associateBy { it.ticketId } }
        .catch { emit(emptyMap()) }
        .toState(this, emptyMap())

    fun sendOffer(ticket: Ticket, price: Double?, onValidation: (ValidationResult) -> Unit, onDone: () -> Unit) = runAction(
        successMessage = ApplyToTicketUseCase.SUCCESS_MESSAGE,
        onValidation = onValidation,
        onSuccess = { onDone() },
    ) { applyToTicket(ticket.id, price) }

    fun becomeAvailable() = runAction(successMessage = "¡Estás disponible!") { toggleAvailability(true) }
}

// ============================================================ Mis ofertas (postulaciones)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalkerApplicationsViewModel @Inject constructor(
    session: SessionRepository,
    observeApplications: ObserveWalkerApplicationsUseCase,
    private val respond: RespondToApplicationUseCase,
) : PetGoViewModel() {
    val applications: StateFlow<ListState<Application>> = session.userIdFlow().flatMapLatest { observeApplications(it) }.asListState(this)

    fun acceptCounter(app: Application) = runAction(successMessage = "¡Paseo confirmado con ${app.client.name}!") { respond.accept(app.id) }
    fun rejectCounter(app: Application) = runAction(successMessage = "Contraoferta rechazada.") { respond.reject(app.id) }
}

// ============================================================ Reseñas

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalkerReviewsViewModel @Inject constructor(
    session: SessionRepository,
    observeReviews: ObserveWalkerReviewsUseCase,
) : PetGoViewModel() {
    val reviews: StateFlow<WalkerReviews?> = session.userIdFlow()
        .flatMapLatest { observeReviews(it) }
        .map<WalkerReviews, WalkerReviews?> { it }
        .catch { emit(WalkerReviews(ec.edu.epn.petgo.domain.model.WalkerStats.EMPTY, emptyList())) }
        .toState(this, null)
}

// ============================================================ Detalle de un paseo asignado

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WalkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeTicket: ObserveTicketUseCase,
    private val getUserProfile: GetUserProfileUseCase,
) : PetGoViewModel() {
    private val ticketId = savedStateHandle.toRoute<WalkDetailRoute>().ticketId

    /** null mientras carga; si no existe se muestra estado vacío. */
    val ticket: StateFlow<Ticket?> = observeTicket(ticketId).catch { emit(null) }.toState(this, null)

    /** Perfil del dueño (teléfono para coordinar el paseo). */
    val client: StateFlow<User?> = ticket
        .map { it?.clientId }
        .flatMapLatest { id -> if (id == null) flowOf(null) else flowOf((getUserProfile(id) as? AppResult.Success)?.data) }
        .toState(this, null)
}
