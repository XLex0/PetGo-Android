package ec.edu.epn.petgo.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.data.mapper.toDomain
import ec.edu.epn.petgo.data.mapper.toDto
import ec.edu.epn.petgo.data.remote.abort
import ec.edu.epn.petgo.data.remote.businessAbort
import ec.edu.epn.petgo.data.remote.dto.ApplicationDto
import ec.edu.epn.petgo.data.remote.dto.Collections
import ec.edu.epn.petgo.data.remote.dto.ReviewDto
import ec.edu.epn.petgo.data.remote.dto.TicketDto
import ec.edu.epn.petgo.data.remote.firebaseCall
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.Review
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.repository.ApplicationRepository
import ec.edu.epn.petgo.domain.repository.ReviewRepository
import ec.edu.epn.petgo.domain.usecase.application.ApplyToTicketUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private val OPEN_STATUSES = setOf(ApplicationStatus.PENDING.name, ApplicationStatus.COUNTERED.name)

@Singleton
class FirestoreApplicationRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : ApplicationRepository {

    private val applications get() = db.collection(Collections.APPLICATIONS)
    private val tickets get() = db.collection(Collections.TICKETS)

    /** ApplicationService.applyToTicket: id determinístico + transacción = sin duplicados. */
    override suspend fun create(application: Application): AppResult<Unit> = firebaseCall {
        val ref = applications.document(Application.idFor(application.ticketId, application.walkerId))
        db.runTransaction { tx ->
            if (tx.get(ref).exists()) businessAbort(ApplyToTicketUseCase.DUPLICATE_MESSAGE)
            tx.set(ref, application.copy(id = ref.id, status = ApplicationStatus.PENDING).toDto())
            null
        }.await()
        Unit
    }

    override fun observeByWalker(walkerId: String): Flow<List<Application>> =
        applications.whereEqualTo("walkerId", walkerId).snapshots()
            .map { snap -> snap.toObjects(ApplicationDto::class.java).mapNotNull { it.toDomain() } }

    override fun observeByClient(clientId: String): Flow<List<Application>> =
        applications.whereEqualTo("clientId", clientId).snapshots()
            .map { snap -> snap.toObjects(ApplicationDto::class.java).mapNotNull { it.toDomain() } }

    override suspend fun getApplication(applicationId: String): AppResult<Application> = firebaseCall {
        applications.document(applicationId).get().await().toObject(ApplicationDto::class.java)?.toDomain()
            ?: abort(AppError.NotFound)
    }

    /** ApplicationService.updateOfferPriceAndStatus(id, precio, "countered") */
    override suspend fun counterOffer(applicationId: String, newPrice: Double): AppResult<Unit> =
        transitionApplication(applicationId, setOf(ApplicationStatus.PENDING)) {
            mapOf("offerPrice" to newPrice, "status" to ApplicationStatus.COUNTERED.name)
        }

    override suspend fun reject(applicationId: String, allowedFrom: Set<ApplicationStatus>): AppResult<Unit> =
        transitionApplication(applicationId, allowedFrom) { mapOf("status" to ApplicationStatus.REJECTED.name) }

    /** ApplicationService.acceptApplicationAndRejectOthers + TicketService.assignTicket, ahora atómico. */
    override suspend fun accept(applicationId: String, allowedFrom: Set<ApplicationStatus>, rejectSiblings: Boolean): AppResult<Unit> =
        firebaseCall {
            val appRef = applications.document(applicationId)
            val app = appRef.get().await().toObject(ApplicationDto::class.java) ?: abort(AppError.NotFound)
            val ticketRef = tickets.document(app.ticketId)
            // Las consultas no pueden ir dentro de la transacción: se obtienen los ids antes y se releen dentro.
            val siblingRefs = if (rejectSiblings) {
                applications.whereEqualTo("ticketId", app.ticketId).whereEqualTo("clientId", app.clientId).get().await()
                    .documents.map { it.reference }.filter { it.id != applicationId }
            } else emptyList()

            db.runTransaction { tx ->
                // 1) Lecturas (Firestore exige todas las lecturas antes de cualquier escritura)
                val current = tx.get(appRef).toObject(ApplicationDto::class.java) ?: abort(AppError.NotFound)
                val ticket = tx.get(ticketRef).toObject(TicketDto::class.java) ?: abort(AppError.NotFound)
                val siblings = siblingRefs.map { it to tx.get(it).getString("status") }

                // 2) Validaciones
                if (current.status !in allowedFrom.map { it.name }) businessAbort("Esta postulación ya fue respondida.")
                if (ticket.deleted || ticket.status != TicketStatus.PENDING.name) businessAbort("Esta solicitud ya no está disponible.")

                // 3) Escrituras
                tx.update(appRef, mapOf("status" to ApplicationStatus.ACCEPTED.name, "updatedAt" to FieldValue.serverTimestamp()))
                tx.update(
                    ticketRef,
                    mapOf(
                        "status" to TicketStatus.ASSIGNED.name,
                        "assignedWalkerId" to current.walkerId,
                        "agreedPrice" to current.offerPrice,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                siblings.filter { (_, status) -> status in OPEN_STATUSES }.forEach { (ref, _) ->
                    tx.update(ref, mapOf("status" to ApplicationStatus.REJECTED.name, "updatedAt" to FieldValue.serverTimestamp()))
                }
                null
            }.await()
            Unit
        }

    override suspend fun rejectSiblings(ticketId: String, clientId: String, acceptedApplicationId: String): AppResult<Unit> =
        firebaseCall {
            val open = applications.whereEqualTo("ticketId", ticketId).whereEqualTo("clientId", clientId).get().await()
                .documents.filter { it.id != acceptedApplicationId && it.getString("status") in OPEN_STATUSES }
            if (open.isNotEmpty()) {
                val batch = db.batch()
                open.forEach {
                    batch.update(it.reference, mapOf("status" to ApplicationStatus.REJECTED.name, "updatedAt" to FieldValue.serverTimestamp()))
                }
                batch.commit().await()
            }
            Unit
        }

    private suspend fun transitionApplication(
        applicationId: String,
        allowedFrom: Set<ApplicationStatus>,
        changes: () -> Map<String, Any>,
    ): AppResult<Unit> = firebaseCall {
        val ref = applications.document(applicationId)
        db.runTransaction { tx ->
            val status = tx.get(ref).getString("status") ?: abort(AppError.NotFound)
            if (status !in allowedFrom.map { it.name }) businessAbort("Esta postulación ya fue respondida.")
            tx.update(ref, changes() + ("updatedAt" to FieldValue.serverTimestamp()))
            null
        }.await()
        Unit
    }
}

@Singleton
class FirestoreReviewRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : ReviewRepository {

    private val reviews get() = db.collection(Collections.REVIEWS)
    private val tickets get() = db.collection(Collections.TICKETS)

    /** ReviewService.rateTicket: crea la reseña y marca el ticket REVIEWED de forma atómica. */
    override suspend fun rate(review: Review): AppResult<Unit> = firebaseCall {
        val ticketRef = tickets.document(review.ticketId)
        val reviewRef = reviews.document(review.ticketId)
        db.runTransaction { tx ->
            val ticket = tx.get(ticketRef).toObject(TicketDto::class.java) ?: abort(AppError.NotFound)
            val existing = tx.get(reviewRef)
            if (ticket.clientId != review.clientId) abort(AppError.PermissionDenied)
            if (existing.exists() || ticket.status == TicketStatus.REVIEWED.name) businessAbort("Este paseo ya fue calificado.")
            if (ticket.status != TicketStatus.COMPLETED.name) businessAbort("Solo puedes calificar paseos completados.")
            tx.set(reviewRef, review.toDto())
            tx.update(ticketRef, mapOf("status" to TicketStatus.REVIEWED.name, "updatedAt" to FieldValue.serverTimestamp()))
            null
        }.await()
        Unit
    }

    /** ReviewDAO.findByWalkerId (ya no necesita JOIN con applications: walkerId va en la reseña). */
    override fun observeByWalker(walkerId: String): Flow<List<Review>> =
        reviews.whereEqualTo("walkerId", walkerId).snapshots()
            .map { snap -> snap.toObjects(ReviewDto::class.java).map { it.toDomain() } }
}
