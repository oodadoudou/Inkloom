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

// A single Android app-wide theme owner. Observable tokens can also be read in DrawScope
// and pointer callbacks, so all existing panels update without recreating their workspace.
private val selectedGrayTheme = androidx.compose.runtime.mutableStateOf(app.inkloom.settings.UiGrayTheme.DEFAULT)
private fun tone(value: Int) = Color(selectedGrayTheme.value.argb(value))
val Sage get() = tone(selectedGrayTheme.value.selected)
val SageContainer get() = tone(selectedGrayTheme.value.selected)
val Paper get() = tone(selectedGrayTheme.value.surface)
val Workspace get() = tone(selectedGrayTheme.value.workspace)
val Ink get() = tone(selectedGrayTheme.value.text)
val MutedInk get() = tone(selectedGrayTheme.value.muted)
val OutlineSoft get() = tone(selectedGrayTheme.value.divider)

private fun colors(theme: app.inkloom.settings.UiGrayTheme): androidx.compose.material3.ColorScheme {
    fun gray(value: Int) = Color(theme.argb(value))
    val text = gray(theme.text)
    val surface = gray(theme.surface)
    val workspace = gray(theme.workspace)
    val raised = gray(theme.raised)
    val selected = gray(theme.selected)
    val muted = gray(theme.muted)
    val outline = gray(theme.divider)
    return darkColorScheme(
        primary = text, onPrimary = workspace,
        primaryContainer = selected, onPrimaryContainer = text,
        secondary = text, onSecondary = workspace,
        secondaryContainer = selected, onSecondaryContainer = text,
        tertiary = text, onTertiary = workspace,
        tertiaryContainer = selected, onTertiaryContainer = text,
        error = text, onError = workspace, errorContainer = raised, onErrorContainer = text,
        background = workspace, onBackground = text,
        surface = surface, onSurface = text, surfaceVariant = raised, onSurfaceVariant = muted,
        outline = outline, outlineVariant = outline,
        inverseSurface = text, inverseOnSurface = workspace, inversePrimary = workspace,
        surfaceTint = Color.Transparent, scrim = Color.Black,
        surfaceBright = raised, surfaceDim = workspace,
        surfaceContainer = surface, surfaceContainerHigh = raised, surfaceContainerHighest = selected,
        surfaceContainerLow = surface, surfaceContainerLowest = workspace,
    )
}

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = androidx.compose.runtime.remember(context) {
        context.getSharedPreferences("inkloom_settings", android.content.Context.MODE_PRIVATE)
    }
    // Read startup preferences before content first appears; subsequent writes are observable.
    androidx.compose.runtime.remember(preferences) {
        selectedGrayTheme.value = app.inkloom.settings.UiGrayTheme.decode(preferences.getString("uiGrayTheme", null))
        true
    }
    androidx.compose.runtime.DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
            if (key == "uiGrayTheme" || key == null) selectedGrayTheme.value =
                app.inkloom.settings.UiGrayTheme.decode(source.getString("uiGrayTheme", null))
            if (key == app.inkloom.settings.InterfaceScale.PREFERENCE_KEY) {
                var host: android.content.Context? = context
                while (host is android.content.ContextWrapper && host !is android.app.Activity) host = host.baseContext
                (host as? android.app.Activity)?.let(InterfaceScaling::recreateIfChanged)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val theme = selectedGrayTheme.value
    val colorScheme = androidx.compose.runtime.remember(theme) { colors(theme) }
    androidx.compose.runtime.SideEffect {
        var host: android.content.Context? = context
        while (host is android.content.ContextWrapper && host !is android.app.Activity) host = host.baseContext
        (host as? android.app.Activity)?.let { activity ->
            // Android 15 forces transparent system bars. Paint the decor behind their insets.
            activity.window.decorView.setBackgroundColor(theme.argb(theme.workspace))
            activity.window.statusBarColor = theme.argb(theme.workspace)
            activity.window.navigationBarColor = theme.argb(theme.workspace)
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = theme == app.inkloom.settings.UiGrayTheme.LIGHT
                isAppearanceLightNavigationBars = isAppearanceLightStatusBars
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = InkloomTypography, shapes = androidx.compose.material3.Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ), content = {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Ink) { content() }
    })
}
