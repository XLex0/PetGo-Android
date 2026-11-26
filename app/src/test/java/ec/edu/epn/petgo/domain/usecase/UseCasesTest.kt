package ec.edu.epn.petgo.domain.usecase

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.model.TicketSummary
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.model.UserSummary
import ec.edu.epn.petgo.domain.model.WalkerSummary
import ec.edu.epn.petgo.domain.repository.ApplicationRepository
import ec.edu.epn.petgo.domain.repository.AuthRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.UserRepository
import ec.edu.epn.petgo.domain.usecase.application.RespondToApplicationUseCase
import ec.edu.epn.petgo.domain.usecase.auth.LoginUseCase
import ec.edu.epn.petgo.domain.usecase.auth.RegisterUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

private fun user(id: String, role: Role, active: Boolean = true) =
    User(id = id, name = "Ana", lastname = "Pérez", email = "$id@correo.com", phone = "0991234567", role = role, active = active)

class LoginUseCaseTest {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val users = mockk<UserRepository>()
    private val login = LoginUseCase(auth, users)

    @Test fun `usuario inactivo no puede ingresar y se cierra la sesion`() = runTest {
        coEvery { auth.signIn(any(), any()) } returns AppResult.Success("u1")
        coEvery { users.getUser("u1") } returns AppResult.Success(user("u1", Role.CLIENT, active = false))

        val result = login("u1@correo.com", "secreta1")

        assertEquals(AppError.Business(LoginUseCase.INACTIVE_MESSAGE), (result as AppResult.Failure).error)
        coVerify { auth.signOut() }
    }

    @Test fun `campos vacios no llaman a Firebase`() = runTest {
        val result = login("", "")
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
        coVerify(exactly = 0) { auth.signIn(any(), any()) }
    }
}

class RegisterUseCaseTest {
    private val auth = mockk<AuthRepository>(relaxed = true)
    private val users = mockk<UserRepository>()
    private val register = RegisterUseCase(auth, users)

    @Test fun `si falla Firestore se borra la cuenta de Auth`() = runTest {
        coEvery { auth.register(any(), any()) } returns AppResult.Success("u1")
        coEvery { users.createUser(any()) } returns AppResult.Failure(AppError.Network)

        val result = register("Ana", "Pérez", "ana@correo.com", "0991234567", "secreta1", "secreta1", Role.CLIENT, true)

        assertTrue(result is AppResult.Failure)
        coVerify { auth.deleteCurrentAuthUser() }
    }
}

class RespondToApplicationUseCaseTest {
    private val session = mockk<SessionRepository>()
    private val applications = mockk<ApplicationRepository>(relaxed = true)
    private val respond = RespondToApplicationUseCase(session, applications)

    private fun app(status: ApplicationStatus) = Application(
        id = "t1_w1", ticketId = "t1", clientId = "c1", walkerId = "w1",
        client = UserSummary("c1", "Ana", "P", null),
        walker = WalkerSummary("w1", "Luis", "G", null, "exp"),
        ticket = TicketSummary(LocalDate.of(2026, 12, 1), LocalTime.NOON, 60, listOf("Firulais")),
        offerPrice = 10.0, originalPrice = 10.0, status = status,
    )

    @Test fun `cliente acepta postulacion pendiente`() = runTest {
        coEvery { session.requireUser() } returns AppResult.Success(user("c1", Role.CLIENT))
        coEvery { applications.getApplication("t1_w1") } returns AppResult.Success(app(ApplicationStatus.PENDING))
        coEvery { applications.accept("t1_w1", setOf(ApplicationStatus.PENDING), rejectSiblings = true) } returns AppResult.Success(Unit)

        val result = respond.accept("t1_w1")

        assertEquals(AppResult.Success(RespondToApplicationUseCase.Response.ACCEPTED), result)
    }

    @Test fun `paseador solo puede responder contraofertas`() = runTest {
        coEvery { session.requireUser() } returns AppResult.Success(user("w1", Role.WALKER))
        coEvery { applications.getApplication("t1_w1") } returns AppResult.Success(app(ApplicationStatus.PENDING))

        val result = respond.accept("t1_w1")

        assertTrue((result as AppResult.Failure).error is AppError.Business)
        coVerify(exactly = 0) { applications.accept(any(), any(), any()) }
    }

    @Test fun `paseador acepta contraoferta`() = runTest {
        coEvery { session.requireUser() } returns AppResult.Success(user("w1", Role.WALKER))
        coEvery { applications.getApplication("t1_w1") } returns AppResult.Success(app(ApplicationStatus.COUNTERED))
        // El paseador no puede leer postulaciones ajenas → rejectSiblings = false
        coEvery { applications.accept("t1_w1", setOf(ApplicationStatus.COUNTERED), rejectSiblings = false) } returns AppResult.Success(Unit)

        assertEquals(AppResult.Success(RespondToApplicationUseCase.Response.COUNTER_OFFER_ACCEPTED), respond.accept("t1_w1"))
    }

    @Test fun `otro cliente no puede responder`() = runTest {
        coEvery { session.requireUser() } returns AppResult.Success(user("intruso", Role.CLIENT))
        coEvery { applications.getApplication("t1_w1") } returns AppResult.Success(app(ApplicationStatus.PENDING))

        assertEquals(AppError.PermissionDenied, (respond.reject("t1_w1") as AppResult.Failure).error)
    }
}
