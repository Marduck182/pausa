package app.pausa.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A note the guard leaves every little while about the app in front: which one, since when, and the last moment it
 * was known to still be there. If the process is killed (low memory, a crash, an update) the session that was open
 * would otherwise vanish and the time would never count against a daily limit.
 */
data class SessionCheckpoint(
    val packageName: String,
    val startMillis: Long,
    val lastSeenMillis: Long,
)

/** Turns a checkpoint left behind by a dead process into the sessions that should be stored. */
object SessionRecovery {
    /** No real stretch with one app in front and the screen on lasts this long: a longer one is a bad reading. */
    val MAX_SESSION: Duration = Duration.ofHours(12)

    /**
     * Counts the time up to [SessionCheckpoint.lastSeenMillis], never up to "now": the phone may have been off or the
     * guard dead for hours, and that time was not spent in the app. Split at midnight so each piece belongs to one day.
     */
    fun recover(checkpoint: SessionCheckpoint, zone: ZoneId): List<CompletedSession> {
        val start = Instant.ofEpochMilli(checkpoint.startMillis)
        val end = minOf(Instant.ofEpochMilli(checkpoint.lastSeenMillis), start.plus(MAX_SESSION))
        if (checkpoint.packageName.isBlank() || end <= start) return emptyList()

        val pieces = mutableListOf<CompletedSession>()
        var from = start
        while (from < end) {
            val nextMidnight = from.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            val to = minOf(end, nextMidnight)
            if (to > from) pieces += CompletedSession(checkpoint.packageName, from, to)
            from = to
        }
        return pieces
    }
}
