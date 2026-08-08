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
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewConfiguration
import app.inkloom.engine.v2.EngineV2Metrics
import app.inkloom.engine.v2.EngineV2LatencySnapshot
import app.inkloom.engine.v2.traceEngineV2
import app.inkloom.engine.v2.input.StrokeInputModeler
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeMaskDab
import app.inkloom.document.StrokeMaskRenderer
import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushColorJitter
import app.inkloom.document.BrushBlur
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
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspBrushGap
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CanvasGuideGestureMode
import app.inkloom.document.CanvasGuideState
import app.inkloom.document.CanvasGuides
import app.inkloom.document.CanvasSnapConstraint
import app.inkloom.document.GrainBehavior
import app.inkloom.document.GrainDynamics
import app.inkloom.document.LassoGeometry
import app.inkloom.document.LassoSelectionPolicy
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.toBlendMode
import app.inkloom.document.LaserTrail
import app.inkloom.document.PaperPattern
import app.inkloom.document.PressureCalibration
import app.inkloom.document.PageElement
import app.inkloom.document.PdfGotoLink
import app.inkloom.document.PdfGotoLinkNavigation
import app.inkloom.document.RenderedColorSampling
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
import app.inkloom.document.StrokeEditHistory
import app.inkloom.document.StrokeEdits
import app.inkloom.document.StrokeEraser
import app.inkloom.document.StrokeClipboardCodec
import app.inkloom.document.StrokeStampSampler
import app.inkloom.document.StrokeStabilization
import app.inkloom.document.StrokeTaper
import app.inkloom.document.StrokeTransform
import app.inkloom.document.StrokeTileIndex
import app.inkloom.document.StrokeTileKey
import app.inkloom.document.StylusInputClassifier
import app.inkloom.document.TwoFingerTapUndoGesture
import app.inkloom.document.TwoPointerRotationGesture
import app.inkloom.document.ThreeFingerTapRedoGesture
import app.inkloom.document.ViewportTransform
import app.inkloom.document.FloodFill
import app.inkloom.document.WaterEdge
import app.inkloom.document.WetMixDynamics
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

class InkCanvasView(context: Context) : View(context) {
    private val engineV2Metrics = EngineV2Metrics()
    private val strokeInputModeler = StrokeInputModeler()
    private val strokes = mutableListOf<InkStroke>()
    private val editHistory = StrokeEditHistory()
    private val currentPoints = mutableListOf<StrokePoint>()
    private var currentStrokeId = java.util.UUID.randomUUID().toString()
    private var activeStrokeTemplate: InkStroke? = null
    private var liveStrokeBitmap: Bitmap? = null
    private var liveStrokeCheckpoint = 0
    private var livePrimaryStampCheckpoint = 0
    private var liveDualStampCheckpoint = 0
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
    private var shapeFreeformLastMoveAt = 0L
    private var fillSourceBitmap: Bitmap? = null
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
    private val fillPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN) }
    private var smudgeSnapshot: Bitmap? = null
    private var smudgeCurrentColor = 0
    private var smudgeSegments = mutableListOf<Pair<Int, MutableList<StrokePoint>>>()
    private val smudgePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var movingSelection = false
    private var selectionMoved = false
    private var selectionTransformMode = TRANSFORM_NONE
    private var selectionTransformCenterX = 0f
    private var selectionTransformCenterY = 0f
    private var selectionTransformStartDistance = 0f
    private var selectionTransformStartAngle = 0f
    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val selectionHandleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(24, 119, 242) }
    private var lastX = 0f
    private var lastY = 0f
    private var navigationX = 0f
    private var navigationY = 0f
    private var navigationDownX = 0f
    private var navigationDownY = 0f
    private var navigationSpan = 0f
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
    private var configuredInputTool: EditorTool? = null
    private var configuredInputBrushId: String? = null
    private val tipCache = object : LinkedHashMap<String, Bitmap>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean {
            val remove = size > MAX_TIP_CACHE_ENTRIES
            if (remove) eldest.value.recycle()
            return remove
        }
    }
    private val pendingTipLoads = mutableSetOf<String>()
    private var prewarmedBrushSignature: String? = null
    private val protectedStrokeIds = mutableSetOf<String>()
    private val protectedLayerIds = mutableSetOf<String>()
    private val strokeTileIndex = StrokeTileIndex()
    private val strokeTiles = mutableMapOf<StrokeTileKey, Bitmap>()
    private val dirtyStrokeTiles = linkedSetOf<StrokeTileKey>()
    internal var onTileRendered: ((StrokeTileKey, Int) -> Unit)? = null
    internal var onLivePreviewRendered: ((Int) -> Unit)? = null
    /** Test-only guard against re-allocating a page-sized preview surface per stroke. */
    internal var onLivePreviewSurfaceAllocated: (() -> Unit)? = null

    var tool: EditorTool = EditorTool.PEN
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
    var alphaLockedLayers: Set<String> = emptySet()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    var alphaLockBaseContentIds: Map<String, Set<String>> = emptyMap()
        set(value) { if (field != value) { field = value; resetStrokeTiles() } }
    var clippedLayers: Set<String> = emptySet()
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
    var grainAssetPath: String? = null
    var grainDensity: Float = 0f
    var grainDensityDynamics: BrushParameterDynamics = BrushParameterDynamics()
    var grainScale: Float = 100f
    var grainRotationDegrees: Float = 0f
    var grainBrightness: Float = 0f
    var grainContrast: Float = 0f
    var grainReversed: Boolean = false
    var grainEmphasized: Boolean = false
    var grainOffsetJitter: Boolean = false
    var grainCompositeMode: Int = CspTextureComposite.MULTIPLY
    var brushSpacing: Float = .12f
    var brushSpacingMode: Int = CspBrushGap.FIXED
    var brushFlow: Float = 1f
    var brushHardness: Float = 1f
    var brushAntiAliasMode: Int = CspAntiAlias.MEDIUM
    var brushBlur: Float = 0f
    var brushBlurUnit: Int = 0
    var brushBlurLinkedToSize: Boolean = false
    var brushThickness: Float = 1f
    var brushThicknessVertical: Boolean = false
    var brushRotation: Float = 0f
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
    var colorJitterHueDynamics: BrushColorDynamics = BrushColorDynamics()
    var colorJitterSaturationDynamics: BrushColorDynamics = BrushColorDynamics()
    var colorJitterValueDynamics: BrushColorDynamics = BrushColorDynamics()
    var brushWatercolor: Boolean = false
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
    var dualThicknessDynamics: BrushParameterDynamics = BrushParameterDynamics()
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
    var pressureSensitivity: Float = 1f
    var pressureMinimumInput: Float = 0f
    var pressureMaximumInput: Float = 1f
    var stabilization: Float = .12f
    var stabilizationBySpeed: Boolean = false
    var stabilizationBySpeedMode: Int = StrokeStabilization.REDUCE_WHEN_FAST
    var primaryButtonAction: StylusButtonAction = StylusButtonAction.ERASER
    var secondaryButtonAction: StylusButtonAction = StylusButtonAction.LASSO
    var primaryButtonTrigger: ShortcutTrigger = ShortcutTrigger.HOLD
    var secondaryButtonTrigger: ShortcutTrigger = ShortcutTrigger.HOLD
    var shapeKind: ShapeKind = ShapeKind.RECTANGLE
    var selectionShape: SelectionShape = SelectionShape.FREEFORM
    var selectionMode: SelectionMode = SelectionMode.REPLACE
    var selectionFilter: SelectionFilter = SelectionFilter.ALL
    var pageModelWidth: Float = 1600f
    var pageModelHeight: Float = 2263f
    var onStrokeCommitted: (InkStroke) -> Unit = {}
    var onShapeCommitted: (PageElement.Shape) -> Unit = {}
    /** Paint-bucket fill: receives the fill mask bitmap (page-local pixels) plus the
     *  mask's bounding box in normalized page coordinates, the fill color and opacity.
     *  The host persists the mask and creates a [PageElement.Fill]. */
    var onFillCommitted: ((mask: Bitmap, x: Float, y: Float, width: Float, height: Float, colorArgb: Long, opacity: Float) -> Unit)? = null
    var fillSensitivity: Float = .25f
    var symmetryMode: SymmetryMode = SymmetryMode.NONE
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
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            if (activeTool != null) cancelActiveGestureForNavigation()
            stopContinuousPlot()
            laserTrail.clear()
            rulerGestureStart?.let { rulerGuide = it }
            rulerGestureMode = null
            rulerGestureStart = null
            canvasGuideGestureStart?.let { canvasGuideState = it }
            canvasGuideGestureMode = null
            canvasGuideGestureStart = null
            activeCanvasSnapConstraint = null
            ctrlModifierHeld = false
            altModifierHeld = false
            releaseHeldStylusButtons()
            lastStylusButtonState = 0
            stylusCommandConsumesContact = false
            stylusHandNavigationContact = false
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

    fun setLayerElementBitmaps(value: Map<String, LayerElementBitmaps>) {
        if (layerElementBitmaps != value) {
            layerElementBitmaps = value
            resetStrokeTiles()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPageContent(canvas)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val toolType = event.getToolType(pointerIndex)
        val stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val cursorTool = if (toolType == MotionEvent.TOOL_TYPE_ERASER) EditorTool.ERASER else tool
        val drawableTool = cursorTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                if (!stylus || !drawableTool) {
                    if (hoverCursorVisible) { hoverCursorVisible = false; invalidate() }
                    return stylus
                }
                val point = canvasPoint(event, pointerIndex)
                hoverCursorX = point.first
                hoverCursorY = point.second
                hoverCursorUsesEraser = cursorTool == EditorTool.ERASER
                hoverCursorVisible = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_CANCEL -> {
                if (hoverCursorVisible) { hoverCursorVisible = false; invalidate() }
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

    private fun drawPageContent(canvas: Canvas, includeInteractionOverlays: Boolean = true) {
        backgroundBitmap?.let { bitmap -> backgroundDestination.set(0, 0, width, height); canvas.drawBitmap(bitmap, null, backgroundDestination, null) }
            ?: pagePaperColor?.let { color ->
                paperFillPaint.color = color
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paperFillPaint)
                pagePaperPattern?.let { drawPaperPattern(canvas, it) }
            }
        if (includeInteractionOverlays) drawSnapGrid(canvas)
        if (!drawStrokeTiles(canvas)) drawLayeredStrokes(canvas, strokes, includeElements = true)
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
            lassoPath.reset()
            lassoPath.moveTo(selectionPreview.first().x * width, selectionPreview.first().y * height)
            for (index in 1 until selectionPreview.size) lassoPath.lineTo(selectionPreview[index].x * width, selectionPreview[index].y * height)
            if (selectionShape == SelectionShape.RECTANGLE) lassoPath.close()
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(98, 98, 98); paint.alpha = 220; paint.strokeWidth = 2f
            canvas.drawPath(lassoPath, paint)
        }
        drawSelectionHandles(canvas)
        drawSymmetryGuides(canvas)
        drawShapePreview(canvas)
        drawLiveStroke(canvas)
        drawAlignmentGuides(canvas)
        drawRulerGuide(canvas)
        drawLaserTrail(canvas, SystemClock.uptimeMillis())
        drawFillPreview(canvas)
        drawSmudgePreview(canvas)
        drawBrushSizePreview(canvas)
        drawStylusHoverCursor(canvas)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) {
            releaseLiveStrokePreview()
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
        releaseLiveStrokePreview()
        tipCache.values.forEach(Bitmap::recycle); tipCache.clear()
        super.onDetachedFromWindow()
    }

    private fun drawStrokeTiles(canvas: Canvas): Boolean {
        if (layerBlendModes.values.any { it != LayerBlendMode.NORMAL }) return false
        if (alphaLockedLayers.isNotEmpty() || clippedLayers.isNotEmpty()) return false
        if (layerElementBitmaps.isNotEmpty()) return false
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
            val affectedTiles = strokeTileIndex.update(before, after)
            if (before.isEmpty() && after.isNotEmpty() && canAppendCommittedInkToTiles(affectedTiles)) {
                affectedTiles.forEach { key ->
                    val rect = strokeTileIndex.rect(key)
                    val tile = strokeTiles.getValue(key)
                    val tileCanvas = Canvas(tile)
                    tileCanvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
                    drawLayeredStrokes(tileCanvas, after.filter { key in strokeTileIndex.tilesFor(it) })
                    dirtyStrokeTiles.remove(key)
                }
            } else {
                dirtyStrokeTiles += affectedTiles
            }
            invalidate()
        }
    }

    /** Normal, fully opaque layers are associative, so newly committed ink can be composed once
     * into clean tiles instead of replaying every earlier stroke before the next pen contact. */
    private fun canAppendCommittedInkToTiles(affectedTiles: Set<StrokeTileKey>): Boolean =
        affectedTiles.isNotEmpty() &&
            layerBlendModes.values.all { it == LayerBlendMode.NORMAL } &&
            layerOpacities.values.all { it >= .999f } &&
            alphaLockedLayers.isEmpty() && clippedLayers.isEmpty() && layerElementBitmaps.isEmpty() &&
            affectedTiles.none { it in dirtyStrokeTiles } && affectedTiles.all(strokeTiles::containsKey)

    private fun drawLayeredStrokes(
        canvas: Canvas,
        value: List<InkStroke>,
        primaryStampStartIndex: Int = 0,
        dualStampStartIndex: Int = 0,
        includeElements: Boolean = false,
    ) {
        val groupedStrokes = value.groupBy(InkStroke::layerId)
        val availableLayerIds = groupedStrokes.keys + if (includeElements) layerElementBitmaps.keys else emptySet()
        val orderedLayerIds = (layerOrder.filter { it in availableLayerIds } + availableLayerIds.filterNot { it in layerOrder }).distinct()
        var previousLayerId: String? = null
        orderedLayerIds.forEach { layerId ->
            val layerStrokes = groupedStrokes[layerId].orEmpty()
            val opacity = (layerOpacities[layerId] ?: 1f).coerceIn(0f, 1f)
            if (opacity <= 0f) return@forEach
            val layerBlendMode = layerBlendModes[layerId] ?: LayerBlendMode.NORMAL
            val clipped = layerId in clippedLayers
            val lowerLayerId = previousLayerId
            if (!clipped || lowerLayerId != null) {
                val checkpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (opacity * 255f).toInt().coerceIn(0, 255)
                    this.blendMode = layerBlendMode.toBlendMode()
                })
                drawRawLayerContent(canvas, layerId, layerStrokes, includeElements, primaryStampStartIndex, dualStampStartIndex)
                if (clipped && lowerLayerId != null) {
                    val maskCheckpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
                    drawRawLayerContent(canvas, lowerLayerId, groupedStrokes[lowerLayerId].orEmpty(), includeElements, primaryStampStartIndex, dualStampStartIndex)
                    canvas.restoreToCount(maskCheckpoint)
                }
                canvas.restoreToCount(checkpoint)
            }
            previousLayerId = layerId
        }
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
        val baseIds = alphaLockBaseContentIds[layerId].orEmpty()
        val hasCapturedAlpha = layerId in alphaLockedLayers && baseIds.isNotEmpty()
        if (!hasCapturedAlpha) {
            layerStrokes.forEach { drawStroke(canvas, it, primaryStampStartIndex, dualStampStartIndex) }
            drawElementBitmap(canvas, elements?.base)
            drawElementBitmap(canvas, elements?.additions)
            return
        }
        val baseStrokes = layerStrokes.filter { it.id in baseIds }
        val addedStrokes = layerStrokes.filterNot { it.id in baseIds }
        fun drawBase() {
            baseStrokes.forEach { drawStroke(canvas, it, primaryStampStartIndex, dualStampStartIndex) }
            drawElementBitmap(canvas, elements?.base)
        }
        drawBase()
        if (addedStrokes.isNotEmpty() || elements?.additions != null) {
            val additionsCheckpoint = canvas.saveLayer(null, null)
            addedStrokes.forEach { drawStroke(canvas, it, primaryStampStartIndex, dualStampStartIndex) }
            drawElementBitmap(canvas, elements?.additions)
            val maskCheckpoint = canvas.saveLayer(null, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = android.graphics.BlendMode.DST_IN })
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

    /**
     * Keeps long in-progress strokes responsive. The stable prefix is rasterized only at coarse
     * checkpoints; each frame then renders the small live tail instead of replaying every stamp.
     * The committed stroke is still rendered once from its complete vector data.
     */
    private fun drawLiveStroke(canvas: Canvas) {
        if (currentPoints.isEmpty()) return
        val startedAt = System.nanoTime()
        try {
            traceEngineV2("Inkloom.V2.ActiveStrokeDraw") { drawLiveStrokeV2(canvas) }
        } finally {
            engineV2Metrics.activeStrokeDraw.recordNanos(System.nanoTime() - startedAt)
        }
    }

    private fun drawLiveStrokeV2(canvas: Canvas) {
        if (currentPoints.isEmpty()) return
        if (symmetryMode != SymmetryMode.NONE) {
            drawLayeredStrokes(canvas, symmetryStrokes(currentPoints))
            onLivePreviewRendered?.invoke(currentPoints.size)
            return
        }
        if (currentPoints.size < LIVE_STROKE_CACHE_START_POINTS || width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_CACHE_PIXELS) {
            drawLayeredStrokes(canvas, listOf(makeStroke(currentPoints)))
            onLivePreviewRendered?.invoke(currentPoints.size)
            return
        }
        val liveStroke = makeStroke(currentPoints)
        val liveTipPaths = liveStroke.tipAssetPaths.ifEmpty { listOfNotNull(liveStroke.tipAssetPath) }
        if (liveTipPaths.isEmpty()) {
            // A vector pen is cheap to replay and drawing it directly is pixel-identical to the
            // committed stroke. Rasterizing its prefix introduces a second alpha-composition pass
            // that can make the curve subtly "settle" when the pen is lifted.
            drawLayeredStrokes(canvas, listOf(liveStroke))
            onLivePreviewRendered?.invoke(currentPoints.size)
            return
        }
        if (liveStroke.taperEndEnabled) {
            drawLayeredStrokes(canvas, listOf(liveStroke))
            onLivePreviewRendered?.invoke(currentPoints.size)
            return
        }
        val desiredCheckpoint = ((currentPoints.size - LIVE_STROKE_TAIL_POINTS) / LIVE_STROKE_CHECKPOINT_POINTS) * LIVE_STROKE_CHECKPOINT_POINTS
        if (desiredCheckpoint <= 0) {
            drawLayeredStrokes(canvas, listOf(makeStroke(currentPoints)))
            onLivePreviewRendered?.invoke(currentPoints.size)
            return
        }
        if (desiredCheckpoint > liveStrokeCheckpoint || liveStrokeBitmap?.width != width || liveStrokeBitmap?.height != height) {
            val bitmap = liveStrokeBitmap?.takeIf { it.width == width && it.height == height } ?: runCatching {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }.getOrNull()
            if (bitmap == null) {
                drawLayeredStrokes(canvas, listOf(makeStroke(currentPoints)))
                onLivePreviewRendered?.invoke(currentPoints.size)
                return
            }
            if (bitmap !== liveStrokeBitmap) {
                liveStrokeBitmap?.recycle()
                liveStrokeBitmap = bitmap
                liveStrokeCheckpoint = 0
                livePrimaryStampCheckpoint = 0
                liveDualStampCheckpoint = 0
                onLivePreviewSurfaceAllocated?.invoke()
            }
            val nextCheckpoint = desiredCheckpoint.coerceAtLeast(1)
            val checkpointStroke = liveStroke.copy(points = currentPoints.subList(0, nextCheckpoint).toList())
            val canAppend = liveStrokeCheckpoint > 0 && canAppendLiveCheckpoint(checkpointStroke)
            if (!canAppend) {
                bitmap.eraseColor(Color.TRANSPARENT)
                livePrimaryStampCheckpoint = 0
                liveDualStampCheckpoint = 0
            }
            drawLayeredStrokes(
                Canvas(bitmap),
                listOf(checkpointStroke),
                primaryStampStartIndex = if (canAppend) livePrimaryStampCheckpoint else 0,
                dualStampStartIndex = if (canAppend) liveDualStampCheckpoint else 0,
            )
            liveStrokeCheckpoint = nextCheckpoint
            livePrimaryStampCheckpoint = primaryStampedPoints(checkpointStroke).size
            liveDualStampCheckpoint = if (checkpointStroke.dualBrush) dualStampedPoints(checkpointStroke).size else 0
        }
        liveStrokeBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        val prefix = liveStroke.copy(points = currentPoints.take(liveStrokeCheckpoint))
        val primaryStart = primaryStampedPoints(prefix).size
        val dualStart = if (liveStroke.dualBrush) dualStampedPoints(prefix).size else 0
        drawLayeredStrokes(canvas, listOf(liveStroke), primaryStart, dualStart)
        onLivePreviewRendered?.invoke((currentPoints.size - liveStrokeCheckpoint).coerceAtLeast(1))
    }

    /** Starts a fresh preview without allocating or releasing a page-sized bitmap. */
    private fun beginLiveStrokePreview() {
        liveStrokeBitmap?.eraseColor(Color.TRANSPARENT)
        liveStrokeCheckpoint = 0
        livePrimaryStampCheckpoint = 0
        liveDualStampCheckpoint = 0
    }

    /** Hides the completed preview. Its backing surface is retained for the next stroke. */
    private fun finishLiveStrokePreview() {
        liveStrokeCheckpoint = 0
        livePrimaryStampCheckpoint = 0
        liveDualStampCheckpoint = 0
        activeStrokeTemplate = null
    }

    private fun releaseLiveStrokePreview() {
        liveStrokeBitmap?.recycle()
        liveStrokeBitmap = null
        liveStrokeCheckpoint = 0
        livePrimaryStampCheckpoint = 0
        liveDualStampCheckpoint = 0
        activeStrokeTemplate = null
    }

    /** Incremental prefix composition is exact for ordinary normal-blend strokes without tapers.
     * More complex blend/taper modes keep the conservative full-prefix redraw path. */
    private fun canAppendLiveCheckpoint(stroke: InkStroke): Boolean =
        !stroke.taperStartEnabled && !stroke.taperEndEnabled &&
            !stroke.blendTipsWithDarken && !stroke.dualBrush &&
            layerBlendModes[stroke.layerId].let { it == null || it == LayerBlendMode.NORMAL } &&
            (layerOpacities[stroke.layerId] ?: 1f) >= .999f &&
            stroke.layerId !in alphaLockedLayers && stroke.layerId !in clippedLayers

    private fun drawStroke(canvas: Canvas, stroke: InkStroke, primaryStampStartIndex: Int = 0, dualStampStartIndex: Int = 0) {
        if (stroke.masks.isEmpty()) {
            drawStrokeContent(canvas, stroke, primaryStampStartIndex, dualStampStartIndex)
            resetAntiAliasPaint()
            return
        }
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        drawStrokeContent(canvas, stroke, primaryStampStartIndex, dualStampStartIndex)
        StrokeMaskRenderer.draw(canvas, paint, stroke.masks, width, height)
        canvas.restoreToCount(checkpoint)
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
        paint.isAntiAlias = CspAntiAlias.enabled(stroke.antiAliasMode)
        paint.isFilterBitmap = CspAntiAlias.enabled(stroke.antiAliasMode)
        val tipPaths = stroke.tipAssetPaths.ifEmpty { listOfNotNull(stroke.tipAssetPath) }
        val grainOffset = GrainBehavior.offset(stroke.renderSeed, stroke.grainOffsetJitter)
        val tips = tipPaths.mapNotNull { loadTip(it, stroke.hardness, stroke.grainAssetPath, stroke.grainDensity, stroke.grainScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, grainOffset.x, grainOffset.y, stroke.grainCompositeMode, stroke.antiAliasMode) }
        if (tips.isNotEmpty()) {
            val dualTips = if (stroke.dualBrush) stroke.dualTipAssetPaths.mapNotNull { loadTip(it, stroke.dualHardness, null, 0f, antiAliasMode = stroke.dualAntiAliasMode) }.ifEmpty { tipPaths.mapNotNull { loadTip(it, stroke.dualHardness, null, 0f, antiAliasMode = stroke.dualAntiAliasMode) } } else emptyList()
            drawStampedStroke(canvas, stroke, tips, dualTips, tipPaths, grainOffset.x, grainOffset.y, primaryStampStartIndex, dualStampStartIndex)
            return
        }
        paint.color = stroke.colorArgb.toInt()
        paint.colorFilter = null
        val scale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val taper = StrokeTaper.factors(stroke.points, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        if (stroke.points.size == 1) {
            val point = stroke.points.first(); val strokeWidth = stroke.width * scale * taper.firstOrNull().orDefault() * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, point.pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE); val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, point.pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, point.pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * WetMixDynamics.alphaMultiplier(stroke, point) * 255).toInt().coerceIn(1, 255); drawLinePoint(canvas, stroke, point, point.x * width, point.y * height, strokeWidth, alpha)
        } else for (index in 0 until stroke.points.lastIndex) {
            val start = stroke.points[index]
            val end = stroke.points[index + 1]
            val point = midpoint(start, end); val pressure = point.pressure.coerceIn(0f, 1f)
            val taperFactor = (taper[index] + taper[index + 1]) / 2f
            val strokeWidth = stroke.width * scale * taperFactor * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)
            val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * WetMixDynamics.alphaMultiplier(stroke, point) * 255).toInt().coerceIn(1, 255)
            drawLineSegment(canvas, stroke, point, start.x * width, start.y * height, end.x * width, end.y * height, strokeWidth, alpha)
        }
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
        // A CSP Dual Brush combines its two stamp streams first, then deposits
        // that single result. Isolating the stroke prevents the secondary mode
        // from accidentally blending with older strokes or the page itself.
        val isolateStroke = stroke.blendTipsWithDarken || stroke.dualBrush && dualTips.isNotEmpty()
        val compositeLayer = if (isolateStroke) canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null) else -1
        paint.isFilterBitmap = CspAntiAlias.enabled(stroke.antiAliasMode)
        paint.blendMode = if (stroke.blendTipsWithDarken) BlendMode.DARKEN else null
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val scatterRandom = java.util.Random(stroke.renderSeed.toLong())
        val rotationRandom = java.util.Random(stroke.renderSeed.toLong() xor 0x49A2B31DL)
        val sampledPoints = primaryStampedPoints(stroke)
        val safeStart = stampStartIndex.coerceIn(0, sampledPoints.size)
        advanceStampRandoms(scatterRandom, rotationRandom, stroke, sampledPoints, safeStart, dual = false)
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        sampledPoints.forEachIndexed { index, point ->
            if (index < safeStart) return@forEachIndexed
            val stampsAtPoint = BrushStampCount.resolve(stroke.stampCount, stroke.stampCountJitter, stroke.renderSeed, index)
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.tipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed, stroke.ribbon) ?: return@stamp
            val tip = if (GrainDynamics.isDynamic(stroke) && stroke.grainAssetPath != null) {
                tipPaths.getOrNull(tipIndex)?.let { path ->
                    loadTip(path, stroke.hardness, stroke.grainAssetPath, GrainDynamics.density(stroke, point), stroke.grainScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, grainOffsetX, grainOffsetY, stroke.grainCompositeMode, stroke.antiAliasMode)
                } ?: tips[tipIndex]
            } else tips[tipIndex]
            val x = point.x * width; val y = point.y * height
            val pressure = point.pressure.coerceIn(0f, 1f)
            val brushDiameter = (stroke.width * referenceScale * taper[index] * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(.25f)
            val diameter = if (stroke.spray) SprayGeometry.particleDiameter(brushDiameter, SprayDynamics.particleSize(stroke, point), stroke.sprayParticleSizeUnit, stroke.sprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
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
            val matrix = primaryStampMatrix.apply {
                reset()
                postTranslate(-tip.width / 2f, -tip.height / 2f)
                postScale(scaleX, scaleY)
                postRotate(angle)
                postTranslate(drawX, drawY)
            }
            val jitteredColor = BrushColorJitter.argb(stroke, point, sequenceIndex)
            paint.colorFilter = srcInColorFilter(jitteredColor)
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW)
            val mainAlpha = (stroke.opacity * opacityDynamic * stroke.flow * flowDynamic * WetMixDynamics.alphaMultiplier(stroke, point)).coerceIn(.01f, 1f).times(255).toInt()
            if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
                val edgeRadius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat())
                val edgeTipScale = BrushTipTransform.scale(tip.width, tip.height, diameter + edgeRadius * 2f, thicknessScale, stroke.thicknessVertical)
                val edgeMatrix = edgeStampMatrix.apply { reset(); postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(edgeTipScale.x * flip.x, edgeTipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
                paint.colorFilter = srcInColorFilter(WaterEdge.darkenArgb(jitteredColor, stroke.waterEdgeDarkness))
                paint.alpha = (mainAlpha * stroke.waterEdgeOpacity.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
                val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat())
                paint.maskFilter = cachedBlurMask(blur)
                canvas.drawBitmap(tip, edgeMatrix, paint)
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
        if (stroke.dualBrush && dualTips.isNotEmpty()) {
            paint.isFilterBitmap = CspAntiAlias.enabled(stroke.dualAntiAliasMode)
            drawDualStampedStroke(canvas, stroke, dualTips, dualStampStartIndex)
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
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        return StrokeStampSampler.sample(
            stroke.points,
            width.toFloat(),
            height.toFloat(),
            includeStationaryPoints = stroke.continuousPlot,
        ) { point ->
            val pressure = point.pressure.coerceIn(0f, 1f)
            val diameter = (stroke.width * referenceScale * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(1f)
            val spacingDynamic = pressureDynamic(stroke.spacingPressureEnabled, stroke.spacingPressureMinimum, stroke.spacingPressureCurve, pressure) * secondaryDynamic(stroke.spacingSecondaryDynamics, point, stroke.renderSeed, SALT_SPACING)
            val gapRatio = CspBrushGap.ratio(stroke.spacingMode, stroke.spacing).let { if (stroke.ribbon) it.coerceAtMost(.06f) else it }
            diameter * gapRatio * spacingDynamic.coerceIn(.01f, 1f) / if (stroke.spray) SprayGeometry.particlesPerPlot(SprayDynamics.density(stroke, point)) else 1
        }
    }

    private fun dualStampedPoints(stroke: InkStroke): List<StrokePoint> {
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        return StrokeStampSampler.sample(
            stroke.points,
            width.toFloat(),
            height.toFloat(),
            includeStationaryPoints = stroke.continuousPlot,
        ) { point ->
            val pressure = point.pressure.coerceIn(0f, 1f)
            val diameter = (stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f) * pressureDynamic(stroke.dualSizeDynamics.pressureEnabled, stroke.dualSizeDynamics.pressureMinimum, stroke.dualSizeDynamics.pressureCurve, pressure) * secondaryDynamic(stroke.dualSizeDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_SIZE)).coerceAtLeast(1f)
            val spacingDynamic = pressureDynamic(stroke.dualSpacingDynamics.pressureEnabled, stroke.dualSpacingDynamics.pressureMinimum, stroke.dualSpacingDynamics.pressureCurve, pressure) * secondaryDynamic(stroke.dualSpacingDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_SPACING)
            val gapRatio = CspBrushGap.ratio(stroke.dualSpacingMode, stroke.dualSpacing).let { if (stroke.dualRibbon) it.coerceAtMost(.06f) else it }
            diameter * gapRatio * spacingDynamic.coerceIn(.01f, 1f) / if (stroke.dualSpray) SprayGeometry.particlesPerPlot(SprayDynamics.dualDensity(stroke, point)) else 1
        }
    }

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

    /** Draws the secondary stream into the isolated per-stroke composition layer. */
    private fun drawDualStampedStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>, stampStartIndex: Int = 0) {
        val compositePaint = Paint().apply { blendMode = CspDualBrushBlendMode.resolve(stroke.dualCompositeMode) }
        val dualLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), compositePaint)
        paint.blendMode = if (stroke.dualBlendTipsWithDarken) BlendMode.DARKEN else null
        val referenceScale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val scatterRandom = java.util.Random(stroke.renderSeed.toLong() xor DUAL_SCATTER_SEED)
        val rotationRandom = java.util.Random(stroke.renderSeed.toLong() xor DUAL_ROTATION_SEED)
        val sampledPoints = dualStampedPoints(stroke)
        val safeStart = stampStartIndex.coerceIn(0, sampledPoints.size)
        advanceStampRandoms(scatterRandom, rotationRandom, stroke, sampledPoints, safeStart, dual = true)
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        sampledPoints.forEachIndexed { index, point ->
            if (index < safeStart) return@forEachIndexed
            val stampsAtPoint = BrushStampCount.resolve(stroke.dualStampCount, stroke.dualStampCountJitter, stroke.renderSeed, index, dual = true)
            repeat(stampsAtPoint) stamp@ { repeatIndex ->
            val sequenceIndex = index * 16 + repeatIndex
            val tipIndex = BrushTipSequence.index(stroke.dualTipSequenceMode, sequenceIndex, tips.size, stroke.renderSeed xor DUAL_SEQUENCE_SEED, stroke.dualRibbon) ?: return@stamp
            val tip = tips[tipIndex]
            val pressure = point.pressure.coerceIn(0f, 1f)
            val brushDiameter = (stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f) * taper[index] * pressureDynamic(stroke.dualSizeDynamics.pressureEnabled, stroke.dualSizeDynamics.pressureMinimum, stroke.dualSizeDynamics.pressureCurve, pressure) * secondaryDynamic(stroke.dualSizeDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_SIZE)).coerceAtLeast(.25f)
            val diameter = if (stroke.dualSpray) SprayGeometry.particleDiameter(brushDiameter, SprayDynamics.dualParticleSize(stroke, point), stroke.dualSprayParticleSizeUnit, stroke.dualSprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val offset = if (stroke.dualSpray) SprayGeometry.offset(brushDiameter / 2f * stroke.dualSpraySpread.coerceIn(0f, 4f), scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.dualSprayBias) else app.inkloom.document.SprayOffset(0f, 0f)
            val drawX = point.x * width + offset.x
            val drawY = point.y * height + offset.y
            val dynamicThickness = pressureDynamic(stroke.dualThicknessDynamics.pressureEnabled, stroke.dualThicknessDynamics.pressureMinimum, stroke.dualThicknessDynamics.pressureCurve, pressure) * secondaryDynamic(stroke.dualThicknessDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_THICKNESS)
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
            val matrix = secondaryStampMatrix.apply { reset(); postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(tipScale.x * flip.x, tipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.dualFlowDynamics.pressureEnabled, stroke.dualFlowDynamics.pressureMinimum, stroke.dualFlowDynamics.pressureCurve, pressure) * secondaryDynamic(stroke.dualFlowDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_FLOW)
            paint.colorFilter = srcInColorFilter(BrushColorJitter.argb(stroke, point, sequenceIndex))
            paint.alpha = (stroke.opacity * opacityDynamic * stroke.dualFlow.coerceIn(0f, 1f) * flowDynamic * WetMixDynamics.alphaMultiplier(stroke, point)).coerceIn(.01f, 1f).times(255).toInt()
            paint.maskFilter = brushBlurMask(stroke, point, diameter, stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f), width.toFloat())
            canvas.drawBitmap(tip, matrix, paint)
            paint.maskFilter = null
            }
        }
        paint.blendMode = null
        canvas.restoreToCount(dualLayer)
    }

    private fun drawLinePoint(canvas: Canvas, stroke: InkStroke, point: StrokePoint, x: Float, y: Float, strokeWidth: Float, alpha: Int) {
        val jitteredColor = BrushColorJitter.argb(stroke, point, point.timeMs.toInt())
        if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
            configureWaterEdge(stroke, strokeWidth, alpha, jitteredColor)
            canvas.drawPoint(x, y, paint)
        }
        paint.maskFilter = brushBlurMask(stroke, point, strokeWidth, stroke.width * width.coerceAtLeast(1) / REFERENCE_WIDTH, width.toFloat()); paint.color = jitteredColor; paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawPoint(x, y, paint)
        paint.maskFilter = null
    }

    private fun drawLineSegment(canvas: Canvas, stroke: InkStroke, point: StrokePoint, startX: Float, startY: Float, endX: Float, endY: Float, strokeWidth: Float, alpha: Int) {
        val jitteredColor = BrushColorJitter.argb(stroke, point, point.timeMs.toInt())
        if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
            configureWaterEdge(stroke, strokeWidth, alpha, jitteredColor)
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
        paint.maskFilter = brushBlurMask(stroke, point, strokeWidth, stroke.width * width.coerceAtLeast(1) / REFERENCE_WIDTH, width.toFloat()); paint.color = jitteredColor; paint.strokeWidth = strokeWidth; paint.alpha = alpha
        canvas.drawLine(startX, startY, endX, endY, paint)
        paint.maskFilter = null
    }

    private fun brushBlurMask(stroke: InkStroke, point: StrokePoint, diameter: Float, baseDiameter: Float, pageWidth: Float): BlurMaskFilter? {
        val dynamic = pressureDynamic(stroke.blurPressureEnabled, stroke.blurPressureMinimum, stroke.blurPressureCurve, point.pressure) *
            secondaryDynamic(stroke.blurSecondaryDynamics, point, stroke.renderSeed, SALT_BLUR)
        val radius = BrushBlur.radiusPixels(stroke.blur, stroke.blurUnit, stroke.blurLinkedToSize, diameter, baseDiameter, pageWidth) * dynamic
        return cachedBlurMask(radius)
    }

    private fun configureWaterEdge(stroke: InkStroke, strokeWidth: Float, alpha: Int, color: Int) {
        val radius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat())
        paint.color = WaterEdge.darkenArgb(color, stroke.waterEdgeDarkness)
        paint.strokeWidth = strokeWidth + radius * 2f
        paint.alpha = (alpha * stroke.waterEdgeOpacity.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
        val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat())
        paint.maskFilter = cachedBlurMask(blur)
    }

    private fun srcInColorFilter(color: Int): PorterDuffColorFilter =
        srcInColorFilters[color] ?: PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN).also { srcInColorFilters[color] = it }

    /** BlurMaskFilter construction is native-backed and expensive. Quarter-pixel buckets are
     * visually indistinguishable at tablet density while turning per-dab allocation into reuse. */
    private fun cachedBlurMask(radius: Float): BlurMaskFilter? {
        if (!radius.isFinite() || radius <= .01f) return null
        val bucket = (radius.coerceAtMost(MAX_CACHED_BLUR_RADIUS_PX) * 4f).toInt().coerceAtLeast(1)
        return blurMaskFilters[bucket] ?: BlurMaskFilter(bucket / 4f, BlurMaskFilter.Blur.NORMAL).also { blurMaskFilters[bucket] = it }
    }

    private fun loadTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false, grainOffsetX: Float = 0f, grainOffsetY: Float = 0f, grainCompositeMode: Int = CspTextureComposite.MULTIPLY, antiAliasMode: Int = CspAntiAlias.MEDIUM): Bitmap? {
        val normalizedAntiAlias = CspAntiAlias.normalize(antiAliasMode)
        val key = tipCacheKey(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, normalizedAntiAlias)
        tipCache[key]?.let { return it }
        if (key in pendingTipLoads) return null
        // Dynamic grain can request many density buckets while a stroke is moving. Disk/bitmap
        // decoding those variants on this path blocks MotionEvent delivery, so schedule them and
        // let the caller use the already decoded base tip for this frame. A missing base tip may
        // still decode once (normally it was prewarmed on selection); otherwise the first stroke
        // would visibly switch from a fallback line to its real shape on pen-up.
        val isDynamicVariant = grainPath != null && (
            kotlin.math.abs(grainDensity - this.grainDensity) >= .025f ||
                kotlin.math.abs(grainOffsetX) > .0001f || kotlin.math.abs(grainOffsetY) > .0001f
            )
        if (activeTool != null && isDynamicVariant) {
            prewarmTip(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, normalizedAntiAlias)
            return null
        }
        return BrushTipTextureDecoder.decode(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, normalizedAntiAlias)?.also { tipCache[key] = it }
    }

    private fun tipCacheKey(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float, grainRotation: Float, grainBrightness: Float, grainContrast: Float, grainReversed: Boolean, grainEmphasized: Boolean, grainOffsetX: Float, grainOffsetY: Float, grainCompositeMode: Int, antiAliasMode: Int): String =
        "$path:${(hardness.coerceIn(0f, 1f) * 20).toInt()}:$grainPath:${(grainDensity.coerceIn(0f, 1f) * 20).toInt()}:${grainScale.toInt()}:${grainRotation.toInt()}:${grainBrightness.toInt()}:${grainContrast.toInt()}:$grainReversed:$grainEmphasized:${grainOffsetX.toBits()}:${grainOffsetY.toBits()}:$grainCompositeMode:${CspAntiAlias.normalize(antiAliasMode)}"

    private fun prewarmTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false, grainOffsetX: Float = 0f, grainOffsetY: Float = 0f, grainCompositeMode: Int = CspTextureComposite.MULTIPLY, antiAliasMode: Int = CspAntiAlias.MEDIUM) {
        val normalizedAntiAlias = CspAntiAlias.normalize(antiAliasMode)
        val key = tipCacheKey(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, normalizedAntiAlias)
        if (tipCache.containsKey(key) || !pendingTipLoads.add(key)) return
        TIP_DECODE_EXECUTOR.execute {
            val decoded = BrushTipTextureDecoder.decode(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, normalizedAntiAlias)
            post {
                pendingTipLoads.remove(key)
                if (decoded != null && !decoded.isRecycled) tipCache[key] = decoded
                postInvalidateOnAnimation()
            }
        }
    }

    /** Decodes a newly selected imported tip before the first pen contact. */
    fun prewarmBrushAssets() {
        val primary = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }
        val secondary = if (brushDual) dualTipAssetPaths.ifEmpty { primary } else emptyList()
        if (primary.isEmpty() && secondary.isEmpty()) return
        val signature = "${primary.joinToString()}|${secondary.joinToString()}|$brushHardness|$dualHardness|$grainAssetPath|$grainDensity|$grainScale|$grainRotationDegrees|$grainBrightness|$grainContrast|$grainReversed|$grainEmphasized|$grainCompositeMode|$brushAntiAliasMode|$dualAntiAliasMode"
        if (signature == prewarmedBrushSignature) return
        prewarmedBrushSignature = signature
        primary.forEach { prewarmTip(it, brushHardness, grainAssetPath, grainDensity, grainScale, grainRotationDegrees, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainCompositeMode = grainCompositeMode, antiAliasMode = brushAntiAliasMode) }
        secondary.forEach { prewarmTip(it, dualHardness, null, 0f, antiAliasMode = dualAntiAliasMode) }
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
        if (tool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.EYEDROPPER, EditorTool.SELECT, EditorTool.SHAPE, EditorTool.LASER, EditorTool.FILL, EditorTool.SMUDGE)) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (hoverCursorVisible) { hoverCursorVisible = false; invalidate() }
            if (!hasFocus()) requestFocus()
        }
        val pointerIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val eventToolType = event.getToolType(pointerIndex)
        val isStylus = eventToolType == MotionEvent.TOOL_TYPE_STYLUS || eventToolType == MotionEvent.TOOL_TYPE_ERASER
        engineV2Metrics.inputDispatch.recordNanos(
            (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0L) * 1_000_000L,
        )
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Every new contact stream is a hard recovery boundary. This also recovers from a
            // device/parent that consumed the previous stream's final UP or CANCEL.
            viewportGestureActive = false
            externalViewportGestureContact = false
            pageContactRejected = false
            if (isStylus && tool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE)) {
                requestUnbufferedDispatch(event)
            }
        }
        if (isStylus) singleFingerDoubleTapUndoGesture.cancel()
        if (isStylus) lastStylusSeenAt = event.eventTime
        onInputDiagnostic(when (eventToolType) { MotionEvent.TOOL_TYPE_STYLUS -> "Stylus"; MotionEvent.TOOL_TYPE_ERASER -> "Eraser"; MotionEvent.TOOL_TYPE_FINGER -> "Finger"; else -> "Other" }, event.pressure, event.getAxisValue(MotionEvent.AXIS_TILT), event.buttonState)
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
            viewportGestureActive = true
            // Canvas navigation always outranks one-finger ruler/guide manipulation.
            rulerGestureStart?.let { rulerGuide = it }
            rulerGestureMode = null
            rulerGestureStart = null
            canvasGuideGestureStart?.let { canvasGuideState = it }
            canvasGuideGestureMode = null
            canvasGuideGestureStart = null
        }
        if (!viewportGestureActive && handleCanvasGuideGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        if (!viewportGestureActive && handleRulerGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        val buttonForcesNavigation = isStylus && (stylusButtonAction == StylusButtonAction.HAND || stylusHandNavigationContact)
        val isFingerNavigation = viewportGestureActive || forceNavigation || buttonForcesNavigation || StylusInputClassifier.shouldNavigateOrRejectFinger(isStylus, fingerDrawingEnabled, event.pointerCount, event.getTouchMajor(pointerIndex), elapsedSinceStylus)
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
                    navigationMoved = true
                    navigationTapEligible = false
                    twoPointerRotationGesture.reset()
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                    val focus = event.centroid()
                    val span = event.localSpan()
                    if (navigationSpan > .01f && span > .01f) {
                        onViewportScale((span / navigationSpan).coerceIn(.5f, 2f), navigationX, navigationY)
                    }
                    val rotationDelta = if (twoFingerRotationEnabled && event.pointerCount == 2) {
                        twoPointerRotationGesture.update(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                    } else {
                        twoPointerRotationGesture.reset()
                        null
                    }
                    rotationDelta?.let(onViewportRotate)
                    if (event.pointerCount == 3) threeFingerTapRedoGesture.move(focus.first, focus.second, transforming = true)
                    else twoFingerTapUndoGesture.move(focus.first, focus.second, scaling = true)
                    onViewportPan(focus.first - navigationX, focus.second - navigationY)
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
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) pageContactRejected = false
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            (inputPoint.first !in 0f..width.toFloat() || inputPoint.second !in 0f..height.toFloat())
        ) {
            pageContactRejected = true
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) singleFingerDoubleTapUndoGesture.cancel()
        val normalizedX = (inputPoint.first / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val normalizedY = (inputPoint.second / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activeTool = resolveTool(event, eventToolType)
                activeInputWasStylus = isStylus
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
                    eraseAlong(normalizedX, normalizedY, event.pressure)
                    return true
                }
                if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                    return true
                }
                if (activeTool == EditorTool.SELECT) {
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
                            lastX = normalizedX; lastY = normalizedY
                            invalidate(); return true
                        }
                    }
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
                    beginFillGesture(normalizedX, normalizedY, event.x)
                    invalidate()
                    return true
                }
                if (activeTool == EditorTool.SMUDGE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    beginSmudgeGesture(normalizedX, normalizedY)
                    invalidate()
                    return true
                }
                clearActiveStrokeInput()
                beginLiveStrokePreview()
                currentStrokeId = java.util.UUID.randomUUID().toString()
                // Freeze the many brush-engine parameters once per contact. Live rendering then
                // copies only the changing point list instead of rebuilding the full descriptor
                // several times per display frame.
                activeStrokeTemplate = buildStroke(emptyList())
                activeCanvasSnapConstraint = if (!rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
                    CanvasGuides.constraintAt(canvasGuideState, normalizedX, normalizedY, pageModelWidth, pageModelHeight, width.toFloat(), height.toFloat(), GUIDE_SNAP_TOLERANCE_DP * resources.displayMetrics.density)
                } else null
                updateContinuousPlotInput(normalizedX, normalizedY, event.pressure, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                addPoint(normalizedX, normalizedY, event.pressure, event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                if (brushContinuousPlot && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) startContinuousPlot()
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTool == EditorTool.FILL) {
                    adjustFillSensitivity(event.x)
                }
                else if (activeTool == EditorTool.SMUDGE) {
                    for (i in 0 until event.historySize) historicalCanvasPoint(event, 0, i).let { point -> extendSmudge(point.first / width.coerceAtLeast(1), point.second / height.coerceAtLeast(1)) }
                    extendSmudge(normalizedX, normalizedY)
                }
                else if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                }
                else if (activeTool == EditorTool.ERASER) {
                    for (i in 0 until event.historySize) historicalCanvasPoint(event, 0, i).let { point -> eraseAlong(point.first / width.coerceAtLeast(1), point.second / height.coerceAtLeast(1), event.getHistoricalPressure(i)) }
                    eraseAlong(normalizedX, normalizedY, event.pressure)
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
                        if (selectionShape == SelectionShape.FREEFORM) {
                            for (i in 0 until event.historySize) historicalCanvasPoint(event, 0, i).let { point -> appendSelectionPoint(point.first / width.coerceAtLeast(1), point.second / height.coerceAtLeast(1)) }
                        }
                        appendSelectionPoint(normalizedX, normalizedY)
                    }
                }
                else if (activeTool == EditorTool.SHAPE) {
                    if (shapeKind == ShapeKind.FREEFORM) {
                        for (i in 0 until event.historySize) historicalCanvasPoint(event, 0, i).let { point -> appendShapePoint(point.first / width.coerceAtLeast(1), point.second / height.coerceAtLeast(1)) }
                        appendShapePoint(normalizedX, normalizedY)
                        shapeFreeformLastMoveAt = event.eventTime
                    } else {
                        val angled = ShapeGeometry.snapEnd(shapeStart ?: ShapePoint(normalizedX, normalizedY), ShapePoint(normalizedX, normalizedY), pageModelWidth, pageModelHeight)
                        val snapped = snapCanvasPoint(angled.x, angled.y)
                        shapeEnd = ShapePoint(snapped.x, snapped.y)
                    }
                }
                else if (activeTool == EditorTool.LASER) {
                    for (i in 0 until event.historySize) historicalCanvasPoint(event, 0, i).let { point -> laserTrail.append(
                        point.first / width.coerceAtLeast(1),
                        point.second / height.coerceAtLeast(1),
                        event.getHistoricalEventTime(i),
                    ) }
                    laserTrail.append(normalizedX, normalizedY, event.eventTime)
                }
                else {
                    for (i in 0 until event.historySize) historicalCanvasPoint(event, 0, i).let { point -> addPoint(point.first / width, point.second / height, event.getHistoricalPressure(i), event.getHistoricalEventTime(i), event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i), event.getHistoricalOrientation(i)) }
                    updateContinuousPlotInput(normalizedX, normalizedY, event.pressure, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                    addPoint(normalizedX, normalizedY, event.pressure, event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopContinuousPlot()
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    clearActiveStrokeInput()
                    finishLiveStrokePreview()
                    if (activeTool == EditorTool.LASER) laserTrail.cancelActive()
                    if (activeTool == EditorTool.FILL) cancelFillGesture()
                    if (activeTool == EditorTool.SMUDGE) cancelSmudgeGesture()
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear()
                    if (activeTool == EditorTool.ERASER) cancelEraserGesture()
                    selectionGestureBefore?.let { restoreStrokeSnapshot(it) }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null; selectionTransformMode = TRANSFORM_NONE
                } else if (activeTool == EditorTool.FILL) {
                    commitFillGesture()
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (activeTool == EditorTool.SMUDGE) {
                    commitSmudgeGesture()
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                } else if (activeTool == EditorTool.ERASER) {
                    eraseAlong(normalizedX, normalizedY, event.pressure)
                    finishEraserGesture()
                } else if (activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && currentPoints.isNotEmpty()) {
                    val before = strokes.toList()
                    val newStrokes = symmetryStrokes(currentPoints).map { it.copy(grainCompositeMode = grainCompositeMode) }
                    strokes.addAll(newStrokes)
                    invalidateStrokeCache(emptyList(), newStrokes)
                    commitStrokeEdit(before, strokes)
                    clearActiveStrokeInput()
                    finishLiveStrokePreview()
                } else if (activeTool == EditorTool.SELECT) {
                    if ((movingSelection || selectionTransformMode != TRANSFORM_NONE) && selectionMoved) {
                        selectionGestureBefore?.let { commitStrokeEdit(it, strokes) }
                    } else if (!movingSelection && selectionTransformMode == TRANSFORM_NONE) {
                        appendSelectionPoint(normalizedX, normalizedY)
                        selectWithLasso(selectionPolygon())
                    }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null; selectionTransformMode = TRANSFORM_NONE
                } else if (activeTool == EditorTool.SHAPE) {
                    val shape = if (shapeKind == ShapeKind.FREEFORM) {
                        appendShapePoint(normalizedX, normalizedY)
                        // QuickShape: when the freehand gesture ends with a brief hold,
                        // snap the rough path to a perfect line/rectangle/ellipse.
                        val held = shapeFreeformLastMoveAt > 0L && event.eventTime - shapeFreeformLastMoveAt >= QUICKSHAPE_HOLD_MS
                        val recognized = if (held) ShapeGeometry.quickShape(shapeFreeformPoints, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), baseWidth) else null
                        recognized ?: ShapeGeometry.createFreeform(shapeFreeformPoints, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), baseWidth)
                    } else {
                        val start = shapeStart
                        val end = shapeEnd
                        if (start != null && end != null) ShapeGeometry.create(shapeKind, start, end, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), baseWidth) else null
                    }
                    shape?.let(onShapeCommitted)
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear(); shapeFreeformLastMoveAt = 0L
                } else if (activeTool == EditorTool.LASER) {
                    laserTrail.append(normalizedX, normalizedY, event.eventTime)
                    laserTrail.end()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                activeCanvasSnapConstraint = null
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
        val mask = fillPreviewMask ?: return
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

    private fun drawStylusHoverCursor(canvas: Canvas) {
        if (!hoverCursorVisible || activeTool != null || brushSizeDragActive) return
        val configuredSize = if (hoverCursorUsesEraser) eraserWidth else baseWidth
        val zoom = viewportScale.coerceAtLeast(1f)
        val radius = (configuredSize.coerceIn(.1f, 800f) * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f)
            .coerceAtLeast(1.5f / zoom)
        hoverCursorPaint.strokeWidth = 3.5f / zoom
        hoverCursorPaint.color = Color.WHITE
        hoverCursorPaint.alpha = 235
        canvas.drawCircle(hoverCursorX, hoverCursorY, radius, hoverCursorPaint)
        hoverCursorPaint.strokeWidth = 1.5f / zoom
        hoverCursorPaint.color = Color.BLACK
        hoverCursorPaint.alpha = 235
        canvas.drawCircle(hoverCursorX, hoverCursorY, radius, hoverCursorPaint)
    }

    internal fun hoverCursorSnapshot(): FloatArray = floatArrayOf(
        if (hoverCursorVisible) 1f else 0f,
        hoverCursorX,
        hoverCursorY,
        (if (hoverCursorUsesEraser) eraserWidth else baseWidth) * width.coerceAtLeast(1) / REFERENCE_WIDTH,
    )

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

    private fun canvasPoint(event: MotionEvent, pointerIndex: Int = event.actionIndex.coerceIn(0, event.pointerCount - 1)): Pair<Float, Float> {
        return event.getX(pointerIndex) to event.getY(pointerIndex)
    }

    private fun historicalCanvasPoint(event: MotionEvent, pointerIndex: Int, position: Int): Pair<Float, Float> {
        return event.getHistoricalX(pointerIndex, position) to event.getHistoricalY(pointerIndex, position)
    }

    private fun cancelActiveGestureForNavigation() {
        stopContinuousPlot()
        clearActiveStrokeInput()
        finishLiveStrokePreview()
        laserTrail.cancelActive()
        cancelFillGesture()
        cancelSmudgeGesture()
        rulerGestureMode = null
        canvasGuideGestureMode = null
        activeCanvasSnapConstraint = null
        shapeStart = null
        shapeEnd = null
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

    private fun addPoint(x: Float, y: Float, pressure: Float, time: Long, tilt: Float, orientation: Float, force: Boolean = false) {
        val startedAt = System.nanoTime()
        traceEngineV2("Inkloom.V2.InputModel") {
            addModeledPointV2(x, y, pressure, time, tilt, orientation, force)
        }
        engineV2Metrics.inputModel.recordNanos(System.nanoTime() - startedAt)
    }

    private fun addModeledPointV2(x: Float, y: Float, pressure: Float, time: Long, tilt: Float, orientation: Float, force: Boolean) {
        val adjustedPressure = PressureCalibration.apply(pressure, pressureMinimumInput, pressureMaximumInput, pressureSensitivity)
        val constrained = if (rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
            RulerGuide.project(rulerGuide, x, y, width.toFloat(), height.toFloat())
        } else activeCanvasSnapConstraint?.apply(x, y)
        val rawX = (constrained?.x ?: x).coerceIn(0f, 1f)
        val rawY = (constrained?.y ?: y).coerceIn(0f, 1f)
        if (!strokeInputModeler.append(
                rawX = rawX,
                rawY = rawY,
                adjustedPressure = adjustedPressure,
                sampleTimeMs = time,
                sampleTilt = tilt,
                sampleOrientation = orientation,
                viewportWidthPx = width,
                viewportHeightPx = height,
                stabilization = stabilization,
                stabilizationBySpeed = stabilizationBySpeed,
                stabilizationBySpeedMode = stabilizationBySpeedMode,
                force = force,
            )
        ) return
        val point = StrokePoint(
            x = strokeInputModeler.x,
            y = strokeInputModeler.y,
            pressure = strokeInputModeler.pressure,
            timeMs = strokeInputModeler.timeMs,
            tilt = strokeInputModeler.tilt,
            orientation = strokeInputModeler.orientation,
            velocity = strokeInputModeler.velocity,
        )
        currentPoints += point
    }

    private fun clearActiveStrokeInput() {
        currentPoints.clear()
        strokeInputModeler.reset()
    }

    internal fun engineV2LatencySnapshot(): Array<EngineV2LatencySnapshot> = arrayOf(
        engineV2Metrics.inputDispatch.snapshot(),
        engineV2Metrics.inputModel.snapshot(),
        engineV2Metrics.activeStrokeDraw.snapshot(),
    )

    /** The full-screen proxy receives screen-space contacts. Drawing contacts are mapped into the
     * page once, while navigation remains in screen space so pan deltas do not shrink with zoom. */
    internal fun proxyContactUsesPageCoordinates(event: MotionEvent): Boolean {
        if (event.pointerCount != 1 || forceNavigation || viewportGestureActive) return false
        val type = event.getToolType(0)
        val stylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
        if (stylus) {
            return resolveStylusButtonAction(event.buttonState) != StylusButtonAction.HAND
        }
        val elapsedSinceStylus = if (lastStylusSeenAt == Long.MIN_VALUE) Long.MAX_VALUE else event.eventTime - lastStylusSeenAt
        return fingerDrawingEnabled && event.getTouchMajor(0) < StylusInputClassifier.PALM_TOUCH_MAJOR_PX &&
            elapsedSinceStylus !in 0..StylusInputClassifier.RECENT_STYLUS_WINDOW_MS
    }

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
        val source = fillSourceBitmap ?: return
        val tolerance = (fillSensitivityNow * 200f).toInt().coerceIn(0, 255)
        val now = SystemClock.uptimeMillis()
        if (!force && (tolerance == fillPreviewTolerance || now - fillPreviewComputedAt < FILL_PREVIEW_THROTTLE_MS)) return
        val result = FloodFill.fill(source, fillSeedX, fillSeedY, tolerance)
        fillPreviewMask?.recycle()
        fillPreviewMask = if (result.isEmpty) null else result.mask
        val safeWidth = width.coerceAtLeast(1).toFloat()
        val safeHeight = height.coerceAtLeast(1).toFloat()
        fillPreviewLeft = result.left / safeWidth
        fillPreviewTop = result.top / safeHeight
        fillPreviewWidth = result.width / safeWidth
        fillPreviewHeight = result.height / safeHeight
        fillPreviewTolerance = tolerance
        fillPreviewComputedAt = now
    }

    private fun renderDocumentPageBitmap(pageWidth: Int, pageHeight: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(pageWidth / width.coerceAtLeast(1).toFloat(), pageHeight / height.coerceAtLeast(1).toFloat())
        drawPageContent(canvas, includeInteractionOverlays = false)
        return bitmap
    }

    private fun commitFillGesture() {
        recomputeFillPreview(force = true)
        val mask = fillPreviewMask
        if (mask != null && fillPreviewWidth > 0f && fillPreviewHeight > 0f) {
            val color = inkColor.toUInt().toLong()
            onFillCommitted?.invoke(
                mask.copy(Bitmap.Config.ARGB_8888, false),
                fillPreviewLeft,
                fillPreviewTop,
                fillPreviewWidth * pageModelWidth,
                fillPreviewHeight * pageModelHeight,
                color,
                inkOpacity,
            )
        }
        fillPreviewMask = null
        fillSourceBitmap?.recycle()
        fillSourceBitmap = null
        invalidate()
    }

    private fun cancelFillGesture() {
        fillPreviewMask?.recycle()
        fillPreviewMask = null
        fillSourceBitmap?.recycle()
        fillSourceBitmap = null
        invalidate()
    }

    private fun beginSmudgeGesture(normalizedX: Float, normalizedY: Float) {
        smudgeSnapshot?.recycle()
        smudgeSnapshot = renderDocumentPageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        smudgeSegments.clear()
        smudgeCurrentColor = quantizeSmudgeColor(sampleSmudgeColor(normalizedX, normalizedY))
        smudgeSegments += smudgeCurrentColor to mutableListOf(StrokePoint(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f), 1f, SystemClock.uptimeMillis()))
        invalidate()
    }

    private fun extendSmudge(normalizedX: Float, normalizedY: Float) {
        val x = normalizedX.coerceIn(0f, 1f)
        val y = normalizedY.coerceIn(0f, 1f)
        val color = quantizeSmudgeColor(sampleSmudgeColor(x, y))
        if (color != smudgeCurrentColor && smudgeSegments.lastOrNull()?.second?.isNotEmpty() == true) {
            val previousPoint = smudgeSegments.last().second.last()
            smudgeSegments += color to mutableListOf(previousPoint)
            smudgeCurrentColor = color
        }
        smudgeSegments.lastOrNull()?.second?.let { points ->
            if (points.isEmpty() || hypot((points.last().x - x).toDouble(), (points.last().y - y).toDouble()).toFloat() >= .0006f) points += StrokePoint(x, y, 1f, SystemClock.uptimeMillis())
        }
        invalidate()
    }

    private fun sampleSmudgeColor(normalizedX: Float, normalizedY: Float): Int {
        val snapshot = smudgeSnapshot ?: return inkColor
        val x = (normalizedX.coerceIn(0f, 1f) * (snapshot.width - 1).coerceAtLeast(0)).toInt()
        val y = (normalizedY.coerceIn(0f, 1f) * (snapshot.height - 1).coerceAtLeast(0)).toInt()
        return snapshot.getPixel(x, y)
    }

    private fun quantizeSmudgeColor(color: Int): Int {
        fun channel(value: Int): Int = ((value + SMUDGE_COLOR_QUANTIZATION / 2) / SMUDGE_COLOR_QUANTIZATION * SMUDGE_COLOR_QUANTIZATION).coerceIn(0, 255)
        return Color.argb(
            channel(Color.alpha(color)),
            channel(Color.red(color)),
            channel(Color.green(color)),
            channel(Color.blue(color)),
        )
    }

    private fun commitSmudgeGesture() {
        val before = strokes.toList()
        val newStrokes = smudgeSegments.mapNotNull { (color, points) ->
            if (points.size < 2) null
            else makeStroke(points).copy(
                id = java.util.UUID.randomUUID().toString(),
                colorArgb = color.toUInt().toLong(),
                opacity = (inkOpacity * .55f).coerceIn(.05f, 1f),
                width = (baseWidth * .8f).coerceAtLeast(1f),
                grainCompositeMode = grainCompositeMode,
            )
        }
        if (newStrokes.isNotEmpty()) {
            strokes.addAll(newStrokes)
            invalidateStrokeCache(emptyList(), newStrokes)
            commitStrokeEdit(before, strokes)
        }
        smudgeSnapshot?.recycle()
        smudgeSnapshot = null
        smudgeSegments.clear()
        invalidate()
    }

    private fun cancelSmudgeGesture() {
        smudgeSnapshot?.recycle()
        smudgeSnapshot = null
        smudgeSegments.clear()
        invalidate()
    }

    private fun drawSmudgePreview(canvas: Canvas) {
        if (smudgeSegments.isEmpty()) return
        val previewStrokes = smudgeSegments.mapNotNull { (color, points) ->
            if (points.size < 2) null
            else makeStroke(points).copy(
                id = java.util.UUID.randomUUID().toString(),
                colorArgb = color.toUInt().toLong(),
                opacity = (inkOpacity * .55f).coerceIn(.05f, 1f),
                width = (baseWidth * .8f).coerceAtLeast(1f),
                grainCompositeMode = grainCompositeMode,
            )
        }
        if (previewStrokes.isNotEmpty()) drawLayeredStrokes(canvas, previewStrokes)
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

    /** Returns TRANSFORM_SCALE / TRANSFORM_ROTATE when the touch lands on a handle, else TRANSFORM_NONE. */
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
        val rotY = (bounds.top - SELECTION_ROTATE_HANDLE_OFFSET_PX / height.coerceAtLeast(1))
        if (hypot(((bounds.centerX() - normalizedX) * width).toDouble(), ((rotY - normalizedY) * height).toDouble()).toFloat() <= SELECTION_HANDLE_TOUCH_PX) return TRANSFORM_ROTATE
        return TRANSFORM_NONE
    }

    private fun applySelectionTransform(scale: Float, rotation: Float) {
        val before = selectionGestureBefore ?: return
        val selectedSet = selectedStrokeIds.toHashSet()
        val movedBefore = strokes.filter { it.id in selectedSet }
        val transformed = StrokeTransform.transform(before.filter { it.id in selectedSet }, selectionTransformCenterX, selectionTransformCenterY, scale, rotation, 0f, 0f, pageModelWidth, pageModelHeight).associateBy(InkStroke::id)
        for (i in strokes.indices) {
            val replacement = transformed[strokes[i].id]
            if (replacement != null) strokes[i] = replacement
        }
        invalidateStrokeCache(movedBefore, strokes.filter { it.id in selectedSet })
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        if (selectedStrokeIds.isEmpty()) return
        val bounds = selectionBounds() ?: return
        val handleRadius = SELECTION_HANDLE_TOUCH_PX / 3f / width.coerceAtLeast(1)
        val corners = listOf(
            bounds.left to bounds.top, bounds.right to bounds.top,
            bounds.right to bounds.bottom, bounds.left to bounds.bottom,
        )
        val rotY = bounds.top - SELECTION_ROTATE_HANDLE_OFFSET_PX / height.coerceAtLeast(1)
        paint.style = Paint.Style.STROKE; paint.color = Color.rgb(24, 119, 242); paint.alpha = 255; paint.strokeWidth = 2f
        canvas.drawLine(bounds.centerX() * width, bounds.top * height, bounds.centerX() * width, rotY * height, paint)
        val drawHandles = listOf(bounds.centerX() to rotY) + corners
        drawHandles.forEach { (hx, hy) ->
            val cx = hx * width; val cy = hy * height
            selectionHandlePaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, handleRadius * width, selectionHandlePaint)
            selectionHandleStrokePaint.color = Color.rgb(24, 119, 242)
            canvas.drawCircle(cx, cy, handleRadius * width, selectionHandleStrokePaint)
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
        activeStrokeTemplate?.copy(points = points.toList()) ?: buildStroke(points)

    private fun buildStroke(points: List<StrokePoint>) = InkStroke(id = currentStrokeId, points = points.toList(), colorArgb = inkColor.toUInt().toLong(), width = baseWidth, opacity = inkOpacity, brushId = brushId, layerId = activeLayerId, tipAssetPath = tipAssetPath, tipAssetPaths = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }, dualTipAssetPaths = dualTipAssetPaths, grainAssetPath = grainAssetPath, grainDensity = grainDensity, grainDensityDynamics = grainDensityDynamics, grainScale = grainScale, grainRotationDegrees = grainRotationDegrees, grainBrightness = grainBrightness, grainContrast = grainContrast, grainReversed = grainReversed, grainEmphasized = grainEmphasized, grainOffsetJitter = grainOffsetJitter, spacing = brushSpacing, spacingMode = brushSpacingMode, flow = brushFlow, hardness = brushHardness, antiAliasMode = brushAntiAliasMode, blur = brushBlur, blurUnit = brushBlurUnit, blurLinkedToSize = brushBlurLinkedToSize, thickness = brushThickness, thicknessVertical = brushThicknessVertical, rotationDegrees = brushRotation, followStylusOrientation = followStylusOrientation, followStrokeDirection = followStrokeDirection, randomRotationDegrees = randomRotationDegrees, strokeRotationJitterDegrees = strokeRotationJitterDegrees, stampCount = stampCount, stampCountJitter = stampCountJitter, continuousPlot = brushContinuousPlot, colorJitterHueDegrees = colorJitterHueDegrees, colorJitterSaturation = colorJitterSaturation, colorJitterValue = colorJitterValue, colorJitterTarget = colorJitterTarget, colorJitterHueDynamics = colorJitterHueDynamics, colorJitterSaturationDynamics = colorJitterSaturationDynamics, colorJitterValueDynamics = colorJitterValueDynamics, watercolor = brushWatercolor, colorMix = brushColorMix, colorMixPressureEnabled = colorMixPressureEnabled, colorMixPressureMinimum = colorMixPressureMinimum, colorMixPressureCurve = colorMixPressureCurve, colorMixSecondaryDynamics = colorMixSecondaryDynamics, paintMix = brushPaintMix, paintMixPressureEnabled = paintMixPressureEnabled, paintMixPressureMinimum = paintMixPressureMinimum, paintMixPressureCurve = paintMixPressureCurve, paintMixSecondaryDynamics = paintMixSecondaryDynamics, spray = brushSpray, sprayParticleSize = sprayParticleSize, sprayParticleSizeUnit = sprayParticleSizeUnit, sprayParticleSizeSyncBrushSize = sprayParticleSizeSyncBrushSize, sprayParticleSizePressureEnabled = sprayParticleSizePressureEnabled, sprayParticleSizePressureMinimum = sprayParticleSizePressureMinimum, sprayParticleSizePressureCurve = sprayParticleSizePressureCurve, sprayParticleSizeSecondaryDynamics = sprayParticleSizeSecondaryDynamics, sprayDensity = sprayDensity, sprayDensityPressureEnabled = sprayDensityPressureEnabled, sprayDensityPressureMinimum = sprayDensityPressureMinimum, sprayDensityPressureCurve = sprayDensityPressureCurve, sprayDensitySecondaryDynamics = sprayDensitySecondaryDynamics, sprayBias = sprayBias, spraySpread = spraySpread, sprayParticleRotationDegrees = sprayParticleRotationDegrees, sprayParticleFollowStylusOrientation = sprayParticleFollowStylusOrientation, sprayParticleFollowStrokeDirection = sprayParticleFollowStrokeDirection, sprayParticleRandomRotationDegrees = sprayParticleRandomRotationDegrees, ribbon = brushRibbon, tipSequenceMode = brushTipSequenceMode, tipFlipHorizontalMode = tipFlipHorizontalMode, tipFlipVerticalMode = tipFlipVerticalMode, blendTipsWithDarken = blendTipsWithDarken, dualBrush = brushDual, dualSizeScale = dualSizeScale, dualSizeDynamics = dualSizeDynamics, dualSpacing = dualSpacing, dualSpacingMode = dualSpacingMode, dualSpacingDynamics = dualSpacingDynamics, dualFlow = dualFlow, dualFlowDynamics = dualFlowDynamics, dualHardness = dualHardness, dualAntiAliasMode = dualAntiAliasMode, dualThickness = dualThickness, dualThicknessDynamics = dualThicknessDynamics, dualThicknessVertical = dualThicknessVertical, dualRotationDegrees = dualRotationDegrees, dualFollowStylusOrientation = dualFollowStylusOrientation, dualFollowStrokeDirection = dualFollowStrokeDirection, dualRandomRotationDegrees = dualRandomRotationDegrees, dualStrokeRotationJitterDegrees = dualStrokeRotationJitterDegrees, dualStampCount = dualStampCount, dualStampCountJitter = dualStampCountJitter, dualRibbon = dualRibbon, dualSpray = dualSpray, dualSprayParticleSize = dualSprayParticleSize, dualSprayParticleSizeUnit = dualSprayParticleSizeUnit, dualSprayParticleSizeSyncBrushSize = dualSprayParticleSizeSyncBrushSize, dualSprayParticleSizeDynamics = dualSprayParticleSizeDynamics, dualSprayDensity = dualSprayDensity, dualSprayDensityDynamics = dualSprayDensityDynamics, dualSprayBias = dualSprayBias, dualSpraySpread = dualSpraySpread, dualSprayParticleRotationDegrees = dualSprayParticleRotationDegrees, dualSprayParticleFollowStylusOrientation = dualSprayParticleFollowStylusOrientation, dualSprayParticleFollowStrokeDirection = dualSprayParticleFollowStrokeDirection, dualSprayParticleRandomRotationDegrees = dualSprayParticleRandomRotationDegrees, dualTipSequenceMode = dualTipSequenceMode, dualTipFlipHorizontalMode = dualTipFlipHorizontalMode, dualTipFlipVerticalMode = dualTipFlipVerticalMode, dualBlendTipsWithDarken = dualBlendTipsWithDarken, dualCompositeMode = dualCompositeMode, dualApplyRgb = dualApplyRgb, taperMode = taperMode, taperStartEnabled = taperStartEnabled, taperStartLength = taperStartLength, taperStartLengthUnit = taperStartLengthUnit, taperStartMinimum = taperStartMinimum, taperEndEnabled = taperEndEnabled, taperEndLength = taperEndLength, taperEndLengthUnit = taperEndLengthUnit, taperEndMinimum = taperEndMinimum, waterEdgeEnabled = waterEdgeEnabled, waterEdgeRadius = waterEdgeRadius, waterEdgeRadiusUnit = waterEdgeRadiusUnit, waterEdgeOpacity = waterEdgeOpacity, waterEdgeDarkness = waterEdgeDarkness, waterEdgeBlur = waterEdgeBlur, waterEdgeBlurUnit = waterEdgeBlurUnit, sizePressureEnabled = sizePressureEnabled, sizePressureMinimum = sizePressureMinimum, sizePressureCurve = sizePressureCurve, sizeSecondaryDynamics = sizeSecondaryDynamics, opacityPressureEnabled = opacityPressureEnabled, opacityPressureMinimum = opacityPressureMinimum, opacityPressureCurve = opacityPressureCurve, opacitySecondaryDynamics = opacitySecondaryDynamics, flowPressureEnabled = flowPressureEnabled, flowPressureMinimum = flowPressureMinimum, flowPressureCurve = flowPressureCurve, flowSecondaryDynamics = flowSecondaryDynamics, spacingPressureEnabled = spacingPressureEnabled, spacingPressureMinimum = spacingPressureMinimum, spacingPressureCurve = spacingPressureCurve, spacingSecondaryDynamics = spacingSecondaryDynamics, thicknessPressureEnabled = thicknessPressureEnabled, thicknessPressureMinimum = thicknessPressureMinimum, thicknessPressureCurve = thicknessPressureCurve, thicknessSecondaryDynamics = thicknessSecondaryDynamics, blurPressureEnabled = blurPressureEnabled, blurPressureMinimum = blurPressureMinimum, blurPressureCurve = blurPressureCurve, blurSecondaryDynamics = blurSecondaryDynamics)

    /** The original stroke plus its symmetry-mirrored copies (each with a fresh id). */
    private fun symmetryStrokes(points: List<StrokePoint>): List<InkStroke> {
        val base = makeStroke(points)
        if (symmetryMode == SymmetryMode.NONE) return listOf(base)
        return listOf(base) + symmetryMirrors(points).map { makeStroke(it).copy(id = java.util.UUID.randomUUID().toString()) }
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
        if (strokeTileIndex.widthPx != width || strokeTileIndex.heightPx != height) {
            strokeTileIndex.rebuild(strokes, width, height)
        }
        val candidateIds = strokeTileIndex.candidates(
            x * width - eraserRadius,
            y * height - eraserRadius,
            x * width + eraserRadius,
            y * height + eraserRadius,
        )
        var changed = false
        val changedBefore = mutableListOf<InkStroke>()
        val changedAfter = mutableListOf<InkStroke>()
        for (index in strokes.indices.reversed()) {
            val stroke = strokes[index]
            if (stroke.id !in candidateIds) continue
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

    fun selectAllEditableStrokes(): Boolean {
        val selectable = strokes.asSequence()
            .filterNot { it.id in protectedStrokeIds || it.layerId in protectedLayerIds }
            .map(InkStroke::id)
            .toList()
        selectedStrokeIds.clear()
        selectedStrokeIds += selectable
        invalidate()
        return selectable.isNotEmpty()
    }

    fun clearSelection(): Boolean {
        if (selectedStrokeIds.isEmpty()) return false
        selectedStrokeIds.clear()
        lassoPoints.clear()
        movingSelection = false
        invalidate()
        return true
    }

    fun deleteSelection() {
        if (selectedStrokeIds.isEmpty()) return
        val before = strokes.toList()
        val ids = selectedStrokeIds.toSet(); val removed = strokes.filter { it.id in ids }; strokes.removeAll { it.id in ids }; commitStrokeEdit(before, strokes); selectedStrokeIds.clear(); invalidateStrokeCache(removed, emptyList())
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
        invalidateStrokeCache(selected, strokes.filter { it.id in selectedStrokeIds })
    }

    private companion object {
        const val REFERENCE_WIDTH = 1600f
        const val MAX_CACHE_PIXELS = 16_000_000L
        const val FILL_MAX_SAMPLE_DIMENSION = 1_600f
        const val FILL_PREVIEW_THROTTLE_MS = 32L
        const val SMUDGE_COLOR_QUANTIZATION = 16
        const val LIVE_STROKE_CACHE_START_POINTS = 8
        const val LIVE_STROKE_CHECKPOINT_POINTS = 16
        const val LIVE_STROKE_TAIL_POINTS = 8
        const val MAX_ERASER_SAMPLES_PER_EVENT = 256
        const val CONTINUOUS_PLOT_INTERVAL_MS = 32L
        const val QUICKSHAPE_HOLD_MS = 120L
        const val TRANSFORM_NONE = 0
        const val TRANSFORM_SCALE = 1
        const val TRANSFORM_ROTATE = 2
        const val SELECTION_HANDLE_TOUCH_PX = 48f
        const val SELECTION_SMALL_HANDLE_TOUCH_PX = 10f
        const val SELECTION_ROTATE_HANDLE_OFFSET_PX = 64f
        const val SALT_SIZE = 11
        const val SALT_OPACITY = 23
        const val SALT_FLOW = 37
        const val SALT_SPACING = 53
        const val SALT_THICKNESS = 71
        const val SALT_DUAL_SIZE = 101
        const val SALT_DUAL_FLOW = 103
        const val SALT_DUAL_SPACING = 107
        const val SALT_DUAL_THICKNESS = 109
        const val SALT_BLUR = 89
        const val DUAL_SCATTER_SEED = 0x27C4915DL
        const val DUAL_ROTATION_SEED = 0x61B8D2A3L
        const val VIEW_ROTATION_DEGREES_PER_PIXEL = .25f
        const val RULER_TOUCH_RADIUS_DP = 38f
        const val GUIDE_TOUCH_RADIUS_DP = 14f
        const val GUIDE_SNAP_TOLERANCE_DP = 12f
        const val DUAL_SEQUENCE_SEED = 0x35A71C9E
        const val MAX_TIP_CACHE_ENTRIES = 64
        const val MAX_COLOR_FILTER_CACHE_ENTRIES = 64
        const val MAX_BLUR_MASK_CACHE_ENTRIES = 48
        const val MAX_CACHED_BLUR_RADIUS_PX = 256f
        const val STYLUS_BUTTON_MASK = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        const val POINTER_BUTTON_MASK = MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD
        val TIP_DECODE_EXECUTOR = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "inkloom-brush-tip").apply { isDaemon = true }
        }
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
