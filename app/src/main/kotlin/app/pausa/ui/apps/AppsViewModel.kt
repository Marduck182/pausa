package app.pausa.ui.apps

import dagger.hilt.android.qualifiers.ApplicationContext
import app.pausa.R
import android.content.Context
import android.util.Log
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
import app.pausa.domain.usecase.RuleLockChecker
import app.pausa.ui.common.RuleText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** What was used today next to the limit, like "Today: 12 of 30 min"; null if there is nothing to say yet. */
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
    private val locks: RuleLockChecker,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _notice = MutableStateFlow<Int?>(null)

    /** Why the last action was not done, or null. Shown in a dialog until the person dismisses it. */
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

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
                        summary = if (rule.unreadable) {
                            context.getString(R.string.apps_unreadable)
                        } else {
                            RuleText.summary(context, draft)
                        },
                        usageToday = if (target is Target.App) RuleText.usageToday(context, draft, usage[rule.target.packageName]) else null,
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
            try {
                val rule = rules.getRules().firstOrNull { it.id == ruleId } ?: return@launch
                // Asked again here: the list may have been drawn before a hard-mode block started.
                if (locks.isRuleLocked(ruleId)) {
                    _notice.value = RuleMessages.LOCKED
                    return@launch
                }
                rules.upsert(rule.copy(enabled = enabled))
                if (!enabled) {
                    val now = clock.instant()
                    stats.record(
                        InterventionEvent(now, rule.target.packageName, InterventionOutcome.RULE_DISABLED),
                        now.atZone(clock.zone).toLocalDate().toEpochDay(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PausaApps", "Could not change rule $ruleId", e)
                _notice.value = RuleMessages.SAVE_FAILED
            }
        }
    }
}
