package app.inkloom.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import app.inkloom.document.CanvasOrientationChange
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.PageGeometryEdit
import app.inkloom.document.PreparedPageGeometry
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** CSP Edit > Rotate/Flip canvas. Unlike the view mirror, these change the artwork itself. */
@Composable
internal fun CanvasOrientationDialog(language: UiLanguage, canFlip: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onChoose: (CanvasOrientationChange) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    StudioDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(tr("Rotate or flip canvas", "旋转／翻转画布")) },
        text = { Column {
            CanvasOrientationChange.entries.forEach { change ->
                val flip = change.flip != null
                StudioMenuItem(
                    text = { Text(when (change) {
                        CanvasOrientationChange.ROTATE_CLOCKWISE -> tr("Rotate 90° clockwise", "顺时针旋转 90°")
                        CanvasOrientationChange.ROTATE_180 -> tr("Rotate 180°", "旋转 180°")
                        CanvasOrientationChange.ROTATE_COUNTERCLOCKWISE -> tr("Rotate 90° counterclockwise", "逆时针旋转 90°")
                        CanvasOrientationChange.FLIP_HORIZONTAL -> tr("Flip horizontally", "水平翻转")
                        CanvasOrientationChange.FLIP_VERTICAL -> tr("Flip vertically", "垂直翻转")
                    }) },
                    leadingIcon = { when (change) {
                        CanvasOrientationChange.ROTATE_CLOCKWISE -> Icon(Icons.Outlined.Rotate90DegreesCw, null)
                        CanvasOrientationChange.ROTATE_180 -> Icon(Icons.Outlined.RotateRight, null)
                        CanvasOrientationChange.ROTATE_COUNTERCLOCKWISE -> Icon(Icons.Outlined.Rotate90DegreesCcw, null)
                        CanvasOrientationChange.FLIP_HORIZONTAL -> Icon(Icons.Outlined.Flip, null)
                        CanvasOrientationChange.FLIP_VERTICAL -> Icon(Icons.Outlined.Flip, null, Modifier.rotate(90f))
                    } },
                    enabled = !busy && (!flip || canFlip),
                    onClick = { onChoose(change) })
            }
            if (!canFlip) Text(tr("Pages with an imported PDF or image background can be rotated but not flipped.",
                "带有导入 PDF 或图片背景的页面可以旋转，但不能翻转。"))
        } },
        confirmButton = {}, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(tr("Cancel", "取消")) } })
}

/**
 * Prepares the change off the UI thread after pending strokes are published, applies it as one
 * page edit and records one undo step.
 */
internal fun applyCanvasOrientation(scope: CoroutineScope, view: InkCanvasView, page: () -> InkPage, assets: DocumentAssetService,
    change: CanvasOrientationChange, apply: (PageGeometryEdit, Boolean) -> Boolean, setBusy: (Boolean) -> Unit, onApplied: () -> Unit) {
    if (view.selectionLayerTransferPending) return
    view.finishSelectionTransform(apply = true)
    view.selectionLayerTransferPending = true; setBusy(true)
    scope.launch {
        var prepared: PreparedPageGeometry? = null
        var published = false
        try {
            kotlinx.coroutines.withTimeout(15_000) { while (view.hasPendingPaintPublicationForMerge()) kotlinx.coroutines.delay(8) }
            val before = page()
            prepared = withContext(Dispatchers.IO) { assets.prepareCanvasOrientation(before, change) }
            val transaction = requireNotNull(prepared)
            check(apply(transaction.edit, false)) { "Page changed during rotate or flip" }
            view.recordExternalEdit(undo = { apply(transaction.edit, true) }, redo = { apply(transaction.edit, false) }, dispose = transaction::close)
            published = true
            onApplied()
        } catch (failure: Throwable) {
            android.util.Log.e("InkloomCanvas", "Canvas rotate or flip failed", failure)
            Toast.makeText(view.context, failure.message ?: "Canvas rotate or flip failed", Toast.LENGTH_LONG).show()
        } finally {
            if (!published) prepared?.close()
            view.selectionLayerTransferPending = false; setBusy(false)
        }
    }
}
