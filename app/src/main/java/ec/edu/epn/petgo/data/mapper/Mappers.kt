package ec.edu.epn.petgo.data.mapper

import ec.edu.epn.petgo.data.remote.dto.UserDto
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.model.User

/*
 * DTO ↔ dominio. Los mappers "toDomain" devuelven null si el documento está corrupto
 * (p. ej. fecha mal formada) para que una lista no falle completa por un solo documento.
 */

private inline fun <reified E : Enum<E>> enumOrNull(value: String): E? = runCatching { enumValueOf<E>(value) }.getOrNull()

// ---------- User ----------
fun UserDto.toDomain(): User? {
    val parsedRole = enumOrNull<Role>(this.role) ?: return null
    return User(id, name, lastname, email, phone, parsedRole, experience, available, active, photoUrl)
}

fun User.toDto() = UserDto(
    id = id, name = name, lastname = lastname, email = email, phone = phone, role = role.name,
    experience = experience, available = available, active = active, photoUrl = photoUrl,
)
