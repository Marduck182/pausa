package app.pausa.ui.common

import app.pausa.domain.model.BlockMode
import app.pausa.domain.model.BlockReason
import app.pausa.domain.model.PackageUsage
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.TimeWindow
import java.time.DayOfWeek

/** Spanish wording for rules, kept in one place so every screen describes the same rule the same way. */
object RuleText {

    private val shortNames = mapOf(
        DayOfWeek.MONDAY to "Lun", DayOfWeek.TUESDAY to "Mar", DayOfWeek.WEDNESDAY to "Mié",
        DayOfWeek.THURSDAY to "Jue", DayOfWeek.FRIDAY to "Vie", DayOfWeek.SATURDAY to "Sáb",
        DayOfWeek.SUNDAY to "Dom",
    )

    val dayLetters = mapOf(
        DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "X",
        DayOfWeek.THURSDAY to "J", DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S",
        DayOfWeek.SUNDAY to "D",
    )

    val dayFullNames = mapOf(
        DayOfWeek.MONDAY to "Lunes", DayOfWeek.TUESDAY to "Martes", DayOfWeek.WEDNESDAY to "Miércoles",
        DayOfWeek.THURSDAY to "Jueves", DayOfWeek.FRIDAY to "Viernes", DayOfWeek.SATURDAY to "Sábado",
        DayOfWeek.SUNDAY to "Domingo",
    )

    private val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /** A moment as people say it: "14:00", "mañana 09:00" or "lun 09:00". */
    fun moment(instant: java.time.Instant, now: java.time.ZonedDateTime): String {
        val local = instant.atZone(now.zone)
        val time = clock(local.hour * 60 + local.minute)
        val today = now.toLocalDate()
        return when (local.toLocalDate()) {
            today -> time
            today.plusDays(1) -> "mañana $time"
            else -> "${shortNames.getValue(local.dayOfWeek).lowercase()} $time"
        }
    }

    /** Why and until when something is blocked, in a few words for a list: "hasta 14:00", "siempre", "límite de hoy". */
    fun blockedBrief(reason: BlockReason, until: java.time.Instant?, now: java.time.ZonedDateTime): String = when (reason) {
        BlockReason.ALWAYS -> "siempre"
        BlockReason.SCHEDULE, BlockReason.FOCUS -> until?.let { "hasta ${moment(it, now)}" } ?: "ahora"
        BlockReason.DAILY_LIMIT -> "límite de hoy"
        BlockReason.SESSION_LIMIT -> "aperturas de hoy"
    }

    /** "45 min", "2 h" or "1 h 30 min". Rounds down to the minute. */
    fun duration(millis: Long): String {
        val minutes = (millis / 60_000L).toInt().coerceAtLeast(0)
        return when {
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0 -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }

    fun days(days: Set<DayOfWeek>): String = when {
        days.size == 7 -> "Todos los días"
        days == weekdays -> "Lun–Vie"
        days == weekend -> "Fin de semana"
        else -> days.sortedBy { it.value }.joinToString(", ") { shortNames.getValue(it) }
    }

    fun window(window: TimeWindow): String =
        "${days(window.days)} ${clock(window.startMinute)}–${clock(window.endMinute)}"

    /** One short line for the apps list, like "Bloqueada Lun–Vie 09:00–14:00 · 30 min al día". */
    fun summary(draft: RuleDraft): String {
        val parts = buildList {
            when (draft.blockMode) {
                BlockMode.NONE -> Unit
                BlockMode.ALWAYS -> add("Siempre bloqueada")
                BlockMode.SCHEDULE -> {
                    val prefix = if (draft.scheduleMode == ScheduleMode.BLOCK_DURING) "Bloqueada" else "Solo permitida"
                    add("$prefix ${draft.windows.joinToString("; ") { window(it) }}")
                }
            }
            draft.dailyLimitMinutes?.let { add("${it} min al día") }
            draft.maxSessionsPerDay?.let { add("máx. $it ${if (it == 1) "apertura" else "aperturas"} al día") }
            if (draft.patternLock) add("pide patrón")
            draft.intervention?.let { add("pausa de ${it.waitSeconds} s") }
        }
        return if (parts.isEmpty()) "Sin restricciones" else parts.joinToString(" · ")
    }

    /**
     * Today's use next to the limits that apply, like "Hoy: 12 de 30 min · 2 de 3 aperturas". Null when there is
     * nothing to show: no use yet and no limit to compare against.
     */
    fun usageToday(draft: RuleDraft, usage: PackageUsage?): String? {
        val minutes = (usage?.usedMillis ?: 0L) / 60_000L
        val openings = usage?.sessions ?: 0
        val parts = buildList {
            val limit = draft.dailyLimitMinutes
            when {
                limit != null -> add("$minutes de $limit min")
                minutes > 0 -> add("$minutes min")
            }
            val maxOpenings = draft.maxSessionsPerDay
            when {
                maxOpenings != null -> add("$openings de $maxOpenings ${if (maxOpenings == 1) "apertura" else "aperturas"}")
                openings > 0 && limit == null -> add("$openings ${if (openings == 1) "apertura" else "aperturas"}")
            }
        }
        return if (parts.isEmpty()) null else "Hoy: " + parts.joinToString(" · ")
    }
}
