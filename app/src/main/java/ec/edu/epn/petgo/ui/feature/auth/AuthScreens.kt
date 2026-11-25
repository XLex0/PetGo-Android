@file:OptIn(ExperimentalMaterial3Api::class)

package ec.edu.epn.petgo.ui.feature.auth

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ec.edu.epn.petgo.R
import ec.edu.epn.petgo.core.designsystem.PetGoTheme
import ec.edu.epn.petgo.core.designsystem.components.PasswordField
import ec.edu.epn.petgo.core.designsystem.components.PetGoButton
import ec.edu.epn.petgo.core.designsystem.components.PetGoCard
import ec.edu.epn.petgo.core.designsystem.components.PetGoTextField
import ec.edu.epn.petgo.domain.model.Role
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.ui.common.CollectMessages
import ec.edu.epn.petgo.ui.common.ScreenScaffold

// ============================================================ Bienvenida (index.jsp)

/**
 * MEJORA: en lugar de una landing con modales, dos caminos claros desde el inicio:
 * "Tengo una mascota" / "Quiero pasear" llevan al registro con el rol ya elegido.
 */
@Composable
fun WelcomeScreen(onLogin: () -> Unit, onRegister: (role: String?) -> Unit) {
    val bounce = rememberInfiniteTransition(label = "bounce")
    val offset by bounce.animateFloat(
        initialValue = 0f, targetValue = -12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "offset",
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(PetGoTheme.colors.headerGradient)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Image(
            painterResource(R.drawable.petgo_logo),
            contentDescription = "PetGo",
            modifier = Modifier.size(150.dp).graphicsLayer { translationY = offset }.clip(MaterialTheme.shapes.extraLarge),
        )
        Spacer(Modifier.height(16.dp))
        Text("PetGo", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text(
            "¡Pasea a tu mascota con confianza!",
            style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center,
        )
        Text(
            "Conecta con paseadores que cuidan de tu mejor amigo.",
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        RoleEntryCard(Icons.Filled.Pets, "Tengo una mascota", "Publica paseos y elige al mejor paseador.") { onRegister(Role.CLIENT.name) }
        Spacer(Modifier.height(12.dp))
        RoleEntryCard(Icons.AutoMirrored.Filled.DirectionsWalk, "Quiero pasear", "Encuentra paseos cerca y gana dinero.") { onRegister(Role.WALKER.name) }

        Spacer(Modifier.height(24.dp))
        Text("¿Ya tienes cuenta?", color = Color.White.copy(alpha = 0.9f))
        TextButton(onClick = onLogin) {
            Text("Iniciar sesión", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Text("© 2025 PetGo – Paseos con Amor", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun RoleEntryCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1B1F1C))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6C757D))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ============================================================ Login

@Composable
fun LoginScreen(onBack: () -> Unit, onRegister: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var showReset by rememberSaveable { mutableStateOf(false) }
    CollectMessages(vm.messages)

    ScreenScaffold(title = "Iniciar sesión", onBack = onBack, topLevel = false) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("¡Hola de nuevo! 🐾", style = MaterialTheme.typography.headlineMedium)
            Text("Ingresa para gestionar tus paseos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PetGoTextField(
                value = form.email, onValueChange = vm::onEmail, label = "Correo electrónico",
                leadingIcon = Icons.Filled.Email, keyboardType = KeyboardType.Email,
                error = form.errors.error(Fields.EMAIL), modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = form.password, onValueChange = vm::onPassword, label = "Contraseña",
                leadingIcon = Icons.Filled.Lock, error = form.errors.error(Fields.PASSWORD), modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { showReset = true }, modifier = Modifier.align(Alignment.End)) { Text("¿Olvidaste tu contraseña?") }
            PetGoButton("Ingresar", onClick = vm::submit, loading = busy, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("¿No tienes cuenta?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRegister) { Text("Regístrate") }
            }
        }
    }

    if (showReset) {
        var email by remember { mutableStateOf(form.email) }
        AlertDialog(
            onDismissRequest = { showReset = false },
            icon = { Icon(Icons.Filled.LockReset, null) },
            title = { Text("Recuperar contraseña") },
            text = {
                Column {
                    Text("Te enviaremos un enlace para crear una nueva contraseña.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, label = { Text("Correo electrónico") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.sendReset(email) { showReset = false } }, enabled = !busy) { Text("Enviar") } },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancelar") } },
        )
    }
}

// ============================================================ Registro

/**
 * MEJORA sobre registro.jsp: el rol se elige primero (viene preseleccionado desde la bienvenida),
 * errores por campo en vivo, y al terminar entra directo a la app (la web obligaba a loguearse después).
 */
@Composable
fun RegisterScreen(initialRole: String?, onBack: () -> Unit, onLogin: () -> Unit, vm: RegisterViewModel = hiltViewModel()) {
    LaunchedEffect(initialRole) { vm.init(initialRole) }
    val f by vm.form.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    val e = f.errors

    ScreenScaffold(title = "Crear cuenta", onBack = onBack, topLevel = false) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("¿Cómo usarás PetGo?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoleCard(Icons.Filled.Pets, "Dueño", f.role == Role.CLIENT, Modifier.weight(1f)) { vm.edit(Fields.ROLE) { it.copy(role = Role.CLIENT) } }
                RoleCard(Icons.AutoMirrored.Filled.DirectionsWalk, "Paseador", f.role == Role.WALKER, Modifier.weight(1f)) { vm.edit(Fields.ROLE) { it.copy(role = Role.WALKER) } }
            }
            e.error(Fields.ROLE)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PetGoTextField(f.name, { v -> vm.edit(Fields.NAME) { it.copy(name = v) } }, "Nombre", error = e.error(Fields.NAME), modifier = Modifier.weight(1f), maxLength = 100)
                PetGoTextField(f.lastname, { v -> vm.edit(Fields.LASTNAME) { it.copy(lastname = v) } }, "Apellido", error = e.error(Fields.LASTNAME), modifier = Modifier.weight(1f), maxLength = 100)
            }
            PetGoTextField(
                f.email, { v -> vm.edit(Fields.EMAIL) { it.copy(email = v) } }, "Correo electrónico",
                leadingIcon = Icons.Filled.Email, keyboardType = KeyboardType.Email, error = e.error(Fields.EMAIL), modifier = Modifier.fillMaxWidth(),
            )
            PetGoTextField(
                f.phone, { v -> vm.edit(Fields.PHONE) { it.copy(phone = v) } }, "Teléfono",
                leadingIcon = Icons.Filled.Phone, keyboardType = KeyboardType.Phone, error = e.error(Fields.PHONE), modifier = Modifier.fillMaxWidth(),
                placeholder = "0991234567", maxLength = 15,
            )
            PasswordField(f.password, { v -> vm.edit(Fields.PASSWORD) { it.copy(password = v) } }, "Contraseña", leadingIcon = Icons.Filled.Lock, error = e.error(Fields.PASSWORD), modifier = Modifier.fillMaxWidth())
            PasswordField(f.confirmPassword, { v -> vm.edit(Fields.CONFIRM_PASSWORD) { it.copy(confirmPassword = v) } }, "Confirmar contraseña", leadingIcon = Icons.Filled.Lock, error = e.error(Fields.CONFIRM_PASSWORD), modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.edit(Fields.TERMS) { it.copy(acceptedTerms = !it.acceptedTerms) } }) {
                Checkbox(checked = f.acceptedTerms, onCheckedChange = { checked -> vm.edit(Fields.TERMS) { it.copy(acceptedTerms = checked) } })
                Text("Acepto los términos y condiciones de PetGo", style = MaterialTheme.typography.bodyMedium)
            }
            e.error(Fields.TERMS)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            PetGoButton("Crear cuenta", onClick = vm::submit, loading = busy, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("¿Ya tienes cuenta?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onLogin) { Text("Inicia sesión") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PetGoCard(modifier = modifier, selected = selected, onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}
