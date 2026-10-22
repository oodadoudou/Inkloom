package app.inkloom.ui

import android.app.Activity
import androidx.core.graphics.get
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/**
 * CSP Edit > Obtain screen color. A transparent layer takes one tap anywhere in the app and
 * the pixel the user sees there (page, panels or reference images) becomes the drawing colour.
 * The app window is read with PixelCopy, so nothing outside the app is captured and no screen
 * capture permission is needed.
 */
@Composable
internal fun ScreenColorPicker(language: UiLanguage, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    val activity = androidx.activity.compose.LocalActivity.current ?: return onCancel()
    Dialog(onCancel, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        // No dimming: the colours under the finger must stay exactly as shown.
        SideEffect { window?.setDimAmount(0f) }
        val overlay = LocalView.current
        Box(Modifier.fillMaxSize().semantics { contentDescription = language.text("Screen color picker", "屏幕取色") }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val onScreen = IntArray(2).also(overlay::getLocationOnScreen)
                    val appOrigin = IntArray(2).also(activity.window.decorView::getLocationOnScreen)
                    val x = (onScreen[0] + offset.x - appOrigin[0]).toInt()
                    val y = (onScreen[1] + offset.y - appOrigin[1]).toInt()
                    sampleWindow(activity, x, y, onPick, onCancel)
                }
            }) {
            Row(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(language.text("Tap anywhere to pick its color", "点按任意位置取色"), style = MaterialTheme.typography.bodyMedium)
                TextButton(onCancel) { Text(language.text("Cancel", "取消")) }
            }
        }
    }
}

private fun sampleWindow(activity: Activity, x: Int, y: Int, onPick: (Int) -> Unit, onFailed: () -> Unit) {
    val decor = activity.window.decorView
    if (x !in 0 until decor.width || y !in 0 until decor.height) return onFailed()
    val pixel = androidx.core.graphics.createBitmap(1, 1)
    PixelCopy.request(activity.window, android.graphics.Rect(x, y, x + 1, y + 1), pixel, { result ->
        try { if (result == PixelCopy.SUCCESS) onPick(pixel[0, 0] or 0xFF000000.toInt()) else onFailed() }
        finally { pixel.recycle() }
    }, Handler(Looper.getMainLooper()))
}
