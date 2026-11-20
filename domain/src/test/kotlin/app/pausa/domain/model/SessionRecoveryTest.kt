package app.pausa.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryTest {

    private val zone = ZoneId.of("America/Mexico_City")

    private fun millis(hour: Int, minute: Int = 0, day: Int = 10) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `counts the time up to the last note`() {
        val checkpoint = SessionCheckpoint("com.game", millis(10, 0), millis(10, 25))

        val sessions = SessionRecovery.recover(checkpoint, zone)

        assertEquals(1, sessions.size)
        assertEquals(Duration.ofMinutes(25), Duration.between(sessions[0].start, sessions[0].end))
    }

    @Test
    fun `a note with no elapsed time recovers nothing`() {
        assertTrue(SessionRecovery.recover(SessionCheckpoint("com.game", millis(10), millis(10)), zone).isEmpty())
        assertTrue(SessionRecovery.recover(SessionCheckpoint("com.game", millis(10), millis(9)), zone).isEmpty())
    }

    @Test
    fun `a blank package recovers nothing`() {
        assertTrue(SessionRecovery.recover(SessionCheckpoint(" ", millis(10), millis(11)), zone).isEmpty())
    }

    @Test
    fun `a session across midnight is split into two days`() {
        val checkpoint = SessionCheckpoint("com.game", millis(23, 30), millis(0, 20, day = 11))

        val sessions = SessionRecovery.recover(checkpoint, zone)

        assertEquals(2, sessions.size)
        assertEquals(Duration.ofMinutes(30), Duration.between(sessions[0].start, sessions[0].end))
        assertEquals(Duration.ofMinutes(20), Duration.between(sessions[1].start, sessions[1].end))
        assertEquals(sessions[0].end, sessions[1].start)
    }

    @Test
    fun `an absurdly long note is capped`() {
        val start = millis(0)
        val checkpoint = SessionCheckpoint("com.game", start, Instant.ofEpochMilli(start).plus(Duration.ofDays(3)).toEpochMilli())

        val total = SessionRecovery.recover(checkpoint, zone).sumOf { Duration.between(it.start, it.end).toMinutes() }

        assertEquals(SessionRecovery.MAX_SESSION.toMinutes(), total)
    }
}
