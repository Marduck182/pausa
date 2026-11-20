package app.pausa.domain.enforcement

/**
 * Decides whether to believe a reading of the windows on screen ("this app is on top") that disagrees with what the
 * window events said. Readings exist for the case where the phone sends no event for an app that comes back, but
 * they have their own trap: right after going home the phone still lists the old app's window while it animates
 * away, so a reading taken then names the app the user just left. Believing it would block that app again over the
 * home screen.
 *
 * Two protections: readings are ignored for a moment after the home screen shows up or after the user is sent out,
 * and a reading that disagrees is only acted on if the next one agrees too. Time is passed in, so this is testable.
 */
class ForegroundReconciler(
    private val afterHomeMillis: Long = AFTER_HOME_MILLIS,
    private val afterLeaveMillis: Long = AFTER_LEAVE_MILLIS,
) {
    sealed interface Verdict {
        /** Nothing to correct. */
        data object Nothing : Verdict

        /** The reading disagrees with what is known: read again before believing it. */
        data object ReadAgain : Verdict

        /** Two readings agree: the app on top really is not the one the events said. */
        data object Act : Verdict
    }

    private var suppressedUntil = 0L
    private var unconfirmed: String? = null

    /** A real window event arrived: events are working, so any doubt from an earlier reading is dropped. */
    fun onWindowEvent(isHomeScreen: Boolean, nowMillis: Long) {
        unconfirmed = null
        if (isHomeScreen) suppress(nowMillis + afterHomeMillis)
    }

    /** The user was sent out of an app (home, or back out of a feature) by Pausa. */
    fun onLeft(nowMillis: Long) = suppress(nowMillis + afterLeaveMillis)

    fun isSuppressed(nowMillis: Long): Boolean = nowMillis < suppressedUntil

    /**
     * [top] is the package found on top (null if none), [current] what the core believes is in front (null for the
     * home screen) and [home] the launcher packages, so that seeing the launcher means no app.
     */
    fun onReading(top: String?, home: Set<String>, current: String?, nowMillis: Long): Verdict {
        if (top == null || isSuppressed(nowMillis)) return Verdict.Nothing
        val seen = top.takeUnless { it in home }
        if (seen == current) {
            unconfirmed = null
            return Verdict.Nothing
        }
        if (unconfirmed != top) {
            unconfirmed = top
            return Verdict.ReadAgain
        }
        unconfirmed = null
        return Verdict.Act
    }

    private fun suppress(until: Long) {
        suppressedUntil = maxOf(suppressedUntil, until)
    }

    companion object {
        // Short on purpose: the resolver already prefers the active window, which is what stops the window that is
        // animating away from being mistaken for the app in front. This pause is only a second safety net, and every
        // millisecond of it is time during which an app the user just opened is not yet checked.
        const val AFTER_HOME_MILLIS = 800L
        const val AFTER_LEAVE_MILLIS = 600L
    }
}
