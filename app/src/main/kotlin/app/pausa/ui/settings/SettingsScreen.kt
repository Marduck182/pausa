package app.pausa.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.BuildConfig
import app.pausa.domain.model.AppSettings
import app.pausa.domain.security.PinHasher
import app.pausa.enforcement.AccessibilityStatus
import app.pausa.ui.common.CreatePatternDialog
import app.pausa.ui.common.Dialog
import app.pausa.ui.common.VerifyPatternDialog
import app.pausa.ui.common.ScreenTitle
import app.pausa.ui.common.Section
import app.pausa.ui.common.Stepper
import app.pausa.ui.common.rememberFriction

@Composable
fun SettingsScreen(
    onOpenExceptions: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val friction = rememberFriction()
    val context = LocalContext.current

    var serviceEnabled by remember { mutableStateOf(AccessibilityStatus.isServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) serviceEnabled = AccessibilityStatus.isServiceEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var settingPin by remember { mutableStateOf(false) }
    /** What the pattern dialogs are doing: creating a new one, or proving the old one first. */
    var patternStep by remember { mutableStateOf<PatternStep?>(null) }
    var confirmErase by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        ScreenTitle("Ajustes")

        Section(
            title = "Protección contra impulsos",
            description = "Un poco de fricción cuando vayas a apagar algo de lo que tú mismo configuraste.",
        ) {
            Text(
                if (settings.pinEnabled) "PIN activado." else "Sin PIN. Con un PIN, apagar reglas o pedir permisos también lo pide.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!settings.pinEnabled) {
                Button(onClick = { settingPin = true }, modifier = Modifier.fillMaxWidth()) { Text("Crear PIN") }
            } else {
                OutlinedButton(
                    onClick = {
                        friction.ask(
                            title = "Cambiar el PIN",
                            message = "Escribe tu PIN actual para poder cambiarlo.",
                            confirmLabel = "Continuar",
                        ) { settingPin = true }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cambiar PIN") }
                OutlinedButton(
                    onClick = {
                        friction.ask(
                            title = "Quitar el PIN",
                            message = "Sin PIN será más fácil saltarte tus propias reglas.",
                            confirmLabel = "Quitar",
                            onConfirmed = viewModel::removePin,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quitar PIN") }
            }
            Stepper(
                label = "Espera antes de acciones delicadas",
                value = settings.cooldownSeconds,
                onValueChange = { seconds ->
                    if (seconds < settings.cooldownSeconds) {
                        friction.ask(
                            title = "¿Acortar la espera?",
                            message = "Una espera más corta da menos tiempo para pensarlo.",
                            confirmLabel = "Acortar",
                        ) { viewModel.setCooldown(seconds) }
                    } else {
                        viewModel.setCooldown(seconds)
                    }
                },
                range = 0..AppSettings.MAX_COOLDOWN_SECONDS,
                step = 5,
                format = { "$it s" },
            )
        }

        Section(
            title = "Patrón de seguridad",
            description = "Para pedir un patrón antes de abrir una app. Lo activas app por app, en su regla.",
        ) {
            Text(
                if (settings.patternEnabled) "Patrón activado." else "Sin patrón.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!settings.patternEnabled) {
                Button(onClick = { patternStep = PatternStep.Create }, modifier = Modifier.fillMaxWidth()) { Text("Crear patrón") }
            } else {
                OutlinedButton(onClick = { patternStep = PatternStep.VerifyToChange }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cambiar patrón")
                }
                OutlinedButton(onClick = { patternStep = PatternStep.VerifyToRemove }, modifier = Modifier.fillMaxWidth()) {
                    Text("Quitar patrón")
                }
            }
        }

        Section(
            title = "Excepciones",
            description = "Apps que siempre pasan, aunque haya un bloqueo o una concentración: mensajes, mapas, correo…",
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenExceptions)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (settings.exceptions.isEmpty()) "Ninguna" else "${settings.exceptions.size} ${if (settings.exceptions.size == 1) "app" else "apps"}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text("Editar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }

        Section(
            title = "Permisos",
            description = if (serviceEnabled) "El servicio de accesibilidad está activo." else "El servicio de accesibilidad está apagado: Pausa no bloquea nada.",
        ) {
            PermissionGuide()
            ActivationHelp()
        }

        Section(title = "Privacidad", description = "Lo que Pausa guarda en este teléfono y nada más.") {
            Text(
                "Tus reglas, el tiempo de uso de las apps que controlas (30 días), las veces que te frenó y tus ajustes. " +
                    "No hay cuenta, ni internet, ni copia en la nube: Android no la hace con esta app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { confirmErase = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Borrar todos mis datos") }
        }

        Text(
            "Pausa ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }

    if (settingPin) {
        PinDialog(
            onDismiss = { settingPin = false },
            onSave = { pin ->
                val saved = viewModel.setPin(pin)
                if (saved) settingPin = false
                saved
            },
        )
    }
    when (patternStep) {
        PatternStep.Create -> CreatePatternDialog(
            onCreated = { dots -> viewModel.setPattern(dots); patternStep = null },
            onDismiss = { patternStep = null },
        )
        PatternStep.VerifyToChange -> VerifyPatternDialog(
            title = "Cambiar el patrón",
            message = "Dibuja tu patrón actual para poder cambiarlo.",
            verify = viewModel::verifyPattern,
            onVerified = { patternStep = PatternStep.Create },
            onDismiss = { patternStep = null },
        )
        PatternStep.VerifyToRemove -> VerifyPatternDialog(
            title = "Quitar el patrón",
            message = "Dibuja tu patrón actual. Las apps que lo piden dejarán de pedirlo.",
            verify = viewModel::verifyPattern,
            onVerified = { viewModel.removePattern(); patternStep = null },
            onDismiss = { patternStep = null },
        )
        null -> Unit
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("¿Borrar todos tus datos?") },
            text = { Text("Se borran tus reglas, el uso, las estadísticas, las excepciones y el PIN. No se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmErase = false
                        friction.ask(
                            title = "Borrar todo",
                            message = "Última confirmación: Pausa volverá a estar como recién instalada.",
                            confirmLabel = "Borrar",
                            onConfirmed = viewModel::eraseEverything,
                        )
                    },
                ) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } },
        )
    }
    friction.Dialog()
}

private enum class PatternStep { Create, VerifyToChange, VerifyToRemove }

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "De ${PinHasher.MIN_LENGTH} a ${PinHasher.MAX_LENGTH} dígitos. No es una contraseña bancaria: sirve para frenar un impulso.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField("PIN", pin) { pin = it; error = null }
                PinField("Repite el PIN", again) { again = it; error = null }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !PinHasher.isValid(pin) -> error = "Usa entre ${PinHasher.MIN_LENGTH} y ${PinHasher.MAX_LENGTH} dígitos."
                        pin != again -> error = "Los dos PIN no coinciden."
                        else -> onSave(pin)
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= PinHasher.MAX_LENGTH && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
