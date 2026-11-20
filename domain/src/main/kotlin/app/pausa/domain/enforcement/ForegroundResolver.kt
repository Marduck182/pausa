package app.pausa.domain.enforcement

/** One application window on the screen, reduced to what picking the foreground app needs. */
data class AppWindow(
    /** Z-order: a higher value is drawn on top of a lower one. */
    val layer: Int,
    val packageName: String?,
    /** The window the user is interacting with right now. */
    val isActive: Boolean = false,
)

/**
 * Works out which app the user is really looking at from the windows on screen, instead of trusting the last
 * "a window changed" event. Some phones do not send that event for an app that comes back from the recent apps
 * list (Samsung does this with games that have Game Tools), so waiting for it can miss an app being opened at all.
 */
object ForegroundResolver {

    /**
     * The package of the window the user is interacting with, or else of the top-most one, ignoring the
     * [transient] ones (keyboards, the notification shade, game bars...). Null if there is none.
     */
    fun topApp(windows: List<AppWindow>, transient: Set<String>): String? = topApp(
        windows,
        layerOf = { it.layer },
        packageOf = { it.packageName },
        isActiveOf = { it.isActive },
        transient = transient,
    )

    /**
     * Same, but asks for a window's package only when it is needed, and stops at the first that counts. Asking is
     * the expensive part (on Android it is a call into the other app), so a busy app that does not matter is never
     * bothered.
     *
     * The active window wins over the top-most one on purpose: when the user goes home, the phone keeps listing
     * the app they just left on top for a moment while it animates away, but that window is no longer the active
     * one. The app that is opening, on the other hand, already is.
     */
    fun <W> topApp(
        windows: List<W>,
        layerOf: (W) -> Int,
        packageOf: (W) -> String?,
        isActiveOf: (W) -> Boolean,
        transient: Set<String>,
    ): String? {
        val topDown = windows.sortedByDescending(layerOf)
        val active = topDown.filter(isActiveOf).firstNotNullOfOrNull { packageOf(it)?.takeIf { pkg -> pkg !in transient } }
        return active ?: topDown.firstNotNullOfOrNull { packageOf(it)?.takeIf { pkg -> pkg !in transient } }
    }
}
