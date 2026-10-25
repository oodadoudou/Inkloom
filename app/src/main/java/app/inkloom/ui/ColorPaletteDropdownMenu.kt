package app.inkloom.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Menu anchored inside the color palette's popup, including when that popup moves or resizes.
 * Correct the coordinate space ([popupFrame]) before choosing an edge; a DropdownMenu DpOffset cannot
 * do this because its vertical sign reverses when the menu opens above its anchor.
 */
@Composable
internal fun ColorPaletteDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val hostView = LocalView.current
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positioner = remember(hostView, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val frame = popupFrame(hostView, windowSize)
                return colorPaletteMenuPosition(anchorBounds, frame.anchorOffset, frame.visibleBounds,
                    popupContentSize, layoutDirection, margin)
            }
        }
    }
    Popup(
        popupPositionProvider = positioner,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true,
            dismissOnClickOutside = true),
    ) {
        Surface(modifier = modifier, shape = MenuDefaults.shape, color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation, shadowElevation = MenuDefaults.ShadowElevation) {
            Column(Modifier.width(IntrinsicSize.Max).verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp), content = content)
        }
    }
}

/** All arguments and the result use pixels; anchorOffset converts the supplied anchor to bounds. */
internal fun colorPaletteMenuPosition(
    anchor: IntRect,
    anchorOffset: IntOffset,
    visibleBounds: IntRect,
    menuSize: IntSize,
    layoutDirection: LayoutDirection,
    margin: Int,
): IntOffset {
    val left = anchor.left + anchorOffset.x
    val right = anchor.right + anchorOffset.x
    val top = anchor.top + anchorOffset.y
    val bottom = anchor.bottom + anchorOffset.y
    val horizontalMargin = margin.coerceIn(0, (visibleBounds.width / 2).coerceAtLeast(0))
    val verticalMargin = margin.coerceIn(0, (visibleBounds.height / 2).coerceAtLeast(0))
    val minX = visibleBounds.left + horizontalMargin
    val maxX = (visibleBounds.right - horizontalMargin - menuSize.width).coerceAtLeast(minX)
    val minY = visibleBounds.top + verticalMargin
    val maxY = (visibleBounds.bottom - verticalMargin - menuSize.height).coerceAtLeast(minY)
    val start = if (layoutDirection == LayoutDirection.Ltr) left else right - menuSize.width
    val end = if (layoutDirection == LayoutDirection.Ltr) right - menuSize.width else left
    val x = when {
        start in minX..maxX -> start
        end in minX..maxX -> end
        else -> start.coerceIn(minX, maxX)
    }
    val above = top - menuSize.height
    val y = when {
        bottom in minY..maxY -> bottom
        above in minY..maxY -> above
        else -> bottom.coerceIn(minY, maxY)
    }
    return IntOffset(x, y)
}
