package app.pausa.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.StatsSnapshot
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.ui.common.StatsLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val stats: StatsSnapshot? = null,
    val labels: Map<String, String> = emptyMap(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val loader: StatsLoader,
    private val installedApps: InstalledAppsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Reads the history again; called every time the screen comes back to the front. */
    fun refresh() {
        viewModelScope.launch {
            val labels = installedApps.getLaunchableApps().associate { it.packageName to it.label }
            _state.value = StatsUiState(loading = false, stats = loader.load(), labels = labels)
        }
    }
}
