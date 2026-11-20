package app.pausa.ui.common

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import app.pausa.R
import app.pausa.domain.model.PackageUsage
import app.pausa.domain.model.RuleDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The wording of rules comes from string resources, which a plain JVM test cannot load. This fake answers with
 * English text for the few strings the code under test asks for, so what is checked is the logic (which numbers are
 * compared, which plural is picked), not the copy: that is checked by reading the app in both languages.
 */
private class FakeContext : ContextWrapper(null) {
    private val strings = mapOf(
        R.string.rule_minutes_of to "%1\$d of %2\$d min",
        R.string.rule_today to "Today: %1\$s",
    )
    private val plurals = mapOf(
        R.plurals.rule_openings_of to ("%1\$d of %2\$d opening" to "%1\$d of %2\$d openings"),
        R.plurals.rule_openings to ("%1\$d opening" to "%1\$d openings"),
    )

    private val fakeResources = object : Resources(null, null, null) {
        override fun getString(id: Int, vararg formatArgs: Any?): String = String.format(strings.getValue(id), *formatArgs)

        override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String {
            val (one, other) = plurals.getValue(id)
            return String.format(if (quantity == 1) one else other, *formatArgs)
        }
    }

    override fun getResources(): Resources = fakeResources
}

class RuleTextTest {

    private val context: Context = FakeContext()
    private val base = RuleDraft(packageName = "com.instagram.android")

    private fun usage(minutes: Long, sessions: Int) = PackageUsage(minutes * 60_000L, sessions)

    private fun today(draft: RuleDraft, usage: PackageUsage?) = RuleText.usageToday(context, draft, usage)

    @Test
    fun `nothing to show without use or limits`() {
        assertNull(today(base, null))
        assertNull(today(base, usage(0, 0)))
    }

    @Test
    fun `use is compared with the daily limit`() {
        val draft = base.copy(dailyLimitMinutes = 30)
        assertEquals("Today: 12 of 30 min", today(draft, usage(12, 1)))
        assertEquals("Today: 0 of 30 min", today(draft, null))
    }

    @Test
    fun `openings are compared with the session limit`() {
        val draft = base.copy(maxSessionsPerDay = 3)
        assertEquals("Today: 5 min · 2 of 3 openings", today(draft, usage(5, 2)))
        assertEquals("Today: 2 of 3 openings", today(draft, usage(0, 2)))
        assertEquals("Today: 0 of 1 opening", today(base.copy(maxSessionsPerDay = 1), null))
    }

    @Test
    fun `both limits show together`() {
        val draft = base.copy(dailyLimitMinutes = 30, maxSessionsPerDay = 3)
        assertEquals("Today: 12 of 30 min · 2 of 3 openings", today(draft, usage(12, 2)))
    }

    @Test
    fun `without limits the plain use is shown with correct plurals`() {
        assertEquals("Today: 7 min · 1 opening", today(base, usage(7, 1)))
        assertEquals("Today: 7 min · 4 openings", today(base, usage(7, 4)))
    }
}
