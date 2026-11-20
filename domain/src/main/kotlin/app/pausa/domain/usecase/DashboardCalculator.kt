package app.pausa.domain.usecase

import app.pausa.domain.engine.RuleEngine
import app.pausa.domain.engine.ScheduleEvaluator
import app.pausa.domain.model.AccessRequest
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.Decision
import app.pausa.domain.model.EngineContext
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.Target
import app.pausa.domain.model.UsageSnapshot
import java.time.Instant
import java.time.ZonedDateTime

data class BlockedApp(
    val target: Target,
    val reason: BlockReason,
    /** When this block ends, or null if it has no end. */
    val until: Instant?,
)

data class DashboardSnapshot(
    /** The focus session in progress, if any. */
    val focus: FocusSession?,
    val blockedNow: List<BlockedApp>,
    /** The next moment the picture changes (a block ends, a block starts, focus ends), or null if none is coming. */
    val nextChange: Instant?,
    /** True if what happens at [nextChange] is a block starting; false if it is a block (or focus) ending. */
    val nextChangeStartsBlock: Boolean,
    val controlledCount: Int,
    /** Time used today in apps that have an active rule. */
    val usedTodayMillis: Long,
)

/** Works out what the home screen shows from the rules and the day's use, without any Android in it. */
object DashboardCalculator {
    private val engine = RuleEngine()

    fun compute(
        rules: List<Rule>,
        usage: UsageSnapshot,
        focus: FocusSession?,
        exceptions: Set<String>,
        protectedPackages: Set<String>,
        now: ZonedDateTime,
    ): DashboardSnapshot {
        val instant = now.toInstant()
        val activeFocus = focus?.takeIf { it.endsAt > instant }
        val enabled = rules.filter { it.enabled }
        val context = EngineContext(
            usage = usage,
            focus = activeFocus,
            exceptions = exceptions,
            protectedPackages = protectedPackages,
        )

        // Everything that could be blocked: what has a rule, plus what the focus session covers.
        val targets = (enabled.map { it.target } + activeFocus?.targets.orEmpty()).distinct()
        val blocked = targets.mapNotNull { target ->
            val decision = engine.evaluate(AccessRequest(target, isSessionStart = true), rules, context, now)
            (decision as? Decision.Block)?.let { BlockedApp(target, it.reason, it.until) }
        }
        // An app that is blocked as a whole does not also list its features.
        val blockedNow = blocked.filterNot { entry ->
            entry.target is Target.Feature && blocked.any { it.target == Target.App(entry.target.packageName) }
        }

        val appPackages = enabled.filter { it.target is Target.App }.map { it.target.packageName }.toSet()
        return DashboardSnapshot(
            focus = activeFocus,
            blockedNow = blockedNow,
            nextChange = null,
            nextChangeStartsBlock = false,
            controlledCount = enabled.map { it.target.packageName }.distinct().size,
            usedTodayMillis = usage.usedMillisToday.filterKeys { it in appPackages }.values.sum(),
        ).withNextChange(nextChange(enabled, blockedNow, activeFocus, now))
    }

    private fun DashboardSnapshot.withNextChange(change: Pair<Instant, Boolean>?) =
        copy(nextChange = change?.first, nextChangeStartsBlock = change?.second ?: false)

    private fun nextChange(
        rules: List<Rule>,
        blockedNow: List<BlockedApp>,
        focus: FocusSession?,
        now: ZonedDateTime,
    ): Pair<Instant, Boolean>? {
        val candidates = mutableListOf<Pair<Instant, Boolean>>()
        blockedNow.mapNotNull { it.until }.forEach { candidates += it to false }
        focus?.let { candidates += it.endsAt to false }
        for (rule in rules) {
            for (restriction in rule.restrictions) {
                if (restriction !is Restriction.Schedule) continue
                val flip = ScheduleEvaluator.nextChange(restriction, now) ?: continue
                candidates += flip.toInstant() to ScheduleEvaluator.isBlocked(restriction, flip)
            }
        }
        return candidates.minByOrNull { it.first }
    }
}
