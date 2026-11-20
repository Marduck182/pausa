@file:OptIn(ExperimentalMaterial3Api::class)

package app.pausa.ui.apps

import androidx.compose.ui.platform.LocalContext
import app.pausa.R
import androidx.compose.ui.res.stringResource
import app.pausa.ui.common.AlertDialog
import app.pausa.ui.common.Switch
import app.pausa.ui.common.OutlinedButton
import app.pausa.ui.common.NoticeDialog
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
    val saveTitle = stringResource(R.string.apps_save_title)
    val saveMessage = stringResource(
        when {
            state.blockingNow -> R.string.apps_save_msg_blocking
            draft != null && !draft.patternLock && state.original?.patternLock == true -> R.string.apps_save_msg_pattern
            else -> R.string.apps_save_msg_off
        },
    )
    val saveLabel = stringResource(R.string.apps_save)
    val allowTitle = stringResource(R.string.apps_temp_confirm_title)
    val allowMessage = stringResource(R.string.apps_temp_confirm_msg)
    val allowLabel = stringResource(R.string.apps_temp_confirm)
    val save = {
        if (savingNeedsPause) {
            friction.ask(
                title = saveTitle,
                message = saveMessage,
                confirmLabel = saveLabel,
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
                    IconButton(onClick = requestBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.apps_back)) }
                },
                actions = {
                    TextButton(onClick = save, enabled = draft != null && draft.isValid && state.dirty && !state.locked) {
                        Text(stringResource(R.string.apps_save))
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
                    title = stringResource(R.string.apps_controlled_title),
                    description = stringResource(R.string.apps_controlled_desc),
                    checked = draft.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )

                BlockSection(draft, viewModel, onPick = { picking = it })

                // "Siempre" already blocks everything: a pattern, a limit, a count or a pause would never be reached.
                // The values stay in the draft, so switching to another mode brings them back as they were.
                val always = draft.blockMode == BlockMode.ALWAYS

                if (!isFeature && !always) {
                    Section(
                        title = stringResource(R.string.apps_pattern_title),
                        description = stringResource(R.string.apps_pattern_desc),
                        checked = draft.patternLock,
                        onCheckedChange = { on ->
                            // The first time there is no pattern yet: it is created right here.
                            if (on && !state.patternSet) creatingPattern = true else viewModel.setPatternLock(on)
                        },
                    )

                    Section(
                        title = stringResource(R.string.apps_daily_title),
                        description = stringResource(R.string.apps_daily_desc),
                        checked = draft.dailyLimitMinutes != null,
                        onCheckedChange = { viewModel.setDailyLimit(if (it) 30 else null) },
                    ) {
                        Stepper(
                            label = stringResource(R.string.apps_max),
                            value = draft.dailyLimitMinutes ?: 30,
                            onValueChange = viewModel::setDailyLimit,
                            range = 5..480,
                            step = 5,
                            format = ::formatMinutes,
                        )
                    }

                    Section(
                        title = stringResource(R.string.apps_openings_title),
                        description = stringResource(R.string.apps_openings_desc),
                        checked = draft.maxSessionsPerDay != null,
                        onCheckedChange = { viewModel.setMaxSessions(if (it) 3 else null) },
                    ) {
                        Stepper(
                            label = stringResource(R.string.apps_max),
                            value = draft.maxSessionsPerDay ?: 3,
                            onValueChange = viewModel::setMaxSessions,
                            range = 1..20,
                        )
                    }
                }

                val intervention = draft.intervention
                if (!always) Section(
                    title = stringResource(if (isFeature) R.string.apps_pause_title_feature else R.string.apps_pause_title_app),
                    description = stringResource(R.string.apps_pause_desc),
                    checked = intervention != null,
                    onCheckedChange = { viewModel.setIntervention(if (it) Restriction.Intervention() else null) },
                ) {
                    if (intervention != null) {
                        Stepper(
                            label = stringResource(R.string.apps_wait),
                            value = intervention.waitSeconds,
                            onValueChange = { viewModel.setIntervention(intervention.copy(waitSeconds = it)) },
                            range = 0..60,
                            step = 5,
                            format = { "$it s" },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.apps_breathing), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(
                                checked = intervention.breathing,
                                onCheckedChange = { viewModel.setIntervention(intervention.copy(breathing = it)) },
                            )
                        }
                    }
                }

                if (state.features.isNotEmpty() && !always) {
                    Section(
                        title = stringResource(R.string.apps_inside_title),
                        description = stringResource(R.string.apps_inside_desc),
                    ) {
                        state.features.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.feature.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(if (entry.ruleId == null) R.string.apps_not_set else R.string.apps_set_up),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { viewModel.openFeature(entry, onOpenRule) }) {
                                    Text(stringResource(if (entry.ruleId == null) R.string.apps_block else R.string.apps_configure))
                                }
                            }
                        }
                    }
                }

                Section(
                    title = stringResource(R.string.apps_hard_title),
                    description = stringResource(R.string.apps_hard_desc),
                    checked = draft.hardMode,
                    onCheckedChange = viewModel::setHardMode,
                )

                if (state.blockingNow) {
                    Section(
                        title = stringResource(R.string.apps_temp_title),
                        description = stringResource(R.string.apps_temp_desc),
                    ) {
                        OutlinedButton(
                            onClick = {
                                friction.ask(
                                    title = allowTitle,
                                    message = allowMessage,
                                    confirmLabel = allowLabel,
                                    onConfirmed = viewModel::allowTemporarily,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.apps_temp_button)) }
                    }
                }

                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .heightIn(min = 48.dp),
                    contentColor = MaterialTheme.colorScheme.error,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(if (isFeature) R.string.apps_remove_rule else R.string.apps_stop_controlling))
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
            title = { Text(stringResource(R.string.apps_discard_title)) },
            text = { Text(stringResource(R.string.apps_discard_text)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.apps_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.apps_keep_editing)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.apps_remove_title, state.label)) },
            text = { Text(stringResource(R.string.apps_remove_text)) },
            confirmButton = {
                val askTitle = stringResource(R.string.apps_remove_confirm_title, state.label)
                val askMessage = stringResource(R.string.apps_remove_confirm_msg)
                val askLabel = stringResource(R.string.apps_remove)
                TextButton(
                    onClick = {
                        confirmDelete = false
                        // For now removing a rule does not wait (the user asked for that); a PIN, if set, is still asked.
                        friction.ask(
                            title = askTitle,
                            message = askMessage,
                            confirmLabel = askLabel,
                            wait = false,
                            onConfirmed = viewModel::delete,
                        )
                    },
                ) { Text(stringResource(R.string.apps_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.apps_cancel)) } },
        )
    }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    NoticeDialog(notice, viewModel::dismissNotice)
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
                    stringResource(R.string.apps_locked_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    stringResource(R.string.apps_locked_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Text(RuleText.summary(LocalContext.current, draft), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BlockSection(draft: RuleDraft, viewModel: EditRuleViewModel, onPick: (TimePick) -> Unit) {
    Section(title = stringResource(R.string.apps_block_title)) {
        val options = listOf(
            BlockMode.NONE to stringResource(R.string.apps_mode_free),
            BlockMode.ALWAYS to stringResource(R.string.apps_mode_always),
            BlockMode.SCHEDULE to stringResource(R.string.apps_mode_schedule),
        )
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
            text = stringResource(
                when (draft.blockMode) {
                    BlockMode.NONE -> R.string.apps_mode_free_desc
                    BlockMode.ALWAYS -> R.string.apps_mode_always_desc
                    BlockMode.SCHEDULE -> R.string.apps_mode_schedule_desc
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (draft.blockMode == BlockMode.SCHEDULE) {
            val modes = listOf(
                ScheduleMode.BLOCK_DURING to stringResource(R.string.apps_sched_block),
                ScheduleMode.ALLOW_ONLY_DURING to stringResource(R.string.apps_sched_allow),
            )
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
            TextButton(onClick = viewModel::addWindow) { Text(stringResource(R.string.apps_add_window)) }
            TextButton(onClick = viewModel::useWorkdayPreset) { Text(stringResource(R.string.apps_workday_preset)) }
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
                        letter = RuleText.dayLetter(day),
                        fullName = RuleText.dayFullName(day),
                        selected = day in window.days,
                        onToggle = { onToggleDay(day, it) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.apps_from, RuleText.clock(window.startMinute)))
                }
                OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.apps_until, RuleText.clock(window.endMinute)))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.apps_remove_window))
                }
            }
            if (window.crossesMidnight) {
                Text(
                    stringResource(R.string.apps_ends_next_day),
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
