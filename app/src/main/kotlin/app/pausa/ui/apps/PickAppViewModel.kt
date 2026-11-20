package app.pausa.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pausa.enforcement.SystemPackages
import app.pausa.domain.model.InstalledApp
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.Target
import app.pausa.domain.repository.InstalledAppsRepository
import app.pausa.domain.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickUiState(
    val loading: Boolean = true,
    val query: String = "",
    val apps: List<InstalledApp> = emptyList(),
)

@HiltViewModel
class PickAppViewModel @Inject constructor(
    private val rules: RuleRepository,
    installedApps: InstalledAppsRepository,
    private val systemPackages: SystemPackages,
) : ViewModel() {

    private val allApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val alreadyControlled = rules.getRules().map { it.target.packageName }.toSet()
            // Never offered: Pausa itself, the phone, settings and the launcher. Blocking them would lock the
            // user out of their own rules or out of calling for help.
            val untouchable = systemPackages.resolve().protectedPackages
            allApps.value = installedApps.getLaunchableApps()
                .filter { it.packageName !in alreadyControlled && it.packageName !in untouchable }
        }
    }

    val state: StateFlow<PickUiState> = combine(allApps, query) { apps, q ->
        if (apps == null) {
            PickUiState(loading = true, query = q)
        } else {
            val needle = q.trim().lowercase()
            PickUiState(
                loading = false,
                query = q,
                apps = if (needle.isEmpty()) apps else apps.filter { it.label.lowercase().contains(needle) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    /** Starts controlling [packageName] with a gentle pause only, then reports the new rule so it can be edited. */
    fun pick(packageName: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val existing = rules.getRules().firstOrNull { it.target == Target.App(packageName) }
            val id = existing?.id ?: rules.upsert(RuleDraft.newFor(packageName).toRule())
            onCreated(id)
        }
    }
}
