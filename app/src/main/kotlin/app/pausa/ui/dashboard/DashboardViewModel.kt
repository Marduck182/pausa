package app.pausa.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.InterventionOutcome
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.StatsSnapshot
import app.pausa.domain.model.Target
import app.pausa.domain.model.UsageSnapshot
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.RuleRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.repository.StatsRepository
import app.pausa.domain.repository.UsageRepository
import app.pausa.domain.usecase.DashboardCalculator
import app.pausa.domain.usecase.DashboardSnapshot
import app.pausa.enforcement.SystemPackages
import app.pausa.ui.common.StatsLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val now: ZonedDateTime? = null,
    val snapshot: DashboardSnapshot? = null,
    /** Names for the apps in [snapshot], so the screen never shows a package name. */
    val labels: Map<String, String> = emptyMap(),
    /** Time left today across apps that have a daily limit, or null if none has one. */
    val availableMillis: Long? = null,
    val stats: StatsSnapshot? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    usageRepository: UsageRepository,
    installedApps: InstalledAppsRepository,
    systemPackages: SystemPackages,
    statsLoader: StatsLoader,
    private val clock: Clock,
) : ViewModel() {

    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val protectedPackages = systemPackages.resolve().protectedPackages

    /** Ticks every half minute so "until 14:00" and the countdown stay true while the screen is open. */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    private val usage = ticker
        .map { LocalDate.now(clock).toEpochDay() }
        .distinctUntilChanged()
        .flatMapLatest { usageRepository.observeTotalsForDay(it) }

    private val stats = usage.mapLatest { statsLoader.load() }

    init {
        viewModelScope.launch {
            labels.value = installedApps.getLaunchableApps().associate { it.packageName to it.label }
        }
    }

    val state: StateFlow<DashboardUiState> = combine(
        ruleRepository.observeRules(),
        usage,
        settingsRepository.focus,
        settingsRepository.settings,
        ticker,
    ) { rules, usage, focus, settings, _ ->
        val now = ZonedDateTime.now(clock)
        val snapshot = UsageSnapshot(
            usedMillisToday = usage.mapValues { it.value.usedMillis },
            sessionsStartedToday = usage.mapValues { it.value.sessions },
        )
        val dashboard = DashboardCalculator.compute(rules, snapshot, focus, settings.exceptions, protectedPackages, now)
        val limits = rules.filter { it.enabled && it.target is Target.App }.mapNotNull { rule ->
            rule.restrictions.filterIsInstance<Restriction.DailyLimit>().minOfOrNull { it.minutes }
                ?.let { limit -> (limit * 60_000L - snapshot.usedMillis(rule.target.packageName)).coerceAtLeast(0) }
        }
        DashboardUiState(
            loading = false,
            now = now,
            snapshot = dashboard,
            availableMillis = limits.takeIf { it.isNotEmpty() }?.sum(),
        )
    }.combine(labels) { state, labels -> state.copy(labels = labels) }
        .combine(stats) { state, stats -> state.copy(stats = stats) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Starts a focus session on every app and feature that has an active rule. */
    fun startFocus(minutes: Int) {
        viewModelScope.launch {
            val targets = ruleRepository.getRules().filter { it.enabled }.map { it.target }.toSet()
            if (targets.isEmpty()) return@launch
            settingsRepository.startFocus(FocusSession(clock.instant().plus(Duration.ofMinutes(minutes.toLong())), targets))
        }
    }

    /** Ends focus before its time. Giving in like this breaks the streak, so it is recorded as such. */
    fun endFocusEarly() {
        viewModelScope.launch {
            val now = clock.instant()
            statsRepository.record(
                InterventionEvent(now, "", InterventionOutcome.FOCUS_ENDED_EARLY),
                now.atZone(clock.zone).toLocalDate().toEpochDay(),
            )
            settingsRepository.endFocus()
        }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}
