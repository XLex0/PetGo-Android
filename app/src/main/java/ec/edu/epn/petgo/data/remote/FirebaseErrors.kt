package ec.edu.epn.petgo.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Excepción para cortar una transacción de Firestore con un error de dominio. */
class DomainException(val error: AppError) : RuntimeException(error.toString())

fun businessAbort(message: String): Nothing = throw DomainException(AppError.Business(message))
fun abort(error: AppError): Nothing = throw DomainException(error)

/** Envuelve cualquier llamada a Firebase y traduce sus excepciones a AppError. */
suspend inline fun <T> firebaseCall(crossinline block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.toAppError())
    }

fun Throwable.toAppError(): AppError {
    // Las transacciones envuelven la excepción original: buscar en la cadena de causas.
    generateSequence(this) { it.cause }.firstOrNull { it is DomainException }?.let { return (it as DomainException).error }

    return when (this) {
        is FirebaseNetworkException -> AppError.Network
        is FirebaseAuthUserCollisionException -> AppError.Business("Ya existe una cuenta registrada con este correo.")
        is FirebaseAuthWeakPasswordException -> AppError.Business("La contraseña es demasiado débil (mínimo 6 caracteres).")
        is FirebaseAuthRecentLoginRequiredException -> AppError.Business("Por seguridad, vuelve a iniciar sesión e inténtalo de nuevo.")
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> AppError.Business("Correo o contraseña incorrectos, o usuario inactivo.")
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.PermissionDenied
            FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Network
            else -> AppError.Unknown(this)
        }
        is IOException -> AppError.Network // OkHttp (Cloudinary) sin conexión / timeout
        else -> AppError.Unknown(this)
    }
}
