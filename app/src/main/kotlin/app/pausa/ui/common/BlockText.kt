package app.pausa.ui.common

import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.Decision
import java.time.Instant
import java.time.ZonedDateTime

/** What the block screen tells the user, in plain words and with the time they can come back. */
object BlockText {

    fun describe(block: Decision.Block, now: ZonedDateTime, featureId: String? = null): String = when (block.reason) {
        BlockReason.ALWAYS -> "Bloqueada por tu regla."
        BlockReason.SCHEDULE ->
            block.until?.let { "Podrás usarla ${whenText(it, now)}." }
                ?: "Bloqueada por tu regla."
        BlockReason.DAILY_LIMIT -> "Llegaste a tu límite de hoy. Mañana vuelve."
        BlockReason.SESSION_LIMIT -> "Ya usaste las aperturas de hoy."
        BlockReason.FOCUS ->
            block.until?.let { "Concentración hasta las ${clockOf(it, now)}." }
                ?: "En concentración."
    }

    private fun clockOf(instant: Instant, now: ZonedDateTime): String {
        val local = instant.atZone(now.zone)
        return RuleText.clock(local.hour * 60 + local.minute)
    }

    private fun whenText(until: Instant, now: ZonedDateTime): String {
        val local = until.atZone(now.zone)
        val clock = RuleText.clock(local.hour * 60 + local.minute)
        val today = now.toLocalDate()
        return when (local.toLocalDate()) {
            today -> "a las $clock"
            today.plusDays(1) -> "mañana a las $clock"
            else -> "el ${RuleText.dayFullNames.getValue(local.dayOfWeek).lowercase()} a las $clock"
        }
    }
}
