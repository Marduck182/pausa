package app.pausa.enforcement

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pausa.ui.common.AppIcon
import app.pausa.ui.common.AppIconRing
import app.pausa.ui.common.PatternChallenge
import app.pausa.ui.common.Ring
import app.pausa.ui.theme.NumeralStyle
import app.pausa.ui.theme.PausaTheme
import kotlinx.coroutines.delay

/*
 * The three screens Pausa draws over another app, in the Noche design: black, one ring, one accent. They appear at
 * the moment of the impulse, so each is read in a second, used with one thumb (the actions sit at the bottom, where
 * the thumb already is) and never leaves the user without a way out.
 *
 * The app the screen is about is always shown by its own icon, so there is no doubt about what is being paused or
 * blocked. The voice is calm and firm: what is happening and when the app comes back, in few words, never a judgement.
 * Movement is a short entrance and, if asked for, a slow breathing ring, and all of it stops when the user has
 * animations turned off.
 */

/** A pause before opening an app: a ring that counts down, then a choice. The primary action is to not open it. */
@Composable
fun InterventionContent(
    packageName: String,
    appLabel: String,
    waitSeconds: Int,
    breathing: Boolean,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
) {
    PausaTheme {
        var remaining by remember { mutableIntStateOf(waitSeconds) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1_000)
                remaining--
            }
        }

        OverlayFrame(
            icon = { AppIcon(packageName, Modifier.size(56.dp)) },
            title = "¿Abrir $appLabel?",
            message = if (breathing || waitSeconds == 0) null else "Tómate un momento antes de entrar.",
            body = { if (waitSeconds > 0) PauseRing(remaining, waitSeconds, breathing) },
            primaryLabel = "Mejor no",
            onPrimary = onCancel,
            secondaryLabel = if (remaining == 0) "Abrir $appLabel" else "Abrir en $remaining s",
            secondaryEnabled = remaining == 0,
            onSecondary = onContinue,
        )
    }
}

/**
 * An app, or a part of one, that is blocked right now. Its icon sits inside the ring. When [temporaryAllowMinutes]
 * is set it also offers a temporary pass, available only after [waitSeconds]: a deliberate act with a pause in front
 * of it, not a one-tap way around the block.
 */
@Composable
fun BlockContent(
    packageName: String,
    title: String,
    message: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    temporaryAllowMinutes: Int?,
    waitSeconds: Int,
    onAllowTemporarily: () -> Unit,
) {
    PausaTheme {
        var remaining by remember { mutableIntStateOf(waitSeconds) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1_000)
                remaining--
            }
        }

        OverlayFrame(
            icon = { AppIconRing(packageName, color = MaterialTheme.colorScheme.primary) },
            title = title,
            message = message,
            primaryLabel = dismissLabel,
            onPrimary = onDismiss,
            secondaryLabel = temporaryAllowMinutes?.let {
                if (remaining == 0) "Permitir $it minutos" else "Permitir $it min en $remaining s"
            },
            secondaryEnabled = remaining == 0,
            onSecondary = onAllowTemporarily,
        )
    }
}

/**
 * The pattern lock shown over an app that asks for the security pattern. It is drawn with the finger, so it needs
 * no keyboard. Backing out sends the user home; the app underneath is never usable while this is up.
 */
@Composable
fun PatternLockContent(
    packageName: String,
    appLabel: String,
    verify: suspend (List<Int>) -> Boolean,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    PausaTheme {
        OverlayFrame(
            icon = { AppIcon(packageName, Modifier.size(56.dp)) },
            title = "Dibuja tu patrón",
            message = "para abrir $appLabel",
            body = {
                PatternChallenge(
                    verify = verify,
                    onVerified = onUnlocked,
                    idleHint = "",
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(),
                )
            },
            primaryLabel = null,
            onPrimary = {},
            secondaryLabel = "Cancelar",
            secondaryEnabled = true,
            onSecondary = onCancel,
        )
    }
}

/**
 * The shared composition: the app's icon, a title and a short message in the middle, and the actions at the
 * bottom. The primary action is the filled pill; the secondary one is quiet text, so the easy path is the one the
 * user chose when they made the rule.
 */
@Composable
private fun OverlayFrame(
    icon: @Composable () -> Unit,
    title: String,
    message: String?,
    primaryLabel: String?,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    secondaryEnabled: Boolean,
    onSecondary: () -> Unit,
    body: @Composable () -> Unit = {},
) {
    val animationsOff = rememberAnimationsOff()
    var visible by remember { mutableStateOf(animationsOff) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 48.dp),
    ) {
        // A short fade and a small rise: it says "this just appeared" without making anyone wait for it.
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.weight(1f),
            enter = if (animationsOff) {
                EnterTransition.None
            } else {
                fadeIn(tween(ENTER_MILLIS, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(ENTER_MILLIS, easing = FastOutSlowInEasing)) { it / 40 }
            },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                icon()
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .semantics { heading() },
                )
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                body()
            }
        }

        if (primaryLabel != null) {
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(primaryLabel, style = MaterialTheme.typography.titleMedium) }
        }
        if (secondaryLabel != null) {
            TextButton(
                onClick = onSecondary,
                enabled = secondaryEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(top = if (primaryLabel != null) 4.dp else 0.dp),
            ) { Text(secondaryLabel, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** True when the user turned animations off in system settings. Everything that moves must then stand still. */
@Composable
private fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The ring of a pause: it empties as the wait runs out, with the seconds left inside. If the rule asks for a breathing
 * exercise the ring also swells and settles over four seconds each way and the word inside says which. With animations
 * off the ring stays still and only the words change.
 */
@Composable
private fun PauseRing(remaining: Int, total: Int, breathing: Boolean) {
    val animationsOff = rememberAnimationsOff()
    val transition = rememberInfiniteTransition(label = "breathing")
    val swell by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swell",
    )
    var inhale by remember { mutableStateOf(true) }
    LaunchedEffect(breathing) {
        while (breathing) {
            delay(BREATH_MILLIS.toLong())
            inhale = !inhale
        }
    }

    Ring(
        color = MaterialTheme.colorScheme.primary,
        size = 168.dp,
        progress = remaining.toFloat() / total,
        animate = !animationsOff,
        modifier = Modifier
            .padding(top = 32.dp)
            .scale(if (breathing && !animationsOff) swell else 1f),
    ) {
        when {
            breathing -> Text(
                if (inhale) "Inhala" else "Exhala",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            remaining > 0 -> Text(
                remaining.toString(),
                style = NumeralStyle.copy(fontSize = 56.sp, lineHeight = 60.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            else -> Text("Listo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val BREATH_MILLIS = 4_000
private const val ENTER_MILLIS = 220
