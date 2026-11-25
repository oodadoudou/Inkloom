package app.inkloom.ui

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.document.CanvasSizeRequest
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.PageCrop
import app.inkloom.document.PageCropTransform
import app.inkloom.document.PageGeometryEdit
import app.inkloom.document.PreparedPageGeometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs a whole-page change after pending strokes are published: [prepare] runs off the UI thread,
 * the result is applied as one page edit and recorded as one undo step. A page that changed in
 * the meantime is refused rather than overwritten.
 */
internal fun applyPreparedPageEdit(scope: CoroutineScope, view: InkCanvasView, page: () -> InkPage,
    prepare: (InkPage) -> PreparedPageGeometry, apply: (PageGeometryEdit, Boolean) -> Boolean,
    setBusy: (Boolean) -> Unit, onApplied: () -> Unit) {
    setBusy(true)
    scope.launch {
        var prepared: PreparedPageGeometry? = null
        var published = false
        var owned = false
        try {
            // Another page operation may briefly own the canvas; wait for it rather than dropping the request.
            kotlinx.coroutines.withTimeout(15_000) {
                while (view.selectionLayerTransferPending || view.hasPendingPaintPublicationForMerge()) kotlinx.coroutines.delay(8)
            }
            view.finishSelectionTransform(apply = true)
            view.selectionLayerTransferPending = true; owned = true
            val before = page()
            prepared = withContext(Dispatchers.IO) { prepare(before) }
            val transaction = requireNotNull(prepared)
            check(apply(transaction.edit, false)) { "The page changed before the edit could be applied" }
            view.recordExternalEdit(undo = { apply(transaction.edit, true) }, redo = { apply(transaction.edit, false) }, dispose = transaction::close)
            published = true
            onApplied()
        } catch (failure: Throwable) {
            android.util.Log.e("InkloomCanvas", "Page edit failed", failure)
            // Leaving the screen cancels the edit quietly; a timeout or a real failure is reported.
            if (failure is kotlinx.coroutines.CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
            val chinese = view.feedbackLanguage == app.inkloom.settings.UiLanguage.SIMPLIFIED_CHINESE
            val message = when {
                failure is kotlinx.coroutines.TimeoutCancellationException -> if (chinese) "画布仍在处理，请稍后再试。" else "The canvas is still busy; try again."
                else -> failure.message ?: if (chinese) "页面编辑失败。" else "The page edit failed."
            }
            Toast.makeText(view.context, message, Toast.LENGTH_LONG).show()
        } finally {
            if (!published) prepared?.close()
            if (owned) view.selectionLayerTransferPending = false
            setBusy(false)
        }
    }
}

/**
 * CSP Edit > Fit canvas to selection. The selection's bounds (fractions of the visible page)
 * become the crop, and the canvas is then cropped to exactly that area, as one edit of [page].
 */
internal fun prepareFitCanvasToSelection(assets: DocumentAssetService, page: InkPage, bounds: android.graphics.RectF): PreparedPageGeometry {
    val crop = PageCropTransform.normalized(page.crop)
    val width = crop.right - crop.left; val height = crop.bottom - crop.top
    val fitted = page.copy(crop = PageCropTransform.normalized(PageCrop(crop.left + bounds.left * width, crop.top + bounds.top * height,
        crop.left + bounds.right * width, crop.top + bounds.bottom * height)))
    val pixelsWide = Math.round(PageCropTransform.visibleWidth(fitted)).coerceIn(32, 8192)
    val pixelsHigh = Math.round(PageCropTransform.visibleHeight(fitted)).coerceIn(32, 8192)
    val prepared = assets.prepareCanvasSize(fitted, CanvasSizeRequest(pixelsWide.toFloat(), pixelsHigh.toFloat(), page.dpi))
    return PreparedPageGeometry(PageGeometryEdit(page, prepared.edit.after), prepared::close)
}

/** CSP Edit > Clear memory, confirmed first because the cleared steps cannot come back. */
@Composable
internal fun ClearHistoryDialog(language: UiLanguage, steps: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    StudioDialog(onDismissRequest = onDismiss, title = { Text(tr("Clear undo history", "清除历史记录")) },
        text = { Text(tr("Removes $steps steps. The artwork stays as it is.", "移除 $steps 个步骤，作品保持不变。")) },
        confirmButton = { TextButton(onConfirm) { Text(tr("Clear", "清除")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "取消")) } })
}

/** Gap check: marks the breaks in the referenced line art that a fill would leak through. */
internal fun runGapCheck(scope: CoroutineScope, view: InkCanvasView, context: android.content.Context, language: UiLanguage) {
    scope.launch {
        val reference = try { view.gapCheckReference() } catch (failure: Throwable) {
            android.util.Log.e("InkloomCanvas", "Gap check could not read the page", failure)
            Toast.makeText(context, language.text("Gap check failed.", "缺口检查失败。"), Toast.LENGTH_SHORT).show()
            return@launch
        }
        val gaps = withContext(Dispatchers.Default) {
            app.inkloom.engine.v5.fill.GapCheck.find(reference.pixels, reference.width, reference.height, reference.maxGapPixels,
                if (reference.scanned) app.inkloom.engine.v5.fill.EncloseFill.LineRule.INK else app.inkloom.engine.v5.fill.EncloseFill.LineRule.ALPHA)
        }
        view.showGapMarks(gaps, reference)
        if (gaps.isEmpty()) Toast.makeText(context, language.text("No gaps", "没有缺口"), Toast.LENGTH_SHORT).show()
    }
}
