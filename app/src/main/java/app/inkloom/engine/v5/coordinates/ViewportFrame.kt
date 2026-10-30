package app.inkloom.engine.v5.coordinates

import android.graphics.Matrix
import app.inkloom.document.ViewportState
import kotlin.math.cos
import kotlin.math.sin

@JvmInline
value class ViewportFrameId(val value: Long)

data class ViewportPoint(val x: Float, val y: Float)
data class PagePoint(val x: Float, val y: Float)

/**
 * One affine truth shared by viewport input and active-stroke rendering.
 *
 * Page coordinates are the untransformed pixel coordinates used by [app.inkloom.ui.InkCanvasView].
 * Viewport coordinates are physical pixels in the stable editor input/render plane. Keeping the
 * forward and inverse coefficients together prevents cursor, input and front-buffer code from
 * independently reconstructing a nearly-identical transform.
 */
internal class ViewportFrame private constructor(
    val id: ViewportFrameId,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val pageWidth: Float,
    val pageHeight: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val panX: Float,
    val panY: Float,
    private val pageToViewportValues: FloatArray,
    private val viewportToPageValues: FloatArray,
) {
    fun pageToViewportMatrix(destination: Matrix = Matrix()): Matrix =
        destination.apply { setValues(pageToViewportValues) }

    fun viewportToPageMatrix(destination: Matrix = Matrix()): Matrix =
        destination.apply { setValues(viewportToPageValues) }

    fun pageToViewport(point: PagePoint): ViewportPoint {
        val mapped = floatArrayOf(point.x, point.y)
        pageToViewportMatrix().mapPoints(mapped)
        return ViewportPoint(mapped[0], mapped[1])
    }

    fun viewportToPage(point: ViewportPoint): PagePoint {
        val mapped = floatArrayOf(point.x, point.y)
        viewportToPageMatrix().mapPoints(mapped)
        return PagePoint(mapped[0], mapped[1])
    }

    override fun toString(): String =
        "ViewportFrame(id=${id.value}, viewport=${viewportWidth}x$viewportHeight, page=${pageWidth}x$pageHeight, scale=$scale, rotation=$rotationDegrees, pan=($panX,$panY))"

    companion object {
        fun create(
            id: ViewportFrameId,
            viewportWidth: Float,
            viewportHeight: Float,
            pageWidth: Float,
            pageHeight: Float,
            viewport: ViewportState,
        ): ViewportFrame? {
            if (!viewportWidth.isFinite() || !viewportHeight.isFinite() || !pageWidth.isFinite() || !pageHeight.isFinite()) return null
            if (viewportWidth <= 0f || viewportHeight <= 0f || pageWidth <= 0f || pageHeight <= 0f) return null
            val scale = viewport.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
            val rotationDegrees = viewport.rotationDegrees.takeIf(Float::isFinite) ?: 0f
            val rotation = Math.toRadians(rotationDegrees.toDouble())
            val cosine = cos(rotation).toFloat()
            val sine = sin(rotation).toFloat()
            // A view-only mirror flips page x about the page centre before scale and rotation,
            // matching the editor's graphicsLayer with a negative horizontal scale.
            val mirror = if (viewport.mirrored) -1f else 1f
            val a = scale * cosine * mirror
            val b = -scale * sine
            val d = scale * sine * mirror
            val e = scale * cosine
            val pageCenterX = pageWidth / 2f
            val pageCenterY = pageHeight / 2f
            val viewportCenterX = viewportWidth / 2f
            val viewportCenterY = viewportHeight / 2f
            val panX = viewport.panX.takeIf(Float::isFinite) ?: 0f
            val panY = viewport.panY.takeIf(Float::isFinite) ?: 0f
            val forward = floatArrayOf(
                a, b, viewportCenterX + panX - a * pageCenterX - b * pageCenterY,
                d, e, viewportCenterY + panY - d * pageCenterX - e * pageCenterY,
                0f, 0f, 1f,
            )
            val inverseMatrix = Matrix()
            if (!Matrix().apply { setValues(forward) }.invert(inverseMatrix)) return null
            val inverse = FloatArray(9)
            inverseMatrix.getValues(inverse)
            return ViewportFrame(
                id, viewportWidth, viewportHeight, pageWidth, pageHeight,
                scale, rotationDegrees, panX, panY, forward, inverse,
            )
        }
    }
}
