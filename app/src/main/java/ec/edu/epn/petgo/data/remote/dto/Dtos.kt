package ec.edu.epn.petgo.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/*
 * Documentos de Firestore. Reglas para que toObject() funcione:
 *  - `var` con valor por defecto en TODOS los campos (constructor vacío + setters).
 *  - Ningún campo booleano empieza con "is" (Firestore lo renombraría).
 *  - Enums se guardan como String (no ordinal; ver bug 8.4 de la guía).
 * El esquema completo está documentado en docs/MIGRACION_ANDROID.md §2.2.
 */

object Collections {
    const val USERS = "users"
    const val PETS = "pets"
    const val TICKETS = "tickets"
    const val APPLICATIONS = "applications"
    const val REVIEWS = "reviews"
}

data class UserDto(
    @DocumentId var id: String = "",
    var name: String = "",
    var lastname: String = "",
    var email: String = "",
    var phone: String = "",
    var role: String = "CLIENT",
    var experience: String = "",
    var available: Boolean = false,
    var active: Boolean = true,
    var photoUrl: String? = null,
    @ServerTimestamp var createdAt: Timestamp? = null,
)

data class UserSummaryDto(
    var id: String = "",
    var name: String = "",
    var lastname: String = "",
    var photoUrl: String? = null,
)

data class WalkerSummaryDto(
    var id: String = "",
    var name: String = "",
    var lastname: String = "",
    var photoUrl: String? = null,
    var experience: String = "",
)

data class PetDto(
    @DocumentId var id: String = "",
    var ownerId: String = "",
    var name: String = "",
    var race: String = "",
    var age: Int = 0,
    var weight: Double = 0.0,
    var behavior: String = "",
    var gender: String = "",
    var active: Boolean = true,
    var photoUrl: String? = null,
    @ServerTimestamp var createdAt: Timestamp? = null,
)

data class PetSummaryDto(
    var id: String = "",
    var name: String = "",
    var race: String = "",
    var age: Int = 0,
    var gender: String = "",
    var behavior: String = "",
    var photoUrl: String? = null,
)

data class TicketDto(
    @DocumentId var id: String = "",
    var clientId: String = "",
    var client: UserSummaryDto = UserSummaryDto(),
    var petIds: List<String> = emptyList(),
    var pets: List<PetSummaryDto> = emptyList(),
    /** ISO yyyy-MM-dd */
    var date: String = "",
    /** HH:mm */
    var hour: String = "",
    /** date+hour en la zona del dispositivo; para ordenar/filtrar en consultas. */
    var startAt: Timestamp? = null,
    var durationMinutes: Int = 0,
    var status: String = "PENDING",
    var deleted: Boolean = false,
    var assignedWalkerId: String? = null,
    var agreedPrice: Double? = null,
    @ServerTimestamp var createdAt: Timestamp? = null,
    @ServerTimestamp var updatedAt: Timestamp? = null,
)

data class TicketSummaryDto(
    var date: String = "",
    var hour: String = "",
    var durationMinutes: Int = 0,
    var petNames: List<String> = emptyList(),
)

data class ApplicationDto(
    @DocumentId var id: String = "",
    var ticketId: String = "",
    var clientId: String = "",
    var walkerId: String = "",
    var client: UserSummaryDto = UserSummaryDto(),
    var walker: WalkerSummaryDto = WalkerSummaryDto(),
    var ticket: TicketSummaryDto = TicketSummaryDto(),
    var offerPrice: Double = 0.0,
    var originalPrice: Double = 0.0,
    var status: String = "PENDING",
    @ServerTimestamp var createdAt: Timestamp? = null,
    @ServerTimestamp var updatedAt: Timestamp? = null,
)

/** Id del documento = ticketId → una sola reseña por paseo. */
data class ReviewDto(
    @DocumentId var id: String = "",
    var ticketId: String = "",
    var clientId: String = "",
    var walkerId: String = "",
    var client: UserSummaryDto = UserSummaryDto(),
    var stars: Int = 0,
    var punctuality: Int = 0,
    var treatment: Int = 0,
    var comment: String = "",
    @ServerTimestamp var createdAt: Timestamp? = null,
)
