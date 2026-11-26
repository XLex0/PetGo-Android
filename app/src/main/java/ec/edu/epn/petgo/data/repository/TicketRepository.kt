package ec.edu.epn.petgo.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.data.mapper.startTimestamp
import ec.edu.epn.petgo.data.mapper.toDomain
import ec.edu.epn.petgo.data.mapper.toDto
import ec.edu.epn.petgo.data.mapper.toHourString
import ec.edu.epn.petgo.data.mapper.toIso
import ec.edu.epn.petgo.data.remote.abort
import ec.edu.epn.petgo.data.remote.businessAbort
import ec.edu.epn.petgo.data.remote.dto.Collections
import ec.edu.epn.petgo.data.remote.dto.TicketDto
import ec.edu.epn.petgo.data.remote.firebaseCall
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.repository.TicketRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTicketRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : TicketRepository {

    private val tickets get() = db.collection(Collections.TICKETS)
    private val applications get() = db.collection(Collections.APPLICATIONS)

    /** TicketDAO.findTicketsByUser (el orden se aplica en el caso de uso). */
    override fun observeClientTickets(clientId: String): Flow<List<Ticket>> =
        tickets.whereEqualTo("clientId", clientId).whereEqualTo("deleted", false)
            .snapshots()
            .map { snap -> snap.toObjects(TicketDto::class.java).mapNotNull { it.toDomain() } }

    /** TicketDAO.findAllTickets: deleted=false AND status=PENDING. */
    override fun observeAvailableTickets(): Flow<List<Ticket>> =
        tickets.whereEqualTo("status", TicketStatus.PENDING.name).whereEqualTo("deleted", false)
            .snapshots()
            .map { snap -> snap.toObjects(TicketDto::class.java).mapNotNull { it.toDomain() } }

    override fun observeWalkerTickets(walkerId: String): Flow<List<Ticket>> =
        tickets.whereEqualTo("assignedWalkerId", walkerId)
            .snapshots()
            .map { snap -> snap.toObjects(TicketDto::class.java).mapNotNull { it.toDomain() } }

    override fun observeTicket(ticketId: String): Flow<Ticket?> =
        tickets.document(ticketId).snapshots()
            .map { snap -> snap.toObject(TicketDto::class.java)?.toDomain() }
            .catch { emit(null) }

    override suspend fun getTicket(ticketId: String): AppResult<Ticket> = firebaseCall {
        tickets.document(ticketId).get().await().toObject(TicketDto::class.java)?.toDomain() ?: abort(AppError.NotFound)
    }

    override suspend fun createTicket(ticket: Ticket): AppResult<String> = firebaseCall {
        val ref = tickets.document()
        ref.set(ticket.copy(id = ref.id, status = TicketStatus.PENDING, deleted = false).toDto()).await()
        ref.id
    }

    override suspend fun updateSchedule(ticketId: String, date: LocalDate, hour: LocalTime, durationMinutes: Int): AppResult<Unit> =
        firebaseCall {
            tickets.document(ticketId).update(
                mapOf(
                    "date" to date.toIso(),
                    "hour" to hour.toHourString(),
                    "startAt" to startTimestamp(date, hour),
                    "durationMinutes" to durationMinutes,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Unit
        }

    /** TicketService.suspendTicket: deleted=true + ApplicationService.deleteApplicationsByTicketId en un batch. */
    override suspend fun softDelete(ticketId: String): AppResult<Unit> = firebaseCall {
        val ticketRef = tickets.document(ticketId)
        val ticket = ticketRef.get().await().toObject(TicketDto::class.java) ?: abort(AppError.NotFound)
        // El filtro por clientId es obligatorio: las reglas solo permiten al cliente listar SUS postulaciones.
        val apps = applications.whereEqualTo("ticketId", ticketId).whereEqualTo("clientId", ticket.clientId).get().await()
        val batch = db.batch()
        batch.update(ticketRef, mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()))
        apps.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
        Unit
    }

    /** TicketDAO.ticketInProgress / ticketCompleted / ticketCanceled, con validación de transición. */
    override suspend fun changeStatus(ticketId: String, expectedClientId: String, target: TicketStatus): AppResult<Unit> =
        firebaseCall {
            val ref = tickets.document(ticketId)
            db.runTransaction { tx ->
                val dto = tx.get(ref).toObject(TicketDto::class.java) ?: abort(AppError.NotFound)
                if (dto.clientId != expectedClientId) abort(AppError.PermissionDenied)
                if (dto.deleted) businessAbort("Esta solicitud fue eliminada.")
                val current = runCatching { TicketStatus.valueOf(dto.status) }.getOrNull() ?: abort(AppError.Unknown())
                if (!current.canTransitionTo(target)) businessAbort(transitionError(current, target))
                tx.update(ref, mapOf("status" to target.name, "updatedAt" to FieldValue.serverTimestamp()))
                null
            }.await()
            Unit
        }

    private fun transitionError(current: TicketStatus, target: TicketStatus): String = when (target) {
        TicketStatus.IN_PROGRESS -> "Solo puedes iniciar paseos asignados."
        TicketStatus.COMPLETED -> "Solo puedes finalizar paseos en progreso."
        TicketStatus.CANCELED -> "Este paseo no se puede cancelar en estado ${current.name}."
        else -> "Cambio de estado no permitido."
    }
}
