package app.pausa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pausa.domain.model.ThemeMode
import app.pausa.ui.RootViewModel
import app.pausa.ui.PausaRoot
import app.pausa.ui.theme.PausaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
            PausaTheme(themeMode ?: ThemeMode.SYSTEM) {
                PausaRoot(rootViewModel)
            }
        }
    }
}
