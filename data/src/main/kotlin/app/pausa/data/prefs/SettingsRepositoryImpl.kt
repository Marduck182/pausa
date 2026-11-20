package app.pausa.data.prefs

import app.pausa.domain.model.BlockExit
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pausa.domain.model.AppSettings
import app.pausa.domain.model.FocusSession
import app.pausa.domain.model.SessionCheckpoint
import app.pausa.domain.model.Target
import app.pausa.domain.model.TemporaryAllow
import app.pausa.domain.model.ThemeMode
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.security.Pattern
import app.pausa.domain.security.PinHasher
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * If the settings file is damaged (a write cut short by a dead battery, say) the store starts empty instead of
 * throwing on every read, which would leave the guard and every screen failing until the app is reinstalled. The
 * cost is that the PIN, pattern and exceptions are lost, so it is logged: it is rare and the person should be able
 * to find out why.
 */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "pausa_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { error ->
        Log.e("PausaSettings", "Settings file was damaged and has been reset", error)
        emptyPreferences()
    },
)

/**
 * Settings, the focus session and temporary passes, in a small local file. The PIN is kept only as a salted hash.
 * A focus session or pass may already be over when it is read: whoever uses it still compares with the clock, so
 * a stale value can never keep something blocked or allowed past its end.
 */
class SettingsRepositoryImpl(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : SettingsRepository {

    private val store get() = context.settingsStore

    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            cooldownSeconds = prefs[COOLDOWN] ?: AppSettings.DEFAULT_COOLDOWN_SECONDS,
            pinEnabled = prefs[PIN_HASH] != null,
            patternEnabled = prefs[PATTERN_HASH] != null,
            onboardingDone = prefs[ONBOARDING_DONE] == true,
            exceptions = prefs[EXCEPTIONS] ?: emptySet(),
            // An unknown value (from a newer version, say) falls back to following the phone.
            blockExit = prefs[BLOCK_EXIT]?.let { name -> BlockExit.entries.firstOrNull { it.name == name } } ?: BlockExit.HOME,
            themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
        )
    }

    override val focus: Flow<FocusSession?> = store.data.map { prefs ->
        val end = prefs[FOCUS_END] ?: return@map null
        val endsAt = Instant.ofEpochMilli(end)
        if (endsAt <= clock.instant()) return@map null
        FocusSession(endsAt, (prefs[FOCUS_TARGETS] ?: emptySet()).mapNotNull(::decodeTarget).toSet())
    }

    override val temporaryAllows: Flow<List<TemporaryAllow>> = store.data.map { prefs ->
        val now = clock.instant()
        (prefs[ALLOWS] ?: emptySet()).mapNotNull(::decodeAllow).filter { it.until > now }
    }

    override suspend fun setCooldownSeconds(seconds: Int) {
        store.edit { it[COOLDOWN] = seconds.coerceIn(0, AppSettings.MAX_COOLDOWN_SECONDS) }
    }

    override suspend fun setBlockExit(exit: BlockExit) {
        store.edit { it[BLOCK_EXIT] = exit.name }
    }

    override suspend fun clearTemporaryAllows() {
        store.edit { it.remove(ALLOWS) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setOnboardingDone() {
        store.edit { it[ONBOARDING_DONE] = true }
    }

    override suspend fun setExceptions(packages: Set<String>) {
        store.edit { it[EXCEPTIONS] = packages }
    }

    override suspend fun addException(packageName: String) {
        store.edit { it[EXCEPTIONS] = (it[EXCEPTIONS] ?: emptySet()) + packageName }
    }

    override suspend fun removeException(packageName: String) {
        store.edit { it[EXCEPTIONS] = (it[EXCEPTIONS] ?: emptySet()) - packageName }
    }

    override suspend fun setPin(pin: String?) {
        val hash = pin?.let { withContext(Dispatchers.Default) { PinHasher.hash(it) } }
        store.edit { if (hash == null) it.remove(PIN_HASH) else it[PIN_HASH] = hash }
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val stored = store.data.first()[PIN_HASH] ?: return true
        return withContext(Dispatchers.Default) { PinHasher.verify(pin, stored) }
    }

    override suspend fun setPattern(dots: List<Int>?) {
        val hash = dots?.let { withContext(Dispatchers.Default) { Pattern.hash(it) } }
        store.edit { if (hash == null) it.remove(PATTERN_HASH) else it[PATTERN_HASH] = hash }
    }

    override suspend fun verifyPattern(dots: List<Int>): Boolean {
        val stored = store.data.first()[PATTERN_HASH] ?: return false
        return withContext(Dispatchers.Default) { Pattern.verify(dots, stored) }
    }

    override suspend fun startFocus(session: FocusSession) {
        store.edit {
            it[FOCUS_END] = session.endsAt.toEpochMilli()
            it[FOCUS_TARGETS] = session.targets.map(::encodeTarget).toSet()
        }
    }

    override suspend fun endFocus() {
        store.edit {
            it.remove(FOCUS_END)
            it.remove(FOCUS_TARGETS)
        }
    }

    override suspend fun addTemporaryAllow(allow: TemporaryAllow) {
        val now = clock.instant()
        store.edit { prefs ->
            // Old passes are dropped here so the stored set never grows.
            val kept = (prefs[ALLOWS] ?: emptySet()).filter { decodeAllow(it)?.until?.isAfter(now) == true }
            prefs[ALLOWS] = (kept + encodeAllow(allow)).toSet()
        }
    }

    override suspend fun checkpoint(): SessionCheckpoint? {
        val parts = store.data.first()[CHECKPOINT]?.split('|') ?: return null
        if (parts.size != 3) return null
        val start = parts[1].toLongOrNull() ?: return null
        val seen = parts[2].toLongOrNull() ?: return null
        return SessionCheckpoint(parts[0], start, seen)
    }

    override suspend fun saveCheckpoint(checkpoint: SessionCheckpoint?) {
        store.edit {
            if (checkpoint == null) {
                it.remove(CHECKPOINT)
            } else {
                it[CHECKPOINT] = "${checkpoint.packageName}|${checkpoint.startMillis}|${checkpoint.lastSeenMillis}"
            }
        }
    }

    override suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private companion object {
        val COOLDOWN = intPreferencesKey("cooldown_seconds")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PATTERN_HASH = stringPreferencesKey("pattern_hash")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BLOCK_EXIT = stringPreferencesKey("block_exit")
        val EXCEPTIONS = stringSetPreferencesKey("exceptions")
        val FOCUS_END = longPreferencesKey("focus_end_millis")
        val FOCUS_TARGETS = stringSetPreferencesKey("focus_targets")
        val ALLOWS = stringSetPreferencesKey("temporary_allows")
        val CHECKPOINT = stringPreferencesKey("session_checkpoint")

        fun encodeTarget(target: Target): String = when (target) {
            is Target.App -> "app:${target.packageName}"
            is Target.Feature -> "feature:${target.packageName}:${target.featureId}"
        }

        fun decodeTarget(text: String): Target? {
            val parts = text.split(':')
            return when {
                parts.size == 2 && parts[0] == "app" -> Target.App(parts[1])
                parts.size == 3 && parts[0] == "feature" -> Target.Feature(parts[1], parts[2])
                else -> null
            }
        }

        fun encodeAllow(allow: TemporaryAllow) = "${allow.packageName}|${allow.until.toEpochMilli()}"

        fun decodeAllow(text: String): TemporaryAllow? {
            val parts = text.split('|')
            val until = parts.getOrNull(1)?.toLongOrNull() ?: return null
            return if (parts.size == 2) TemporaryAllow(parts[0], Instant.ofEpochMilli(until)) else null
        }
    }
}
