package app.pausa.domain.model

import java.time.Instant

/**
 * Something the user did in front of a restriction. Only a short code and the package name are kept: never what
 * was on the screen. The last three outcomes are the ones that count as giving in, which is what breaks a streak.
 */
enum class InterventionOutcome {
    /** The app was blocked when the user tried to open it. */
    BLOCKED,

    /** The user saw the pause and chose not to open the app. */
    CANCELLED,

    /** The user saw the pause and went on. */
    CONTINUED,

    /** The user asked for a temporary pass through a block. */
    TEMP_ALLOW,

    /** The user ended a focus session before it was over. */
    FOCUS_ENDED_EARLY,

    /** The user turned a rule off or deleted it. */
    RULE_DISABLED,
    ;

    val isGivingIn: Boolean get() = this == TEMP_ALLOW || this == FOCUS_ENDED_EARLY || this == RULE_DISABLED
    val isAvoided: Boolean get() = this == BLOCKED || this == CANCELLED
}

data class InterventionEvent(
    val timestamp: Instant,
    val packageName: String,
    val outcome: InterventionOutcome,
)

/** Raw rows for the statistics window: finished sessions and events. */
data class RawStats(
    val sessions: List<CompletedSession>,
    val events: List<InterventionEvent>,
)

data class AppUsageStat(val packageName: String, val millis: Long)

/**
 * How many times, today, the user tried to open an app: every opening that was let in plus every one Pausa stopped
 * ([stopped], blocked or backed out of at the pause). A count of events, not a measurement of time.
 */
data class AppAttemptStat(val packageName: String, val attempts: Int, val stopped: Int)

data class StatsSnapshot(
    /** Times, this week, the user did not get into an app: blocked at the door or backed out of a pause. */
    val attemptsAvoided: Int,
    /** Times this week the user tried to open something that was blocked. */
    val blockedAttempts: Int,
    /** Pauses shown this week (backed out of or gone through). */
    val interventions: Int,
    /** A rough guess, not a measurement: avoided openings times the typical length of that app's sessions. */
    val estimatedSavedMillis: Long,
    /** Use per app over the last seven days, most used first. */
    val usageByApp: List<AppUsageStat>,
    /** Today's attempts to open each controlled app, most attempts first. */
    val attemptsTodayByApp: List<AppAttemptStat> = emptyList(),
    /** Minutes of use per hour of the day over the last seven days (24 entries). */
    val minutesByHour: List<Int>,
    /** The hour with the most use, or null if there is none. */
    val peakHour: Int?,
    /** Consecutive days up to today without giving in. */
    val currentStreakDays: Int,
    val bestStreakDays: Int,
)
