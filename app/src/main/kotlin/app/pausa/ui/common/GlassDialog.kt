package app.pausa.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pausa.ui.theme.LocalDarkTheme

/**
 * The stock Material dialog, redrawn in the look of the app: a rounded panel of dark (or light) glass with the ring of
 * colours around it. It keeps the name and the parameters the screens already use, so a screen takes the new look by
 * importing this instead of the one from material3.
 *
 * A dialog lives in its own window, so it cannot blur what is behind it the way the panels in a screen do; the panel is
 * made almost opaque instead, which is also what keeps its text readable over any screen. It is fully opaque: nothing of the screen behind should
 * show through the text.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(28.dp)
    val panel = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF1D1933), Color(0xFF0E0B18)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1EEF9)))
    }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Side margins: without them the panel touches the screen's edges and its corners are cut off.
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .prismBorder(shape, width = 1.2.dp, alpha = 0.85f, animated = false)
                .clip(shape)
                .background(panel)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                if (title != null) ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                if (text != null) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { text() }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
