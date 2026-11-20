package app.pausa.ui.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.ui.common.AppRow
import app.pausa.ui.common.Dialog
import app.pausa.ui.common.EmptyState
import app.pausa.ui.common.ScreenTitle
import app.pausa.ui.common.SkeletonRows
import app.pausa.ui.common.Supporting
import app.pausa.ui.common.rememberFriction

@Composable
fun AppsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val friction = rememberFriction()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            ScreenTitle("Apps")
            when {
                state.loading -> SkeletonRows()
                state.items.isEmpty() -> EmptyState(
                    title = "Nada controlado todavía",
                    body = "Añade la app que más te distrae. Después eliges cuándo se bloquea y cuánto puedes usarla.",
                    actionLabel = "Añadir una app",
                    onAction = onAdd,
                    modifier = Modifier.padding(bottom = 96.dp),
                )
                else -> LazyColumn(
                    // Room at the bottom so the last row is never hidden behind the button.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.items, key = { it.ruleId }) { item ->
                        AppRow(
                            packageName = item.packageName,
                            title = item.label,
                            onClick = { onEdit(item.ruleId) },
                            supporting = {
                                Supporting(item.summary)
                                if (item.usageToday != null) Supporting(item.usageToday, emphasis = true)
                                if (item.locked) Supporting("Modo difícil: no se puede cambiar ahora", emphasis = true)
                            },
                            trailing = {
                                Switch(
                                    checked = item.enabled,
                                    enabled = !item.locked,
                                    onCheckedChange = { enabled ->
                                        // Turning something off is where an impulse acts, so it takes a pause; turning it on does not.
                                        if (enabled) {
                                            viewModel.setEnabled(item.ruleId, true)
                                        } else {
                                            friction.ask(
                                                title = "¿Apagar ${item.label}?",
                                                message = "Dejará de estar protegida hasta que la vuelvas a encender.",
                                                confirmLabel = "Apagar",
                                            ) { viewModel.setEnabled(item.ruleId, false) }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        if (!state.loading && state.items.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Añadir app", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            )
        }
    }
    friction.Dialog()
}
