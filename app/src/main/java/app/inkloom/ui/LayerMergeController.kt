package app.inkloom.ui

import android.content.Context
import android.widget.Toast
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** One page's explicit merge operation, sharing the painting host's save ordering and history. */
internal class LayerMergeController(
    private val context: Context,
    private val documentId: String,
    private val assets: DocumentAssetService,
    private val runtime: RasterLayerSurfaceRuntime,
    private val mutex: Mutex,
    private val latestAssets: MutableMap<String, Pair<RasterTileSurfaceRef?, Int>>,
    private val pendingSaves: AtomicInteger,
    private val scope: CoroutineScope,
    private val pageProvider: () -> InkPage,
    private val applyEdit: (RasterLayerMergeEdit, Boolean, InkPage?) -> Boolean,
    private val chooseLayer: (String) -> Unit,
    private val language: UiLanguage,
) {
    fun merge(view: InkCanvasView, sourceId: String) = merge(
        view,
        RasterLayerMergeRequest(RasterLayerMergeOperation.MERGE_DOWN, sourceId),
    )

    fun merge(view: InkCanvasView, request: RasterLayerMergeRequest) {
        if (view.selectionLayerTransferPending) return
        val capturedPage = pageProvider()
        val capturedPlan = RasterLayerMerge.plan(capturedPage, request)
        capturedPlan.rejection?.let { reject(it); return }
        view.selectionLayerTransferPending = true
        scope.launch(NonCancellable) {
            val reserved = mutableListOf<RasterTileSurfaceRef>()
            var releaseAssets: () -> Unit = {}
            var published = false
            try {
                // A finished worker stroke may still have a main-thread publication pending.
                // Its disk save must be enqueued and completed before the merge sources are captured.
                withTimeout(15_000) {
                    while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8)
                }
                mutex.withLock {
                    check(view.isBoundToSelectionPage(documentId, capturedPage.id))
                    val current = pageProvider()
                    val effectivePage = current.copy(layers = current.layers.map { layer ->
                        latestAssets[layer.id]?.let {
                            layer.copy(rasterTileSurface = it.first, rasterRotationQuarterTurns = it.second)
                        } ?: layer
                    })
                    val planning = RasterLayerMerge.plan(effectivePage, request)
                    planning.rejection?.let { reason -> withContext(Dispatchers.Main.immediate) { reject(reason) }; return@withLock }
                    val plan = requireNotNull(planning.plan)
                    val removedPaintLayers = effectivePage.layers.filter {
                        it.id in plan.removedLayerIds && it.isPaintSurfaceLayer()
                    }
                    val presentationWidth = CanvasRasterResolution.forPage(effectivePage)
                    val originalSurfaces = mutableMapOf<String, PixelTileSurface>()
                    removedPaintLayers.forEach { layer ->
                        originalSurfaces[layer.id] = runtime.retainedSurface(layer.id) ?: withContext(Dispatchers.Default) {
                            assets.renderRasterLayerSurface(
                                effectivePage.copy(layers = effectivePage.layers.map {
                                    it.copy(visible = true, parentId = null)
                                }),
                                layer.id,
                                presentationWidth,
                            )
                        }
                    }
                    val elementPaths = effectivePage.elements.filter { it.layerId in plan.removedLayerIds }.mapNotNull { element ->
                        when (element) {
                            is PageElement.Image -> element.assetPath
                            is PageElement.Fill -> element.maskAssetPath
                            is PageElement.Shape -> element.maskAssetPath
                            else -> null
                        }
                    }
                    val maskPaths = effectivePage.layers.filter { it.id in plan.removedLayerIds }
                        .mapNotNull { it.mask?.surface?.assetPath }
                    releaseAssets = assets.retainHistoryAssets(elementPaths + maskPaths)
                    val mergedFull = withContext(Dispatchers.Default) { assets.renderLayerMergeSurface(plan) }
                    val mergedRef = withContext(Dispatchers.IO) {
                        assets.saveRasterTileSurface(mergedFull)
                    }.also { reserved += it }
                    val edit = RasterLayerMerge.edit(effectivePage, plan, mergedRef)
                    val mergedPage = edit.apply(effectivePage, false)
                    val mergedVisible = withContext(Dispatchers.Default) {
                        assets.renderRasterLayerSurface(mergedPage, edit.merged.id, presentationWidth)
                    }
                    withContext(Dispatchers.IO) {
                        removedPaintLayers.forEach { layer ->
                            layer.rasterTileSurface?.let { reference ->
                                // History retention reserves the exact durable generation. The live
                                // presentation surface may have a different identity after projection.
                                reserved += assets.saveRasterTileSurface(assets.loadRasterTileSurface(reference), reference)
                            }
                        }
                    }
                    fun apply(undo: Boolean, expected: InkPage? = null) {
                        val presentPage = pageProvider()
                        val resultPage = edit.apply(presentPage, undo)
                        check(applyEdit(edit, undo, expected)) { "The page changed while merging" }
                        if (resultPage.crop == effectivePage.crop && resultPage.width == effectivePage.width && resultPage.height == effectivePage.height) {
                            removedPaintLayers.forEach { layer ->
                                runtime.retainHistoryLayer(effectivePage, layer, originalSurfaces.getValue(layer.id))
                            }
                            if (!undo) runtime.retainHistoryLayer(mergedPage, edit.merged, mergedVisible)
                        }
                        if (undo) {
                            plan.removedLayerIds.forEach(latestAssets::remove)
                            removedPaintLayers.forEach { layer ->
                                latestAssets[layer.id] = layer.rasterTileSurface to layer.rasterRotationQuarterTurns
                            }
                        } else {
                            plan.removedLayerIds.forEach(latestAssets::remove)
                            latestAssets[edit.merged.id] = edit.merged.rasterTileSurface to 0
                        }
                        if (view.isBoundToSelectionPage(documentId, capturedPage.id)) {
                            view.setRasterLayerSurfaces(runtime.reconcile(resultPage))
                            val restoredPrimary = request.primaryLayerId?.takeIf { id -> resultPage.layers.any { it.id == id } }
                            val forwardTarget = request.primaryLayerId?.takeIf {
                                request.operation == RasterLayerMergeOperation.TRANSFER_TO_LOWER &&
                                    resultPage.layers.any { layer -> layer.id == it }
                            } ?: edit.merged.id
                            chooseLayer(if (undo) restoredPrimary ?: removedPaintLayers.lastOrNull()?.id ?: edit.merged.id else forwardTarget)
                            view.refreshSelectionTransferPixels()
                        }
                    }
                    withContext(Dispatchers.Main.immediate) {
                        check(view.isBoundToSelectionPage(documentId, capturedPage.id))
                        apply(false, effectivePage)
                        published = true
                        fun restore(undo: Boolean) {
                            view.selectionLayerTransferPending = true
                            scope.launch(NonCancellable) {
                                try {
                                    withTimeout(15_000) {
                                        while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8)
                                    }
                                    mutex.withLock { apply(undo) }
                                } catch (error: Exception) {
                                    android.util.Log.e("InkloomLayers", "Layer merge history could not be restored", error)
                                    withContext(Dispatchers.Main.immediate) {
                                        Toast.makeText(context, language.text(
                                            "Could not restore layer merge history",
                                            "无法恢复图层合并历史",
                                        ), Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    view.selectionLayerTransferPending = false
                                    view.refreshSelectionTransferPixels()
                                }
                            }
                        }
                        view.recordExternalEdit(undo = { restore(true) }, redo = { restore(false) }, dispose = {
                            reserved.forEach(assets::releaseRasterTileReservation)
                            releaseAssets()
                            (removedPaintLayers.map(InkLayer::id) + edit.merged.id).distinct().forEach(runtime::releaseHistoryLayer)
                        })
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("InkloomLayers", "Layer merge failed without replacing source layers", error)
                withContext(Dispatchers.Main.immediate) {
                    Toast.makeText(context, language.text(
                        "Could not merge layers. The page may have changed; please try again.",
                        "无法合并图层，页面可能已变化，请重试。",
                    ), Toast.LENGTH_LONG).show()
                }
            } finally {
                if (!published) {
                    reserved.forEach(assets::releaseRasterTileReservation)
                    releaseAssets()
                }
                view.selectionLayerTransferPending = false
                view.refreshSelectionTransferPixels()
            }
        }
    }

    private fun reject(reason: RasterLayerMerge.Rejection) {
        val message = when (reason) {
            RasterLayerMerge.Rejection.NO_LOWER_LAYER -> language.text("No lower layer", "下方没有可合并图层")
            RasterLayerMerge.Rejection.NOT_ENOUGH_LAYERS -> language.text("Select at least two layers", "请至少选择两个图层")
            RasterLayerMerge.Rejection.NO_VISIBLE_LAYERS -> language.text("There are no visible drawing layers", "没有可见的绘画图层")
            RasterLayerMerge.Rejection.PROTECTED -> language.text("Unlock the affected layers and folders first", "请先解锁相关图层和图层组")
            RasterLayerMerge.Rejection.INVALID_SELECTION -> language.text("This layer selection cannot be merged", "当前图层选择无法合并")
            RasterLayerMerge.Rejection.PIXEL_FORMAT -> language.text("This pixel format cannot be merged without losing precision", "此像素格式暂时无法无损合并")
            RasterLayerMerge.Rejection.SIZE_LIMIT -> language.text("This merge exceeds the current 8192-pixel compositor limit", "此次合并超出当前 8192 像素合成限制")
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
