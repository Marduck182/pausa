package app.pausa.ui.common

import android.content.Context
import app.pausa.R
import app.pausa.domain.model.BlockMode
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.PackageUsage
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.TimeWindow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Wording for rules, kept in one place so every screen describes the same rule the same way. Day names come from the
 * system's language; the rest are string resources, so the text follows the phone's language (English by default).
 */
object RuleText {

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private fun String.capitalised(locale: Locale) = replaceFirstChar { it.titlecase(locale) }

    /** "Mon" / "Lun": the short name of the day, capitalised. */
    private fun shortName(day: DayOfWeek): String {
        val locale = Locale.getDefault()
        return day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').capitalised(locale)
    }

    /** One letter for a day button ("M", "T", ... or "L", "M", "X", ...). */
    fun dayLetter(day: DayOfWeek): String {
        val locale = Locale.getDefault()
        return day.getDisplayName(TextStyle.NARROW, locale).capitalised(locale)
    }

    /** "Monday" / "Lunes": for screen readers and labels. */
    fun dayFullName(day: DayOfWeek): String {
        val locale = Locale.getDefault()
        return day.getDisplayName(TextStyle.FULL, locale).capitalised(locale)
    }

    fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** A moment as people say it: "14:00", "tomorrow 09:00" or "mon 09:00". */
    fun moment(context: Context, instant: java.time.Instant, now: java.time.ZonedDateTime): String {
        val local = instant.atZone(now.zone)
        val time = clock(local.hour * 60 + local.minute)
        val today = now.toLocalDate()
        return when (local.toLocalDate()) {
            today -> time
            today.plusDays(1) -> context.getString(R.string.rule_tomorrow_at, time)
            else -> "${shortName(local.dayOfWeek).lowercase()} $time"
        }
    }

    /** Why and until when something is blocked, in a few words for a list: "until 14:00", "always", "today's limit". */
    fun blockedBrief(context: Context, reason: BlockReason, until: java.time.Instant?, now: java.time.ZonedDateTime): String = when (reason) {
        BlockReason.ALWAYS -> context.getString(R.string.rule_brief_always)
        BlockReason.SCHEDULE, BlockReason.FOCUS ->
            until?.let { context.getString(R.string.rule_brief_until, moment(context, it, now)) } ?: context.getString(R.string.rule_brief_now)
        BlockReason.DAILY_LIMIT -> context.getString(R.string.rule_brief_daily_limit)
        BlockReason.SESSION_LIMIT -> context.getString(R.string.rule_brief_session_limit)
    }

    /** "45 min", "2 h" or "1 h 30 min". Rounds down to the minute. The units read the same in every language we have. */
    fun duration(millis: Long): String {
        val minutes = (millis / 60_000L).toInt().coerceAtLeast(0)
        return when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }

    fun days(context: Context, days: Set<DayOfWeek>): String = when {
        days.size == 7 -> context.getString(R.string.rule_every_day)
        days == weekdays -> context.getString(R.string.rule_weekdays)
        days == weekend -> context.getString(R.string.rule_weekend)
        else -> days.sortedBy { it.value }.joinToString(", ") { shortName(it) }
    }

    fun window(context: Context, window: TimeWindow): String =
        "${days(context, window.days)} ${clock(window.startMinute)}–${clock(window.endMinute)}"

    /** One short line for the apps list, like "Blocked Mon–Fri 09:00–14:00 · 30 min a day". */
    fun summary(context: Context, draft: RuleDraft): String {
        val res = context.resources
        val parts = buildList {
            when (draft.blockMode) {
                BlockMode.NONE -> Unit
                BlockMode.ALWAYS -> add(context.getString(R.string.rule_always_blocked))
                BlockMode.SCHEDULE -> {
                    val prefix = context.getString(
                        if (draft.scheduleMode == ScheduleMode.BLOCK_DURING) R.string.rule_blocked else R.string.rule_only_allowed,
                    )
                    add("$prefix ${draft.windows.joinToString("; ") { window(context, it) }}")
                }
            }
            draft.dailyLimitMinutes?.let { add(context.getString(R.string.rule_minutes_a_day, it)) }
            draft.maxSessionsPerDay?.let { add(res.getQuantityString(R.plurals.rule_max_openings, it, it)) }
            if (draft.patternLock) add(context.getString(R.string.rule_asks_pattern))
            draft.intervention?.let { add(context.getString(R.string.rule_pause_seconds, it.waitSeconds)) }
        }
        return if (parts.isEmpty()) context.getString(R.string.rule_no_restrictions) else parts.joinToString(" · ")
    }

    /**
     * Today's use next to the limits that apply, like "Today: 12 of 30 min · 2 of 3 openings". Null when there is
     * nothing to show: no use yet and no limit to compare against.
     */
    fun usageToday(context: Context, draft: RuleDraft, usage: PackageUsage?): String? {
        val res = context.resources
        val minutes = (usage?.usedMillis ?: 0L) / 60_000L
        val openings = usage?.sessions ?: 0
        val parts = buildList {
            val limit = draft.dailyLimitMinutes
            when {
                limit != null -> add(res.getString(R.string.rule_minutes_of, minutes, limit))
                minutes > 0 -> add("$minutes min")
            }
            val maxOpenings = draft.maxSessionsPerDay
            when {
                maxOpenings != null -> add(res.getQuantityString(R.plurals.rule_openings_of, maxOpenings, openings, maxOpenings))
                openings > 0 && limit == null -> add(res.getQuantityString(R.plurals.rule_openings, openings, openings))
            }
        }
        return if (parts.isEmpty()) null else res.getString(R.string.rule_today, parts.joinToString(" · "))
    }
}
