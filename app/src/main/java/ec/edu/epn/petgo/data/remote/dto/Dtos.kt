package ec.edu.epn.petgo.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/*
 * Documentos de Firestore. Reglas para que toObject() funcione:
 *  - `var` con valor por defecto en TODOS los campos (constructor vacío + setters).
 *  - Ningún campo booleano empieza con "is" (Firestore lo renombraría).
 *  - Enums se guardan como String (no ordinal; ver bug 8.4 de la guía).
 * El esquema completo está documentado en docs/MIGRACION_ANDROID.md §2.2.
 */

object Collections {
    const val USERS = "users"
}

data class UserDto(
    @DocumentId var id: String = "",
    var name: String = "",
    var lastname: String = "",
    var email: String = "",
    var phone: String = "",
    var role: String = "CLIENT",
    var experience: String = "",
    var available: Boolean = false,
    var active: Boolean = true,
    var photoUrl: String? = null,
    @ServerTimestamp var createdAt: Timestamp? = null,
)
