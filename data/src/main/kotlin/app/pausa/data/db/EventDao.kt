package app.pausa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: InterventionEventEntity)

    @Query("SELECT * FROM intervention_event WHERE epochDay >= :fromEpochDay ORDER BY timestampMillis")
    suspend fun since(fromEpochDay: Long): List<InterventionEventEntity>

    @Query("SELECT MIN(epochDay) FROM intervention_event")
    suspend fun earliestDay(): Long?
}
