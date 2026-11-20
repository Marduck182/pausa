package app.pausa.data

import android.content.Context
import androidx.room.Room
import app.pausa.data.apps.InstalledAppsRepositoryImpl
import app.pausa.data.db.PausaDatabase
import app.pausa.data.prefs.SettingsRepositoryImpl
import app.pausa.data.repository.RuleRepositoryImpl
import app.pausa.data.repository.StatsRepositoryImpl
import app.pausa.data.repository.UsageRepositoryImpl
import app.pausa.domain.repository.DataEraser
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.RuleRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.repository.StatsRepository
import app.pausa.domain.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only door into this module. The app asks for repositories, never for Room: storage technology stays an
 * implementation detail that can change without touching UI or domain.
 */
class DataGraph(context: Context) {
    private val appContext = context.applicationContext

    private val database: PausaDatabase by lazy {
        Room.databaseBuilder(appContext, PausaDatabase::class.java, PausaDatabase.NAME).build()
    }

    val rules: RuleRepository by lazy { RuleRepositoryImpl(database.ruleDao()) }

    val usage: UsageRepository by lazy { UsageRepositoryImpl(database.usageDao()) }

    val stats: StatsRepository by lazy { StatsRepositoryImpl(database.eventDao(), database.usageDao()) }

    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(appContext) }

    val installedApps: InstalledAppsRepository by lazy { InstalledAppsRepositoryImpl(appContext) }

    /** Everything Pausa has stored: rules, use, events, settings, the PIN. Nothing is kept anywhere else. */
    val eraser: DataEraser = object : DataEraser {
        override suspend fun eraseAll() {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            settings.clearAll()
        }
    }
}
