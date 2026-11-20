package app.pausa.data.repository

import app.pausa.data.db.RuleDao
import app.pausa.data.db.RuleEntity
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Target
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleRepositoryImplTest {

    private class FakeDao(private val rows: List<RuleEntity>) : RuleDao {
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(rows)
        override suspend fun getAll() = rows
        override suspend fun upsert(rule: RuleEntity) = rule.id
        override suspend fun delete(id: Long) = Unit
    }

    private fun entity(id: Long, json: String, hardMode: Boolean = false) = RuleEntity(
        id = id,
        packageName = "com.example.game",
        featureId = null,
        enabled = true,
        hardMode = hardMode,
        restrictionsJson = json,
    )

    @Test
    fun `a rule that cannot be read still blocks instead of disappearing`() = runBlocking {
        val repo = RuleRepositoryImpl(FakeDao(listOf(entity(7, "this is not json", hardMode = true))))

        val rules = repo.getRules()

        assertEquals(1, rules.size)
        val rule = rules.single()
        assertTrue(rule.unreadable)
        assertEquals(listOf<Restriction>(Restriction.AlwaysBlock), rule.restrictions)
        assertEquals(Target.App("com.example.game"), rule.target)
    }

    @Test
    fun `a damaged rule never keeps hard mode so it can always be fixed`() = runBlocking {
        val repo = RuleRepositoryImpl(FakeDao(listOf(entity(7, "{", hardMode = true))))

        assertFalse(repo.getRules().single().hardMode)
    }

    @Test
    fun `readable rules are not marked unreadable`() = runBlocking {
        val json = app.pausa.data.db.RestrictionJson.encode(listOf(Restriction.DailyLimit(30)))
        val repo = RuleRepositoryImpl(FakeDao(listOf(entity(1, json))))

        val rule = repo.getRules().single()

        assertFalse(rule.unreadable)
        assertEquals(listOf<Restriction>(Restriction.DailyLimit(30)), rule.restrictions)
    }
}
