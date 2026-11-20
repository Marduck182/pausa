package app.pausa.data.db

import app.pausa.domain.model.Restriction
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.TimeWindow
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionJsonTest {

    private val everything: List<Restriction> = listOf(
        Restriction.AlwaysBlock,
        Restriction.Schedule(
            ScheduleMode.ALLOW_ONLY_DURING,
            listOf(
                TimeWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 9 * 60, 14 * 60),
                TimeWindow(setOf(DayOfWeek.SUNDAY), 21 * 60, 9 * 60),
            ),
        ),
        Restriction.DailyLimit(30),
        Restriction.SessionLimit(3),
        Restriction.PatternLock,
        Restriction.Intervention(waitSeconds = 8, breathing = true),
    )

    @Test
    fun `every kind of restriction survives being stored and read back`() {
        assertEquals(everything, RestrictionJson.decode(RestrictionJson.encode(everything)))
    }

    @Test
    fun `an empty list round-trips`() {
        assertEquals(emptyList<Restriction>(), RestrictionJson.decode(RestrictionJson.encode(emptyList())))
    }

    @Test
    fun `the stored format is the one already on the phone`() {
        // A rule saved by an earlier version must still be readable: this exact text is on the user's device.
        val onPhone = """{"items":[{"type":"always_block"},{"type":"intervention","waitSeconds":5,"breathing":false}]}"""
        assertEquals(
            listOf(Restriction.AlwaysBlock, Restriction.Intervention(5, false)),
            RestrictionJson.decode(onPhone),
        )
    }

    @Test
    fun `the pattern requirement is stored under its own name`() {
        assertTrue(RestrictionJson.encode(listOf(Restriction.PatternLock)).contains("pattern_lock"))
    }

    @Test(expected = Exception::class)
    fun `damaged text is an error, not an empty rule`() {
        RestrictionJson.decode("not json")
    }
}
