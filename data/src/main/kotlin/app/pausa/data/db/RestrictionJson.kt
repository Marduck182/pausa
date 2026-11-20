package app.pausa.data.db

import app.pausa.domain.model.Restriction
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.TimeWindow
import java.time.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Storage format of a rule's restrictions. It is separate from the domain model on purpose: the domain can be
 * refactored freely, while this format is a promise to data already on the phone.
 */
@Serializable
private data class RestrictionsDocument(val v: Int = 1, val items: List<RestrictionDto>)

@Serializable
private sealed interface RestrictionDto {
    @Serializable
    @SerialName("always_block")
    data object AlwaysBlock : RestrictionDto

    @Serializable
    @SerialName("schedule")
    data class Schedule(val mode: String, val windows: List<WindowDto>) : RestrictionDto

    @Serializable
    @SerialName("daily_limit")
    data class DailyLimit(val minutes: Int) : RestrictionDto

    @Serializable
    @SerialName("session_limit")
    data class SessionLimit(val maxPerDay: Int) : RestrictionDto

    @Serializable
    @SerialName("pattern_lock")
    data object PatternLock : RestrictionDto

    @Serializable
    @SerialName("intervention")
    data class Intervention(val waitSeconds: Int, val breathing: Boolean) : RestrictionDto
}

@Serializable
private data class WindowDto(val days: List<Int>, val start: Int, val end: Int)

object RestrictionJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(restrictions: List<Restriction>): String =
        json.encodeToString(RestrictionsDocument(items = restrictions.map { it.toDto() }))

    /** Throws if the text is not a valid document; the repository decides what to do with such a rule. */
    fun decode(text: String): List<Restriction> =
        json.decodeFromString<RestrictionsDocument>(text).items.map { it.toDomain() }

    private fun Restriction.toDto(): RestrictionDto = when (this) {
        Restriction.AlwaysBlock -> RestrictionDto.AlwaysBlock
        is Restriction.Schedule -> RestrictionDto.Schedule(
            mode = mode.name,
            windows = windows.map { w ->
                WindowDto(w.days.map { it.value }.sorted(), w.startMinute, w.endMinute)
            },
        )
        is Restriction.DailyLimit -> RestrictionDto.DailyLimit(minutes)
        is Restriction.SessionLimit -> RestrictionDto.SessionLimit(maxPerDay)
        Restriction.PatternLock -> RestrictionDto.PatternLock
        is Restriction.Intervention -> RestrictionDto.Intervention(waitSeconds, breathing)
    }

    private fun RestrictionDto.toDomain(): Restriction = when (this) {
        RestrictionDto.AlwaysBlock -> Restriction.AlwaysBlock
        is RestrictionDto.Schedule -> Restriction.Schedule(
            mode = ScheduleMode.valueOf(mode),
            windows = windows.map { w ->
                TimeWindow(w.days.map { DayOfWeek.of(it) }.toSet(), w.start, w.end)
            },
        )
        is RestrictionDto.DailyLimit -> Restriction.DailyLimit(minutes)
        is RestrictionDto.SessionLimit -> Restriction.SessionLimit(maxPerDay)
        RestrictionDto.PatternLock -> Restriction.PatternLock
        is RestrictionDto.Intervention -> Restriction.Intervention(waitSeconds, breathing)
    }
}
