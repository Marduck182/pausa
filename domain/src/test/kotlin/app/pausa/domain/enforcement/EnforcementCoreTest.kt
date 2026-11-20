package app.pausa.domain.enforcement

import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.Target
import app.pausa.domain.model.TimeWindow
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnforcementCoreTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val launcher = "com.sec.android.app.launcher"
    private val systemUi = "com.android.systemui"
    private val pausa = "app.pausa"

    private lateinit var core: EnforcementCore

    // Monday 2026-11-16
    private fun at(hour: Int, minute: Int = 0): ZonedDateTime = ZonedDateTime.of(2026, 11, 16, hour, minute, 0, 0, zone)

    private fun rule(pkg: String, vararg restrictions: Restriction, id: Long = 1) =
        Rule(id, Target.App(pkg), restrictions.toList())

    @Before
    fun setUp() {
        core = EnforcementCore(interventionGrace = Duration.ofMinutes(5))
        core.systemPackages = SystemPackageSets(
            home = setOf(launcher),
            transient = setOf(systemUi),
            protectedPackages = setOf(pausa, launcher),
        )
    }

    @Test
    fun `opening an uncontrolled app does nothing`() {
        core.rules = listOf(rule(instagram, Restriction.AlwaysBlock))
        assertEquals(EnforcementAction.None, core.onWindowChanged(youtube, at(10)))
    }

    @Test
    fun `opening a blocked app shows the block`() {
        core.rules = listOf(rule(instagram, Restriction.AlwaysBlock))
        val action = core.onWindowChanged(instagram, at(10)) as EnforcementAction.ShowBlock
        assertEquals(instagram, action.packageName)
        assertEquals(BlockReason.ALWAYS, action.block.reason)
    }

    @Test
    fun `a block is not repeated on every tick, and opening again is checked again`() {
        core.rules = listOf(rule(instagram, Restriction.AlwaysBlock))
        core.onWindowChanged(instagram, at(10))
        assertEquals(EnforcementAction.None, core.onTick(at(10, 1)))
        assertTrue(core.onWindowChanged(instagram, at(10, 2)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `events from the same app are not a new session`() {
        core.rules = listOf(rule(instagram, Restriction.Intervention()))
        assertTrue(core.onWindowChanged(instagram, at(10)) is EnforcementAction.ShowIntervention)
        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(10, 1)))
    }

    @Test
    fun `transient windows do not end the session or start one`() {
        core.rules = listOf(rule(instagram, Restriction.Intervention()))
        core.onWindowChanged(instagram, at(10))
        assertEquals(EnforcementAction.None, core.onWindowChanged(systemUi, at(10, 1)))
        assertEquals(instagram, core.currentPackage)
        // Pulling down the shade and coming back is still the same session: no second pause.
        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(10, 2)))
    }

    @Test
    fun `going home ends the session`() {
        core.rules = listOf(rule(instagram, Restriction.Intervention()))
        core.onWindowChanged(instagram, at(10))
        assertEquals(EnforcementAction.None, core.onWindowChanged(launcher, at(10, 1)))
        assertNull(core.currentPackage)
    }

    @Test
    fun `the pause is skipped for a while after continuing, then returns`() {
        core.rules = listOf(rule(instagram, Restriction.Intervention()))
        core.onWindowChanged(instagram, at(10))
        core.onInterventionPassed(instagram, at(10))

        core.onWindowChanged(launcher, at(10, 1))
        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(10, 4)))

        core.onWindowChanged(launcher, at(10, 5))
        assertTrue(core.onWindowChanged(instagram, at(10, 6)) is EnforcementAction.ShowIntervention)
    }

    @Test
    fun `a schedule that starts while the app is open blocks it on the next tick`() {
        val weekdayAfternoon = Restriction.Schedule(
            ScheduleMode.BLOCK_DURING,
            listOf(TimeWindow(setOf(DayOfWeek.MONDAY), 16 * 60, 21 * 60)),
        )
        core.rules = listOf(rule(instagram, weekdayAfternoon))

        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(15, 58)))
        assertEquals(EnforcementAction.None, core.onTick(at(15, 59)))
        assertTrue(core.onTick(at(16, 0)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `turning the screen off makes the same app a new session`() {
        core.rules = listOf(rule(instagram, Restriction.Intervention()))
        core.onWindowChanged(instagram, at(10))
        core.onScreenOff(at(10, 0))
        assertTrue(core.onWindowChanged(instagram, at(10, 1)) is EnforcementAction.ShowIntervention)
    }

    @Test
    fun `protected packages are never blocked`() {
        core.rules = listOf(rule(pausa, Restriction.AlwaysBlock))
        assertEquals(EnforcementAction.None, core.onWindowChanged(pausa, at(10)))
    }

    @Test
    fun `rules changed while the app is open apply on the next tick`() {
        core.rules = emptyList()
        core.onWindowChanged(instagram, at(10))
        core.rules = listOf(rule(instagram, Restriction.AlwaysBlock))
        assertTrue(core.onTick(at(10, 1)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `a disabled rule does nothing`() {
        core.rules = listOf(Rule(1, Target.App(instagram), listOf(Restriction.AlwaysBlock), enabled = false))
        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(10)))
    }
}
