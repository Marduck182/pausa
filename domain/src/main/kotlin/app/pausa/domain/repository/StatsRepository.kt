package app.pausa.domain.repository

import app.pausa.domain.model.InterventionEvent
import app.pausa.domain.model.RawStats

interface StatsRepository {
    suspend fun record(event: InterventionEvent, epochDay: Long)

    /** Sessions and events from [fromEpochDay] on. */
    suspend fun load(fromEpochDay: Long): RawStats

    /** The first day with any activity, so a streak does not count days before the app was in use. */
    suspend fun earliestActiveDay(): Long?
}
