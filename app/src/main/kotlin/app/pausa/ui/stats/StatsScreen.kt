package app.pausa.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.domain.model.StatsSnapshot
import app.pausa.ui.common.RuleText
import app.pausa.ui.common.ScreenTitle
import app.pausa.ui.common.Section
import app.pausa.ui.common.SkeletonRows

/**
 * How things are going, told in sentences. There is one streak, one short account of the week, the apps that took
 * the most time, and, behind a tap, the hours of the day. Nothing is a tile of big numbers and the only chart is
 * optional: the point is to know where you stand, not to study a dashboard.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        ScreenTitle("Cómo vas")

        val stats = state.stats
        if (state.loading || stats == null) {
            SkeletonRows(count = 4)
        } else {
            Streak(stats)
            Week(stats)
            TopApps(stats, state.labels)
            Hours(stats)
            Text(
                "Todo se calcula en este teléfono, solo con el nombre de la app y la hora.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Streak(stats: StatsSnapshot) {
    Section(title = "Racha") {
        val days = stats.currentStreakDays
        Text(
            if (days == 0) "Empiezas hoy" else "$days ${if (days == 1) "día seguido" else "días seguidos"}",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (stats.bestStreakDays > days) {
            Text(
                "Tu mejor racha fue de ${stats.bestStreakDays} ${if (stats.bestStreakDays == 1) "día" else "días"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Un día cuenta si no pediste un permiso temporal, no terminaste una concentración antes y no apagaste ninguna regla.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Week(stats: StatsSnapshot) {
    Section(title = "Esta semana") {
        if (stats.attemptsAvoided == 0 && stats.interventions == 0) {
            Text(
                "Todavía no hay nada que contar. Cuando Pausa te frene en una app, lo verás aquí.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }
        Text(
            "Evitaste ${stats.attemptsAvoided} ${if (stats.attemptsAvoided == 1) "apertura" else "aperturas"}.",
            style = MaterialTheme.typography.bodyLarge,
        )
        val parts = buildList {
            if (stats.blockedAttempts > 0) add("${stats.blockedAttempts} ${if (stats.blockedAttempts == 1) "bloqueo" else "bloqueos"}")
            if (stats.interventions > 0) add("${stats.interventions} ${if (stats.interventions == 1) "pausa" else "pausas"}")
        }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" y "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (stats.estimatedSavedMillis > 0) {
            Text(
                "Unas ${RuleText.duration(stats.estimatedSavedMillis)} que no gastaste (estimación según lo que duran tus sesiones).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopApps(stats: StatsSnapshot, labels: Map<String, String>) {
    Section(title = "Dónde se fue el tiempo", description = "Últimos 7 días") {
        if (stats.usageByApp.isEmpty()) {
            Text(
                "Aún no hay uso registrado de las apps que controlas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }
        val top = stats.usageByApp.take(MAX_APPS)
        val max = top.first().millis.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            top.forEach { app ->
                val name = labels[app.packageName] ?: app.packageName
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics { contentDescription = "$name: ${RuleText.duration(app.millis)}" },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(RuleText.duration(app.millis), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Bar(fraction = app.millis.toFloat() / max)
                }
            }
        }
    }
}

/** A thin horizontal bar: the share of the longest one. Decoration is kept to the one accent colour. */
@Composable
private fun Bar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** The hour with the most use is said in words; the chart that shows all of them is there only if asked for. */
@Composable
private fun Hours(stats: StatsSnapshot) {
    val peak = stats.peakHour ?: return
    var expanded by remember { mutableStateOf(false) }
    Section(title = "A qué hora te distraes más") {
        Text(
            "Sobre todo entre las ${RuleText.clock(peak * 60)} y las ${RuleText.clock(((peak + 1) % 24) * 60)}.",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (expanded) "Ocultar las horas" else "Ver todas las horas")
        }
        AnimatedVisibility(visible = expanded) {
            HoursChart(stats, peak)
        }
    }
}

@Composable
private fun HoursChart(stats: StatsSnapshot, peak: Int) {
    val max = stats.minutesByHour.max().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics { contentDescription = "Uso por hora del día; el máximo es a las ${RuleText.clock(peak * 60)}" },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.minutesByHour.forEachIndexed { hour, minutes ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((minutes.toFloat() / max).coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (hour == peak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0 h", "6 h", "12 h", "18 h", "24 h").forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val MAX_APPS = 3
