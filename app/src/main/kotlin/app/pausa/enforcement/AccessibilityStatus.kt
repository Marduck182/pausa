package app.pausa.enforcement

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings

/** What the user sees about the guard: off, working, or switched on in Settings but not answering. */
enum class ServiceState { Off, Running, NotRunning }

object AccessibilityStatus {
    /** How long after the app starts the service is given to connect before it is reported as not answering. */
    private const val CONNECT_GRACE_MILLIS = 6_000L

    /** Whether the user has turned Pausa's service on. Reading this list needs no permission. */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val mine = ComponentName(context, GuardAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    /**
     * Being listed in Settings is not the same as working: the system can leave a service unbound after it crashed or
     * was killed. So "on" also needs the service to be alive in this process. Right after the app starts the service
     * has not had time to connect, so it is not reported as stalled until a few seconds have passed.
     */
    fun state(context: Context): ServiceState {
        if (!isServiceEnabled(context)) return ServiceState.Off
        if (GuardAccessibilityService.instance != null) return ServiceState.Running
        val sinceStart = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        return if (sinceStart < CONNECT_GRACE_MILLIS) ServiceState.Running else ServiceState.NotRunning
    }
}
