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
import app.inkloom.BuildConfig
import app.inkloom.engine.v5.diagnostics.PaintingTruthDiagnostics
import app.inkloom.engine.v5.diagnostics.StrokeTrace
import app.inkloom.engine.v5.diagnostics.traceEngineV5
import app.inkloom.engine.v5.history.EngineTransaction
import app.inkloom.engine.v5.history.EngineTransactionJournal
import app.inkloom.engine.v5.input.NativeStrokeSession
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.android.PixelTileCanvasRenderer
import app.inkloom.engine.v5.android.MaskTileCanvasRenderer
import app.inkloom.engine.v5.composite.PixelLayerProjectionCache
import app.inkloom.engine.v5.composite.PixelLayerProjectionInput
import app.inkloom.engine.v5.selection.AlphaMaskOperation
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.MaskTileTransactionRecord
import app.inkloom.engine.v5.surface.MaskTileWriteTransaction
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.RasterSelectionClipboard
import app.inkloom.engine.v5.surface.RasterSelectionContent
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.material.BrushTipMaskCache
import app.inkloom.engine.v5.material.BrushTipMaskRequest
import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.material.GrainMaskRequest
import app.inkloom.engine.v5.material.PressureResponse
import app.inkloom.engine.v5.material.TiltResponse
import app.inkloom.engine.v5.raster.DryBlend
import app.inkloom.engine.v5.raster.DryInkDefinition
import app.inkloom.engine.v5.raster.DryInkPaintOp
import app.inkloom.engine.v5.raster.PaintResult
import app.inkloom.engine.v5.raster.PaintOpKind
import app.inkloom.engine.v5.raster.PaintSampleTransform
import app.inkloom.engine.v5.raster.StampBrushDefinition
import app.inkloom.engine.v5.raster.StampPaintOp
import app.inkloom.engine.v5.raster.WetPaintDefinition
import app.inkloom.engine.v5.raster.WetPaintOp
import app.inkloom.engine.v5.runtime.RawDocumentSample
import app.inkloom.engine.v5.runtime.RealtimeStrokeController
import app.inkloom.engine.v5.storage.RasterLayerCommit
import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.selection.CanonicalLassoContour
import app.inkloom.engine.v5.selection.LassoSession
import app.inkloom.engine.v5.selection.SelectionMaskRasterizer
import app.inkloom.engine.v5.selection.DocumentSelectionState
import app.inkloom.engine.v5.selection.SparseAlphaMask
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.tools.NativeRasterOps
import app.inkloom.engine.v5.tools.RasterLayerPatch
import app.inkloom.engine.v5.tools.RasterSelectionMask
import app.inkloom.engine.v5.tools.RasterSelectionTransformSession
import app.inkloom.engine.v5.tools.RasterTileBlendSession
import app.inkloom.engine.v5.tools.RasterTileEditSession
import app.inkloom.engine.v5.tools.RasterTileLiquifySession
import app.inkloom.engine.v5.tools.SelectionAffineTransform
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.ViewportState
import app.inkloom.document.StrokeMaskDab
import app.inkloom.document.StrokeMaskRenderer
import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushCursorGeometry
import app.inkloom.document.BrushEngineKind
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushSensorFlags
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

class InkCanvasView(context: Context) : View(context) {
    private val renderLock = Any()
    private val strokeInputModeler = NativeStrokeSession()
    private val strokes = mutableListOf<InkStroke>()
    private val editHistory = EngineTransactionJournal()
    private val currentPoints = mutableListOf<StrokePoint>()
    private var currentStrokeId = java.util.UUID.randomUUID().toString()
    private var activeStrokeTemplate: InkStroke? = null

    /** Debug-only stroke handoff overlay and [StrokeTrace] gate. No-op in release builds. */
    var showStrokeTraceOverlay: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            StrokeTrace.enabled = BuildConfig.PAINTING_DIAGNOSTICS && value
            if (!value) {
                StrokeTrace.reset()
                PaintingTruthDiagnostics.reset()
                pendingProjectionStrokeId = null
                pendingStableFrameStrokeId = null
            }
            postInvalidateOnAnimation()
        }
    private var pendingProjectionStrokeId: String? = null
    private var pendingStableFrameStrokeId: String? = null
    private var pendingMaterialBarrierSequence: Long? = null
    private var lastCommittedFrameDrawn = false
    private val strokeTraceOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val strokeTraceOverlayBackground = Paint().apply {
        color = Color.argb(190, 20, 20, 20)
    }
    private var backgroundBitmap: Bitmap? = null
    private val selectedStrokeIds = linkedSetOf<String>()
    private var selectedMaskRegions: List<List<StrokePoint>> = emptyList()
    private var documentSelectionState: DocumentSelectionState? = null
    /** Explicitly isolated adapter for view-only tests which do not install a document runtime. */
    private var isolatedTestSelectionAlphaMask: SparseAlphaMask? = null
    private var selectionBoundaryMask: SparseAlphaMask? = null
    private var lastDeselectedStrokeIds: Set<String> = emptySet()
    private var lastDeselectedMaskRegions: List<List<StrokePoint>> = emptyList()
    private var isolatedTestLastDeselectedAlphaMask: SparseAlphaMask? = null
    private var lastDeselectedFeatherPixels = 0f
    private var lastReportedSelectionActive: Boolean? = null
    var selectionFeatherPixels: Float = 0f
        private set
    private var selectionFeatherBase: MaskTileSurfaceSnapshot? = null
    private var selectionGestureBefore: List<InkStroke>? = null
    private var strokeHistoryKey: String? = null
    private val lassoPoints = mutableListOf<StrokePoint>()
    private var v5LassoSession: LassoSession? = null
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
    private var smudgeLastX = 0f
    private var smudgeLastY = 0f
    private var smudgeChanged = false
    private var smudgeDirtyBounds: Rect? = null
    private var rasterTileBlendSession: RasterTileBlendSession? = null
    private val v5StrokeController = RealtimeStrokeController()
    private val v5BrushTipMasks = BrushTipMaskCache()
    private var v5RejectedContact = false
    @VisibleForTesting internal var v5DryGestureCount: Int = 0
        private set
    @VisibleForTesting internal var v5DryPublishedGeneration: Long = -1L
        private set
    @VisibleForTesting
    internal fun v5LastCommittedNormalizedPoints(): List<Pair<Float, Float>> {
        val surface = rasterTileSurfaceFor(activeLayerId) ?: return emptyList()
        return v5StrokeController.lastPublishedSamples().map { sample ->
            sample.x / surface.logicalWidth to sample.y / surface.logicalHeight
        }
    }
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
    private var rasterTileLiquifySession: RasterTileLiquifySession? = null
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
    private fun liquifyGestureActive(): Boolean = rasterTileLiquifySession != null
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
    private var selectionTransformTranslateX = 0f
    private var selectionTransformTranslateY = 0f
    private var rasterSelectionTransformSession: RasterSelectionTransformSession? = null
    private var rasterSelectionTransformOriginalMask: MaskTileSurfaceSnapshot? = null
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
    private var v5GestureStartNormalized: Pair<Float, Float>? = null
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
            val surface = rasterTileSurfaceFor(activeLayerId)
            if (surface != null && v5StrokeController.isActive) {
                val now = SystemClock.uptimeMillis() * 1_000_000L
                v5StrokeController.append(
                    listOf(
                        RawDocumentSample(
                            continuousPlotX * surface.logicalWidth,
                            continuousPlotY * surface.logicalHeight,
                            now,
                            now,
                            continuousPlotPressure,
                            continuousPlotTilt,
                            continuousPlotOrientation,
                            InputTool.STYLUS,
                        ),
                    ),
                )?.let { invalidateV5Damage(surface, it) }
            }
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
    private var prewarmedBrushSignature: String? = null
    private val protectedStrokeIds = mutableSetOf<String>()
    private val protectedLayerIds = mutableSetOf<String>()

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
    var brushRenderSeed: Int = brushId.hashCode()
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
    var layerMaskSurfaces: Map<String, MaskTileSurfaceSnapshot> = emptyMap()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    private var editingLayerMaskId: String? = null
    private var editingLayerMaskSurface: MaskTileSurface? = null
    private var layerMaskGesture: MaskTileWriteTransaction? = null
    private var layerMaskGestureErasing = false
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
    var graphiteTiltWidth: Float = 0f
    var graphiteTiltWidthStart: Float = 0f
    var graphiteTiltGrain: Float = 0f
    var graphiteTiltGrainStart: Float = 0f
    var proceduralPaperGrainEnabled: Boolean = false
    var proceduralPaperGrainScale: Float = 100f
    var proceduralPaperGrainDensity: Float = .5f
    var proceduralPaperGrainStrength: Float = 1f
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
    var onGradientCommitted: (startX: Float, startY: Float, endX: Float, endY: Float, definition: GradientDefinition, selectionMask: MaskTileSurfaceSnapshot?) -> Unit = { _, _, _, _, _, _ -> }
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
    var onSparseFillCommitted: ((mask: MaskTileSurfaceSnapshot, colorArgb: Long, opacity: Float) -> Unit)? = null
    /** Immutable generation handoff; bitmap materialization belongs on a persistence worker. */
    var onRasterLayerCommitted: ((RasterLayerCommit) -> Unit)? = null
    var onLayerMaskSurfaceCommitted: (layerId: String, mask: MaskTileSurfaceSnapshot) -> Unit = { _, _ -> }

    private fun mergeDirtyBounds(current: Rect?, changed: Rect): Rect =
        current?.apply { union(changed) } ?: Rect(changed)

    private fun commitRasterLayerPatch(
        surface: PixelTileSurface,
        dirtyBounds: Rect?,
        layerId: String = activeLayerId,
    ) {
        val dirty = dirtyBounds ?: return
        val snapshot = surface.snapshot()
        val clipped = PixelRect(dirty.left, dirty.top, dirty.right, dirty.bottom).intersect(
            PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight),
        )
        if (!clipped.isEmpty) onRasterLayerCommitted?.invoke(RasterLayerCommit(layerId, snapshot, clipped))
    }

    private fun publishRasterSelectionEdit(layerId: String, surface: PixelTileSurface, bounds: PixelRect) {
        commitRasterLayerPatch(surface, Rect(bounds.left, bounds.top, bounds.right, bounds.bottom), layerId)
        val displayLeft = (bounds.left * width.toFloat() / surface.logicalWidth).toInt() - 2
        val displayTop = (bounds.top * height.toFloat() / surface.logicalHeight).toInt() - 2
        val displayRight = (bounds.right * width.toFloat() / surface.logicalWidth).toInt() + 2
        val displayBottom = (bounds.bottom * height.toFloat() / surface.logicalHeight).toInt() + 2
        invalidate(displayLeft, displayTop, displayRight, displayBottom)
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
    var onPaintCapabilityRejected: (String) -> Unit = {}
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
            resetSelectionViewBinding(clearReselect = true, unbindDocument = false)
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
        val isolated = isolatedTestSelectionAlphaMask
        documentSelectionState = state
        if (isolated != null && state.isEmpty) state.replaceFrom(isolated.surface.snapshot())
        isolatedTestSelectionAlphaMask = null
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

    private val rasterLayerTileSurfaces = mutableMapOf<String, PixelTileSurface>()
    private val rasterTileRenderer = PixelTileCanvasRenderer()
    private var rasterLayerProjectionCache: PixelLayerProjectionCache? = null
    private var rasterLayerProjectionGeometry: RasterProjectionGeometry? = null
    @VisibleForTesting internal var v5RasterProjectionDrawCount: Int = 0
    @VisibleForTesting internal var lastRasterProjectionRecomposedTileCount: Int = 0

    fun setRasterLayerSurfaces(value: Map<String, PixelTileSurface>) {
        val changedLayerIds = (rasterLayerTileSurfaces.keys + value.keys).filterTo(mutableSetOf()) { layerId ->
            rasterLayerTileSurfaces[layerId] !== value[layerId]
        }
        if (changedLayerIds.isEmpty()) return
        changedLayerIds.forEach { layerId ->
            rasterLayerTileSurfaces[layerId]?.let { previous ->
                if (previous !== value[layerId]) rasterTileRenderer.releaseSurface(previous.surfaceId)
            }
        }
        rasterLayerTileSurfaces.clear()
        rasterLayerTileSurfaces.putAll(value)
        if (value.isEmpty()) releaseRasterLayerProjection()
        resetStrokeTiles()
    }

    private fun rasterTileSurfaceFor(layerId: String): PixelTileSurface? = rasterLayerTileSurfaces[layerId]

    /** One gesture-scoped sparse preview; it is presentation only and never document truth. */
    private fun liveRasterPreviewSnapshot(layerId: String): app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot? {
        if (layerId != activeLayerId) return null
        return runCatching {
            v5StrokeController.previewSnapshot(layerId)
                ?: rasterTileBlendSession?.previewSnapshot()
                ?: rasterTileLiquifySession?.previewSnapshot()
        }.getOrNull()
    }

    /** Toggles direct grayscale mask editing. The layer pixels remain untouched. */
    fun toggleLayerMaskEditing(layerId: String): Boolean {
        require(layerId.isNotBlank())
        cancelLayerMaskGesture()
        if (editingLayerMaskId == layerId) {
            editingLayerMaskId = null
            editingLayerMaskSurface = null
            return false
        }
        val source = layerMaskSurfaces[layerId] ?: return false
        val editable = MaskTileSurface(
            source.logicalWidth,
            source.logicalHeight,
            source.tileSize,
            surfaceId = "layer-mask-edit:$layerId",
            initialDefaultAlpha = source.defaultAlpha,
        )
        if (source.tileCount > 0) editable.beginWrite("open layer mask editor").run {
            composeSurface(source, MaskComposeMode.REPLACE)
            commit().close()
        }
        editingLayerMaskId = layerId
        editingLayerMaskSurface = editable
        activeLayerId = layerId
        invalidate()
        return true
    }

    fun isEditingLayerMask(layerId: String): Boolean = editingLayerMaskId == layerId

    fun fillLayerMask(layerId: String, alpha: Int): Boolean {
        require(alpha in 0..255)
        if (editingLayerMaskId != layerId && !toggleLayerMaskEditing(layerId)) return false
        val surface = editingLayerMaskSurface ?: return false
        val record = surface.beginWrite(if (alpha == 0) "hide all layer mask" else "reveal all layer mask").run {
            fill(alpha)
            commit()
        }
        return recordLayerMaskEdit(layerId, surface, record)
    }

    private fun beginLayerMaskGesture(tool: EditorTool): Boolean {
        val surface = editingLayerMaskSurface ?: return false
        if (tool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)) return false
        cancelLayerMaskGesture()
        layerMaskGesture = surface.beginWrite(if (tool == EditorTool.ERASER) "erase layer mask gesture" else "paint layer mask gesture")
        layerMaskGestureErasing = tool == EditorTool.ERASER
        return true
    }

    private fun extendLayerMaskGesture(normalizedX: Float, normalizedY: Float, pressure: Float) {
        val transaction = layerMaskGesture ?: return
        val surface = editingLayerMaskSurface ?: return
        val modelSize = if (layerMaskGestureErasing) gestureEraserSize else gestureBrushSize
        val scale = ((surface.logicalWidth / pageModelWidth.coerceAtLeast(1f)) +
            (surface.logicalHeight / pageModelHeight.coerceAtLeast(1f))) * .5f
        val radius = (modelSize * scale * .5f).coerceAtLeast(1f)
        val centerX = normalizedX * surface.logicalWidth
        val centerY = normalizedY * surface.logicalHeight
        val bounds = PixelRect(
            kotlin.math.floor(centerX - radius - 1f).toInt().coerceAtLeast(0),
            kotlin.math.floor(centerY - radius - 1f).toInt().coerceAtLeast(0),
            kotlin.math.ceil(centerX + radius + 1f).toInt().coerceAtMost(surface.logicalWidth),
            kotlin.math.ceil(centerY + radius + 1f).toInt().coerceAtMost(surface.logicalHeight),
        )
        if (bounds.isEmpty) return
        val alpha = ByteArray(bounds.width * bounds.height)
        val strength = (pressure.coerceIn(.05f, 1f) * 255f).toInt()
        for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
            val distance = hypot((x + .5f - centerX).toDouble(), (y + .5f - centerY).toDouble()).toFloat()
            if (distance <= radius) alpha[(y - bounds.top) * bounds.width + x - bounds.left] = strength.toByte()
        }
        transaction.composeAlpha(
            bounds,
            alpha,
            mode = if (layerMaskGestureErasing) MaskComposeMode.SUBTRACT else MaskComposeMode.ADD,
        )
        editingLayerMaskId?.let { layerId ->
            layerMaskSurfaces = layerMaskSurfaces + (layerId to transaction.previewSnapshot())
        }
        invalidate()
    }

    private fun finishLayerMaskGesture() {
        val transaction = layerMaskGesture ?: return
        val surface = editingLayerMaskSurface ?: return cancelLayerMaskGesture()
        val layerId = editingLayerMaskId ?: return cancelLayerMaskGesture()
        layerMaskGesture = null
        val record = transaction.commit()
        if (record.changedTileCount == 0 && record.beforeDefaultAlpha == record.afterDefaultAlpha) {
            record.close()
            layerMaskSurfaces = layerMaskSurfaces + (layerId to surface.snapshot())
            return
        }
        recordLayerMaskEdit(layerId, surface, record)
    }

    private fun recordLayerMaskEdit(
        layerId: String,
        surface: MaskTileSurface,
        record: MaskTileTransactionRecord,
    ): Boolean {
        if (record.changedTileCount == 0 && record.beforeDefaultAlpha == record.afterDefaultAlpha) {
            record.close()
            return false
        }
        fun publish() {
            val snapshot = surface.snapshot()
            layerMaskSurfaces = layerMaskSurfaces + (layerId to snapshot)
            onLayerMaskSurfaceCommitted(layerId, snapshot)
            invalidate()
        }
        publish()
        recordExternalEdit(
            undo = { surface.apply(record, MaskTileSurface.HistoryDirection.UNDO); publish() },
            redo = { surface.apply(record, MaskTileSurface.HistoryDirection.REDO); publish() },
            dispose = record::close,
        )
        return true
    }

    private fun cancelLayerMaskGesture() {
        val transaction = layerMaskGesture ?: return
        layerMaskGesture = null
        transaction.cancel()
        val surface = editingLayerMaskSurface ?: return
        editingLayerMaskId?.let { layerId -> layerMaskSurfaces = layerMaskSurfaces + (layerId to surface.snapshot()) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(renderLock) { drawPageContent(canvas) }
        rasterTileSurfaceFor(activeLayerId)?.let { surface ->
            v5StrokeController.onDocumentGenerationPresented(activeLayerId, surface.revision)
        }
        pendingStableFrameStrokeId?.let { strokeId ->
            pendingStableFrameStrokeId = null
            lastCommittedFrameDrawn = true
            val stable = rasterTileSurfaceFor(activeLayerId)?.snapshot()
            StrokeTrace.markOnce(strokeId, StrokeTrace.STAGE_RENDERER_DRAW, tileCount = stable?.tileCount, generation = stable?.revision)
            StrokeTrace.markOnce(strokeId, StrokeTrace.STAGE_STABLE_FRAME, tileCount = stable?.tileCount, generation = stable?.revision)
        }
        pendingMaterialBarrierSequence = null
        if (showStrokeTraceOverlay && BuildConfig.PAINTING_DIAGNOSTICS) drawStrokeTraceOverlay(canvas)
    }

    private fun drawStrokeTraceOverlay(canvas: Canvas) {
        val active = v5StrokeController.isActive
        val stablePresented = lastCommittedFrameDrawn
        val stable = rasterTileSurfaceFor(activeLayerId)?.snapshot()
        val tx = stable?.tileCount ?: 0
        val gen = stable?.revision ?: 0L
        val sid = currentStrokeId.take(8)
        val truth = PaintingTruthDiagnostics.snapshot()
        val line = "ACT=${if (active) "1" else "0"}  STABLE=${if (stablePresented) "1" else "0"}  TX=$tx  GEN=$gen  DIFF=${truth.totalMismatchCount}  GAP=${truth.presentationGapCount}  FULL=${truth.fullCanvasInvalidationCount}  SID=$sid"
        val baseline = 38f
        val widthPx = strokeTraceOverlayPaint.measureText(line) + 24f
        strokeTraceOverlayBackground.setShadowLayer(6f, 0f, 0f, Color.BLACK)
        canvas.drawRoundRect(8f, 8f, 8f + widthPx, baseline + 10f, 10f, 10f, strokeTraceOverlayBackground)
        strokeTraceOverlayBackground.clearShadowLayer()
        canvas.drawText(line, 20f, baseline, strokeTraceOverlayPaint)
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
        drawV5LayerComposition(canvas, includeElements = true, layerFilter = referencedLayerIds)
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
            currentSelectionAlphaMask()?.let { selected ->
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
            invalidateLayerComposition()
        }
    }

    override fun onDetachedFromWindow() {
        if (activeTool != null) cancelActiveGestureForNavigation()
        releaseHeldStylusButtons()
        lastStylusButtonState = 0
        stopContinuousPlot()
        laserTrail.clear()
        releaseActiveStrokePreview()
        resetSelectionViewBinding(clearReselect = true, unbindDocument = true)
        editHistory.clear()
        strokeInputModeler.close()
        v5BrushTipMasks.clear()
        releaseRasterLayerProjection()
        rasterTileRenderer.close()
        super.onDetachedFromWindow()
    }

    private fun resetStrokeTiles() = invalidateLayerComposition()

    private fun invalidateLayerComposition() {
        releaseRasterLayerProjection()
        invalidate()
    }

    private fun invalidateStrokeCache() = invalidateLayerComposition()

    private fun invalidateStrokeCache(before: Collection<InkStroke>, after: Collection<InkStroke>) {
        if (StrokeTrace.enabled) StrokeTrace.primaryStrokeId(after.toList())?.let { id ->
            pendingProjectionStrokeId = id
            StrokeTrace.mark(id, StrokeTrace.STAGE_PROJECTION_INVALIDATED)
        }
        invalidateLayerComposition()
    }

    /** The only document presentation path: immutable V5 raster tiles plus page elements. */
    private fun drawV5LayerComposition(
        canvas: Canvas,
        includeElements: Boolean = false,
        layerFilter: Set<String>? = null,
    ) {
        if (drawV5RasterLayerProjection(canvas, includeElements, layerFilter)) return
        val elementLayerIds = if (includeElements) layerElementBitmaps.keys.filter { layerFilter == null || it in layerFilter } else emptyList()
        val rasterLayerIds = rasterLayerTileSurfaces.keys.filter { layerFilter == null || it in layerFilter }
        val availableLayerIds = elementLayerIds + rasterLayerIds
        val orderedLayerIds = (layerOrder.filter { it in availableLayerIds } + availableLayerIds.filterNot { it in layerOrder }).distinct()
        val compositionBounds = RectF(canvas.clipBounds)
        if (compositionBounds.isEmpty) return
        var previousLayerId: String? = null
        orderedLayerIds.forEach { layerId ->
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
                    drawV5RawLayerContent(canvas, layerId, includeElements)
                    previousLayerId = layerId
                    return@forEach
                }
                val checkpoint = canvas.saveLayer(compositionBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (opacity * 255f).toInt().coerceIn(0, 255)
                    this.blendMode = layerBlendMode.toBlendMode()
                })
                drawV5RawLayerContent(canvas, layerId, includeElements)
                layerMasks[layerId]?.let { mask ->
                    val maskCheckpoint = canvas.saveLayer(compositionBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    val sparse = layerMaskSurfaces[layerId]
                    if (mask.enabled && sparse != null) {
                        MaskTileCanvasRenderer.draw(sparse, canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()))
                    } else LayerMaskRenderer.draw(canvas, mask, width, height)
                    canvas.restoreToCount(maskCheckpoint)
                }
                if (clipped && lowerLayerId != null) {
                    val maskCheckpoint = canvas.saveLayer(compositionBounds, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    drawV5RawLayerContent(canvas, lowerLayerId, includeElements)
                    canvas.restoreToCount(maskCheckpoint)
                }
                canvas.restoreToCount(checkpoint)
            }
            previousLayerId = layerId
        }
    }

    /** Production V5 path for paint documents whose visible content is entirely sparse raster
     * layers. Layer opacity, blend modes, and clipping are resolved once per dirty tile; HWUI then
     * draws the immutable projection without allocating a full-canvas saveLayer. */
    private fun drawV5RasterLayerProjection(
        canvas: Canvas,
        includeElements: Boolean,
        layerFilter: Set<String>?,
    ): Boolean {
        if (!includeElements || layerFilter != null) return false
        if (layerElementBitmaps.isNotEmpty()) return false
        if (rasterLayerTileSurfaces.isEmpty()) return false
        if (layerMasks.any { (layerId, mask) ->
                layerId in rasterLayerTileSurfaces && mask.enabled && layerMaskSurfaces[layerId] == null
            }) return false

        val orderedIds = (
            layerOrder.filter(rasterLayerTileSurfaces::containsKey) +
                rasterLayerTileSurfaces.keys.filterNot(layerOrder::contains)
            ).distinct()
        if (orderedIds.isEmpty()) return false
        val directSourceOverStack = orderedIds.all { layerId ->
            (layerOpacities[layerId] ?: 1f) >= .999f &&
                (layerBlendModes[layerId] ?: LayerBlendMode.NORMAL) == LayerBlendMode.NORMAL &&
                layerMasks[layerId]?.enabled != true && layerId !in clippedLayers
        }
        if (directSourceOverStack) {
            // Skia/HWUI already composites normal premultiplied layers. Sending this common case
            // through PixelLayerProjectionCache needlessly copies every changed tile and replaces
            // its RenderNode generation while a pen is moving, which is both slower and the source
            // of the visible "old ink refreshed" sensation. Immutable layer tiles can be retained
            // and drawn directly; non-Normal/masked/clipped stacks still use the projection below.
            releaseRasterLayerProjection()
            val destination = RectF(0f, 0f, width.toFloat(), height.toFloat())
            orderedIds.forEach { layerId ->
                rasterTileRenderer.draw(
                    canvas,
                    liveRasterPreviewSnapshot(layerId) ?: rasterLayerTileSurfaces.getValue(layerId).snapshot(),
                    destination,
                )
            }
            lastRasterProjectionRecomposedTileCount = 0
            v5RasterProjectionDrawCount++
            return true
        }
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
                pixels = liveRasterPreviewSnapshot(layerId) ?: rasterLayerTileSurfaces.getValue(layerId).snapshot(),
                opacity = layerOpacities[layerId] ?: 1f,
                blendMode = (layerBlendModes[layerId] ?: LayerBlendMode.NORMAL).toPixelBlendMode(),
                mask = layerMasks[layerId]?.takeIf(LayerMask::enabled)?.let { layerMaskSurfaces[layerId] },
                clipToBaseBelow = layerId in clippedLayers,
            )
        })
        lastRasterProjectionRecomposedTileCount = update.recomposedTileCount
        v5RasterProjectionDrawCount++
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

    private fun drawV5RawLayerContent(
        canvas: Canvas,
        layerId: String,
        includeElements: Boolean,
    ) {
        val elements = if (includeElements) layerElementBitmaps[layerId] else null
        val rasterSurface = rasterLayerTileSurfaces[layerId]
        val baseIds = alphaLockBaseContentIds[layerId].orEmpty()
        val hasCapturedAlpha = layerId in alphaLockedLayers && baseIds.isNotEmpty()
        if (!hasCapturedAlpha) {
            drawRasterLayer(canvas, layerId, rasterSurface)
            drawElementBitmap(canvas, elements?.base)
            drawElementBitmap(canvas, elements?.additions)
            return
        }
        fun drawBase() {
            drawRasterLayer(canvas, layerId, rasterSurface)
            drawElementBitmap(canvas, elements?.base)
        }
        drawBase()
        if (elements?.additions != null) {
            val compositionBounds = RectF(canvas.clipBounds)
            val additionsCheckpoint = canvas.saveLayer(compositionBounds, null)
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

    private fun drawRasterLayer(canvas: Canvas, layerId: String, surface: PixelTileSurface?) {
        surface ?: return
        rasterTileRenderer.draw(
            canvas,
            liveRasterPreviewSnapshot(layerId) ?: surface.snapshot(),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
        )
    }

    private data class RasterProjectionGeometry(
        val width: Int,
        val height: Int,
        val tileSize: Int,
        val pixelFormat: PixelTileFormat,
    )

    private fun beginActiveStrokePreview() = Unit

    private fun finishActiveStrokePreview() {
        activeStrokeTemplate = null
    }

    private fun releaseActiveStrokePreview() {
        v5StrokeController.cancel()
        v5RejectedContact = false
        activeStrokeTemplate = null
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


    private fun v5TipRequest(path: String, secondary: Boolean = false, eraser: Boolean = false): BrushTipMaskRequest {
        if (eraser) return BrushTipMaskRequest(
            path = path,
            hardness = eraserHardness.coerceIn(0f, 1f),
            antiAliasMode = brushAntiAliasMode,
        )
        return if (secondary) {
            BrushTipMaskRequest(
                path = path,
                hardness = dualHardness.coerceIn(0f, 1f),
                antiAliasMode = dualAntiAliasMode,
                alphaThresholdEnabled = alphaThresholdEnabled,
                alphaThreshold = alphaThreshold,
            )
        } else {
            BrushTipMaskRequest(
                path = path,
                hardness = brushHardness.coerceIn(0f, 1f),
                antiAliasMode = brushAntiAliasMode,
                alphaThresholdEnabled = alphaThresholdEnabled,
                alphaThreshold = alphaThreshold,
            )
        }
    }

    /** Resolve imported material before DOWN. Live input may only read immutable V5 masks. */
    fun prepareBrushAssetsNow() {
        tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }
            .forEach { path -> v5BrushTipMasks.resolve(v5TipRequest(path)) }
        grainAssetPath?.let { path ->
            v5BrushTipMasks.resolveGrain(
                GrainMaskRequest(path, grainBrightness, grainContrast, grainReversed, grainEmphasized),
            )
        }
        if (brushDual) {
            dualTipAssetPaths.ifEmpty { tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) } }
                .forEach { path -> v5BrushTipMasks.resolve(v5TipRequest(path, secondary = true)) }
            dualGrainAssetPath?.let { path ->
                v5BrushTipMasks.resolveGrain(
                    GrainMaskRequest(path, dualGrainBrightness, dualGrainContrast, dualGrainReversed, dualGrainEmphasized),
                )
            }
        }
        eraserTipAssetPath?.let { path -> v5BrushTipMasks.resolve(v5TipRequest(path, eraser = true)) }
    }

    fun prewarmBrushAssets() = prepareBrushAssetsNow()

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
                if (beginLayerMaskGesture(activeTool ?: tool)) {
                    extendLayerMaskGesture(normalizedX, normalizedY, event.getPressure(pointerIndex))
                    return true
                }
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
                    currentStrokeId = java.util.UUID.randomUUID().toString()
                    if (beginV5PaintGesture(event, pointerIndex, normalizedX, normalizedY)) return true
                    onPaintCapabilityRejected("This eraser configuration is not available in Paint Engine V5")
                    activeTool = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                    return true
                }
                if (activeTool == EditorTool.SELECT) {
                    if (selectionShape == SelectionShape.SMART_BORDER || selectionShape == SelectionShape.MAGIC_WAND) {
                        clearLassoInput(); selectionGestureBefore = null; selectionMoved = false; movingSelection = false
                        appendSelectionPoint(normalizedX, normalizedY)
                        invalidate(); return true
                    }
                    if (!selectionTransformArmed) {
                        clearLassoInput(); selectionGestureBefore = null; selectionMoved = false; movingSelection = false
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
                            selectionTransformTranslateX = 0f
                            selectionTransformTranslateY = 0f
                            beginRasterSelectionTransform()
                            lastX = normalizedX; lastY = normalizedY
                            invalidate(); return true
                        }
                    }
                    val hit = strokes.getOrNull(findStroke(normalizedX, normalizedY, .035f))
                    clearLassoInput(); selectionGestureBefore = null; selectionMoved = false
                    val hitRasterSelection = selectionMaskForRasterTools()?.let { mask ->
                        mask.alphaAt(
                            (normalizedX * mask.logicalWidth).toInt().coerceIn(0, mask.logicalWidth - 1),
                            (normalizedY * mask.logicalHeight).toInt().coerceIn(0, mask.logicalHeight - 1),
                        ) > 0
                    } == true
                    if (hit != null || hitRasterSelection) {
                        if (hit != null && hit.id !in selectedStrokeIds) {
                                selectedStrokeIds.clear(); selectedStrokeIds += hit.id
                                selectedMaskRegions = emptyList()
                                syncSelectionAlphaFromSelectedStrokes(resetFeather = true)
                        }
                        selectionGestureBefore = strokes.toList()
                        movingSelection = true
                        selectionTransformTranslateX = 0f
                        selectionTransformTranslateY = 0f
                        beginRasterSelectionTransform()
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
                    if (!beginSmudgeGesture(normalizedX, normalizedY)) {
                        onPaintCapabilityRejected("Blend requires a writable Paint Engine V5 raster layer")
                        activeTool = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.LIQUIFY) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (!beginLiquifyGesture(normalizedX, normalizedY, event.getPressure(pointerIndex))) {
                        onPaintCapabilityRejected("Liquify requires a writable Paint Engine V5 raster layer")
                        activeTool = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.CORRECT_LINE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    beginCorrectLineGesture(normalizedX, normalizedY)
                    return true
                }
                if (beginV5PaintGesture(event, pointerIndex, normalizedX, normalizedY)) return true
                onPaintCapabilityRejected("This canvas tool is not available in Paint Engine V5")
                activeTool = null
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (appendV5PaintGesture(event, pointerIndex)) return true
                if (layerMaskGesture != null) {
                    for (i in 0 until event.historySize) {
                        val point = historicalCanvasPoint(event, pointerIndex, i)
                        extendLayerMaskGesture(
                            point.first / width.coerceAtLeast(1),
                            point.second / height.coerceAtLeast(1),
                            event.getHistoricalPressure(pointerIndex, i),
                        )
                    }
                    extendLayerMaskGesture(normalizedX, normalizedY, event.getPressure(pointerIndex))
                    return true
                }
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
                        selectionTransformTranslateX += dx * pageModelWidth
                        selectionTransformTranslateY += dy * pageModelHeight
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
                        updateRasterSelectionTransform(
                            scaleX = 1f,
                            scaleY = 1f,
                            rotation = 0f,
                            translateX = selectionTransformTranslateX,
                            translateY = selectionTransformTranslateY,
                        )
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
                    if (activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) StrokeTrace.mark(currentStrokeId, StrokeTrace.STAGE_LAST_MOVE)
                    updateContinuousPlotInput(normalizedX, normalizedY, event.getPressure(pointerIndex), event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex), event.getOrientation(pointerIndex))
                    addMotionEventPoints(event, pointerIndex)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (layerMaskGesture != null) {
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0) {
                        cancelLayerMaskGesture()
                    } else {
                        extendLayerMaskGesture(normalizedX, normalizedY, event.getPressure(pointerIndex))
                        finishLayerMaskGesture()
                    }
                    activeTool = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                stopContinuousPlot()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    v5StrokeController.cancel()
                    v5RejectedContact = false
                    StrokeTrace.abandon(currentStrokeId)
                    clearActiveStrokeInput()
                    finishActiveStrokePreview()
                    pendingMaterialBarrierSequence = null
                    if (activeTool == EditorTool.LASER) laserTrail.cancelActive()
                    if (activeTool == EditorTool.FILL) cancelFillGesture()
                    if (activeTool == EditorTool.GRADIENT) { gradientStart = null; gradientEnd = null }
                    if (activeTool == EditorTool.SMUDGE) cancelSmudgeGesture()
                    if (activeTool == EditorTool.LIQUIFY) cancelLiquifyGesture()
                    if (activeTool == EditorTool.CORRECT_LINE) cancelCorrectLineGesture()
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear()
                    selectionGestureBefore?.let { restoreStrokeSnapshot(it) }
                    cancelRasterSelectionTransform()
                    clearLassoInput(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null; selectionTransformMode = TRANSFORM_NONE
                } else if (finishV5PaintGesture(event, pointerIndex, normalizedX, normalizedY)) {
                    activeStrokeTemplate = null
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
                        onGradientCommitted(start.x, start.y, end.x, end.y, gradientDefinition.normalized(), selectionMaskForRasterTools())
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
                } else if (activeTool == EditorTool.SELECT) {
                    if ((movingSelection || selectionTransformMode != TRANSFORM_NONE) && selectionMoved) {
                        selectionGestureBefore?.let { commitStrokeEdit(it, strokes) }
                        if (!finishRasterSelectionTransform()) syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
                    } else if (!movingSelection && selectionTransformMode == TRANSFORM_NONE) {
                        if (selectionShape == SelectionShape.SMART_BORDER || selectionShape == SelectionShape.MAGIC_WAND) {
                            selectAutomaticRegion(normalizedX, normalizedY, selectionShape == SelectionShape.SMART_BORDER)
                        } else if (selectionShape == SelectionShape.FREEFORM) {
                            v5LassoSession?.finish(
                                DocumentPoint(
                                    normalizedX * pageModelWidth.coerceAtLeast(1f),
                                    normalizedY * pageModelHeight.coerceAtLeast(1f),
                                ),
                            )?.let(::selectWithCanonicalLasso)
                        } else {
                            appendSelectionPoint(normalizedX, normalizedY)
                            selectWithLasso(selectionPolygon())
                        }
                    }
                    clearLassoInput(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null; selectionTransformMode = TRANSFORM_NONE
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
        postInvalidateOnAnimation()
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
        if (fillGestureUsesSelection) {
            val mask = currentSelectionAlphaMask() ?: return
            fillPreviewPaint.colorFilter = PorterDuffColorFilter(inkColor, PorterDuff.Mode.SRC_IN)
            fillPreviewPaint.alpha = (inkOpacity * 255f).toInt().coerceIn(0, 255)
            mask.draw(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), fillPreviewPaint)
            return
        }
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
        if (hoverCursorUsesEraser) {
            val pressure = if (eraserPressureSize) calibratedPressure.coerceIn(.15f, 1f) else 1f
            val diameter = documentEraserSize().coerceIn(.01f, 800f) * scale * pressure
            val tip = activeCursorTip(eraserTipAssetPath, eraser = true)
            val footprint = BrushCursorGeometry.footprint(
                diameter = diameter,
                tipWidth = tip?.width,
                tipHeight = tip?.height,
                thickness = if (tip == null) 1f else brushThickness,
                thicknessVertical = brushThicknessVertical,
            )
            return footprint.width to footprint.height
        }
        val pressure = BrushDynamics.pressure(sizePressureEnabled, sizePressureMinimum, sizePressureCurve, calibratedPressure)
        val tilt = TiltResponse(
            sizeSecondaryDynamics.tiltEnabled,
            sizeSecondaryDynamics.tiltMinimum,
            sizeSecondaryDynamics.tiltStart,
            sizeSecondaryDynamics.tiltCurve.map { it.x to it.y },
        ).evaluate(hoverCursorTilt)
        val diameter = documentBrushSize().coerceIn(.01f, 800f) * scale * pressure * tilt
        val thicknessPressure = BrushDynamics.pressure(thicknessPressureEnabled, thicknessPressureMinimum, thicknessPressureCurve, calibratedPressure)
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

    private fun activeCursorTip(path: String?, eraser: Boolean = false): BrushTipMask? {
        path ?: return null
        return v5BrushTipMasks.resolve(v5TipRequest(path, eraser = eraser))
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
        hoverCursorTilt = tilt.takeIf(Float::isFinite)?.coerceIn(0f, (Math.PI / 2.0).toFloat()) ?: 0f
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
        v5StrokeController.cancel()
        v5RejectedContact = false
        clearActiveStrokeInput()
        finishActiveStrokePreview()
        pendingMaterialBarrierSequence = null
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
        v5GestureStartNormalized = null
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
        selectionGestureBefore?.let(::restoreStrokeSnapshot)
        cancelRasterSelectionTransform()
        clearLassoInput()
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
        val selectionMask = currentSelectionAlphaMask()
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
        if (selectionShape == SelectionShape.FREEFORM) {
            val session = v5LassoSession?.takeIf(LassoSession::isActive) ?: LassoSession(
                documentWidth = pageModelWidth.coerceAtLeast(1f),
                documentHeight = pageModelHeight.coerceAtLeast(1f),
            ).also { v5LassoSession = it }
            if (session.append(DocumentPoint(
                    x.coerceIn(0f, 1f) * pageModelWidth.coerceAtLeast(1f),
                    y.coerceIn(0f, 1f) * pageModelHeight.coerceAtLeast(1f),
                ))
            ) syncLassoPreview(session.preview())
            return
        }
        val point = StrokePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val previous = lassoPoints.lastOrNull()
        if (previous == null || hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) > .0015) lassoPoints += point
    }

    private fun syncLassoPreview(contour: CanonicalLassoContour) {
        lassoPoints.clear()
        val width = pageModelWidth.coerceAtLeast(1f)
        val height = pageModelHeight.coerceAtLeast(1f)
        contour.points.mapTo(lassoPoints) { point ->
            StrokePoint((point.x / width).coerceIn(0f, 1f), (point.y / height).coerceIn(0f, 1f))
        }
    }

    private fun clearLassoInput() {
        v5LassoSession?.cancel()
        v5LassoSession = null
        lassoPoints.clear()
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
        val selectionState = documentSelectionState
        val beforeAlpha = selectionState?.snapshot()
        val beforeIds = selectedStrokeIds.toSet()
        val beforeRegions = selectedMaskRegions.map(List<StrokePoint>::toList)
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
        val exactMask = if (region.isEmpty) null else SparseAlphaMask.fromFloodFill(
            region.mask,
            region.left,
            region.top,
            sampleWidth,
            sampleHeight,
            pageModelWidth,
            pageModelHeight,
        )
        composeSelectionAlpha(exactMask, selectionMode)
        if (selectionMode == SelectionMode.REPLACE) selectedMaskRegions = emptyList()
        region.mask.recycle()
        page.recycle()
        recordSelectionHistory(selectionState, beforeAlpha, beforeIds, beforeRegions)
        invalidate()
    }

    private fun selectWithLasso(polygon: List<StrokePoint>) {
        selectWithLasso(polygon, exactMask = null)
    }

    private fun selectWithCanonicalLasso(contour: CanonicalLassoContour) {
        val logicalWidth = documentSelectionState?.logicalWidth
            ?: SelectionMaskCompositor.maskSize(pageModelWidth, pageModelHeight)?.width
            ?: return
        val logicalHeight = documentSelectionState?.logicalHeight
            ?: SelectionMaskCompositor.maskSize(pageModelWidth, pageModelHeight)?.height
            ?: return
        val exact = SelectionMaskRasterizer.rasterize(
            contour = contour,
            documentWidth = pageModelWidth.coerceAtLeast(1f),
            documentHeight = pageModelHeight.coerceAtLeast(1f),
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )?.let(SparseAlphaMask::fromSnapshot)
        val width = pageModelWidth.coerceAtLeast(1f)
        val height = pageModelHeight.coerceAtLeast(1f)
        val polygon = contour.points.map { point -> StrokePoint(point.x / width, point.y / height) }
        selectWithLasso(polygon, exact)
    }

    private fun selectWithLasso(polygon: List<StrokePoint>, exactMask: SparseAlphaMask?) {
        if (polygon.size < 3) return
        val selectionState = documentSelectionState
        val beforeAlpha = selectionState?.snapshot()
        val beforeIds = selectedStrokeIds.toSet()
        val beforeRegions = selectedMaskRegions.map(List<StrokePoint>::toList)
        composeSelectionAlpha(
            exactMask ?: rasterizeSelectionPolygonV5(polygon),
            selectionMode,
        )
        // A selection is a mask, not a document edit. Never split/rewrite artwork while merely
        // selecting it; vector consumers use these ids while raster consumers use the same alpha.
        val hits = strokes.asSequence().filter { stroke ->
            stroke.id !in protectedStrokeIds && stroke.layerId !in protectedLayerIds && when (selectionFilter) {
                SelectionFilter.ALL -> true
                SelectionFilter.ACTIVE_LAYER -> stroke.layerId == activeLayerId
                SelectionFilter.CURRENT_BRUSH -> stroke.brushId == brushId
                SelectionFilter.CURRENT_COLOR -> stroke.colorArgb.toInt() == inkColor
            }
        }.filter { stroke -> LassoGeometry.partition(polygon, stroke).pieces.any { it.selected } }
            .mapTo(linkedSetOf(), InkStroke::id)
        when (selectionMode) {
            SelectionMode.REPLACE -> { selectedStrokeIds.clear(); selectedStrokeIds.addAll(hits) }
            SelectionMode.ADD -> selectedStrokeIds.addAll(hits)
            SelectionMode.SUBTRACT -> selectedStrokeIds.removeAll(hits)
            SelectionMode.INTERSECT -> selectedStrokeIds.retainAll(hits)
        }
        selectedMaskRegions = when (selectionMode) {
            SelectionMode.REPLACE -> listOf(polygon.toList())
            SelectionMode.ADD -> selectedMaskRegions + listOf(polygon.toList())
            SelectionMode.SUBTRACT, SelectionMode.INTERSECT -> emptyList()
        }
        if (selectionMode == SelectionMode.REPLACE) selectionFeatherPixels = 0f
        recordSelectionHistory(selectionState, beforeAlpha, beforeIds, beforeRegions)
        invalidate()
    }

    private fun rasterizeSelectionPolygonV5(polygon: List<StrokePoint>): SparseAlphaMask? {
        if (polygon.size < 3) return null
        val logicalWidth = documentSelectionState?.logicalWidth
            ?: SelectionMaskCompositor.maskSize(pageModelWidth, pageModelHeight)?.width
            ?: return null
        val logicalHeight = documentSelectionState?.logicalHeight
            ?: SelectionMaskCompositor.maskSize(pageModelWidth, pageModelHeight)?.height
            ?: return null
        val contour = CanonicalLassoContour(
            points = polygon.map { point ->
                DocumentPoint(
                    point.x.coerceIn(0f, 1f) * pageModelWidth.coerceAtLeast(1f),
                    point.y.coerceIn(0f, 1f) * pageModelHeight.coerceAtLeast(1f),
                )
            },
            closed = true,
        )
        return SelectionMaskRasterizer.rasterize(
            contour = contour,
            documentWidth = pageModelWidth.coerceAtLeast(1f),
            documentHeight = pageModelHeight.coerceAtLeast(1f),
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )?.let(SparseAlphaMask::fromSnapshot)
    }

    private fun recordSelectionHistory(
        state: DocumentSelectionState?,
        beforeAlpha: MaskTileSurfaceSnapshot?,
        beforeIds: Set<String>,
        beforeRegions: List<List<StrokePoint>>,
    ) {
        state ?: return
        beforeAlpha ?: return
        val afterAlpha = state.snapshot()
        val afterIds = selectedStrokeIds.toSet()
        val afterRegions = selectedMaskRegions.map(List<StrokePoint>::toList)
        fun restore(alpha: MaskTileSurfaceSnapshot, ids: Set<String>, regions: List<List<StrokePoint>>) {
            state.replaceFrom(alpha)
            selectedStrokeIds.clear()
            selectedStrokeIds.addAll(ids.intersect(strokes.mapTo(mutableSetOf(), InkStroke::id)))
            selectedMaskRegions = regions.map(List<StrokePoint>::toList)
            rebuildSelectionDerivedState()
        }
        recordExternalEdit(
            undo = { restore(beforeAlpha, beforeIds, beforeRegions) },
            redo = { restore(afterAlpha, afterIds, afterRegions) },
        )
    }

    private fun addPoint(x: Float, y: Float, pressure: Float, time: Long, tilt: Float, orientation: Float, force: Boolean = false) {
        traceEngineV5("Inkloom.V5.InputModel") {
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
        traceEngineV5("Inkloom.V5.InputModelBatch") {
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
        if (previous == null) StrokeTrace.markOnce(currentStrokeId, StrokeTrace.STAGE_FIRST_SAMPLE)
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
            // The first modeled sample has no history: velocity is 0 by construction and
            // tilt/orientation are often device defaults. Marking it not-established lets brush
            // dynamics treat the sensors as neutral instead of feeding zero through a curve.
            sensorFlags = if (previous == null) 0 else BrushSensorFlags.ALL,
        )
    }

    private fun requestActiveSurfaceFrame() {
        postInvalidateOnAnimation()
    }

    @VisibleForTesting
    internal fun flushRealtimeToolFrame() {
        if (smudgeFrameScheduled || hasPendingSmudge) flushSmudgeFrame()
        if (liquifyFrameScheduled || hasPendingLiquify) flushLiquifyFrame()
    }

    private fun clearActiveStrokeInput() {
        currentPoints.clear()
        strokeInputModeler.reset()
    }

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
            fillPreviewMask = null
            fillPreviewLeft = 0f
            fillPreviewTop = 0f
            fillPreviewWidth = if (hasSelection()) 1f else 0f
            fillPreviewHeight = if (hasSelection()) 1f else 0f
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
        if (fillGestureUsesSelection) {
            val snapshot = selectionMaskForRasterTools()
            if (snapshot != null) onSparseFillCommitted?.invoke(snapshot, inkColor.toUInt().toLong(), inkOpacity)
            fillPreviewMask?.recycle()
            fillPreviewMask = null
            fillSourceBitmap?.recycle()
            fillSourceBitmap = null
            fillGestureUsesSelection = false
            invalidate()
            return
        }
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
        val strokePadding = if (fillSubTool == FillSubTool.LEFTOVER_PEN) {
            gestureBrushSize.coerceAtLeast(.1f) * bitmapWidth / pageModelWidth.coerceAtLeast(1f) / 2f + 2f
        } else 2f
        val left = kotlin.math.floor(fillGesturePoints.minOf(StrokePoint::x) * bitmapWidth - strokePadding)
            .toInt().coerceIn(0, bitmapWidth - 1)
        val top = kotlin.math.floor(fillGesturePoints.minOf(StrokePoint::y) * bitmapHeight - strokePadding)
            .toInt().coerceIn(0, bitmapHeight - 1)
        val right = kotlin.math.ceil(fillGesturePoints.maxOf(StrokePoint::x) * bitmapWidth + strokePadding)
            .toInt().coerceIn(left + 1, bitmapWidth)
        val bottom = kotlin.math.ceil(fillGesturePoints.maxOf(StrokePoint::y) * bitmapHeight + strokePadding)
            .toInt().coerceIn(top + 1, bitmapHeight)
        val drawnMask = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(drawnMask)
        val path = Path().apply {
            moveTo(fillGesturePoints.first().x * bitmapWidth - left, fillGesturePoints.first().y * bitmapHeight - top)
            fillGesturePoints.drop(1).forEach { point -> lineTo(point.x * bitmapWidth - left, point.y * bitmapHeight - top) }
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
        selectionMaskForRasterTools()?.let { selection ->
            RasterSelectionMask(selection, bitmapWidth, bitmapHeight).apply(drawnMask, Rect(left, top, right, bottom))
        }
        if (NativeRasterOps.alphaBounds(drawnMask) == null) {
            drawnMask.recycle()
            fillGesturePoints.clear()
            invalidate()
            return
        }
        NativeRasterOps.alphaBounds(drawnMask)?.let { bounds ->
            val cropped = Bitmap.createBitmap(drawnMask, bounds.left, bounds.top, bounds.width(), bounds.height())
            val callback = onFillCommitted
            if (callback != null) callback(
                cropped,
                (left + bounds.left) / bitmapWidth.toFloat(),
                (top + bounds.top) / bitmapHeight.toFloat(),
                bounds.width() / bitmapWidth.toFloat() * pageModelWidth,
                bounds.height() / bitmapHeight.toFloat() * pageModelHeight,
                inkColor.toUInt().toLong(),
                inkOpacity,
            ) else cropped.recycle()
        }
        drawnMask.recycle()
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

    private fun beginSmudgeGesture(normalizedX: Float, normalizedY: Float): Boolean {
        removeCallbacks(smudgeFrame)
        smudgeFrameScheduled = false
        hasPendingSmudge = false
        rasterTileBlendSession?.close()
        rasterTileBlendSession = null
        val surface = rasterTileSurfaceFor(activeLayerId)
            ?.takeUnless { activeLayerId in protectedLayerIds }
            ?: return false
        rasterTileBlendSession = RasterTileBlendSession(
            surface,
            selectionMaskForRasterTools(),
            preserveDestinationAlpha = activeLayerId in alphaLockedLayers,
        )
        smudgeLastX = normalizedX.coerceIn(0f, 1f)
        smudgeLastY = normalizedY.coerceIn(0f, 1f)
        smudgeChanged = false
        smudgeDirtyBounds = null
        return true
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
        val tileSession = rasterTileBlendSession
        val surface = rasterTileSurfaceFor(activeLayerId)
        if (surface != null && tileSession != null) {
            val rasterWidth = surface.logicalWidth
            val rasterHeight = surface.logicalHeight
            val radius = (gestureBrushSize.coerceIn(.01f, 800f) / REFERENCE_WIDTH * rasterWidth).coerceAtLeast(.125f)
            val dirty = tileSession.blend(
                smudgeLastX * rasterWidth,
                smudgeLastY * rasterHeight,
                x * rasterWidth,
                y * rasterHeight,
                radius,
                (.3f + brushColorMix.coerceIn(0f, 1f) * .7f),
                brushHardness,
            )
            if (dirty != null) {
                smudgeChanged = true
                smudgeDirtyBounds = mergeDirtyBounds(smudgeDirtyBounds, dirty)
                invalidate(
                    (dirty.left * width.toFloat() / rasterWidth).toInt() - 2,
                    (dirty.top * height.toFloat() / rasterHeight).toInt() - 2,
                    (dirty.right * width.toFloat() / rasterWidth).toInt() + 2,
                    (dirty.bottom * height.toFloat() / rasterHeight).toInt() + 2,
                )
            }
            smudgeLastX = x
            smudgeLastY = y
            return
        }
        cancelSmudgeGesture()
    }

    private fun commitSmudgeGesture() {
        removeCallbacks(smudgeFrame)
        smudgeFrameScheduled = false
        hasPendingSmudge = false
        val tileSession = rasterTileBlendSession
        if (tileSession != null) {
            if (smudgeChanged) {
                val record = tileSession.commit()
                if (record.changedTileCount > 0) {
                    rasterTileSurfaceFor(activeLayerId)?.let { commitRasterLayerPatch(it, smudgeDirtyBounds) }
                }
                record.close()
            } else tileSession.restoreOriginal()
            tileSession.close()
        }
        rasterTileBlendSession = null
        smudgeChanged = false
        smudgeDirtyBounds = null
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
        }
        rasterTileBlendSession = null
        smudgeChanged = false
        smudgeDirtyBounds = null
        invalidate()
    }

    private fun beginLiquifyGesture(x: Float, y: Float, pressure: Float): Boolean {
        removeCallbacks(liquifyFrame)
        removeCallbacks(liquifyHoldStart)
        removeCallbacks(liquifyHoldFrame)
        liquifyFrameScheduled = false
        hasPendingLiquify = false
        rasterTileLiquifySession?.close()
        rasterTileLiquifySession = null
        val surface = rasterTileSurfaceFor(activeLayerId)
            ?.takeUnless { activeLayerId in protectedLayerIds }
            ?: return false
        rasterTileLiquifySession = RasterTileLiquifySession(surface, selectionMaskForRasterTools())
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
        return true
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
        val tileSession = rasterTileLiquifySession
        val surface = rasterTileSurfaceFor(activeLayerId)
        if (surface != null && tileSession != null) {
            val rasterWidth = surface.logicalWidth
            val rasterHeight = surface.logicalHeight
            val radius = (gestureBrushSize.coerceIn(.01f, 800f) / REFERENCE_WIDTH * rasterWidth).coerceAtLeast(.125f)
            val dirty = tileSession.deform(
                liquifyLastX * rasterWidth,
                liquifyLastY * rasterHeight,
                nextX * rasterWidth,
                nextY * rasterHeight,
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
        cancelLiquifyGesture()
    }

    private fun finishLiquifyGesture() {
        removeCallbacks(liquifyFrame)
        removeCallbacks(liquifyHoldStart)
        removeCallbacks(liquifyHoldFrame)
        liquifyFrameScheduled = false
        hasPendingLiquify = false
        val tileSession = rasterTileLiquifySession
        if (tileSession != null) {
            if (liquifyChanged) {
                val record = tileSession.commit()
                if (record.changedTileCount > 0) {
                    rasterTileSurfaceFor(activeLayerId)?.let { commitRasterLayerPatch(it, liquifyDirtyBounds) }
                }
                record.close()
            } else tileSession.restoreOriginal()
            tileSession.close()
        }
        rasterTileLiquifySession = null
        liquifyHoldEligible = false
        liquifyChanged = false
        liquifyDirtyBounds = null
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
        }
        rasterTileLiquifySession = null
        liquifyHoldEligible = false
        liquifyChanged = false
        liquifyDirtyBounds = null
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
        selectionMaskForRasterTools()?.nonZeroBounds()?.let { maskBounds ->
            left = minOf(left, maskBounds.left / pageModelWidth.coerceAtLeast(1f))
            top = minOf(top, maskBounds.top / pageModelHeight.coerceAtLeast(1f))
            right = maxOf(right, maskBounds.right / pageModelWidth.coerceAtLeast(1f))
            bottom = maxOf(bottom, maskBounds.bottom / pageModelHeight.coerceAtLeast(1f))
        }
        return if (left.isFinite()) android.graphics.RectF(left, top, right, bottom) else null
    }

    /** Returns a corner, edge, or rotation transform when the touch lands on a handle. */
    private fun hitSelectionHandle(normalizedX: Float, normalizedY: Float): Int {
        if (!hasSelection()) return TRANSFORM_NONE
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
        updateRasterSelectionTransform(scaleX, scaleY, rotation, 0f, 0f)
    }

    private fun beginRasterSelectionTransform(): Boolean {
        cancelRasterSelectionTransform()
        val surface = rasterTileSurfaceFor(activeLayerId) ?: return false
        val selection = selectionMaskForRasterTools()?.takeUnless(MaskTileSurfaceSnapshot::isEmpty) ?: return false
        return runCatching {
            rasterSelectionTransformOriginalMask = selection
            rasterSelectionTransformSession = RasterSelectionTransformSession(surface, selection)
            true
        }.getOrElse {
            rasterSelectionTransformOriginalMask = null
            false
        }
    }

    private fun updateRasterSelectionTransform(
        scaleX: Float,
        scaleY: Float,
        rotation: Float,
        translateX: Float,
        translateY: Float,
    ) {
        val session = rasterSelectionTransformSession ?: return
        val result = session.update(
            SelectionAffineTransform(
                centerX = selectionTransformCenterX * pageModelWidth,
                centerY = selectionTransformCenterY * pageModelHeight,
                scaleX = scaleX.coerceAtLeast(.01f),
                scaleY = scaleY.coerceAtLeast(.01f),
                rotationDegrees = rotation,
                translateX = translateX,
                translateY = translateY,
            ),
        )
        documentSelectionState?.replaceFrom(result.selection)
            ?: replaceSelectionAlphaMask(SparseAlphaMask.fromSnapshot(result.selection), resetFeather = false)
        rebuildSelectionDerivedState()
    }

    private fun finishRasterSelectionTransform(): Boolean {
        val session = rasterSelectionTransformSession ?: return false
        rasterSelectionTransformSession = null
        val originalSelection = rasterSelectionTransformOriginalMask
        rasterSelectionTransformOriginalMask = null
        val result = session.commit() ?: return false
        val record = result.transactionRecord ?: return false
        rasterTileSurfaceFor(activeLayerId)?.let { surface ->
            val layerId = activeLayerId
            publishRasterSelectionEdit(layerId, surface, result.dirtyBounds)
            recordExternalEdit(
                undo = {
                    surface.apply(record, app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection.UNDO)
                    originalSelection?.let { original ->
                        documentSelectionState?.replaceFrom(original)
                            ?: replaceSelectionAlphaMask(SparseAlphaMask.fromSnapshot(original), resetFeather = false)
                    }
                    rebuildSelectionDerivedState()
                    publishRasterSelectionEdit(layerId, surface, result.dirtyBounds)
                },
                redo = {
                    surface.apply(record, app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection.REDO)
                    documentSelectionState?.replaceFrom(result.selection)
                        ?: replaceSelectionAlphaMask(SparseAlphaMask.fromSnapshot(result.selection), resetFeather = false)
                    rebuildSelectionDerivedState()
                    publishRasterSelectionEdit(layerId, surface, result.dirtyBounds)
                },
                dispose = record::close,
            )
        } ?: run {
            record.close()
            return false
        }
        return true
    }

    private fun cancelRasterSelectionTransform() {
        val session = rasterSelectionTransformSession ?: return
        rasterSelectionTransformSession = null
        session.cancel()
        rasterSelectionTransformOriginalMask?.let { original ->
            documentSelectionState?.replaceFrom(original)
                ?: replaceSelectionAlphaMask(SparseAlphaMask.fromSnapshot(original), resetFeather = false)
        }
        rasterSelectionTransformOriginalMask = null
        rebuildSelectionDerivedState()
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        if (!selectionTransformArmed || !hasSelection()) return
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

    private data class V5PaintConfiguration(
        val paintOp: app.inkloom.engine.v5.raster.PaintOp,
        val maximumRadius: Float,
        val runtimeId: String,
    )

    /** Resolves every resource and scalar before contact work starts. Null means explicitly
     * unsupported by V5; callers must consume the gesture instead of falling back to V3/V4. */
    private fun v5PaintConfiguration(): V5PaintConfiguration? {
        val active = activeTool ?: return null
        if (active !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)) return null
        val surface = rasterTileSurfaceFor(activeLayerId) ?: return null
        if (surface.pixelFormat != PixelTileFormat.PREMULTIPLIED_SRGB_8888) return null
        if (activeLayerId in protectedLayerIds || activeLayerId in alphaLockedLayers ||
            activeLayerId in clippedLayers || layerMasks[activeLayerId]?.enabled == true
        ) return null
        if ((layerOpacities[activeLayerId] ?: 1f) < .999f ||
            (layerBlendModes[activeLayerId] ?: LayerBlendMode.NORMAL) != LayerBlendMode.NORMAL
        ) return null
        if (stabilization > .0001f || streamLine > .0001f || procreateStabilization > .0001f ||
            motionFiltering > .0001f || postCorrection > .0001f
        ) return null

        val diameter = if (active == EditorTool.ERASER) gestureEraserSize else gestureBrushSize
        val diameterPixels = (diameter.coerceIn(.01f, 800f) / REFERENCE_WIDTH * surface.logicalWidth)
            .coerceAtLeast(.1f)
        val selection = selectionMaskForRasterTools()?.takeUnless { it.isEmpty }
        val sampleTransforms = v5SampleTransforms(surface)
        fun pressure(enabled: Boolean, minimum: Float, curve: List<BrushCurvePoint>) = PressureResponse(
            enabled,
            minimum,
            curve.map { it.x to it.y },
        )
        fun tilt(dynamics: BrushSecondaryDynamics) = TiltResponse(
            dynamics.tiltEnabled,
            dynamics.tiltMinimum,
            dynamics.tiltStart,
            dynamics.tiltCurve.map { it.x to it.y },
        )
        if (active == EditorTool.ERASER) {
            val eraserTip = eraserTipAssetPath
            if (eraserTip != null) {
                val mask = v5BrushTipMasks.resolve(
                    BrushTipMaskRequest(eraserTip, eraserHardness.coerceIn(0f, 1f), antiAliasMode = brushAntiAliasMode),
                ) ?: return null
                val definition = StampBrushDefinition(
                    diameterPixels = diameterPixels,
                    colorArgb = Color.TRANSPARENT,
                    opacity = eraserOpacity.coerceIn(0f, 1f),
                    spacingRatio = CspBrushGap.ratio(brushSpacingMode, brushSpacing),
                    tips = listOf(mask),
                    sizePressure = PressureResponse(eraserPressureSize, .15f),
                    blend = DryBlend.DESTINATION_OUT,
                )
                return V5PaintConfiguration(
                    StampPaintOp(surface, definition, selection, sampleTransforms = sampleTransforms),
                    diameterPixels * .5f + 2f,
                    "eraser:$eraserTip",
                )
            }
            val definition = DryInkDefinition(
                    diameterPixels = diameterPixels,
                    colorArgb = Color.TRANSPARENT,
                    opacity = eraserOpacity.coerceIn(0f, 1f),
                    minimumPressureScale = if (eraserPressureSize) .15f else 1f,
                    hardness = eraserHardness.coerceIn(0f, 1f),
                    blend = DryBlend.DESTINATION_OUT,
                )
            return V5PaintConfiguration(
                DryInkPaintOp(surface, definition, selection, sampleTransforms),
                diameterPixels * .5f + 2f,
                "eraser:round",
            )
        }

        // CSP files retain non-zero mix/density values while Color mixing is switched off. The
        // parser preserves those values for editing, so only the authored enable bit (watercolor)
        // or an active Procreate Wet Mix program may select the V5 wet op.
        val wetEnabled = brushWatercolor || !procreateWetMix.isDisabled()
        if (wetEnabled && !brushDual && !brushSpray && tipAssetPath == null && tipAssetPaths.isEmpty()) {
            val authored = procreateWetMix
            val definition = WetPaintDefinition(
                diameterPixels = diameterPixels,
                colorArgb = inkColor,
                opacity = inkOpacity.coerceIn(0f, 1f),
                hardness = brushHardness.coerceIn(0f, 1f),
                dilution = if (authored.isDisabled()) brushColorMix.coerceIn(0f, 1f) else authored.dilution.coerceIn(0f, 1f),
                charge = if (authored.isDisabled()) brushPaintMix.coerceIn(.15f, 1f) else authored.charge.coerceIn(0f, 1f),
                attack = if (authored.isDisabled()) .7f else authored.attack.coerceIn(0f, 1f),
                pull = if (authored.isDisabled()) brushColorMix.coerceIn(0f, 1f) else authored.pull.coerceIn(0f, 1f),
                sizePressure = pressure(sizePressureEnabled, sizePressureMinimum, sizePressureCurve),
                sizeTilt = tilt(sizeSecondaryDynamics),
            )
            return V5PaintConfiguration(
                WetPaintOp(surface, definition, selection, sampleTransforms),
                diameterPixels * .5f + 2f,
                "wet:$brushId",
            )
        }

        val isAirbrush = brushId.contains("airbrush", ignoreCase = true)
        if (isAirbrush && !brushDual && !brushWatercolor && procreateWetMix.isDisabled() &&
            brushColorMix <= .001f && brushPaintMix <= .001f && !taperStartEnabled && !taperEndEnabled
        ) {
            val particleDiameter = if (brushSpray) {
                SprayGeometry.particleDiameter(
                    diameterPixels,
                    sprayParticleSize,
                    sprayParticleSizeUnit,
                    sprayParticleSizeSyncBrushSize,
                    surface.logicalWidth.toFloat(),
                )
            } else diameterPixels
            val particleSizePressure = if (brushSpray) {
                pressure(
                    sprayParticleSizePressureEnabled,
                    sprayParticleSizePressureMinimum,
                    sprayParticleSizePressureCurve,
                )
            } else pressure(sizePressureEnabled, sizePressureMinimum, sizePressureCurve)
            val scatterRadius = if (brushSpray) diameterPixels * .5f * spraySpread.coerceIn(0f, 4f) else 0f
            val definition = StampBrushDefinition(
                kind = PaintOpKind.AIRBRUSH,
                diameterPixels = particleDiameter.coerceIn(.1f, 4096f),
                spacingReferencePixels = diameterPixels,
                colorArgb = inkColor,
                opacity = inkOpacity.coerceIn(0f, 1f),
                flow = brushFlow.coerceIn(0f, 1f),
                spacingRatio = CspBrushGap.ratio(brushSpacingMode, brushSpacing),
                tips = listOf(BrushTipMask.solidCircle(96, brushHardness.coerceIn(0f, 1f))),
                sizePressure = particleSizePressure,
                sizeTilt = tilt(sizeSecondaryDynamics),
                opacityPressure = pressure(opacityPressureEnabled, opacityPressureMinimum, opacityPressureCurve),
                flowPressure = pressure(flowPressureEnabled, flowPressureMinimum, flowPressureCurve),
                rotationRadians = Math.toRadians(sprayParticleRotationDegrees.toDouble()).toFloat(),
                followStrokeDirection = sprayParticleFollowStrokeDirection,
                followStylusOrientation = sprayParticleFollowStylusOrientation,
                renderSeed = brushRenderSeed,
                particlesPerPlot = if (brushSpray) SprayGeometry.particlesPerPlot(sprayDensity) else 1,
                scatterRadiusPixels = scatterRadius,
                scatterBias = (sprayBias / 100f).coerceIn(-1f, 1f),
                randomRotationRadians = Math.toRadians(sprayParticleRandomRotationDegrees.toDouble()).toFloat(),
            )
            return V5PaintConfiguration(
                StampPaintOp(surface, definition, selection, sampleTransforms = sampleTransforms),
                scatterRadius + particleDiameter * .5f + 2f,
                "airbrush:$brushId",
            )
        }

        val unsupportedColorJitter =
            (colorJitterPerTipEnabled && (
                colorJitterHueDegrees != 0f || colorJitterSaturation != 0f || colorJitterValue != 0f
                )) ||
                (colorJitterPerStrokeEnabled && (
                    colorJitterStrokeHueDegrees != 0f || colorJitterStrokeSaturation != 0f ||
                        colorJitterStrokeValue != 0f || colorJitterStrokeSubColorMix != 0f
                    ))
        if (brushRibbon || brushWatercolor || !procreateWetMix.isDisabled() ||
            brushBlur > .001f || taperStartEnabled || taperEndEnabled || !procreateTaper.isDisabled() ||
            strokeFalloff > .0001f || strokePathJitter > .0001f || strokePathLongitudinalJitter > .0001f ||
            strokeRotationJitterDegrees != 0f || stampCount != 1 || unsupportedColorJitter
        ) return null
        val tipPaths = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }
        if (tipPaths.isNotEmpty()) {
            val masks = tipPaths.map { path ->
                v5BrushTipMasks.resolve(v5TipRequest(path)) ?: return null
            }
            val grain = grainAssetPath?.takeIf { grainDensity > .0001f }?.let { path ->
                v5BrushTipMasks.resolveGrain(
                    GrainMaskRequest(path, grainBrightness, grainContrast, grainReversed, grainEmphasized),
                ) ?: return null
            }
            val grainOffset = GrainBehavior.offset(brushRenderSeed, grainOffsetJitter)
            val definition = StampBrushDefinition(
                diameterPixels = diameterPixels,
                colorArgb = inkColor,
                opacity = inkOpacity.coerceIn(0f, 1f),
                flow = brushFlow.coerceIn(0f, 1f),
                spacingRatio = CspBrushGap.ratio(brushSpacingMode, brushSpacing),
                tips = masks,
                grain = grain,
                grainDensity = grainDensity.coerceIn(0f, 1f),
                grainScalePercent = grainScale.coerceIn(1f, 4000f),
                grainMovement = grainMovement.coerceIn(0f, 1f),
                grainZoom = grainZoom.coerceIn(0f, 1f),
                grainRotationRadians = Math.toRadians(grainRotationDegrees.toDouble()).toFloat(),
                grainPhaseX = grainOffset.x,
                grainPhaseY = grainOffset.y,
                sizePressure = pressure(sizePressureEnabled, sizePressureMinimum, sizePressureCurve),
                sizeTilt = tilt(sizeSecondaryDynamics),
                opacityPressure = pressure(opacityPressureEnabled, opacityPressureMinimum, opacityPressureCurve),
                flowPressure = pressure(flowPressureEnabled, flowPressureMinimum, flowPressureCurve),
                roundness = brushThickness.coerceIn(.01f, 1f),
                rotationRadians = Math.toRadians(brushRotation.toDouble()).toFloat(),
                followStrokeDirection = followStrokeDirection,
                followStylusOrientation = followStylusOrientation,
                renderSeed = brushRenderSeed,
                particlesPerPlot = if (brushSpray) SprayGeometry.particlesPerPlot(sprayDensity) else 1,
                scatterRadiusPixels = if (brushSpray) diameterPixels * .5f * spraySpread.coerceIn(0f, 4f) else 0f,
                scatterBias = (sprayBias / 100f).coerceIn(-1f, 1f),
                randomRotationRadians = kotlin.math.abs(Math.toRadians(
                    (randomRotationDegrees + if (brushSpray) sprayParticleRandomRotationDegrees else 0f).toDouble(),
                ).toFloat()),
                repeatStationarySamples = brushContinuousPlot,
            )
            val secondaryDefinitions = if (brushDual) {
                val dualMasks = dualTipAssetPaths.map { path ->
                    v5BrushTipMasks.resolve(
                        BrushTipMaskRequest(path, dualHardness.coerceIn(0f, 1f), antiAliasMode = dualAntiAliasMode),
                    ) ?: return null
                }
                if (dualMasks.isEmpty()) return null
                val dualGrain = dualGrainAssetPath?.takeIf { dualGrainDensity > .0001f }?.let { path ->
                    v5BrushTipMasks.resolveGrain(
                        GrainMaskRequest(
                            path,
                            dualGrainBrightness,
                            dualGrainContrast,
                            dualGrainReversed,
                            dualGrainEmphasized,
                        ),
                    ) ?: return null
                }
                val dualGrainOffset = GrainBehavior.offset(brushRenderSeed xor 0x4D2A61C7, dualGrainOffsetJitter)
                val dualDiameter = (diameterPixels * dualSizeScale.coerceIn(.01f, 8f)).coerceIn(.1f, 4096f)
                listOf(
                    StampBrushDefinition(
                        kind = if (dualSpray) PaintOpKind.AIRBRUSH else PaintOpKind.DECORATION,
                        diameterPixels = dualDiameter,
                        spacingReferencePixels = dualDiameter,
                        colorArgb = inkColor,
                        opacity = inkOpacity.coerceIn(0f, 1f),
                        flow = dualFlow.coerceIn(0f, 1f),
                        spacingRatio = CspBrushGap.ratio(dualSpacingMode, dualSpacing),
                        tips = dualMasks,
                        grain = dualGrain,
                        grainDensity = dualGrainDensity.coerceIn(0f, 1f),
                        grainScalePercent = dualGrainScale.coerceIn(1f, 4000f),
                        grainMovement = dualGrainMovement.coerceIn(0f, 1f),
                        grainZoom = dualGrainZoom.coerceIn(0f, 1f),
                        grainRotationRadians = Math.toRadians(dualGrainRotationDegrees.toDouble()).toFloat(),
                        grainPhaseX = dualGrainOffset.x,
                        grainPhaseY = dualGrainOffset.y,
                        roundness = dualThickness.coerceIn(.01f, 1f),
                        rotationRadians = Math.toRadians(dualRotationDegrees.toDouble()).toFloat(),
                        followStrokeDirection = dualFollowStrokeDirection,
                        followStylusOrientation = dualFollowStylusOrientation,
                        renderSeed = brushRenderSeed xor 0x35A71C9E,
                        particlesPerPlot = if (dualSpray) SprayGeometry.particlesPerPlot(dualSprayDensity) else 1,
                        scatterRadiusPixels = if (dualSpray) dualDiameter * .5f * dualSpraySpread.coerceIn(0f, 4f) else 0f,
                        scatterBias = (dualSprayBias / 100f).coerceIn(-1f, 1f),
                        randomRotationRadians = kotlin.math.abs(Math.toRadians(
                            (dualRandomRotationDegrees + if (dualSpray) dualSprayParticleRandomRotationDegrees else 0f).toDouble(),
                        ).toFloat()),
                        repeatStationarySamples = brushContinuousPlot,
                    ),
                )
            } else emptyList()
            val maximumDiameter = secondaryDefinitions.maxOfOrNull(StampBrushDefinition::diameterPixels)
                ?.coerceAtLeast(diameterPixels) ?: diameterPixels
            val maximumScatter = secondaryDefinitions.maxOfOrNull(StampBrushDefinition::scatterRadiusPixels)
                ?.coerceAtLeast(definition.scatterRadiusPixels) ?: definition.scatterRadiusPixels
            return V5PaintConfiguration(
                StampPaintOp(surface, definition, selection, secondaryDefinitions, sampleTransforms),
                maximumDiameter * .5f + maximumScatter + 2f,
                "stamp:$brushId:${masks.joinToString { it.contentHash().toString(16) }}:dual=${secondaryDefinitions.size}",
            )
        }
        if (grainAssetPath != null) return null
        if (brushContinuousPlot) {
            val definition = StampBrushDefinition(
                diameterPixels = diameterPixels,
                colorArgb = inkColor,
                opacity = inkOpacity.coerceIn(0f, 1f),
                flow = brushFlow.coerceIn(0f, 1f),
                spacingRatio = CspBrushGap.ratio(brushSpacingMode, brushSpacing),
                tips = listOf(BrushTipMask.solidCircle(96, brushHardness.coerceIn(0f, 1f))),
                sizePressure = pressure(sizePressureEnabled, sizePressureMinimum, sizePressureCurve),
                sizeTilt = tilt(sizeSecondaryDynamics),
                opacityPressure = pressure(opacityPressureEnabled, opacityPressureMinimum, opacityPressureCurve),
                flowPressure = pressure(flowPressureEnabled, flowPressureMinimum, flowPressureCurve),
                renderSeed = brushRenderSeed,
                repeatStationarySamples = true,
            )
            return V5PaintConfiguration(
                StampPaintOp(surface, definition, selection, sampleTransforms = sampleTransforms),
                diameterPixels * .5f + 2f,
                "continuous:$brushId",
            )
        }
        val pencilGrain = when {
            "6b-pencil" in brushId -> .38f
            "2b-pencil" in brushId -> .30f
            "hb-pencil" in brushId -> .22f
            "h-pencil" in brushId -> .16f
            "pencil" in brushId -> .12f
            else -> 0f
        }
        val definition = DryInkDefinition(
            diameterPixels = diameterPixels,
            colorArgb = inkColor,
            opacity = (inkOpacity * brushFlow).coerceIn(0f, 1f),
            minimumPressureScale = if (sizePressureEnabled) sizePressureMinimum.coerceIn(0f, 1f) else 1f,
            opacityPressureMinimum = if (opacityPressureEnabled) opacityPressureMinimum.coerceIn(0f, 1f) else 1f,
            flowPressureMinimum = if (flowPressureEnabled) flowPressureMinimum.coerceIn(0f, 1f) else 1f,
            hardness = brushHardness.coerceIn(0f, 1f),
            pencilGrain = pencilGrain,
            grainSeed = brushRenderSeed,
            sizePressure = PressureResponse(
                sizePressureEnabled,
                sizePressureMinimum,
                sizePressureCurve.map { it.x to it.y },
            ),
            sizeTilt = tilt(sizeSecondaryDynamics),
            opacityPressure = PressureResponse(
                opacityPressureEnabled,
                opacityPressureMinimum,
                opacityPressureCurve.map { it.x to it.y },
            ),
            flowPressure = PressureResponse(
                flowPressureEnabled,
                flowPressureMinimum,
                flowPressureCurve.map { it.x to it.y },
            ),
        )
        return V5PaintConfiguration(
            DryInkPaintOp(surface, definition, selection, sampleTransforms),
            diameterPixels * .5f + 2f,
            "dry:$brushId",
        )
    }

    private fun beginV5PaintGesture(event: MotionEvent, pointerIndex: Int, normalizedX: Float, normalizedY: Float): Boolean {
        if (activeTool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)) return false
        v5RejectedContact = false
        val surface = rasterTileSurfaceFor(activeLayerId)
        val configuration = v5PaintConfiguration()
        if (surface == null || configuration == null) {
            v5RejectedContact = true
            onPaintCapabilityRejected(
                if (surface == null) "V5 requires a raster layer surface"
                else "This brush configuration is not yet supported by Paint Engine V5",
            )
            return true
        }
        v5GestureStartNormalized = normalizedX to normalizedY
        activeCanvasSnapConstraint = CanvasGuides.constraintAt(
            canvasGuideState,
            normalizedX,
            normalizedY,
            pageModelWidth,
            pageModelHeight,
            width.toFloat(),
            height.toFloat(),
            GUIDE_SNAP_TOLERANCE_DP * resources.displayMetrics.density,
        )
        val initialSample = v5DocumentSample(event, pointerIndex, normalizedX, normalizedY, historicalIndex = null)
        val result = v5StrokeController.begin(
            layerId = activeLayerId,
            surface = surface,
            paintOp = configuration.paintOp,
            maximumRadius = configuration.maximumRadius,
            brushRuntimeId = configuration.runtimeId,
            sample = initialSample,
        )
        v5DryGestureCount++
        invalidateV5Damage(surface, result)
        if (brushContinuousPlot && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
            updateContinuousPlotInput(
                normalizedX,
                normalizedY,
                initialSample.pressure,
                initialSample.tiltRadians,
                initialSample.orientationRadians,
            )
            startContinuousPlot()
        }
        return true
    }

    private fun v5SampleTransforms(surface: PixelTileSurface): List<PaintSampleTransform> = when (symmetryMode) {
        SymmetryMode.NONE -> listOf(PaintSampleTransform.Identity)
        SymmetryMode.HORIZONTAL -> listOf(
            PaintSampleTransform.Identity,
            PaintSampleTransform.horizontalAxis(surface.logicalHeight.toFloat()),
        )
        SymmetryMode.VERTICAL -> listOf(
            PaintSampleTransform.Identity,
            PaintSampleTransform.verticalAxis(surface.logicalWidth.toFloat()),
        )
        SymmetryMode.QUADRANT -> listOf(
            PaintSampleTransform.Identity,
            PaintSampleTransform.horizontalAxis(surface.logicalHeight.toFloat()),
            PaintSampleTransform.verticalAxis(surface.logicalWidth.toFloat()),
            PaintSampleTransform.bothAxes(surface.logicalWidth.toFloat(), surface.logicalHeight.toFloat()),
        )
        SymmetryMode.RADIAL_4, SymmetryMode.RADIAL_6, SymmetryMode.RADIAL_8 -> {
            val count = when (symmetryMode) {
                SymmetryMode.RADIAL_4 -> 4
                SymmetryMode.RADIAL_6 -> 6
                else -> 8
            }
            List(count) { index ->
                PaintSampleTransform.rotation(
                    (Math.PI * 2.0 * index / count).toFloat(),
                    surface.logicalWidth * .5f,
                    surface.logicalHeight * .5f,
                )
            }
        }
    }

    private fun appendV5PaintGesture(event: MotionEvent, pointerIndex: Int): Boolean {
        if (v5RejectedContact) return true
        if (!v5StrokeController.isActive) return false
        val samples = buildList(event.historySize + 1) {
            for (index in 0 until event.historySize) {
                val point = historicalCanvasPoint(event, pointerIndex, index)
                add(v5DocumentSample(
                    event,
                    pointerIndex,
                    point.first / width.coerceAtLeast(1),
                    point.second / height.coerceAtLeast(1),
                    historicalIndex = index,
                ))
            }
            val point = canvasPoint(event, pointerIndex)
            add(v5DocumentSample(
                event,
                pointerIndex,
                point.first / width.coerceAtLeast(1),
                point.second / height.coerceAtLeast(1),
                historicalIndex = null,
            ))
        }
        val surface = rasterTileSurfaceFor(activeLayerId) ?: return false
        if (brushContinuousPlot) {
            val last = samples.last()
            updateContinuousPlotInput(
                last.x / surface.logicalWidth,
                last.y / surface.logicalHeight,
                last.pressure,
                last.tiltRadians,
                last.orientationRadians,
            )
        }
        v5StrokeController.append(samples)?.let { invalidateV5Damage(surface, it) }
        return true
    }

    private fun finishV5PaintGesture(event: MotionEvent, pointerIndex: Int, normalizedX: Float, normalizedY: Float): Boolean {
        if (v5RejectedContact) {
            v5RejectedContact = false
            return true
        }
        if (!v5StrokeController.isActive) return false
        val surface = rasterTileSurfaceFor(activeLayerId) ?: run {
            v5StrokeController.cancel()
            return true
        }
        val published = v5StrokeController.finish(
            v5DocumentSample(event, pointerIndex, normalizedX, normalizedY, historicalIndex = null),
        ) ?: return true
        try {
            v5DryPublishedGeneration = published.snapshot.revision
            published.dirtyBounds?.let { bounds ->
                commitRasterLayerPatch(
                    surface,
                    Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                )
                invalidateV5Damage(surface, bounds)
            }
        } finally {
            published.record.close()
        }
        return true
    }

    private fun v5DocumentSample(
        event: MotionEvent,
        pointerIndex: Int,
        normalizedX: Float,
        normalizedY: Float,
        historicalIndex: Int?,
    ): RawDocumentSample {
        val surface = requireNotNull(rasterTileSurfaceFor(activeLayerId))
        val pressure = if (historicalIndex == null) event.getPressure(pointerIndex)
            else event.getHistoricalPressure(pointerIndex, historicalIndex)
        val timeMillis = if (historicalIndex == null) event.eventTime else event.getHistoricalEventTime(historicalIndex)
        val tilt = if (historicalIndex == null) event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
            else event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, historicalIndex)
        val orientation = if (historicalIndex == null) event.getOrientation(pointerIndex)
            else event.getHistoricalOrientation(pointerIndex, historicalIndex)
        val constrained = v5ConstrainedNormalizedPoint(normalizedX, normalizedY)
        return RawDocumentSample(
            x = constrained.first * surface.logicalWidth,
            y = constrained.second * surface.logicalHeight,
            eventTimeNanos = timeMillis.coerceAtLeast(0L) * 1_000_000L,
            enqueuedTimeNanos = SystemClock.uptimeMillis() * 1_000_000L,
            pressure = PressureCalibration.apply(
                pressure,
                pressureMinimumInput,
                pressureMaximumInput,
                pressureSensitivity,
                pressureResponseCurve,
            ),
            tiltRadians = tilt.takeIf(Float::isFinite) ?: 0f,
            orientationRadians = orientation.takeIf(Float::isFinite) ?: 0f,
            tool = if (activeTool == EditorTool.ERASER) InputTool.STYLUS_ERASER else InputTool.STYLUS,
        )
    }

    private fun v5ConstrainedNormalizedPoint(x: Float, y: Float): Pair<Float, Float> {
        val start = v5GestureStartNormalized
        if (!rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) &&
            activePerspectiveSnapConstraint == null && start != null
        ) {
            activePerspectiveSnapConstraint = PerspectiveGuides.constraintAt(
                perspectiveGuideState,
                start.first,
                start.second,
                x,
                y,
                width.toFloat(),
                height.toFloat(),
                GUIDE_CAPTURE_MOVEMENT_DP * resources.displayMetrics.density,
            )
        }
        if (!rulerEnabled && perspectiveGuideState.mode == PerspectiveGuideMode.NONE &&
            activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) &&
            activeSpecialRulerConstraint == null && start != null
        ) {
            activeSpecialRulerConstraint = SpecialRulers.constraintAt(
                specialRulerState,
                start.first,
                start.second,
                x,
                y,
                width.toFloat(),
                height.toFloat(),
                GUIDE_CAPTURE_MOVEMENT_DP * resources.displayMetrics.density,
            )
        }
        val result = if (rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
            RulerGuide.project(rulerGuide, x, y, width.toFloat(), height.toFloat())
        } else {
            activePerspectiveSnapConstraint?.apply(x, y)
                ?: activeSpecialRulerConstraint?.apply(x, y)
                ?: activeCanvasSnapConstraint?.apply(x, y)
        }
        return (result?.x ?: x).coerceIn(0f, 1f) to (result?.y ?: y).coerceIn(0f, 1f)
    }

    private fun invalidateV5Damage(surface: PixelTileSurface, result: PaintResult) {
        result.dirtyBounds?.let { invalidateV5Damage(surface, it) }
    }

    private fun invalidateV5Damage(surface: PixelTileSurface, bounds: PixelRect) {
        postInvalidateOnAnimation(
            (bounds.left * width.toFloat() / surface.logicalWidth).toInt() - 2,
            (bounds.top * height.toFloat() / surface.logicalHeight).toInt() - 2,
            (bounds.right * width.toFloat() / surface.logicalWidth).toInt() + 2,
            (bounds.bottom * height.toFloat() / surface.logicalHeight).toInt() + 2,
        )
    }

    /**
     * Programs whose geometry is append-only are deposited once per sampled dab. Ordinary paint
     * is associative source-over; wet paint is non-associative but remains correct because each
     * fresh dab is composed exactly once, in input order, into one retained private transaction.
     */
    private fun findStroke(x: Float, y: Float, threshold: Float): Int = strokes.indexOfLast { stroke ->
        stroke.id !in protectedStrokeIds && StrokeEraser.intersects(stroke, x, y, threshold, threshold)
    }

    fun undo() {
        val edit = editHistory.takeUndo() ?: return
        when (edit) {
            is EngineTransaction.Strokes -> {
                if (editTouchesProtectedLayer(edit.edit)) {
                    editHistory.restoreUndo(edit)
                    return
                }
                applyStrokeEdit(edit.edit, forward = false)
            }
            is EngineTransaction.External -> edit.undo()
        }
    }

    fun redo() {
        val edit = editHistory.takeRedo() ?: return
        when (edit) {
            is EngineTransaction.Strokes -> {
                if (editTouchesProtectedLayer(edit.edit)) {
                    editHistory.restoreRedo(edit)
                    return
                }
                applyStrokeEdit(edit.edit, forward = true)
            }
            is EngineTransaction.External -> edit.redo()
        }
    }

    /** Adds a host-owned page mutation to the same chronological stream as ink edits. */
    fun recordExternalEdit(undo: () -> Unit, redo: () -> Unit, dispose: () -> Unit = {}) {
        editHistory.record(EngineTransaction.External(undo, redo, dispose))
    }

    private fun commitStrokeEdit(before: List<InkStroke>, after: List<InkStroke>) {
        val edit = StrokeEdits.difference(before, after) ?: return
        editHistory.record(EngineTransaction.Strokes(edit))
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
        val selection = selectionMaskForRasterTools()
        val rasterSurface = rasterTileSurfaceFor(activeLayerId)
        if (selection != null && rasterSurface != null) {
            RasterSelectionContent.copy(rasterSurface.snapshot(), selection)?.let { copied ->
                rasterSelectionClipboard = copied
                return true
            }
        }
        val selected = selectedVectorFragments()
        val encoded = StrokeClipboardCodec.encode(selected) ?: return false
        val clip = ClipData(
            ClipDescription("Inkloom strokes", arrayOf(StrokeClipboardCodec.MIME_TYPE)),
            ClipData.Item(encoded),
        )
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        return true
    }

    fun cutSelectionToClipboard(): Boolean {
        if (activeLayerId in protectedLayerIds) return false
        if (!copySelectionToClipboard()) return false
        deleteSelection()
        return true
    }

    fun pasteSelectionFromClipboard(): Boolean {
        if (activeLayerId in protectedLayerIds) return false
        val rasterSurface = rasterTileSurfaceFor(activeLayerId)
        val rasterClipboard = rasterSelectionClipboard
        if (rasterSurface != null && rasterClipboard != null && rasterClipboard.pixelFormat == rasterSurface.pixelFormat) {
            val offsetX = (pageModelWidth * .03f).toInt()
            val offsetY = (pageModelHeight * .03f).toInt()
            val left = (rasterClipboard.sourceLeft + offsetX).coerceIn(-rasterClipboard.width + 1, rasterSurface.logicalWidth - 1)
            val top = (rasterClipboard.sourceTop + offsetY).coerceIn(-rasterClipboard.height + 1, rasterSurface.logicalHeight - 1)
            val edit = RasterSelectionContent.paste(rasterSurface, rasterClipboard, left, top) ?: return false
            val layerId = activeLayerId
            publishRasterSelectionEdit(layerId, rasterSurface, edit.dirtyBounds)
            recordExternalEdit(
                undo = { rasterSurface.apply(edit.record, app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection.UNDO); publishRasterSelectionEdit(layerId, rasterSurface, edit.dirtyBounds) },
                redo = { rasterSurface.apply(edit.record, app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection.REDO); publishRasterSelectionEdit(layerId, rasterSurface, edit.dirtyBounds) },
                dispose = edit.record::close,
            )
            val pastedSelection = RasterSelectionContent.selectionAt(
                rasterClipboard,
                documentSelectionState?.logicalWidth ?: selectionMaskForRasterTools()?.logicalWidth ?: rasterSurface.logicalWidth,
                documentSelectionState?.logicalHeight ?: selectionMaskForRasterTools()?.logicalHeight ?: rasterSurface.logicalHeight,
                documentSelectionState?.surface()?.tileSize ?: MaskTileSurface.DEFAULT_TILE_SIZE,
                left,
                top,
                rasterSurface.logicalWidth,
                rasterSurface.logicalHeight,
            )
            replaceSelectionFromMask(pastedSelection)
            return true
        }
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

    private fun hasSelection(): Boolean = selectedStrokeIds.isNotEmpty() ||
        documentSelectionState?.isEmpty == false || isolatedTestSelectionAlphaMask?.isEmpty == false

    /** A short-lived facade only. Production alpha remains owned by [DocumentSelectionState]. */
    private fun currentSelectionAlphaMask(): SparseAlphaMask? = documentSelectionState?.surface()
        ?.let(SparseAlphaMask::fromSurface) ?: isolatedTestSelectionAlphaMask

    fun hasActiveSelection(): Boolean = hasSelection()

    private fun notifySelectionActiveChanged() {
        val active = hasSelection()
        if (lastReportedSelectionActive == active) return
        lastReportedSelectionActive = active
        onSelectionActiveChanged(active)
    }

    private fun ensureSelectionAlphaFromSelectedStrokes() {
        if (currentSelectionAlphaMask() != null || selectedStrokeIds.isEmpty()) return
        syncSelectionAlphaFromSelectedStrokes(resetFeather = false)
    }

    private fun syncSelectionAlphaFromSelectedStrokes(resetFeather: Boolean) {
        val selected = strokes.filter { it.id in selectedStrokeIds }
        replaceSelectionAlphaMask(SparseAlphaMask.fromStrokes(selected, pageModelWidth, pageModelHeight), resetFeather)
    }

    private fun composeSelectionAlpha(region: SparseAlphaMask?, mode: SelectionMode) {
        selectionFeatherBase = null
        val operation = when (mode) {
            SelectionMode.REPLACE -> AlphaMaskOperation.REPLACE
            SelectionMode.ADD -> AlphaMaskOperation.ADD
            SelectionMode.SUBTRACT -> AlphaMaskOperation.SUBTRACT
            SelectionMode.INTERSECT -> AlphaMaskOperation.INTERSECT
        }
        val base = currentSelectionAlphaMask()
        val combined = base?.composed(region, operation) ?: when (operation) {
            AlphaMaskOperation.REPLACE, AlphaMaskOperation.ADD -> region
            AlphaMaskOperation.SUBTRACT -> SparseAlphaMask.full(pageModelWidth, pageModelHeight)?.let { full ->
                try { full.composed(region, AlphaMaskOperation.SUBTRACT) } finally { full.close() }
            }
            AlphaMaskOperation.INTERSECT -> null
        }
        if (region != null && combined !== region) region.close()
        replaceSelectionAlphaMask(combined, resetFeather = mode == SelectionMode.REPLACE)
    }

    /** Takes ownership of [replacement]. */
    private fun replaceSelectionAlphaMask(replacement: SparseAlphaMask?, resetFeather: Boolean) {
        val documentState = documentSelectionState
        if (documentState == null) {
            isolatedTestSelectionAlphaMask?.close()
            isolatedTestSelectionAlphaMask = replacement
        } else {
            if (replacement?.surface !== documentState.surface()) {
                documentState.replaceFrom(replacement?.surface?.snapshot())
            }
            replacement?.close()
        }
        if (resetFeather) selectionFeatherPixels = 0f
        if (resetFeather) selectionFeatherBase = null
        rebuildSelectionDerivedState()
    }

    private fun rebuildSelectionDerivedState() {
        selectionBoundaryMask?.close()
        val base = currentSelectionAlphaMask()
        if (base == null) {
            selectionBoundaryMask = null
        } else {
            selectionBoundaryMask = base.boundary()
        }
        notifySelectionActiveChanged()
        invalidate()
    }

    /** Authoritative selection sampled by raster tools without an entire-page Bitmap bridge. */
    private fun selectionMaskForRasterTools(): MaskTileSurfaceSnapshot? {
        ensureSelectionAlphaFromSelectedStrokes()
        return documentSelectionState?.snapshot() ?: isolatedTestSelectionAlphaMask?.surface?.snapshot()
    }

    fun selectionMaskSurfaceSnapshot(): MaskTileSurfaceSnapshot? = selectionMaskForRasterTools()

    fun replaceSelectionFromMask(snapshot: MaskTileSurfaceSnapshot?): Boolean {
        val documentState = documentSelectionState ?: return false
        if (snapshot != null && (snapshot.logicalWidth != documentState.logicalWidth || snapshot.logicalHeight != documentState.logicalHeight)) {
            return false
        }
        selectedStrokeIds.clear()
        selectedMaskRegions = emptyList()
        documentState.replaceFrom(snapshot)
        selectionFeatherPixels = 0f
        selectionFeatherBase = null
        rebuildSelectionDerivedState()
        return snapshot?.isEmpty == false
    }

    fun fillSelection(): Boolean {
        val mask = selectionMaskForRasterTools() ?: return false
        val callback = onSparseFillCommitted ?: return false
        callback(mask, inkColor.toUInt().toLong(), inkOpacity)
        return true
    }

    /**
     * Clears only View-owned selection affordances. Document selection alpha belongs to
     * [DocumentSelectionRuntime] and must survive page switches, recomposition and View detach.
     */
    private fun resetSelectionViewBinding(clearReselect: Boolean, unbindDocument: Boolean) {
        selectedStrokeIds.clear()
        selectedMaskRegions = emptyList()
        isolatedTestSelectionAlphaMask?.close(); isolatedTestSelectionAlphaMask = null
        selectionBoundaryMask?.close(); selectionBoundaryMask = null
        selectionFeatherPixels = 0f
        clearLassoInput()
        movingSelection = false
        selectionMoved = false
        selectionTransformArmed = false
        selectionTransformMode = TRANSFORM_NONE
        if (clearReselect) {
            lastDeselectedStrokeIds = emptySet()
            lastDeselectedMaskRegions = emptyList()
            isolatedTestLastDeselectedAlphaMask?.close(); isolatedTestLastDeselectedAlphaMask = null
            lastDeselectedFeatherPixels = 0f
        }
        if (unbindDocument) documentSelectionState = null else rebuildSelectionDerivedState()
        if (unbindDocument) {
            notifySelectionActiveChanged()
            invalidate()
        }
    }

    fun selectAllEditableStrokes(): Boolean {
        val selectionState = documentSelectionState
        val beforeAlpha = selectionState?.snapshot()
        val beforeIds = selectedStrokeIds.toSet()
        val beforeRegions = selectedMaskRegions.map(List<StrokePoint>::toList)
        val selectable = strokes.asSequence()
            .filterNot { it.id in protectedStrokeIds || it.layerId in protectedLayerIds }
            .map(InkStroke::id)
            .toList()
        selectedStrokeIds.clear()
        selectedStrokeIds += selectable
        selectedMaskRegions = emptyList()
        replaceSelectionAlphaMask(SparseAlphaMask.full(pageModelWidth, pageModelHeight), resetFeather = true)
        recordSelectionHistory(selectionState, beforeAlpha, beforeIds, beforeRegions)
        invalidate()
        return hasSelection()
    }

    /** Selects every editable stroke that is not currently selected. */
    fun invertSelection(): Boolean {
        val selectionState = documentSelectionState
        val beforeAlpha = selectionState?.snapshot()
        val beforeIds = selectedStrokeIds.toSet()
        val beforeRegions = selectedMaskRegions.map(List<StrokePoint>::toList)
        val editable = strokes.asSequence()
            .filterNot { it.id in protectedStrokeIds || it.layerId in protectedLayerIds }
            .map(InkStroke::id)
            .toSet()
        val inverted = editable - selectedStrokeIds
        ensureSelectionAlphaFromSelectedStrokes()
        val invertedAlpha = currentSelectionAlphaMask()?.inverted() ?: run {
            SparseAlphaMask.full(pageModelWidth, pageModelHeight) ?: return false
        }
        selectedStrokeIds.clear()
        selectedStrokeIds += inverted
        selectedMaskRegions = emptyList()
        replaceSelectionAlphaMask(invertedAlpha, resetFeather = false)
        clearLassoInput()
        movingSelection = false
        selectionFeatherBase = null
        recordSelectionHistory(selectionState, beforeAlpha, beforeIds, beforeRegions)
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
        clearLassoInput()
        invalidate()
    }

    /** Cancels only the transient Selection interaction. A committed alpha/stroke selection is
     * preserved so the host can leave the Selection tool without losing the user's work. */
    fun cancelSelectionInput() {
        if (activeTool != null) cancelActiveGestureForNavigation()
        clearLassoInput()
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
        documentSelectionState?.captureForReselect()
        isolatedTestLastDeselectedAlphaMask?.close()
        isolatedTestLastDeselectedAlphaMask = if (documentSelectionState == null) currentSelectionAlphaMask()?.copy() else null
        lastDeselectedFeatherPixels = selectionFeatherPixels
        selectedStrokeIds.clear()
        selectedMaskRegions = emptyList()
        replaceSelectionAlphaMask(null, resetFeather = true)
        clearLassoInput()
        movingSelection = false
        selectionTransformArmed = false
        invalidate()
        return true
    }

    fun reselect(): Boolean {
        val available = strokes.mapTo(mutableSetOf(), InkStroke::id)
        val restored = lastDeselectedStrokeIds.intersect(available)
        val restoredDocumentAlpha = documentSelectionState?.restoreReselect() == true
        val restoredIsolatedAlpha = isolatedTestLastDeselectedAlphaMask?.isEmpty == false
        if (restored.isEmpty() && !restoredDocumentAlpha && !restoredIsolatedAlpha) return false
        selectedStrokeIds.clear()
        selectedStrokeIds += restored
        selectedMaskRegions = lastDeselectedMaskRegions.map(List<StrokePoint>::toList)
        selectionFeatherPixels = lastDeselectedFeatherPixels
        if (!restoredDocumentAlpha) replaceSelectionAlphaMask(isolatedTestLastDeselectedAlphaMask?.copy(), resetFeather = false)
        else rebuildSelectionDerivedState()
        invalidate()
        return true
    }

    fun expandSelection(pixels: Float): Boolean = modifySelectionContours(pixels.coerceAtLeast(0f))

    fun shrinkSelection(pixels: Float): Boolean = modifySelectionContours(-pixels.coerceAtLeast(0f))

    fun borderSelection(widthPixels: Float): Boolean {
        val selectionState = documentSelectionState
        val beforeAlpha = selectionState?.snapshot()
        val beforeIds = selectedStrokeIds.toSet()
        val beforeRegions = selectedMaskRegions.map(List<StrokePoint>::toList)
        ensureSelectionAlphaFromSelectedStrokes()
        val base = currentSelectionAlphaMask() ?: return false
        val radius = ceil(widthPixels.coerceAtLeast(1f) / 2f * base.width / pageModelWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val outerMask = base.morphed(radius, dilate = true) ?: return false
        val innerMask = base.morphed(radius, dilate = false)
        val ring = try { outerMask.composed(innerMask, AlphaMaskOperation.SUBTRACT) } finally {
            outerMask.close()
            innerMask?.close()
        } ?: return false
        selectedMaskRegions = emptyList()
        selectionFeatherBase = null
        replaceSelectionAlphaMask(ring, resetFeather = false)
        recordSelectionHistory(selectionState, beforeAlpha, beforeIds, beforeRegions)
        invalidate()
        return hasSelection()
    }

    fun featherSelection(pixels: Float): Boolean {
        if (!hasSelection()) return false
        ensureSelectionAlphaFromSelectedStrokes()
        val requested = pixels.coerceIn(0f, minOf(pageModelWidth, pageModelHeight) / 2f)
        val stateSnapshot = selectionFeatherBase ?: selectionMaskForRasterTools()?.also { selectionFeatherBase = it } ?: return false
        val base = SparseAlphaMask.fromSnapshot(stateSnapshot) ?: return false
        val replacement = try {
            if (requested > 0f) {
                val radius = requested * base.width / pageModelWidth.coerceAtLeast(1f)
                base.feathered(radius)
            } else base.copy()
        } finally { base.close() }
        replaceSelectionAlphaMask(replacement, resetFeather = false)
        selectionFeatherPixels = requested
        invalidate()
        return true
    }

    private fun modifySelectionContours(pixels: Float): Boolean {
        val selectionState = documentSelectionState
        val beforeAlpha = selectionState?.snapshot()
        val beforeIds = selectedStrokeIds.toSet()
        val beforeRegions = selectedMaskRegions.map(List<StrokePoint>::toList)
        ensureSelectionAlphaFromSelectedStrokes()
        val base = currentSelectionAlphaMask() ?: return false
        val radius = ceil(kotlin.math.abs(pixels) * base.width / pageModelWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val changed = base.morphed(radius, dilate = pixels > 0f) ?: return false
        selectionFeatherBase = null
        replaceSelectionAlphaMask(changed, resetFeather = false)
        val contours = selectionMaskRegions()
        if (contours.isNotEmpty()) selectedMaskRegions = contours.map { SelectionContourGeometry.offset(it, pixels, pageModelWidth, pageModelHeight) }
        recordSelectionHistory(selectionState, beforeAlpha, beforeIds, beforeRegions)
        invalidate()
        return hasSelection()
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
        if (activeLayerId in protectedLayerIds) return
        val selection = selectionMaskForRasterTools()
        val rasterSurface = rasterTileSurfaceFor(activeLayerId)
        if (selection != null && rasterSurface != null) {
            RasterSelectionContent.clear(rasterSurface, selection)?.let { edit ->
                val layerId = activeLayerId
                publishRasterSelectionEdit(layerId, rasterSurface, edit.dirtyBounds)
                recordExternalEdit(
                    undo = { rasterSurface.apply(edit.record, app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection.UNDO); publishRasterSelectionEdit(layerId, rasterSurface, edit.dirtyBounds) },
                    redo = { rasterSurface.apply(edit.record, app.inkloom.engine.v5.surface.PixelTileSurface.HistoryDirection.REDO); publishRasterSelectionEdit(layerId, rasterSurface, edit.dirtyBounds) },
                    dispose = edit.record::close,
                )
                return
            }
        }
        val before = strokes.toList()
        val ids = selectedStrokeIds.toSet()
        val removed = strokes.filter { it.id in ids }
        if (removed.isNotEmpty()) {
            val retainedPieces = removed.flatMap { stroke ->
                if (selectedMaskRegions.size == 1) {
                    LassoGeometry.partition(selectedMaskRegions.single(), stroke).pieces.filterNot { it.selected }.map { it.stroke }
                } else emptyList()
            }
            val rebuilt = strokes.toMutableList()
            removed.sortedByDescending { rebuilt.indexOf(it) }.forEach { stroke ->
                val index = rebuilt.indexOf(stroke)
                if (index >= 0) {
                    rebuilt.removeAt(index)
                    val pieces = if (selectedMaskRegions.size == 1) {
                        LassoGeometry.partition(selectedMaskRegions.single(), stroke).pieces.filterNot { it.selected }.map { it.stroke }
                    } else emptyList()
                    rebuilt.addAll(index, pieces)
                }
            }
            strokes.clear()
            strokes.addAll(rebuilt)
            commitStrokeEdit(before, strokes)
            selectedStrokeIds.removeAll(removed.mapTo(mutableSetOf(), InkStroke::id))
            invalidateStrokeCache(removed, retainedPieces)
        }
        invalidate()
    }

    private fun selectedVectorFragments(): List<InkStroke> = strokes.asSequence()
        .filter { it.id in selectedStrokeIds }
        .flatMap { stroke ->
            if (selectedMaskRegions.size == 1) {
                LassoGeometry.partition(selectedMaskRegions.single(), stroke).pieces.asSequence().filter { it.selected }.map { it.stroke }
            } else sequenceOf(stroke)
        }
        .toList()

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
        @Volatile var rasterSelectionClipboard: RasterSelectionClipboard? = null
        const val REFERENCE_WIDTH = 1600f
        private const val SMALL_CURSOR_MARK_THRESHOLD_PX = 8f
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
        const val STYLUS_BUTTON_MASK = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        const val POINTER_BUTTON_MASK = MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD
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
