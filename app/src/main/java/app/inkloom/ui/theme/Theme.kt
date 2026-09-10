package app.inkloom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sage = Color(0xFF686868)
val SageContainer = Color(0xFF555555)
val Paper = Color(0xFF414141)
val Workspace = Color(0xFF363636)
val Ink = Color(0xFFECECEC)
val MutedInk = Color(0xFFC4C4C4)
val OutlineSoft = Color(0xFF626262)

private val InkloomColors = darkColorScheme(
    primary = Color(0xFFDEDEDE), onPrimary = Color(0xFF171717),
    primaryContainer = SageContainer, onPrimaryContainer = Ink,
    secondary = Color(0xFFC8C8C8), onSecondary = Color.Black,
    secondaryContainer = SageContainer, onSecondaryContainer = Ink,
    tertiary = Color(0xFFBDBDBD), onTertiary = Color.Black,
    tertiaryContainer = SageContainer, onTertiaryContainer = Ink,
    error = Color(0xFFE0E0E0), onError = Color.Black,
    errorContainer = Color(0xFF484848), onErrorContainer = Ink,
    background = Workspace, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFF494949), onSurfaceVariant = MutedInk,
    outline = Color(0xFF888888), outlineVariant = OutlineSoft,
    inverseSurface = Ink, inverseOnSurface = Workspace, inversePrimary = Color(0xFF444444),
    surfaceTint = Color.Transparent, scrim = Color.Black,
    surfaceBright = Color(0xFF555555), surfaceDim = Workspace,
    surfaceContainer = Paper, surfaceContainerHigh = Color(0xFF494949),
    surfaceContainerHighest = Color(0xFF525252), surfaceContainerLow = Color(0xFF3B3B3B),
    surfaceContainerLowest = Workspace,
)

private val InkloomTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun InkloomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = InkloomColors, typography = InkloomTypography, shapes = androidx.compose.material3.Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ), content = {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Ink) { content() }
    })
}
