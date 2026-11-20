package app.pausa.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RuleEntity::class,
        UsageSessionEntity::class,
        InterventionEventEntity::class,
        DaySummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PausaDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    abstract fun usageDao(): UsageDao

    abstract fun eventDao(): EventDao

    companion object {
        const val NAME = "pausa.db"
    }
}
