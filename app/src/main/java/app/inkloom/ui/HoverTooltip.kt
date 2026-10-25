package app.inkloom.ui

import android.view.WindowManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A vertical rail sets this so a name shows beside its icon instead of over the next one. */
internal val LocalTooltipBeside = staticCompositionLocalOf { false }

private const val HOVER_SHOW_DELAY_MILLIS = 300L
private const val HOVER_VISIBLE_MILLIS = 2_000L

// Neither focusable nor touchable: a name on screen must never take the pen tap that follows it.
// Material3's TooltipBox popup is focusable, so its window swallowed the next touch anywhere.
private val TooltipWindow = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
    inheritSecurePolicy = true,
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    excludeFromSystemGesture = false,
    usePlatformDefaultWidth = false,
)

/**
 * Names an icon-only control while the pen or a mouse rests over it, and while a finger holds it
 * unless the control already uses long-press for something else ([longPressShows] false).
 */
@Composable
internal fun HoverTooltip(description: String, longPressShows: Boolean = true, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    Box(
        Modifier.pointerInput(Unit) { showWhileHovering { visible = it } }
            .then(if (longPressShows) Modifier.pointerInput(Unit) { showWhileHeld { visible = it } } else Modifier),
    ) {
        content()
        if (visible) TooltipPopup(description)
    }
}

/** Only watches the pointer: the control under it gets every event untouched. */
private suspend fun PointerInputScope.showWhileHovering(onVisible: (Boolean) -> Unit) = coroutineScope {
    var pending: Job? = null
    // After a tap the name stays away until the pen leaves; you know what you just pressed.
    var tapped = false
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when (event.type) {
                PointerEventType.Enter, PointerEventType.Move -> if (pending == null && !tapped && event.changes.none { it.pressed }) {
                    pending = launch { delay(HOVER_SHOW_DELAY_MILLIS); onVisible(true); delay(HOVER_VISIBLE_MILLIS); onVisible(false) }
                }
                PointerEventType.Exit, PointerEventType.Press -> {
                    tapped = event.type == PointerEventType.Press
                    pending?.cancel()
                    pending = null
                    onVisible(false)
                }
                else -> Unit
            }
        }
    }
}

/** A pen names a control by hovering, so only a finger holds to see it. */
private suspend fun PointerInputScope.showWhileHeld(onVisible: (Boolean) -> Unit) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (down.type != PointerType.Touch) return@awaitEachGesture
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial); true }
    if (ended == null) {
        onVisible(true)
        // Lifting the finger after reading the name must not also press the control.
        try { waitForUpOrCancellation(PointerEventPass.Initial)?.consume() } finally { onVisible(false) }
    }
}

@Composable
private fun TooltipPopup(description: String) {
    val hostView = LocalView.current
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val beside = LocalTooltipBeside.current
    val placement = remember(hostView, gap, beside) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val frame = popupFrame(hostView, windowSize)
                return hoverTooltipPosition(anchorBounds.translate(frame.anchorOffset), frame.visibleBounds, popupContentSize, gap, beside)
            }
        }
    }
    Popup(popupPositionProvider = placement, properties = TooltipWindow) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
            Text(description, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, maxLines = 1)
        }
    }
}

/**
 * Pixels throughout. Beside the anchor toward the middle of [visible] for a vertical rail, otherwise
 * below or above it toward the middle, kept inside [visible].
 */
internal fun hoverTooltipPosition(anchor: IntRect, visible: IntRect, tooltip: IntSize, gap: Int, beside: Boolean): IntOffset {
    val position = if (beside) {
        val x = if (anchor.center.x <= visible.center.x) anchor.right + gap else anchor.left - gap - tooltip.width
        IntOffset(x, anchor.center.y - tooltip.height / 2)
    } else {
        val y = if (anchor.center.y <= visible.center.y) anchor.bottom + gap else anchor.top - gap - tooltip.height
        IntOffset(anchor.center.x - tooltip.width / 2, y)
    }
    return IntOffset(
        position.x.coerceIn(visible.left, (visible.right - tooltip.width).coerceAtLeast(visible.left)),
        position.y.coerceIn(visible.top, (visible.bottom - tooltip.height).coerceAtLeast(visible.top)),
    )
}
