package app.pausa.domain.enforcement

import app.pausa.domain.enforcement.ForegroundReconciler.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundReconcilerTest {

    private val game = "com.supercell.clashroyale"
    private val launcher = "com.sec.android.app.launcher"
    private val home = setOf(launcher)

    private val reconciler = ForegroundReconciler()
    private val afterHome = ForegroundReconciler.AFTER_HOME_MILLIS
    private val afterLeave = ForegroundReconciler.AFTER_LEAVE_MILLIS

    @Test
    fun `the stale game window right after going home is ignored`() {
        // The bug seen on the phone: home gesture at t=0, a reading 150 ms later still lists the game on top.
        reconciler.onWindowEvent(isHomeScreen = true, nowMillis = 0)
        assertEquals(Verdict.Nothing, reconciler.onReading(game, home, current = null, nowMillis = 150))
        assertEquals(Verdict.Nothing, reconciler.onReading(game, home, current = null, nowMillis = afterHome - 1))
    }

    @Test
    fun `once the animation is over a stale reading still needs a second one to agree`() {
        reconciler.onWindowEvent(isHomeScreen = true, nowMillis = 0)
        assertEquals(Verdict.ReadAgain, reconciler.onReading(game, home, current = null, nowMillis = afterHome + 100))
        // The second reading shows the launcher: the first was wrong and nothing happens.
        assertEquals(Verdict.Nothing, reconciler.onReading(launcher, home, current = null, nowMillis = afterHome + 350))
    }

    @Test
    fun `an app that really came back with no event is acted on after two agreeing readings`() {
        assertEquals(Verdict.ReadAgain, reconciler.onReading(game, home, current = null, nowMillis = 10_000))
        assertEquals(Verdict.Act, reconciler.onReading(game, home, current = null, nowMillis = 10_250))
    }

    @Test
    fun `the pause after going home is short enough not to be felt as a delay`() {
        // Opening a game from the recent apps list: the launcher event comes first, then the game. The wait before the
        // block is this pause plus one confirming reading; it must stay under a second.
        assertTrue("pause was $afterHome ms", afterHome <= 1_000)
        assertTrue("pause was $afterLeave ms", afterLeave <= 1_000)
    }

    @Test
    fun `seeing the launcher while at home is no disagreement`() {
        // This used to be reported over and over as a correction.
        assertEquals(Verdict.Nothing, reconciler.onReading(launcher, home, current = null, nowMillis = 5_000))
        assertEquals(Verdict.Nothing, reconciler.onReading(launcher, home, current = null, nowMillis = 5_400))
    }

    @Test
    fun `seeing the app the core already knows is no disagreement`() {
        assertEquals(Verdict.Nothing, reconciler.onReading(game, home, current = game, nowMillis = 5_000))
    }

    @Test
    fun `a missed trip home is found too`() {
        assertEquals(Verdict.ReadAgain, reconciler.onReading(launcher, home, current = game, nowMillis = 5_000))
        assertEquals(Verdict.Act, reconciler.onReading(launcher, home, current = game, nowMillis = 5_250))
    }

    @Test
    fun `a real window event drops the doubt from an earlier reading`() {
        assertEquals(Verdict.ReadAgain, reconciler.onReading(game, home, current = null, nowMillis = 5_000))
        reconciler.onWindowEvent(isHomeScreen = false, nowMillis = 5_100)
        // The doubt was cleared, so this one starts over instead of acting.
        assertEquals(Verdict.ReadAgain, reconciler.onReading(game, home, current = null, nowMillis = 5_350))
    }

    @Test
    fun `readings are ignored for a moment after Pausa sends the user out`() {
        reconciler.onLeft(nowMillis = 1_000)
        assertTrue(reconciler.isSuppressed(1_000 + afterLeave - 1))
        assertEquals(Verdict.Nothing, reconciler.onReading(game, home, current = null, nowMillis = 1_000 + afterLeave - 1))
        assertFalse(reconciler.isSuppressed(1_000 + afterLeave))
        assertEquals(Verdict.ReadAgain, reconciler.onReading(game, home, current = null, nowMillis = 1_000 + afterLeave))
    }

    @Test
    fun `an ordinary event in an app does not pause the readings`() {
        reconciler.onWindowEvent(isHomeScreen = false, nowMillis = 1_000)
        assertFalse(reconciler.isSuppressed(1_001))
    }

    @Test
    fun `no window found means nothing to say`() {
        assertEquals(Verdict.Nothing, reconciler.onReading(null, home, current = game, nowMillis = 5_000))
    }

    @Test
    fun `the longer pause wins when two overlap`() {
        reconciler.onWindowEvent(isHomeScreen = true, nowMillis = 0) // until afterHome
        reconciler.onLeft(nowMillis = 1) // ends earlier than the first: must not shorten it
        assertTrue(reconciler.isSuppressed(afterHome - 1))
    }
}
