package app.inkloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import app.inkloom.ui.InkloomApp
import app.inkloom.ui.theme.InkloomTheme
import app.inkloom.engine.v5.android.PixelMemoryPressureCoordinator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(25, 25, 25)
        window.navigationBarColor = android.graphics.Color.rgb(25, 25, 25)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
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
