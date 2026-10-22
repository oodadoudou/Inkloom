package app.inkloom.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.inkloom.document.*
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.BrushToolTipReuse
import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.BrushOutput
import app.inkloom.brushpresetv5.DepositionSettings
import app.inkloom.brushpresetv5.DynamicsSettings
import app.inkloom.brushpresetv5.PresetIdentity
import app.inkloom.brushpresetv5.PressureMappings
import app.inkloom.brushpresetv5.TiltMappings
import app.inkloom.brushpresetv5.TipSettings
import app.inkloom.brushpresetv5.CompiledBrushProgramV5
import app.inkloom.importer.BrushRoleClassifier
import app.inkloom.model.*
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.settings.ShortcutActivation
import app.inkloom.settings.KeyInputDiagnostic
import app.inkloom.settings.MotionInputDiagnostic
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun PaperCanvas(layerPickMode: Boolean, onLayerPickCandidates: (List<String>) -> Unit, onLayerPickPreview: (String?) -> Unit, onApplyLayerInsertion: (String, String, app.inkloom.document.LayerInsertionEdit, Boolean) -> Boolean, onApplyLayerTreeEdit: (String, String, LayerTreeEdit, Boolean, InkPage?) -> Boolean, onApplyRasterLayerMerge: (String, String, RasterLayerMergeEdit, Boolean, InkPage?) -> Boolean, onApplyRasterLayerFilter: (String, String, RasterLayerFilterEdit, Boolean, InkPage?) -> Boolean, onApplySelectionLayerTransfer: (String, String, String, RasterTileSurfaceRef?, String, InkLayer?) -> Unit, onSelectionTransferLayerChosen: (String) -> Unit, modifier: Modifier, tool: EditorTool, onView: (InkCanvasView) -> Unit, page: InkPage, documentId: String, onSetLayerRasterTileSurface: (String, String, String, RasterTileSurfaceRef?, Int, app.inkloom.document.LayerElementEdit?) -> Unit, onElement: (String, String, PageElement) -> Unit, onRemoveElement: (String, String, String) -> Unit, onDuplicateElement: (String, String, String) -> Unit, selectedElementId: String?, onSelectElement: (String?) -> Unit, onBeginQuickShapeEdit: () -> Unit, assets: DocumentAssetService, selectionRuntime: DocumentSelectionRuntime, settings: AppSettings, viewportState: ViewportState, onZoom: (Float, Float, Float) -> Unit, onPinch: (Float, Float, Float, Float, Float) -> Unit, onPan: (Float, Float) -> Unit, onRotate: (Float) -> Unit, onUndoGesture: () -> Unit, onRedoGesture: () -> Unit, onCanvasContact: () -> Unit, onBackKey: () -> Boolean, onColorDropCommitted: (Int) -> Unit, activeBrush: ImportedBrush?, borrowPaintTipForEraser: Boolean, borrowPaintTipForSmudge: Boolean, brushSize: Float, brushOpacity: Float, brushColor: Int, secondaryBrushColor: Int, colorPreview: DrawingColorPreview, shapeKind: ShapeKind, activeLayerId: String, transformLayerIds: Set<String>, eraserPreset: EraserPreset, forceNavigation: Boolean, rotateNavigation: Boolean, searchBounds: PdfTextBounds?, onPdfGotoLink: (PdfGotoLink) -> Unit, onPageSwipe: (Boolean) -> Unit, documents: List<InkDocument>, onOpenInternalLink: (String, String) -> Unit, onInputDiagnostic: (String, Float, Float, Int) -> Unit, onColorSampled: (Int) -> Unit, onBrushSizeAdjusted: (Float) -> Unit, onKeyInputDiagnostic: (KeyInputDiagnostic) -> Unit, onMotionInputDiagnostic: (MotionInputDiagnostic) -> Unit, onShortcutAction: (ShortcutActivation) -> Unit, onSymmetryGuideChange: (SymmetryGuideState) -> Unit, onRulerGuideChange: (RulerGuideState) -> Unit, onCanvasGuideChange: (CanvasGuideState) -> Unit, onPerspectiveGuideChange: (PerspectiveGuideState) -> Unit, onSpecialRulerChange: (SpecialRulerState) -> Unit, onSelectionActiveChanged: (Boolean) -> Unit) {
    val renderedPage = remember(page) { PageCropTransform.project(page) }
    val rasterRenderWidth = remember(renderedPage.width, renderedPage.height) {
        CanvasRasterResolution.forDimensions(renderedPage.width, renderedPage.height)
    }
    val selectionMaskSize = remember(renderedPage.width, renderedPage.height) {
        requireNotNull(SelectionMaskCompositor.maskSize(renderedPage.width, renderedPage.height))
    }
    val documentSelectionState = remember(documentId, page.id, selectionMaskSize) {
        selectionRuntime.stateFor(documentId, page.id, selectionMaskSize.width, selectionMaskSize.height)
    }
    val layerMaskSurfaces = remember(page.layers.map { it.id to it.mask?.surface }) {
        page.layers.mapNotNull { layer ->
            layer.mask?.surface?.let { reference ->
                runCatching { assets.loadMaskTileSurface(reference).snapshot() }.getOrNull()?.let { layer.id to it }
            }
        }.toMap()
    }
    val context = LocalContext.current
    val gradientCommitScope = rememberCoroutineScope()
    val gradientCommitMutex = remember(documentId, page.id) { Mutex() }
    val rasterCommitMutex = remember(documentId, page.id) { Mutex() }
    val pendingRasterSaves = remember(documentId, page.id) { java.util.concurrent.atomic.AtomicInteger() }
    val currentMergePage by rememberUpdatedState(page)
    val rasterSurfaceRuntime = remember(documentId, page.id, rasterRenderWidth, page.colorManagement.highPrecision) {
        RasterLayerSurfaceRuntime(renderWidth = rasterRenderWidth, loader = assets::renderRasterLayerSurface)
    }
    val latestRasterAssets = remember(documentId, page.id) {
        page.layers.associate { layer ->
            layer.id to (layer.rasterTileSurface to layer.rasterRotationQuarterTurns)
        }.toMutableMap()
    }
    val filterController = remember(documentId, page.id, rasterSurfaceRuntime) {
        RasterFilterController(context, documentId, page.id, assets, rasterSurfaceRuntime,
            rasterCommitMutex, pendingRasterSaves, gradientCommitScope, { currentMergePage },
            { edit, undo, expected -> onApplyRasterLayerFilter(documentId, page.id, edit, undo, expected) },
            settings.language)
    }
    LaunchedEffect(page.layers) {
        page.layers.forEach { layer ->
            latestRasterAssets[layer.id] = layer.rasterTileSurface to layer.rasterRotationQuarterTurns
        }
    }
    val gradientSaveFailure = tr("The gradient mask could not be saved", "渐变蒙版保存失败")
    val commitGradient: (String, Float, Float, Float, Float, GradientDefinition, app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot?) -> Unit = commit@{ capturedLayerId, sx, sy, ex, ey, definition, selectionMask ->
        if (gradientCommitScope.coroutineContext[Job]?.isActive != true) {
            return@commit
        }
        val stops = definition.normalized().stops
        val capturedPage = renderedPage
        val capturedCrop = page.crop
        // A completed gesture is durable work: page navigation may dispose this composition, but
        // must not leave a reserved mask behind or silently drop the committed gradient.
        gradientCommitScope.launch(NonCancellable) {
            var reservedMaskPath: String? = null
            try {
                gradientCommitMutex.withLock {
                    val maskPath = if (selectionMask == null) null else withContext(Dispatchers.IO) {
                        assets.saveFillMask(selectionMask)
                    }
                    reservedMaskPath = maskPath
                    val gradient = PageElement.Shape(
                        x = 0f, y = 0f, width = capturedPage.width, height = capturedPage.height,
                        layerId = capturedLayerId, kind = ShapeKind.RECTANGLE,
                        colorArgb = stops.first().colorArgb, fillArgb = stops.first().colorArgb, strokeWidth = 0f,
                        gradientEndArgb = stops.last().colorArgb,
                        gradientStartX = sx, gradientStartY = sy,
                        gradientEndX = ex, gradientEndY = ey,
                        gradientKind = definition.kind, gradientEdgeMode = definition.edgeMode,
                        gradientReverse = definition.reverse, gradientDither = definition.dither,
                        gradientEllipseRatio = definition.ellipseRatio, gradientStops = stops,
                        maskAssetPath = maskPath,
                    )
                    onElement(documentId, page.id, PageCropTransform.unproject(gradient, capturedCrop))
                }
            } catch (error: Exception) {
                if (error !is kotlinx.coroutines.CancellationException) Toast.makeText(context, gradientSaveFailure, Toast.LENGTH_SHORT).show()
            } finally {
                assets.releaseAssetReservation(reservedMaskPath)
            }
        }
    }
    fun installFillCommit(view: InkCanvasView) {
        view.onSparseFillCommitted = { mask, color, opacity, edge -> view.paintFillIntoActiveRaster(mask, color, opacity, edge) }
        view.onSelectionStrokeRequested = request@{ request ->
            if (view.selectionLayerTransferPending) return@request
            val layerId = view.activeLayerId
            view.selectionLayerTransferPending = true
            gradientCommitScope.launch {
                try {
                    kotlinx.coroutines.withTimeout(15_000) {
                        while (view.hasPendingPaintPublicationForMerge() || pendingRasterSaves.get() > 0) kotlinx.coroutines.delay(8)
                    }
                    val coverage = withContext(Dispatchers.Default) {
                        app.inkloom.engine.v5.selection.SelectionStrokeMask.create(request.selection,
                            request.rasterWidth, request.rasterHeight, request.widthPixels, request.placement,
                            cancelled = { !isActive })
                    }
                    if (view.isBoundToSelectionPage(documentId, page.id) && view.activeLayerId == layerId) {
                        view.paintFillIntoActiveRaster(coverage.snapshot(), request.colorArgb.toUInt().toLong(), request.opacity, 0)
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Page disposal owns cancellation; no pixels/history have been published yet.
                } catch (error: Exception) {
                    android.util.Log.e("InkloomSelection", "Could not stroke selection", error)
                    Toast.makeText(context, settings.language.text("Could not stroke the selection", "无法为选区描边"), Toast.LENGTH_SHORT).show()
                } finally { view.selectionLayerTransferPending = false }
            }
        }
    }
    val selectionTransferFailure = tr("Could not transfer the selected pixels to a new layer", "无法将所选像素转移到新图层")
    val selectionLayerName = tr("From selection", "来自选区")
    fun installSelectionTransfer(view: InkCanvasView) {
        view.onSelectionLayerTransferRequested = request@{ cut ->
            if (view.selectionLayerTransferPending) return@request
            view.selectionLayerTransferPending = true
            val sourceId = view.activeLayerId
            val capturedPage = page
            val sourceLayer = capturedPage.layers.firstOrNull { it.id == sourceId }
            if (sourceLayer == null) { view.selectionLayerTransferPending = false; return@request }
            gradientCommitScope.launch(NonCancellable) {
                var transfer: app.inkloom.engine.v5.tools.SelectionLayerTransfer? = null
                val reserved = mutableListOf<RasterTileSurfaceRef>()
                var published = false
                try {
                    rasterCommitMutex.withLock {
                        val previous = latestRasterAssets[sourceId] ?: (sourceLayer.rasterTileSurface to sourceLayer.rasterRotationQuarterTurns)
                        val edit = withContext(Dispatchers.Main.immediate) {
                            check(view.isBoundToSelectionPage(documentId, capturedPage.id))
                            requireNotNull(view.prepareSelectionLayerTransfer(cut))
                        }
                        transfer = edit
                        val newLayer = InkLayer(name = selectionLayerName, parentId = sourceLayer.parentId)
                        val effectivePage = capturedPage.copy(layers = capturedPage.layers.map {
                            if (it.id == sourceId) it.copy(rasterTileSurface = previous.first, rasterRotationQuarterTurns = previous.second) else it
                        } + newLayer)
                        suspend fun saveVisible(surface: app.inkloom.engine.v5.surface.PixelTileSurface, layerId: String, previousRef: RasterTileSurfaceRef?): RasterTileSurfaceRef {
                            val bitmap = withContext(Dispatchers.Default) {
                                app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(surface.snapshot())
                            }
                            try {
                                val full = withContext(Dispatchers.Default) {
                                    assets.composeVisibleRasterTilePatch(effectivePage, layerId, bitmap, 0, 0, surface.logicalWidth, surface.logicalHeight)
                                }
                                return withContext(Dispatchers.IO) { assets.saveRasterTileSurface(full, previousRef) }.also { reserved += it }
                            } finally { bitmap.recycle() }
                        }
                        val targetRef = saveVisible(edit.destination, newLayer.id, null)
                        val sourceRef = if (cut) saveVisible(edit.source, sourceId, previous.first) else previous.first
                        val target = newLayer.copy(rasterTileSurface = targetRef)
                        fun apply(source: RasterTileSurfaceRef?, layer: InkLayer?) {
                            if (layer != null) rasterSurfaceRuntime.retainHistoryLayer(effectivePage, layer, edit.destination)
                            if (source != null) {
                                latestRasterAssets[sourceId] = source to previous.second
                                rasterSurfaceRuntime.acceptPublished(capturedPage, sourceId, source, previous.second)
                            }
                            onApplySelectionLayerTransfer(documentId, capturedPage.id, sourceId, source, target.id, layer)
                            if (view.isBoundToSelectionPage(documentId, capturedPage.id)) {
                                val appliedLayers = effectivePage.layers.filterNot { it.id == target.id }.map {
                                    if (it.id == sourceId && source != null) it.copy(rasterTileSurface = source) else it
                                } + listOfNotNull(layer)
                                view.setRasterLayerSurfaces(rasterSurfaceRuntime.reconcile(effectivePage.copy(layers = appliedLayers)))
                                view.refreshSelectionTransferPixels()
                                onSelectionTransferLayerChosen(if (layer == null) sourceId else target.id)
                            }
                        }
                        // Reserve original source checkpoints while history can restore them.
                        previous.first?.let { old ->
                            assets.loadRasterTileSurface(old).let { original ->
                                val pin = assets.saveRasterTileSurface(original, old)
                                reserved += pin
                            }
                        }
                        withContext(Dispatchers.Main.immediate) {
                        check(view.isBoundToSelectionPage(documentId, capturedPage.id))
                        apply(sourceRef, target)
                        published = true
                        view.recordExternalEdit(
                            undo = { if (cut) edit.undoSource(); apply(previous.first, null) },
                            redo = { if (cut) edit.redoSource(); apply(sourceRef, target) },
                            dispose = { edit.close(); reserved.forEach(assets::releaseRasterTileReservation); rasterSurfaceRuntime.releaseHistoryLayer(target.id) },
                        )
                        }
                    }
                } catch (failure: Exception) {
                    android.util.Log.e("InkloomSelection", "Selection layer transfer failed", failure)
                    if (!published) { transfer?.undoSource(); transfer?.close(); reserved.forEach(assets::releaseRasterTileReservation) }
                    Toast.makeText(context, selectionTransferFailure, Toast.LENGTH_LONG).show()
                } finally {
                    withContext(Dispatchers.Main.immediate) {
                        view.selectionLayerTransferPending = false
                        view.refreshSelectionTransferPixels()
                    }
                }
            }
        }
    }
    fun installRasterCommit(view: InkCanvasView) {
        view.onPrepareMixedSelection = { layerId, nativeWidth ->
            val elements=page.elements.filter { it.layerId==layerId }
            if(elements.isEmpty()) null else {
                val release=assets.retainHistoryAssets(elements.mapNotNull { element -> when(element) {
                    is PageElement.Image -> element.assetPath
                    is PageElement.Fill -> element.maskAssetPath
                    is PageElement.Shape -> element.maskAssetPath
                    else -> null
                } })
                try { MixedSelectionSource(elements, assets.renderElementLayerBitmap(page, layerId, nativeWidth), release = release) }
                catch(failure: Throwable) { release(); throw failure }
            }
        }

        installSelectionTransfer(view)
        view.onLayerCreationRequested = { targetId, folder ->
            LayerCreationEditor(documentId, page.id, { currentMergePage }, rasterSurfaceRuntime,
                pendingRasterSaves, rasterCommitMutex, latestRasterAssets,
                { edit, undo -> onApplyLayerInsertion(documentId, page.id, edit, undo) },
                { id -> onSelectionTransferLayerChosen(id); view.selectLayerContent(id) }, gradientCommitScope,
                { Toast.makeText(context, settings.language.text("Could not create or restore layer; finish the current operation or unlock its folder", "无法新建或恢复图层，请完成当前操作或解锁目标文件夹"), Toast.LENGTH_SHORT).show() }
            ).create(view, targetId, folder)
        }
        view.onAdjustmentLayerCreationRequested = { targetId, adjustment, onCreated ->
            LayerCreationEditor(documentId, page.id, { currentMergePage }, rasterSurfaceRuntime,
                pendingRasterSaves, rasterCommitMutex, latestRasterAssets,
                { edit, undo -> onApplyLayerInsertion(documentId, page.id, edit, undo) },
                { id -> onSelectionTransferLayerChosen(id); view.selectLayerContent(id) }, gradientCommitScope,
                { Toast.makeText(context, settings.language.text("Could not add adjustment layer", "无法添加调整图层"), Toast.LENGTH_SHORT).show() },
                assets,
            ).create(view, targetId, folder = false, adjustment = adjustment, onCreated = onCreated)
        }
        view.onLayerMergeRequested = { request ->
            LayerMergeController(context, documentId, assets, rasterSurfaceRuntime, rasterCommitMutex,
                latestRasterAssets, pendingRasterSaves, gradientCommitScope, { currentMergePage },
                { edit, undo, expected -> onApplyRasterLayerMerge(documentId, page.id, edit, undo, expected) },
                onSelectionTransferLayerChosen, settings.language).merge(view, request)
        }
        view.onRasterFilterPreviewRequested = { effect -> filterController.preview(view, view.activeLayerId, effect) }
        view.onRasterFilterFinished = { apply -> filterController.finish(view, apply) }
        view.onLayerTreeRequested = { action, targetId, selectedIds ->
            LayerTreeController(context, documentId, page.id, assets, rasterSurfaceRuntime, rasterCommitMutex,
                latestRasterAssets, pendingRasterSaves, gradientCommitScope, { currentMergePage },
                { edit, undo, expected -> onApplyLayerTreeEdit(documentId, page.id, edit, undo, expected) },
                { id -> onSelectionTransferLayerChosen(id); view.selectLayerContent(id) }, settings.language)
                .execute(view, action, targetId, selectedIds)
        }

        view.onRasterLayerCommitted = commit@{ commit ->
            val layerId = commit.layerId
            val capturedPage = page
            val layer = capturedPage.layers.firstOrNull { it.id == layerId && it.isPaintSurfaceLayer() }
            if (layer == null || gradientCommitScope.coroutineContext[Job]?.isActive != true) {
                return@commit
            }
            pendingRasterSaves.incrementAndGet()
            gradientCommitScope.launch(NonCancellable) {
                var newReference: RasterTileSurfaceRef? = null
                var patch: RasterLayerPatch? = null
                try {
                    rasterCommitMutex.withLock {
                        patch = withContext(Dispatchers.Default) { commit.capturePatch() }
                        val capturedPatch = requireNotNull(patch)
                        val previous = latestRasterAssets[layerId] ?:
                            (layer.rasterTileSurface to layer.rasterRotationQuarterTurns)
                        val effectivePage = capturedPage.copy(layers = capturedPage.layers.map { candidate ->
                            if (candidate.id != layerId) candidate else candidate.copy(
                                rasterTileSurface = previous.first,
                                rasterRotationQuarterTurns = previous.second,
                            )
                        })
                        val surface = withContext(Dispatchers.Default) {
                            assets.composeVisibleRasterTilePatch(
                                page = effectivePage,
                                layerId = layerId,
                                patch = capturedPatch.bitmap,
                                patchLeft = capturedPatch.left,
                                patchTop = capturedPatch.top,
                                visibleSourceWidth = capturedPatch.sourceWidth,
                                visibleSourceHeight = capturedPatch.sourceHeight,
                            )
                        }
                        newReference = withContext(Dispatchers.IO) {
                            assets.saveRasterTileSurface(surface, previous.first)
                        }
                        val savedReference = requireNotNull(newReference)
                        withContext(Dispatchers.Main.immediate) {
                            latestRasterAssets[layerId] = savedReference to previous.second
                            // The live surface can already contain later gestures. Associate this
                            // checkpoint with it before publishing the document reference so the
                            // resulting Compose update cannot replace newer pixels with older disk
                            // projection data. Document/Compose state is always published on main,
                            // even when a test or future host supplies a non-main parent scope.
                            rasterSurfaceRuntime.acceptPublished(capturedPage, layerId, savedReference, previous.second)
                            onSetLayerRasterTileSurface(documentId, capturedPage.id, layerId, savedReference, previous.second, commit.layerElements)
                        }
                        assets.releaseRasterTileReservation(savedReference)
                        newReference = null
                    }
                } finally {
                    assets.releaseRasterTileReservation(newReference)
                    patch?.close()
                    pendingRasterSaves.decrementAndGet()
                }
            }
        }
    }
    val backgroundVisible = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }?.visible != false
    val background = remember(page.id, page.background, page.backgroundRotationQuarterTurns, page.crop, page.backgroundFrame, page.backgroundSourceCrop, page.colorManagement, backgroundVisible, rasterRenderWidth) { if (backgroundVisible) runCatching { if(page.colorManagement.highPrecision || page.colorManagement.assignedProfile!=null || page.colorManagement.softProof) assets.renderManagedBackground(page,rasterRenderWidth) else assets.renderBackground(page.background, rasterRenderWidth, page.backgroundRotationQuarterTurns, page.backgroundSourceCrop ?: page.crop) }.getOrNull() else null }
    DisposableEffect(background) { onDispose { background?.recycle() } }
    val paperColor = (page.background as? PageBackground.Paper)?.colorArgb?.let(::Color) ?: Color.White
    val visibleLayerIds = page.layers.filter { LayerHierarchy.effectiveVisible(page.layers, it) && it.role != LayerRole.FOLDER }.mapTo(mutableSetOf()) { it.id }
    val lockedLayerIds = page.layers.filter { LayerHierarchy.effectiveLocked(page.layers, it) || it.role == LayerRole.FOLDER }.mapTo(mutableSetOf()) { it.id }
    val layerOpacities = page.layers.associate { it.id to it.opacity }
    val layerBlendModes = page.layers.associate { it.id to it.blendMode }
    val layerOrder = page.layers.mapIndexed { index, layer -> layer.id to index }.toMap()
    val retainedLayerKeys = RetainedLayerRenderKey.forPage(page)
    val layerElementBitmaps = remember(page.id, page.width, page.height, page.elements, retainedLayerKeys, page.crop, page.backgroundRotationQuarterTurns, rasterRenderWidth, page.colorManagement) {
        page.layers.mapNotNull { layer ->
            val elementIds = page.elements.filter { it.layerId == layer.id }.mapTo(linkedSetOf(), PageElement::id)
            if (elementIds.isEmpty() || !LayerHierarchy.effectiveVisible(page.layers, layer)) return@mapNotNull null
            layer.id to renderRetainedLayerElements(assets, page, layer, rasterRenderWidth)
        }.toMap()
    }
    DisposableEffect(layerElementBitmaps) { onDispose { layerElementBitmaps.values.forEach(LayerElementBitmaps::recycle) } }
    // This page-scoped runtime, rather than a disk-derived Compose value, owns editable pixels.
    // Brush imports, preset switches and asynchronous checkpoint publication are UI changes and
    // must never reconstruct or replace the live painting surface.
    val rasterLayerSurfaces = rasterSurfaceRuntime.reconcile(page)
    val activeLayerGuide = page.layers.firstOrNull { it.id == activeLayerId && it.drawingAssist }?.drawingGuide
    val symmetryMode = activeLayerGuide?.let { enumOrDefault(it.symmetryMode, settings.symmetryMode) } ?: settings.symmetryMode
    val symmetryGuide = (activeLayerGuide?.let { SymmetryGuideState(it.symmetryCenterX, it.symmetryCenterY, it.symmetryAngleDegrees) }
        ?: SymmetryGuideState(settings.symmetryCenterX, settings.symmetryCenterY, settings.symmetryAngleDegrees)).normalized()
    val rulerEnabled = activeLayerGuide?.rulerEnabled ?: settings.rulerEnabled
    val rulerGuide = (activeLayerGuide?.let { RulerGuideState(it.rulerCenterX, it.rulerCenterY, it.rulerAngleDegrees, it.rulerSnapDegrees) }
        ?: RulerGuideState(settings.rulerCenterX, settings.rulerCenterY, settings.rulerAngleDegrees, settings.rulerSnapDegrees)).normalized()
    val canvasGuide = (activeLayerGuide?.let { CanvasGuideState(it.gridSnapEnabled, it.gridSpacing, it.alignmentGuidesEnabled, it.verticalGuideX, it.horizontalGuideY) }
        ?: CanvasGuideState(settings.gridSnapEnabled, settings.gridSpacing, settings.alignmentGuidesEnabled, settings.verticalGuideX, settings.horizontalGuideY)).normalized()
    val perspectiveGuide = PerspectiveGuideState(
        mode = activeLayerGuide?.let { enumOrDefault(it.perspectiveMode, settings.perspectiveGuideMode) } ?: settings.perspectiveGuideMode,
        assisted = activeLayerGuide?.perspectiveAssisted ?: settings.perspectiveGuideAssisted,
        editing = activeLayerGuide?.perspectiveEditing ?: settings.perspectiveGuideEditing,
        points = listOf(
            PerspectiveGuidePoint(activeLayerGuide?.perspectivePoint1X ?: settings.perspectivePoint1X, activeLayerGuide?.perspectivePoint1Y ?: settings.perspectivePoint1Y),
            PerspectiveGuidePoint(activeLayerGuide?.perspectivePoint2X ?: settings.perspectivePoint2X, activeLayerGuide?.perspectivePoint2Y ?: settings.perspectivePoint2Y),
            PerspectiveGuidePoint(activeLayerGuide?.perspectivePoint3X ?: settings.perspectivePoint3X, activeLayerGuide?.perspectivePoint3Y ?: settings.perspectivePoint3Y),
        ),
    ).normalized()
    val specialRuler = SpecialRulerState(
        mode = activeLayerGuide?.let { enumOrDefault(it.specialRulerMode, settings.specialRulerMode) } ?: settings.specialRulerMode,
        assisted = activeLayerGuide?.specialRulerAssisted ?: settings.specialRulerAssisted,
        editing = activeLayerGuide?.specialRulerEditing ?: settings.specialRulerEditing,
        centerX = activeLayerGuide?.specialRulerCenterX ?: settings.specialRulerCenterX,
        centerY = activeLayerGuide?.specialRulerCenterY ?: settings.specialRulerCenterY,
        angleDegrees = activeLayerGuide?.specialRulerAngleDegrees ?: settings.specialRulerAngleDegrees,
        spacing = activeLayerGuide?.specialRulerSpacing ?: settings.specialRulerSpacing,
    ).normalized()
    // Existing documents keep their historical page-wide assist behavior until at least one
    // paint layer explicitly opts in. From that point, assistance follows the layer badges.
    val hasLayerDrawingAssist = page.layers.any { it.drawingAssist }
    val activeDrawingAssist = !hasLayerDrawingAssist || page.layers.firstOrNull { it.id == activeLayerId }?.drawingAssist == true
    val effectivePerspectiveGuide = perspectiveGuide.copy(assisted = perspectiveGuide.assisted && activeDrawingAssist)
    val effectiveSpecialRuler = specialRuler.copy(assisted = specialRuler.assisted && activeDrawingAssist)
    val gradientDefinition = GradientDefinition(
        kind = settings.gradientKind,
        edgeMode = settings.gradientEdgeMode,
        reverse = settings.gradientReverse,
        dither = settings.gradientDither,
        ellipseRatio = settings.gradientEllipseRatio,
        stops = GradientColors.normalize(settings.gradientStops).mapIndexed { index, stop ->
            if (index == 0) stop.copy(colorArgb = brushColor.toUInt().toLong()) else stop
        },
    ).normalized()
    BoxWithConstraints(
        modifier
            .graphicsLayer(
                scaleX = if (viewportState.mirrored) -viewportState.scale else viewportState.scale,
                scaleY = viewportState.scale,
                translationX = viewportState.panX,
                translationY = viewportState.panY,
                rotationZ = viewportState.rotationDegrees,
            )
            .shadow(3.dp, RoundedCornerShape(4.dp))
            .background(paperColor),
    ) {
        val density = LocalDensity.current
        val canvasWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val canvasHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val paperMaxWidth = maxWidth
        val paperMaxHeight = maxHeight
        AndroidView(
            factory = { context -> CanvasHost(context).also { host ->
                val view = host.canvasView
                view.bindDocumentSelectionState(documentSelectionState)
                view.tool = tool
                view.layerPickMode = layerPickMode
                view.onLayerPickCandidates = onLayerPickCandidates
                view.onLayerPickPreview = onLayerPickPreview
                view.forceNavigation = forceNavigation
                view.rotateNavigation = rotateNavigation
                view.externalViewportGestures = false
                view.viewportScale = viewportState.scale
                view.viewportPanX = viewportState.panX
                view.viewportPanY = viewportState.panY
                view.viewportRotationDegrees = viewportState.rotationDegrees
                view.viewportMirrored = viewportState.mirrored
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onQuickShapeCommitted = { projected ->
                    val committed = PageCropTransform.unproject(projected, page.crop)
                    onElement(documentId, page.id, committed)
                    onSelectElement(committed.id)
                    onBeginQuickShapeEdit()
                }
                view.onGradientCommitted = { sx, sy, ex, ey, definition, mask -> commitGradient(view.activeLayerId, sx, sy, ex, ey, definition, mask) }
                installFillCommit(view)
                installRasterCommit(view)
                view.onViewportScale = onZoom
                view.onViewportPinch = onPinch
                view.onViewportPan = onPan
                view.onViewportRotate = onRotate
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onPageSwipe = onPageSwipe
                view.onUndoGesture = onUndoGesture
                view.onRedoGesture = onRedoGesture
                view.onCanvasContact = onCanvasContact
                view.onBackKey = onBackKey
                view.onColorDropCommitted = onColorDropCommitted
                view.onSelectionActiveChanged = onSelectionActiveChanged
                view.singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
                view.fillSensitivity = settings.fillSensitivity
                view.fillSubTool = settings.fillSubTool
                view.fillCloseGapPixels = settings.fillCloseGapPixels
                view.fillAreaScalingPixels = settings.fillAreaScalingPixels
                view.fillTargetMode = settings.fillTargetMode
                view.fillTargetColor = settings.fillTargetColor
                view.fillExpansionShape = settings.fillExpansionShape
                view.fillAntiAlias = settings.fillAntiAlias
                view.symmetryMode = symmetryMode
                view.symmetryGuide = symmetryGuide
                view.onSymmetryGuideChanged = onSymmetryGuideChange
                view.symmetryAssisted = activeDrawingAssist
                view.twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
                view.threeFingerTapRedo = settings.threeFingerTapRedo
                view.twoFingerRotationEnabled = settings.twoFingerRotation && !settings.continuousPages
                view.onInputDiagnostic = onInputDiagnostic
                view.showStrokeTraceOverlay = settings.showStrokeTraceOverlay
                view.onColorSampled = onColorSampled
                view.onBrushSizeAdjusted = onBrushSizeAdjusted
                view.onKeyInputDiagnostic = onKeyInputDiagnostic
                view.onMotionInputDiagnostic = onMotionInputDiagnostic
                view.onShortcutAction = onShortcutAction
                view.rulerEnabled = rulerEnabled
                view.rulerGuide = rulerGuide
                view.onRulerGuideChanged = onRulerGuideChange
                view.rulerAssisted = activeDrawingAssist
                view.canvasGuideState = canvasGuide
                view.onCanvasGuideChanged = onCanvasGuideChange
                view.perspectiveGuideState = effectivePerspectiveGuide
                view.onPerspectiveGuideChanged = onPerspectiveGuideChange
                view.specialRulerState = effectiveSpecialRuler
                view.onSpecialRulerChanged = onSpecialRulerChange
                view.gradientDefinition = gradientDefinition
                view.gradientAngleSnapEnabled = settings.gradientAngleSnapEnabled
                view.gradientAngleSnapDegrees = settings.gradientAngleSnapDegrees
                view.editableGradient = (renderedPage.elements.firstOrNull { it.id == selectedElementId } as? PageElement.Shape)?.takeIf { it.gradientDefinition() != null && tool == EditorTool.SELECT }
                view.onGradientHandleChanged = { updated -> onElement(documentId, page.id, PageCropTransform.unproject(updated, page.crop)) }
                view.shortcutBindings = settings.shortcutBindings
                view.shortcutAxisBindings = settings.shortcutAxisBindings
                view.shortcutButtonBindings = settings.shortcutButtonBindings
                view.post { view.requestFocus() }
                onView(view)
            } },
            update = { host ->
                val view = host.canvasView
                view.bindDocumentSelectionState(documentSelectionState)
                view.tool = tool
                view.layerPickMode = layerPickMode
                view.onLayerPickCandidates = onLayerPickCandidates
                view.onLayerPickPreview = onLayerPickPreview
                view.forceNavigation = forceNavigation
                view.rotateNavigation = rotateNavigation
                view.externalViewportGestures = false
                view.viewportScale = viewportState.scale
                view.viewportPanX = viewportState.panX
                view.viewportPanY = viewportState.panY
                view.viewportRotationDegrees = viewportState.rotationDegrees
                view.viewportMirrored = viewportState.mirrored
                view.setDocumentLayers(page.layers)
                view.setProtectedLayerIds(lockedLayerIds)
                view.setColorManagement(page.colorManagement)
                view.setPageBackground(background, PageCropTransform.project(page).backgroundFrame?.let {
                    android.graphics.RectF(it.left / renderedPage.width, it.top / renderedPage.height,
                        (it.left + it.width) / renderedPage.width, (it.top + it.height) / renderedPage.height) })
                view.setLayerElementBitmaps(layerElementBitmaps, page.elements)
                view.pageHasImportedBackground = page.background !is PageBackground.Paper
                view.renderTextFreeReferenceLayer = { layerId, elements ->
                    renderRetainedLayerElements(assets, page.copy(elements = elements),
                        page.layers.first { it.id == layerId }, rasterRenderWidth, excludeText = true)
                }
                view.setRasterLayerSurfaces(filterController.presentationSurfaces(rasterLayerSurfaces))
                view.activeLayerId = activeLayerId
                view.selectionTransformLayerIds = transformLayerIds
                view.layerOpacities = layerOpacities
                view.layerBlendModes = layerBlendModes
                view.layerColorReplacements = page.layers.mapNotNull { layer ->
                    layer.colorReplacement?.let { layer.id to it }
                }.toMap()
                view.alphaLockedLayers = page.layers.filter { it.alphaLock }.mapTo(mutableSetOf()) { it.id }
                view.alphaLockBaseContentIds = page.layers.filter { it.alphaLock }.associate { it.id to it.alphaLockBaseContentIds.toSet() }
                view.clippedLayers = page.layers.filter { it.clippedToBelow }.mapTo(mutableSetOf()) { it.id }
                view.layerMasks = page.layers.mapNotNull { layer -> layer.mask?.let { layer.id to it } }.toMap()
                view.bindLayerMaskSurfaces(layerMaskSurfaces)
                view.layerOrder = page.layers.filter { it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) && LayerHierarchy.effectiveVisible(page.layers, it) }.map(InkLayer::id)
                view.pagePaperColor = if (renderedPage.background is PageBackground.Paper) (if (backgroundVisible) renderedPage.background.colorArgb.toInt() else AndroidColor.WHITE) else null
                view.pagePaperDensity = (renderedPage.background as? PageBackground.Paper)?.density ?: PaperDensity.DENSE
                view.pagePaperPattern = (renderedPage.background as? PageBackground.Paper)?.pattern?.takeIf { backgroundVisible }
                view.shapeKind = shapeKind
                view.pageModelWidth = renderedPage.width
                view.pageModelHeight = renderedPage.height
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onQuickShapeCommitted = { projected ->
                    val committed = PageCropTransform.unproject(projected, page.crop)
                    onElement(documentId, page.id, committed)
                    onSelectElement(committed.id)
                    onBeginQuickShapeEdit()
                }
                view.onGradientCommitted = { sx, sy, ex, ey, definition, mask -> commitGradient(view.activeLayerId, sx, sy, ex, ey, definition, mask) }
                installFillCommit(view)
                installRasterCommit(view)
                view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, colorPreview.foreground ?: brushColor, eraserPreset, colorPreview.secondary ?: secondaryBrushColor,
                    borrowPaintTipForEraser = borrowPaintTipForEraser, borrowPaintTipForSmudge = borrowPaintTipForSmudge)
                view.onViewportScale = onZoom
                view.onViewportPinch = onPinch
                view.onViewportPan = onPan
                view.onViewportRotate = onRotate
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onPageSwipe = onPageSwipe
                view.onUndoGesture = onUndoGesture
                view.onRedoGesture = onRedoGesture
                view.onCanvasContact = onCanvasContact
                view.onBackKey = onBackKey
                view.onColorDropCommitted = onColorDropCommitted
                view.onSelectionActiveChanged = onSelectionActiveChanged
                view.singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
                view.fillSensitivity = settings.fillSensitivity
                view.fillSubTool = settings.fillSubTool
                view.fillCloseGapPixels = settings.fillCloseGapPixels
                view.fillAreaScalingPixels = settings.fillAreaScalingPixels
                view.fillTargetMode = settings.fillTargetMode
                view.fillTargetColor = settings.fillTargetColor
                view.fillExpansionShape = settings.fillExpansionShape
                view.fillAntiAlias = settings.fillAntiAlias
                view.symmetryMode = symmetryMode
                view.symmetryGuide = symmetryGuide
                view.onSymmetryGuideChanged = onSymmetryGuideChange
                view.symmetryAssisted = activeDrawingAssist
                view.twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
                view.threeFingerTapRedo = settings.threeFingerTapRedo
                view.twoFingerRotationEnabled = settings.twoFingerRotation && !settings.continuousPages
                view.onInputDiagnostic = onInputDiagnostic
                view.showStrokeTraceOverlay = settings.showStrokeTraceOverlay
                view.onColorSampled = onColorSampled
                view.onBrushSizeAdjusted = onBrushSizeAdjusted
                view.onKeyInputDiagnostic = onKeyInputDiagnostic
                view.onMotionInputDiagnostic = onMotionInputDiagnostic
                view.onShortcutAction = onShortcutAction
                view.rulerEnabled = rulerEnabled
                view.rulerGuide = rulerGuide
                view.onRulerGuideChanged = onRulerGuideChange
                view.rulerAssisted = activeDrawingAssist
                view.canvasGuideState = canvasGuide
                view.onCanvasGuideChanged = onCanvasGuideChange
                view.perspectiveGuideState = effectivePerspectiveGuide
                view.onPerspectiveGuideChanged = onPerspectiveGuideChange
                view.specialRulerState = effectiveSpecialRuler
                view.onSpecialRulerChanged = onSpecialRulerChange
                view.gradientDefinition = gradientDefinition
                view.gradientAngleSnapEnabled = settings.gradientAngleSnapEnabled
                view.gradientAngleSnapDegrees = settings.gradientAngleSnapDegrees
                view.editableGradient = (renderedPage.elements.firstOrNull { it.id == selectedElementId } as? PageElement.Shape)?.takeIf { it.gradientDefinition() != null && tool == EditorTool.SELECT }
                view.onGradientHandleChanged = { updated -> onElement(documentId, page.id, PageCropTransform.unproject(updated, page.crop)) }
                view.shortcutBindings = settings.shortcutBindings
                view.shortcutAxisBindings = settings.shortcutAxisBindings
                view.shortcutButtonBindings = settings.shortcutButtonBindings
            },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Ink canvas" },
        )
        Box(Modifier.fillMaxSize()) {
            searchBounds?.let { PageCropTransform.project(it, page.crop) }?.let { bounds ->
                Canvas(Modifier.fillMaxSize()) {
                    val topLeft = Offset(bounds.left * size.width, bounds.top * size.height)
                    val highlightSize = androidx.compose.ui.geometry.Size((bounds.right - bounds.left) * size.width, (bounds.bottom - bounds.top) * size.height)
                    drawRect(Color(0x33111111), topLeft = topLeft, size = highlightSize)
                    drawRect(Color(0xFF111111), topLeft = topLeft, size = highlightSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

internal fun InkCanvasView.applyBrushPresetV5Configuration(
    preset: BrushPresetV5,
    settings: AppSettings,
    brushSize: Float = preset.tip.size,
    brushOpacity: Float = preset.deposition.opacity,
    brushColor: Int = AndroidColor.rgb(17, 17, 17),
    prepareAssets: Boolean = true,
) {
    val compiled = CompiledBrushProgramV5.compile(preset)
    feedbackLanguage = settings.language
    globalStrokeStabilization = settings.globalStrokeStabilization
    globalMotionFiltering = app.inkloom.brushpresetv5.MotionFilteringSettings(settings.globalMotionFiltering, settings.globalMotionExpression)
    switchInputConfiguration(EditorTool.PEN, preset.identity.id)
    setCompiledBrushProgramV5(compiled, prepareAssets = prepareAssets)
    fingerDrawingEnabled = settings.fingerDrawing
    drawAndHoldEnabled = settings.drawAndHoldEnabled
    drawAndHoldDelayMillis = settings.drawAndHoldDelayMillis
    singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
    pageSwipeEnabled = settings.swipeToTurnPages && !settings.continuousPages
    twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
    threeFingerTapRedo = settings.threeFingerTapRedo
    pressureSensitivity = settings.pressureSensitivity
    pressureMinimumInput = settings.pressureMinimumInput
    pressureMaximumInput = settings.pressureMaximumInput
    pressureResponseCurve = settings.pressureResponseCurve
    primaryButtonAction = settings.stylusPrimaryButton
    secondaryButtonAction = settings.stylusSecondaryButton
    primaryButtonTrigger = settings.stylusPrimaryTrigger
    secondaryButtonTrigger = settings.stylusSecondaryTrigger
    baseWidth = brushSize
    inkOpacity = brushOpacity
    inkColor = brushColor
    brushId = preset.identity.renderBrushId
    brushRenderSeed = preset.renderSeed
    notifyHoverCursorStyleChanged()
}

internal fun InkCanvasView.applyBrushConfiguration(
    selectedTool: EditorTool,
    settings: AppSettings,
    selectedBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    eraserPreset: EraserPreset,
    secondaryBrushColor: Int = AndroidColor.WHITE,
    borrowPaintTipForEraser: Boolean = false,
    borrowPaintTipForSmudge: Boolean = false,
) {
    // A delayed Compose frame or hardware-button transition must never carry a normal paint
    // descriptor into eraser mode. Imported erasers remain full brush presets; other brushes are
    // discarded at this boundary and the selected system eraser preset is used instead.
    val activeBrush = if (selectedTool == EditorTool.ERASER && !borrowPaintTipForEraser)
        selectedBrush?.takeIf(BrushRoleClassifier::isEraser) else selectedBrush
    val paintTool = selectedTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)
    feedbackLanguage = settings.language
    val defaultPreset = if (paintTool && activeBrush == null) BrushPresetV5(
            identity = PresetIdentity(
                id = when (selectedTool) {
                    EditorTool.ERASER -> "system-default-eraser"
                    EditorTool.HIGHLIGHTER -> "system-default-highlighter"
                    else -> "system-default-pen"
                },
                name = when (selectedTool) {
                    EditorTool.ERASER -> "Default Eraser"
                    EditorTool.HIGHLIGHTER -> "Default Highlighter"
                    else -> "Default Pen"
                },
                family = BrushFamily.INK,
                renderBrushId = when (selectedTool) {
                    EditorTool.ERASER -> "system-default-eraser"
                    EditorTool.HIGHLIGHTER -> "system-highlighter"
                    else -> "system-pen"
                },
            ),
            renderSeed = when (selectedTool) {
                EditorTool.ERASER -> 0x45A5E
                EditorTool.HIGHLIGHTER -> 0x41A11
                else -> 0x1A9E
            },
            output = if (selectedTool == EditorTool.ERASER) BrushOutput.ERASE else BrushOutput.PAINT,
            tip = TipSettings(
                size = brushSize,
                hardness = if (selectedTool == EditorTool.ERASER) eraserPreset.hardness else 1f,
            ),
            deposition = DepositionSettings(
                opacity = if (selectedTool == EditorTool.ERASER) eraserPreset.opacity else brushOpacity,
                flow = 1f,
            ),
            dynamics = DynamicsSettings(
                pressure = PressureMappings(
                    widthEnabled = if (selectedTool == EditorTool.ERASER) eraserPreset.pressureSize else selectedTool == EditorTool.PEN,
                    widthMinimum = .35f,
                ),
                tilt = TiltMappings(),
            ),
        ) else null
    val compiledProgram = when {
        !paintTool || activeBrush?.toolBinding?.isSupported == false -> null
        activeBrush != null && selectedTool == EditorTool.ERASER && borrowPaintTipForEraser ->
            BrushToolTipReuse.forEraser(activeBrush.v5PresetJson).getOrNull()
                ?.let(compiledBrushProgramV5Cache::resolvePreset)
        activeBrush != null -> compiledBrushProgramV5Cache.resolveEncoded(activeBrush.v5PresetJson)
        else -> compiledBrushProgramV5Cache.resolvePreset(requireNotNull(defaultPreset))
    }
    setCompiledBrushProgramV5(
        compiledProgram,
        required = paintTool,
        sourceBrush = activeBrush,
    )
    switchInputConfiguration(selectedTool, activeBrush?.id)
    fingerDrawingEnabled = settings.fingerDrawing
    drawAndHoldEnabled = settings.drawAndHoldEnabled
    drawAndHoldDelayMillis = settings.drawAndHoldDelayMillis
    singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
    pageSwipeEnabled = settings.swipeToTurnPages && !settings.continuousPages
    twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
    threeFingerTapRedo = settings.threeFingerTapRedo
    globalStrokeStabilization = settings.globalStrokeStabilization
    globalMotionFiltering = app.inkloom.brushpresetv5.MotionFilteringSettings(settings.globalMotionFiltering, settings.globalMotionExpression)
    pressureSensitivity = settings.pressureSensitivity
    pressureMinimumInput = settings.pressureMinimumInput
    pressureMaximumInput = settings.pressureMaximumInput
    pressureResponseCurve = settings.pressureResponseCurve
    primaryButtonAction = settings.stylusPrimaryButton
    secondaryButtonAction = settings.stylusSecondaryButton
    primaryButtonTrigger = settings.stylusPrimaryTrigger
    secondaryButtonTrigger = settings.stylusSecondaryTrigger
    selectionShape = settings.selectionShape
    selectionBrushSize = settings.selectionBrushSize
    selectionMaskVisibility = settings.selectionMaskVisibility
    selectionThreshold = settings.selectionThreshold
    selectionInterpolation = settings.selectionInterpolation
    liquifyMode = activeBrush?.toolBinding?.liquifyMode ?: LiquifyMode.PUSH
    liquifyStrength = ((activeBrush?.flow ?: .55f) * settings.liquifyStrengthScale).coerceIn(0f, 1f)
    liquifyHardness = activeBrush?.hardness ?: .5f
    liquifyDistortion = settings.liquifyDistortion
    liquifyMomentum = settings.liquifyMomentum
    selectionMode = settings.selectionMode
    selectionFilter = settings.selectionFilter
    referenceTarget = settings.referenceTarget
    excludeDraftLayersFromReference = settings.excludeDraftLayersFromReference
    excludeLockedLayersFromReference = settings.excludeLockedLayersFromReference
    excludeActiveLayerFromReference = settings.excludeActiveLayerFromReference
    excludePaperFromReference = settings.excludePaperFromReference
    excludeImportedBackgroundFromReference = settings.excludeImportedBackgroundFromReference
    excludeTextFromReference = settings.excludeTextFromReference
    eyedropperReferenceTarget = settings.eyedropperReferenceTarget
    eyedropperIncludePaper = settings.eyedropperIncludePaper
    fillSensitivity = settings.fillSensitivity
    fillSubTool = settings.fillSubTool
    fillCloseGapPixels = settings.fillCloseGapPixels
    fillAreaScalingPixels = settings.fillAreaScalingPixels
    fillTargetMode = settings.fillTargetMode
    fillTargetColor = settings.fillTargetColor
    fillExpansionShape = settings.fillExpansionShape
    fillAntiAlias = settings.fillAntiAlias
    correctLineMode = settings.correctLineMode
    correctLineAmount = settings.correctLineAmount
    baseWidth = brushSize
    brushSizeSyncViewScale = activeBrush?.sizeSyncViewScale ?: false
    val importedEraser = activeBrush?.takeIf { selectedTool == EditorTool.ERASER &&
        (BrushRoleClassifier.isEraser(it) || borrowPaintTipForEraser) }
    eraserWidth = if (importedEraser != null) brushSize else settings.eraserSize
    eraserHardness = importedEraser?.hardness ?: eraserPreset.hardness
    eraserOpacity = importedEraser?.let { brushOpacity * it.flow }?.coerceIn(.01f, 1f) ?: eraserPreset.opacity
    eraserPressureSize = importedEraser?.sizePressureEnabled ?: eraserPreset.pressureSize
    eraserTipAssetPath = importedEraser?.tipPreviewPath
    inkOpacity = brushOpacity
    inkColor = brushColor
    secondaryInkColor = secondaryBrushColor
    brushId = activeBrush?.renderBrushId ?: activeBrush?.id
        ?: if (selectedTool == EditorTool.HIGHLIGHTER) "system-highlighter" else "system-pen"
    brushRenderSeed = activeBrush?.renderSeed ?: brushId.hashCode()
    brushSpacing = activeBrush?.spacing ?: .12f
    brushSpacingMode = activeBrush?.spacingMode ?: CspBrushGap.FIXED
    brushAntiAliasMode = activeBrush?.antiAliasMode ?: CspAntiAlias.MEDIUM
    brushColorMix = activeBrush?.colorMix ?: 0f
    val borrowedSmudge = if (selectedTool == EditorTool.SMUDGE && borrowPaintTipForSmudge)
        BrushToolTipReuse.forSmudge(activeBrush?.v5PresetJson).getOrNull() else null
    setBorrowedSmudgeTip(borrowedSmudge)
    smudgeHardness = borrowedSmudge?.tip?.hardness ?: activeBrush?.hardness ?: .5f
    smudgeWidthPressure = borrowedSmudge?.dynamics?.pressure?.let { pressure ->
        app.inkloom.engine.v5.material.PressureResponse(pressure.widthEnabled, pressure.widthMinimum,
            pressure.widthCurve.map { it.x to it.y })
    } ?: app.inkloom.engine.v5.material.PressureResponse.CONSTANT
    smudgeStrength = borrowedSmudge?.let { (brushOpacity * it.deposition.opacity * it.deposition.flow).coerceIn(0f, 1f) } ?: 1f
    rasterToolMotionFiltering = compiledBrushProgramV5Cache.resolveEncoded(activeBrush?.v5PresetJson)?.preset?.stroke?.motionFiltering ?: app.inkloom.brushpresetv5.MotionFilteringSettings()
    rasterToolStrokeStabilization = if (selectedTool in setOf(EditorTool.SMUDGE, EditorTool.LIQUIFY)) {
        compiledBrushProgramV5Cache.resolveEncoded(activeBrush?.v5PresetJson)?.strokeShaping?.smoothing ?: 0f
    } else 0f
    notifyHoverCursorStyleChanged()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ObjectElementView(
    element: PageElement,
    page: InkPage,
    layerOpacity: Float,
    selected: Boolean,
    editable: Boolean,
    canvasWidth: androidx.compose.ui.unit.Dp,
    canvasHeight: androidx.compose.ui.unit.Dp,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onSelect: () -> Unit,
    onChange: (PageElement) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    internalLinkLabel: String?,
    onOpenInternalLink: (String, String) -> Unit,
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    eraserPreset: EraserPreset,
    showContent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Content is already painted by InkCanvasView. A disabled clickable still participates
    // in Compose hit testing and used to cover the entire page after a full-page fill.
    // Only actual object editing needs this transparent interaction plane.
    if (!showContent && !editable && !selected) return
    val context = LocalContext.current
    val elementAssets = remember(context) { DocumentAssetService(context) }
    val language = LocalUiLanguage.current
    var dragX by remember(element.id) { mutableFloatStateOf(0f) }
    var dragY by remember(element.id) { mutableFloatStateOf(0f) }
    var editing by remember(element.id) { mutableStateOf(false) }
    val warpedBounds = element.deformation?.let { element.displayBounds(page.width, page.height) }
    val displayX = warpedBounds?.minOf { it.x }?.div(page.width) ?: element.x
    val displayY = warpedBounds?.minOf { it.y }?.div(page.height) ?: element.y
    val displayWidth = warpedBounds?.let { it.maxOf { p -> p.x } - it.minOf { p -> p.x } } ?: element.width
    val displayHeight = warpedBounds?.let { it.maxOf { p -> p.y } - it.minOf { p -> p.y } } ?: element.height
    val objectWidth = (canvasWidth * (displayWidth / page.width)).coerceIn(54.dp, canvasWidth)
    val objectHeight = (canvasHeight * (displayHeight / page.height)).coerceIn(42.dp, canvasHeight)
    val gradientForHandles = (element as? PageElement.Shape)?.takeIf { selected && editable && it.gradientDefinition() != null }
    val gestureModifier = if (editable) Modifier.pointerInput(element.id, canvasWidthPx, canvasHeightPx, gradientForHandles != null) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val passToCanvas = gradientForHandles != null && run {
                val handles = listOf(
                    (gradientForHandles.gradientStartX * canvasWidthPx) to (gradientForHandles.gradientStartY * canvasHeightPx),
                    (gradientForHandles.gradientEndX * canvasWidthPx) to (gradientForHandles.gradientEndY * canvasHeightPx),
                )
                val radius = viewConfiguration.touchSlop * 3f
                handles.any { (hx, hy) ->
                    val dx = down.position.x - hx; val dy = down.position.y - hy
                    (dx * dx + dy * dy) <= radius * radius
                }
            }
            if (passToCanvas) {
                return@awaitEachGesture
            }
            onSelect()
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.size > 1) {
                    change.consume()
                    dragX = 0f; dragY = 0f
                    break
                }
                dx += change.positionChange().x
                dy += change.positionChange().y
                change.consume()
                dragX = dx; dragY = dy
                if (!change.pressed) break
            }
            if (dx != 0f || dy != 0f) {
                onChange(moveElement(element, dx / canvasWidthPx, dy / canvasHeightPx))
            }
            dragX = 0f; dragY = 0f
        }
    } else Modifier
    Box(
        modifier
            .offset(x = canvasWidth * displayX, y = canvasHeight * displayY)
            .size(objectWidth, objectHeight)
            .graphicsLayer(rotationZ = if (warpedBounds == null) element.rotationDegrees else 0f, alpha = (element.opacity * layerOpacity).coerceIn(0f, 1f), translationX = dragX, translationY = dragY)
            .then(if (selected) Modifier.border(2.dp, Sage, RoundedCornerShape(5.dp)) else Modifier)
            .then(gestureModifier)
            .combinedClickable(
                enabled = editable,
                onClick = onSelect,
                onDoubleClick = {
                    onSelect()
                    when (element) {
                        is PageElement.Sticky -> editing = true
                        is PageElement.Link -> if (InternalLinkTarget.isInternal(element)) {
                            val documentId = element.targetDocumentId
                            val pageId = element.targetPageId
                            if (documentId != null && pageId != null) onOpenInternalLink(documentId, pageId)
                        } else openLink(context, element.url, language)
                        else -> Unit
                    }
                },
            ),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer(alpha = if (showContent) 1f else 0f)) {
            when (element) {
            is PageElement.Text -> {
                val scaledTextSize = (element.textSize * canvasWidth.value / page.width).coerceIn(10f, 40f)
                Text(
                    RichTextFormatter.displayText(element.text, element.listStyle),
                    color = Color(element.colorArgb),
                    fontSize = scaledTextSize.sp,
                    lineHeight = (scaledTextSize * element.lineSpacing.coerceIn(.8f, 2f)).sp,
                    fontWeight = if (element.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (element.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (element.underline) TextDecoration.Underline else TextDecoration.None,
                    textAlign = when (element.alignment) { InkTextAlignment.START -> TextAlign.Start; InkTextAlignment.CENTER -> TextAlign.Center; InkTextAlignment.END -> TextAlign.End },
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                )
            }
            is PageElement.Sticky -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(element.colorArgb), contentColor = Color.DarkGray)) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        if (element.collapsed) element.text.lineSequence().firstOrNull().orEmpty() else element.text,
                        Modifier.padding(10.dp),
                        maxLines = if (element.collapsed) 1 else 12,
                    )
                }
            }
            is PageElement.Shape -> Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val color = Color(element.colorArgb)
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(element.strokeWidth.coerceIn(1f, 40f))
                val triangle = androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                val diamond = androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close() }
                val freeform = androidx.compose.ui.graphics.Path().apply {
                    element.path.firstOrNull()?.let { first ->
                        moveTo(first.x * size.width, first.y * size.height)
                        for (index in 1 until element.path.size) { val vertex = element.path[index]; lineTo(vertex.x * size.width, vertex.y * size.height) }
                        close()
                    }
                }
                element.fillArgb?.let { fill ->
                    val gradientDefinition = element.gradientDefinition()
                    if (element.kind == ShapeKind.RECTANGLE && gradientDefinition != null) {
                        drawIntoCanvas { composeCanvas ->
                            val nativeCanvas = composeCanvas.nativeCanvas
                            val nativePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                isDither = gradientDefinition.dither
                                shader = GradientShaderFactory.create(
                                    gradientDefinition,
                                    element.gradientStartX * size.width,
                                    element.gradientStartY * size.height,
                                    element.gradientEndX * size.width,
                                    element.gradientEndY * size.height,
                                )
                            }
                            val maskPath = element.maskAssetPath
                            if (maskPath == null) {
                                nativeCanvas.drawRect(0f, 0f, size.width, size.height, nativePaint)
                            } else {
                                val checkpoint = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                                val maskDrawn = elementAssets.drawFillMask(
                                    nativeCanvas,
                                    maskPath,
                                    android.graphics.RectF(0f, 0f, size.width, size.height),
                                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG),
                                )
                                if (maskDrawn) {
                                    nativePaint.blendMode = android.graphics.BlendMode.SRC_IN
                                    nativeCanvas.drawRect(0f, 0f, size.width, size.height, nativePaint)
                                    nativePaint.blendMode = null
                                }
                                nativeCanvas.restoreToCount(checkpoint)
                            }
                        }
                    } else when (element.kind) { ShapeKind.ELLIPSE -> drawOval(Color(fill)); ShapeKind.RECTANGLE -> drawRect(Color(fill)); ShapeKind.TRIANGLE -> drawPath(triangle, Color(fill)); ShapeKind.DIAMOND -> drawPath(diamond, Color(fill)); ShapeKind.FREEFORM -> drawPath(freeform, Color(fill)); else -> Unit }
                }
                if (element.strokeWidth > 0f) when (element.kind) {
                    ShapeKind.ELLIPSE -> drawOval(color, style = stroke)
                    ShapeKind.RECTANGLE -> drawRect(color, style = stroke)
                    ShapeKind.TRIANGLE -> drawPath(triangle, color, style = stroke)
                    ShapeKind.DIAMOND -> drawPath(diamond, color, style = stroke)
                    ShapeKind.LINE -> drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = element.strokeWidth)
                    ShapeKind.ARROW -> { val y = size.height / 2f; drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = element.strokeWidth); drawLine(color, Offset(size.width, y), Offset(size.width - 24f, y - 16f), strokeWidth = element.strokeWidth); drawLine(color, Offset(size.width, y), Offset(size.width - 24f, y + 16f), strokeWidth = element.strokeWidth) }
                    ShapeKind.FREEFORM -> drawPath(freeform, color, style = stroke)
                }
            }
            is PageElement.Image -> {
                val normalizedCrop = element.crop.normalized()
                val bitmap = remember(element.assetPath, normalizedCrop) { ImageAssetDecoder.decodeCropped(element.assetPath, normalizedCrop) }
                DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
                val maskModifier = when (element.mask) {
                    ImageMask.RECTANGLE -> Modifier
                    ImageMask.ROUNDED -> Modifier.clip(RoundedCornerShape((element.cornerRadius * canvasWidth.value / page.width).coerceIn(0f, 120f).dp))
                    ImageMask.OVAL -> Modifier.clip(CircleShape)
                }
                if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), tr("Page image", "页面图片"), Modifier.fillMaxSize().then(maskModifier), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Workspace), contentAlignment = Alignment.Center) { Text(tr("Image unavailable", "图片不可用"), color = MutedInk, fontSize = 11.sp) }
            }
            is PageElement.Fill -> {
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { composeCanvas ->
                        elementAssets.drawFillMask(
                            composeCanvas.nativeCanvas,
                            element.maskAssetPath,
                            android.graphics.RectF(0f, 0f, size.width, size.height),
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                                colorFilter = android.graphics.PorterDuffColorFilter(
                                    element.colorArgb.toInt(),
                                    android.graphics.PorterDuff.Mode.SRC_IN,
                                )
                            },
                        )
                    }
                }
            }
            is PageElement.Link -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7E7E7), contentColor = Color(0xFF111111))) {
                Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White) { Icon(Icons.Outlined.Link, null, Modifier.padding(10.dp), tint = Color(0xFF111111)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(element.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(internalLinkLabel ?: LinkTarget.displayDestination(element.url), color = Color(0xFF626262), fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = {
                        val documentId = element.targetDocumentId
                        val pageId = element.targetPageId
                        if (InternalLinkTarget.isInternal(element) && documentId != null && pageId != null) onOpenInternalLink(documentId, pageId)
                        else openLink(context, element.url, language)
                    }) { Icon(if (InternalLinkTarget.isInternal(element)) Icons.Outlined.SubdirectoryArrowRight else Icons.Outlined.OpenInNew, tr("Open link", "打开链接")) }
                }
            }
            }
        }
        if (selected && editable) Surface(Modifier.align(Alignment.TopEnd).offset(y = (-38).dp), shape = RoundedCornerShape(18.dp), color = Paper, shadowElevation = 4.dp) {
            Row {
                if (element is PageElement.Sticky) {
                    IconButton(onClick = { onChange(element.copy(collapsed = !element.collapsed)) }, modifier = Modifier.size(36.dp)) { Icon(if (element.collapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess, if (element.collapsed) tr("Expand sticky note", "展开便签") else tr("Collapse sticky note", "折叠便签"), Modifier.size(18.dp)) }
                }
                if (element is PageElement.Link) IconButton(onClick = {
                    val documentId = element.targetDocumentId
                    val pageId = element.targetPageId
                    if (InternalLinkTarget.isInternal(element) && documentId != null && pageId != null) onOpenInternalLink(documentId, pageId)
                    else openLink(context, element.url, language)
                }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.OpenInNew, tr("Open link", "打开链接"), Modifier.size(18.dp)) }
                IconButton(onClick = { editing = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Tune, tr("Edit object", "编辑对象"), Modifier.size(18.dp)) }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, tr("Duplicate object", "复制对象"), Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Delete, tr("Delete object", "删除对象"), Modifier.size(18.dp)) }
            }
        }
    }
    if (editing) ElementEditorDialog(element, onDismiss = { editing = false }, onSave = { onChange(it); editing = false })
}
@Composable
private fun ImageTransformPreview(image: PageElement.Image) {
    val crop = image.crop.normalized()
    val bitmap = remember(image.assetPath, crop) { ImageAssetDecoder.decodeCropped(image.assetPath, crop) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Workspace), contentAlignment = Alignment.Center) {
        val ratio = (image.width / image.height.coerceAtLeast(1f)).coerceIn(.01f, 100f)
        val previewWidth = minOf(maxWidth.value * .8f, maxHeight.value * .8f * ratio)
        val shape = when (image.mask) {
            ImageMask.RECTANGLE -> androidx.compose.ui.graphics.RectangleShape
            ImageMask.ROUNDED -> RoundedCornerShape((image.cornerRadius / image.width.coerceAtLeast(1f) * previewWidth).dp)
            ImageMask.OVAL -> CircleShape
        }
        bitmap?.let { pixels ->
            Image(pixels.asImageBitmap(), tr("Image transform preview", "图片变换预览"), contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(previewWidth.dp, (previewWidth / ratio).dp).graphicsLayer(rotationZ = image.rotationDegrees, alpha = image.opacity).clip(shape))
        }
    }
}

@Composable
internal fun ElementEditorDialog(element: PageElement, onDismiss: () -> Unit, onSave: (PageElement) -> Unit) {
    var draft by remember(element.id) { mutableStateOf(element) }
    val defaultLinkedPageTitle = tr("Linked page", "链接页面")
    val normalizedDraftLink = (draft as? PageElement.Link)?.takeUnless(InternalLinkTarget::isInternal)?.let { LinkTarget.normalize(it.url) }
    val canSave = (draft as? PageElement.Link)?.let { InternalLinkTarget.isInternal(it) || normalizedDraftLink != null } ?: true
    StudioDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (element is PageElement.Image) tr("Transform image", "变换图片") else tr("Object properties", "对象属性")) },
        text = { LazyColumn(Modifier.dialogContentWidth(340.dp, 520.dp).heightIn(max = 520.dp).semantics { contentDescription = "Object properties content" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val image = draft as? PageElement.Image
            if (image != null) {
                item { ImageTransformPreview(image) }
                item { BrushSettingSlider("Width", image.width, 40f..3200f) { draft = image.copy(width = it) } }
                item { BrushSettingSlider("Height", image.height, 40f..4526f) { draft = image.copy(height = it) } }
                item { BrushSettingSlider("Rotation", image.rotationDegrees, -180f..180f) { draft = image.copy(rotationDegrees = it) } }
            }
            when (val current = draft) {
                is PageElement.Text -> {
                    item { StudioOutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text(tr("Text", "文本")) }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                    item { BrushSettingSlider("Text size", current.textSize, 16f..160f) { draft = current.copy(textSize = it) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudioFilterChip(selected = current.bold, onClick = { draft = current.copy(bold = !current.bold) }, label = { Text(tr("Bold", "粗体"), fontWeight = FontWeight.Bold) })
                        StudioFilterChip(selected = current.italic, onClick = { draft = current.copy(italic = !current.italic) }, label = { Text(tr("Italic", "斜体"), fontStyle = FontStyle.Italic) })
                        StudioFilterChip(selected = current.underline, onClick = { draft = current.copy(underline = !current.underline) }, label = { Text(tr("Underline", "下划线"), textDecoration = TextDecoration.Underline) })
                    } }
                    item { Text(tr("Alignment", "对齐方式"), color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { InkTextAlignment.entries.forEach { alignment -> StudioFilterChip(selected = current.alignment == alignment, onClick = { draft = current.copy(alignment = alignment) }, label = { Text(localizedTextAlignment(alignment)) }) } } }
                    item { Text(tr("List style", "列表样式"), color = MutedInk, fontSize = 11.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextListStyle.entries.forEach { style -> item { StudioFilterChip(selected = current.listStyle == style, onClick = { draft = current.copy(listStyle = style) }, label = { Text(localizedListStyle(style)) }) } } } }
                    item { BrushSettingSlider("Line spacing", current.lineSpacing, .8f..2f) { draft = current.copy(lineSpacing = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt(), onColor = { draft = current.copy(colorArgb = it.toUInt().toLong()) }) } }
                }
                is PageElement.Sticky -> {
                    item { StudioOutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text(tr("Note", "便签")) }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
                    item { SettingSwitch("Collapsed", "Show only the first line.", current.collapsed) { draft = current.copy(collapsed = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt(), onColor = { draft = current.copy(colorArgb = it.toUInt().toLong()) }) } }
                }
                is PageElement.Shape -> {
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> item { StudioFilterChip(selected = current.kind == kind, onClick = {
                        val path = if (kind == ShapeKind.FREEFORM && current.path.size < 3) listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, 0f), ShapeVertex(1f, 1f), ShapeVertex(0f, 1f)) else current.path
                        draft = current.copy(kind = kind, path = path)
                    }, label = { Text(localizedShapeKind(kind)) }) } } } }
                    item { BrushSettingSlider("Stroke width", current.strokeWidth, 1f..40f) { draft = current.copy(strokeWidth = it) } }
                    item { SettingSwitch("Fill", "Adds a translucent shape fill.", current.fillArgb != null) { draft = current.copy(fillArgb = if (it) 0x33111111 else null) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Stroke color", "描边颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt(), onColor = { draft = current.copy(colorArgb = it.toUInt().toLong()) }) } }
                }
                is PageElement.Image -> {
                    if (current.source != null) item { FileObjectControls(current) { draft = it } }
                    item { Text(tr("Image source is retained unchanged. Crop and mask remain reversible and are included in editable backups.", "图片源文件保持不变。裁剪和蒙版均可撤销，并会包含在可编辑备份中。"), color = MutedInk, fontSize = 12.sp) }
                    item { Text(tr("Mask", "蒙版"), color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ImageMask.entries.forEach { mask -> StudioFilterChip(selected = current.mask == mask, onClick = { draft = current.copy(mask = mask) }, label = { Text(localizedImageMask(mask)) }) } } }
                    if (current.mask == ImageMask.ROUNDED) item { BrushSettingSlider("Corner radius", current.cornerRadius, 0f..200f) { draft = current.copy(cornerRadius = it) } }
                    item { Text(tr("Crop edges", "裁剪边缘"), color = MutedInk, fontSize = 11.sp) }
                    item { BrushSettingSlider("Left", current.crop.left, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(left = it).normalized()) } }
                    item { BrushSettingSlider("Top", current.crop.top, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(top = it).normalized()) } }
                    item { BrushSettingSlider("Right", current.crop.right, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(right = it).normalized()) } }
                    item { BrushSettingSlider("Bottom", current.crop.bottom, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(bottom = it).normalized()) } }
                    item { OutlinedButton(onClick = { draft = current.copy(crop = ImageCrop()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset crop", "重置裁剪")) } }
                }
                is PageElement.Link -> {
                    item { StudioOutlinedTextField(current.title, { draft = current.copy(title = it) }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    if (InternalLinkTarget.isInternal(current)) item {
                        Text(tr("This object links to a notebook page. Create a new link to change its destination.", "此对象链接到笔记页面。如需更改目标，请新建链接。"), color = MutedInk, fontSize = 12.sp)
                    } else item { StudioOutlinedTextField(current.url, { draft = current.copy(url = it) }, label = { Text(tr("Web, email, or phone address", "网页、邮箱或电话号码")) }, isError = LinkTarget.normalize(current.url) == null, supportingText = { Text(if (LinkTarget.normalize(current.url) == null) tr("Use a valid http, https, mailto, or tel address.", "请输入有效的 http、https、mailto 或 tel 地址。") else LinkTarget.displayDestination(current.url)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                }
                is PageElement.Fill -> {
                    item { Text(tr("Fill color", "填充颜色") + ": #" + "%08X".format(current.colorArgb), color = MutedInk, fontSize = 11.sp) }
                }
            }
            item { BrushSettingSlider("Opacity", draft.opacity, .05f..1f) { draft = setElementOpacity(draft, it) } }
            if (draft !is PageElement.Image) {
                item { BrushSettingSlider("Rotation", draft.rotationDegrees, -180f..180f) { draft = setElementRotation(draft, it) } }
                item { BrushSettingSlider("Width", draft.width, 40f..1600f) { draft = setElementSize(draft, it, draft.height) } }
                item { BrushSettingSlider("Height", draft.height, 40f..2263f) { draft = setElementSize(draft, draft.width, it) } }
            }
        } },
        confirmButton = { Button(enabled = canSave, onClick = {
            val saved = (draft as? PageElement.Link)?.let { link ->
                if (InternalLinkTarget.isInternal(link)) link.copy(title = link.title.trim().ifBlank { defaultLinkedPageTitle })
                else {
                    val url = normalizedDraftLink ?: return@Button
                    link.copy(title = link.title.trim().ifBlank { LinkTarget.displayDestination(url, 48) }, url = url)
                }
            } ?: draft
            onSave(saved)
        }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

private fun moveElement(element: PageElement, dx: Float, dy: Float): PageElement = when (element) {
    is PageElement.Text -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Sticky -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Shape -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Image -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Fill -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Link -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
}

private fun resizeElement(element: PageElement, factor: Float): PageElement = setElementSize(element, (element.width * factor).coerceIn(40f, 1600f), (element.height * factor).coerceIn(40f, 2263f))
private fun rotateElement(element: PageElement, delta: Float): PageElement = setElementRotation(element, ((element.rotationDegrees + delta + 180f) % 360f) - 180f)
private fun setElementSize(element: PageElement, width: Float, height: Float): PageElement = when (element) { is PageElement.Text -> element.copy(width = width, height = height); is PageElement.Sticky -> element.copy(width = width, height = height); is PageElement.Shape -> element.copy(width = width, height = height); is PageElement.Image -> element.copy(width = width, height = height); is PageElement.Fill -> element.copy(width = width, height = height); is PageElement.Link -> element.copy(width = width, height = height) }
private fun setElementRotation(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(rotationDegrees = value); is PageElement.Sticky -> element.copy(rotationDegrees = value); is PageElement.Shape -> element.copy(rotationDegrees = value); is PageElement.Image -> element.copy(rotationDegrees = value); is PageElement.Fill -> element.copy(rotationDegrees = value); is PageElement.Link -> element.copy(rotationDegrees = value) }
private fun setElementOpacity(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(opacity = value); is PageElement.Sticky -> element.copy(opacity = value); is PageElement.Shape -> element.copy(opacity = value); is PageElement.Image -> element.copy(opacity = value); is PageElement.Fill -> element.copy(opacity = value); is PageElement.Link -> element.copy(opacity = value) }

private fun openLink(context: android.content.Context, rawUrl: String, language: UiLanguage) {
    val url = LinkTarget.normalize(rawUrl)
    if (url == null) {
        Toast.makeText(context, language.text("This link is not valid or supported.", "此链接无效或不受支持。"), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    }.onFailure {
        Toast.makeText(context, language.text("No app can open this link.", "没有应用可以打开此链接。"), Toast.LENGTH_SHORT).show()
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
