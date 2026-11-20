@file:OptIn(ExperimentalMaterial3Api::class)

package app.pausa.ui.common

import app.pausa.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import app.pausa.ui.theme.LocalDarkTheme
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The title of a top-level screen. One style everywhere, and announced as a heading to screen readers. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/**
 * A titled group of settings, drawn flat and separated from the next by a hairline. It is deliberately not a card:
 * boxes around every group make a settings screen look like a stack of identical tiles, and the editor already has
 * real boxes (the time windows) that must not end up nested inside another one.
 *
 * With [checked] set it has an on/off switch and its [content] only shows while on, so nobody sees options for
 * something they did not turn on.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (checked != null) {
                    // Without this a screen reader says only "switch": it must say what the switch is for.
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.semantics { contentDescription = title },
                    )
                }
            }
            if (checked != false) content()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * One app in a list: its icon, its name, a few lines of detail and something on the right (usually a switch). Rows
 * are separated by a hairline, not boxed, so a long list reads as a list.
 */
@Composable
fun AppRow(
    packageName: String,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    supporting: @Composable ColumnScope.() -> Unit = {},
) {
    val dark = LocalDarkTheme.current
    val shape = if (dark) CircleShape else RoundedCornerShape(24.dp)
    // The glass is the row: no hairline between rows, a little air instead.
    Box(modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .glassSurface(shape, dark)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassSphere(size = 52.dp, halo = false, animated = false) { AppIcon(packageName, Modifier.size(32.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                supporting()
            }
            trailing?.invoke()
        }
    }
}

/** A quiet supporting line under a title: the default colour and size for secondary text in a row. */
@Composable
fun ColumnScope.Supporting(text: String, emphasis: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What a list shows when it has nothing yet. It says what the list is for and offers the first step, instead of
 * just stating that it is empty.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        GlassButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Placeholder rows shown while a list loads, in the shape of the rows that will replace them. Still blocks, no
 * shimmer: movement that only says "wait" is decoration.
 */
@Composable
fun SkeletonRows(modifier: Modifier = Modifier, count: Int = 5) {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    val loading = stringResource(R.string.ui_loading)
    Column(modifier.fillMaxWidth().semantics { contentDescription = loading }) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(block))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(block))
                    Box(Modifier.fillMaxWidth(0.75f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(block))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** A value with minus and plus buttons: faster and less error-prone than typing a number on a phone. */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
) {
    val lessLabel = stringResource(R.string.ui_less)
    val moreLabel = stringResource(R.string.ui_more)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onValueChange((value - step).coerceAtLeast(range.first)) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = lessLabel },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            format(value),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + step).coerceAtMost(range.last)) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = moreLabel },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * A round day-of-week toggle (L M X J V S D). It looks 40 dp but its touch target is 48. On is a filled circle and
 * off is an outline, so the state is never carried by colour alone. Announces its full name and state.
 */
@Composable
fun DayToggle(
    letter: String,
    fullName: String,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = fullName },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else colors.surface)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TimeDialog(
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ui_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/** Tells the person why something was not done. Shown while [message] is not null; [onDismiss] clears it. */
@Composable
fun NoticeDialog(@StringRes message: Int?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(message), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_got_it)) } },
    )
}
