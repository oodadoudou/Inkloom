package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushStampRotation
import app.inkloom.document.BrushTipTransform
import app.inkloom.document.GrainDynamics
import app.inkloom.document.GrainBehavior
import app.inkloom.document.InkStroke
import app.inkloom.document.ShapeRoundnessJitter
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeStampSampler
import app.inkloom.document.StrokeTaper
import app.inkloom.document.StrokeFalloff
import app.inkloom.document.StrokePathJitter
import app.inkloom.engine.v2.brush.EngineV2BrushEvaluator
import kotlin.math.cos
import kotlin.math.sin

/** One Canvas submission for all ordinary single-tip dabs in a frame. */
internal object EngineV2StampBatchRenderer {
    fun draw(
        canvas: Canvas,
        paint: Paint,
        stroke: InkStroke,
        tip: Bitmap,
        sampledPoints: List<StrokePoint>,
        startIndex: Int,
        width: Float,
        height: Float,
        containsLocalPigment: Boolean = stroke.points.any { it.pigmentArgb != null },
    ): Boolean {
        if (!eligible(stroke, containsLocalPigment) || sampledPoints.isEmpty()) return false
        val safeStart = startIndex.coerceIn(0, sampledPoints.size)
        val dabCount = sampledPoints.size - safeStart
        if (dabCount <= 0) return true
        val floatCount = dabCount * VERTEX_FLOATS_PER_DAB
        val buffers = (threadBuffers.get() ?: Buffers().also(threadBuffers::set)).also { it.ensureCapacity(floatCount) }
        val vertices = buffers.vertices
        val textureCoordinates = buffers.textureCoordinates
        val falloff = StrokeFalloff.factors(sampledPoints, width, height, stroke.width * width / REFERENCE_WIDTH, stroke.strokeFalloff)
        val dynamicAlpha = hasDynamicAlpha(stroke) || falloff != null
        val clip = canvas.clipBounds
        val taper = if (stroke.taperStartEnabled || stroke.taperEndEnabled) StrokeTaper.factors(
            sampledPoints, width, height, stroke.taperMode,
            stroke.taperStartEnabled, stroke.taperStartLength, stroke.taperStartLengthUnit, stroke.taperStartMinimum,
            stroke.taperEndEnabled, stroke.taperEndLength, stroke.taperEndLengthUnit, stroke.taperEndMinimum,
        ) else null
        var offset = 0
        for (index in safeStart until sampledPoints.size) {
            val point = sampledPoints[index]
            val baseX = point.x * width
            val baseY = point.y * height
            val dynamics = EngineV2BrushEvaluator.evaluateDab(stroke, point)
            val depositedAlpha = (stroke.flow * dynamics.depositedAlpha * (falloff?.get(index) ?: 1f)).coerceIn(0f, 1f)
            if (depositedAlpha <= .0001f) continue
            val diameter = (stroke.width * width.coerceAtLeast(1f) / REFERENCE_WIDTH * (taper?.get(index) ?: 1f) * dynamics.size).coerceAtLeast(.25f)
            val roundness = ShapeRoundnessJitter.factors(stroke.renderSeed, index, stroke.shapeRoundnessHorizontalJitter, stroke.shapeRoundnessVerticalJitter)
            val tipScale = BrushTipTransform.scale(
                tip.width,
                tip.height,
                diameter,
                stroke.thickness.coerceIn(.05f, 2f) * dynamics.thickness,
                stroke.thicknessVertical,
                roundness.horizontal,
                roundness.vertical,
            )
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = false)
            val hasPathJitter = stroke.strokePathJitter > .0001f || stroke.strokePathLongitudinalJitter > .0001f
            val strokeDirection = if (followsStroke || hasPathJitter) StrokeStampSampler.directionDegrees(sampledPoints, index, width, height) else 0f
            val pathOffset = StrokePathJitter.offset(stroke.renderSeed, index, strokeDirection, diameter, stroke.strokePathJitter, stroke.strokePathLongitudinalJitter)
            val drawX = baseX + StrokePathJitter.unpackX(pathOffset)
            val drawY = baseY + StrokePathJitter.unpackY(pathOffset)
            val angle = BrushStampRotation.degrees(
                stroke,
                dual = false,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) strokeDirection else 0f,
                randomUnit = legacyRandomFloatAt(stroke.renderSeed.toLong() xor ROTATION_SEED, index),
            )
            // Use the transformed tip diagonal so wide and rotated imported tips are never
            // clipped by the tile optimization.
            val influenceRadius = .5f * kotlin.math.hypot(
                tip.width * tipScale.x,
                tip.height * tipScale.y,
            )
            if (drawX + influenceRadius < clip.left || drawX - influenceRadius > clip.right ||
                drawY + influenceRadius < clip.top || drawY - influenceRadius > clip.bottom
            ) continue
            writeQuad(
                vertices,
                textureCoordinates,
                offset,
                drawX,
                drawY,
                tip.width * tipScale.x,
                tip.height * tipScale.y,
                angle,
                tip.width.toFloat(),
                tip.height.toFloat(),
            )
            if (dynamicAlpha) {
                val vertexColor = Color.argb((depositedAlpha * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
                val colorOffset = offset / 2
                for (vertex in 0 until VERTICES_PER_DAB) buffers.colors[colorOffset + vertex] = vertexColor
            }
            offset += VERTEX_FLOATS_PER_DAB
        }
        if (offset == 0) return true
        paint.shader = BitmapShader(tip, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
        paint.alpha = if (dynamicAlpha) 255 else (stroke.flow.coerceIn(0f, 1f) * 255).toInt()
        drawVertices(canvas, paint, vertices, textureCoordinates, if (dynamicAlpha) buffers.colors else null, offset)
        paint.shader = null
        paint.colorFilter = null
        return true
    }

    fun eligible(stroke: InkStroke): Boolean = eligible(stroke, stroke.points.any { it.pigmentArgb != null })

    private fun eligible(stroke: InkStroke, containsLocalPigment: Boolean): Boolean =
        !stroke.dualBrush && !stroke.spray && !stroke.watercolor && !stroke.waterEdgeEnabled &&
            stroke.tiltGradation <= .001f && stroke.tiltBleed <= .001f && !stroke.tiltSizeCompression &&
            !containsLocalPigment &&
            !stroke.blendTipsWithDarken && stroke.blur <= .001f && !GrainDynamics.isDynamic(stroke) &&
            !GrainBehavior.requiresPerDabSampling(stroke.grainMovement, stroke.grainZoom) &&
            !GrainBehavior.requiresPerDabRotation(stroke.grainStrokeRotation) &&
            stroke.stampCount == 1 && stroke.stampCountJitter <= .001f &&
            stroke.tipFlipHorizontalMode == 0 && stroke.tipFlipVerticalMode == 0 &&
            noColorDynamics(stroke.colorJitterHueDynamics) && noColorDynamics(stroke.colorJitterSaturationDynamics) &&
            noColorDynamics(stroke.colorJitterValueDynamics) && stroke.colorJitterHueDegrees == 0f &&
            stroke.colorJitterSaturation == 0f && stroke.colorJitterValue == 0f &&
            stroke.colorMix <= .001f && stroke.paintMix <= .001f

    private fun hasDynamicAlpha(stroke: InkStroke): Boolean =
        stroke.opacityPressureEnabled || stroke.flowPressureEnabled ||
            secondary(stroke.opacitySecondaryDynamics) || secondary(stroke.flowSecondaryDynamics)

    private fun drawVertices(
        canvas: Canvas,
        paint: Paint,
        vertices: FloatArray,
        textureCoordinates: FloatArray,
        colors: IntArray?,
        floatCount: Int,
    ) {
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            floatCount,
            vertices,
            0,
            textureCoordinates,
            0,
            colors,
            0,
            null,
            0,
            0,
            paint,
        )
    }

    private fun secondary(value: app.inkloom.document.BrushSecondaryDynamics): Boolean =
        value.tiltEnabled || value.velocityEnabled || value.randomEnabled

    private fun noColorDynamics(value: BrushColorDynamics): Boolean =
        !value.pressureEnabled && !value.tiltEnabled && !value.velocityEnabled && !value.randomEnabled

    /** Jump directly to java.util.Random's Nth float so incremental drawing stays compatible with
     * existing strokes without replaying every preceding dab on every display frame. */
    internal fun legacyRandomFloatAt(seed: Long, sampleIndex: Int): Float {
        var steps = sampleIndex.toLong().coerceAtLeast(0L) + 1L
        var multiplier = JAVA_RANDOM_MULTIPLIER
        var addend = JAVA_RANDOM_ADDEND
        var accumulatedMultiplier = 1L
        var accumulatedAddend = 0L
        while (steps > 0L) {
            if (steps and 1L != 0L) {
                accumulatedAddend = (multiplier * accumulatedAddend + addend) and JAVA_RANDOM_MASK
                accumulatedMultiplier = (multiplier * accumulatedMultiplier) and JAVA_RANDOM_MASK
            }
            addend = (addend * (multiplier + 1L)) and JAVA_RANDOM_MASK
            multiplier = (multiplier * multiplier) and JAVA_RANDOM_MASK
            steps = steps ushr 1
        }
        val initial = (seed xor JAVA_RANDOM_MULTIPLIER) and JAVA_RANDOM_MASK
        val state = (accumulatedMultiplier * initial + accumulatedAddend) and JAVA_RANDOM_MASK
        return (state ushr 24).toFloat() / (1 shl 24).toFloat()
    }

    private fun writeQuad(
        vertices: FloatArray,
        textureCoordinates: FloatArray,
        offset: Int,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotationDegrees: Float,
        textureWidth: Float,
        textureHeight: Float,
    ) {
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        fun x(localX: Float, localY: Float) = centerX + localX * cosine - localY * sine
        fun y(localX: Float, localY: Float) = centerY + localX * sine + localY * cosine
        val left = -width / 2f
        val right = width / 2f
        val top = -height / 2f
        val bottom = height / 2f
        val leftTopX = x(left, top); val leftTopY = y(left, top)
        val rightTopX = x(right, top); val rightTopY = y(right, top)
        val rightBottomX = x(right, bottom); val rightBottomY = y(right, bottom)
        val leftBottomX = x(left, bottom); val leftBottomY = y(left, bottom)
        vertices[offset] = leftTopX; vertices[offset + 1] = leftTopY
        vertices[offset + 2] = rightTopX; vertices[offset + 3] = rightTopY
        vertices[offset + 4] = rightBottomX; vertices[offset + 5] = rightBottomY
        vertices[offset + 6] = leftTopX; vertices[offset + 7] = leftTopY
        vertices[offset + 8] = rightBottomX; vertices[offset + 9] = rightBottomY
        vertices[offset + 10] = leftBottomX; vertices[offset + 11] = leftBottomY
        textureCoordinates[offset] = 0f; textureCoordinates[offset + 1] = 0f
        textureCoordinates[offset + 2] = textureWidth; textureCoordinates[offset + 3] = 0f
        textureCoordinates[offset + 4] = textureWidth; textureCoordinates[offset + 5] = textureHeight
        textureCoordinates[offset + 6] = 0f; textureCoordinates[offset + 7] = 0f
        textureCoordinates[offset + 8] = textureWidth; textureCoordinates[offset + 9] = textureHeight
        textureCoordinates[offset + 10] = 0f; textureCoordinates[offset + 11] = textureHeight
    }

    private class Buffers {
        var vertices = FloatArray(0)
        var textureCoordinates = FloatArray(0)
        var colors = IntArray(0)

        fun ensureCapacity(required: Int) {
            if (vertices.size >= required) return
            var capacity = vertices.size.coerceAtLeast(192)
            while (capacity < required) capacity *= 2
            vertices = FloatArray(capacity)
            textureCoordinates = FloatArray(capacity)
            colors = IntArray(capacity / 2)
        }
    }

    private const val VERTEX_FLOATS_PER_DAB = 12
    private const val VERTICES_PER_DAB = 6
    private const val REFERENCE_WIDTH = 1600f
    private const val ROTATION_SEED = 0x49A2B31DL
    private const val JAVA_RANDOM_MULTIPLIER = 0x5DEECE66DL
    private const val JAVA_RANDOM_ADDEND = 0xBL
    private const val JAVA_RANDOM_MASK = (1L shl 48) - 1L
    private val threadBuffers = ThreadLocal.withInitial(::Buffers)
}
