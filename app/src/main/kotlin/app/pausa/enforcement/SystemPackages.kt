package app.pausa.enforcement

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import app.pausa.data.apps.activitiesFor
import app.pausa.domain.enforcement.SystemPackageSets
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds, on this phone, the packages that must not be treated as "an app the user opened": the launcher, the
 * keyboard, the notification shade and permission dialogs; and the ones that must never be blocked: phone,
 * settings and Pausa itself. Each name is asked of Android instead of hard-coded where there is a way to ask,
 * because Samsung, Pixel and other makers use different packages.
 */
@Singleton
class SystemPackages @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun resolve(): SystemPackageSets {
        val home = activitiesFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        val dialers = activitiesFor(Intent(Intent.ACTION_DIAL))
        val keyboards = context.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList.orEmpty().map { it.packageName }.toSet()

        val transient = keyboards + KNOWN_TRANSIENT
        val protected = home + dialers + keyboards + KNOWN_PROTECTED + context.packageName
        return SystemPackageSets(home = home, transient = transient, protectedPackages = protected)
    }

    private fun activitiesFor(intent: Intent): Set<String> =
        context.packageManager
            .activitiesFor(intent)
            .map { it.activityInfo.packageName }
            .toSet()

    private companion object {
        /** Windows that float over an app without leaving it. The lab log shows if this phone needs more. */
        val KNOWN_TRANSIENT = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.samsung.android.permissioncontroller",
            "com.samsung.android.app.cocktailbarservice",
            // Seen on a Galaxy S26 (One UI 9) floating over other apps: game tools bar, the camera/microphone
            // privacy indicator and the fingerprint prompt. None of them means the user left the app.
            "com.samsung.android.game.gametools",
            "com.samsung.android.privacydashboard",
            "com.samsung.android.biometrics.app.setting",
        )

        val KNOWN_PROTECTED = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.emergency",
        )
    }
}
