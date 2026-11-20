package app.pausa.ui.settings

import app.pausa.ui.common.AlertDialog
import app.pausa.domain.model.BlockExit
import app.pausa.ui.common.OutlinedButton
import app.pausa.ui.common.Button
import app.pausa.ui.common.PillChip
import app.pausa.domain.model.ThemeMode
import app.pausa.ui.common.NoticeDialog
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
import androidx.compose.material3.MaterialTheme
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

        Section(title = "Apariencia") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro").forEach { (mode, label) ->
                    PillChip(text = label, onClick = { viewModel.setThemeMode(mode) }, selected = settings.themeMode == mode)
                }
            }
        }

        Section(
            title = "Al bloquear una app",
            description = if (settings.blockExit == BlockExit.BACK) "Si volver atrás no la cierra, te lleva al inicio." else null,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(BlockExit.HOME to "Ir al inicio", BlockExit.BACK to "Volver atrás").forEach { (exit, label) ->
                    PillChip(text = label, onClick = { viewModel.setBlockExit(exit) }, selected = settings.blockExit == exit)
                }
            }
        }

        Section(title = "Protección") {
            Text(
                if (settings.pinEnabled) "PIN activado." else "Sin PIN.",
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
                label = "Espera al apagar reglas",
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

        Section(title = "Patrón de seguridad") {
            Text(
                if (settings.patternEnabled) "Patrón activado. Se pide en las apps que lo tengan en su regla." else "Sin patrón.",
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
            description = "Apps que siempre pasan.",
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
            description = if (serviceEnabled) "Accesibilidad activa." else "Accesibilidad apagada: no se bloquea nada.",
        ) {
            // The long explanation is folded away; with the service off it is what the person needs, so it opens by itself.
            var expanded by remember(serviceEnabled) { mutableStateOf(!serviceEnabled) }
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Qué pide Pausa y por qué", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(if (expanded) "Ocultar" else "Ver", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            if (expanded) {
                PermissionGuide()
                ActivationHelp()
            }
        }

        Section(title = "Privacidad") {
            Text(
                "Todo se queda en este teléfono: tus reglas, el uso de las apps que controlas (30 días) y tus ajustes. " +
                    "Sin cuenta ni internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { confirmErase = true },
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.error,
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
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    NoticeDialog(notice, viewModel::dismissNotice)
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
