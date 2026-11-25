package ec.edu.epn.petgo.domain.model

import ec.edu.epn.petgo.domain.usecase.ticket.TicketSorting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TicketStatusTest {
    @Test fun `transiciones validas del flujo del cliente`() {
        assertTrue(TicketStatus.PENDING.canTransitionTo(TicketStatus.ASSIGNED))
        assertTrue(TicketStatus.ASSIGNED.canTransitionTo(TicketStatus.IN_PROGRESS))
        assertTrue(TicketStatus.ASSIGNED.canTransitionTo(TicketStatus.CANCELED))
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.COMPLETED))
        assertTrue(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.CANCELED))
        assertTrue(TicketStatus.COMPLETED.canTransitionTo(TicketStatus.REVIEWED))
    }

    @Test fun `transiciones invalidas`() {
        assertFalse(TicketStatus.PENDING.canTransitionTo(TicketStatus.IN_PROGRESS))
        assertFalse(TicketStatus.COMPLETED.canTransitionTo(TicketStatus.CANCELED))
        assertFalse(TicketStatus.CANCELED.canTransitionTo(TicketStatus.PENDING))
        assertFalse(TicketStatus.REVIEWED.canTransitionTo(TicketStatus.COMPLETED))
    }
}

class TicketSortingTest {
    private fun ticket(id: String, status: TicketStatus, day: Int) = Ticket(
        id = id, clientId = "c", client = UserSummary("c", "Ana", "P", null), pets = emptyList(),
        date = LocalDate.of(2026, 12, day), hour = LocalTime.NOON, durationMinutes = 60, status = status,
    )

    @Test fun `orden igual que TicketService_getTicketsByUser`() {
        val sorted = listOf(
            ticket("canceled", TicketStatus.CANCELED, 1),
            ticket("pending", TicketStatus.PENDING, 1),
            ticket("completed", TicketStatus.COMPLETED, 1),
            ticket("progress", TicketStatus.IN_PROGRESS, 1),
            ticket("assigned", TicketStatus.ASSIGNED, 1),
        ).sortedWith(TicketSorting.byStatusThenDate).map { it.id }
        assertEquals(listOf("progress", "assigned", "pending", "completed", "canceled"), sorted)
    }
}

class WalkerStatsTest {
    private fun review(stars: Int, p: Int, t: Int) =
        Review("t$stars$p$t", "c", "w", UserSummary("c", "A", "B", null), stars, p, t, "")

    @Test fun `sin reviews devuelve ceros`() = assertEquals(WalkerStats.EMPTY, WalkerStats.from(emptyList()))

    @Test fun `promedios`() {
        val stats = WalkerStats.from(listOf(review(5, 4, 3), review(3, 2, 5)))
        assertEquals(4.0, stats.averageStars, 0.001)
        assertEquals(3.0, stats.averagePunctuality, 0.001)
        assertEquals(4.0, stats.averageTreatment, 0.001)
        assertEquals(2, stats.totalReviews)
    }
}

class PetBreedsTest {
    @Test fun `33 razas de la web`() = assertEquals(33, PetBreeds.ALL.size)
    @Test fun `display name`() = assertEquals("Pastor Alemán", PetBreeds.displayName("pastor alemán"))
}
