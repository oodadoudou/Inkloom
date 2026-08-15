package app.inkloom.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.KeyEvent
import android.view.DragEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.WorkerThread
import androidx.annotation.VisibleForTesting
import app.inkloom.engine.v3.api.*
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v3.android.PixelTileCanvasRenderer
import app.inkloom.engine.v3.composite.PixelLayerProjectionCache
import app.inkloom.engine.v3.composite.PixelLayerProjectionInput
import app.inkloom.engine.v3.scene.AlphaMaskOperation
import app.inkloom.engine.v3.surface.PixelTileFormat
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.ViewportState
import app.inkloom.document.StrokeMaskDab
import app.inkloom.document.StrokeMaskRenderer
import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushCursorGeometry
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.ProcreateColorDynamics
import app.inkloom.document.ProcreateBrushBlend
import app.inkloom.document.ProcreateFiltering
import app.inkloom.document.ProcreateRenderingMode
import app.inkloom.document.ProcreateRendering
import app.inkloom.document.ProcreateWetMix
import app.inkloom.document.ProcreateTaper
import app.inkloom.document.StrokeInputKind
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushColorJitter
import app.inkloom.document.BrushBlur
import app.inkloom.document.BrushDynamics
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushStampCount
import app.inkloom.document.BrushStampRotation
import app.inkloom.document.BrushSizeDrag
import app.inkloom.document.BrushTipSequence
import app.inkloom.document.BrushTipFlip
import app.inkloom.document.BrushTipTransform
import app.inkloom.document.ShapeRoundnessJitter
import app.inkloom.document.BrushTaperMode
import app.inkloom.document.CspDualBrushCompositor
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspBrushGap
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspStrokeTextureCompositor
import app.inkloom.document.CspColorMixMode
import app.inkloom.document.CspPigmentMixingMode
import app.inkloom.document.CspBrightnessCorrection
import app.inkloom.document.CspPigmentMixer
import app.inkloom.document.CspScreenBrushSize
import app.inkloom.document.CanvasGuideGestureMode
import app.inkloom.document.CanvasGuideState
import app.inkloom.document.CanvasGuides
import app.inkloom.document.CanvasSnapConstraint
import app.inkloom.document.GrainBehavior
import app.inkloom.document.FillSubTool
import app.inkloom.document.GrainDynamics
import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientDefinition
import app.inkloom.document.GradientGeometry
import app.inkloom.document.GradientShaderFactory
import app.inkloom.document.LassoGeometry
import app.inkloom.document.LassoSelectionPolicy
import app.inkloom.document.SelectionMaskCompositor
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.LayerMask
import app.inkloom.document.LayerMaskRenderer
import app.inkloom.document.toBlendMode
import app.inkloom.document.toPixelBlendMode
import app.inkloom.document.LaserTrail
import app.inkloom.document.PaperPattern
import app.inkloom.document.PressureCalibration
import app.inkloom.document.PerspectiveGuideMode
import app.inkloom.document.PerspectiveGuideState
import app.inkloom.document.PerspectiveGuides
import app.inkloom.document.PerspectiveSnapConstraint
import app.inkloom.document.SpecialRulerConstraint
import app.inkloom.document.SpecialRulerHandle
import app.inkloom.document.SpecialRulerMode
import app.inkloom.document.SpecialRulerState
import app.inkloom.document.SpecialRulers
import app.inkloom.document.PageElement
import app.inkloom.document.PdfGotoLink
import app.inkloom.document.PdfGotoLinkNavigation
import app.inkloom.document.RenderedColorSampling
import app.inkloom.document.SelectionContourGeometry
import app.inkloom.document.RulerGestureMode
import app.inkloom.document.RulerGuide
import app.inkloom.document.RulerGuideState
import app.inkloom.document.ShapeGeometry
import app.inkloom.document.ShapeKind
import app.inkloom.document.ShapePoint
import app.inkloom.document.SingleFingerDoubleTapUndoGesture
import app.inkloom.document.SprayGeometry
import app.inkloom.document.SprayDynamics
import app.inkloom.document.StrokeEdit
import app.inkloom.document.StrokeTileIndex
import app.inkloom.document.StrokeEdits
import app.inkloom.document.StrokeEraser
import app.inkloom.document.StrokeClipboardCodec
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.StrokeCorrection
import app.inkloom.document.StrokePostCorrection
import app.inkloom.document.StrokeStampSampler
import app.inkloom.document.StrokeStabilization
import app.inkloom.document.StrokeTaper
import app.inkloom.document.StrokeTransform
import app.inkloom.document.StrokeTileKey
import app.inkloom.document.StylusInputClassifier
import app.inkloom.document.TwoFingerTapUndoGesture
import app.inkloom.document.TwoPointerRotationGesture
import app.inkloom.document.ThreeFingerTapRedoGesture
import app.inkloom.document.ViewportTransform
import app.inkloom.document.FloodFill
import app.inkloom.document.LiquifyMode
import app.inkloom.document.VectorLiquify
import app.inkloom.document.VectorBlend
import app.inkloom.document.WaterEdge
import app.inkloom.model.EditorTool
import app.inkloom.model.SymmetryMode
import app.inkloom.settings.CspShortcutResolver
import app.inkloom.settings.AxisShortcutBinding
import app.inkloom.settings.ButtonShortcutBinding
import app.inkloom.settings.KeyInputDiagnostic
import app.inkloom.settings.MotionInputDiagnostic
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutActivation
import app.inkloom.settings.ShortcutBinding
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.ShortcutAxisAccumulator
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.StylusButtonShortcut
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxMotionResolver
import app.inkloom.settings.SelectionFilter
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.ReferenceTarget
import kotlin.math.ceil
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class LayerElementBitmaps(val base: Bitmap?, val additions: Bitmap?) {
    fun recycle() {
        base?.recycle()
        if (additions !== base) additions?.recycle()
    }
}

data class CanvasColorDrop(val color: Int)

private class ActiveCspDualBrushSurface(val width: Int, val height: Int, val applyRgb: Boolean) {
    val primary: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val secondary: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val output: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    fun clear() {
        primary.eraseColor(Color.TRANSPARENT)
        secondary.eraseColor(Color.TRANSPARENT)
        output.eraseColor(Color.TRANSPARENT)
    }

    fun recycle() {
        primary.recycle()
        secondary.recycle()
        output.recycle()
    }
}

class InkCanvasView(context: Context) : View(context) {
    private val renderLock = Any()
    private val strokeInputModeler = NativeStrokeSession()
    private val strokes = mutableListOf<InkStroke>()
    private val editHistory = EngineTransactionJournal()
    private val currentPoints = mutableListOf<StrokePoint>()
    private var currentStrokeId = java.util.UUID.randomUUID().toString()
    private var activeStrokeTemplate: InkStroke? = null
    internal var activeStrokeSurface: ActiveStrokeCompositor? = null
    private var activeSurfaceFrameScheduled = false
    private var activeSurfaceCommitted = false
    private var activeTipRefreshScheduled = false
    private var forceSynchronousTipDecode = false
    private val activePrimaryStampCounts = mutableMapOf<String, Int>()
    private val activeDualStampCounts = mutableMapOf<String, Int>()
    private val activePrimarySamples = mutableMapOf<String, app.inkloom.document.StrokeStampSampler.Incremental>()
    private val activeDualSamples = mutableMapOf<String, app.inkloom.document.StrokeStampSampler.Incremental>()
    private val activePrimaryScatterRandoms = mutableMapOf<String, java.util.Random>()
    private val activePrimaryRotationRandoms = mutableMapOf<String, java.util.Random>()
    private val activeDualScatterRandoms = mutableMapOf<String, java.util.Random>()
    private val activeDualRotationRandoms = mutableMapOf<String, java.util.Random>()
    private val activeCspDualBrushSurfaces = mutableMapOf<String, ActiveCspDualBrushSurface>()
    /**
     * Authoritative raster sampling cannot share the low-latency surface's replay state. Android
     * may request a complete front-buffer redraw at any time; resetting that visual cache must not
     * make already-transactional dabs deposit for a second time.
     */
    private val rasterPrimaryStampCounts = mutableMapOf<String, Int>()
    private val rasterRawPointCounts = mutableMapOf<String, Int>()
    private val rasterPrimarySamples = mutableMapOf<String, app.inkloom.document.StrokeStampSampler.Incremental>()
    private val rasterPrimaryScatterRandoms = mutableMapOf<String, java.util.Random>()
    private val rasterPrimaryRotationRandoms = mutableMapOf<String, java.util.Random>()
    private var rasterIncrementalPatchBitmap: Bitmap? = null
    private var rasterIncrementalRendering = false
    private var rasterIncrementalPaintEnabled = false
    @VisibleForTesting internal var rasterIncrementalPatchCount: Int = 0
        private set
    @VisibleForTesting internal var rasterFullReplayCount: Int = 0
        private set
    private val activeSurfaceFrame = Runnable {
        activeSurfaceFrameScheduled = false
        val surface = activeStrokeSurface ?: return@Runnable
        if (activeTool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) || currentPoints.isEmpty()) return@Runnable
        advanceWetPaintGesture()
        if (usesViewActivePreview()) {
            surface.cancel()
            postInvalidateOnAnimation()
            return@Runnable
        }
        // End taper depends on the eventual stroke length. Replaying the complete growing stroke
        // on every display frame is quadratic and made long SUT/Procreate brushes fall seconds
        // behind the pen. Preview the body incrementally; the committed stroke applies its exact
        // terminal taper once, affecting only the final tail.
        surface.render(symmetryStrokes(currentPoints).map { stroke ->
            val hasProcreateEnd = stroke.procreateTaper.pressureEndLength > .0001f || stroke.procreateTaper.touchEndLength > .0001f
            if (stroke.taperEndEnabled || hasProcreateEnd) stroke.copy(
                taperEndEnabled = false,
                procreateTaper = stroke.procreateTaper.copy(pressureEndLength = 0f, touchEndLength = 0f),
            ) else stroke
        })
    }
    private val activeTipRefreshFrame = Runnable {
        activeTipRefreshScheduled = false
        val surface = activeStrokeSurface ?: return@Runnable
        if (activeTool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) || currentPoints.isEmpty()) return@Runnable
        // A newly decoded page-space grain variant must replace every provisional dab, not only
        // the next incremental tail. Clearing the front buffer prevents the familiar pen-up shape
        // change while all decoding still happens away from MotionEvent.
        surface.cancel()
        resetActiveRenderState()
        requestActiveSurfaceFrame()
    }
    private var backgroundBitmap: Bitmap? = null
    private val selectedStrokeIds = linkedSetOf<String>()
    private var selectedMaskRegions: List<List<StrokePoint>> = emptyList()
    private var documentSelectionState: DocumentSelectionState? = null
    private var selectionAlphaMask: SparseAlphaMask? = null
    private var selectionDisplayMask: SparseAlphaMask? = null
    private var selectionBoundaryMask: SparseAlphaMask? = null
    private var lastDeselectedStrokeIds: Set<String> = emptySet()
    private var lastDeselectedMaskRegions: List<List<StrokePoint>> = emptyList()
    private var lastDeselectedAlphaMask: SparseAlphaMask? = null
    private var lastDeselectedFeatherPixels = 0f
    private var lastReportedSelectionActive: Boolean? = null
    var selectionFeatherPixels: Float = 0f
        private set
    private var selectionGestureBefore: List<InkStroke>? = null
    private var eraserGestureBefore: List<InkStroke>? = null
    private var pendingEraserInvalidationBefore: List<InkStroke>? = null
    private var eraserInvalidationScheduled = false
    private val eraserInvalidationFrame = Runnable {
        eraserInvalidationScheduled = false
        flushEraserInvalidation()
    }
    private var lastEraserX: Float? = null
    private var lastEraserY: Float? = null
    private var rasterTileEraserSession: RasterTileEraserSession? = null
    private var rasterEraserBitmap: Bitmap? = null
    private var rasterEraserChanged = false
    private var rasterEraserDirtyBounds: Rect? = null
    private val rasterEraserPoints = mutableListOf<StrokePoint>()
    private val eraserStrokeIndices = mutableMapOf<String, Int>()
    private var eraserMaskBatch: MutableMap<String, MutableList<StrokeMaskDab>>? = null
    private var strokeHistoryKey: String? = null
    private val lassoPoints = mutableListOf<StrokePoint>()
    private var shapeStart: ShapePoint? = null
    private var shapeEnd: ShapePoint? = null
    private val shapeFreeformPoints = mutableListOf<ShapePoint>()
    private var shapeFreeformLastMoveAt = 0L
    private var gradientStart: ShapePoint? = null
    private var gradientEnd: ShapePoint? = null
    private var fillSourceBitmap: Bitmap? = null
    private var fillGestureUsesSelection = false
    private var fillSeedX = 0
    private var fillSeedY = 0
    private var fillBaseSensitivity = 0f
    private var fillDragOriginX = 0f
    private var fillSensitivityNow = 0f
    private var fillPreviewTolerance = -1
    private var fillPreviewComputedAt = 0L
    private var fillPreviewMask: Bitmap? = null
    private var fillPreviewLeft = 0f
    private var fillPreviewTop = 0f
    private var fillPreviewWidth = 0f
    private var fillPreviewHeight = 0f
    private val fillGesturePoints = mutableListOf<StrokePoint>()
    private val fillPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN) }
    private var smudgeBefore: List<InkStroke>? = null
    private var smudgeLastX = 0f
    private var smudgeLastY = 0f
    private var smudgeChanged = false
    private var smudgeDirtyBounds: Rect? = null
    private var rasterTileBlendSession: RasterTileBlendSession? = null
    private var rasterBlendBitmap: Bitmap? = null
    private var rasterPaintSession: RasterTilePaintSession? = null
    private var rasterWetPaintBitmap: Bitmap? = null
    private var wetPaintDirtyBounds: Rect? = null
    private val rasterFrontPatchPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var wetPaintLastPositions: List<Pair<Float, Float>> = emptyList()
    private var wetPaintPickupColors: MutableList<Int?> = mutableListOf()
    private val localToolStrokeIndices = mutableMapOf<String, Int>()
    private var pendingSmudgeX = 0f
    private var pendingSmudgeY = 0f
    private var hasPendingSmudge = false
    private var smudgeFrameScheduled = false
    private val smudgeFrame = Runnable {
        smudgeFrameScheduled = false
        if (hasPendingSmudge) {
            hasPendingSmudge = false
            extendSmudge(pendingSmudgeX, pendingSmudgeY)
        }
    }
    private var liquifyBefore: List<InkStroke>? = null
    private var rasterTileLiquifySession: RasterTileLiquifySession? = null
    private var rasterLiquifyBitmap: Bitmap? = null
    private var liquifyLastX = 0f
    private var liquifyLastY = 0f
    private var liquifyChanged = false
    private var liquifyDirtyBounds: Rect? = null
    private var pendingLiquifyX = 0f
    private var pendingLiquifyY = 0f
    private var pendingLiquifyPressure = 1f
    private var liquifyPressure = 1f
    private var liquifyHoldEligible = false
    private var liquifyMovedBeyondSlop = false
    private var liquifyDownX = 0f
    private var liquifyDownY = 0f
    @VisibleForTesting
    internal var liquifyLongPressDelayMs: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private var hasPendingLiquify = false
    private var liquifyFrameScheduled = false
    private val liquifyFrame = Runnable {
        liquifyFrameScheduled = false
        if (hasPendingLiquify) {
            hasPendingLiquify = false
            extendLiquify(pendingLiquifyX, pendingLiquifyY, pendingLiquifyPressure)
        }
    }
    private val liquifyHoldFrame = object : Runnable {
        override fun run() {
            if (!liquifyHoldEligible || liquifyMovedBeyondSlop || !liquifyGestureActive()) return
            extendLiquify(liquifyLastX, liquifyLastY, liquifyPressure)
            postOnAnimation(this)
        }
    }
    private val liquifyHoldStart = Runnable {
        if (liquifyHoldEligible && !liquifyMovedBeyondSlop && liquifyGestureActive()) liquifyHoldFrame.run()
    }
    @VisibleForTesting
    internal fun runLiquifyLongPressFrameForTest() {
        liquifyHoldStart.run()
        removeCallbacks(liquifyHoldFrame)
    }
    private fun liquifyGestureActive(): Boolean = liquifyBefore != null || rasterTileLiquifySession != null
    private var correctionBefore: List<InkStroke>? = null
    private var correctionStrokeId: String? = null
    private var correctionStartX = 0f
    private var correctionEndX = 0f
    private var correctionEndY = 0f
    private var correctionPointIndex = -1
    private var correctionChanged = false
    private var movingSelection = false
    private var selectionMoved = false
    private var selectionTransformMode = TRANSFORM_NONE
    private var selectionTransformArmed = false
    private var selectionTransformCenterX = 0f
    private var selectionTransformCenterY = 0f
    private var selectionTransformStartDistance = 0f
    private var selectionTransformStartAngle = 0f
    private var selectionTransformStartXSpan = 0f
    private var selectionTransformStartYSpan = 0f
    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val selectionHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(17, 17, 17) }
    private val selectionAntPattern = Bitmap.createBitmap(
        16,
        16,
        Bitmap.Config.ARGB_8888,
    ).apply {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (((x + y) / 4) % 2 == 0) Color.WHITE else Color.TRANSPARENT
        }
        setPixels(pixels, 0, width, 0, 0, width, height)
    }
    private val selectionAntShader = BitmapShader(selectionAntPattern, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val selectionAntMatrix = Matrix()
    private val selectionBoundaryRect = android.graphics.RectF()
    private val selectionBlackFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
    private val selectionBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.FILL
        colorFilter = selectionBlackFilter
    }
    private val selectionMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val selectionAntPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = selectionAntShader
        blendMode = BlendMode.SRC_IN
    }
    private val selectionProtectedPattern = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
        val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(44, 32, 32, 32)
            strokeWidth = 2f
        }
        Canvas(this).apply {
            drawLine(-8f, 16f, 8f, 0f, patternPaint)
            drawLine(0f, 24f, 24f, 0f, patternPaint)
            drawLine(8f, 24f, 24f, 8f, patternPaint)
        }
    }
    private val selectionProtectedShader = BitmapShader(selectionProtectedPattern, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val selectionProtectedMatrix = Matrix()
    private val selectionProtectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = selectionProtectedShader
    }
    private val selectionCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        blendMode = BlendMode.DST_OUT
    }
    private val liveSelectionOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val liveSelectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(28, 24, 119, 242)
    }
    private val liveSelectionOriginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var lastX = 0f
    private var lastY = 0f
    private var navigationX = 0f
    private var navigationY = 0f
    private var navigationDownX = 0f
    private var navigationDownY = 0f
    private var navigationSpan = 0f
    private var navigationGestureStartSpan = 0f
    private var navigationGestureRotation = 0f
    private var navigationMoved = false
    private var navigationTapEligible = false
    /** Once two fingers enter viewport navigation, the gesture remains navigation until the last
     * contact is released. This prevents the remaining finger from becoming an accidental stroke. */
    private var viewportGestureActive = false
    private var externalViewportGestureContact = false
    private val navigationTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val singleFingerDoubleTapUndoGesture = SingleFingerDoubleTapUndoGesture(navigationTouchSlop)
    private val twoFingerTapUndoGesture = TwoFingerTapUndoGesture(navigationTouchSlop)
    private val threeFingerTapRedoGesture = ThreeFingerTapRedoGesture(navigationTouchSlop)
    private val twoPointerRotationGesture = TwoPointerRotationGesture()
    private val colorSampleBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var activeTool: EditorTool? = null
    private var activeInputWasStylus = false
    private var pageContactRejected = false
    private var lastStylusSeenAt = Long.MIN_VALUE
    /** Pointer ids, unlike pointer indices, remain stable while a palm/finger joins the stream. */
    private var activeStylusPointerId = MotionEvent.INVALID_POINTER_ID
    private var ctrlModifierHeld = false
    private var altModifierHeld = false
    private var lastStylusButtonState = 0
    private val heldStylusButtons = mutableMapOf<Int, Pair<StylusButtonAction, ShortcutTrigger>>()
    private var stylusCommandConsumesContact = false
    private var stylusHandNavigationContact = false
    private var brushSizeDragActive = false
    private var brushSizeDragStartX = 0f
    private var brushSizeDragStartSize = 0f
    private var brushSizeDragCurrentSize = 0f
    private var brushSizeDragPreviewX = 0f
    private var brushSizeDragPreviewY = 0f
    private var continuousPlotActive = false
    private var continuousPlotX = 0f
    private var continuousPlotY = 0f
    private var continuousPlotPressure = 1f
    private var continuousPlotTilt = 0f
    private var continuousPlotOrientation = 0f
    private val laserTrail = LaserTrail()
    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 45, 85)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rulerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 245, 245); alpha = 220; style = Paint.Style.FILL }
    private val rulerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(17, 17, 17); alpha = 210; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val rulerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(17, 17, 17); textSize = 28f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private var rulerGestureMode: RulerGestureMode? = null
    private var rulerGestureStart: RulerGuideState? = null
    private var rulerGestureX = 0f
    private var rulerGestureY = 0f
    private val snapGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(130, 130, 130); alpha = 70; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val alignmentGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 110, 220); alpha = 210; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val alignmentGuideHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 245; style = Paint.Style.FILL }
    private var canvasGuideGestureMode: CanvasGuideGestureMode? = null
    private var canvasGuideGestureStart: CanvasGuideState? = null
    private var activeCanvasSnapConstraint: CanvasSnapConstraint? = null
    private val perspectiveGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(55, 116, 220); alpha = 140; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val perspectiveHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 250; style = Paint.Style.FILL }
    private var perspectiveGuideGesturePoint: Int? = null
    private var perspectiveGuideGestureStart: PerspectiveGuideState? = null
    private var activePerspectiveSnapConstraint: PerspectiveSnapConstraint? = null
    private var specialRulerGestureHandle: SpecialRulerHandle? = null
    private var specialRulerGestureStart: SpecialRulerState? = null
    private var activeSpecialRulerConstraint: SpecialRulerConstraint? = null
    private val continuousPlotRunnable = object : Runnable {
        override fun run() {
            if (!continuousPlotActive || !brushContinuousPlot || activeTool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) return
            addPoint(
                continuousPlotX,
                continuousPlotY,
                continuousPlotPressure,
                SystemClock.uptimeMillis(),
                continuousPlotTilt,
                continuousPlotOrientation,
                force = true,
            )
            postInvalidateOnAnimation()
            postDelayed(this, CONTINUOUS_PLOT_INTERVAL_MS)
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // Stamped brushes can deposit hundreds of dabs per display frame. Reusing these objects keeps
    // the input/render hot path out of ART's allocator and prevents GC pauses under the pen.
    private val primaryStampMatrix = Matrix()
    private val secondaryStampMatrix = Matrix()
    private val dualCompositeOutputPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val edgeStampMatrix = Matrix()
    private val srcInColorFilters = object : LinkedHashMap<Int, PorterDuffColorFilter>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, PorterDuffColorFilter>): Boolean = size > MAX_COLOR_FILTER_CACHE_ENTRIES
    }
    private val blurMaskFilters = object : LinkedHashMap<Int, BlurMaskFilter>(24, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > MAX_BLUR_MASK_CACHE_ENTRIES
    }
    private val paperFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val brushSizePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val brushSizePreviewTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val hoverCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var hoverCursorVisible = false
    private var hoverCursorX = 0f
    private var hoverCursorY = 0f
    private var hoverCursorUsesEraser = false
    private var hoverCursorPressure = 1f
    private var hoverCursorTilt = 0f
    private var hoverCursorOrientationRadians = 0f
    private var hoverCursorDirectionDegrees = 0f
    private var hoverCursorHasDirection = false
    private var hoverCursorLastX = 0f
    private var hoverCursorLastY = 0f
    internal var externalHoverCursor: Boolean = false
        set(value) { field = value; postInvalidateOnAnimation() }
    internal var onHoverCursorStyleChanged: (() -> Unit)? = null
    private var configuredInputTool: EditorTool? = null
    private var configuredInputBrushId: String? = null
    private var configuredBrushTemplate: InkStroke? = null
    private var configuredBrushProgram: BrushProgram? = null
    private var brushResources = BrushResourceCache()
    private var wholeStrokeTextureBitmap: Bitmap? = null
    private var activeWholeTextureCoverageBitmap: Bitmap? = null
    private var activeWholeTextureStrokeId: String? = null
    private var activeWholeTextureProgram: InkStroke? = null
    private var activeWholeTexturePointCount: Int = 0
    private var activeWholeTexturePageWidth: Int = 0
    private var activeWholeTexturePageHeight: Int = 0
    private var incrementalActiveDraw: Boolean = false
    private val wholeStrokeReplacementPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private var prewarmedBrushSignature: String? = null
    private val protectedStrokeIds = mutableSetOf<String>()
    private val protectedLayerIds = mutableSetOf<String>()
    private val committedScene = SparseTileScene(MAX_CACHE_PIXELS)
    internal var onTileRendered: ((StrokeTileKey, Int) -> Unit)?
        get() = committedScene.onTileRendered
        set(value) { committedScene.onTileRendered = value }

    var tool: EditorTool = EditorTool.PEN
        set(value) {
            if (field == value) return
            field = value
            if (value != EditorTool.SELECT) selectionTransformArmed = false
            if (hoverCursorVisible) {
                hoverCursorUsesEraser = value == EditorTool.ERASER
                invalidateInternalHoverCursor()
            }
            onHoverCursorStyleChanged?.invoke()
        }
    var forceNavigation: Boolean = false
    var rotateNavigation: Boolean = false
    var externalViewportGestures: Boolean = false
    var viewportScale: Float = 1f
        set(value) { field = value.takeIf(Float::isFinite)?.coerceIn(1f, ViewportTransform.MAXIMUM_SCALE) ?: 1f; invalidate() }
    var viewportPanX: Float = 0f
        set(value) { field = value.takeIf(Float::isFinite) ?: 0f; invalidate() }
    var viewportPanY: Float = 0f
        set(value) { field = value.takeIf(Float::isFinite) ?: 0f; invalidate() }
    var viewportRotationDegrees: Float = 0f
        set(value) { field = ViewportTransform.normalizeDegrees(value.takeIf(Float::isFinite) ?: 0f); invalidate() }
    var inkColor: Int = Color.rgb(17, 17, 17)
    var secondaryInkColor: Int = Color.WHITE
    var baseWidth: Float = 5f
        set(value) {
            field = value
            if (hoverCursorVisible && !hoverCursorUsesEraser) invalidateInternalHoverCursor()
            onHoverCursorStyleChanged?.invoke()
        }
    var brushSizeSyncViewScale: Boolean = false
        set(value) {
            field = value
            if (hoverCursorVisible) invalidateInternalHoverCursor()
            onHoverCursorStyleChanged?.invoke()
        }
    private var gestureBrushSize: Float = 5f
    private var gestureEraserSize: Float = 24f
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
    var alphaLockedLayers: Set<String> = emptySet()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    var alphaLockBaseContentIds: Map<String, Set<String>> = emptyMap()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    var clippedLayers: Set<String> = emptySet()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    var layerMasks: Map<String, LayerMask> = emptyMap()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    var layerOrder: List<String> = emptyList()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    private var layerElementBitmaps: Map<String, LayerElementBitmaps> = emptyMap()
    var pagePaperColor: Int? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var pagePaperPattern: PaperPattern? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var tipAssetPath: String? = null
    var tipAssetPaths: List<String> = emptyList()
    var dualTipAssetPaths: List<String> = emptyList()
    var dualGrainAssetPath: String? = null
    var dualGrainDensity: Float = 0f
    var dualGrainDensityMinimum: Float = 0f
    var dualGrainDensityDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var dualGrainScale: Float = 100f
    var dualGrainMovement: Float = 0f
    var dualGrainZoom: Float = 0f
    var dualGrainRotationDegrees: Float = 0f
    var dualGrainStrokeRotation: Float = 0f
    var dualGrainBrightness: Float = 0f
    var dualGrainContrast: Float = 0f
    var dualGrainReversed: Boolean = false
    var dualGrainEmphasized: Boolean = false
    var dualGrainOffsetJitter: Boolean = false
    var dualGrainCompositeMode: Int = CspTextureComposite.MULTIPLY
    var dualGrainApplyPerPlot: Boolean = true
    var dualGrainFilteringMode: Int = ProcreateFiltering.NONE
    var grainAssetPath: String? = null
    var grainDensity: Float = 0f
    var grainDensityMinimum: Float = 0f
    var grainDensityDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var grainScale: Float = 100f
    var grainMovement: Float = 0f
    var grainZoom: Float = 0f
    var grainRotationDegrees: Float = 0f
    var grainStrokeRotation: Float = 0f
    var grainBrightness: Float = 0f
    var grainContrast: Float = 0f
    var grainReversed: Boolean = false
    var grainEmphasized: Boolean = false
    var grainOffsetJitter: Boolean = false
    var grainCompositeMode: Int = CspTextureComposite.MULTIPLY
    var grainApplyPerPlot: Boolean = true
    var grainFilteringMode: Int = ProcreateFiltering.NONE
    var procreateRenderingMode: ProcreateRenderingMode = ProcreateRenderingMode.UNIFORM_GLAZE
    var procreateBrushBlendMode: Int = ProcreateBrushBlend.NORMAL
    var procreateBurntEdgeBlendMode: Int = ProcreateBrushBlend.MULTIPLY
    var procreateLuminanceBlending: Boolean = false
    var alphaThresholdEnabled: Boolean = false
    var alphaThreshold: Float = 0f
    var brushSpacing: Float = .12f
    var strokeFalloff: Float = 0f
    var strokePathJitter: Float = 0f
    var strokePathLongitudinalJitter: Float = 0f
    var brushSpacingMode: Int = CspBrushGap.FIXED
    var brushFlow: Float = 1f
    var brushHardness: Float = 1f
    var brushAntiAliasMode: Int = CspAntiAlias.MEDIUM
    var brushBlur: Float = 0f
    var brushBlurUnit: Int = 0
    var brushBlurLinkedToSize: Boolean = false
    var tiltGradation: Float = 0f
    var tiltGradationStart: Float = 0f
    var tiltBleed: Float = 0f
    var tiltBleedStart: Float = 0f
    var tiltSizeCompression: Boolean = false
    var brushThickness: Float = 1f
    var brushThicknessVertical: Boolean = false
    var shapeRoundnessHorizontalJitter: Float = 0f
    var shapeRoundnessVerticalJitter: Float = 0f
    var brushRotation: Float = 0f
    var shapeTouchRotation: Float = 0f
    var shapeOrientToScreen: Boolean = false
    var followStylusOrientation: Boolean = false
    var followStrokeDirection: Boolean = false
    var randomRotationDegrees: Float = 0f
    var strokeRotationJitterDegrees: Float = 0f
    var stampCount: Int = 1
    var stampCountJitter: Float = 0f
    var brushContinuousPlot: Boolean = false
    var colorJitterHueDegrees: Float = 0f
    var colorJitterSaturation: Float = 0f
    var colorJitterValue: Float = 0f
    var colorJitterTarget: Int = 0
    var colorJitterPerTipEnabled: Boolean = true
    var colorJitterHueDynamics: BrushColorDynamics = BrushColorDynamics()
    var colorJitterSaturationDynamics: BrushColorDynamics = BrushColorDynamics()
    var colorJitterValueDynamics: BrushColorDynamics = BrushColorDynamics()
    var colorJitterSubColorMix: Float = 0f
    var colorJitterSubColorMixDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var colorJitterPerStrokeEnabled: Boolean = false
    var colorJitterStrokeHueDegrees: Float = 0f
    var colorJitterStrokeSaturation: Float = 0f
    var colorJitterStrokeValue: Float = 0f
    var colorJitterStrokeSubColorMix: Float = 0f
    var procreateColorDynamics: ProcreateColorDynamics = ProcreateColorDynamics()
    var procreateWetMix: ProcreateWetMix = ProcreateWetMix()
    var procreateTaper: ProcreateTaper = ProcreateTaper()
    var brushWatercolor: Boolean = false
    var cspColorMixMode: Int = CspColorMixMode.BLEND
    var brushColorMix: Float = 0f
    var colorMixPressureEnabled: Boolean = false
    var colorMixPressureMinimum: Float = 0f
    var colorMixPressureCurve: List<BrushCurvePoint> = emptyList()
    var colorMixSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var brushPaintMix: Float = 0f
    var paintMixPressureEnabled: Boolean = false
    var paintMixPressureMinimum: Float = 0f
    var paintMixPressureCurve: List<BrushCurvePoint> = emptyList()
    var paintMixSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var colorStretch: Float = 0f
    var cspPigmentMixingMode: Int = CspPigmentMixingMode.STANDARD
    var cspBrightnessCorrection: Int = CspBrightnessCorrection.NEUTRAL
    var colorMixBlur: Float = 0f
    var colorMixBlurUnit: Int = 0
    var colorMixBlurAutomatic: Boolean = true
    var colorMixBlurDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var brushSpray: Boolean = false
    var sprayParticleSize: Float = 100f
    var sprayParticleSizeUnit: Int = 0
    var sprayParticleSizeSyncBrushSize: Boolean = true
    var sprayParticleSizePressureEnabled: Boolean = false
    var sprayParticleSizePressureMinimum: Float = 0f
    var sprayParticleSizePressureCurve: List<BrushCurvePoint> = emptyList()
    var sprayParticleSizeSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var sprayDensity: Float = 1f
    var sprayDensityPressureEnabled: Boolean = false
    var sprayDensityPressureMinimum: Float = 0f
    var sprayDensityPressureCurve: List<BrushCurvePoint> = emptyList()
    var sprayDensitySecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
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
    var dualSizeDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var dualSpacing: Float = .12f
    var dualSpacingMode: Int = CspBrushGap.FIXED
    var dualSpacingDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var dualFlow: Float = 1f
    var dualFlowDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var dualHardness: Float = 1f
    var dualAntiAliasMode: Int = CspAntiAlias.MEDIUM
    var dualThickness: Float = 1f
    var dualShapeRoundnessHorizontalJitter: Float = 0f
    var dualShapeRoundnessVerticalJitter: Float = 0f
    var dualThicknessDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var dualThicknessVertical: Boolean = false
    var dualRotationDegrees: Float = 0f
    var dualShapeTouchRotation: Float = 0f
    var dualShapeOrientToScreen: Boolean = false
    var dualFollowStylusOrientation: Boolean = false
    var dualFollowStrokeDirection: Boolean = false
    var dualRandomRotationDegrees: Float = 0f
    var dualStrokeRotationJitterDegrees: Float = 0f
    var dualStampCount: Int = 1
    var dualStampCountJitter: Float = 0f
    var dualStrokePathJitter: Float = 0f
    var dualStrokePathLongitudinalJitter: Float = 0f
    var dualRibbon: Boolean = false
    var dualSpray: Boolean = false
    var dualSprayParticleSize: Float = 100f
    var dualSprayParticleSizeUnit: Int = 0
    var dualSprayParticleSizeSyncBrushSize: Boolean = true
    var dualSprayParticleSizeDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var dualSprayDensity: Float = 1f
    var dualSprayDensityDynamics: BrushParameterDynamics = BrushParameterDynamics()
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
    var blurPressureEnabled: Boolean = false
    var blurPressureMinimum: Float = 0f
    var blurPressureCurve: List<BrushCurvePoint> = emptyList()
    var blurSecondaryDynamics: BrushSecondaryDynamics = BrushSecondaryDynamics()
    var localEraser: Boolean = false
    var nonDestructiveEraser: Boolean = false
    var eraserWidth: Float = 24f
        set(value) {
            field = value
            if (hoverCursorVisible && hoverCursorUsesEraser) invalidateInternalHoverCursor()
            onHoverCursorStyleChanged?.invoke()
        }
    var eraserHardness: Float = 1f
    var eraserOpacity: Float = 1f
    var eraserPressureSize: Boolean = false
    var eraserTipAssetPath: String? = null
    var fingerDrawingEnabled: Boolean = false
    var singleFingerDoubleTapUndo: Boolean = false
        set(value) {
            field = value
            if (!value) singleFingerDoubleTapUndoGesture.cancel()
        }
    var twoFingerDoubleTapUndo: Boolean = true
    var threeFingerTapRedo: Boolean = true
    var twoFingerRotationEnabled: Boolean = false
    var rulerEnabled: Boolean = false
        set(value) { field = value; if (!value) rulerGestureMode = null; invalidate() }
    var rulerGuide: RulerGuideState = RulerGuideState()
        set(value) { field = value.normalized(); invalidate() }
    var onRulerGuideChanged: (RulerGuideState) -> Unit = {}
    var canvasGuideState: CanvasGuideState = CanvasGuideState()
        set(value) { field = value.normalized(); if (!field.alignmentGuidesEnabled) canvasGuideGestureMode = null; invalidate() }
    var onCanvasGuideChanged: (CanvasGuideState) -> Unit = {}
    var perspectiveGuideState: PerspectiveGuideState = PerspectiveGuideState()
        set(value) {
            field = value.normalized()
            if (!field.editing || field.mode == PerspectiveGuideMode.NONE) perspectiveGuideGesturePoint = null
            invalidate()
        }
    var onPerspectiveGuideChanged: (PerspectiveGuideState) -> Unit = {}
    var specialRulerState: SpecialRulerState = SpecialRulerState()
        set(value) {
            field = value.normalized()
            if (!field.editing || field.mode == SpecialRulerMode.NONE) specialRulerGestureHandle = null
            invalidate()
        }
    var onSpecialRulerChanged: (SpecialRulerState) -> Unit = {}
    var pressureSensitivity: Float = 1f
    var pressureMinimumInput: Float = 0f
    var pressureMaximumInput: Float = 1f
    var pressureResponseCurve: List<Float> = app.inkloom.document.PressureResponseCurve.LINEAR
    var streamLine: Float = 0f
    var streamLinePressure: Float = 0f
    var procreateStabilization: Float = 0f
    var motionFiltering: Float = 0f
    var motionFilteringExpression: Float = 0f
    // Correction is opt-in. A fresh host must preserve the physical stylus trajectory even before
    // Compose has delivered the selected brush/settings configuration.
    var stabilization: Float = 0f
    var stabilizationBySpeed: Boolean = false
    var stabilizationBySpeedMode: Int = StrokeStabilization.REDUCE_WHEN_FAST
    var postCorrection: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }
    var postCorrectionAdjustBySpeed: Boolean = false
    var postCorrectionAdjustByScale: Boolean = true
    var postCorrectionBezier: Boolean = false
    var correctionSharpAngles: Boolean = false
    var primaryButtonAction: StylusButtonAction = StylusButtonAction.ERASER
    var secondaryButtonAction: StylusButtonAction = StylusButtonAction.LASSO
    var primaryButtonTrigger: ShortcutTrigger = ShortcutTrigger.HOLD
    var secondaryButtonTrigger: ShortcutTrigger = ShortcutTrigger.HOLD
    var shapeKind: ShapeKind = ShapeKind.RECTANGLE
    var selectionShape: SelectionShape = SelectionShape.FREEFORM
    var selectionBrushSize: Float = 42f
        set(value) { field = value.coerceIn(4f, 300f); invalidate() }
    var selectionThreshold: Float = .25f
        set(value) { field = value.coerceIn(0f, 1f) }
    var selectionMode: SelectionMode = SelectionMode.REPLACE
    var selectionFilter: SelectionFilter = SelectionFilter.ALL
    var referenceTarget: ReferenceTarget = ReferenceTarget.ALL_VISIBLE
    var referenceLayerIds: Set<String> = emptySet()
    var draftLayerIds: Set<String> = emptySet()
    var liquifyMode: LiquifyMode = LiquifyMode.PUSH
    var liquifyStrength: Float = .55f
        set(value) { field = value.coerceIn(.01f, 1f) }
    var liquifyHardness: Float = .5f
        set(value) { field = value.coerceIn(0f, 1f) }
    var correctLineMode: CorrectLineMode = CorrectLineMode.SMOOTH
    var correctLineAmount: Float = .55f
        set(value) { field = value.coerceIn(0f, 1f) }
    var pageModelWidth: Float = 1600f
    var pageModelHeight: Float = 2263f
    var onStrokeCommitted: (InkStroke) -> Unit = {}
    var onShapeCommitted: (PageElement.Shape) -> Unit = {}
    var onGradientCommitted: (startX: Float, startY: Float, endX: Float, endY: Float, definition: GradientDefinition, selectionMask: Bitmap?) -> Unit = { _, _, _, _, _, mask -> mask?.recycle() }
    var gradientDefinition: GradientDefinition = GradientDefinition(stops = listOf(GradientColorStop(0f, 0xFF111111L), GradientColorStop(1f, 0xFFFFFFFFL)))
    var gradientAngleSnapEnabled: Boolean = false
    var gradientAngleSnapDegrees: Float = 45f
    var editableGradient: PageElement.Shape? = null
    var onGradientHandleChanged: (PageElement.Shape) -> Unit = {}
    private var gradientGestureHandle: Int = -1
    private var gradientGestureStart: PageElement.Shape? = null
    /** Paint-bucket fill: receives the fill mask bitmap (page-local pixels) plus the
     *  mask's bounding box in normalized page coordinates, the fill color and opacity.
     *  The host persists the mask and creates a [PageElement.Fill]. */
    var onFillCommitted: ((mask: Bitmap, x: Float, y: Float, width: Float, height: Float, colorArgb: Long, opacity: Float) -> Unit)? = null
    /** The callback owns the immutable dirty patch and must close it after saving. */
    var onRasterLayerCommitted: ((RasterLayerPatch) -> Unit)? = null

    private fun mergeDirtyBounds(current: Rect?, changed: Rect): Rect =
        current?.apply { union(changed) } ?: Rect(changed)

    private fun commitRasterLayerPatch(
        surface: PixelTileSurface,
        dirtyBounds: Rect?,
    ) {
        val dirty = dirtyBounds ?: return
        val patch = RasterLayerPatch.capture(activeLayerId, surface.snapshot(), dirty) ?: return
        val callback = onRasterLayerCommitted
        if (callback == null) patch.close() else callback(patch)
    }
    var onColorDropCommitted: (Int) -> Unit = {}
    var onSelectionActiveChanged: (Boolean) -> Unit = {}
        set(value) {
            field = value
            lastReportedSelectionActive = null
            notifySelectionActiveChanged()
        }
    private var continueFillingColor: Int? = null
    var fillSensitivity: Float = .25f
    var fillSubTool: FillSubTool = FillSubTool.BUCKET
    var fillCloseGapPixels: Float = 0f
        set(value) { field = value.coerceIn(0f, 64f) }
    var fillAreaScalingPixels: Float = 0f
        set(value) { field = value.coerceIn(-64f, 64f) }
    var symmetryMode: SymmetryMode = SymmetryMode.NONE
    var onStrokeRemoved: (String) -> Unit = {}
    var onStrokesReplaced: (List<String>, List<InkStroke>, List<Int>) -> Unit = { removed, replacements, _ -> removed.forEach(onStrokeRemoved); replacements.forEach(onStrokeCommitted) }
    var onViewportScale: (Float, Float, Float) -> Unit = { _, _, _ -> }
    var onViewportPan: (Float, Float) -> Unit = { _, _ -> }
    var onViewportPinch: ((Float, Float, Float, Float, Float) -> Unit)? = null
    var onViewportRotate: (Float) -> Unit = {}
    var pdfGotoLinks: List<PdfGotoLink> = emptyList()
    var onPdfGotoLink: (PdfGotoLink) -> Unit = {}
    var onUndoGesture: () -> Unit = {}
    var onRedoGesture: () -> Unit = {}
    var onColorSampled: (Int) -> Unit = {}
    var onBrushSizeAdjusted: (Float) -> Unit = {}
    var onInputDiagnostic: (String, Float, Float, Int) -> Unit = { _, _, _, _ -> }
    var onKeyInputDiagnostic: (KeyInputDiagnostic) -> Unit = {}
    var onMotionInputDiagnostic: (MotionInputDiagnostic) -> Unit = {}
    var onShortcutAction: (ShortcutActivation) -> Unit = {}
    var shortcutBindings: List<ShortcutBinding> = CspShortcutResolver.defaultBindings
    var shortcutAxisBindings: List<AxisShortcutBinding> = emptyList()
    var shortcutButtonBindings: List<ButtonShortcutBinding> = emptyList()
    private val shortcutAxisAccumulator = ShortcutAxisAccumulator()
    private val heldMotionButtons = mutableMapOf<Pair<String?, Int>, ButtonShortcutBinding>()

    private val backgroundDestination = android.graphics.Rect()
    private val lassoPath = Path()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        filterTouchesWhenObscured = false
        setOnDragListener { _, drag ->
            val payload = drag.localState as? CanvasColorDrop ?: return@setOnDragListener false
            when (drag.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    inkColor = payload.color
                    beginFillGesture(
                        drag.x / width.coerceAtLeast(1).toFloat(),
                        drag.y / height.coerceAtLeast(1).toFloat(),
                        drag.x,
                    )
                    commitFillGesture()
                    onColorDropCommitted(payload.color)
                    true
                }
                else -> true
            }
        }
    }

    fun setContinueFilling(enabled: Boolean, color: Int = inkColor) {
        continueFillingColor = color.takeIf { enabled }
        if (!enabled) cancelFillGesture()
    }

    /** Partially obscured events are still valid canvas input. Android's window manager remains
     * responsible for blocking unsafe pass-through under a fully covering untrusted overlay. */
    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean = true

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            // Floating palettes and other multi-window applications can take window focus while
            // Android continues to deliver stylus input. Do not discard that valid stroke stream.
            stopContinuousPlot()
            laserTrail.clear()
            rulerGestureStart?.let { rulerGuide = it }
            rulerGestureMode = null
            rulerGestureStart = null
            canvasGuideGestureStart?.let { canvasGuideState = it }
            canvasGuideGestureMode = null
            canvasGuideGestureStart = null
            activeCanvasSnapConstraint = null
            perspectiveGuideGestureStart?.let { perspectiveGuideState = it }
            perspectiveGuideGesturePoint = null
            perspectiveGuideGestureStart = null
            activePerspectiveSnapConstraint = null
            specialRulerGestureStart?.let { specialRulerState = it }
            specialRulerGestureHandle = null
            specialRulerGestureStart = null
            activeSpecialRulerConstraint = null
            ctrlModifierHeld = false
            altModifierHeld = false
            singleFingerDoubleTapUndoGesture.cancel()
            twoFingerTapUndoGesture.cancel()
            threeFingerTapRedoGesture.cancel()
            twoPointerRotationGesture.reset()
            shortcutAxisAccumulator.clear()
            heldMotionButtons.values.forEach { binding ->
                ToolboxMotionResolver.buttonActivation(binding, ShortcutPhase.RELEASE)?.let(onShortcutAction)
            }
            heldMotionButtons.clear()
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
            releaseSelectionState(clearHistory = true)
        }
        if (strokes != value) {
            editHistory.clear()
            strokes.clear()
            strokes.addAll(value)
            val selectionChanged = selectedStrokeIds.retainAll(value.mapTo(mutableSetOf()) { it.id })
            if (selectionChanged && selectedMaskRegions.isEmpty()) {
                syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
            }
            invalidateStrokeCache()
        }
    }

    /** Binds selection alpha to the document runtime before any page content is installed. */
    internal fun bindDocumentSelectionState(state: DocumentSelectionState) {
        if (documentSelectionState === state) return
        documentSelectionState = state
        selectionAlphaMask = SparseAlphaMask.fromSurface(state.surface())
        rebuildSelectionDerivedState()
    }

    fun setProtectedStrokeIds(value: Set<String>) {
        protectedStrokeIds.clear()
        protectedStrokeIds.addAll(value)
        if (selectedStrokeIds.removeAll(value) && selectedMaskRegions.isEmpty()) {
            syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
        }
    }

    fun setProtectedLayerIds(value: Set<String>) {
        protectedLayerIds.clear()
        protectedLayerIds.addAll(value)
        val newlyProtected = strokes.filter { it.layerId in value }.mapTo(mutableSetOf(), InkStroke::id)
        if (selectedStrokeIds.removeAll(newlyProtected) && selectedMaskRegions.isEmpty()) {
            syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
        }
    }

    fun setPageBackground(bitmap: Bitmap?) { backgroundBitmap = bitmap; invalidate() }

    fun setLayerElementBitmaps(value: Map<String, LayerElementBitmaps>) {
        if (layerElementBitmaps != value) {
            layerElementBitmaps = value
            resetStrokeTiles()
        }
    }

    /** External mirrors exist only for the legacy/test adapter. Production supplies tile surfaces. */
    private var rasterLayerBitmaps: Map<String, Bitmap> = emptyMap()
    private val rasterLayerTileSurfaces = mutableMapOf<String, PixelTileSurface>()
    private val ownedRasterEditBitmaps = mutableMapOf<String, Bitmap>()
    private val rasterTileRenderer = PixelTileCanvasRenderer()
    private var rasterLayerProjectionCache: PixelLayerProjectionCache? = null
    private var rasterLayerProjectionGeometry: RasterProjectionGeometry? = null
    @VisibleForTesting internal var v3RasterProjectionDrawCount: Int = 0
    @VisibleForTesting internal var legacyLayerCompositionDrawCount: Int = 0
    @VisibleForTesting internal var lastRasterProjectionRecomposedTileCount: Int = 0

    fun setRasterLayerSurfaces(value: Map<String, PixelTileSurface>) {
        val changedLayerIds = (rasterLayerTileSurfaces.keys + value.keys).filterTo(mutableSetOf()) { layerId ->
            rasterLayerTileSurfaces[layerId] !== value[layerId]
        }
        if (changedLayerIds.isEmpty() && rasterLayerBitmaps.isEmpty()) return
        changedLayerIds.forEach { layerId ->
            ownedRasterEditBitmaps.remove(layerId)?.let { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            rasterLayerTileSurfaces[layerId]?.let { previous ->
                if (previous !== value[layerId]) rasterTileRenderer.releaseSurface(previous.surfaceId)
            }
        }
        rasterLayerBitmaps = emptyMap()
        rasterLayerTileSurfaces.clear()
        rasterLayerTileSurfaces.putAll(value)
        if (value.isEmpty()) releaseRasterLayerProjection()
        resetStrokeTiles()
    }

    /** One-way compatibility adapter for focused Android tests and legacy callers. */
    fun setRasterLayerBitmaps(value: Map<String, Bitmap>) {
        if (rasterLayerBitmaps != value) {
            val previous = rasterLayerBitmaps
            ownedRasterEditBitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            ownedRasterEditBitmaps.clear()
            rasterLayerTileSurfaces.keys.retainAll(value.keys)
            value.forEach { (layerId, bitmap) ->
                if (previous[layerId] !== bitmap || rasterLayerTileSurfaces[layerId] == null) {
                    rasterLayerTileSurfaces[layerId]?.let { rasterTileRenderer.releaseSurface(it.surfaceId) }
                    rasterLayerTileSurfaces[layerId] = importPixelTileSurface(bitmap)
                }
            }
            rasterLayerBitmaps = value
            releaseRasterLayerProjection()
            resetStrokeTiles()
        }
    }

    private fun rasterTileSurfaceFor(layerId: String): PixelTileSurface? = rasterLayerTileSurfaces[layerId]

    /** A mutable Android front buffer is retained only for the active raster layer. */
    private fun rasterEditBitmapFor(layerId: String): Bitmap? {
        rasterLayerBitmaps[layerId]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        ownedRasterEditBitmaps[layerId]?.takeUnless(Bitmap::isRecycled)?.let { return it }
        val surface = rasterLayerTileSurfaces[layerId] ?: return null
        val immutableProjection = PixelTileBitmapAdapter.render(surface.snapshot())
        return try {
            requireNotNull(immutableProjection.copy(Bitmap.Config.ARGB_8888, true))
                .also { ownedRasterEditBitmaps[layerId] = it }
        } finally {
            immutableProjection.recycle()
        }
    }

    private fun releaseOwnedRasterEditBitmap(layerId: String) {
        ownedRasterEditBitmaps.remove(layerId)?.let { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
    }

    /** Prewarms the sole mutable front buffer on layer activation, never on the first pen packet. */
    fun prepareRasterLayerForEditing(layerId: String) {
        ownedRasterEditBitmaps.keys.filter { it != layerId }.toList().forEach(::releaseOwnedRasterEditBitmap)
        rasterEditBitmapFor(layerId)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(renderLock) { drawPageContent(canvas) }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val pointerIndex = event.stylusPointerIndex()
        if (pointerIndex < 0) return super.onHoverEvent(event)
        val toolType = event.getToolType(pointerIndex)
        val stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (stylus) dispatchStylusButtonTransitions(updateStylusButtonState(event.buttonState))
        val cursorTool = if (toolType == MotionEvent.TOOL_TYPE_ERASER) EditorTool.ERASER else resolveTool(event, toolType)
        val drawableTool = cursorTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                if (!stylus || !drawableTool) {
                    if (hoverCursorVisible) { hoverCursorVisible = false; invalidateInternalHoverCursor() }
                    return stylus
                }
                val point = canvasPoint(event, pointerIndex)
                updateHoverCursorPacket(
                    x = point.first,
                    y = point.second,
                    pressure = 1f,
                    tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
                    orientationRadians = event.getOrientation(pointerIndex),
                    usesEraser = cursorTool == EditorTool.ERASER,
                    resetDirection = event.actionMasked == MotionEvent.ACTION_HOVER_ENTER || !hoverCursorVisible,
                )
                invalidateInternalHoverCursor()
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_CANCEL -> {
                if (hoverCursorVisible) { hoverCursorVisible = false; invalidateInternalHoverCursor() }
                return stylus
            }
        }
        return super.onHoverEvent(event)
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        val index = pointerIndex.coerceIn(0, event.pointerCount - 1)
        return if (event.getToolType(index) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)) {
            PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
        } else {
            super.onResolvePointerIcon(event, pointerIndex)
        }
    }

    private fun drawPageContent(canvas: Canvas, includeInteractionOverlays: Boolean = true, referencedLayerIds: Set<String>? = null) {
        backgroundBitmap?.let { bitmap -> backgroundDestination.set(0, 0, width, height); canvas.drawBitmap(bitmap, null, backgroundDestination, null) }
            ?: pagePaperColor?.let { color ->
                paperFillPaint.color = color
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paperFillPaint)
                pagePaperPattern?.let { drawPaperPattern(canvas, it) }
            }
        if (includeInteractionOverlays) drawSnapGrid(canvas)
        if (referencedLayerIds == null && drawStrokeTiles(canvas)) Unit else drawLayeredStrokes(
            canvas,
            if (referencedLayerIds == null) strokes else strokes.filter { it.layerId in referencedLayerIds },
            includeElements = true,
            layerFilter = referencedLayerIds,
        )
        if (
            includeInteractionOverlays &&
            usesViewActivePreview() &&
            activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) &&
            currentPoints.isNotEmpty()
        ) {
            drawLayeredStrokes(canvas, symmetryStrokes(currentPoints))
        }
        if (!includeInteractionOverlays) return
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
            val overlayScale = viewportScale.coerceAtLeast(.1f)
            lassoPath.reset()
            lassoPath.moveTo(selectionPreview.first().x * width, selectionPreview.first().y * height)
            for (index in 1 until selectionPreview.size) lassoPath.lineTo(selectionPreview[index].x * width, selectionPreview[index].y * height)
            if (selectionShape != SelectionShape.FREEFORM || selectionPreview.size >= 3) lassoPath.close()
            if (selectionPreview.size >= 3) canvas.drawPath(lassoPath, liveSelectionFillPaint)
            liveSelectionOutlinePaint.pathEffect = null
            liveSelectionOutlinePaint.color = Color.BLACK
            liveSelectionOutlinePaint.alpha = 255
            liveSelectionOutlinePaint.strokeWidth = 4f / overlayScale
            canvas.drawPath(lassoPath, liveSelectionOutlinePaint)
            liveSelectionOutlinePaint.color = Color.WHITE
            liveSelectionOutlinePaint.strokeWidth = 2f / overlayScale
            liveSelectionOutlinePaint.pathEffect = DashPathEffect(
                floatArrayOf(6f / overlayScale, 4f / overlayScale),
                selectionAntPhase() / overlayScale,
            )
            canvas.drawPath(lassoPath, liveSelectionOutlinePaint)
            liveSelectionOutlinePaint.pathEffect = null
            val originX = selectionPreview.first().x * width
            val originY = selectionPreview.first().y * height
            liveSelectionOriginPaint.color = Color.BLACK
            canvas.drawCircle(originX, originY, 9f / overlayScale, liveSelectionOriginPaint)
            liveSelectionOriginPaint.color = Color.WHITE
            canvas.drawCircle(originX, originY, 4.5f / overlayScale, liveSelectionOriginPaint)
            postInvalidateDelayed(SELECTION_ANTS_FRAME_MS)
        }
        drawCommittedSelectionBoundary(canvas)
        drawSelectionHandles(canvas)
        drawSymmetryGuides(canvas)
        drawShapePreview(canvas)
        drawAlignmentGuides(canvas)
        drawSpecialRulerGuide(canvas)
        drawPerspectiveGuide(canvas)
        drawRulerGuide(canvas)
        drawLaserTrail(canvas, SystemClock.uptimeMillis())
        drawFillPreview(canvas)
        drawGradientPreview(canvas)
        drawGradientHandles(canvas)
        drawBrushSizePreview(canvas)
        drawStylusHoverCursor(canvas)
    }

    private fun selectionAntPhase(): Float =
        ((SystemClock.uptimeMillis() / SELECTION_ANTS_FRAME_MS) % selectionAntPattern.width).toFloat()

    private fun drawCommittedSelectionBoundary(canvas: Canvas) {
        val boundary = selectionBoundaryMask ?: return
        selectionBoundaryRect.set(0f, 0f, width.toFloat(), height.toFloat())
        if (tool == EditorTool.SELECT) {
            selectionDisplayMask?.let { selected ->
                val protectedCheckpoint = canvas.saveLayer(selectionBoundaryRect, null)
                selectionProtectedMatrix.reset()
                selectionProtectedMatrix.setTranslate(selectionAntPhase() * .5f, selectionAntPhase() * .5f)
                selectionProtectedShader.setLocalMatrix(selectionProtectedMatrix)
                canvas.drawRect(selectionBoundaryRect, selectionProtectedPaint)
                selected.draw(canvas, selectionBoundaryRect, selectionCutoutPaint)
                canvas.restoreToCount(protectedCheckpoint)
            }
        }
        boundary.draw(canvas, selectionBoundaryRect, selectionBoundaryPaint)

        val checkpoint = canvas.saveLayer(selectionBoundaryRect, null)
        boundary.draw(canvas, selectionBoundaryRect, selectionMaskPaint)
        selectionAntMatrix.reset()
        selectionAntMatrix.setTranslate(selectionAntPhase(), 0f)
        selectionAntShader.setLocalMatrix(selectionAntMatrix)
        canvas.drawRect(selectionBoundaryRect, selectionAntPaint)
        canvas.restoreToCount(checkpoint)
        postInvalidateDelayed(SELECTION_ANTS_FRAME_MS)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            releaseActiveStrokePreview()
            resetStrokeTiles()
        }
    }

    override fun onDetachedFromWindow() {
        if (activeTool != null) cancelActiveGestureForNavigation()
        releaseHeldStylusButtons()
        lastStylusButtonState = 0
        stopContinuousPlot()
        laserTrail.clear()
        recycleStrokeTiles()
        releaseActiveStrokePreview()
        releaseSelectionState(clearHistory = true)
        editHistory.clear()
        removeCallbacks(activeSurfaceFrame)
        activeSurfaceFrameScheduled = false
        strokeInputModeler.close()
        brushResources.close()
        brushResources = BrushResourceCache()
        wholeStrokeTextureBitmap?.recycle(); wholeStrokeTextureBitmap = null
        activeWholeTextureCoverageBitmap?.recycle(); activeWholeTextureCoverageBitmap = null
        ownedRasterEditBitmaps.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        ownedRasterEditBitmaps.clear()
        releaseRasterLayerProjection()
        rasterTileRenderer.close()
        super.onDetachedFromWindow()
    }

    private fun drawStrokeTiles(canvas: Canvas): Boolean {
        if (layerBlendModes.values.any { it != LayerBlendMode.NORMAL }) return false
        if (strokes.any(::usesDestinationDependentBrushBlend)) return false
        if (alphaLockedLayers.isNotEmpty() || clippedLayers.isNotEmpty() || layerMasks.isNotEmpty()) return false
        if (layerElementBitmaps.isNotEmpty() || rasterLayerTileSurfaces.isNotEmpty()) return false
        return committedScene.draw(
            canvas,
            strokes,
            width,
            height,
            independentStrokes = canAppendCommittedInkToTiles(),
        ) { target, candidates ->
            drawLayeredStrokes(target, candidates)
        }
    }

    private fun resetStrokeTiles() {
        committedScene.reset(strokes, width, height)
        invalidate()
    }

    private fun recycleStrokeTiles() = committedScene.close()

    private fun invalidateStrokeCache() = resetStrokeTiles()

    private fun invalidateStrokeCache(before: Collection<InkStroke>, after: Collection<InkStroke>) {
        committedScene.invalidate(
            before,
            after,
            strokes,
            width,
            height,
            appendCompatible = canAppendCommittedInkToTiles(),
        ) { target, candidates ->
            drawLayeredStrokes(target, candidates)
        }
        invalidate()
    }

    /** Normal, fully opaque layers are associative, so newly committed ink can be composed once
     * into clean tiles instead of replaying every earlier stroke before the next pen contact. */
    private fun canAppendCommittedInkToTiles(): Boolean =
        layerBlendModes.values.all { it == LayerBlendMode.NORMAL } &&
            strokes.none(::usesDestinationDependentBrushBlend) &&
            layerOpacities.values.all { it >= .999f } &&
            alphaLockedLayers.isEmpty() && clippedLayers.isEmpty() && layerMasks.isEmpty() &&
            layerElementBitmaps.isEmpty() && rasterLayerTileSurfaces.isEmpty()

    private fun drawLayeredStrokes(
        canvas: Canvas,
        value: List<InkStroke>,
        primaryStampStartIndex: Int = 0,
        dualStampStartIndex: Int = 0,
        includeElements: Boolean = false,
        layerFilter: Set<String>? = null,
    ) {
        if (drawV3RasterLayerProjection(canvas, value, includeElements, layerFilter)) return
        legacyLayerCompositionDrawCount++
        drawLegacyMixedLayerComposition(
            canvas,
            value,
            primaryStampStartIndex,
            dualStampStartIndex,
            includeElements,
            layerFilter,
        )
    }

    /**
     * Compatibility boundary for scenes that still require Android Canvas layer replay: vector
     * strokes/elements, the legacy Bitmap test adapter, and document masks that have not yet been
     * rasterized into a V3 alpha surface. Pure raster documents never enter this path.
     */
    private fun drawLegacyMixedLayerComposition(
        canvas: Canvas,
        value: List<InkStroke>,
        primaryStampStartIndex: Int,
        dualStampStartIndex: Int,
        includeElements: Boolean,
        layerFilter: Set<String>?,
    ) {
        val groupedStrokes = value.groupBy(InkStroke::layerId)
        val elementLayerIds = if (includeElements) layerElementBitmaps.keys.filter { layerFilter == null || it in layerFilter } else emptyList()
        val rasterLayerIds = rasterLayerTileSurfaces.keys.filter { layerFilter == null || it in layerFilter }
        val availableLayerIds = groupedStrokes.keys + elementLayerIds + rasterLayerIds
        val orderedLayerIds = (layerOrder.filter { it in availableLayerIds } + availableLayerIds.filterNot { it in layerOrder }).distinct()
        val compositionBounds = RectF(canvas.clipBounds)
        if (compositionBounds.isEmpty) return
        var previousLayerId: String? = null
        orderedLayerIds.forEach { layerId ->
            val layerStrokes = groupedStrokes[layerId].orEmpty()
            val opacity = (layerOpacities[layerId] ?: 1f).coerceIn(0f, 1f)
            if (opacity <= 0f) return@forEach
            val layerBlendMode = layerBlendModes[layerId] ?: LayerBlendMode.NORMAL
            val clipped = layerId in clippedLayers
            val lowerLayerId = previousLayerId
            if (!clipped || lowerLayerId != null) {
                val hasEnabledMask = layerMasks[layerId]?.enabled == true
                if (opacity >= .999f && layerBlendMode == LayerBlendMode.NORMAL && !clipped && !hasEnabledMask) {
                    // Normal CSP-style paint layers are the dominant live path. Allocating a
                    // full-canvas saveLayer here made every front-buffer packet pay an offscreen
                    // composition cost even though direct drawing is pixel-identical.
                    drawRawLayerContent(canvas, layerId, layerStrokes, includeElements, primaryStampStartIndex, dualStampStartIndex)
                    previousLayerId = layerId
                    return@forEach
                }
                val checkpoint = canvas.saveLayer(compositionBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (opacity * 255f).toInt().coerceIn(0, 255)
                    this.blendMode = layerBlendMode.toBlendMode()
                })
                drawRawLayerContent(canvas, layerId, layerStrokes, includeElements, primaryStampStartIndex, dualStampStartIndex)
                layerMasks[layerId]?.let { mask ->
                    val maskCheckpoint = canvas.saveLayer(compositionBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    LayerMaskRenderer.draw(canvas, mask, width, height)
                    canvas.restoreToCount(maskCheckpoint)
                }
                if (clipped && lowerLayerId != null) {
                    val maskCheckpoint = canvas.saveLayer(compositionBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    drawRawLayerContent(canvas, lowerLayerId, groupedStrokes[lowerLayerId].orEmpty(), includeElements, primaryStampStartIndex, dualStampStartIndex)
                    canvas.restoreToCount(maskCheckpoint)
                }
                canvas.restoreToCount(checkpoint)
            }
            previousLayerId = layerId
        }
    }

    /** Production V3 path for paint documents whose visible content is entirely sparse raster
     * layers. Layer opacity, blend modes, and clipping are resolved once per dirty tile; HWUI then
     * draws the immutable projection without allocating a full-canvas saveLayer. */
    private fun drawV3RasterLayerProjection(
        canvas: Canvas,
        value: List<InkStroke>,
        includeElements: Boolean,
        layerFilter: Set<String>?,
    ): Boolean {
        if (!includeElements || layerFilter != null || value.isNotEmpty()) return false
        if (layerElementBitmaps.isNotEmpty() || rasterLayerBitmaps.isNotEmpty()) return false
        if (rasterLayerTileSurfaces.isEmpty()) return false
        if (layerMasks.any { (layerId, mask) -> layerId in rasterLayerTileSurfaces && mask.enabled }) return false

        val orderedIds = (
            layerOrder.filter(rasterLayerTileSurfaces::containsKey) +
                rasterLayerTileSurfaces.keys.filterNot(layerOrder::contains)
            ).distinct()
        if (orderedIds.isEmpty()) return false
        val firstSurface = rasterLayerTileSurfaces.getValue(orderedIds.first())
        val geometry = RasterProjectionGeometry(
            firstSurface.logicalWidth,
            firstSurface.logicalHeight,
            firstSurface.tileSize,
            firstSurface.pixelFormat,
        )
        if (orderedIds.any { layerId ->
                val surface = rasterLayerTileSurfaces.getValue(layerId)
                surface.logicalWidth != geometry.width || surface.logicalHeight != geometry.height ||
                    surface.tileSize != geometry.tileSize || surface.pixelFormat != geometry.pixelFormat
            }) return false

        var cache = rasterLayerProjectionCache
        if (cache == null || rasterLayerProjectionGeometry != geometry) {
            releaseRasterLayerProjection()
            cache = PixelLayerProjectionCache(
                logicalWidth = geometry.width,
                logicalHeight = geometry.height,
                tileSize = geometry.tileSize,
                pixelFormat = geometry.pixelFormat,
                surfaceId = "live-raster-projection-${System.identityHashCode(this)}",
            )
            rasterLayerProjectionCache = cache
            rasterLayerProjectionGeometry = geometry
        }
        val update = cache.update(orderedIds.map { layerId ->
            PixelLayerProjectionInput(
                layerId = layerId,
                pixels = rasterLayerTileSurfaces.getValue(layerId).snapshot(),
                opacity = layerOpacities[layerId] ?: 1f,
                blendMode = (layerBlendModes[layerId] ?: LayerBlendMode.NORMAL).toPixelBlendMode(),
                clipToBaseBelow = layerId in clippedLayers,
            )
        })
        lastRasterProjectionRecomposedTileCount = update.recomposedTileCount
        v3RasterProjectionDrawCount++
        rasterTileRenderer.draw(
            canvas,
            update.snapshot,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
        )
        return true
    }

    private fun releaseRasterLayerProjection() {
        rasterLayerProjectionCache?.snapshot?.surfaceId?.let(rasterTileRenderer::releaseSurface)
        rasterLayerProjectionCache = null
        rasterLayerProjectionGeometry = null
        lastRasterProjectionRecomposedTileCount = 0
    }

    private fun drawRawLayerContent(
        canvas: Canvas,
        layerId: String,
        layerStrokes: List<InkStroke>,
        includeElements: Boolean,
        primaryStampStartIndex: Int,
        dualStampStartIndex: Int,
    ) {
        val elements = if (includeElements) layerElementBitmaps[layerId] else null
        // The mutable full-page bitmap is an editing/sampling workspace only. Production display
        // truth is the sparse surface, so a committed generation cannot diverge from what is shown.
        val raster = rasterLayerBitmaps[layerId]
        val rasterSurface = rasterLayerTileSurfaces[layerId]
        val baseIds = alphaLockBaseContentIds[layerId].orEmpty()
        val hasCapturedAlpha = layerId in alphaLockedLayers && baseIds.isNotEmpty()
        if (!hasCapturedAlpha) {
            drawRasterLayer(canvas, rasterSurface, raster)
            layerStrokes.forEach { drawStroke(canvas, it, primaryStampStartIndex, dualStampStartIndex) }
            drawElementBitmap(canvas, elements?.base)
            drawElementBitmap(canvas, elements?.additions)
            return
        }
        val baseStrokes = layerStrokes.filter { it.id in baseIds }
        val addedStrokes = layerStrokes.filterNot { it.id in baseIds }
        fun drawBase() {
            drawRasterLayer(canvas, rasterSurface, raster)
            baseStrokes.forEach { drawStroke(canvas, it, primaryStampStartIndex, dualStampStartIndex) }
            drawElementBitmap(canvas, elements?.base)
        }
        drawBase()
        if (addedStrokes.isNotEmpty() || elements?.additions != null) {
            val compositionBounds = RectF(canvas.clipBounds)
            val additionsCheckpoint = canvas.saveLayer(compositionBounds, null)
            addedStrokes.forEach { drawStroke(canvas, it, primaryStampStartIndex, dualStampStartIndex) }
            drawElementBitmap(canvas, elements?.additions)
            val maskCheckpoint = canvas.saveLayer(compositionBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
            drawBase()
            canvas.restoreToCount(maskCheckpoint)
            canvas.restoreToCount(additionsCheckpoint)
        }
    }

    private fun drawElementBitmap(canvas: Canvas, bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        backgroundDestination.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, backgroundDestination, null)
    }

    private fun drawRasterLayer(canvas: Canvas, surface: PixelTileSurface?, editBitmap: Bitmap?) {
        if (editBitmap != null && !editBitmap.isRecycled) {
            drawElementBitmap(canvas, editBitmap)
            return
        }
        surface ?: return
        rasterTileRenderer.draw(canvas, surface.snapshot(), RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }

    private data class RasterProjectionGeometry(
        val width: Int,
        val height: Int,
        val tileSize: Int,
        val pixelFormat: PixelTileFormat,
    )

    private fun beginActiveStrokePreview() {
        removeCallbacks(activeSurfaceFrame)
        activeSurfaceFrameScheduled = false
        activeSurfaceCommitted = false
        activeStrokeSurface?.cancel()
        activePrimaryStampCounts.clear()
        activeDualStampCounts.clear()
        clearActiveStampRandoms()
    }

    private fun finishActiveStrokePreview() {
        removeCallbacks(activeSurfaceFrame)
        activeSurfaceFrameScheduled = false
        if (!activeSurfaceCommitted) activeStrokeSurface?.cancel()
        activeSurfaceCommitted = false
        activeStrokeTemplate = null
    }

    private fun releaseActiveStrokePreview() {
        if (rasterPaintSession != null) cancelWetPaintGesture()
        removeCallbacks(activeSurfaceFrame)
        activeSurfaceFrameScheduled = false
        activeSurfaceCommitted = false
        activeStrokeSurface?.cancel()
        activeStrokeTemplate = null
        activePrimaryStampCounts.clear()
        activeDualStampCounts.clear()
        clearActiveStampRandoms()
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke, primaryStampStartIndex: Int = 0, dualStampStartIndex: Int = 0) {
        val brushBlendMode = ProcreateBrushBlend.normalize(stroke.procreateBrushBlendMode)
        val compositeCheckpoint = if (stroke.opacity < .999f || brushBlendMode != ProcreateBrushBlend.NORMAL) {
            canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (stroke.opacity.coerceIn(0f, 1f) * 255).toInt()
                blendMode = ProcreateBrushBlend.resolve(brushBlendMode)
            })
        } else -1
        if (stroke.masks.isEmpty()) {
            drawStrokeContent(canvas, stroke, primaryStampStartIndex, dualStampStartIndex)
            if (compositeCheckpoint >= 0) canvas.restoreToCount(compositeCheckpoint)
            resetAntiAliasPaint()
            return
        }
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        drawStrokeContent(canvas, stroke, primaryStampStartIndex, dualStampStartIndex)
        StrokeMaskRenderer.draw(canvas, paint, stroke.masks, width, height)
        canvas.restoreToCount(checkpoint)
        if (compositeCheckpoint >= 0) canvas.restoreToCount(compositeCheckpoint)
        resetAntiAliasPaint()
    }

    private fun resetAntiAliasPaint() {
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
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

    private fun drawStrokeContent(
        canvas: Canvas,
        stroke: InkStroke,
        primaryStampStartIndex: Int = 0,
        dualStampStartIndex: Int = 0,
    ) {
        if (stroke.points.isEmpty()) return
        if (stroke.grainAssetPath != null && stroke.grainDensity > 0f && !stroke.grainApplyPerPlot &&
            drawWholeStrokeTexture(canvas, stroke, primaryStampStartIndex, dualStampStartIndex)
        ) return
        paint.isAntiAlias = CspAntiAlias.enabled(stroke.antiAliasMode)
        paint.isFilterBitmap = CspAntiAlias.enabled(stroke.antiAliasMode)
        val tipPaths = stroke.tipAssetPaths.ifEmpty { listOfNotNull(stroke.tipAssetPath) }
        val grainOffset = GrainBehavior.offset(stroke.renderSeed, stroke.grainOffsetJitter)
        val decodedTips = tipPaths.mapNotNull { loadTip(it, stroke.hardness, stroke.grainAssetPath, stroke.grainDensity, stroke.grainScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, grainOffset.x, grainOffset.y, stroke.grainCompositeMode, stroke.grainFilteringMode, stroke.antiAliasMode, stroke.alphaThresholdEnabled, stroke.alphaThreshold) }
        val tips = decodedTips.ifEmpty {
            if (!ProceduralBrushTip.supports(stroke)) emptyList()
            else {
                val proceduralKey = "${stroke.brushId}:${stroke.alphaThresholdEnabled}:${(stroke.alphaThreshold.coerceIn(0f, 1f) * 255f).toInt()}"
                listOfNotNull(brushResources.proceduralTip(proceduralKey) { ProceduralBrushTip.create(stroke) })
            }
        }
        if (tips.isNotEmpty()) {
            val dualGrainOffset = GrainBehavior.offset(stroke.renderSeed xor DUAL_GRAIN_SEED, stroke.dualGrainOffsetJitter)
            val dualTips = if (stroke.dualBrush) stroke.dualTipAssetPaths.mapNotNull { loadTip(it, stroke.dualHardness, stroke.dualGrainAssetPath.takeIf { _ -> stroke.dualGrainApplyPerPlot }, stroke.dualGrainDensity, stroke.dualGrainScale, stroke.dualGrainRotationDegrees, stroke.dualGrainBrightness, stroke.dualGrainContrast, stroke.dualGrainReversed, stroke.dualGrainEmphasized, dualGrainOffset.x, dualGrainOffset.y, stroke.dualGrainCompositeMode, stroke.dualGrainFilteringMode, stroke.dualAntiAliasMode, stroke.alphaThresholdEnabled, stroke.alphaThreshold, stroke.dualApplyRgb) }.ifEmpty { tipPaths.mapNotNull { loadTip(it, stroke.dualHardness, stroke.dualGrainAssetPath.takeIf { _ -> stroke.dualGrainApplyPerPlot }, stroke.dualGrainDensity, stroke.dualGrainScale, stroke.dualGrainRotationDegrees, stroke.dualGrainBrightness, stroke.dualGrainContrast, stroke.dualGrainReversed, stroke.dualGrainEmphasized, dualGrainOffset.x, dualGrainOffset.y, stroke.dualGrainCompositeMode, stroke.dualGrainFilteringMode, stroke.dualAntiAliasMode, stroke.alphaThresholdEnabled, stroke.alphaThreshold, stroke.dualApplyRgb) } } else emptyList()
            drawStampedStroke(canvas, stroke, tips, dualTips, tipPaths, grainOffset.x, grainOffset.y, primaryStampStartIndex, dualStampStartIndex)
            return
        }
        if (stroke.brushId == "system-ps19" && stroke.points.size > 1 && stroke.strokeFalloff <= .0001f) {
            drawContinuousRoundStroke(canvas, stroke)
            return
        }
        paint.color = stroke.colorArgb.toInt()
        paint.colorFilter = null
        val scale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val taper = strokeTaperFactors(stroke, stroke.points)
        val falloff = app.inkloom.document.StrokeFalloff.factors(stroke.points, width.toFloat(), height.toFloat(), stroke.width * scale, stroke.strokeFalloff)
        val contentClip = canvas.clipBounds
        if (stroke.points.size == 1) {
            val point = stroke.points.first(); paint.color = (point.pigmentArgb ?: stroke.colorArgb).toInt(); val dynamics = BrushProgramRuntime.evaluateDab(stroke, point); val strokeWidth = stroke.width * scale * (taper?.size?.firstOrNull() ?: 1f) * dynamics.size; val alpha = (ProcreateRendering.depositedAlpha(stroke.procreateRenderingMode, stroke.flow * dynamics.depositedAlpha * (falloff?.firstOrNull() ?: 1f) * (taper?.opacity?.firstOrNull() ?: 1f)) * 255).toInt().coerceIn(0, 255); drawLinePoint(canvas, stroke, point, point.x * width, point.y * height, strokeWidth, alpha)
        } else for (index in 0 until stroke.points.lastIndex) {
            val start = stroke.points[index]
            val end = stroke.points[index + 1]
            val point = midpoint(start, end)
            paint.color = mixArgb(start.pigmentArgb ?: stroke.colorArgb, end.pigmentArgb ?: stroke.colorArgb, .5f).toInt()
            val taperFactor = taper?.size?.let { (it[index] + it[index + 1]) / 2f } ?: 1f
            val taperOpacity = taper?.opacity?.let { (it[index] + it[index + 1]) / 2f } ?: 1f
            val dynamics = BrushProgramRuntime.evaluateDab(stroke, point)
            val strokeWidth = stroke.width * scale * taperFactor * dynamics.size
            val falloffFactor = falloff?.let { (it[index] + it[index + 1]) / 2f } ?: 1f
            val alpha = (ProcreateRendering.depositedAlpha(stroke.procreateRenderingMode, stroke.flow * dynamics.depositedAlpha * falloffFactor * taperOpacity) * 255).toInt().coerceIn(0, 255)
            val startX = start.x * width
            val startY = start.y * height
            val endX = end.x * width
            val endY = end.y * height
            val influenceRadius = strokeWidth + BrushBlur.radiusPixels(stroke.blur, stroke.blurUnit, stroke.blurLinkedToSize, strokeWidth, stroke.width * scale, width.toFloat()) +
                if (stroke.waterEdgeEnabled) WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat()) else 0f
            if (maxOf(startX, endX) + influenceRadius < contentClip.left || minOf(startX, endX) - influenceRadius > contentClip.right ||
                maxOf(startY, endY) + influenceRadius < contentClip.top || minOf(startY, endY) - influenceRadius > contentClip.bottom
            ) continue
            drawLineSegment(canvas, stroke, point, startX, startY, endX, endY, strokeWidth, alpha)
        }
    }

    /** CSP Texture with Apply by each plot disabled is a post-process over the union of all stamp
     * coverage. Rendering a textured tip repeatedly would make overlap regions progressively
     * darker, which is precisely the behavior the switch disables. */
    private fun drawWholeStrokeTexture(
        canvas: Canvas,
        stroke: InkStroke,
        primaryStampStartIndex: Int,
        dualStampStartIndex: Int,
    ): Boolean {
        if (incrementalActiveDraw && canIncrementallyReplaceWholeStrokeTexture(stroke)) {
            return drawIncrementalWholeStrokeTexture(canvas, stroke, primaryStampStartIndex, dualStampStartIndex)
        }
        val grainPath = stroke.grainAssetPath ?: return false
        val material = brushResources.grainMaterial(grainPath) ?: return false
        val strokeBounds = committedScene.pixelBoundsFor(stroke, width, height) ?: return false
        val clip = canvas.clipBounds
        val left = maxOf(strokeBounds.left, clip.left, 0)
        val top = maxOf(strokeBounds.top, clip.top, 0)
        val right = minOf(strokeBounds.right, clip.right, width)
        val bottom = minOf(strokeBounds.bottom, clip.bottom, height)
        if (right <= left || bottom <= top) return true
        val contentWidth = right - left
        val contentHeight = bottom - top
        val existing = wholeStrokeTextureBitmap
        val scratch = if (existing == null || existing.isRecycled || existing.width < contentWidth || existing.height < contentHeight) {
            existing?.recycle()
            runCatching {
                Bitmap.createBitmap(
                    maxOf(contentWidth, existing?.width ?: 0),
                    maxOf(contentHeight, existing?.height ?: 0),
                    Bitmap.Config.ARGB_8888,
                )
            }.getOrNull()?.also { wholeStrokeTextureBitmap = it }
        } else existing
        scratch ?: return false
        val scratchCanvas = Canvas(scratch)
        val checkpoint = scratchCanvas.save()
        scratchCanvas.clipRect(0, 0, contentWidth, contentHeight)
        scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        scratchCanvas.translate(-left.toFloat(), -top.toFloat())
        drawStrokeContent(
            scratchCanvas,
            stroke.copy(
                grainAssetPath = null,
                grainDensity = 0f,
                grainApplyPerPlot = true,
                alphaThresholdEnabled = false,
            ),
            primaryStampStartIndex,
            dualStampStartIndex,
        )
        scratchCanvas.restoreToCount(checkpoint)
        val grainOffset = GrainBehavior.offset(stroke.renderSeed, stroke.grainOffsetJitter)
        CspStrokeTextureCompositor.applyInPlace(
            strokeBitmap = scratch,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            originX = left,
            originY = top,
            pageWidth = width,
            pageHeight = height,
            material = material,
            density = stroke.grainDensity,
            scalePercent = stroke.grainScale,
            rotationDegrees = stroke.grainRotationDegrees,
            offsetX = grainOffset.x,
            offsetY = grainOffset.y,
            filteringMode = stroke.grainFilteringMode,
            brightness = stroke.grainBrightness,
            contrast = stroke.grainContrast,
            reversed = stroke.grainReversed,
            emphasized = stroke.grainEmphasized,
            compositeMode = stroke.grainCompositeMode,
            alphaThresholdEnabled = stroke.alphaThresholdEnabled,
            alphaThreshold = stroke.alphaThreshold,
        )
        canvas.drawBitmap(scratch, Rect(0, 0, contentWidth, contentHeight), Rect(left, top, right, bottom), null)
        return true
    }

    /** Maintains the untextured union for the active stroke and replaces only pixels touched by
     * newly plotted tips. This keeps CSP's per-stroke semantics without quadratically replaying the
     * entire history on every input packet. The front buffer persists between callbacks, so SRC is
     * intentional: a threshold/Outline result is allowed to become less opaque after union update. */
    private fun drawIncrementalWholeStrokeTexture(
        canvas: Canvas,
        stroke: InkStroke,
        primaryStampStartIndex: Int,
        dualStampStartIndex: Int,
    ): Boolean {
        val grainPath = stroke.grainAssetPath ?: return false
        val material = brushResources.grainMaterial(grainPath) ?: return false
        val program = stroke.copy(points = emptyList(), masks = emptyList())
        val existingCoverage = activeWholeTextureCoverageBitmap
        val coverage = if (existingCoverage == null || existingCoverage.isRecycled || existingCoverage.width < width || existingCoverage.height < height) {
            existingCoverage?.recycle()
            runCatching {
                Bitmap.createBitmap(
                    maxOf(width, existingCoverage?.width ?: 0),
                    maxOf(height, existingCoverage?.height ?: 0),
                    Bitmap.Config.ARGB_8888,
                )
            }.getOrNull()?.also { activeWholeTextureCoverageBitmap = it }
        } else existingCoverage
        coverage ?: return false
        val reset = activeWholeTextureStrokeId != stroke.id ||
            activeWholeTextureProgram != program ||
            activeWholeTexturePageWidth != width || activeWholeTexturePageHeight != height ||
            primaryStampStartIndex <= 0 || activeWholeTexturePointCount > stroke.points.size
        val coverageCanvas = Canvas(coverage)
        if (reset) {
            val clearCheckpoint = coverageCanvas.save()
            coverageCanvas.clipRect(0, 0, width, height)
            coverageCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            coverageCanvas.restoreToCount(clearCheckpoint)
        }
        val firstDirtyPoint = if (reset) 0 else (activeWholeTexturePointCount - 1).coerceIn(0, stroke.points.lastIndex)
        val dirtyStroke = if (firstDirtyPoint == 0) stroke else stroke.copy(points = stroke.points.subList(firstDirtyPoint, stroke.points.size))
        val dirty = committedScene.pixelBoundsFor(dirtyStroke, width, height) ?: return false
        val coverageCheckpoint = coverageCanvas.save()
        coverageCanvas.clipRect(dirty.left, dirty.top, dirty.right, dirty.bottom)
        drawStrokeContent(
            coverageCanvas,
            stroke.copy(
                grainAssetPath = null,
                grainDensity = 0f,
                grainApplyPerPlot = true,
                alphaThresholdEnabled = false,
            ),
            if (reset) 0 else primaryStampStartIndex,
            if (reset) 0 else dualStampStartIndex,
        )
        coverageCanvas.restoreToCount(coverageCheckpoint)
        activeWholeTextureStrokeId = stroke.id
        activeWholeTextureProgram = program
        activeWholeTexturePointCount = stroke.points.size
        activeWholeTexturePageWidth = width
        activeWholeTexturePageHeight = height

        val contentWidth = dirty.width
        val contentHeight = dirty.height
        val existingScratch = wholeStrokeTextureBitmap
        val scratch = if (existingScratch == null || existingScratch.isRecycled || existingScratch.width < contentWidth || existingScratch.height < contentHeight) {
            existingScratch?.recycle()
            runCatching {
                Bitmap.createBitmap(
                    maxOf(contentWidth, existingScratch?.width ?: 0),
                    maxOf(contentHeight, existingScratch?.height ?: 0),
                    Bitmap.Config.ARGB_8888,
                )
            }.getOrNull()?.also { wholeStrokeTextureBitmap = it }
        } else existingScratch
        scratch ?: return false
        val scratchCanvas = Canvas(scratch)
        val scratchCheckpoint = scratchCanvas.save()
        scratchCanvas.clipRect(0, 0, contentWidth, contentHeight)
        scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        scratchCanvas.drawBitmap(
            coverage,
            Rect(dirty.left, dirty.top, dirty.right, dirty.bottom),
            Rect(0, 0, contentWidth, contentHeight),
            null,
        )
        scratchCanvas.restoreToCount(scratchCheckpoint)
        val grainOffset = GrainBehavior.offset(stroke.renderSeed, stroke.grainOffsetJitter)
        CspStrokeTextureCompositor.applyInPlace(
            scratch, contentWidth, contentHeight, dirty.left, dirty.top, width, height, material,
            stroke.grainDensity, stroke.grainScale, stroke.grainRotationDegrees,
            grainOffset.x, grainOffset.y, stroke.grainFilteringMode,
            stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed,
            stroke.grainEmphasized, stroke.grainCompositeMode,
            stroke.alphaThresholdEnabled, stroke.alphaThreshold,
        )
        canvas.drawBitmap(
            scratch,
            Rect(0, 0, contentWidth, contentHeight),
            Rect(dirty.left, dirty.top, dirty.right, dirty.bottom),
            wholeStrokeReplacementPaint,
        )
        return true
    }

    private fun canIncrementallyReplaceWholeStrokeTexture(stroke: InkStroke): Boolean {
        val layerId = stroke.layerId
        return stroke.masks.isEmpty() && stroke.opacity >= .999f &&
            ProcreateBrushBlend.normalize(stroke.procreateBrushBlendMode) == ProcreateBrushBlend.NORMAL &&
            (layerOpacities[layerId] ?: 1f) >= .999f &&
            (layerBlendModes[layerId] ?: LayerBlendMode.NORMAL) == LayerBlendMode.NORMAL &&
            layerId !in clippedLayers && layerId !in alphaLockedLayers && layerMasks[layerId]?.enabled != true &&
            layerElementBitmaps.isEmpty() && rasterLayerTileSurfaces.isEmpty()
    }

    internal fun requiresCompleteActiveReplay(strokes: List<InkStroke>): Boolean = synchronized(renderLock) {
        val wholeTextureStrokes = strokes.filter { !it.grainApplyPerPlot && it.grainAssetPath != null && it.grainDensity > 0f }
        val dualStrokes = strokes.filter(InkStroke::dualBrush)
        wholeTextureStrokes.isNotEmpty() &&
            (strokes.size != 1 || wholeTextureStrokes.size != 1 || !canIncrementallyReplaceWholeStrokeTexture(wholeTextureStrokes.single())) ||
            dualStrokes.isNotEmpty() && (
                strokes.size != 1 ||
                    dualStrokes.single().opacity < .999f ||
                    ProcreateBrushBlend.normalize(dualStrokes.single().procreateBrushBlendMode) != ProcreateBrushBlend.NORMAL
                )
    }

    private fun drawStampedStroke(
        canvas: Canvas,
        stroke: InkStroke,
        tips: List<Bitmap>,
        dualTips: List<Bitmap>,
        tipPaths: List<String>,
        grainOffsetX: Float,
        grainOffsetY: Float,
        stampStartIndex: Int = 0,
        dualStampStartIndex: Int = 0,
    ) {
        if (stroke.dualBrush && dualTips.isNotEmpty()) {
            drawCspDualBrushStroke(
                canvas,
                stroke,
                tips,
                dualTips,
                tipPaths,
                grainOffsetX,
                grainOffsetY,
                stampStartIndex,
                dualStampStartIndex,
            )
            return
        }
        // A CSP Dual Brush combines its two stamp streams first, then deposits
        // that single result. Isolating the stroke prevents the secondary mode
        // from accidentally blending with older strokes or the page itself.
        val isolateStroke = stroke.blendTipsWithDarken || stroke.dualBrush && dualTips.isNotEmpty()
        val compositeLayer = if (isolateStroke) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) else -1
        paint.isFilterBitmap = CspAntiAlias.enabled(stroke.antiAliasMode)
        paint.blendMode = if (stroke.blendTipsWithDarken) BlendMode.DARKEN else null
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val sampledPoints = primaryStampedPoints(stroke)
        val falloff = app.inkloom.document.StrokeFalloff.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.width * referenceScale, stroke.strokeFalloff)
        val safeStart = stampStartIndex.coerceIn(0, sampledPoints.size)
        val containsLocalPigment = stroke.points.any { it.pigmentArgb != null }
        if (tips.size == 1 && BrushProgramBatchRenderer.draw(
                canvas,
                paint,
                stroke,
                tips.single(),
                sampledPoints,
                safeStart,
                width.toFloat(),
                height.toFloat(),
                containsLocalPigment,
            )) {
            paint.blendMode = null
            if (compositeLayer >= 0) canvas.restoreToCount(compositeLayer)
            paint.colorFilter = null
            return
        }
        val reuseRandomState = isCurrentActiveStroke(stroke) && safeStart > 0
        val scatterStates = if (rasterIncrementalRendering) rasterPrimaryScatterRandoms else activePrimaryScatterRandoms
        val rotationStates = if (rasterIncrementalRendering) rasterPrimaryRotationRandoms else activePrimaryRotationRandoms
        val scatterRandom = (if (reuseRandomState) scatterStates[stroke.id] else null)
            ?: java.util.Random(stroke.renderSeed.toLong())
        val rotationRandom = (if (reuseRandomState) rotationStates[stroke.id] else null)
            ?: java.util.Random(stroke.renderSeed.toLong() xor 0x49A2B31DL)
        if (!reuseRandomState || stroke.id !in scatterStates || stroke.id !in rotationStates) {
            advanceStampRandoms(scatterRandom, rotationRandom, stroke, sampledPoints, safeStart, dual = false)
        }
        val taper = strokeTaperFactors(stroke, sampledPoints)
        val clipBounds = canvas.clipBounds
        for (index in safeStart until sampledPoints.size) {
            val point = sampledPoints[index]
            val stampsAtPoint = BrushStampCount.resolve(stroke.stampCount, stroke.stampCountJitter, stroke.renderSeed, index)
            val x = point.x * width
            val y = point.y * height
            val dynamics = BrushProgramRuntime.evaluateDab(stroke, point)
            val brushDiameter = (stroke.width * referenceScale * (taper?.size?.get(index) ?: 1f) * dynamics.size).coerceAtLeast(.25f)
            val diameter = if (stroke.spray) SprayGeometry.particleDiameter(brushDiameter, SprayDynamics.particleSize(stroke, point), stroke.sprayParticleSizeUnit, stroke.sprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val scatterRadius = if (stroke.spray) brushDiameter / 2f * stroke.spraySpread.coerceIn(0f, 4f) else 0f
            val transformedDiameter = diameter * maxOf(1f, stroke.thickness.coerceIn(.05f, 2f) * dynamics.thickness) * 1.5f
            val influenceRadius = scatterRadius + transformedDiameter +
                app.inkloom.document.StrokePathJitter.maximumRadius(diameter, stroke.strokePathJitter, stroke.strokePathLongitudinalJitter) +
                BrushBlur.radiusPixels(stroke.blur * dynamics.blur, stroke.blurUnit, stroke.blurLinkedToSize, diameter, stroke.width * referenceScale, width.toFloat()) +
                if (stroke.waterEdgeEnabled) WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat()) else 0f
            if (x + influenceRadius < clipBounds.left || x - influenceRadius > clipBounds.right ||
                y + influenceRadius < clipBounds.top || y - influenceRadius > clipBounds.bottom
            ) {
                repeat(stampsAtPoint) {
                    if (stroke.spray) { scatterRandom.nextFloat(); scatterRandom.nextFloat() }
                    rotationRandom.nextFloat()
                }
                continue
            }
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = false)
            val hasPathJitter = stroke.strokePathJitter > .0001f || stroke.strokePathLongitudinalJitter > .0001f
            val strokeDirection = if (followsStroke || hasPathJitter) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.tipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed, stroke.ribbon) ?: return@stamp
            val offset = if (stroke.spray) SprayGeometry.offset(brushDiameter / 2f * stroke.spraySpread.coerceIn(0f, 4f), scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.sprayBias) else app.inkloom.document.SprayOffset(0f, 0f)
            val pathOffset = app.inkloom.document.StrokePathJitter.offset(stroke.renderSeed, sequenceIndex, strokeDirection, diameter, stroke.strokePathJitter, stroke.strokePathLongitudinalJitter)
            val drawX = x + offset.x + app.inkloom.document.StrokePathJitter.unpackX(pathOffset)
            val drawY = y + offset.y + app.inkloom.document.StrokePathJitter.unpackY(pathOffset)
            val spatialGrain = stroke.grainAssetPath != null && (GrainBehavior.requiresPerDabSampling(stroke.grainMovement, stroke.grainZoom) || GrainBehavior.requiresPerDabRotation(stroke.grainStrokeRotation) || stroke.tiltSizeCompression)
            val grainSample = if (spatialGrain) GrainBehavior.sample(
                baseOffset = app.inkloom.document.GrainOffset(grainOffsetX, grainOffsetY),
                movement = stroke.grainMovement,
                zoom = if (stroke.tiltSizeCompression) 1f else stroke.grainZoom,
                scalePercent = stroke.grainScale,
                centerX = drawX,
                centerY = drawY,
                diameter = if (stroke.tiltSizeCompression) stroke.width * referenceScale else diameter,
            ) else null
            val grainRotation = GrainBehavior.rotationDegrees(stroke.grainRotationDegrees, stroke.grainStrokeRotation, strokeDirection)
            val tip = if (stroke.grainAssetPath != null && (GrainDynamics.isDynamic(stroke) || spatialGrain)) {
                tipPaths.getOrNull(tipIndex)?.let { path ->
                    loadTip(path, stroke.hardness, stroke.grainAssetPath, GrainDynamics.density(stroke, point), grainSample?.scalePercent ?: stroke.grainScale, grainRotation, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, grainSample?.offset?.x ?: grainOffsetX, grainSample?.offset?.y ?: grainOffsetY, stroke.grainCompositeMode, stroke.grainFilteringMode, stroke.antiAliasMode, stroke.alphaThresholdEnabled, stroke.alphaThreshold)
                } ?: tips[tipIndex]
            } else tips[tipIndex]
            val thicknessScale = stroke.thickness.coerceIn(.05f, 2f) * dynamics.thickness
            val roundness = ShapeRoundnessJitter.factors(stroke.renderSeed, sequenceIndex, stroke.shapeRoundnessHorizontalJitter, stroke.shapeRoundnessVerticalJitter)
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, thicknessScale, stroke.thicknessVertical, roundness.horizontal, roundness.vertical)
            val flip = BrushTipFlip.signs(stroke.tipFlipHorizontalMode, stroke.tipFlipVerticalMode, stroke.tipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed)
            val scaleX = tipScale.x * flip.x
            val scaleY = tipScale.y * flip.y
            val angle = BrushStampRotation.degrees(
                stroke = stroke,
                dual = false,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) strokeDirection else 0f,
                randomUnit = rotationRandom.nextFloat(),
            )
            val matrix = primaryStampMatrix.apply {
                reset()
                postTranslate(-tip.width / 2f, -tip.height / 2f)
                postScale(scaleX, scaleY)
                postRotate(angle)
                postTranslate(drawX, drawY)
            }
            val jitteredColor = BrushColorJitter.argb(stroke, point, sequenceIndex)
            paint.colorFilter = srcInColorFilter(jitteredColor)
            val mainAlpha = (ProcreateRendering.depositedAlpha(stroke.procreateRenderingMode, stroke.flow * dynamics.depositedAlpha * (falloff?.get(index) ?: 1f) * (taper?.opacity?.get(index) ?: 1f)) * 255f).toInt()
            val tiltBleedAmount = stroke.tiltBleed.coerceIn(0f, 1f) * app.inkloom.document.BrushTilt.response(point, stroke.tiltBleedStart)
            if ((stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) || tiltBleedAmount > .001f) {
                val edgeRadius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat()) + diameter * tiltBleedAmount * .18f
                val edgeTipScale = BrushTipTransform.scale(tip.width, tip.height, diameter + edgeRadius * 2f, thicknessScale, stroke.thicknessVertical, roundness.horizontal, roundness.vertical)
                val edgeMatrix = edgeStampMatrix.apply { reset(); postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(edgeTipScale.x * flip.x, edgeTipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
                paint.colorFilter = srcInColorFilter(WaterEdge.darkenArgb(jitteredColor, stroke.waterEdgeDarkness))
                paint.alpha = (mainAlpha * maxOf(stroke.waterEdgeOpacity.coerceIn(0f, 1f), tiltBleedAmount * .55f)).toInt().coerceIn(1, 255)
                val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat()) + diameter * tiltBleedAmount * .08f
                paint.maskFilter = cachedBlurMask(blur)
                paint.blendMode = ProcreateBrushBlend.resolve(stroke.procreateBurntEdgeBlendMode)
                canvas.drawBitmap(tip, edgeMatrix, paint)
                paint.blendMode = if (stroke.blendTipsWithDarken) BlendMode.DARKEN else null
                paint.maskFilter = null
            }
            paint.colorFilter = srcInColorFilter(jitteredColor)
            paint.alpha = mainAlpha
            paint.maskFilter = brushBlurMask(stroke, point, diameter, stroke.width * referenceScale, width.toFloat())
            canvas.drawBitmap(tip, matrix, paint)
            paint.maskFilter = null
            }
        }
        paint.blendMode = null
        if (isCurrentActiveStroke(stroke)) {
            scatterStates[stroke.id] = scatterRandom
            rotationStates[stroke.id] = rotationRandom
        }
        if (compositeLayer >= 0) canvas.restoreToCount(compositeLayer)
        paint.colorFilter = null
    }

    /**
     * Sampling remains global for a live stamped stroke. That lets a cached prefix and a freshly
     * drawn tail share exactly the same spacing, sequence index and random stream as the final
     * committed stroke, rather than visibly changing shape on pen-up.
     */
    private fun primaryStampedPoints(stroke: InkStroke): List<StrokePoint> {
        if (isCurrentActiveStroke(stroke)) {
            val samplers = if (rasterIncrementalRendering) rasterPrimarySamples else activePrimarySamples
            val sampler = samplers.getOrPut(stroke.id) {
                BrushProgramRuntime.newIncrementalSampler(stroke, width.toFloat(), height.toFloat(), dual = false)
            }
            return BrushProgramRuntime.updateIncrementalSampler(sampler, stroke, width.toFloat(), dual = false)
        }
        return BrushProgramRuntime.sampleStampPoints(stroke, width.toFloat(), height.toFloat(), dual = false)
    }

    private fun dualStampedPoints(stroke: InkStroke): List<StrokePoint> {
        if (isCurrentActiveStroke(stroke)) {
            val sampler = activeDualSamples.getOrPut(stroke.id) {
                BrushProgramRuntime.newIncrementalSampler(stroke, width.toFloat(), height.toFloat(), dual = true)
            }
            return BrushProgramRuntime.updateIncrementalSampler(sampler, stroke, width.toFloat(), dual = true)
        }
        return BrushProgramRuntime.sampleStampPoints(stroke, width.toFloat(), height.toFloat(), dual = true)
    }

    private fun isCurrentActiveStroke(stroke: InkStroke): Boolean =
        activeTool != null && (stroke.id == currentStrokeId || stroke.id.startsWith("$currentStrokeId-sym-"))

    private fun advanceStampRandoms(
        scatterRandom: java.util.Random,
        rotationRandom: java.util.Random,
        stroke: InkStroke,
        sampledPoints: List<StrokePoint>,
        startIndex: Int,
        dual: Boolean,
    ) {
        for (index in 0 until startIndex.coerceAtMost(sampledPoints.size)) {
            val stampCount = if (dual) {
                BrushStampCount.resolve(stroke.dualStampCount, stroke.dualStampCountJitter, stroke.renderSeed, index, dual = true)
            } else {
                BrushStampCount.resolve(stroke.stampCount, stroke.stampCountJitter, stroke.renderSeed, index)
            }
            repeat(stampCount) {
                if (if (dual) stroke.dualSpray else stroke.spray) {
                    scatterRandom.nextFloat()
                    scatterRandom.nextFloat()
                }
                rotationRandom.nextFloat()
            }
        }
    }

    private fun drawCspDualBrushStroke(
        canvas: Canvas,
        stroke: InkStroke,
        primaryTips: List<Bitmap>,
        secondaryTips: List<Bitmap>,
        tipPaths: List<String>,
        grainOffsetX: Float,
        grainOffsetY: Float,
        primaryStartIndex: Int,
        secondaryStartIndex: Int,
    ) {
        if (isCurrentActiveStroke(stroke) && incrementalActiveDraw &&
            (stroke.dualGrainAssetPath == null || stroke.dualGrainDensity <= 0f || stroke.dualGrainApplyPerPlot)
        ) {
            drawActiveCspDualBrushStroke(
                canvas,
                stroke,
                primaryTips,
                secondaryTips,
                tipPaths,
                grainOffsetX,
                grainOffsetY,
                primaryStartIndex,
                secondaryStartIndex,
            )
            return
        }
        val bounds = dualBrushBounds(stroke) ?: return
        val primary = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val secondary = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val output = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        try {
            Canvas(primary).apply { translate(-bounds.left.toFloat(), -bounds.top.toFloat()) }.also { primaryCanvas ->
                drawStampedStroke(
                    primaryCanvas,
                    stroke.copy(dualBrush = false),
                    primaryTips,
                    emptyList(),
                    tipPaths,
                    grainOffsetX,
                    grainOffsetY,
                    primaryStartIndex,
                    0,
                )
            }
            paint.isFilterBitmap = CspAntiAlias.enabled(stroke.dualAntiAliasMode)
            Canvas(secondary).apply { translate(-bounds.left.toFloat(), -bounds.top.toFloat()) }.also { secondaryCanvas ->
                drawDualStampStream(secondaryCanvas, stroke, secondaryTips, secondaryStartIndex)
            }
            applyDualWholeStrokeTexture(secondary, bounds.left, bounds.top, stroke)
            val localBounds = Rect(0, 0, output.width, output.height)
            CspDualBrushCompositor.compositeRegion(primary, secondary, output, localBounds, stroke.dualCompositeMode, stroke.dualApplyRgb)
            canvas.drawBitmap(output, bounds.left.toFloat(), bounds.top.toFloat(), dualCompositeOutputPaint)
        } finally {
            primary.recycle()
            secondary.recycle()
            output.recycle()
            paint.blendMode = null
            paint.colorFilter = null
            paint.maskFilter = null
        }
    }

    /**
     * The front buffer keeps the two source streams independently. New dabs update only their
     * conservative dirty rectangle, so non-associative CSP modes remain exact without replaying a
     * growing imported stroke on every display frame.
     */
    private fun drawActiveCspDualBrushStroke(
        canvas: Canvas,
        stroke: InkStroke,
        primaryTips: List<Bitmap>,
        secondaryTips: List<Bitmap>,
        tipPaths: List<String>,
        grainOffsetX: Float,
        grainOffsetY: Float,
        primaryStartIndex: Int,
        secondaryStartIndex: Int,
    ) {
        val surface = activeCspDualBrushSurface(stroke)
        if (primaryStartIndex == 0 && secondaryStartIndex == 0) surface.clear()
        val dirty = activeDualBrushDirtyBounds(stroke, primaryStartIndex, secondaryStartIndex) ?: return
        drawStampedStroke(
            Canvas(surface.primary),
            stroke.copy(dualBrush = false),
            primaryTips,
            emptyList(),
            tipPaths,
            grainOffsetX,
            grainOffsetY,
            primaryStartIndex,
            0,
        )
        paint.isFilterBitmap = CspAntiAlias.enabled(stroke.dualAntiAliasMode)
        drawDualStampStream(Canvas(surface.secondary), stroke, secondaryTips, secondaryStartIndex)
        CspDualBrushCompositor.compositeRegion(
            surface.primary,
            surface.secondary,
            surface.output,
            dirty,
            stroke.dualCompositeMode,
            stroke.dualApplyRgb,
        )
        val checkpoint = canvas.save()
        canvas.clipRect(dirty)
        if (incrementalActiveDraw) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(surface.output, dirty, dirty, dualCompositeOutputPaint)
        canvas.restoreToCount(checkpoint)
        paint.blendMode = null
        paint.colorFilter = null
        paint.maskFilter = null
    }

    private fun activeCspDualBrushSurface(stroke: InkStroke): ActiveCspDualBrushSurface {
        val existing = activeCspDualBrushSurfaces[stroke.id]
        if (existing != null && existing.width == width && existing.height == height && existing.applyRgb == stroke.dualApplyRgb) return existing
        existing?.recycle()
        return ActiveCspDualBrushSurface(width.coerceAtLeast(1), height.coerceAtLeast(1), stroke.dualApplyRgb).also {
            activeCspDualBrushSurfaces[stroke.id] = it
        }
    }

    private fun applyDualWholeStrokeTexture(target: Bitmap, pageLeft: Int, pageTop: Int, stroke: InkStroke) {
        if (stroke.dualGrainApplyPerPlot || stroke.dualGrainDensity <= 0f) return
        val material = stroke.dualGrainAssetPath?.let(brushResources::grainMaterial) ?: return
        val grainOffset = GrainBehavior.offset(stroke.renderSeed xor DUAL_GRAIN_SEED, stroke.dualGrainOffsetJitter)
        CspStrokeTextureCompositor.applyInPlace(
            target, target.width, target.height, pageLeft, pageTop, width, height, material,
            stroke.dualGrainDensity, stroke.dualGrainScale, stroke.dualGrainRotationDegrees,
            grainOffset.x, grainOffset.y, stroke.dualGrainFilteringMode,
            stroke.dualGrainBrightness, stroke.dualGrainContrast, stroke.dualGrainReversed,
            stroke.dualGrainEmphasized, stroke.dualGrainCompositeMode,
            stroke.alphaThresholdEnabled, stroke.alphaThreshold,
        )
    }

    private fun activeDualBrushDirtyBounds(stroke: InkStroke, primaryStartIndex: Int, secondaryStartIndex: Int): Rect? {
        val primaryPoints = primaryStampedPoints(stroke)
        val secondaryPoints = dualStampedPoints(stroke)
        val affected = buildList {
            addAll(primaryPoints.subList(primaryStartIndex.coerceIn(0, primaryPoints.size), primaryPoints.size))
            addAll(secondaryPoints.subList(secondaryStartIndex.coerceIn(0, secondaryPoints.size), secondaryPoints.size))
        }
        if (affected.isEmpty()) return null
        val boundsIndex = StrokeTileIndex().apply { rebuild(emptyList(), width, height) }
        val expansion = boundsIndex.influenceRadiusPixels(stroke) + 4f
        val left = kotlin.math.floor(affected.minOf(StrokePoint::x) * width - expansion).toInt().coerceIn(0, width)
        val top = kotlin.math.floor(affected.minOf(StrokePoint::y) * height - expansion).toInt().coerceIn(0, height)
        val right = ceil(affected.maxOf(StrokePoint::x) * width + expansion).toInt().coerceIn(left, width)
        val bottom = ceil(affected.maxOf(StrokePoint::y) * height + expansion).toInt().coerceIn(top, height)
        return Rect(left, top, right, bottom).takeUnless(Rect::isEmpty)
    }

    private fun dualBrushBounds(stroke: InkStroke): Rect? {
        val index = StrokeTileIndex().apply { rebuild(listOf(stroke), width, height) }
        val bounds = index.pixelBoundsFor(stroke) ?: return null
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    /** Draws only the secondary stream. CSP composition happens after both retained streams exist. */
    private fun drawDualStampStream(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>, stampStartIndex: Int = 0) {
        paint.blendMode = if (stroke.dualBlendTipsWithDarken) BlendMode.DARKEN else null
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val sampledPoints = dualStampedPoints(stroke)
        val falloff = app.inkloom.document.StrokeFalloff.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.width * referenceScale, stroke.strokeFalloff)
        val safeStart = stampStartIndex.coerceIn(0, sampledPoints.size)
        val reuseRandomState = isCurrentActiveStroke(stroke) && safeStart > 0
        val scatterRandom = (if (reuseRandomState) activeDualScatterRandoms[stroke.id] else null)
            ?: java.util.Random(stroke.renderSeed.toLong() xor DUAL_SCATTER_SEED)
        val rotationRandom = (if (reuseRandomState) activeDualRotationRandoms[stroke.id] else null)
            ?: java.util.Random(stroke.renderSeed.toLong() xor DUAL_ROTATION_SEED)
        if (!reuseRandomState || stroke.id !in activeDualScatterRandoms || stroke.id !in activeDualRotationRandoms) {
            advanceStampRandoms(scatterRandom, rotationRandom, stroke, sampledPoints, safeStart, dual = true)
        }
        val taper = strokeTaperFactors(stroke, sampledPoints)
        val dualTipPaths = stroke.dualTipAssetPaths.ifEmpty { stroke.tipAssetPaths.ifEmpty { listOfNotNull(stroke.tipAssetPath) } }
        val dualGrainOffset = GrainBehavior.offset(stroke.renderSeed xor DUAL_GRAIN_SEED, stroke.dualGrainOffsetJitter)
        val clipBounds = canvas.clipBounds
        for (index in safeStart until sampledPoints.size) {
            val point = sampledPoints[index]
            val stampsAtPoint = BrushStampCount.resolve(stroke.dualStampCount, stroke.dualStampCountJitter, stroke.renderSeed, index, dual = true)
            val dynamics = BrushProgramRuntime.evaluateDab(stroke, point, dual = true)
            val brushDiameter = (stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f) * (taper?.size?.get(index) ?: 1f) * dynamics.size).coerceAtLeast(.25f)
            val diameter = if (stroke.dualSpray) SprayGeometry.particleDiameter(brushDiameter, SprayDynamics.dualParticleSize(stroke, point), stroke.dualSprayParticleSizeUnit, stroke.dualSprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val x = point.x * width
            val y = point.y * height
            val scatterRadius = if (stroke.dualSpray) brushDiameter / 2f * stroke.dualSpraySpread.coerceIn(0f, 4f) else 0f
            val transformedDiameter = diameter * maxOf(1f, stroke.dualThickness.coerceIn(.05f, 2f) * dynamics.thickness) * 1.5f
            val influenceRadius = scatterRadius + transformedDiameter +
                app.inkloom.document.StrokePathJitter.maximumRadius(diameter, stroke.dualStrokePathJitter, stroke.dualStrokePathLongitudinalJitter) +
                BrushBlur.radiusPixels(stroke.blur * dynamics.blur, stroke.blurUnit, stroke.blurLinkedToSize, diameter, stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f), width.toFloat())
            if (x + influenceRadius < clipBounds.left || x - influenceRadius > clipBounds.right ||
                y + influenceRadius < clipBounds.top || y - influenceRadius > clipBounds.bottom
            ) {
                repeat(stampsAtPoint) {
                    if (stroke.dualSpray) { scatterRandom.nextFloat(); scatterRandom.nextFloat() }
                    rotationRandom.nextFloat()
                }
                continue
            }
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = true)
            val hasPathJitter = stroke.dualStrokePathJitter > .0001f || stroke.dualStrokePathLongitudinalJitter > .0001f
            val strokeDirection = if (followsStroke || hasPathJitter) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.dualTipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed xor DUAL_SEQUENCE_SEED, stroke.dualRibbon) ?: return@stamp
            val baseTip = tips[tipIndex]
            val offset = if (stroke.dualSpray) SprayGeometry.offset(brushDiameter / 2f * stroke.dualSpraySpread.coerceIn(0f, 4f), scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.dualSprayBias) else app.inkloom.document.SprayOffset(0f, 0f)
            val pathOffset = app.inkloom.document.StrokePathJitter.offset(stroke.renderSeed, sequenceIndex, strokeDirection, diameter, stroke.dualStrokePathJitter, stroke.dualStrokePathLongitudinalJitter, dual = true)
            val drawX = x + offset.x + app.inkloom.document.StrokePathJitter.unpackX(pathOffset)
            val drawY = y + offset.y + app.inkloom.document.StrokePathJitter.unpackY(pathOffset)
            val spatialGrain = stroke.dualGrainAssetPath != null &&
                (GrainBehavior.requiresPerDabSampling(stroke.dualGrainMovement, stroke.dualGrainZoom) ||
                    GrainBehavior.requiresPerDabRotation(stroke.dualGrainStrokeRotation))
            val grainSample = if (spatialGrain) GrainBehavior.sample(
                dualGrainOffset, stroke.dualGrainMovement, stroke.dualGrainZoom, stroke.dualGrainScale,
                drawX, drawY, diameter,
            ) else null
            val grainRotation = GrainBehavior.rotationDegrees(stroke.dualGrainRotationDegrees, stroke.dualGrainStrokeRotation, strokeDirection)
            val tip = if (stroke.dualGrainApplyPerPlot && stroke.dualGrainAssetPath != null &&
                (GrainDynamics.isDualDynamic(stroke) || spatialGrain)
            ) {
                dualTipPaths.getOrNull(tipIndex)?.let { path ->
                    loadTip(
                        path, stroke.dualHardness, stroke.dualGrainAssetPath, GrainDynamics.dualDensity(stroke, point),
                        grainSample?.scalePercent ?: stroke.dualGrainScale, grainRotation,
                        stroke.dualGrainBrightness, stroke.dualGrainContrast, stroke.dualGrainReversed,
                        stroke.dualGrainEmphasized, grainSample?.offset?.x ?: dualGrainOffset.x,
                        grainSample?.offset?.y ?: dualGrainOffset.y, stroke.dualGrainCompositeMode,
                        stroke.dualGrainFilteringMode, stroke.dualAntiAliasMode,
                        stroke.alphaThresholdEnabled, stroke.alphaThreshold, stroke.dualApplyRgb,
                    )
                } ?: baseTip
            } else baseTip
            val roundness = ShapeRoundnessJitter.factors(stroke.renderSeed, sequenceIndex, stroke.dualShapeRoundnessHorizontalJitter, stroke.dualShapeRoundnessVerticalJitter, dual = true)
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, stroke.dualThickness.coerceIn(.05f, 2f) * dynamics.thickness, stroke.dualThicknessVertical, roundness.horizontal, roundness.vertical)
            val flip = BrushTipFlip.signs(stroke.dualTipFlipHorizontalMode, stroke.dualTipFlipVerticalMode, stroke.dualTipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed, dual = true)
            val angle = BrushStampRotation.degrees(
                stroke = stroke,
                dual = true,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) strokeDirection else 0f,
                randomUnit = rotationRandom.nextFloat(),
            )
            val matrix = secondaryStampMatrix.apply { reset(); postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(tipScale.x * flip.x, tipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
            paint.colorFilter = if (stroke.dualApplyRgb) null else srcInColorFilter(BrushColorJitter.argb(stroke, point, sequenceIndex))
            paint.alpha = (stroke.dualFlow.coerceIn(0f, 1f) * dynamics.depositedAlpha * (falloff?.get(index) ?: 1f) * (taper?.opacity?.get(index) ?: 1f)).coerceIn(0f, 1f).times(255).toInt()
            paint.maskFilter = brushBlurMask(stroke, point, diameter, stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f), width.toFloat())
            canvas.drawBitmap(tip, matrix, paint)
            paint.maskFilter = null
            }
        }
        paint.blendMode = null
        if (isCurrentActiveStroke(stroke)) {
            activeDualScatterRandoms[stroke.id] = scatterRandom
            activeDualRotationRandoms[stroke.id] = rotationRandom
        }
    }

    private fun strokeTaperFactors(stroke: InkStroke, points: List<StrokePoint>): StrokeTaper.DabFactors? =
        if (!stroke.taperStartEnabled && !stroke.taperEndEnabled && stroke.procreateTaper.isDisabled()) null
        else StrokeTaper.evaluate(stroke, points, width.toFloat(), height.toFloat())

    private fun drawLinePoint(canvas: Canvas, stroke: InkStroke, point: StrokePoint, x: Float, y: Float, strokeWidth: Float, alpha: Int) {
        val jitteredColor = BrushColorJitter.argb(stroke, point, point.timeMs.toInt())
        if (hasWaterEdge(stroke, point)) {
            configureWaterEdge(stroke, point, strokeWidth, alpha, jitteredColor)
            paint.blendMode = ProcreateBrushBlend.resolve(stroke.procreateBurntEdgeBlendMode)
            canvas.drawPoint(x, y, paint)
            paint.blendMode = null
        }
        paint.maskFilter = brushBlurMask(stroke, point, strokeWidth, stroke.width * width.coerceAtLeast(1) / REFERENCE_WIDTH, width.toFloat()); paint.color = jitteredColor; paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawPoint(x, y, paint)
        paint.maskFilter = null
    }

    /** Photoshop's classic hard round brush is a continuous deposit. Drawing every input pair as
     * an independently alpha-blended round-capped line creates a dark bead at every join. Build a
     * single pressure-width ribbon instead, so joins are rasterized once and remain seamless. */
    private fun drawContinuousRoundStroke(canvas: Canvas, stroke: InkStroke) {
        val points = stroke.points
        val scale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val taper = strokeTaperFactors(stroke, points)
        val radii = FloatArray(points.size)
        var alphaSum = 0f
        points.forEachIndexed { index, point ->
            val dynamics = BrushProgramRuntime.evaluateDab(stroke, point)
            radii[index] = stroke.width * scale * (taper?.size?.get(index) ?: 1f) * dynamics.size * .5f
            alphaSum += stroke.flow * dynamics.depositedAlpha * (taper?.opacity?.get(index) ?: 1f)
        }
        val path = Path()
        fun edge(index: Int, side: Float): Pair<Float, Float> {
            val previous = points[(index - 1).coerceAtLeast(0)]
            val next = points[(index + 1).coerceAtMost(points.lastIndex)]
            val dx = (next.x - previous.x) * width
            val dy = (next.y - previous.y) * height
            val length = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(.001f)
            val radius = radii[index]
            return points[index].x * width - dy / length * radius * side to
                points[index].y * height + dx / length * radius * side
        }
        points.indices.forEach { index ->
            val edge = edge(index, 1f)
            if (index == 0) path.moveTo(edge.first, edge.second) else path.lineTo(edge.first, edge.second)
        }
        points.indices.reversed().forEach { index ->
            val edge = edge(index, -1f)
            path.lineTo(edge.first, edge.second)
        }
        path.close()
        val first = points.first()
        val last = points.last()
        path.addCircle(first.x * width, first.y * height, radii.first(), Path.Direction.CW)
        path.addCircle(last.x * width, last.y * height, radii.last(), Path.Direction.CW)
        val previousStyle = paint.style
        paint.style = Paint.Style.FILL
        paint.color = stroke.colorArgb.toInt()
        paint.colorFilter = null
        paint.maskFilter = null
        paint.alpha = (alphaSum / points.size * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(path, paint)
        paint.style = previousStyle
        paint.alpha = 255
    }

    private fun drawLineSegment(canvas: Canvas, stroke: InkStroke, point: StrokePoint, startX: Float, startY: Float, endX: Float, endY: Float, strokeWidth: Float, alpha: Int) {
        val jitteredColor = BrushColorJitter.argb(stroke, point, point.timeMs.toInt())
        if (hasWaterEdge(stroke, point)) {
            configureWaterEdge(stroke, point, strokeWidth, alpha, jitteredColor)
            paint.blendMode = ProcreateBrushBlend.resolve(stroke.procreateBurntEdgeBlendMode)
            canvas.drawLine(startX, startY, endX, endY, paint)
            paint.blendMode = null
        }
        paint.maskFilter = brushBlurMask(stroke, point, strokeWidth, stroke.width * width.coerceAtLeast(1) / REFERENCE_WIDTH, width.toFloat()); paint.color = jitteredColor; paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawLine(startX, startY, endX, endY, paint)
        paint.maskFilter = null
    }

    private fun brushBlurMask(stroke: InkStroke, point: StrokePoint, diameter: Float, baseDiameter: Float, pageWidth: Float): BlurMaskFilter? {
        val gradation = stroke.tiltGradation.coerceIn(0f, 1f) * app.inkloom.document.BrushTilt.response(point, stroke.tiltGradationStart)
        val radius = BrushBlur.radiusPixels(stroke.blur, stroke.blurUnit, stroke.blurLinkedToSize, diameter, baseDiameter, pageWidth) *
            BrushProgramRuntime.evaluateDab(stroke, point).blur + diameter * gradation * .22f +
            diameter * app.inkloom.document.WetMixDynamics.blurAmount(stroke, point) * .18f
        return cachedBlurMask(radius)
    }

    private fun hasWaterEdge(stroke: InkStroke, point: StrokePoint): Boolean =
        (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) ||
            (stroke.tiltBleed > 0f && app.inkloom.document.BrushTilt.response(point, stroke.tiltBleedStart) > .001f)

    private fun configureWaterEdge(stroke: InkStroke, point: StrokePoint, strokeWidth: Float, alpha: Int, color: Int) {
        val tiltBleedAmount = stroke.tiltBleed.coerceIn(0f, 1f) * app.inkloom.document.BrushTilt.response(point, stroke.tiltBleedStart)
        val radius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat()) + strokeWidth * tiltBleedAmount * .18f
        paint.color = WaterEdge.darkenArgb(color, stroke.waterEdgeDarkness)
        paint.strokeWidth = strokeWidth + radius * 2f
        paint.alpha = (alpha * maxOf(stroke.waterEdgeOpacity.coerceIn(0f, 1f), tiltBleedAmount * .55f)).toInt().coerceIn(1, 255)
        val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat()) + strokeWidth * tiltBleedAmount * .08f
        paint.maskFilter = cachedBlurMask(blur)
    }

    private fun srcInColorFilter(color: Int): PorterDuffColorFilter =
        srcInColorFilters[color] ?: PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN).also { srcInColorFilters[color] = it }

    /** BlurMaskFilter construction is native-backed and expensive. Quarter-pixel buckets are
     * visually indistinguishable at tablet density while turning per-dab allocation into reuse. */
    private fun cachedBlurMask(radius: Float): BlurMaskFilter? {
        if (!radius.isFinite() || radius <= .01f) return null
        val quantizedRadius = BrushBlur.quantizedRadius(radius, MAX_CACHED_BLUR_RADIUS_PX)
        if (quantizedRadius <= 0f) return null
        val bucket = (quantizedRadius * 4f).toInt()
        return blurMaskFilters[bucket] ?: BlurMaskFilter(quantizedRadius, BlurMaskFilter.Blur.NORMAL).also { blurMaskFilters[bucket] = it }
    }

    private fun loadTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false, grainOffsetX: Float = 0f, grainOffsetY: Float = 0f, grainCompositeMode: Int = CspTextureComposite.MULTIPLY, grainFilteringMode: Int = ProcreateFiltering.NONE, antiAliasMode: Int = CspAntiAlias.MEDIUM, alphaThresholdEnabled: Boolean = false, alphaThreshold: Float = 0f, preserveRgb: Boolean = false): Bitmap? {
        val request = tipRequest(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, grainFilteringMode, antiAliasMode, alphaThresholdEnabled, alphaThreshold, preserveRgb)
        brushResources.tip(request)?.let { return it }
        if (brushResources.isLoading(request)) return null
        // Dynamic grain can request many density buckets while a stroke is moving. Disk/bitmap
        // decoding those variants on this path blocks MotionEvent delivery, so schedule them and
        // let the caller use the already decoded base tip for this frame. A missing base tip may
        // still decode once (normally it was prewarmed on selection); otherwise the first stroke
        // would visibly switch from a fallback line to its real shape on pen-up.
        val isDynamicVariant = grainPath != null && (
            kotlin.math.abs(grainDensity - this.grainDensity) >= .025f ||
                kotlin.math.abs(grainScale - this.grainScale) >= 2.5f ||
                kotlin.math.abs(grainRotation - this.grainRotationDegrees) >= 7.5f ||
                kotlin.math.abs(grainOffsetX) > .0001f || kotlin.math.abs(grainOffsetY) > .0001f
            )
        if (activeTool != null && isDynamicVariant && !forceSynchronousTipDecode) {
            prewarmTip(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, grainFilteringMode, antiAliasMode, alphaThresholdEnabled, alphaThreshold, preserveRgb)
            return null
        }
        return brushResources.decodeNow(request)
    }

    private fun tipRequest(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float, grainRotation: Float, grainBrightness: Float, grainContrast: Float, grainReversed: Boolean, grainEmphasized: Boolean, grainOffsetX: Float, grainOffsetY: Float, grainCompositeMode: Int, grainFilteringMode: Int, antiAliasMode: Int, alphaThresholdEnabled: Boolean, alphaThreshold: Float, preserveRgb: Boolean = false): BrushTipRequest =
        BrushTipRequest(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, grainFilteringMode, antiAliasMode, alphaThresholdEnabled, alphaThreshold, preserveRgb)

    private fun prewarmTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false, grainOffsetX: Float = 0f, grainOffsetY: Float = 0f, grainCompositeMode: Int = CspTextureComposite.MULTIPLY, grainFilteringMode: Int = ProcreateFiltering.NONE, antiAliasMode: Int = CspAntiAlias.MEDIUM, alphaThresholdEnabled: Boolean = false, alphaThreshold: Float = 0f, preserveRgb: Boolean = false) {
        val request = tipRequest(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, grainFilteringMode, antiAliasMode, alphaThresholdEnabled, alphaThreshold, preserveRgb)
        val resources = brushResources
        if (!resources.beginLoad(request)) return
        TIP_DECODE_EXECUTOR.execute {
            val decoded = request.decode()
            post {
                if (resources.completeLoad(request, decoded)) {
                    requestActiveTipRefresh()
                    notifyHoverCursorStyleChanged()
                }
                postInvalidateOnAnimation()
            }
        }
    }

    private fun requestActiveTipRefresh() {
        if (activeTipRefreshScheduled || activeStrokeSurface == null || currentPoints.isEmpty()) return
        activeTipRefreshScheduled = true
        postOnAnimation(activeTipRefreshFrame)
    }

    /** Decodes a newly selected imported tip before the first pen contact. */
    fun prewarmBrushAssets() {
        val primary = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }
        val secondary = if (brushDual) dualTipAssetPaths.ifEmpty { primary } else emptyList()
        if (primary.isEmpty() && secondary.isEmpty()) return
        val signature = "${primary.joinToString()}|${secondary.joinToString()}|$brushHardness|$dualHardness|$grainAssetPath|$grainDensity|$grainScale|$grainMovement|$grainZoom|$grainRotationDegrees|$grainStrokeRotation|$grainBrightness|$grainContrast|$grainReversed|$grainEmphasized|$grainCompositeMode|$grainFilteringMode|$dualGrainAssetPath|$dualGrainDensity|$dualGrainScale|$dualGrainMovement|$dualGrainZoom|$dualGrainRotationDegrees|$dualGrainStrokeRotation|$dualGrainBrightness|$dualGrainContrast|$dualGrainReversed|$dualGrainEmphasized|$dualGrainCompositeMode|$dualGrainFilteringMode|$brushAntiAliasMode|$dualAntiAliasMode|$dualApplyRgb|$alphaThresholdEnabled|$alphaThreshold"
        if (signature == prewarmedBrushSignature) return
        prewarmedBrushSignature = signature
        primary.forEach { prewarmTip(it, brushHardness, grainAssetPath, grainDensity, grainScale, grainRotationDegrees, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainCompositeMode = grainCompositeMode, grainFilteringMode = grainFilteringMode, antiAliasMode = brushAntiAliasMode, alphaThresholdEnabled = alphaThresholdEnabled, alphaThreshold = alphaThreshold) }
        secondary.forEach { prewarmTip(it, dualHardness, dualGrainAssetPath.takeIf { dualGrainApplyPerPlot }, dualGrainDensity, dualGrainScale, dualGrainRotationDegrees, dualGrainBrightness, dualGrainContrast, dualGrainReversed, dualGrainEmphasized, grainCompositeMode = dualGrainCompositeMode, grainFilteringMode = dualGrainFilteringMode, antiAliasMode = dualAntiAliasMode, alphaThresholdEnabled = alphaThresholdEnabled, alphaThreshold = alphaThreshold, preserveRgb = dualApplyRgb) }
    }

    /** Selection must hand back a brush that can draw on the very next input packet. Imported tip
     * images are decoded once at selection time; live rendering never waits on an async prewarm. */
    fun prepareBrushAssetsNow() {
        val primary = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }
        val secondary = if (brushDual) dualTipAssetPaths.ifEmpty { primary } else emptyList()
        primary.forEach { path ->
            val request = tipRequest(path, brushHardness, grainAssetPath, grainDensity, grainScale, grainRotationDegrees, grainBrightness, grainContrast, grainReversed, grainEmphasized, 0f, 0f, grainCompositeMode, grainFilteringMode, brushAntiAliasMode, alphaThresholdEnabled, alphaThreshold)
            brushResources.decodeNow(request)
        }
        secondary.forEach { path ->
            val grain = dualGrainAssetPath.takeIf { dualGrainApplyPerPlot }
            val request = tipRequest(path, dualHardness, grain, dualGrainDensity, dualGrainScale, dualGrainRotationDegrees, dualGrainBrightness, dualGrainContrast, dualGrainReversed, dualGrainEmphasized, 0f, 0f, dualGrainCompositeMode, dualGrainFilteringMode, dualAntiAliasMode, alphaThresholdEnabled, alphaThreshold, dualApplyRgb)
            brushResources.decodeNow(request)
        }
    }

    /** Freezes the format-neutral cursor/dynamics program when a preset is selected. The next
     * physical contact therefore performs neither compilation nor format-specific work. */
    fun prepareConfiguredBrushProgram() {
        val template = withCurrentBrushBehavior(buildStroke(emptyList()))
        configuredBrushTemplate = template
        configuredBrushProgram = BrushProgramRuntime.compile(template)
    }

    /** A palette/tool tap can interrupt a contact stream before Android delivers its final UP.
     * Treat each tool or preset change as a hard input boundary so that stale navigation,
     * eraser, stylus-button, or drawing state cannot consume every later contact. */
    fun switchInputConfiguration(nextTool: EditorTool, nextBrushId: String?) {
        if (configuredInputTool == nextTool && configuredInputBrushId == nextBrushId) return
        cancelActiveGestureForNavigation()
        viewportGestureActive = false
        externalViewportGestureContact = false
        pageContactRejected = false
        stylusCommandConsumesContact = false
        stylusHandNavigationContact = false
        // Do not synthesize a hardware-button release here. A HOLD shortcut can legitimately
        // trigger this reconfiguration (for example pen -> eraser) while the button is still
        // physically down; its real release event must remain responsible for restoring the tool.
        configuredInputTool = nextTool
        configuredInputBrushId = nextBrushId
        tool = nextTool
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.EYEDROPPER, EditorTool.SELECT, EditorTool.SHAPE, EditorTool.LASER, EditorTool.FILL, EditorTool.GRADIENT, EditorTool.SMUDGE, EditorTool.LIQUIFY, EditorTool.CORRECT_LINE)) return false
        val pointerIndex = event.stylusPointerIndex(activeStylusPointerId).takeIf { it >= 0 }
            ?: event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val eventToolType = event.getToolType(pointerIndex)
        val isStylus = eventToolType == MotionEvent.TOOL_TYPE_STYLUS || eventToolType == MotionEvent.TOOL_TYPE_ERASER
        if (isStylus && event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN)) {
            activeStylusPointerId = event.getPointerId(pointerIndex)
        }
        // Some EMR devices report a resting palm first and the pen as POINTER_DOWN. Promote the
        // pen's own lifecycle so it starts a fresh stroke instead of appending to stale native
        // input state (the source of sporadic marks near 0,0).
        val effectiveAction = when {
            isStylus && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN -> MotionEvent.ACTION_DOWN
            isStylus && event.actionMasked == MotionEvent.ACTION_POINTER_UP -> MotionEvent.ACTION_UP
            else -> event.actionMasked
        }
        if (effectiveAction == MotionEvent.ACTION_DOWN && !hasFocus()) requestFocus()
        updateContactCursor(event, pointerIndex, isStylus)
        if (effectiveAction == MotionEvent.ACTION_DOWN) {
            // Every new contact stream is a hard recovery boundary. This also recovers from a
            // device/parent that consumed the previous stream's final UP or CANCEL.
            if (activeTool != null) cancelActiveGestureForNavigation()
            viewportGestureActive = false
            externalViewportGestureContact = false
            pageContactRejected = false
            if (isStylus && tool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY)) {
                requestUnbufferedDispatch(event)
            }
        }
        if (isStylus) singleFingerDoubleTapUndoGesture.cancel()
        if (isStylus) lastStylusSeenAt = event.eventTime
        onInputDiagnostic(when (eventToolType) { MotionEvent.TOOL_TYPE_STYLUS -> "Stylus"; MotionEvent.TOOL_TYPE_ERASER -> "Eraser"; MotionEvent.TOOL_TYPE_FINGER -> "Finger"; else -> "Other" }, event.getPressure(pointerIndex), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.buttonState)
        val buttonTransitions = if (isStylus) updateStylusButtonState(event.buttonState) else emptyList()
        val pressedButtonActions = buttonTransitions.filter { it.phase == ShortcutPhase.PRESS }.map(StylusButtonTransition::action)
        val pressedCommands = pressedButtonActions.filter(StylusButtonAction::isCommand).distinct()
        val stylusButtonAction = if (isStylus) resolveStylusButtonAction(event.buttonState) else StylusButtonAction.NONE
        val pressedContactSwitch = pressedButtonActions.any { it in setOf(StylusButtonAction.ERASER, StylusButtonAction.LASSO, StylusButtonAction.EYEDROPPER) }
        if ((pressedCommands.isNotEmpty() || pressedContactSwitch) && activeTool != null) {
            cancelActiveGestureForNavigation()
            if (pressedContactSwitch) stylusCommandConsumesContact = true
        }
        dispatchStylusButtonTransitions(buttonTransitions)
        if (stylusButtonAction.isCommand) stylusCommandConsumesContact = true
        if (StylusButtonAction.HAND in pressedButtonActions) {
            navigationX = event.x
            navigationY = event.y
            navigationDownX = event.x
            navigationDownY = event.y
            navigationMoved = false
        }
        if (stylusButtonAction == StylusButtonAction.HAND) stylusHandNavigationContact = true
        if (stylusCommandConsumesContact || stylusButtonAction.isCommand) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                stylusCommandConsumesContact = false
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    releaseHeldStylusButtons()
                    lastStylusButtonState = 0
                }
            }
            return true
        }
        val inputPoint = canvasPoint(event, pointerIndex)
        continueFillingColor?.takeIf { event.pointerCount == 1 }?.let { fillColor ->
            inkColor = fillColor
            when (effectiveAction) {
                MotionEvent.ACTION_DOWN -> beginFillGesture(
                    inputPoint.first / width.coerceAtLeast(1).toFloat(),
                    inputPoint.second / height.coerceAtLeast(1).toFloat(),
                    event.x,
                )
                MotionEvent.ACTION_MOVE -> adjustFillSensitivity(event.x)
                MotionEvent.ACTION_UP -> commitFillGesture()
                MotionEvent.ACTION_CANCEL -> cancelFillGesture()
            }
            return true
        }
        if (brushSizeDragActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) updateBrushSizeDrag(inputPoint.first, inputPoint.second) else finishBrushSizeDrag(cancelled = true)
                MotionEvent.ACTION_UP -> finishBrushSizeDrag(cancelled = false)
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> finishBrushSizeDrag(cancelled = true)
            }
            return true
        }
        val elapsedSinceStylus = if (lastStylusSeenAt == Long.MIN_VALUE) Long.MAX_VALUE else event.eventTime - lastStylusSeenAt
        val hasStylusPointer = (0 until event.pointerCount).any { index ->
            event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER
        }
        if (event.pointerCount >= 2 && !hasStylusPointer && !activeInputWasStylus) {
            if (!viewportGestureActive) cancelActiveGestureForNavigation()
            viewportGestureActive = true
            // Canvas navigation always outranks one-finger ruler/guide manipulation.
            rulerGestureStart?.let { rulerGuide = it }
            rulerGestureMode = null
            rulerGestureStart = null
            canvasGuideGestureStart?.let { canvasGuideState = it }
            canvasGuideGestureMode = null
            canvasGuideGestureStart = null
            perspectiveGuideGestureStart?.let { perspectiveGuideState = it }
            perspectiveGuideGesturePoint = null
            perspectiveGuideGestureStart = null
            specialRulerGestureStart?.let { specialRulerState = it }
            specialRulerGestureHandle = null
            specialRulerGestureStart = null
        }
        if (!viewportGestureActive && handleGradientGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        if (!viewportGestureActive && handleSpecialRulerGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        if (!viewportGestureActive && handlePerspectiveGuideGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        if (!viewportGestureActive && handleCanvasGuideGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        if (!viewportGestureActive && handleRulerGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        val buttonForcesNavigation = isStylus && (stylusButtonAction == StylusButtonAction.HAND || stylusHandNavigationContact)
        // An explicitly selected lasso owns a single non-palm pointer even immediately after pen
        // hover/contact. The recent-stylus rejection window is valuable for paint palm rejection,
        // but applying it to Selection turns the first lasso drag into an unexpected canvas pan.
        val selectionDrawContact = !isStylus && event.pointerCount == 1 && tool == EditorTool.SELECT &&
            event.getTouchMajor(pointerIndex) < StylusInputClassifier.PALM_TOUCH_MAJOR_PX
        val isFingerNavigation = viewportGestureActive || forceNavigation || buttonForcesNavigation ||
            (!selectionDrawContact && StylusInputClassifier.shouldNavigateOrRejectFinger(isStylus, fingerDrawingEnabled, event.pointerCount, event.getTouchMajor(pointerIndex), elapsedSinceStylus))
        if (isFingerNavigation) {
            pageContactRejected = false
            if (activeTool != null) {
                if (activeInputWasStylus && !forceNavigation && !buttonForcesNavigation) return true
                cancelActiveGestureForNavigation()
            }
            if (externalViewportGestures && (externalViewportGestureContact || event.pointerCount >= 2)) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> externalViewportGestureContact = event.pointerCount >= 2
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        externalViewportGestureContact = false
                        viewportGestureActive = false
                    }
                }
                return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    twoPointerRotationGesture.reset()
                    navigationSpan = 0f
                    navigationGestureStartSpan = 0f
                    navigationGestureRotation = 0f
                    threeFingerTapRedoGesture.cancel()
                    navigationX = event.x; navigationY = event.y
                    navigationDownX = event.x; navigationDownY = event.y
                    navigationMoved = false
                    navigationTapEligible = eventToolType == MotionEvent.TOOL_TYPE_FINGER &&
                        event.getTouchMajor(pointerIndex) < StylusInputClassifier.PALM_TOUCH_MAJOR_PX &&
                        elapsedSinceStylus > StylusInputClassifier.RECENT_STYLUS_WINDOW_MS
                    if (singleFingerDoubleTapUndo && navigationTapEligible) {
                        singleFingerDoubleTapUndoGesture.start(event.eventTime, event.x, event.y)
                    } else singleFingerDoubleTapUndoGesture.cancel()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    navigationMoved = true
                    navigationTapEligible = false
                    singleFingerDoubleTapUndoGesture.cancel()
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
                    navigationSpan = event.localSpan()
                    navigationGestureStartSpan = navigationSpan
                    navigationGestureRotation = 0f
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
                    navigationSpan = 0f
                    navigationGestureStartSpan = 0f
                    navigationGestureRotation = 0f
                    navigationMoved = true
                    navigationTapEligible = false
                    twoPointerRotationGesture.reset()
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                    val focus = event.centroid()
                    val span = event.localSpan()
                    val factor = if (navigationSpan > .01f && span > .01f) (span / navigationSpan).coerceIn(.5f, 2f) else 1f
                    val rotationDelta = if (twoFingerRotationEnabled && event.pointerCount == 2) {
                        twoPointerRotationGesture.update(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    } else {
                        twoPointerRotationGesture.reset()
                        null
                    }
                    rotationDelta?.let { delta ->
                        navigationGestureRotation += kotlin.math.abs(delta)
                        onViewportRotate(delta)
                    }
                    if (event.pointerCount == 3) threeFingerTapRedoGesture.move(focus.first, focus.second, transforming = true)
                    else twoFingerTapUndoGesture.move(
                        focus.first,
                        focus.second,
                        transforming = kotlin.math.abs(span - navigationGestureStartSpan) > navigationTouchSlop || navigationGestureRotation > 2f,
                    )
                    onViewportPinch?.invoke(factor, navigationX, navigationY, focus.first, focus.second) ?: run {
                        if (factor != 1f) onViewportScale(factor, navigationX, navigationY)
                        onViewportPan(focus.first - navigationX, focus.second - navigationY)
                    }
                    navigationX = focus.first; navigationY = focus.second
                    navigationSpan = span
                    navigationMoved = true
                } else if (rotateNavigation) {
                    singleFingerDoubleTapUndoGesture.move(event.x, event.y, transforming = false)
                    onViewportRotate((event.x - navigationX) * VIEW_ROTATION_DEGREES_PER_PIXEL)
                    navigationX = event.x; navigationY = event.y
                    navigationMoved = true
                } else {
                    singleFingerDoubleTapUndoGesture.move(event.x, event.y, transforming = false)
                    if (hypot(event.x - navigationDownX, event.y - navigationDownY) > navigationTouchSlop) navigationMoved = true
                    onViewportPan(event.x - navigationX, event.y - navigationY)
                    navigationX = event.x; navigationY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    twoPointerRotationGesture.reset()
                    navigationSpan = 0f
                    threeFingerTapRedoGesture.cancel()
                    val movedFromDown = navigationMoved || hypot(event.x - navigationDownX, event.y - navigationDownY) > navigationTouchSlop
                    if (navigationTapEligible && !movedFromDown && !viewportGestureActive) {
                        val link = PdfGotoLinkNavigation.hit(
                            pdfGotoLinks,
                            (inputPoint.first / width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (inputPoint.second / height.coerceAtLeast(1)).coerceIn(0f, 1f),
                        )
                        if (link != null) {
                            singleFingerDoubleTapUndoGesture.cancel()
                            onPdfGotoLink(link)
                        } else if (singleFingerDoubleTapUndo && singleFingerDoubleTapUndoGesture.finish(event.eventTime)) {
                            onUndoGesture()
                        }
                        performClick()
                    } else singleFingerDoubleTapUndoGesture.cancel()
                    navigationTapEligible = false
                    stylusHandNavigationContact = false
                    viewportGestureActive = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    navigationTapEligible = false
                    singleFingerDoubleTapUndoGesture.cancel()
                    twoFingerTapUndoGesture.cancel()
                    threeFingerTapRedoGesture.cancel()
                    twoPointerRotationGesture.reset()
                    navigationSpan = 0f
                    stylusHandNavigationContact = false
                    viewportGestureActive = false
                    if (isStylus) {
                        releaseHeldStylusButtons()
                        lastStylusButtonState = 0
                    }
                }
            }
            return true
        }
        if (pageContactRejected) {
            if (effectiveAction == MotionEvent.ACTION_UP || effectiveAction == MotionEvent.ACTION_CANCEL) pageContactRejected = false
            return true
        }
        if (effectiveAction == MotionEvent.ACTION_DOWN &&
            (inputPoint.first !in 0f..width.toFloat() || inputPoint.second !in 0f..height.toFloat())
        ) {
            pageContactRejected = true
            return true
        }
        if (effectiveAction == MotionEvent.ACTION_DOWN) singleFingerDoubleTapUndoGesture.cancel()
        val normalizedX = (inputPoint.first / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val normalizedY = (inputPoint.second / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        when (effectiveAction) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activeTool = resolveTool(event, eventToolType)
                activeInputWasStylus = isStylus
                // Freeze CSP's screen-size conversion at contact start. A viewport update cannot
                // change an in-progress stroke/tool footprint halfway through the gesture.
                gestureBrushSize = documentBrushSize()
                gestureEraserSize = documentEraserSize()
                val motionCtrlPressed = event.metaState and KeyEvent.META_CTRL_ON != 0
                val motionAltPressed = event.metaState and KeyEvent.META_ALT_ON != 0
                if ((motionCtrlPressed || ctrlModifierHeld) && (motionAltPressed || altModifierHeld) && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)) {
                    beginBrushSizeDrag(inputPoint.first, inputPoint.second, if (activeTool == EditorTool.ERASER) eraserWidth else baseWidth)
                    return true
                }
                if (activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && activeLayerId in protectedLayerIds) {
                    activeTool = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                if (activeTool == EditorTool.ERASER) {
                    beginEraserGesture()
                    eraseAlong(normalizedX, normalizedY, event.getPressure(pointerIndex))
                    return true
                }
                if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                    return true
                }
                if (activeTool == EditorTool.SELECT) {
                    if (selectionShape == SelectionShape.SMART_BORDER || selectionShape == SelectionShape.MAGIC_WAND) {
                        lassoPoints.clear(); selectionGestureBefore = null; selectionMoved = false; movingSelection = false
                        appendSelectionPoint(normalizedX, normalizedY)
                        invalidate(); return true
                    }
                    if (!selectionTransformArmed) {
                        lassoPoints.clear(); selectionGestureBefore = null; selectionMoved = false; movingSelection = false
                        appendSelectionPoint(normalizedX, normalizedY)
                        lastX = normalizedX; lastY = normalizedY
                        invalidate(); return true
                    }
                    val handleMode = hitSelectionHandle(normalizedX, normalizedY)
                    if (handleMode != TRANSFORM_NONE) {
                        val bounds = selectionBounds()
                        if (bounds != null) {
                            selectionGestureBefore = strokes.toList()
                            selectionMoved = false
                            movingSelection = false
                            selectionTransformMode = handleMode
                            selectionTransformCenterX = bounds.centerX()
                            selectionTransformCenterY = bounds.centerY()
                            val dx = (normalizedX - selectionTransformCenterX) * pageModelWidth
                            val dy = (normalizedY - selectionTransformCenterY) * pageModelHeight
                            selectionTransformStartDistance = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(.0001f)
                            selectionTransformStartAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            selectionTransformStartXSpan = kotlin.math.abs(dx).coerceAtLeast(.0001f)
                            selectionTransformStartYSpan = kotlin.math.abs(dy).coerceAtLeast(.0001f)
                            lastX = normalizedX; lastY = normalizedY
                            invalidate(); return true
                        }
                    }
                    val hit = strokes.getOrNull(findStroke(normalizedX, normalizedY, .035f))
                    lassoPoints.clear(); selectionGestureBefore = null; selectionMoved = false
                    if (hit != null) {
                        if (hit.id !in selectedStrokeIds) {
                            selectedStrokeIds.clear(); selectedStrokeIds += hit.id
                            selectedMaskRegions = emptyList()
                            syncSelectionAlphaFromSelectedStrokes(resetFeather = true)
                        }
                        selectionGestureBefore = strokes.toList()
                        movingSelection = true
                    } else {
                        movingSelection = false
                        appendSelectionPoint(normalizedX, normalizedY)
                    }
                    lastX = normalizedX; lastY = normalizedY
                    invalidate(); return true
                }
                if (activeTool == EditorTool.SHAPE) {
                    val snapped = snapCanvasPoint(normalizedX, normalizedY)
                    shapeStart = ShapePoint(snapped.x, snapped.y)
                    shapeEnd = shapeStart
                    shapeFreeformPoints.clear()
                    if (shapeKind == ShapeKind.FREEFORM) appendShapePoint(normalizedX, normalizedY)
                    invalidate(); return true
                }
                if (activeTool == EditorTool.LASER) {
                    laserTrail.begin(normalizedX, normalizedY, event.eventTime)
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.FILL) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (fillSubTool == FillSubTool.BUCKET) beginFillGesture(normalizedX, normalizedY, event.x)
                    else {
                        fillGesturePoints.clear()
                        fillGesturePoints += StrokePoint(normalizedX, normalizedY, event.getPressure(pointerIndex), event.eventTime)
                    }
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.GRADIENT) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    gradientStart = ShapePoint(normalizedX, normalizedY)
                    gradientEnd = gradientStart
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.SMUDGE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    beginSmudgeGesture(normalizedX, normalizedY)
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.LIQUIFY) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    beginLiquifyGesture(normalizedX, normalizedY, event.getPressure(pointerIndex))
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.CORRECT_LINE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    beginCorrectLineGesture(normalizedX, normalizedY)
                    return true
                }
                clearActiveStrokeInput()
                beginActiveStrokePreview()
                currentStrokeId = java.util.UUID.randomUUID().toString()
                // Freeze the many brush-engine parameters once per contact. Live rendering then
                // copies only the changing point list instead of rebuilding the full descriptor
                // several times per display frame.
                activeStrokeTemplate = withCurrentBrushBehavior(buildStroke(emptyList())).copy(width = gestureBrushSize)
                beginWetPaintGesture(normalizedX, normalizedY)
                activePerspectiveSnapConstraint = null
                activeSpecialRulerConstraint = null
                activeCanvasSnapConstraint = if (!rulerEnabled && (!perspectiveGuideState.assisted || perspectiveGuideState.mode == PerspectiveGuideMode.NONE) && (!specialRulerState.assisted || specialRulerState.mode == SpecialRulerMode.NONE) && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
                    CanvasGuides.constraintAt(canvasGuideState, normalizedX, normalizedY, pageModelWidth, pageModelHeight, width.toFloat(), height.toFloat(), GUIDE_SNAP_TOLERANCE_DP * resources.displayMetrics.density)
                } else null
                updateContinuousPlotInput(normalizedX, normalizedY, event.getPressure(pointerIndex), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getOrientation(pointerIndex))
                addPoint(normalizedX, normalizedY, event.getPressure(pointerIndex), event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getOrientation(pointerIndex))
                if (brushContinuousPlot && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) startContinuousPlot()
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTool == EditorTool.FILL) {
                    if (fillSubTool == FillSubTool.BUCKET) adjustFillSensitivity(event.x)
                    else {
                        for (index in 0 until event.historySize) fillGesturePoints += StrokePoint(
                            event.getHistoricalX(pointerIndex, index) / width.coerceAtLeast(1),
                            event.getHistoricalY(pointerIndex, index) / height.coerceAtLeast(1),
                            event.getHistoricalPressure(pointerIndex, index),
                            event.getHistoricalEventTime(index),
                        )
                        fillGesturePoints += StrokePoint(normalizedX, normalizedY, event.getPressure(pointerIndex), event.eventTime)
                        invalidate()
                    }
                }
                else if (activeTool == EditorTool.GRADIENT) {
                    val start = gradientStart
                    gradientEnd = if (start == null) ShapePoint(normalizedX, normalizedY) else GradientGeometry.snapEnd(
                        start.x,
                        start.y,
                        normalizedX,
                        normalizedY,
                        width.toFloat(),
                        height.toFloat(),
                        gradientAngleSnapEnabled,
                        gradientAngleSnapDegrees,
                    ).let { ShapePoint(it.x, it.y) }
                    invalidate()
                }
                else if (activeTool == EditorTool.SMUDGE) {
                    scheduleSmudge(normalizedX, normalizedY)
                }
                else if (activeTool == EditorTool.LIQUIFY) {
                    scheduleLiquify(normalizedX, normalizedY, event.getPressure(pointerIndex))
                }
                else if (activeTool == EditorTool.CORRECT_LINE) {
                    updateCorrectLineGesture(normalizedX, normalizedY)
                }
                else if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                }
                else if (activeTool == EditorTool.ERASER) {
                    eraseMotionEvent(event, pointerIndex)
                }
                else if (activeTool == EditorTool.SELECT) {
                    if (selectionTransformMode == TRANSFORM_SCALE) {
                        val dx = (normalizedX - selectionTransformCenterX) * pageModelWidth
                        val dy = (normalizedY - selectionTransformCenterY) * pageModelHeight
                        val scale = (hypot(dx.toDouble(), dy.toDouble()).toFloat() / selectionTransformStartDistance).coerceAtLeast(.01f)
                        applySelectionTransform(scale, 0f)
                        selectionMoved = true
                        lastX = normalizedX; lastY = normalizedY
                        invalidate()
                    } else if (selectionTransformMode == TRANSFORM_SCALE_X) {
                        val dx = kotlin.math.abs((normalizedX - selectionTransformCenterX) * pageModelWidth)
                        applySelectionTransform(dx / selectionTransformStartXSpan, 1f, 0f)
                        selectionMoved = true
                        lastX = normalizedX; lastY = normalizedY
                        invalidate()
                    } else if (selectionTransformMode == TRANSFORM_SCALE_Y) {
                        val dy = kotlin.math.abs((normalizedY - selectionTransformCenterY) * pageModelHeight)
                        applySelectionTransform(1f, dy / selectionTransformStartYSpan, 0f)
                        selectionMoved = true
                        lastX = normalizedX; lastY = normalizedY
                        invalidate()
                    } else if (selectionTransformMode == TRANSFORM_ROTATE) {
                        val dx = (normalizedX - selectionTransformCenterX) * pageModelWidth
                        val dy = (normalizedY - selectionTransformCenterY) * pageModelHeight
                        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        applySelectionTransform(1f, normalizeAngleDelta(angle, selectionTransformStartAngle))
                        selectionMoved = true
                        lastX = normalizedX; lastY = normalizedY
                        invalidate()
                    } else if (movingSelection) {
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
                        val selectedPoints = strokes.asSequence().filter { it.id in selectedStrokeIds }.flatMap { it.points.asSequence() }.toList()
                        if (selectedPoints.isNotEmpty()) {
                            val correction = CanvasGuides.translationForBounds(
                                canvasGuideState,
                                selectedPoints.minOf(StrokePoint::x),
                                selectedPoints.minOf(StrokePoint::y),
                                selectedPoints.maxOf(StrokePoint::x),
                                selectedPoints.maxOf(StrokePoint::y),
                                pageModelWidth,
                                pageModelHeight,
                                width.toFloat(),
                                height.toFloat(),
                                GUIDE_SNAP_TOLERANCE_DP * resources.displayMetrics.density,
                            )
                            if (correction.x != 0f || correction.y != 0f) selectedStrokeIds.forEach { id ->
                                val index = strokes.indexOfFirst { it.id == id }
                                if (index >= 0) strokes[index] = strokes[index].copy(
                                    points = strokes[index].points.map { it.copy(x = (it.x + correction.x).coerceIn(0f, 1f), y = (it.y + correction.y).coerceIn(0f, 1f)) },
                                    masks = strokes[index].masks.map { it.copy(x = (it.x + correction.x).coerceIn(0f, 1f), y = (it.y + correction.y).coerceIn(0f, 1f)) },
                                )
                            }
                        }
                        lastX = normalizedX; lastY = normalizedY
                        invalidateStrokeCache(movedBefore, strokes.filter { it.id in selectedStrokeIds })
                    } else {
                        if (selectionShape == SelectionShape.FREEFORM || selectionShape == SelectionShape.BRUSH) {
                            for (i in 0 until event.historySize) historicalCanvasPoint(event, pointerIndex, i).let { point -> appendSelectionPoint(point.first / width.coerceAtLeast(1), point.second / height.coerceAtLeast(1)) }
                        }
                        appendSelectionPoint(normalizedX, normalizedY)
                    }
                }
                else if (activeTool == EditorTool.SHAPE) {
                    if (shapeKind == ShapeKind.FREEFORM) {
                        for (i in 0 until event.historySize) historicalCanvasPoint(event, pointerIndex, i).let { point -> appendShapePoint(point.first / width.coerceAtLeast(1), point.second / height.coerceAtLeast(1)) }
                        appendShapePoint(normalizedX, normalizedY)
                        shapeFreeformLastMoveAt = event.eventTime
                    } else {
                        val angled = ShapeGeometry.snapEnd(shapeStart ?: ShapePoint(normalizedX, normalizedY), ShapePoint(normalizedX, normalizedY), pageModelWidth, pageModelHeight)
                        val snapped = snapCanvasPoint(angled.x, angled.y)
                        shapeEnd = ShapePoint(snapped.x, snapped.y)
                    }
                }
                else if (activeTool == EditorTool.LASER) {
                    for (i in 0 until event.historySize) historicalCanvasPoint(event, pointerIndex, i).let { point -> laserTrail.append(
                        point.first / width.coerceAtLeast(1),
                        point.second / height.coerceAtLeast(1),
                        event.getHistoricalEventTime(i),
                    ) }
                    laserTrail.append(normalizedX, normalizedY, event.eventTime)
                }
                else {
                    updateContinuousPlotInput(normalizedX, normalizedY, event.getPressure(pointerIndex), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getOrientation(pointerIndex))
                    addMotionEventPoints(event, pointerIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopContinuousPlot()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    cancelWetPaintGesture()
                    clearActiveStrokeInput()
                    finishActiveStrokePreview()
                    if (activeTool == EditorTool.LASER) laserTrail.cancelActive()
                    if (activeTool == EditorTool.FILL) cancelFillGesture()
                    if (activeTool == EditorTool.GRADIENT) { gradientStart = null; gradientEnd = null }
                    if (activeTool == EditorTool.SMUDGE) cancelSmudgeGesture()
                    if (activeTool == EditorTool.LIQUIFY) cancelLiquifyGesture()
                    if (activeTool == EditorTool.CORRECT_LINE) cancelCorrectLineGesture()
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear()
                    if (activeTool == EditorTool.ERASER) cancelEraserGesture()
                    selectionGestureBefore?.let { restoreStrokeSnapshot(it) }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null; selectionTransformMode = TRANSFORM_NONE
                } else if (activeTool == EditorTool.FILL) {
                    if (fillSubTool == FillSubTool.BUCKET) commitFillGesture() else {
                        fillGesturePoints += StrokePoint(normalizedX, normalizedY, event.getPressure(pointerIndex), event.eventTime)
                        commitManualFillGesture()
                    }
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (activeTool == EditorTool.GRADIENT) {
                    val start = gradientStart
                    val end = gradientEnd
                    if (start != null && end != null && hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()) > .002) {
                        onGradientCommitted(start.x, start.y, end.x, end.y, gradientDefinition.normalized(), selectionMaskSnapshot())
                    }
                    gradientStart = null; gradientEnd = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (activeTool == EditorTool.SMUDGE) {
                    flushSmudgeFrame()
                    if (normalizedX != smudgeLastX || normalizedY != smudgeLastY) extendSmudge(normalizedX, normalizedY)
                    commitSmudgeGesture()
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (activeTool == EditorTool.LIQUIFY) {
                    flushLiquifyFrame()
                    if (normalizedX != liquifyLastX || normalizedY != liquifyLastY) extendLiquify(normalizedX, normalizedY, event.getPressure(pointerIndex))
                    finishLiquifyGesture()
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (activeTool == EditorTool.CORRECT_LINE) {
                    finishCorrectLineGesture()
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                } else if (activeTool == EditorTool.ERASER) {
                    eraseAlong(normalizedX, normalizedY, event.getPressure(pointerIndex))
                    finishEraserGesture()
                } else if (activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && currentPoints.isNotEmpty()) {
                    // ACTION_UP can carry the only sample produced by a very fast final motion.
                    // Settle the modeled tail onto the physical pen before committing so the
                    // stored trajectory and its live cursor share the same endpoint.
                    addPoint(
                        normalizedX,
                        normalizedY,
                        event.getPressure(pointerIndex),
                        event.eventTime,
                        event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
                        event.getOrientation(pointerIndex),
                        force = true,
                    )
                    // Publish the final pickup-well color before freezing the committed points.
                    // Otherwise the live preview is correct but the last dab replays as fresh paint.
                    advanceWetPaintGesture()
                    val before = strokes.toList()
                    val newStrokes = symmetryStrokes(currentPoints).map { stroke ->
                        StrokePostCorrection.apply(
                            stroke.copy(grainCompositeMode = grainCompositeMode),
                            postCorrection,
                            postCorrectionAdjustBySpeed,
                            postCorrectionAdjustByScale,
                            viewportScale,
                            pageModelWidth,
                            pageModelHeight,
                            postCorrectionBezier,
                            correctionSharpAngles,
                        )
                    }
                    val raster = rasterEditBitmapFor(activeLayerId)
                    val incrementalRasterCommit = if (raster != null) {
                        commitIncrementalRasterStampGesture(newStrokes).also { committedIncrementally ->
                            if (!committedIncrementally) commitRasterPaintStrokes(raster, newStrokes)
                        }
                    } else {
                        strokes.addAll(newStrokes)
                        invalidateStrokeCache(emptyList(), newStrokes)
                        commitStrokeEdit(before, strokes)
                        false
                    }
                    finishWetPaintGesture()
                    val previewSurface = activeStrokeSurface?.takeUnless { usesViewActivePreview() }
                    if (incrementalRasterCommit) {
                        // The stable tile projection now contains the exact front-buffer dabs.
                        // Committing the transient surface as well would alpha-compose them twice.
                        previewSurface?.cancel()
                    } else previewSurface?.let { surface ->
                        // The UP packet settles stabilization onto the physical endpoint. Publish
                        // that exact final packet before committing the low-latency surface.
                        surface.render(newStrokes)
                        surface.commit(newStrokes)
                    }
                    activeStrokeSurface?.takeIf { previewSurface == null }?.cancel()
                    activeSurfaceCommitted = previewSurface != null && !incrementalRasterCommit
                    clearActiveStrokeInput()
                    finishActiveStrokePreview()
                } else if (activeTool == EditorTool.SELECT) {
                    if ((movingSelection || selectionTransformMode != TRANSFORM_NONE) && selectionMoved) {
                        selectionGestureBefore?.let { commitStrokeEdit(it, strokes) }
                        syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
                    } else if (!movingSelection && selectionTransformMode == TRANSFORM_NONE) {
                        if (selectionShape == SelectionShape.SMART_BORDER || selectionShape == SelectionShape.MAGIC_WAND) {
                            selectAutomaticRegion(normalizedX, normalizedY, selectionShape == SelectionShape.SMART_BORDER)
                        } else {
                            appendSelectionPoint(normalizedX, normalizedY)
                            selectWithLasso(selectionPolygon())
                        }
                    }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null; selectionTransformMode = TRANSFORM_NONE
                } else if (activeTool == EditorTool.SHAPE) {
                    val shape = if (shapeKind == ShapeKind.FREEFORM) {
                        appendShapePoint(normalizedX, normalizedY)
                        // QuickShape: when the freehand gesture ends with a brief hold,
                        // snap the rough path to a perfect line/rectangle/ellipse.
                        val held = shapeFreeformLastMoveAt > 0L && event.eventTime - shapeFreeformLastMoveAt >= QUICKSHAPE_HOLD_MS
                        val recognized = if (held) ShapeGeometry.quickShape(shapeFreeformPoints, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), gestureBrushSize) else null
                        recognized ?: ShapeGeometry.createFreeform(shapeFreeformPoints, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), gestureBrushSize)
                    } else {
                        val start = shapeStart
                        val end = shapeEnd
                        if (start != null && end != null) ShapeGeometry.create(shapeKind, start, end, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), gestureBrushSize) else null
                    }
                    shape?.let(onShapeCommitted)
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear(); shapeFreeformLastMoveAt = 0L
                } else if (activeTool == EditorTool.LASER) {
                    laserTrail.append(normalizedX, normalizedY, event.eventTime)
                    laserTrail.end()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                activeCanvasSnapConstraint = null
                activePerspectiveSnapConstraint = null
                activeSpecialRulerConstraint = null
                activeTool = null
                activeInputWasStylus = false
            }
        }
        if (isStylus && event.actionMasked == MotionEvent.ACTION_CANCEL) {
            releaseHeldStylusButtons()
            lastStylusButtonState = 0
        }
        if (activeStrokeSurface == null || activeTool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) postInvalidateOnAnimation()
        return true
    }

    private fun sampleRenderedColor(x: Float, y: Float): Int {
        colorSampleBitmap.eraseColor(Color.TRANSPARENT)
        val sampleCanvas = Canvas(colorSampleBitmap)
        sampleCanvas.translate(-x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat()), -y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat()))
        drawPageContent(sampleCanvas)
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

    private fun drawFillPreview(canvas: Canvas) {
        val mask = fillPreviewMask
        if (mask == null) {
            if (fillGesturePoints.size < 2) return
            lassoPath.reset()
            lassoPath.moveTo(fillGesturePoints.first().x * width, fillGesturePoints.first().y * height)
            fillGesturePoints.drop(1).forEach { lassoPath.lineTo(it.x * width, it.y * height) }
            paint.color = inkColor
            paint.alpha = (inkOpacity * 150f).toInt().coerceIn(0, 255)
            paint.style = if (fillSubTool == FillSubTool.LEFTOVER_PEN) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = gestureBrushSize.coerceAtLeast(.1f) * width / pageModelWidth.coerceAtLeast(1f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            if (fillSubTool != FillSubTool.LEFTOVER_PEN) lassoPath.close()
            canvas.drawPath(lassoPath, paint)
            paint.alpha = 255
            return
        }
        if (fillPreviewWidth <= 0f || fillPreviewHeight <= 0f) return
        fillPreviewPaint.colorFilter = PorterDuffColorFilter(inkColor, PorterDuff.Mode.SRC_IN)
        fillPreviewPaint.alpha = (inkOpacity * 255f).toInt().coerceIn(0, 255)
        backgroundDestination.set(
            (fillPreviewLeft * width).toInt(),
            (fillPreviewTop * height).toInt(),
            ((fillPreviewLeft + fillPreviewWidth) * width).toInt(),
            ((fillPreviewTop + fillPreviewHeight) * height).toInt(),
        )
        canvas.drawBitmap(mask, null, backgroundDestination, fillPreviewPaint)
    }

    private fun documentBrushSize(authoredSize: Float = baseWidth): Float =
        CspScreenBrushSize.documentSize(authoredSize, viewportScale, brushSizeSyncViewScale)

    private fun documentEraserSize(authoredSize: Float = eraserWidth): Float =
        CspScreenBrushSize.documentSize(authoredSize, viewportScale, brushSizeSyncViewScale)

    private fun drawBrushSizePreview(canvas: Canvas) {
        if (!brushSizeDragActive) return
        val documentSize = if (activeTool == EditorTool.ERASER) documentEraserSize(brushSizeDragCurrentSize) else documentBrushSize(brushSizeDragCurrentSize)
        val radius = (documentSize * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f).coerceAtLeast(3f / viewportScale.coerceAtLeast(1f))
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

    private fun drawStylusHoverCursor(canvas: Canvas) {
        if (externalHoverCursor || !hoverCursorVisible || brushSizeDragActive) return
        val footprint = hoverCursorFootprint()
        val zoom = viewportScale.coerceAtLeast(1f)
        val radiusX = (footprint.first / 2f).coerceAtLeast(1.5f / zoom)
        val radiusY = (footprint.second / 2f).coerceAtLeast(1.5f / zoom)
        val checkpoint = canvas.save()
        canvas.rotate(hoverCursorPageRotation(), hoverCursorX, hoverCursorY)
        hoverCursorPaint.strokeWidth = 3.5f / zoom
        hoverCursorPaint.color = Color.WHITE
        hoverCursorPaint.alpha = 235
        canvas.drawOval(hoverCursorX - radiusX, hoverCursorY - radiusY, hoverCursorX + radiusX, hoverCursorY + radiusY, hoverCursorPaint)
        hoverCursorPaint.strokeWidth = 1.5f / zoom
        hoverCursorPaint.color = Color.BLACK
        hoverCursorPaint.alpha = 235
        canvas.drawOval(hoverCursorX - radiusX, hoverCursorY - radiusY, hoverCursorX + radiusX, hoverCursorY + radiusY, hoverCursorPaint)
        canvas.restoreToCount(checkpoint)
        drawSmallCursorOrigin(canvas, footprint.first * zoom, footprint.second * zoom, zoom)
    }

    /** The full-viewport proxy owns the visible cursor in the editor. Do not invalidate the
     * expensive stable page beneath it for every hover/contact packet. */
    private fun invalidateInternalHoverCursor() {
        if (!externalHoverCursor) postInvalidateOnAnimation()
    }

    internal fun hoverCursorSnapshot(): FloatArray {
        val footprint = hoverCursorFootprint()
        return floatArrayOf(
            if (hoverCursorVisible) 1f else 0f,
            hoverCursorX,
            hoverCursorY,
            footprint.first,
            footprint.second,
            if (hoverCursorUsesEraser) 0f else BrushCursorGeometry.fixedScreenRotation(brushRotation, viewportRotationDegrees, shapeOrientToScreen),
            if (!hoverCursorUsesEraser && followStylusOrientation) 1f else 0f,
            if (!hoverCursorUsesEraser && followStrokeDirection) 1f else if (!hoverCursorUsesEraser) shapeTouchRotation.coerceIn(-1f, 1f) else 0f,
        )
    }

    /** The cursor follows the raw stylus contact, never the stabilized/modelled tail. This is the
     * same no-delay contract as professional drawing applications: stabilization may trail, but
     * the physical pen location must remain truthful. */
    private fun updateContactCursor(event: MotionEvent, pointerIndex: Int, isStylus: Boolean) {
        if (!isStylus) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val cursorTool = if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER) EditorTool.ERASER else resolveTool(event, event.getToolType(pointerIndex))
                if (cursorTool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY)) return
                val point = canvasPoint(event, pointerIndex)
                updateHoverCursorPacket(
                    x = point.first,
                    y = point.second,
                    pressure = event.getPressure(pointerIndex).takeIf { it.isFinite() && it > 0f }?.coerceIn(0f, 1f) ?: 1f,
                    tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex),
                    orientationRadians = event.getOrientation(pointerIndex),
                    usesEraser = cursorTool == EditorTool.ERASER,
                    resetDirection = event.actionMasked in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN) || !hoverCursorVisible,
                )
                invalidateInternalHoverCursor()
            }
            MotionEvent.ACTION_CANCEL -> {
                hoverCursorVisible = false
                invalidateInternalHoverCursor()
            }
        }
    }

    private fun hoverCursorFootprint(): Pair<Float, Float> {
        val scale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val calibratedPressure = PressureCalibration.apply(
            hoverCursorPressure,
            pressureMinimumInput,
            pressureMaximumInput,
            pressureSensitivity,
            pressureResponseCurve,
        )
        val cursorPoint = StrokePoint(
            x = 0f,
            y = 0f,
            pressure = calibratedPressure,
            tilt = hoverCursorTilt,
            orientation = hoverCursorOrientationRadians,
        )
        val configuredDynamics = configuredBrushTemplate?.let { template ->
            configuredBrushProgram?.evaluateDab(template, cursorPoint, dual = false)
        }
        if (hoverCursorUsesEraser) {
            val pressure = if (configuredInputTool == EditorTool.ERASER && configuredDynamics != null) {
                configuredDynamics.size
            } else if (eraserPressureSize) calibratedPressure.coerceIn(.15f, 1f) else 1f
            val diameter = documentEraserSize().coerceIn(.01f, 800f) * scale * pressure
            val tip = activeCursorTip(eraserTipAssetPath)
            val footprint = BrushCursorGeometry.footprint(
                diameter = diameter,
                tipWidth = tip?.width,
                tipHeight = tip?.height,
                thickness = if (tip == null) 1f else brushThickness * (configuredDynamics?.thickness ?: 1f),
                thicknessVertical = brushThicknessVertical,
            )
            return footprint.width to footprint.height
        }
        val pressure = configuredDynamics?.size
            ?: BrushDynamics.pressure(sizePressureEnabled, sizePressureMinimum, sizePressureCurve, calibratedPressure)
        val diameter = documentBrushSize().coerceIn(.01f, 800f) * scale * pressure
        val thicknessPressure = configuredDynamics?.thickness
            ?: BrushDynamics.pressure(thicknessPressureEnabled, thicknessPressureMinimum, thicknessPressureCurve, calibratedPressure)
        val tip = activeCursorTip(tipAssetPaths.firstOrNull() ?: tipAssetPath)
        val footprint = BrushCursorGeometry.footprint(
            diameter = diameter,
            tipWidth = tip?.width,
            tipHeight = tip?.height,
            thickness = if (tip == null && tipAssetPath == null && tipAssetPaths.isEmpty()) 1f else brushThickness * thicknessPressure,
            thicknessVertical = brushThicknessVertical,
        )
        return footprint.width to footprint.height
    }

    private fun activeCursorTip(path: String?): Bitmap? {
        path ?: return null
        val exactRequest = tipRequest(
            path, brushHardness, grainAssetPath, grainDensity, grainScale, grainRotationDegrees,
            grainBrightness, grainContrast, grainReversed, grainEmphasized, 0f, 0f,
            grainCompositeMode, grainFilteringMode, brushAntiAliasMode, alphaThresholdEnabled, alphaThreshold,
        )
        return brushResources.tip(exactRequest) ?: brushResources.tipForPath(path)
    }

    private fun updateHoverCursorPacket(
        x: Float,
        y: Float,
        pressure: Float,
        tilt: Float,
        orientationRadians: Float,
        usesEraser: Boolean,
        resetDirection: Boolean,
    ) {
        if (!resetDirection) {
            val deltaX = x - hoverCursorLastX
            val deltaY = y - hoverCursorLastY
            if (BrushCursorGeometry.hasMeaningfulDirection(deltaX, deltaY)) {
                hoverCursorDirectionDegrees = Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat()
                hoverCursorHasDirection = true
            }
        } else {
            hoverCursorHasDirection = false
            hoverCursorDirectionDegrees = 0f
        }
        hoverCursorLastX = x
        hoverCursorLastY = y
        hoverCursorX = x
        hoverCursorY = y
        hoverCursorUsesEraser = usesEraser
        hoverCursorPressure = pressure
        hoverCursorTilt = tilt.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        hoverCursorOrientationRadians = orientationRadians.takeIf(Float::isFinite) ?: 0f
        hoverCursorVisible = true
    }

    private fun hoverCursorPageRotation(): Float {
        if (hoverCursorUsesEraser) return 0f
        val fixed = brushRotation + if (shapeOrientToScreen) -viewportRotationDegrees else 0f
        val dynamic = when {
            followStylusOrientation -> Math.toDegrees(hoverCursorOrientationRadians.toDouble()).toFloat()
            hoverCursorHasDirection && followStrokeDirection -> hoverCursorDirectionDegrees
            hoverCursorHasDirection -> hoverCursorDirectionDegrees * shapeTouchRotation.coerceIn(-1f, 1f)
            else -> 0f
        }
        return BrushCursorGeometry.normalizeDegrees(fixed, dynamic)
    }

    private fun drawSmallCursorOrigin(canvas: Canvas, screenWidth: Float, screenHeight: Float, zoom: Float) {
        if (minOf(screenWidth, screenHeight) >= SMALL_CURSOR_MARK_THRESHOLD_PX) return
        hoverCursorPaint.style = Paint.Style.FILL
        hoverCursorPaint.color = Color.WHITE
        hoverCursorPaint.alpha = 245
        canvas.drawCircle(hoverCursorX, hoverCursorY, 3f / zoom, hoverCursorPaint)
        hoverCursorPaint.color = Color.BLACK
        canvas.drawCircle(hoverCursorX, hoverCursorY, 1.5f / zoom, hoverCursorPaint)
        hoverCursorPaint.style = Paint.Style.STROKE
    }

    fun notifyHoverCursorStyleChanged() {
        if (hoverCursorVisible) invalidateInternalHoverCursor()
        onHoverCursorStyleChanged?.invoke()
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

    private fun MotionEvent.localSpan(): Float {
        if (pointerCount < 2) return 0f
        return hypot(getX(1) - getX(0), getY(1) - getY(0))
    }

    private fun MotionEvent.stylusPointerIndex(preferredPointerId: Int = MotionEvent.INVALID_POINTER_ID): Int {
        if (preferredPointerId != MotionEvent.INVALID_POINTER_ID) {
            val preferred = findPointerIndex(preferredPointerId)
            if (preferred >= 0 && getToolType(preferred) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)) return preferred
        }
        val action = actionIndex.coerceIn(0, pointerCount - 1)
        if (getToolType(action) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)) return action
        return (0 until pointerCount).firstOrNull { getToolType(it) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER) } ?: -1
    }

    private fun canvasPoint(event: MotionEvent, pointerIndex: Int = event.actionIndex.coerceIn(0, event.pointerCount - 1)): Pair<Float, Float> {
        return event.getX(pointerIndex) to event.getY(pointerIndex)
    }

    private fun historicalCanvasPoint(event: MotionEvent, pointerIndex: Int, position: Int): Pair<Float, Float> {
        return event.getHistoricalX(pointerIndex, position) to event.getHistoricalY(pointerIndex, position)
    }

    private fun cancelActiveGestureForNavigation() {
        stopContinuousPlot()
        cancelWetPaintGesture()
        clearActiveStrokeInput()
        finishActiveStrokePreview()
        laserTrail.cancelActive()
        cancelFillGesture()
        cancelSmudgeGesture()
        cancelLiquifyGesture()
        cancelCorrectLineGesture()
        rulerGestureStart?.let { rulerGuide = it }
        rulerGestureMode = null
        rulerGestureStart = null
        canvasGuideGestureStart?.let { canvasGuideState = it }
        canvasGuideGestureMode = null
        canvasGuideGestureStart = null
        activeCanvasSnapConstraint = null
        perspectiveGuideGestureStart?.let { perspectiveGuideState = it }
        perspectiveGuideGesturePoint = null
        perspectiveGuideGestureStart = null
        activePerspectiveSnapConstraint = null
        specialRulerGestureStart?.let { specialRulerState = it }
        specialRulerGestureHandle = null
        specialRulerGestureStart = null
        activeSpecialRulerConstraint = null
        if (gradientGestureHandle >= 0) {
            gradientGestureStart?.let { editableGradient = it }
            gradientGestureHandle = -1
            gradientGestureStart = null
        }
        shapeStart = null
        shapeEnd = null
        gradientStart = null
        gradientEnd = null
        shapeFreeformPoints.clear()
        if (activeTool == EditorTool.ERASER) cancelEraserGesture()
        selectionGestureBefore?.let(::restoreStrokeSnapshot)
        lassoPoints.clear()
        movingSelection = false
        selectionMoved = false
        selectionGestureBefore = null
        selectionTransformMode = TRANSFORM_NONE
        activeTool = null
        activeInputWasStylus = false
        parent?.requestDisallowInterceptTouchEvent(false)
        invalidate()
    }

    fun hasVisibleLaserTrail(nowMs: Long = SystemClock.uptimeMillis()): Boolean = laserTrail.renderPoints(nowMs).isNotEmpty()

    private fun drawLaserTrail(canvas: Canvas, nowMs: Long) {
        val points = laserTrail.renderPoints(nowMs)
        if (points.isEmpty()) return
        val scale = resources.displayMetrics.density.coerceAtLeast(1f)
        laserGlowPaint.strokeWidth = 18f * scale
        laserCorePaint.strokeWidth = 4f * scale
        points.zipWithNext().forEach { (start, end) ->
            if (start.gestureId != end.gestureId) return@forEach
            val alpha = (minOf(start.alpha, end.alpha) * 255f).toInt().coerceIn(1, 255)
            laserGlowPaint.alpha = (alpha * .34f).toInt().coerceIn(1, 255)
            laserCorePaint.alpha = alpha
            val startX = start.x * width
            val startY = start.y * height
            val endX = end.x * width
            val endY = end.y * height
            canvas.drawLine(startX, startY, endX, endY, laserGlowPaint)
            canvas.drawLine(startX, startY, endX, endY, laserCorePaint)
        }
        val head = points.last()
        laserGlowPaint.alpha = (head.alpha * 100f).toInt().coerceIn(1, 255)
        laserCorePaint.alpha = (head.alpha * 255f).toInt().coerceIn(1, 255)
        canvas.drawCircle(head.x * width, head.y * height, 9f * scale, laserGlowPaint)
        canvas.drawCircle(head.x * width, head.y * height, 2f * scale, laserCorePaint)
        postInvalidateOnAnimation()
    }

    private fun handleCanvasGuideGesture(event: MotionEvent, canvasX: Float, canvasY: Float, isStylus: Boolean, elapsedSinceStylus: Long): Boolean {
        if (isStylus || !canvasGuideState.alignmentGuidesEnabled) return false
        val existing = canvasGuideGestureMode
        if (existing == null) {
            if (forceNavigation || event.actionMasked != MotionEvent.ACTION_DOWN || event.pointerCount != 1 ||
                elapsedSinceStylus <= StylusInputClassifier.RECENT_STYLUS_WINDOW_MS ||
                event.getTouchMajor(event.actionIndex) >= StylusInputClassifier.PALM_TOUCH_MAJOR_PX
            ) return false
            val hit = CanvasGuides.hitTest(canvasGuideState, canvasX, canvasY, width.toFloat(), height.toFloat(), GUIDE_TOUCH_RADIUS_DP * resources.displayMetrics.density) ?: return false
            canvasGuideGestureMode = hit
            canvasGuideGestureStart = canvasGuideState
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                canvasGuideState = CanvasGuides.move(canvasGuideState, existing, canvasX, canvasY, width.toFloat(), height.toFloat())
            }
            MotionEvent.ACTION_UP -> {
                onCanvasGuideChanged(canvasGuideState)
                canvasGuideGestureMode = null
                canvasGuideGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                canvasGuideGestureStart?.let { canvasGuideState = it }
                canvasGuideGestureMode = null
                canvasGuideGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    private fun drawSnapGrid(canvas: Canvas) {
        val state = canvasGuideState.normalized()
        if (!state.gridSnapEnabled || width <= 0 || height <= 0 || pageModelWidth <= 0f || pageModelHeight <= 0f) return
        val stepX = state.gridSpacing / pageModelWidth * width
        val stepY = state.gridSpacing / pageModelHeight * height
        if (stepX < 2f || stepY < 2f) return
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), snapGridPaint)
            x += stepX
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, snapGridPaint)
            y += stepY
        }
    }

    private fun drawAlignmentGuides(canvas: Canvas) {
        val state = canvasGuideState.normalized()
        if (!state.alignmentGuidesEnabled || width <= 0 || height <= 0) return
        val x = state.verticalGuideX * width
        val y = state.horizontalGuideY * height
        val radius = 8f * resources.displayMetrics.density
        canvas.drawLine(x, 0f, x, height.toFloat(), alignmentGuidePaint)
        canvas.drawLine(0f, y, width.toFloat(), y, alignmentGuidePaint)
        canvas.drawCircle(x, radius * 1.6f, radius, alignmentGuideHandlePaint)
        canvas.drawCircle(radius * 1.6f, y, radius, alignmentGuideHandlePaint)
        canvas.drawCircle(x, radius * 1.6f, radius, alignmentGuidePaint)
        canvas.drawCircle(radius * 1.6f, y, radius, alignmentGuidePaint)
    }

    private fun handlePerspectiveGuideGesture(event: MotionEvent, canvasX: Float, canvasY: Float, isStylus: Boolean, elapsedSinceStylus: Long): Boolean {
        val state = perspectiveGuideState
        if (!state.editing || state.mode == PerspectiveGuideMode.NONE) return false
        val existing = perspectiveGuideGesturePoint
        if (existing == null) {
            if (forceNavigation || event.actionMasked != MotionEvent.ACTION_DOWN || event.pointerCount != 1 ||
                (!isStylus && elapsedSinceStylus <= StylusInputClassifier.RECENT_STYLUS_WINDOW_MS) ||
                (!isStylus && event.getTouchMajor(event.actionIndex) >= StylusInputClassifier.PALM_TOUCH_MAJOR_PX)
            ) return false
            val hit = PerspectiveGuides.hitPoint(state, canvasX, canvasY, width.toFloat(), height.toFloat(), PERSPECTIVE_TOUCH_RADIUS_DP * resources.displayMetrics.density) ?: return false
            perspectiveGuideGesturePoint = hit
            perspectiveGuideGestureStart = state
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && width > 0 && height > 0) {
                perspectiveGuideState = perspectiveGuideState.movePoint(existing, canvasX / width, canvasY / height)
            }
            MotionEvent.ACTION_UP -> {
                onPerspectiveGuideChanged(perspectiveGuideState)
                perspectiveGuideGesturePoint = null
                perspectiveGuideGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                perspectiveGuideGestureStart?.let { perspectiveGuideState = it }
                perspectiveGuideGesturePoint = null
                perspectiveGuideGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    private fun handleSpecialRulerGesture(event: MotionEvent, canvasX: Float, canvasY: Float, isStylus: Boolean, elapsedSinceStylus: Long): Boolean {
        val state = specialRulerState
        if (!state.editing || state.mode == SpecialRulerMode.NONE) return false
        val existing = specialRulerGestureHandle
        if (existing == null) {
            if (forceNavigation || event.actionMasked != MotionEvent.ACTION_DOWN || event.pointerCount != 1 ||
                (!isStylus && elapsedSinceStylus <= StylusInputClassifier.RECENT_STYLUS_WINDOW_MS) ||
                (!isStylus && event.getTouchMajor(event.actionIndex) >= StylusInputClassifier.PALM_TOUCH_MAJOR_PX)
            ) return false
            val density = resources.displayMetrics.density
            val hit = SpecialRulers.hitHandle(
                state,
                canvasX,
                canvasY,
                width.toFloat(),
                height.toFloat(),
                PERSPECTIVE_TOUCH_RADIUS_DP * density,
                SPECIAL_RULER_ROTATION_HANDLE_DP * density,
            ) ?: return false
            specialRulerGestureHandle = hit
            specialRulerGestureStart = state
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                specialRulerState = SpecialRulers.moveHandle(specialRulerState, existing, canvasX, canvasY, width.toFloat(), height.toFloat())
            }
            MotionEvent.ACTION_UP -> {
                onSpecialRulerChanged(specialRulerState)
                specialRulerGestureHandle = null
                specialRulerGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                specialRulerGestureStart?.let { specialRulerState = it }
                specialRulerGestureHandle = null
                specialRulerGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    private fun drawSpecialRulerGuide(canvas: Canvas) {
        val state = specialRulerState.normalized()
        if (state.mode == SpecialRulerMode.NONE || width <= 0 || height <= 0) return
        val centerX = state.centerX * width
        val centerY = state.centerY * height
        val spacingPx = (state.spacing / pageModelWidth.coerceAtLeast(1f) * width).coerceAtLeast(8f)
        when (state.mode) {
            SpecialRulerMode.NONE -> Unit
            SpecialRulerMode.PARALLEL -> drawParallelGuideFamily(canvas, centerX, centerY, state.angleDegrees, spacingPx)
            SpecialRulerMode.RADIAL -> {
                val reach = hypot(width.toDouble(), height.toDouble()).toFloat() * 1.6f
                repeat(24) { index ->
                    val radians = Math.toRadians(index * 15.0)
                    canvas.drawLine(
                        centerX - cos(radians).toFloat() * reach,
                        centerY - sin(radians).toFloat() * reach,
                        centerX + cos(radians).toFloat() * reach,
                        centerY + sin(radians).toFloat() * reach,
                        perspectiveGuidePaint,
                    )
                }
            }
            SpecialRulerMode.CONCENTRIC -> {
                val reach = hypot(maxOf(centerX, width - centerX).toDouble(), maxOf(centerY, height - centerY).toDouble()).toFloat()
                var radius = spacingPx
                while (radius <= reach * 1.2f) {
                    canvas.drawCircle(centerX, centerY, radius, perspectiveGuidePaint)
                    radius += spacingPx
                }
            }
            SpecialRulerMode.ISOMETRIC -> {
                drawParallelGuideFamily(canvas, centerX, centerY, state.angleDegrees + 30f, spacingPx)
                drawParallelGuideFamily(canvas, centerX, centerY, state.angleDegrees + 90f, spacingPx)
                drawParallelGuideFamily(canvas, centerX, centerY, state.angleDegrees + 150f, spacingPx)
            }
        }
        if (state.editing) {
            val density = resources.displayMetrics.density
            val radius = 8f * density
            canvas.drawCircle(centerX, centerY, radius, perspectiveHandlePaint)
            canvas.drawCircle(centerX, centerY, radius, perspectiveGuidePaint)
            if (state.mode != SpecialRulerMode.RADIAL && state.mode != SpecialRulerMode.CONCENTRIC) {
                val rotation = SpecialRulers.rotationHandle(state, width.toFloat(), height.toFloat(), SPECIAL_RULER_ROTATION_HANDLE_DP * density)
                canvas.drawLine(centerX, centerY, rotation.x, rotation.y, perspectiveGuidePaint)
                canvas.drawCircle(rotation.x, rotation.y, radius, perspectiveHandlePaint)
                canvas.drawCircle(rotation.x, rotation.y, radius, perspectiveGuidePaint)
            }
        }
    }

    private fun drawParallelGuideFamily(canvas: Canvas, centerX: Float, centerY: Float, angleDegrees: Float, spacingPx: Float) {
        val reach = hypot(width.toDouble(), height.toDouble()).toFloat() * 1.5f
        val checkpoint = canvas.save()
        canvas.rotate(angleDegrees, centerX, centerY)
        var offset = -reach
        while (offset <= reach) {
            canvas.drawLine(centerX - reach, centerY + offset, centerX + reach, centerY + offset, perspectiveGuidePaint)
            offset += spacingPx
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun drawPerspectiveGuide(canvas: Canvas) {
        val state = perspectiveGuideState.normalized()
        if (state.mode == PerspectiveGuideMode.NONE || width <= 0 || height <= 0) return
        val targets = listOf(
            0f to 0f,
            width.toFloat() to 0f,
            0f to height.toFloat(),
            width.toFloat() to height.toFloat(),
            width / 2f to 0f,
            width / 2f to height.toFloat(),
        )
        val points = state.activePoints()
        points.forEach { point ->
            val x = point.x * width
            val y = point.y * height
            targets.forEach { target -> canvas.drawLine(x, y, target.first, target.second, perspectiveGuidePaint) }
        }
        if (points.size >= 2) {
            canvas.drawLine(points[0].x * width, points[0].y * height, points[1].x * width, points[1].y * height, perspectiveGuidePaint)
        }
        if (state.editing) {
            val radius = 8f * resources.displayMetrics.density
            points.forEachIndexed { index, point ->
                val x = point.x * width
                val y = point.y * height
                canvas.drawCircle(x, y, radius, perspectiveHandlePaint)
                perspectiveGuidePaint.style = Paint.Style.STROKE
                perspectiveGuidePaint.strokeWidth = 2f * resources.displayMetrics.density
                canvas.drawCircle(x, y, radius, perspectiveGuidePaint)
                perspectiveGuidePaint.style = Paint.Style.FILL
                perspectiveGuidePaint.textSize = 10f * resources.displayMetrics.scaledDensity
                perspectiveGuidePaint.textAlign = Paint.Align.CENTER
                canvas.drawText((index + 1).toString(), x, y + perspectiveGuidePaint.textSize * .35f, perspectiveGuidePaint)
                perspectiveGuidePaint.style = Paint.Style.STROKE
                perspectiveGuidePaint.strokeWidth = 1.5f
            }
        }
    }

    private fun snapCanvasPoint(x: Float, y: Float) = CanvasGuides.snapPoint(
        canvasGuideState,
        x,
        y,
        pageModelWidth,
        pageModelHeight,
        width.toFloat(),
        height.toFloat(),
        GUIDE_SNAP_TOLERANCE_DP * resources.displayMetrics.density,
    )

    private fun gradientHandlePoint(element: PageElement.Shape, isStart: Boolean): Pair<Float, Float> {
        val nx = if (isStart) element.gradientStartX else element.gradientEndX
        val ny = if (isStart) element.gradientStartY else element.gradientEndY
        val pageNx = element.x + nx * element.width / pageModelWidth.coerceAtLeast(1f)
        val pageNy = element.y + ny * element.height / pageModelHeight.coerceAtLeast(1f)
        return pageNx * width to pageNy * height
    }

    private fun hitGradientHandle(element: PageElement.Shape, canvasX: Float, canvasY: Float, touchRadiusPx: Float): Int {
        val (sx, sy) = gradientHandlePoint(element, true)
        val (ex, ey) = gradientHandlePoint(element, false)
        return when {
            hypot((canvasX - sx).toDouble(), (canvasY - sy).toDouble()) <= touchRadiusPx -> 0
            hypot((canvasX - ex).toDouble(), (canvasY - ey).toDouble()) <= touchRadiusPx -> 1
            else -> -1
        }
    }

    private fun drawGradientHandles(canvas: Canvas) {
        val element = editableGradient ?: return
        if (width <= 0 || height <= 0) return
        val (sx, sy) = gradientHandlePoint(element, true)
        val (ex, ey) = gradientHandlePoint(element, false)
        val density = resources.displayMetrics.density
        val radius = 9f * density
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(24, 119, 242)
        paint.alpha = 255
        paint.strokeWidth = 2f
        canvas.drawLine(sx, sy, ex, ey, paint)
        listOf(sx to sy, ex to ey).forEach { (hx, hy) ->
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(hx, hy, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(24, 119, 242)
            paint.strokeWidth = 3f
            canvas.drawCircle(hx, hy, radius, paint)
        }
    }

    private fun handleGradientGesture(event: MotionEvent, canvasX: Float, canvasY: Float, isStylus: Boolean, elapsedSinceStylus: Long): Boolean {
        val element = editableGradient ?: return false
        val existing = gradientGestureHandle
        if (existing < 0) {
            if (forceNavigation || event.actionMasked != MotionEvent.ACTION_DOWN || event.pointerCount != 1 ||
                (!isStylus && elapsedSinceStylus <= StylusInputClassifier.RECENT_STYLUS_WINDOW_MS) ||
                (!isStylus && event.getTouchMajor(event.actionIndex) >= StylusInputClassifier.PALM_TOUCH_MAJOR_PX)
            ) return false
            val touchRadius = PERSPECTIVE_TOUCH_RADIUS_DP * resources.displayMetrics.density
            val hit = hitGradientHandle(element, canvasX, canvasY, touchRadius)
            if (hit < 0) return false
            gradientGestureHandle = hit
            gradientGestureStart = element
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        val start = gradientGestureStart ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                val pageNx = (canvasX / width.toFloat()).coerceIn(0f, 1f)
                val pageNy = (canvasY / height.toFloat()).coerceIn(0f, 1f)
                val localX = ((pageNx - start.x) / (start.width / pageModelWidth.coerceAtLeast(1f))).coerceIn(0f, 1f)
                val localY = ((pageNy - start.y) / (start.height / pageModelHeight.coerceAtLeast(1f))).coerceIn(0f, 1f)
                editableGradient = if (existing == 0) {
                    start.copy(gradientStartX = localX, gradientStartY = localY)
                } else {
                    start.copy(gradientEndX = localX, gradientEndY = localY)
                }
            }
            MotionEvent.ACTION_UP -> {
                editableGradient?.let(onGradientHandleChanged)
                gradientGestureHandle = -1
                gradientGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                editableGradient = start
                gradientGestureHandle = -1
                gradientGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    private fun handleRulerGesture(event: MotionEvent, canvasX: Float, canvasY: Float, isStylus: Boolean, elapsedSinceStylus: Long): Boolean {
        if (isStylus || !rulerEnabled) return false
        val existing = rulerGestureMode
        if (existing == null) {
            if (forceNavigation || event.actionMasked != MotionEvent.ACTION_DOWN || event.pointerCount != 1 ||
                elapsedSinceStylus <= StylusInputClassifier.RECENT_STYLUS_WINDOW_MS ||
                event.getTouchMajor(event.actionIndex) >= StylusInputClassifier.PALM_TOUCH_MAJOR_PX
            ) return false
            val hit = RulerGuide.hitTest(rulerGuide, canvasX, canvasY, width.toFloat(), height.toFloat(), RULER_TOUCH_RADIUS_DP * resources.displayMetrics.density) ?: return false
            rulerGestureMode = hit
            rulerGestureStart = rulerGuide
            rulerGestureX = canvasX
            rulerGestureY = canvasY
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1) {
                rulerGuide = when (existing) {
                    RulerGestureMode.MOVE -> RulerGuide.moveBy(rulerGuide, canvasX - rulerGestureX, canvasY - rulerGestureY, width.toFloat(), height.toFloat())
                    RulerGestureMode.ROTATE -> RulerGuide.rotateToward(rulerGuide, canvasX, canvasY, width.toFloat(), height.toFloat())
                }
                rulerGestureX = canvasX
                rulerGestureY = canvasY
            }
            MotionEvent.ACTION_UP -> {
                onRulerGuideChanged(rulerGuide)
                rulerGestureMode = null
                rulerGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                rulerGestureStart?.let { rulerGuide = it }
                rulerGestureMode = null
                rulerGestureStart = null
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate()
        return true
    }

    private fun drawRulerGuide(canvas: Canvas) {
        if (!rulerEnabled || width <= 0 || height <= 0) return
        val state = rulerGuide.normalized()
        val radians = Math.toRadians(state.angleDegrees.toDouble())
        val directionX = cos(radians).toFloat()
        val directionY = sin(radians).toFloat()
        val normalX = -directionY
        val normalY = directionX
        val centerX = state.centerX * width
        val centerY = state.centerY * height
        val halfLength = hypot(width.toDouble(), height.toDouble()).toFloat()
        val halfBand = 34f * resources.displayMetrics.density
        val startX = centerX - directionX * halfLength
        val startY = centerY - directionY * halfLength
        val endX = centerX + directionX * halfLength
        val endY = centerY + directionY * halfLength
        val band = Path().apply {
            moveTo(startX + normalX * halfBand, startY + normalY * halfBand)
            lineTo(endX + normalX * halfBand, endY + normalY * halfBand)
            lineTo(endX - normalX * halfBand, endY - normalY * halfBand)
            lineTo(startX - normalX * halfBand, startY - normalY * halfBand)
            close()
        }
        canvas.drawPath(band, rulerFillPaint)
        canvas.drawLine(startX, startY, endX, endY, rulerLinePaint)
        val tickSpacing = 28f * resources.displayMetrics.density
        var offset = -halfLength
        var tickIndex = 0
        while (offset <= halfLength) {
            val tickLength = (if (tickIndex % 5 == 0) 18f else 9f) * resources.displayMetrics.density
            val x = centerX + directionX * offset
            val y = centerY + directionY * offset
            canvas.drawLine(x, y, x + normalX * tickLength, y + normalY * tickLength, rulerLinePaint)
            offset += tickSpacing
            tickIndex++
        }
        val handle = RulerGuide.rotationHandle(state, width.toFloat(), height.toFloat())
        rulerFillPaint.color = Color.WHITE
        rulerFillPaint.alpha = 245
        canvas.drawCircle(handle.x, handle.y, 16f * resources.displayMetrics.density, rulerFillPaint)
        rulerFillPaint.color = Color.rgb(245, 245, 245)
        rulerFillPaint.alpha = 220
        canvas.drawCircle(handle.x, handle.y, 16f * resources.displayMetrics.density, rulerLinePaint)
        canvas.drawText("${state.angleDegrees.toInt()}°", centerX + normalX * (halfBand + 22f), centerY + normalY * (halfBand + 22f), rulerLabelPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isStylus = (0 until event.pointerCount).any { index -> event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER }
        if (isStylus) {
            lastStylusSeenAt = event.eventTime
            val transitions = updateStylusButtonState(event.buttonState)
            dispatchStylusButtonTransitions(transitions)
            val pressedCommands = transitions.filter { it.phase == ShortcutPhase.PRESS }.map(StylusButtonTransition::action).filter(StylusButtonAction::isCommand).distinct()
            if (pressedCommands.isNotEmpty() || resolveStylusButtonAction(event.buttonState).isCommand) return true
        }
        if (!isStylus && event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val axisOrder = if (event.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)) {
                ToolboxAxis.entries
            } else {
                listOf(ToolboxAxis.VERTICAL_SCROLL, ToolboxAxis.HORIZONTAL_SCROLL, ToolboxAxis.ROTARY)
            }
            val axisValues = axisOrder.map { axis -> axis to event.getAxisValue(axis.axisCode) }
            val activeAxis = axisValues.firstOrNull { (_, value) -> value.isFinite() && kotlin.math.abs(value) > 0f }
            val deviceDescriptor = event.device?.descriptor
            val matchedAxis = axisValues.firstNotNullOfOrNull { (axis, value) ->
                if (!value.isFinite() || kotlin.math.abs(value) <= 0f) null
                else ToolboxMotionResolver.resolveAxis(axis, shortcutAxisBindings, deviceDescriptor)?.let { Triple(axis, value, it) }
            }
            if (matchedAxis != null) {
                val (axis, value, binding) = matchedAxis
                val activations = binding?.let { shortcutAxisAccumulator.activations(it, value, deviceDescriptor) }.orEmpty()
                activations.forEach(onShortcutAction)
                onMotionInputDiagnostic(event.toMotionDiagnostic(axis.axisCode, value, 0, ShortcutAxisAccumulator.actionFor(binding.mode, value * (if (binding.reversed) -1f else 1f) > 0f)))
                return true
            }
            activeAxis?.let { (axis, value) -> onMotionInputDiagnostic(event.toMotionDiagnostic(axis.axisCode, value, 0, null)) }
        }
        if (!isStylus && event.actionMasked in setOf(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE)) {
            val phase = if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) ShortcutPhase.PRESS else ShortcutPhase.RELEASE
            val deviceDescriptor = event.device?.descriptor
            val actionButton = when {
                event.actionButton > 0 && event.actionButton.countOneBits() == 1 -> event.actionButton
                phase == ShortcutPhase.PRESS -> Integer.lowestOneBit(event.buttonState and POINTER_BUTTON_MASK)
                else -> heldMotionButtons.keys.firstOrNull { it.first == deviceDescriptor }?.second ?: 0
            }
            val heldKey = deviceDescriptor to actionButton
            val resolvedBinding = ToolboxMotionResolver.resolveButton(actionButton, shortcutButtonBindings, deviceDescriptor)
            val binding = if (phase == ShortcutPhase.RELEASE) heldMotionButtons.remove(heldKey) ?: resolvedBinding else resolvedBinding
            if (phase == ShortcutPhase.PRESS && binding?.trigger == ShortcutTrigger.HOLD) heldMotionButtons[heldKey] = binding
            binding?.let { ToolboxMotionResolver.buttonActivation(it, phase) }?.let(onShortcutAction)
            onMotionInputDiagnostic(event.toMotionDiagnostic(null, 0f, actionButton, binding?.action))
            if (binding != null) return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun MotionEvent.toMotionDiagnostic(axisCode: Int?, axisValue: Float, actionButton: Int, matchedAction: ShortcutAction?): MotionInputDiagnostic =
        MotionInputDiagnostic(
            action = actionMasked,
            axisCode = axisCode,
            axisValue = axisValue,
            actionButton = actionButton,
            buttonState = buttonState,
            deviceId = deviceId,
            deviceName = device?.name,
            deviceDescriptor = device?.descriptor,
            source = source,
            matchedAction = matchedAction,
        )

    private data class StylusButtonTransition(val action: StylusButtonAction, val trigger: ShortcutTrigger, val phase: ShortcutPhase)

    private fun updateStylusButtonState(buttonState: Int): List<StylusButtonTransition> {
        val tracked = buttonState and STYLUS_BUTTON_MASK
        val newlyPressed = tracked and lastStylusButtonState.inv()
        val newlyReleased = lastStylusButtonState and tracked.inv()
        lastStylusButtonState = tracked
        return buildList {
            if (newlyPressed and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) add(stylusButtonPress(MotionEvent.BUTTON_STYLUS_PRIMARY, primaryButtonAction, primaryButtonTrigger))
            if (newlyPressed and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) add(stylusButtonPress(MotionEvent.BUTTON_STYLUS_SECONDARY, secondaryButtonAction, secondaryButtonTrigger))
            if (newlyReleased and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) stylusButtonRelease(MotionEvent.BUTTON_STYLUS_PRIMARY)?.let(::add)
            if (newlyReleased and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) stylusButtonRelease(MotionEvent.BUTTON_STYLUS_SECONDARY)?.let(::add)
        }
    }

    private fun stylusButtonPress(button: Int, action: StylusButtonAction, trigger: ShortcutTrigger): StylusButtonTransition {
        if (trigger == ShortcutTrigger.HOLD && !action.isCommand && action != StylusButtonAction.NONE) heldStylusButtons[button] = action to trigger
        return StylusButtonTransition(action, trigger, ShortcutPhase.PRESS)
    }

    private fun stylusButtonRelease(button: Int): StylusButtonTransition? {
        val (action, trigger) = heldStylusButtons.remove(button) ?: return null
        return StylusButtonTransition(action, trigger, ShortcutPhase.RELEASE)
    }

    private fun dispatchStylusButtonTransitions(transitions: List<StylusButtonTransition>) {
        transitions.forEach { transition ->
            if (transition.phase == ShortcutPhase.PRESS && transition.action.isCommand) {
                performStylusCommand(transition.action)
            } else {
                StylusButtonShortcut.activation(transition.action, transition.trigger, transition.phase)?.let(onShortcutAction)
            }
        }
    }

    private fun releaseHeldStylusButtons() {
        heldStylusButtons.values.toList().forEach { (action, trigger) ->
            StylusButtonShortcut.activation(action, trigger, ShortcutPhase.RELEASE)?.let(onShortcutAction)
        }
        heldStylusButtons.clear()
    }

    private fun resolveStylusButtonAction(buttonState: Int): StylusButtonAction = when {
        buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 -> primaryButtonAction
        buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 -> secondaryButtonAction
        else -> StylusButtonAction.NONE
    }

    private fun performStylusCommand(action: StylusButtonAction) {
        when (action) {
            StylusButtonAction.UNDO -> onUndoGesture()
            StylusButtonAction.REDO -> onRedoGesture()
            else -> Unit
        }
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
            paint.strokeWidth = gestureBrushSize.coerceAtLeast(.1f)
            canvas.drawPath(lassoPath, paint)
            paint.alpha = 255
            return
        }
        val start = shapeStart ?: return
        val end = shapeEnd ?: return
        val shape = ShapeGeometry.create(shapeKind, start, end, width.toFloat(), height.toFloat(), inkColor.toUInt().toLong(), gestureBrushSize) ?: return
        val left = shape.x * width
        val top = shape.y * height
        val right = left + shape.width / width.coerceAtLeast(1) * width
        val bottom = top + shape.height / height.coerceAtLeast(1) * height
        paint.style = Paint.Style.STROKE
        paint.color = inkColor
        paint.alpha = 190
        paint.strokeWidth = gestureBrushSize.coerceAtLeast(.1f)
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

    private fun drawGradientPreview(canvas: Canvas) {
        val start = gradientStart ?: return
        val end = gradientEnd ?: return
        val x0 = start.x * width
        val y0 = start.y * height
        var x1 = end.x * width
        var y1 = end.y * height
        if (hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()) < 1.0) x1 = x0 + 1f
        paint.style = Paint.Style.FILL
        val selectionMask = selectionDisplayMask
        val previewCheckpoint = if (selectionMask != null) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) else -1
        if (selectionMask != null) {
            paint.alpha = 255
            paint.color = Color.WHITE
            selectionMask.draw(canvas, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
            paint.blendMode = BlendMode.SRC_IN
        }
        paint.alpha = (inkOpacity * 210f).toInt().coerceIn(0, 255)
        paint.isDither = gradientDefinition.dither
        paint.shader = GradientShaderFactory.create(gradientDefinition, x0, y0, x1, y1)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        if (selectionMask != null) {
            paint.blendMode = null
            canvas.restoreToCount(previewCheckpoint)
        }
        paint.isDither = true
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.DKGRAY
        canvas.drawLine(x0, y0, x1, y1, paint)
        canvas.drawCircle(x0, y0, 8f, paint)
        canvas.drawCircle(x1, y1, 8f, paint)
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
        if (selectionShape == SelectionShape.FREEFORM || selectionShape == SelectionShape.BRUSH) {
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
        SelectionShape.SMART_BORDER, SelectionShape.MAGIC_WAND -> emptyList()
        SelectionShape.FREEFORM -> lassoPoints
        SelectionShape.RECTANGLE -> if (lassoPoints.size >= 2) LassoGeometry.rectangle(lassoPoints.first(), lassoPoints.last()) else emptyList()
        SelectionShape.ELLIPSE -> if (lassoPoints.size >= 2) LassoGeometry.ellipse(lassoPoints.first(), lassoPoints.last()) else emptyList()
        SelectionShape.BRUSH -> LassoGeometry.brushCorridor(lassoPoints, selectionBrushSize, pageModelWidth, pageModelHeight)
    }

    private fun selectAutomaticRegion(normalizedX: Float, normalizedY: Float, smartBorder: Boolean) {
        val sampleWidth = width.coerceIn(1, 512)
        val sampleHeight = (height.coerceAtLeast(1) * (sampleWidth / width.coerceAtLeast(1).toFloat())).toInt().coerceIn(1, 768)
        val page = renderDocumentPageBitmap(sampleWidth, sampleHeight)
        val tolerance = if (smartBorder) (8f + (1f - selectionThreshold) * 56f).toInt() else (selectionThreshold * 255f).toInt()
        val region = FloodFill.fill(
            page,
            (normalizedX * (sampleWidth - 1)).toInt().coerceIn(0, sampleWidth - 1),
            (normalizedY * (sampleHeight - 1)).toInt().coerceIn(0, sampleHeight - 1),
            tolerance,
        )
        fun regionContains(x: Int, y: Int, radius: Int): Boolean {
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val localX = x + dx - region.left
                val localY = y + dy - region.top
                if (localX in 0 until region.mask.width && localY in 0 until region.mask.height && Color.alpha(region.mask.getPixel(localX, localY)) != 0) return true
            }
            return false
        }
        val hits = strokes.asSequence().filter { stroke ->
            stroke.id !in protectedStrokeIds && when (selectionFilter) {
                SelectionFilter.ALL -> true
                SelectionFilter.ACTIVE_LAYER -> stroke.layerId == activeLayerId
                SelectionFilter.CURRENT_BRUSH -> stroke.brushId == brushId
                SelectionFilter.CURRENT_COLOR -> stroke.colorArgb.toInt() == inkColor
            }
        }.filter { stroke ->
            stroke.points.any { point ->
                val x = (point.x * (sampleWidth - 1)).toInt().coerceIn(0, sampleWidth - 1)
                val y = (point.y * (sampleHeight - 1)).toInt().coerceIn(0, sampleHeight - 1)
                regionContains(x, y, if (smartBorder) 3 else 0)
            }
        }.mapTo(linkedSetOf(), InkStroke::id)
        when (selectionMode) {
            SelectionMode.REPLACE -> { selectedStrokeIds.clear(); selectedStrokeIds.addAll(hits) }
            SelectionMode.ADD -> selectedStrokeIds.addAll(hits)
            SelectionMode.SUBTRACT -> selectedStrokeIds.removeAll(hits)
            SelectionMode.INTERSECT -> selectedStrokeIds.retainAll(hits)
        }
        val exactMask = if (region.isEmpty) null else SelectionMaskCompositor.fromFloodFill(
            region.mask,
            region.left,
            region.top,
            sampleWidth,
            sampleHeight,
            pageModelWidth,
            pageModelHeight,
        )
        composeSelectionAlpha(exactMask, selectionMode)
        exactMask?.recycle()
        if (selectionMode == SelectionMode.REPLACE) selectedMaskRegions = emptyList()
        region.mask.recycle()
        page.recycle()
        invalidate()
    }

    private fun selectWithLasso(polygon: List<StrokePoint>) {
        if (polygon.size < 3) return
        val exactMask = SelectionMaskCompositor.polygon(polygon, pageModelWidth, pageModelHeight)
        composeSelectionAlpha(exactMask, selectionMode)
        exactMask?.recycle()
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
        selectedMaskRegions = when (selectionMode) {
            SelectionMode.REPLACE -> listOf(polygon.toList())
            SelectionMode.ADD -> selectedMaskRegions + listOf(polygon.toList())
            SelectionMode.SUBTRACT, SelectionMode.INTERSECT -> emptyList()
        }
        if (selectionMode == SelectionMode.REPLACE) selectionFeatherPixels = 0f
        if (splitOccurred) {
            strokes.clear()
            strokes += rebuilt
            commitStrokeEdit(before, strokes)
            val edit = StrokeEdits.difference(before, strokes)
            invalidateStrokeCache(edit?.before.orEmpty().map { it.stroke }, edit?.after.orEmpty().map { it.stroke })
        }
    }

    private fun addPoint(x: Float, y: Float, pressure: Float, time: Long, tilt: Float, orientation: Float, force: Boolean = false) {
        traceEngineV3("Inkloom.V3.InputModel") {
            addModeledPoint(x, y, pressure, time, tilt, orientation, force)
        }
    }

    private fun addModeledPoint(x: Float, y: Float, pressure: Float, time: Long, tilt: Float, orientation: Float, force: Boolean) {
        stageNativeInput(0, x, y, pressure, time, tilt, orientation, force)
        if (strokeInputModeler.appendBatch(1, modeledViewportWidth(), modeledViewportHeight(), stabilization, stabilizationBySpeed, stabilizationBySpeedMode, streamLine, streamLinePressure, procreateStabilization, motionFiltering, motionFilteringExpression) != 1) return
        appendNativeOutput(0)
        requestActiveSurfaceFrame()
    }

    /** Copies every historical and current sample into one direct-buffer batch. The event-to-model
     * boundary now incurs one JNI call instead of one call per sample. */
    private fun addMotionEventPoints(event: MotionEvent, pointerIndex: Int) {
        traceEngineV3("Inkloom.V3.InputModelBatch") {
            val total = event.historySize + 1
            var sourceIndex = 0
            while (sourceIndex < total) {
                val count = minOf(strokeInputModeler.batchCapacity, total - sourceIndex)
                for (batchIndex in 0 until count) {
                    val eventIndex = sourceIndex + batchIndex
                    val historical = eventIndex < event.historySize
                    val x = if (historical) event.getHistoricalX(pointerIndex, eventIndex) else event.getX(pointerIndex)
                    val y = if (historical) event.getHistoricalY(pointerIndex, eventIndex) else event.getY(pointerIndex)
                    val pressure = if (historical) event.getHistoricalPressure(pointerIndex, eventIndex) else event.getPressure(pointerIndex)
                    val time = if (historical) event.getHistoricalEventTime(eventIndex) else event.eventTime
                    val tilt = if (historical) event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, eventIndex) else event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
                    val orientation = if (historical) event.getHistoricalOrientation(pointerIndex, eventIndex) else event.getOrientation(pointerIndex)
                    stageNativeInput(
                        batchIndex,
                        x / width.coerceAtLeast(1),
                        y / height.coerceAtLeast(1),
                        pressure,
                        time,
                        tilt,
                        orientation,
                        force = false,
                    )
                }
                val accepted = strokeInputModeler.appendBatch(
                    count,
                    modeledViewportWidth(),
                    modeledViewportHeight(),
                    stabilization,
                    stabilizationBySpeed,
                    stabilizationBySpeedMode,
                    streamLine,
                    streamLinePressure,
                    procreateStabilization,
                    motionFiltering,
                    motionFilteringExpression,
                )
                for (outputIndex in 0 until accepted) appendNativeOutput(outputIndex)
                sourceIndex += count
            }
        }
        requestActiveSurfaceFrame()
    }

    /** Sampling density and velocity are physical-screen properties. At 8x zoom, using the fitted
     * page dimensions would discard points roughly eight times farther apart on screen and turn a
     * smooth pen path into visible chords. */
    private fun modeledViewportWidth(): Int =
        (width.coerceAtLeast(1) * viewportScale.coerceIn(1f, ViewportTransform.MAXIMUM_SCALE)).toInt().coerceAtLeast(1)

    private fun modeledViewportHeight(): Int =
        (height.coerceAtLeast(1) * viewportScale.coerceIn(1f, ViewportTransform.MAXIMUM_SCALE)).toInt().coerceAtLeast(1)

    private fun stageNativeInput(index: Int, x: Float, y: Float, pressure: Float, time: Long, tilt: Float, orientation: Float, force: Boolean) {
        val adjustedPressure = PressureCalibration.apply(pressure, pressureMinimumInput, pressureMaximumInput, pressureSensitivity, pressureResponseCurve)
        if (!rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && activePerspectiveSnapConstraint == null) {
            currentPoints.firstOrNull()?.let { start ->
                activePerspectiveSnapConstraint = PerspectiveGuides.constraintAt(
                    perspectiveGuideState,
                    start.x,
                    start.y,
                    x,
                    y,
                    width.toFloat(),
                    height.toFloat(),
                    GUIDE_CAPTURE_MOVEMENT_DP * resources.displayMetrics.density,
                )
            }
        }
        if (!rulerEnabled && perspectiveGuideState.mode == PerspectiveGuideMode.NONE && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && activeSpecialRulerConstraint == null) {
            currentPoints.firstOrNull()?.let { start ->
                activeSpecialRulerConstraint = SpecialRulers.constraintAt(
                    specialRulerState,
                    start.x,
                    start.y,
                    x,
                    y,
                    width.toFloat(),
                    height.toFloat(),
                    GUIDE_CAPTURE_MOVEMENT_DP * resources.displayMetrics.density,
                )
            }
        }
        val constrained = if (rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
            RulerGuide.project(rulerGuide, x, y, width.toFloat(), height.toFloat())
        } else activePerspectiveSnapConstraint?.apply(x, y) ?: activeSpecialRulerConstraint?.apply(x, y) ?: activeCanvasSnapConstraint?.apply(x, y)
        strokeInputModeler.putSample(
            index,
            (constrained?.x ?: x).coerceIn(0f, 1f),
            (constrained?.y ?: y).coerceIn(0f, 1f),
            adjustedPressure,
            time,
            tilt,
            orientation,
            force,
        )
    }

    private fun appendNativeOutput(index: Int) {
        val x = strokeInputModeler.x(index)
        val y = strokeInputModeler.y(index)
        val previous = currentPoints.lastOrNull()
        val pathDistance = (previous?.pathDistance ?: 0f) + if (previous == null) 0f else hypot(
            ((x - previous.x) * pageModelWidth).toDouble(),
            ((y - previous.y) * pageModelHeight).toDouble(),
        ).toFloat() / REFERENCE_WIDTH
        currentPoints += StrokePoint(
            x = x,
            y = y,
            pressure = strokeInputModeler.pressure(index),
            timeMs = strokeInputModeler.timeMs(index),
            tilt = strokeInputModeler.tilt(index),
            orientation = strokeInputModeler.orientation(index),
            velocity = strokeInputModeler.velocity(index),
            pathDistance = pathDistance,
        )
    }

    private fun requestActiveSurfaceFrame() {
        if (usesViewActivePreview()) {
            postInvalidateOnAnimation()
            return
        }
        if (activeStrokeSurface == null || activeSurfaceFrameScheduled) return
        activeSurfaceFrameScheduled = true
        postOnAnimation(activeSurfaceFrame)
    }

    /** The low-latency surface is composited source-over by Android. Masks and non-Normal
     * whole-stroke blend modes must preview in the stable scene so they can see destination pixels. */
    private fun usesViewActivePreview(): Boolean =
        layerMasks[activeLayerId]?.enabled == true ||
            ProcreateBrushBlend.normalize(procreateBrushBlendMode) != ProcreateBrushBlend.NORMAL ||
            (waterEdgeEnabled || tiltBleed > .001f) &&
                ProcreateBrushBlend.normalize(procreateBurntEdgeBlendMode) != ProcreateBrushBlend.NORMAL

    private fun usesDestinationDependentBrushBlend(stroke: InkStroke): Boolean =
        ProcreateBrushBlend.normalize(stroke.procreateBrushBlendMode) != ProcreateBrushBlend.NORMAL ||
            (stroke.waterEdgeEnabled || stroke.tiltBleed > .001f) &&
                ProcreateBrushBlend.normalize(stroke.procreateBurntEdgeBlendMode) != ProcreateBrushBlend.NORMAL

    @VisibleForTesting
    internal fun flushActiveStrokeFrame() {
        if (!activeSurfaceFrameScheduled) return
        removeCallbacks(activeSurfaceFrame)
        activeSurfaceFrame.run()
    }

    @VisibleForTesting
    internal fun flushRealtimeToolFrame() {
        if (smudgeFrameScheduled || hasPendingSmudge) flushSmudgeFrame()
        if (liquifyFrameScheduled || hasPendingLiquify) flushLiquifyFrame()
    }

    @WorkerThread
    internal fun drawActiveStrokes(
        canvas: Canvas,
        strokes: List<InkStroke>,
        incrementalStampDraw: Boolean = false,
        completeRedraw: Boolean = false,
    ) {
        traceEngineV3("Inkloom.V3.ActiveStrokeDraw") {
            synchronized(renderLock) {
                if (rasterIncrementalPaintEnabled && !completeRedraw &&
                    renderIncrementalRasterPaintPatch(canvas, strokes, finalCompleteInput = false)
                ) return@synchronized
                incrementalActiveDraw = incrementalStampDraw
                try {
                    if (!incrementalStampDraw) {
                        drawLayeredStrokes(canvas, strokes)
                        strokes.filter(::usesStampedRenderer).forEach { stroke ->
                            activePrimaryStampCounts[stroke.id] = primaryStampedPoints(stroke).size
                            activeDualStampCounts[stroke.id] = if (stroke.dualBrush) dualStampedPoints(stroke).size else 0
                        }
                    } else {
                        strokes.forEach { stroke ->
                            val primaryCount = primaryStampedPoints(stroke).size
                            val dualCount = if (stroke.dualBrush) dualStampedPoints(stroke).size else 0
                            val primaryStart = activePrimaryStampCounts[stroke.id] ?: 0
                            val dualStart = activeDualStampCounts[stroke.id] ?: 0
                            drawLayeredStrokes(canvas, listOf(stroke), primaryStart, dualStart)
                            activePrimaryStampCounts[stroke.id] = primaryCount
                            activeDualStampCounts[stroke.id] = dualCount
                        }
                    }
                } finally {
                    incrementalActiveDraw = false
                }
            }
        }
    }

    internal fun resetActiveRenderState() {
        synchronized(renderLock) {
            activePrimaryStampCounts.clear()
            activeDualStampCounts.clear()
            activePrimarySamples.clear()
            activeDualSamples.clear()
            clearActiveStampRandoms()
            activeCspDualBrushSurfaces.values.forEach(ActiveCspDualBrushSurface::recycle)
            activeCspDualBrushSurfaces.clear()
            activeWholeTextureStrokeId = null
            activeWholeTextureProgram = null
            activeWholeTexturePointCount = 0
            activeWholeTexturePageWidth = 0
            activeWholeTexturePageHeight = 0
        }
    }

    private fun clearActiveStampRandoms() {
        activePrimaryScatterRandoms.clear()
        activePrimaryRotationRandoms.clear()
        activeDualScatterRandoms.clear()
        activeDualRotationRandoms.clear()
    }

    private fun usesStampedRenderer(stroke: InkStroke): Boolean =
        stroke.tipAssetPath != null || stroke.tipAssetPaths.isNotEmpty() || ProceduralBrushTip.supports(stroke)

    private fun clearActiveStrokeInput() {
        currentPoints.clear()
        strokeInputModeler.reset()
    }

    @VisibleForTesting
    internal fun committedTileSnapshot(): SparseTileDebugSnapshot =
        committedScene.debugSnapshot()

    /** The full-screen proxy receives screen-space contacts. Drawing contacts are mapped into the
     * page once, while navigation remains in screen space so pan deltas do not shrink with zoom. */
    internal fun proxyContactUsesPageCoordinates(event: MotionEvent): Boolean {
        if (forceNavigation || viewportGestureActive) return false
        val stylusIndex = (0 until event.pointerCount).firstOrNull { index ->
            event.getToolType(index) in setOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)
        }
        if (stylusIndex != null) {
            return resolveStylusButtonAction(event.buttonState) != StylusButtonAction.HAND
        }
        if (event.pointerCount != 1) return false
        val elapsedSinceStylus = if (lastStylusSeenAt == Long.MIN_VALUE) Long.MAX_VALUE else event.eventTime - lastStylusSeenAt
        val selectionDrawContact = tool == EditorTool.SELECT
        return (fingerDrawingEnabled || selectionDrawContact) &&
            event.getTouchMajor(0) < StylusInputClassifier.PALM_TOUCH_MAJOR_PX &&
            (selectionDrawContact || elapsedSinceStylus !in 0..StylusInputClassifier.RECENT_STYLUS_WINDOW_MS)
    }

    internal fun viewportState(): ViewportState = ViewportState(
        viewportScale,
        viewportPanX,
        viewportPanY,
        viewportRotationDegrees,
    )

    private fun updateContinuousPlotInput(x: Float, y: Float, pressure: Float, tilt: Float, orientation: Float) {
        continuousPlotX = x.coerceIn(0f, 1f)
        continuousPlotY = y.coerceIn(0f, 1f)
        continuousPlotPressure = pressure
        continuousPlotTilt = tilt
        continuousPlotOrientation = orientation
    }

    private fun startContinuousPlot() {
        stopContinuousPlot()
        continuousPlotActive = true
        postDelayed(continuousPlotRunnable, CONTINUOUS_PLOT_INTERVAL_MS)
    }

    private fun stopContinuousPlot() {
        continuousPlotActive = false
        removeCallbacks(continuousPlotRunnable)
    }

    private fun beginFillGesture(normalizedX: Float, normalizedY: Float, rawX: Float) {
        ensureSelectionAlphaFromSelectedStrokes()
        fillGestureUsesSelection = hasSelection()
        if (fillGestureUsesSelection) {
            fillBaseSensitivity = fillSensitivity
            fillSensitivityNow = fillBaseSensitivity
            fillDragOriginX = rawX
            fillPreviewTolerance = -1
            recomputeFillPreview(force = true)
            return
        }
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val sampleScale = minOf(1f, FILL_MAX_SAMPLE_DIMENSION / maxOf(safeWidth, safeHeight).toFloat())
        val sampleWidth = (safeWidth * sampleScale).toInt().coerceAtLeast(1)
        val sampleHeight = (safeHeight * sampleScale).toInt().coerceAtLeast(1)
        val source = renderDocumentPageBitmap(sampleWidth, sampleHeight)
        fillSourceBitmap = source
        fillSeedX = (normalizedX.coerceIn(0f, 1f) * sampleWidth).toInt().coerceIn(0, sampleWidth - 1)
        fillSeedY = (normalizedY.coerceIn(0f, 1f) * sampleHeight).toInt().coerceIn(0, sampleHeight - 1)
        fillBaseSensitivity = fillSensitivity
        fillSensitivityNow = fillBaseSensitivity
        fillDragOriginX = rawX
        fillPreviewTolerance = -1
        recomputeFillPreview(force = true)
    }

    private fun adjustFillSensitivity(rawX: Float) {
        val next = (fillBaseSensitivity + (rawX - fillDragOriginX) / 480f).coerceIn(0f, 1f)
        if (next != fillSensitivityNow) {
            fillSensitivityNow = next
            recomputeFillPreview(force = false)
            invalidate()
        }
    }

    private fun recomputeFillPreview(force: Boolean) {
        if (fillGestureUsesSelection) {
            fillPreviewMask?.recycle()
            fillPreviewMask = selectionDisplayMask?.snapshot()
            fillPreviewLeft = 0f
            fillPreviewTop = 0f
            fillPreviewWidth = if (fillPreviewMask == null) 0f else 1f
            fillPreviewHeight = if (fillPreviewMask == null) 0f else 1f
            fillPreviewTolerance = (fillSensitivityNow * 200f).toInt().coerceIn(0, 255)
            fillPreviewComputedAt = SystemClock.uptimeMillis()
            return
        }
        val source = fillSourceBitmap ?: return
        val tolerance = (fillSensitivityNow * 200f).toInt().coerceIn(0, 255)
        val now = SystemClock.uptimeMillis()
        if (!force && (tolerance == fillPreviewTolerance || now - fillPreviewComputedAt < FILL_PREVIEW_THROTTLE_MS)) return
        val modelToSample = source.width / pageModelWidth.coerceAtLeast(1f)
        val result = FloodFill.fill(
            source,
            fillSeedX,
            fillSeedY,
            tolerance,
            closeGapPixels = (fillCloseGapPixels * modelToSample).toInt(),
            areaScalingPixels = (fillAreaScalingPixels * modelToSample).toInt(),
        )
        fillPreviewMask?.recycle()
        fillPreviewMask = if (result.isEmpty) null else SelectionMaskCompositor.clipRegion(
            result.mask,
            result.left,
            result.top,
            source.width,
            source.height,
            null,
        )
        result.mask.recycle()
        val safeWidth = source.width.coerceAtLeast(1).toFloat()
        val safeHeight = source.height.coerceAtLeast(1).toFloat()
        fillPreviewLeft = result.left / safeWidth
        fillPreviewTop = result.top / safeHeight
        fillPreviewWidth = if (fillPreviewMask == null) 0f else result.width / safeWidth
        fillPreviewHeight = if (fillPreviewMask == null) 0f else result.height / safeHeight
        fillPreviewTolerance = tolerance
        fillPreviewComputedAt = now
    }

    private fun renderDocumentPageBitmap(pageWidth: Int, pageHeight: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(pageWidth / width.coerceAtLeast(1).toFloat(), pageHeight / height.coerceAtLeast(1).toFloat())
        val availableLayers = layerOrder.ifEmpty { strokes.map(InkStroke::layerId).distinct() }
        val visibleNonDraft = availableLayers.filterNotTo(linkedSetOf()) { it in draftLayerIds }
        val referenced = when (referenceTarget) {
            ReferenceTarget.ALL_VISIBLE -> visibleNonDraft
            ReferenceTarget.ACTIVE_LAYER -> setOf(activeLayerId).intersect(visibleNonDraft)
            ReferenceTarget.REFERENCE_LAYERS -> referenceLayerIds.intersect(visibleNonDraft).ifEmpty { setOf(activeLayerId).intersect(visibleNonDraft) }
        }
        drawPageContent(canvas, includeInteractionOverlays = false, referencedLayerIds = referenced)
        return bitmap
    }

    private fun commitFillGesture() {
        recomputeFillPreview(force = true)
        val mask = fillPreviewMask
        try {
            if (mask != null && fillPreviewWidth > 0f && fillPreviewHeight > 0f) {
                val committedMask = mask.copy(Bitmap.Config.ARGB_8888, false)
                val callback = onFillCommitted
                if (callback != null) callback(
                    committedMask,
                    fillPreviewLeft,
                    fillPreviewTop,
                    fillPreviewWidth * pageModelWidth,
                    fillPreviewHeight * pageModelHeight,
                    inkColor.toUInt().toLong(),
                    inkOpacity,
                ) else committedMask.recycle()
            }
        } finally {
            mask?.recycle()
            fillPreviewMask = null
            fillSourceBitmap?.recycle()
            fillSourceBitmap = null
            fillGestureUsesSelection = false
            invalidate()
        }
    }

    private fun commitManualFillGesture() {
        if (fillGesturePoints.size < 2) {
            fillGesturePoints.clear()
            invalidate()
            return
        }
        val bitmapWidth = width.coerceAtLeast(1)
        val bitmapHeight = height.coerceAtLeast(1)
        val drawnMask = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(drawnMask)
        val path = Path().apply {
            moveTo(fillGesturePoints.first().x * bitmapWidth, fillGesturePoints.first().y * bitmapHeight)
            fillGesturePoints.drop(1).forEach { point -> lineTo(point.x * bitmapWidth, point.y * bitmapHeight) }
            if (fillSubTool != FillSubTool.LEFTOVER_PEN) close()
        }
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = if (fillSubTool == FillSubTool.LEFTOVER_PEN) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = gestureBrushSize.coerceAtLeast(.1f) * bitmapWidth / pageModelWidth.coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        maskCanvas.drawPath(path, maskPaint)
        val selection = selectionDisplayMask?.snapshot()
        val fullMask = try {
            SelectionMaskCompositor.clipRegion(drawnMask, 0, 0, bitmapWidth, bitmapHeight, selection)
        } finally {
            selection?.recycle()
        }
        drawnMask.recycle()
        if (fullMask == null) {
            fillGesturePoints.clear()
            invalidate()
            return
        }
        NativeRasterOps.alphaBounds(fullMask)?.let { bounds ->
            val cropped = Bitmap.createBitmap(fullMask, bounds.left, bounds.top, bounds.width(), bounds.height())
            val callback = onFillCommitted
            if (callback != null) callback(
                cropped,
                bounds.left / bitmapWidth.toFloat(),
                bounds.top / bitmapHeight.toFloat(),
                bounds.width() / bitmapWidth.toFloat() * pageModelWidth,
                bounds.height() / bitmapHeight.toFloat() * pageModelHeight,
                inkColor.toUInt().toLong(),
                inkOpacity,
            ) else cropped.recycle()
        }
        fullMask.recycle()
        fillGesturePoints.clear()
        invalidate()
    }

    private fun cancelFillGesture() {
        fillPreviewMask?.recycle()
        fillPreviewMask = null
        fillSourceBitmap?.recycle()
        fillSourceBitmap = null
        fillGestureUsesSelection = false
        fillGesturePoints.clear()
        invalidate()
    }

    private fun beginSmudgeGesture(normalizedX: Float, normalizedY: Float) {
        removeCallbacks(smudgeFrame)
        smudgeFrameScheduled = false
        hasPendingSmudge = false
        rasterTileBlendSession?.close()
        rasterTileBlendSession = null
        rasterBlendBitmap = rasterEditBitmapFor(activeLayerId)
        val raster = rasterBlendBitmap
        val surface = rasterTileSurfaceFor(activeLayerId)
        if (raster != null && surface != null) {
            rasterTileBlendSession = RasterTileBlendSession(surface, raster, selectionMaskForRasterTools())
            smudgeBefore = null
            localToolStrokeIndices.clear()
        } else {
            smudgeBefore = strokes.toList()
            prepareLocalToolStrokeIndex()
        }
        smudgeLastX = normalizedX.coerceIn(0f, 1f)
        smudgeLastY = normalizedY.coerceIn(0f, 1f)
        smudgeChanged = false
        smudgeDirtyBounds = null
    }

    private fun scheduleSmudge(x: Float, y: Float) {
        pendingSmudgeX = x
        pendingSmudgeY = y
        hasPendingSmudge = true
        if (!smudgeFrameScheduled) {
            smudgeFrameScheduled = true
            postOnAnimation(smudgeFrame)
        }
    }

    private fun flushSmudgeFrame() {
        if (!smudgeFrameScheduled && !hasPendingSmudge) return
        removeCallbacks(smudgeFrame)
        smudgeFrame.run()
    }

    private fun extendSmudge(normalizedX: Float, normalizedY: Float) {
        val x = normalizedX.coerceIn(0f, 1f)
        val y = normalizedY.coerceIn(0f, 1f)
        val raster = rasterBlendBitmap
        val tileSession = rasterTileBlendSession
        if (raster != null && tileSession != null) {
            val radius = (gestureBrushSize.coerceIn(.01f, 800f) / REFERENCE_WIDTH * raster.width).coerceAtLeast(.125f)
            val dirty = tileSession.blend(
                smudgeLastX * raster.width,
                smudgeLastY * raster.height,
                x * raster.width,
                y * raster.height,
                radius,
                (.3f + brushColorMix.coerceIn(0f, 1f) * .7f),
                brushHardness,
            )
            if (dirty != null) {
                smudgeChanged = true
                smudgeDirtyBounds = mergeDirtyBounds(smudgeDirtyBounds, dirty)
                invalidate(
                    (dirty.left * width.toFloat() / raster.width).toInt() - 2,
                    (dirty.top * height.toFloat() / raster.height).toInt() - 2,
                    (dirty.right * width.toFloat() / raster.width).toInt() + 2,
                    (dirty.bottom * height.toFloat() / raster.height).toInt() + 2,
                )
            }
            smudgeLastX = x
            smudgeLastY = y
            return
        }
        val radius = (gestureBrushSize.coerceIn(.01f, 800f) / REFERENCE_WIDTH).coerceAtLeast(.0001f)
        val beforeStep = localToolCandidates(smudgeLastX, smudgeLastY, x, y, radius)
        if (beforeStep.isEmpty()) {
            smudgeLastX = x
            smudgeLastY = y
            return
        }
        val changed = VectorBlend.apply(
            strokes = beforeStep,
            centerX = x,
            centerY = y,
            deltaX = x - smudgeLastX,
            deltaY = y - smudgeLastY,
            radius = radius,
            strength = (.3f + brushColorMix.coerceIn(0f, 1f) * .7f),
            hardness = brushHardness,
            pageWidth = pageModelWidth,
            pageHeight = pageModelHeight,
        ) { stroke -> stroke.layerId == activeLayerId && stroke.id !in protectedStrokeIds && stroke.layerId !in protectedLayerIds }
        if (changed != beforeStep) {
            replaceLocalToolCandidates(changed)
            smudgeChanged = true
            invalidateStrokeCache(beforeStep, changed)
        }
        smudgeLastX = x
        smudgeLastY = y
        invalidate()
    }

    private fun commitSmudgeGesture() {
        removeCallbacks(smudgeFrame)
        smudgeFrameScheduled = false
        hasPendingSmudge = false
        val raster = rasterBlendBitmap
        val tileSession = rasterTileBlendSession
        if (raster != null && tileSession != null) {
            if (smudgeChanged) {
                tileSession.commit().close()
                rasterTileSurfaceFor(activeLayerId)?.let { commitRasterLayerPatch(it, smudgeDirtyBounds) }
            } else tileSession.restoreOriginal()
            tileSession.close()
        } else {
            val before = smudgeBefore
            if (before != null && smudgeChanged) commitStrokeEdit(before, strokes)
        }
        rasterTileBlendSession = null
        rasterBlendBitmap = null
        smudgeBefore = null
        smudgeChanged = false
        smudgeDirtyBounds = null
        localToolStrokeIndices.clear()
        invalidate()
    }

    private fun cancelSmudgeGesture() {
        removeCallbacks(smudgeFrame)
        smudgeFrameScheduled = false
        hasPendingSmudge = false
        val tileSession = rasterTileBlendSession
        if (tileSession != null) {
            if (smudgeChanged) tileSession.restoreOriginal()
            tileSession.close()
            invalidate()
        } else {
            val before = smudgeBefore
            if (before != null && smudgeChanged) restoreStrokeSnapshot(before)
        }
        rasterTileBlendSession = null
        rasterBlendBitmap = null
        smudgeBefore = null
        smudgeChanged = false
        smudgeDirtyBounds = null
        localToolStrokeIndices.clear()
        invalidate()
    }

    private fun beginLiquifyGesture(x: Float, y: Float, pressure: Float) {
        removeCallbacks(liquifyFrame)
        removeCallbacks(liquifyHoldStart)
        removeCallbacks(liquifyHoldFrame)
        liquifyFrameScheduled = false
        hasPendingLiquify = false
        rasterTileLiquifySession?.close()
        rasterTileLiquifySession = null
        rasterLiquifyBitmap = rasterEditBitmapFor(activeLayerId)
        val raster = rasterLiquifyBitmap
        val surface = rasterTileSurfaceFor(activeLayerId)
        if (raster != null && surface != null) {
            rasterTileLiquifySession = RasterTileLiquifySession(surface, raster, selectionMaskForRasterTools())
            liquifyBefore = null
            localToolStrokeIndices.clear()
        } else {
            liquifyBefore = strokes.toList()
            prepareLocalToolStrokeIndex()
        }
        liquifyLastX = x.coerceIn(0f, 1f)
        liquifyLastY = y.coerceIn(0f, 1f)
        liquifyDownX = liquifyLastX
        liquifyDownY = liquifyLastY
        liquifyPressure = pressure.coerceIn(0f, 1f)
        liquifyMovedBeyondSlop = false
        liquifyHoldEligible = liquifyMode in setOf(
            LiquifyMode.TWIRL_CLOCKWISE,
            LiquifyMode.TWIRL_COUNTERCLOCKWISE,
            LiquifyMode.PINCH,
            LiquifyMode.EXPAND,
            LiquifyMode.CRYSTALS,
            LiquifyMode.RECONSTRUCT,
        )
        if (liquifyHoldEligible) postDelayed(liquifyHoldStart, liquifyLongPressDelayMs.coerceAtLeast(0L))
        liquifyChanged = false
        liquifyDirtyBounds = null
    }

    private fun scheduleLiquify(x: Float, y: Float, pressure: Float) {
        val dx = (x - liquifyDownX) * width.coerceAtLeast(1)
        val dy = (y - liquifyDownY) * height.coerceAtLeast(1)
        if (!liquifyMovedBeyondSlop && hypot(dx.toDouble(), dy.toDouble()) > navigationTouchSlop) {
            liquifyMovedBeyondSlop = true
            removeCallbacks(liquifyHoldStart)
            removeCallbacks(liquifyHoldFrame)
        }
        pendingLiquifyX = x
        pendingLiquifyY = y
        pendingLiquifyPressure = pressure.coerceIn(0f, 1f)
        hasPendingLiquify = true
        if (!liquifyFrameScheduled) {
            liquifyFrameScheduled = true
            postOnAnimation(liquifyFrame)
        }
    }

    private fun flushLiquifyFrame() {
        if (!liquifyFrameScheduled && !hasPendingLiquify) return
        removeCallbacks(liquifyFrame)
        liquifyFrame.run()
    }

    private fun extendLiquify(x: Float, y: Float, pressure: Float = liquifyPressure) {
        val nextX = x.coerceIn(0f, 1f)
        val nextY = y.coerceIn(0f, 1f)
        val raster = rasterLiquifyBitmap
        val tileSession = rasterTileLiquifySession
        if (raster != null && tileSession != null) {
            val radius = (gestureBrushSize.coerceIn(.01f, 800f) / REFERENCE_WIDTH * raster.width).coerceAtLeast(.125f)
            val dirty = tileSession.deform(
                liquifyLastX * raster.width,
                liquifyLastY * raster.height,
                nextX * raster.width,
                nextY * raster.height,
                radius,
                liquifyStrength * pressure.coerceIn(0f, 1f),
                liquifyHardness,
                liquifyMode,
            )
            if (dirty != null) {
                liquifyChanged = true
                liquifyDirtyBounds = mergeDirtyBounds(liquifyDirtyBounds, dirty)
                postInvalidateOnAnimation()
            }
            liquifyLastX = nextX
            liquifyLastY = nextY
            liquifyPressure = pressure.coerceIn(0f, 1f)
            return
        }
        val radius = (gestureBrushSize.coerceIn(.01f, 800f) / REFERENCE_WIDTH).coerceAtLeast(.0001f)
        val beforeStep = localToolCandidates(liquifyLastX, liquifyLastY, nextX, nextY, radius)
        if (beforeStep.isEmpty()) {
            liquifyLastX = nextX
            liquifyLastY = nextY
            return
        }
        val effectiveStrength = liquifyStrength * pressure.coerceIn(0f, 1f)
        val eligible: (InkStroke) -> Boolean = { stroke -> stroke.layerId == activeLayerId && stroke.id !in protectedStrokeIds && stroke.layerId !in protectedLayerIds }
        val warped = if (liquifyMode == LiquifyMode.RECONSTRUCT) {
            VectorLiquify.reconstruct(
                current = beforeStep,
                original = liquifyBefore.orEmpty(),
                centerX = nextX,
                centerY = nextY,
                radius = radius,
                strength = effectiveStrength,
                hardness = liquifyHardness,
                pageWidth = pageModelWidth,
                pageHeight = pageModelHeight,
                eligible = eligible,
            )
        } else {
            VectorLiquify.apply(
                strokes = beforeStep,
                centerX = nextX,
                centerY = nextY,
                deltaX = nextX - liquifyLastX,
                deltaY = nextY - liquifyLastY,
                radius = radius,
                strength = effectiveStrength,
                hardness = liquifyHardness,
                pageWidth = pageModelWidth,
                pageHeight = pageModelHeight,
                mode = liquifyMode,
                eligible = eligible,
            )
        }
        if (warped != beforeStep) {
            replaceLocalToolCandidates(warped)
            liquifyChanged = true
            invalidateStrokeCache(beforeStep, warped)
        }
        liquifyLastX = nextX
        liquifyLastY = nextY
        liquifyPressure = pressure.coerceIn(0f, 1f)
    }

    /** Blend and Liquify are local tools. Restricting each input sample to spatial-index hits keeps
     * latency proportional to the touched region instead of to every stroke and point on a page. */
    private fun prepareLocalToolStrokeIndex() {
        localToolStrokeIndices.clear()
        strokes.forEachIndexed { index, stroke -> localToolStrokeIndices[stroke.id] = index }
    }

    private fun localToolCandidates(startX: Float, startY: Float, endX: Float, endY: Float, radius: Float): List<InkStroke> {
        committedScene.ensureIndex(strokes, width, height)
        val aspect = pageModelWidth.coerceAtLeast(1f) / pageModelHeight.coerceAtLeast(1f)
        val radiusX = radius * width.coerceAtLeast(1)
        val radiusY = radius * aspect * height.coerceAtLeast(1)
        val candidateIds = committedScene.candidates(
            minOf(startX, endX) * width - radiusX,
            minOf(startY, endY) * height - radiusY,
            maxOf(startX, endX) * width + radiusX,
            maxOf(startY, endY) * height + radiusY,
        )
        if (candidateIds.isEmpty()) return emptyList()
        return candidateIds
            .mapNotNull(localToolStrokeIndices::get)
            .sorted()
            .map(strokes::get)
    }

    private fun replaceLocalToolCandidates(replacements: List<InkStroke>) {
        if (replacements.isEmpty()) return
        replacements.forEach { replacement ->
            localToolStrokeIndices[replacement.id]?.let { index -> strokes[index] = replacement }
        }
    }

    private fun finishLiquifyGesture() {
        removeCallbacks(liquifyFrame)
        removeCallbacks(liquifyHoldStart)
        removeCallbacks(liquifyHoldFrame)
        liquifyFrameScheduled = false
        hasPendingLiquify = false
        val raster = rasterLiquifyBitmap
        val tileSession = rasterTileLiquifySession
        if (raster != null && tileSession != null) {
            if (liquifyChanged) {
                tileSession.commit().close()
                rasterTileSurfaceFor(activeLayerId)?.let { commitRasterLayerPatch(it, liquifyDirtyBounds) }
            } else tileSession.restoreOriginal()
            tileSession.close()
        } else {
            val before = liquifyBefore
            if (before != null && liquifyChanged) commitStrokeEdit(before, strokes)
        }
        rasterTileLiquifySession = null
        rasterLiquifyBitmap = null
        liquifyBefore = null
        liquifyHoldEligible = false
        liquifyChanged = false
        liquifyDirtyBounds = null
        localToolStrokeIndices.clear()
        invalidate()
    }

    private fun cancelLiquifyGesture() {
        removeCallbacks(liquifyFrame)
        removeCallbacks(liquifyHoldStart)
        removeCallbacks(liquifyHoldFrame)
        liquifyFrameScheduled = false
        hasPendingLiquify = false
        val tileSession = rasterTileLiquifySession
        if (tileSession != null) {
            if (liquifyChanged) tileSession.restoreOriginal()
            tileSession.close()
            invalidate()
        } else {
            val before = liquifyBefore
            if (before != null && liquifyChanged) restoreStrokeSnapshot(before)
        }
        rasterTileLiquifySession = null
        rasterLiquifyBitmap = null
        liquifyBefore = null
        liquifyHoldEligible = false
        liquifyChanged = false
        liquifyDirtyBounds = null
        localToolStrokeIndices.clear()
        invalidate()
    }

    private fun beginCorrectLineGesture(x: Float, y: Float) {
        val index = if (correctLineMode == CorrectLineMode.CONNECT) findNearestEndpointStroke(x, y, null, .06f) else findStroke(x, y, .05f)
        val target = strokes.getOrNull(index)
        if (target == null || target.id in protectedStrokeIds || target.layerId in protectedLayerIds) {
            correctionBefore = null
            correctionStrokeId = null
            return
        }
        correctionBefore = strokes.toList()
        correctionStrokeId = target.id
        correctionStartX = x
        correctionEndX = x
        correctionEndY = y
        correctionPointIndex = target.points.indices.minByOrNull { pointIndex ->
            val point = target.points[pointIndex]
            hypot(((point.x - x) * pageModelWidth).toDouble(), ((point.y - y) * pageModelHeight).toDouble())
        } ?: -1
        correctionChanged = false
        if (correctLineMode !in setOf(CorrectLineMode.CONNECT, CorrectLineMode.CONTROL_POINTS)) updateCorrectLine(correctLineAmount)
    }

    private fun updateCorrectLineGesture(x: Float, y: Float) {
        if (correctionBefore == null || correctionStrokeId == null) return
        correctionEndX = x
        correctionEndY = y
        if (correctLineMode == CorrectLineMode.CONTROL_POINTS) {
            val id = correctionStrokeId ?: return
            val strokeIndex = strokes.indexOfFirst { it.id == id }
            val current = strokes.getOrNull(strokeIndex) ?: return
            if (correctionPointIndex !in current.points.indices) return
            val changed = current.copy(points = current.points.mapIndexed { index, point -> if (index == correctionPointIndex) point.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else point })
            strokes[strokeIndex] = changed
            correctionChanged = true
            invalidateStrokeCache(listOf(current), listOf(changed))
            invalidate()
        } else if (correctLineMode != CorrectLineMode.CONNECT) {
            updateCorrectLine((correctLineAmount + (x - correctionStartX) * 1.5f).coerceIn(0f, 1f))
        }
    }

    private fun updateCorrectLine(amount: Float) {
        val before = correctionBefore ?: return
        val id = correctionStrokeId ?: return
        val original = before.firstOrNull { it.id == id } ?: return
        val corrected = StrokeCorrection.apply(original, correctLineMode, amount, pageModelWidth, pageModelHeight)
        val index = strokes.indexOfFirst { it.id == id }
        if (index < 0) return
        val previous = strokes[index]
        strokes[index] = corrected
        correctionChanged = corrected != original
        invalidateStrokeCache(listOf(previous), listOf(corrected))
        invalidate()
    }

    private fun finishCorrectLineGesture() {
        val before = correctionBefore
        if (before != null && correctLineMode == CorrectLineMode.CONNECT) connectCorrectionStrokes()
        if (before != null && correctionChanged) commitStrokeEdit(before, strokes)
        correctionBefore = null
        correctionStrokeId = null
        correctionChanged = false
        correctionPointIndex = -1
        invalidate()
    }

    private fun connectCorrectionStrokes() {
        val sourceId = correctionStrokeId ?: return
        val source = strokes.firstOrNull { it.id == sourceId } ?: return
        val targetIndex = findNearestEndpointStroke(correctionEndX, correctionEndY, sourceId, .06f)
        val target = strokes.getOrNull(targetIndex)?.takeIf { it.id != sourceId && it.layerId == source.layerId && it.id !in protectedStrokeIds } ?: return
        if (source.points.isEmpty() || target.points.isEmpty()) return
        val candidates = listOf(
            Triple(source.points, target.points, pointDistance(source.points.last(), target.points.first())),
            Triple(source.points, target.points.reversed(), pointDistance(source.points.last(), target.points.last())),
            Triple(source.points.reversed(), target.points, pointDistance(source.points.first(), target.points.first())),
            Triple(source.points.reversed(), target.points.reversed(), pointDistance(source.points.first(), target.points.last())),
        )
        val best = candidates.minBy { it.third }
        if (best.third > maxOf(source.width, target.width, 48f) * 2f) return
        val merged = source.copy(points = best.first + best.second.drop(1))
        val sourceIndex = strokes.indexOfFirst { it.id == source.id }
        val removed = listOf(source, target)
        strokes.removeAll { it.id == source.id || it.id == target.id }
        strokes.add(sourceIndex.coerceIn(0, strokes.size), merged)
        correctionChanged = true
        invalidateStrokeCache(removed, listOf(merged))
    }

    private fun pointDistance(first: StrokePoint, second: StrokePoint): Float = hypot(
        ((first.x - second.x) * pageModelWidth).toDouble(),
        ((first.y - second.y) * pageModelHeight).toDouble(),
    ).toFloat()

    private fun findNearestEndpointStroke(x: Float, y: Float, excludingId: String?, threshold: Float): Int = strokes.indices
        .filter { index ->
            val stroke = strokes[index]
            stroke.id != excludingId && stroke.id !in protectedStrokeIds && stroke.points.isNotEmpty()
        }
        .minByOrNull { index ->
            val stroke = strokes[index]
            minOf(
                hypot((stroke.points.first().x - x).toDouble(), (stroke.points.first().y - y).toDouble()),
                hypot((stroke.points.last().x - x).toDouble(), (stroke.points.last().y - y).toDouble()),
            )
        }
        ?.takeIf { index ->
            val stroke = strokes[index]
            minOf(
                hypot((stroke.points.first().x - x).toDouble(), (stroke.points.first().y - y).toDouble()),
                hypot((stroke.points.last().x - x).toDouble(), (stroke.points.last().y - y).toDouble()),
            ) <= threshold
        } ?: -1

    private fun cancelCorrectLineGesture() {
        val before = correctionBefore
        if (before != null && correctionChanged) restoreStrokeSnapshot(before)
        correctionBefore = null
        correctionStrokeId = null
        correctionChanged = false
        correctionPointIndex = -1
        invalidate()
    }

    private fun normalizeAngleDelta(first: Float, second: Float): Float = ((first - second + 180f).mod(360f)) - 180f

    private fun selectionBounds(): android.graphics.RectF? {
        var left = Float.POSITIVE_INFINITY; var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY; var bottom = Float.NEGATIVE_INFINITY
        strokes.forEach { stroke -> if (stroke.id in selectedStrokeIds) stroke.points.forEach { point ->
            left = minOf(left, point.x); top = minOf(top, point.y)
            right = maxOf(right, point.x); bottom = maxOf(bottom, point.y)
        } }
        return if (left.isFinite()) android.graphics.RectF(left, top, right, bottom) else null
    }

    /** Returns a corner, edge, or rotation transform when the touch lands on a handle. */
    private fun hitSelectionHandle(normalizedX: Float, normalizedY: Float): Int {
        if (selectedStrokeIds.isEmpty()) return TRANSFORM_NONE
        val bounds = selectionBounds() ?: return TRANSFORM_NONE
        val corners = listOf(
            bounds.left to bounds.top, bounds.right to bounds.top,
            bounds.right to bounds.bottom, bounds.left to bounds.bottom,
        )
        val boundsWidthPx = (bounds.width() * width).coerceAtLeast(0f)
        val boundsHeightPx = (bounds.height() * height).coerceAtLeast(0f)
        if (boundsWidthPx < 1f && boundsHeightPx < 1f) return TRANSFORM_NONE
        val scaleHandleRadius = if (boundsWidthPx < SELECTION_HANDLE_TOUCH_PX * 2f || boundsHeightPx < SELECTION_HANDLE_TOUCH_PX * 2f) SELECTION_SMALL_HANDLE_TOUCH_PX else SELECTION_HANDLE_TOUCH_PX
        if (corners.any { hypot(((it.first - normalizedX) * width).toDouble(), ((it.second - normalizedY) * height).toDouble()).toFloat() <= scaleHandleRadius }) return TRANSFORM_SCALE
        val horizontalEdges = listOf(bounds.left to bounds.centerY(), bounds.right to bounds.centerY())
        if (boundsWidthPx >= scaleHandleRadius * 2f && horizontalEdges.any { hypot(((it.first - normalizedX) * width).toDouble(), ((it.second - normalizedY) * height).toDouble()).toFloat() <= scaleHandleRadius }) return TRANSFORM_SCALE_X
        val verticalEdges = listOf(bounds.centerX() to bounds.top, bounds.centerX() to bounds.bottom)
        if (boundsHeightPx >= scaleHandleRadius * 2f && verticalEdges.any { hypot(((it.first - normalizedX) * width).toDouble(), ((it.second - normalizedY) * height).toDouble()).toFloat() <= scaleHandleRadius }) return TRANSFORM_SCALE_Y
        val rotY = (bounds.top - SELECTION_ROTATE_HANDLE_OFFSET_PX / height.coerceAtLeast(1))
        if (hypot(((bounds.centerX() - normalizedX) * width).toDouble(), ((rotY - normalizedY) * height).toDouble()).toFloat() <= SELECTION_HANDLE_TOUCH_PX) return TRANSFORM_ROTATE
        return TRANSFORM_NONE
    }

    private fun applySelectionTransform(scale: Float, rotation: Float) {
        applySelectionTransform(scale, scale, rotation)
    }

    private fun applySelectionTransform(scaleX: Float, scaleY: Float, rotation: Float) {
        val before = selectionGestureBefore ?: return
        val selectedSet = selectedStrokeIds.toHashSet()
        val movedBefore = strokes.filter { it.id in selectedSet }
        val transformed = StrokeTransform.transformNonUniform(before.filter { it.id in selectedSet }, selectionTransformCenterX, selectionTransformCenterY, scaleX.coerceAtLeast(.01f), scaleY.coerceAtLeast(.01f), rotation, 0f, 0f, pageModelWidth, pageModelHeight).associateBy(InkStroke::id)
        for (i in strokes.indices) {
            val replacement = transformed[strokes[i].id]
            if (replacement != null) strokes[i] = replacement
        }
        invalidateStrokeCache(movedBefore, strokes.filter { it.id in selectedSet })
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        if (!selectionTransformArmed || selectedStrokeIds.isEmpty()) return
        val bounds = selectionBounds() ?: return
        val handleRadius = SELECTION_HANDLE_TOUCH_PX / 2.5f / width.coerceAtLeast(1)
        val corners = listOf(
            bounds.left to bounds.top, bounds.right to bounds.top,
            bounds.right to bounds.bottom, bounds.left to bounds.bottom,
        )
        val rotY = bounds.top - SELECTION_ROTATE_HANDLE_OFFSET_PX / height.coerceAtLeast(1)
        paint.style = Paint.Style.STROKE; paint.color = Color.rgb(17, 17, 17); paint.alpha = 255; paint.strokeWidth = 2f
        canvas.drawLine(bounds.centerX() * width, bounds.top * height, bounds.centerX() * width, rotY * height, paint)
        val edgeHandles = buildList {
            if (bounds.width() * width >= SELECTION_HANDLE_TOUCH_PX * 2f) addAll(listOf(bounds.left to bounds.centerY(), bounds.right to bounds.centerY()))
            if (bounds.height() * height >= SELECTION_HANDLE_TOUCH_PX * 2f) addAll(listOf(bounds.centerX() to bounds.top, bounds.centerX() to bounds.bottom))
        }
        val drawHandles = listOf(bounds.centerX() to rotY) + corners + edgeHandles
        drawHandles.forEach { (hx, hy) ->
            val cx = hx * width; val cy = hy * height
            selectionHandlePaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, handleRadius * width, selectionHandlePaint)
            selectionHandleStrokePaint.color = Color.rgb(17, 17, 17)
            selectionHandleStrokePaint.strokeWidth = 3f
            canvas.drawCircle(cx, cy, handleRadius * width, selectionHandleStrokePaint)
            selectionHandlePaint.color = Color.rgb(17, 17, 17)
            canvas.drawCircle(cx, cy, 3.5f, selectionHandlePaint)
        }
    }

    private fun drawSymmetryGuides(canvas: Canvas) {
        if (symmetryMode == SymmetryMode.NONE || width <= 0 || height <= 0) return
        paint.style = Paint.Style.STROKE; paint.color = Color.rgb(24, 119, 242); paint.alpha = 120; paint.strokeWidth = 1.5f
        val cx = width / 2f; val cy = height / 2f
        when (symmetryMode) {
            SymmetryMode.VERTICAL, SymmetryMode.QUADRANT -> canvas.drawLine(cx, 0f, cx, height.toFloat(), paint)
            else -> Unit
        }
        when (symmetryMode) {
            SymmetryMode.HORIZONTAL, SymmetryMode.QUADRANT -> canvas.drawLine(0f, cy, width.toFloat(), cy, paint)
            else -> Unit
        }
        when (symmetryMode) {
            SymmetryMode.RADIAL_4, SymmetryMode.RADIAL_6, SymmetryMode.RADIAL_8 -> {
                val n = when (symmetryMode) { SymmetryMode.RADIAL_4 -> 4; SymmetryMode.RADIAL_6 -> 6; SymmetryMode.RADIAL_8 -> 8; else -> 4 }
                for (k in 0 until n) {
                    val a = Math.toRadians((360.0 / n * k))
                    canvas.drawLine(cx, cy, (cx + kotlin.math.cos(a) * width).toFloat(), (cy + kotlin.math.sin(a) * height).toFloat(), paint)
                }
            }
            else -> Unit
        }
    }

    private fun resolveTool(event: MotionEvent, toolType: Int): EditorTool {
        if (toolType == MotionEvent.TOOL_TYPE_ERASER) return EditorTool.ERASER
        val action = resolveStylusButtonAction(event.buttonState)
        return when (action) {
            StylusButtonAction.ERASER -> EditorTool.ERASER
            StylusButtonAction.LASSO -> EditorTool.SELECT
            StylusButtonAction.EYEDROPPER -> EditorTool.EYEDROPPER
            StylusButtonAction.HAND, StylusButtonAction.UNDO, StylusButtonAction.REDO, StylusButtonAction.NONE -> tool
        }
    }

    private fun makeStroke(points: List<StrokePoint>): InkStroke =
        activeStrokeTemplate?.copy(points = points.toList())
            ?: withCurrentBrushBehavior(buildStroke(points)).copy(width = gestureBrushSize)

    private fun withCurrentBrushBehavior(stroke: InkStroke): InkStroke = stroke.copy(
        secondaryColorArgb = secondaryInkColor.toUInt().toLong(),
        procreateColorDynamics = procreateColorDynamics,
        procreateTaper = procreateTaper,
        inputKind = if (activeInputWasStylus) StrokeInputKind.STYLUS else StrokeInputKind.TOUCH,
        shapeTouchRotation = shapeTouchRotation,
        dualShapeTouchRotation = dualShapeTouchRotation,
        shapeScreenRotationDegrees = if (shapeOrientToScreen) -viewportRotationDegrees else 0f,
        dualShapeScreenRotationDegrees = if (dualShapeOrientToScreen) -viewportRotationDegrees else 0f,
        grainFilteringMode = grainFilteringMode,
        dualGrainAssetPath = dualGrainAssetPath,
        dualGrainDensity = dualGrainDensity,
        dualGrainDensityMinimum = dualGrainDensityMinimum,
        dualGrainDensityDynamics = dualGrainDensityDynamics,
        dualGrainScale = dualGrainScale,
        dualGrainMovement = dualGrainMovement,
        dualGrainZoom = dualGrainZoom,
        dualGrainRotationDegrees = dualGrainRotationDegrees,
        dualGrainStrokeRotation = dualGrainStrokeRotation,
        dualGrainBrightness = dualGrainBrightness,
        dualGrainContrast = dualGrainContrast,
        dualGrainReversed = dualGrainReversed,
        dualGrainEmphasized = dualGrainEmphasized,
        dualGrainOffsetJitter = dualGrainOffsetJitter,
        dualGrainCompositeMode = dualGrainCompositeMode,
        dualGrainApplyPerPlot = dualGrainApplyPerPlot,
        dualGrainFilteringMode = dualGrainFilteringMode,
        procreateRenderingMode = procreateRenderingMode,
        procreateBrushBlendMode = procreateBrushBlendMode,
        procreateBurntEdgeBlendMode = procreateBurntEdgeBlendMode,
        procreateLuminanceBlending = procreateLuminanceBlending,
        alphaThresholdEnabled = alphaThresholdEnabled,
        alphaThreshold = alphaThreshold,
        grainDensityMinimum = grainDensityMinimum,
        shapeRoundnessHorizontalJitter = shapeRoundnessHorizontalJitter,
        shapeRoundnessVerticalJitter = shapeRoundnessVerticalJitter,
        dualShapeRoundnessHorizontalJitter = dualShapeRoundnessHorizontalJitter,
        dualShapeRoundnessVerticalJitter = dualShapeRoundnessVerticalJitter,
        grainMovement = grainMovement,
        grainZoom = grainZoom,
        grainStrokeRotation = grainStrokeRotation,
        grainCompositeMode = grainCompositeMode,
        grainApplyPerPlot = grainApplyPerPlot,
        tiltGradation = tiltGradation,
        tiltGradationStart = tiltGradationStart,
        tiltBleed = tiltBleed,
        tiltBleedStart = tiltBleedStart,
        tiltSizeCompression = tiltSizeCompression,
        strokeFalloff = strokeFalloff,
        strokePathJitter = strokePathJitter,
        strokePathLongitudinalJitter = strokePathLongitudinalJitter,
        dualStrokePathJitter = dualStrokePathJitter,
        dualStrokePathLongitudinalJitter = dualStrokePathLongitudinalJitter,
    )

    private fun buildStroke(points: List<StrokePoint>) = InkStroke(id = currentStrokeId, engineKind = activeBrushEngineKind(), points = points.toList(), colorArgb = inkColor.toUInt().toLong(), width = baseWidth, opacity = inkOpacity, brushId = brushId, layerId = activeLayerId, tipAssetPath = tipAssetPath, tipAssetPaths = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }, dualTipAssetPaths = dualTipAssetPaths, grainAssetPath = grainAssetPath, grainDensity = grainDensity, grainDensityDynamics = grainDensityDynamics, grainScale = grainScale, grainRotationDegrees = grainRotationDegrees, grainBrightness = grainBrightness, grainContrast = grainContrast, grainReversed = grainReversed, grainEmphasized = grainEmphasized, grainOffsetJitter = grainOffsetJitter, spacing = brushSpacing, spacingMode = brushSpacingMode, flow = brushFlow, hardness = brushHardness, antiAliasMode = brushAntiAliasMode, blur = brushBlur, blurUnit = brushBlurUnit, blurLinkedToSize = brushBlurLinkedToSize, thickness = brushThickness, thicknessVertical = brushThicknessVertical, rotationDegrees = brushRotation, followStylusOrientation = followStylusOrientation, followStrokeDirection = followStrokeDirection, randomRotationDegrees = randomRotationDegrees, strokeRotationJitterDegrees = strokeRotationJitterDegrees, stampCount = stampCount, stampCountJitter = stampCountJitter, continuousPlot = brushContinuousPlot, colorJitterHueDegrees = colorJitterHueDegrees, colorJitterSaturation = colorJitterSaturation, colorJitterValue = colorJitterValue, colorJitterTarget = colorJitterTarget, colorJitterPerTipEnabled = colorJitterPerTipEnabled, colorJitterHueDynamics = colorJitterHueDynamics, colorJitterSaturationDynamics = colorJitterSaturationDynamics, colorJitterValueDynamics = colorJitterValueDynamics, colorJitterSubColorMix = colorJitterSubColorMix, colorJitterSubColorMixDynamics = colorJitterSubColorMixDynamics, colorJitterPerStrokeEnabled = colorJitterPerStrokeEnabled, colorJitterStrokeHueDegrees = colorJitterStrokeHueDegrees, colorJitterStrokeSaturation = colorJitterStrokeSaturation, colorJitterStrokeValue = colorJitterStrokeValue, colorJitterStrokeSubColorMix = colorJitterStrokeSubColorMix, procreateColorDynamics = procreateColorDynamics, procreateWetMix = procreateWetMix, watercolor = brushWatercolor, cspColorMixMode = cspColorMixMode, colorMix = brushColorMix, colorMixPressureEnabled = colorMixPressureEnabled, colorMixPressureMinimum = colorMixPressureMinimum, colorMixPressureCurve = colorMixPressureCurve, colorMixSecondaryDynamics = colorMixSecondaryDynamics, paintMix = brushPaintMix, paintMixPressureEnabled = paintMixPressureEnabled, paintMixPressureMinimum = paintMixPressureMinimum, paintMixPressureCurve = paintMixPressureCurve, paintMixSecondaryDynamics = paintMixSecondaryDynamics, colorStretch = colorStretch, cspPigmentMixingMode = cspPigmentMixingMode, cspBrightnessCorrection = cspBrightnessCorrection, colorMixBlur = colorMixBlur, colorMixBlurUnit = colorMixBlurUnit, colorMixBlurAutomatic = colorMixBlurAutomatic, colorMixBlurDynamics = colorMixBlurDynamics, spray = brushSpray, sprayParticleSize = sprayParticleSize, sprayParticleSizeUnit = sprayParticleSizeUnit, sprayParticleSizeSyncBrushSize = sprayParticleSizeSyncBrushSize, sprayParticleSizePressureEnabled = sprayParticleSizePressureEnabled, sprayParticleSizePressureMinimum = sprayParticleSizePressureMinimum, sprayParticleSizePressureCurve = sprayParticleSizePressureCurve, sprayParticleSizeSecondaryDynamics = sprayParticleSizeSecondaryDynamics, sprayDensity = sprayDensity, sprayDensityPressureEnabled = sprayDensityPressureEnabled, sprayDensityPressureMinimum = sprayDensityPressureMinimum, sprayDensityPressureCurve = sprayDensityPressureCurve, sprayDensitySecondaryDynamics = sprayDensitySecondaryDynamics, sprayBias = sprayBias, spraySpread = spraySpread, sprayParticleRotationDegrees = sprayParticleRotationDegrees, sprayParticleFollowStylusOrientation = sprayParticleFollowStylusOrientation, sprayParticleFollowStrokeDirection = sprayParticleFollowStrokeDirection, sprayParticleRandomRotationDegrees = sprayParticleRandomRotationDegrees, ribbon = brushRibbon, tipSequenceMode = brushTipSequenceMode, tipFlipHorizontalMode = tipFlipHorizontalMode, tipFlipVerticalMode = tipFlipVerticalMode, blendTipsWithDarken = blendTipsWithDarken, dualBrush = brushDual, dualSizeScale = dualSizeScale, dualSizeDynamics = dualSizeDynamics, dualSpacing = dualSpacing, dualSpacingMode = dualSpacingMode, dualSpacingDynamics = dualSpacingDynamics, dualFlow = dualFlow, dualFlowDynamics = dualFlowDynamics, dualHardness = dualHardness, dualAntiAliasMode = dualAntiAliasMode, dualThickness = dualThickness, dualThicknessDynamics = dualThicknessDynamics, dualThicknessVertical = dualThicknessVertical, dualRotationDegrees = dualRotationDegrees, dualFollowStylusOrientation = dualFollowStylusOrientation, dualFollowStrokeDirection = dualFollowStrokeDirection, dualRandomRotationDegrees = dualRandomRotationDegrees, dualStrokeRotationJitterDegrees = dualStrokeRotationJitterDegrees, dualStampCount = dualStampCount, dualStampCountJitter = dualStampCountJitter, dualRibbon = dualRibbon, dualSpray = dualSpray, dualSprayParticleSize = dualSprayParticleSize, dualSprayParticleSizeUnit = dualSprayParticleSizeUnit, dualSprayParticleSizeSyncBrushSize = dualSprayParticleSizeSyncBrushSize, dualSprayParticleSizeDynamics = dualSprayParticleSizeDynamics, dualSprayDensity = dualSprayDensity, dualSprayDensityDynamics = dualSprayDensityDynamics, dualSprayBias = dualSprayBias, dualSpraySpread = dualSpraySpread, dualSprayParticleRotationDegrees = dualSprayParticleRotationDegrees, dualSprayParticleFollowStylusOrientation = dualSprayParticleFollowStylusOrientation, dualSprayParticleFollowStrokeDirection = dualSprayParticleFollowStrokeDirection, dualSprayParticleRandomRotationDegrees = dualSprayParticleRandomRotationDegrees, dualTipSequenceMode = dualTipSequenceMode, dualTipFlipHorizontalMode = dualTipFlipHorizontalMode, dualTipFlipVerticalMode = dualTipFlipVerticalMode, dualBlendTipsWithDarken = dualBlendTipsWithDarken, dualCompositeMode = dualCompositeMode, dualApplyRgb = dualApplyRgb, taperMode = taperMode, taperStartEnabled = taperStartEnabled, taperStartLength = taperStartLength, taperStartLengthUnit = taperStartLengthUnit, taperStartMinimum = taperStartMinimum, taperEndEnabled = taperEndEnabled, taperEndLength = taperEndLength, taperEndLengthUnit = taperEndLengthUnit, taperEndMinimum = taperEndMinimum, waterEdgeEnabled = waterEdgeEnabled, waterEdgeRadius = waterEdgeRadius, waterEdgeRadiusUnit = waterEdgeRadiusUnit, waterEdgeOpacity = waterEdgeOpacity, waterEdgeDarkness = waterEdgeDarkness, waterEdgeBlur = waterEdgeBlur, waterEdgeBlurUnit = waterEdgeBlurUnit, sizePressureEnabled = sizePressureEnabled, sizePressureMinimum = sizePressureMinimum, sizePressureCurve = sizePressureCurve, sizeSecondaryDynamics = sizeSecondaryDynamics, opacityPressureEnabled = opacityPressureEnabled, opacityPressureMinimum = opacityPressureMinimum, opacityPressureCurve = opacityPressureCurve, opacitySecondaryDynamics = opacitySecondaryDynamics, flowPressureEnabled = flowPressureEnabled, flowPressureMinimum = flowPressureMinimum, flowPressureCurve = flowPressureCurve, flowSecondaryDynamics = flowSecondaryDynamics, spacingPressureEnabled = spacingPressureEnabled, spacingPressureMinimum = spacingPressureMinimum, spacingPressureCurve = spacingPressureCurve, spacingSecondaryDynamics = spacingSecondaryDynamics, thicknessPressureEnabled = thicknessPressureEnabled, thicknessPressureMinimum = thicknessPressureMinimum, thicknessPressureCurve = thicknessPressureCurve, thicknessSecondaryDynamics = thicknessSecondaryDynamics, blurPressureEnabled = blurPressureEnabled, blurPressureMinimum = blurPressureMinimum, blurPressureCurve = blurPressureCurve, blurSecondaryDynamics = blurSecondaryDynamics)

    private data class RasterStrokePatch(val bitmap: Bitmap, val left: Int, val top: Int)

    private data class RasterStampIncrement(
        val stroke: InkStroke,
        val startIndex: Int,
        val changedPoints: List<StrokePoint>,
        val stamped: Boolean,
        val resultingRawPointCount: Int = 0,
    )

    /**
     * The safe associative subset is deposited once per sampled dab. More complex whole-stroke,
     * wet, dual-tip and destination-dependent programs keep the exact pen-up fallback until their
     * own retained transaction compositor exists.
     */
    private fun supportsIncrementalRasterStampPaint(stroke: InkStroke): Boolean {
        val layerId = stroke.layerId
        val incrementalGeometry = usesStampedRenderer(stroke) || stroke.brushId != "system-ps19"
        return incrementalGeometry && !stroke.dualBrush &&
            stroke.masks.isEmpty() && stroke.opacity >= .999f &&
            ProcreateBrushBlend.normalize(stroke.procreateBrushBlendMode) == ProcreateBrushBlend.NORMAL &&
            !stroke.blendTipsWithDarken &&
            (stroke.grainAssetPath == null || stroke.grainDensity <= 0f || stroke.grainApplyPerPlot) &&
            stroke.strokeFalloff <= .0001f && !stroke.taperStartEnabled && !stroke.taperEndEnabled &&
            stroke.procreateTaper.isDisabled() &&
            stroke.procreateWetMix.isDisabled() && !stroke.watercolor &&
            stroke.colorMix <= .0001f && stroke.paintMix <= .0001f &&
            !stroke.waterEdgeEnabled && stroke.tiltBleed <= .0001f &&
            postCorrection <= .0001f &&
            (layerOpacities[layerId] ?: 1f) >= .999f &&
            (layerBlendModes[layerId] ?: LayerBlendMode.NORMAL) == LayerBlendMode.NORMAL &&
            layerId !in alphaLockedLayers && layerId !in clippedLayers &&
            layerMasks[layerId]?.enabled != true
    }

    /**
     * Renders only newly sampled stamps into one bounded patch. That exact patch is presented on
     * the low-latency surface and appended to the still-private tile transaction; no stable bitmap
     * is touched until the atomic gesture commit.
     */
    private fun renderIncrementalRasterPaintPatch(
        frontCanvas: Canvas?,
        strokes: List<InkStroke>,
        finalCompleteInput: Boolean,
    ): Boolean {
        if (!rasterIncrementalPaintEnabled || strokes.isEmpty() || strokes.any { !supportsIncrementalRasterStampPaint(it) }) return false
        val raster = rasterWetPaintBitmap?.takeUnless(Bitmap::isRecycled) ?: return false
        val session = rasterPaintSession ?: return false
        val increments = mutableListOf<RasterStampIncrement>()
        rasterIncrementalRendering = true
        try {
            strokes.forEach { stroke ->
                if (usesStampedRenderer(stroke)) {
                    val sampled = primaryStampedPoints(stroke)
                    val start = (rasterPrimaryStampCounts[stroke.id] ?: 0).coerceIn(0, sampled.size)
                    if (start < sampled.size) increments += RasterStampIncrement(
                        stroke, start, sampled.subList(start, sampled.size), stamped = true,
                    )
                } else {
                    val previousCount = rasterRawPointCounts[stroke.id] ?: 0
                    val fullCount = if (finalCompleteInput) stroke.points.size else {
                        if (previousCount == 0) stroke.points.size else previousCount + (stroke.points.size - 1).coerceAtLeast(0)
                    }
                    val tail = when {
                        finalCompleteInput && stroke.points.size == 1 && previousCount <= 1 -> stroke.points
                        finalCompleteInput && stroke.points.size > previousCount -> stroke.points.subList((previousCount - 1).coerceAtLeast(0), stroke.points.size)
                        !finalCompleteInput && stroke.points.size >= 2 -> stroke.points
                        else -> emptyList()
                    }
                    if (tail.isNotEmpty()) increments += RasterStampIncrement(
                        stroke.copy(points = tail), 0, tail, stamped = false, resultingRawPointCount = fullCount,
                    ) else if (!finalCompleteInput && previousCount == 0 && stroke.points.size == 1) {
                        // Show the contact dot through the ordinary front-buffer renderer, but do
                        // not make it durable until UP proves this was a tap instead of a segment.
                        rasterRawPointCounts[stroke.id] = 1
                    }
                }
            }
            if (increments.isEmpty()) return finalCompleteInput
            val changedPoints = increments.flatMap(RasterStampIncrement::changedPoints)
            val maximumWidth = increments.maxOf { increment ->
                val stroke = increment.stroke
                val base = stroke.width.coerceAtLeast(1f) / REFERENCE_WIDTH * raster.width
                val particle = if (stroke.spray && !stroke.sprayParticleSizeSyncBrushSize) {
                    when (stroke.sprayParticleSizeUnit) {
                        1 -> stroke.sprayParticleSize / REFERENCE_WIDTH * raster.width
                        else -> base * stroke.sprayParticleSize.coerceAtLeast(1f) / 100f
                    }
                } else base
                val spread = if (stroke.spray) base * stroke.spraySpread.coerceIn(0f, 4f) else 0f
                val pathJitter = app.inkloom.document.StrokePathJitter.maximumRadius(
                    maxOf(base, particle), stroke.strokePathJitter, stroke.strokePathLongitudinalJitter,
                )
                val blur = base * stroke.blur.coerceAtLeast(0f) / 25f
                maxOf(base, particle) * maxOf(1f, stroke.thickness.coerceIn(.05f, 2f)) * 1.5f + spread + pathJitter + blur
            }
            val padding = (maximumWidth * 1.5f + 12f).coerceAtLeast(12f)
            val patchLeft = kotlin.math.floor(changedPoints.minOf(StrokePoint::x) * raster.width - padding).toInt().coerceIn(0, raster.width - 1)
            val patchTop = kotlin.math.floor(changedPoints.minOf(StrokePoint::y) * raster.height - padding).toInt().coerceIn(0, raster.height - 1)
            val patchRight = kotlin.math.ceil(changedPoints.maxOf(StrokePoint::x) * raster.width + padding).toInt().coerceIn(patchLeft + 1, raster.width)
            val patchBottom = kotlin.math.ceil(changedPoints.maxOf(StrokePoint::y) * raster.height + padding).toInt().coerceIn(patchTop + 1, raster.height)
            val patchWidth = patchRight - patchLeft
            val patchHeight = patchBottom - patchTop
            val patch = obtainRasterIncrementalPatchBitmap(patchWidth, patchHeight)
            run {
                val target = Canvas(patch)
                val patchClip = target.save()
                target.clipRect(0, 0, patchWidth, patchHeight)
                target.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                val scaleX = raster.width / width.coerceAtLeast(1).toFloat()
                val scaleY = raster.height / height.coerceAtLeast(1).toFloat()
                val checkpoint = target.save()
                target.scale(scaleX, scaleY)
                target.translate(-patchLeft / scaleX, -patchTop / scaleY)
                val previousSynchronousDecode = forceSynchronousTipDecode
                val previousIncrementalDraw = incrementalActiveDraw
                forceSynchronousTipDecode = true
                incrementalActiveDraw = true
                try {
                    increments.forEach { increment -> drawStroke(target, increment.stroke, increment.startIndex, 0) }
                } finally {
                    incrementalActiveDraw = previousIncrementalDraw
                    forceSynchronousTipDecode = previousSynchronousDecode
                }
                target.restoreToCount(checkpoint)
                selectionMaskForRasterTools()?.let { selection ->
                    RasterSelectionMask(selection, raster.width, raster.height).apply(
                        patch,
                        Rect(patchLeft, patchTop, patchRight, patchBottom),
                    )
                }
                val changed = session.compositePaintPatch(
                    patch, patchLeft, patchTop, increments.map(RasterStampIncrement::stroke), wetComposite = false,
                    contentWidth = patchWidth, contentHeight = patchHeight,
                )
                if (changed != null) wetPaintDirtyBounds = mergeDirtyBounds(wetPaintDirtyBounds, changed)
                frontCanvas?.drawBitmap(
                    patch,
                    Rect(0, 0, patchWidth, patchHeight),
                    RectF(
                        patchLeft * width.toFloat() / raster.width,
                        patchTop * height.toFloat() / raster.height,
                        patchRight * width.toFloat() / raster.width,
                        patchBottom * height.toFloat() / raster.height,
                    ),
                    rasterFrontPatchPaint,
                )
                target.restoreToCount(patchClip)
                increments.forEach { increment ->
                    if (increment.stamped) {
                        rasterPrimaryStampCounts[increment.stroke.id] = increment.startIndex + increment.changedPoints.size
                    } else {
                        rasterRawPointCounts[increment.stroke.id] = increment.resultingRawPointCount
                    }
                }
                rasterIncrementalPatchCount++
            }
            return true
        } finally {
            rasterIncrementalRendering = false
        }
    }

    private fun clearRasterIncrementalPaintState() {
        rasterIncrementalPaintEnabled = false
        rasterIncrementalRendering = false
        rasterPrimaryStampCounts.clear()
        rasterRawPointCounts.clear()
        rasterPrimarySamples.clear()
        rasterPrimaryScatterRandoms.clear()
        rasterPrimaryRotationRandoms.clear()
        rasterIncrementalPatchBitmap?.recycle()
        rasterIncrementalPatchBitmap = null
    }

    private fun obtainRasterIncrementalPatchBitmap(requiredWidth: Int, requiredHeight: Int): Bitmap {
        val existing = rasterIncrementalPatchBitmap
        if (existing != null && !existing.isRecycled && existing.width >= requiredWidth && existing.height >= requiredHeight) return existing
        existing?.takeUnless(Bitmap::isRecycled)?.recycle()
        fun bucket(value: Int): Int {
            var result = 64
            while (result < value && result < 4096) result *= 2
            return maxOf(value, result)
        }
        return Bitmap.createBitmap(bucket(requiredWidth), bucket(requiredHeight), Bitmap.Config.ARGB_8888).also {
            rasterIncrementalPatchBitmap = it
        }
    }

    private fun renderRasterStrokePatch(raster: Bitmap, newStrokes: List<InkStroke>): RasterStrokePatch? {
        if (newStrokes.isEmpty() || raster.isRecycled) return null
        val allPoints = newStrokes.flatMap(InkStroke::points)
        if (allPoints.isEmpty()) return null
        val maximumWidth = newStrokes.maxOf { stroke ->
            val spread = if (stroke.spray) 1f + stroke.spraySpread.coerceAtLeast(0f) else 1f
            val blur = 1f + stroke.blur.coerceAtLeast(0f) / 50f
            stroke.width.coerceAtLeast(1f) / REFERENCE_WIDTH * raster.width * spread * blur
        }
        val padding = (maximumWidth * 1.25f + 6f).coerceAtLeast(6f)
        val patchLeft = kotlin.math.floor(allPoints.minOf(StrokePoint::x) * raster.width - padding).toInt().coerceIn(0, raster.width - 1)
        val patchTop = kotlin.math.floor(allPoints.minOf(StrokePoint::y) * raster.height - padding).toInt().coerceIn(0, raster.height - 1)
        val patchRight = kotlin.math.ceil(allPoints.maxOf(StrokePoint::x) * raster.width + padding).toInt().coerceIn(patchLeft + 1, raster.width)
        val patchBottom = kotlin.math.ceil(allPoints.maxOf(StrokePoint::y) * raster.height + padding).toInt().coerceIn(patchTop + 1, raster.height)
        val patchWidth = patchRight - patchLeft
        val patchHeight = patchBottom - patchTop
        val paintSurface = Bitmap.createBitmap(patchWidth, patchHeight, Bitmap.Config.ARGB_8888)
        val target = Canvas(paintSurface)
        target.save()
        val scaleX = raster.width / width.coerceAtLeast(1).toFloat()
        val scaleY = raster.height / height.coerceAtLeast(1).toFloat()
        target.scale(scaleX, scaleY)
        target.translate(-patchLeft / scaleX, -patchTop / scaleY)
        val previousSynchronousDecode = forceSynchronousTipDecode
        forceSynchronousTipDecode = true
        try {
            newStrokes.forEach { stroke -> drawStroke(target, stroke) }
        } finally {
            forceSynchronousTipDecode = previousSynchronousDecode
        }
        target.restore()
        selectionMaskForRasterTools()?.let { selection ->
            RasterSelectionMask(selection, raster.width, raster.height).apply(
                paintSurface,
                Rect(patchLeft, patchTop, patchRight, patchBottom),
            )
        }
        if (activeLayerId in alphaLockedLayers) target.drawBitmap(
            raster,
            android.graphics.Rect(patchLeft, patchTop, patchRight, patchBottom),
            android.graphics.Rect(0, 0, patchWidth, patchHeight),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN },
        )
        return RasterStrokePatch(paintSurface, patchLeft, patchTop)
    }

    private fun beginWetPaintGesture(normalizedX: Float, normalizedY: Float) {
        cancelWetPaintGesture()
        val template = activeStrokeTemplate ?: return
        val raster = rasterEditBitmapFor(activeLayerId) ?: return
        val surface = rasterTileSurfaceFor(activeLayerId) ?: return
        clearRasterIncrementalPaintState()
        rasterIncrementalPaintEnabled = supportsIncrementalRasterStampPaint(template)
        rasterWetPaintBitmap = raster
        rasterPaintSession = RasterTilePaintSession(
            surface,
            raster,
            selectionMaskForRasterTools(),
            preserveDestinationAlpha = activeLayerId in alphaLockedLayers,
            mirrorWrites = !rasterIncrementalPaintEnabled,
        )
        wetPaintDirtyBounds = null
        if (template.procreateWetMix.isDisabled() && !template.watercolor) return
        val initial = StrokePoint(normalizedX, normalizedY)
        wetPaintLastPositions = wetPaintPositions(initial)
        wetPaintPickupColors = MutableList(wetPaintLastPositions.size) { null }
    }

    private fun wetPaintPositions(point: StrokePoint): List<Pair<Float, Float>> = buildList {
        add(point.x to point.y)
        symmetryMirrors(listOf(point)).forEach { mirrored -> mirrored.firstOrNull()?.let { add(it.x to it.y) } }
    }

    private fun advanceWetPaintGesture() {
        val session = rasterPaintSession ?: return
        val raster = rasterWetPaintBitmap?.takeUnless(Bitmap::isRecycled) ?: return
        val template = activeStrokeTemplate ?: return
        if (template.procreateWetMix.isDisabled() && !template.watercolor) return
        val point = currentPoints.lastOrNull() ?: return
        val currentPositions = wetPaintPositions(point)
        if (currentPositions.size != wetPaintLastPositions.size) {
            wetPaintLastPositions = currentPositions
            wetPaintPickupColors = MutableList(currentPositions.size) { null }
            return
        }
        val dynamics = BrushProgramRuntime.evaluateDab(template, point)
        val radius = (template.width.coerceIn(.5f, 800f) / REFERENCE_WIDTH * raster.width * dynamics.size * .5f).coerceAtLeast(.5f)
        val cspMixing = template.watercolor && template.procreateWetMix.isDisabled()
        val amountOfPaint = if (cspMixing) app.inkloom.document.WetMixDynamics.colorMix(template, point) else 1f
        val cspPull = when (CspColorMixMode.normalize(template.cspColorMixMode)) {
            CspColorMixMode.RUNNING_COLOR -> template.colorStretch * (.55f + .45f * (1f - amountOfPaint))
            CspColorMixMode.SMEAR -> template.colorStretch * (1f - amountOfPaint * .3f)
            else -> template.colorStretch * (1f - amountOfPaint) * .8f
        }.coerceIn(0f, 1f)
        val pull = if (cspMixing) cspPull else app.inkloom.document.WetMixDynamics.pullStrength(template, point)
        val runningColor = cspMixing && CspColorMixMode.normalize(template.cspColorMixMode) == CspColorMixMode.RUNNING_COLOR
        val blurDynamics = if (runningColor) {
            BrushDynamics.pressure(
                template.colorMixBlurDynamics.pressureEnabled,
                template.colorMixBlurDynamics.pressureMinimum,
                template.colorMixBlurDynamics.pressureCurve,
                point.pressure,
            ) * BrushDynamics.secondary(template.colorMixBlurDynamics.secondary, point, template.renderSeed, 223)
        } else 0f
        val fixedBlurRadius = if (runningColor && !template.colorMixBlurAutomatic) BrushBlur.radiusPixels(
            template.colorMixBlur * blurDynamics,
            template.colorMixBlurUnit,
            linkedToSize = false,
            currentDiameter = radius * 2f,
            baseDiameter = template.width / REFERENCE_WIDTH * raster.width,
            pageWidth = raster.width.toFloat(),
        ) else -1f
        val blur = when {
            !runningColor -> if (cspMixing) 0f else app.inkloom.document.WetMixDynamics.blurAmount(template, point)
            template.colorMixBlurAutomatic -> (template.colorMixBlur / 100f * blurDynamics).coerceIn(0f, 1f)
            else -> (fixedBlurRadius / radius.coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
        var dirty: android.graphics.Rect? = null
        currentPositions.indices.forEach { index ->
            val previous = wetPaintLastPositions[index]
            val current = currentPositions[index]
            if (cspMixing) {
                val sampled = session.samplePigment(current.first * raster.width, current.second * raster.height, radius)
                val priorPickup = wetPaintPickupColors.getOrNull(index)
                val pickup = when {
                    sampled ushr 24 == 0 -> priorPickup ?: sampled
                    priorPickup == null || priorPickup ushr 24 == 0 -> sampled
                    else -> CspPigmentMixer.mixArgb(
                        sampled,
                        priorPickup,
                        template.colorStretch.coerceIn(0f, 1f),
                        template.cspPigmentMixingMode,
                        template.cspBrightnessCorrection,
                    )
                }
                if (index < wetPaintPickupColors.size) wetPaintPickupColors[index] = pickup
                if (index == 0 && currentPoints.isNotEmpty()) {
                    val fresh = template.colorArgb.toInt()
                    val pigment = if (pickup ushr 24 == 0) fresh else CspPigmentMixer.mixArgb(
                        pickup,
                        fresh,
                        amountOfPaint,
                        template.cspPigmentMixingMode,
                        template.cspBrightnessCorrection,
                    )
                    val last = currentPoints.lastIndex
                    currentPoints[last] = currentPoints[last].copy(pigmentArgb = pigment.toUInt().toLong())
                }
            }
            val changed = session.advanceWetTransport(
                previous.first * raster.width, previous.second * raster.height,
                current.first * raster.width, current.second * raster.height,
                radius, pull, blur, template.hardness, fixedBlurRadius,
            ) ?: return@forEach
            if (dirty == null) dirty = android.graphics.Rect(changed) else dirty?.union(changed)
        }
        wetPaintLastPositions = currentPositions
        dirty?.let { bounds ->
            wetPaintDirtyBounds = mergeDirtyBounds(wetPaintDirtyBounds, bounds)
            invalidate(
                (bounds.left * width.toFloat() / raster.width).toInt() - 2,
                (bounds.top * height.toFloat() / raster.height).toInt() - 2,
                (bounds.right * width.toFloat() / raster.width).toInt() + 2,
                (bounds.bottom * height.toFloat() / raster.height).toInt() + 2,
            )
        }
    }

    private fun finishWetPaintGesture() {
        rasterPaintSession?.close()
        rasterPaintSession = null
        rasterWetPaintBitmap = null
        wetPaintDirtyBounds = null
        wetPaintLastPositions = emptyList()
        wetPaintPickupColors.clear()
        clearRasterIncrementalPaintState()
    }

    private fun commitIncrementalRasterStampGesture(newStrokes: List<InkStroke>): Boolean {
        if (!rasterIncrementalPaintEnabled) return false
        val surface = rasterTileSurfaceFor(activeLayerId) ?: return false
        val session = rasterPaintSession ?: return false
        val rendered = synchronized(renderLock) {
            renderIncrementalRasterPaintPatch(null, newStrokes, finalCompleteInput = true)
        }
        if (!rendered) return false
        try {
            val dirty = wetPaintDirtyBounds
            if (dirty != null) {
                session.commit().close()
                commitRasterLayerPatch(surface, dirty)
            } else {
                session.restoreOriginal()
            }
        } finally {
            session.close()
            if (rasterPaintSession === session) rasterPaintSession = null
        }
        invalidate()
        return true
    }

    private fun cancelWetPaintGesture() {
        rasterPaintSession?.restoreOriginal()
        finishWetPaintGesture()
        invalidate()
    }

    private fun commitRasterPaintStrokes(raster: Bitmap, newStrokes: List<InkStroke>) {
        rasterFullReplayCount++
        val patch = renderRasterStrokePatch(raster, newStrokes) ?: return
        val surface = rasterTileSurfaceFor(activeLayerId) ?: run { patch.bitmap.recycle(); return }
        val session = rasterPaintSession ?: RasterTilePaintSession(
            surface,
            raster,
            selectionMaskForRasterTools(),
            preserveDestinationAlpha = activeLayerId in alphaLockedLayers,
        )
        try {
            val painted = session.compositePaintPatch(
                patch.bitmap,
                patch.left,
                patch.top,
                newStrokes,
                wetComposite = newStrokes.any { stroke ->
                    stroke.watercolor || !stroke.procreateWetMix.isDisabled() || stroke.colorMix > .001f || stroke.paintMix > .001f
                },
            )
            val dirtyBounds = painted?.let { mergeDirtyBounds(wetPaintDirtyBounds, it) } ?: wetPaintDirtyBounds
            if (dirtyBounds != null) {
                session.commit().close()
                commitRasterLayerPatch(surface, dirtyBounds)
            } else {
                session.restoreOriginal()
            }
        } finally {
            patch.bitmap.recycle()
            session.close()
            if (rasterPaintSession === session) rasterPaintSession = null
        }
        invalidate()
    }

    private fun activeBrushEngineKind(): BrushEngineKind = when {
        activeTool == EditorTool.SMUDGE || brushWatercolor || !procreateWetMix.isDisabled() || brushColorMix > 0f || brushPaintMix > 0f -> BrushEngineKind.WET_SMUDGE
        brushContinuousPlot || brushSpray -> BrushEngineKind.AIRBRUSH
        tipAssetPath != null || tipAssetPaths.isNotEmpty() || brushDual || grainAssetPath != null -> BrushEngineKind.STAMP_RASTER
        else -> BrushEngineKind.MESH_INK
    }

    /** The original stroke plus deterministic mirror ids. Stable ids let the front buffer append
     * each mirrored tail instead of treating every display frame as unrelated new ink. */
    private fun symmetryStrokes(points: List<StrokePoint>): List<InkStroke> {
        val base = makeStroke(points)
        if (symmetryMode == SymmetryMode.NONE) return listOf(base)
        return listOf(base) + symmetryMirrors(points).mapIndexed { index, mirrored ->
            makeStroke(mirrored).copy(id = "$currentStrokeId-sym-$index")
        }
    }

    private fun symmetryMirrors(points: List<StrokePoint>): List<List<StrokePoint>> {
        val mirrorX: (StrokePoint) -> StrokePoint = { it.copy(x = (1f - it.x).coerceIn(0f, 1f)) }
        val mirrorY: (StrokePoint) -> StrokePoint = { it.copy(y = (1f - it.y).coerceIn(0f, 1f)) }
        return when (symmetryMode) {
            SymmetryMode.NONE -> emptyList()
            SymmetryMode.HORIZONTAL -> listOf(points.map(mirrorY))
            SymmetryMode.VERTICAL -> listOf(points.map(mirrorX))
            SymmetryMode.QUADRANT -> listOf(points.map(mirrorX), points.map(mirrorY), points.map(mirrorX).map(mirrorY))
            SymmetryMode.RADIAL_4, SymmetryMode.RADIAL_6, SymmetryMode.RADIAL_8 -> {
                val n = when (symmetryMode) { SymmetryMode.RADIAL_4 -> 4; SymmetryMode.RADIAL_6 -> 6; SymmetryMode.RADIAL_8 -> 8; else -> 4 }
                (1 until n).map { k -> points.map { rotateAroundCenter(it, 360f / n * k) } }
            }
        }
    }

    private fun rotateAroundCenter(point: StrokePoint, rotationDegrees: Float): StrokePoint {
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        val safeWidth = pageModelWidth.coerceAtLeast(.0001f)
        val safeHeight = pageModelHeight.coerceAtLeast(.0001f)
        val dx = (point.x - .5f) * safeWidth
        val dy = (point.y - .5f) * safeHeight
        return point.copy(x = .5f + (dx * cos - dy * sin) / safeWidth, y = .5f + (dx * sin + dy * cos) / safeHeight)
    }

    private fun beginEraserGesture() {
        removeCallbacks(eraserInvalidationFrame)
        eraserInvalidationScheduled = false
        pendingEraserInvalidationBefore = null
        rasterTileEraserSession?.close()
        rasterTileEraserSession = null
        rasterEraserBitmap = rasterEditBitmapFor(activeLayerId)
        val raster = rasterEraserBitmap
        val surface = rasterTileSurfaceFor(activeLayerId)
        if (raster != null && surface != null) {
            rasterTileEraserSession = RasterTileEraserSession(surface, raster, selectionMaskForRasterTools())
            eraserGestureBefore = null
        } else eraserGestureBefore = strokes.toList()
        rasterEraserChanged = false
        rasterEraserDirtyBounds = null
        rasterEraserPoints.clear()
        lastEraserX = null
        lastEraserY = null
        eraserStrokeIndices.clear()
        if (nonDestructiveEraser) strokes.forEachIndexed { index, stroke -> eraserStrokeIndices[stroke.id] = index }
    }

    private fun eraseAlong(x: Float, y: Float, pressure: Float) {
        val raster = rasterEraserBitmap
        val tileSession = rasterTileEraserSession
        if (raster != null && tileSession != null) {
            val targetX = x.coerceIn(0f, 1f); val targetY = y.coerceIn(0f, 1f)
            val point = StrokePoint(targetX, targetY, pressure.coerceIn(0f, 1f), android.os.SystemClock.uptimeMillis())
            if (rasterEraserPoints.lastOrNull()?.let { last -> last.x == point.x && last.y == point.y } != true) rasterEraserPoints += point
            val startX = lastEraserX ?: targetX; val startY = lastEraserY ?: targetY
            val radius = eraserRadiusPixels(pressure) * raster.width / width.coerceAtLeast(1).toFloat()
            val dirty = tileSession.eraseSegment(startX * raster.width, startY * raster.height, targetX * raster.width, targetY * raster.height, radius, eraserOpacity, eraserHardness)
            if (dirty != null) {
                rasterEraserChanged = true
                rasterEraserDirtyBounds = mergeDirtyBounds(rasterEraserDirtyBounds, dirty)
                postInvalidateOnAnimation()
            }
            lastEraserX = targetX; lastEraserY = targetY
            return
        }
        val before = captureEraserInvalidationBefore()
        beginEraserMaskBatch()
        eraseAlongUnchecked(x, y, pressure)
        if (!nonDestructiveEraser) flushEraserMaskBatch()
        invalidateEraserEvent(before)
    }

    /** The latest point plus segment interpolation already covers the full path. Replaying every
     * historical point makes dense Android input packets erase the same area repeatedly. */
    private fun eraseMotionEvent(event: MotionEvent, pointerIndex: Int) {
        if (rasterTileEraserSession != null) {
            eraseAlong(event.getX(pointerIndex) / width.coerceAtLeast(1), event.getY(pointerIndex) / height.coerceAtLeast(1), event.getPressure(pointerIndex))
            return
        }
        val before = captureEraserInvalidationBefore()
        beginEraserMaskBatch()
        eraseAlongUnchecked(
            event.getX(pointerIndex) / width.coerceAtLeast(1),
            event.getY(pointerIndex) / height.coerceAtLeast(1),
            event.getPressure(pointerIndex),
        )
        if (!nonDestructiveEraser) flushEraserMaskBatch()
        invalidateEraserEvent(before)
    }

    private fun beginEraserMaskBatch() {
        if (nonDestructiveEraser && eraserMaskBatch == null) eraserMaskBatch = linkedMapOf()
    }

    private fun flushEraserMaskBatch() {
        val batch = eraserMaskBatch ?: return
        batch.forEach { (strokeId, additions) ->
            val index = eraserStrokeIndices[strokeId] ?: return@forEach
            val stroke = strokes.getOrNull(index)?.takeIf { it.id == strokeId } ?: return@forEach
            if (additions.isNotEmpty()) strokes[index] = stroke.copy(masks = stroke.masks + additions)
        }
        eraserMaskBatch = null
    }

    private fun invalidateEraserEvent(before: List<InkStroke>) {
        if (nonDestructiveEraser) {
            if (!eraserInvalidationScheduled) {
                eraserInvalidationScheduled = true
                postOnAnimation(eraserInvalidationFrame)
            }
            return
        }
        val edit = StrokeEdits.difference(before, strokes) ?: return
        invalidateStrokeCache(edit.before.map { it.stroke }, edit.after.map { it.stroke })
    }

    /** Mask erasers do not change stroke bounds, so all samples received during one display frame
     * can share a single tile diff. This removes a full stroke-list comparison from every input
     * packet while preserving immediate model updates and one-frame visual feedback. */
    private fun captureEraserInvalidationBefore(): List<InkStroke> {
        if (!nonDestructiveEraser) return strokes.toList()
        if (pendingEraserInvalidationBefore == null) pendingEraserInvalidationBefore = strokes.toList()
        return emptyList()
    }

    private fun flushEraserInvalidation() {
        val before = pendingEraserInvalidationBefore ?: return
        pendingEraserInvalidationBefore = null
        flushEraserMaskBatch()
        val edit = StrokeEdits.difference(before, strokes) ?: return
        invalidateStrokeCache(edit.before.map { it.stroke }, edit.after.map { it.stroke })
    }

    private fun eraseAlongUnchecked(x: Float, y: Float, pressure: Float) {
        val targetX = x.coerceIn(0f, 1f)
        val targetY = y.coerceIn(0f, 1f)
        val startX = lastEraserX
        val startY = lastEraserY
        if (startX == null || startY == null) {
            eraseAt(targetX, targetY, pressure)
        } else {
            val distancePixels = hypot(((targetX - startX) * width).toDouble(), ((targetY - startY) * height).toDouble()).toFloat()
            // Overlapping dabs at 75% of the radius remain continuous, while the former 45%
            // spacing generated hundreds of redundant stroke intersections per input packet.
            val spacing = (eraserRadiusPixels(pressure) * .75f).coerceAtLeast(1.5f)
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
        committedScene.ensureIndex(strokes, width, height)
        val candidateIds = committedScene.candidates(
            x * width - eraserRadius,
            y * height - eraserRadius,
            x * width + eraserRadius,
            y * height + eraserRadius,
        )
        val candidateIndices: Sequence<Int> = if (nonDestructiveEraser) {
            candidateIds.asSequence().mapNotNull(eraserStrokeIndices::get).sortedDescending()
        } else {
            strokes.indices.reversed().asSequence().filter { strokes[it].id in candidateIds }
        }
        for (index in candidateIndices) {
            val stroke = strokes.getOrNull(index) ?: continue
            if (stroke.layerId != activeLayerId) continue
            if (stroke.id in protectedStrokeIds) continue
            val strokeRadius = stroke.width.coerceIn(.1f, 800f) * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f
            val combinedRadius = eraserRadius + strokeRadius
            val radiusX = combinedRadius / width.coerceAtLeast(1)
            val radiusY = combinedRadius / height.coerceAtLeast(1)
            if (!StrokeEraser.intersects(stroke, x, y, radiusX, radiusY)) continue
            if (nonDestructiveEraser) {
                val dab = StrokeMaskDab(x, y, eraserRadius / width.coerceAtLeast(1), eraserRadius / height.coerceAtLeast(1), hardness = eraserHardness, opacity = eraserOpacity, tipAssetPath = eraserTipAssetPath)
                val pending = eraserMaskBatch?.getOrPut(stroke.id, ::mutableListOf)
                val previous = pending?.lastOrNull() ?: stroke.masks.lastOrNull()
                if (previous?.let { kotlin.math.abs(it.x - dab.x) < .00001f && kotlin.math.abs(it.y - dab.y) < .00001f } == true) continue
                if (pending != null) pending += dab else strokes[index] = stroke.copy(masks = stroke.masks + dab)
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
        }
    }

    private fun eraserRadiusPixels(pressure: Float): Float {
        val pressureFactor = if (eraserPressureSize) PressureCalibration.apply(pressure, pressureMinimumInput, pressureMaximumInput, pressureSensitivity, pressureResponseCurve).coerceIn(.15f, 1f) else 1f
        return (gestureEraserSize.coerceIn(.01f, 400f) * pressureFactor * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f)
            .coerceAtLeast(1f / viewportScale.coerceAtLeast(1f))
    }

    private fun finishEraserGesture() {
        removeCallbacks(eraserInvalidationFrame)
        eraserInvalidationScheduled = false
        flushEraserInvalidation()
        val raster = rasterEraserBitmap
        val tileSession = rasterTileEraserSession
        if (raster != null && tileSession != null) {
            if (rasterEraserChanged) {
                // Discard the low-cost round preview, then authoritatively replay the imported or
                // configured eraser descriptor into one sparse-tile transaction.
                tileSession.restoreOriginal()
                rasterEraserDirtyBounds = null
                val eraserStroke = withCurrentBrushBehavior(buildStroke(rasterEraserPoints)).copy(
                    engineKind = BrushEngineKind.ERASE_MASK,
                    grainMovement = grainMovement,
                    grainZoom = grainZoom,
                    width = gestureEraserSize,
                    opacity = eraserOpacity,
                    hardness = eraserHardness,
                    tipAssetPath = eraserTipAssetPath ?: tipAssetPath,
                    tipAssetPaths = listOfNotNull(eraserTipAssetPath ?: tipAssetPath).ifEmpty { tipAssetPaths },
                    watercolor = false,
                    colorMix = 0f,
                    paintMix = 0f,
                )
                val surface = rasterLayerTileSurfaces[activeLayerId]
                val strokePatch = renderRasterStrokePatch(raster, listOf(eraserStroke))
                if (surface != null && strokePatch != null) {
                    val bounds = Rect(
                        strokePatch.left,
                        strokePatch.top,
                        strokePatch.left + strokePatch.bitmap.width,
                        strokePatch.top + strokePatch.bitmap.height,
                    )
                    RasterTileEditSession(surface, raster, "authored raster eraser").use { exact ->
                        exact.edit(app.inkloom.engine.v3.surface.PixelRect(bounds.left, bounds.top, bounds.right, bounds.bottom)) { destination ->
                            Canvas(destination).drawBitmap(strokePatch.bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                blendMode = android.graphics.BlendMode.DST_OUT
                            })
                        }
                        exact.commit().close()
                    }
                    rasterEraserDirtyBounds = bounds
                    strokePatch.bitmap.recycle()
                    commitRasterLayerPatch(surface, bounds)
                } else {
                    strokePatch?.bitmap?.recycle()
                }
            } else {
                tileSession.restoreOriginal()
            }
            tileSession.close()
        } else eraserGestureBefore?.let { commitStrokeEdit(it, strokes) }
        rasterTileEraserSession = null; rasterEraserBitmap = null; rasterEraserChanged = false; rasterEraserDirtyBounds = null
        rasterEraserPoints.clear()
        eraserGestureBefore = null
        lastEraserX = null
        lastEraserY = null
        eraserStrokeIndices.clear()
        eraserMaskBatch = null
        if (selectedMaskRegions.isEmpty() && selectedStrokeIds.isNotEmpty() && selectionAlphaMask != null) {
            syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
        }
    }

    private fun cancelEraserGesture() {
        removeCallbacks(eraserInvalidationFrame)
        eraserInvalidationScheduled = false
        pendingEraserInvalidationBefore = null
        rasterTileEraserSession?.let { session ->
            if (rasterEraserChanged) session.restoreOriginal()
            session.close()
            invalidate()
        } ?: eraserGestureBefore?.let(::restoreStrokeSnapshot)
        rasterTileEraserSession = null; rasterEraserBitmap = null; rasterEraserChanged = false; rasterEraserDirtyBounds = null
        rasterEraserPoints.clear()
        eraserGestureBefore = null
        lastEraserX = null
        lastEraserY = null
        eraserStrokeIndices.clear()
        eraserMaskBatch = null
    }

    private fun findStroke(x: Float, y: Float, threshold: Float): Int = strokes.indexOfLast { stroke ->
        stroke.id !in protectedStrokeIds && StrokeEraser.intersects(stroke, x, y, threshold, threshold)
    }

    fun undo() {
        val edit = editHistory.takeUndo() ?: return
        when (edit) {
            is EngineStrokeTransaction -> {
                if (editTouchesProtectedLayer(edit.edit)) {
                    editHistory.restoreUndo(edit)
                    return
                }
                applyStrokeEdit(edit.edit, forward = false)
            }
            is EngineExternalTransaction -> edit.undo()
        }
    }

    fun redo() {
        val edit = editHistory.takeRedo() ?: return
        when (edit) {
            is EngineStrokeTransaction -> {
                if (editTouchesProtectedLayer(edit.edit)) {
                    editHistory.restoreRedo(edit)
                    return
                }
                applyStrokeEdit(edit.edit, forward = true)
            }
            is EngineExternalTransaction -> edit.redo()
        }
    }

    /** Adds a host-owned page mutation to the same chronological stream as ink edits. */
    fun recordExternalEdit(undo: () -> Unit, redo: () -> Unit, dispose: () -> Unit = {}) {
        editHistory.record(EngineExternalTransaction(undo, redo, dispose))
    }

    private fun commitStrokeEdit(before: List<InkStroke>, after: List<InkStroke>) {
        val edit = StrokeEdits.difference(before, after) ?: return
        editHistory.record(EngineStrokeTransaction(edit))
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
        val selectionChanged = selectedStrokeIds.retainAll(snapshot.mapTo(mutableSetOf()) { it.id })
        if (selectionChanged && selectedMaskRegions.isEmpty()) {
            syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
        }
        val edit = StrokeEdits.difference(before, snapshot)
        invalidateStrokeCache(edit?.before.orEmpty().map { it.stroke }, edit?.after.orEmpty().map { it.stroke })
    }

    fun scaleSelection(factor: Float) = transformSelection(factor, 0f)

    fun rotateSelection(degrees: Float) = transformSelection(1f, degrees)

    fun copySelection() {
        val before = strokes.toList()
        val copies = strokes.filter { it.id in selectedStrokeIds }.map { original -> original.copy(
            id = java.util.UUID.randomUUID().toString(),
            points = original.points.map { it.copy(x = (it.x + .03f).coerceIn(0f, 1f), y = (it.y + .03f).coerceIn(0f, 1f)) },
            masks = original.masks.map { it.copy(x = (it.x + .03f).coerceIn(0f, 1f), y = (it.y + .03f).coerceIn(0f, 1f)) },
        ) }
        if (copies.isEmpty()) return
        strokes += copies
        selectedStrokeIds.clear()
        selectedStrokeIds += copies.map(InkStroke::id)
        selectedMaskRegions = emptyList()
        syncSelectionAlphaFromSelectedStrokes(resetFeather = true)
        commitStrokeEdit(before, strokes)
        invalidateStrokeCache(emptyList(), copies)
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
        selectedMaskRegions = emptyList()
        syncSelectionAlphaFromSelectedStrokes(resetFeather = true)
        commitStrokeEdit(before, strokes)
        invalidateStrokeCache(emptyList(), copies)
        return true
    }

    private fun hasSelection(): Boolean = selectedStrokeIds.isNotEmpty() || selectionAlphaMask?.isEmpty == false

    fun hasActiveSelection(): Boolean = hasSelection()

    private fun notifySelectionActiveChanged() {
        val active = hasSelection()
        if (lastReportedSelectionActive == active) return
        lastReportedSelectionActive = active
        onSelectionActiveChanged(active)
    }

    private fun ensureSelectionAlphaFromSelectedStrokes() {
        if (selectionAlphaMask != null || selectedStrokeIds.isEmpty()) return
        syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
    }

    private fun syncSelectionAlphaFromSelectedStrokes(resetFeather: Boolean) {
        val selected = strokes.filter { it.id in selectedStrokeIds }
        replaceSelectionAlpha(SelectionMaskCompositor.fromStrokes(selected, pageModelWidth, pageModelHeight), resetFeather)
    }

    private fun composeSelectionAlpha(region: Bitmap?, mode: SelectionMode) {
        val operation = when (mode) {
            SelectionMode.REPLACE -> AlphaMaskOperation.REPLACE
            SelectionMode.ADD -> AlphaMaskOperation.ADD
            SelectionMode.SUBTRACT -> AlphaMaskOperation.SUBTRACT
            SelectionMode.INTERSECT -> AlphaMaskOperation.INTERSECT
        }
        val combined = selectionAlphaMask?.composed(region, operation) ?: when (operation) {
            AlphaMaskOperation.REPLACE, AlphaMaskOperation.ADD -> SparseAlphaMask.fromBitmap(region)
            AlphaMaskOperation.SUBTRACT, AlphaMaskOperation.INTERSECT -> null
        }
        replaceSelectionAlphaMask(combined, resetFeather = mode == SelectionMode.REPLACE)
    }

    /** Takes ownership of [replacement]. */
    private fun replaceSelectionAlpha(replacement: Bitmap?, resetFeather: Boolean) {
        val sparse = SparseAlphaMask.fromBitmap(replacement)
        replacement?.takeUnless(Bitmap::isRecycled)?.recycle()
        replaceSelectionAlphaMask(sparse, resetFeather)
    }

    /** Takes ownership of [replacement]. */
    private fun replaceSelectionAlphaMask(replacement: SparseAlphaMask?, resetFeather: Boolean) {
        val documentState = documentSelectionState
        if (documentState == null) {
            selectionAlphaMask?.close()
            selectionAlphaMask = replacement
        } else {
            if (replacement?.surface !== documentState.surface()) {
                documentState.replaceFrom(replacement?.surface?.snapshot())
            }
            replacement?.close()
            selectionAlphaMask = SparseAlphaMask.fromSurface(documentState.surface())
        }
        if (resetFeather) selectionFeatherPixels = 0f
        rebuildSelectionDerivedState()
    }

    private fun rebuildSelectionDerivedState() {
        selectionDisplayMask?.takeUnless { it === selectionAlphaMask }?.close()
        selectionBoundaryMask?.close()
        val base = selectionAlphaMask
        if (base == null) {
            selectionDisplayMask = null
            selectionBoundaryMask = null
        } else if (selectionFeatherPixels > 0f) {
            val radius = selectionFeatherPixels * base.width / pageModelWidth.coerceAtLeast(1f)
            selectionDisplayMask = base.feathered(radius)
            selectionBoundaryMask = base.boundary()
        } else {
            selectionDisplayMask = base
            selectionBoundaryMask = base.boundary()
        }
        notifySelectionActiveChanged()
        invalidate()
    }

    /** Authoritative selection sampled by raster tools without an entire-page Bitmap bridge. */
    private fun selectionMaskForRasterTools(): MaskTileSurfaceSnapshot? {
        ensureSelectionAlphaFromSelectedStrokes()
        return selectionDisplayMask?.surface?.snapshot()
    }

    private fun selectionMaskSnapshot(): Bitmap? {
        ensureSelectionAlphaFromSelectedStrokes()
        return selectionDisplayMask?.snapshot()
    }

    fun fillSelection(): Boolean {
        val mask = selectionMaskSnapshot() ?: return false
        val callback = onFillCommitted
        if (callback == null) {
            mask.recycle()
            return false
        }
        callback(mask, 0f, 0f, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), inkOpacity)
        return true
    }

    private fun releaseSelectionState(clearHistory: Boolean) {
        selectedStrokeIds.clear()
        selectedMaskRegions = emptyList()
        documentSelectionState?.replaceFrom(null)
        selectionAlphaMask?.close(); selectionAlphaMask = null
        selectionDisplayMask?.takeUnless { it === selectionAlphaMask }?.close(); selectionDisplayMask = null
        selectionBoundaryMask?.close(); selectionBoundaryMask = null
        selectionFeatherPixels = 0f
        if (clearHistory) {
            lastDeselectedStrokeIds = emptySet()
            lastDeselectedMaskRegions = emptyList()
            lastDeselectedAlphaMask?.close(); lastDeselectedAlphaMask = null
            lastDeselectedFeatherPixels = 0f
        }
        notifySelectionActiveChanged()
        invalidate()
    }

    fun selectAllEditableStrokes(): Boolean {
        val selectable = strokes.asSequence()
            .filterNot { it.id in protectedStrokeIds || it.layerId in protectedLayerIds }
            .map(InkStroke::id)
            .toList()
        selectedStrokeIds.clear()
        selectedStrokeIds += selectable
        selectedMaskRegions = emptyList()
        replaceSelectionAlpha(SelectionMaskCompositor.full(pageModelWidth, pageModelHeight), resetFeather = true)
        invalidate()
        return hasSelection()
    }

    /** Selects every editable stroke that is not currently selected. */
    fun invertSelection(): Boolean {
        val editable = strokes.asSequence()
            .filterNot { it.id in protectedStrokeIds || it.layerId in protectedLayerIds }
            .map(InkStroke::id)
            .toSet()
        val inverted = editable - selectedStrokeIds
        ensureSelectionAlphaFromSelectedStrokes()
        val invertedAlpha = selectionAlphaMask?.inverted() ?: run {
            val full = SelectionMaskCompositor.full(pageModelWidth, pageModelHeight) ?: return false
            try { SparseAlphaMask.fromBitmap(full) } finally { full.recycle() }
        }
        selectedStrokeIds.clear()
        selectedStrokeIds += inverted
        selectedMaskRegions = emptyList()
        replaceSelectionAlphaMask(invertedAlpha, resetFeather = false)
        lassoPoints.clear()
        movingSelection = false
        invalidate()
        return hasSelection()
    }

    /** Existing corner, edge and rotation handles are the free-transform mode. */
    fun enterFreeTransform(): Boolean {
        if (!hasSelection()) return false
        tool = EditorTool.SELECT
        selectionTransformArmed = true
        selectionTransformMode = TRANSFORM_NONE
        invalidate()
        return true
    }

    /** Explicitly returns Selection to boundary-input mode. Selecting a lasso/shape in a drawing
     * app is a mode command, even when the same shape is tapped twice; it must disarm a previous
     * Free Transform session so the next drag draws a new selection instead of moving content. */
    fun beginSelectionInput(shape: SelectionShape) {
        if (activeTool != null) cancelActiveGestureForNavigation()
        tool = EditorTool.SELECT
        selectionShape = shape
        selectionTransformArmed = false
        selectionTransformMode = TRANSFORM_NONE
        movingSelection = false
        selectionMoved = false
        selectionGestureBefore = null
        lassoPoints.clear()
        invalidate()
    }

    /** Cancels only the transient Selection interaction. A committed alpha/stroke selection is
     * preserved so the host can leave the Selection tool without losing the user's work. */
    fun cancelSelectionInput() {
        if (activeTool != null) cancelActiveGestureForNavigation()
        lassoPoints.clear()
        movingSelection = false
        selectionMoved = false
        selectionGestureBefore = null
        selectionTransformArmed = false
        selectionTransformMode = TRANSFORM_NONE
        invalidate()
    }

    fun clearSelection(): Boolean {
        if (!hasSelection()) return false
        lastDeselectedStrokeIds = selectedStrokeIds.toSet()
        lastDeselectedMaskRegions = selectionMaskRegions().map(List<StrokePoint>::toList)
        lastDeselectedAlphaMask?.close()
        lastDeselectedAlphaMask = selectionAlphaMask?.copy()
        lastDeselectedFeatherPixels = selectionFeatherPixels
        selectedStrokeIds.clear()
        selectedMaskRegions = emptyList()
        replaceSelectionAlpha(null, resetFeather = true)
        lassoPoints.clear()
        movingSelection = false
        selectionTransformArmed = false
        invalidate()
        return true
    }

    fun reselect(): Boolean {
        val available = strokes.mapTo(mutableSetOf(), InkStroke::id)
        val restored = lastDeselectedStrokeIds.intersect(available)
        if (restored.isEmpty() && lastDeselectedAlphaMask?.isEmpty != false) return false
        selectedStrokeIds.clear()
        selectedStrokeIds += restored
        selectedMaskRegions = lastDeselectedMaskRegions.map(List<StrokePoint>::toList)
        selectionFeatherPixels = lastDeselectedFeatherPixels
        replaceSelectionAlphaMask(lastDeselectedAlphaMask?.copy(), resetFeather = false)
        invalidate()
        return true
    }

    fun expandSelection(pixels: Float): Boolean = modifySelectionContours(pixels.coerceAtLeast(0f))

    fun shrinkSelection(pixels: Float): Boolean = modifySelectionContours(-pixels.coerceAtLeast(0f))

    fun borderSelection(widthPixels: Float): Boolean {
        ensureSelectionAlphaFromSelectedStrokes()
        val base = selectionAlphaMask?.snapshot()
        val ring = try { SelectionMaskCompositor.border(base, widthPixels, pageModelWidth) } finally { base?.recycle() } ?: return false
        val contours = selectionMaskRegions()
        if (contours.isEmpty()) {
            replaceSelectionAlpha(ring, resetFeather = false)
            return true
        }
        val half = widthPixels.coerceAtLeast(1f) / 2f
        val outer = contours.map { SelectionContourGeometry.offset(it, half, pageModelWidth, pageModelHeight) }
        val inner = contours.map { SelectionContourGeometry.offset(it, -half, pageModelWidth, pageModelHeight) }
        replaceSelectionContours(outer)
        val previousMode = selectionMode
        selectionMode = SelectionMode.SUBTRACT
        inner.forEach(::selectWithLasso)
        selectionMode = previousMode
        selectedMaskRegions = emptyList()
        replaceSelectionAlpha(ring, resetFeather = false)
        invalidate()
        return hasSelection()
    }

    fun featherSelection(pixels: Float): Boolean {
        if (!hasSelection()) return false
        ensureSelectionAlphaFromSelectedStrokes()
        selectionFeatherPixels = pixels.coerceIn(0f, minOf(pageModelWidth, pageModelHeight) / 2f)
        rebuildSelectionDerivedState()
        invalidate()
        return true
    }

    private fun modifySelectionContours(pixels: Float): Boolean {
        ensureSelectionAlphaFromSelectedStrokes()
        val base = selectionAlphaMask ?: return false
        val radius = ceil(kotlin.math.abs(pixels) * base.width / pageModelWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val changed = base.morphed(radius, dilate = pixels > 0f) ?: return false
        replaceSelectionAlphaMask(changed, resetFeather = false)
        val contours = selectionMaskRegions()
        if (contours.isNotEmpty()) selectedMaskRegions = contours.map { SelectionContourGeometry.offset(it, pixels, pageModelWidth, pageModelHeight) }
        invalidate()
        return hasSelection()
    }

    private fun replaceSelectionContours(contours: List<List<StrokePoint>>) {
        val previousMode = selectionMode
        contours.forEachIndexed { index, contour ->
            selectionMode = if (index == 0) SelectionMode.REPLACE else SelectionMode.ADD
            selectWithLasso(contour)
        }
        selectionMode = previousMode
        selectedMaskRegions = contours.map(List<StrokePoint>::toList)
        invalidate()
    }

    /** Returns normalized selection contours for non-destructive layer-mask commands. Exact lasso,
     * rectangle, ellipse and brush-selection contours are retained; selections produced by smart
     * region or whole-document commands use conservative per-stroke bounds. */
    fun selectionMaskRegions(): List<List<StrokePoint>> {
        val regions = selectedMaskRegions.map(List<StrokePoint>::toList).toMutableList()
        if (selectedStrokeIds.isNotEmpty() && selectedMaskRegions.isEmpty()) {
            strokes.asSequence().filter { it.id in selectedStrokeIds }.forEach { stroke ->
                val points = stroke.points
                if (points.isNotEmpty()) {
                    val halfWidthX = stroke.width / pageModelWidth.coerceAtLeast(1f) / 2f
                    val halfWidthY = stroke.width / pageModelHeight.coerceAtLeast(1f) / 2f
                    regions += LassoGeometry.rectangle(
                        StrokePoint((points.minOf(StrokePoint::x) - halfWidthX).coerceIn(0f, 1f), (points.minOf(StrokePoint::y) - halfWidthY).coerceIn(0f, 1f)),
                        StrokePoint((points.maxOf(StrokePoint::x) + halfWidthX).coerceIn(0f, 1f), (points.maxOf(StrokePoint::y) + halfWidthY).coerceIn(0f, 1f)),
                    )
                }
            }
        }
        return regions.filter { it.size >= 3 }
    }

    fun deleteSelection() {
        if (!hasSelection()) return
        val before = strokes.toList()
        val ids = selectedStrokeIds.toSet(); val removed = strokes.filter { it.id in ids }; strokes.removeAll { it.id in ids }; commitStrokeEdit(before, strokes); selectedStrokeIds.clear(); selectedMaskRegions = emptyList(); replaceSelectionAlpha(null, resetFeather = true); invalidateStrokeCache(removed, emptyList())
    }

    private fun transformSelection(scale: Float, rotationDegrees: Float) {
        val before = strokes.toList()
        val selected = strokes.filter { it.id in selectedStrokeIds }
        val points = selected.flatMap(InkStroke::points)
        if (points.isEmpty()) return
        val centerX = points.map(StrokePoint::x).average().toFloat(); val centerY = points.map(StrokePoint::y).average().toFloat()
        val transformed = StrokeTransform.transform(selected, centerX, centerY, scale, rotationDegrees, 0f, 0f, pageModelWidth, pageModelHeight).associateBy(InkStroke::id)
        selected.forEach { current ->
            val changed = transformed.getValue(current.id)
            val currentIndex = strokes.indexOfFirst { it.id == current.id }
            if (currentIndex >= 0) strokes[currentIndex] = changed
        }
        commitStrokeEdit(before, strokes)
        syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
        invalidateStrokeCache(selected, strokes.filter { it.id in selectedStrokeIds })
    }

    private companion object {
        const val REFERENCE_WIDTH = 1600f
        private const val SMALL_CURSOR_MARK_THRESHOLD_PX = 8f
        const val MAX_CACHE_PIXELS = 16_000_000L
        const val FILL_MAX_SAMPLE_DIMENSION = 1_600f
        const val FILL_PREVIEW_THROTTLE_MS = 32L
        const val MAX_ERASER_SAMPLES_PER_EVENT = 128
        const val CONTINUOUS_PLOT_INTERVAL_MS = 32L
        const val QUICKSHAPE_HOLD_MS = 120L
        const val TRANSFORM_NONE = 0
        const val TRANSFORM_SCALE = 1
        const val TRANSFORM_ROTATE = 2
        const val TRANSFORM_SCALE_X = 3
        const val TRANSFORM_SCALE_Y = 4
        const val SELECTION_HANDLE_TOUCH_PX = 48f
        const val SELECTION_SMALL_HANDLE_TOUCH_PX = 10f
        const val SELECTION_ROTATE_HANDLE_OFFSET_PX = 64f
        const val SELECTION_ANTS_FRAME_MS = 80L
        const val DUAL_SCATTER_SEED = 0x27C4915DL
        const val DUAL_ROTATION_SEED = 0x61B8D2A3L
        const val VIEW_ROTATION_DEGREES_PER_PIXEL = .25f
        const val RULER_TOUCH_RADIUS_DP = 38f
        const val GUIDE_TOUCH_RADIUS_DP = 14f
        const val GUIDE_SNAP_TOLERANCE_DP = 12f
        const val GUIDE_CAPTURE_MOVEMENT_DP = 3f
        const val PERSPECTIVE_TOUCH_RADIUS_DP = 28f
        const val SPECIAL_RULER_ROTATION_HANDLE_DP = 72f
        const val DUAL_SEQUENCE_SEED = 0x35A71C9E
        const val DUAL_GRAIN_SEED = 0x4D2A61C7
        const val MAX_COLOR_FILTER_CACHE_ENTRIES = 64
        const val MAX_BLUR_MASK_CACHE_ENTRIES = 48
        const val MAX_CACHED_BLUR_RADIUS_PX = 256f
        const val STYLUS_BUTTON_MASK = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        const val POINTER_BUTTON_MASK = MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD
        val TIP_DECODE_EXECUTOR = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "inkloom-brush-tip").apply { isDaemon = true }
        }
    }

    private fun midpoint(start: StrokePoint, end: StrokePoint) = StrokePoint(
        x = (start.x + end.x) / 2f, y = (start.y + end.y) / 2f,
        pressure = (start.pressure + end.pressure) / 2f,
        timeMs = (start.timeMs + end.timeMs) / 2L,
        tilt = (start.tilt + end.tilt) / 2f,
        orientation = (start.orientation + end.orientation) / 2f,
        velocity = (start.velocity + end.velocity) / 2f,
        pigmentArgb = when {
            start.pigmentArgb == null -> end.pigmentArgb
            end.pigmentArgb == null -> start.pigmentArgb
            else -> mixArgb(start.pigmentArgb, end.pigmentArgb, .5f)
        },
    )

    private fun mixArgb(from: Long, to: Long, amount: Float): Long {
        fun channel(shift: Int): Long {
            val first = from shr shift and 0xFF
            val second = to shr shift and 0xFF
            return (first + (second - first) * amount.coerceIn(0f, 1f)).toLong().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun Float?.orDefault() = this ?: 1f
}
