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
        val gray = app.inkloom.settings.UiGrayTheme.decode(getSharedPreferences("inkloom_settings", MODE_PRIVATE).getString("uiGrayTheme", null))
        window.decorView.setBackgroundColor(gray.argb(gray.workspace))
        window.statusBarColor = gray.argb(gray.workspace)
        window.navigationBarColor = window.statusBarColor
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = gray == app.inkloom.settings.UiGrayTheme.LIGHT
            isAppearanceLightNavigationBars = isAppearanceLightStatusBars
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
