package app.pausa.data.apps

import android.content.Context
import android.content.Intent
import app.pausa.domain.model.InstalledApp
import app.pausa.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepositoryImpl(private val context: Context) : InstalledAppsRepository {

    override suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.activitiesFor(launcherIntent)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
