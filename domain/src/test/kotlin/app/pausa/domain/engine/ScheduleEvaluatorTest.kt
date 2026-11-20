package app.pausa.domain.engine

import app.pausa.domain.model.Restriction
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.TimeWindow
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEvaluatorTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    // 2026-11-16 is a Monday, 2026-11-21 a Saturday.
    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    private fun min(hour: Int, minute: Int = 0) = hour * 60 + minute

    /** The example from the brief: weekdays blocked 09-14 and 16-21, free in between and at night. */
    private val workday = Restriction.Schedule(
        ScheduleMode.BLOCK_DURING,
        listOf(
            TimeWindow(weekdays, min(9), min(14)),
            TimeWindow(weekdays, min(16), min(21)),
        ),
    )

    @Test
    fun `blocked inside the morning window`() {
        assertTrue(ScheduleEvaluator.isBlocked(workday, at(16, 9)))
        assertTrue(ScheduleEvaluator.isBlocked(workday, at(16, 13, 59)))
    }

    @Test
    fun `window end is exclusive and start is inclusive`() {
        assertFalse(ScheduleEvaluator.isBlocked(workday, at(16, 14)))
        assertFalse(ScheduleEvaluator.isBlocked(workday, at(16, 8, 59)))
    }

    @Test
    fun `free between windows and after the evening window`() {
        assertFalse(ScheduleEvaluator.isBlocked(workday, at(16, 15)))
        assertTrue(ScheduleEvaluator.isBlocked(workday, at(16, 16)))
        assertFalse(ScheduleEvaluator.isBlocked(workday, at(16, 21)))
    }

    @Test
    fun `weekend has no blocking`() {
        assertFalse(ScheduleEvaluator.isBlocked(workday, at(21, 10)))
        assertFalse(ScheduleEvaluator.isBlocked(workday, at(22, 18)))
    }

    @Test
    fun `window that crosses midnight belongs to the day it starts`() {
        val night = Restriction.Schedule(
            ScheduleMode.BLOCK_DURING,
            listOf(TimeWindow(setOf(DayOfWeek.MONDAY), min(21), min(9))),
        )
        assertTrue(ScheduleEvaluator.isBlocked(night, at(16, 22)))
        assertTrue(ScheduleEvaluator.isBlocked(night, at(17, 8, 59))) // Tuesday morning, still Monday's window
        assertFalse(ScheduleEvaluator.isBlocked(night, at(17, 9)))
        assertFalse(ScheduleEvaluator.isBlocked(night, at(16, 8))) // Monday morning: Sunday had no window
        assertFalse(ScheduleEvaluator.isBlocked(night, at(17, 22))) // Tuesday evening: not a listed day
    }

    @Test
    fun `allow-only mode blocks everything outside the windows`() {
        val onlyEvening = Restriction.Schedule(
            ScheduleMode.ALLOW_ONLY_DURING,
            listOf(TimeWindow(weekdays, min(21), min(23))),
        )
        assertTrue(ScheduleEvaluator.isBlocked(onlyEvening, at(16, 12)))
        assertFalse(ScheduleEvaluator.isBlocked(onlyEvening, at(16, 22)))
        assertTrue(ScheduleEvaluator.isBlocked(onlyEvening, at(21, 22))) // Saturday is not a listed day
    }

    @Test
    fun `next change while blocked is the end of the window`() {
        assertEquals(at(16, 14), ScheduleEvaluator.nextChange(workday, at(16, 10)))
    }

    @Test
    fun `next change while free is the start of the next window`() {
        assertEquals(at(16, 16), ScheduleEvaluator.nextChange(workday, at(16, 15)))
    }

    @Test
    fun `next change skips the weekend`() {
        // Friday 22:00 is free; the next block starts Monday 09:00.
        assertEquals(at(23, 9), ScheduleEvaluator.nextChange(workday, at(20, 22)))
    }

    @Test
    fun `next change across midnight lands on the following day`() {
        val night = Restriction.Schedule(
            ScheduleMode.BLOCK_DURING,
            listOf(TimeWindow(setOf(DayOfWeek.MONDAY), min(21), min(9))),
        )
        assertEquals(at(17, 9), ScheduleEvaluator.nextChange(night, at(16, 23)))
    }

    @Test
    fun `no windows means no change`() {
        val empty = Restriction.Schedule(ScheduleMode.BLOCK_DURING, emptyList())
        assertNull(ScheduleEvaluator.nextChange(empty, at(16, 10)))
        assertFalse(ScheduleEvaluator.isBlocked(empty, at(16, 10)))
    }

    @Test
    fun `daylight saving day still lands on the right wall clock time`() {
        val newYork = ZoneId.of("America/New_York")
        val sundayWindow = Restriction.Schedule(
            ScheduleMode.BLOCK_DURING,
            listOf(TimeWindow(setOf(DayOfWeek.SUNDAY), min(9), min(14))),
        )
        // 2026-03-08: clocks jump forward at 02:00. At 08:00 EDT the next change is 09:00 EDT.
        val from = ZonedDateTime.of(2026, 3, 8, 8, 0, 0, 0, newYork)
        assertEquals(
            ZonedDateTime.of(2026, 3, 8, 9, 0, 0, 0, newYork),
            ScheduleEvaluator.nextChange(sundayWindow, from),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty window is rejected`() {
        TimeWindow(weekdays, min(9), min(9))
    }
}
