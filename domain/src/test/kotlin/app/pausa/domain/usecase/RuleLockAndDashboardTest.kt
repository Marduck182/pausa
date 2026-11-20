package app.pausa.domain.usecase

import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.Target
import app.pausa.domain.model.TimeWindow
import app.pausa.domain.model.UsageSnapshot
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleLockAndDashboardTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val instagram = Target.App("com.instagram.android")
    private val youtube = Target.App("com.google.android.youtube")
    private val shorts = Target.Feature("com.google.android.youtube", "youtube_shorts")

    // Monday 2026-11-16
    private fun at(hour: Int, minute: Int = 0): ZonedDateTime = ZonedDateTime.of(2026, 11, 16, hour, minute, 0, 0, zone)

    private val weekdayWork = Restriction.Schedule(
        ScheduleMode.BLOCK_DURING,
        listOf(
            TimeWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 9 * 60, 14 * 60),
            TimeWindow(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), 16 * 60, 21 * 60),
        ),
    )

    private fun rule(target: Target, vararg r: Restriction, id: Long = 1, hard: Boolean = false, enabled: Boolean = true) =
        Rule(id, target, r.toList(), enabled, hard)

    // ---- RuleLock

    @Test
    fun `a hard rule is locked while it blocks and free once it stops`() {
        val hard = rule(instagram, weekdayWork, hard = true)
        assertTrue(RuleLock.isLocked(hard, UsageSnapshot(), at(10)))
        assertFalse(RuleLock.isLocked(hard, UsageSnapshot(), at(15)))
    }

    @Test
    fun `a normal rule is never locked, even while blocking`() {
        val normal = rule(instagram, Restriction.AlwaysBlock)
        assertTrue(RuleLock.isBlockingNow(normal, UsageSnapshot(), at(10)))
        assertFalse(RuleLock.isLocked(normal, UsageSnapshot(), at(10)))
    }

    @Test
    fun `a disabled hard rule is not locked`() {
        assertFalse(RuleLock.isLocked(rule(instagram, Restriction.AlwaysBlock, hard = true, enabled = false), UsageSnapshot(), at(10)))
    }

    @Test
    fun `a hard rule with a daily limit locks only after the limit is reached`() {
        val hard = rule(instagram, Restriction.DailyLimit(30), hard = true)
        val under = UsageSnapshot(usedMillisToday = mapOf(instagram.packageName to 29 * 60_000L))
        val over = UsageSnapshot(usedMillisToday = mapOf(instagram.packageName to 30 * 60_000L))
        assertFalse(RuleLock.isLocked(hard, under, at(10)))
        assertTrue(RuleLock.isLocked(hard, over, at(10)))
    }

    // ---- Dashboard

    private fun dashboard(
        rules: List<Rule>,
        now: ZonedDateTime,
        usage: UsageSnapshot = UsageSnapshot(),
        focus: FocusSession? = null,
        exceptions: Set<String> = emptySet(),
    ) = DashboardCalculator.compute(rules, usage, focus, exceptions, protectedPackages = emptySet(), now = now)

    @Test
    fun `lists what is blocked now and when it ends`() {
        val snapshot = dashboard(listOf(rule(instagram, weekdayWork)), at(10))
        val blocked = snapshot.blockedNow.single()
        assertEquals(instagram, blocked.target)
        assertEquals(BlockReason.SCHEDULE, blocked.reason)
        assertEquals(at(14).toInstant(), snapshot.nextChange)
        assertFalse(snapshot.nextChangeStartsBlock)
    }

    @Test
    fun `when free, the next change is the next block starting`() {
        val snapshot = dashboard(listOf(rule(instagram, weekdayWork)), at(15))
        assertTrue(snapshot.blockedNow.isEmpty())
        assertEquals(at(16).toInstant(), snapshot.nextChange)
        assertTrue(snapshot.nextChangeStartsBlock)
    }

    @Test
    fun `no rules means nothing to show`() {
        val snapshot = dashboard(emptyList(), at(10))
        assertTrue(snapshot.blockedNow.isEmpty())
        assertNull(snapshot.nextChange)
        assertEquals(0, snapshot.controlledCount)
    }

    @Test
    fun `a focus session blocks its targets and its end is the next change`() {
        val end = at(11).toInstant()
        val focus = FocusSession(end, setOf(instagram))
        val snapshot = dashboard(listOf(rule(instagram, Restriction.Intervention())), at(10), focus = focus)
        assertEquals(BlockReason.FOCUS, snapshot.blockedNow.single().reason)
        assertEquals(end, snapshot.nextChange)
        assertEquals(focus, snapshot.focus)
    }

    @Test
    fun `an expired focus session is ignored`() {
        val focus = FocusSession(at(9).toInstant(), setOf(instagram))
        val snapshot = dashboard(listOf(rule(instagram, Restriction.Intervention())), at(10), focus = focus)
        assertNull(snapshot.focus)
        assertTrue(snapshot.blockedNow.isEmpty())
    }

    @Test
    fun `an excepted app is never listed as blocked`() {
        val snapshot = dashboard(listOf(rule(instagram, Restriction.AlwaysBlock)), at(10), exceptions = setOf(instagram.packageName))
        assertTrue(snapshot.blockedNow.isEmpty())
    }

    @Test
    fun `a blocked app hides its blocked features`() {
        val rules = listOf(rule(youtube, Restriction.AlwaysBlock, id = 1), rule(shorts, Restriction.AlwaysBlock, id = 2))
        val snapshot = dashboard(rules, at(10))
        assertEquals(listOf(youtube), snapshot.blockedNow.map { it.target })
    }

    @Test
    fun `a blocked feature shows when the app is free`() {
        val snapshot = dashboard(listOf(rule(shorts, Restriction.AlwaysBlock)), at(10))
        assertEquals(listOf<Target>(shorts), snapshot.blockedNow.map { it.target })
    }

    @Test
    fun `used time counts only apps with an active rule`() {
        val usage = UsageSnapshot(
            usedMillisToday = mapOf(instagram.packageName to 600_000L, "com.other" to 9_000_000L),
        )
        val snapshot = dashboard(listOf(rule(instagram, Restriction.Intervention())), at(10), usage = usage)
        assertEquals(600_000L, snapshot.usedTodayMillis)
        assertEquals(1, snapshot.controlledCount)
    }
}
