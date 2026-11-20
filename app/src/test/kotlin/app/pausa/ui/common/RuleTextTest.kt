package app.pausa.ui.common

import app.pausa.domain.model.PackageUsage
import app.pausa.domain.model.RuleDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleTextTest {

    private val base = RuleDraft(packageName = "com.instagram.android")

    private fun usage(minutes: Long, sessions: Int) = PackageUsage(minutes * 60_000L, sessions)

    @Test
    fun `nothing to show without use or limits`() {
        assertNull(RuleText.usageToday(base, null))
        assertNull(RuleText.usageToday(base, usage(0, 0)))
    }

    @Test
    fun `use is compared with the daily limit`() {
        val draft = base.copy(dailyLimitMinutes = 30)
        assertEquals("Hoy: 12 de 30 min", RuleText.usageToday(draft, usage(12, 1)))
        assertEquals("Hoy: 0 de 30 min", RuleText.usageToday(draft, null))
    }

    @Test
    fun `openings are compared with the session limit`() {
        val draft = base.copy(maxSessionsPerDay = 3)
        assertEquals("Hoy: 5 min · 2 de 3 aperturas", RuleText.usageToday(draft, usage(5, 2)))
        assertEquals("Hoy: 2 de 3 aperturas", RuleText.usageToday(draft, usage(0, 2)))
        assertEquals("Hoy: 0 de 1 apertura", RuleText.usageToday(base.copy(maxSessionsPerDay = 1), null))
    }

    @Test
    fun `both limits show together`() {
        val draft = base.copy(dailyLimitMinutes = 30, maxSessionsPerDay = 3)
        assertEquals("Hoy: 12 de 30 min · 2 de 3 aperturas", RuleText.usageToday(draft, usage(12, 2)))
    }

    @Test
    fun `without limits the plain use is shown with correct plurals`() {
        assertEquals("Hoy: 7 min · 1 apertura", RuleText.usageToday(base, usage(7, 1)))
        assertEquals("Hoy: 7 min · 4 aperturas", RuleText.usageToday(base, usage(7, 4)))
    }
}
