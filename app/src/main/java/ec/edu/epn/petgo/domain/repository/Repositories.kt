package ec.edu.epn.petgo.domain.repository

import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.model.Pet
import ec.edu.epn.petgo.domain.model.Review
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime

/*
 * Contratos que la capa data implementa con Firebase. Equivalen a los DAOs del proyecto web,
 * pero devuelven AppResult/Flow en lugar de boolean/null.
 */

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

interface AuthRepository {
    /** uid actual o null. */
    val currentUserId: String?
    val authState: Flow<String?>

    suspend fun signIn(email: String, password: String): AppResult<String>
    suspend fun register(email: String, password: String): AppResult<String>
    suspend fun signOut()
    suspend fun sendPasswordReset(email: String): AppResult<Unit>
    suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit>
    suspend fun requestEmailChange(newEmail: String): AppResult<Unit>
    /** Rollback de registro si falla la creación del perfil en Firestore. */
    suspend fun deleteCurrentAuthUser()
}

interface SessionRepository {
    /** Reemplaza HttpSession.getAttribute("user"): se actualiza en vivo si cambia el doc del usuario. */
    val session: StateFlow<SessionState>
    suspend fun requireUser(): AppResult<User>
}

interface UserRepository {
    fun observeUser(uid: String): Flow<User?>
    suspend fun getUser(uid: String): AppResult<User>
    suspend fun createUser(user: User): AppResult<Unit>
    suspend fun updatePersonalData(uid: String, name: String, lastname: String, phone: String): AppResult<Unit>
    suspend fun updateWalkerProfile(uid: String, experience: String, available: Boolean): AppResult<Unit>
    suspend fun updatePhotoUrl(uid: String, photoUrl: String): AppResult<Unit>
    /** Sincroniza el email del perfil tras verificarlo en Firebase Auth. */
    suspend fun updateEmail(uid: String, email: String): AppResult<Unit>
    suspend fun deactivate(uid: String): AppResult<Unit>
}

interface ImageRepository {
    /** Comprime y sube a Storage; devuelve la URL de descarga. */
    suspend fun uploadUserPhoto(uid: String, image: LocalImage): AppResult<String>
    suspend fun uploadPetPhoto(ownerId: String, petId: String, image: LocalImage): AppResult<String>
}

interface PetRepository {
    fun newPetId(): String
    fun observeActivePets(ownerId: String): Flow<List<Pet>>
    suspend fun getActivePets(ownerId: String): AppResult<List<Pet>>
    suspend fun createPet(pet: Pet): AppResult<Unit>
    suspend fun deactivate(petId: String): AppResult<Unit>
}

interface TicketRepository {
    fun observeClientTickets(clientId: String): Flow<List<Ticket>>
    /** Equivale a TicketDAO.findAllTickets (status PENDING, no borrados). */
    fun observeAvailableTickets(): Flow<List<Ticket>>
    /** NUEVO: paseos asignados a un paseador (la web no tenía esta vista). */
    fun observeWalkerTickets(walkerId: String): Flow<List<Ticket>>
    fun observeTicket(ticketId: String): Flow<Ticket?>
    suspend fun getTicket(ticketId: String): AppResult<Ticket>
    suspend fun createTicket(ticket: Ticket): AppResult<String>
    suspend fun updateSchedule(ticketId: String, date: LocalDate, hour: LocalTime, durationMinutes: Int): AppResult<Unit>
    /** Borrado lógico + elimina sus postulaciones (TicketService.suspendTicket) en un solo batch. */
    suspend fun softDelete(ticketId: String): AppResult<Unit>
    /** Cambia el estado dentro de una transacción validando el estado actual. */
    suspend fun changeStatus(ticketId: String, expectedClientId: String, target: TicketStatus): AppResult<Unit>
}

interface ApplicationRepository {
    /** Falla con AppError.Business si ya existe (una postulación por paseador y ticket). */
    suspend fun create(application: Application): AppResult<Unit>
    fun observeByWalker(walkerId: String): Flow<List<Application>>
    fun observeByClient(clientId: String): Flow<List<Application>>
    suspend fun getApplication(applicationId: String): AppResult<Application>
    suspend fun counterOffer(applicationId: String, newPrice: Double): AppResult<Unit>
    suspend fun reject(applicationId: String, allowedFrom: Set<ApplicationStatus>): AppResult<Unit>
    /**
     * Transacción: esta → ACCEPTED y ticket → ASSIGNED con assignedWalkerId y agreedPrice.
     * Si [rejectSiblings] (solo el cliente puede leer todas las postulaciones de su ticket),
     * las demás postulaciones abiertas del ticket pasan a REJECTED en la misma transacción.
     */
    suspend fun accept(applicationId: String, allowedFrom: Set<ApplicationStatus>, rejectSiblings: Boolean): AppResult<Unit>

    /**
     * Nivel A (sin Cloud Functions): cuando el PASEADOR acepta una contraoferta no puede tocar
     * las postulaciones de otros; el dispositivo del cliente las rechaza después con esto.
     */
    suspend fun rejectSiblings(ticketId: String, clientId: String, acceptedApplicationId: String): AppResult<Unit>
}

interface ReviewRepository {
    /** Transacción: crea reviews/{ticketId} y pasa el ticket de COMPLETED a REVIEWED. */
    suspend fun rate(review: Review): AppResult<Unit>
    fun observeByWalker(walkerId: String): Flow<List<Review>>
}
