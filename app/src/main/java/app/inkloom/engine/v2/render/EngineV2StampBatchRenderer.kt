package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushStampRotation
import app.inkloom.document.BrushTipTransform
import app.inkloom.document.GrainDynamics
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeStampSampler
import app.inkloom.document.StrokeTaper
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
    ): Boolean {
        if (!eligible(stroke) || sampledPoints.isEmpty()) return false
        val safeStart = startIndex.coerceIn(0, sampledPoints.size)
        val dabCount = sampledPoints.size - safeStart
        if (dabCount <= 0) return true
        val vertices = FloatArray(dabCount * VERTEX_FLOATS_PER_DAB)
        val textureCoordinates = FloatArray(vertices.size)
        val taper = StrokeTaper.factors(
            sampledPoints,
            width,
            height,
            stroke.taperMode,
            stroke.taperStartEnabled,
            stroke.taperStartLength,
            stroke.taperStartLengthUnit,
            stroke.taperStartMinimum,
            stroke.taperEndEnabled,
            stroke.taperEndLength,
            stroke.taperEndLengthUnit,
            stroke.taperEndMinimum,
        )
        val rotationRandom = java.util.Random(stroke.renderSeed.toLong() xor ROTATION_SEED)
        repeat(safeStart) { rotationRandom.nextFloat() }
        var offset = 0
        for (index in safeStart until sampledPoints.size) {
            val point = sampledPoints[index]
            val dynamics = EngineV2BrushEvaluator.evaluateDab(stroke, point)
            val diameter = (stroke.width * width.coerceAtLeast(1f) / REFERENCE_WIDTH * taper[index] * dynamics.size).coerceAtLeast(.25f)
            val tipScale = BrushTipTransform.scale(
                tip.width,
                tip.height,
                diameter,
                stroke.thickness.coerceIn(.05f, 2f) * dynamics.thickness,
                stroke.thicknessVertical,
            )
            val followsStroke = BrushStampRotation.followsStroke(stroke, dual = false)
            val angle = BrushStampRotation.degrees(
                stroke,
                dual = false,
                stylusOrientationRadians = point.orientation,
                strokeDirectionDegrees = if (followsStroke) StrokeStampSampler.directionDegrees(sampledPoints, index, width, height) else 0f,
                randomUnit = rotationRandom.nextFloat(),
            )
            writeQuad(
                vertices,
                textureCoordinates,
                offset,
                point.x * width,
                point.y * height,
                tip.width * tipScale.x,
                tip.height * tipScale.y,
                angle,
                tip.width.toFloat(),
                tip.height.toFloat(),
            )
            offset += VERTEX_FLOATS_PER_DAB
        }
        paint.shader = BitmapShader(tip, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix())
        }
        paint.colorFilter = PorterDuffColorFilter(stroke.colorArgb.toInt(), PorterDuff.Mode.SRC_IN)
        paint.alpha = (stroke.flow.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            vertices.size,
            vertices,
            0,
            textureCoordinates,
            0,
            null,
            0,
            null,
            0,
            0,
            paint,
        )
        paint.shader = null
        paint.colorFilter = null
        return true
    }

    fun eligible(stroke: InkStroke): Boolean =
        !stroke.dualBrush && !stroke.spray && !stroke.watercolor && !stroke.waterEdgeEnabled &&
            !stroke.blendTipsWithDarken && stroke.blur <= .001f && !GrainDynamics.isDynamic(stroke) &&
            stroke.stampCount == 1 && stroke.stampCountJitter <= .001f &&
            stroke.tipFlipHorizontalMode == 0 && stroke.tipFlipVerticalMode == 0 &&
            !stroke.opacityPressureEnabled && !stroke.flowPressureEnabled &&
            !secondary(stroke.opacitySecondaryDynamics) && !secondary(stroke.flowSecondaryDynamics) &&
            noColorDynamics(stroke.colorJitterHueDynamics) && noColorDynamics(stroke.colorJitterSaturationDynamics) &&
            noColorDynamics(stroke.colorJitterValueDynamics) && stroke.colorJitterHueDegrees == 0f &&
            stroke.colorJitterSaturation == 0f && stroke.colorJitterValue == 0f &&
            stroke.colorMix <= .001f && stroke.paintMix <= .001f

    private fun secondary(value: app.inkloom.document.BrushSecondaryDynamics): Boolean =
        value.tiltEnabled || value.velocityEnabled || value.randomEnabled

    private fun noColorDynamics(value: BrushColorDynamics): Boolean =
        !value.pressureEnabled && !value.tiltEnabled && !value.velocityEnabled && !value.randomEnabled

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
        val positions = floatArrayOf(
            x(left, top), y(left, top), x(right, top), y(right, top), x(right, bottom), y(right, bottom),
            x(left, top), y(left, top), x(right, bottom), y(right, bottom), x(left, bottom), y(left, bottom),
        )
        val texture = floatArrayOf(
            0f, 0f, textureWidth, 0f, textureWidth, textureHeight,
            0f, 0f, textureWidth, textureHeight, 0f, textureHeight,
        )
        positions.copyInto(vertices, offset)
        texture.copyInto(textureCoordinates, offset)
    }

    private const val VERTEX_FLOATS_PER_DAB = 12
    private const val REFERENCE_WIDTH = 1600f
    private const val ROTATION_SEED = 0x49A2B31DL
}
