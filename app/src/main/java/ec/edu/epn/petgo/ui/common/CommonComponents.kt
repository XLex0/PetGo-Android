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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ec.edu.epn.petgo.data.image.CloudinaryUrls
import ec.edu.epn.petgo.domain.model.LocalImage

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
