package app.pausa.ui.common

import app.pausa.domain.model.StatsSnapshot
import app.pausa.domain.repository.StatsRepository
import app.pausa.domain.usecase.StatsCalculator
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Reads the raw history and turns it into the numbers screens show. Shared by the home and statistics screens. */
class StatsLoader @Inject constructor(
    private val repository: StatsRepository,
    private val clock: Clock,
) {
    suspend fun load(): StatsSnapshot {
        val today = LocalDate.now(clock)
        val raw = repository.load(today.minusDays(StatsCalculator.HISTORY_DAYS - 1L).toEpochDay())
        val first = repository.earliestActiveDay()?.let(LocalDate::ofEpochDay)
        return StatsCalculator.compute(raw, first, today, clock.zone)
    }
}
