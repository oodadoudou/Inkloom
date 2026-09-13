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
) {
    fun create(view: InkCanvasView, targetId: String, folder: Boolean) {
        if (view.selectionLayerTransferPending || view.hasActivePaintContact() ||
            !view.isBoundToSelectionPage(documentId, pageId)) { rejected(); return }
        view.finishSelectionTransform(apply = true)
        view.selectionLayerTransferPending = true
        scope.launch {
            try {
                awaitPublication(view)
                mutex.withLock {
                    val edit = checkNotNull(LayerInsertionEdit.create(page(), targetId, folder))
                    val createdPage = checkNotNull(edit.apply(page()))
                    check(apply(edit, false))
                    // Install and retain the same surface that later paint-history records mutate.
                    // Recreating it on redo would detach those records from the displayed pixels.
                    val surface = if (folder) null else runtime.retainCreatedLayerForHistory(createdPage, edit.layer)
                    select(edit.layer.id)
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
                    view.recordExternalEdit(undo = { restore(true) }, redo = { restore(false) },
                        dispose = { runtime.releaseHistoryLayer(edit.layer.id) })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.e("InkloomLayers", "Layer creation failed", error); rejected() }
            finally { view.selectionLayerTransferPending = false }
        }
    }

    private suspend fun awaitPublication(view: InkCanvasView) {
        withTimeout(15_000) { while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8) }
        check(view.isBoundToSelectionPage(documentId, pageId))
    }
}
