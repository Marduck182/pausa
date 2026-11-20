package app.pausa.domain.engine

import app.pausa.domain.model.AccessRequest
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.Decision
import app.pausa.domain.model.EngineContext
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.Target
import app.pausa.domain.model.appliesTo
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Decides what happens when the user tries to use something. A pure function of its inputs: no clock, no
 * storage, no Android. That is what makes the rules easy to test and safe to extend.
 *
 * Order of precedence:
 *  1. Protected packages and user exceptions are always allowed.
 *  2. Every blocking restriction that applies is collected (focus session + each rule).
 *  3. A temporary allow cancels the blocks from normal rules, but not hard-mode rules or a focus session:
 *     both are explicit commitments the user made earlier.
 *  4. If anything still blocks, the most restrictive block wins (one without an end beats one with an end,
 *     otherwise the latest end).
 *  5. Otherwise, opening an app that has a pattern rule asks for the pattern (a temporary pass does not skip it).
 *  6. Otherwise an intervention may ask for a pause, only when a session is starting.
 */
class RuleEngine {

    fun evaluate(
        request: AccessRequest,
        rules: List<Rule>,
        context: EngineContext,
        now: ZonedDateTime,
    ): Decision {
        val packageName = request.target.packageName
        val instant = now.toInstant()

        if (packageName in context.protectedPackages) return Decision.Allow
        if (packageName in context.exceptions) return Decision.Allow

        val applicable = rules.filter { it.enabled && it.target.appliesTo(request.target) }
        val hasTemporaryAllow = context.temporaryAllows.any { it.packageName == packageName && it.until > instant }

        val blocks = mutableListOf<Candidate>()
        focusBlock(request, context, instant)?.let { blocks += Candidate(it, overridable = false) }
        for (rule in applicable) {
            for (restriction in rule.restrictions) {
                blockFor(rule, restriction, request, context, now)?.let {
                    blocks += Candidate(it, overridable = !rule.hardMode)
                }
            }
        }

        val effective = if (hasTemporaryAllow) blocks.filterNot { it.overridable } else blocks
        mostRestrictive(effective)?.let { return it }

        if (request.isSessionStart && request.target is Target.App && context.patternAvailable) {
            val gate = applicable.firstOrNull { rule -> rule.restrictions.any { it is Restriction.PatternLock } }
            if (gate != null) {
                val passUntil = context.patternPassUntil[packageName]
                if (passUntil == null || passUntil <= instant) return Decision.RequirePattern(gate.id)
            }
        }

        if (request.isSessionStart && !hasTemporaryAllow) {
            val passUntil = context.interventionPassUntil[packageName]
            val passed = passUntil != null && passUntil > instant
            if (!passed) {
                interventionFor(applicable)?.let { return it }
            }
        }
        return Decision.Allow
    }

    private fun focusBlock(request: AccessRequest, context: EngineContext, now: Instant): Decision.Block? {
        val focus = context.focus ?: return null
        if (focus.endsAt <= now) return null
        if (focus.targets.none { it.appliesTo(request.target) }) return null
        return Decision.Block(BlockReason.FOCUS, focus.endsAt, ruleId = null)
    }

    private fun blockFor(
        rule: Rule,
        restriction: Restriction,
        request: AccessRequest,
        context: EngineContext,
        now: ZonedDateTime,
    ): Decision.Block? {
        val packageName = request.target.packageName
        return when (restriction) {
            Restriction.AlwaysBlock -> Decision.Block(BlockReason.ALWAYS, until = null, ruleId = rule.id)

            is Restriction.Schedule ->
                if (ScheduleEvaluator.isBlocked(restriction, now)) {
                    Decision.Block(
                        BlockReason.SCHEDULE,
                        until = ScheduleEvaluator.nextChange(restriction, now)?.toInstant(),
                        ruleId = rule.id,
                    )
                } else {
                    null
                }

            is Restriction.DailyLimit ->
                if (context.usage.usedMillis(packageName) >= restriction.minutes * 60_000L) {
                    Decision.Block(BlockReason.DAILY_LIMIT, until = startOfNextDay(now), ruleId = rule.id)
                } else {
                    null
                }

            is Restriction.SessionLimit ->
                // A session that is already open is never cut off by the session count: only opening a new one.
                if (request.isSessionStart && context.usage.sessions(packageName) >= restriction.maxPerDay) {
                    Decision.Block(BlockReason.SESSION_LIMIT, until = startOfNextDay(now), ruleId = rule.id)
                } else {
                    null
                }

            is Restriction.Intervention, Restriction.PatternLock -> null
        }
    }

    private fun interventionFor(applicable: List<Rule>): Decision.Intervene? {
        var best: Decision.Intervene? = null
        for (rule in applicable) {
            for (restriction in rule.restrictions) {
                if (restriction !is Restriction.Intervention) continue
                val current = best
                if (current == null || restriction.waitSeconds > current.intervention.waitSeconds) {
                    best = Decision.Intervene(restriction, rule.id)
                }
            }
        }
        return best
    }

    private fun mostRestrictive(candidates: List<Candidate>): Decision.Block? {
        if (candidates.isEmpty()) return null
        candidates.firstOrNull { it.block.until == null }?.let { return it.block }
        return candidates.maxBy { it.block.until!! }.block
    }

    private fun startOfNextDay(now: ZonedDateTime): Instant =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant()

    /** [overridable] is false for blocks a temporary allow must not cancel. */
    private class Candidate(val block: Decision.Block, val overridable: Boolean)
}
