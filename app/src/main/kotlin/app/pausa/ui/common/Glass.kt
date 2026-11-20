package app.pausa.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeTint
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pausa.R
import app.pausa.ui.theme.LocalDarkTheme
import app.pausa.ui.theme.PrismColors

/*
 * The material of Pausa: glass. A sphere (a round body with a highlight at the top left and coloured light at the
 * bottom), a prismatic ring (a thin border with every colour of the spectrum, turning slowly) and a halo (a blurred
 * glow of those colours behind). Claro uses spheres and rounded rectangles; Oscuro uses capsules. The same three
 * pieces build everything, so the two looks feel like one app.
 *
 * Motion is spent where it says something: the hero pieces turn slowly, the small ones stand still. Everything
 * stands still when the user turned animations off.
 */

/** True when the user turned animations off in system settings. Everything that moves must then stand still. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** A value that goes 0 to 360 over [millis] and starts again; a constant 0 when [animated] is false or motion is off. */
@Composable
private fun rememberTurn(animated: Boolean, millis: Int): State<Float> {
    val reduce = rememberReduceMotion()
    if (!animated || reduce) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "turn")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
}

/** A sweep of the prism colours around the centre of the area, turned by [angle] degrees. */
private fun prismBrush(angle: Float, colors: List<Color> = PrismColors): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val cx = size.width / 2
        val cy = size.height / 2
        val sweep = android.graphics.SweepGradient(cx, cy, colors.map { it.toArgb() }.toIntArray(), null)
        sweep.setLocalMatrix(android.graphics.Matrix().apply { setRotate(angle, cx, cy) })
        return sweep
    }
}

/** The thin ring of every colour around a glass shape. [animated] makes it turn; small pieces leave it still. */
fun Modifier.prismBorder(
    shape: Shape,
    width: Dp = 1.5.dp,
    alpha: Float = 0.9f,
    animated: Boolean = true,
): Modifier = composed {
    val angle by rememberTurn(animated, millis = 10_000)
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = Stroke(width = width.toPx())
        onDrawWithContent {
            drawContent()
            drawOutline(outline, prismBrush(angle), alpha = alpha, style = stroke)
        }
    }
}
/**
 * Glass for rows, chips and panels: the backdrop behind it, blurred, with a faint tint and a bright top edge. Inside a
 * GlassStage the blur is real; anywhere else (a dialog, a preview) it falls back to a plain translucent fill.
 */
fun Modifier.glassSurface(shape: Shape, dark: Boolean, tint: Color = Color.Unspecified): Modifier = composed {
    val haze = LocalHazeState.current
    val fill = when {
        tint != Color.Unspecified -> tint
        dark -> Color.White.copy(alpha = 0.07f)
        else -> Color.White.copy(alpha = 0.50f)
    }
    val edge = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.9f), Color.White.copy(alpha = if (dark) 0.07f else 0.3f)),
    )
    Modifier
        .clip(shape)
        .then(
            if (haze != null) {
                Modifier.hazeEffect(haze) {
                    blurRadius = 24.dp
                    backgroundColor = if (dark) Color(0xFF05040A) else Color(0xFFF4F1FA)
                    tints = listOf(HazeTint(fill))
                    noiseFactor = 0.03f
                }
            } else {
                Modifier.background(fill)
            },
        )
        .border(1.dp, edge, shape)
}

/**
 * A panel with the material of the spheres: glass inside and a thin ring of every colour around it. The ring is
 * drawn outside the glass so the clip does not eat half of it, and it stands still (a panel is not a hero piece).
 */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(if (dark) 28.dp else 24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .prismBorder(shape, width = 1.2.dp, alpha = 0.8f, animated = false)
            .glassSurface(shape, dark)
            .padding(18.dp),
        content = content,
    )
}

/** The body of a sphere or of a glass button: a translucent fill, a highlight at the top left, coloured light at the bottom. */
private fun Modifier.glassBody(dark: Boolean, round: Boolean): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val fill = Color.White.copy(alpha = if (dark) 0.05f else 0.34f)
    val highlight = Color.White.copy(alpha = if (dark) 0.30f else 0.95f)
    val glow = Color(0xFFA99BFF).copy(alpha = if (dark) 0.42f else 0.5f)
    if (round) {
        drawCircle(fill)
        drawCircle(Brush.radialGradient(listOf(highlight, Color.Transparent), Offset(w * 0.32f, h * 0.2f), w * 0.42f))
        drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(w * 0.6f, h), w * 0.6f))
    } else {
        drawRect(fill)
        drawRect(Brush.radialGradient(listOf(highlight, Color.Transparent), Offset(w * 0.28f, 0f), w * 0.7f))
        drawRect(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(w * 0.62f, h * 1.3f), w * 0.8f))
    }
}

/**
 * A blurred glow of the prism colours. The glow is drawn smaller than its layer and the blur is allowed to spill past
 * it, so the layer's edge never shows (a square layer that turns would show up as a diamond). It turns as a layer, so
 * turning it costs the compositor a transform and no redraw.
 */
@Composable
fun GlassHalo(
    modifier: Modifier = Modifier,
    alpha: Float = 0.6f,
    blur: Dp = 28.dp,
    animated: Boolean = true,
    shape: Shape = CircleShape,
    inner: Float = if (shape == CircleShape) 0.8f else 1f,
) {
    val angle by rememberTurn(animated, millis = 16_000)
    val colors = remember(alpha) { PrismColors.map { it.copy(alpha = alpha) } }
    Box(modifier.graphicsLayer { rotationZ = if (shape == CircleShape) angle else 0f }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize(inner)
                .blur(blur, BlurredEdgeTreatment.Unbounded)
                .background(prismBrush(0f, colors), shape),
        )
    }
}

/** A multicolour sphere of glass with its halo. The content sits in its middle (a time, a word, an app's icon). */
@Composable
fun GlassSphere(
    size: Dp,
    modifier: Modifier = Modifier,
    halo: Boolean = true,
    animated: Boolean = true,
    /** Blur what is behind the sphere (the backdrop), so the colour inside it is calmer. Needs a GlassStage. */
    blur: Boolean = false,
    haloAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val dark = LocalDarkTheme.current
    val haze = LocalHazeState.current
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (halo) GlassHalo(Modifier.requiredSize(size * 1.75f), alpha = haloAlpha ?: if (dark) 0.5f else 0.8f)
        Box(
            Modifier
                .size(size)
                // The ring is drawn outside the clip below so the clip does not eat half of it.
                .prismBorder(CircleShape, 1.5.dp, animated = animated)
                .then(
                    if (blur && haze != null) {
                        Modifier.clip(CircleShape).hazeEffect(haze) {
                            blurRadius = 30.dp
                            backgroundColor = if (dark) Color(0xFF05040A) else Color(0xFFF4F1FA)
                            tints = listOf(HazeTint(Color.White.copy(alpha = if (dark) 0.06f else 0.38f)))
                            noiseFactor = 0.02f
                        }
                    } else {
                        Modifier
                    },
                )
                .glassBody(dark, round = true),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** The corner of an action in the current look: a capsule in Oscuro, a rounded rectangle in Claro. */
@Composable
fun actionShape(): Shape = if (LocalDarkTheme.current) CircleShape else RoundedCornerShape(18.dp)

/**
 * The main action of a screen, with whatever [content] it needs. In Claro it has the material of the spheres (glass
 * body, prismatic ring, halo behind); in Oscuro it is a glass capsule with a lit edge and, if [bead] is set, a small
 * sphere with the shield at its end. The stock Material buttons are replaced by this through ui/common/GlassControls.kt.
 */
@Composable
fun GlassActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bead: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val shape = actionShape()
    val glow = Color(0xFFA99BFF)
    Box(modifier.heightIn(min = if (dark) 60.dp else 56.dp).alpha(if (enabled) 1f else 0.62f), contentAlignment = Alignment.Center) {
        if (!dark) {
            // The halo of a button: a soft blurred band of the spectrum, as wide as the button.
            GlassHalo(
                Modifier.matchParentSize().padding(horizontal = 6.dp, vertical = 4.dp),
                alpha = 0.6f,
                blur = 14.dp,
                animated = false,
                shape = RoundedCornerShape(18.dp),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .then(if (dark) Modifier.shadow(14.dp, shape, clip = false, ambientColor = glow, spotColor = glow) else Modifier)
                .clip(shape)
                .then(
                    if (dark) {
                        Modifier
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))))
                            .glassBody(dark = true, round = false)
                    } else {
                        Modifier.glassBody(dark = false, round = false)
                    },
                )
                .prismBorder(shape, 1.5.dp, alpha = if (dark) 0.75f else 0.9f)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        )
        Row(
            Modifier.padding(start = if (bead) 64.dp else 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            ) { content() }
        }
        if (bead) {
            Box(Modifier.matchParentSize().padding(start = 10.dp), contentAlignment = Alignment.CenterStart) {
                GlassSphere(size = 44.dp, halo = false, animated = false) {
                    Image(painterResource(R.drawable.ic_shield), contentDescription = null, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

/** The main action with a label and, if given, a quieter second piece of text (a duration, a count) beside it. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
    bead: Boolean = false,
) {
    GlassActionButton(onClick, modifier, enabled, bead) {
        Text(text)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** A quieter action next to the main one, with whatever [content] it needs: glass without the ring or the halo. */
@Composable
fun GlassGhostAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    /** The main choice among quiet buttons: tinted like a selected chip, not filled bright. */
    emphasized: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val ink = if (contentColor != Color.Unspecified) contentColor else MaterialTheme.colorScheme.onBackground
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.62f)
            .prismBorder(actionShape(), width = 1.dp, alpha = 0.65f, animated = false)
            .glassSurface(actionShape(), dark)
            .then(if (emphasized) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides ink,
                LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            ) { content() }
        }
    }
}

@Composable
fun GlassGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassGhostAction(onClick, modifier, enabled) { Text(text) }
}
