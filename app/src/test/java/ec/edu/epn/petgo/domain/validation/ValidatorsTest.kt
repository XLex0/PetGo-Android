package ec.edu.epn.petgo.domain.validation

import ec.edu.epn.petgo.domain.model.Gender
import ec.edu.epn.petgo.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PetValidatorTest {
    private fun validate(
        name: String = "Firulais",
        race: String = "Labrador",
        age: Int? = 3,
        weight: Double? = 20.0,
        behavior: String = "Tranquilo",
        gender: Gender? = Gender.MALE,
        hasImage: Boolean = true,
    ) = PetValidator.validate(name, race, age, weight, behavior, gender, hasImage)

    @Test fun `mascota valida`() = assertTrue(validate().isValid)

    @Test fun `raza no reconocida`() {
        assertEquals("La raza no es reconocida.", validate(race = "gato").error(Fields.RACE))
    }

    @Test fun `raza con espacios y mayusculas es valida`() = assertTrue(validate(race = "  Pastor Alemán ").isValid)

    @Test fun `edad fuera de rango`() {
        assertTrue(validate(age = -1).hasError(Fields.AGE))
        assertTrue(validate(age = 31).hasError(Fields.AGE))
        assertFalse(validate(age = 0).hasError(Fields.AGE))
        assertFalse(validate(age = 30).hasError(Fields.AGE))
    }

    @Test fun `peso limites igual que la web`() {
        assertTrue(validate(weight = 1.4).hasError(Fields.WEIGHT))   // <= 1.4 es inválido
        assertFalse(validate(weight = 1.5).hasError(Fields.WEIGHT))
        assertFalse(validate(weight = 150.0).hasError(Fields.WEIGHT))
        assertTrue(validate(weight = 150.1).hasError(Fields.WEIGHT))
        assertTrue(validate(weight = null).hasError(Fields.WEIGHT))
    }

    @Test fun `comportamiento obligatorio y maximo 500`() {
        assertTrue(validate(behavior = " ").hasError(Fields.BEHAVIOR))
        assertTrue(validate(behavior = "a".repeat(501)).hasError(Fields.BEHAVIOR))
    }

    @Test fun `imagen y genero obligatorios`() {
        val result = validate(gender = null, hasImage = false)
        assertTrue(result.hasError(Fields.GENDER))
        assertEquals("La imagen es obligatoria para registrar una mascota", result.error(Fields.IMAGE))
    }
}

class TicketValidatorTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test fun `fecha pasada invalida`() {
        val r = TicketValidator.validateSchedule(today.minusDays(1), LocalTime.NOON, 60, today)
        assertTrue(r.hasError(Fields.DATE))
    }

    @Test fun `hoy es valido`() = assertTrue(TicketValidator.validateSchedule(today, LocalTime.NOON, 60, today).isValid)

    @Test fun `duracion entre 30 y 180`() {
        assertTrue(TicketValidator.validateSchedule(today, LocalTime.NOON, 29, today).hasError(Fields.DURATION))
        assertTrue(TicketValidator.validateSchedule(today, LocalTime.NOON, 181, today).hasError(Fields.DURATION))
        assertTrue(TicketValidator.validateSchedule(today, LocalTime.NOON, 30, today).isValid)
        assertTrue(TicketValidator.validateSchedule(today, LocalTime.NOON, 180, today).isValid)
    }

    @Test fun `campos nulos`() {
        val r = TicketValidator.validateSchedule(null, null, null, today)
        assertEquals(setOf(Fields.DATE, Fields.HOUR, Fields.DURATION), r.errors.keys)
    }

    @Test fun `crear exige al menos una mascota`() {
        assertTrue(TicketValidator.validateCreate(today, LocalTime.NOON, 60, emptyList(), today).hasError(Fields.PETS))
    }
}

class WalkerProfileValidatorTest {
    @Test fun `cuenta palabras ignorando espacios multiples`() {
        assertEquals(3, WalkerProfileValidator.wordCount("  hola   mundo  feliz "))
        assertEquals(0, WalkerProfileValidator.wordCount("   "))
    }

    @Test fun `maximo 60 palabras`() {
        val sixty = List(60) { "palabra" }.joinToString(" ")
        assertTrue(WalkerProfileValidator.validateExperience(sixty).isValid)
        assertFalse(WalkerProfileValidator.validateExperience("$sixty extra").isValid)
    }

    @Test fun `experiencia vacia invalida`() = assertFalse(WalkerProfileValidator.validateExperience("").isValid)
}

class RegistrationValidatorTest {
    private fun validate(
        email: String = "ana@correo.com",
        phone: String = "0991234567",
        password: String = "secreta1",
        confirm: String = "secreta1",
        role: Role? = Role.CLIENT,
        terms: Boolean = true,
    ) = RegistrationValidator.validate("Ana", "Pérez", email, phone, password, confirm, role, terms)

    @Test fun `registro valido`() = assertTrue(validate().isValid)
    @Test fun `email invalido`() = assertTrue(validate(email = "ana@").hasError(Fields.EMAIL))
    @Test fun `telefono corto`() = assertTrue(validate(phone = "123").hasError(Fields.PHONE))
    @Test fun `contrasenas distintas`() = assertTrue(validate(confirm = "otra").hasError(Fields.CONFIRM_PASSWORD))
    @Test fun `rol y terminos obligatorios`() {
        val r = validate(role = null, terms = false)
        assertTrue(r.hasError(Fields.ROLE))
        assertTrue(r.hasError(Fields.TERMS))
    }
}

class OfferAndReviewValidatorTest {
    @Test fun `precio debe ser positivo`() {
        assertFalse(OfferValidator.validate(0.0).isValid)
        assertFalse(OfferValidator.validate(null).isValid)
        assertTrue(OfferValidator.validate(12.5).isValid)
    }

    @Test fun `review en rango`() {
        assertTrue(ReviewValidator.validate(5, 3, 4, "Excelente").isValid)
        assertFalse(ReviewValidator.validate(0, 3, 4, "").isValid)
        assertFalse(ReviewValidator.validate(5, 3, 4, "x".repeat(251)).isValid)
    }
}
