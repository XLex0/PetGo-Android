@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ec.edu.epn.petgo.ui.feature.client

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ec.edu.epn.petgo.core.designsystem.PetGoTheme
import ec.edu.epn.petgo.core.designsystem.components.ConfirmDialog
import ec.edu.epn.petgo.core.designsystem.components.EmptyState
import ec.edu.epn.petgo.core.designsystem.components.LoadingOverlay
import ec.edu.epn.petgo.core.designsystem.components.PetGoButton
import ec.edu.epn.petgo.core.designsystem.components.PetGoCard
import ec.edu.epn.petgo.core.designsystem.components.PetGoOutlinedButton
import ec.edu.epn.petgo.core.designsystem.components.StatusChip
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.PetBreeds
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.domain.validation.ReviewValidator
import ec.edu.epn.petgo.ui.common.CollectMessages
import ec.edu.epn.petgo.ui.common.DurationChips
import ec.edu.epn.petgo.ui.common.EmojiScale
import ec.edu.epn.petgo.ui.common.Format
import ec.edu.epn.petgo.ui.common.FutureDatePickerDialog
import ec.edu.epn.petgo.ui.common.InfoRow
import ec.edu.epn.petgo.ui.common.ListState
import ec.edu.epn.petgo.ui.common.PetChipsRow
import ec.edu.epn.petgo.ui.common.PickerField
import ec.edu.epn.petgo.ui.common.ScreenScaffold
import ec.edu.epn.petgo.ui.common.SectionHeader
import ec.edu.epn.petgo.ui.common.StarsInput
import ec.edu.epn.petgo.ui.common.PetGoTimePickerDialog
import ec.edu.epn.petgo.ui.common.UserAvatar
import ec.edu.epn.petgo.ui.common.style

// ============================================================ Crear / editar paseo

/**
 * MEJORAS sobre client/ticket/register.jsp + modal de edición:
 * selector de fecha que no permite días pasados, chips de duración, mascotas como tarjetas seleccionables,
 * y si no hay mascotas se puede registrar una sin perder el flujo.
 */
@Composable
fun TicketFormScreen(
    onBack: () -> Unit,
    onAddPet: () -> Unit,
    onCreated: (String) -> Unit,
    vm: TicketFormViewModel = hiltViewModel(),
) {
    val pets by vm.pets.collectAsStateWithLifecycle()
    val f by vm.form.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    CollectMessages(vm.messages)
    val e = f.errors

    ScreenScaffold(title = if (vm.isEditing) "Reprogramar paseo" else "Nuevo paseo", onBack = onBack) { padding ->
        val petList = (pets as? ListState.Ready)?.items
        when {
            petList == null -> LoadingOverlay(Modifier.padding(padding))
            petList.isEmpty() && !vm.isEditing -> EmptyState(
                icon = Icons.Filled.Pets,
                title = "No tienes mascotas registradas",
                message = "Registra a tu mascota y vuelve aquí para programar el paseo.",
                action = { PetGoButton("Registrar mascota", onClick = onAddPet) },
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("¿Quién sale a pasear?")
                if (vm.isEditing) {
                    Text("Las mascotas de un paseo no se pueden cambiar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    petList.forEach { pet ->
                        val selected = pet.id in f.petIds
                        PetGoCard(selected = selected, onClick = if (vm.isEditing) null else ({ vm.togglePet(pet.id) }), modifier = Modifier.width(104.dp)) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                UserAvatar(pet.photoUrl, 56.dp, contentDescription = pet.name)
                                Text(pet.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(PetBreeds.displayName(pet.race), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (!vm.isEditing) {
                        AssistChip(onClick = onAddPet, label = { Text("Otra mascota") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                    }
                }
                e.error(Fields.PETS)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                SectionHeader("¿Cuándo?")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField("Fecha", f.date?.let { Format.date(it) } ?: "", Icons.Filled.CalendarMonth, { pickDate = true }, Modifier.weight(1f), e.error(Fields.DATE))
                    PickerField("Hora", f.hour?.let { Format.time(it) } ?: "", Icons.Filled.Schedule, { pickTime = true }, Modifier.weight(1f), e.error(Fields.HOUR))
                }
                DurationChips(f.durationMinutes, { m -> vm.edit(Fields.DURATION) { it.copy(durationMinutes = m) } }, error = e.error(Fields.DURATION))

                Spacer(Modifier.height(8.dp))
                PetGoButton(
                    if (vm.isEditing) "Guardar cambios" else "Publicar paseo",
                    onClick = { vm.submit(onCreated = onCreated, onUpdated = onBack) },
                    loading = busy, modifier = Modifier.fillMaxWidth(),
                )
                if (!vm.isEditing) {
                    Text(
                        "Los paseadores disponibles verán tu solicitud y te enviarán su precio. Tú eliges.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (pickDate) FutureDatePickerDialog(f.date, onDismiss = { pickDate = false }) { d -> vm.edit(Fields.DATE) { it.copy(date = d) } }
    if (pickTime) PetGoTimePickerDialog(f.hour, onDismiss = { pickTime = false }) { t -> vm.edit(Fields.HOUR) { it.copy(hour = t) } }
}

// ============================================================ Detalle del paseo

/**
 * NUEVO: una sola pantalla por paseo con estado, paseador asignado (y botón para llamarlo),
 * acciones del flujo y las ofertas recibidas. Reemplaza client/listApplications.jsp
 * y los botones sueltos de client/ticket/list.jsp.
 */
@Composable
fun TicketDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: TicketDetailViewModel = hiltViewModel()) {
    val load by vm.ticket.collectAsStateWithLifecycle()
    val offers by vm.offers.collectAsStateWithLifecycle()
    val walker by vm.walker.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val askRating by vm.askRating.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)

    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var counterFor by remember { mutableStateOf<Application?>(null) }

    val ticket = (load as? TicketLoad.Ready)?.ticket
    ScreenScaffold(
        title = "Detalle del paseo",
        onBack = onBack,
        actions = {
            if (ticket?.status == TicketStatus.PENDING) {
                IconButton(onClick = { onEdit(ticket.id) }) { Icon(Icons.Filled.Edit, "Reprogramar") }
                IconButton(onClick = { confirm = PendingConfirm.DELETE }) { Icon(Icons.Filled.Delete, "Eliminar") }
            }
        },
    ) { padding ->
        when (val l = load) {
            TicketLoad.Loading -> LoadingOverlay(Modifier.padding(padding))
            TicketLoad.NotFound -> EmptyState(Icons.Filled.SearchOff, "Este paseo ya no existe", Modifier.padding(padding))
            is TicketLoad.Ready -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryCard(l.ticket)
                StatusTimeline(l.ticket.status)

                if (walker != null && l.ticket.assignedWalkerId != null) {
                    SectionHeader("Tu paseador")
                    WalkerCard(walker!!, l.ticket.agreedPrice)
                }

                ActionsFor(
                    ticket = l.ticket, busy = busy,
                    onStart = vm::start,
                    onFinish = { confirm = PendingConfirm.FINISH },
                    onCancel = { confirm = PendingConfirm.CANCEL },
                    onRate = vm::openRating,
                )

                if (l.ticket.status == TicketStatus.PENDING || offers.isNotEmpty()) {
                    SectionHeader("Ofertas recibidas (${offers.size})")
                    if (offers.isEmpty()) {
                        PetGoCard(Modifier.fillMaxWidth()) {
                            Text("Aún no hay ofertas", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Los paseadores disponibles ya pueden ver tu solicitud. Las ofertas aparecerán aquí automáticamente.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    offers.sortedWith(compareBy<Application> { it.status != ApplicationStatus.PENDING }.thenBy { it.offerPrice })
                        .forEach { app ->
                            OfferCard(
                                app, busy = busy, canDecide = l.ticket.status == TicketStatus.PENDING,
                                onAccept = { vm.accept(app) }, onReject = { vm.reject(app) }, onCounter = { counterFor = app },
                            )
                        }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    when (confirm) {
        PendingConfirm.DELETE -> ConfirmDialog(
            "¿Eliminar esta solicitud?", "Se borrará junto con las ofertas recibidas.", "Eliminar", "Cancelar",
            onConfirm = { confirm = null; vm.delete(onBack) }, onDismiss = { confirm = null }, destructive = true,
        )
        PendingConfirm.CANCEL -> ConfirmDialog(
            "¿Cancelar el paseo?", "Avisa a tu paseador antes de cancelar. Esta acción no se puede deshacer.", "Cancelar paseo", "Volver",
            onConfirm = { confirm = null; vm.cancel() }, onDismiss = { confirm = null }, destructive = true,
        )
        PendingConfirm.FINISH -> ConfirmDialog(
            "¿El paseo terminó?", "Lo marcaremos como finalizado y podrás calificar al paseador.", "Finalizar", "Aún no",
            onConfirm = { confirm = null; vm.finish() }, onDismiss = { confirm = null },
        )
        null -> Unit
    }
    counterFor?.let { app ->
        CounterOfferDialog(app, busy, onDismiss = { counterFor = null }) { price -> vm.counter(app, price) { counterFor = null } }
    }
    if (askRating && ticket?.status == TicketStatus.COMPLETED) {
        RatingSheet(walkerName = walker?.name ?: "tu paseador", busy = busy, onDismiss = vm::dismissRating, onSubmit = vm::rate)
    }
}

private enum class PendingConfirm { DELETE, CANCEL, FINISH }

@Composable
private fun SummaryCard(t: Ticket) {
    val st = t.status.style()
    PetGoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.date(t.date), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            StatusChip(st.label, st.color)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(Icons.Filled.Schedule, Format.time(t.hour))
            InfoRow(Icons.Filled.Timer, Format.duration(t.durationMinutes))
        }
        PetChipsRow(t.pets)
    }
}

/** Línea de tiempo simple del paseo (en la web solo había un badge). */
@Composable
private fun StatusTimeline(status: TicketStatus) {
    if (status == TicketStatus.CANCELED) return
    val steps = listOf("Publicado", "Asignado", "En curso", "Terminado", "Calificado")
    val current = when (status) {
        TicketStatus.PENDING -> 0
        TicketStatus.ASSIGNED -> 1
        TicketStatus.IN_PROGRESS -> 2
        TicketStatus.COMPLETED -> 3
        TicketStatus.REVIEWED -> 4
        TicketStatus.CANCELED -> 0
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, label ->
            val done = index <= current
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Icon(
                    if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun WalkerCard(walker: User, agreedPrice: Double?) {
    val context = LocalContext.current
    PetGoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(walker.photoUrl, 56.dp, contentDescription = walker.fullName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(walker.fullName, style = MaterialTheme.typography.titleMedium)
                agreedPrice?.let { Text("Precio acordado: ${Format.price(it)}", color = MaterialTheme.colorScheme.primary) }
            }
            FilledTonalIconButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${walker.phone}")))
            }) { Icon(Icons.Filled.Phone, "Llamar a ${walker.name}") }
        }
        if (walker.experience.isNotBlank()) {
            Text(walker.experience, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionsFor(ticket: Ticket, busy: Boolean, onStart: () -> Unit, onFinish: () -> Unit, onCancel: () -> Unit, onRate: () -> Unit) {
    when (ticket.status) {
        TicketStatus.ASSIGNED -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PetGoOutlinedButton("Cancelar", onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f))
            PetGoButton("Iniciar paseo", onClick = onStart, loading = busy, icon = Icons.Filled.PlayArrow, modifier = Modifier.weight(1f))
        }
        TicketStatus.IN_PROGRESS -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PetGoOutlinedButton("Cancelar", onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f))
            PetGoButton("Finalizar paseo", onClick = onFinish, loading = busy, icon = Icons.Filled.Flag, modifier = Modifier.weight(1f))
        }
        TicketStatus.COMPLETED -> PetGoButton(
            "Calificar paseo", onClick = onRate, icon = Icons.Filled.Star, modifier = Modifier.fillMaxWidth(),
            containerColor = PetGoTheme.colors.warning, contentColor = PetGoTheme.colors.onWarning,
        )
        TicketStatus.REVIEWED -> InfoRow(Icons.Filled.CheckCircle, "Ya calificaste este paseo. ¡Gracias!", color = MaterialTheme.colorScheme.primary)
        TicketStatus.CANCELED -> InfoRow(Icons.Filled.Cancel, "Este paseo fue cancelado.", color = MaterialTheme.colorScheme.error)
        TicketStatus.PENDING -> Unit
    }
}

@Composable
private fun OfferCard(
    app: Application,
    busy: Boolean,
    canDecide: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCounter: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val st = app.status.style()
    PetGoCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(app.walker.photoUrl, 48.dp, contentDescription = app.walker.fullName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.walker.fullName, style = MaterialTheme.typography.titleSmall)
                StatusChip(st.label, st.color)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.price(app.offerPrice), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (app.status == ApplicationStatus.COUNTERED && app.originalPrice != app.offerPrice) {
                    Text("Pidió ${Format.price(app.originalPrice)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (app.walker.experience.isNotBlank()) {
            Text(
                app.walker.experience, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (app.status == ApplicationStatus.COUNTERED) {
            InfoRow(Icons.Filled.HourglassTop, "Esperando respuesta del paseador a tu contraoferta")
        }
        if (canDecide && app.status == ApplicationStatus.PENDING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReject, enabled = !busy) { Text("Rechazar", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onCounter, enabled = !busy) { Text("Contraofertar") }
                Button(onClick = onAccept, enabled = !busy) { Text("Aceptar") }
            }
        }
    }
}

@Composable
private fun CounterOfferDialog(app: Application, busy: Boolean, onDismiss: () -> Unit, onSubmit: (Double?) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contraoferta a ${app.walker.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pidió ${Format.price(app.offerPrice)}. ¿Cuánto propones?")
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
                    prefix = { Text("$") },
                    label = { Text("Tu precio") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("El paseador podrá aceptarla o rechazarla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(text.replace(',', '.').toDoubleOrNull()) }, enabled = !busy && text.isNotBlank()) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Calificación con estrellas + escalas de emojis (como reviews de la web) y comentario opcional. */
@Composable
private fun RatingSheet(walkerName: String, busy: Boolean, onDismiss: () -> Unit, onSubmit: (Int, Int, Int, String) -> Unit) {
    var stars by rememberSaveable { mutableStateOf(5) }
    var punctuality by rememberSaveable { mutableStateOf(5) }
    var treatment by rememberSaveable { mutableStateOf(5) }
    var comment by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("¿Cómo te fue con $walkerName?", style = MaterialTheme.typography.titleLarge)
            StarsInput(stars, { stars = it }, Modifier.fillMaxWidth())
            EmojiScale("Puntualidad", punctuality, { punctuality = it })
            EmojiScale("Trato con tu mascota", treatment, { treatment = it })
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= ReviewValidator.MAX_COMMENT) comment = it },
                label = { Text("Comentario (opcional)") },
                supportingText = { Text("${comment.length}/${ReviewValidator.MAX_COMMENT}") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            PetGoButton("Enviar calificación", onClick = { onSubmit(stars, punctuality, treatment, comment) }, loading = busy, modifier = Modifier.fillMaxWidth())
        }
    }
}
