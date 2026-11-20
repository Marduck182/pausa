package app.pausa.enforcement

import app.pausa.ui.theme.LocalDarkTheme
import app.pausa.ui.common.GlassGhostAction
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.pausa.ui.common.GlassButton
import app.pausa.ui.common.GlassGhostButton
import app.pausa.ui.common.GlassSphere
import app.pausa.ui.common.GlassStage
import app.pausa.ui.common.PatternChallenge
import app.pausa.domain.model.ThemeMode
import app.pausa.ui.theme.NumeralStyle
import app.pausa.ui.theme.PausaTheme
import kotlinx.coroutines.delay

/*
 * The three screens Pausa draws over another app, in the current look (Oscuro or Claro): glass, one sphere, one action. They appear at
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
    themeMode: ThemeMode,
) {
    PausaTheme(themeMode) {
        var remaining by remember { mutableIntStateOf(waitSeconds) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1_000)
                remaining--
            }
        }

        OverlayFrame(
            icon = { GlassSphere(size = 88.dp, halo = false, blur = true) { AppIcon(packageName, Modifier.size(52.dp)) } },
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
    themeMode: ThemeMode,
) {
    PausaTheme(themeMode) {
        var remaining by remember { mutableIntStateOf(waitSeconds) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1_000)
                remaining--
            }
        }

        OverlayFrame(
            icon = {
                val compact = rememberCompactHeight()
                // The halo spills about 70 dp past the sphere, so it gets that much room above and below.
                Box(Modifier.padding(vertical = if (compact) 4.dp else HALO_ROOM)) {
                    GlassSphere(size = if (compact) 112.dp else 168.dp, halo = !compact, blur = true) {
                        AppIcon(packageName, Modifier.size(if (compact) 52.dp else 76.dp))
                    }
                }
            },
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
    themeMode: ThemeMode,
) {
    PausaTheme(themeMode) {
        OverlayFrame(
            icon = { GlassSphere(size = 88.dp, halo = false, blur = true) { AppIcon(packageName, Modifier.size(52.dp)) } },
            title = "Dibuja tu patrón",
            message = "para abrir $appLabel",
            body = {
                PatternChallenge(
                    verify = verify,
                    onVerified = onUnlocked,
                    idleHint = "",
                    modifier = Modifier
                        .padding(top = if (rememberCompactHeight()) 8.dp else 24.dp)
                        .widthIn(max = if (rememberCompactHeight()) 200.dp else 320.dp)
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
    val compact = rememberCompactHeight()
    var visible by remember { mutableStateOf(animationsOff) }
    LaunchedEffect(Unit) { visible = true }

    // The video plays here too, in the dark look: this is the screen that appears at the moment of the impulse.
    GlassStage(video = true) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 48.dp),
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Room around the icon: a scrolling container clips whatever sticks out of it, and a ring's stroke sticks out by half.
                Box(Modifier.padding(4.dp)) { icon() }
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
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                body()
            }
            }
        }

        if (primaryLabel != null) {
            // Oscuro: the same quiet glass as every other button, tinted to mark the main choice. Claro keeps the sphere material.
            if (LocalDarkTheme.current) {
                GlassGhostAction(onClick = onPrimary, modifier = Modifier.fillMaxWidth(), emphasized = true) { Text(primaryLabel) }
            } else {
                GlassButton(text = primaryLabel, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
            }
        }
        if (secondaryLabel != null) {
            GlassGhostButton(
                text = secondaryLabel,
                onClick = onSecondary,
                enabled = secondaryEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (primaryLabel != null) 10.dp else 0.dp),
            )
        }
    }
    }
}

/** True when the screen has little height (a phone held on its side): margins and shapes shrink to fit. */
@Composable
private fun rememberCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP

private const val COMPACT_HEIGHT_DP = 480

/** True when the user turned animations off in system settings. Everything that moves must then stand still. */
@Composable
private fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Between 0.86 and 1 while [active], following the breath; a constant 1 otherwise, with nothing running. */
@Composable
private fun rememberSwell(active: Boolean): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = "breathing")
    val swell by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swell",
    )
    return swell
}

/**
 * The sphere of a pause: the seconds left inside. If the rule asks for a breathing exercise the sphere also swells and
 * settles over four seconds each way and the word inside says which. With animations off it stays still and only the
 * words change.
 */
@Composable
private fun PauseRing(remaining: Int, total: Int, breathing: Boolean) {
    val animationsOff = rememberAnimationsOff()
    // The animation exists only while it is used: an endless transition redraws every frame even when nothing shows it.
    val swell = rememberSwell(active = breathing && !animationsOff)
    var inhale by remember { mutableStateOf(true) }
    LaunchedEffect(breathing) {
        while (breathing) {
            delay(BREATH_MILLIS.toLong())
            inhale = !inhale
        }
    }

    val compact = rememberCompactHeight()
    GlassSphere(
        size = if (compact) 112.dp else 168.dp,
        halo = !compact,
        modifier = Modifier
            .padding(vertical = if (compact) 8.dp else HALO_ROOM)
            .scale(swell),
    ) {
        when {
            breathing -> Text(
                if (inhale) "Inhala" else "Exhala",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            remaining > 0 -> Text(
                remaining.toString(),
                style = NumeralStyle.copy(fontSize = if (compact) 40.sp else 56.sp, lineHeight = if (compact) 44.sp else 60.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            else -> Text("Listo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val BREATH_MILLIS = 4_000

/** What a sphere's halo needs above and below it so nothing clips it. */
private val HALO_ROOM = 64.dp
private const val ENTER_MILLIS = 220
