package app.inkloom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushDynamics
import app.inkloom.document.BrushTipSequence
import app.inkloom.document.BrushTipTextureDecoder
import app.inkloom.document.BrushTipTransform
import app.inkloom.document.BrushTaperMode
import app.inkloom.document.LassoGeometry
import app.inkloom.document.PressureCalibration
import app.inkloom.document.PageElement
import app.inkloom.document.ShapeGeometry
import app.inkloom.document.ShapeKind
import app.inkloom.document.ShapePoint
import app.inkloom.document.SprayGeometry
import app.inkloom.document.StrokeEdit
import app.inkloom.document.StrokeEditHistory
import app.inkloom.document.StrokeEdits
import app.inkloom.document.StrokeEraser
import app.inkloom.document.StrokeStampSampler
import app.inkloom.document.StrokeStabilization
import app.inkloom.document.StrokeTaper
import app.inkloom.document.StylusInputClassifier
import app.inkloom.document.WaterEdge
import app.inkloom.model.EditorTool
import app.inkloom.settings.StylusButtonAction
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
    private var movingSelection = false
    private var selectionMoved = false
    private var lastX = 0f
    private var lastY = 0f
    private var navigationX = 0f
    private var navigationY = 0f
    private var lastTwoFingerTap = 0L
    private var activeTool: EditorTool? = null
    private var lastStylusSeenAt = Long.MIN_VALUE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tipCache = mutableMapOf<String, Bitmap>()
    private val protectedStrokeIds = mutableSetOf<String>()
    private var strokeCache: Bitmap? = null
    private var strokeCacheDirty = true

    var tool: EditorTool = EditorTool.PEN
    var inkColor: Int = Color.rgb(17, 17, 17)
    var baseWidth: Float = 5f
    var inkOpacity: Float = 1f
    var brushId: String = "system-pencil"
    var activeLayerId: String = app.inkloom.document.LayerIds.INK
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
    var brushSpacing: Float = .12f
    var brushFlow: Float = 1f
    var brushHardness: Float = 1f
    var brushThickness: Float = 1f
    var brushThicknessVertical: Boolean = false
    var brushRotation: Float = 0f
    var followStylusOrientation: Boolean = false
    var followStrokeDirection: Boolean = false
    var randomRotationDegrees: Float = 0f
    var brushWatercolor: Boolean = false
    var brushColorMix: Float = 0f
    var brushPaintMix: Float = 0f
    var brushSpray: Boolean = false
    var sprayParticleSize: Float = 100f
    var sprayParticleSizeUnit: Int = 0
    var sprayParticleSizeSyncBrushSize: Boolean = true
    var sprayDensity: Float = 1f
    var sprayBias: Float = 0f
    var brushRibbon: Boolean = false
    var brushTipSequenceMode: Int = 0
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
    var dualRibbon: Boolean = false
    var dualSpray: Boolean = false
    var dualSprayParticleSize: Float = 100f
    var dualSprayParticleSizeUnit: Int = 0
    var dualSprayParticleSizeSyncBrushSize: Boolean = true
    var dualSprayDensity: Float = 1f
    var dualSprayBias: Float = 0f
    var dualTipSequenceMode: Int = 0
    var dualCompositeMode: Int = 0
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
    var eraserWidth: Float = 24f
    var fingerDrawingEnabled: Boolean = false
    var twoFingerDoubleTapUndo: Boolean = true
    var pressureSensitivity: Float = 1f
    var pressureMinimumInput: Float = 0f
    var pressureMaximumInput: Float = 1f
    var stabilization: Float = .12f
    var stabilizationBySpeed: Boolean = false
    var stabilizationBySpeedMode: Int = StrokeStabilization.REDUCE_WHEN_FAST
    var primaryButtonAction: StylusButtonAction = StylusButtonAction.ERASER
    var secondaryButtonAction: StylusButtonAction = StylusButtonAction.LASSO
    var shapeKind: ShapeKind = ShapeKind.RECTANGLE
    var pageModelWidth: Float = 1600f
    var pageModelHeight: Float = 2263f
    var onStrokeCommitted: (InkStroke) -> Unit = {}
    var onShapeCommitted: (PageElement.Shape) -> Unit = {}
    var onStrokeRemoved: (String) -> Unit = {}
    var onStrokesReplaced: (List<String>, List<InkStroke>, List<Int>) -> Unit = { removed, replacements, _ -> removed.forEach(onStrokeRemoved); replacements.forEach(onStrokeCommitted) }
    var onViewportScale: (Float, Float, Float) -> Unit = { _, _, _ -> }
    var onViewportPan: (Float, Float) -> Unit = { _, _ -> }
    var onUndoGesture: () -> Unit = {}
    var onInputDiagnostic: (String, Float, Float, Int) -> Unit = { _, _, _, _ -> }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onViewportScale(detector.scaleFactor, detector.focusX - width / 2f, detector.focusY - height / 2f)
            return true
        }
    })
    private val backgroundDestination = android.graphics.Rect()
    private val lassoPath = Path()

    init { setBackgroundColor(Color.TRANSPARENT); isFocusable = true }

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

    fun setPageBackground(bitmap: Bitmap?) { backgroundBitmap = bitmap; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let { bitmap -> backgroundDestination.set(0, 0, width, height); canvas.drawBitmap(bitmap, null, backgroundDestination, null) }
        val cached = ensureStrokeCache()
        if (cached != null) canvas.drawBitmap(cached, 0f, 0f, null) else strokes.forEach { drawStroke(canvas, it) }
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
        if (lassoPoints.size >= 2) {
            lassoPath.reset()
            lassoPath.moveTo(lassoPoints.first().x * width, lassoPoints.first().y * height)
            for (index in 1 until lassoPoints.size) lassoPath.lineTo(lassoPoints[index].x * width, lassoPoints[index].y * height)
            paint.style = Paint.Style.STROKE; paint.color = Color.rgb(98, 98, 98); paint.alpha = 220; paint.strokeWidth = 2f
            canvas.drawPath(lassoPath, paint)
        }
        drawShapePreview(canvas)
        if (currentPoints.isNotEmpty()) drawStroke(canvas, makeStroke(currentPoints))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width != oldWidth || height != oldHeight) { strokeCache?.recycle(); strokeCache = null; strokeCacheDirty = true }
    }

    override fun onDetachedFromWindow() {
        strokeCache?.recycle(); strokeCache = null; strokeCacheDirty = true
        tipCache.values.forEach(Bitmap::recycle); tipCache.clear()
        super.onDetachedFromWindow()
    }

    private fun ensureStrokeCache(): Bitmap? {
        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_CACHE_PIXELS) return null
        val existing = strokeCache
        val bitmap = if (existing == null || existing.width != width || existing.height != height) {
            existing?.recycle()
            runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()?.also { strokeCache = it; strokeCacheDirty = true }
        } else existing
        bitmap ?: return null
        if (strokeCacheDirty) {
            bitmap.eraseColor(Color.TRANSPARENT)
            val cacheCanvas = Canvas(bitmap)
            strokes.forEach { drawStroke(cacheCanvas, it) }
            strokeCacheDirty = false
        }
        return bitmap
    }

    private fun invalidateStrokeCache() { strokeCacheDirty = true; invalidate() }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        if (stroke.points.isEmpty()) return
        val tipPaths = stroke.tipAssetPaths.ifEmpty { listOfNotNull(stroke.tipAssetPath) }
        val tips = tipPaths.mapNotNull { loadTip(it, stroke.hardness, stroke.grainAssetPath, stroke.grainDensity, stroke.grainScale, stroke.grainRotationDegrees, stroke.grainBrightness, stroke.grainContrast, stroke.grainReversed, stroke.grainEmphasized) }
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
            val point = stroke.points.first(); val strokeWidth = stroke.width * scale * taper.firstOrNull().orDefault() * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, point.pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE); val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, point.pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, point.pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * 255).toInt().coerceIn(1, 255); drawLinePoint(canvas, stroke, point.x * width, point.y * height, strokeWidth, alpha)
        } else stroke.points.zipWithNext().forEachIndexed { index, (start, end) ->
            val point = midpoint(start, end); val pressure = point.pressure.coerceIn(0f, 1f)
            val taperFactor = (taper[index] + taper[index + 1]) / 2f
            val strokeWidth = stroke.width * scale * taperFactor * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)
            val alpha = (stroke.opacity * pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY) * stroke.flow * pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW) * 255).toInt().coerceIn(1, 255)
            drawLineSegment(canvas, stroke, start.x * width, start.y * height, end.x * width, end.y * height, strokeWidth, alpha)
        }
    }

    private fun drawStampedStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>, dualTips: List<Bitmap>) {
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
            val tipIndex = BrushTipSequence.index(stroke.tipSequenceMode, index, tips.size, stroke.renderSeed, stroke.ribbon) ?: return@forEachIndexed
            val tip = tips[tipIndex]
            val x = point.x * width; val y = point.y * height
            val pressure = point.pressure.coerceIn(0f, 1f)
            val brushDiameter = (stroke.width * referenceScale * taper[index] * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(.25f)
            val diameter = if (stroke.spray) SprayGeometry.particleDiameter(brushDiameter, stroke.sprayParticleSize, stroke.sprayParticleSizeUnit, stroke.sprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val offset = if (stroke.spray) SprayGeometry.offset(brushDiameter / 2f, scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.sprayBias) else app.inkloom.document.SprayOffset(0f, 0f)
            val drawX = x + offset.x
            val drawY = y + offset.y
            val dynamicThickness = pressureDynamic(stroke.thicknessPressureEnabled, stroke.thicknessPressureMinimum, stroke.thicknessPressureCurve, pressure) * secondaryDynamic(stroke.thicknessSecondaryDynamics, point, stroke.renderSeed, SALT_THICKNESS)
            val thicknessScale = stroke.thickness.coerceIn(.05f, 2f) * dynamicThickness
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, thicknessScale, stroke.thicknessVertical)
            val scaleX = tipScale.x
            val scaleY = tipScale.y
            val angle = stroke.rotationDegrees +
                (if (stroke.followStylusOrientation) Math.toDegrees(point.orientation.toDouble()).toFloat() else 0f) +
                (if (stroke.followStrokeDirection || stroke.ribbon) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f) +
                (rotationRandom.nextFloat() - .5f) * 2f * stroke.randomRotationDegrees.coerceIn(0f, 180f)
            val matrix = Matrix().apply {
                postTranslate(-tip.width / 2f, -tip.height / 2f)
                postScale(scaleX, scaleY)
                postRotate(angle)
                postTranslate(drawX, drawY)
            }
            paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW)
            val mainAlpha = (stroke.opacity * opacityDynamic * stroke.flow * flowDynamic * if (stroke.watercolor) .72f else 1f).coerceIn(.01f, 1f).times(255).toInt()
            if (stroke.waterEdgeEnabled && stroke.waterEdgeOpacity > 0f) {
                val edgeRadius = WaterEdge.distancePixels(stroke.waterEdgeRadius, stroke.waterEdgeRadiusUnit, width.toFloat())
                val edgeTipScale = BrushTipTransform.scale(tip.width, tip.height, diameter + edgeRadius * 2f, thicknessScale, stroke.thicknessVertical)
                val edgeMatrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(edgeTipScale.x, edgeTipScale.y); postRotate(angle); postTranslate(drawX, drawY) }
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
        if (stroke.dualBrush && dualTips.isNotEmpty()) drawDualStampedStroke(canvas, stroke, dualTips)
        paint.colorFilter = null
    }

    /** Draws the imported secondary tip as its own deterministic stamp stream.
     * Unknown CSP composite-mode numbers are retained in the document model; until their
     * semantics are verified this pass deliberately uses Android SRC_OVER. */
    private fun drawDualStampedStroke(canvas: Canvas, stroke: InkStroke, tips: List<Bitmap>) {
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
            val tipIndex = BrushTipSequence.index(stroke.dualTipSequenceMode, index, tips.size, stroke.renderSeed xor DUAL_SEQUENCE_SEED, stroke.dualRibbon) ?: return@forEachIndexed
            val tip = tips[tipIndex]
            val pressure = point.pressure.coerceIn(0f, 1f)
            val brushDiameter = (stroke.width * referenceScale * stroke.dualSizeScale.coerceIn(.01f, 8f) * taper[index] * pressureDynamic(stroke.sizePressureEnabled, stroke.sizePressureMinimum, stroke.sizePressureCurve, pressure) * secondaryDynamic(stroke.sizeSecondaryDynamics, point, stroke.renderSeed, SALT_SIZE)).coerceAtLeast(.25f)
            val diameter = if (stroke.dualSpray) SprayGeometry.particleDiameter(brushDiameter, stroke.dualSprayParticleSize, stroke.dualSprayParticleSizeUnit, stroke.dualSprayParticleSizeSyncBrushSize, width.toFloat()) else brushDiameter
            val offset = if (stroke.dualSpray) SprayGeometry.offset(brushDiameter / 2f, scatterRandom.nextFloat(), scatterRandom.nextFloat(), stroke.dualSprayBias) else app.inkloom.document.SprayOffset(0f, 0f)
            val drawX = point.x * width + offset.x
            val drawY = point.y * height + offset.y
            val dynamicThickness = pressureDynamic(stroke.thicknessPressureEnabled, stroke.thicknessPressureMinimum, stroke.thicknessPressureCurve, pressure) * secondaryDynamic(stroke.thicknessSecondaryDynamics, point, stroke.renderSeed, SALT_THICKNESS)
            val tipScale = BrushTipTransform.scale(tip.width, tip.height, diameter, stroke.dualThickness.coerceIn(.05f, 2f) * dynamicThickness, stroke.dualThicknessVertical)
            val angle = stroke.dualRotationDegrees +
                (if (stroke.dualFollowStylusOrientation) Math.toDegrees(point.orientation.toDouble()).toFloat() else 0f) +
                (if (stroke.dualFollowStrokeDirection || stroke.dualRibbon) StrokeStampSampler.directionDegrees(sampledPoints, index, width.toFloat(), height.toFloat()) else 0f) +
                (rotationRandom.nextFloat() - .5f) * 2f * stroke.dualRandomRotationDegrees.coerceIn(0f, 180f)
            val matrix = Matrix().apply { postTranslate(-tip.width / 2f, -tip.height / 2f); postScale(tipScale.x, tipScale.y); postRotate(angle); postTranslate(drawX, drawY) }
            val opacityDynamic = pressureDynamic(stroke.opacityPressureEnabled, stroke.opacityPressureMinimum, stroke.opacityPressureCurve, pressure) * secondaryDynamic(stroke.opacitySecondaryDynamics, point, stroke.renderSeed, SALT_OPACITY)
            val flowDynamic = pressureDynamic(stroke.flowPressureEnabled, stroke.flowPressureMinimum, stroke.flowPressureCurve, pressure) * secondaryDynamic(stroke.flowSecondaryDynamics, point, stroke.renderSeed, SALT_FLOW)
            paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
            paint.alpha = (stroke.opacity * opacityDynamic * stroke.dualFlow.coerceIn(0f, 1f) * flowDynamic * if (stroke.watercolor) .72f else 1f).coerceIn(.01f, 1f).times(255).toInt()
            canvas.drawBitmap(tip, matrix, paint)
        }
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

    private fun loadTip(path: String, hardness: Float, grainPath: String?, grainDensity: Float, grainScale: Float = 100f, grainRotation: Float = 0f, grainBrightness: Float = 0f, grainContrast: Float = 0f, grainReversed: Boolean = false, grainEmphasized: Boolean = false): Bitmap? {
        val key = "$path:${(hardness.coerceIn(0f, 1f) * 20).toInt()}:$grainPath:${(grainDensity.coerceIn(0f, 1f) * 20).toInt()}:${grainScale.toInt()}:${grainRotation.toInt()}:${grainBrightness.toInt()}:${grainContrast.toInt()}:$grainReversed:$grainEmphasized"
        return tipCache[key] ?: BrushTipTextureDecoder.decode(path, hardness, grainPath, grainDensity, grainScale, grainRotation, grainBrightness, grainContrast, grainReversed, grainEmphasized)?.also { tipCache[key] = it }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (tool !in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SELECT, EditorTool.SHAPE)) return false
        val pointerIndex = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val eventToolType = event.getToolType(pointerIndex)
        val isStylus = eventToolType == MotionEvent.TOOL_TYPE_STYLUS || eventToolType == MotionEvent.TOOL_TYPE_ERASER
        if (isStylus) lastStylusSeenAt = event.eventTime
        onInputDiagnostic(when (eventToolType) { MotionEvent.TOOL_TYPE_STYLUS -> "Stylus"; MotionEvent.TOOL_TYPE_ERASER -> "Eraser"; MotionEvent.TOOL_TYPE_FINGER -> "Finger"; else -> "Other" }, event.pressure, event.getAxisValue(MotionEvent.AXIS_TILT), event.buttonState)
        val elapsedSinceStylus = if (lastStylusSeenAt == Long.MIN_VALUE) Long.MAX_VALUE else event.eventTime - lastStylusSeenAt
        val isFingerNavigation = StylusInputClassifier.shouldNavigateOrRejectFinger(isStylus, fingerDrawingEnabled, event.pointerCount, event.getTouchMajor(pointerIndex), elapsedSinceStylus)
        if (isFingerNavigation) {
            if (activeTool != null) return true
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { navigationX = event.x; navigationY = event.y }
                MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2 && twoFingerDoubleTapUndo) {
                    val now = event.eventTime
                    if (now - lastTwoFingerTap in 40..360) { onUndoGesture(); lastTwoFingerTap = 0L } else lastTwoFingerTap = now
                }
                MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    onViewportPan(event.x - navigationX, event.y - navigationY)
                    navigationX = event.x; navigationY = event.y
                }
            }
            return true
        }
        val normalizedX = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val normalizedY = (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activeTool = resolveTool(event, eventToolType)
                if (activeTool == EditorTool.ERASER) {
                    beginEraserGesture()
                    eraseAlong(normalizedX, normalizedY)
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
                        selectedStrokeIds.clear(); movingSelection = false
                        appendLassoPoint(normalizedX, normalizedY)
                    }
                    lastX = normalizedX; lastY = normalizedY
                    invalidate(); return true
                }
                if (activeTool == EditorTool.SHAPE) {
                    shapeStart = ShapePoint(normalizedX, normalizedY)
                    shapeEnd = shapeStart
                    invalidate(); return true
                }
                currentPoints.clear(); currentStrokeId = java.util.UUID.randomUUID().toString(); addPoint(normalizedX, normalizedY, event.pressure, event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeTool == EditorTool.ERASER) {
                    for (i in 0 until event.historySize) eraseAlong(event.getHistoricalX(i) / width.coerceAtLeast(1), event.getHistoricalY(i) / height.coerceAtLeast(1))
                    eraseAlong(normalizedX, normalizedY)
                }
                else if (activeTool == EditorTool.SELECT) {
                    if (movingSelection) {
                        val dx = normalizedX - lastX; val dy = normalizedY - lastY
                        if (hypot(dx.toDouble(), dy.toDouble()) > .0001) selectionMoved = true
                        selectedStrokeIds.forEach { id ->
                            val index = strokes.indexOfFirst { it.id == id }
                            if (index >= 0) strokes[index] = strokes[index].copy(points = strokes[index].points.map { it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) })
                        }
                        lastX = normalizedX; lastY = normalizedY
                        strokeCacheDirty = true
                    } else {
                        for (i in 0 until event.historySize) appendLassoPoint(event.getHistoricalX(i) / width.coerceAtLeast(1), event.getHistoricalY(i) / height.coerceAtLeast(1))
                        appendLassoPoint(normalizedX, normalizedY)
                    }
                }
                else if (activeTool == EditorTool.SHAPE) shapeEnd = ShapeGeometry.snapEnd(shapeStart ?: ShapePoint(normalizedX, normalizedY), ShapePoint(normalizedX, normalizedY), pageModelWidth, pageModelHeight)
                else {
                    for (i in 0 until event.historySize) addPoint(event.getHistoricalX(i) / width, event.getHistoricalY(i) / height, event.getHistoricalPressure(i), event.getHistoricalEventTime(i), event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, i), event.getHistoricalOrientation(i))
                    addPoint(normalizedX, normalizedY, event.pressure, event.eventTime, event.getAxisValue(MotionEvent.AXIS_TILT), event.orientation)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    currentPoints.clear()
                    shapeStart = null; shapeEnd = null
                    if (activeTool == EditorTool.ERASER) cancelEraserGesture()
                    selectionGestureBefore?.let { restoreStrokeSnapshot(it) }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null; strokeCacheDirty = true
                } else if (activeTool == EditorTool.ERASER) {
                    eraseAlong(normalizedX, normalizedY)
                    finishEraserGesture()
                } else if (activeTool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER) && currentPoints.isNotEmpty()) {
                    val before = strokes.toList()
                    val stroke = makeStroke(currentPoints)
                    strokes += stroke
                    strokeCacheDirty = true
                    commitStrokeEdit(before, strokes)
                    currentPoints.clear()
                } else if (activeTool == EditorTool.SELECT) {
                    if (movingSelection && selectionMoved) {
                        selectionGestureBefore?.let { commitStrokeEdit(it, strokes) }
                    } else if (!movingSelection) {
                        appendLassoPoint(normalizedX, normalizedY)
                        selectWithLasso(lassoPoints)
                    }
                    lassoPoints.clear(); movingSelection = false; selectionMoved = false; selectionGestureBefore = null
                } else if (activeTool == EditorTool.SHAPE) {
                    val start = shapeStart
                    val end = shapeEnd
                    if (start != null && end != null) ShapeGeometry.create(shapeKind, start, end, pageModelWidth, pageModelHeight, inkColor.toUInt().toLong(), baseWidth)?.let(onShapeCommitted)
                    shapeStart = null; shapeEnd = null
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                activeTool = null
            }
        }
        invalidate()
        return true
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
        }
        canvas.restore()
        paint.alpha = 255
    }

    private fun appendLassoPoint(x: Float, y: Float) {
        val point = StrokePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val previous = lassoPoints.lastOrNull()
        if (previous == null || hypot((point.x - previous.x).toDouble(), (point.y - previous.y).toDouble()) > .0015) lassoPoints += point
    }

    private fun selectWithLasso(polygon: List<StrokePoint>) {
        if (polygon.size < 3) return
        val before = strokes.toList()
        val rebuilt = mutableListOf<InkStroke>()
        val selected = linkedSetOf<String>()
        var splitOccurred = false
        before.forEach { stroke ->
            if (stroke.id in protectedStrokeIds) {
                rebuilt += stroke
            } else {
                val partition = LassoGeometry.partition(polygon, stroke)
                splitOccurred = splitOccurred || partition.changed
                partition.pieces.forEach { piece ->
                    rebuilt += piece.stroke
                    if (piece.selected) selected += piece.stroke.id
                }
            }
        }
        selectedStrokeIds.clear()
        selectedStrokeIds += selected
        if (splitOccurred) {
            strokes.clear()
            strokes += rebuilt
            commitStrokeEdit(before, strokes)
            invalidateStrokeCache()
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

    private fun makeStroke(points: List<StrokePoint>) = InkStroke(id = currentStrokeId, points = points.toList(), colorArgb = inkColor.toUInt().toLong(), width = baseWidth, opacity = inkOpacity, brushId = brushId, layerId = activeLayerId, tipAssetPath = tipAssetPath, tipAssetPaths = tipAssetPaths.ifEmpty { listOfNotNull(tipAssetPath) }, dualTipAssetPaths = dualTipAssetPaths, grainAssetPath = grainAssetPath, grainDensity = grainDensity, grainScale = grainScale, grainRotationDegrees = grainRotationDegrees, grainBrightness = grainBrightness, grainContrast = grainContrast, grainReversed = grainReversed, grainEmphasized = grainEmphasized, spacing = brushSpacing, flow = brushFlow, hardness = brushHardness, thickness = brushThickness, thicknessVertical = brushThicknessVertical, rotationDegrees = brushRotation, followStylusOrientation = followStylusOrientation, followStrokeDirection = followStrokeDirection, randomRotationDegrees = randomRotationDegrees, watercolor = brushWatercolor, colorMix = brushColorMix, paintMix = brushPaintMix, spray = brushSpray, sprayParticleSize = sprayParticleSize, sprayParticleSizeUnit = sprayParticleSizeUnit, sprayParticleSizeSyncBrushSize = sprayParticleSizeSyncBrushSize, sprayDensity = sprayDensity, sprayBias = sprayBias, ribbon = brushRibbon, tipSequenceMode = brushTipSequenceMode, dualBrush = brushDual, dualSizeScale = dualSizeScale, dualSpacing = dualSpacing, dualFlow = dualFlow, dualHardness = dualHardness, dualThickness = dualThickness, dualThicknessVertical = dualThicknessVertical, dualRotationDegrees = dualRotationDegrees, dualFollowStylusOrientation = dualFollowStylusOrientation, dualFollowStrokeDirection = dualFollowStrokeDirection, dualRandomRotationDegrees = dualRandomRotationDegrees, dualRibbon = dualRibbon, dualSpray = dualSpray, dualSprayParticleSize = dualSprayParticleSize, dualSprayParticleSizeUnit = dualSprayParticleSizeUnit, dualSprayParticleSizeSyncBrushSize = dualSprayParticleSizeSyncBrushSize, dualSprayDensity = dualSprayDensity, dualSprayBias = dualSprayBias, dualTipSequenceMode = dualTipSequenceMode, dualCompositeMode = dualCompositeMode, taperMode = taperMode, taperStartEnabled = taperStartEnabled, taperStartLength = taperStartLength, taperStartLengthUnit = taperStartLengthUnit, taperStartMinimum = taperStartMinimum, taperEndEnabled = taperEndEnabled, taperEndLength = taperEndLength, taperEndLengthUnit = taperEndLengthUnit, taperEndMinimum = taperEndMinimum, waterEdgeEnabled = waterEdgeEnabled, waterEdgeRadius = waterEdgeRadius, waterEdgeRadiusUnit = waterEdgeRadiusUnit, waterEdgeOpacity = waterEdgeOpacity, waterEdgeDarkness = waterEdgeDarkness, waterEdgeBlur = waterEdgeBlur, waterEdgeBlurUnit = waterEdgeBlurUnit, sizePressureEnabled = sizePressureEnabled, sizePressureMinimum = sizePressureMinimum, sizePressureCurve = sizePressureCurve, sizeSecondaryDynamics = sizeSecondaryDynamics, opacityPressureEnabled = opacityPressureEnabled, opacityPressureMinimum = opacityPressureMinimum, opacityPressureCurve = opacityPressureCurve, opacitySecondaryDynamics = opacitySecondaryDynamics, flowPressureEnabled = flowPressureEnabled, flowPressureMinimum = flowPressureMinimum, flowPressureCurve = flowPressureCurve, flowSecondaryDynamics = flowSecondaryDynamics, spacingPressureEnabled = spacingPressureEnabled, spacingPressureMinimum = spacingPressureMinimum, spacingPressureCurve = spacingPressureCurve, spacingSecondaryDynamics = spacingSecondaryDynamics, thicknessPressureEnabled = thicknessPressureEnabled, thicknessPressureMinimum = thicknessPressureMinimum, thicknessPressureCurve = thicknessPressureCurve, thicknessSecondaryDynamics = thicknessSecondaryDynamics)

    private fun beginEraserGesture() {
        eraserGestureBefore = strokes.toList()
        lastEraserX = null
        lastEraserY = null
    }

    private fun eraseAlong(x: Float, y: Float) {
        val targetX = x.coerceIn(0f, 1f)
        val targetY = y.coerceIn(0f, 1f)
        val startX = lastEraserX
        val startY = lastEraserY
        if (startX == null || startY == null) {
            eraseAt(targetX, targetY)
        } else {
            val distancePixels = hypot(((targetX - startX) * width).toDouble(), ((targetY - startY) * height).toDouble()).toFloat()
            val spacing = (eraserRadiusPixels() * .45f).coerceAtLeast(1f)
            val steps = ceil(distancePixels / spacing).toInt().coerceIn(1, MAX_ERASER_SAMPLES_PER_EVENT)
            for (step in 1..steps) {
                val amount = step / steps.toFloat()
                eraseAt(startX + (targetX - startX) * amount, startY + (targetY - startY) * amount)
            }
        }
        lastEraserX = targetX
        lastEraserY = targetY
    }

    private fun eraseAt(x: Float, y: Float) {
        val eraserRadius = eraserRadiusPixels()
        var changed = false
        for (index in strokes.indices.reversed()) {
            val stroke = strokes[index]
            if (stroke.id in protectedStrokeIds) continue
            val strokeRadius = stroke.width.coerceIn(.1f, 800f) * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f
            val combinedRadius = eraserRadius + strokeRadius
            val radiusX = combinedRadius / width.coerceAtLeast(1)
            val radiusY = combinedRadius / height.coerceAtLeast(1)
            if (!StrokeEraser.intersects(stroke, x, y, radiusX, radiusY)) continue
            val pieces = if (localEraser) {
                StrokeEraser.erase(stroke, x, y, radiusX, radiusY)?.replacements ?: continue
            } else {
                emptyList()
            }
            strokes.removeAt(index)
            selectedStrokeIds.remove(stroke.id)
            if (localEraser) strokes.addAll(index, pieces)
            changed = true
        }
        if (changed) invalidateStrokeCache()
    }

    private fun eraserRadiusPixels(): Float =
        (eraserWidth.coerceIn(.5f, 400f) * width.coerceAtLeast(1) / REFERENCE_WIDTH / 2f).coerceAtLeast(1f)

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
        strokes.removeAll { it.id in removableIds }
        selectedStrokeIds.removeAll(removableIds)
        commitStrokeEdit(before, strokes)
        invalidateStrokeCache()
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
        val protectedLayerIds = strokes.filter { it.id in protectedStrokeIds }.mapTo(mutableSetOf()) { it.layerId }
        return (edit.before + edit.after).any { it.stroke.layerId in protectedLayerIds }
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
        strokes.clear()
        strokes.addAll(snapshot)
        selectedStrokeIds.retainAll(snapshot.mapTo(mutableSetOf()) { it.id })
        invalidateStrokeCache()
    }

    fun scaleSelection(factor: Float) = transformSelection { dx, dy -> dx * factor to dy * factor }

    fun rotateSelection(degrees: Float) {
        val radians = Math.toRadians(degrees.toDouble())
        transformSelection { dx, dy -> (dx * cos(radians) - dy * sin(radians)).toFloat() to (dx * sin(radians) + dy * cos(radians)).toFloat() }
    }

    fun copySelection() {
        val before = strokes.toList()
        val copies = strokes.filter { it.id in selectedStrokeIds }.map { original -> original.copy(id = java.util.UUID.randomUUID().toString(), points = original.points.map { it.copy(x = (it.x + .03f).coerceIn(0f, 1f), y = (it.y + .03f).coerceIn(0f, 1f)) }) }
        if (copies.isEmpty()) return
        strokes += copies; selectedStrokeIds.clear(); selectedStrokeIds += copies.map(InkStroke::id); commitStrokeEdit(before, strokes); invalidateStrokeCache()
    }

    fun deleteSelection() {
        if (selectedStrokeIds.isEmpty()) return
        val before = strokes.toList()
        val ids = selectedStrokeIds.toSet(); strokes.removeAll { it.id in ids }; commitStrokeEdit(before, strokes); selectedStrokeIds.clear(); invalidateStrokeCache()
    }

    private fun transformSelection(transform: (Float, Float) -> Pair<Float, Float>) {
        val before = strokes.toList()
        val selected = strokes.filter { it.id in selectedStrokeIds }
        val points = selected.flatMap(InkStroke::points)
        if (points.isEmpty()) return
        val centerX = points.map(StrokePoint::x).average().toFloat(); val centerY = points.map(StrokePoint::y).average().toFloat()
        selected.forEach { current ->
            val changed = current.copy(points = current.points.map { point -> val (x, y) = transform(point.x - centerX, point.y - centerY); point.copy(x = (centerX + x).coerceIn(0f, 1f), y = (centerY + y).coerceIn(0f, 1f)) })
            val currentIndex = strokes.indexOfFirst { it.id == current.id }
            if (currentIndex >= 0) strokes[currentIndex] = changed
        }
        commitStrokeEdit(before, strokes)
        invalidateStrokeCache()
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
        const val DUAL_SEQUENCE_SEED = 0x35A71C9E
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
