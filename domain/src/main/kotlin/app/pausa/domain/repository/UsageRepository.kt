package app.pausa.domain.repository

import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.PackageUsage
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    suspend fun record(session: CompletedSession, epochDay: Long)

    /** What each app used on [epochDay], keyed by package name. */
    suspend fun totalsForDay(epochDay: Long): Map<String, PackageUsage>

    /** Same as [totalsForDay] but updates whenever a session is recorded, for screens that show it live. */
    fun observeTotalsForDay(epochDay: Long): Flow<Map<String, PackageUsage>>

    /** Raw sessions are kept only for a limited time; summaries (streaks, time saved) live in their own table. */
    suspend fun purgeBefore(epochDay: Long)
}
