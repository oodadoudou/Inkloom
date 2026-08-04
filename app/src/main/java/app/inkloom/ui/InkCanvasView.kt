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
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import java.util.LinkedHashMap
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
import app.inkloom.document.StrokeTileIndex
import app.inkloom.document.StrokeTileKey
import app.inkloom.document.StylusInputClassifier
import app.inkloom.document.TwoFingerTapUndoGesture
import app.inkloom.document.TwoPointerRotationGesture
import app.inkloom.document.ThreeFingerTapRedoGesture
import app.inkloom.document.ViewportTransform
import app.inkloom.document.ViewportState
import app.inkloom.document.WaterEdge
import app.inkloom.document.WetMixDynamics
import app.inkloom.model.EditorTool
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
    private var navigationSpan = 0f
    private var navigationMoved = false
    private var navigationTapEligible = false
    private var externalViewportGestureContact = false
    private val navigationTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val singleFingerDoubleTapUndoGesture = SingleFingerDoubleTapUndoGesture(navigationTouchSlop)
    private val twoFingerTapUndoGesture = TwoFingerTapUndoGesture(navigationTouchSlop)
    private val threeFingerTapRedoGesture = ThreeFingerTapRedoGesture(navigationTouchSlop)
    private val twoPointerRotationGesture = TwoPointerRotationGesture()
    private val colorSampleBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var activeTool: EditorTool? = null
    private var activeInputWasStylus = false
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
            invalidate()
            postDelayed(this, CONTINUOUS_PLOT_INTERVAL_MS)
        }
    }
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
    var externalViewportGestures: Boolean = false
    var viewportInputTransformEnabled: Boolean = false
    var viewportScale: Float = 1f
    var viewportPanX: Float = 0f
    var viewportPanY: Float = 0f
    var viewportRotationDegrees: Float = 0f
    var viewportCenterScreenX: Float = 0f
    var viewportCenterScreenY: Float = 0f
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

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean = true
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let { bitmap -> backgroundDestination.set(0, 0, width, height); canvas.drawBitmap(bitmap, null, backgroundDestination, null) }
            ?: pagePaperColor?.let { color -> canvas.drawColor(color); pagePaperPattern?.let { drawPaperPattern(canvas, it) } }
        drawSnapGrid(canvas)
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
        drawAlignmentGuides(canvas)
        drawRulerGuide(canvas)
        drawLaserTrail(canvas, SystemClock.uptimeMillis())
        drawBrushSizePreview(canvas)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) resetStrokeTiles()
    }

    override fun onDetachedFromWindow() {
        if (activeTool != null) cancelActiveGestureForNavigation()
        releaseHeldStylusButtons()
        lastStylusButtonState = 0
        stopContinuousPlot()
        laserTrail.clear()
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
            resetAntiAliasPaint()
            return
        }
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        drawStrokeContent(canvas, stroke)
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

    private fun drawStrokeContent(canvas: Canvas, stroke: InkStroke) {
        if (stroke.points.isEmpty()) return
        paint.isAntiAlias = CspAntiAlias.enabled(stroke.antiAliasMode)
        paint.isFilterBitmap = CspAntiAlias.enabled(stroke.antiAliasMode)
        val tipPaths = stroke.tipAssetPaths.ifEmpty { listOfNotNull(stroke.tipAssetPath) }
        val grainOffset = GrainBehavior.offset(stroke.renderSeed, stroke.grainOffsetJitter)
        val tips = tipPaths.mapNotNull { loadTip(it, stroke.hardness, stroke.grainAssetPath, stroke.grainDensity, stroke.grainScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized, grainOffset.x, grainOffset.y, stroke.grainCompositeMode, stroke.antiAliasMode) }
        if (tips.isNotEmpty()) {
            val dualTips = if (stroke.dualBrush) stroke.dualTipAssetPaths.mapNotNull { loadTip(it, stroke.dualHardness, null, 0f, antiAliasMode = stroke.dualAntiAliasMode) }.ifEmpty { tipPaths.mapNotNull { loadTip(it, stroke.dualHardness, null, 0f, antiAliasMode = stroke.dualAntiAliasMode) } } else emptyList()
            drawStampedStroke(canvas, stroke, tips, dualTips, tipPaths, grainOffset.x, grainOffset.y)
            return
        }
        paint.color = stroke.colorArgb.toInt()
        paint.colorFilter = null
        val scale = width.coerceAtLeast(1) / REFERENCE_WIDTH
        val taper = StrokeTaper.factors(stroke.points, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        if (stroke.points.size == 1) {
            val point = stroke.points.first(); val strokeWidth = stroke.width * scale * taper.firstOrNull().orDefault() * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, point.pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE); val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, point.pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, point.pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * WetMixDynamics.alphaMultiplier(stroke, point) * 255).toInt().coerceIn(1, 255); drawLinePoint(canvas, stroke, point, point.x * width, point.y * height, strokeWidth, alpha)
        } else stroke.points.zipWithNext().forEachIndexed { index, (start, end) ->
            val point = midpoint(start, end); val pressure = point.pressure.coerceIn(0f, 1f)
            val taperFactor = (taper[index] + taper[index + 1]) / 2f
            val strokeWidth = stroke.width * scale * taperFactor * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)
            val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * WetMixDynamics.alphaMultiplier(stroke, point) * 255).toInt().coerceIn(1, 255)
            drawLineSegment(canvas, stroke, point, start.x * width, start.y * height, end.x * width, end.y * height, strokeWidth, alpha)
        }
    }

    private fun drawStampedStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>, dualTips: List<Bitmap>, tipPaths: List<String>, grainOffsetX: Float, grainOffsetY: Float) {
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
        val sampledPoints = StrokeStampSampler.sample(
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
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        sampledPoints.forEachIndexed { index, point ->
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
            val matrix = Matrix().apply {
                postTranslate(-tip.width / 2f, -tip.height / 2f)
                postScale(scaleX, scaleY)
                postRotate(angle)
                postTranslate(drawX, drawY)
            }
            val jitteredColor = BrushColorJitter.argb(stroke, point, sequenceIndex)
            paint.colorFilter = PorterDuffColorFilter(jitteredColor, PorterDuff.Mode.SRC_IN)
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW)
            val mainAlpha = (stroke.opacity * opacityDynamic * stroke.flow * flowDynamic * WetMixDynamics.alphaMultiplier(stroke, point)).coerceIn(.01f, 1f).times(255).toInt()
            if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
                val edgeRadius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat())
                val edgeTipScale = BrushTipTransform.scale(tip.width, tip.height, diameter + edgeRadius * 2f, thicknessScale, stroke.thicknessVertical)
                val edgeMatrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(edgeTipScale.x * flip.x, edgeTipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
                paint.colorFilter = PorterDuffColorFilter(WaterEdge.darkenArgb(jitteredColor, stroke.waterEdgeDarkness), PorterDuff.Mode.SRC_IN)
                paint.alpha = (mainAlpha * stroke.waterEdgeOpacity.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
                val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat())
                paint.maskFilter = blur.takeIf { it > .01f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
                canvas.drawBitmap(tip, edgeMatrix, paint)
                paint.maskFilter = null
            }
            paint.colorFilter = PorterDuffColorFilter(jitteredColor, PorterDuff.Mode.SRC_IN)
            paint.alpha = mainAlpha
            paint.maskFilter = brushBlurMask(stroke, point, diameter, stroke.width * referenceScale, width.toFloat())
            canvas.drawBitmap(tip, matrix, paint)
            paint.maskFilter = null
            }
        }
        paint.blendMode = null
        if (stroke.dualBrush && dualTips.isNotEmpty()) {
            paint.isFilterBitmap = CspAntiAlias.enabled(stroke.dualAntiAliasMode)
            drawDualStampedStroke(canvas, stroke, dualTips)
        }
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
        val sampledPoints = StrokeStampSampler.sample(
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
        val taper = StrokeTaper.factors(sampledPoints, width.toFloat(), height.toFloat(), stroke.taperMode, stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum, stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum)
        sampledPoints.forEachIndexed { index, point ->
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
            val matrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(tipScale.x * flip.x, tipScale.y * flip.y); postRotate(angle); postTranslate(drawX, drawY) }
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.dualFlowDynamics.pressureEnabled, stroke.dualFlowDynamics.pressureMinimum, stroke.dualFlowDynamics.pressureCurve, pressure) * secondaryDynamic(stroke.dualFlowDynamics.secondary, point, stroke.renderSeed, SALT_DUAL_FLOW)
            paint.colorFilter = PorterDuffColorFilter(BrushColorJitter.argb(stroke, point, sequenceIndex), PorterDuff.Mode.SRC_IN)
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
        return radius.takeIf { it > .01f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
    }

    private fun configureWaterEdge(stroke: InkStroke, strokeWidth: Float, alpha: Int, color: Int) {
        val radius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat())
        paint.color = WaterEdge.darkenArgb(color, stroke.waterEdgeDarkness)
        paint.strokeWidth = strokeWidth + radius * 2f
        paint.alpha = (alpha * stroke.waterEdgeOpacity.coerceIn(0f, 1f)).toInt().coerceIn(1, 255)
        val blur = WaterEdge.distancePixels(stroke.waterEdgeBlur, stroke.waterEdgeBlurUnit, width.toFloat())
        paint.maskFilter = blur.takeIf { it > .01f }?.let { BlurMaskFilter(it, BlurMaskFilter.Blur.NORMAL) }
    }

    private fun loadTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false, grainOffsetX: Float = 0f, grainOffsetY: Float = 0f, grainCompositeMode: Int = CspTextureComposite.MULTIPLY, antiAliasMode: Int = CspAntiAlias.MEDIUM): Bitmap? {
        val normalizedAntiAlias = CspAntiAlias.normalize(antiAliasMode)
        val key = "$path:${(hardness.coerceIn(0f, 1f) * 20).toInt()}:$grainPath:${(grainDensity.coerceIn(0f, 1f) * 20).toInt()}:${grainScale.toInt()}:${grainRotation.toInt()}:${grainBrightness.toInt()}:${grainContrast.toInt()}:$grainReversed:$grainEmphasized:${grainOffsetX.toBits()}:${grainOffsetY.toBits()}:$grainCompositeMode:$normalizedAntiAlias"
        return tipCache[key] ?: BrushTipTextureDecoder.decode(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized, grainOffsetX, grainOffsetY, grainCompositeMode, normalizedAntiAlias)?.also { tipCache[key] = it }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.EYEDROPPER, EditorTool.SELECT, EditorTool.SHAPE, EditorTool.LASER)) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !hasFocus()) requestFocus()
        val pointerIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val eventToolType = event.getToolType(pointerIndex)
        val isStylus = eventToolType == MotionEvent.TOOL_TYPE_STYLUS || eventToolType == MotionEvent.TOOL_TYPE_ERASER
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
            navigationX = event.rawX
            navigationY = event.rawY
            navigationDownX = event.rawX
            navigationDownY = event.rawY
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
        if (handleCanvasGuideGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        if (handleRulerGesture(event, inputPoint.first, inputPoint.second, isStylus, elapsedSinceStylus)) return true
        val buttonForcesNavigation = isStylus && (stylusButtonAction == StylusButtonAction.HAND || stylusHandNavigationContact)
        val isFingerNavigation = forceNavigation || buttonForcesNavigation || StylusInputClassifier.shouldNavigateOrRejectFinger(isStylus, fingerDrawingEnabled, event.pointerCount, event.getTouchMajor(pointerIndex), elapsedSinceStylus)
        if (isFingerNavigation) {
            if (activeTool != null) {
                if (activeInputWasStylus && !forceNavigation && !buttonForcesNavigation) return true
                cancelActiveGestureForNavigation()
            }
            if (externalViewportGestures && (externalViewportGestureContact || event.pointerCount >= 2)) {
                if (event.pointerCount >= 2) externalViewportGestureContact = true
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) externalViewportGestureContact = false
                return true
            }
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    twoPointerRotationGesture.reset()
                    navigationSpan = 0f
                    threeFingerTapRedoGesture.cancel()
                    navigationX = event.rawX; navigationY = event.rawY
                    navigationDownX = event.rawX; navigationDownY = event.rawY
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
                    val focus = event.rawCentroid()
                    navigationX = focus.first
                    navigationY = focus.second
                    navigationSpan = event.rawSpan()
                    if (twoFingerRotationEnabled && event.pointerCount == 2) {
                        twoPointerRotationGesture.begin(event.getRawX(0), event.getRawY(0), event.getRawX(1), event.getRawY(1))
                    } else twoPointerRotationGesture.reset()
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount == 3 && threeFingerTapRedo && threeFingerTapRedoGesture.finish(event.eventTime)) onRedoGesture()
                    else if (event.pointerCount == 2 && twoFingerDoubleTapUndo && twoFingerTapUndoGesture.finish(event.eventTime)) onUndoGesture()
                    ViewportTransform.remainingPointerIndex(event.pointerCount, event.actionIndex)?.let { remaining ->
                        navigationX = event.getRawX(remaining)
                        navigationY = event.getRawY(remaining)
                        navigationDownX = navigationX
                        navigationDownY = navigationY
                    }
                    navigationSpan = 0f
                    navigationMoved = true
                    navigationTapEligible = false
                    twoPointerRotationGesture.reset()
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount >= 2) {
                    val focus = event.rawCentroid()
                    val span = event.rawSpan()
                    if (navigationSpan > .01f && span > .01f) {
                        onViewportScale((span / navigationSpan).coerceIn(.5f, 2f), navigationX, navigationY)
                    }
                    val rotationDelta = if (twoFingerRotationEnabled && event.pointerCount == 2) {
                        twoPointerRotationGesture.update(event.getRawX(0), event.getRawY(0), event.getRawX(1), event.getRawY(1))
                    } else {
                        twoPointerRotationGesture.reset()
                        null
                    }
                    rotationDelta?.let(onViewportRotate)
                    if (event.pointerCount == 3) threeFingerTapRedoGesture.move(focus.first, focus.second, scaleDetector.isInProgress)
                    else twoFingerTapUndoGesture.move(focus.first, focus.second, scaleDetector.isInProgress || twoPointerRotationGesture.isRotating)
                    onViewportPan(focus.first - navigationX, focus.second - navigationY)
                    navigationX = focus.first; navigationY = focus.second
                    navigationSpan = span
                    navigationMoved = true
                } else if (!scaleDetector.isInProgress && rotateNavigation) {
                    singleFingerDoubleTapUndoGesture.move(event.x, event.y, transforming = false)
                    onViewportRotate((event.rawX - navigationX) * VIEW_ROTATION_DEGREES_PER_PIXEL)
                    navigationX = event.rawX; navigationY = event.rawY
                    navigationMoved = true
                } else if (!scaleDetector.isInProgress) {
                    singleFingerDoubleTapUndoGesture.move(event.x, event.y, transforming = false)
                    if (hypot(event.rawX - navigationDownX, event.rawY - navigationDownY) > navigationTouchSlop) navigationMoved = true
                    onViewportPan(event.rawX - navigationX, event.rawY - navigationY)
                    navigationX = event.rawX; navigationY = event.rawY
                }
                MotionEvent.ACTION_UP -> {
                    twoPointerRotationGesture.reset()
                    navigationSpan = 0f
                    threeFingerTapRedoGesture.cancel()
                    val movedFromDown = navigationMoved || hypot(event.rawX - navigationDownX, event.rawY - navigationDownY) > navigationTouchSlop
                    if (navigationTapEligible && !movedFromDown && !scaleDetector.isInProgress) {
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
                }
                MotionEvent.ACTION_CANCEL -> {
                    navigationTapEligible = false
                    singleFingerDoubleTapUndoGesture.cancel()
                    twoFingerTapUndoGesture.cancel()
                    threeFingerTapRedoGesture.cancel()
                    twoPointerRotationGesture.reset()
                    navigationSpan = 0f
                    stylusHandNavigationContact = false
                    if (isStylus) {
                        releaseHeldStylusButtons()
                        lastStylusButtonState = 0
                    }
                }
            }
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
                currentPoints.clear()
                currentStrokeId = java.util.UUID.randomUUID().toString()
                activeCanvasSnapConstraint = if (!rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
                    CanvasGuides.constraintAt(canvasGuideState, normalizedX, normalizedY, pageModelWidth, pageModelHeight, width.toFloat(), height.toFloat(), GUIDE_SNAP_TOLERANCE_DP * resources.displayMetrics.density)
                } else null
                updateContinuousPlotInput(normalizedX, normalizedY, event.pressure, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                addPoint(normalizedX, normalizedY, event.pressure, event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                if (brushContinuousPlot && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) startContinuousPlot()
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
                }
                else if (activeTool == EditorTool.ERASER) {
                    for (i in 0 until event.historySize) historicalCanvasPoint(event, 0, i).let { point -> eraseAlong(point.first / width.coerceAtLeast(1), point.second / height.coerceAtLeast(1), event.getHistoricalPressure(i)) }
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
                    currentPoints.clear()
                    if (activeTool == EditorTool.LASER) laserTrail.cancelActive()
                    shapeStart = null; shapeEnd = null; shapeFreeformPoints.clear()
                    if (activeTool == EditorTool.ERASER) cancelEraserGesture()
                    selectionGestureBefore?.let { restoreStrokeSnapshot(it) }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null
                } else if (activeTool == EditorTool.EYEDROPPER) {
                    onColorSampled(sampleRenderedColor(inputPoint.first, inputPoint.second))
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

    private fun MotionEvent.rawCentroid(): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        for (index in 0 until pointerCount) {
            x += getRawX(index)
            y += getRawY(index)
        }
        return x / pointerCount.coerceAtLeast(1) to y / pointerCount.coerceAtLeast(1)
    }

    private fun MotionEvent.rawSpan(): Float {
        if (pointerCount < 2) return 0f
        return hypot(getRawX(1) - getRawX(0), getRawY(1) - getRawY(0))
    }

    private fun canvasPoint(event: MotionEvent, pointerIndex: Int = event.actionIndex.coerceIn(0, event.pointerCount - 1)): Pair<Float, Float> {
        if (!viewportInputTransformEnabled || width <= 0 || height <= 0) return event.getX(pointerIndex) to event.getY(pointerIndex)
        return ViewportTransform.screenToLocal(
            event.getRawX(pointerIndex),
            event.getRawY(pointerIndex),
            viewportCenterScreenX,
            viewportCenterScreenY,
            width.toFloat(),
            height.toFloat(),
            ViewportState(viewportScale, viewportPanX, viewportPanY, viewportRotationDegrees),
        )
    }

    private fun historicalCanvasPoint(event: MotionEvent, pointerIndex: Int, position: Int): Pair<Float, Float> {
        if (!viewportInputTransformEnabled || width <= 0 || height <= 0) return event.getHistoricalX(pointerIndex, position) to event.getHistoricalY(pointerIndex, position)
        val rawOffsetX = event.getRawX(pointerIndex) - event.getX(pointerIndex)
        val rawOffsetY = event.getRawY(pointerIndex) - event.getY(pointerIndex)
        return ViewportTransform.screenToLocal(
            event.getHistoricalX(pointerIndex, position) + rawOffsetX,
            event.getHistoricalY(pointerIndex, position) + rawOffsetY,
            viewportCenterScreenX,
            viewportCenterScreenY,
            width.toFloat(),
            height.toFloat(),
            ViewportState(viewportScale, viewportPanX, viewportPanY, viewportRotationDegrees),
        )
    }

    private fun cancelActiveGestureForNavigation() {
        stopContinuousPlot()
        currentPoints.clear()
        laserTrail.cancelActive()
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
        val adjustedPressure = PressureCalibration.apply(pressure, pressureMinimumInput, pressureMaximumInput, pressureSensitivity)
        val previous = currentPoints.lastOrNull()
        val constrained = if (rulerEnabled && activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER)) {
            RulerGuide.project(rulerGuide, x, y, width.toFloat(), height.toFloat())
        } else activeCanvasSnapConstraint?.apply(x, y)
        val rawX = (constrained?.x ?: x).coerceIn(0f, 1f)
        val rawY = (constrained?.y ?: y).coerceIn(0f, 1f)
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
        if (force || previous == null || hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) > .0008) currentPoints += point
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

    private fun makeStroke(points: List<StrokePoint>) = InkStroke(id = currentStrokeId, points = points.toList(), colorArgb = inkColor.toUInt().toLong(), width = baseWidth, opacity = inkOpacity, brushId = brushId, layerId = activeLayerId, tipAssetPath = tipAssetPath, tipAssetPaths = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }, dualTipAssetPaths = dualTipAssetPaths, grainAssetPath = grainAssetPath, grainDensity = grainDensity, grainDensityDynamics = grainDensityDynamics, grainScale = grainScale, grainRotationDegrees = grainRotationDegrees, grainBrightness = grainBrightness, grainContrast = grainContrast, grainReversed = grainReversed, grainEmphasized = grainEmphasized, grainOffsetJitter = grainOffsetJitter, spacing = brushSpacing, spacingMode = brushSpacingMode, flow = brushFlow, hardness = brushHardness, antiAliasMode = brushAntiAliasMode, blur = brushBlur, blurUnit = brushBlurUnit, blurLinkedToSize = brushBlurLinkedToSize, thickness = brushThickness, thicknessVertical = brushThicknessVertical, rotationDegrees = brushRotation, followStylusOrientation = followStylusOrientation, followStrokeDirection = followStrokeDirection, randomRotationDegrees = randomRotationDegrees, strokeRotationJitterDegrees = strokeRotationJitterDegrees, stampCount = stampCount, stampCountJitter = stampCountJitter, continuousPlot = brushContinuousPlot, colorJitterHueDegrees = colorJitterHueDegrees, colorJitterSaturation = colorJitterSaturation, colorJitterValue = colorJitterValue, colorJitterTarget = colorJitterTarget, colorJitterHueDynamics = colorJitterHueDynamics, colorJitterSaturationDynamics = colorJitterSaturationDynamics, colorJitterValueDynamics = colorJitterValueDynamics, watercolor = brushWatercolor, colorMix = brushColorMix, colorMixPressureEnabled = colorMixPressureEnabled, colorMixPressureMinimum = colorMixPressureMinimum, colorMixPressureCurve = colorMixPressureCurve, colorMixSecondaryDynamics = colorMixSecondaryDynamics, paintMix = brushPaintMix, paintMixPressureEnabled = paintMixPressureEnabled, paintMixPressureMinimum = paintMixPressureMinimum, paintMixPressureCurve = paintMixPressureCurve, paintMixSecondaryDynamics = paintMixSecondaryDynamics, spray = brushSpray, sprayParticleSize = sprayParticleSize, sprayParticleSizeUnit = sprayParticleSizeUnit, sprayParticleSizeSyncBrushSize = sprayParticleSizeSyncBrushSize, sprayParticleSizePressureEnabled = sprayParticleSizePressureEnabled, sprayParticleSizePressureMinimum = sprayParticleSizePressureMinimum, sprayParticleSizePressureCurve = sprayParticleSizePressureCurve, sprayParticleSizeSecondaryDynamics = sprayParticleSizeSecondaryDynamics, sprayDensity = sprayDensity, sprayDensityPressureEnabled = sprayDensityPressureEnabled, sprayDensityPressureMinimum = sprayDensityPressureMinimum, sprayDensityPressureCurve = sprayDensityPressureCurve, sprayDensitySecondaryDynamics = sprayDensitySecondaryDynamics, sprayBias = sprayBias, spraySpread = spraySpread, sprayParticleRotationDegrees = sprayParticleRotationDegrees, sprayParticleFollowStylusOrientation = sprayParticleFollowStylusOrientation, sprayParticleFollowStrokeDirection = sprayParticleFollowStrokeDirection, sprayParticleRandomRotationDegrees = sprayParticleRandomRotationDegrees, ribbon = brushRibbon, tipSequenceMode = brushTipSequenceMode, tipFlipHorizontalMode = tipFlipHorizontalMode, tipFlipVerticalMode = tipFlipVerticalMode, blendTipsWithDarken = blendTipsWithDarken, dualBrush = brushDual, dualSizeScale = dualSizeScale, dualSizeDynamics = dualSizeDynamics, dualSpacing = dualSpacing, dualSpacingMode = dualSpacingMode, dualSpacingDynamics = dualSpacingDynamics, dualFlow = dualFlow, dualFlowDynamics = dualFlowDynamics, dualHardness = dualHardness, dualAntiAliasMode = dualAntiAliasMode, dualThickness = dualThickness, dualThicknessDynamics = dualThicknessDynamics, dualThicknessVertical = dualThicknessVertical, dualRotationDegrees = dualRotationDegrees, dualFollowStylusOrientation = dualFollowStylusOrientation, dualFollowStrokeDirection = dualFollowStrokeDirection, dualRandomRotationDegrees = dualRandomRotationDegrees, dualStrokeRotationJitterDegrees = dualStrokeRotationJitterDegrees, dualStampCount = dualStampCount, dualStampCountJitter = dualStampCountJitter, dualRibbon = dualRibbon, dualSpray = dualSpray, dualSprayParticleSize = dualSprayParticleSize, dualSprayParticleSizeUnit = dualSprayParticleSizeUnit, dualSprayParticleSizeSyncBrushSize = dualSprayParticleSizeSyncBrushSize, dualSprayParticleSizeDynamics = dualSprayParticleSizeDynamics, dualSprayDensity = dualSprayDensity, dualSprayDensityDynamics = dualSprayDensityDynamics, dualSprayBias = dualSprayBias, dualSpraySpread = dualSpraySpread, dualSprayParticleRotationDegrees = dualSprayParticleRotationDegrees, dualSprayParticleFollowStylusOrientation = dualSprayParticleFollowStylusOrientation, dualSprayParticleFollowStrokeDirection = dualSprayParticleFollowStrokeDirection, dualSprayParticleRandomRotationDegrees = dualSprayParticleRandomRotationDegrees, dualTipSequenceMode = dualTipSequenceMode, dualTipFlipHorizontalMode = dualTipFlipHorizontalMode, dualTipFlipVerticalMode = dualTipFlipVerticalMode, dualBlendTipsWithDarken = dualBlendTipsWithDarken, dualCompositeMode = dualCompositeMode, dualApplyRgb = dualApplyRgb, taperMode = taperMode, taperStartEnabled = taperStartEnabled, taperStartLength = taperStartLength, taperStartLengthUnit = taperStartLengthUnit, taperStartMinimum = taperStartMinimum, taperEndEnabled = taperEndEnabled, taperEndLength = taperEndLength, taperEndLengthUnit = taperEndLengthUnit, taperEndMinimum = taperEndMinimum, waterEdgeEnabled = waterEdgeEnabled, waterEdgeRadius = waterEdgeRadius, waterEdgeRadiusUnit = waterEdgeRadiusUnit, waterEdgeOpacity = waterEdgeOpacity, waterEdgeDarkness = waterEdgeDarkness, waterEdgeBlur = waterEdgeBlur, waterEdgeBlurUnit = waterEdgeBlurUnit, sizePressureEnabled = sizePressureEnabled, sizePressureMinimum = sizePressureMinimum, sizePressureCurve = sizePressureCurve, sizeSecondaryDynamics = sizeSecondaryDynamics, opacityPressureEnabled = opacityPressureEnabled, opacityPressureMinimum = opacityPressureMinimum, opacityPressureCurve = opacityPressureCurve, opacitySecondaryDynamics = opacitySecondaryDynamics, flowPressureEnabled = flowPressureEnabled, flowPressureMinimum = flowPressureMinimum, flowPressureCurve = flowPressureCurve, flowSecondaryDynamics = flowSecondaryDynamics, spacingPressureEnabled = spacingPressureEnabled, spacingPressureMinimum = spacingPressureMinimum, spacingPressureCurve = spacingPressureCurve, spacingSecondaryDynamics = spacingSecondaryDynamics, thicknessPressureEnabled = thicknessPressureEnabled, thicknessPressureMinimum = thicknessPressureMinimum, thicknessPressureCurve = thicknessPressureCurve, thicknessSecondaryDynamics = thicknessSecondaryDynamics, blurPressureEnabled = blurPressureEnabled, blurPressureMinimum = blurPressureMinimum, blurPressureCurve = blurPressureCurve, blurSecondaryDynamics = blurSecondaryDynamics)

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
        const val CONTINUOUS_PLOT_INTERVAL_MS = 32L
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
        const val STYLUS_BUTTON_MASK = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
        const val POINTER_BUTTON_MASK = MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD
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
