package app.pausa.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Noche. Pure black with a single lavender accent, and amber only to say "blocked". The accent marks the one
 * action and what is active; everything else is greys. Containers are barely lighter than the background so the
 * screen reads as shapes on black, not as boxes.
 */
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A7FF),
    onPrimary = Color(0xFF1B0F4D),
    primaryContainer = Color(0xFF3B2D7A),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFC9C2E0),
    onSecondary = Color(0xFF2F2A45),
    secondaryContainer = Color(0xFF2A2640),
    onSecondaryContainer = Color(0xFFE6E1F5),
    tertiary = Color(0xFFFFB961),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFF9A9AA6),
    outline = Color(0xFF6E6E7A),
    outlineVariant = Color(0xFF26262D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0D),
    surfaceContainer = Color(0xFF111115),
    surfaceContainerHigh = Color(0xFF18181D),
    surfaceContainerHighest = Color(0xFF222228),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF5B45C6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0F4D),
    secondary = Color(0xFF5F5A78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E1F5),
    onSecondaryContainer = Color(0xFF1C1830),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1700),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0B0B10),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0B10),
    surfaceVariant = Color(0xFFEDEDF2),
    onSurfaceVariant = Color(0xFF5A5A66),
    outline = Color(0xFF8A8A96),
    outlineVariant = Color(0xFFE2E2EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F8FB),
    surfaceContainer = Color(0xFFF3F3F7),
    surfaceContainerHigh = Color(0xFFEDEDF2),
    surfaceContainerHighest = Color(0xFFE6E6EC),
)
