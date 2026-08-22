package app.inkloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import app.inkloom.ui.InkloomApp
import app.inkloom.ui.theme.InkloomTheme
import app.inkloom.engine.v3.android.PixelMemoryPressureCoordinator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            InkloomTheme {
                InkloomApp(initialIntent = intent)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        PixelMemoryPressureCoordinator.onTrimMemory(level)
        super.onTrimMemory(level)
    }
}
