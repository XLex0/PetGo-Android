@file:OptIn(ExperimentalMaterial3Api::class)

package ec.edu.epn.petgo.ui.feature.walker

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ec.edu.epn.petgo.core.designsystem.PetGoTheme
import ec.edu.epn.petgo.core.designsystem.components.EmptyState
import ec.edu.epn.petgo.core.designsystem.components.LoadingOverlay
import ec.edu.epn.petgo.core.designsystem.components.PetGoButton
import ec.edu.epn.petgo.core.designsystem.components.PetGoCard
import ec.edu.epn.petgo.core.designsystem.components.StatusChip
import ec.edu.epn.petgo.domain.model.Application
import ec.edu.epn.petgo.domain.model.ApplicationStatus
import ec.edu.epn.petgo.domain.model.PetBreeds
import ec.edu.epn.petgo.domain.model.PetSummary
import ec.edu.epn.petgo.domain.model.Ticket
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.domain.model.WalkerStats
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.ui.common.CollectMessages
import ec.edu.epn.petgo.ui.common.Format
import ec.edu.epn.petgo.ui.common.InfoRow
import ec.edu.epn.petgo.ui.common.ListState
import ec.edu.epn.petgo.ui.common.PetChipsRow
import ec.edu.epn.petgo.ui.common.ScreenScaffold
import ec.edu.epn.petgo.ui.common.SectionHeader
import ec.edu.epn.petgo.ui.common.StarsDisplay
import ec.edu.epn.petgo.ui.common.UserAvatar
import ec.edu.epn.petgo.ui.common.style

// ============================================================ Inicio del paseador (walker/home.jsp)

/**
 * MEJORA: la web solo tenía 3 tarjetas de navegación. Ahora el inicio muestra
 * disponibilidad (switch), contraofertas por responder, los paseos confirmados (vista nueva) y un resumen de reseñas.
 */
@Composable
fun WalkerHomeScreen(
    user: User,
    onOpenWalk: (String) -> Unit,
    onBrowse: () -> Unit,
    onOpenOffers: () -> Unit,
    onEditProfile: () -> Unit,
    vm: WalkerHomeViewModel = hiltViewModel(),
) {
    val walks by vm.walks.collectAsStateWithLifecycle()
    val apps by vm.applications.collectAsStateWithLifecycle()
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)

    ScreenScaffold(title = null) { padding ->
        val walkList = (walks as? ListState.Ready)?.items
        val appList = (apps as? ListState.Ready)?.items
        if (walkList == null || appList == null) {
            LoadingOverlay(Modifier.padding(padding)); return@ScreenScaffold
        }
        val active = walkList.filter { it.status == TicketStatus.ASSIGNED || it.status == TicketStatus.IN_PROGRESS }
        val counters = appList.filter { it.status == ApplicationStatus.COUNTERED }
        val pendingOffers = appList.count { it.status == ApplicationStatus.PENDING }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().background(PetGoTheme.colors.headerGradient, MaterialTheme.shapes.large).padding(20.dp)) {
                    Text("Hola, ${user.name} 🐕", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (user.available) "Estás disponible" else "No disponible", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                if (user.available) "Puedes enviar ofertas a nuevos paseos." else "Actívate para ofertar en paseos.",
                                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = user.available, enabled = !busy, onCheckedChange = vm::setAvailable,
                            colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            if (user.experience.isBlank()) {
                item {
                    PetGoCard(Modifier.fillMaxWidth(), onClick = onEditProfile) {
                        InfoRow(Icons.Filled.Info, "Completa tu perfil profesional", color = PetGoTheme.colors.warning)
                        Text("Los dueños eligen según tu experiencia. Cuéntales en pocas palabras.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Reseñas", reviews?.stats?.let { if (it.totalReviews == 0) "—" else "★ " + Format.rating(it.averageStars) } ?: "—", Modifier.weight(1f))
                    StatTile("Ofertas enviadas", pendingOffers.toString(), Modifier.weight(1f), onClick = onOpenOffers)
                    StatTile("Paseos", walkList.count { it.status == TicketStatus.COMPLETED || it.status == TicketStatus.REVIEWED }.toString(), Modifier.weight(1f))
                }
            }

            if (counters.isNotEmpty()) {
                item { SectionHeader("Contraofertas por responder") }
                items(counters, key = { "c_" + it.id }) { app ->
                    PetGoCard(Modifier.fillMaxWidth(), onClick = onOpenOffers) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalOffer, null, tint = PetGoTheme.colors.info)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${app.client.name} te ofrece ${Format.price(app.offerPrice)}", style = MaterialTheme.typography.titleSmall)
                                Text(Format.dateTime(app.ticket.date, app.ticket.hour), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }
            }

            item { SectionHeader("Mis paseos confirmados") }
            if (active.isEmpty()) {
                item {
                    PetGoCard(Modifier.fillMaxWidth()) {
                        Text("No tienes paseos confirmados", style = MaterialTheme.typography.titleSmall)
                        Text("Revisa las solicitudes disponibles y envía tu oferta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PetGoButton("Ver solicitudes", onClick = onBrowse, icon = Icons.AutoMirrored.Filled.DirectionsWalk)
                    }
                }
            } else {
                items(active, key = { it.id }) { t -> WalkCard(t, onClick = { onOpenWalk(t.id) }) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    PetGoCard(modifier, onClick = onClick) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WalkCard(t: Ticket, onClick: () -> Unit) {
    val st = t.status.style()
    PetGoCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(t.client.photoUrl, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.client.fullName, style = MaterialTheme.typography.titleSmall)
                Text(Format.dateTime(t.date, t.hour), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(st.label, st.color)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(Icons.Filled.Timer, Format.duration(t.durationMinutes))
            t.agreedPrice?.let { InfoRow(Icons.Filled.AttachMoney, Format.price(it)) }
        }
        PetChipsRow(t.pets)
    }
}

// ============================================================ Solicitudes disponibles (walker/listTickets.jsp)

@Composable
fun AvailableTicketsScreen(user: User, onEditProfile: () -> Unit, vm: AvailableTicketsViewModel = hiltViewModel()) {
    val state by vm.tickets.collectAsStateWithLifecycle()
    val myOffers by vm.myOffers.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var offerFor by remember { mutableStateOf<Ticket?>(null) }
    var petDetail by remember { mutableStateOf<PetSummary?>(null) }
    CollectMessages(vm.messages)

    ScreenScaffold(title = "Solicitudes disponibles") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!user.available) {
                Surface(color = PetGoTheme.colors.warning.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Estás como no disponible. Actívate para enviar ofertas.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = vm::becomeAvailable, enabled = !busy) { Text("Activar") }
                    }
                }
            }
            when (val s = state) {
                ListState.Loading -> LoadingOverlay()
                is ListState.Ready -> if (s.items.isEmpty()) {
                    EmptyState(Icons.Filled.Inbox, "No hay solicitudes por ahora", message = "Las nuevas solicitudes aparecerán aquí automáticamente.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(s.items, key = { it.id }) { t ->
                            AvailableTicketCard(
                                t, myOffer = myOffers[t.id], canOffer = user.available,
                                onOffer = { offerFor = t }, onPet = { petDetail = it },
                            )
                        }
                    }
                }
            }
        }
    }

    offerFor?.let { t ->
        OfferSheet(t, busy = busy, needsProfile = user.experience.isBlank(), onEditProfile = { offerFor = null; onEditProfile() }, onDismiss = { offerFor = null }) { price, setError ->
            vm.sendOffer(t, price, onValidation = { setError(it.error(Fields.PRICE)) }, onDone = { offerFor = null })
        }
    }
    petDetail?.let { pet ->
        ModalBottomSheet(onDismissRequest = { petDetail = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(pet.photoUrl, 72.dp, contentDescription = pet.name)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(pet.name, style = MaterialTheme.typography.titleLarge)
                        Text("${PetBreeds.displayName(pet.race)} · ${pet.gender.label} · ${Format.age(pet.age)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Comportamiento", style = MaterialTheme.typography.titleSmall)
                Text(pet.behavior)
            }
        }
    }
}

@Composable
private fun AvailableTicketCard(t: Ticket, myOffer: Application?, canOffer: Boolean, onOffer: () -> Unit, onPet: (PetSummary) -> Unit) {
    PetGoCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(t.client.photoUrl, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.client.fullName, style = MaterialTheme.typography.titleSmall)
                Text(Format.date(t.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(Icons.Filled.Schedule, Format.time(t.hour))
            InfoRow(Icons.Filled.Timer, Format.duration(t.durationMinutes))
            InfoRow(Icons.Filled.Pets, if (t.pets.size == 1) "1 mascota" else "${t.pets.size} mascotas")
        }
        PetChipsRow(t.pets, onPetClick = onPet)
        if (myOffer != null) {
            val st = myOffer.status.style()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tu oferta: ${Format.price(myOffer.offerPrice)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(st.label, st.color)
            }
        } else {
            PetGoButton("Enviar oferta", onClick = onOffer, enabled = canOffer, icon = Icons.Filled.LocalOffer, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OfferSheet(
    t: Ticket,
    busy: Boolean,
    needsProfile: Boolean,
    onEditProfile: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (Double?, (String?) -> Unit) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tu oferta para ${t.client.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                "${Format.dateTime(t.date, t.hour)} · ${Format.duration(t.durationMinutes)} · ${t.pets.joinToString { it.name }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (needsProfile) {
                PetGoCard(Modifier.fillMaxWidth(), onClick = onEditProfile) {
                    InfoRow(Icons.Filled.Info, "Tip: completa tu experiencia", color = PetGoTheme.colors.warning)
                    Text("Es lo que el dueño verá junto a tu precio.", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(7); error = null },
                label = { Text("Precio por el paseo") },
                prefix = { Text("$") },
                isError = error != null,
                supportingText = { Text(error ?: "El dueño puede aceptar, rechazar o hacerte una contraoferta.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            PetGoButton(
                "Enviar oferta", loading = busy, modifier = Modifier.fillMaxWidth(),
                onClick = { onSubmit(text.replace(',', '.').toDoubleOrNull()) { error = it } },
            )
        }
    }
}

// ============================================================ Mis ofertas (walker/listApplications.jsp)

private enum class OfferFilter(val label: String) { ACTION("Por responder"), OPEN("Pendientes"), CLOSED("Cerradas") }

@Composable
fun WalkerApplicationsScreen(onOpenWalk: (String) -> Unit, vm: WalkerApplicationsViewModel = hiltViewModel()) {
    val state by vm.applications.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<OfferFilter?>(null) }
    CollectMessages(vm.messages)

    ScreenScaffold(title = "Mis ofertas") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val all = (state as? ListState.Ready)?.items
            // Por defecto: "Por responder" si hay contraofertas, si no "Pendientes".
            val effective = filter ?: if (all.orEmpty().any { it.status == ApplicationStatus.COUNTERED }) OfferFilter.ACTION else OfferFilter.OPEN
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OfferFilter.entries.forEach { f -> FilterChip(selected = effective == f, onClick = { filter = f }, label = { Text(f.label) }) }
            }
            if (all == null) {
                LoadingOverlay(); return@Column
            }
            val shown = all.filter {
                when (effective) {
                    OfferFilter.ACTION -> it.status == ApplicationStatus.COUNTERED
                    OfferFilter.OPEN -> it.status == ApplicationStatus.PENDING
                    OfferFilter.CLOSED -> it.status == ApplicationStatus.ACCEPTED || it.status == ApplicationStatus.REJECTED
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Filled.LocalOffer,
                    when (effective) {
                        OfferFilter.ACTION -> "No tienes contraofertas"
                        OfferFilter.OPEN -> "No tienes ofertas pendientes"
                        OfferFilter.CLOSED -> "Aún no hay ofertas cerradas"
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.id }) { app ->
                        ApplicationCard(
                            app, busy,
                            onAccept = { vm.acceptCounter(app) }, onReject = { vm.rejectCounter(app) },
                            onOpenWalk = { onOpenWalk(app.ticketId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationCard(app: Application, busy: Boolean, onAccept: () -> Unit, onReject: () -> Unit, onOpenWalk: () -> Unit) {
    val st = app.status.style()
    PetGoCard(Modifier.fillMaxWidth(), onClick = if (app.status == ApplicationStatus.ACCEPTED) onOpenWalk else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(app.client.photoUrl, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(app.client.fullName, style = MaterialTheme.typography.titleSmall)
                Text(Format.dateTime(app.ticket.date, app.ticket.hour), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(st.label, st.color)
        }
        Text(
            "${Format.duration(app.ticket.durationMinutes)} · ${app.ticket.petNames.joinToString()}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (app.status == ApplicationStatus.COUNTERED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Te ofrecen ${Format.price(app.offerPrice)}", style = MaterialTheme.typography.titleMedium, color = PetGoTheme.colors.info)
                    Text("Tú pediste ${Format.price(app.originalPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onReject, enabled = !busy) { Text("Rechazar", color = MaterialTheme.colorScheme.error) }
                Button(onClick = onAccept, enabled = !busy) { Text("Aceptar") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tu oferta: ${Format.price(app.offerPrice)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (app.status == ApplicationStatus.ACCEPTED) Text("Ver paseo ›", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ============================================================ Reseñas (walker/reviews.jsp)

@Composable
fun WalkerReviewsScreen(vm: WalkerReviewsViewModel = hiltViewModel()) {
    val data by vm.reviews.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Mis reseñas") { padding ->
        val d = data
        if (d == null) {
            LoadingOverlay(Modifier.padding(padding)); return@ScreenScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatsHeader(d.stats) }
            if (d.reviews.isEmpty()) {
                item { EmptyState(Icons.Filled.StarBorder, "Aún no tienes reseñas", message = "Cuando completes paseos, los dueños podrán calificarte.") }
            } else {
                item { SectionHeader("Comentarios") }
                items(d.reviews, key = { it.ticketId }) { r ->
                    PetGoCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(r.client.photoUrl, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(r.client.fullName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StarsDisplay(r.stars.toDouble())
                        }
                        if (r.comment.isNotBlank()) Text("“${r.comment}”")
                        Text(
                            "Puntualidad ${r.punctuality}/5 · Trato ${r.treatment}/5",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(stats: WalkerStats) {
    Column(
        Modifier.fillMaxWidth().background(PetGoTheme.colors.headerGradient, MaterialTheme.shapes.large).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (stats.totalReviews == 0) "—" else Format.rating(stats.averageStars), style = MaterialTheme.typography.displaySmall, color = Color.White)
        StarsDisplay(stats.averageStars, size = 22.dp)
        Text(
            if (stats.totalReviews == 1) "1 reseña" else "${stats.totalReviews} reseñas",
            color = Color.White.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniStat("Puntualidad", stats.averagePunctuality, stats.totalReviews > 0)
            MiniStat("Trato", stats.averageTreatment, stats.totalReviews > 0)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Double, hasData: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (hasData) Format.rating(value) + "/5" else "—", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
    }
}

// ============================================================ Detalle de un paseo (NUEVO para el paseador)

@Composable
fun WalkDetailScreen(onBack: () -> Unit, vm: WalkDetailViewModel = hiltViewModel()) {
    val ticket by vm.ticket.collectAsStateWithLifecycle()
    val client by vm.client.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var petDetail by remember { mutableStateOf<PetSummary?>(null) }

    ScreenScaffold(title = "Paseo", onBack = onBack) { padding ->
        val t = ticket
        if (t == null) {
            LoadingOverlay(Modifier.padding(padding)); return@ScreenScaffold
        }
        val st = t.status.style()
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PetGoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.date(t.date), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    StatusChip(st.label, st.color)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoRow(Icons.Filled.Schedule, Format.time(t.hour))
                    InfoRow(Icons.Filled.Timer, Format.duration(t.durationMinutes))
                    t.agreedPrice?.let { InfoRow(Icons.Filled.AttachMoney, Format.price(it)) }
                }
                Text(
                    when (t.status) {
                        TicketStatus.ASSIGNED -> "El dueño marcará el inicio cuando le entregues a su mascota."
                        TicketStatus.IN_PROGRESS -> "Paseo en curso. El dueño lo marcará como terminado al recibirla."
                        TicketStatus.COMPLETED, TicketStatus.REVIEWED -> "Paseo terminado. ¡Buen trabajo!"
                        TicketStatus.CANCELED -> "Este paseo fue cancelado."
                        TicketStatus.PENDING -> "Esperando confirmación."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionHeader("Dueño")
            PetGoCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(t.client.photoUrl, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(t.client.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val phone = client?.phone
                    if (!phone.isNullOrBlank() && (t.status == TicketStatus.ASSIGNED || t.status == TicketStatus.IN_PROGRESS)) {
                        FilledTonalIconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }) {
                            Icon(Icons.Filled.Phone, "Llamar al dueño")
                        }
                    }
                }
            }

            SectionHeader("Mascotas")
            t.pets.forEach { pet ->
                PetGoCard(Modifier.fillMaxWidth(), onClick = { petDetail = pet }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(pet.photoUrl, 48.dp, contentDescription = pet.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pet.name, style = MaterialTheme.typography.titleSmall)
                            Text("${PetBreeds.displayName(pet.race)} · ${pet.gender.label} · ${Format.age(pet.age)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(pet.behavior, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    petDetail?.let { pet ->
        ModalBottomSheet(onDismissRequest = { petDetail = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pet.name, style = MaterialTheme.typography.titleLarge)
                Text("Comportamiento", style = MaterialTheme.typography.titleSmall)
                Text(pet.behavior)
            }
        }
    }
}
