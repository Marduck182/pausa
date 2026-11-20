package app.pausa.ui.lab

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
}
