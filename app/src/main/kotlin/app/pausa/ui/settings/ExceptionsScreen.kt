@file:OptIn(ExperimentalMaterial3Api::class)

package app.pausa.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.ui.common.AppRow
import app.pausa.ui.common.Dialog
import app.pausa.ui.common.SkeletonRows
import app.pausa.ui.common.rememberFriction

@Composable
fun ExceptionsScreen(
    onBack: () -> Unit,
    viewModel: ExceptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val friction = rememberFriction()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Excepciones", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Las apps que actives aquí pasan todos los bloqueos y las concentraciones. Mejor pocas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Buscar") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            if (state.loading) {
                SkeletonRows(count = 8)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(state.rows, key = { it.app.packageName }) { row ->
                        AppRow(
                            packageName = row.app.packageName,
                            title = row.app.label,
                            trailing = {
                                Switch(
                                    checked = row.allowed,
                                    onCheckedChange = { allow ->
                                        // Letting an app through weakens the protection, so it takes a pause; removing one does not.
                                        if (allow) {
                                            friction.ask(
                                                title = "¿Dejar pasar ${row.app.label}?",
                                                message = "Ningún bloqueo ni concentración le afectará.",
                                                confirmLabel = "Permitir",
                                            ) { viewModel.setAllowed(row.app.packageName, true) }
                                        } else {
                                            viewModel.setAllowed(row.app.packageName, false)
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
    friction.Dialog()
}
