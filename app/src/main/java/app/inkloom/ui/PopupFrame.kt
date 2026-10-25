package app.inkloom.ui

import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * Where a popup's anchor is and where the popup may go, in the pixels Android places it in.
 * Compose UI 1.8.3 supplies window-local anchor bounds even for a popup opened inside another popup,
 * for which Android expects screen coordinates (AndroidX b/191279752, fixed upstream in UI 1.10.0-alpha05);
 * [anchorOffset] converts the anchor and [visibleBounds] is the visible display frame in the same space.
 */
internal data class PopupFrame(val anchorOffset: IntOffset, val visibleBounds: IntRect)

/**
 * [hostView] is the composition's own AndroidComposeView (LocalView), including inside a Popup;
 * the Activity decor view's origin would lose the containing popup's position.
 */
internal fun popupFrame(hostView: View, windowSize: IntSize): PopupFrame {
    val onScreen = IntArray(2)
    val inWindow = IntArray(2)
    val visibleFrame = Rect()
    hostView.getLocationOnScreen(onScreen)
    hostView.getLocationInWindow(inWindow)
    val windowOrigin = IntOffset(onScreen[0] - inWindow[0], onScreen[1] - inWindow[1])
    val nested = (hostView.rootView.layoutParams as? WindowManager.LayoutParams)?.type ==
        WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
    hostView.getWindowVisibleDisplayFrame(visibleFrame)
    val visibleBounds = if (!visibleFrame.isEmpty) {
        // Visible display bounds are always on screen. Ordinary Activity/Dialog popups
        // still use window-relative placement; nested popups use screen placement.
        val origin = if (nested) IntOffset.Zero else windowOrigin
        IntRect(visibleFrame.left - origin.x, visibleFrame.top - origin.y,
            visibleFrame.right - origin.x, visibleFrame.bottom - origin.y)
    } else {
        IntRect(0, 0, windowSize.width, windowSize.height)
    }
    return PopupFrame(if (nested) windowOrigin else IntOffset.Zero, visibleBounds)
}
