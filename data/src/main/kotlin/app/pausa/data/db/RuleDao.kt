package app.pausa.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule ORDER BY id")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rule ORDER BY id")
    suspend fun getAll(): List<RuleEntity>

    @Upsert
    suspend fun upsert(rule: RuleEntity): Long

    @Query("DELETE FROM rule WHERE id = :id")
    suspend fun delete(id: Long)
}
