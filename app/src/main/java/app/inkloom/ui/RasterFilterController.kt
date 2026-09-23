package app.inkloom.ui

import android.content.Context
import android.widget.Toast
import app.inkloom.document.*
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.tools.RasterImageFilter
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** Page-scoped filter transaction. The document is untouched until Apply succeeds. */
internal class RasterFilterController(
    private val context: Context,
    private val documentId: String,
    private val pageId: String,
    private val assets: DocumentAssetService,
    private val runtime: RasterLayerSurfaceRuntime,
    private val mutex: Mutex,
    private val pendingSaves: AtomicInteger,
    private val scope: CoroutineScope,
    private val pageProvider: () -> InkPage,
    private val applyEdit: (RasterLayerFilterEdit, Boolean, InkPage?) -> Boolean,
    private val language: UiLanguage,
) {
    private data class Session(
        val layerId: String,
        val page: InkPage,
        val source: PixelTileSurface,
        val snapshot: PixelTileSurfaceSnapshot,
        val selection: MaskTileSurfaceSnapshot?,
        var generation: Int = 0,
        var effect: RasterImageFilter.Effect? = null,
        var preview: PixelTileSurface? = null,
        var worker: Job? = null,
    )

    private var session: Session? = null
    private var starting: Job? = null
    private var pendingEffect: RasterImageFilter.Effect? = null
    private var finishing = false

    fun preview(view: InkCanvasView, layerId: String, effect: RasterImageFilter.Effect) {
        if (finishing) return
        pendingEffect = effect
        val active = session
        if (active == null) {
            if (starting != null) return
            if (view.selectionLayerTransferPending || view.hasActivePaintContact()) {
                reject("Finish the current stroke or operation before filtering", "请先完成当前笔画或操作")
                return
            }
            view.selectionLayerTransferPending = true
            starting = scope.launch {
                try {
                    withTimeout(15_000) {
                        while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8)
                    }
                    mutex.withLock {
                        check(view.isBoundToSelectionPage(documentId, pageId))
                        val page = pageProvider()
                        val layer = page.layers.firstOrNull { it.id == layerId }
                        require(layer != null && layer.isPaintSurfaceLayer() &&
                            !LayerHierarchy.effectiveLocked(page.layers, layer) &&
                            LayerHierarchy.effectiveVisible(page.layers, layer)) {
                            "Choose an unlocked, visible pixel layer"
                        }
                        val surface = runtime.retainedSurface(layerId)
                            ?: withContext(Dispatchers.Default) {
                                assets.renderRasterLayerSurface(page, layerId, CanvasRasterResolution.forPage(page))
                            }
                        val selection = view.selectionMaskSurfaceSnapshot()?.takeUnless { it.isEmpty }
                        session = Session(layerId, page, surface, surface.snapshot(), selection)
                    }
                    session?.let { renderPreview(view, it, checkNotNull(pendingEffect)) }
                } catch (error: Exception) {
                    android.util.Log.e("InkloomFilters", "Could not start filter preview", error)
                    reject(error.message ?: "Could not preview this layer", "无法预览此图层：${error.message ?: "未知原因"}")
                    view.selectionLayerTransferPending = false
                } finally { starting = null }
            }
            return
        }
        if (active.layerId != layerId) {
            reject("Finish this filter before changing layers", "请先完成当前滤镜再切换图层")
            return
        }
        renderPreview(view, active, effect)
    }

    private fun renderPreview(view: InkCanvasView, current: Session, effect: RasterImageFilter.Effect) {
        current.effect = effect
        current.preview = null
        current.worker?.cancel()
        val generation = ++current.generation
        current.worker = scope.launch {
            try {
                val output = withContext(Dispatchers.Default) {
                    RasterImageFilter.apply(current.snapshot, effect, current.selection) {
                        !isActive || current.generation != generation
                    }
                }
                if (session !== current || generation != current.generation || finishing) return@launch
                current.preview = output
                val page = pageProvider()
                if (view.isBoundToSelectionPage(documentId, pageId)) {
                    view.setRasterLayerSurfaces(runtime.reconcile(page) + (current.layerId to output))
                }
            } catch (_: CancellationException) {
                // A newer parameter value owns the preview.
            } catch (error: Exception) {
                android.util.Log.e("InkloomFilters", "Filter preview failed", error)
                reject(error.message ?: "Filter preview failed", "滤镜预览失败：${error.message ?: "未知原因"}")
            }
        }
    }

    fun finish(view: InkCanvasView, apply: Boolean) {
        if (finishing) return
        finishing = true
        scope.launch(NonCancellable) {
            var newReference: RasterTileSurfaceRef? = null
            var oldReservation: RasterTileSurfaceRef? = null
            var published = false
            try {
                starting?.join()
                val current = session ?: return@launch
                current.worker?.join()
                if (!apply) return@launch
                mutex.withLock {
                    check(view.isBoundToSelectionPage(documentId, pageId))
                    val page = pageProvider()
                    val layer = checkNotNull(page.layers.firstOrNull { it.id == current.layerId })
                    check(layer.rasterTileSurface == current.page.layers.first { it.id == current.layerId }.rasterTileSurface) {
                        "The layer changed while the filter was open"
                    }
                    val output = current.preview ?: withContext(Dispatchers.Default) {
                        RasterImageFilter.apply(current.snapshot, checkNotNull(current.effect), current.selection)
                    }
                    val bitmap = withContext(Dispatchers.Default) { PixelTileBitmapAdapter.render(output.snapshot()) }
                    val durable = try {
                        withContext(Dispatchers.Default) {
                            assets.composeVisibleRasterTilePatch(page, current.layerId, bitmap,
                                0, 0, output.logicalWidth, output.logicalHeight)
                        }
                    } finally { bitmap.recycle() }
                    newReference = withContext(Dispatchers.IO) { assets.saveRasterTileSurface(durable) }
                    val before = layer.rasterTileSurface
                    if (before != null) oldReservation = withContext(Dispatchers.IO) {
                        assets.saveRasterTileSurface(assets.loadRasterTileSurface(before), before)
                    }
                    val edit = RasterLayerFilterEdit(current.layerId, before,
                        layer.rasterRotationQuarterTurns, checkNotNull(newReference))
                    val afterPage = checkNotNull(edit.apply(page, false))
                    withContext(Dispatchers.Main.immediate) {
                        check(applyEdit(edit, false, page)) { "The layer changed while saving the filter" }
                        published = true
                        runtime.retainHistoryLayer(afterPage, afterPage.layers.first { it.id == current.layerId }, output)
                        view.setRasterLayerSurfaces(runtime.reconcile(afterPage))
                        fun restore(undo: Boolean) {
                            view.selectionLayerTransferPending = true
                            scope.launch(NonCancellable) {
                                try {
                                    withTimeout(15_000) {
                                        while (view.hasPendingPaintPublicationForMerge() || pendingSaves.get() > 0) delay(8)
                                    }
                                    mutex.withLock {
                                        val beforeRestore = pageProvider()
                                        val restored = checkNotNull(edit.apply(beforeRestore, undo))
                                        check(applyEdit(edit, undo, beforeRestore))
                                        val restoredLayer = restored.layers.first { it.id == current.layerId }
                                        runtime.retainHistoryLayer(restored, restoredLayer,
                                            if (undo) current.source else output)
                                        if (view.isBoundToSelectionPage(documentId, pageId)) {
                                            view.setRasterLayerSurfaces(runtime.reconcile(restored))
                                        }
                                    }
                                } catch (error: Exception) {
                                    android.util.Log.e("InkloomFilters", "Could not restore filter history", error)
                                    reject("Could not restore filter history", "无法恢复滤镜历史")
                                } finally { view.selectionLayerTransferPending = false }
                            }
                        }
                        val afterReservation = checkNotNull(newReference)
                        val beforeReservation = oldReservation
                        view.recordExternalEdit(undo = { restore(true) }, redo = { restore(false) }, dispose = {
                            assets.releaseRasterTileReservation(afterReservation)
                            assets.releaseRasterTileReservation(beforeReservation)
                            runtime.releaseHistoryLayer(current.layerId)
                        })
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("InkloomFilters", "Could not apply filter", error)
                reject(error.message ?: "Could not apply filter", "无法应用滤镜：${error.message ?: "未知原因"}")
            } finally {
                if (!published) {
                    assets.releaseRasterTileReservation(newReference)
                    assets.releaseRasterTileReservation(oldReservation)
                    if (view.isBoundToSelectionPage(documentId, pageId)) {
                        view.setRasterLayerSurfaces(runtime.reconcile(pageProvider()))
                    }
                }
                session = null
                pendingEffect = null
                finishing = false
                view.selectionLayerTransferPending = false
            }
        }
    }

    private fun reject(english: String, chinese: String) {
        Toast.makeText(context, language.text(english, chinese), Toast.LENGTH_LONG).show()
    }
}
