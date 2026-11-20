package app.pausa.data.repository

import android.util.Log
import app.pausa.data.db.RestrictionJson
import app.pausa.data.db.RuleDao
import app.pausa.data.db.RuleEntity
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.Target
import app.pausa.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepositoryImpl(private val dao: RuleDao) : RuleRepository {

    override fun observeRules(): Flow<List<Rule>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getRules(): List<Rule> = dao.getAll().map { it.toDomain() }

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

/**
 * A row that cannot be read does not crash the app and does not become "no restrictions" (that would open the app
 * without anyone noticing). It becomes an always-block rule marked [Rule.unreadable], and the failure is logged.
 * Hard mode is dropped so a damaged rule can never lock the person out of fixing it.
 */
private fun RuleEntity.toDomain(): Rule {
    val target = featureId?.let { Target.Feature(packageName, it) } ?: Target.App(packageName)
    return try {
        Rule(
            id = id,
            target = target,
            restrictions = RestrictionJson.decode(restrictionsJson),
            enabled = enabled,
            hardMode = hardMode,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Rule $id for $packageName could not be read; blocking until it is set up again", e)
        Rule(
            id = id,
            target = target,
            restrictions = listOf(Restriction.AlwaysBlock),
            enabled = enabled,
            hardMode = false,
            unreadable = true,
        )
    }
}

private const val TAG = "PausaRules"
