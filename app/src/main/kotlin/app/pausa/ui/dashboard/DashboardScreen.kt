@file:OptIn(ExperimentalLayoutApi::class)

package app.pausa.ui.dashboard

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

    // The user changes this in system settings, so re-check every time they come back to the app.
    var serviceEnabled by remember { mutableStateOf(AccessibilityStatus.isServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = AccessibilityStatus.isServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TopRow(
            serviceEnabled = serviceEnabled,
            onFix = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )

        if (!serviceEnabled) {
            SetupNotice(
                onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onOpenAppInfo = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )
        }

        val snapshot = state.snapshot
        val now = state.now
        if (!state.loading && snapshot != null && now != null) {
            // The block changes when the state does (free, blocking, focus): a short cross-fade, nothing more.
            AnimatedContent(
                targetState = HeroKind.of(snapshot.focus != null, snapshot.blockedNow.isNotEmpty(), snapshot.controlledCount),
                transitionSpec = { fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(150)) },
                label = "hero",
            ) { kind ->
                Hero(
                    kind = kind,
                    state = state,
                    onOpenApps = onOpenApps,
                    onEndFocus = {
                        friction.ask(
                            title = "¿Terminar la concentración?",
                            message = "Terminarla antes reinicia tu racha de días seguidos.",
                            confirmLabel = "Terminar",
                            onConfirmed = viewModel::endFocusEarly,
                        )
                    },
                )
            }

            Facts(state)

            if (snapshot.focus == null) {
                FocusStart(enabled = snapshot.controlledCount > 0, onStart = viewModel::startFocus)
            }
        } else if (state.loading) {
            Spacer(Modifier.height(160.dp))
        }

        Text(
            "Tus datos se quedan en este teléfono. Pausa no tiene permiso de internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    friction.Dialog()
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
private fun TopRow(serviceEnabled: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Pausa", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        // The state is a word as well as a colour, and when it is "off" the pill is also the way to fix it.
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .then(if (!serviceEnabled) Modifier.clickable(onClick = onFix) else Modifier)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(if (serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            Text(
                if (serviceEnabled) "Activa" else "Apagada",
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
            Button(onClick = onOpenApps, modifier = Modifier.heightIn(min = 48.dp)) { Text("Añadir una app") }
            return@Column
        }

        // The colour of the ring says the state: lavender for focus, amber for a block, grey when nothing is blocked.
        val ringColor = when (kind) {
            HeroKind.Focus -> MaterialTheme.colorScheme.primary
            HeroKind.Blocking -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outline
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

        Ring(
            color = ringColor,
            size = 188.dp,
            progress = if (kind == HeroKind.Free) 0f else 1f,
            animate = false,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    big,
                    style = NumeralStyle.copy(fontSize = if (big.length > 6) 26.sp else 42.sp, lineHeight = 48.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (kind == HeroKind.Focus) {
            val left = Duration.between(now.toInstant(), snapshot.focus!!.endsAt).toMillis().coerceAtLeast(60_000)
            Text("Quedan ${RuleText.duration(left)}", style = MaterialTheme.typography.bodyLarge)
        }
        if (kind == HeroKind.Focus || kind == HeroKind.Blocking) {
            BlockedList(snapshot.blockedNow, state.labels, now)
        }
        if (kind == HeroKind.Blocking && next != null && !snapshot.nextChangeStartsBlock) {
            Text(
                "Próximo descanso a las $untilLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (kind == HeroKind.Focus) {
            TextButton(onClick = onEndFocus, modifier = Modifier.heightIn(min = 48.dp)) { Text("Terminar antes") }
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

@Composable
private fun FocusStart(enabled: Boolean, onStart: (Int) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Concentrarme ahora", style = MaterialTheme.typography.titleMedium)
        Text(
            if (enabled) "Bloquea todo lo que controlas hasta que pase el tiempo."
            else "Cuando controles alguna app, aquí podrás bloquearlas todas un rato.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h").forEach { (minutes, label) ->
                PillChip(text = label, onClick = { onStart(minutes) }, enabled = enabled)
            }
            PillChip(text = "Otro tiempo", onClick = { custom = true }, enabled = enabled)
        }
    }
    if (custom) {
        var minutes by remember { mutableIntStateOf(45) }
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text("¿Cuánto tiempo?") },
            text = {
                Stepper(
                    label = "Duración",
                    value = minutes,
                    onValueChange = { minutes = it },
                    range = 5..480,
                    step = 5,
                    format = { RuleText.duration(it * 60_000L) },
                )
            },
            confirmButton = { TextButton(onClick = { custom = false; onStart(minutes) }) { Text("Empezar") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancelar") } },
        )
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
