package ec.edu.epn.petgo.domain.usecase.ticket

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.businessError
import ec.edu.epn.petgo.core.result.failure
import ec.edu.epn.petgo.core.result.flatMap
import ec.edu.epn.petgo.domain.model.Pet
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.repository.PetRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.TicketRepository
import ec.edu.epn.petgo.domain.validation.TicketValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** TicketService.getTicketsByUser: orden por estado y luego por fecha más próxima. */
object TicketSorting {
    val byStatusThenDate: Comparator<Ticket> =
        compareBy<Ticket> { it.status.sortOrder }.thenBy { it.date }.thenBy { it.hour }

    /** TicketService.getAllTickets: fecha descendente. */
    val byDateDesc: Comparator<Ticket> = compareByDescending<Ticket> { it.date }.thenByDescending { it.hour }
}

/** TicketController?route=create → TicketService.createTicket */
class CreateTicketUseCase @Inject constructor(
    private val session: SessionRepository,
    private val pets: PetRepository,
    private val tickets: TicketRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate?, hour: LocalTime?, durationMinutes: Int?, petIds: List<String>): AppResult<String> {
        val validation = TicketValidator.validateCreate(date, hour, durationMinutes, petIds, LocalDate.now(clock))
        if (!validation.isValid) return failure(AppError.Validation(validation))

        return session.requireUser().flatMap { user ->
            if (user.role != Role.CLIENT) return@flatMap businessError("Solo los dueños pueden crear solicitudes.")
            pets.getActivePets(user.id).flatMap { myPets ->
                val selected: List<Pet> = myPets.filter { it.id in petIds }
                if (selected.size != petIds.toSet().size) return@flatMap businessError("Alguna mascota seleccionada ya no está disponible.")
                tickets.createTicket(
                    Ticket(
                        id = "",
                        clientId = user.id,
                        client = user.toSummary(),
                        pets = selected.map { it.toSummary() },
                        date = date!!,
                        hour = hour!!,
                        durationMinutes = durationMinutes!!,
                    ),
                )
            }
        }
    }
}

/**
 * TicketController?route=update.
 * MEJORA: solo se puede reprogramar mientras está PENDING (antes se podía en cualquier estado).
 */
class UpdateTicketScheduleUseCase @Inject constructor(
    private val session: SessionRepository,
    private val tickets: TicketRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(ticketId: String, date: LocalDate?, hour: LocalTime?, durationMinutes: Int?): AppResult<Unit> {
        val validation = TicketValidator.validateSchedule(date, hour, durationMinutes, LocalDate.now(clock))
        if (!validation.isValid) return failure(AppError.Validation(validation))
        return session.requireUser().flatMap { user ->
            tickets.getTicket(ticketId).flatMap { ticket ->
                when {
                    ticket.clientId != user.id -> failure(AppError.PermissionDenied)
                    ticket.status != TicketStatus.PENDING -> businessError("Solo puedes modificar solicitudes pendientes.")
                    else -> tickets.updateSchedule(ticketId, date!!, hour!!, durationMinutes!!)
                }
            }
        }
    }
}

/** TicketController?route=suspend → TicketService.suspendTicket (borrado lógico + borra postulaciones). */
class DeleteTicketUseCase @Inject constructor(
    private val session: SessionRepository,
    private val tickets: TicketRepository,
) {
    suspend operator fun invoke(ticketId: String): AppResult<Unit> =
        session.requireUser().flatMap { user ->
            tickets.getTicket(ticketId).flatMap { ticket ->
                when {
                    ticket.clientId != user.id -> failure(AppError.PermissionDenied)
                    ticket.status != TicketStatus.PENDING -> businessError("Solo puedes eliminar solicitudes pendientes.")
                    else -> tickets.softDelete(ticketId)
                }
            }
        }
}

/** route=start / finish / cancel. Los conduce el CLIENTE, igual que en la web. */
class ChangeTicketStatusUseCase @Inject constructor(
    private val session: SessionRepository,
    private val tickets: TicketRepository,
) {
    suspend fun start(ticketId: String) = change(ticketId, TicketStatus.IN_PROGRESS)
    suspend fun finish(ticketId: String) = change(ticketId, TicketStatus.COMPLETED)
    suspend fun cancel(ticketId: String) = change(ticketId, TicketStatus.CANCELED)

    private suspend fun change(ticketId: String, target: TicketStatus): AppResult<Unit> =
        session.requireUser().flatMap { user -> tickets.changeStatus(ticketId, expectedClientId = user.id, target = target) }
}

/** TicketController?route=list */
class ObserveMyTicketsUseCase @Inject constructor(private val tickets: TicketRepository) {
    operator fun invoke(clientId: String): Flow<List<Ticket>> =
        tickets.observeClientTickets(clientId).map { list -> list.filterNot { it.deleted }.sortedWith(TicketSorting.byStatusThenDate) }
}

/** NUEVO: "Mis paseos" del paseador (asignados, en curso y terminados). */
class ObserveWalkerWalksUseCase @Inject constructor(private val tickets: TicketRepository) {
    operator fun invoke(walkerId: String): Flow<List<Ticket>> =
        tickets.observeWalkerTickets(walkerId).map { list -> list.filterNot { it.deleted }.sortedWith(TicketSorting.byStatusThenDate) }
}

class ObserveTicketUseCase @Inject constructor(private val tickets: TicketRepository) {
    operator fun invoke(ticketId: String): Flow<Ticket?> = tickets.observeTicket(ticketId)
}

/**
 * TicketController?route=listAll (vista del paseador).
 * MEJORA: oculta solicitudes con fecha pasada.
 */
class ObserveAvailableTicketsUseCase @Inject constructor(
    private val tickets: TicketRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<List<Ticket>> = tickets.observeAvailableTickets().map { list ->
        val today = LocalDate.now(clock)
        list.filter { !it.deleted && it.status == TicketStatus.PENDING && !it.date.isBefore(today) }
            .sortedWith(TicketSorting.byDateDesc)
    }
}
