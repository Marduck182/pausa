package app.pausa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.InstalledApp
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.enforcement.SystemPackages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExceptionRow(val app: InstalledApp, val allowed: Boolean)

data class ExceptionsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val rows: List<ExceptionRow> = emptyList(),
)

@HiltViewModel
class ExceptionsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    installedApps: InstalledAppsRepository,
    systemPackages: SystemPackages,
) : ViewModel() {

    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            // The phone, settings and the launcher always pass already, so listing them would only confuse.
            val untouchable = systemPackages.resolve().protectedPackages
            apps.value = installedApps.getLaunchableApps().filter { it.packageName !in untouchable }
        }
    }

    val state: StateFlow<ExceptionsUiState> = combine(apps, settings.settings, query) { apps, settings, q ->
        if (apps == null) {
            ExceptionsUiState(loading = true, query = q)
        } else {
            val needle = q.trim().lowercase()
            val rows = apps
                .filter { needle.isEmpty() || it.label.lowercase().contains(needle) }
                .map { ExceptionRow(it, it.packageName in settings.exceptions) }
                // The ones already allowed come first, so what is set is easy to find and to undo.
                .sortedWith(compareByDescending<ExceptionRow> { it.allowed }.thenBy { it.app.label.lowercase() })
            ExceptionsUiState(loading = false, query = q, rows = rows)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExceptionsUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch {
            val current = settings.settings.first().exceptions
            settings.setExceptions(if (allowed) current + packageName else current - packageName)
        }
    }
}
