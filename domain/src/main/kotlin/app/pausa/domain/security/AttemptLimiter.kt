package app.pausa.domain.security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What came of one try at a PIN or pattern. */
sealed interface AttemptResult {
    data object Success : AttemptResult

    /** Wrong. [lockedForMillis] is above zero when this failure started a wait. */
    data class Wrong(val lockedForMillis: Long) : AttemptResult

    /** Not even checked: too many failures, still waiting. */
    data class Locked(val remainingMillis: Long) : AttemptResult
}

/**
 * Slows down guessing. After [maxFailures] wrong tries in a row it refuses every try for a while, and each time that
 * happens the wait doubles, up to [maxLockMillis]. A right answer starts everything over.
 *
 * It lives in one place for the whole process, not inside a dialog: closing and reopening the dialog (or the lock
 * screen over an app) must not hand out five fresh tries. Tries are handled one at a time, so two strokes drawn at
 * once cannot both be checked before the wait starts.
 *
 * The clock is given by the caller and should be a monotonic one (time since boot), so changing the phone's date
 * does not shorten a wait. The count lives in memory: it starts over if the process is killed, which is the price
 * of not writing every failure to disk. It deters impulse and curiosity, not a determined attacker.
 */
class AttemptLimiter(
    private val maxFailures: Int = 5,
    private val baseLockMillis: Long = 30_000,
    private val maxLockMillis: Long = 5 * 60_000,
) {
    private val turn = Mutex()
    private val state = Any()
    private var failures = 0
    private var lockRounds = 0
    private var lockedUntil = 0L

    init {
        require(maxFailures > 0 && baseLockMillis > 0 && maxLockMillis >= baseLockMillis)
    }

    /** How long until tries are accepted again; zero when they are accepted now. */
    fun remainingLockMillis(nowMillis: Long): Long =
        synchronized(state) { (lockedUntil - nowMillis).coerceAtLeast(0) }

    /** Runs [check] unless a wait is in progress. [nowMillis] is read inside, after waiting for any earlier try. */
    suspend fun attempt(nowMillis: () -> Long, check: suspend () -> Boolean): AttemptResult = turn.withLock {
        val now = nowMillis()
        val remaining = remainingLockMillis(now)
        if (remaining > 0) return@withLock AttemptResult.Locked(remaining)

        if (check()) {
            synchronized(state) {
                failures = 0
                lockRounds = 0
                lockedUntil = 0
            }
            AttemptResult.Success
        } else {
            synchronized(state) {
                failures++
                if (failures >= maxFailures) {
                    val wait = (baseLockMillis shl lockRounds.coerceAtMost(MAX_SHIFT)).coerceAtMost(maxLockMillis)
                    lockRounds++
                    failures = 0
                    lockedUntil = now + wait
                    AttemptResult.Wrong(wait)
                } else {
                    AttemptResult.Wrong(0)
                }
            }
        }
    }

    private companion object {
        /** Keeps the doubling from overflowing; far beyond what [maxLockMillis] allows anyway. */
        const val MAX_SHIFT = 20
    }
}
