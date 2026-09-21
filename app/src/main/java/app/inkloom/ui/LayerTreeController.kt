package app.inkloom.ui

import android.content.Context
import android.widget.Toast
import app.inkloom.document.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** Serializes structure edits with paint saves and gives one history entry ownership of the subtree. */
internal class LayerTreeController(
    private val context: Context,
    private val documentId: String,
    private val pageId: String,
    private val assets: DocumentAssetService,
    private val runtime: RasterLayerSurfaceRuntime,
    private val mutex: Mutex,
    private val latestAssets: MutableMap<String, Pair<RasterTileSurfaceRef?, Int>>,
    private val pendingSaves: AtomicInteger,
    private val scope: CoroutineScope,
    private val page: () -> InkPage,
    private val applyEdit: (LayerTreeEdit, Boolean, InkPage?) -> Boolean,
    private val select: (String) -> Unit,
    private val language: UiLanguage,
) {
    fun execute(view: InkCanvasView, action: LayerTreeAction, targetId: String, selectedIds: Set<String>) {
        if (view.selectionLayerTransferPending || view.hasActivePaintContact() ||
            !view.isBoundToSelectionPage(documentId, pageId)) return
        view.selectionLayerTransferPending = true
        scope.launch(NonCancellable) {
            var releaseAssets: () -> Unit = {}
            var published = false
            try {
                awaitPublication(view)
                mutex.withLock {
                    val effective = page().copy(layers = page().layers.map { layer ->
                        latestAssets[layer.id]?.let { layer.copy(rasterTileSurface = it.first,
                            rasterRotationQuarterTurns = it.second) } ?: layer
                    })
                    val transaction = LayerTreeEdit.create(effective, action, targetId, selectedIds)
                    val changed = checkNotNull(transaction.apply(effective, false))
                    val historyPaths = (transaction.beforeLayers + transaction.afterLayers).flatMap { layer ->
                        listOfNotNull(layer.rasterTileSurface?.manifestAssetPath, layer.mask?.surface?.assetPath)
                    } + (transaction.beforeElements + transaction.afterElements).mapNotNull { element ->
                        when (element) {
                            is PageElement.Image -> element.assetPath
                            is PageElement.Fill -> element.maskAssetPath
                            is PageElement.Shape -> element.maskAssetPath
                            else -> null
                        }
                    }
                    releaseAssets = assets.retainHistoryAssets(historyPaths)
                    withContext(Dispatchers.Main.immediate) {
                        check(view.isBoundToSelectionPage(documentId, pageId))
                        check(applyEdit(transaction, false, effective))
                        published = true
                        (transaction.removedLayers + transaction.insertedLayers).filter(InkLayer::isPaintSurfaceLayer).forEach { layer ->
                            val sourcePage = if (layer in transaction.removedLayers) effective else changed
                            runtime.retainCreatedLayerForHistory(sourcePage, layer)
                        }
                        transaction.insertedLayers.forEach { latestAssets[it.id] = it.rasterTileSurface to it.rasterRotationQuarterTurns }
                        view.setRasterLayerSurfaces(runtime.reconcile(changed))
                        select(transaction.afterSelection)
                        view.refreshSelectionTransferPixels()
                        fun restore(undo: Boolean) {
                            view.selectionLayerTransferPending = true
                            scope.launch(NonCancellable) {
                                try {
                                    awaitPublication(view)
                                    mutex.withLock {
                                        val current = page()
                                        val result = checkNotNull(transaction.apply(current, undo))
                                        check(applyEdit(transaction, undo, current))
                                        (if (undo) transaction.removedLayers else transaction.insertedLayers).forEach {
                                            latestAssets[it.id] = it.rasterTileSurface to it.rasterRotationQuarterTurns
                                        }
                                        if (view.isBoundToSelectionPage(documentId, pageId)) {
                                            view.setRasterLayerSurfaces(runtime.reconcile(result))
                                            select(if (undo) transaction.beforeSelection else transaction.afterSelection)
                                            view.refreshSelectionTransferPixels()
                                        }
                                    }
                                } catch (error: Exception) {
                                    android.util.Log.e("InkloomLayers", "Could not restore layer tree history", error)
                                    reject(language.text("Could not restore layer history", "无法恢复图层操作历史"))
                                } finally { view.selectionLayerTransferPending = false }
                            }
                        }
                        view.recordExternalEdit(undo = { restore(true) }, redo = { restore(false) }, dispose = {
                            releaseAssets()
                            (transaction.removedLayers + transaction.insertedLayers).forEach { runtime.releaseHistoryLayer(it.id) }
                        })
                    }
                }
            } catch (error: LayerTreeRejected) {
                reject(language.text(when (error.reason) {
                    LayerTreeRejection.MISSING -> "Select an existing layer"
                    LayerTreeRejection.BACKGROUND -> "The paper layer cannot be grouped, copied or deleted"
                    LayerTreeRejection.LOCKED -> "Unlock the layers or their folders first"
                    LayerTreeRejection.MIXED_PARENTS -> "Move the selected layers into the same folder first"
                    LayerTreeRejection.CLIPPING -> "This clipping relationship cannot be preserved by grouping"
                    LayerTreeRejection.PROTECTED_CHILD -> "This group contains a protected system layer"
                    LayerTreeRejection.INVALID_STRUCTURE -> "This layer structure cannot be changed"
                }, when (error.reason) {
                    LayerTreeRejection.MISSING -> "请选择现有图层"
                    LayerTreeRejection.BACKGROUND -> "纸张图层不能编组、复制或删除"
                    LayerTreeRejection.LOCKED -> "请先解锁图层或所属分组"
                    LayerTreeRejection.MIXED_PARENTS -> "请先将所选图层移至同一分组"
                    LayerTreeRejection.CLIPPING -> "当前剪贴关系无法在编组后保持"
                    LayerTreeRejection.PROTECTED_CHILD -> "此分组包含受保护的系统图层"
                    LayerTreeRejection.INVALID_STRUCTURE -> "无法调整此图层结构"
                }))
            } catch (error: Exception) {
                android.util.Log.e("InkloomLayers", "Layer tree transaction failed", error)
                reject(language.text("Could not change layers. Please try again.", "无法调整图层，请重试。"))
            } finally {
                if (!published) releaseAssets()
                view.selectionLayerTransferPending = false
                view.refreshSelectionTransferPixels()
            }
        }
    }

    private suspend fun awaitPublication(view: InkCanvasView) {
        withTimeout(15_000) { while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8) }
        check(view.isBoundToSelectionPage(documentId, pageId))
    }

    private fun reject(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
