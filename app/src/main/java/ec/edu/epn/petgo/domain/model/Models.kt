package ec.edu.epn.petgo.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class Role { CLIENT, WALKER }

data class User(
    val id: String,
    val name: String,
    val lastname: String,
    val email: String,
    val phone: String,
    val role: Role,
    val experience: String = "",
    val available: Boolean = false,
    val active: Boolean = true,
    val photoUrl: String? = null,
) {
    val fullName: String get() = "$name $lastname".trim()
    fun toSummary() = UserSummary(id = id, name = name, lastname = lastname, photoUrl = photoUrl)
    fun toWalkerSummary() = WalkerSummary(id = id, name = name, lastname = lastname, photoUrl = photoUrl, experience = experience)
}

/** Copia desnormalizada de un usuario para listas (evita "joins" en Firestore). */
data class UserSummary(
    val id: String,
    val name: String,
    val lastname: String,
    val photoUrl: String?,
) {
    val fullName: String get() = "$name $lastname".trim()
}

data class WalkerSummary(
    val id: String,
    val name: String,
    val lastname: String,
    val photoUrl: String?,
    val experience: String,
) {
    val fullName: String get() = "$name $lastname".trim()
}

enum class Gender(val label: String) {
    MALE("Macho"), FEMALE("Hembra");

    companion object {
        fun fromLabel(label: String?): Gender? = entries.firstOrNull { it.label == label }
    }
}

data class Pet(
    val id: String,
    val ownerId: String,
    val name: String,
    val race: String,
    val age: Int,
    val weight: Double,
    val behavior: String,
    val gender: Gender,
    val active: Boolean = true,
    val photoUrl: String? = null,
) {
    fun toSummary() = PetSummary(id, name, race, age, gender, behavior, photoUrl)
}

data class PetSummary(
    val id: String,
    val name: String,
    val race: String,
    val age: Int,
    val gender: Gender,
    val behavior: String,
    val photoUrl: String?,
)

enum class TicketStatus {
    PENDING, ASSIGNED, CANCELED, IN_PROGRESS, COMPLETED, REVIEWED;

    /** Transiciones permitidas (derivadas de los botones de client/ticket/list.jsp). */
    fun canTransitionTo(target: TicketStatus): Boolean = when (this) {
        PENDING -> target == ASSIGNED
        ASSIGNED -> target == IN_PROGRESS || target == CANCELED
        IN_PROGRESS -> target == COMPLETED || target == CANCELED
        COMPLETED -> target == REVIEWED
        CANCELED, REVIEWED -> false
    }

    /** Orden de TicketService.getTicketsByUser. */
    val sortOrder: Int
        get() = when (this) {
            IN_PROGRESS -> 0
            ASSIGNED -> 1
            PENDING -> 2
            COMPLETED -> 3
            CANCELED -> 4
            REVIEWED -> 5
        }
}

data class Ticket(
    val id: String,
    val clientId: String,
    val client: UserSummary,
    val pets: List<PetSummary>,
    val date: LocalDate,
    val hour: LocalTime,
    val durationMinutes: Int,
    val status: TicketStatus = TicketStatus.PENDING,
    val deleted: Boolean = false,
    val assignedWalkerId: String? = null,
    val agreedPrice: Double? = null,
) {
    val petIds: List<String> get() = pets.map { it.id }
    fun toSummary() = TicketSummary(date = date, hour = hour, durationMinutes = durationMinutes, petNames = pets.map { it.name })
}

data class TicketSummary(
    val date: LocalDate,
    val hour: LocalTime,
    val durationMinutes: Int,
    val petNames: List<String>,
)

enum class ApplicationStatus { PENDING, ACCEPTED, REJECTED, COUNTERED }

data class Application(
    val id: String,
    val ticketId: String,
    val clientId: String,
    val walkerId: String,
    val client: UserSummary,
    val walker: WalkerSummary,
    val ticket: TicketSummary,
    val offerPrice: Double,
    /** Precio que propuso el paseador; se conserva aunque haya contraoferta (mejora sobre la web). */
    val originalPrice: Double,
    val status: ApplicationStatus = ApplicationStatus.PENDING,
    val createdAt: Instant? = null,
) {
    companion object {
        /** Id determinístico: garantiza una sola postulación por (ticket, paseador). */
        fun idFor(ticketId: String, walkerId: String) = "${ticketId}_$walkerId"
    }
}

data class Review(
    val ticketId: String,
    val clientId: String,
    val walkerId: String,
    val client: UserSummary,
    val stars: Int,
    val punctuality: Int,
    val treatment: Int,
    val comment: String,
    val createdAt: Instant? = null,
)

/** Estadísticas de ReviewService.getStatsForWalker. */
data class WalkerStats(
    val averageStars: Double,
    val averagePunctuality: Double,
    val averageTreatment: Double,
    val totalReviews: Int,
) {
    companion object {
        val EMPTY = WalkerStats(0.0, 0.0, 0.0, 0)

        fun from(reviews: List<Review>): WalkerStats {
            if (reviews.isEmpty()) return EMPTY
            return WalkerStats(
                averageStars = reviews.map { it.stars }.average(),
                averagePunctuality = reviews.map { it.punctuality }.average(),
                averageTreatment = reviews.map { it.treatment }.average(),
                totalReviews = reviews.size,
            )
        }
    }
}

/** Imagen elegida por el usuario (content:// Uri como texto) para no acoplar el dominio a Android. */
@JvmInline
value class LocalImage(val uri: String)
