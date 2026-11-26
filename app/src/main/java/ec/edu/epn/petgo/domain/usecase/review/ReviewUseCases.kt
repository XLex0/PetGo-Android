package ec.edu.epn.petgo.domain.usecase.review

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.businessError
import ec.edu.epn.petgo.core.result.failure
import ec.edu.epn.petgo.core.result.flatMap
import ec.edu.epn.petgo.domain.model.Review
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.model.WalkerStats
import ec.edu.epn.petgo.domain.repository.ReviewRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.TicketRepository
import ec.edu.epn.petgo.domain.validation.ReviewValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** TicketController?route=review → ReviewService.rateTicket */
class RateWalkUseCase @Inject constructor(
    private val session: SessionRepository,
    private val tickets: TicketRepository,
    private val reviews: ReviewRepository,
) {
    suspend operator fun invoke(ticketId: String, stars: Int, punctuality: Int, treatment: Int, comment: String): AppResult<Unit> {
        val validation = ReviewValidator.validate(stars, punctuality, treatment, comment)
        if (!validation.isValid) return failure(AppError.Validation(validation))

        return session.requireUser().flatMap { user ->
            tickets.getTicket(ticketId).flatMap { ticket ->
                val walkerId = ticket.assignedWalkerId
                when {
                    ticket.clientId != user.id -> failure(AppError.PermissionDenied)
                    ticket.status == TicketStatus.REVIEWED -> businessError("Este paseo ya fue calificado.")
                    ticket.status != TicketStatus.COMPLETED -> businessError("Solo puedes calificar paseos completados.")
                    walkerId == null -> businessError("Este paseo no tiene un paseador asignado.")
                    else -> reviews.rate(
                        Review(
                            ticketId = ticketId,
                            clientId = user.id,
                            walkerId = walkerId,
                            client = user.toSummary(),
                            stars = stars,
                            punctuality = punctuality,
                            treatment = treatment,
                            comment = comment.trim(),
                        ),
                    )
                }
            }
        }
    }
}

data class WalkerReviews(val stats: WalkerStats, val reviews: List<Review>)

/** TicketController?route=reviewsWalker → ReviewService.getStatsForWalker */
class ObserveWalkerReviewsUseCase @Inject constructor(private val reviews: ReviewRepository) {
    operator fun invoke(walkerId: String): Flow<WalkerReviews> =
        reviews.observeByWalker(walkerId).map { list ->
            val sorted = list.sortedByDescending { it.createdAt }
            WalkerReviews(stats = WalkerStats.from(sorted), reviews = sorted)
        }
}
