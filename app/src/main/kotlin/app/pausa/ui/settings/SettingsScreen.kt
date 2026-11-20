package app.pausa.ui.settings

import app.pausa.R
import androidx.compose.ui.res.stringResource
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
        ScreenTitle(stringResource(R.string.settings_title))

        Section(title = stringResource(R.string.settings_appearance)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                ).forEach { (mode, label) ->
                    PillChip(text = label, onClick = { viewModel.setThemeMode(mode) }, selected = settings.themeMode == mode)
                }
            }
        }

        Section(
            title = stringResource(R.string.settings_on_block),
            description = if (settings.blockExit == BlockExit.BACK) stringResource(R.string.settings_on_block_back_note) else null,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    BlockExit.HOME to stringResource(R.string.settings_exit_home),
                    BlockExit.BACK to stringResource(R.string.settings_exit_back),
                ).forEach { (exit, label) ->
                    PillChip(text = label, onClick = { viewModel.setBlockExit(exit) }, selected = settings.blockExit == exit)
                }
            }
        }

        Section(title = stringResource(R.string.settings_protection)) {
            Text(
                stringResource(if (settings.pinEnabled) R.string.settings_pin_on else R.string.settings_pin_off),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!settings.pinEnabled) {
                Button(onClick = { settingPin = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_pin_create)) }
            } else {
                OutlinedButton(
                    onClick = {
                        friction.ask(
                            title = friction.text(R.string.settings_pin_change_title),
                            message = friction.text(R.string.settings_pin_change_message),
                            confirmLabel = friction.text(R.string.settings_continue),
                        ) { settingPin = true }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_pin_change)) }
                OutlinedButton(
                    onClick = {
                        friction.ask(
                            title = friction.text(R.string.settings_pin_remove_title),
                            message = friction.text(R.string.settings_pin_remove_message),
                            confirmLabel = friction.text(R.string.settings_remove),
                            onConfirmed = viewModel::removePin,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_pin_remove)) }
            }
            Stepper(
                label = stringResource(R.string.settings_cooldown_label),
                value = settings.cooldownSeconds,
                onValueChange = { seconds ->
                    if (seconds < settings.cooldownSeconds) {
                        friction.ask(
                            title = friction.text(R.string.settings_cooldown_shorten_title),
                            message = friction.text(R.string.settings_cooldown_shorten_message),
                            confirmLabel = friction.text(R.string.settings_shorten),
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

        Section(title = stringResource(R.string.settings_pattern)) {
            Text(
                stringResource(if (settings.patternEnabled) R.string.settings_pattern_on else R.string.settings_pattern_off),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!settings.patternEnabled) {
                Button(onClick = { patternStep = PatternStep.Create }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_pattern_create)) }
            } else {
                OutlinedButton(onClick = { patternStep = PatternStep.VerifyToChange }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_pattern_change))
                }
                OutlinedButton(onClick = { patternStep = PatternStep.VerifyToRemove }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_pattern_remove))
                }
            }
        }

        Section(
            title = stringResource(R.string.settings_exceptions),
            description = stringResource(R.string.settings_exceptions_description),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenExceptions)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (settings.exceptions.isEmpty()) {
                        stringResource(R.string.settings_exceptions_none)
                    } else {
                        friction.quantity(R.plurals.settings_exceptions_count, settings.exceptions.size, settings.exceptions.size)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.settings_edit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }

        Section(
            title = stringResource(R.string.settings_permissions),
            description = stringResource(if (serviceEnabled) R.string.settings_accessibility_on else R.string.settings_accessibility_off),
        ) {
            // The long explanation is folded away; with the service off it is what the person needs, so it opens by itself.
            var expanded by remember(serviceEnabled) { mutableStateOf(!serviceEnabled) }
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_permissions_what), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(stringResource(if (expanded) R.string.settings_hide else R.string.settings_show), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
            if (expanded) {
                PermissionGuide()
                ActivationHelp()
            }
        }

        Section(title = stringResource(R.string.settings_privacy)) {
            Text(
                stringResource(R.string.settings_privacy_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { confirmErase = true },
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.error,
            ) { Text(stringResource(R.string.settings_erase_all)) }
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
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
            title = stringResource(R.string.settings_pattern_change_title),
            message = stringResource(R.string.settings_pattern_change_message),
            verify = viewModel::verifyPattern,
            onVerified = { patternStep = PatternStep.Create },
            onDismiss = { patternStep = null },
        )
        PatternStep.VerifyToRemove -> VerifyPatternDialog(
            title = stringResource(R.string.settings_pattern_remove_title),
            message = stringResource(R.string.settings_pattern_remove_message),
            verify = viewModel::verifyPattern,
            onVerified = { viewModel.removePattern(); patternStep = null },
            onDismiss = { patternStep = null },
        )
        null -> Unit
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.settings_erase_title)) },
            text = { Text(stringResource(R.string.settings_erase_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmErase = false
                        friction.ask(
                            title = friction.text(R.string.settings_erase_confirm_title),
                            message = friction.text(R.string.settings_erase_confirm_message),
                            confirmLabel = friction.text(R.string.settings_delete),
                            onConfirmed = viewModel::eraseEverything,
                        )
                    },
                ) { Text(stringResource(R.string.settings_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text(stringResource(R.string.settings_cancel)) } },
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
    // Resolved here: the click handler below is not composable.
    val lengthError = stringResource(R.string.settings_pin_length_error, PinHasher.MIN_LENGTH, PinHasher.MAX_LENGTH)
    val mismatchError = stringResource(R.string.settings_pin_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_pin_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_pin_hint, PinHasher.MIN_LENGTH, PinHasher.MAX_LENGTH),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PinField(stringResource(R.string.settings_pin_field), pin) { pin = it; error = null }
                PinField(stringResource(R.string.settings_pin_repeat), again) { again = it; error = null }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !PinHasher.isValid(pin) -> error = lengthError
                        pin != again -> error = mismatchError
                        else -> onSave(pin)
                    }
                },
            ) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
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
