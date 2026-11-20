package app.pausa.domain.repository

import app.pausa.domain.model.BlockExit
import app.pausa.domain.model.AppSettings
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.SessionCheckpoint
import app.pausa.domain.model.ThemeMode
import app.pausa.domain.model.TemporaryAllow
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /**
     * The focus session in progress, or null. It may already be over by the time it is read (nothing is emitted when
     * time passes), so whoever uses it compares [FocusSession.endsAt] with the clock.
     */
    val focus: Flow<FocusSession?>

    /** Temporary passes, with the same caveat: compare [TemporaryAllow.until] with the clock. */
    val temporaryAllows: Flow<List<TemporaryAllow>>

    suspend fun setCooldownSeconds(seconds: Int)

    suspend fun setOnboardingDone()

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setBlockExit(exit: BlockExit)

    suspend fun setExceptions(packages: Set<String>)

    /** Adds one app in a single atomic step: a read-then-write from the caller could lose a concurrent change. */
    suspend fun addException(packageName: String)

    suspend fun removeException(packageName: String)

    /** Sets or changes the PIN; null removes it. Only a salted hash is kept, never the PIN itself. */
    suspend fun setPin(pin: String?)

    suspend fun verifyPin(pin: String): Boolean

    /** Sets or changes the security pattern; null removes it. Only a salted hash is kept. */
    suspend fun setPattern(dots: List<Int>?)

    suspend fun verifyPattern(dots: List<Int>): Boolean

    suspend fun startFocus(session: FocusSession)

    suspend fun endFocus()

    suspend fun addTemporaryAllow(allow: TemporaryAllow)

    /** Ends every temporary pass at once: this is how a break is ended early. */
    suspend fun clearTemporaryAllows()

    /** The note about the session that was open when the guard last wrote one, or null (see [SessionCheckpoint]). */
    suspend fun checkpoint(): SessionCheckpoint?

    /** Replaces the note; null removes it. */
    suspend fun saveCheckpoint(checkpoint: SessionCheckpoint?)

    /** Forgets all settings, the PIN, the focus session and the passes. */
    suspend fun clearAll()
}
