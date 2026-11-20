package app.pausa.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Owns the one full-screen window Pausa may show over other apps. It is an accessibility overlay
 * (TYPE_ACCESSIBILITY_OVERLAY), a window type that only an accessibility service can add and that needs no
 * "draw over other apps" permission. At most one is visible at a time.
 */
class OverlayController(private val service: AccessibilityService) {

    private var view: KeyInterceptLayout? = null
    private var owner: OverlayLifecycleOwner? = null

    /** The app the overlay is covering, or null if nothing is shown. */
    var shownFor: String? = null
        private set

    val isShowing: Boolean get() = view != null

    /** Shows [content] over [packageName]. [onBack] runs when the user presses Back, instead of closing it. */
    fun show(packageName: String, onBack: () -> Unit, content: @Composable () -> Unit): Boolean {
        hide()
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        // ComposeView is final, so the Back key is caught by a plain container around it. Compose finds the
        // lifecycle by walking up the view tree, so the owners go on the container.
        val container = KeyInterceptLayout(service, onBack).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            // Focus is what lets the view receive the Back key.
            isFocusable = true
            isFocusableInTouchMode = true
            addView(
                ComposeView(service).apply { setContent(content) },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        return try {
            service.getSystemService(WindowManager::class.java).addView(container, params)
            container.post { container.requestFocus() }
            view = container
            owner = lifecycleOwner
            shownFor = packageName
            true
        } catch (e: RuntimeException) {
            lifecycleOwner.stop()
            LabLog.add("No se pudo mostrar el overlay: ${e.javaClass.simpleName}")
            false
        }
    }

    fun hide() {
        view?.let { runCatching { service.getSystemService(WindowManager::class.java).removeView(it) } }
        owner?.stop()
        view = null
        owner = null
        shownFor = null
    }

    private class KeyInterceptLayout(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }
}
