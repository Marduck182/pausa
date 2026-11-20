package app.pausa.domain.usecase

import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.InterventionOutcome
import app.pausa.domain.model.RawStats
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsCalculatorTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val today = LocalDate.of(2026, 11, 19)
    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"

    private fun time(daysAgo: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(today.minusDays(daysAgo.toLong()).atTime(hour, minute), zone).toInstant()

    private fun session(pkg: String, daysAgo: Int, hour: Int, minute: Int, minutes: Long) =
        CompletedSession(pkg, time(daysAgo, hour, minute), time(daysAgo, hour, minute).plusSeconds(minutes * 60))

    private fun event(pkg: String, daysAgo: Int, outcome: InterventionOutcome, hour: Int = 12) =
        InterventionEvent(time(daysAgo, hour), pkg, outcome)

    private fun compute(raw: RawStats, first: LocalDate? = today.minusDays(10)) =
        StatsCalculator.compute(raw, first, today, zone)

    @Test
    fun `empty history gives zeros`() {
        val stats = compute(RawStats(emptyList(), emptyList()), first = null)
        assertEquals(0, stats.attemptsAvoided)
        assertEquals(0L, stats.estimatedSavedMillis)
        assertNull(stats.peakHour)
        assertEquals(0, stats.currentStreakDays)
        assertEquals(24, stats.minutesByHour.size)
    }

    @Test
    fun `blocked and cancelled openings count as avoided, a continued pause does not`() {
        val events = listOf(
            event(ig, 0, InterventionOutcome.BLOCKED),
            event(ig, 1, InterventionOutcome.CANCELLED),
            event(ig, 1, InterventionOutcome.CONTINUED),
        )
        val stats = compute(RawStats(emptyList(), events))
        assertEquals(2, stats.attemptsAvoided)
        assertEquals(1, stats.blockedAttempts)
        assertEquals(2, stats.interventions)
    }

    @Test
    fun `only the last seven days are counted`() {
        val events = listOf(event(ig, 6, InterventionOutcome.BLOCKED), event(ig, 7, InterventionOutcome.BLOCKED))
        assertEquals(1, compute(RawStats(emptyList(), events)).attemptsAvoided)
    }

    @Test
    fun `saved time is avoided openings times that apps usual session`() {
        val sessions = listOf(session(ig, 2, 10, 0, 20), session(ig, 3, 10, 0, 10)) // average 15 min
        val events = listOf(event(ig, 0, InterventionOutcome.BLOCKED), event(ig, 0, InterventionOutcome.CANCELLED))
        assertEquals(30 * 60_000L, compute(RawStats(sessions, events)).estimatedSavedMillis)
    }

    @Test
    fun `saved time falls back to ten minutes for an app with no history`() {
        val events = listOf(event(yt, 0, InterventionOutcome.BLOCKED))
        assertEquals(10 * 60_000L, compute(RawStats(emptyList(), events)).estimatedSavedMillis)
    }

    @Test
    fun `use per app is summed and sorted, most used first`() {
        val sessions = listOf(session(ig, 1, 9, 0, 10), session(yt, 1, 9, 30, 40), session(ig, 2, 9, 0, 15))
        val usage = compute(RawStats(sessions, emptyList())).usageByApp
        assertEquals(listOf(yt, ig), usage.map { it.packageName })
        assertEquals(40 * 60_000L, usage[0].millis)
        assertEquals(25 * 60_000L, usage[1].millis)
    }

    @Test
    fun `a session is spread over the hours it covers and the peak hour is found`() {
        val sessions = listOf(session(ig, 1, 21, 40, 40)) // 21:40 -> 22:20
        val stats = compute(RawStats(sessions, emptyList()))
        assertEquals(20, stats.minutesByHour[21])
        assertEquals(20, stats.minutesByHour[22])
        assertEquals(21, stats.peakHour)
    }

    @Test
    fun `streak counts days without giving in up to today`() {
        val first = today.minusDays(4)
        val events = listOf(event(ig, 2, InterventionOutcome.TEMP_ALLOW))
        val stats = compute(RawStats(emptyList(), events), first)
        // Days: -4 ok, -3 ok, -2 gave in, -1 ok, today ok
        assertEquals(2, stats.currentStreakDays)
        assertEquals(2, stats.bestStreakDays)
    }

    @Test
    fun `giving in today resets the streak to zero`() {
        val events = listOf(event(ig, 0, InterventionOutcome.RULE_DISABLED))
        val stats = compute(RawStats(emptyList(), events), today.minusDays(5))
        assertEquals(0, stats.currentStreakDays)
        assertEquals(5, stats.bestStreakDays)
    }

    @Test
    fun `blocked and cancelled openings never break a streak`() {
        val events = listOf(event(ig, 1, InterventionOutcome.BLOCKED), event(ig, 0, InterventionOutcome.CANCELLED))
        assertEquals(3, compute(RawStats(emptyList(), events), today.minusDays(2)).currentStreakDays)
    }

    @Test
    fun `days before the app was in use do not count`() {
        assertEquals(1, compute(RawStats(emptyList(), emptyList()), today).currentStreakDays)
    }
}
