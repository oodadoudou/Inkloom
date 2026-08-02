package app.inkloom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sage = Color(0xFF6750A4)
val SageContainer = Color(0xFFE9DDFF)
val Paper = Color(0xFFFFFFFF)
val Workspace = Color(0xFFF2F1F6)
val Ink = Color(0xFF24212A)
val MutedInk = Color(0xFF696472)
val OutlineSoft = Color(0xFFD2CDD9)

private val InkloomColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = Color(0xFF211047),
    background = Color(0xFFFAF8FC),
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Workspace,
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = OutlineSoft,
)

@Composable
fun InkloomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkloomColors, content = content)
}
