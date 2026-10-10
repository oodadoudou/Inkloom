package app.inkloom.ui

import app.inkloom.document.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** One creation transaction, owned by the painting host's publication and surface history. */
internal class LayerCreationEditor(
    private val documentId: String,
    private val pageId: String,
    private val page: () -> InkPage,
    private val runtime: RasterLayerSurfaceRuntime,
    private val pendingSaves: AtomicInteger,
    private val mutex: Mutex,
    private val latestAssets: MutableMap<String, Pair<RasterTileSurfaceRef?, Int>>,
    private val apply: (LayerInsertionEdit, Boolean) -> Boolean,
    private val select: (String) -> Unit,
    private val scope: CoroutineScope,
    private val rejected: () -> Unit,
    private val assets: DocumentAssetService? = null,
) {
    fun create(view: InkCanvasView, targetId: String, folder: Boolean, adjustment: LayerAdjustment? = null,
        onCreated: ((String) -> Unit)? = null) {
        if (view.selectionLayerTransferPending || view.hasActivePaintContact() ||
            !view.isBoundToSelectionPage(documentId, pageId)) { rejected(); return }
        view.finishSelectionTransform(apply = true)
        // Like Photoshop and Clip Studio Paint, an active selection becomes the new correction's mask.
        val selection = if (adjustment != null && assets != null) view.selectionMaskSurfaceSnapshot()?.takeUnless { it.isEmpty } else null
        view.selectionLayerTransferPending = true
        scope.launch {
            var mask: PreparedLayerMaskEdit? = null
            var recorded = false
            try {
                awaitPublication(view)
                mutex.withLock {
                    val unmasked = checkNotNull(LayerInsertionEdit.create(page(), targetId, folder, adjustment = adjustment))
                    mask = selection?.let { selected ->
                        val planned = checkNotNull(unmasked.apply(page()))
                        withContext(NonCancellable + Dispatchers.IO) {
                            LayerMaskCommandPlanner(checkNotNull(assets)).prepare(planned, unmasked.layer.id,
                                LayerMaskCommand.CREATE_MASK_SELECTION, selected)
                        }
                    }
                    val edit = mask?.let { unmasked.copy(layer = unmasked.layer.copy(mask = it.edit.after)) } ?: unmasked
                    val createdPage = checkNotNull(edit.apply(page()))
                    check(apply(edit, false))
                    mask?.let { prepared ->
                        prepared.published()
                        view.installLayerMaskAttachment(edit.layer.id, edit.layer.mask, checkNotNull(assets))
                    }
                    // Install and retain the same surface that later paint-history records mutate.
                    // Recreating it on redo would detach those records from the displayed pixels.
                    val surface = if (folder || adjustment != null) null else runtime.retainCreatedLayerForHistory(createdPage, edit.layer)
                    select(edit.layer.id)
                    onCreated?.invoke(edit.layer.id)
                    fun restore(undo: Boolean) {
                        view.selectionLayerTransferPending = true
                        scope.launch {
                            try {
                                awaitPublication(view)
                                mutex.withLock {
                                    if (undo) check(surface?.snapshot()?.tileInfo?.none { it.nonTransparentPixelCount > 0 } != false)
                                    check(apply(edit, undo))
                                    latestAssets[edit.layer.id] = null to 0
                                    if (!undo && surface != null) runtime.retainHistoryLayer(page(), edit.layer, surface)
                                    select(if (undo) edit.previousTargetId else edit.layer.id)
                                    view.refreshSelectionTransferPixels()
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { android.util.Log.e("InkloomLayers", "Creation history failed", error); rejected() }
                            finally { view.selectionLayerTransferPending = false }
                        }
                    }
                    val history = mask
                    view.recordExternalEdit(undo = { restore(true) }, redo = { restore(false) },
                        dispose = { runtime.releaseHistoryLayer(edit.layer.id); history?.close() })
                    recorded = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.e("InkloomLayers", "Layer creation failed", error); rejected() }
            finally {
                if (!recorded) mask?.close()
                view.selectionLayerTransferPending = false
            }
        }
    }

    private suspend fun awaitPublication(view: InkCanvasView) {
        withTimeout(15_000) { while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8) }
        check(view.isBoundToSelectionPage(documentId, pageId))
    }
}
