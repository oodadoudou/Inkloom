package app.inkloom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.inkloom.document.InkPage
import app.inkloom.document.LayerOpacityEdit
import app.inkloom.document.LayerRole
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
internal class LayerOpacityEditor(
    private val documentId: String,
    private val pageId: String,
    private val page: () -> InkPage,
    private val canvas: () -> InkCanvasView?,
    private val apply: (LayerOpacityEdit, Boolean) -> Boolean,
    private val scope: CoroutineScope,
    private val rejected: () -> Unit,
) {
    var preview by mutableStateOf<LayerOpacityEdit?>(null)
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
                if (preview == null) {
                    val layer = page().layers.firstOrNull { it.id == layerId }
                    val view = canvas()
                    if (layer == null || layer.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) ||
                        view == null || view.selectionLayerTransferPending || view.hasActivePaintContact() ||
                        !view.isBoundToSelectionPage(documentId, pageId)) {
                        // Do not steal the pen-up from a contact that still owns the canvas.
                        rejectedGesture = true; rejected(); return
                    }
                    owner = view
                    view.selectionLayerTransferPending = true
                    preview = LayerOpacityEdit(layerId, layer.opacity, layer.opacity)
                }
                val current = preview ?: return
                if (current.layerId == layerId) preview = current.copy(after = action.value.coerceIn(0f, 1f))
            }
            LayerOpacityAction.Cancel -> if (preview?.layerId == layerId) cancel()
            LayerOpacityAction.Commit -> if (preview?.layerId == layerId) commit()
        }
    }

    private fun commit() {
        val edit = preview ?: return
        val view = owner ?: return
        if (edit.before == edit.after) { cancel(); return }
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
