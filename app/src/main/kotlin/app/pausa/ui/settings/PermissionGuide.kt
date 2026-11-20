package app.pausa.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * What Pausa asks for, what it does with it and, just as important, what it never asks for. Shown in the first-run
 * introduction and again in Settings, so the explanation is always one tap away and always the same.
 */
@Composable
fun PermissionGuide(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GuideBlock(
            title = "Servicio de accesibilidad",
            body = "Es lo único que Pausa necesita. Le permite saber qué app está en pantalla para mostrarte una pausa " +
                "o un bloqueo, y reconocer las secciones que elijas bloquear (como Shorts). Lo activas tú, a mano, en " +
                "los ajustes de Android, y puedes apagarlo cuando quieras.",
        )
        GuideBlock(
            title = "Lo que NO hace",
            body = "No lee tus mensajes, fotos ni contraseñas. No guarda lo que ves. Solo anota el nombre de las apps " +
                "que controlas y cuánto tiempo las usas, para aplicar tus límites.",
        )
        GuideBlock(
            title = "Lo que nunca pide",
            body = "Internet, contactos, ubicación, cámara, micrófono ni almacenamiento. Sin permiso de internet, Pausa " +
                "no puede enviar nada fuera del teléfono: lo garantiza Android, no solo una promesa.",
        )
    }
}

@Composable
private fun GuideBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Buttons to turn the service on, and the help for the two things that commonly get in the way on Samsung. */
@Composable
fun ActivationHelp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { context.openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) {
            Text("Abrir ajustes de accesibilidad")
        }
        OutlinedButton(onClick = { context.openAppInfo() }, modifier = Modifier.fillMaxWidth()) {
            Text("Abrir información de la app")
        }
        GuideBlock(
            title = "Si el interruptor sale gris",
            body = "Android bloquea el servicio de las apps instaladas fuera de la tienda hasta que lo permites: " +
                "abre la información de la app, toca ⋮ (arriba a la derecha) y elige «Permitir ajustes restringidos». " +
                "Después vuelve a los ajustes de accesibilidad.",
        )
        GuideBlock(
            title = "Para que no se apague sola (Samsung)",
            body = "Si Pausa deja de funcionar con la pantalla apagada, en Ajustes → Batería → Límites de uso en segundo " +
                "plano, quítala de «Apps en suspensión» y añádela a «Apps que nunca se suspenden». Los nombres pueden " +
                "variar un poco según la versión de One UI.",
        )
    }
}

private fun Context.openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

private fun Context.openAppInfo() =
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
