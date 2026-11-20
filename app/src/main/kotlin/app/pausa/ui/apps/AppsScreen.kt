package app.pausa.ui.apps

import app.pausa.ui.common.Switch
import app.pausa.ui.common.FilledTonalButton
import app.pausa.ui.common.NoticeDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.ui.common.AppRow
import app.pausa.ui.common.Dialog
import app.pausa.ui.common.EmptyState
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

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // The way to add an app sits next to the title, where it cannot cover a row (a floating button did, on its side).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Apps",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (!state.loading && state.items.isNotEmpty()) {
                FilledTonalButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Añadir app")
                }
            }
        }
        run {
            when {
                state.loading -> SkeletonRows()
                state.items.isEmpty() -> EmptyState(
                    title = "Nada controlado todavía",
                    body = "Añade la app que más te distrae. Después eliges cuándo se bloquea y cuánto puedes usarla.",
                    actionLabel = "Añadir una app",
                    onAction = onAdd,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
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
                                    modifier = Modifier.semantics { contentDescription = "Controlar ${item.label}" },
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
    }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    NoticeDialog(notice, viewModel::dismissNotice)
    friction.Dialog()
}
