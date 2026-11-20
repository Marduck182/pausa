package app.pausa.data.repository

import app.pausa.data.db.RestrictionJson
import app.pausa.data.db.RuleDao
import app.pausa.data.db.RuleEntity
import app.pausa.domain.model.Rule
import app.pausa.domain.model.Target
import app.pausa.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepositoryImpl(private val dao: RuleDao) : RuleRepository {

    override fun observeRules(): Flow<List<Rule>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override suspend fun getRules(): List<Rule> = dao.getAll().mapNotNull { it.toDomainOrNull() }

    override suspend fun upsert(rule: Rule): Long {
        val id = dao.upsert(rule.toEntity())
        // Room returns -1 when it updated an existing row instead of inserting one.
        return if (id == -1L) rule.id else id
    }

    override suspend fun delete(ruleId: Long) = dao.delete(ruleId)
}

private fun Rule.toEntity() = RuleEntity(
    id = id,
    packageName = target.packageName,
    featureId = (target as? Target.Feature)?.featureId,
    enabled = enabled,
    hardMode = hardMode,
    restrictionsJson = RestrictionJson.encode(restrictions),
)

/** A row that cannot be read is skipped instead of crashing the app or silently becoming "no restrictions". */
private fun RuleEntity.toDomainOrNull(): Rule? = runCatching {
    Rule(
        id = id,
        target = featureId?.let { Target.Feature(packageName, it) } ?: Target.App(packageName),
        restrictions = RestrictionJson.decode(restrictionsJson),
        enabled = enabled,
        hardMode = hardMode,
    )
}.getOrNull()
