package app.pausa.domain.engine

import app.pausa.domain.model.AccessRequest
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.Decision
import app.pausa.domain.model.EngineContext
import app.pausa.domain.model.FeatureIds
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.Target
import app.pausa.domain.model.TemporaryAllow
import app.pausa.domain.model.TimeWindow
import app.pausa.domain.model.UsageSnapshot
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val engine = RuleEngine()
    private val zone = ZoneId.of("America/Mexico_City")

    private val instagram = Target.App("com.instagram.android")
    private val youtube = Target.App("com.google.android.youtube")
    private val shorts = Target.Feature("com.google.android.youtube", FeatureIds.YOUTUBE_SHORTS)

    // Monday 2026-11-16, 10:00
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, zone)

    private fun open(target: Target, sessionStart: Boolean = true) = AccessRequest(target, sessionStart)

    private fun decide(
        request: AccessRequest,
        rules: List<Rule>,
        context: EngineContext = EngineContext(),
    ) = engine.evaluate(request, rules, context, now)

    private fun rule(
        target: Target,
        vararg restrictions: Restriction,
        id: Long = 1,
        hard: Boolean = false,
        enabled: Boolean = true,
    ) = Rule(id, target, restrictions.toList(), enabled, hard)

    private val weekdayMorning = Restriction.Schedule(
        ScheduleMode.BLOCK_DURING,
        listOf(
            TimeWindow(
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                9 * 60,
                14 * 60,
            ),
        ),
    )

    @Test
    fun `no rules allows`() {
        assertEquals(Decision.Allow, decide(open(instagram), emptyList()))
    }

    @Test
    fun `always block blocks with no end`() {
        val decision = decide(open(instagram), listOf(rule(instagram, Restriction.AlwaysBlock)))
        assertEquals(Decision.Block(BlockReason.ALWAYS, null, 1), decision)
    }

    @Test
    fun `disabled rule is ignored`() {
        val rules = listOf(rule(instagram, Restriction.AlwaysBlock, enabled = false))
        assertEquals(Decision.Allow, decide(open(instagram), rules))
    }

    @Test
    fun `rule for one app does not affect another`() {
        assertEquals(Decision.Allow, decide(open(youtube), listOf(rule(instagram, Restriction.AlwaysBlock))))
    }

    @Test
    fun `schedule blocks until the end of the window`() {
        val decision = decide(open(instagram), listOf(rule(instagram, weekdayMorning)))
        val block = decision as Decision.Block
        assertEquals(BlockReason.SCHEDULE, block.reason)
        assertEquals(now.withHour(14).toInstant(), block.until)
    }

    @Test
    fun `schedule outside its window allows`() {
        val late = now.withHour(15)
        val decision = engine.evaluate(open(instagram), listOf(rule(instagram, weekdayMorning)), EngineContext(), late)
        assertEquals(Decision.Allow, decision)
    }

    @Test
    fun `daily limit blocks once reached and until tomorrow`() {
        val rules = listOf(rule(instagram, Restriction.DailyLimit(30)))
        val under = EngineContext(usage = UsageSnapshot(usedMillisToday = mapOf(instagram.packageName to 29 * 60_000L)))
        val over = EngineContext(usage = UsageSnapshot(usedMillisToday = mapOf(instagram.packageName to 30 * 60_000L)))

        assertEquals(Decision.Allow, decide(open(instagram), rules, under))
        val block = decide(open(instagram), rules, over) as Decision.Block
        assertEquals(BlockReason.DAILY_LIMIT, block.reason)
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant(), block.until)
    }

    @Test
    fun `session limit blocks a new session but not the one already open`() {
        val rules = listOf(rule(instagram, Restriction.SessionLimit(3)))
        val context = EngineContext(usage = UsageSnapshot(sessionsStartedToday = mapOf(instagram.packageName to 3)))

        val opening = decide(open(instagram, sessionStart = true), rules, context)
        val midSession = decide(open(instagram, sessionStart = false), rules, context)

        assertEquals(BlockReason.SESSION_LIMIT, (opening as Decision.Block).reason)
        assertEquals(Decision.Allow, midSession)
    }

    @Test
    fun `app rule covers its features, feature rule does not cover the whole app`() {
        val appRule = listOf(rule(youtube, Restriction.AlwaysBlock))
        val featureRule = listOf(rule(shorts, Restriction.AlwaysBlock))

        assertTrue(decide(open(shorts), appRule) is Decision.Block)
        assertTrue(decide(open(shorts), featureRule) is Decision.Block)
        assertEquals(Decision.Allow, decide(open(youtube), featureRule))
    }

    @Test
    fun `protected packages and exceptions always pass`() {
        val rules = listOf(rule(instagram, Restriction.AlwaysBlock))
        val protectedCtx = EngineContext(protectedPackages = setOf(instagram.packageName))
        val exceptionCtx = EngineContext(exceptions = setOf(instagram.packageName))

        assertEquals(Decision.Allow, decide(open(instagram), rules, protectedCtx))
        assertEquals(Decision.Allow, decide(open(instagram), rules, exceptionCtx))
    }

    @Test
    fun `focus session blocks its targets until it ends`() {
        val end = now.plusMinutes(30).toInstant()
        val context = EngineContext(focus = FocusSession(end, setOf(instagram)))

        assertEquals(Decision.Block(BlockReason.FOCUS, end, null), decide(open(instagram), emptyList(), context))
        assertEquals(Decision.Allow, decide(open(youtube), emptyList(), context))
    }

    @Test
    fun `expired focus session does nothing`() {
        val context = EngineContext(focus = FocusSession(now.minusMinutes(1).toInstant(), setOf(instagram)))
        assertEquals(Decision.Allow, decide(open(instagram), emptyList(), context))
    }

    @Test
    fun `temporary allow cancels a normal rule`() {
        val rules = listOf(rule(instagram, Restriction.AlwaysBlock))
        val context = EngineContext(
            temporaryAllows = listOf(TemporaryAllow(instagram.packageName, now.plusMinutes(10).toInstant())),
        )
        assertEquals(Decision.Allow, decide(open(instagram), rules, context))
    }

    @Test
    fun `expired temporary allow no longer cancels`() {
        val rules = listOf(rule(instagram, Restriction.AlwaysBlock))
        val context = EngineContext(
            temporaryAllows = listOf(TemporaryAllow(instagram.packageName, now.minusMinutes(1).toInstant())),
        )
        assertTrue(decide(open(instagram), rules, context) is Decision.Block)
    }

    @Test
    fun `temporary allow cannot beat hard mode or a focus session`() {
        val allow = listOf(TemporaryAllow(instagram.packageName, now.plusMinutes(10).toInstant()))
        val hard = listOf(rule(instagram, Restriction.AlwaysBlock, hard = true))
        val focusCtx = EngineContext(
            temporaryAllows = allow,
            focus = FocusSession(now.plusMinutes(30).toInstant(), setOf(instagram)),
        )

        assertTrue(decide(open(instagram), hard, EngineContext(temporaryAllows = allow)) is Decision.Block)
        assertTrue(decide(open(instagram), emptyList(), focusCtx) is Decision.Block)
    }

    @Test
    fun `most restrictive block wins - no end beats an end`() {
        val rules = listOf(
            rule(instagram, weekdayMorning, id = 1),
            rule(instagram, Restriction.AlwaysBlock, id = 2),
        )
        assertEquals(Decision.Block(BlockReason.ALWAYS, null, 2), decide(open(instagram), rules))
    }

    @Test
    fun `most restrictive block wins - the latest end`() {
        val end = now.plusHours(5).toInstant()
        val context = EngineContext(focus = FocusSession(end, setOf(instagram)))
        val block = decide(open(instagram), listOf(rule(instagram, weekdayMorning)), context) as Decision.Block
        assertEquals(BlockReason.FOCUS, block.reason)
        assertEquals(end, block.until)
    }

    @Test
    fun `intervention only on session start`() {
        val rules = listOf(rule(instagram, Restriction.Intervention(waitSeconds = 7, breathing = true)))
        val start = decide(open(instagram, sessionStart = true), rules)
        val mid = decide(open(instagram, sessionStart = false), rules)

        assertEquals(7, (start as Decision.Intervene).intervention.waitSeconds)
        assertEquals(Decision.Allow, mid)
    }

    @Test
    fun `passing the intervention silences it for a while`() {
        val rules = listOf(rule(instagram, Restriction.Intervention()))
        val context = EngineContext(
            interventionPassUntil = mapOf(instagram.packageName to now.plusMinutes(5).toInstant()),
        )
        assertEquals(Decision.Allow, decide(open(instagram), rules, context))
    }

    @Test
    fun `a block wins over an intervention`() {
        val rules = listOf(rule(instagram, Restriction.Intervention(), Restriction.AlwaysBlock))
        assertTrue(decide(open(instagram), rules) is Decision.Block)
    }
}
