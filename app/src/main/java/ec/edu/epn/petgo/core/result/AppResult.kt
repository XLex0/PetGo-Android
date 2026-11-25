package ec.edu.epn.petgo.core.result

import ec.edu.epn.petgo.domain.validation.ValidationResult

/** Resultado de cualquier operación de dominio/datos. Reemplaza los `boolean` + `session.message` del backend web. */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    /** Errores por campo (equivale a ValidationResult del proyecto web). */
    data class Validation(val result: ValidationResult) : AppError

    /** Regla de negocio incumplida; `message` se muestra tal cual al usuario. */
    data class Business(val message: String) : AppError

    data object NotAuthenticated : AppError
    data object PermissionDenied : AppError
    data object NotFound : AppError
    data object Network : AppError
    data class Unknown(val cause: Throwable? = null) : AppError
}

fun <T> success(data: T): AppResult<T> = AppResult.Success(data)
fun failure(error: AppError): AppResult<Nothing> = AppResult.Failure(error)
fun businessError(message: String): AppResult<Nothing> = AppResult.Failure(AppError.Business(message))

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

/** Mensaje legible en español para mostrar en Snackbar/diálogo. */
fun AppError.userMessage(): String = when (this) {
    is AppError.Validation -> result.errors.values.firstOrNull() ?: "Revisa los datos ingresados."
    is AppError.Business -> message
    AppError.NotAuthenticated -> "Tu sesión expiró. Vuelve a iniciar sesión."
    AppError.PermissionDenied -> "No tienes permiso para realizar esta acción."
    AppError.NotFound -> "El recurso solicitado no existe."
    AppError.Network -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    is AppError.Unknown -> "Ocurrió un error inesperado. Inténtalo de nuevo."
}
