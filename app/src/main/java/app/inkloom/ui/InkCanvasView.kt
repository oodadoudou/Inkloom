package app.inkloom.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import java.util.LinkedHashMap
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeMaskDab
import app.inkloom.document.StrokeMaskRenderer
import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushDynamics
import app.inkloom.document.BrushStampCount
import app.inkloom.document.BrushStampRotation
import app.inkloom.document.BrushSizeDrag
import app.inkloom.document.BrushTipSequence
import app.inkloom.document.BrushTipFlip
import app.inkloom.document.BrushTipTextureDecoder
import app.inkloom.document.BrushTipTransform
import app.inkloom.document.BrushTaperMode
import app.inkloom.document.CspDualBrushBlendMode
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.GrainBehavior
import app.inkloom.document.LassoGeometry
import app.inkloom.document.LassoSelectionPolicy
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.PaperPattern
import app.inkloom.document.PressureCalibration
import app.inkloom.document.PageElement
import app.inkloom.document.PdfGotoLink
import app.inkloom.document.PdfGotoLinkNavigation
import app.inkloom.document.RenderedColorSampling
import app.inkloom.document.ShapeGeometry
import app.inkloom.document.ShapeKind
import app.inkloom.document.ShapePoint
import app.inkloom.document.SprayGeometry
import app.inkloom.document.StrokeEdit
import app.inkloom.document.StrokeEditHistory
import app.inkloom.document.StrokeEdits
import app.inkloom.document.StrokeEraser
import app.inkloom.document.StrokeClipboardCodec
import app.inkloom.document.StrokeStampSampler
import app.inkloom.document.StrokeStabilization
import app.inkloom.document.StrokeTaper
import app.inkloom.document.StrokeTileIndex
import app.inkloom.document.StrokeTileKey
import app.inkloom.document.StylusInputClassifier
import app.inkloom.document.TwoFingerTapUndoGesture
import app.inkloom.document.TwoPointerRotationGesture
import app.inkloom.document.ThreeFingerTapRedoGesture
import app.inkloom.document.ViewportTransform
import app.inkloom.document.WaterEdge
import app.inkloom.document.WetMixDynamics
import app.inkloom.model.EditorTool
import app.inkloom.settings.CspShortcutResolver
import app.inkloom.settings.KeyInputDiagnostic
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutActivation
import app.inkloom.settings.ShortcutBinding
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.SelectionFilter
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class InkCanvasView(context: Context) : View(context) {
    private val strokes = mutableListOf<InkStroke>()
    private val editHistory = StrokeEditHistory()
    private val currentPoints = mutableListOf<StrokePoint>()
    private var currentStrokeId = java.util.UUID.randomUUID().toString()
    private var backgroundBitmap: Bitmap? = null
    private val selectedStrokeIds = linkedSetOf<String>()
    private var selectionGestureBefore: List<InkStroke>? = null
    private var eraserGestureBefore: List<InkStroke>? = null
    private var lastEraserX: Float? = null
    private var lastEraserY: Float? = null
    private var strokeHistoryKey: String? = null
    private val lassoPoints = mutableListOf<StrokePoint>()
    private var shapeStart: ShapePoint? = null
    private var shapeEnd: ShapePoint? = null
    private val shapeFreeformPoints = mutableListOf<ShapePoint>()
    private var movingSelection = false
    private var selectionMoved = false
    private var lastX = 0f
    private var lastY = 0f
    private var navigationX = 0f
    private var navigationY = 0f
    private var navigationDownX = 0f
    private var navigationDownY = 0f
    private var navigationMoved = false
    private var navigationTapEligible = false
    private val navigationTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val twoFingerTapUndoGesture = TwoFingerTapUndoGesture(navigationTouchSlop)
    private val threeFingerTapRedoGesture = ThreeFingerTapRedoGesture(navigationTouchSlop)
    private val twoPointerRotationGesture = TwoPointerRotationGesture()
    private val colorSampleBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var activeTool: EditorTool? = null
    private var activeInputWasStylus = false
    private var lastStylusSeenAt = Long.MIN_VALUE
    private var ctrlModifierHeld = false
    private var altModifierHeld = false
    private var brushSizeDragActive = false
    private var brushSizeDragStartX = 0f
    private var brushSizeDragStartSize = 0f
    private var brushSizeDragCurrentSize = 0f
    private var brushSizeDragPreviewX = 0f
    private var brushSizeDragPreviewY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val brushSizePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val brushSizePreviewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val tipCache = object : LinkedHashMap<String, Bitmap>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean {
            val remove = size > MAX_TIP_CACHE_ENTRIES
            if (remove) eldest.value.recycle()
            return remove
        }
    }
    private val protectedStrokeIds = mutableSetOf<String>()
    private val protectedLayerIds = mutableSetOf<String>()
    private val strokeTileIndex = StrokeTileIndex()
    private val strokeTiles = mutableMapOf<StrokeTileKey, Bitmap>()
    private val dirtyStrokeTiles = linkedSetOf<StrokeTileKey>()
    internal var onTileRendered: ((StrokeTileKey, Int) -> Unit)? = null

    var tool: EditorTool = EditorTool.PEN
    var forceNavigation: Boolean = false
    var rotateNavigation: Boolean = false
    var inkColor: Int = Color.rgb(17, 17, 17)
    var baseWidth: Float = 5f
    var inkOpacity: Float = 1f
    var brushId: String = "system-pencil"
    var activeLayerId: String = app.inkloom.document.LayerIds.INK
    var layerOpacities: Map<String, Float> = emptyMap()
        set(value) {
            val normalized = value.mapValues { (_, opacity) -> opacity.coerceIn(0f, 1f) }
            if (field != normalized) {
                field = normalized
                resetStrokeTiles()
            }
        }
    var layerBlendModes: Map<String, LayerBlendMode> = emptyMap()
        set(value) {
            if (field != value) {
                field = value
                resetStrokeTiles()
            }
        }
    var pagePaperColor: Int? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var pagePaperPattern: PaperPattern? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var tipAssetPath: String? = null
    var tipAssetPaths: List<String> = emptyList()
    var dualTipAssetPaths: List<String> = emptyList()
    var grainAssetPath: String? = null
    var grainDensity: Float = 0f
    var grainScale: Float = 100f
    var grainRotationDegrees: Float = 0f
    var grainBrightness: Float = 0f
    var grainContrast: Float = 0f
    var grainReversed: Boolean = false
    var grainEmphasized: Boolean = false
    var grainOffsetJitter: Boolean = false
    var grainCompositeMode: Int = CspTextureComposite.MULTIPLY
    var brushSpacing: Float = .12f
    var brushFlow: Float = 1f
    var brushHardness: Float = 1f
    var brushThickness: Float = 1f
    var brushThicknessVertical: Boolean = false
    var brushRotation: Float = 0f
    var followStylusOrientation: Boolean = false
    var followStrokeDirection: Boolean = false
    var randomRotationDegrees: Float = 0f
    var strokeRotationJitterDegrees: Float = 0f
    var stampCount: Int = 1
    var stampCountJitter: Float = 0f
    var brushWatercolor: Boolean = false
    var brushColorMix: Float = 0f
    var brushPaintMix: Float = 0f
    var brushSpray: Boolean = false
    var sprayParticleSize: Float = 100f
    var sprayParticleSizeUnit: Int = 0
    var sprayParticleSizeSyncBrushSize: Boolean = true
    var sprayDensity: Float = 1f
    var sprayBias: Float = 0f
    var spraySpread: Float = 1f
    var sprayParticleRotationDegrees: Float = 0f
    var sprayParticleFollowStylusOrientation: Boolean = false
    var sprayParticleFollowStrokeDirection: Boolean = false
    var sprayParticleRandomRotationDegrees: Float = 0f
    var brushRibbon: Boolean = false
    var brushTipSequenceMode: Int = 0
    var tipFlipHorizontalMode: Int = 0
    var tipFlipVerticalMode: Int = 0
    var blendTipsWithDarken: Boolean = false
    var brushDual: Boolean = false
    var dualSizeScale: Float = 1f
    var dualSpacing: Float = .12f
    var dualFlow: Float = 1f
    var dualHardness: Float = 1f
    var dualThickness: Float = 1f
    var dualThicknessVertical: Boolean = false
    var dualRotationDegrees: Float = 0f
    var dualFollowStylusOrientation: Boolean = false
    var dualFollowStrokeDirection: Boolean = false
    var dualRandomRotationDegrees: Float = 0f
    var dualStrokeRotationJitterDegrees: Float = 0f
    var dualStampCount: Int = 1
    var dualStampCountJitter: Float = 0f
    var dualRibbon: Boolean = false
    var dualSpray: Boolean = false
    var dualSprayParticleSize: Float = 100f
    var dualSprayParticleSizeUnit: Int = 0
    var dualSprayParticleSizeSyncBrushSize: Boolean = true
    var dualSprayDensity: Float = 1f
    var dualSprayBias: Float = 0f
    var dualSpraySpread: Float = 1f
    var dualSprayParticleRotationDegrees: Float = 0f
    var dualSprayParticleFollowStylusOrientation: Boolean = false
    var dualSprayParticleFollowStrokeDirection: Boolean = false
    var dualSprayParticleRandomRotationDegrees: Float = 0f
    var dualTipSequenceMode: Int = 0
    var dualTipFlipHorizontalMode: Int = 0
    var dualTipFlipVerticalMode: Int = 0
    var dualBlendTipsWithDarken: Boolean = false
    var dualCompositeMode: Int = 0
    var dualApplyRgb: Boolean = false
    var taperMode: BrushTaperMode = BrushTaperMode.PERCENTAGE
    var taperStartEnabled: Boolean = false
    var taperStartLength: Float = .2f
    var taperStartLengthUnit: Int = 0
    var taperStartMinimum: Float = .3f
    var taperEndEnabled: Boolean = false
    var taperEndLength: Float = .2f
    var taperEndLengthUnit: Int = 0
    var taperEndMinimum: Float = .3f
    var waterEdgeEnabled: Boolean = false
    var waterEdgeRadius: Float = 3f
    var waterEdgeRadiusUnit: Int = 0
    var waterEdgeOpacity: Float = .2f
    var waterEdgeDarkness: Float = 0f
    var waterEdgeBlur: Float = 0f
    var waterEdgeBlurUnit: Int = 0
    var sizePressureEnabled: Boolean = true
    var sizePressureMinimum: Float = .35f
    var sizePressureCurve: List<BrushCurvePoint> = emptyList()
    var sizeSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var opacityPressureEnabled: Boolean = false
    var opacityPressureMinimum: Float = 0f
    var opacityPressureCurve: List<BrushCurvePoint> = emptyList()
    var opacitySecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var flowPressureEnabled: Boolean = false
    var flowPressureMinimum: Float = 0f
    var flowPressureCurve: List<BrushCurvePoint> = emptyList()
    var flowSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var spacingPressureEnabled: Boolean = false
    var spacingPressureMinimum: Float = 0f
    var spacingPressureCurve: List<BrushCurvePoint> = emptyList()
    var spacingSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var thicknessPressureEnabled: Boolean = false
    var thicknessPressureMinimum: Float = 0f
    var thicknessPressureCurve: List<BrushCurvePoint> = emptyList()
    var thicknessSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var localEraser: Boolean = false
    var nonDestructiveEraser: Boolean = false
    var eraserWidth: Float = 24f
    var eraserHardness: Float = 1f
    var eraserOpacity: Float = 1f
    var eraserPressureSize: Boolean = false
    var eraserTipAssetPath: String? = null
    var fingerDrawingEnabled: Boolean = false
    var twoFingerDoubleTapUndo: Boolean = true
    var threeFingerTapRedo: Boolean = true
    var twoFingerRotationEnabled: Boolean = false
    var pressureSensitivity: Float = 1f
    var pressureMinimumInput: Float = 0f
    var pressureMaximumInput: Float = 1f
    var stabilization: Float = .12f
    var stabilizationBySpeed: Boolean = false
    var stabilizationBySpeedMode: Int = StrokeStabilization.REDUCE_WHEN_FAST
    var primaryButtonAction: StylusButtonAction = StylusButtonAction.ERASER
    var secondaryButtonAction: StylusButtonAction = StylusButtonAction.LASSO
    var shapeKind: ShapeKind = ShapeKind.RECTANGLE
    var selectionShape: SelectionShape = SelectionShape.FREEFORM
    var selectionMode: SelectionMode = SelectionMode.REPLACE
    var selectionFilter: SelectionFilter = SelectionFilter.ALL
    var pageModelWidth: Float = 1600f
    var pageModelHeight: Float = 2263f
    var onStrokeCommitted: (InkStroke) -> Unit = {}
    var onShapeCommitted: (PageElement.Shape) -> Unit = {}
    var onStrokeRemoved: (String) -> Unit = {}
    var onStrokesReplaced: (List<String>, List<InkStroke>, List<Int>) -> Unit = { removed, replacements, _ -> removed.forEach(onStrokeRemoved); replacements.forEach(onStrokeCommitted) }
    var onViewportScale: (Float, Float, Float) -> Unit = { _, _, _ -> }
    var onViewportPan: (Float, Float) -> Unit = { _, _ -> }
    var onViewportRotate: (Float) -> Unit = {}
    var pdfGotoLinks: List<PdfGotoLink> = emptyList()
    var onPdfGotoLink: (PdfGotoLink) -> Unit = {}
    var onUndoGesture: () -> Unit = {}
    var onRedoGesture: () -> Unit = {}
    var onColorSampled: (Int) -> Unit = {}
    var onBrushSizeAdjusted: (Float) -> Unit = {}
    var onInputDiagnostic: (String, Float, Float, Int) -> Unit = { _, _, _, _ -> }
    var onKeyInputDiagnostic: (KeyInputDiagnostic) -> Unit = {}
    var onShortcutAction: (ShortcutActivation) -> Unit = {}
    var shortcutBindings: List<ShortcutBinding> = CspShortcutResolver.defaultBindings

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onViewportScale(detector.scaleFactor, detector.focusX - width / 2f, detector.focusY - height / 2f)
            return true
        }
    })
    private val backgroundDestination = android.graphics.Rect()
    private val lassoPath = Path()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            ctrlModifierHeld = false
            altModifierHeld = false
            twoFingerTapUndoGesture.cancel()
            threeFingerTapRedoGesture.cancel()
            twoPointerRotationGesture.reset()
            if (brushSizeDragActive) finishBrushSizeDrag(cancelled = true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (updateModifierState(keyCode, pressed = true)) {
            onKeyInputDiagnostic(event.toDiagnostic(ShortcutPhase.PRESS, null))
            return true
        }
        val binding = CspShortcutResolver.resolveBinding(
            ShortcutChord(
                keyCode = keyCode,
                ctrl = event.isCtrlPressed || ctrlModifierHeld,
                alt = event.isAltPressed || altModifierHeld,
                shift = event.isShiftPressed,
                scanCode = event.scanCode,
            ),
            shortcutBindings,
            event.device?.descriptor,
        )
        onKeyInputDiagnostic(event.toDiagnostic(ShortcutPhase.PRESS, binding?.action))
        binding ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0 || CspShortcutResolver.isRepeatable(binding.action)) onShortcutAction(ShortcutActivation(binding.action, ShortcutPhase.PRESS, binding.trigger))
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (updateModifierState(keyCode, pressed = false)) {
            onKeyInputDiagnostic(event.toDiagnostic(ShortcutPhase.RELEASE, null))
            return true
        }
        val binding = CspShortcutResolver.resolveBinding(
            ShortcutChord(
                keyCode = keyCode,
                ctrl = event.isCtrlPressed || ctrlModifierHeld,
                alt = event.isAltPressed || altModifierHeld,
                shift = event.isShiftPressed,
                scanCode = event.scanCode,
            ),
            shortcutBindings,
            event.device?.descriptor,
        )
        onKeyInputDiagnostic(event.toDiagnostic(ShortcutPhase.RELEASE, binding?.action))
        binding ?: return super.onKeyUp(keyCode, event)
        if (binding.trigger == ShortcutTrigger.HOLD) onShortcutAction(ShortcutActivation(binding.action, ShortcutPhase.RELEASE, binding.trigger))
        return true
    }

    private fun KeyEvent.toDiagnostic(phase: ShortcutPhase, matchedAction: ShortcutAction?): KeyInputDiagnostic =
        KeyInputDiagnostic(
            phase = phase,
            keyCode = keyCode,
            scanCode = scanCode,
            metaState = metaState,
            deviceId = deviceId,
            deviceName = device?.name,
            deviceDescriptor = device?.descriptor,
            source = source,
            matchedAction = matchedAction,
        )

    private fun updateModifierState(keyCode: Int, pressed: Boolean): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> { ctrlModifierHeld = pressed; true }
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> { altModifierHeld = pressed; true }
        else -> false
    }

    fun setDocumentStrokes(value: List<InkStroke>, historyKey: String) {
        if (strokeHistoryKey != historyKey) {
            strokeHistoryKey = historyKey
            editHistory.clear()
            selectionGestureBefore = null
            eraserGestureBefore = null
        }
        if (strokes != value) {
            editHistory.clear()
            strokes.clear()
            strokes.addAll(value)
            selectedStrokeIds.retainAll(value.mapTo(mutableSetOf()) { it.id })
            invalidateStrokeCache()
        }
    }

    fun setProtectedStrokeIds(value: Set<String>) { protectedStrokeIds.clear(); protectedStrokeIds.addAll(value); selectedStrokeIds.removeAll(value) }

    fun setProtectedLayerIds(value: Set<String>) { protectedLayerIds.clear(); protectedLayerIds.addAll(value); selectedStrokeIds.removeAll(strokes.filter { it.layerId in value }.mapTo(mutableSetOf(), InkStroke::id)) }

    fun setPageBackground(bitmap: Bitmap?) { backgroundBitmap = bitmap; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let { bitmap -> backgroundDestination.set(0, 0, width, height); canvas.drawBitmap(bitmap, null, backgroundDestination, null) }
            ?: pagePaperColor?.let { color -> canvas.drawColor(color); pagePaperPattern?.let { drawPaperPattern(canvas, it) } }
        if (!drawStrokeTiles(canvas)) drawLayeredStrokes(canvas, strokes)
        var selectionLeft = Float.POSITIVE_INFINITY
        var selectionTop = Float.POSITIVE_INFINITY
        var selectionRight = Float.NEGATIVE_INFINITY
        var selectionBottom = Float.NEGATIVE_INFINITY
        strokes.forEach { stroke -> if (stroke.id in selectedStrokeIds) stroke.points.forEach { point ->
            val x = point.x * width; val y = point.y * height
            selectionLeft = minOf(selectionLeft, x); selectionTop = minOf(selectionTop, y)
            selectionRight = maxOf(selectionRight, x); selectionBottom = maxOf(selectionBottom, y)
        } }
        if (selectionLeft.isFinite()) {
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(17, 17, 17); paint.alpha = 255; paint.strokeWidth = 2f
            canvas.drawRect(selectionLeft - 14, selectionTop - 14, selectionRight + 14, selectionBottom + 14, paint)
        }
        val selectionPreview = selectionPolygon()
        if (selectionPreview.size >= 2) {
            lassoPath.reset()
            lassoPath.moveTo(selectionPreview.first().x * width, selectionPreview.first().y * height)
            for (index in 1 until selectionPreview.size) lassoPath.lineTo(selectionPreview[index].x * width, selectionPreview[index].y * height)
            if (selectionShape == SelectionShape.RECTANGLE) lassoPath.close()
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(98, 98, 98); paint.alpha = 220; paint.strokeWidth = 2f
            canvas.drawPath(lassoPath, paint)
        }
        drawShapePreview(canvas)
        if (currentPoints.isNotEmpty()) drawLayeredStrokes(canvas, listOf(makeStroke(currentPoints).copy(grainCompositeMode = grainCompositeMode)))
        drawBrushSizePreview(canvas)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) resetStrokeTiles()
    }

    override fun onDetachedFromWindow() {
        recycleStrokeTiles()
        tipCache.values.forEach(Bitmap::recycle); tipCache.clear()
        super.onDetachedFromWindow()
    }

    private fun drawStrokeTiles(canvas: Canvas): Boolean {
        if (layerBlendModes.values.any { it != LayerBlendMode.NORMAL }) return false
        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_CACHE_PIXELS) return false
        if (strokeTileIndex.widthPx != width || strokeTileIndex.heightPx != height) resetStrokeTiles()
        val keys = strokeTileIndex.allTiles()
        for (key in keys) {
            val rect = strokeTileIndex.rect(key)
            var tile = strokeTiles[key]
            if (tile == null || tile.width != rect.width || tile.height != rect.height) {
                tile?.recycle()
                tile = runCatching { Bitmap.createBitmap(rect.width, rect.height, Bitmap.Config.ARGB_8888) }.getOrNull()
                if (tile == null) {
                    recycleStrokeTiles()
                    return false
                }
                strokeTiles[key] = tile
                dirtyStrokeTiles += key
            }
        }
        for (key in keys) {
            val rect = strokeTileIndex.rect(key)
            val tile = strokeTiles.getValue(key)
            if (key in dirtyStrokeTiles) {
                tile.eraseColor(Color.TRANSPARENT)
                val tileCanvas = Canvas(tile)
                tileCanvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
                val candidateIds = strokeTileIndex.candidates(key)
                var renderedStrokeCount = 0
                val candidates = strokes.filter { it.id in candidateIds }
                drawLayeredStrokes(tileCanvas, candidates)
                renderedStrokeCount = candidates.size
                dirtyStrokeTiles.remove(key)
                onTileRendered?.invoke(key, renderedStrokeCount)
            }
            canvas.drawBitmap(tile, rect.left.toFloat(), rect.top.toFloat(), null)
        }
        return true
    }

    private fun resetStrokeTiles() {
        recycleStrokeTiles()
        dirtyStrokeTiles += strokeTileIndex.rebuild(strokes, width, height)
        invalidate()
    }

    private fun recycleStrokeTiles() {
        strokeTiles.values.forEach(Bitmap::recycle)
        strokeTiles.clear()
        dirtyStrokeTiles.clear()
    }

    private fun invalidateStrokeCache() = resetStrokeTiles()

    private fun invalidateStrokeCache(before: Collection<InkStroke>, after: Collection<InkStroke>) {
        if (strokeTileIndex.widthPx != width || strokeTileIndex.heightPx != height) {
            resetStrokeTiles()
        } else {
            dirtyStrokeTiles += strokeTileIndex.update(before, after)
            invalidate()
        }
    }

    private fun drawLayeredStrokes(canvas: Canvas, value: List<InkStroke>) {
        value.groupBy(InkStroke::layerId).forEach { (layerId, layerStrokes) ->
            val opacity = (layerOpacities[layerId] ?: 1f).coerceIn(0f, 1f)
            if (opacity <= 0f) return@forEach
            val blendMode = layerBlendModes[layerId] ?: LayerBlendMode.NORMAL
            val needsLayer = opacity < .999f || blendMode != LayerBlendMode.NORMAL
            val checkpoint = if (needsLayer) {
                canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (opacity * 255f).toInt().coerceIn(0, 255)
                    this.blendMode = when (blendMode) {
                        LayerBlendMode.NORMAL -> android.graphics.BlendMode.SRC_OVER
                        LayerBlendMode.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
                        LayerBlendMode.SCREEN -> android.graphics.BlendMode.SCREEN
                        LayerBlendMode.ADD -> android.graphics.BlendMode.PLUS
                    }
                })
            } else null
            layerStrokes.forEach { drawStroke(canvas, it) }
            checkpoint?.let(canvas::restoreToCount)
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        if (stroke.masks.isEmpty()) {
            drawStrokeContent(canvas, stroke)
            return
        }
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        drawStrokeContent(canvas, stroke)
        StrokeMaskRenderer.draw(canvas, paint, stroke.masks, width, height)
        canvas.restoreToCount(checkpoint)
    }

    private fun drawPaperPattern(canvas: Canvas, pattern: PaperPattern) {
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(227, 227, 227); strokeWidth = 1.5f; style = Paint.Style.STROKE }
        when (pattern) {
            PaperPattern.PLAIN -> Unit
            PaperPattern.RULED -> { var y = 36f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, guide); y += 36f } }
            PaperPattern.GRID -> { var x = 0f; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), guide); x += 32f }; var y = 0f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, guide); y += 32f } }
            PaperPattern.DOT -> { guide.style = Paint.Style.FILL; guide.color = Color.rgb(217, 217, 217); var x = 8f; while (x < width) { var y = 8f; while (y < height) { canvas.drawCircle(x, y, 1.4f, guide); y += 28f }; x += 28f } }
            PaperPattern.CORNELL -> { val cueX = width * .28f; val summaryY = height * .78f; guide.color = Color.rgb(212, 212, 212); canvas.drawLine(cueX, 0f, cueX, summaryY, guide); canvas.drawLine(0f, summaryY, width.toFloat(), summaryY, guide); guide.color = Color.rgb(231, 231, 231); var y = 36f; while (y < summaryY) { canvas.drawLine(cueX, y, width.toFloat(), y, guide); y += 36f }; y = summaryY + 36f; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, guide); y += 36f } }
        }
    }

    private fun drawStrokeContent(canvas: Canvas, stroke: InkStroke) {
        if (stroke.points.isEmpty()) return
        val tipPaths = stroke.tipAssetPaths.ifEmpty { listOfNotNull(stroke.tipAssetPath) }
        val grainOffset = GrainBehavior.offset(stroke.renderSeed, stroke.grainOffsetJitter)
        val tips = tipPaths.mapNotNull { loadTip(it, stroke.hardness, stroke.grainAssetPath, stroke.grainDensity, stroke.grainScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, grainOffset.x, grainOffset.y, stroke.grainCompositeMode) }
        if (tips.isNotEmpty()) {
            val dualTips = if (stroke.dualBrush) stroke.dualTipAssetPaths.mapNotNull { loadTip(it, stroke.dualHardness, null, 0f) }.ifEmpty { tips } else emptyList()
            drawStampedStroke(canvas, stroke, tips, dualTips)
            return
        }
        paint.color = stroke.colorArgb.toInt()
        paint.colorFilter = null
        val scale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val taper = StrokeTaper.factors(stroke.points, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        if (stroke.points.size == 1) {
            val point = stroke.points.first(); val strokeWidth = stroke.width * scale * taper.firstOrNull().orDefault() * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, point.pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE); val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, point.pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, point.pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * WetMixDynamics.alphaMultiplier(stroke.watercolor, stroke.colorMix, stroke.paintMix) * 255).toInt().coerceIn(1, 255); drawLinePoint(canvas, stroke, point.x * width, point.y * height, strokeWidth, alpha)
        } else stroke.points.zipWithNext().forEachIndexed { index, (start, end) ->
            val point = midpoint(start, end); val pressure = point.pressure.coerceIn(0f, 1f)
            val taperFactor = (taper[index] + taper[index + 1]) / 2f
            val strokeWidth = stroke.width * scale * taperFactor * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)
            val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * WetMixDynamics.alphaMultiplier(stroke.watercolor, stroke.colorMix, stroke.paintMix) * 255).toInt().coerceIn(1, 255)
            drawLineSegment(canvas, stroke, start.x * width, start.y * height, end.x * width, end.y * height, strokeWidth, alpha)
        }
    }

    private fun drawStampedStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>, dualTips: List<Bitmap>) {
        // A CSP Dual Brush combines its two stamp streams first, then deposits
        // that single result. Isolating the stroke prevents the secondary mode
        // from accidentally blending with older strokes or the page itself.
        val isolateStroke = stroke.blendTipsWithDarken || stroke.dualBrush && dualTips.isNotEmpty()
        val compositeLayer = if (isolateStroke) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) else -1
        paint.blendMode = if (stroke.blendTipsWithDarken) BlendMode.DARKEN else null
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val scatterRandom = java.util.Random(stroke.renderSeed.toLong())
        val rotationRandom = java.util.Random(stroke.renderSeed.toLong() xor 0x49A2B31DL)
        val sampledPoints = StrokeStampSampler.sample(stroke.points, width.toFloat(), height.toFloat()) { point ->
            val pressure = point.pressure.coerceIn(0f, 1f)
            val diameter = (stroke.width * referenceScale * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(1f)
            val spacingDynamic = pressureDynamic(stroke.spacingPressureEnabled, stroke.spacingPressureMinimum, stroke.spacingPressureCurve, pressure) * secondaryDynamic(stroke.spacingSecondaryDynamics, point, stroke.renderSeed, SALT_SPACING)
            diameter * (if (stroke.ribbon) stroke.spacing.coerceIn(.01f, .06f) else stroke.spacing.coerceIn(.01f, 2f)) * spacingDynamic.coerceIn(.01f, 1f) / if (stroke.spray) SprayGeometry.particlesPerPlot(stroke.sprayDensity) else 1
        }
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        sampledPoints.forEachIndexed { index, point ->
            val stampsAtPoint = BrushStampCount.resolve(stroke.stampCount, stroke.stampCountJitter, stroke.renderSeed, index)
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.tipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed, stroke.ribbon) ?: return@stamp
            val tip = tips[tipIndex]
            val x = point.x * width; val y = point.y * height
            val pressure = point.pressure.coerceIn(0f, 1f)
            val brushDiameter = (stroke.width * referenceScale * taper[index] * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(.25f)
            val diameter = if (stroke.spray) SprayGeometry.particleDiameter(brushDiameter, stroke.sprayParticleSize, stroke.sprayParticleSizeUnit, stroke.sprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val offset = if (stroke.spray) SprayGeometry.offset(brushDiameter / 2f * stroke.spraySpread.coerceIn(0f, 4f), scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.sprayBias) else app.inkloom.document.SprayOffset(0f, 0f)
            val drawX = x + offset.x
            val drawY = y + offset.y
            val dynamicThickness = pressureDynamic(stroke.thicknessPressureEnabled, stroke.thicknessPressureMinimum, stroke.thicknessPressureCurve, pressure) * secondaryDynamic(stroke.thicknessSecondaryDynamics, point, stroke.renderSeed, SALT_THICKNESS)
            val thicknessScale = stroke.thickness.coerceIn(.05f, 2f) * dynamicThickness
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, thicknessScale, stroke.thicknessVertical)
            val flip = BrushTipFlip.signs(stroke.tipFlipHorizontalMode, stroke.tipFlipVerticalMode, stroke.tipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed)
            val scaleX = tipScale.x * flip.x
            val scaleY = tipScale.y * flip.y
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = false)
            val angle = BrushStampRotation.degrees(
                stroke = stroke,
                dual = false,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f,
                randomUnit = rotationRandom.nextFloat(),
            )
            val matrix = Matrix().apply {
                postTranslate(-tip.width / 2f, -tip.height / 2f)
                postScale(scaleX, scaleY)
                postRotate(angle)
                postTranslate(drawX, drawY)
            }
            paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW)
            val mainAlpha = (stroke.opacity * opacityDynamic * stroke.flow * flowDynamic * WetMixDynamics.alphaMultiplier(stroke.watercolor, stroke.colorMix, stroke.paintMix)).coerceIn(.01f, 1f).times(255).toInt()
            if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
                val edgeRadius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat())
                val edgeTipScale = BrushTipTransform.scale(tip.width, tip.height, diameter + edgeRadius * 2f, thicknessScale, stroke.thicknessVertical)
                val edgeMatrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(edgeTipScale.x * flip.x, edgeTipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
                paint.colorFilter = PorterDuffColorFilter(WaterEdge.darkenArgb(stroke.colorArgb.toInt(), stroke.waterEdgeDarkness), PorterDuff.Mode.SRC_IN)
                paint.alpha = (mainAlpha * stroke.waterEdgeOpacity.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
                val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat())
                paint.maskFilter = blur.takeIf { it > .01f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
                canvas.drawBitmap(tip, edgeMatrix, paint)
                paint.maskFilter = null
            }
            paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
            paint.alpha = mainAlpha
            canvas.drawBitmap(tip, matrix, paint)
            }
        }
        paint.blendMode = null
        if (stroke.dualBrush && dualTips.isNotEmpty()) drawDualStampedStroke(canvas, stroke, dualTips)
        if (compositeLayer >= 0) canvas.restoreToCount(compositeLayer)
        paint.colorFilter = null
    }

    /** Draws the secondary stream into the isolated per-stroke composition layer. */
    private fun drawDualStampedStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>) {
        val compositePaint = Paint().apply { blendMode = CspDualBrushBlendMode.resolve(stroke.dualCompositeMode) }
        val dualLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), compositePaint)
        paint.blendMode = if (stroke.dualBlendTipsWithDarken) BlendMode.DARKEN else null
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val scatterRandom = java.util.Random(stroke.renderSeed.toLong() xor DUAL_SCATTER_SEED)
        val rotationRandom = java.util.Random(stroke.renderSeed.toLong() xor DUAL_ROTATION_SEED)
        val sampledPoints = StrokeStampSampler.sample(stroke.points, width.toFloat(), height.toFloat()) { point ->
            val pressure = point.pressure.coerceIn(0f, 1f)
            val diameter = (stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f) * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(1f)
            val spacingDynamic = pressureDynamic(stroke.spacingPressureEnabled, stroke.spacingPressureMinimum, stroke.spacingPressureCurve, pressure) * secondaryDynamic(stroke.spacingSecondaryDynamics, point, stroke.renderSeed, SALT_SPACING)
            diameter * (if (stroke.dualRibbon) stroke.dualSpacing.coerceIn(.01f, .06f) else stroke.dualSpacing.coerceIn(.01f, 2f)) * spacingDynamic.coerceIn(.01f, 1f) / if (stroke.dualSpray) SprayGeometry.particlesPerPlot(stroke.dualSprayDensity) else 1
        }
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        sampledPoints.forEachIndexed { index, point ->
            val stampsAtPoint = BrushStampCount.resolve(stroke.dualStampCount, stroke.dualStampCountJitter, stroke.renderSeed, index, dual = true)
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.dualTipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed xor DUAL_SEQUENCE_SEED, stroke.dualRibbon) ?: return@stamp
            val tip = tips[tipIndex]
            val pressure = point.pressure.coerceIn(0f, 1f)
            val brushDiameter = (stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f) * taper[index] * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(.25f)
            val diameter = if (stroke.dualSpray) SprayGeometry.particleDiameter(brushDiameter, stroke.dualSprayParticleSize, stroke.dualSprayParticleSizeUnit, stroke.dualSprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val offset = if (stroke.dualSpray) SprayGeometry.offset(brushDiameter / 2f * stroke.dualSpraySpread.coerceIn(0f, 4f), scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.dualSprayBias) else app.inkloom.document.SprayOffset(0f, 0f)
            val drawX = point.x * width + offset.x
            val drawY = point.y * height + offset.y
            val dynamicThickness = pressureDynamic(stroke.thicknessPressureEnabled, stroke.thicknessPressureMinimum, stroke.thicknessPressureCurve, pressure) * secondaryDynamic(stroke.thicknessSecondaryDynamics, point, stroke.renderSeed, SALT_THICKNESS)
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, stroke.dualThickness.coerceIn(.05f, 2f) * dynamicThickness, stroke.dualThicknessVertical)
            val flip = BrushTipFlip.signs(stroke.dualTipFlipHorizontalMode, stroke.dualTipFlipVerticalMode, stroke.dualTipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed, dual = true)
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = true)
            val angle = BrushStampRotation.degrees(
                stroke = stroke,
                dual = true,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f,
                randomUnit = rotationRandom.nextFloat(),
            )
            val matrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(tipScale.x * flip.x, tipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW)
            paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
            paint.alpha = (stroke.opacity * opacityDynamic * stroke.dualFlow.coerceIn(0f, 1f) * flowDynamic * WetMixDynamics.alphaMultiplier(stroke.watercolor, stroke.colorMix, stroke.paintMix)).coerceIn(.01f, 1f).times(255).toInt()
            canvas.drawBitmap(tip, matrix, paint)
            }
        }
        paint.blendMode = null
        canvas.restoreToCount(dualLayer)
    }

    private fun drawLinePoint(canvas: Canvas, stroke: InkStroke, x: Float, y: Float, strokeWidth: Float, alpha: Int) {
        if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
            configureWaterEdge(stroke, strokeWidth, alpha)
            canvas.drawPoint(x, y, paint)
        }
        paint.maskFilter = null; paint.color = stroke.colorArgb.toInt(); paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawPoint(x, y, paint)
    }

    private fun drawLineSegment(canvas: Canvas, stroke: InkStroke, startX: Float, startY: Float, endX: Float, endY: Float, strokeWidth: Float, alpha: Int) {
        if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
            configureWaterEdge(stroke, strokeWidth, alpha)
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
        paint.maskFilter = null; paint.color = stroke.colorArgb.toInt(); paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawLine(startX, startY, endX, endY, paint)
    }

    private fun configureWaterEdge(stroke: InkStroke, strokeWidth: Float, alpha: Int) {
        val radius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat())
        paint.color = WaterEdge.darkenArgb(stroke.colorArgb.toInt(), stroke.waterEdgeDarkness)
        paint.strokeWidth = strokeWidth + radius * 2f
        paint.alpha = (alpha * stroke.waterEdgeOpacity.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
        val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat())
        paint.maskFilter = blur.takeIf { it > .01f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
    }

    private fun loadTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false, grainOffsetX: Float = 0f, grainOffsetY: Float = 0f, grainCompositeMode: Int = CspTextureComposite.MULTIPLY): Bitmap? {
        val key = "$path:${(hardness.coerceIn(0f, 1f) * 20).toInt()}:$grainPath:${(grainDensity.coerceIn(0f, 1f) * 20).toInt()}:${grainScale.toInt()}:${grainRotation.toInt()}:${grainBrightness.toInt()}:${grainContrast.toInt()}:$grainReversed:$grainEmphasized:${grainOffsetX.toBits()}:${grainOffsetY.toBits()}:$grainCompositeMode"
        return tipCache[key] ?: BrushTipTextureDecoder.decode(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode)?.also { tipCache[key] = it }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.EYEDROPPER, EditorTool.SELECT, EditorTool.SHAPE)) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !hasFocus()) requestFocus()
        val pointerIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val eventToolType = event.getToolType(pointerIndex)
        val isStylus = eventToolType == MotionEvent.TOOL_TYPE_STYLUS || eventToolType == MotionEvent.TOOL_TYPE_ERASER
        if (isStylus) lastStylusSeenAt = event.eventTime
        onInputDiagnostic(when (eventToolType) { MotionEvent.TOOL_TYPE_STYLUS -> "Stylus"; MotionEvent.TOOL_TYPE_ERASER -> "Eraser"; MotionEvent.TOOL_TYPE_FINGER -> "Finger"; else -> "Other" }, event.pressure, event.getAxisValue(MotionEvent.AXIS_TILT), event.buttonState)
        if (brushSizeDragActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) updateBrushSizeDrag(event.x, event.y) else finishBrushSizeDrag(cancelled = true)
                MotionEvent.ACTION_UP -> finishBrushSizeDrag(cancelled = false)
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> finishBrushSizeDrag(cancelled = true)
            }
            return true
        }
        val elapsedSinceStylus = if (lastStylusSeenAt == Long.MIN_VALUE) Long.MAX_VALUE else event.eventTime - lastStylusSeenAt
        val isFingerNavigation = forceNavigation || StylusInputClassifier.shouldNavigateOrRejectFinger(isStylus, fingerDrawingEnabled, event.pointerCount, event.getTouchMajor(pointerIndex), elapsedSinceStylus)
        if (isFingerNavigation) {
            if (activeTool != null) {
                if (activeInputWasStylus && !forceNavigation) return true
                cancelActiveGestureForNavigation()
            }
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    twoPointerRotationGesture.reset()
                    threeFingerTapRedoGesture.cancel()
                    navigationX = event.x; navigationY = event.y
                    navigationDownX = event.x; navigationDownY = event.y
                    navigationMoved = false
                    navigationTapEligible = eventToolType == MotionEvent.TOOL_TYPE_FINGER &&
                        event.getTouchMajor(pointerIndex) < StylusInputClassifier.PALM_TOUCH_MAJOR_PX &&
                        elapsedSinceStylus > StylusInputClassifier.RECENT_STYLUS_WINDOW_MS
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    navigationMoved = true
                    navigationTapEligible = false
                    if (event.pointerCount == 3 && threeFingerTapRedo) {
                        twoFingerTapUndoGesture.cancel()
                        val focus = event.centroid()
                        threeFingerTapRedoGesture.start(event.eventTime, focus.first, focus.second)
                    } else if (event.pointerCount == 2 && twoFingerDoubleTapUndo) {
                        val focus = event.centroid()
                        twoFingerTapUndoGesture.start(event.eventTime, focus.first, focus.second)
                    } else {
                        twoFingerTapUndoGesture.cancel()
                        threeFingerTapRedoGesture.cancel()
                    }
                    val focus = event.centroid()
                    navigationX = focus.first
                    navigationY = focus.second
                    if (twoFingerRotationEnabled && event.pointerCount == 2) {
                        twoPointerRotationGesture.begin(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    } else twoPointerRotationGesture.reset()
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount == 3 && threeFingerTapRedo && threeFingerTapRedoGesture.finish(event.eventTime)) onRedoGesture()
                    else if (event.pointerCount == 2 && twoFingerDoubleTapUndo && twoFingerTapUndoGesture.finish(event.eventTime)) onUndoGesture()
                    ViewportTransform.remainingPointerIndex(event.pointerCount, event.actionIndex)?.let { remaining ->
                        navigationX = event.getX(remaining)
                        navigationY = event.getY(remaining)
                        navigationDownX = navigationX
                        navigationDownY = navigationY
                    }
                    navigationMoved = true
                    navigationTapEligible = false
                    twoPointerRotationGesture.reset()
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                    val focus = event.centroid()
                    val rotationDelta = if (twoFingerRotationEnabled && event.pointerCount == 2) {
                        twoPointerRotationGesture.update(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    } else {
                        twoPointerRotationGesture.reset()
                        null
                    }
                    rotationDelta?.let(onViewportRotate)
                    if (event.pointerCount == 3) threeFingerTapRedoGesture.move(focus.first, focus.second, scaleDetector.isInProgress)
                    else twoFingerTapUndoGesture.move(focus.first, focus.second, scaleDetector.isInProgress || twoPointerRotationGesture.isRotating)
                    onViewportPan(focus.first - navigationX, focus.second - navigationY)
                    navigationX = focus.first; navigationY = focus.second
                    navigationMoved = true
                } else if (!scaleDetector.isInProgress && rotateNavigation) {
                    onViewportRotate((event.x - navigationX) * VIEW_ROTATION_DEGREES_PER_PIXEL)
                    navigationX = event.x; navigationY = event.y
                    navigationMoved = true
                } else if (!scaleDetector.isInProgress) {
                    if (hypot(event.x - navigationDownX, event.y - navigationDownY) > navigationTouchSlop) navigationMoved = true
                    onViewportPan(event.x - navigationX, event.y - navigationY)
                    navigationX = event.x; navigationY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    twoPointerRotationGesture.reset()
                    threeFingerTapRedoGesture.cancel()
                    val movedFromDown = navigationMoved || hypot(event.x - navigationDownX, event.y - navigationDownY) > navigationTouchSlop
                    if (navigationTapEligible && !movedFromDown && !scaleDetector.isInProgress) {
                        PdfGotoLinkNavigation.hit(
                            pdfGotoLinks,
                            (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f),
                        )?.let(onPdfGotoLink)
                        performClick()
                    }
                    navigationTapEligible = false
                }
                MotionEvent.ACTION_CANCEL -> { navigationTapEligible = false; twoFingerTapUndoGesture.cancel(); threeFingerTapRedoGesture.cancel(); twoPointerRotationGesture.reset() }
            }
            return true
        }
        val normalizedX = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val normalizedY = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activeTool = resolveTool(event, eventToolType)
                activeInputWasStylus = isStylus
                val motionCtrlPressed = event.metaState and KeyEvent.META_CTRL_ON != 0
                val motionAltPressed = event.metaState and KeyEvent.META_ALT_ON != 0
                if ((motionCtrlPressed || ctrlModifierHeld) && (motionAltPressed || altModifierHeld) && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)) {
                    beginBrushSizeDrag(event.x, event.y, if (activeTool == EditorTool.ERASER) eraserWidth else baseWidth)
                    return true
                }
                if (activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && activeLayerId in protectedLayerIds) {
                    activeTool = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                if (activeTool == EditorTool.ERASER) {
                    beginEraserGesture()
                    eraseAlong(normalizedX, normalizedY, event.pressure)
                    return true
                }
                if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(event.x, event.y))
                    return true
                }
                if (activeTool == EditorTool.SELECT) {
                    val hit = strokes.getOrNull(findStroke(normalizedX, normalizedY, .035f))
                    lassoPoints.clear(); selectionGestureBefore = null; selectionMoved = false
                    if (hit != null) {
                        if (hit.id !in selectedStrokeIds) { selectedStrokeIds.clear(); selectedStrokeIds += hit.id }
                        selectionGestureBefore = strokes.toList()
                        movingSelection = true
                    } else {
                        if (selectionMode == SelectionMode.REPLACE) selectedStrokeIds.clear()
                        movingSelection = false
                        appendSelectionPoint(normalizedX, normalizedY)
                    }
                    lastX = normalizedX; lastY = normalizedY
                    invalidate(); return true
                }
                if (activeTool == EditorTool.SHAPE) {
                    shapeStart = ShapePoint(normalizedX, normalizedY)
                    shapeEnd = shapeStart
                    shapeFreeformPoints.clear()
                    if (shapeKind == ShapeKind.FREEFORM) appendShapePoint(normalizedX, normalizedY)
                    invalidate(); return true
                }
                currentPoints.clear(); currentStrokeId = java.util.UUID.randomUUID().toString(); addPoint(normalizedX, normalizedY, event.pressure, event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(event.x, event.y))
                }
                else if (activeTool == EditorTool.ERASER) {
                    for (i in 0 until event.historySize) eraseAlong(event.getHistoricalX(i) / width.coerceAtLeast(1), event.getHistoricalY(i) / height.coerceAtLeast(1), event.getHistoricalPressure(i))
                    eraseAlong(normalizedX, normalizedY, event.pressure)
                }
                else if (activeTool == EditorTool.SELECT) {
                    if (movingSelection) {
                        val dx = normalizedX - lastX; val dy = normalizedY - lastY
                        if (hypot(dx.toDouble(), dy.toDouble()) > .0001) selectionMoved = true
                        val movedBefore = strokes.filter { it.id in selectedStrokeIds }
                        selectedStrokeIds.forEach { id ->
                            val index = strokes.indexOfFirst { it.id == id }
                            if (index >= 0) strokes[index] = strokes[index].copy(
                                points = strokes[index].points.map { it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) },
                                masks = strokes[index].masks.map { it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) },
                            )
                        }
                        lastX = normalizedX; lastY = normalizedY
                        invalidateStrokeCache(movedBefore, strokes.filter { it.id in selectedStrokeIds })
                    } else {
                        if (selectionShape == SelectionShape.FREEFORM) {
                            for (i in 0 until event.historySize) appendSelectionPoint(event.getHistoricalX(i) / width.coerceAtLeast(1), event.getHistoricalY(i) / height.coerceAtLeast(1))
                        }
                        appendSelectionPoint(normalizedX, normalizedY)
                    }
                }
                else if (activeTool == EditorTool.SHAPE) {
                    if (shapeKind == ShapeKind.FREEFORM) {
                        for (i in 0 until event.historySize) appendShapePoint(event.getHistoricalX(i) / width.coerceAtLeast(1), event.getHistoricalY(i) / height.coerceAtLeast(1))
                        appendShapePoint(normalizedX, normalizedY)
                    } else shapeEnd = ShapeGeometry.snapEnd(shapeStart ?: ShapePoint(normalizedX, normalizedY), ShapePoint(normalizedX, normalizedY), pageModelWidth, pageModelHeight)
                }
                else {
                    for (i in 0 until event.historySize) addPoint(event.getHistoricalX(i) / width, event.getHistoricalY(i) / height, event.getHistoricalPressure(i), event.getHistoricalEventTime(i), event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i), event.getHistoricalOrientation(i))
                    addPoint(normalizedX, normalizedY, event.pressure, event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    currentPoints.clear()
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear()
                    if (activeTool == EditorTool.ERASER) cancelEraserGesture()
                    selectionGestureBefore?.let { restoreStrokeSnapshot(it) }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null
                } else if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(event.x, event.y))
                } else if (activeTool == EditorTool.ERASER) {
                    eraseAlong(normalizedX, normalizedY, event.pressure)
                    finishEraserGesture()
                } else if (activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && currentPoints.isNotEmpty()) {
                    val before = strokes.toList()
                    val stroke = makeStroke(currentPoints).copy(grainCompositeMode = grainCompositeMode)
                    strokes += stroke
                    invalidateStrokeCache(emptyList(), listOf(stroke))
                    commitStrokeEdit(before, strokes)
                    currentPoints.clear()
                } else if (activeTool == EditorTool.SELECT) {
                    if (movingSelection && selectionMoved) {
                        selectionGestureBefore?.let { commitStrokeEdit(it, strokes) }
                    } else if (!movingSelection) {
                        appendSelectionPoint(normalizedX, normalizedY)
                        selectWithLasso(selectionPolygon())
                    }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null
                } else if (activeTool == EditorTool.SHAPE) {
                    val shape = if (shapeKind == ShapeKind.FREEFORM) {
                        appendShapePoint(normalizedX, normalizedY)
                        ShapeGeometry.createFreeform(shapeFreeformPoints, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), baseWidth)
                    } else {
                        val start = shapeStart
                        val end = shapeEnd
                        if (start != null && end != null) ShapeGeometry.create(shapeKind, start, end, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), baseWidth) else null
                    }
                    shape?.let(onShapeCommitted)
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                activeTool = null
                activeInputWasStylus = false
            }
        }
        invalidate()
        return true
    }

    private fun sampleRenderedColor(x: Float, y: Float): Int {
        colorSampleBitmap.eraseColor(Color.TRANSPARENT)
        val sampleCanvas = Canvas(colorSampleBitmap)
        sampleCanvas.translate(-x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat()), -y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat()))
        draw(sampleCanvas)
        return RenderedColorSampling.opaqueOverWhite(colorSampleBitmap.getPixel(0, 0))
    }

    private fun beginBrushSizeDrag(x: Float, y: Float, startSize: Float) {
        brushSizeDragActive = true
        brushSizeDragStartX = x
        brushSizeDragStartSize = startSize.coerceIn(.5f, 400f)
        brushSizeDragCurrentSize = brushSizeDragStartSize
        brushSizeDragPreviewX = x
        brushSizeDragPreviewY = y
        invalidate()
    }

    private fun updateBrushSizeDrag(x: Float, y: Float) {
        brushSizeDragCurrentSize = BrushSizeDrag.resolve(brushSizeDragStartSize, x - brushSizeDragStartX)
        brushSizeDragPreviewX = x
        brushSizeDragPreviewY = y
        if (activeTool == EditorTool.ERASER) eraserWidth = brushSizeDragCurrentSize else baseWidth = brushSizeDragCurrentSize
        onBrushSizeAdjusted(brushSizeDragCurrentSize)
        invalidate()
    }

    private fun finishBrushSizeDrag(cancelled: Boolean) {
        if (cancelled) {
            if (activeTool == EditorTool.ERASER) eraserWidth = brushSizeDragStartSize else baseWidth = brushSizeDragStartSize
            onBrushSizeAdjusted(brushSizeDragStartSize)
        }
        brushSizeDragActive = false
        activeTool = null
        activeInputWasStylus = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    private fun drawBrushSizePreview(canvas: Canvas) {
        if (!brushSizeDragActive) return
        val radius = (brushSizeDragCurrentSize * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f).coerceAtLeast(3f)
        brushSizePreviewPaint.strokeWidth = 4f
        brushSizePreviewPaint.color = Color.WHITE
        canvas.drawCircle(brushSizeDragPreviewX, brushSizeDragPreviewY, radius, brushSizePreviewPaint)
        brushSizePreviewPaint.strokeWidth = 2f
        brushSizePreviewPaint.color = Color.BLACK
        canvas.drawCircle(brushSizeDragPreviewX, brushSizeDragPreviewY, radius, brushSizePreviewPaint)
        val label = if (brushSizeDragCurrentSize < 10f) "%.1f".format(brushSizeDragCurrentSize) else brushSizeDragCurrentSize.toInt().toString()
        brushSizePreviewTextPaint.style = Paint.Style.FILL
        brushSizePreviewTextPaint.color = Color.WHITE
        canvas.drawText(label, brushSizeDragPreviewX + radius + 14f, brushSizeDragPreviewY - 6f, brushSizePreviewTextPaint)
        brushSizePreviewTextPaint.color = Color.BLACK
        canvas.drawText(label, brushSizeDragPreviewX + radius + 12f, brushSizeDragPreviewY - 8f, brushSizePreviewTextPaint)
    }

    private fun MotionEvent.centroid(): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        for (index in 0 until pointerCount) {
            x += getX(index)
            y += getY(index)
        }
        return x / pointerCount.coerceAtLeast(1) to y / pointerCount.coerceAtLeast(1)
    }

    private fun cancelActiveGestureForNavigation() {
        currentPoints.clear()
        shapeStart = null
        shapeEnd = null
        shapeFreeformPoints.clear()
        if (activeTool == EditorTool.ERASER) cancelEraserGesture()
        selectionGestureBefore?.let(::restoreStrokeSnapshot)
        lassoPoints.clear()
        movingSelection = false
        selectionMoved = false
        selectionGestureBefore = null
        activeTool = null
        activeInputWasStylus = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((0 until event.pointerCount).any { index -> event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER }) lastStylusSeenAt = event.eventTime
        return super.onGenericMotionEvent(event)
    }

    private fun drawShapePreview(canvas: Canvas) {
        if (shapeKind == ShapeKind.FREEFORM) {
            if (shapeFreeformPoints.size < 2) return
            lassoPath.reset()
            lassoPath.moveTo(shapeFreeformPoints.first().x * width, shapeFreeformPoints.first().y * height)
            for (index in 1 until shapeFreeformPoints.size) {
                val point = shapeFreeformPoints[index]
                lassoPath.lineTo(point.x * width, point.y * height)
            }
            if (shapeFreeformPoints.size >= 3) lassoPath.close()
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = inkColor
            paint.alpha = 190
            paint.strokeWidth = baseWidth.coerceAtLeast(2f)
            canvas.drawPath(lassoPath, paint)
            paint.alpha = 255
            return
        }
        val start = shapeStart ?: return
        val end = shapeEnd ?: return
        val shape = ShapeGeometry.create(shapeKind, start, end, width.toFloat(), height.toFloat(), inkColor.toUInt().toLong(), baseWidth) ?: return
        val left = shape.x * width
        val top = shape.y * height
        val right = left + shape.width / width.coerceAtLeast(1) * width
        val bottom = top + shape.height / height.coerceAtLeast(1) * height
        paint.style = Paint.Style.STROKE
        paint.color = inkColor
        paint.alpha = 190
        paint.strokeWidth = baseWidth.coerceAtLeast(2f)
        canvas.save()
        canvas.rotate(shape.rotationDegrees, (left + right) / 2f, (top + bottom) / 2f)
        when (shape.kind) {
            ShapeKind.RECTANGLE -> canvas.drawRect(left, top, right, bottom, paint)
            ShapeKind.ELLIPSE -> canvas.drawOval(left, top, right, bottom, paint)
            ShapeKind.TRIANGLE -> canvas.drawPath(Path().apply { moveTo((left + right) / 2f, top); lineTo(right, bottom); lineTo(left, bottom); close() }, paint)
            ShapeKind.DIAMOND -> canvas.drawPath(Path().apply { moveTo((left + right) / 2f, top); lineTo(right, (top + bottom) / 2f); lineTo((left + right) / 2f, bottom); lineTo(left, (top + bottom) / 2f); close() }, paint)
            ShapeKind.LINE -> canvas.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, paint)
            ShapeKind.ARROW -> {
                val centerY = (top + bottom) / 2f
                canvas.drawLine(left, centerY, right, centerY, paint)
                canvas.drawLine(right, centerY, right - 24f, centerY - 16f, paint)
                canvas.drawLine(right, centerY, right - 24f, centerY + 16f, paint)
            }
            ShapeKind.FREEFORM -> Unit
        }
        canvas.restore()
        paint.alpha = 255
    }

    private fun appendShapePoint(x: Float, y: Float) {
        val point = ShapePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val previous = shapeFreeformPoints.lastOrNull()
        val distance = previous?.let { hypot(((point.x - it.x) * width.coerceAtLeast(1)).toDouble(), ((point.y - it.y) * height.coerceAtLeast(1)).toDouble()) } ?: Double.MAX_VALUE
        if (distance >= 1.0) shapeFreeformPoints += point
    }

    private fun appendLassoPoint(x: Float, y: Float) {
        val point = StrokePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val previous = lassoPoints.lastOrNull()
        if (previous == null || hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) > .0015) lassoPoints += point
    }

    private fun appendSelectionPoint(x: Float, y: Float) {
        if (selectionShape == SelectionShape.FREEFORM) {
            appendLassoPoint(x, y)
            return
        }
        val point = StrokePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        when (lassoPoints.size) {
            0 -> lassoPoints += point
            1 -> lassoPoints += point
            else -> lassoPoints[1] = point
        }
    }

    private fun selectionPolygon(): List<StrokePoint> = when (selectionShape) {
        SelectionShape.FREEFORM -> lassoPoints
        SelectionShape.RECTANGLE -> if (lassoPoints.size >= 2) LassoGeometry.rectangle(lassoPoints.first(), lassoPoints.last()) else emptyList()
    }

    private fun selectWithLasso(polygon: List<StrokePoint>) {
        if (polygon.size < 3) return
        val before = strokes.toList()
        val rebuilt = mutableListOf<InkStroke>()
        val selected = linkedSetOf<String>()
        var splitOccurred = false
        before.forEach { stroke ->
            val previouslySelected = stroke.id in selectedStrokeIds
            val matchesFilter = when (selectionFilter) {
                SelectionFilter.ALL -> true
                SelectionFilter.ACTIVE_LAYER -> stroke.layerId == activeLayerId
                SelectionFilter.CURRENT_BRUSH -> stroke.brushId == brushId
                SelectionFilter.CURRENT_COLOR -> stroke.colorArgb.toInt() == inkColor
            }
            val shouldPartition = stroke.id !in protectedStrokeIds && matchesFilter &&
                (selectionMode == SelectionMode.REPLACE || selectionMode == SelectionMode.ADD || previouslySelected)
            if (!shouldPartition) {
                rebuilt += stroke
                val keepSelected = LassoSelectionPolicy.selectedWithoutEligibleIntersection(selectionMode, previouslySelected)
                if (keepSelected) selected += stroke.id
            } else {
                val partition = LassoGeometry.partition(polygon, stroke)
                splitOccurred = splitOccurred || partition.changed
                partition.pieces.forEach { piece ->
                    rebuilt += piece.stroke
                    val keepSelected = LassoSelectionPolicy.selected(selectionMode, previouslySelected, piece.selected)
                    if (keepSelected) selected += piece.stroke.id
                }
            }
        }
        selectedStrokeIds.clear()
        selectedStrokeIds += selected
        if (splitOccurred) {
            strokes.clear()
            strokes += rebuilt
            commitStrokeEdit(before, strokes)
            val edit = StrokeEdits.difference(before, strokes)
            invalidateStrokeCache(edit?.before.orEmpty().map { it.stroke }, edit?.after.orEmpty().map { it.stroke })
        }
    }

    private fun addPoint(x: Float, y: Float, pressure: Float, time: Long, tilt: Float, orientation: Float) {
        val adjustedPressure = PressureCalibration.apply(pressure, pressureMinimumInput, pressureMaximumInput, pressureSensitivity)
        val previous = currentPoints.lastOrNull()
        val rawX = x.coerceIn(0f, 1f)
        val rawY = y.coerceIn(0f, 1f)
        val inputVelocity = previous?.let { prior -> val elapsed = (time - prior.timeMs).coerceAtLeast(1L); (hypot(((rawX - prior.x) * width).toDouble(), ((rawY - prior.y) * height).toDouble()).toFloat() / elapsed / VELOCITY_FULL_SCALE_PX_PER_MS).coerceIn(0f, 1f) } ?: 0f
        val smooth = StrokeStabilization.smoothing(stabilization, stabilizationBySpeed, stabilizationBySpeedMode, inputVelocity)
        val pointX = ((previous?.x ?: rawX) * smooth + rawX * (1f - smooth)).coerceIn(0f, 1f)
        val pointY = ((previous?.y ?: rawY) * smooth + rawY * (1f - smooth)).coerceIn(0f, 1f)
        val rawVelocity = previous?.let { prior -> val elapsed = (time - prior.timeMs).coerceAtLeast(1L); (hypot(((pointX - prior.x) * width).toDouble(), ((pointY - prior.y) * height).toDouble()).toFloat() / elapsed / VELOCITY_FULL_SCALE_PX_PER_MS).coerceIn(0f, 1f) } ?: 0f
        val point = StrokePoint(
            x = pointX,
            y = pointY,
            pressure = max(.05f, adjustedPressure), timeMs = time, tilt = tilt, orientation = orientation,
            velocity = previous?.let { prior -> prior.velocity * VELOCITY_SMOOTHING + rawVelocity * (1f - VELOCITY_SMOOTHING) } ?: rawVelocity,
        )
        if (previous == null || hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) > .0008) currentPoints += point
    }

    private fun resolveTool(event: MotionEvent, toolType: Int): EditorTool {
        if (toolType == MotionEvent.TOOL_TYPE_ERASER) return EditorTool.ERASER
        val action = when {
            event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 -> primaryButtonAction
            event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 -> secondaryButtonAction
            else -> StylusButtonAction.NONE
        }
        return when (action) {
            StylusButtonAction.ERASER -> EditorTool.ERASER
            StylusButtonAction.LASSO -> EditorTool.SELECT
            StylusButtonAction.NONE -> tool
        }
    }

    private fun makeStroke(points: List<StrokePoint>) = InkStroke(id = currentStrokeId, points = points.toList(), colorArgb = inkColor.toUInt().toLong(), width = baseWidth, opacity = inkOpacity, brushId = brushId, layerId = activeLayerId, tipAssetPath = tipAssetPath, tipAssetPaths = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }, dualTipAssetPaths = dualTipAssetPaths, grainAssetPath = grainAssetPath, grainDensity = grainDensity, grainScale = grainScale, grainRotationDegrees = grainRotationDegrees, grainBrightness = grainBrightness, grainContrast = grainContrast, grainReversed = grainReversed, grainEmphasized = grainEmphasized, grainOffsetJitter = grainOffsetJitter, spacing = brushSpacing, flow = brushFlow, hardness = brushHardness, thickness = brushThickness, thicknessVertical = brushThicknessVertical, rotationDegrees = brushRotation, followStylusOrientation = followStylusOrientation, followStrokeDirection = followStrokeDirection, randomRotationDegrees = randomRotationDegrees, strokeRotationJitterDegrees = strokeRotationJitterDegrees, stampCount = stampCount, stampCountJitter = stampCountJitter, watercolor = brushWatercolor, colorMix = brushColorMix, paintMix = brushPaintMix, spray = brushSpray, sprayParticleSize = sprayParticleSize, sprayParticleSizeUnit = sprayParticleSizeUnit, sprayParticleSizeSyncBrushSize = sprayParticleSizeSyncBrushSize, sprayDensity = sprayDensity, sprayBias = sprayBias, spraySpread = spraySpread, sprayParticleRotationDegrees = sprayParticleRotationDegrees, sprayParticleFollowStylusOrientation = sprayParticleFollowStylusOrientation, sprayParticleFollowStrokeDirection = sprayParticleFollowStrokeDirection, sprayParticleRandomRotationDegrees = sprayParticleRandomRotationDegrees, ribbon = brushRibbon, tipSequenceMode = brushTipSequenceMode, tipFlipHorizontalMode = tipFlipHorizontalMode, tipFlipVerticalMode = tipFlipVerticalMode, blendTipsWithDarken = blendTipsWithDarken, dualBrush = brushDual, dualSizeScale = dualSizeScale, dualSpacing = dualSpacing, dualFlow = dualFlow, dualHardness = dualHardness, dualThickness = dualThickness, dualThicknessVertical = dualThicknessVertical, dualRotationDegrees = dualRotationDegrees, dualFollowStylusOrientation = dualFollowStylusOrientation, dualFollowStrokeDirection = dualFollowStrokeDirection, dualRandomRotationDegrees = dualRandomRotationDegrees, dualStrokeRotationJitterDegrees = dualStrokeRotationJitterDegrees, dualStampCount = dualStampCount, dualStampCountJitter = dualStampCountJitter, dualRibbon = dualRibbon, dualSpray = dualSpray, dualSprayParticleSize = dualSprayParticleSize, dualSprayParticleSizeUnit = dualSprayParticleSizeUnit, dualSprayParticleSizeSyncBrushSize = dualSprayParticleSizeSyncBrushSize, dualSprayDensity = dualSprayDensity, dualSprayBias = dualSprayBias, dualSpraySpread = dualSpraySpread, dualSprayParticleRotationDegrees = dualSprayParticleRotationDegrees, dualSprayParticleFollowStylusOrientation = dualSprayParticleFollowStylusOrientation, dualSprayParticleFollowStrokeDirection = dualSprayParticleFollowStrokeDirection, dualSprayParticleRandomRotationDegrees = dualSprayParticleRandomRotationDegrees, dualTipSequenceMode = dualTipSequenceMode, dualTipFlipHorizontalMode = dualTipFlipHorizontalMode, dualTipFlipVerticalMode = dualTipFlipVerticalMode, dualBlendTipsWithDarken = dualBlendTipsWithDarken, dualCompositeMode = dualCompositeMode, dualApplyRgb = dualApplyRgb, taperMode = taperMode, taperStartEnabled = taperStartEnabled, taperStartLength = taperStartLength, taperStartLengthUnit = taperStartLengthUnit, taperStartMinimum = taperStartMinimum, taperEndEnabled = taperEndEnabled, taperEndLength = taperEndLength, taperEndLengthUnit = taperEndLengthUnit, taperEndMinimum = taperEndMinimum, waterEdgeEnabled = waterEdgeEnabled, waterEdgeRadius = waterEdgeRadius, waterEdgeRadiusUnit = waterEdgeRadiusUnit, waterEdgeOpacity = waterEdgeOpacity, waterEdgeDarkness = waterEdgeDarkness, waterEdgeBlur = waterEdgeBlur, waterEdgeBlurUnit = waterEdgeBlurUnit, sizePressureEnabled = sizePressureEnabled, sizePressureMinimum = sizePressureMinimum, sizePressureCurve = sizePressureCurve, sizeSecondaryDynamics = sizeSecondaryDynamics, opacityPressureEnabled = opacityPressureEnabled, opacityPressureMinimum = opacityPressureMinimum, opacityPressureCurve = opacityPressureCurve, opacitySecondaryDynamics = opacitySecondaryDynamics, flowPressureEnabled = flowPressureEnabled, flowPressureMinimum = flowPressureMinimum, flowPressureCurve = flowPressureCurve, flowSecondaryDynamics = flowSecondaryDynamics, spacingPressureEnabled = spacingPressureEnabled, spacingPressureMinimum = spacingPressureMinimum, spacingPressureCurve = spacingPressureCurve, spacingSecondaryDynamics = spacingSecondaryDynamics, thicknessPressureEnabled = thicknessPressureEnabled, thicknessPressureMinimum = thicknessPressureMinimum, thicknessPressureCurve = thicknessPressureCurve, thicknessSecondaryDynamics = thicknessSecondaryDynamics)

    private fun beginEraserGesture() {
        eraserGestureBefore = strokes.toList()
        lastEraserX = null
        lastEraserY = null
    }

    private fun eraseAlong(x: Float, y: Float, pressure: Float) {
        val targetX = x.coerceIn(0f, 1f)
        val targetY = y.coerceIn(0f, 1f)
        val startX = lastEraserX
        val startY = lastEraserY
        if (startX == null || startY == null) {
            eraseAt(targetX, targetY, pressure)
        } else {
            val distancePixels = hypot(((targetX - startX) * width).toDouble(), ((targetY - startY) * height).toDouble()).toFloat()
            val spacing = (eraserRadiusPixels(pressure) * .45f).coerceAtLeast(1f)
            val steps = ceil(distancePixels / spacing).toInt().coerceIn(1, MAX_ERASER_SAMPLES_PER_EVENT)
            for (step in 1..steps) {
                val amount = step / steps.toFloat()
                eraseAt(startX + (targetX - startX) * amount, startY + (targetY - startY) * amount, pressure)
            }
        }
        lastEraserX = targetX
        lastEraserY = targetY
    }

    private fun eraseAt(x: Float, y: Float, pressure: Float) {
        val eraserRadius = eraserRadiusPixels(pressure)
        var changed = false
        val changedBefore = mutableListOf<InkStroke>()
        val changedAfter = mutableListOf<InkStroke>()
        for (index in strokes.indices.reversed()) {
            val stroke = strokes[index]
            if (stroke.id in protectedStrokeIds) continue
            val strokeRadius = stroke.width.coerceIn(.1f, 800f) * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f
            val combinedRadius = eraserRadius + strokeRadius
            val radiusX = combinedRadius / width.coerceAtLeast(1)
            val radiusY = combinedRadius / height.coerceAtLeast(1)
            if (!StrokeEraser.intersects(stroke, x, y, radiusX, radiusY)) continue
            if (nonDestructiveEraser) {
                val dab = StrokeMaskDab(x, y, eraserRadius / width.coerceAtLeast(1), eraserRadius / height.coerceAtLeast(1), hardness = eraserHardness, opacity = eraserOpacity, tipAssetPath = eraserTipAssetPath)
                if (stroke.masks.lastOrNull()?.let { kotlin.math.abs(it.x - dab.x) < .00001f && kotlin.math.abs(it.y - dab.y) < .00001f } == true) continue
                val masked = stroke.copy(masks = stroke.masks + dab)
                strokes[index] = masked
                changedBefore += stroke
                changedAfter += masked
                changed = true
                continue
            }
            val pieces = if (localEraser) {
                StrokeEraser.erase(stroke, x, y, radiusX, radiusY)?.replacements ?: continue
            } else {
                emptyList()
            }
            strokes.removeAt(index)
            selectedStrokeIds.remove(stroke.id)
            if (localEraser) strokes.addAll(index, pieces)
            changedBefore += stroke
            changedAfter += pieces
            changed = true
        }
        if (changed) invalidateStrokeCache(changedBefore, changedAfter)
    }

    private fun eraserRadiusPixels(pressure: Float): Float {
        val pressureFactor = if (eraserPressureSize) PressureCalibration.apply(pressure, pressureMinimumInput, pressureMaximumInput, pressureSensitivity).coerceIn(.15f, 1f) else 1f
        return (eraserWidth.coerceIn(.5f, 400f) * pressureFactor * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f).coerceAtLeast(1f)
    }

    private fun finishEraserGesture() {
        eraserGestureBefore?.let { commitStrokeEdit(it, strokes) }
        eraserGestureBefore = null
        lastEraserX = null
        lastEraserY = null
    }

    private fun cancelEraserGesture() {
        eraserGestureBefore?.let(::restoreStrokeSnapshot)
        eraserGestureBefore = null
        lastEraserX = null
        lastEraserY = null
    }

    private fun findStroke(x: Float, y: Float, threshold: Float): Int = strokes.indexOfLast { stroke ->
        stroke.id !in protectedStrokeIds && StrokeEraser.intersects(stroke, x, y, threshold, threshold)
    }

    fun undo() {
        val edit = editHistory.takeUndo() ?: return
        if (editTouchesProtectedLayer(edit)) {
            editHistory.takeRedo()
            return
        }
        applyStrokeEdit(edit, forward = false)
    }

    fun redo() {
        val edit = editHistory.takeRedo() ?: return
        if (editTouchesProtectedLayer(edit)) {
            editHistory.takeUndo()
            return
        }
        applyStrokeEdit(edit, forward = true)
    }

    fun clearInk() {
        val before = strokes.toList()
        val removableIds = strokes.filterNot { it.id in protectedStrokeIds }.mapTo(mutableSetOf()) { it.id }
        if (removableIds.isEmpty()) return
        val removed = strokes.filter { it.id in removableIds }
        strokes.removeAll { it.id in removableIds }
        selectedStrokeIds.removeAll(removableIds)
        commitStrokeEdit(before, strokes)
        invalidateStrokeCache(removed, emptyList())
    }

    private fun commitStrokeEdit(before: List<InkStroke>, after: List<InkStroke>) {
        val edit = StrokeEdits.difference(before, after) ?: return
        editHistory.record(edit)
        persistStrokeTransition(edit.before, edit.after)
    }

    private fun applyStrokeEdit(edit: StrokeEdit, forward: Boolean) {
        val remove = if (forward) edit.before else edit.after
        val insert = if (forward) edit.after else edit.before
        val updated = StrokeEdits.apply(strokes, remove, insert)
        restoreStrokeSnapshot(updated)
        persistStrokeTransition(remove, insert)
    }

    private fun editTouchesProtectedLayer(edit: StrokeEdit): Boolean {
        val protectedLayers = protectedLayerIds + strokes.filter { it.id in protectedStrokeIds }.map(InkStroke::layerId)
        return (edit.before + edit.after).any { it.stroke.layerId in protectedLayers }
    }

    private fun persistStrokeTransition(
        remove: List<app.inkloom.document.PositionedStroke>,
        insert: List<app.inkloom.document.PositionedStroke>,
    ) {
        onStrokesReplaced(
            remove.map { it.stroke.id },
            insert.map { it.stroke },
            insert.map { it.index },
        )
    }

    private fun restoreStrokeSnapshot(snapshot: List<InkStroke>) {
        val before = strokes.toList()
        strokes.clear()
        strokes.addAll(snapshot)
        selectedStrokeIds.retainAll(snapshot.mapTo(mutableSetOf()) { it.id })
        val edit = StrokeEdits.difference(before, snapshot)
        invalidateStrokeCache(edit?.before.orEmpty().map { it.stroke }, edit?.after.orEmpty().map { it.stroke })
    }

    fun scaleSelection(factor: Float) = transformSelection(radiusScale = factor) { dx, dy -> dx * factor to dy * factor }

    fun rotateSelection(degrees: Float) {
        val radians = Math.toRadians(degrees.toDouble())
        transformSelection(rotationDeltaDegrees = degrees) { dx, dy -> (dx * cos(radians) - dy * sin(radians)).toFloat() to (dx * sin(radians) + dy * cos(radians)).toFloat() }
    }

    fun copySelection() {
        val before = strokes.toList()
        val copies = strokes.filter { it.id in selectedStrokeIds }.map { original -> original.copy(
            id = java.util.UUID.randomUUID().toString(),
            points = original.points.map { it.copy(x = (it.x + .03f).coerceIn(0f, 1f), y = (it.y + .03f).coerceIn(0f, 1f)) },
            masks = original.masks.map { it.copy(x = (it.x + .03f).coerceIn(0f, 1f), y = (it.y + .03f).coerceIn(0f, 1f)) },
        ) }
        if (copies.isEmpty()) return
        strokes += copies; selectedStrokeIds.clear(); selectedStrokeIds += copies.map(InkStroke::id); commitStrokeEdit(before, strokes); invalidateStrokeCache(emptyList(), copies)
    }

    fun copySelectionToClipboard(): Boolean {
        val selected = strokes.filter { it.id in selectedStrokeIds }
        val encoded = StrokeClipboardCodec.encode(selected) ?: return false
        val clip = ClipData(
            ClipDescription("Inkloom strokes", arrayOf(StrokeClipboardCodec.MIME_TYPE)),
            ClipData.Item(encoded),
        )
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        return true
    }

    fun cutSelectionToClipboard(): Boolean {
        if (!copySelectionToClipboard()) return false
        deleteSelection()
        return true
    }

    fun pasteSelectionFromClipboard(): Boolean {
        if (activeLayerId in protectedLayerIds) return false
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip ?: return false
        if (clip.itemCount < 1 || !clip.description.hasMimeType(StrokeClipboardCodec.MIME_TYPE)) return false
        val payload = StrokeClipboardCodec.decode(clip.getItemAt(0).coerceToText(context)) ?: return false
        val copies = StrokeClipboardCodec.materialize(payload, activeLayerId)
        if (copies.isEmpty()) return false
        val before = strokes.toList()
        strokes += copies
        selectedStrokeIds.clear()
        selectedStrokeIds += copies.map(InkStroke::id)
        commitStrokeEdit(before, strokes)
        invalidateStrokeCache(emptyList(), copies)
        return true
    }

    fun deleteSelection() {
        if (selectedStrokeIds.isEmpty()) return
        val before = strokes.toList()
        val ids = selectedStrokeIds.toSet(); val removed = strokes.filter { it.id in ids }; strokes.removeAll { it.id in ids }; commitStrokeEdit(before, strokes); selectedStrokeIds.clear(); invalidateStrokeCache(removed, emptyList())
    }

    private fun transformSelection(
        radiusScale: Float = 1f,
        rotationDeltaDegrees: Float = 0f,
        transform: (Float, Float) -> Pair<Float, Float>,
    ) {
        val before = strokes.toList()
        val selected = strokes.filter { it.id in selectedStrokeIds }
        val points = selected.flatMap(InkStroke::points)
        if (points.isEmpty()) return
        val centerX = points.map(StrokePoint::x).average().toFloat(); val centerY = points.map(StrokePoint::y).average().toFloat()
        selected.forEach { current ->
            val changed = current.copy(
                points = current.points.map { point -> val (x, y) = transform(point.x - centerX, point.y - centerY); point.copy(x = (centerX + x).coerceIn(0f, 1f), y = (centerY + y).coerceIn(0f, 1f)) },
                masks = current.masks.map { mask ->
                    val (x, y) = transform(mask.x - centerX, mask.y - centerY)
                    mask.copy(
                        x = (centerX + x).coerceIn(0f, 1f),
                        y = (centerY + y).coerceIn(0f, 1f),
                        radiusX = mask.radiusX * radiusScale,
                        radiusY = mask.radiusY * radiusScale,
                        rotationDegrees = mask.rotationDegrees + rotationDeltaDegrees,
                    )
                },
            )
            val currentIndex = strokes.indexOfFirst { it.id == current.id }
            if (currentIndex >= 0) strokes[currentIndex] = changed
        }
        commitStrokeEdit(before, strokes)
        invalidateStrokeCache(selected, strokes.filter { it.id in selectedStrokeIds })
    }

    private companion object {
        const val REFERENCE_WIDTH = 1600f
        const val MAX_CACHE_PIXELS = 16_000_000L
        const val MAX_ERASER_SAMPLES_PER_EVENT = 256
        const val VELOCITY_FULL_SCALE_PX_PER_MS = 2f
        const val VELOCITY_SMOOTHING = .65f
        const val SALT_SIZE = 11
        const val SALT_OPACITY = 23
        const val SALT_FLOW = 37
        const val SALT_SPACING = 53
        const val SALT_THICKNESS = 71
        const val DUAL_SCATTER_SEED = 0x27C4915DL
        const val DUAL_ROTATION_SEED = 0x61B8D2A3L
        const val VIEW_ROTATION_DEGREES_PER_PIXEL = .25f
        const val DUAL_SEQUENCE_SEED = 0x35A71C9E
        const val MAX_TIP_CACHE_ENTRIES = 64
    }

    private fun pressureDynamic(enabled: Boolean, minimum: Float, curve: List<BrushCurvePoint>, input: Float): Float {
        return BrushDynamics.pressure(enabled, minimum, curve, input)
    }

    private fun secondaryDynamic(dynamics: BrushSecondaryDynamics, point: StrokePoint, strokeSeed: Int, salt: Int): Float = BrushDynamics.secondary(dynamics, point, strokeSeed, salt)

    private fun midpoint(start: StrokePoint, end: StrokePoint) = StrokePoint(
        x = (start.x + end.x) / 2f, y = (start.y + end.y) / 2f,
        pressure = (start.pressure + end.pressure) / 2f,
        timeMs = (start.timeMs + end.timeMs) / 2L,
        tilt = (start.tilt + end.tilt) / 2f,
        orientation = (start.orientation + end.orientation) / 2f,
        velocity = (start.velocity + end.velocity) / 2f,
    )

    private fun Float?.orDefault() = this ?: 1f
}
