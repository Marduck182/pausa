package app.pausa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleDraftTest {

    private val pkg = "com.instagram.android"

    @Test
    fun `a new draft only has a gentle pause`() {
        val rule = RuleDraft.newFor(pkg).toRule()
        assertEquals(Target.App(pkg), rule.target)
        assertEquals(listOf<Restriction>(Restriction.Intervention()), rule.restrictions)
        assertTrue(rule.enabled)
    }

    @Test
    fun `the example from the brief round-trips through a rule`() {
        val draft = RuleDraft(
            ruleId = 7,
            packageName = pkg,
            blockMode = BlockMode.SCHEDULE,
            windows = RuleDraft.workdayWindows(),
            dailyLimitMinutes = 30,
            maxSessionsPerDay = 3,
            intervention = Restriction.Intervention(waitSeconds = 8, breathing = true),
        )
        assertEquals(draft, RuleDraft.from(draft.toRule()))
    }

    @Test
    fun `always block wins over a stored schedule when reading back`() {
        val rule = Rule(
            id = 1,
            target = Target.App(pkg),
            restrictions = listOf(
                Restriction.AlwaysBlock,
                Restriction.Schedule(ScheduleMode.BLOCK_DURING, RuleDraft.workdayWindows()),
            ),
        )
        assertEquals(BlockMode.ALWAYS, RuleDraft.from(rule).blockMode)
    }

    @Test
    fun `turning limits off removes their restrictions`() {
        val withLimits = RuleDraft(packageName = pkg, dailyLimitMinutes = 20, maxSessionsPerDay = 2)
        val without = withLimits.copy(dailyLimitMinutes = null, maxSessionsPerDay = null)
        assertEquals(2, withLimits.toRule().restrictions.size)
        assertTrue(without.toRule().restrictions.isEmpty())
    }

    @Test
    fun `a schedule without windows is not valid`() {
        assertFalse(RuleDraft(packageName = pkg, blockMode = BlockMode.SCHEDULE).isValid)
        assertTrue(RuleDraft(packageName = pkg, blockMode = BlockMode.SCHEDULE, windows = RuleDraft.workdayWindows()).isValid)
        assertTrue(RuleDraft(packageName = pkg, blockMode = BlockMode.NONE).isValid)
    }

    @Test
    fun `disabled and hard mode survive the round trip`() {
        val draft = RuleDraft(packageName = pkg, enabled = false, hardMode = true, blockMode = BlockMode.ALWAYS)
        val back = RuleDraft.from(draft.toRule())
        assertFalse(back.enabled)
        assertTrue(back.hardMode)
    }
}
