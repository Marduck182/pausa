package app.pausa.ui.common

import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import app.pausa.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What every screen sits on. Claro: a mesh of soft pastel colours (pink, lilac, blue, mint, peach) that drifts as one
 * layer. Oscuro: a video that plays in a loop behind everything (screens in the app) or, where a video would be too
 * heavy (the screens drawn over other apps) and as its fallback, a near-black aurora whose four lights travel slowly.
 *
 * Claro's drift moves a layer drawn once (a transform per frame, no redraw). Oscuro redraws four gradients per
 * frame, which is cheap for the GPU. With animations off nothing moves in either.
 */
@Composable
fun PausaBackdrop(modifier: Modifier = Modifier, video: Boolean = false) {
    val dark = LocalDarkTheme.current
    if (dark) {
        // With the video playing the aurora is only what shows if it cannot, so it is drawn once and does not redraw.
        val useVideo = video && !rememberReduceMotion()
        val time = if (useVideo) remember { mutableFloatStateOf(0f) } else rememberLoop(periodMillis = AURORA_LOOP_MILLIS)
        Box(modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) { drawAurora(time.value) }
            if (useVideo) {
                BackdropVideo(Modifier.fillMaxSize())
                // Darkens and tints the video so text stays readable and it sits in the same violet as the rest.
                Box(Modifier.fillMaxSize().background(Color(0xFF05040A).copy(alpha = VIDEO_SCRIM)))
            }
        }
    } else {
        val drift = rememberDrift()
        Box(modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = drift.value
                        translationX = -size.width * 0.04f * p
                        translationY = size.height * 0.03f * p
                        scaleX = 1.12f
                        scaleY = 1.12f
                    },
            ) { drawMesh() }
        }
    }
}

/** The background that glass panels blur, shared with them through the composition. Null where there is none. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * A screen's stage: the backdrop at the back (which is what the glass blurs) and [content] on top. Every panel drawn
 * with glassSurface inside it blurs the backdrop behind it, so a panel reads as glass over the picture instead of as a
 * flat translucent rectangle.
 */
@Composable
fun GlassStage(
    modifier: Modifier = Modifier,
    video: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides state) {
        Box(modifier.fillMaxSize()) {
            PausaBackdrop(Modifier.hazeSource(state), video = video)
            content()
        }
    }
}

/** An angle in radians that goes 0 to 2π over [periodMillis] and starts again; constant when animations are off. */
@Composable
private fun rememberLoop(periodMillis: Int): State<Float> {
    if (rememberReduceMotion()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "loop",
    )
}

@Composable
private fun rememberDrift(): State<Float> {
    if (rememberReduceMotion()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
}

private class Blob(val x: Float, val y: Float, val radius: Float, val color: Color)

private fun DrawScope.blob(b: Blob, dx: Float = 0f, dy: Float = 0f, swell: Float = 1f) {
    val center = Offset(size.width * (b.x + dx), size.height * (b.y + dy))
    val radius = size.width * b.radius * swell
    drawCircle(Brush.radialGradient(listOf(b.color, Color.Transparent), center, radius), radius, center)
}

private val MeshBlobs = listOf(
    Blob(0.14f, 0.14f, 0.60f, Color(0xFFF2A5C4).copy(alpha = 0.75f)),
    Blob(0.88f, 0.26f, 0.60f, Color(0xFFB8A7F2).copy(alpha = 0.70f)),
    Blob(0.20f, 0.62f, 0.65f, Color(0xFFA6DDF2).copy(alpha = 0.70f)),
    Blob(0.86f, 0.78f, 0.60f, Color(0xFFB5EBCB).copy(alpha = 0.65f)),
    Blob(0.55f, 0.98f, 0.55f, Color(0xFFF4D8A0).copy(alpha = 0.60f)),
)

/** One light of the aurora: where it rests, how far and how fast it travels, and where in its loop it starts. */
private class Light(val blob: Blob, val reachX: Float, val reachY: Float, val speedX: Int, val speedY: Int, val phase: Float)

private val AuroraLights = listOf(
    Light(Blob(0.20f, 0.14f, 0.80f, Color(0xFF7A4CE0).copy(alpha = 0.60f)), 0.20f, 0.10f, 1, 2, 0.0f),
    Light(Blob(0.86f, 0.30f, 0.72f, Color(0xFF2BA8C8).copy(alpha = 0.46f)), 0.16f, 0.14f, 2, 1, 1.7f),
    Light(Blob(0.30f, 0.76f, 0.80f, Color(0xFFC040A0).copy(alpha = 0.44f)), 0.18f, 0.12f, 1, 1, 3.4f),
    Light(Blob(0.86f, 0.92f, 0.72f, Color(0xFF4C5CE0).copy(alpha = 0.48f)), 0.20f, 0.10f, 2, 2, 5.1f),
)

private const val AURORA_LOOP_MILLIS = 26_000

/** How much of the video's brightness is kept out: enough that text on top reads at 4.5:1 or better. */
private const val VIDEO_SCRIM = 0.52f

private fun DrawScope.drawMesh() {
    drawRect(
        Brush.linearGradient(
            listOf(Color(0xFFFBEFF8), Color(0xFFEFEFFB), Color(0xFFEEF8F2)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
    MeshBlobs.forEach { blob(it) }
}

/** The lights move on closed loops (each with its own speed and phase) and swell a little, so they never line up twice. */
private fun DrawScope.drawAurora(time: Float) {
    drawRect(Color(0xFF05040A))
    AuroraLights.forEach { l ->
        val dx = l.reachX * cos(time * l.speedX + l.phase)
        val dy = l.reachY * sin(time * l.speedY + l.phase)
        val swell = 1f + 0.12f * sin(time * 2 + l.phase)
        blob(l.blob, dx, dy, swell)
    }
}
