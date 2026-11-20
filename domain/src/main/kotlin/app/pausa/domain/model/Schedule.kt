package app.pausa.domain.model

import java.time.DayOfWeek

/**
 * A weekly time window. Times are minutes since midnight (0..1439). If [endMinute] <= [startMinute] the window
 * crosses midnight and belongs to the day it starts on: Monday 21:00 -> 09:00 runs until Tuesday 09:00.
 */
data class TimeWindow(
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(days.isNotEmpty()) { "A window needs at least one day" }
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of range: $endMinute" }
        require(startMinute != endMinute) { "A window cannot be empty; use AlwaysBlock for a whole day" }
    }

    val crossesMidnight: Boolean get() = endMinute < startMinute

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

enum class ScheduleMode {
    /** The app is blocked while any window is active, and free the rest of the time. */
    BLOCK_DURING,

    /** The app is only usable while a window is active, and blocked the rest of the time. */
    ALLOW_ONLY_DURING,
}
