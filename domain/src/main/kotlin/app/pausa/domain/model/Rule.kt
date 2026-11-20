package app.pausa.domain.model

/**
 * One rule: a [target] plus the restrictions that apply to it. New kinds of restriction are new subtypes of
 * [Restriction]; the engine and the storage format need one new branch each, nothing else changes.
 */
data class Rule(
    val id: Long = 0,
    val target: Target,
    val restrictions: List<Restriction>,
    val enabled: Boolean = true,
    /** While a hard-mode rule is blocking, it cannot be edited, disabled or temporarily allowed. */
    val hardMode: Boolean = false,
)

sealed interface Restriction {
    /** The target can never be used. */
    data object AlwaysBlock : Restriction

    /** Blocks by time of day and day of week. */
    data class Schedule(
        val mode: ScheduleMode,
        val windows: List<TimeWindow>,
    ) : Restriction

    /** Blocks once the target has been used for [minutes] today. */
    data class DailyLimit(val minutes: Int) : Restriction {
        init {
            require(minutes > 0) { "minutes must be positive" }
        }
    }

    /** Blocks opening the target again after it was opened [maxPerDay] times today. */
    data class SessionLimit(val maxPerDay: Int) : Restriction {
        init {
            require(maxPerDay > 0) { "maxPerDay must be positive" }
        }
    }

    /**
     * Asks for the security pattern before the app opens. It does not block by itself and a temporary pass does
     * not skip it: it is there to keep other hands (and impulses) out, not to limit time.
     */
    data object PatternLock : Restriction

    /** Never blocks: adds a pause (and optionally a breathing exercise) before opening, like One Sec's idea. */
    data class Intervention(
        val waitSeconds: Int = 5,
        val breathing: Boolean = false,
    ) : Restriction {
        init {
            require(waitSeconds in 0..120) { "waitSeconds must be 0..120" }
        }
    }
}
