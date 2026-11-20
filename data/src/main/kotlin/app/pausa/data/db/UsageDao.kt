package app.pausa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row of the daily totals query: what a package used on one day. */
data class DayTotal(
    val packageName: String,
    val usedMillis: Long,
    val sessions: Int,
)

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(session: UsageSessionEntity)

    @Query(
        """
        SELECT packageName,
               SUM(endedAtMillis - startedAtMillis) AS usedMillis,
               COUNT(*) AS sessions
        FROM usage_session
        WHERE epochDay = :epochDay
        GROUP BY packageName
        """,
    )
    suspend fun totalsForDay(epochDay: Long): List<DayTotal>

    @Query(
        """
        SELECT packageName,
               SUM(endedAtMillis - startedAtMillis) AS usedMillis,
               COUNT(*) AS sessions
        FROM usage_session
        WHERE epochDay = :epochDay
        GROUP BY packageName
        """,
    )
    fun observeTotalsForDay(epochDay: Long): Flow<List<DayTotal>>

    @Query("SELECT * FROM usage_session WHERE epochDay >= :fromEpochDay ORDER BY startedAtMillis")
    suspend fun sessionsSince(fromEpochDay: Long): List<UsageSessionEntity>

    @Query("SELECT MIN(epochDay) FROM usage_session")
    suspend fun earliestDay(): Long?

    @Query("DELETE FROM usage_session WHERE epochDay < :epochDay")
    suspend fun purgeBefore(epochDay: Long)
}
