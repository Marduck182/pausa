@file:OptIn(ExperimentalLayoutApi::class)

package app.pausa.ui.dashboard

import app.pausa.ui.common.GlassGhostAction
import app.pausa.ui.common.AlertDialog
import app.pausa.ui.common.prismBorder
import app.pausa.ui.common.GlassPanel
import java.time.Instant
import app.pausa.ui.common.Button
import app.pausa.ui.theme.LocalDarkTheme
import app.pausa.ui.common.glassSurface
import app.pausa.ui.common.GlassSphere
import app.pausa.ui.common.GlassGhostButton
import app.pausa.ui.common.GlassButton
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import app.pausa.ui.rememberUsesSideLayout
import app.pausa.enforcement.ServiceState
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.net.toUri
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.domain.model.FeatureCatalog
import app.pausa.domain.model.Target
import app.pausa.domain.usecase.BlockedApp
import app.pausa.enforcement.AccessibilityStatus
import app.pausa.ui.common.AppIcon
import app.pausa.ui.common.Dialog
import app.pausa.ui.common.PillChip
import app.pausa.ui.common.Ring
import app.pausa.ui.common.RuleText
import app.pausa.ui.common.Stepper
import app.pausa.ui.common.rememberFriction
import app.pausa.ui.theme.NumeralStyle
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Home answers one question: what is happening now? A single block of colour says it (focus, blocking, or free),
 * and everything else is quieter than it. The numbers (time used, streak, avoided openings) are one sentence of text
 * rather than tiles, so the screen informs without turning into a dashboard.
 */
@Composable
fun DashboardScreen(
    onOpenApps: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val friction = rememberFriction()
    val serviceState = rememberServiceState()
    val sideLayout = rememberUsesSideLayout()

    val openAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    val endFocus = {
        friction.ask(
            title = "¿Terminar la concentración?",
            message = "Terminarla antes reinicia tu racha de días seguidos.",
            confirmLabel = "Terminar",
            onConfirmed = viewModel::endFocusEarly,
        )
    }

    // A break is chosen in advance, so there is no wait. A PIN, if the person set one, is still asked: it is a way around the rules.
    val startBreak = { minutes: Int ->
        friction.ask(
            title = "¿Tomar un descanso de $minutes min?",
            message = "Tus apps se abrirán sin espera durante $minutes minutos. Las reglas estrictas y la concentración siguen.",
            confirmLabel = "Descansar",
            wait = false,
            onConfirmed = { viewModel.startBreak(minutes) },
        )
    }

    if (sideLayout) {
        // On its side the phone has no height to spare: the ring goes on one half and everything else on the other.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                StatusHeader(serviceState, openAccessibility)
                ServiceNotice(serviceState, openAccessibility)
                HeroSection(state, onOpenApps, endFocus)
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                DetailsSection(state, startBreak, viewModel::endBreak)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatusHeader(serviceState, openAccessibility)
            ServiceNotice(serviceState, openAccessibility)
            HeroSection(state, onOpenApps, endFocus)
            DetailsSection(state, startBreak, viewModel::endBreak)
        }
    }

    friction.Dialog()
}

/** The state of the guard, kept fresh while the screen is visible: the user may switch it in Settings and come back. */
@Composable
private fun rememberServiceState(): ServiceState {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var current by remember { mutableStateOf(AccessibilityStatus.state(context)) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                current = AccessibilityStatus.state(context)
                delay(SERVICE_POLL_MILLIS)
            }
        }
    }
    return current
}

private const val SERVICE_POLL_MILLIS = 2_000L

@Composable
private fun ServiceNotice(serviceState: ServiceState, onOpenAccessibility: () -> Unit) {
    val context = LocalContext.current
    when (serviceState) {
        ServiceState.Running -> Unit
        ServiceState.Off -> SetupNotice(
            onOpenAccessibility = onOpenAccessibility,
            onOpenAppInfo = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                )
            },
        )
        ServiceState.NotRunning -> StalledNotice(onOpenAccessibility)
    }
}

/** The hour inside the ring, whatever state it is in. Fades between states: nothing more. */
@Composable
private fun HeroSection(state: DashboardUiState, onOpenApps: () -> Unit, onEndFocus: () -> Unit) {
    val snapshot = state.snapshot
    if (state.loading || snapshot == null || state.now == null) {
        Spacer(Modifier.height(160.dp))
        return
    }
    AnimatedContent(
        targetState = HeroKind.of(snapshot.focus != null, snapshot.blockedNow.isNotEmpty(), snapshot.controlledCount),
        transitionSpec = { fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(150)) },
        label = "hero",
    ) { kind ->
        Hero(kind = kind, state = state, onOpenApps = onOpenApps, onEndFocus = onEndFocus)
    }
}

/** What is quieter than the ring: the facts in a sentence, the focus durations, and the privacy line. */
@Composable
private fun DetailsSection(
    state: DashboardUiState,
    onStartBreak: (Int) -> Unit,
    onEndBreak: () -> Unit,
) {
    val snapshot = state.snapshot
    if (!state.loading && snapshot != null) {
        Facts(state)
        if (snapshot.focus == null) {
            if (snapshot.controlledCount > 0) GlassPanel { BreakSection(state.breakUntil, state.now, onStartBreak, onEndBreak) }
        }
    }
    Text(
        "Tus datos se quedan en este teléfono. Pausa no tiene permiso de internet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private enum class HeroKind {
    Focus, Blocking, Free, Empty;

    companion object {
        fun of(focus: Boolean, blocking: Boolean, controlled: Int): HeroKind = when {
            focus -> Focus
            blocking -> Blocking
            controlled == 0 -> Empty
            else -> Free
        }
    }
}

@Composable
private fun StatusHeader(serviceState: ServiceState, onFix: () -> Unit) {
    val running = serviceState == ServiceState.Running
    val pillShape = if (LocalDarkTheme.current) CircleShape else RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Pausa", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        // The state is a word as well as a colour, and when it is "off" the pill is also the way to fix it.
        Row(
            Modifier
                .prismBorder(pillShape, width = 1.dp, alpha = 0.7f, animated = false)
                .glassSurface(pillShape, LocalDarkTheme.current)
                .then(if (!running) Modifier.clickable(onClick = onFix) else Modifier)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            Text(
                when (serviceState) {
                    ServiceState.Running -> "Activa"
                    ServiceState.Off -> "Apagada"
                    ServiceState.NotRunning -> "Sin respuesta"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(color: androidx.compose.ui.graphics.Color) {
    Spacer(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun Hero(kind: HeroKind, state: DashboardUiState, onOpenApps: () -> Unit, onEndFocus: () -> Unit) {
    val snapshot = state.snapshot ?: return
    val now = state.now ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (kind == HeroKind.Empty) {
            Text(
                "Empieza por una app",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                "Elige la que más te distrae y decide cuándo y cuánto puedes usarla.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            GlassButton(text = "Añadir una app", onClick = onOpenApps, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        val next = snapshot.nextChange
        val untilLabel = next?.let { RuleText.moment(it, now) }
        val (big, caption) = when (kind) {
            HeroKind.Focus -> RuleText.moment(snapshot.focus!!.endsAt, now) to "concentración hasta"
            HeroKind.Blocking ->
                if (untilLabel != null && !snapshot.nextChangeStartsBlock) untilLabel to "enfoque hasta" else "Ahora" to "bloqueado"
            else ->
                if (untilLabel != null && snapshot.nextChangeStartsBlock) untilLabel to "próximo bloqueo" else "Libre" to "sin bloqueos"
        }

        val dark = LocalDarkTheme.current
        if (dark) {
            // Oscuro: the state is said in big type, left aligned; the glass is in the actions below.
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    big,
                    style = NumeralStyle.copy(fontSize = if (big.length > 6) 40.sp else 56.sp, lineHeight = 60.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(caption, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Claro: the state lives inside a multicolour sphere with its halo. Amber is kept for "blocked".
            // Blurred and with a softer halo: the multicolour mesh behind it was too much on the first screen.
            GlassSphere(size = 208.dp, modifier = Modifier.padding(top = 8.dp), blur = true, haloAlpha = 0.42f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        big,
                        style = NumeralStyle.copy(fontSize = if (big.length > 6) 26.sp else 42.sp, lineHeight = 48.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (kind == HeroKind.Blocking) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (kind == HeroKind.Focus) {
            val left = Duration.between(now.toInstant(), snapshot.focus!!.endsAt).toMillis().coerceAtLeast(60_000)
            Text("Quedan ${RuleText.duration(left)}", style = MaterialTheme.typography.bodyLarge)
        }
        if (kind == HeroKind.Focus || kind == HeroKind.Blocking) {
            GlassPanel(Modifier.padding(top = 8.dp)) { BlockedList(snapshot.blockedNow, state.labels, now) }
        }
        if (kind == HeroKind.Blocking && next != null && !snapshot.nextChangeStartsBlock) {
            Text(
                "Próximo descanso a las $untilLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (kind == HeroKind.Focus) {
            GlassGhostButton(text = "Terminar antes", onClick = onEndFocus, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The apps that are blocked, one line each with its icon and, quietly, until when. At most three are listed. */
@Composable
private fun BlockedList(blocked: List<BlockedApp>, labels: Map<String, String>, now: ZonedDateTime) {
    if (blocked.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocked.take(MAX_LISTED).forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(entry.target.packageName, Modifier.size(28.dp))
                Text(labelOf(entry.target, labels), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(RuleText.blockedBrief(entry.reason, entry.until, now), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (blocked.size > MAX_LISTED) {
            Text("y ${blocked.size - MAX_LISTED} más", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun labelOf(target: Target, labels: Map<String, String>): String {
    val app = labels[target.packageName] ?: target.packageName
    return when (target) {
        is Target.App -> app
        is Target.Feature -> "${FeatureCatalog.displayName(target.featureId)} · $app"
    }
}

/** What the numbers say, in one quiet sentence. Parts with nothing to report are left out. */
@Composable
private fun Facts(state: DashboardUiState) {
    val snapshot = state.snapshot ?: return
    if (snapshot.controlledCount == 0) return
    val stats = state.stats
    val parts = buildList {
        state.availableMillis?.let { add("Te quedan ${RuleText.duration(it)} hoy") }
            ?: add("Hoy usaste ${RuleText.duration(snapshot.usedTodayMillis)}")
        if (stats != null && stats.currentStreakDays > 0) {
            add("${stats.currentStreakDays} ${if (stats.currentStreakDays == 1) "día seguido" else "días seguidos"}")
        }
        if (stats != null && stats.attemptsAvoided > 0) {
            add("${stats.attemptsAvoided} ${if (stats.attemptsAvoided == 1) "apertura evitada" else "aperturas evitadas"} esta semana")
        }
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What is left of a break, counted down every second as m:ss (h:mm:ss from an hour). It stops at 0:00. */
@Composable
private fun rememberCountdown(until: Instant): String {
    var now by remember(until) { mutableStateOf(Instant.now()) }
    LaunchedEffect(until) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val total = Duration.between(now, until).seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * A break chosen in advance: 10, 15 or 30 minutes in which the apps open without a wait. It exists so the person does
 * not have to sit through a pause at the moment they want an app: they decide first, calmly, and it ends by itself.
 */
@Composable
private fun BreakSection(breakUntil: Instant?, now: ZonedDateTime?, onStart: (Int) -> Unit, onEnd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tomar un descanso", style = MaterialTheme.typography.titleMedium)
        if (breakUntil != null && now != null) {
            Text(
                "Tus apps se abren sin espera hasta las ${RuleText.moment(breakUntil, now)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val left = rememberCountdown(breakUntil)
            GlassGhostAction(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                Text("Terminar descanso")
                Text(
                    left,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else {
            Text(
                "Elige cuánto y tus apps se abren sin esperar. Las reglas estrictas y la concentración siguen en pie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var custom by remember { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 30).forEach { minutes -> PillChip(text = "$minutes min", onClick = { onStart(minutes) }) }
                PillChip(text = "Otro tiempo", onClick = { custom = true })
            }
            if (custom) {
                var minutes by remember { mutableIntStateOf(20) }
                AlertDialog(
                    onDismissRequest = { custom = false },
                    title = { Text("¿Cuánto descansas?") },
                    text = {
                        Stepper(
                            label = "Duración",
                            value = minutes,
                            onValueChange = { minutes = it },
                            range = 5..120,
                            step = 5,
                            format = { RuleText.duration(it * 60_000L) },
                        )
                    },
                    confirmButton = { TextButton(onClick = { custom = false; onStart(minutes) }) { Text("Descansar") } },
                    dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancelar") } },
                )
            }
        }
    }
}

/**
 * Shown when Settings lists Pausa as on but the service is not alive. Android can leave a service unbound after it was
 * killed, and nothing is blocked until it is switched off and on again, so the person needs to be told.
 */
@Composable
private fun StalledNotice(onOpenAccessibility: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pausa no está respondiendo", style = MaterialTheme.typography.titleLarge)
            Text(
                "Está activada en Ajustes, pero el sistema la dejó sin conectar y ahora no bloquea nada. " +
                    "Apágala y vuelve a encenderla para que funcione otra vez.",
                style = MaterialTheme.typography.bodyLarge,
            )
            GlassButton(text = "Abrir ajustes de accesibilidad", onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Shown while the service is off. Amber, not red: it is a thing to fix, not an alarm. */
@Composable
private fun SetupNotice(onOpenAccessibility: () -> Unit, onOpenAppInfo: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Falta activar Pausa", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sin su servicio de accesibilidad no sabe qué app tienes abierta y no puede bloquear nada. " +
                    "Solo mira qué app está en pantalla: no lee tus mensajes ni guarda lo que ves.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Abrir ajustes de accesibilidad")
            }
            TextButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("El interruptor sale gris")
            }
            Text(
                "Android bloquea este permiso en apps instaladas fuera de la tienda. En la info de la app, toca ⋮ y elige «Permitir ajustes restringidos».",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val MAX_LISTED = 3
