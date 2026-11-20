package app.pausa.domain.engine

import app.pausa.domain.model.Restriction
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.TimeWindow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure schedule maths. Everything takes an explicit [ZonedDateTime], so tests control time and the result
 * respects the user's time zone and daylight saving changes.
 */
object ScheduleEvaluator {

    fun isWindowActive(window: TimeWindow, at: ZonedDateTime): Boolean {
        val minute = at.hour * 60 + at.minute
        val today = at.dayOfWeek
        return if (!window.crossesMidnight) {
            today in window.days && minute >= window.startMinute && minute < window.endMinute
        } else {
            // The part after the start belongs to today; the part before the end belongs to a window that
            // started yesterday.
            (today in window.days && minute >= window.startMinute) ||
                (today.minus(1) in window.days && minute < window.endMinute)
        }
    }

    fun isBlocked(schedule: Restriction.Schedule, at: ZonedDateTime): Boolean {
        val anyActive = schedule.windows.any { isWindowActive(it, at) }
        return when (schedule.mode) {
            ScheduleMode.BLOCK_DURING -> anyActive
            ScheduleMode.ALLOW_ONLY_DURING -> !anyActive
        }
    }

    /**
     * The next moment, after [from], when the blocked/allowed state of [schedule] flips. Null if it never
     * does within the next week (for example, no windows at all).
     */
    fun nextChange(schedule: Restriction.Schedule, from: ZonedDateTime): ZonedDateTime? {
        val current = isBlocked(schedule, from)
        val firstDate = from.toLocalDate().minusDays(1)
        val candidates = sortedSetOf<ZonedDateTime>()
        for (offset in 0..8) {
            val date = firstDate.plusDays(offset.toLong())
            for (window in schedule.windows) {
                if (date.dayOfWeek !in window.days) continue
                val start = at(date, window.startMinute, from)
                val endDate = if (window.crossesMidnight) date.plusDays(1) else date
                val end = at(endDate, window.endMinute, from)
                if (start.isAfter(from)) candidates += start
                if (end.isAfter(from)) candidates += end
            }
        }
        return candidates.firstOrNull { isBlocked(schedule, it) != current }
    }

    private fun at(date: LocalDate, minuteOfDay: Int, reference: ZonedDateTime): ZonedDateTime =
        LocalDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).atZone(reference.zone)
}
