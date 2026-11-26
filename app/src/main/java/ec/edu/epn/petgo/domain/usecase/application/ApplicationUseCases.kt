package ec.edu.epn.petgo.domain.usecase.application

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.businessError
import ec.edu.epn.petgo.core.result.failure
import ec.edu.epn.petgo.core.result.flatMap
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.repository.ApplicationRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.TicketRepository
import ec.edu.epn.petgo.domain.validation.OfferValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** ApplicationController?route=apply → ApplicationService.applyToTicket */
class ApplyToTicketUseCase @Inject constructor(
    private val session: SessionRepository,
    private val tickets: TicketRepository,
    private val applications: ApplicationRepository,
) {
    suspend operator fun invoke(ticketId: String, price: Double?): AppResult<Unit> {
        val validation = OfferValidator.validate(price)
        if (!validation.isValid) return failure(AppError.Validation(validation))

        return session.requireUser().flatMap { walker ->
            if (walker.role != Role.WALKER) return@flatMap businessError("Solo los paseadores pueden postularse.")
            tickets.getTicket(ticketId).flatMap { ticket ->
                if (ticket.deleted || ticket.status != TicketStatus.PENDING) {
                    businessError("Esta solicitud ya no está disponible.")
                } else {
                    applications.create(
                        Application(
                            id = Application.idFor(ticketId, walker.id),
                            ticketId = ticketId,
                            clientId = ticket.clientId,
                            walkerId = walker.id,
                            client = ticket.client,
                            walker = walker.toWalkerSummary(),
                            ticket = ticket.toSummary(),
                            offerPrice = price!!,
                            originalPrice = price,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        const val SUCCESS_MESSAGE = "Postulación enviada correctamente."
        const val DUPLICATE_MESSAGE = "Ya te has postulado a este ticket."
    }
}

/** ApplicationController?route=counteroffer (cliente). Solo sobre postulaciones PENDING. */
class CounterOfferUseCase @Inject constructor(
    private val session: SessionRepository,
    private val applications: ApplicationRepository,
) {
    suspend operator fun invoke(applicationId: String, newPrice: Double?): AppResult<Unit> {
        val validation = OfferValidator.validate(newPrice)
        if (!validation.isValid) return failure(AppError.Validation(validation))
        return session.requireUser().flatMap { user ->
            applications.getApplication(applicationId).flatMap { app ->
                when {
                    app.clientId != user.id -> failure(AppError.PermissionDenied)
                    app.status != ApplicationStatus.PENDING -> businessError("Solo puedes contraofertar postulaciones pendientes.")
                    else -> applications.counterOffer(applicationId, newPrice!!)
                }
            }
        }
    }
}

/**
 * ApplicationController?route=respond.
 * - Cliente: acepta o rechaza una postulación PENDING.
 * - Paseador: acepta o rechaza una contraoferta (COUNTERED) sobre su propia postulación.
 * Aceptar = transacción que asigna el ticket y rechaza las demás.
 */
class RespondToApplicationUseCase @Inject constructor(
    private val session: SessionRepository,
    private val applications: ApplicationRepository,
) {
    suspend fun accept(applicationId: String): AppResult<Response> = respond(applicationId, accept = true)
    suspend fun reject(applicationId: String): AppResult<Response> = respond(applicationId, accept = false)

    private suspend fun respond(applicationId: String, accept: Boolean): AppResult<Response> =
        session.requireUser().flatMap { user ->
            applications.getApplication(applicationId).flatMap { app ->
                val allowedFrom: Set<ApplicationStatus> = when {
                    user.role == Role.CLIENT && app.clientId == user.id -> setOf(ApplicationStatus.PENDING)
                    user.role == Role.WALKER && app.walkerId == user.id -> setOf(ApplicationStatus.COUNTERED)
                    else -> return@flatMap failure(AppError.PermissionDenied)
                }
                if (app.status !in allowedFrom) return@flatMap businessError("Esta postulación ya fue respondida.")
                val result = if (accept) {
                    applications.accept(applicationId, allowedFrom, rejectSiblings = user.role == Role.CLIENT)
                } else {
                    applications.reject(applicationId, allowedFrom)
                }
                result.flatMap {
                    AppResult.Success(
                        when {
                            !accept -> Response.REJECTED
                            app.status == ApplicationStatus.COUNTERED -> Response.COUNTER_OFFER_ACCEPTED
                            else -> Response.ACCEPTED
                        },
                    )
                }
            }
        }

    enum class Response(val message: String) {
        ACCEPTED("Respuesta enviada correctamente."),
        REJECTED("Respuesta enviada correctamente."),
        COUNTER_OFFER_ACCEPTED("¡Contraoferta aceptada! El paseo quedó asignado."),
    }
}

/** route=listByWalker */
class ObserveWalkerApplicationsUseCase @Inject constructor(private val applications: ApplicationRepository) {
    operator fun invoke(walkerId: String): Flow<List<Application>> =
        applications.observeByWalker(walkerId).map { list -> list.sortedByDescending { it.createdAt } }
}

/**
 * Nivel A: si en la lista del cliente hay un ticket con una postulación ACCEPTED y otras aún abiertas
 * (porque el paseador aceptó una contraoferta), las rechaza. El ViewModel del cliente lo llama al recibir la lista.
 * En Nivel B lo reemplaza una Cloud Function onUpdate(tickets).
 */
class ReconcileClientApplicationsUseCase @Inject constructor(private val applications: ApplicationRepository) {
    suspend operator fun invoke(clientApplications: List<Application>) {
        val open = setOf(ApplicationStatus.PENDING, ApplicationStatus.COUNTERED)
        clientApplications.groupBy { it.ticketId }.forEach { (ticketId, apps) ->
            val accepted = apps.firstOrNull { it.status == ApplicationStatus.ACCEPTED } ?: return@forEach
            if (apps.any { it.status in open }) {
                applications.rejectSiblings(ticketId, accepted.clientId, accepted.id)
            }
        }
    }
}

/** route=listByClient */
class ObserveClientApplicationsUseCase @Inject constructor(private val applications: ApplicationRepository) {
    operator fun invoke(clientId: String): Flow<List<Application>> =
        applications.observeByClient(clientId).map { list ->
            // Pendientes primero (son las que requieren acción del cliente).
            list.sortedWith(compareBy<Application> { it.status != ApplicationStatus.PENDING }.thenByDescending { it.createdAt })
        }
}
