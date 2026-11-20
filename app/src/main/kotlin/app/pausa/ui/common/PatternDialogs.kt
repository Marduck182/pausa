package app.pausa.ui.common

import androidx.compose.runtime.mutableIntStateOf
import app.pausa.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pausa.domain.security.Pattern

/** Draw a new pattern twice. [onCreated] gets it only when both drawings match. */
@Composable
fun CreatePatternDialog(onCreated: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<List<Int>?>(null) }
    // The message is a string id, so it can be set from the drawing callback and still follow the language.
    var message by remember { mutableIntStateOf(R.string.pat_min_dots_hint) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (first == null) R.string.pat_new else R.string.pat_draw_again)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PatternPad(
                    onPattern = { dots ->
                        val previous = first
                        when {
                            !Pattern.isValid(dots) -> {
                                error = true
                                message = R.string.pat_too_short
                            }
                            previous == null -> {
                                first = dots
                                error = false
                                message = R.string.pat_confirm
                            }
                            dots == previous -> onCreated(dots)
                            else -> {
                                first = null
                                error = true
                                message = R.string.pat_mismatch
                            }
                        }
                    },
                    isError = error,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(message, Pattern.MIN_DOTS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.pat_disclaimer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pat_cancel)) } },
    )
}

/** Asks for the current pattern before something that changes or removes it. */
@Composable
fun VerifyPatternDialog(
    title: String,
    message: String,
    verify: suspend (List<Int>) -> Boolean,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                PatternChallenge(verify = verify, onVerified = onVerified)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pat_cancel)) } },
    )
}
