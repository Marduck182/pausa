package app.pausa.domain.model

import java.time.Instant

/** What the rule engine tells the enforcement layer to do about an access attempt. */
sealed interface Decision {
    data object Allow : Decision

    /** Let the user through, but only after a deliberate pause. */
    data class Intervene(val intervention: Restriction.Intervention, val ruleId: Long) : Decision

    /** Let the user in only after drawing the security pattern. */
    data class RequirePattern(val ruleId: Long) : Decision

    /** Do not let the user in. [until] is when the block ends, or null if it has no end (always blocked). */
    data class Block(val reason: BlockReason, val until: Instant?, val ruleId: Long?) : Decision
}

enum class BlockReason { ALWAYS, SCHEDULE, DAILY_LIMIT, SESSION_LIMIT, FOCUS }
