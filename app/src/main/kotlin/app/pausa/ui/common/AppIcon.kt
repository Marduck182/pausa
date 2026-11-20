package app.pausa.ui.common

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icons already drawn, by package. A list shows the same few apps every time it scrolls or is opened again, and
 * reading an icon from the system each time is slow and allocates a bitmap. Small on purpose: 96 px icons, 64 of them.
 */
private val iconCache = LruCache<String, ImageBitmap>(64)

/** The launcher icon of [packageName], loaded off the main thread. Shows a neutral circle while it loads. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = iconCache.get(packageName), packageName) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(width = 96, height = 96).asImageBitmap()
            }.getOrNull()?.also { iconCache.put(packageName, it) }
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
