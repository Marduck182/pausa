package app.pausa.domain.usecase

import app.pausa.domain.model.AppAttemptStat
import app.pausa.domain.model.AppUsageStat
import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.InterventionOutcome
import app.pausa.domain.model.RawStats
import app.pausa.domain.model.StatsSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Local statistics, computed on the phone from finished sessions and the user's own reactions. The numbers are
 * for the user to see how they are doing; nothing here is sent anywhere, and the only inputs are package names
 * and times.
 */
object StatsCalculator {
    const val WINDOW_DAYS = 7
    const val HISTORY_DAYS = 30

    /** Used when an app has no history yet to guess how long a skipped opening would have lasted. */
    private val DEFAULT_SESSION: Duration = Duration.ofMinutes(10)

    fun compute(raw: RawStats, firstActiveDay: LocalDate?, today: LocalDate, zone: ZoneId): StatsSnapshot {
        val windowStart = today.minusDays(WINDOW_DAYS - 1L).atStartOfDay(zone).toInstant()

        val weekSessions = raw.sessions.filter { it.start >= windowStart }
        val weekEvents = raw.events.filter { it.timestamp >= windowStart }

        val avoided = weekEvents.filter { it.outcome.isAvoided }
        val averages = averageSessionByApp(raw.sessions)
        val saved = avoided.sumOf { (averages[it.packageName] ?: DEFAULT_SESSION).toMillis() }

        val usageByApp = weekSessions
            .groupBy { it.packageName }
            .map { (pkg, sessions) -> AppUsageStat(pkg, sessions.sumOf { millis(it) }) }
            .sortedByDescending { it.millis }

        val attemptsToday = attemptsToday(raw, today, zone)

        val minutesByHour = minutesByHour(weekSessions, zone)
        val peak = minutesByHour.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index

        val givingInDays = raw.events.filter { it.outcome.isGivingIn }.map { dayOf(it.timestamp, zone) }.toSet()
        val streaks = streaks(firstActiveDay, today, givingInDays)

        return StatsSnapshot(
            attemptsAvoided = avoided.size,
            blockedAttempts = weekEvents.count { it.outcome == InterventionOutcome.BLOCKED },
            interventions = weekEvents.count {
                it.outcome == InterventionOutcome.CANCELLED ||
                    it.outcome == InterventionOutcome.CONTINUED
            },
            estimatedSavedMillis = saved,
            usageByApp = usageByApp,
            attemptsTodayByApp = attemptsToday,
            minutesByHour = minutesByHour,
            peakHour = peak,
            currentStreakDays = streaks.first,
            bestStreakDays = streaks.second,
        )
    }

    /**
     * Each time the user went for a controlled app today ends one of three ways: it opened (a session started), it was
     * blocked, or the user backed out of the pause. So attempts = sessions + blocked + backed out. A pause that was
     * gone through is not added again: the session that follows already counts it.
     */
    private fun attemptsToday(raw: RawStats, today: LocalDate, zone: ZoneId): List<AppAttemptStat> {
        val dayStart = today.atStartOfDay(zone).toInstant()
        val opened = raw.sessions.filter { it.start >= dayStart }.groupingBy { it.packageName }.eachCount()
        val stopped = raw.events
            .filter { it.timestamp >= dayStart && it.outcome.isAvoided }
            .groupingBy { it.packageName }
            .eachCount()
        return (opened.keys + stopped.keys)
            .map { pkg -> AppAttemptStat(pkg, attempts = (opened[pkg] ?: 0) + (stopped[pkg] ?: 0), stopped = stopped[pkg] ?: 0) }
            .sortedWith(compareByDescending<AppAttemptStat> { it.attempts }.thenBy { it.packageName })
    }

    private fun millis(session: CompletedSession) = Duration.between(session.start, session.end).toMillis()

    private fun averageSessionByApp(sessions: List<CompletedSession>): Map<String, Duration> =
        sessions.groupBy { it.packageName }.mapValues { (_, list) -> Duration.ofMillis(list.sumOf { millis(it) } / list.size) }

    /** Spreads each session over the hours it covered, so a session from 21:40 to 22:20 counts in both. */
    private fun minutesByHour(sessions: List<CompletedSession>, zone: ZoneId): List<Int> {
        val millisPerHour = LongArray(24)
        for (session in sessions) {
            var cursor = session.start
            while (cursor < session.end) {
                val local = cursor.atZone(zone)
                val nextHour = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
                val to = minOf(session.end, nextHour)
                millisPerHour[local.hour] += Duration.between(cursor, to).toMillis()
                cursor = to
            }
        }
        return millisPerHour.map { (it / 60_000L).toInt() }
    }

    private fun dayOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    /**
     * A day counts for the streak when the user did not give in (no temporary pass, no focus ended early, no rule
     * turned off). Days before the app saw any activity do not count. Returns (current, best).
     */
    private fun streaks(first: LocalDate?, today: LocalDate, givingInDays: Set<LocalDate>): Pair<Int, Int> {
        if (first == null || first > today) return 0 to 0
        var best = 0
        var run = 0
        var day: LocalDate = first
        while (!day.isAfter(today)) {
            if (day in givingInDays) run = 0 else run++
            if (run > best) best = run
            day = day.plusDays(1)
        }
        return run to best
    }
}
