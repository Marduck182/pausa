package app.pausa.domain.usecase

import app.pausa.domain.model.UsageSnapshot
import app.pausa.domain.repository.RuleRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.repository.UsageRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * Answers "is this protected right now?" from what is stored at this moment, not from what a screen loaded a while
 * ago. A screen can stay open for minutes: a block that started after it was drawn must still stop the change that
 * the screen is about to make, so every action that weakens a protection asks again just before it runs.
 */
class RuleLockChecker(
    private val rules: RuleRepository,
    private val usage: UsageRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** True if the rule exists, is in hard mode and is blocking right now. */
    suspend fun isRuleLocked(ruleId: Long): Boolean {
        val rule = rules.getRules().firstOrNull { it.id == ruleId } ?: return false
        return RuleLock.isLocked(rule, snapshot(), now())
    }

    /**
     * True while a focus session is running or any hard-mode rule is blocking. Things that would let an app through
     * or wipe the rules (adding an exception, erasing everything) wait until then.
     */
    suspend fun isAnyProtectionActive(): Boolean {
        val focus = settings.focus.first()
        if (focus != null && focus.endsAt.isAfter(clock.instant())) return true
        val snapshot = snapshot()
        val now = now()
        return rules.getRules().any { RuleLock.isLocked(it, snapshot, now) }
    }

    private suspend fun snapshot(): UsageSnapshot {
        val totals = usage.totalsForDay(LocalDate.now(clock).toEpochDay())
        return UsageSnapshot(totals.mapValues { it.value.usedMillis }, totals.mapValues { it.value.sessions })
    }

    private fun now(): ZonedDateTime = ZonedDateTime.now(clock)
}
