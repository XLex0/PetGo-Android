package ec.edu.epn.petgo.domain.repository

import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
