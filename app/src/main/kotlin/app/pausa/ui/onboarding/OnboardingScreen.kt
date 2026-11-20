package app.pausa.ui.onboarding

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.pausa.R
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
import androidx.compose.material3.MaterialTheme
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
 *
 * One idea per page, a quiet top bar, and the way forward in the same place every time (bottom right, under the
 * thumb), as a label plus a round button instead of a full-width bar.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = PAGES - 1
    val compact = LocalConfiguration.current.screenHeightDp < 480

    // Back goes to the previous page, not out of the app, until the first page is reached.
    BackHandler(enabled = page > 0) { page-- }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        TopBar(page = page, onBack = { page-- })

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
                    0 -> Welcome(compact)
                    1 -> Permissions()
                    else -> Activate()
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 8.dp else 20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            ForwardAction(
                label = stringResource(if (page == last) R.string.onb_finish else if (page == 0) R.string.onb_begin else R.string.onb_next),
                onClick = { if (page == last) onFinish() else page++ },
            )
        }
    }
}

/** Back on the left (from the second page on), the name in the middle, and where the person is on the right. */
@Composable
private fun TopBar(page: Int, onBack: () -> Unit) {
    val stepDescription = stringResource(R.string.onb_step_description, page + 1, PAGES)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (page > 0) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onb_back))
                }
            }
        }
        Text(
            "PAUSA",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Text(
            "${page + 1} / $PAGES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(48.dp)
                .semantics { contentDescription = stepDescription },
        )
    }
}

/** The label and the round button are one target: touching either goes forward. */
@Composable
private fun ForwardAction(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
        )
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun Welcome(compact: Boolean) {
    // The same shield as the app icon, large and on its own: it says what the app is before any words do.
    Image(
        painter = painterResource(R.drawable.ic_shield),
        contentDescription = null,
        modifier = Modifier
            .padding(top = if (compact) 0.dp else 24.dp)
            .size(if (compact) 72.dp else 144.dp),
    )
    Text(
        stringResource(R.string.onb_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = if (compact) 0.dp else 16.dp),
    )
    Text(
        stringResource(R.string.onb_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.onb_privacy_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    listOf(
        stringResource(R.string.onb_privacy_1),
        stringResource(R.string.onb_privacy_2),
        stringResource(R.string.onb_privacy_3),
        stringResource(R.string.onb_privacy_4),
    ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun Permissions() {
    Text(stringResource(R.string.onb_permissions_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    PermissionGuide()
}

@Composable
private fun Activate() {
    Text(stringResource(R.string.onb_activate_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    Text(
        stringResource(R.string.onb_activate_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ActivationHelp()
}

private const val PAGES = 3
