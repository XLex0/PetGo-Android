@file:OptIn(ExperimentalMaterial3Api::class)

package ec.edu.epn.petgo.ui.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ec.edu.epn.petgo.core.designsystem.components.ConfirmDialog
import ec.edu.epn.petgo.core.designsystem.components.LoadingOverlay
import ec.edu.epn.petgo.core.designsystem.components.PasswordField
import ec.edu.epn.petgo.core.designsystem.components.PetGoButton
import ec.edu.epn.petgo.core.designsystem.components.PetGoCard
import ec.edu.epn.petgo.core.designsystem.components.PetGoTextField
import ec.edu.epn.petgo.core.designsystem.components.StatusChip
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.repository.SessionRepository
import ec.edu.epn.petgo.domain.usecase.auth.LogoutUseCase
import ec.edu.epn.petgo.domain.usecase.auth.SuspendAccountUseCase
import ec.edu.epn.petgo.domain.usecase.profile.ChangePasswordUseCase
import ec.edu.epn.petgo.domain.usecase.profile.UpdateProfilePhotoUseCase
import ec.edu.epn.petgo.domain.usecase.profile.UpdateProfileUseCase
import ec.edu.epn.petgo.domain.usecase.profile.UpdateWalkerProfileUseCase
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.domain.validation.ValidationResult
import ec.edu.epn.petgo.domain.validation.WalkerProfileValidator
import ec.edu.epn.petgo.ui.common.CollectMessages
import ec.edu.epn.petgo.ui.common.PetGoViewModel
import ec.edu.epn.petgo.ui.common.PhotoPickerBox
import ec.edu.epn.petgo.ui.common.ScreenScaffold
import ec.edu.epn.petgo.ui.common.SectionHeader
import ec.edu.epn.petgo.ui.common.currentUserFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonalForm(
    val name: String = "",
    val lastname: String = "",
    val email: String = "",
    val phone: String = "",
    val errors: ValidationResult = ValidationResult.VALID,
)

data class WalkerForm(
    val experience: String = "",
    val available: Boolean = false,
    val errors: ValidationResult = ValidationResult.VALID,
)

/**
 * Unifica profile.jsp (datos + foto), profileWalker.jsp (experiencia + disponibilidad)
 * y las acciones de cuenta (logout, suspender) que en la web estaban dispersas.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    session: SessionRepository,
    private val updateProfile: UpdateProfileUseCase,
    private val updatePhoto: UpdateProfilePhotoUseCase,
    private val updateWalker: UpdateWalkerProfileUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val suspendAccountUseCase: SuspendAccountUseCase,
) : PetGoViewModel() {

    val user: StateFlow<User?> = session.currentUserFlow(this)

    private val _personal = MutableStateFlow(PersonalForm())
    val personal: StateFlow<PersonalForm> = _personal.asStateFlow()

    private val _walker = MutableStateFlow(WalkerForm())
    val walker: StateFlow<WalkerForm> = _walker.asStateFlow()

    private var loadedFor: String? = null

    /** Copia los datos del usuario a los formularios una sola vez (no pisa lo que el usuario está escribiendo). */
    fun load(user: User) {
        if (loadedFor == user.id) return
        loadedFor = user.id
        _personal.value = PersonalForm(user.name, user.lastname, user.email, user.phone)
        _walker.value = WalkerForm(user.experience, user.available)
    }

    fun editPersonal(field: String, transform: (PersonalForm) -> PersonalForm) =
        _personal.update { transform(it).copy(errors = ValidationResult(it.errors.errors - field)) }

    fun editWalker(transform: (WalkerForm) -> WalkerForm) =
        _walker.update { transform(it).copy(errors = ValidationResult.VALID) }

    fun savePersonal() {
        val f = _personal.value
        runAction(
            onValidation = { errors -> _personal.update { it.copy(errors = errors) } },
            onSuccess = { change ->
                showMessage(
                    if (change == UpdateProfileUseCase.EmailChange.VERIFICATION_SENT)
                        "Datos guardados. Revisa tu nuevo correo para confirmar el cambio."
                    else "Datos actualizados correctamente.",
                )
            },
        ) { updateProfile(f.name, f.lastname, f.email, f.phone) }
    }

    fun savePhoto(image: LocalImage) {
        runAction(successMessage = "Foto actualizada.") { updatePhoto(image) }
    }

    fun saveWalker() {
        val f = _walker.value
        runAction(
            successMessage = "Perfil profesional actualizado.",
            onValidation = { errors -> _walker.update { it.copy(errors = errors) } },
        ) { updateWalker(f.experience, f.available) }
    }

    fun changePassword(current: String, new: String, confirm: String, onDone: () -> Unit) {
        runAction(successMessage = "Contraseña actualizada.", onSuccess = { onDone() }) { changePasswordUseCase(current, new, confirm) }
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    fun suspendAccount() {
        runAction { suspendAccountUseCase() }
    }
}

@Composable
fun ProfileScreen(vm: ProfileViewModel = hiltViewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    val personal by vm.personal.collectAsStateWithLifecycle()
    val walker by vm.walker.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)

    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmSuspend by rememberSaveable { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val u = user
    if (u == null) {
        LoadingOverlay()
        return
    }
    LaunchedEffect(u.id) { vm.load(u) }

    ScreenScaffold(title = "Mi perfil") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Cabecera
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PhotoPickerBox(localImage = null, currentUrl = u.photoUrl, onPicked = vm::savePhoto, size = 112.dp, placeholderIcon = Icons.Filled.Person)
                Spacer(Modifier.height(8.dp))
                Text(u.fullName, style = MaterialTheme.typography.titleLarge)
                Text(u.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                StatusChip(if (u.role == Role.WALKER) "Paseador" else "Dueño de mascota", MaterialTheme.colorScheme.primary)
            }

            // ---- Perfil profesional (solo paseador): va primero porque es lo que ven los dueños
            if (u.role == Role.WALKER) {
                SectionHeader("Perfil profesional")
                PetGoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Disponible para paseos", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (walker.available) "Los dueños te verán como disponible" else "No recibirás nuevos paseos",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = walker.available, onCheckedChange = { checked -> vm.editWalker { it.copy(available = checked) } })
                    }
                    Spacer(Modifier.height(12.dp))
                    val words = WalkerProfileValidator.wordCount(walker.experience)
                    PetGoTextField(
                        value = walker.experience,
                        onValueChange = { v -> vm.editWalker { it.copy(experience = v) } },
                        label = "Tu experiencia con mascotas",
                        singleLine = false, minLines = 4,
                        error = walker.errors.error(Fields.EXPERIENCE),
                        supportingText = "$words / ${WalkerProfileValidator.MAX_WORDS} palabras",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PetGoButton("Guardar perfil profesional", onClick = vm::saveWalker, loading = busy, modifier = Modifier.fillMaxWidth())
                }
            }

            // ---- Datos personales
            SectionHeader("Datos personales")
            PetGoCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PetGoTextField(personal.name, { v -> vm.editPersonal(Fields.NAME) { it.copy(name = v) } }, "Nombre", error = personal.errors.error(Fields.NAME), modifier = Modifier.weight(1f))
                    PetGoTextField(personal.lastname, { v -> vm.editPersonal(Fields.LASTNAME) { it.copy(lastname = v) } }, "Apellido", error = personal.errors.error(Fields.LASTNAME), modifier = Modifier.weight(1f))
                }
                PetGoTextField(personal.phone, { v -> vm.editPersonal(Fields.PHONE) { it.copy(phone = v) } }, "Teléfono", leadingIcon = Icons.Filled.Phone, keyboardType = KeyboardType.Phone, error = personal.errors.error(Fields.PHONE), modifier = Modifier.fillMaxWidth())
                PetGoTextField(
                    personal.email, { v -> vm.editPersonal(Fields.EMAIL) { it.copy(email = v) } }, "Correo electrónico",
                    leadingIcon = Icons.Filled.Email, keyboardType = KeyboardType.Email, error = personal.errors.error(Fields.EMAIL),
                    supportingText = "Si lo cambias, te enviaremos un correo para confirmarlo.", modifier = Modifier.fillMaxWidth(),
                )
                val changed = personal.name != u.name || personal.lastname != u.lastname || personal.phone != u.phone || !personal.email.equals(u.email, true)
                PetGoButton("Guardar cambios", onClick = vm::savePersonal, enabled = changed, loading = busy, modifier = Modifier.fillMaxWidth())
            }

            // ---- Cuenta
            SectionHeader("Cuenta")
            PetGoCard(Modifier.fillMaxWidth()) {
                AccountRow(Icons.Filled.Lock, "Cambiar contraseña") { showPassword = true }
                HorizontalDivider()
                AccountRow(Icons.AutoMirrored.Filled.Logout, "Cerrar sesión") { confirmLogout = true }
                HorizontalDivider()
                AccountRow(Icons.Filled.PersonOff, "Suspender mi cuenta", destructive = true) { confirmSuspend = true }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "¿Cerrar sesión?", message = "Tendrás que volver a ingresar tus credenciales.",
            confirmText = "Cerrar sesión", dismissText = "Cancelar",
            onConfirm = { confirmLogout = false; vm.logout() }, onDismiss = { confirmLogout = false },
        )
    }
    if (confirmSuspend) {
        ConfirmDialog(
            title = "¿Suspender tu cuenta?",
            message = "Tu cuenta quedará inactiva y se cerrará la sesión. Para reactivarla tendrás que contactar a soporte.",
            confirmText = "Suspender", dismissText = "Cancelar", destructive = true,
            onConfirm = { confirmSuspend = false; vm.suspendAccount() }, onDismiss = { confirmSuspend = false },
        )
    }
    if (showPassword) ChangePasswordDialog(busy = busy, onDismiss = { showPassword = false }) { cur, new, confirm ->
        vm.changePassword(cur, new, confirm) { showPassword = false }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(text, color = color) },
        leadingContent = { Icon(icon, null, tint = color) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun ChangePasswordDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, "Contraseña actual")
                PasswordField(new, { new = it }, "Nueva contraseña")
                PasswordField(confirm, { confirm = it }, "Confirmar nueva contraseña")
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(current, new, confirm) }, enabled = !busy) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
