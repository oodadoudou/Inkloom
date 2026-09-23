package app.inkloom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.inkloom.document.InkPage
import app.inkloom.document.LayerOpacityEdit
import app.inkloom.document.LayerAppearanceEdit
import app.inkloom.document.LayerAdjustment
import app.inkloom.document.LayerAdjustmentEdit
import app.inkloom.document.LayerBlendEdit
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.LayerHierarchy
import app.inkloom.document.LayerRole
import app.inkloom.document.LayerPaletteColor
import app.inkloom.document.LayerPaletteColorEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal sealed interface LayerOpacityAction {
    data class Preview(val value: Float) : LayerOpacityAction
    data object Commit : LayerOpacityAction
    data object Cancel : LayerOpacityAction
}

/** One page owns the preview and publication barrier. Dragging never writes the document store. */
internal class LayerAppearanceEditor(
    private val documentId: String,
    private val pageId: String,
    private val page: () -> InkPage,
    private val canvas: () -> InkCanvasView?,
    private val apply: (LayerAppearanceEdit, Boolean) -> Boolean,
    private val scope: CoroutineScope,
    private val rejected: () -> Unit,
    private val throughRejected: () -> Unit = rejected,
) {
    var preview by mutableStateOf<LayerAppearanceEdit?>(null)
        private set
    private var owner: InkCanvasView? = null
    private var publication: Job? = null
    private var finishing = false
    private var closed = false
    private var rejectedGesture = false

    fun handle(layerId: String, action: LayerOpacityAction) {
        if (closed || finishing) return
        if (rejectedGesture) {
            if (action == LayerOpacityAction.Commit || action == LayerOpacityAction.Cancel) rejectedGesture = false
            return
        }
        when (action) {
            is LayerOpacityAction.Preview -> {
                if (!action.value.isFinite()) { cancel(); rejected(); return }
                if (preview == null && !begin(layerId) { LayerOpacityEdit(layerId, it.opacity, it.opacity) }) return
                val current = preview as? LayerOpacityEdit ?: return
                if (current.layerId == layerId) preview = current.copy(after = action.value.coerceIn(0f, 1f))
            }
            LayerOpacityAction.Cancel -> if (preview?.layerId == layerId) cancel()
            LayerOpacityAction.Commit -> if (preview?.layerId == layerId) commit()
        }
    }

    fun setBlendMode(layerId: String, mode: LayerBlendMode) {
        if (closed || finishing || preview != null) { rejected(); return }
        if (begin(layerId) { LayerBlendEdit(layerId, it.blendMode, mode, it.passThrough) }) commit()
        // A discrete choice owns no subsequent pointer-up to clear this flag.
        rejectedGesture = false
    }

    fun setThrough(layerId: String) {
        if (closed || finishing || preview != null) { rejected(); return }
        if (!app.inkloom.document.LayerGroupPolicy.canPassThrough(page().layers, layerId)) { throughRejected(); return }
        if (begin(layerId) { app.inkloom.document.LayerGroupCompositingEdit(layerId, it.passThrough, true) }) commit()
        rejectedGesture = false
    }

    fun setPaletteColor(layerId: String, color: LayerPaletteColor) {
        if (closed || finishing || preview != null) { rejected(); return }
        if (begin(layerId, allowBackground = true, allowLocked = true) {
                LayerPaletteColorEdit(layerId, it.paletteColor, color)
            }) commit()
        rejectedGesture = false
    }

    fun previewAdjustment(layerId: String, value: LayerAdjustment) {
        if (closed || finishing) return
        if (preview == null) {
            val layer = page().layers.firstOrNull { it.id == layerId && it.role == LayerRole.ADJUSTMENT && it.adjustment != null }
            if (layer == null) { rejected(); return }
            if (!begin(layerId) { LayerAdjustmentEdit(layerId, requireNotNull(layer.adjustment), value) }) return
        }
        val current = preview as? LayerAdjustmentEdit ?: return
        if (current.layerId == layerId) preview = current.copy(after = value)
    }

    fun commitAdjustment(layerId: String) {
        if (preview is LayerAdjustmentEdit && preview?.layerId == layerId) commit()
    }

    fun cancelAdjustment(layerId: String) {
        if (preview is LayerAdjustmentEdit && preview?.layerId == layerId) cancel()
    }

    fun move(primaryId: String, selected: Set<String>, drop: app.inkloom.document.LayerDrop) {
        if (closed || finishing || preview != null) { rejected(); return }
        val edit = try { app.inkloom.document.LayerOrderEdit.create(page(), primaryId, selected, drop) }
        catch (failure: app.inkloom.document.LayerMoveRejected) {
            if (failure.reason == app.inkloom.document.LayerMoveRejection.THROUGH_CLIPPING) throughRejected() else rejected()
            return
        }
        if (edit.isNoOp) return
        canvas()?.finishSelectionTransform(apply = true)
        if (begin(primaryId) { edit }) commit()
        rejectedGesture = false
    }

    private fun begin(
        layerId: String,
        allowBackground: Boolean = false,
        allowLocked: Boolean = false,
        edit: (app.inkloom.document.InkLayer) -> LayerAppearanceEdit,
    ): Boolean {
        val currentPage = page()
        val layer = currentPage.layers.firstOrNull { it.id == layerId }
        val view = canvas()
        if (layer == null || (!allowBackground && layer.role == LayerRole.BACKGROUND) ||
            (!allowLocked && LayerHierarchy.effectiveLocked(currentPage.layers, layer)) ||
            view == null || view.selectionLayerTransferPending || view.hasActivePaintContact() ||
            !view.isBoundToSelectionPage(documentId, pageId)) {
            // Never steal the pen-up from a contact that still owns the canvas.
            rejectedGesture = true; rejected(); return false
        }
        owner = view
        view.selectionLayerTransferPending = true
        preview = edit(layer)
        return true
    }

    private fun commit() {
        val edit = preview ?: return
        val view = owner ?: return
        if (edit.isNoOp) { cancel(); return }
        finishing = true
        publication = scope.launch {
            try {
                // Place the property transaction after a just-finished pen contact, not before its
                // delayed publication. The view blocks new contacts while this barrier is owned.
                withTimeout(15_000) { while (view.hasPendingPaintPublicationForMerge()) delay(8) }
                check(!closed && view.isBoundToSelectionPage(documentId, pageId))
                check(apply(edit, false))
                view.recordExternalEdit(
                    undo = { if (!apply(edit, true)) rejected() },
                    redo = { if (!apply(edit, false)) rejected() },
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (!closed) rejected()
                throw cancelled
            } catch (_: Exception) { rejected() }
            finally { finishing = false; cancel() }
        }
    }

    private fun cancel() {
        preview = null
        owner?.let { it.selectionLayerTransferPending = false; it.refreshSelectionTransferPixels() }
        owner = null
    }

    fun close() { closed = true; publication?.cancel(); finishing = false; cancel() }
}
