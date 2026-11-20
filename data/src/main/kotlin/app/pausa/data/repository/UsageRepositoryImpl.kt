package app.pausa.data.repository

import app.pausa.data.db.DayTotal
import app.pausa.data.db.UsageDao
import app.pausa.data.db.UsageSessionEntity
import app.pausa.domain.model.CompletedSession
import app.pausa.domain.model.PackageUsage
import app.pausa.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UsageRepositoryImpl(private val dao: UsageDao) : UsageRepository {

    override suspend fun record(session: CompletedSession, epochDay: Long) {
        dao.insert(
            UsageSessionEntity(
                packageName = session.packageName,
                startedAtMillis = session.start.toEpochMilli(),
                endedAtMillis = session.end.toEpochMilli(),
                epochDay = epochDay,
            ),
        )
    }

    override suspend fun totalsForDay(epochDay: Long): Map<String, PackageUsage> =
        dao.totalsForDay(epochDay).toUsageMap()

    override fun observeTotalsForDay(epochDay: Long): Flow<Map<String, PackageUsage>> =
        dao.observeTotalsForDay(epochDay).map { it.toUsageMap() }

    override suspend fun purgeBefore(epochDay: Long) = dao.purgeBefore(epochDay)
}

private fun List<DayTotal>.toUsageMap(): Map<String, PackageUsage> =
    associate { it.packageName to PackageUsage(it.usedMillis, it.sessions) }
