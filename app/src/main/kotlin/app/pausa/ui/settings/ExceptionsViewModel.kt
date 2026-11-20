package app.pausa.ui.settings

import app.pausa.R
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.InstalledApp
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.SettingsRepository
import app.pausa.domain.usecase.RuleLockChecker
import app.pausa.enforcement.SystemPackages
import app.pausa.ui.apps.RuleMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val locks: RuleLockChecker,
    installedApps: InstalledAppsRepository,
    systemPackages: SystemPackages,
) : ViewModel() {

    private val _notice = MutableStateFlow<Int?>(null)

    /** Why the last action was not done, or null. Shown in a dialog until the person dismisses it. */
    val notice: StateFlow<Int?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

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
            try {
                // An exception lets an app through, so it waits while a focus session or a strict block is on.
                if (allowed && locks.isAnyProtectionActive()) {
                    _notice.value = R.string.exc_notice_blocked
                    return@launch
                }
                // One atomic change: two quick toggles must not overwrite each other with a stale copy of the set.
                if (allowed) settings.addException(packageName) else settings.removeException(packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PausaExceptions", "Could not change exception for $packageName", e)
                _notice.value = RuleMessages.SAVE_FAILED
            }
        }
    }
}
