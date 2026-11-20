package app.pausa.ui.lab

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import android.widget.ImageView
import app.pausa.enforcement.InterventionContent
import app.pausa.enforcement.BlockContent
import app.pausa.domain.model.ThemeMode
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import app.pausa.ui.common.OutlinedButton
import app.pausa.ui.common.Button
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import app.pausa.enforcement.GuardAccessibilityService
import app.pausa.enforcement.LabConfig
import app.pausa.enforcement.LabLog

/** Technical lab (debug builds only): checks what Android really lets the service see and do on this phone. */
@Composable
fun LabScreen() {
    val context = LocalContext.current
    val entries by LabLog.entries.collectAsState()
    val blocked by LabConfig.blockedPackages.collectAsState()
    // The screens Pausa draws over other apps, shown inside the app so they can be checked without the service.
    var preview by remember { mutableStateOf<String?>(null) }

    fun withService(action: (GuardAccessibilityService) -> Unit) {
        val service = GuardAccessibilityService.instance
        if (service == null) LabLog.add("El servicio no está activo: actívalo en Ajustes de accesibilidad") else action(service)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Laboratorio", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Abre otras apps y mira abajo qué detecta el servicio. Todo vive en memoria y se borra al cerrar Pausa.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Ajustes de accesibilidad") }

        Text("Bloquear de prueba", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = LabConfig.YOUTUBE in blocked,
                onClick = { LabConfig.toggle(LabConfig.YOUTUBE) },
                label = { Text("YouTube") },
            )
            FilterChip(
                selected = LabConfig.INSTAGRAM in blocked,
                onClick = { LabConfig.toggle(LabConfig.INSTAGRAM) },
                label = { Text("Instagram") },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { withService { it.showOverlay("Prueba", "Overlay de Pausa sobre otra app.") } }) {
                Text("Overlay")
            }
            OutlinedButton(onClick = { withService { it.captureTreeAfter(8) } }) {
                Text("Capturar en 8 s")
            }
            OutlinedButton(onClick = LabLog::clear) { Text("Limpiar") }
        }

        // The launcher icon as the system draws it (foreground over background), to check it without leaving the app.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply { setImageDrawable(ctx.packageManager.getApplicationIcon(ctx.packageName)) } },
                modifier = Modifier.size(96.dp),
            )
            Text("Icono de la app", style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { preview = "block" }) { Text("Ver bloqueo") }
            OutlinedButton(onClick = { preview = "pause" }) { Text("Ver pausa") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries) { entry ->
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                )
            }
        }
    }

    if (preview != null) {
        Dialog(
            onDismissRequest = { preview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            if (preview == "block") {
                BlockContent(
                    packageName = "com.brave.browser",
                    title = "Brave está bloqueada",
                    message = "La bloquea tu regla «Siempre».",
                    dismissLabel = "Volver al inicio",
                    onDismiss = { preview = null },
                    temporaryAllowMinutes = null,
                    waitSeconds = 0,
                    onAllowTemporarily = {},
                    themeMode = ThemeMode.SYSTEM,
                )
            } else {
                InterventionContent(
                    packageName = "com.brave.browser",
                    appLabel = "Brave",
                    waitSeconds = 5,
                    breathing = true,
                    onCancel = { preview = null },
                    onContinue = { preview = null },
                    themeMode = ThemeMode.SYSTEM,
                )
            }
        }
    }
}
