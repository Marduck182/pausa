package app.pausa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.ThemeMode
import app.pausa.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Null until the setting is read, so the first-run screen never flashes for someone who already finished it. */
    val onboardingDone: StateFlow<Boolean?> = settings.settings
        .map<_, Boolean?> { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until the settings are read, together with [onboardingDone]: nothing is drawn in the wrong look first. */
    val themeMode: StateFlow<ThemeMode?> = settings.settings
        .map<_, ThemeMode?> { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun finishOnboarding() {
        viewModelScope.launch { settings.setOnboardingDone() }
    }
}
