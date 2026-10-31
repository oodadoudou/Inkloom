package app.inkloom.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import app.inkloom.capture.ScreenColorCapture
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/**
 * ED-34: asks Android for one screen capture, either right away (another app beside Inkloom in
 * split screen) or after hiding Inkloom until the user taps the notification.
 */
@Composable
internal fun ObtainScreenColorFromApps(language: UiLanguage, onDismiss: () -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val activity = androidx.activity.compose.LocalActivity.current ?: return onDismiss()
    var hidden by remember { mutableStateOf(true) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenColorCapture.start(activity, result.resultCode, data, hidden)
            if (hidden) activity.moveTaskToBack(true) else captureWhenFocused(activity)
        } else Toast.makeText(activity, tr("Screen capture was not allowed", "未允许屏幕录制"), Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        consent.launch(ScreenColorCapture.consentIntent(activity))
    }
    StudioDialog(onDismissRequest = onDismiss, title = { Text(tr("Obtain screen color from other apps", "从其他应用屏幕取色")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StudioSegmented(listOf(tr("Hide Inkloom first", "先隐藏 Inkloom"), tr("Pick from the screen now", "立即从屏幕取色")),
                if (hidden) 0 else 1, { hidden = it == 0 })
            // The one step the dialog cannot show: picking resumes from the notification.
            if (hidden) StudioNote(tr("Open what you want to pick from, then tap the Inkloom notification.", "打开要取色的画面后，点按 Inkloom 通知。"))
        }
    }, confirmButton = { TextButton({
        val needsNotifications = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (hidden && needsNotifications) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else consent.launch(ScreenColorCapture.consentIntent(activity))
    }) { Text(tr("Continue", "继续")) } }, dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "取消")) } })
}

/** Split screen: the frame is taken once the consent screen and this dialog have left Inkloom's window. */
private fun captureWhenFocused(activity: Activity, attempt: Int = 0) {
    activity.window.decorView.postDelayed({
        if (activity.hasWindowFocus() || attempt >= 40) activity.window.decorView.postDelayed({ ScreenColorCapture.captureNow(activity) }, 250)
        else captureWhenFocused(activity, attempt + 1)
    }, 75)
}

/** The captured frame, frozen full screen: one tap picks the colour under it. */
@Composable
internal fun FrozenScreenColorPicker(frame: Bitmap, language: UiLanguage, onPick: (Int) -> Unit, onCancel: () -> Unit) {
    val image = remember(frame) { frame.asImageBitmap() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onCancel, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }
            .semantics { contentDescription = language.text("Captured screen", "已捕获的屏幕") }
            .pointerInput(frame) {
                detectTapGestures { offset ->
                    // Same fit as the image: centred, scaled to the smaller ratio.
                    val scale = minOf(size.width / frame.width.toFloat(), size.height / frame.height.toFloat())
                    val left = (size.width - frame.width * scale) / 2f; val top = (size.height - frame.height * scale) / 2f
                    val x = ((offset.x - left) / scale).toInt(); val y = ((offset.y - top) / scale).toInt()
                    if (x in 0 until frame.width && y in 0 until frame.height) onPick(frame[x, y] or 0xFF000000.toInt())
                }
            }) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Row(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 12.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(language.text("Tap the colour to pick", "点按要选取的颜色"), style = MaterialTheme.typography.bodyMedium)
                TextButton(onCancel) { Text(language.text("Cancel", "取消")) }
            }
        }
    }
}
