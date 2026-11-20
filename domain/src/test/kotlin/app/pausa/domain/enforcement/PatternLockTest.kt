package app.pausa.domain.enforcement

import app.pausa.domain.engine.RuleEngine
import app.pausa.domain.model.AccessRequest
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.Decision
import app.pausa.domain.model.EngineContext
import app.pausa.domain.model.FeatureIds
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.Target
import app.pausa.domain.model.TemporaryAllow
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PatternLockTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val engine = RuleEngine()
    private val bank = "com.example.bank"
    private val launcher = "com.sec.android.app.launcher"

    private fun at(hour: Int, minute: Int = 0, second: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, 19, hour, minute, second, 0, zone)

    private fun rule(vararg restrictions: Restriction, id: Long = 1, target: Target = Target.App(bank)) =
        Rule(id, target, restrictions.toList())

    private fun decide(
        rules: List<Rule>,
        context: EngineContext = EngineContext(),
        sessionStart: Boolean = true,
        target: Target = Target.App(bank),
    ) = engine.evaluate(AccessRequest(target, sessionStart), rules, context, at(10))

    // ---- engine

    @Test
    fun `opening an app with a pattern rule asks for the pattern`() {
        assertEquals(Decision.RequirePattern(1), decide(listOf(rule(Restriction.PatternLock))))
    }

    @Test
    fun `an app already open is not asked again`() {
        assertEquals(Decision.Allow, decide(listOf(rule(Restriction.PatternLock)), sessionStart = false))
    }

    @Test
    fun `a recent correct pattern lets the app through, an old one does not`() {
        val rules = listOf(rule(Restriction.PatternLock))
        val recent = EngineContext(patternPassUntil = mapOf(bank to at(10, 1).toInstant()))
        val old = EngineContext(patternPassUntil = mapOf(bank to at(9, 59).toInstant()))
        assertEquals(Decision.Allow, decide(rules, recent))
        assertEquals(Decision.RequirePattern(1), decide(rules, old))
    }

    @Test
    fun `without a pattern set the rule asks for nothing, so nobody is locked out`() {
        assertEquals(Decision.Allow, decide(listOf(rule(Restriction.PatternLock)), EngineContext(patternAvailable = false)))
    }

    @Test
    fun `a block wins over the pattern`() {
        val decision = decide(listOf(rule(Restriction.PatternLock, Restriction.AlwaysBlock)))
        assertTrue(decision is Decision.Block)
        assertEquals(BlockReason.ALWAYS, (decision as Decision.Block).reason)
    }

    @Test
    fun `a temporary pass does not skip the pattern`() {
        val context = EngineContext(temporaryAllows = listOf(TemporaryAllow(bank, at(10, 10).toInstant())))
        assertEquals(Decision.RequirePattern(1), decide(listOf(rule(Restriction.PatternLock)), context))
    }

    @Test
    fun `the pattern comes before the pause`() {
        val rules = listOf(rule(Restriction.PatternLock, Restriction.Intervention()))
        assertEquals(Decision.RequirePattern(1), decide(rules))
        val unlocked = EngineContext(patternPassUntil = mapOf(bank to at(10, 2).toInstant()))
        assertTrue(decide(rules, unlocked) is Decision.Intervene)
    }

    @Test
    fun `an excepted or protected app is never asked`() {
        val rules = listOf(rule(Restriction.PatternLock))
        assertEquals(Decision.Allow, decide(rules, EngineContext(exceptions = setOf(bank))))
        assertEquals(Decision.Allow, decide(rules, EngineContext(protectedPackages = setOf(bank))))
    }

    @Test
    fun `a feature is never asked for a pattern, the app was already unlocked`() {
        val feature = Target.Feature(bank, FeatureIds.YOUTUBE_SHORTS)
        assertEquals(Decision.Allow, decide(listOf(rule(Restriction.PatternLock, target = feature)), target = feature))
        // The whole-app rule does not ask again for one of its features either.
        assertEquals(Decision.Allow, decide(listOf(rule(Restriction.PatternLock)), target = feature))
    }

    // ---- rule draft

    @Test
    fun `the draft round-trips the pattern requirement and a feature draft drops it`() {
        val draft = RuleDraft(packageName = bank, patternLock = true)
        assertEquals(draft, RuleDraft.from(draft.toRule()))
        val feature = RuleDraft.newForFeature(bank, FeatureIds.YOUTUBE_SHORTS).copy(patternLock = true)
        assertFalse(feature.toRule().restrictions.contains(Restriction.PatternLock))
    }

    // ---- core

    private lateinit var core: EnforcementCore

    @Before
    fun setUp() {
        core = EnforcementCore(patternGrace = Duration.ofMinutes(2))
        core.systemPackages = SystemPackageSets(home = setOf(launcher))
        core.patternAvailable = true
    }

    @Test
    fun `the core asks for the pattern, then lets the user in and counts use from then on`() {
        core.rules = listOf(rule(Restriction.PatternLock, Restriction.DailyLimit(10)))
        val recorded = mutableListOf<Any>()
        core.sessionListener = { recorded += it }

        assertEquals(EnforcementAction.ShowPatternLock(bank), core.onWindowChanged(bank, at(10)))
        // Two minutes of staring at the lock screen cost no time.
        assertEquals(EnforcementAction.None, core.onPatternPassed(bank, at(10, 2)))
        assertEquals(EnforcementAction.None, core.onTick(at(10, 11, 59)))
        assertTrue(core.onTick(at(10, 12)) is EnforcementAction.ShowBlock)
        assertEquals(1, recorded.size)
    }

    @Test
    fun `after the right pattern the pause still comes`() {
        core.rules = listOf(rule(Restriction.PatternLock, Restriction.Intervention(waitSeconds = 7)))
        core.onWindowChanged(bank, at(10))
        val next = core.onPatternPassed(bank, at(10, 1)) as EnforcementAction.ShowIntervention
        assertEquals(7, next.intervention.waitSeconds)
    }

    @Test
    fun `backing out of the lock screen costs no time and no opening`() {
        core.rules = listOf(rule(Restriction.PatternLock, Restriction.SessionLimit(1)))
        core.onWindowChanged(bank, at(10))
        core.onWindowChanged(launcher, at(10, 5))
        // Still allowed one opening: the abandoned lock screen used none.
        assertEquals(EnforcementAction.ShowPatternLock(bank), core.onWindowChanged(bank, at(11)))
    }

    @Test
    fun `the pattern is not asked again for a short while, then it is`() {
        core.rules = listOf(rule(Restriction.PatternLock))
        core.onWindowChanged(bank, at(10))
        core.onPatternPassed(bank, at(10, 0, 5))

        core.onWindowChanged(launcher, at(10, 1))
        assertEquals(EnforcementAction.None, core.onWindowChanged(bank, at(10, 1, 30)))
        core.onWindowChanged(launcher, at(10, 2))
        assertEquals(EnforcementAction.ShowPatternLock(bank), core.onWindowChanged(bank, at(10, 3)))
    }

    @Test
    fun `without a pattern the core never asks`() {
        core.patternAvailable = false
        core.rules = listOf(rule(Restriction.PatternLock))
        assertEquals(EnforcementAction.None, core.onWindowChanged(bank, at(10)))
    }
}
