package app.pausa.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One rule. The restrictions live in one JSON column (see [RestrictionJson]) so new kinds need no migration. */
@Entity(tableName = "rule", indices = [Index("packageName")])
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** Null means the rule is about the whole app. */
    val featureId: String?,
    val enabled: Boolean,
    val hardMode: Boolean,
    val restrictionsJson: String,
)

/** One use of an app, from foreground to background. Raw rows are purged after a retention period. */
@Entity(tableName = "usage_session", indices = [Index("epochDay"), Index("packageName")])
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val epochDay: Long,
)

/** The user tried to open something restricted. [outcome] is a short code, never any app content. */
@Entity(tableName = "intervention_event", indices = [Index("epochDay")])
data class InterventionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val epochDay: Long,
    val packageName: String,
    val outcome: String,
)

/** One row per day, kept long term: it is what streaks and the time saved are computed from. */
@Entity(tableName = "day_summary")
data class DaySummaryEntity(
    @PrimaryKey val epochDay: Long,
    val goalMet: Boolean,
    val savedMillis: Long,
)
