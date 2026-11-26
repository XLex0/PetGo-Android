@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ec.edu.epn.petgo.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ec.edu.epn.petgo.core.designsystem.PetGoWarning
import ec.edu.epn.petgo.core.designsystem.PillShape
import ec.edu.epn.petgo.core.designsystem.components.AvatarImage
import ec.edu.epn.petgo.data.image.CloudinaryUrls
import ec.edu.epn.petgo.domain.model.LocalImage
import ec.edu.epn.petgo.domain.model.PetBreeds
import ec.edu.epn.petgo.domain.model.PetSummary
import ec.edu.epn.petgo.domain.validation.TicketValidator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// ---------------------------------------------------------------- Barras y secciones

@Composable
fun PetGoTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Fila ícono + texto (fecha, hora, duración, precio…). */
@Composable
fun InfoRow(icon: ImageVector, text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Avatar que pide una miniatura a Cloudinary del tamaño mostrado. */
@Composable
fun UserAvatar(url: String?, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val px = (size.value * 3).toInt() // ~xxhdpi
    AvatarImage(url = CloudinaryUrls.thumbnail(url, px), contentDescription = contentDescription, size = size, modifier = modifier, borderWidth = 2.dp)
}

/** Chips con foto + nombre de las mascotas de un paseo. */
@Composable
fun PetChipsRow(pets: List<PetSummary>, modifier: Modifier = Modifier, onPetClick: ((PetSummary) -> Unit)? = null) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pets.forEach { pet ->
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = if (onPetClick != null) Modifier.clip(PillShape).clickable { onPetClick(pet) } else Modifier,
            ) {
                Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(pet.photoUrl, 24.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(pet.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Estrellas de solo lectura. */
@Composable
fun StarsDisplay(value: Double, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Row(modifier) {
        for (i in 1..5) {
            val icon = when {
                value >= i -> Icons.Filled.Star
                value >= i - 0.5 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(icon, null, tint = PetGoWarning, modifier = Modifier.size(size))
        }
    }
}

/** Estrellas seleccionables 1–5. */
@Composable
fun StarsInput(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        for (i in 1..5) {
            IconButton(onClick = { onChange(i) }) {
                Icon(
                    if (i <= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$i estrellas",
                    tint = PetGoWarning,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

/** Escala 1–5 con emojis (la web usaba sliders con emojis para puntualidad y trato). */
@Composable
fun EmojiScale(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val emojis = listOf("😞", "🙁", "😐", "🙂", "😄")
    val texts = listOf("Muy malo", "Malo", "Regular", "Bueno", "Excelente")
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(texts[value - 1], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            emojis.forEachIndexed { index, emoji ->
                val selected = index + 1 == value
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onChange(index + 1) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Selectores de fecha, hora y duración

/** Campo de solo lectura que abre un selector al tocarlo. */
@Composable
fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(icon, null) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        // Capa transparente: OutlinedTextField de solo lectura no recibe clicks propios.
        Box(Modifier.matchParentSize().padding(bottom = if (error != null) 20.dp else 0.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick))
    }
}

/** DatePicker que no permite fechas pasadas (TicketValidator: fecha ≥ hoy). */
@Composable
fun FutureDatePickerDialog(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val todayUtcMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
            override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state = state) }
}

@Composable
fun PetGoTimePickerDialog(initial: LocalTime?, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial?.hour ?: 9, initialMinute = initial?.minute ?: 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hora de inicio") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** MEJORA: chips de duración en lugar de un campo de hora "HH:mm" (la web guardaba la duración como LocalTime). */
@Composable
fun DurationChips(selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    val options = listOf(30, 45, 60, 90, 120, TicketValidator.MAX_DURATION)
    Column(modifier) {
        Text("Duración", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { minutes ->
                val isSelected = selected == minutes
                val check: (@Composable () -> Unit)? = if (isSelected) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                } else null
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(minutes) },
                    label = { Text(Format.duration(minutes)) },
                    leadingIcon = check,
                )
            }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- Imagen

/**
 * Selector de foto con vista previa (Photo Picker del sistema, sin pedir permisos).
 * Muestra la imagen local recién elegida o, si no hay, la URL actual.
 */
@Composable
fun PhotoPickerBox(
    localImage: LocalImage?,
    currentUrl: String?,
    onPicked: (LocalImage) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    circle: Boolean = true,
    error: String? = null,
    placeholderIcon: ImageVector = Icons.Filled.CameraAlt,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(LocalImage(uri.toString()))
    }
    val shape = if (circle) CircleShape else RoundedCornerShape(16.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(BorderStroke(2.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary), shape)
                .clickable { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            val model: Any? = localImage?.uri ?: CloudinaryUrls.limit(currentUrl, 600)
            if (model != null) {
                AsyncImage(model = model, contentDescription = "Foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(placeholderIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size / 3))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Edit, "Cambiar foto", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- Raza con búsqueda

/** MEJORA: buscador de raza (la web usaba un select de 33 opciones). */
@Composable
fun BreedPickerField(value: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(
        label = "Raza",
        value = if (value.isBlank()) "" else PetBreeds.displayName(value),
        icon = Icons.Filled.Pets,
        onClick = { open = true },
        modifier = modifier,
        error = error,
    )
    if (open) {
        var query by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar raza…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val filtered = PetBreeds.ALL.filter { it.contains(query.trim().lowercase()) }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered) { breed ->
                        ListItem(
                            headlineContent = { Text(PetBreeds.displayName(breed)) },
                            trailingContent = { if (breed == value) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { onSelect(breed); open = false },
                        )
                    }
                    if (filtered.isEmpty()) {
                        item { Text("No encontramos esa raza.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Scaffold de pantalla

/**
 * Scaffold de cada pantalla. [topLevel] = pantalla de pestaña (la barra inferior ya consume el inset inferior).
 */
@Composable
fun ScreenScaffold(
    title: String?,
    onBack: (() -> Unit)? = null,
    topLevel: Boolean = onBack == null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { if (title != null) PetGoTopBar(title, onBack, actions) },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (topLevel) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        } else {
            WindowInsets.safeDrawing
        },
        content = content,
    )
}
