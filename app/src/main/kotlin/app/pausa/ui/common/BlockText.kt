package app.pausa.ui.common

import android.content.Context
import app.pausa.R
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.Decision
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** What the block screen tells the user, in plain words and with the time they can come back. */
object BlockText {

    @Suppress("UNUSED_PARAMETER")
    fun describe(context: Context, block: Decision.Block, now: ZonedDateTime, featureId: String? = null): String = when (block.reason) {
        BlockReason.ALWAYS -> context.getString(R.string.block_by_rule)
        BlockReason.SCHEDULE ->
            block.until?.let { context.getString(R.string.block_available, whenText(context, it, now)) }
                ?: context.getString(R.string.block_by_rule)
        BlockReason.DAILY_LIMIT -> context.getString(R.string.block_daily_limit)
        BlockReason.SESSION_LIMIT -> context.getString(R.string.block_session_limit)
        BlockReason.FOCUS ->
            block.until?.let { context.getString(R.string.block_focus_until, clockOf(it, now)) }
                ?: context.getString(R.string.block_focus)
    }

    private fun clockOf(instant: Instant, now: ZonedDateTime): String {
        val local = instant.atZone(now.zone)
        return RuleText.clock(local.hour * 60 + local.minute)
    }

    private fun whenText(context: Context, until: Instant, now: ZonedDateTime): String {
        val local = until.atZone(now.zone)
        val clock = RuleText.clock(local.hour * 60 + local.minute)
        val today = now.toLocalDate()
        return when (local.toLocalDate()) {
            today -> context.getString(R.string.block_at_time, clock)
            today.plusDays(1) -> context.getString(R.string.block_tomorrow_at, clock)
            else -> context.getString(R.string.block_on_day_at, local.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), clock)
        }
    }
}
