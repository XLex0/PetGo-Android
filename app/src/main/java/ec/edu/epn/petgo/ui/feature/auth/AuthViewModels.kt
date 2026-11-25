package ec.edu.epn.petgo.ui.feature.auth

import dagger.hilt.android.lifecycle.HiltViewModel
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.usecase.auth.LoginUseCase
import ec.edu.epn.petgo.domain.usecase.auth.RegisterUseCase
import ec.edu.epn.petgo.domain.usecase.auth.ResetPasswordUseCase
import ec.edu.epn.petgo.domain.validation.ValidationResult
import ec.edu.epn.petgo.ui.common.PetGoViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LoginForm(
    val email: String = "",
    val password: String = "",
    val errors: ValidationResult = ValidationResult.VALID,
)

/** loginController (POST) + el link "¿Olvidaste tu contraseña?" que en la web no hacía nada. */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
    private val resetPassword: ResetPasswordUseCase,
) : PetGoViewModel() {

    private val _form = MutableStateFlow(LoginForm())
    val form: StateFlow<LoginForm> = _form.asStateFlow()

    fun onEmail(value: String) = _form.update { it.copy(email = value, errors = ValidationResult.VALID) }
    fun onPassword(value: String) = _form.update { it.copy(password = value, errors = ValidationResult.VALID) }

    /** Al tener éxito la sesión cambia y PetGoRoot navega solo al inicio del rol. */
    fun submit() {
        val f = _form.value
        runAction(onValidation = { errors -> _form.update { it.copy(errors = errors) } }) { login(f.email, f.password) }
    }

    fun sendReset(email: String, onSent: () -> Unit) {
        runAction(
            successMessage = "Te enviamos un correo para restablecer tu contraseña.",
            onSuccess = { onSent() },
        ) { resetPassword(email) }
    }
}

data class RegisterForm(
    val role: Role? = null,
    val name: String = "",
    val lastname: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptedTerms: Boolean = false,
    val errors: ValidationResult = ValidationResult.VALID,
)

/** UserController?route=create */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : PetGoViewModel() {

    private val _form = MutableStateFlow(RegisterForm())
    val form: StateFlow<RegisterForm> = _form.asStateFlow()

    fun init(role: String?) {
        if (_form.value.role == null && role != null) {
            _form.update { it.copy(role = runCatching { Role.valueOf(role) }.getOrNull()) }
        }
    }

    /** Edita el formulario y borra el error del campo tocado (validación "en vivo" suave). */
    fun edit(field: String, transform: (RegisterForm) -> RegisterForm) = _form.update { current ->
        val updated = transform(current)
        updated.copy(errors = ValidationResult(current.errors.errors - field))
    }

    fun submit() {
        val f = _form.value
        runAction(
            successMessage = "¡Bienvenido a PetGo, ${f.name.trim()}!",
            onValidation = { errors -> _form.update { it.copy(errors = errors) } },
        ) {
            register(f.name, f.lastname, f.email, f.phone, f.password, f.confirmPassword, f.role, f.acceptedTerms)
        }
    }
}
