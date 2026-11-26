package ec.edu.epn.petgo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ec.edu.epn.petgo.core.designsystem.PetGoDanger
import ec.edu.epn.petgo.core.designsystem.PetGoGrayText
import ec.edu.epn.petgo.core.designsystem.PetGoInfo
import ec.edu.epn.petgo.core.designsystem.PetGoPrimary
import ec.edu.epn.petgo.core.designsystem.PetGoSecondary
import ec.edu.epn.petgo.core.designsystem.PetGoWarningDark
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.TicketStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale.forLanguageTag("es-EC")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d 'de' MMM", ES)
private val DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("EEE d 'de' MMM yyyy", ES)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", ES)

object Format {
    fun date(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> (if (date.year == today.year) date.format(DAY_FORMAT) else date.format(DAY_YEAR_FORMAT))
            .replaceFirstChar { it.uppercaseChar() }
    }

    fun time(time: LocalTime): String = time.format(TIME_FORMAT)

    fun dateTime(date: LocalDate, time: LocalTime): String = "${date(date)} · ${time(time)}"

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun price(value: Double): String = "$" + String.format(Locale.US, "%.2f", value)

    fun rating(value: Double): String = String.format(Locale.US, "%.1f", value)

    fun age(years: Int): String = if (years == 1) "1 año" else "$years años"

    fun weight(kg: Double): String = (if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)) + " kg"
}

/** Texto + color de cada estado (equivale a los badges de client/ticket/list.jsp). */
data class StatusStyle(val label: String, val color: Color)

@Composable
fun TicketStatus.style(): StatusStyle = when (this) {
    TicketStatus.PENDING -> StatusStyle("Buscando paseador", PetGoWarningDark)
    TicketStatus.ASSIGNED -> StatusStyle("Asignado", PetGoInfo)
    TicketStatus.IN_PROGRESS -> StatusStyle("En curso", PetGoSecondary)
    TicketStatus.COMPLETED -> StatusStyle("Por calificar", PetGoPrimary)
    TicketStatus.REVIEWED -> StatusStyle("Finalizado", PetGoGrayText)
    TicketStatus.CANCELED -> StatusStyle("Cancelado", PetGoDanger)
}

@Composable
fun ApplicationStatus.style(): StatusStyle = when (this) {
    ApplicationStatus.PENDING -> StatusStyle("Pendiente", PetGoWarningDark)
    ApplicationStatus.COUNTERED -> StatusStyle("Contraoferta", PetGoInfo)
    ApplicationStatus.ACCEPTED -> StatusStyle("Aceptada", PetGoPrimary)
    ApplicationStatus.REJECTED -> StatusStyle("Rechazada", PetGoDanger)
}
