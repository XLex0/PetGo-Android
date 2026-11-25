package ec.edu.epn.petgo.domain.usecase.profile

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.businessError
import ec.edu.epn.petgo.core.result.failure
import ec.edu.epn.petgo.core.result.flatMap
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.repository.AuthRepository
import ec.edu.epn.petgo.domain.repository.ImageRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.repository.UserRepository
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.domain.validation.ProfileValidator
import ec.edu.epn.petgo.domain.validation.RegistrationValidator
import ec.edu.epn.petgo.domain.validation.ValidationResult
import ec.edu.epn.petgo.domain.validation.WalkerProfileValidator
import javax.inject.Inject

/**
 * UserController?route=updateImage (parte de datos) → UserService.updateUser.
 * MEJORA: actualización parcial; ya no borra experience/available del paseador (bug 8.6),
 * y el cambio de email pasa por verificación de Firebase Auth.
 */
class UpdateProfileUseCase @Inject constructor(
    private val session: SessionRepository,
    private val users: UserRepository,
    private val auth: AuthRepository,
) {
    suspend operator fun invoke(name: String, lastname: String, email: String, phone: String): AppResult<EmailChange> {
        val validation = ProfileValidator.validate(name, lastname, email, phone)
        if (!validation.isValid) return failure(AppError.Validation(validation))

        return session.requireUser().flatMap { user ->
            users.updatePersonalData(user.id, name.trim(), lastname.trim(), phone.trim()).flatMap {
                val newEmail = email.trim()
                if (newEmail.equals(user.email, ignoreCase = true)) AppResult.Success(EmailChange.UNCHANGED)
                else auth.requestEmailChange(newEmail).flatMap { AppResult.Success(EmailChange.VERIFICATION_SENT) }
            }
        }
    }

    enum class EmailChange { UNCHANGED, VERIFICATION_SENT }
}

/** Perfil público de otro usuario (para mostrar/contactar al paseador o al cliente de un paseo). */
class GetUserProfileUseCase @Inject constructor(private val users: UserRepository) {
    suspend operator fun invoke(uid: String): AppResult<ec.edu.epn.petgo.domain.model.User> = users.getUser(uid)
}

/** ProfileService.updateUserImage */
class UpdateProfilePhotoUseCase @Inject constructor(
    private val session: SessionRepository,
    private val images: ImageRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(image: LocalImage): AppResult<String> =
        session.requireUser().flatMap { user ->
            images.uploadUserPhoto(user.id, image).flatMap { url ->
                users.updatePhotoUrl(user.id, url).flatMap { AppResult.Success(url) }
            }
        }
}

class ChangePasswordUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(current: String, new: String, confirm: String): AppResult<Unit> {
        val validation = ValidationResult.build {
            ensure(current.isNotBlank(), "currentPassword", "Ingresa tu contraseña actual")
            ensure(new.length >= RegistrationValidator.MIN_PASSWORD, Fields.PASSWORD, "La contraseña debe tener al menos ${RegistrationValidator.MIN_PASSWORD} caracteres")
            ensure(new == confirm, Fields.CONFIRM_PASSWORD, "Las contraseñas no coinciden")
        }
        if (!validation.isValid) return failure(AppError.Validation(validation))
        return auth.changePassword(current, new)
    }
}

/**
 * WalkerController?route=updateProfile / updateExperience / updateAvailability.
 * Un solo caso de uso: valida experiencia (≤60 palabras) y guarda disponibilidad.
 */
class UpdateWalkerProfileUseCase @Inject constructor(
    private val session: SessionRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(experience: String, available: Boolean): AppResult<Unit> {
        val validation = WalkerProfileValidator.validateExperience(experience)
        if (!validation.isValid) return failure(AppError.Validation(validation))
        return session.requireUser().flatMap { user ->
            if (user.role != Role.WALKER) businessError("Solo los paseadores tienen perfil profesional.")
            else users.updateWalkerProfile(user.id, experience.trim(), available)
        }
    }
}

/** Atajo para el switch de disponibilidad: no exige reescribir la experiencia. */
class ToggleAvailabilityUseCase @Inject constructor(
    private val session: SessionRepository,
    private val users: UserRepository,
) {
    suspend operator fun invoke(available: Boolean): AppResult<Unit> =
        session.requireUser().flatMap { user ->
            if (user.role != Role.WALKER) businessError("Solo los paseadores pueden cambiar su disponibilidad.")
            else users.updateWalkerProfile(user.id, user.experience, available)
        }
}
