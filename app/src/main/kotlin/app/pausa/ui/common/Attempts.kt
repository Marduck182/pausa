package app.pausa.ui.common

import android.os.SystemClock
import app.pausa.domain.security.AttemptLimiter

/**
 * The tries at the PIN and at the pattern, counted for the whole process. They are not kept inside a dialog or a
 * screen: those are rebuilt all the time, and a counter that is rebuilt with them can be reset by closing and
 * reopening. Time since boot is used so changing the phone's clock does not shorten a wait.
 */
object Attempts {
    val pin = AttemptLimiter()
    val pattern = AttemptLimiter()

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Whole seconds to show for a wait, rounded up so "0 s" never appears while it still holds. */
    fun seconds(millis: Long): Int = ((millis + 999) / 1000).toInt()
}
