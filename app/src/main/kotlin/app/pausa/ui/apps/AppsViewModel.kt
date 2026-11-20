package app.pausa.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.FeatureCatalog
import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.InterventionOutcome
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.Target
import app.pausa.domain.model.UsageSnapshot
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.RuleRepository
import app.pausa.domain.repository.StatsRepository
import app.pausa.domain.repository.UsageRepository
import app.pausa.domain.usecase.RuleLock
import app.pausa.ui.common.RuleText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ControlledAppItem(
    val ruleId: Long,
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val summary: String,
    /** What was used today next to the limit, like "Hoy: 12 de 30 min"; null if there is nothing to say yet. */
    val usageToday: String?,
    /** Hard mode and blocking right now: it cannot be turned off until the block ends. */
    val locked: Boolean,
)

data class AppsUiState(
    val loading: Boolean = true,
    val items: List<ControlledAppItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val rules: RuleRepository,
    installedApps: InstalledAppsRepository,
    usageRepository: UsageRepository,
    private val stats: StatsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Follows the calendar day, so the usage shown starts again at midnight even if the screen stays open. */
    private val usage = flow {
        while (true) {
            emit(LocalDate.now(clock).toEpochDay())
            delay(60_000)
        }
    }.distinctUntilChanged().flatMapLatest { usageRepository.observeTotalsForDay(it) }

    /** Null until the installed apps are read, so the list never flashes raw package names. */
    private val labels = MutableStateFlow<Map<String, String>?>(null)

    init {
        viewModelScope.launch {
            labels.value = installedApps.getLaunchableApps().associate { it.packageName to it.label }
        }
    }

    val state: StateFlow<AppsUiState> = combine(rules.observeRules(), labels, usage) { rules, labels, usage ->
        if (labels == null) {
            AppsUiState(loading = true)
        } else {
            val snapshot = UsageSnapshot(usage.mapValues { it.value.usedMillis }, usage.mapValues { it.value.sessions })
            val now = ZonedDateTime.now(clock)
            val items = rules
                .map { rule ->
                    val draft = RuleDraft.from(rule)
                    val app = labels[rule.target.packageName] ?: rule.target.packageName
                    val target = rule.target
                    ControlledAppItem(
                        ruleId = rule.id,
                        packageName = rule.target.packageName,
                        label = if (target is Target.Feature) "${FeatureCatalog.displayName(target.featureId)} · $app" else app,
                        enabled = rule.enabled,
                        summary = RuleText.summary(draft),
                        usageToday = if (target is Target.App) RuleText.usageToday(draft, usage[rule.target.packageName]) else null,
                        locked = RuleLock.isLocked(rule, snapshot, now),
                    )
                }
                .sortedBy { it.label.lowercase() }
            AppsUiState(loading = false, items = items)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    /** Turning a rule off is giving in, so it is recorded; turning it on is not. */
    fun setEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val rule = rules.getRules().firstOrNull { it.id == ruleId } ?: return@launch
            rules.upsert(rule.copy(enabled = enabled))
            if (!enabled) {
                val now = clock.instant()
                stats.record(
                    InterventionEvent(now, rule.target.packageName, InterventionOutcome.RULE_DISABLED),
                    now.atZone(clock.zone).toLocalDate().toEpochDay(),
                )
            }
        }
    }
}
