package app.pausa.data.repository

import app.pausa.data.db.EventDao
import app.pausa.data.db.InterventionEventEntity
import app.pausa.data.db.UsageDao
import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.InterventionOutcome
import app.pausa.domain.model.RawStats
import app.pausa.domain.repository.StatsRepository
import java.time.Instant

class StatsRepositoryImpl(
    private val events: EventDao,
    private val usage: UsageDao,
) : StatsRepository {

    override suspend fun record(event: InterventionEvent, epochDay: Long) {
        events.insert(
            InterventionEventEntity(
                timestampMillis = event.timestamp.toEpochMilli(),
                epochDay = epochDay,
                packageName = event.packageName,
                outcome = event.outcome.name,
            ),
        )
    }

    override suspend fun load(fromEpochDay: Long): RawStats = RawStats(
        sessions = usage.sessionsSince(fromEpochDay).map {
            CompletedSession(it.packageName, Instant.ofEpochMilli(it.startedAtMillis), Instant.ofEpochMilli(it.endedAtMillis))
        },
        // A row with an outcome this version does not know is skipped rather than guessed at.
        events = events.since(fromEpochDay).mapNotNull { row ->
            val outcome = runCatching { InterventionOutcome.valueOf(row.outcome) }.getOrNull() ?: return@mapNotNull null
            InterventionEvent(Instant.ofEpochMilli(row.timestampMillis), row.packageName, outcome)
        },
    )

    override suspend fun purgeBefore(epochDay: Long) = events.deleteBefore(epochDay)

    override suspend fun earliestActiveDay(): Long? =
        listOfNotNull(events.earliestDay(), usage.earliestDay()).minOrNull()
}
