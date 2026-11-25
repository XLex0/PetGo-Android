package ec.edu.epn.petgo.domain.validation

import ec.edu.epn.petgo.domain.model.Gender
import ec.edu.epn.petgo.domain.model.PetBreeds
import ec.edu.epn.petgo.domain.model.Role
import java.time.LocalDate
import java.time.LocalTime

/*
 * Validadores puros (sin Android ni Firebase). Cada uno es un port de la lógica de los *Service web,
 * con los mismos mensajes en español. Las diferencias intencionales están marcadas con "MEJORA".
 */

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PHONE_REGEX = Regex("^\\+?[0-9 ]{7,15}$")

object RegistrationValidator {
    const val MIN_PASSWORD = 6 // mínimo exigido por Firebase Auth

    fun validate(
        name: String,
        lastname: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        role: Role?,
        acceptedTerms: Boolean,
    ): ValidationResult = ValidationResult.build {
        validatePersonalData(name, lastname, email, phone)
        if (password.isBlank()) addError(Fields.PASSWORD, "La contraseña es obligatoria")
        else if (password.length < MIN_PASSWORD) addError(Fields.PASSWORD, "La contraseña debe tener al menos $MIN_PASSWORD caracteres")
        ensure(password == confirmPassword, Fields.CONFIRM_PASSWORD, "Las contraseñas no coinciden")
        ensure(role != null, Fields.ROLE, "Selecciona si eres dueño de mascota o paseador")
        ensure(acceptedTerms, Fields.TERMS, "Debes aceptar los términos y condiciones")
    }
}

object ProfileValidator {
    fun validate(name: String, lastname: String, email: String, phone: String): ValidationResult =
        ValidationResult.build { validatePersonalData(name, lastname, email, phone) }
}

/** Reglas comunes de registro.jsp / UserService.updateUser. */
internal fun ValidationResult.Builder.validatePersonalData(name: String, lastname: String, email: String, phone: String) {
    if (name.trim().length < 2) addError(Fields.NAME, "Por favor ingresa un nombre válido (mínimo 2 caracteres).")
    else if (name.length > 100) addError(Fields.NAME, "El nombre no puede exceder 100 caracteres")
    if (lastname.trim().length < 2) addError(Fields.LASTNAME, "Por favor ingresa un apellido válido (mínimo 2 caracteres).")
    else if (lastname.length > 100) addError(Fields.LASTNAME, "El apellido no puede exceder 100 caracteres")
    ensure(EMAIL_REGEX.matches(email.trim()), Fields.EMAIL, "Por favor ingresa un correo electrónico válido.")
    ensure(PHONE_REGEX.matches(phone.trim()), Fields.PHONE, "Por favor ingresa un teléfono válido.")
}

object LoginValidator {
    fun validate(email: String, password: String): ValidationResult = ValidationResult.build {
        ensure(EMAIL_REGEX.matches(email.trim()), Fields.EMAIL, "Campo vacío o incorrecto")
        ensure(password.isNotBlank(), Fields.PASSWORD, "Campo vacío o incorrecto")
    }
}

object WalkerProfileValidator {
    const val MAX_WORDS = 60

    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    /** WalkerService.validateExperience: obligatoria y máximo 60 palabras. */
    fun validateExperience(experience: String): ValidationResult = ValidationResult.build {
        if (experience.isBlank()) addError(Fields.EXPERIENCE, "La descripción de experiencia no puede estar vacía.")
        else if (wordCount(experience) > MAX_WORDS) addError(Fields.EXPERIENCE, "Describe tu experiencia en máximo $MAX_WORDS palabras")
    }
}

object PetValidator {
    /** PetService.validatePetFields + imagen obligatoria de createPet. */
    fun validate(
        name: String,
        race: String,
        age: Int?,
        weight: Double?,
        behavior: String,
        gender: Gender?,
        hasImage: Boolean,
    ): ValidationResult = ValidationResult.build {
        when {
            name.isBlank() -> addError(Fields.NAME, "El nombre es obligatorio")
            name.length > 100 -> addError(Fields.NAME, "El nombre no puede exceder 100 caracteres")
        }
        when {
            race.isBlank() -> addError(Fields.RACE, "La raza es obligatoria")
            race.length > 100 -> addError(Fields.RACE, "La raza no puede exceder 100 caracteres")
            !PetBreeds.isValid(race) -> addError(Fields.RACE, "La raza no es reconocida.")
        }
        when {
            age == null -> addError(Fields.AGE, "La edad es obligatoria")
            age < 0 || age > 30 -> addError(Fields.AGE, "La edad debe estar entre 0 y 30 años")
        }
        when {
            weight == null -> addError(Fields.WEIGHT, "El peso es obligatorio")
            weight <= 1.4 || weight > 150 -> addError(Fields.WEIGHT, "El peso debe estar entre 1.4 y 150 kg")
        }
        when {
            behavior.isBlank() -> addError(Fields.BEHAVIOR, "La descripción del comportamiento es obligatoria")
            behavior.length > 500 -> addError(Fields.BEHAVIOR, "La descripción no puede exceder 500 caracteres")
        }
        ensure(gender != null, Fields.GENDER, "El género es obligatorio")
        ensure(hasImage, Fields.IMAGE, "La imagen es obligatoria para registrar una mascota")
    }
}

object TicketValidator {
    const val MIN_DURATION = 30
    const val MAX_DURATION = 180

    /**
     * TicketService.isValidDate + isValidFields.
     * MEJORA: se aplica también isValidDuration (30–180 min), que en la web existía pero nunca se llamaba.
     */
    fun validateSchedule(date: LocalDate?, hour: LocalTime?, durationMinutes: Int?, today: LocalDate): ValidationResult =
        ValidationResult.build {
            when {
                date == null -> addError(Fields.DATE, "La fecha es obligatoria")
                date.isBefore(today) -> addError(Fields.DATE, "La fecha no puede ser anterior a hoy")
            }
            ensure(hour != null, Fields.HOUR, "La hora es obligatoria")
            when {
                durationMinutes == null -> addError(Fields.DURATION, "La duración es obligatoria")
                durationMinutes !in MIN_DURATION..MAX_DURATION ->
                    addError(Fields.DURATION, "La duración debe estar entre $MIN_DURATION y $MAX_DURATION minutos")
            }
        }

    fun validateCreate(date: LocalDate?, hour: LocalTime?, durationMinutes: Int?, petIds: List<String>, today: LocalDate): ValidationResult {
        val schedule = validateSchedule(date, hour, durationMinutes, today)
        return if (petIds.isEmpty()) schedule.copy(errors = schedule.errors + (Fields.PETS to "Selecciona al menos una mascota"))
        else schedule
    }
}

object OfferValidator {
    const val MAX_PRICE = 1000.0

    /** MEJORA: la web no validaba el precio. */
    fun validate(price: Double?): ValidationResult = ValidationResult.build {
        when {
            price == null -> addError(Fields.PRICE, "Ingresa un precio")
            price <= 0.0 -> addError(Fields.PRICE, "El precio debe ser mayor a 0")
            price > MAX_PRICE -> addError(Fields.PRICE, "El precio no puede superar $MAX_PRICE")
        }
    }
}

object ReviewValidator {
    const val MAX_COMMENT = 250

    fun validate(stars: Int, punctuality: Int, treatment: Int, comment: String): ValidationResult = ValidationResult.build {
        ensure(stars in 1..5, Fields.STARS, "Selecciona de 1 a 5 estrellas")
        ensure(punctuality in 1..5, Fields.PUNCTUALITY, "Califica la puntualidad de 1 a 5")
        ensure(treatment in 1..5, Fields.TREATMENT, "Califica el trato de 1 a 5")
        ensure(comment.length <= MAX_COMMENT, Fields.COMMENT, "El comentario no puede exceder $MAX_COMMENT caracteres")
    }
}
