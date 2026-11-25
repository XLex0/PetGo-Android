package ec.edu.epn.petgo.domain.validation

/** Port inmutable de model.services.ValidationResult (errores por campo). */
data class ValidationResult(val errors: Map<String, String> = emptyMap()) {
    val isValid: Boolean get() = errors.isEmpty()
    fun error(field: String): String? = errors[field]
    fun hasError(field: String): Boolean = field in errors

    class Builder {
        private val errors = linkedMapOf<String, String>()

        /** Igual que la web: si el campo ya tiene error, el nuevo lo reemplaza. */
        fun addError(field: String, message: String) = apply { errors[field] = message }

        fun ensure(condition: Boolean, field: String, message: String) = apply {
            if (!condition) addError(field, message)
        }

        fun build() = ValidationResult(errors.toMap())
    }

    companion object {
        val VALID = ValidationResult()
        inline fun build(block: Builder.() -> Unit): ValidationResult = Builder().apply(block).build()
    }
}

/** Nombres de campos compartidos entre validadores y formularios. */
object Fields {
    const val NAME = "name"
    const val LASTNAME = "lastname"
    const val EMAIL = "email"
    const val PHONE = "phone"
    const val PASSWORD = "password"
    const val CONFIRM_PASSWORD = "confirmPassword"
    const val TERMS = "terms"
    const val ROLE = "role"
    const val EXPERIENCE = "experience"

    const val RACE = "race"
    const val AGE = "age"
    const val WEIGHT = "weight"
    const val BEHAVIOR = "behavior"
    const val GENDER = "gender"
    const val IMAGE = "image"

    const val DATE = "date"
    const val HOUR = "hour"
    const val DURATION = "duration"
    const val PETS = "pets"

    const val PRICE = "price"

    const val STARS = "stars"
    const val PUNCTUALITY = "punctuality"
    const val TREATMENT = "treatment"
    const val COMMENT = "comment"

    const val GENERAL = "general"
}
