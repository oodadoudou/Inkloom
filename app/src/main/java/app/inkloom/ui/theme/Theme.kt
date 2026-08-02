package app.inkloom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sage = Color(0xFF111111)
val SageContainer = Color(0xFFE7E7E7)
val Paper = Color(0xFFFFFFFF)
val Workspace = Color(0xFFF2F2F2)
val Ink = Color(0xFF111111)
val MutedInk = Color(0xFF626262)
val OutlineSoft = Color(0xFFCCCCCC)

private val InkloomColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF333333),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF555555),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAEAEA),
    onTertiaryContainer = Ink,
    error = Ink,
    onError = Color.White,
    errorContainer = Color(0xFFE2E2E2),
    onErrorContainer = Ink,
    background = Color(0xFFF7F7F7),
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Workspace,
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFF747474),
    outlineVariant = OutlineSoft,
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF4F4F4),
    inversePrimary = Color(0xFFD0D0D0),
    surfaceTint = Ink,
    scrim = Color.Black,
)

@Composable
fun InkloomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkloomColors, content = content)
}
