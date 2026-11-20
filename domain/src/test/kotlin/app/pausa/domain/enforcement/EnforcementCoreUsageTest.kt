package app.pausa.domain.enforcement

import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.PackageUsage
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.Target
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnforcementCoreUsageTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val launcher = "com.sec.android.app.launcher"

    private lateinit var core: EnforcementCore
    private val recorded = mutableListOf<CompletedSession>()

    // Monday 2026-11-16 unless a day is given.
    private fun at(hour: Int, minute: Int = 0, second: Int = 0, day: Int = 16): ZonedDateTime =
        ZonedDateTime.of(2026, 11, day, hour, minute, second, 0, zone)

    private fun rule(pkg: String, vararg restrictions: Restriction) = Rule(1, Target.App(pkg), restrictions.toList())

    private fun minutes(session: CompletedSession) = Duration.between(session.start, session.end).toMinutes()

    @Before
    fun setUp() {
        core = EnforcementCore()
        core.systemPackages = SystemPackageSets(home = setOf(launcher))
        core.sessionListener = { recorded += it }
    }

    @Test
    fun `daily limit blocks on the minute it is reached and the session is recorded`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        core.onWindowChanged(instagram, at(10))

        assertEquals(EnforcementAction.None, core.onTick(at(10, 29, 59)))
        val action = core.onTick(at(10, 30)) as EnforcementAction.ShowBlock
        assertEquals(BlockReason.DAILY_LIMIT, action.block.reason)

        assertEquals(1, recorded.size)
        assertEquals(30L, minutes(recorded.single()))
    }

    @Test
    fun `use adds up across several openings`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        core.onWindowChanged(instagram, at(9))
        core.onWindowChanged(launcher, at(9, 20))
        core.onWindowChanged(instagram, at(12))

        assertEquals(EnforcementAction.None, core.onTick(at(12, 9)))
        assertTrue(core.onTick(at(12, 10)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `after the limit, opening the app again is blocked right away`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(10)))
        core.onWindowChanged(instagram, at(10))
        core.onTick(at(10, 10))
        core.onWindowChanged(launcher, at(10, 11))

        assertTrue(core.onWindowChanged(instagram, at(14)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `session limit allows that many openings and blocks the next one`() {
        core.rules = listOf(rule(instagram, Restriction.SessionLimit(2)))
        core.onWindowChanged(instagram, at(9))
        core.onWindowChanged(launcher, at(9, 5))
        core.onWindowChanged(instagram, at(10))
        core.onWindowChanged(launcher, at(10, 5))

        val third = core.onWindowChanged(instagram, at(11)) as EnforcementAction.ShowBlock
        assertEquals(BlockReason.SESSION_LIMIT, third.block.reason)
    }

    @Test
    fun `a session already open is never cut off by the session limit`() {
        core.rules = listOf(rule(instagram, Restriction.SessionLimit(1)))
        core.onWindowChanged(instagram, at(9))
        assertEquals(EnforcementAction.None, core.onTick(at(9, 30)))
    }

    @Test
    fun `a pause the user walks away from does not count as an opening or as time`() {
        core.rules = listOf(
            rule(instagram, Restriction.SessionLimit(1), Restriction.DailyLimit(5), Restriction.Intervention()),
        )
        assertTrue(core.onWindowChanged(instagram, at(9)) is EnforcementAction.ShowIntervention)
        core.onWindowChanged(launcher, at(9, 20)) // cancelled after twenty minutes of hesitating

        assertTrue(recorded.isEmpty())
        // Still allowed to open once: the abandoned pause used up nothing.
        assertTrue(core.onWindowChanged(instagram, at(10)) is EnforcementAction.ShowIntervention)
    }

    @Test
    fun `time is counted from the moment the pause is accepted`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(10), Restriction.Intervention()))
        core.onWindowChanged(instagram, at(9))
        core.onInterventionPassed(instagram, at(9, 5)) // five minutes of hesitation before this

        assertEquals(EnforcementAction.None, core.onTick(at(9, 14)))
        assertTrue(core.onTick(at(9, 15)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `apps without a rule are never timed`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        core.onWindowChanged(youtube, at(9))
        core.onWindowChanged(launcher, at(10))
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `a disabled rule is not timed`() {
        core.rules = listOf(Rule(1, Target.App(instagram), listOf(Restriction.DailyLimit(30)), enabled = false))
        core.onWindowChanged(instagram, at(9))
        core.onWindowChanged(launcher, at(10))
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `usage restored after a restart counts toward today`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        core.acceptingEvents = false
        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(8))) // ignored while loading

        core.restoreUsage(at(10).toLocalDate(), mapOf(instagram to PackageUsage(usedMillis = 25 * 60_000L, sessions = 2)))
        core.onWindowChanged(launcher, at(10))
        core.onWindowChanged(instagram, at(10, 1))

        assertEquals(EnforcementAction.None, core.onTick(at(10, 5)))
        assertTrue(core.onTick(at(10, 6)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `turning the screen off ends and records the session`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        core.onWindowChanged(instagram, at(10))
        core.onScreenOff(at(10, 7))

        assertEquals(7L, minutes(recorded.single()))
    }

    @Test
    fun `a session across midnight is split in two and the new day starts from zero`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        core.onWindowChanged(instagram, at(23, 50))

        // Twenty-five minutes after midnight: only those count today, so still allowed.
        assertEquals(EnforcementAction.None, core.onTick(at(0, 25, day = 17)))
        assertTrue(core.onTick(at(0, 30, day = 17)) is EnforcementAction.ShowBlock)

        assertEquals(2, recorded.size)
        assertEquals(10L, minutes(recorded[0])) // 23:50 -> 00:00
        assertEquals(30L, minutes(recorded[1])) // 00:00 -> 00:30
        assertEquals(at(0, day = 17).toInstant(), recorded[0].end)
        assertEquals(at(0, day = 17).toInstant(), recorded[1].start)
    }

    @Test
    fun `the check delay shrinks as a daily limit gets close`() {
        core.rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        core.onWindowChanged(instagram, at(10))

        assertEquals(EnforcementCore.TICK_MILLIS, core.nextCheckDelayMillis(at(10, 5)))
        val nearLimit = core.nextCheckDelayMillis(at(10, 29, 55))
        assertTrue("expected about 5 s, was $nearLimit", nearLimit in 4_500..6_000)
    }

    @Test
    fun `the check delay is the usual one when nothing can run out`() {
        core.rules = listOf(rule(instagram, Restriction.Intervention()))
        core.onWindowChanged(instagram, at(10))
        assertEquals(EnforcementCore.TICK_MILLIS, core.nextCheckDelayMillis(at(10, 1)))
        assertEquals(EnforcementCore.TICK_MILLIS, EnforcementCore().nextCheckDelayMillis(at(10)))
    }
}
