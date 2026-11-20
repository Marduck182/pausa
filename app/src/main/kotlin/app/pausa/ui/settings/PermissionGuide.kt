package app.pausa.ui.settings

import app.pausa.R
import androidx.compose.ui.res.stringResource
import app.pausa.ui.common.OutlinedButton
import app.pausa.ui.common.Button
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
            title = stringResource(R.string.perm_service_title),
            body = stringResource(R.string.perm_service_body),
        )
        GuideBlock(
            title = stringResource(R.string.perm_not_title),
            body = stringResource(R.string.perm_not_body),
        )
        GuideBlock(
            title = stringResource(R.string.perm_never_title),
            body = stringResource(R.string.perm_never_body),
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
            Text(stringResource(R.string.perm_open_accessibility))
        }
        OutlinedButton(onClick = { context.openAppInfo() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.perm_open_app_info))
        }
        GuideBlock(
            title = stringResource(R.string.perm_gray_title),
            body = stringResource(R.string.perm_gray_body),
        )
        GuideBlock(
            title = stringResource(R.string.perm_samsung_title),
            body = stringResource(R.string.perm_samsung_body),
        )
    }
}

private fun Context.openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

private fun Context.openAppInfo() =
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
