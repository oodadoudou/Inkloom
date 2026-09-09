package app.inkloom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sage = Color(0xFF1674D8)
val SageContainer = Color(0xFFE5EFFB)
val Paper = Color(0xFFFFFFFF)
val Workspace = Color.White
val Ink = Color(0xFF111111)
val MutedInk = Color(0xFF626262)
val OutlineSoft = Color(0xFFE0E4E1)

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
    error = Color(0xFFC93636),
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E5),
    onErrorContainer = Ink,
    background = Color.White,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFF747474),
    outlineVariant = OutlineSoft,
    inverseSurface = Color.White,
    inverseOnSurface = Ink,
    inversePrimary = Color(0xFFD0D0D0),
    surfaceTint = Ink,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
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
    ), content = content)
}
