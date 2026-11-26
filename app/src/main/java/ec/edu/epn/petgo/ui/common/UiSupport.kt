package ec.edu.epn.petgo.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.userMessage
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.SessionState
import ec.edu.epn.petgo.domain.validation.ValidationResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reemplaza los SweetAlert disparados con session.message en la web. */
data class UiMessage(val text: String, val isError: Boolean = false)

/** Snackbar compartido por el Scaffold del rol (cliente/paseador/auth). */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * ViewModel base: mensajes de una sola vez, estado "ocupado" y manejo uniforme de AppResult.
 */
abstract class PetGoViewModel : ViewModel() {
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    /** true mientras corre una acción (deshabilita botones y muestra progreso). */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    protected fun showMessage(text: String) {
        _messages.trySend(UiMessage(text))
    }

    protected fun showError(error: AppError) {
        _messages.trySend(UiMessage(error.userMessage(), isError = true))
    }

    /**
     * Ejecuta una acción que devuelve AppResult:
     * - Validation → [onValidation] (errores por campo en el formulario), si se pasa; si no, Snackbar.
     * - Otro error → Snackbar.
     * - Éxito → [successMessage] (si hay) + [onSuccess].
     */
    protected fun <T> runAction(
        successMessage: String? = null,
        onValidation: ((ValidationResult) -> Unit)? = null,
        onSuccess: (T) -> Unit = {},
        action: suspend () -> AppResult<T>,
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = action()) {
                    is AppResult.Success -> {
                        successMessage?.let(::showMessage)
                        onSuccess(result.data)
                    }
                    is AppResult.Failure -> {
                        val error = result.error
                        if (error is AppError.Validation && onValidation != null) onValidation(error.result)
                        else showError(error)
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }
}

/** Muestra en el Snackbar los mensajes del ViewModel mientras la pantalla está visible. */
@Composable
fun CollectMessages(messages: Flow<UiMessage>, snackbar: SnackbarHostState = LocalSnackbarHostState.current) {
    LaunchedEffect(messages, snackbar) {
        messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                message = if (msg.isError) "⚠ ${msg.text}" else "✓ ${msg.text}",
                withDismissAction = true,
                duration = if (msg.isError) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
}

/** Usuario autenticado (o null) como StateFlow para la UI. */
fun SessionRepository.currentUserFlow(vm: ViewModel): StateFlow<User?> =
    session.map { (it as? SessionState.SignedIn)?.user }
        .stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), (session.value as? SessionState.SignedIn)?.user)

/** uid del usuario autenticado; emite solo cuando cambia. Útil para flatMapLatest. */
fun SessionRepository.userIdFlow(): Flow<String> =
    session.filterIsInstance<SessionState.SignedIn>().map { it.user.id }.distinctUntilChanged()

/** Estado genérico de una lista cargada en vivo desde Firestore. */
sealed interface ListState<out T> {
    data object Loading : ListState<Nothing>
    data class Ready<T>(val items: List<T>) : ListState<T>
}
