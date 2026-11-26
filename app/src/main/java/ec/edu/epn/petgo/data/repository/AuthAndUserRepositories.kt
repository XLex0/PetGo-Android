package ec.edu.epn.petgo.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.failure
import ec.edu.epn.petgo.data.mapper.toDomain
import ec.edu.epn.petgo.data.mapper.toDto
import ec.edu.epn.petgo.data.remote.abort
import ec.edu.epn.petgo.data.remote.dto.Collections
import ec.edu.epn.petgo.data.remote.dto.UserDto
import ec.edu.epn.petgo.data.remote.firebaseCall
import ec.edu.epn.petgo.di.ApplicationScope
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.repository.AuthRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.SessionState
import ec.edu.epn.petgo.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.firestore.snapshots

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val currentUserId: String? get() = auth.currentUser?.uid

    override val authState: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): AppResult<String> = firebaseCall {
        auth.signInWithEmailAndPassword(email, password).await().user?.uid ?: abort(AppError.NotAuthenticated)
    }

    override suspend fun register(email: String, password: String): AppResult<String> = firebaseCall {
        auth.createUserWithEmailAndPassword(email, password).await().user?.uid ?: abort(AppError.NotAuthenticated)
    }

    override suspend fun signOut() = auth.signOut()

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> = firebaseCall {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit> = firebaseCall {
        val user = auth.currentUser ?: abort(AppError.NotAuthenticated)
        val email = user.email ?: abort(AppError.NotAuthenticated)
        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        user.updatePassword(newPassword).await()
        Unit
    }

    override suspend fun requestEmailChange(newEmail: String): AppResult<Unit> = firebaseCall {
        val user = auth.currentUser ?: abort(AppError.NotAuthenticated)
        user.verifyBeforeUpdateEmail(newEmail).await()
        Unit
    }

    override suspend fun deleteCurrentAuthUser() {
        runCatching { auth.currentUser?.delete()?.await() }
    }

    /** Email verificado actual en Auth (puede diferir del perfil tras verifyBeforeUpdateEmail). */
    fun currentAuthEmail(): String? = auth.currentUser?.email
}

@Singleton
class FirestoreUserRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : UserRepository {

    private fun doc(uid: String) = db.collection(Collections.USERS).document(uid)

    override fun observeUser(uid: String): Flow<User?> =
        doc(uid).snapshots()
            .map { snapshot -> snapshot.toObject(UserDto::class.java)?.toDomain() }
            .catch { emit(null) }

    override suspend fun getUser(uid: String): AppResult<User> = firebaseCall {
        doc(uid).get().await().toObject(UserDto::class.java)?.toDomain() ?: abort(AppError.NotFound)
    }

    override suspend fun createUser(user: User): AppResult<Unit> = firebaseCall {
        doc(user.id).set(user.toDto()).await()
        Unit
    }

    override suspend fun updatePersonalData(uid: String, name: String, lastname: String, phone: String) =
        update(uid, mapOf("name" to name, "lastname" to lastname, "phone" to phone))

    override suspend fun updateWalkerProfile(uid: String, experience: String, available: Boolean) =
        update(uid, mapOf("experience" to experience, "available" to available))

    override suspend fun updatePhotoUrl(uid: String, photoUrl: String) = update(uid, mapOf("photoUrl" to photoUrl))

    override suspend fun updateEmail(uid: String, email: String) = update(uid, mapOf("email" to email))

    override suspend fun deactivate(uid: String) = update(uid, mapOf("active" to false))

    private suspend fun update(uid: String, fields: Map<String, Any>): AppResult<Unit> = firebaseCall {
        doc(uid).update(fields + ("updatedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }
}

/**
 * Reemplaza HttpSession: combina el estado de Firebase Auth con el documento users/{uid} en vivo.
 * Un usuario con active=false se trata como sesión cerrada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirebaseSessionRepository @Inject constructor(
    private val auth: FirebaseAuthRepository,
    private val users: UserRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : SessionRepository {

    override val session: StateFlow<SessionState> = auth.authState
        .flatMapLatest { uid ->
            if (uid == null) flowOf(SessionState.SignedOut)
            else users.observeUser(uid).map { user ->
                if (user == null || !user.active) SessionState.SignedOut else SessionState.SignedIn(user)
            }
        }
        .onEach { state -> if (state is SessionState.SignedIn) syncEmailIfVerified(state.user) }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    override suspend fun requireUser(): AppResult<User> {
        val state = withTimeoutOrNull(10_000) { session.first { it !is SessionState.Loading } }
        return (state as? SessionState.SignedIn)?.let { AppResult.Success(it.user) } ?: failure(AppError.NotAuthenticated)
    }

    private fun syncEmailIfVerified(user: User) {
        val authEmail = auth.currentAuthEmail() ?: return
        if (!authEmail.equals(user.email, ignoreCase = true)) {
            scope.launch { users.updateEmail(user.id, authEmail.lowercase()) }
        }
    }
}
