package app.pausa.domain.usecase

import app.pausa.domain.engine.RuleEngine
import app.pausa.domain.model.AccessRequest
import app.pausa.domain.model.Decision
import app.pausa.domain.model.EngineContext
import app.pausa.domain.model.Rule
import app.pausa.domain.model.UsageSnapshot
import java.time.ZonedDateTime

/**
 * Hard mode: while a hard-mode rule is blocking, it cannot be edited, turned off or deleted. The point is to stop
 * a rule being switched off in the very moment of the urge. Once the block ends, the rule is editable again.
 *
 * This is a promise Pausa keeps inside the app. It does not, and must not, stop the user from turning the
 * accessibility service off in system settings or uninstalling the app.
 */
object RuleLock {
    private val engine = RuleEngine()

    /** Whether [rule] is blocking right now, looked at on its own. */
    fun isBlockingNow(rule: Rule, usage: UsageSnapshot, now: ZonedDateTime): Boolean {
        if (!rule.enabled) return false
        val decision = engine.evaluate(
            AccessRequest(rule.target, isSessionStart = true),
            listOf(rule),
            EngineContext(usage = usage),
            now,
        )
        return decision is Decision.Block
    }

    fun isLocked(rule: Rule, usage: UsageSnapshot, now: ZonedDateTime): Boolean =
        rule.hardMode && isBlockingNow(rule, usage, now)
}
