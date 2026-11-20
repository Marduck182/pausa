package app.pausa.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pausa.ui.settings.ActivationHelp
import app.pausa.ui.settings.PermissionGuide

/**
 * First-run introduction: what Pausa is, what it asks for and why, and how to turn it on. It deliberately comes
 * before anything is requested, so nobody is asked to trust the app before being told what it does.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = PAGES - 1

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (current) {
                    0 -> Welcome()
                    1 -> Permissions()
                    else -> Activate()
                }
            }
        }

        Text(
            "${page + 1} de $PAGES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) {
                OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) { Text("Atrás") }
            }
            Button(onClick = { if (page == last) onFinish() else page++ }, modifier = Modifier.weight(1f)) {
                Text(if (page == last) "Empezar" else "Siguiente")
            }
        }
    }
}

@Composable
private fun Welcome() {
    Text("Pausa", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 32.dp))
    Text(
        "Un momento antes de abrir esa app.",
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        "Tú eliges qué apps controlar, en qué horarios y cuánto tiempo. Pausa te frena con una pausa, o las bloquea, " +
            "y puede incluso cerrar una sección como los Shorts sin cerrar toda la app.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Tu privacidad es lo primero", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    listOf(
        "Todo ocurre en este teléfono.",
        "No necesita cuenta.",
        "No tiene permiso de internet: no puede enviar nada fuera.",
        "No lee tus mensajes, fotos ni contactos.",
    ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun Permissions() {
    Text("Qué pedimos y por qué", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
    PermissionGuide()
}

@Composable
private fun Activate() {
    Text("Actívala cuando quieras", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
    Text(
        "Android pide que este permiso lo des tú, a mano. Puedes hacerlo ahora o más tarde desde el inicio.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActivationHelp()
}

private const val PAGES = 3
