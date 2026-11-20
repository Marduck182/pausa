package app.pausa.domain.model

enum class BlockMode {
    /** Not blocked by default; limits and the intervention may still apply. */
    NONE,

    /** Never usable. */
    ALWAYS,

    /** Blocked or allowed by time of day, see [RuleDraft.scheduleMode]. */
    SCHEDULE,
}

/**
 * What the user edits on the per-app screen: a flat, simple shape (one choice for blocking, optional limits,
 * optional pause) that maps onto the engine's [Rule]. The screen never builds restriction lists itself, so the
 * rules for combining them live in one tested place.
 */
data class RuleDraft(
    val ruleId: Long = 0,
    val packageName: String,
    /** Set for a rule about one feature of the app (Shorts, Reels); null for the whole app. */
    val featureId: String? = null,
    val enabled: Boolean = true,
    val hardMode: Boolean = false,
    val blockMode: BlockMode = BlockMode.NONE,
    val scheduleMode: ScheduleMode = ScheduleMode.BLOCK_DURING,
    val windows: List<TimeWindow> = emptyList(),
    val dailyLimitMinutes: Int? = null,
    val maxSessionsPerDay: Int? = null,
    val intervention: Restriction.Intervention? = null,
    /** Ask for the security pattern before the app opens. Only for whole apps, never for a feature. */
    val patternLock: Boolean = false,
) {
    /** A schedule with no windows would silently do nothing (or block everything), so it cannot be saved. */
    val isValid: Boolean get() = blockMode != BlockMode.SCHEDULE || windows.isNotEmpty()

    fun toRule(): Rule {
        val restrictions = buildList {
            when (blockMode) {
                BlockMode.NONE -> Unit
                BlockMode.ALWAYS -> add(Restriction.AlwaysBlock)
                BlockMode.SCHEDULE -> add(Restriction.Schedule(scheduleMode, windows))
            }
            // Time and openings are measured per app, never per feature, so a feature rule cannot carry them.
            if (featureId == null) {
                dailyLimitMinutes?.let { add(Restriction.DailyLimit(it)) }
                maxSessionsPerDay?.let { add(Restriction.SessionLimit(it)) }
            }
            if (featureId == null && patternLock) add(Restriction.PatternLock)
            intervention?.let { add(it) }
        }
        return Rule(
            id = ruleId,
            target = featureId?.let { Target.Feature(packageName, it) } ?: Target.App(packageName),
            restrictions = restrictions,
            enabled = enabled,
            hardMode = hardMode,
        )
    }

    companion object {
        /** What a newly controlled app starts with: only a gentle pause, nothing blocked. */
        fun newFor(packageName: String) = RuleDraft(
            packageName = packageName,
            intervention = Restriction.Intervention(),
        )

        /** A feature rule starts blocking: that is what someone who picks "block Shorts" wants. */
        fun newForFeature(packageName: String, featureId: String) = RuleDraft(
            packageName = packageName,
            featureId = featureId,
            blockMode = BlockMode.ALWAYS,
        )

        /** The weekday example from the brief: blocked 09:00-14:00 and 16:00-21:00, Monday to Friday. */
        fun workdayWindows(): List<TimeWindow> {
            val weekdays = setOf(
                java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY,
            )
            return listOf(TimeWindow(weekdays, 9 * 60, 14 * 60), TimeWindow(weekdays, 16 * 60, 21 * 60))
        }

        /**
         * Reads a stored rule back into the editable shape. Only the first schedule is kept if there are several,
         * because the screen edits one; rules made by this screen never have more than one.
         */
        fun from(rule: Rule): RuleDraft {
            var draft = RuleDraft(
                ruleId = rule.id,
                packageName = rule.target.packageName,
                featureId = (rule.target as? Target.Feature)?.featureId,
                enabled = rule.enabled,
                hardMode = rule.hardMode,
            )
            var scheduleSeen = false
            for (restriction in rule.restrictions) {
                draft = when (restriction) {
                    Restriction.AlwaysBlock -> draft.copy(blockMode = BlockMode.ALWAYS)
                    is Restriction.Schedule ->
                        if (scheduleSeen || draft.blockMode == BlockMode.ALWAYS) {
                            draft
                        } else {
                            scheduleSeen = true
                            draft.copy(
                                blockMode = BlockMode.SCHEDULE,
                                scheduleMode = restriction.mode,
                                windows = restriction.windows,
                            )
                        }
                    is Restriction.DailyLimit -> draft.copy(dailyLimitMinutes = restriction.minutes)
                    is Restriction.SessionLimit -> draft.copy(maxSessionsPerDay = restriction.maxPerDay)
                    is Restriction.Intervention -> draft.copy(intervention = restriction)
                    Restriction.PatternLock -> draft.copy(patternLock = true)
                }
            }
            return draft
        }
    }
}
