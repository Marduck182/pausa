package app.pausa.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A thin ring that can be partly drawn, like a progress sweep, with [content] in its middle. The ring is the one
 * shape Noche is built from: it holds an hour on the home screen, a countdown in a pause, an app's icon on a block.
 * [progress] is how much of the ring is drawn (1 is all of it); the rest is a faint track.
 */
@Composable
fun Ring(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    progress: Float = 1f,
    strokeWidth: Dp = 3.dp,
    animate: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    // Between two readings a second apart the sweep glides instead of ticking.
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (animate) tween(durationMillis = 1_000, easing = LinearEasing) else tween(0),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arc = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arc, style = stroke)
            if (shown > 0f) drawArc(color, -90f, 360f * shown, false, Offset(inset, inset), arc, style = stroke)
        }
        content()
    }
}

/** The icon of an app inside a quiet full ring: how Pausa shows which app a block or a pause is about. */
@Composable
fun AppIconRing(
    packageName: String,
    color: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 104.dp,
    iconSize: Dp = 52.dp,
) {
    Ring(color = color.copy(alpha = 0.8f), modifier = modifier, size = ringSize, strokeWidth = 1.5.dp, animate = false) {
        AppIcon(packageName, Modifier.size(iconSize))
    }
}

/**
 * A small action drawn as an outlined pill. It replaces the stock chip, whose corners are squarer than the rest of
 * Noche. The touch target is 48 dp even though the pill is smaller.
 */
@Composable
fun PillChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.7f else 0.3f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) ink else ink.copy(alpha = 0.38f),
        )
    }
}
