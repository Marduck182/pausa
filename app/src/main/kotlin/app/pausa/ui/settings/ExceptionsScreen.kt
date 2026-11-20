@file:OptIn(ExperimentalMaterial3Api::class)

package app.pausa.ui.settings

import app.pausa.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.pausa.ui.common.Switch
import app.pausa.ui.common.NoticeDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exc_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.exc_back)) }
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
                stringResource(R.string.exc_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.exc_search)) },
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
                                val allowDescription = stringResource(R.string.exc_allow_description, row.app.label)
                                Switch(
                                    modifier = Modifier.semantics { contentDescription = allowDescription },
                                    checked = row.allowed,
                                    onCheckedChange = { allow ->
                                        // Letting an app through weakens the protection, so it takes a pause; removing one does not.
                                        if (allow) {
                                            friction.ask(
                                                title = friction.text(R.string.exc_confirm_title, row.app.label),
                                                message = friction.text(R.string.exc_confirm_message),
                                                confirmLabel = friction.text(R.string.exc_allow),
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
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    NoticeDialog(notice, viewModel::dismissNotice)
    friction.Dialog()
}
