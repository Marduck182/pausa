package app.pausa.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pausa.domain.model.AppSettings
import app.pausa.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FrictionViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    suspend fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)
}

/**
 * A deliberate pause in front of something that weakens the user's own protection: turning a rule off, deleting it,
 * asking for a pass, ending focus early. It waits a few seconds and, if the user set a PIN, asks for it. The aim is
 * to let the impulse pass, not to lock the user out: nothing here touches the operating system.
 */
@Stable
class Friction internal constructor(
    internal val cooldownSeconds: Int,
    internal val pinRequired: Boolean,
    internal val verifyPin: suspend (String) -> Boolean,
) {
    internal class Pending(
        val title: String,
        val message: String,
        val confirmLabel: String,
        /** False skips the wait; a PIN, if the user set one, is still asked. */
        val wait: Boolean,
        val onConfirmed: () -> Unit,
    )

    internal var pending by mutableStateOf<Pending?>(null)

    fun ask(title: String, message: String, confirmLabel: String, wait: Boolean = true, onConfirmed: () -> Unit) {
        // Nothing to wait for and no PIN to ask: there is nothing left to confirm, so go ahead.
        if (!wait && !pinRequired) {
            onConfirmed()
            return
        }
        pending = Pending(title, message, confirmLabel, wait, onConfirmed)
    }
}

@Composable
fun rememberFriction(viewModel: FrictionViewModel = hiltViewModel()): Friction {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    return remember(settings.cooldownSeconds, settings.pinEnabled, viewModel) {
        Friction(settings.cooldownSeconds, settings.pinEnabled, viewModel::verifyPin)
    }
}

/** Draws the confirmation, if one was asked for. Put it once in the screen that owns the [Friction]. */
@Composable
fun Friction.Dialog() {
    val request = pending ?: return
    var remaining by remember(request) { mutableIntStateOf(if (request.wait) cooldownSeconds else 0) }
    var pin by remember(request) { mutableStateOf("") }
    var wrong by remember(request) { mutableStateOf(false) }
    var failures by remember(request) { mutableIntStateOf(0) }
    var lockedSeconds by remember(request) { mutableIntStateOf(0) }
    var checking by remember(request) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(request) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }
    LaunchedEffect(lockedSeconds > 0) {
        while (lockedSeconds > 0) {
            delay(1_000)
            lockedSeconds--
        }
    }

    val canConfirm = remaining == 0 && lockedSeconds == 0 && !checking && (!pinRequired || pin.length >= 4)

    AlertDialog(
        onDismissRequest = { pending = null },
        title = { Text(request.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(request.message, style = MaterialTheme.typography.bodyMedium)
                if (pinRequired) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { text -> if (text.length <= 8 && text.all(Char::isDigit)) { pin = text; wrong = false } },
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = wrong,
                        supportingText = {
                            when {
                                lockedSeconds > 0 -> Text("Demasiados intentos. Espera $lockedSeconds s.")
                                wrong -> Text("PIN incorrecto.")
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (remaining > 0) {
                    Text(
                        "Respira. Podrás continuar en $remaining s.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = {
                    if (!pinRequired) {
                        pending = null
                        request.onConfirmed()
                    } else {
                        checking = true
                        scope.launch {
                            val ok = verifyPin(pin)
                            checking = false
                            if (ok) {
                                pending = null
                                request.onConfirmed()
                            } else {
                                wrong = true
                                pin = ""
                                failures++
                                if (failures >= MAX_FAILURES) {
                                    failures = 0
                                    lockedSeconds = LOCK_SECONDS
                                }
                            }
                        }
                    }
                },
            ) { Text(request.confirmLabel) }
        },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("Mejor no") } },
    )
}

private const val MAX_FAILURES = 5
private const val LOCK_SECONDS = 30
