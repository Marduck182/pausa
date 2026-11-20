package app.pausa.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.pausa.ui.theme.LocalDarkTheme

/*
 * The stock Material buttons and switch, redrawn as glass. They keep the names and the parameters the screens already
 * use, so a screen takes the new look by importing these instead of the ones from material3 and nothing else changes.
 * Button is the main action (glass with a prismatic ring); OutlinedButton and FilledTonalButton are the quiet ones.
 */

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    // In the dark look the main-action glass is bright and lilac; these everyday buttons (Create PIN, Add app...) take the
    // quiet style of the chips instead: dark blurred glass with the ring of colours. Claro keeps the sphere material.
    if (LocalDarkTheme.current) {
        GlassGhostAction(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    } else {
        GlassActionButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) = GlassGhostAction(onClick = onClick, modifier = modifier, enabled = enabled, contentColor = contentColor, content = content)

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = GlassGhostAction(onClick = onClick, modifier = modifier, enabled = enabled, content = content)

/** A switch whose track is the periwinkle of the glass in Claro and the lavender in Oscuro, not Material's heavy violet. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = LocalDarkTheme.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = if (dark) {
            SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1B0F4D), checkedTrackColor = Color(0xFFB9A7FF), checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f), uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.25f),
            )
        } else {
            SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF7F8DEB), checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.Black.copy(alpha = 0.18f),
                uncheckedBorderColor = Color.Transparent,
            )
        },
    )
}
