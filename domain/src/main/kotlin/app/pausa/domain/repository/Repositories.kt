package app.pausa.domain.repository

import app.pausa.domain.model.InstalledApp
import app.pausa.domain.model.Rule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun observeRules(): Flow<List<Rule>>

    suspend fun getRules(): List<Rule>

    /** Inserts (id == 0) or updates. Returns the id the rule now has. */
    suspend fun upsert(rule: Rule): Long

    suspend fun delete(ruleId: Long)
}

/** Removes everything Pausa has stored, for the user who wants to start clean or stop using the app. */
interface DataEraser {
    suspend fun eraseAll()
}

interface InstalledAppsRepository {
    /** Apps with a launcher icon. Never reads anything about what is inside them. */
    suspend fun getLaunchableApps(): List<InstalledApp>
}
