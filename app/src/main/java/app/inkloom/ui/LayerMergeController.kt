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
    fun merge(view: InkCanvasView, sourceId: String) {
        if (view.selectionLayerTransferPending) return
        val capturedPage = pageProvider()
        RasterLayerMerge.rejection(capturedPage, sourceId)?.let { reject(it); return }
        view.selectionLayerTransferPending = true
        scope.launch(NonCancellable) {
            val reserved = mutableListOf<RasterTileSurfaceRef>()
            var releaseAssets: () -> Unit = {}
            var published = false
            try {
                // A finished worker stroke may still have a main-thread publication pending.
                // Its disk save must be enqueued and completed before the pair is captured.
                withTimeout(15_000) {
                    while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8)
                }
                mutex.withLock {
                    check(view.isBoundToSelectionPage(documentId, capturedPage.id))
                    val effectivePage = pageProvider().copy(layers = pageProvider().layers.map { layer ->
                        latestAssets[layer.id]?.let { layer.copy(rasterTileSurface = it.first, rasterRotationQuarterTurns = it.second) } ?: layer
                    })
                    require(RasterLayerMerge.rejection(effectivePage, sourceId) == null)
                    val index = effectivePage.layers.indexOfFirst { it.id == sourceId }
                    val pair = effectivePage.layers.subList(index - 1, index + 1)
                    val presentationWidth = CanvasRasterResolution.forPage(effectivePage)
                    val originalSurfaces = mutableMapOf<String, PixelTileSurface>()
                    pair.forEach { layer ->
                        originalSurfaces[layer.id] = runtime.retainedSurface(layer.id) ?: withContext(Dispatchers.Default) {
                            assets.renderRasterLayerSurface(effectivePage.copy(layers = effectivePage.layers.map {
                                it.copy(visible = true, parentId = null)
                            }), layer.id, presentationWidth)
                        }
                    }
                    val elementPaths = effectivePage.elements.filter { element -> pair.any { it.id == element.layerId } }.mapNotNull { element ->
                        when (element) {
                            is PageElement.Image -> element.assetPath
                            is PageElement.Fill -> element.maskAssetPath
                            is PageElement.Shape -> element.maskAssetPath
                            else -> null
                        }
                    }
                    releaseAssets = assets.retainHistoryAssets(elementPaths + pair.mapNotNull { it.mask?.surface?.assetPath })
                    val mergedFull = withContext(Dispatchers.Default) { assets.renderMergedLayerPair(effectivePage, sourceId) }
                    val mergedRef = withContext(Dispatchers.IO) { assets.saveRasterTileSurface(mergedFull) }.also { reserved += it }
                    val edit = RasterLayerMerge.edit(effectivePage, sourceId, mergedRef)
                    val mergedPage = edit.apply(effectivePage, false)
                    val mergedVisible = withContext(Dispatchers.Default) {
                        assets.renderRasterLayerSurface(mergedPage, edit.target.id, presentationWidth)
                    }
                    withContext(Dispatchers.IO) {
                        pair.mapNotNull { it.rasterTileSurface }.forEach { reference ->
                            reserved += assets.saveRasterTileSurface(assets.loadRasterTileSurface(reference), reference)
                        }
                    }
                    fun apply(undo: Boolean, expected: InkPage? = null) {
                        val presentPage = pageProvider()
                        val resultPage = edit.apply(presentPage, undo)
                        check(applyEdit(edit, undo, expected)) { "The page changed while merging" }
                        if (resultPage.crop == effectivePage.crop && resultPage.width == effectivePage.width && resultPage.height == effectivePage.height) {
                            pair.forEach { layer -> runtime.retainHistoryLayer(effectivePage, layer, originalSurfaces.getValue(layer.id)) }
                            if (!undo) runtime.retainHistoryLayer(mergedPage, edit.merged, mergedVisible)
                        }
                        (if (undo) pair else listOf(edit.merged)).forEach { layer ->
                            latestAssets[layer.id] = layer.rasterTileSurface to layer.rasterRotationQuarterTurns
                        }
                        if (!undo) latestAssets.remove(edit.source.id)
                        if (view.isBoundToSelectionPage(documentId, capturedPage.id)) {
                            view.setRasterLayerSurfaces(runtime.reconcile(resultPage))
                            chooseLayer(if (undo) edit.source.id else edit.target.id)
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
                                    Toast.makeText(context, language.text("Could not restore layer merge history", "无法恢复图层合并历史"), Toast.LENGTH_LONG).show()
                                } finally { view.selectionLayerTransferPending = false; view.refreshSelectionTransferPixels() }
                            }
                        }
                        view.recordExternalEdit(undo = { restore(true) }, redo = { restore(false) }, dispose = {
                            reserved.forEach(assets::releaseRasterTileReservation)
                            releaseAssets()
                            pair.forEach { runtime.releaseHistoryLayer(it.id) }
                        })
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("InkloomLayers", "Layer merge failed without replacing source layers", error)
                Toast.makeText(context, language.text("Could not merge layers. The page may have changed; please try again.", "无法合并图层，页面可能已变化，请重试。"), Toast.LENGTH_LONG).show()
            } finally {
                if (!published) { reserved.forEach(assets::releaseRasterTileReservation); releaseAssets() }
                view.selectionLayerTransferPending = false
                view.refreshSelectionTransferPixels()
            }
        }
    }
    private fun reject(reason: RasterLayerMerge.Rejection) {
        val message = when (reason) {
            RasterLayerMerge.Rejection.NO_LOWER_LAYER -> language.text("No lower paint layer", "下方没有绘画图层")
            RasterLayerMerge.Rejection.PROTECTED -> language.text("Unlock both paint layers and their folders first", "请先解锁两个绘画图层及所在分组")
            RasterLayerMerge.Rejection.DIFFERENT_GROUP -> language.text("Move both layers into the same group first", "请先将两个图层放入同一分组")
            RasterLayerMerge.Rejection.HIDDEN_OR_DRAFT_MISMATCH -> language.text("Both layers must have matching visibility and draft settings", "两个图层的可见性及草稿设置需要一致")
            RasterLayerMerge.Rejection.BLEND_MODE -> language.text("Merge currently supports Normal blend mode on both layers", "当前合并支持两个图层均为正常混合模式")
            RasterLayerMerge.Rejection.CLIPPING -> language.text("This clipping chain cannot be merged without changing its appearance", "此剪贴关系暂不能保持原有外观进行合并")
            RasterLayerMerge.Rejection.MASK_GEOMETRY -> language.text("This mask geometry is not supported for merging yet", "此蒙版的坐标或数据暂不支持合并")
            RasterLayerMerge.Rejection.PIXEL_FORMAT -> language.text("High-precision layers cannot yet be merged without losing precision", "高精度图层暂不支持无损合并")
            RasterLayerMerge.Rejection.SIZE_LIMIT -> language.text("This layer pair exceeds the current 4096-pixel merge limit", "此图层组合超出当前 4096 像素合并上限")
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
