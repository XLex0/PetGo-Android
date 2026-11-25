package ec.edu.epn.petgo.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PetGoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F5DC),
    onPrimaryContainer = PetGoDark,
    secondary = PetGoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF7EC),
    onSecondaryContainer = Color(0xFF0B5E47),
    tertiary = PetGoInfo,
    onTertiary = Color.White,
    background = PetGoLight,
    onBackground = Color(0xFF1B1F1C),
    surface = Color.White,
    onSurface = Color(0xFF1B1F1C),
    surfaceVariant = PetGoSurfaceAlt,
    onSurfaceVariant = PetGoGrayText,
    outline = Color(0xFFCED4DA),
    outlineVariant = PetGoBorder,
    error = PetGoDanger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = PetGoPrimaryDarkTheme,
    onPrimary = Color(0xFF00391A),
    primaryContainer = PetGoDark,
    onPrimaryContainer = Color(0xFFD4F5DC),
    secondary = PetGoSecondaryDarkTheme,
    onSecondary = Color(0xFF00382A),
    tertiary = Color(0xFF8AB8FF),
    background = PetGoBackgroundDarkTheme,
    onBackground = Color(0xFFE1E6E2),
    surface = PetGoSurfaceDarkTheme,
    onSurface = Color(0xFFE1E6E2),
    surfaceVariant = Color(0xFF223026),
    onSurfaceVariant = Color(0xFFB8C4BB),
    outlineVariant = Color(0xFF34443A),
    error = Color(0xFFFF8A94),
)

/** Colores semánticos que Material 3 no trae (warning, info, chips de paseo). */
@Immutable
data class PetGoExtendedColors(
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val walkChip: Color,
    val headerGradient: Brush,
)

private val LightExtended = PetGoExtendedColors(
    warning = PetGoWarning,
    onWarning = Color(0xFF3D2E00),
    info = PetGoInfo,
    walkChip = PetGoWalkChip,
    headerGradient = Brush.linearGradient(listOf(PetGoPrimary, PetGoSecondary)),
)

private val DarkExtended = PetGoExtendedColors(
    warning = Color(0xFFFFD54F),
    onWarning = Color(0xFF3D2E00),
    info = Color(0xFF8AB8FF),
    walkChip = Color(0xFF4C9A50),
    headerGradient = Brush.linearGradient(listOf(PetGoDark, Color(0xFF138A69))),
)

val LocalPetGoColors = staticCompositionLocalOf { LightExtended }

@Composable
fun PetGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPetGoColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PetGoTypography,
            shapes = PetGoShapes,
            content = content,
        )
    }
}

object PetGoTheme {
    val colors: PetGoExtendedColors
        @Composable get() = LocalPetGoColors.current
}
