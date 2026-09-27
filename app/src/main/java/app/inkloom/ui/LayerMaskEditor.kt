package app.inkloom.ui

import app.inkloom.document.*
import kotlinx.coroutines.*

internal data class LayerMaskStrokeHistory(val undo: () -> Unit, val redo: () -> Unit, val dispose: () -> Unit)

/** Mask attachments share the paint journal and keep both versions' files until history expires. */
internal class LayerMaskEditor(
    private val documentId: String,
    private val pageId: String,
    private val page: () -> InkPage,
    private val canvas: () -> InkCanvasView?,
    private val assets: DocumentAssetService,
    private val apply: (LayerAppearanceEdit, Boolean) -> Boolean,
    private val scope: CoroutineScope,
    private val rejected: (String) -> Unit,
    private val selected: (String) -> Unit,
) {
    private var job: Job? = null
    private var owner: InkCanvasView? = null
    private var closed = false
    private var attachedCanvas: InkCanvasView? = null
    fun attach(view: InkCanvasView) { attachedCanvas = view }

    fun publishStroke(layerId: String, snapshot: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot): LayerMaskStrokeHistory? {
        val view = canvas() ?: return null
        val before = view.layerMasks[layerId] ?: return null
        if (closed || !view.isBoundToSelectionPage(documentId, pageId)) return null
        val reference = try { assets.saveMaskTileSurface(snapshot) }
            catch (_: Exception) { rejected("MASK_ASSET_OR_HISTORY_UNAVAILABLE"); return null }
        val after = before.copy(baseVisible = reference.defaultAlpha > 0, surface = reference)
        val edit = LayerMaskAttachmentEdit(layerId, before, after)
        val release = assets.retainHistoryAssets(listOfNotNull(before.surface?.assetPath, reference.assetPath))
        try {
            check(apply(edit, false))
            view.layerMasks = view.layerMasks + (layerId to after)
            return LayerMaskStrokeHistory(
                undo = { if (apply(edit, true)) view.installLayerMaskAttachment(layerId, before, assets) else rejected("STALE_HISTORY") },
                redo = { if (apply(edit, false)) view.installLayerMaskAttachment(layerId, after, assets) else rejected("STALE_HISTORY") },
                dispose = release,
            )
        } catch (_: Exception) { release(); rejected("STALE_HISTORY"); return null }
        finally { assets.releaseAssetReservation(reference.assetPath) }
    }

    fun execute(layerId: String, command: LayerMaskCommand) {
        val view = canvas()
        if (closed || job?.isActive == true || view == null || view.selectionLayerTransferPending ||
            view.hasActivePaintContact() || !view.isBoundToSelectionPage(documentId, pageId)) {
            rejected("BUSY"); return
        }
        if (command == LayerMaskCommand.EDIT) {
            if (view.isEditingLayerMask(layerId)) view.selectLayerContent(layerId)
            else if (!view.toggleLayerMaskEditing(layerId)) { rejected("MASK_TARGET_UNAVAILABLE"); return }
            selected(layerId)
            return
        }
        if (command == LayerMaskCommand.APPLY) {
            view.requestLayerMerge(RasterLayerMergeRequest(RasterLayerMergeOperation.APPLY_MASK, layerId))
            return
        }
        if (command == LayerMaskCommand.TOGGLE_OVERLAY) {
            if (!view.toggleLayerMaskOverlay(layerId)) rejected("MASK_TARGET_UNAVAILABLE")
            return
        }
        owner = view
        view.selectionLayerTransferPending = true
        val selection = view.selectionMaskSurfaceSnapshot()?.takeUnless { it.isEmpty }
        job = scope.launch {
            var prepared: PreparedLayerMaskEdit? = null
            var recorded = false
            try {
                withTimeout(15_000) { while (view.hasPendingPaintPublicationForMerge()) delay(8) }
                check(!closed && view.isBoundToSelectionPage(documentId, pageId))
                if (command == LayerMaskCommand.LOAD_AS_SELECTION) {
                    val reference = page().layers.firstOrNull { it.id == layerId }?.mask?.surface
                        ?: throw LayerMaskCommandRejected(LayerMaskRejection.MISSING_MASK)
                    val snapshot = withContext(Dispatchers.IO) { assets.loadMaskTileSurface(reference).snapshot() }
                    check(view.isBoundToSelectionPage(documentId, pageId))
                    view.replaceSelectionFromMask(snapshot)
                } else {
                    val captured = page()
                    // NonCancellable gives the owner back its prepared reservations even if the
                    // page closes while IO is running. The finally block always releases them.
                    prepared = withContext(NonCancellable + Dispatchers.IO) {
                        LayerMaskCommandPlanner(assets).prepare(captured, layerId, command, selection)
                    }
                    ensureActive()
                    val transaction = requireNotNull(prepared)
                    val edit = transaction.edit
                    if (!edit.isNoOp) {
                        check(view.isBoundToSelectionPage(documentId, pageId) && apply(edit, false))
                        view.installLayerMaskAttachment(layerId, edit.after, assets)
                        transaction.published()
                        view.recordExternalEdit(
                            undo = { if (apply(edit, true)) view.installLayerMaskAttachment(layerId, edit.before, assets) else rejected("STALE_HISTORY") },
                            redo = { if (apply(edit, false)) view.installLayerMaskAttachment(layerId, edit.after, assets) else rejected("STALE_HISTORY") },
                            dispose = transaction::close,
                        )
                        recorded = true
                        if (edit.before == null && edit.after != null) {
                            view.selectionLayerTransferPending = false
                            if (view.toggleLayerMaskEditing(layerId)) selected(layerId)
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                rejected((failure as? LayerMaskCommandRejected)?.reason?.name ?: "MASK_ASSET_OR_HISTORY_UNAVAILABLE")
            } finally {
                if (!recorded) prepared?.close()
                view.selectionLayerTransferPending = false
                view.refreshSelectionTransferPixels()
                owner = null
            }
        }
    }

    fun setExpression(layerId: String, showGradients: Boolean, threshold: Int) {
        val view = canvas()
        if (closed || job?.isActive == true || view == null || view.selectionLayerTransferPending ||
            view.hasActivePaintContact() || !view.isBoundToSelectionPage(documentId, pageId)) {
            rejected("BUSY"); return
        }
        val captured = page()
        val layer = captured.layers.firstOrNull { it.id == layerId }
        val before = layer?.mask
        if (layer == null || before == null || LayerHierarchy.effectiveLocked(captured.layers, layer)) {
            rejected("MASK_TARGET_UNAVAILABLE"); return
        }
        val after = before.copy(showGradients = showGradients, expressionThreshold = threshold.coerceIn(0, 255))
        val edit = LayerMaskAttachmentEdit(layerId, before, after)
        if (edit.isNoOp) return
        val release = assets.retainHistoryAssets(listOfNotNull(before.surface?.assetPath))
        try {
            if (!apply(edit, false)) { release(); rejected("STALE_HISTORY"); return }
            view.layerMasks = view.layerMasks + (layerId to after)
            view.recordExternalEdit(
                undo = { if (apply(edit, true)) view.layerMasks = view.layerMasks + (layerId to before) else rejected("STALE_HISTORY") },
                redo = { if (apply(edit, false)) view.layerMasks = view.layerMasks + (layerId to after) else rejected("STALE_HISTORY") },
                dispose = release,
            )
        } catch (_: Exception) { release(); rejected("MASK_ASSET_OR_HISTORY_UNAVAILABLE") }
    }

    fun transfer(sourceLayerId: String, targetLayerId: String, copy: Boolean) {
        val view = canvas()
        if (closed || job?.isActive == true || view == null || view.selectionLayerTransferPending ||
            view.hasActivePaintContact() || !view.isBoundToSelectionPage(documentId, pageId)) {
            rejected("BUSY"); return
        }
        val captured = page()
        val source = captured.layers.firstOrNull { it.id == sourceLayerId }
        val target = captured.layers.firstOrNull { it.id == targetLayerId }
        val sourceMask = source?.mask
        if (source == null || target == null || sourceMask == null || sourceLayerId == targetLayerId ||
            source.role == LayerRole.BACKGROUND || target.role == LayerRole.BACKGROUND || target.mask != null ||
            LayerHierarchy.effectiveLocked(captured.layers, source) || LayerHierarchy.effectiveLocked(captured.layers, target)) {
            rejected("MASK_TRANSFER_TARGET_UNAVAILABLE"); return
        }
        val edit = LayerMaskTransferEdit(sourceLayerId, targetLayerId, sourceMask, target.mask, copy)
        val release = assets.retainHistoryAssets(listOfNotNull(sourceMask.surface?.assetPath))
        try {
            if (!apply(edit, false)) { release(); rejected("STALE_HISTORY"); return }
            view.installLayerMaskAttachment(sourceLayerId, edit.sourceAfter, assets)
            view.installLayerMaskAttachment(targetLayerId, edit.targetAfter, assets)
            view.recordExternalEdit(
                undo = {
                    if (apply(edit, true)) {
                        view.installLayerMaskAttachment(sourceLayerId, edit.sourceBefore, assets)
                        view.installLayerMaskAttachment(targetLayerId, edit.targetBefore, assets)
                    } else rejected("STALE_HISTORY")
                },
                redo = {
                    if (apply(edit, false)) {
                        view.installLayerMaskAttachment(sourceLayerId, edit.sourceAfter, assets)
                        view.installLayerMaskAttachment(targetLayerId, edit.targetAfter, assets)
                    } else rejected("STALE_HISTORY")
                },
                dispose = release,
            )
            selected(targetLayerId)
        } catch (_: Exception) { release(); rejected("MASK_ASSET_OR_HISTORY_UNAVAILABLE") }
    }

    fun close() {
        attachedCanvas?.flushPendingMaskPublications()
        closed = true; job?.cancel(); owner?.selectionLayerTransferPending = false
        owner = null; attachedCanvas = null
    }
}
