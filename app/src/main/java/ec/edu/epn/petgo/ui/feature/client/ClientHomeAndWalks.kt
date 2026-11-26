@file:OptIn(ExperimentalMaterial3Api::class)

package ec.edu.epn.petgo.ui.feature.client

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ec.edu.epn.petgo.core.designsystem.PetGoTheme
import ec.edu.epn.petgo.core.designsystem.PillShape
import ec.edu.epn.petgo.core.designsystem.components.EmptyState
import ec.edu.epn.petgo.core.designsystem.components.LoadingOverlay
import ec.edu.epn.petgo.core.designsystem.components.PetGoButton
import ec.edu.epn.petgo.core.designsystem.components.PetGoCard
import ec.edu.epn.petgo.core.designsystem.components.StatusChip
import ec.edu.epn.petgo.domain.model.TicketStatus
import ec.edu.epn.petgo.domain.model.User
import ec.edu.epn.petgo.ui.common.CollectMessages
import ec.edu.epn.petgo.ui.common.Format
import ec.edu.epn.petgo.ui.common.InfoRow
import ec.edu.epn.petgo.ui.common.ListState
import ec.edu.epn.petgo.ui.common.PetChipsRow
import ec.edu.epn.petgo.ui.common.ScreenScaffold
import ec.edu.epn.petgo.ui.common.SectionHeader
import ec.edu.epn.petgo.ui.common.UserAvatar
import ec.edu.epn.petgo.ui.common.style

// ============================================================ Inicio del cliente

/**
 * MEJORA sobre client.jsp (3 tarjetas que solo enlazaban): el inicio muestra lo accionable
 * — ofertas por decidir, paseos por calificar, el próximo paseo y un atajo para programar otro.
 */
@Composable
fun ClientHomeScreen(
    user: User,
    onNewWalk: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onOpenTickets: () -> Unit,
    onAddPet: () -> Unit,
    vm: ClientWalksViewModel = hiltViewModel(),
) {
    val walks by vm.walks.collectAsStateWithLifecycle()
    val pets by vm.pets.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)

    ScreenScaffold(title = null) { padding ->
        val walkList = (walks as? ListState.Ready)?.items
        val petList = (pets as? ListState.Ready)?.items
        if (walkList == null || petList == null) {
            LoadingOverlay(Modifier.padding(padding)); return@ScreenScaffold
        }
        val attention = walkList.filter { it.needsAttention }
        val next = walkList.filter { it.ticket.status == TicketStatus.IN_PROGRESS || it.ticket.status == TicketStatus.ASSIGNED }
            .minByOrNull { it.ticket.date.atTime(it.ticket.hour) }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(PetGoTheme.colors.headerGradient, MaterialTheme.shapes.large).padding(20.dp),
                ) {
                    Text("Hola, ${user.name} 👋", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("¿Tu mascota necesita salir hoy?", color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = if (petList.isEmpty()) onAddPet else onNewWalk,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(if (petList.isEmpty()) Icons.Filled.Pets else Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (petList.isEmpty()) "Registra tu mascota" else "Programar paseo")
                    }
                }
            }

            if (petList.isEmpty()) {
                item {
                    PetGoCard(Modifier.fillMaxWidth()) {
                        Text("Primer paso: registra a tu mascota", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Con su foto, raza y comportamiento los paseadores sabrán cómo cuidarla.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (attention.isNotEmpty()) {
                item { SectionHeader("Necesita tu atención") }
                items(attention, key = { "att_" + it.ticket.id }) { item ->
                    AttentionCard(item, onClick = { onOpenTicket(item.ticket.id) })
                }
            }

            if (next != null) {
                item { SectionHeader(if (next.ticket.status == TicketStatus.IN_PROGRESS) "Paseo en curso" else "Próximo paseo") }
                item { TicketCard(next, onClick = { onOpenTicket(next.ticket.id) }) }
            }

            if (petList.isNotEmpty()) {
                item { SectionHeader("Tus mascotas") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(petList, key = { it.id }) { pet ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                                UserAvatar(pet.photoUrl, 64.dp, contentDescription = pet.name)
                                Text(pet.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
                            }
                        }
                        item {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable(onClick = onAddPet)) {
                                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Agregar mascota", tint = MaterialTheme.colorScheme.primary) }
                                }
                                Text("Agregar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (walkList.isNotEmpty()) {
                item {
                    TextButton(onClick = onOpenTickets, modifier = Modifier.fillMaxWidth()) { Text("Ver todos mis paseos (${walkList.size})") }
                }
            }
        }
    }
}

@Composable
private fun AttentionCard(item: TicketWithOffers, onClick: () -> Unit) {
    val (icon, title) = when {
        item.ticket.status == TicketStatus.COMPLETED -> Icons.Filled.Star to "Califica el paseo de ${petNames(item)}"
        item.pendingOffers == 1 -> Icons.Filled.LocalOffer to "1 oferta nueva para ${petNames(item)}"
        else -> Icons.Filled.LocalOffer to "${item.pendingOffers} ofertas nuevas para ${petNames(item)}"
    }
    PetGoCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PetGoTheme.colors.warning)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(Format.dateTime(item.ticket.date, item.ticket.hour), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

private fun petNames(item: TicketWithOffers): String = item.ticket.pets.joinToString(", ") { it.name }.ifEmpty { "tu mascota" }

/** Tarjeta de paseo reutilizada en Inicio y en la lista. */
@Composable
fun TicketCard(item: TicketWithOffers, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = item.ticket
    val style = t.status.style()
    PetGoCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.date(t.date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusChip(style.label, style.color)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(Icons.Filled.Schedule, Format.time(t.hour))
            InfoRow(Icons.Filled.Timer, Format.duration(t.durationMinutes))
            t.agreedPrice?.let { InfoRow(Icons.Filled.AttachMoney, Format.price(it)) }
        }
        PetChipsRow(t.pets)
        if (t.status == TicketStatus.PENDING) {
            val text = when (item.pendingOffers) {
                0 -> if (item.offers.isEmpty()) "Esperando ofertas de paseadores…" else "Sin ofertas nuevas"
                1 -> "1 oferta por revisar"
                else -> "${item.pendingOffers} ofertas por revisar"
            }
            val color = if (item.pendingOffers > 0) PetGoTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
            InfoRow(Icons.Filled.LocalOffer, text, color = color)
        }
    }
}

// ============================================================ Lista de paseos (client/ticket/list.jsp)

private enum class WalksFilter(val label: String) { ACTIVE("Activos"), HISTORY("Historial") }

@Composable
fun TicketsScreen(onNewWalk: () -> Unit, onOpenTicket: (String) -> Unit, vm: ClientWalksViewModel = hiltViewModel()) {
    val walks by vm.walks.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(WalksFilter.ACTIVE) }
    CollectMessages(vm.messages)

    ScreenScaffold(
        title = "Mis paseos",
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewWalk, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nuevo paseo") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WalksFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            when (val state = walks) {
                ListState.Loading -> LoadingOverlay()
                is ListState.Ready -> {
                    val history = setOf(TicketStatus.REVIEWED, TicketStatus.CANCELED)
                    val shown = state.items.filter { (it.ticket.status in history) == (filter == WalksFilter.HISTORY) }
                    if (shown.isEmpty()) {
                        val active = filter == WalksFilter.ACTIVE
                        val action: (@Composable () -> Unit)? = if (active) {
                            { PetGoButton("Programar paseo", onClick = onNewWalk) }
                        } else null
                        EmptyState(
                            icon = Icons.Filled.ConfirmationNumber,
                            title = if (active) "No tienes paseos activos" else "Aún no hay historial",
                            message = if (active) "Programa un paseo y los paseadores te enviarán sus ofertas." else null,
                            action = action,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(shown, key = { it.ticket.id }) { item -> TicketCard(item, onClick = { onOpenTicket(item.ticket.id) }) }
                        }
                    }
                }
            }
        }
    }
}
