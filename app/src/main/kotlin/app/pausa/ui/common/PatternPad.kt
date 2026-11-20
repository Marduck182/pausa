package app.pausa.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pausa.domain.security.AttemptResult
import app.pausa.domain.security.Pattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A 3x3 grid to draw an unlock pattern on with a finger, like the one Android uses for the lock screen. Passing
 * over a dot that was not visited takes it too (see [Pattern.extend]). When the finger lifts, [onPattern] gets the
 * dots; the drawing stays for half a second (in the error colour if [isError]) and then clears.
 *
 * It is drawn with the finger, so it needs no keyboard: that is what makes it usable on the screen Pausa draws
 * over another app, where typing a PIN safely is not possible.
 */
@Composable
fun PatternPad(
    onPattern: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var dots by remember { mutableStateOf(emptyList<Int>()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var finished by remember { mutableIntStateOf(0) }

    // After the finger lifts the shape is left visible briefly (so a wrong pattern can be seen), then cleared.
    LaunchedEffect(finished) {
        if (finished > 0) {
            delay(CLEAR_MILLIS)
            dots = emptyList()
        }
    }

    fun dotAt(point: Offset): Int? {
        val cell = size.width / GRID.toFloat()
        if (cell <= 0f) return null
        for (index in 0 until GRID * GRID) {
            val center = Offset((index % GRID + 0.5f) * cell, (index / GRID + 0.5f) * cell)
            if ((point - center).getDistance() <= cell * HIT_FRACTION) return index
        }
        return null
    }

    val primary = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val idle = MaterialTheme.colorScheme.outlineVariant
    val color = if (isError) errorColor else primary

    Canvas(
        modifier
            .aspectRatio(1f)
            .onSizeChanged { size = it }
            .semantics { contentDescription = "Zona para dibujar el patrón con el dedo" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        dots = emptyList()
                        finger = start
                        dotAt(start)?.let { dots = Pattern.extend(dots, it) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        finger = change.position
                        dotAt(change.position)?.let { hit -> dots = Pattern.extend(dots, hit) }
                    },
                    onDragEnd = {
                        finger = null
                        if (dots.size >= 2) {
                            onPattern(dots)
                            finished++
                        } else {
                            dots = emptyList()
                        }
                    },
                    onDragCancel = {
                        finger = null
                        dots = emptyList()
                    },
                )
            },
    ) {
        val cell = this.size.width / GRID
        fun center(index: Int) = Offset((index % GRID + 0.5f) * cell, (index / GRID + 0.5f) * cell)

        val line = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        for (i in 1 until dots.size) {
            drawLine(color.copy(alpha = 0.7f), center(dots[i - 1]), center(dots[i]), line.width, StrokeCap.Round)
        }
        val tip = finger
        if (dots.isNotEmpty() && tip != null) {
            drawLine(color.copy(alpha = 0.4f), center(dots.last()), tip, line.width, StrokeCap.Round)
        }
        for (index in 0 until GRID * GRID) {
            val taken = index in dots
            drawCircle(if (taken) color else idle, radius = if (taken) 9.dp.toPx() else 7.dp.toPx(), center = center(index))
            if (taken) drawCircle(color.copy(alpha = 0.18f), radius = 22.dp.toPx(), center = center(index))
        }
    }
}

/**
 * Asks for the pattern and checks it with [verify]. Wrong attempts are counted for the whole process (see
 * [Attempts]): after five, drawing is paused, for longer each time. Used on the lock screen over an app and in the dialogs that ask for the current pattern.
 */
@Composable
fun PatternChallenge(
    verify: suspend (List<Int>) -> Boolean,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
    idleHint: String = "Dibuja tu patrón.",
) {
    var wrong by remember { mutableStateOf(false) }
    // The wait may already be running from an earlier screen: the count is not this one's to reset.
    var lockedSeconds by remember { mutableIntStateOf(Attempts.seconds(Attempts.pattern.remainingLockMillis(Attempts.now()))) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lockedSeconds > 0) {
        while (lockedSeconds > 0) {
            delay(1_000)
            lockedSeconds--
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PatternPad(
            onPattern = { dots ->
                // A second stroke drawn while the first is being checked is ignored, not checked as another try.
                if (!checking) {
                    checking = true
                    scope.launch {
                        val result = try {
                            Attempts.pattern.attempt(Attempts::now) { verify(dots) }
                        } finally {
                            checking = false
                        }
                        when (result) {
                            AttemptResult.Success -> onVerified()
                            is AttemptResult.Wrong -> {
                                wrong = true
                                if (result.lockedForMillis > 0) lockedSeconds = Attempts.seconds(result.lockedForMillis)
                            }
                            is AttemptResult.Locked -> lockedSeconds = Attempts.seconds(result.remainingMillis)
                        }
                    }
                }
            },
            isError = wrong,
            enabled = lockedSeconds == 0 && !checking,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = when {
                lockedSeconds > 0 -> "Demasiados intentos. Espera $lockedSeconds s."
                wrong -> "Patrón incorrecto."
                else -> idleHint
            }.ifEmpty { " " }, // the line is kept so the pad does not jump when an error appears

            style = MaterialTheme.typography.bodyMedium,
            color = if (wrong || lockedSeconds > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val GRID = 3
private const val HIT_FRACTION = 0.3f
private const val CLEAR_MILLIS = 500L
