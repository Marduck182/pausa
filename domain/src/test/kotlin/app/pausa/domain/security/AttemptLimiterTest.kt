package app.pausa.domain.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptLimiterTest {

    private var clock = 1_000L
    private var checks = 0

    private suspend fun AttemptLimiter.go(right: Boolean): AttemptResult =
        attempt({ clock }) { checks++; right }

    private suspend fun AttemptLimiter.failTimes(n: Int) = repeat(n) { go(false) }

    @Test
    fun `wrong tries below the limit do not lock`() = runBlocking {
        val limiter = AttemptLimiter(maxFailures = 5)

        limiter.failTimes(4)

        assertEquals(0L, limiter.remainingLockMillis(clock))
        assertEquals(AttemptResult.Success, limiter.go(true))
    }

    @Test
    fun `the fifth wrong try starts a wait and later tries are not even checked`() = runBlocking {
        val limiter = AttemptLimiter(maxFailures = 5, baseLockMillis = 30_000)
        limiter.failTimes(4)

        val fifth = limiter.go(false)
        val checksBefore = checks
        val during = limiter.go(true)

        assertEquals(AttemptResult.Wrong(30_000), fifth)
        assertEquals(AttemptResult.Locked(30_000), during)
        assertEquals("a try during the wait must not reach the check", checksBefore, checks)
    }

    @Test
    fun `tries are accepted again once the wait is over`() = runBlocking {
        val limiter = AttemptLimiter(maxFailures = 2, baseLockMillis = 30_000)
        limiter.failTimes(2)

        clock += 30_000

        assertEquals(0L, limiter.remainingLockMillis(clock))
        assertEquals(AttemptResult.Success, limiter.go(true))
    }

    @Test
    fun `each wait is twice the last, up to the cap`() = runBlocking {
        val limiter = AttemptLimiter(maxFailures = 1, baseLockMillis = 30_000, maxLockMillis = 100_000)

        val waits = (1..4).map {
            val result = limiter.go(false) as AttemptResult.Wrong
            clock += result.lockedForMillis
            result.lockedForMillis
        }

        assertEquals(listOf(30_000L, 60_000L, 100_000L, 100_000L), waits)
    }

    @Test
    fun `a right answer starts over`() = runBlocking {
        val limiter = AttemptLimiter(maxFailures = 2, baseLockMillis = 30_000)
        limiter.failTimes(1)
        assertEquals(AttemptResult.Success, limiter.go(true))

        // One wrong try after a success is the first of a new run, not the second of the old one.
        assertEquals(AttemptResult.Wrong(0), limiter.go(false))
        assertTrue(limiter.remainingLockMillis(clock) == 0L)
    }

    @Test
    fun `moving the clock backwards cannot shorten a wait`() = runBlocking {
        val limiter = AttemptLimiter(maxFailures = 1, baseLockMillis = 30_000)
        limiter.go(false)

        clock -= 500

        assertEquals(30_500L, limiter.remainingLockMillis(clock))
    }
}
