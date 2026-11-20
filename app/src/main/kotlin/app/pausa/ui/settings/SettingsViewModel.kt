package app.pausa.ui.settings

import app.pausa.R
import app.pausa.domain.model.BlockExit
import app.pausa.domain.model.ThemeMode
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.AppSettings
import app.pausa.domain.repository.DataEraser
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.security.PinHasher
import app.pausa.domain.usecase.RuleLockChecker
import app.pausa.ui.apps.RuleMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val eraser: DataEraser,
    private val locks: RuleLockChecker,
) : ViewModel() {

    private val _notice = MutableStateFlow<Int?>(null)

    /** Why the last action was not done, or null. Shown in a dialog until the person dismisses it. */
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setBlockExit(exit: BlockExit) {
        viewModelScope.launch { repository.setBlockExit(exit) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setCooldown(seconds: Int) {
        viewModelScope.launch { repository.setCooldownSeconds(seconds) }
    }

    /** Returns false, without saving, if [pin] is not four to eight digits. */
    fun setPin(pin: String): Boolean {
        if (!PinHasher.isValid(pin)) return false
        viewModelScope.launch { repository.setPin(pin) }
        return true
    }

    /** Saves a new security pattern (already checked by the screen that asked for it twice). */
    fun setPattern(dots: List<Int>) {
        viewModelScope.launch { repository.setPattern(dots) }
    }

    suspend fun verifyPattern(dots: List<Int>): Boolean = repository.verifyPattern(dots)

    fun removePattern() {
        viewModelScope.launch { repository.setPattern(null) }
    }

    fun removePin() {
        viewModelScope.launch { repository.setPin(null) }
    }

    /** Deletes everything Pausa stored; the app goes back to its first-run state. */
    fun eraseEverything() {
        viewModelScope.launch {
            try {
                // Erasing deletes every rule: while one is protecting the person right now, it would be a way around it.
                if (locks.isAnyProtectionActive()) {
                    _notice.value = R.string.settings_notice_erase_blocked
                    return@launch
                }
                eraser.eraseAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PausaSettings", "Could not erase data", e)
                _notice.value = RuleMessages.SAVE_FAILED
            }
        }
    }
}
