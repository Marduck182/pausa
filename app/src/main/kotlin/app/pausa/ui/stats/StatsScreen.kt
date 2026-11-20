package app.pausa.ui.stats

import app.pausa.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import kotlin.math.sqrt
import app.pausa.ui.theme.LocalDarkTheme
import app.pausa.ui.common.glassSurface
import app.pausa.ui.common.GlassSphere
import app.pausa.ui.common.AppIcon
import app.pausa.domain.model.AppAttemptStat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import app.pausa.ui.theme.NumeralStyle
import app.pausa.ui.common.Ring
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
        ScreenTitle(stringResource(R.string.stats_title))

        val stats = state.stats
        if (state.loading || stats == null) {
            SkeletonRows(count = 4)
        } else {
            Streak(stats)
            Week(stats)
            TopApps(stats, state.labels)
            Hours(stats)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Streak(stats: StatsSnapshot) {
    Section(title = stringResource(R.string.stats_streak_title)) {
        val days = stats.currentStreakDays
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // The ring fills toward the best streak (or a week, if there is none yet): how far along, at a glance.
            val goal = maxOf(stats.bestStreakDays, 7)
            GlassSphere(size = 112.dp, halo = false) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$days", style = NumeralStyle.copy(fontSize = 40.sp, lineHeight = 44.sp))
                    Text(
                        pluralStringResource(R.plurals.stats_streak_unit, days),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (days == 0) stringResource(R.string.stats_streak_start) else pluralStringResource(R.plurals.stats_streak_days, days, days),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (stats.bestStreakDays > days) {
                    Text(
                        pluralStringResource(R.plurals.stats_best_streak, stats.bestStreakDays, stats.bestStreakDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Week(stats: StatsSnapshot) {
    Section(title = stringResource(R.string.stats_week_title)) {
        if (stats.attemptsAvoided == 0 && stats.interventions == 0) {
            Text(
                stringResource(R.string.stats_week_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }
        // The figure is the point of this section, so it is the largest thing in it; the sentence stays for screen readers.
        val avoidedDescription = pluralStringResource(R.plurals.stats_avoided_description, stats.attemptsAvoided, stats.attemptsAvoided)
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = avoidedDescription
            },
        ) {
            Text("${stats.attemptsAvoided}", style = NumeralStyle.copy(fontSize = 56.sp, lineHeight = 60.sp))
            Text(
                pluralStringResource(R.plurals.stats_avoided_label, stats.attemptsAvoided),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val parts = buildList {
            if (stats.blockedAttempts > 0) add(pluralStringResource(R.plurals.stats_blocks, stats.blockedAttempts, stats.blockedAttempts))
            if (stats.interventions > 0) add(pluralStringResource(R.plurals.stats_pauses, stats.interventions, stats.interventions))
        }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(stringResource(R.string.stats_and)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopApps(stats: StatsSnapshot, labels: Map<String, String>) {
    Section(title = stringResource(R.string.stats_today_title)) {
        if (stats.attemptsTodayByApp.isEmpty()) {
            Text(
                stringResource(R.string.stats_today_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Section
        }
        val top = stats.attemptsTodayByApp.take(MAX_APPS)
        // Oscuro: capsule bars, one per app. Claro: glass spheres whose size is the share of attempts; touch one for its detail.
        if (LocalDarkTheme.current) CapsuleBars(top, labels) else AppSpheres(top, labels)
    }
}

@Composable
private fun times(count: Int): String = pluralStringResource(R.plurals.stats_times, count, count)

@Composable
private fun describe(app: AppAttemptStat, name: String): String {
    val base = stringResource(R.string.stats_attempts_description, name, times(app.attempts))
    return if (app.stopped > 0) base + ", " + pluralStringResource(R.plurals.stats_stopped_count, app.stopped, app.stopped) else base
}

@Composable
private fun CapsuleBars(top: List<AppAttemptStat>, labels: Map<String, String>) {
    val max = top.first().attempts.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        top.forEach { app ->
            val name = labels[app.packageName] ?: app.packageName
            val fraction = (app.attempts.toFloat() / max).coerceIn(0.1f, 1f)
            val description = describe(app, name)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .glassSurface(CircleShape, dark = true)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), Color(0xFF7FD6F2).copy(alpha = 0.75f)))),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(times(app.attempts), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun AppSpheres(top: List<AppAttemptStat>, labels: Map<String, String>) {
    var selected by remember { mutableIntStateOf(0) }
    val max = top.first().attempts.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            top.forEachIndexed { index, app ->
                val name = labels[app.packageName] ?: app.packageName
                val size = (72 + 64 * sqrt(app.attempts.toFloat() / max)).dp
                val description = describe(app, name)
                GlassSphere(
                    size = size,
                    halo = false,
                    animated = false,
                    modifier = Modifier
                        .then(if (index == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { selected = index }
                        .semantics { contentDescription = description },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(app.packageName, Modifier.size(size * 0.34f))
                        Text("${app.attempts}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        val chosen = top[selected.coerceIn(0, top.lastIndex)]
        Column(
            Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(22.dp), dark = false).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(labels[chosen.packageName] ?: chosen.packageName, style = MaterialTheme.typography.titleMedium)
                Text(times(chosen.attempts), style = MaterialTheme.typography.bodyLarge)
            }
            if (chosen.stopped > 0) {
                Text(
                    stringResource(R.string.stats_stopped_detail, times(chosen.stopped)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Hours(stats: StatsSnapshot) {
    val peak = stats.peakHour ?: return
    var expanded by remember { mutableStateOf(false) }
    Section(title = stringResource(R.string.stats_hours_title)) {
        Text(
            stringResource(R.string.stats_hours_peak, RuleText.clock(peak * 60), RuleText.clock(((peak + 1) % 24) * 60)),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (expanded) R.string.stats_hours_hide else R.string.stats_hours_show))
        }
        AnimatedVisibility(visible = expanded) {
            HoursChart(stats, peak)
        }
    }
}

@Composable
private fun HoursChart(stats: StatsSnapshot, peak: Int) {
    val max = stats.minutesByHour.max().coerceAtLeast(1)
    val chartDescription = stringResource(R.string.stats_hours_chart_description, RuleText.clock(peak * 60))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics { contentDescription = chartDescription },
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
            listOf(0, 6, 12, 18, 24).map { stringResource(R.string.stats_hour_axis, it) }.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val MAX_APPS = 3
