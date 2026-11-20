package app.pausa.ui.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.BlockMode
import app.pausa.domain.model.FeatureCatalog
import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.InterventionOutcome
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.SupportedFeature
import app.pausa.domain.model.Target
import app.pausa.domain.model.TemporaryAllow
import app.pausa.domain.model.TimeWindow
import app.pausa.domain.model.UsageSnapshot
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.RuleRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.repository.StatsRepository
import app.pausa.domain.repository.UsageRepository
import app.pausa.domain.usecase.RuleLock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A feature of this app that can be restricted on its own, and the rule for it if the user already made one. */
data class FeatureEntry(val feature: SupportedFeature, val ruleId: Long?)

data class EditUiState(
    val loading: Boolean = true,
    val label: String = "",
    val draft: RuleDraft? = null,
    /** The rule as stored, to tell what the user changed (turning it off needs a pause). */
    val original: RuleDraft? = null,
    /** True once the user changed something, so leaving asks before throwing the changes away. */
    val dirty: Boolean = false,
    /** True when the screen has nothing more to do and should close. */
    val finished: Boolean = false,
    /** Hard mode and blocking right now: nothing can be changed until the block ends. */
    val locked: Boolean = false,
    /** The stored rule is blocking right now. Changing it then is the very moment the urge shows up. */
    val blockingNow: Boolean = false,
    val features: List<FeatureEntry> = emptyList(),
    /** A security pattern exists, so a rule can ask for it. */
    val patternSet: Boolean = false,
)

@HiltViewModel
class EditRuleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val rules: RuleRepository,
    private val installedApps: InstalledAppsRepository,
    private val usageRepository: UsageRepository,
    private val settings: SettingsRepository,
    private val stats: StatsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val ruleId: Long = checkNotNull(savedStateHandle["ruleId"])

    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = rules.getRules()
            val rule = all.firstOrNull { it.id == ruleId }
            if (rule == null) {
                _state.value = EditUiState(loading = false, finished = true)
                return@launch
            }
            val packageName = rule.target.packageName
            val appLabel = installedApps.getLaunchableApps().firstOrNull { it.packageName == packageName }?.label ?: packageName
            val label = (rule.target as? Target.Feature)
                ?.let { "${FeatureCatalog.displayName(it.featureId)} · $appLabel" }
                ?: appLabel

            val totals = usageRepository.totalsForDay(LocalDate.now(clock).toEpochDay())
            val usage = UsageSnapshot(totals.mapValues { it.value.usedMillis }, totals.mapValues { it.value.sessions })
            val now = ZonedDateTime.now(clock)

            val features = if (rule.target is Target.App) {
                FeatureCatalog.forPackage(packageName).map { feature ->
                    FeatureEntry(feature, all.firstOrNull { it.target == Target.Feature(packageName, feature.featureId) }?.id)
                }
            } else {
                emptyList()
            }
            val draft = RuleDraft.from(rule)
            _state.value = EditUiState(
                loading = false,
                label = label,
                draft = draft,
                original = draft,
                locked = RuleLock.isLocked(rule, usage, now),
                blockingNow = RuleLock.isBlockingNow(rule, usage, now),
                features = features,
                patternSet = settings.settings.first().patternEnabled,
            )
        }
    }

    private fun edit(transform: (RuleDraft) -> RuleDraft) = _state.update { s ->
        val draft = s.draft ?: return@update s
        if (s.locked) return@update s
        s.copy(draft = transform(draft), dirty = true)
    }

    fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    fun setHardMode(hard: Boolean) = edit { it.copy(hardMode = hard) }

    fun setPatternLock(on: Boolean) = edit { it.copy(patternLock = on) }

    /** Saves the first pattern, drawn from the rule that asked for one, and turns the requirement on. */
    fun createPattern(dots: List<Int>) {
        viewModelScope.launch {
            settings.setPattern(dots)
            _state.update { it.copy(patternSet = true) }
            setPatternLock(true)
        }
    }

    fun setBlockMode(mode: BlockMode) = edit { draft ->
        val next = draft.copy(blockMode = mode)
        // Choosing a schedule starts with one window to edit instead of an empty, unusable list.
        if (mode == BlockMode.SCHEDULE && next.windows.isEmpty()) next.copy(windows = listOf(defaultWindow())) else next
    }

    fun setScheduleMode(mode: ScheduleMode) = edit { it.copy(scheduleMode = mode) }

    fun addWindow() = edit { it.copy(windows = it.windows + defaultWindow()) }

    fun removeWindow(index: Int) = edit { draft ->
        val windows = draft.windows.filterIndexed { i, _ -> i != index }
        // Removing the last window with a schedule selected would leave an invalid rule, so fall back to none.
        if (windows.isEmpty() && draft.blockMode == BlockMode.SCHEDULE) {
            draft.copy(windows = windows, blockMode = BlockMode.NONE)
        } else {
            draft.copy(windows = windows)
        }
    }

    fun useWorkdayPreset() = edit { it.copy(blockMode = BlockMode.SCHEDULE, windows = RuleDraft.workdayWindows()) }

    fun toggleDay(index: Int, day: DayOfWeek, selected: Boolean) = updateWindow(index) { window ->
        val days = if (selected) window.days + day else window.days - day
        // A window with no days could never apply, so the last day cannot be switched off.
        if (days.isEmpty()) window else window.copy(days = days)
    }

    fun setWindowStart(index: Int, minute: Int) = updateWindow(index) { window ->
        window.copy(startMinute = minute, endMinute = distinctFrom(minute, window.endMinute))
    }

    fun setWindowEnd(index: Int, minute: Int) = updateWindow(index) { window ->
        window.copy(endMinute = minute, startMinute = distinctFrom(minute, window.startMinute))
    }

    fun setDailyLimit(minutes: Int?) = edit { it.copy(dailyLimitMinutes = minutes) }

    fun setMaxSessions(count: Int?) = edit { it.copy(maxSessionsPerDay = count) }

    fun setIntervention(intervention: Restriction.Intervention?) = edit { it.copy(intervention = intervention) }

    /** Opens the rule for one feature of this app, creating it (blocking) the first time. */
    fun openFeature(entry: FeatureEntry, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val id = entry.ruleId ?: rules.upsert(
                RuleDraft.newForFeature(entry.feature.packageName, entry.feature.featureId).toRule(),
            )
            onReady(id)
        }
    }

    fun save() {
        val s = _state.value
        val draft = s.draft ?: return
        if (!draft.isValid || s.locked) return
        viewModelScope.launch {
            rules.upsert(draft.toRule())
            if (s.original?.enabled == true && !draft.enabled) recordGivingIn(draft.packageName, InterventionOutcome.RULE_DISABLED)
            _state.update { it.copy(dirty = false, finished = true) }
        }
    }

    fun delete() {
        val s = _state.value
        if (s.locked) return
        viewModelScope.launch {
            rules.delete(ruleId)
            s.draft?.let { recordGivingIn(it.packageName, InterventionOutcome.RULE_DISABLED) }
            _state.update { it.copy(dirty = false, finished = true) }
        }
    }

    /** A temporary pass, asked for inside Pausa so it can be protected by the PIN. */
    fun allowTemporarily() {
        val s = _state.value
        val draft = s.draft ?: return
        if (s.locked || !s.blockingNow) return
        viewModelScope.launch {
            settings.addTemporaryAllow(TemporaryAllow(draft.packageName, clock.instant().plus(TEMPORARY_ALLOW)))
            recordGivingIn(draft.packageName, InterventionOutcome.TEMP_ALLOW)
            _state.update { it.copy(dirty = false, finished = true) }
        }
    }

    private suspend fun recordGivingIn(packageName: String, outcome: InterventionOutcome) {
        val now = clock.instant()
        stats.record(InterventionEvent(now, packageName, outcome), now.atZone(clock.zone).toLocalDate().toEpochDay())
    }

    private fun updateWindow(index: Int, transform: (TimeWindow) -> TimeWindow) = edit { draft ->
        draft.copy(windows = draft.windows.mapIndexed { i, w -> if (i == index) transform(w) else w })
    }

    /** A window cannot start and end at the same minute: if the user picks that, nudge the other end by an hour. */
    private fun distinctFrom(picked: Int, other: Int): Int =
        if (picked == other) (other + 60) % TimeWindow.MINUTES_PER_DAY else other

    private fun defaultWindow() = TimeWindow(
        days = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
        startMinute = 9 * 60,
        endMinute = 14 * 60,
    )

    private companion object {
        val TEMPORARY_ALLOW: Duration = Duration.ofMinutes(10)
    }
}
