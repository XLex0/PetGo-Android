package ec.edu.epn.petgo.domain.usecase.pet

import ec.edu.epn.petgo.core.result.AppError
import ec.edu.epn.petgo.core.result.AppResult
import ec.edu.epn.petgo.core.result.businessError
import ec.edu.epn.petgo.core.result.failure
import ec.edu.epn.petgo.core.result.flatMap
import ec.edu.epn.petgo.domain.model.Gender
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.model.Pet
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.repository.ImageRepository
import ec.edu.epn.petgo.domain.repository.PetRepository
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.domain.validation.PetValidator
import ec.edu.epn.petgo.domain.validation.ValidationResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * mascotas?route=saveNew → PetService.createPet.
 * Orden distinto a la web: primero se sube la foto y luego se crea el documento,
 * así nunca queda una mascota sin imagen.
 */
class CreatePetUseCase @Inject constructor(
    private val session: SessionRepository,
    private val pets: PetRepository,
    private val images: ImageRepository,
) {
    suspend operator fun invoke(
        name: String,
        race: String,
        age: Int?,
        weight: Double?,
        behavior: String,
        gender: Gender?,
        image: LocalImage?,
    ): AppResult<Pet> {
        val validation = PetValidator.validate(name, race, age, weight, behavior, gender, hasImage = image != null)
        if (!validation.isValid) return failure(AppError.Validation(validation))

        return session.requireUser().flatMap { user ->
            if (user.role != Role.CLIENT) return@flatMap businessError("Solo los dueños pueden registrar mascotas.")
            val petId = pets.newPetId()
            when (val upload = images.uploadPetPhoto(user.id, petId, image!!)) {
                is AppResult.Failure -> failure(
                    AppError.Validation(ValidationResult(mapOf(Fields.IMAGE to "No se pudo subir la imagen de la mascota"))),
                )
                is AppResult.Success -> {
                    val pet = Pet(
                        id = petId,
                        ownerId = user.id,
                        name = name.trim(),
                        race = race.trim().lowercase(),
                        age = age!!,
                        weight = weight!!,
                        behavior = behavior.trim(),
                        gender = gender!!,
                        photoUrl = upload.data,
                    )
                    pets.createPet(pet).flatMap { AppResult.Success(pet) }
                }
            }
        }
    }
}

/** mascotas?route=list → PetService.getAllPetsActiveByUser */
class ObserveMyPetsUseCase @Inject constructor(private val pets: PetRepository) {
    operator fun invoke(ownerId: String): Flow<List<Pet>> = pets.observeActivePets(ownerId)
}

/** mascotas?route=suspend → PetService.suspendPet (soft delete). */
class SuspendPetUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(petId: String): AppResult<Unit> = pets.deactivate(petId)
}
