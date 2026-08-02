package app.inkloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.inkloom.ui.InkloomApp
import app.inkloom.ui.theme.InkloomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InkloomTheme {
                InkloomApp(initialIntent = intent)
            }
        }
    }
}
