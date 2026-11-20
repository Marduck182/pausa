package app.pausa.domain.repository

import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.RawStats

interface StatsRepository {
    suspend fun record(event: InterventionEvent, epochDay: Long)

    /** Sessions and events from [fromEpochDay] on. */
    suspend fun load(fromEpochDay: Long): RawStats

    /** Events are kept for a limited time, like raw sessions: the table must not grow for as long as the phone lives. */
    suspend fun purgeBefore(epochDay: Long)

    /** The first day with any activity, so a streak does not count days before the app was in use. */
    suspend fun earliestActiveDay(): Long?
}
