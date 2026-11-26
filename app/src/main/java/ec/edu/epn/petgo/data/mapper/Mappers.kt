package ec.edu.epn.petgo.data.mapper

import com.google.firebase.Timestamp
import ec.edu.epn.petgo.data.remote.dto.ApplicationDto
import ec.edu.epn.petgo.data.remote.dto.PetDto
import ec.edu.epn.petgo.data.remote.dto.PetSummaryDto
import ec.edu.epn.petgo.data.remote.dto.ReviewDto
import ec.edu.epn.petgo.data.remote.dto.TicketDto
import ec.edu.epn.petgo.data.remote.dto.TicketSummaryDto
import ec.edu.epn.petgo.data.remote.dto.UserDto
import ec.edu.epn.petgo.data.remote.dto.UserSummaryDto
import ec.edu.epn.petgo.data.remote.dto.WalkerSummaryDto
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.Gender
import ec.edu.epn.petgo.domain.model.Pet
import ec.edu.epn.petgo.domain.model.PetSummary
import ec.edu.epn.petgo.domain.model.Review
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.model.TicketSummary
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.model.UserSummary
import ec.edu.epn.petgo.domain.model.WalkerSummary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/*
 * DTO ↔ dominio. Los mappers "toDomain" devuelven null si el documento está corrupto
 * (p. ej. fecha mal formada) para que una lista no falle completa por un solo documento.
 */

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDate.toIso(): String = toString()
fun LocalTime.toHourString(): String = format(HOUR_FORMAT)
private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
private fun parseHour(value: String): LocalTime? = runCatching { LocalTime.parse(value, HOUR_FORMAT) }.getOrNull()
private fun Timestamp?.toInstantOrNull(): Instant? = this?.toDate()?.toInstant()

fun startTimestamp(date: LocalDate, hour: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Timestamp =
    Timestamp(Date.from(date.atTime(hour).atZone(zone).toInstant()))

private inline fun <reified E : Enum<E>> enumOrNull(value: String): E? = runCatching { enumValueOf<E>(value) }.getOrNull()

// ---------- User ----------
fun UserDto.toDomain(): User? {
    val parsedRole = enumOrNull<Role>(this.role) ?: return null
    return User(id, name, lastname, email, phone, parsedRole, experience, available, active, photoUrl)
}

fun User.toDto() = UserDto(
    id = id, name = name, lastname = lastname, email = email, phone = phone, role = role.name,
    experience = experience, available = available, active = active, photoUrl = photoUrl,
)

fun UserSummaryDto.toDomain() = UserSummary(id, name, lastname, photoUrl)
fun UserSummary.toDto() = UserSummaryDto(id, name, lastname, photoUrl)
fun WalkerSummaryDto.toDomain() = WalkerSummary(id, name, lastname, photoUrl, experience)
fun WalkerSummary.toDto() = WalkerSummaryDto(id, name, lastname, photoUrl, experience)

// ---------- Pet ----------
fun PetDto.toDomain(): Pet? {
    val parsedGender = Gender.fromLabel(this.gender) ?: return null
    return Pet(id, ownerId, name, race, age, weight, behavior, parsedGender, active, photoUrl)
}

fun Pet.toDto() = PetDto(
    id = id, ownerId = ownerId, name = name, race = race, age = age, weight = weight,
    behavior = behavior, gender = gender.label, active = active, photoUrl = photoUrl,
)

fun PetSummaryDto.toDomain(): PetSummary? {
    val parsedGender = Gender.fromLabel(this.gender) ?: return null
    return PetSummary(id, name, race, age, parsedGender, behavior, photoUrl)
}

fun PetSummary.toDto() = PetSummaryDto(id, name, race, age, gender.label, behavior, photoUrl)

// ---------- Ticket ----------
fun TicketDto.toDomain(): Ticket? {
    val parsedDate = parseDate(this.date) ?: return null
    val parsedHour = parseHour(this.hour) ?: return null
    val parsedStatus = enumOrNull<TicketStatus>(this.status) ?: return null
    return Ticket(
        id = id,
        clientId = clientId,
        client = client.toDomain(),
        pets = pets.mapNotNull { it.toDomain() },
        date = parsedDate,
        hour = parsedHour,
        durationMinutes = durationMinutes,
        status = parsedStatus,
        deleted = deleted,
        assignedWalkerId = assignedWalkerId,
        agreedPrice = agreedPrice,
    )
}

fun Ticket.toDto() = TicketDto(
    id = id,
    clientId = clientId,
    client = client.toDto(),
    petIds = petIds,
    pets = pets.map { it.toDto() },
    date = date.toIso(),
    hour = hour.toHourString(),
    startAt = startTimestamp(date, hour),
    durationMinutes = durationMinutes,
    status = status.name,
    deleted = deleted,
    assignedWalkerId = assignedWalkerId,
    agreedPrice = agreedPrice,
)

fun TicketSummaryDto.toDomain(): TicketSummary? {
    val parsedDate = parseDate(this.date) ?: return null
    val parsedHour = parseHour(this.hour) ?: return null
    return TicketSummary(parsedDate, parsedHour, durationMinutes, petNames)
}

fun TicketSummary.toDto() = TicketSummaryDto(date.toIso(), hour.toHourString(), durationMinutes, petNames)

// ---------- Application ----------
fun ApplicationDto.toDomain(): Application? {
    val parsedStatus = enumOrNull<ApplicationStatus>(this.status) ?: return null
    val parsedTicket = this.ticket.toDomain() ?: return null
    return Application(
        id = id,
        ticketId = ticketId,
        clientId = clientId,
        walkerId = walkerId,
        client = client.toDomain(),
        walker = walker.toDomain(),
        ticket = parsedTicket,
        offerPrice = offerPrice,
        originalPrice = originalPrice,
        status = parsedStatus,
        createdAt = createdAt.toInstantOrNull(),
    )
}

fun Application.toDto() = ApplicationDto(
    id = id,
    ticketId = ticketId,
    clientId = clientId,
    walkerId = walkerId,
    client = client.toDto(),
    walker = walker.toDto(),
    ticket = ticket.toDto(),
    offerPrice = offerPrice,
    originalPrice = originalPrice,
    status = status.name,
)

// ---------- Review ----------
fun ReviewDto.toDomain() = Review(
    ticketId = ticketId.ifEmpty { id },
    clientId = clientId,
    walkerId = walkerId,
    client = client.toDomain(),
    stars = stars,
    punctuality = punctuality,
    treatment = treatment,
    comment = comment,
    createdAt = createdAt.toInstantOrNull(),
)

fun Review.toDto() = ReviewDto(
    id = ticketId,
    ticketId = ticketId,
    clientId = clientId,
    walkerId = walkerId,
    client = client.toDto(),
    stars = stars,
    punctuality = punctuality,
    treatment = treatment,
    comment = comment,
)
