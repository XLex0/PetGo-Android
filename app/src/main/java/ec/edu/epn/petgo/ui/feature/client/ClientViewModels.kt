package ec.edu.epn.petgo.ui.feature.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.Gender
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.model.Pet
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.usecase.application.CounterOfferUseCase
import ec.edu.epn.petgo.domain.usecase.application.ObserveClientApplicationsUseCase
import ec.edu.epn.petgo.domain.usecase.application.RespondToApplicationUseCase
import ec.edu.epn.petgo.domain.usecase.pet.CreatePetUseCase
import ec.edu.epn.petgo.domain.usecase.pet.ObserveMyPetsUseCase
import ec.edu.epn.petgo.domain.usecase.pet.SuspendPetUseCase
import ec.edu.epn.petgo.domain.usecase.profile.GetUserProfileUseCase
import ec.edu.epn.petgo.domain.usecase.review.RateWalkUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.ChangeTicketStatusUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.CreateTicketUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.DeleteTicketUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.ObserveMyTicketsUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.ObserveTicketUseCase
import ec.edu.epn.petgo.domain.usecase.ticket.UpdateTicketScheduleUseCase
import ec.edu.epn.petgo.domain.validation.ValidationResult
import ec.edu.epn.petgo.ui.common.ListState
import ec.edu.epn.petgo.ui.common.PetGoViewModel
import ec.edu.epn.petgo.ui.common.userIdFlow
import ec.edu.epn.petgo.ui.navigation.TicketDetailRoute
import ec.edu.epn.petgo.ui.navigation.TicketFormRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

private fun <T> Flow<List<T>>.asListState(vm: PetGoViewModel): StateFlow<ListState<T>> =
    map<List<T>, ListState<T>> { ListState.Ready(it) }
        .catch { emit(ListState.Ready(emptyList())) }
        .stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState.Loading)

/** Un paseo con sus postulaciones (para tarjetas y dashboard). */
data class TicketWithOffers(val ticket: Ticket, val offers: List<Application>) {
    val pendingOffers: Int get() = offers.count { it.status == ApplicationStatus.PENDING }
    val needsAttention: Boolean get() = pendingOffers > 0 || ticket.status == TicketStatus.COMPLETED
}

// ============================================================ Inicio + lista de paseos

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientWalksViewModel @Inject constructor(
    session: SessionRepository,
    observeTickets: ObserveMyTicketsUseCase,
    observeApplications: ObserveClientApplicationsUseCase,
    observePets: ObserveMyPetsUseCase,
) : PetGoViewModel() {

    val walks: StateFlow<ListState<TicketWithOffers>> = session.userIdFlow()
        .flatMapLatest { uid ->
            combine(observeTickets(uid), observeApplications(uid)) { tickets, apps ->
                val byTicket = apps.groupBy { it.ticketId }
                tickets.map { TicketWithOffers(it, byTicket[it.id].orEmpty()) }
            }
        }
        .asListState(this)

    val pets: StateFlow<ListState<Pet>> = session.userIdFlow().flatMapLatest { observePets(it) }.asListState(this)
}

// ============================================================ Mascotas

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PetsViewModel @Inject constructor(
    session: SessionRepository,
    observePets: ObserveMyPetsUseCase,
    private val suspendPet: SuspendPetUseCase,
) : PetGoViewModel() {
    val pets: StateFlow<ListState<Pet>> = session.userIdFlow().flatMapLatest { observePets(it) }.asListState(this)

    fun remove(pet: Pet) = runAction(successMessage = "${pet.name} fue retirado de tu lista.") { suspendPet(pet.id) }
}

data class PetForm(
    val name: String = "",
    val race: String = "",
    val age: String = "",
    val weight: String = "",
    val behavior: String = "",
    val gender: Gender? = null,
    val image: LocalImage? = null,
    val errors: ValidationResult = ValidationResult.VALID,
)

@HiltViewModel
class PetFormViewModel @Inject constructor(private val createPet: CreatePetUseCase) : PetGoViewModel() {
    private val _form = MutableStateFlow(PetForm())
    val form: StateFlow<PetForm> = _form.asStateFlow()

    fun edit(field: String, transform: (PetForm) -> PetForm) =
        _form.update { transform(it).copy(errors = ValidationResult(it.errors.errors - field)) }

    fun submit(onDone: () -> Unit) {
        val f = _form.value
        runAction(
            successMessage = "¡${f.name.trim()} ya forma parte de PetGo!",
            onValidation = { errors -> _form.update { it.copy(errors = errors) } },
            onSuccess = { onDone() },
        ) {
            createPet(
                name = f.name,
                race = f.race,
                age = f.age.trim().toIntOrNull(),
                weight = f.weight.trim().replace(',', '.').toDoubleOrNull(),
                behavior = f.behavior,
                gender = f.gender,
                image = f.image,
            )
        }
    }
}

// ============================================================ Crear / editar paseo

data class TicketForm(
    val date: LocalDate? = null,
    val hour: LocalTime? = null,
    val durationMinutes: Int? = 60,
    val petIds: Set<String> = emptySet(),
    val errors: ValidationResult = ValidationResult.VALID,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TicketFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    session: SessionRepository,
    observePets: ObserveMyPetsUseCase,
    observeTicket: ObserveTicketUseCase,
    private val createTicket: CreateTicketUseCase,
    private val updateSchedule: UpdateTicketScheduleUseCase,
) : PetGoViewModel() {

    val editingId: String? = savedStateHandle.toRoute<TicketFormRoute>().ticketId
    val isEditing: Boolean get() = editingId != null

    val pets: StateFlow<ListState<Pet>> = session.userIdFlow().flatMapLatest { observePets(it) }.asListState(this)

    private val _form = MutableStateFlow(TicketForm())
    val form: StateFlow<TicketForm> = _form.asStateFlow()

    init {
        val id = editingId
        if (id != null) {
            viewModelScope.launch {
                val ticket = observeTicket(id).first()
                if (ticket != null) {
                    _form.value = TicketForm(ticket.date, ticket.hour, ticket.durationMinutes, ticket.petIds.toSet())
                }
            }
        }
    }

    fun edit(field: String, transform: (TicketForm) -> TicketForm) =
        _form.update { transform(it).copy(errors = ValidationResult(it.errors.errors - field)) }

    fun togglePet(petId: String) = edit(ec.edu.epn.petgo.domain.validation.Fields.PETS) {
        it.copy(petIds = if (petId in it.petIds) it.petIds - petId else it.petIds + petId)
    }

    fun submit(onCreated: (String) -> Unit, onUpdated: () -> Unit) {
        val f = _form.value
        val id = editingId
        if (id == null) {
            runAction(
                successMessage = "¡Paseo publicado! Te avisaremos cuando lleguen ofertas.",
                onValidation = { errors -> _form.update { it.copy(errors = errors) } },
                onSuccess = { newId -> onCreated(newId) },
            ) { createTicket(f.date, f.hour, f.durationMinutes, f.petIds.toList()) }
        } else {
            runAction(
                successMessage = "Paseo actualizado.",
                onValidation = { errors -> _form.update { it.copy(errors = errors) } },
                onSuccess = { onUpdated() },
            ) { updateSchedule(id, f.date, f.hour, f.durationMinutes) }
        }
    }
}

// ============================================================ Detalle de paseo (incluye postulaciones)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    session: SessionRepository,
    observeTicket: ObserveTicketUseCase,
    observeApplications: ObserveClientApplicationsUseCase,
    private val getUserProfile: GetUserProfileUseCase,
    private val changeStatus: ChangeTicketStatusUseCase,
    private val deleteTicket: DeleteTicketUseCase,
    private val respond: RespondToApplicationUseCase,
    private val counterOffer: CounterOfferUseCase,
    private val rateWalk: RateWalkUseCase,
) : PetGoViewModel() {

    val ticketId: String = savedStateHandle.toRoute<TicketDetailRoute>().ticketId

    /** null = cargando; Ready(null) no aplica: si el ticket no existe se emite NotFound. */
    val ticket: StateFlow<TicketLoad> = observeTicket(ticketId)
        .map { if (it == null || it.deleted) TicketLoad.NotFound else TicketLoad.Ready(it) }
        .catch { emit(TicketLoad.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TicketLoad.Loading)

    val offers: StateFlow<List<Application>> = session.userIdFlow()
        .flatMapLatest { uid -> observeApplications(uid) }
        .map { list -> list.filter { it.ticketId == ticketId } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Perfil del paseador asignado (para mostrar foto, experiencia y botón de llamada). */
    val walker: StateFlow<User?> = ticket
        .map { (it as? TicketLoad.Ready)?.ticket?.assignedWalkerId }
        .flatMapLatest { walkerId ->
            if (walkerId == null) flowOf(null)
            else flowOf(getUserProfile(walkerId)).map { (it as? AppResult.Success)?.data }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tras finalizar un paseo se abre directamente la calificación (MEJORA). */
    private val _askRating = MutableStateFlow(false)
    val askRating: StateFlow<Boolean> = _askRating.asStateFlow()
    fun openRating() { _askRating.value = true }
    fun dismissRating() { _askRating.value = false }

    fun start() = runAction(successMessage = "¡Paseo en curso! 🐕") { changeStatus.start(ticketId) }
    fun finish() = runAction(successMessage = "Paseo finalizado.", onSuccess = { _askRating.value = true }) { changeStatus.finish(ticketId) }
    fun cancel() = runAction(successMessage = "Paseo cancelado.") { changeStatus.cancel(ticketId) }
    fun delete(onDeleted: () -> Unit) = runAction(successMessage = "Solicitud eliminada.", onSuccess = { onDeleted() }) { deleteTicket(ticketId) }

    fun accept(app: Application) = runAction(successMessage = "¡${app.walker.name} será tu paseador!") { respond.accept(app.id) }
    fun reject(app: Application) = runAction(successMessage = "Oferta rechazada.") { respond.reject(app.id) }
    fun counter(app: Application, price: Double?, onDone: () -> Unit) =
        runAction(successMessage = "Contraoferta enviada a ${app.walker.name}.", onSuccess = { onDone() }) { counterOffer(app.id, price) }

    fun rate(stars: Int, punctuality: Int, treatment: Int, comment: String) =
        runAction(successMessage = "¡Gracias por tu calificación!", onSuccess = { _askRating.value = false }) {
            rateWalk(ticketId, stars, punctuality, treatment, comment)
        }
}

sealed interface TicketLoad {
    data object Loading : TicketLoad
    data object NotFound : TicketLoad
    data class Ready(val ticket: Ticket) : TicketLoad
}
