@file:OptIn(ExperimentalMaterial3Api::class)

package ec.edu.epn.petgo.ui.feature.client

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import ec.edu.epn.petgo.core.designsystem.components.ConfirmDialog
import ec.edu.epn.petgo.core.designsystem.components.EmptyState
import ec.edu.epn.petgo.core.designsystem.components.LoadingOverlay
import ec.edu.epn.petgo.core.designsystem.components.PetGoButton
import ec.edu.epn.petgo.core.designsystem.components.PetGoCard
import ec.edu.epn.petgo.core.designsystem.components.PetGoTextField
import ec.edu.epn.petgo.data.image.CloudinaryUrls
import ec.edu.epn.petgo.domain.model.Gender
import ec.edu.epn.petgo.domain.model.Pet
import ec.edu.epn.petgo.domain.model.PetBreeds
import ec.edu.epn.petgo.domain.validation.Fields
import ec.edu.epn.petgo.ui.common.BreedPickerField
import ec.edu.epn.petgo.ui.common.CollectMessages
import ec.edu.epn.petgo.ui.common.Format
import ec.edu.epn.petgo.ui.common.InfoRow
import ec.edu.epn.petgo.ui.common.ListState
import ec.edu.epn.petgo.ui.common.PhotoPickerBox
import ec.edu.epn.petgo.ui.common.ScreenScaffold
import ec.edu.epn.petgo.ui.common.UserAvatar
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

// ============================================================ Lista (client/pet/list.jsp)

@Composable
fun PetsScreen(onAddPet: () -> Unit, vm: PetsViewModel = hiltViewModel()) {
    val state by vm.pets.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Pet?>(null) }
    var toRemove by remember { mutableStateOf<Pet?>(null) }
    CollectMessages(vm.messages)

    ScreenScaffold(
        title = "Mis mascotas",
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddPet, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Agregar") })
        },
    ) { padding ->
        when (val s = state) {
            ListState.Loading -> LoadingOverlay(Modifier.padding(padding))
            is ListState.Ready -> if (s.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Pets,
                    title = "No tienes mascotas registradas",
                    message = "Agrega a tu mascota para poder programar paseos.",
                    action = { PetGoButton("Registrar primera mascota", onClick = onAddPet) },
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.items, key = { it.id }) { pet -> PetRow(pet, onClick = { selected = pet }) }
                }
            }
        }
    }

    selected?.let { pet ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            PetDetail(pet, onRemove = { toRemove = pet; selected = null })
        }
    }
    toRemove?.let { pet ->
        ConfirmDialog(
            title = "¿Retirar a ${pet.name}?",
            message = "Ya no aparecerá en tu lista ni podrás incluirla en nuevos paseos.",
            confirmText = "Retirar", dismissText = "Cancelar", destructive = true,
            onConfirm = { vm.remove(pet); toRemove = null }, onDismiss = { toRemove = null },
        )
    }
}

@Composable
private fun PetRow(pet: Pet, onClick: () -> Unit) {
    PetGoCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(pet.photoUrl, 64.dp, contentDescription = pet.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pet.name, style = MaterialTheme.typography.titleMedium)
                Text(PetBreeds.displayName(pet.race), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow(Icons.Filled.Cake, Format.age(pet.age))
                    InfoRow(Icons.Filled.FitnessCenter, Format.weight(pet.weight))
                    InfoRow(if (pet.gender == Gender.MALE) Icons.Filled.Male else Icons.Filled.Female, pet.gender.label)
                }
            }
        }
    }
}

/** Detalle reutilizable (también lo ve el paseador desde una solicitud). */
@Composable
fun PetDetail(pet: Pet, onRemove: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AsyncImage(
            model = CloudinaryUrls.limit(pet.photoUrl, 900),
            contentDescription = pet.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large),
        )
        Text(pet.name, style = MaterialTheme.typography.headlineSmall)
        Text(PetBreeds.displayName(pet.race), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(Icons.Filled.Cake, Format.age(pet.age))
            InfoRow(Icons.Filled.FitnessCenter, Format.weight(pet.weight))
            InfoRow(if (pet.gender == Gender.MALE) Icons.Filled.Male else Icons.Filled.Female, pet.gender.label)
        }
        Text("Comportamiento", style = MaterialTheme.typography.titleSmall)
        Text(pet.behavior)
        if (onRemove != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRemove, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(8.dp)); Text("Retirar mascota")
            }
        }
    }
}

// ============================================================ Formulario (client/pet/register.jsp)

@Composable
fun PetFormScreen(onDone: () -> Unit, vm: PetFormViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    val e = f.errors

    ScreenScaffold(title = "Nueva mascota", onBack = onDone) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PhotoPickerBox(
                localImage = f.image, currentUrl = null,
                onPicked = { img -> vm.edit(Fields.IMAGE) { it.copy(image = img) } },
                circle = false, size = 160.dp, error = e.error(Fields.IMAGE), placeholderIcon = Icons.Filled.Pets,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text("Toca para elegir una foto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))

            PetGoTextField(f.name, { v -> vm.edit(Fields.NAME) { it.copy(name = v) } }, "Nombre", error = e.error(Fields.NAME), maxLength = 100, modifier = Modifier.fillMaxWidth())
            BreedPickerField(f.race, { v -> vm.edit(Fields.RACE) { it.copy(race = v) } }, error = e.error(Fields.RACE), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PetGoTextField(
                    f.age, { v -> vm.edit(Fields.AGE) { it.copy(age = v.filter(Char::isDigit)) } }, "Edad (años)",
                    keyboardType = KeyboardType.Number, error = e.error(Fields.AGE), maxLength = 2, modifier = Modifier.weight(1f),
                )
                PetGoTextField(
                    f.weight, { v -> vm.edit(Fields.WEIGHT) { it.copy(weight = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) } }, "Peso (kg)",
                    keyboardType = KeyboardType.Decimal, error = e.error(Fields.WEIGHT), maxLength = 6, modifier = Modifier.weight(1f),
                )
            }
            Text("Sexo", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Gender.entries.forEachIndexed { index, g ->
                    SegmentedButton(
                        selected = f.gender == g,
                        onClick = { vm.edit(Fields.GENDER) { it.copy(gender = g) } },
                        shape = SegmentedButtonDefaults.itemShape(index, Gender.entries.size),
                        icon = { Icon(if (g == Gender.MALE) Icons.Filled.Male else Icons.Filled.Female, null) },
                    ) { Text(g.label) }
                }
            }
            e.error(Fields.GENDER)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            PetGoTextField(
                f.behavior, { v -> vm.edit(Fields.BEHAVIOR) { it.copy(behavior = v) } }, "Comportamiento",
                placeholder = "Ej.: juguetón, tira de la correa, no le gustan otros perros…",
                singleLine = false, minLines = 3, maxLength = 500, error = e.error(Fields.BEHAVIOR),
                supportingText = "${f.behavior.length}/500", modifier = Modifier.fillMaxWidth(),
            )
            PetGoButton("Guardar mascota", onClick = { vm.submit(onDone) }, loading = busy, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}
