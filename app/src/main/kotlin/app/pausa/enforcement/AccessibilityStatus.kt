package app.pausa.enforcement

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {
    /** Whether the user has turned Pausa's service on. Reading this list needs no permission. */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val mine = ComponentName(context, GuardAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }
}
