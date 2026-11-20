package app.pausa.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
    var message by remember { mutableStateOf("Une al menos ${Pattern.MIN_DOTS} puntos.") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (first == null) "Nuevo patrón" else "Dibújalo otra vez") },
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
                                message = "Muy corto: une al menos ${Pattern.MIN_DOTS} puntos."
                            }
                            previous == null -> {
                                first = dots
                                error = false
                                message = "Para confirmar, dibuja el mismo patrón."
                            }
                            dots == previous -> onCreated(dots)
                            else -> {
                                first = null
                                error = true
                                message = "No coinciden. Empieza de nuevo."
                            }
                        }
                    },
                    isError = error,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Sirve para frenar un impulso o una mano curiosa; no es una protección contra alguien con tiempo y conocimientos.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
