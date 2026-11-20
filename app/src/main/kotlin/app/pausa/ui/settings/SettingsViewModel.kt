package app.pausa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.AppSettings
import app.pausa.domain.repository.DataEraser
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val eraser: DataEraser,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

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
        viewModelScope.launch { eraser.eraseAll() }
    }
}
