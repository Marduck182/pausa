package app.pausa.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Two looks, one lavender. Oscuro: near-black with a violet tint, glass over a slow aurora, amber only to say
 * "blocked". Claro: a silvery lilac studio with multicolour spheres. The backdrop of each is drawn by PausaBackdrop;
 * the background colours here are only what shows if something is drawn without it (a dialog, a system bar).
 * Containers are barely different from the background so the screen reads as glass shapes, not as boxes.
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
    background = Color(0xFF05040A),
    onBackground = Color(0xFFF1EFF8),
    surface = Color(0xFF05040A),
    onSurface = Color(0xFFF1EFF8),
    surfaceVariant = Color(0xFF1C1A26),
    onSurfaceVariant = Color(0xFFB4B0C4),
    outline = Color(0xFF7A7690),
    outlineVariant = Color(0xFF2A2640),
    surfaceContainerLowest = Color(0xFF05040A),
    surfaceContainerLow = Color(0xFF0C0A12),
    surfaceContainer = Color(0xFF13101B),
    surfaceContainerHigh = Color(0xFF1A1724),
    surfaceContainerHighest = Color(0xFF221F2E),
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
    background = Color(0xFFF4F1FA),
    onBackground = Color(0xFF16141F),
    surface = Color(0xFFF4F1FA),
    onSurface = Color(0xFF16141F),
    surfaceVariant = Color(0xFFEAE6F4),
    onSurfaceVariant = Color(0xFF4A4658),
    outline = Color(0xFF7C7890),
    outlineVariant = Color(0xFFDAD5E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F7FD),
    surfaceContainer = Color(0xFFF1EEF8),
    surfaceContainerHigh = Color(0xFFEAE6F4),
    surfaceContainerHighest = Color(0xFFE3DFEF),
)

/** The spectrum the prismatic ring and the halos are drawn with: pink, peach, lime, mint, cyan, lavender and back to pink. */
internal val PrismColors = listOf(
    Color(0xFFF28BB5),
    Color(0xFFF7B77E),
    Color(0xFFD6E88A),
    Color(0xFF8FE3C2),
    Color(0xFF7FD6F2),
    Color(0xFFA99BFF),
    Color(0xFFF28BB5),
)
