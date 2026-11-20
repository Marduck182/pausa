@file:OptIn(ExperimentalMaterial3Api::class)

package app.pausa.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.domain.model.BlockMode
import app.pausa.domain.model.Restriction
import app.pausa.domain.model.RuleDraft
import app.pausa.domain.model.ScheduleMode
import app.pausa.domain.model.TimeWindow
import app.pausa.ui.common.CreatePatternDialog
import app.pausa.ui.common.DayToggle
import app.pausa.ui.common.Dialog
import app.pausa.ui.common.RuleText
import app.pausa.ui.common.Section
import app.pausa.ui.common.Stepper
import app.pausa.ui.common.TimeDialog
import app.pausa.ui.common.rememberFriction
import java.time.DayOfWeek

@Composable
fun EditRuleScreen(
    onClose: () -> Unit,
    onOpenRule: (Long) -> Unit,
    viewModel: EditRuleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val friction = rememberFriction()
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var creatingPattern by remember { mutableStateOf(false) }
    /** Which window and which end is being edited with the time picker, if any. */
    var picking by remember { mutableStateOf<TimePick?>(null) }

    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    val requestBack = { if (state.dirty) confirmDiscard = true else onClose() }
    BackHandler(onBack = requestBack)

    val draft = state.draft
    val isFeature = draft?.featureId != null

    // Turning a rule off, or changing one that is blocking right now, is where an impulse does its work: it gets a pause.
    val savingNeedsPause = draft != null && (
        (state.original?.enabled == true && !draft.enabled) ||
        (state.original?.patternLock == true && !draft.patternLock) ||
        state.blockingNow
    )
    val save = {
        if (savingNeedsPause) {
            friction.ask(
                title = "¿Guardar estos cambios?",
                message = when {
                    state.blockingNow -> "Esta regla está bloqueando ahora mismo."
                    !draft.patternLock && state.original?.patternLock == true -> "Vas a quitar el patrón de esta app."
                    else -> "Vas a apagar esta regla."
                },
                confirmLabel = "Guardar",
                onConfirmed = viewModel::save,
            )
        } else {
            viewModel.save()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.label) },
                navigationIcon = {
                    IconButton(onClick = requestBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    TextButton(onClick = save, enabled = draft != null && draft.isValid && state.dirty && !state.locked) {
                        Text("Guardar")
                    }
                },
            )
        },
    ) { padding ->
        when {
            draft == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.locked -> LockedNotice(draft, Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Section(
                    title = "Controlada",
                    description = "Si la apagas, Pausa ignora esto sin borrar tus reglas.",
                    checked = draft.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )

                BlockSection(draft, viewModel, onPick = { picking = it })

                if (!isFeature) {
                    Section(
                        title = "Pedir patrón para entrar",
                        description = "Antes de abrir la app tendrás que dibujar tu patrón de seguridad. " +
                            "Un permiso temporal no lo salta.",
                        checked = draft.patternLock,
                        onCheckedChange = { on ->
                            // The first time there is no pattern yet: it is created right here.
                            if (on && !state.patternSet) creatingPattern = true else viewModel.setPatternLock(on)
                        },
                    )

                    Section(
                        title = "Tiempo al día",
                        description = "Se bloquea al llegar al límite y vuelve mañana.",
                        checked = draft.dailyLimitMinutes != null,
                        onCheckedChange = { viewModel.setDailyLimit(if (it) 30 else null) },
                    ) {
                        Stepper(
                            label = "Máximo",
                            value = draft.dailyLimitMinutes ?: 30,
                            onValueChange = viewModel::setDailyLimit,
                            range = 5..480,
                            step = 5,
                            format = ::formatMinutes,
                        )
                    }

                    Section(
                        title = "Aperturas al día",
                        description = "Cuántas veces puedes abrirla. Una sesión ya abierta no se corta.",
                        checked = draft.maxSessionsPerDay != null,
                        onCheckedChange = { viewModel.setMaxSessions(if (it) 3 else null) },
                    ) {
                        Stepper(
                            label = "Máximo",
                            value = draft.maxSessionsPerDay ?: 3,
                            onValueChange = viewModel::setMaxSessions,
                            range = 1..20,
                        )
                    }
                }

                val intervention = draft.intervention
                Section(
                    title = if (isFeature) "Pausa al entrar" else "Pausa antes de abrir",
                    description = "Unos segundos para decidir si de verdad quieres entrar.",
                    checked = intervention != null,
                    onCheckedChange = { viewModel.setIntervention(if (it) Restriction.Intervention() else null) },
                ) {
                    if (intervention != null) {
                        Stepper(
                            label = "Espera",
                            value = intervention.waitSeconds,
                            onValueChange = { viewModel.setIntervention(intervention.copy(waitSeconds = it)) },
                            range = 0..60,
                            step = 5,
                            format = { "$it s" },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ejercicio de respiración", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(
                                checked = intervention.breathing,
                                onCheckedChange = { viewModel.setIntervention(intervention.copy(breathing = it)) },
                            )
                        }
                    }
                }

                if (state.features.isNotEmpty()) {
                    Section(
                        title = "Dentro de la app",
                        description = "Bloquea una sección sin bloquear toda la app. Funciona mirando la pantalla, y como las apps cambian, a veces puede fallar.",
                    ) {
                        state.features.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.feature.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (entry.ruleId == null) "Sin configurar" else "Configurada",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.openFeature(entry, onOpenRule) }) {
                                    Text(if (entry.ruleId == null) "Bloquear" else "Configurar")
                                }
                            }
                        }
                    }
                }

                Section(
                    title = "Modo difícil",
                    description = "Mientras esta regla esté bloqueando, no podrás cambiarla, apagarla ni borrarla. " +
                        "Esperas a que termine el bloqueo, como si no hubiera salida. Siempre puedes desinstalar la app.",
                    checked = draft.hardMode,
                    onCheckedChange = viewModel::setHardMode,
                )

                if (state.blockingNow) {
                    Section(
                        title = "Permiso temporal",
                        description = "Deja pasar esta app 10 minutos. Reinicia tu racha de días seguidos.",
                    ) {
                        OutlinedButton(
                            onClick = {
                                friction.ask(
                                    title = "¿Permitir 10 minutos?",
                                    message = "Esto reinicia tu racha de días seguidos.",
                                    confirmLabel = "Permitir",
                                    onConfirmed = viewModel::allowTemporarily,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Permitir 10 minutos") }
                    }
                }

                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(if (isFeature) "Quitar esta regla" else "Dejar de controlar esta app")
                }
            }
        }
    }

    val current = picking
    if (current != null && draft != null) {
        val window = draft.windows.getOrNull(current.index)
        if (window != null) {
            TimeDialog(
                initialMinute = if (current.isStart) window.startMinute else window.endMinute,
                onConfirm = { minute ->
                    if (current.isStart) viewModel.setWindowStart(current.index, minute) else viewModel.setWindowEnd(current.index, minute)
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
    }

    if (creatingPattern) {
        CreatePatternDialog(
            onCreated = { dots ->
                creatingPattern = false
                viewModel.createPattern(dots)
            },
            onDismiss = { creatingPattern = false },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar los cambios?") },
            text = { Text("Lo que cambiaste no se ha guardado.") },
            confirmButton = { TextButton(onClick = onClose) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Quitar ${state.label}?") },
            text = { Text("Se borrarán sus reglas. Podrás volver a añadirla cuando quieras.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        // For now removing a rule does not wait (the user asked for that); a PIN, if set, is still asked.
                        friction.ask(
                            title = "Quitar ${state.label}",
                            message = "Dejará de estar protegida.",
                            confirmLabel = "Quitar",
                            wait = false,
                            onConfirmed = viewModel::delete,
                        )
                    },
                ) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
    friction.Dialog()
}

private data class TimePick(val index: Int, val isStart: Boolean)

/** Shown instead of the editor while a hard-mode rule is blocking: it can be read, not changed. */
@Composable
private fun LockedNotice(draft: RuleDraft, modifier: Modifier = Modifier) {
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Modo difícil activo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Esta regla está bloqueando ahora, así que no se puede cambiar, apagar ni borrar hasta que termine el bloqueo. " +
                        "Esa es la idea: que el impulso de este momento no pueda con lo que decidiste con calma.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Text(RuleText.summary(draft), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BlockSection(draft: RuleDraft, viewModel: EditRuleViewModel, onPick: (TimePick) -> Unit) {
    Section(title = "Bloqueo") {
        val options = listOf(BlockMode.NONE to "Libre", BlockMode.ALWAYS to "Siempre", BlockMode.SCHEDULE to "Horario")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = draft.blockMode == mode,
                    onClick = { viewModel.setBlockMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
        Text(
            text = when (draft.blockMode) {
                BlockMode.NONE -> "Sin bloqueo por defecto. Los límites y la pausa de abajo siguen aplicando."
                BlockMode.ALWAYS -> "No podrás entrar mientras la regla esté activa."
                BlockMode.SCHEDULE -> "Elige cuándo está bloqueada, o cuándo es el único momento en que puedes usarla."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (draft.blockMode == BlockMode.SCHEDULE) {
            val modes = listOf(ScheduleMode.BLOCK_DURING to "Bloquear en…", ScheduleMode.ALLOW_ONLY_DURING to "Solo permitir en…")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = draft.scheduleMode == mode,
                        onClick = { viewModel.setScheduleMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(label) }
                }
            }
            draft.windows.forEachIndexed { index, window ->
                WindowCard(
                    window = window,
                    onToggleDay = { day, selected -> viewModel.toggleDay(index, day, selected) },
                    onPickStart = { onPick(TimePick(index, isStart = true)) },
                    onPickEnd = { onPick(TimePick(index, isStart = false)) },
                    onRemove = { viewModel.removeWindow(index) },
                )
            }
            TextButton(onClick = viewModel::addWindow) { Text("Añadir otro horario") }
            TextButton(onClick = viewModel::useWorkdayPreset) { Text("Usar horario laboral (Lun–Vie, 9–14 y 16–21)") }
        }
    }
}

@Composable
private fun WindowCard(
    window: TimeWindow,
    onToggleDay: (DayOfWeek, Boolean) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.values().forEach { day ->
                    DayToggle(
                        letter = RuleText.dayLetters.getValue(day),
                        fullName = RuleText.dayFullNames.getValue(day),
                        selected = day in window.days,
                        onToggle = { onToggleDay(day, it) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f)) {
                    Text("Desde ${RuleText.clock(window.startMinute)}")
                }
                OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
                    Text("Hasta ${RuleText.clock(window.endMinute)}")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar este horario")
                }
            }
            if (window.crossesMidnight) {
                Text(
                    "Termina al día siguiente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
