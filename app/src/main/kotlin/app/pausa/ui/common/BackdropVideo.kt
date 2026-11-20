package app.pausa.ui.common

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pausa.R
import kotlin.math.max

/**
 * A video that plays silently and in a loop behind the screens of the dark look, filling the area (cropped, not
 * stretched). It is part of the app (res/raw), so it needs no internet. It plays only while the app is on screen: it
 * pauses when the app stops and is released when it leaves the composition, because a video that keeps decoding in the
 * background is battery spent on nothing. If the video cannot be played the view stays empty and the aurora behind shows.
 */
@Composable
fun BackdropVideo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { PlayerHolder() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.play()
                Lifecycle.Event.ON_STOP -> holder.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        holder.attach(context, this@apply, Surface(texture), lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = holder.fit(this@apply)

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        holder.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private class PlayerHolder {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var sourceWidth = 0
    private var sourceHeight = 0

    fun attach(context: android.content.Context, view: TextureView, newSurface: Surface, startNow: Boolean) {
        release()
        surface = newSurface
        player = try {
            MediaPlayer.create(context, R.raw.pausa_bg)?.apply {
                setSurface(newSurface)
                isLooping = true
                setVolume(0f, 0f)
                sourceWidth = this.videoWidth
                sourceHeight = this.videoHeight
                setOnVideoSizeChangedListener { _, w, h ->
                    sourceWidth = w
                    sourceHeight = h
                    fit(view)
                }
                // A failure (a codec the phone lacks, say) must not take the screen down: the aurora behind stays.
                setOnErrorListener { mp, _, _ -> mp.release(); player = null; true }
                if (startNow) start()
            }
        } catch (e: Exception) {
            null
        }
        fit(view)
    }

    fun play() {
        player?.let { if (!it.isPlaying) it.start() }
    }

    fun pause() {
        player?.let { if (it.isPlaying) it.pause() }
    }

    fun release() {
        player?.let { runCatching { it.release() } }
        player = null
        surface?.release()
        surface = null
    }

    /** Scales the video so it covers the whole view and crops what overflows, like a centre-crop image. */
    fun fit(view: TextureView) {
        if (sourceWidth == 0 || sourceHeight == 0 || view.width == 0 || view.height == 0) return
        val viewAspect = view.width.toFloat() / view.height
        val videoAspect = sourceWidth.toFloat() / sourceHeight
        val scaleX = max(1f, videoAspect / viewAspect)
        val scaleY = max(1f, viewAspect / videoAspect)
        view.setTransform(Matrix().apply { setScale(scaleX, scaleY, view.width / 2f, view.height / 2f) })
    }
}
