package app.pausa.data.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * Activities that can handle [intent]. The flags overload only exists from Android 13; below that the older call
 * is the only one, so both live here and the rest of the code does not need to know.
 */
fun PackageManager.activitiesFor(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }
