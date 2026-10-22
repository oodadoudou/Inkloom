package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** Where a studio dialog sits: centred, or docked at the side so the canvas stays visible. */
internal enum class StudioDialogPlacement { CENTER, SIDE }

internal object StudioDialogDefaults {
    val Shape: Shape = RoundedCornerShape(6.dp)

    /** Width for the current window: a fixed tool-window width on tablets, nearly full width on phones. */
    @Composable
    fun width(wide: Boolean = false): Dp {
        val screen = LocalConfiguration.current.screenWidthDp.dp
        return when {
            screen < 600.dp -> screen * .94f
            screen < 840.dp -> minOf(screen * .62f, if (wide) 520.dp else 420.dp)
            else -> if (wide) 520.dp else 400.dp
        }
    }

    @Composable
    fun maxHeight(): Dp = LocalConfiguration.current.screenHeightDp.dp * .86f
}

/**
 * A compact tool dialog in the style of Clip Studio Paint and Photoshop: a slim title bar, dense
 * 13 sp content and a right-aligned button row. It takes the same slots, in the same order, as
 * Material's AlertDialog, so any dialog can switch to it unchanged. Its size follows the window:
 * a fixed tool-window width on tablets and almost the full width on narrow screens, never taller
 * than the window.
 */
@Composable
internal fun StudioDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = StudioDialogDefaults.Shape,
    containerColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") iconContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") titleContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") textContentColor: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
    placement: StudioDialogPlacement = StudioDialogPlacement.CENTER,
    wide: Boolean = false,
) {
    Dialog(onDismissRequest, DialogProperties(dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside, securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = properties.decorFitsSystemWindows)) {
        // The window wraps the panel, so taps beside it still dismiss and the side placement can apply.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                setLayout(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                if (placement == StudioDialogPlacement.SIDE) { setGravity(android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL); setDimAmount(.12f) }
            }
        }
        StudioDialogFrame(modifier.width(StudioDialogDefaults.width(wide)).heightIn(max = StudioDialogDefaults.maxHeight())
            .padding(horizontal = if (placement == StudioDialogPlacement.SIDE) 12.dp else 0.dp)
            // Only dialogs that draw behind the keyboard reserve room for it, as with Material dialogs.
            .let { if (properties.decorFitsSystemWindows) it else it.imePadding() },
            title = title, icon = icon, shape = shape, containerColor = containerColor, body = text,
            footer = { dismissButton?.invoke(); Spacer(Modifier.width(4.dp)); confirmButton() })
    }
}

/**
 * The studio dialog's frame: slim title bar, dense body and a right-aligned button row. Custom
 * dialogs that manage their own window (keyboard insets, full-screen hosts) use it directly.
 */
@Composable
internal fun StudioDialogFrame(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    shape: Shape = StudioDialogDefaults.Shape,
    containerColor: Color = Color.Unspecified,
    footer: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    body: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
        Surface(modifier.border(1.dp, colors.outline, shape), shape = shape,
            color = if (containerColor == Color.Unspecified) colors.surface else containerColor, contentColor = colors.onSurface, shadowElevation = 10.dp) {
            Column {
                if (title != null || icon != null) {
                    Row(Modifier.fillMaxWidth().background(colors.surfaceContainerHigh).heightIn(min = 34.dp)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        icon?.let { Box(Modifier.width(18.dp)) { it() } }
                        ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(color = colors.onSurface)) { title?.invoke() }
                    }
                    HorizontalDivider(color = colors.outline)
                }
                if (body != null) Box(Modifier.weight(1f, fill = false).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { body() }
                    }
                }
                if (footer != null) {
                    HorizontalDivider(color = colors.outline)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) { footer() }
                    }
                }
            }
        }
    }
}
