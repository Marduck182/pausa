package app.pausa.enforcement

import app.pausa.domain.model.BlockExit
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import app.pausa.domain.enforcement.EnforcementAction
import app.pausa.domain.enforcement.EnforcementCore
import app.pausa.domain.enforcement.ForegroundReconciler
import app.pausa.BuildConfig
import app.pausa.domain.enforcement.ForegroundResolver
import app.pausa.domain.model.AppSettings
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.FeatureCatalog
import app.pausa.domain.model.FeatureMatcher
import app.pausa.domain.model.FeatureSignatures
import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.InterventionOutcome
import app.pausa.domain.model.NodeSnapshot
import app.pausa.domain.model.SessionCheckpoint
import app.pausa.domain.model.SessionRecovery
import app.pausa.domain.repository.RuleRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.repository.StatsRepository
import app.pausa.domain.repository.UsageRepository
import app.pausa.ui.common.BlockText
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pausa's eyes and hands. It listens to "a window came to the front", hands that to [EnforcementCore] (which
 * decides, without touching Android) and turns the answer into a screen over the app. Nothing it sees is stored:
 * the only memory is which app is in front right now, plus finished sessions and the user's own reactions.
 *
 * Rules are loaded into memory when the service connects and kept in sync; until stored usage is loaded nothing
 * is enforced, which fails open on purpose so a slow start never locks the phone.
 *
 * Inside an app that has a feature rule (Shorts, Reels) it also reads the structure of the screen, only while
 * that app is in front: view ids and the name of the selected tab, never text, messages or content.
 */
@AndroidEntryPoint
class GuardAccessibilityService : AccessibilityService() {

    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var statsRepository: StatsRepository
    @Inject lateinit var systemPackages: SystemPackages
    @Inject lateinit var clock: Clock

    private val core = EnforcementCore()
    // An exception nobody caught would end the whole process, and with it the guard. It is logged instead; the jobs
    // that must keep running (below) also retry on their own.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Unexpected failure in the guard", e) },
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayController

    @Volatile private var appSettings = AppSettings()
    private var watchingContent = false
    private var scanScheduled = false
    private var reconcileScheduled = false
    private val reconcileReading = AtomicBoolean(false)
    private var lastCheckpointAt = 0L
    private var lastSystemPackagesAt = SystemClock.elapsedRealtime()
    private var checkpointSaved = false

    /** When to believe a reading of the windows (see its notes: the old app lingers on screen after going home). */
    private val reconciler = ForegroundReconciler()

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            core.onScreenOff(now())
            overlay.hide()
            updateContentWatching()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = OverlayController(this)
        core.systemPackages = systemPackages.resolve()

        // Nothing is enforced until today's stored usage is loaded: after a restart in the middle of the day, limits
        // must continue from what was already used, not start from zero.
        core.acceptingEvents = false
        core.sessionListener = ::storeSession
        scope.launch {
            // Everything the decision depends on is read before anything is enforced: with a focus session running or
            // an exception set, the first window event must already know about them. If storage fails the read is
            // retried, because giving up would leave the guard switched on but never enforcing.
            retrying("initial load") {
                core.rules = ruleRepository.getRules()
                val settings = settingsRepository.settings.first()
                appSettings = settings
                core.exceptions = settings.exceptions
                core.patternAvailable = settings.patternEnabled
                core.temporaryAllows = settingsRepository.temporaryAllows.first()
                core.focus = settingsRepository.focus.first()
                recoverInterruptedSession()
                val today = LocalDate.now(clock)
                core.restoreUsage(today, usageRepository.totalsForDay(today.toEpochDay()))
            }

            // Housekeeping must never hold up enforcement or take it down with it.
            launch {
                try {
                    val cutoff = LocalDate.now(clock)
                    usageRepository.purgeBefore(cutoff.minusDays(RETENTION_DAYS).toEpochDay())
                    statsRepository.purgeBefore(cutoff.minusDays(EVENT_RETENTION_DAYS).toEpochDay())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not purge old sessions", e)
                }
            }

            launch {
                keepCollecting("rules", ruleRepository.observeRules()) {
                    core.rules = it
                    updateContentWatching()
                }
            }
            launch {
                keepCollecting("settings", settingsRepository.settings) {
                    appSettings = it
                    core.exceptions = it.exceptions
                    core.patternAvailable = it.patternEnabled
                }
            }
            launch { keepCollecting("temporary passes", settingsRepository.temporaryAllows) { core.temporaryAllows = it } }
            launch {
                keepCollecting("focus", settingsRepository.focus) { focus ->
                    core.focus = focus
                    updateContentWatching()
                    // A focus session that starts now should not wait for the next check to take effect.
                    if (!overlay.isShowing) handle(core.onTick(now()))
                }
            }

            // A schedule can start, or a daily limit run out, while an app is open. The wait shrinks as a limit
            // gets close, so the block lands on the minute. One failed check must not end the loop.
            while (isActive) {
                try {
                    delay(core.nextCheckDelayMillis(now()))
                    if (!overlay.isShowing) {
                        reconcileForeground()
                        handle(core.onTick(now()))
                    }
                    refreshSystemPackagesIfDue()
                    writeCheckpointIfDue()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic check failed", e)
                    delay(RETRY_MILLIS)
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        LabLog.add("Servicio conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // No package on this one: it only says the set of windows changed, so look at what is on top.
            if (!overlay.isShowing) requestReconcile()
            return
        }
        val packageName = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Only delivered while an app with a feature rule is in front (see updateContentWatching).
            if (packageName == core.currentPackage && !overlay.isShowing) requestFeatureScan(packageName)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (BuildConfig.DEBUG) logForLab(event, packageName)
        reconciler.onWindowEvent(packageName in core.systemPackages.home, SystemClock.uptimeMillis())

        if (packageName in LabConfig.blockedPackages.value) {
            showBlock(packageName, "Prueba del laboratorio: esta app está bloqueada.", featureId = null)
            return
        }

        if (overlay.isShowing) {
            // Our own window and the covered app keep producing events; they must not close the overlay.
            if (packageName == this.packageName || packageName == overlay.shownFor) return
            if (packageName in core.systemPackages.transient) return
            // The user left (home gesture, another app): a pause or block for the old app no longer applies.
            overlay.hide()
        }
        handle(core.onWindowChanged(packageName, now()))
        updateContentWatching()
        if (core.hasFeatureRules(packageName)) requestFeatureScan(packageName)
        // A floating window (game bar, shade) can be the only event when an app comes back: check what is below it.
        if (packageName in core.systemPackages.transient) requestReconcile()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(screenOffReceiver) }
        overlay.hide()
        mainHandler.removeCallbacksAndMessages(null)
        // Close the session in progress so the time already used is stored, then stop.
        core.onScreenOff(now())
        scope.cancel()
        if (instance === this) instance = null
        LabLog.add("Servicio desconectado")
        return super.onUnbind(intent)
    }

    private fun now(): ZonedDateTime = ZonedDateTime.now(clock)

    // --- Not dying quietly ---------------------------------------------------------------------------------------

    /** Runs [block] until it succeeds, waiting between attempts. Cancellation (the service stopping) is let through. */
    private suspend fun retrying(what: String, block: suspend () -> Unit) {
        while (true) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed: $what; trying again", e)
                LabLog.add("Falló: $what; se reintenta")
                delay(RETRY_MILLIS)
            }
        }
    }

    /**
     * Applies every value of [flow] and, if the flow or the handler fails, starts it again after a pause. A single
     * bad emission must not leave the guard working with stale rules for the rest of the day.
     */
    private suspend fun <T> keepCollecting(what: String, flow: Flow<T>, apply: (T) -> Unit) {
        while (true) {
            try {
                flow.collect { apply(it) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed while following $what; following again", e)
                LabLog.add("Falló al seguir $what; se reintenta")
                delay(RETRY_MILLIS)
            }
        }
    }

    /**
     * Shows a screen over the app. If the system refuses (it can, for instance while the screen is being
     * rebuilt), the person is taken home instead: a block that does not appear must still keep the app closed.
     */
    private fun present(packageName: String, onBack: () -> Unit, content: @Composable () -> Unit): Boolean {
        val shown = overlay.show(packageName, onBack, content)
        if (!shown) {
            Log.w(TAG, "Overlay for $packageName could not be shown; going home instead")
            goHome()
        }
        return shown
    }

    // --- Acting on decisions -----------------------------------------------------------------------------------

    private fun handle(action: EnforcementAction) {
        when (action) {
            EnforcementAction.None -> Unit
            is EnforcementAction.ShowIntervention -> showIntervention(action)
            is EnforcementAction.ShowPatternLock -> showPatternLock(action.packageName)
            is EnforcementAction.ShowBlock -> {
                val shown = showBlock(
                    packageName = action.packageName,
                    message = BlockText.describe(action.block, now(), action.featureId),
                    featureId = action.featureId,
                )
                // Only a block the person actually saw counts in the statistics.
                if (shown) record(action.packageName, InterventionOutcome.BLOCKED)
            }
        }
    }

    /** Covers the app with the pattern screen. The right pattern lets the user in; backing out sends them home. */
    private fun showPatternLock(packageName: String) {
        val cancel = {
            record(packageName, InterventionOutcome.CANCELLED)
            leave(featureId = null)
        }
        present(packageName, onBack = cancel) {
            PatternLockContent(
                packageName = packageName,
                appLabel = labelFor(packageName, null),
                verify = settingsRepository::verifyPattern,
                onUnlocked = {
                    overlay.hide()
                    // Whatever is left to check (a pause, for example) is answered now.
                    handle(core.onPatternPassed(packageName, now()))
                },
                onCancel = cancel,
                themeMode = appSettings.themeMode,
            )
        }
    }

    private fun showIntervention(action: EnforcementAction.ShowIntervention) {
        val packageName = action.packageName
        present(packageName, onBack = { cancelPause(action) }) {
            InterventionContent(
                packageName = packageName,
                appLabel = labelFor(packageName, action.featureId),
                waitSeconds = action.intervention.waitSeconds,
                breathing = action.intervention.breathing,
                onCancel = { cancelPause(action) },
                onContinue = {
                    record(packageName, InterventionOutcome.CONTINUED)
                    core.onInterventionPassed(packageName, now())
                    overlay.hide()
                },
                themeMode = appSettings.themeMode,
            )
        }
    }

    private fun cancelPause(action: EnforcementAction.ShowIntervention) {
        record(action.packageName, InterventionOutcome.CANCELLED)
        leave(action.featureId)
    }

    private fun showBlock(packageName: String, message: String, featureId: String?): Boolean {
        val dismiss = { leave(featureId) }
        return present(packageName, onBack = dismiss) {
            BlockContent(
                packageName = packageName,
                title = if (featureId == null) "${labelFor(packageName, null)} está bloqueada" else "${labelFor(packageName, featureId)}: bloqueado",
                message = message,
                dismissLabel = if (featureId == null) "Volver al inicio" else "Ir a Inicio",
                onDismiss = dismiss,
                // A pass for one app from here is gone: the way to rest is a break, chosen in advance on the home screen.
                temporaryAllowMinutes = null,
                waitSeconds = 0,
                onAllowTemporarily = {},
                themeMode = appSettings.themeMode,
            )
        }
    }

    /**
     * Closes whatever Pausa is showing and takes the user out: to the phone's home screen for a whole app, or to
     * the app's own Home tab for a feature (the little house of YouTube or Instagram), with Back as the fallback.
     * After a feature block the screen is read again shortly, so if the user is still inside the feature it is
     * blocked again instead of letting them stay.
     */
    private fun leave(featureId: String?) {
        reconciler.onLeft(SystemClock.uptimeMillis())
        // First, so the app is the active window again: only then can its screen be read and its tab pressed.
        overlay.hide()
        if (featureId == null) {
            exitApp()
            return
        }
        val packageName = core.currentPackage
        scope.launch {
            delay(FOCUS_RETURN_MILLIS)
            val moved = packageName != null && withContext(Dispatchers.Default) { clickHomeTab(packageName, featureId) }
            if (!moved) performGlobalAction(GLOBAL_ACTION_BACK)
            LabLog.add(if (moved) "Salida de $featureId: pestaña Inicio" else "Salida de $featureId: Atrás (no se halló la pestaña)")
            core.forgetFeature()
            delay(FEATURE_RECHECK_MILLIS)
            core.currentPackage?.let(::requestFeatureScan)
        }
    }

    /**
     * Presses the first tab of the app's bottom bar, which in YouTube and Instagram is Home. The bar is found from
     * the selected tab that carries the feature's name ("Shorts"): its first sibling is Home whatever the language.
     * Returns false when there is no such tab (the feature was opened from the feed, not from its tab) or the
     * press did nothing, and the caller falls back to Back.
     */
    private fun clickHomeTab(packageName: String, featureId: String): Boolean {
        val labels = FeatureSignatures.all
            .filter { it.packageName == packageName && it.featureId == featureId }
            .flatMap { it.selectedLabels }
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != packageName) return false
        val selected = firstNode(root) { node ->
            node.isSelected && node.contentDescription?.toString()?.let { text -> labels.any { it.equals(text, ignoreCase = true) } } == true
        } ?: return false

        // Climb until the parent has several children: that is the bar. `branch` is the tab that holds the selected one.
        var branch = selected
        var bar = selected.parent
        while (bar != null && bar.childCount < MIN_TABS) {
            branch = bar
            bar = bar.parent
        }
        val home = bar?.getChild(0) ?: return false
        if (home == branch) return false
        return firstNode(home) { it.isClickable }?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    /** The first node, breadth first, that satisfies [matches]. Bounded, so a huge screen cannot stall it. */
    private fun firstNode(start: AccessibilityNodeInfo, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(start) }
        var visited = 0
        while (pending.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val node = pending.removeFirst()
            visited++
            if (matches(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(pending::addLast)
        }
        return null
    }

    private fun labelFor(packageName: String, featureId: String?): String {
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        return if (featureId == null) app else "${FeatureCatalog.displayName(featureId)} de $app"
    }

    /**
     * Takes the person out of a blocked app: to the home screen, or (if they chose it in Settings) only one step back.
     * Back is not enough on its own: from a screen deep inside the app it only moves to the previous screen and the app
     * stays usable, so a moment later, if the same app is still in front, the person is sent home after all.
     */
    private fun exitApp() {
        val packageName = core.currentPackage
        if (appSettings.blockExit != BlockExit.BACK || packageName == null) {
            goHome()
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
        scope.launch {
            delay(BACK_CHECK_MILLIS)
            val stillThere = withContext(Dispatchers.Default) { rootInActiveWindow?.packageName?.toString() == packageName }
            if (stillThere) {
                LabLog.add("Atrás no cerró $packageName: se va al inicio")
                goHome()
            }
        }
    }

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Lab only: shows the block screen over whatever is open. */
    fun showOverlay(title: String, message: String) {
        overlay.show(packageName, onBack = overlay::hide) {
            BlockContent(
                packageName = packageName,
                title = title,
                message = message,
                dismissLabel = "Entendido",
                onDismiss = overlay::hide,
                temporaryAllowMinutes = null,
                waitSeconds = 0,
                onAllowTemporarily = {},
                themeMode = appSettings.themeMode,
            )
        }
    }

    // --- Storing ------------------------------------------------------------------------------------------------

    /** Stores one finished session. It survives the service being stopped right after: it is the last write. */
    private fun storeSession(session: CompletedSession) {
        val epochDay = session.start.atZone(clock.zone).toLocalDate().toEpochDay()
        scope.launch {
            withContext(NonCancellable) {
                usageRepository.record(session, epochDay)
                // Now stored for real: the note about it would only count it a second time.
                if (checkpointSaved) {
                    checkpointSaved = false
                    runCatching { settingsRepository.saveCheckpoint(null) }
                }
            }
        }
    }

    // --- Surviving being killed ----------------------------------------------------------------------------------

    /** A note found at start means the last process died with a session open: its time is stored now. */
    private suspend fun recoverInterruptedSession() {
        val note = settingsRepository.checkpoint() ?: return
        val pieces = SessionRecovery.recover(note, clock.zone)
        pieces.forEach { piece ->
            usageRepository.record(piece, piece.start.atZone(clock.zone).toLocalDate().toEpochDay())
        }
        settingsRepository.saveCheckpoint(null)
        Log.i(TAG, "Recovered ${pieces.size} piece(s) of a session cut short for ${note.packageName}")
    }

    /**
     * Which apps are the launcher, the keyboard or the phone can change while the guard runs (a new launcher or
     * keyboard is installed). Read again now and then, off the main thread, so those apps are never mistaken for
     * ones to control.
     */
    private suspend fun refreshSystemPackagesIfDue() {
        val tick = SystemClock.elapsedRealtime()
        if (tick - lastSystemPackagesAt < SYSTEM_PACKAGES_REFRESH_MILLIS) return
        lastSystemPackagesAt = tick
        core.systemPackages = withContext(Dispatchers.Default) { systemPackages.resolve() }
    }

    /** At most every half minute: often enough to lose little, rarely enough not to wear the storage. */
    private fun writeCheckpointIfDue() {
        val tick = SystemClock.elapsedRealtime()
        if (tick - lastCheckpointAt < CHECKPOINT_MILLIS) return
        lastCheckpointAt = tick
        val active = core.activeSession
        if (active == null && !checkpointSaved) return
        val note = active?.let { (pkg, start) -> SessionCheckpoint(pkg, start.toEpochMilli(), clock.millis()) }
        scope.launch {
            try {
                settingsRepository.saveCheckpoint(note)
                checkpointSaved = note != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not write the session note", e)
            }
        }
    }

    /** Remembers what the user did in front of a restriction: a package, a time and a short code. */
    private fun record(packageName: String, outcome: InterventionOutcome) {
        val at = now().toInstant()
        val event = InterventionEvent(at, packageName, outcome)
        val epochDay = at.atZone(clock.zone).toLocalDate().toEpochDay()
        scope.launch { withContext(NonCancellable) { statsRepository.record(event, epochDay) } }
    }

    // --- Knowing which app is really in front ---------------------------------------------------------------------

    /** Reads the windows shortly after a burst of changes, at most once per burst. */
    private fun requestReconcile(delayMillis: Long = RECONCILE_DELAY_MILLIS) {
        if (reconcileScheduled) return
        reconcileScheduled = true
        mainHandler.postDelayed({
            reconcileScheduled = false
            reconcileForeground()
        }, delayMillis)
    }

    /**
     * Window events are not enough on their own: coming back to a game from the recent apps list on a Samsung
     * sends an event for Game Tools and none for the game, so the game would never be checked. Asking which app
     * owns the top window finds it. Reporting an app that is already known does nothing.
     */
    private fun reconcileForeground() {
        if (overlay.isShowing || reconciler.isSuppressed(SystemClock.uptimeMillis())) return
        if (!reconcileReading.compareAndSet(false, true)) return
        val transient = core.systemPackages.transient
        scope.launch {
            // Asking a window for its owner is a call into the other app. If that app is busy (a game, say) it can
            // take seconds, so it never runs on the main thread: this thread also draws the overlay.
            val reading = try {
                withContext(Dispatchers.Default) { readTopApplication(transient) }
            } finally {
                reconcileReading.set(false)
            }
            applyReconciled(reading.packageName, reading.summary)
        }
    }

    /** The package found on top, plus (debug only) a line describing the windows it was chosen from. */
    private class Reading(val packageName: String?, val summary: String)

    private fun readTopApplication(transient: Set<String>): Reading {
        val appWindows = windows.orEmpty().filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val owners = mutableMapOf<AccessibilityWindowInfo, String?>()
        val top = ForegroundResolver.topApp(
            windows = appWindows,
            layerOf = { it.layer },
            packageOf = { window -> owners.getOrPut(window) { window.root?.packageName?.toString() } },
            isActiveOf = { it.isActive },
            transient = transient,
        )
        val summary = if (BuildConfig.DEBUG) {
            appWindows.sortedByDescending { it.layer }.joinToString(" | ") { window ->
                "capa ${window.layer} ${if (window.isActive) "ACTIVA " else ""}${owners[window]?.substringAfterLast('.') ?: "?"}"
            }
        } else {
            ""
        }
        return Reading(top, summary)
    }

    private fun applyReconciled(top: String?, summary: String) {
        if (top == null || overlay.isShowing) return
        when (reconciler.onReading(top, core.systemPackages.home, core.currentPackage, SystemClock.uptimeMillis())) {
            ForegroundReconciler.Verdict.Nothing -> return
            // One reading can catch a window in the middle of an animation: read again before believing it.
            ForegroundReconciler.Verdict.ReadAgain -> {
                LabLog.add("Lectura dudosa: $top  [$summary]")
                requestReconcile(RECONCILE_CONFIRM_MILLIS)
                return
            }
            ForegroundReconciler.Verdict.Act -> Unit
        }
        LabLog.add("Primer plano corregido: $top (no llegó evento de ventana)  [$summary]")
        handle(core.onWindowChanged(top, now()))
        updateContentWatching()
        if (core.hasFeatureRules(top)) requestFeatureScan(top)
    }

    // --- Features (Shorts, Reels) ---------------------------------------------------------------------------------

    /**
     * Listens to content changes only while the app in front has a feature rule: they are frequent, and there is
     * nothing to look for anywhere else. Android lets a service change this at runtime.
     */
    private fun updateContentWatching() {
        if (!::overlay.isInitialized) return
        val wanted = core.currentPackage?.let(core::hasFeatureRules) == true
        if (wanted == watchingContent) return
        watchingContent = wanted
        val info = serviceInfo ?: return
        val base = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.eventTypes = if (wanted) base or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else base
        serviceInfo = info
    }

    /** Reads the screen at most every few hundred milliseconds, however many change events arrive. */
    private fun requestFeatureScan(packageName: String) {
        if (scanScheduled || !core.hasFeatureRules(packageName)) return
        scanScheduled = true
        mainHandler.postDelayed({
            scanScheduled = false
            scanForFeature(packageName)
        }, SCAN_THROTTLE_MILLIS)
    }

    private fun scanForFeature(packageName: String) {
        if (overlay.isShowing || packageName != core.currentPackage) return
        scope.launch {
            // Walking the screen is one call into the app per element, up to several hundred: off the main thread.
            val scan = withContext(Dispatchers.Default) {
                val root = rootInActiveWindow
                // The active window may be the notification shade or a dialog from another app: only read the app itself.
                if (root == null || root.packageName?.toString() != packageName) {
                    null
                } else {
                    FeatureScan(FeatureMatcher.detect(packageName, nodesOf(root), FeatureSignatures.all))
                }
            }
            // The screen may have changed while it was being read.
            if (scan == null || overlay.isShowing || packageName != core.currentPackage) return@launch
            if (scan.featureId != core.currentFeature) {
                LabLog.add("Función detectada en $packageName: ${scan.featureId ?: "ninguna"}")
            }
            handle(core.onFeatureDetected(packageName, scan.featureId, now()))
        }
    }

    /** What a reading of the screen found: a feature id, or none. A failed reading is null instead. */
    private class FeatureScan(val featureId: String?)

    /** The window as a lazy sequence of the little that recognising a feature needs. No text is read. */
    private fun nodesOf(root: AccessibilityNodeInfo): Sequence<NodeSnapshot> = sequence {
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (pending.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val node = pending.removeFirst()
            visited++
            val label = node.contentDescription?.toString()?.takeIf { node.isSelected && it.length <= MAX_LABEL }
            yield(NodeSnapshot(node.viewIdResourceName, node.isSelected, label))
            for (i in 0 until node.childCount) node.getChild(i)?.let(pending::addLast)
        }
    }

    // --- Technical lab ----------------------------------------------------------------------------------------

    private fun logForLab(event: AccessibilityEvent, packageName: String) {
        val screen = event.className?.toString().orEmpty().substringAfterLast('.')
        val delivery = SystemClock.uptimeMillis() - event.eventTime
        LabLog.add("$packageName / $screen  (evento entregado en $delivery ms)")
    }

    /** Waits so the user can switch to the app to inspect, then logs the ids of the active window. */
    fun captureTreeAfter(seconds: Int) {
        LabLog.add("Captura en $seconds s: cambia a la app que quieras inspeccionar")
        mainHandler.postDelayed(::captureTree, seconds * 1000L)
    }

    /**
     * Logs only the structure: class, view id and selected state. Text is never read, except the short label of
     * a selected element (like a selected tab), which is what feature detection needs.
     */
    private fun captureTree() {
        val root = rootInActiveWindow
        if (root == null) {
            LabLog.add("Sin árbol de la ventana activa (puede ser una ventana protegida)")
            return
        }
        val lines = mutableListOf("--- Estructura de ${root.packageName} ---")
        var visited = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= MAX_NODES) return
            visited++
            val id = node.viewIdResourceName?.substringAfter(":id/")
            if (id != null || node.isSelected) {
                val label = node.contentDescription?.takeIf { node.isSelected && it.length <= MAX_LABEL }
                val indent = "  ".repeat(minOf(depth, 8))
                val cls = node.className?.toString()?.substringAfterLast('.')
                lines += "$indent$cls id=$id sel=${node.isSelected}" + (label?.let { " etiqueta=$it" } ?: "")
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        lines += "--- fin ($visited nodos) ---"
        LabLog.addBlock(lines)
    }

    companion object {
        /** Raw sessions older than this are deleted; only daily summaries are kept longer. */
        private const val RETENTION_DAYS = 30L

        /** Reactions (blocked, cancelled, allowed) are tiny, so they are kept longer than raw sessions. */
        private const val EVENT_RETENTION_DAYS = 180L
        private const val TAG = "PausaGuard"

        /** Pause before trying again after a storage or flow failure. */
        private const val RETRY_MILLIS = 2_000L

        /** Time for an app to leave the screen after Back, before checking whether it is still there. */
        private const val BACK_CHECK_MILLIS = 450L
        private const val CHECKPOINT_MILLIS = 30_000L
        private const val SYSTEM_PACKAGES_REFRESH_MILLIS = 10 * 60_000L
        private const val MAX_NODES = 400
        private const val MAX_SCAN_NODES = 600
        private const val MAX_LABEL = 16
        private const val SCAN_THROTTLE_MILLIS = 350L
        private const val FEATURE_RECHECK_MILLIS = 700L

        /** Time for the app's window to become the active one again after Pausa's screen is removed. */
        private const val FOCUS_RETURN_MILLIS = 200L

        /** A bottom bar has at least this many tabs, which tells it apart from the wrappers around each tab. */
        private const val MIN_TABS = 3
        private const val RECONCILE_DELAY_MILLIS = 150L
        private const val RECONCILE_CONFIRM_MILLIS = 250L


        /** The running service, if the user has it turned on. UI code reaches the service through this. */
        @Volatile
        var instance: GuardAccessibilityService? = null
            private set
    }
}
