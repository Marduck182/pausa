package app.pausa.domain.enforcement

import app.pausa.domain.engine.RuleEngine
import app.pausa.domain.model.AccessRequest
import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.Decision
import app.pausa.domain.model.EngineContext
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.PackageUsage
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.Target
import app.pausa.domain.model.TemporaryAllow
import app.pausa.domain.model.UsageSnapshot
import app.pausa.domain.model.appliesTo
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the Android layer must do after an event. The core only decides; it never touches the screen. */
sealed interface EnforcementAction {
    data object None : EnforcementAction

    /** Cover [packageName] with the pause screen. */
    data class ShowIntervention(
        val packageName: String,
        val intervention: Restriction.Intervention,
        /** Set when the pause is for a feature inside the app rather than the app itself. */
        val featureId: String? = null,
    ) : EnforcementAction

    /** Cover [packageName] with the pattern screen: it opens only if the right pattern is drawn. */
    data class ShowPatternLock(val packageName: String) : EnforcementAction

    /** Cover [packageName] with the block screen. */
    data class ShowBlock(
        val packageName: String,
        val block: Decision.Block,
        /** Set when only a feature is blocked: the app stays open and the user is sent back, not home. */
        val featureId: String? = null,
    ) : EnforcementAction
}

/** Which packages are not real "apps the user switched to". Resolved on the phone, handed in as plain sets. */
data class SystemPackageSets(
    /** Launchers: being on the home screen means no app is in use. */
    val home: Set<String> = emptySet(),
    /** Windows that appear over an app without leaving it: notification shade, keyboards, permission dialogs. */
    val transient: Set<String> = emptySet(),
    /** Never blocked, whatever the rules say: phone, settings, the launcher and Pausa itself. */
    val protectedPackages: Set<String> = emptySet(),
)

/**
 * The decision loop of the guard, free of Android: it is told which window came to the front (and when time
 * passes, and when the screen goes off) and answers with an [EnforcementAction]. Keeping it pure means the
 * trickiest behaviour (what counts as opening an app, when a pause is skipped, when a day's limit is reached) is
 * covered by fast JVM tests.
 *
 * It also measures use, but only for apps that have an enabled rule: an app nobody asked Pausa to watch is never
 * timed or counted. A session is the time an app is in front with the screen on, and starts only once the user
 * is actually allowed in (after a pause is accepted), so a pause the user walks away from costs no time.
 */
class EnforcementCore(
    private val engine: RuleEngine = RuleEngine(),
    private val interventionGrace: Duration = DEFAULT_GRACE,
    private val patternGrace: Duration = DEFAULT_PATTERN_GRACE,
) {
    @Volatile
    var rules: List<Rule> = emptyList()

    @Volatile
    var systemPackages: SystemPackageSets = SystemPackageSets()

    /**
     * Whether a security pattern is set. Until the service says so, pattern rules ask for nothing: a pattern that
     * cannot be checked must never lock the user out of their own app.
     */
    @Volatile
    var patternAvailable: Boolean = false

    /** Apps that pass every block. */
    @Volatile
    var exceptions: Set<String> = emptySet()

    /** Passes the user asked for on purpose. Expired ones are ignored by the engine. */
    @Volatile
    var temporaryAllows: List<TemporaryAllow> = emptyList()

    /** The focus session in progress, if any: everything it covers is blocked until it ends. */
    @Volatile
    var focus: FocusSession? = null

    /**
     * While false, events are ignored. The service turns it off until today's stored usage is loaded, so a
     * restart in the middle of the day cannot make limits start again from zero for a moment.
     */
    @Volatile
    var acceptingEvents: Boolean = true

    /** Called for every finished session, to be stored. Never called with a session across midnight. */
    var sessionListener: ((CompletedSession) -> Unit)? = null

    /** The app the user is in right now, or null on the home screen / while the screen is off. */
    var currentPackage: String? = null
        private set

    /** The feature the user is inside right now (Shorts, Reels), or null. */
    var currentFeature: String? = null
        private set

    /** True when the last [onTick] found that a temporary pass on the current app ended (by time or by being cleared). */
    var allowEndedOnLastTick: Boolean = false
        private set

    /** The pass seen on the current app at the last tick, to notice when it is gone. */
    private var watchedAllow: Pair<String, Instant>? = null

    private val interventionPassUntil = mutableMapOf<String, Instant>()
    private val patternPassUntil = mutableMapOf<String, Instant>()

    private var baseDay: LocalDate? = null
    private val baseUsedMillis = mutableMapOf<String, Long>()
    private val baseSessions = mutableMapOf<String, Int>()
    private var inFlight: InFlight? = null

    private data class InFlight(val packageName: String, val start: Instant)

    /** The app being timed right now and since when, or null. The guard writes it down now and then (see SessionCheckpoint). */
    val activeSession: Pair<String, Instant>?
        get() = inFlight?.let { it.packageName to it.start }

    /** Loads what was already used earlier today (before a restart). Also lets events through again. */
    fun restoreUsage(day: LocalDate, totals: Map<String, PackageUsage>) {
        if (baseDay == null || baseDay == day) {
            baseDay = day
            totals.forEach { (pkg, usage) ->
                baseUsedMillis.merge(pkg, usage.usedMillis, Long::plus)
                baseSessions.merge(pkg, usage.sessions, Int::plus)
            }
        }
        acceptingEvents = true
    }

    /** A window of [packageName] came to the front. Opening an app (not switching inside it) starts a session. */
    fun onWindowChanged(packageName: String, now: ZonedDateTime): EnforcementAction {
        if (!acceptingEvents) return EnforcementAction.None
        rollOver(now)
        if (packageName in systemPackages.transient) return EnforcementAction.None
        val app = if (packageName in systemPackages.home) null else packageName
        if (app == currentPackage) return EnforcementAction.None
        endSession(now)
        currentPackage = app
        currentFeature = null
        return if (app == null) EnforcementAction.None else evaluate(app, isSessionStart = true, now)
    }

    /** Time passed with the user still in the same app: a schedule or a limit may have been reached. */
    fun onTick(now: ZonedDateTime): EnforcementAction {
        if (!acceptingEvents) return EnforcementAction.None
        rollOver(now)
        val app = currentPackage ?: return EnforcementAction.None
        val allowEnded = noticeAllowEnd(app, now)
        val action = evaluate(app, isSessionStart = false, now)
        if (!allowEnded || action != EnforcementAction.None) return action
        // A pass or break that ended while the user stayed in the app: what it was skipping applies again, as if the
        // app had just been opened. A section (Shorts) is looked at again; a pause is asked for again.
        currentFeature = null
        val decision = decide(Target.App(app), isSessionStart = true, now)
        return if (decision is Decision.Intervene) EnforcementAction.ShowIntervention(app, decision.intervention) else EnforcementAction.None
    }

    /** Whether the temporary pass the current app had at the last look is gone now. Also remembers the pass it sees. */
    private fun noticeAllowEnd(app: String, now: ZonedDateTime): Boolean {
        val instant = now.toInstant()
        val active = temporaryAllows.filter { it.packageName == app && it.until > instant }.maxOfOrNull { it.until }
        val before = watchedAllow
        watchedAllow = active?.let { app to it }
        val ended = before != null && before.first == app && active == null
        allowEndedOnLastTick = ended
        return ended
    }

    /** The screen went off: the session ends, and coming back to the same app counts as a new one. */
    fun onScreenOff(now: ZonedDateTime) {
        rollOver(now)
        endSession(now)
        currentPackage = null
        currentFeature = null
    }

    /** Treats the next sighting of the current feature as a new entry, e.g. after the user was sent back out of it. */
    fun forgetFeature() {
        currentFeature = null
    }

    /** Whether any active rule is about a feature of [packageName], so the screen is worth reading. */
    fun hasFeatureRules(packageName: String): Boolean =
        rules.any { it.enabled && it.target is Target.Feature && it.target.packageName == packageName } ||
            focus?.targets.orEmpty().any { it is Target.Feature && it.packageName == packageName }

    /**
     * The screen of [packageName] now shows [featureId] (or none). Entering a feature is checked like opening an
     * app: it may be blocked or ask for a pause. Staying in it, or leaving it, does nothing; leaving and coming
     * back is a new entry.
     */
    fun onFeatureDetected(packageName: String, featureId: String?, now: ZonedDateTime): EnforcementAction {
        if (!acceptingEvents || packageName != currentPackage) return EnforcementAction.None
        if (featureId == currentFeature) return EnforcementAction.None
        currentFeature = featureId
        if (featureId == null) return EnforcementAction.None
        return when (val decision = decide(Target.Feature(packageName, featureId), isSessionStart = true, now)) {
            Decision.Allow, is Decision.RequirePattern -> EnforcementAction.None
            is Decision.Intervene -> EnforcementAction.ShowIntervention(packageName, decision.intervention, featureId)
            // Only the feature is blocked, so the app itself keeps running and is not forgotten.
            is Decision.Block -> EnforcementAction.ShowBlock(packageName, decision, featureId)
        }
    }

    /**
     * The user chose to continue past the pause. For a while the same app does not ask again, so unlocking the
     * phone or answering a message does not bring the pause back every time. Use is counted from this moment.
     */
    fun onInterventionPassed(packageName: String, now: ZonedDateTime) {
        rollOver(now)
        interventionPassUntil[packageName] = now.toInstant().plus(interventionGrace)
        if (currentPackage == packageName && inFlight == null) beginSession(packageName, now)
    }

    /**
     * The user drew the right pattern. For a short while the same app does not ask again, so answering a message
     * and coming back is not a second lock. Whatever is left to check (a pause, say) is answered now.
     */
    fun onPatternPassed(packageName: String, now: ZonedDateTime): EnforcementAction {
        rollOver(now)
        patternPassUntil[packageName] = now.toInstant().plus(patternGrace)
        return if (currentPackage == packageName) evaluate(packageName, isSessionStart = true, now) else EnforcementAction.None
    }

    /**
     * How long to wait before the next [onTick]: the usual interval, shortened when a daily limit would be
     * reached sooner, so the block lands on the minute instead of up to one interval late.
     */
    fun nextCheckDelayMillis(now: ZonedDateTime): Long {
        val app = currentPackage ?: return TICK_MILLIS
        val used = usageSnapshot(now).usedMillis(app)
        val target = Target.App(app)
        var delay = TICK_MILLIS
        // A pass that runs out is noticed on the second it does, not up to one interval late.
        for (allow in temporaryAllows) {
            if (allow.packageName != app) continue
            val remaining = Duration.between(now.toInstant(), allow.until).toMillis()
            if (remaining > 0) delay = minOf(delay, remaining + LIMIT_MARGIN_MILLIS)
        }
        for (rule in rules) {
            if (!rule.enabled || !rule.target.appliesTo(target)) continue
            for (restriction in rule.restrictions) {
                if (restriction !is Restriction.DailyLimit) continue
                val remaining = restriction.minutes * 60_000L - used
                if (remaining > 0) delay = minOf(delay, remaining + LIMIT_MARGIN_MILLIS)
            }
        }
        return delay.coerceAtLeast(MIN_DELAY_MILLIS)
    }

    private fun decide(target: Target, isSessionStart: Boolean, now: ZonedDateTime): Decision {
        val context = EngineContext(
            usage = usageSnapshot(now),
            focus = focus,
            exceptions = exceptions,
            temporaryAllows = temporaryAllows,
            protectedPackages = systemPackages.protectedPackages,
            interventionPassUntil = interventionPassUntil,
            patternPassUntil = patternPassUntil,
            patternAvailable = patternAvailable,
        )
        return engine.evaluate(AccessRequest(target, isSessionStart), rules, context, now)
    }

    private fun evaluate(packageName: String, isSessionStart: Boolean, now: ZonedDateTime): EnforcementAction {
        return when (val decision = decide(Target.App(packageName), isSessionStart, now)) {
            Decision.Allow -> {
                if (isSessionStart) beginSession(packageName, now)
                EnforcementAction.None
            }
            // Not counted yet: the session begins once the user is let in.
            is Decision.RequirePattern -> EnforcementAction.ShowPatternLock(packageName)
            is Decision.Intervene -> EnforcementAction.ShowIntervention(packageName, decision.intervention)
            is Decision.Block -> {
                // The screen the user sees is the block screen, not the app: stop timing it, and forget it so one
                // block is not repeated on every tick. Opening it again starts a fresh, checked session.
                endSession(now)
                currentPackage = null
                EnforcementAction.ShowBlock(packageName, decision)
            }
        }
    }

    private fun isWatched(packageName: String): Boolean =
        rules.any { it.enabled && it.target is Target.App && it.target.packageName == packageName }

    private fun beginSession(packageName: String, now: ZonedDateTime) {
        if (!isWatched(packageName)) return
        inFlight = InFlight(packageName, now.toInstant())
        baseSessions.merge(packageName, 1, Int::plus)
    }

    private fun endSession(now: ZonedDateTime) {
        val flight = inFlight ?: return
        inFlight = null
        val end = now.toInstant()
        if (!end.isAfter(flight.start)) return
        baseUsedMillis.merge(flight.packageName, Duration.between(flight.start, end).toMillis(), Long::plus)
        emit(flight.packageName, flight.start, end, now.zone)
    }

    /** At midnight a new day starts from zero; a session still open is cut at midnight and continues. */
    private fun rollOver(now: ZonedDateTime) {
        val day = now.toLocalDate()
        val known = baseDay
        if (known == null) {
            baseDay = day
            return
        }
        if (day == known) return
        val midnight = day.atStartOfDay(now.zone).toInstant()
        inFlight?.let { flight ->
            emit(flight.packageName, flight.start, midnight, now.zone)
            // The same stretch of use goes on; it is not a new opening, so the session count is not touched.
            inFlight = InFlight(flight.packageName, midnight)
        }
        baseUsedMillis.clear()
        baseSessions.clear()
        baseDay = day
    }

    private fun usageSnapshot(now: ZonedDateTime): UsageSnapshot {
        val used = baseUsedMillis.toMutableMap()
        inFlight?.let { flight ->
            val elapsed = Duration.between(flight.start, now.toInstant()).toMillis().coerceAtLeast(0)
            used.merge(flight.packageName, elapsed, Long::plus)
        }
        return UsageSnapshot(usedMillisToday = used, sessionsStartedToday = baseSessions.toMap())
    }

    /** Reports a finished stretch, split so that no piece crosses midnight. */
    private fun emit(packageName: String, start: Instant, end: Instant, zone: ZoneId) {
        var from = start
        while (from < end) {
            val nextMidnight = from.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            val to = minOf(end, nextMidnight)
            if (to > from) sessionListener?.invoke(CompletedSession(packageName, from, to))
            from = to
        }
    }

    companion object {
        val DEFAULT_GRACE: Duration = Duration.ofMinutes(5)
        val DEFAULT_PATTERN_GRACE: Duration = Duration.ofMinutes(2)
        const val TICK_MILLIS = 15_000L
        private const val LIMIT_MARGIN_MILLIS = 200L
        private const val MIN_DELAY_MILLIS = 500L
    }
}
