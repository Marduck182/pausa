package app.pausa.domain.model

import java.time.Instant

/** A focus session started by the user: everything in [targets] is blocked until [endsAt]. */
data class FocusSession(
    val endsAt: Instant,
    val targets: Set<Target>,
)

/** A deliberate, time-limited permission the user asked for. */
data class TemporaryAllow(
    val packageName: String,
    val until: Instant,
)

/** What has been used today, per package. Built by the enforcement layer, read by the engine. */
data class UsageSnapshot(
    val usedMillisToday: Map<String, Long> = emptyMap(),
    /** Sessions that already started today, not counting the one being evaluated. */
    val sessionsStartedToday: Map<String, Int> = emptyMap(),
) {
    fun usedMillis(packageName: String): Long = usedMillisToday[packageName] ?: 0L

    fun sessions(packageName: String): Int = sessionsStartedToday[packageName] ?: 0
}

/** Everything the engine needs besides the rules themselves. */
data class EngineContext(
    val usage: UsageSnapshot = UsageSnapshot(),
    val focus: FocusSession? = null,
    /** Apps allowed through every block (WhatsApp, Maps...). */
    val exceptions: Set<String> = emptySet(),
    val temporaryAllows: List<TemporaryAllow> = emptyList(),
    /** Packages that must never be blocked: phone, emergency, settings, the launcher and Pausa itself. */
    val protectedPackages: Set<String> = emptySet(),
    /** Per package: the user already passed the intervention and may use the app until then. */
    val interventionPassUntil: Map<String, Instant> = emptyMap(),
    /** Per package: the user already drew the pattern and may use the app until then. */
    val patternPassUntil: Map<String, Instant> = emptyMap(),
    /**
     * Whether a security pattern is set. If it is not, a pattern rule asks for nothing: asking for a pattern that
     * does not exist would lock the user out of their own app.
     */
    val patternAvailable: Boolean = true,
)

/** One attempt to use [target]. [isSessionStart] is false for checks done while the app is already open. */
data class AccessRequest(
    val target: Target,
    val isSessionStart: Boolean,
)
