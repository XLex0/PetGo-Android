package ec.edu.epn.petgo.domain.usecase.auth

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.businessError
import ec.edu.epn.petgo.core.result.failure
import ec.edu.epn.petgo.core.result.success
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.repository.AuthRepository
import ec.edu.epn.petgo.domain.repository.UserRepository
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.domain.validation.LoginValidator
import ec.edu.epn.petgo.domain.validation.RegistrationValidator
import ec.edu.epn.petgo.domain.validation.ValidationResult
import javax.inject.Inject

/** UserController?route=create → UserService.createUser */
class RegisterUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(
        name: String,
        lastname: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        role: Role?,
        acceptedTerms: Boolean,
    ): AppResult<User> {
        val validation = RegistrationValidator.validate(name, lastname, email, phone, password, confirmPassword, role, acceptedTerms)
        if (!validation.isValid) return failure(AppError.Validation(validation))

        val normalizedEmail = email.trim().lowercase()
        val uid = when (val result = auth.register(normalizedEmail, password)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val user = User(
            id = uid,
            name = name.trim(),
            lastname = lastname.trim(),
            email = normalizedEmail,
            phone = phone.trim(),
            role = role!!,
        )
        return when (val created = users.createUser(user)) {
            is AppResult.Success -> success(user)
            is AppResult.Failure -> {
                auth.deleteCurrentAuthUser() // rollback: no dejar cuentas sin perfil
                created
            }
        }
    }
}

/** loginController POST → UserService.login */
class LoginUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<User> {
        val validation = LoginValidator.validate(email, password)
        if (!validation.isValid) return failure(AppError.Validation(validation))

        val uid = when (val result = auth.signIn(email.trim(), password)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        return when (val userResult = users.getUser(uid)) {
            is AppResult.Success -> {
                if (!userResult.data.active) {
                    auth.signOut()
                    businessError(INACTIVE_MESSAGE)
                } else userResult
            }
            is AppResult.Failure -> {
                auth.signOut()
                userResult
            }
        }
    }

    companion object {
        const val INACTIVE_MESSAGE = "Correo o contraseña incorrectos, o usuario inactivo."
    }
}

class LogoutUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.signOut()
}

/** MEJORA: el link "¿Olvidaste tu contraseña?" de login.jsp no hacía nada. */
class ResetPasswordUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): AppResult<Unit> {
        val validation = LoginValidator.validate(email, password = "x")
        if (validation.hasError(Fields.EMAIL)) {
            return failure(AppError.Validation(ValidationResult(mapOf(Fields.EMAIL to validation.error(Fields.EMAIL)!!))))
        }
        return auth.sendPasswordReset(email.trim())
    }
}

/** UserController?route=suspend y WalkerController?route=suspendAccount (soft delete + logout). */
class SuspendAccountUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val uid = auth.currentUserId ?: return failure(AppError.NotAuthenticated)
        val result = users.deactivate(uid)
        if (result is AppResult.Success) auth.signOut()
        return result
    }
}
