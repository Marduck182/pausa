package app.pausa.domain.enforcement

import app.pausa.domain.model.BlockMode
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.FeatureIds
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.Target
import app.pausa.domain.model.TemporaryAllow
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnforcementCoreFocusAndFeatureTest {

    private val zone = ZoneId.of("America/Mexico_City")
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val launcher = "com.sec.android.app.launcher"

    private lateinit var core: EnforcementCore

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime = ZonedDateTime.of(2026, 11, 16, hour, minute, 0, 0, zone)

    private fun rule(target: Target, vararg r: Restriction, id: Long = 1, hard: Boolean = false) =
        Rule(id, target, r.toList(), hardMode = hard)

    @Before
    fun setUp() {
        core = EnforcementCore()
        core.systemPackages = SystemPackageSets(home = setOf(launcher))
    }

    // ---- focus, exceptions, temporary passes

    @Test
    fun `a focus session blocks the apps it covers even without a rule`() {
        core.focus = FocusSession(at(11).toInstant(), setOf(Target.App(instagram)))
        val action = core.onWindowChanged(instagram, at(10)) as EnforcementAction.ShowBlock
        assertEquals(BlockReason.FOCUS, action.block.reason)
    }

    @Test
    fun `starting a focus session while an app is open blocks it on the next tick`() {
        core.rules = listOf(rule(Target.App(instagram), Restriction.Intervention()))
        core.onWindowChanged(instagram, at(10))
        core.focus = FocusSession(at(11).toInstant(), setOf(Target.App(instagram)))
        assertTrue(core.onTick(at(10, 1)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `an exception passes a focus session`() {
        core.focus = FocusSession(at(11).toInstant(), setOf(Target.App(instagram)))
        core.exceptions = setOf(instagram)
        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(10)))
    }

    @Test
    fun `a temporary pass lets an app through a rule, then stops working`() {
        core.rules = listOf(rule(Target.App(instagram), Restriction.AlwaysBlock))
        core.temporaryAllows = listOf(TemporaryAllow(instagram, at(10, 10).toInstant()))

        assertEquals(EnforcementAction.None, core.onWindowChanged(instagram, at(10)))
        core.onWindowChanged(launcher, at(10, 5))
        assertTrue(core.onWindowChanged(instagram, at(10, 11)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `a temporary pass does not beat hard mode or a focus session`() {
        val pass = listOf(TemporaryAllow(instagram, at(10, 10).toInstant()))
        core.temporaryAllows = pass
        core.rules = listOf(rule(Target.App(instagram), Restriction.AlwaysBlock, hard = true))
        assertTrue(core.onWindowChanged(instagram, at(10)) is EnforcementAction.ShowBlock)

        core.onWindowChanged(launcher, at(10, 1))
        core.rules = emptyList()
        core.focus = FocusSession(at(11).toInstant(), setOf(Target.App(instagram)))
        assertTrue(core.onWindowChanged(instagram, at(10, 2)) is EnforcementAction.ShowBlock)
    }

    // ---- features

    private val shorts = Target.Feature(youtube, FeatureIds.YOUTUBE_SHORTS)

    @Test
    fun `entering a blocked feature blocks it and keeps the app open`() {
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        core.onWindowChanged(youtube, at(10))

        val action = core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 1)) as EnforcementAction.ShowBlock
        assertEquals(FeatureIds.YOUTUBE_SHORTS, action.featureId)
        assertEquals(youtube, core.currentPackage) // the app itself is not forgotten
    }

    @Test
    fun `staying in the feature is not a new entry, leaving and returning is`() {
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        core.onWindowChanged(youtube, at(10))

        assertTrue(core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 1)) is EnforcementAction.ShowBlock)
        assertEquals(EnforcementAction.None, core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 2)))
        assertEquals(EnforcementAction.None, core.onFeatureDetected(youtube, null, at(10, 3)))
        assertTrue(core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 4)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `after being sent back, the same feature is blocked again if the user is still in it`() {
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        core.onWindowChanged(youtube, at(10))
        assertTrue(core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 1)) is EnforcementAction.ShowBlock)

        // The Back that was meant to leave Shorts did nothing: the screen still shows Shorts.
        core.forgetFeature()
        assertTrue(core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 2)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `a feature rule does not affect the rest of the app`() {
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        assertEquals(EnforcementAction.None, core.onWindowChanged(youtube, at(10)))
        assertEquals(EnforcementAction.None, core.onFeatureDetected(youtube, null, at(10, 1)))
    }

    @Test
    fun `a whole-app rule also blocks the feature`() {
        core.rules = listOf(rule(Target.App(youtube), Restriction.AlwaysBlock))
        // The app itself was blocked on opening; if it is somehow still in front, the feature is blocked too.
        core.onWindowChanged(launcher, at(10))
        core.onWindowChanged(youtube, at(10, 1))
        assertEquals(null, core.currentPackage)
    }

    @Test
    fun `a feature seen in another app is ignored`() {
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        core.onWindowChanged(instagram, at(10))
        assertEquals(EnforcementAction.None, core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 1)))
    }

    @Test
    fun `a feature can ask for a pause`() {
        core.rules = listOf(rule(shorts, Restriction.Intervention(waitSeconds = 8)))
        core.onWindowChanged(youtube, at(10))
        val action = core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 1)) as EnforcementAction.ShowIntervention
        assertEquals(8, action.intervention.waitSeconds)
        assertEquals(FeatureIds.YOUTUBE_SHORTS, action.featureId)
    }

    @Test
    fun `the feature is forgotten when the user changes app`() {
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        core.onWindowChanged(youtube, at(10))
        core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 1))
        core.onWindowChanged(launcher, at(10, 2))
        core.onWindowChanged(youtube, at(10, 3))
        assertTrue(core.onFeatureDetected(youtube, FeatureIds.YOUTUBE_SHORTS, at(10, 4)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `the screen is only worth reading when a feature rule is active`() {
        assertFalse(core.hasFeatureRules(youtube))
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        assertTrue(core.hasFeatureRules(youtube))
        assertFalse(core.hasFeatureRules(instagram))
        core.rules = listOf(Rule(1, shorts, listOf(Restriction.AlwaysBlock), enabled = false))
        assertFalse(core.hasFeatureRules(youtube))
    }

    @Test
    fun `a feature rule does not make the whole app timed`() {
        val recorded = mutableListOf<Any>()
        core.sessionListener = { recorded += it }
        core.rules = listOf(rule(shorts, Restriction.AlwaysBlock))
        core.onWindowChanged(youtube, at(10))
        core.onWindowChanged(launcher, at(10, 30))
        assertTrue(recorded.isEmpty())
    }

    // ---- RuleDraft for features

    @Test
    fun `a new feature draft blocks and round-trips as a feature rule`() {
        val draft = RuleDraft.newForFeature(youtube, FeatureIds.YOUTUBE_SHORTS)
        assertEquals(BlockMode.ALWAYS, draft.blockMode)
        assertEquals(shorts, draft.toRule().target)
        assertEquals(draft, RuleDraft.from(draft.toRule()))
    }

    @Test
    fun `a feature draft drops time and opening limits`() {
        val rule = RuleDraft.newForFeature(youtube, FeatureIds.YOUTUBE_SHORTS)
            .copy(dailyLimitMinutes = 30, maxSessionsPerDay = 3)
            .toRule()
        assertEquals(listOf<Restriction>(Restriction.AlwaysBlock), rule.restrictions)
    }
}
