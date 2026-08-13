package app.inkloom.engine.v2.input

import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import app.inkloom.document.ViewportState
import kotlin.math.cos
import kotlin.math.sin

/**
 * One affine truth shared by viewport input and active-stroke rendering.
 *
 * Page coordinates are the untransformed pixel coordinates used by [app.inkloom.ui.InkCanvasView].
 * Viewport coordinates are physical pixels in the stable editor input/render plane. Keeping the
 * forward and inverse coefficients together prevents cursor, input and front-buffer code from
 * independently reconstructing a nearly-identical transform.
 */
internal class CanonicalViewportTransform private constructor(
    val pageToViewportValues: FloatArray,
    val viewportToPageValues: FloatArray,
) {
    fun pageToViewportMatrix(destination: Matrix = Matrix()): Matrix =
        destination.apply { setValues(pageToViewportValues) }

    fun viewportToPageMatrix(destination: Matrix = Matrix()): Matrix =
        destination.apply { setValues(viewportToPageValues) }

    fun pageToViewport(x: Float, y: Float): Pair<Float, Float> {
        val point = floatArrayOf(x, y)
        pageToViewportMatrix().mapPoints(point)
        return point[0] to point[1]
    }

    fun viewportToPage(x: Float, y: Float): Pair<Float, Float> {
        val point = floatArrayOf(x, y)
        viewportToPageMatrix().mapPoints(point)
        return point[0] to point[1]
    }

    companion object {
        fun create(
            viewportWidth: Float,
            viewportHeight: Float,
            pageWidth: Float,
            pageHeight: Float,
            viewport: ViewportState,
        ): CanonicalViewportTransform? {
            if (!viewportWidth.isFinite() || !viewportHeight.isFinite() || !pageWidth.isFinite() || !pageHeight.isFinite()) return null
            if (viewportWidth <= 0f || viewportHeight <= 0f || pageWidth <= 0f || pageHeight <= 0f) return null
            val scale = viewport.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
            val rotation = Math.toRadians((viewport.rotationDegrees.takeIf(Float::isFinite) ?: 0f).toDouble())
            val cosine = cos(rotation).toFloat()
            val sine = sin(rotation).toFloat()
            val a = scale * cosine
            val b = -scale * sine
            val d = scale * sine
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
            return CanonicalViewportTransform(forward, inverse)
        }
    }
}

/** Maps complete pointer packets from the stable editor viewport into page-local coordinates.
 * MotionEvent.transform also preserves alignment for every pointer and historical sample. */
internal class PointerCoordinateMapper {
    private val viewportToPage = Matrix()

    /**
     * Maps a full-editor overlay into the page View through their shared visual centre. Compose
     * renders the page with a graphics layer, but vendor View matrices are inconsistent about
     * whether that RenderNode transform is exposed to transformMatrixToGlobal. Building the one
     * known editor transform here avoids applying zoom/pan twice on some stylus displays.
     */
    fun mapCenteredViewport(
        event: MotionEvent,
        source: View,
        target: View,
        viewport: ViewportState? = null,
    ): Boolean {
        if (source.width <= 0 || source.height <= 0 || target.width <= 0 || target.height <= 0) return false
        val transform = CanonicalViewportTransform.create(
            source.width.toFloat(), source.height.toFloat(),
            target.width.toFloat(), target.height.toFloat(),
            viewport ?: ViewportState(1f, 0f, 0f, 0f),
        ) ?: return false
        event.transform(transform.viewportToPageMatrix(viewportToPage))
        return true
    }

    /** Forward counterpart of [mapCenteredViewport]. Cursor feedback is derived from the exact
     * page point accepted by the canvas instead of from a second, raw-event coordinate path. */
    fun mapPagePointToCenteredViewport(
        pageX: Float,
        pageY: Float,
        source: View,
        target: View,
        viewport: ViewportState,
    ): Pair<Float, Float>? {
        if (source.width <= 0 || source.height <= 0 || target.width <= 0 || target.height <= 0) return null
        return CanonicalViewportTransform.create(
            source.width.toFloat(), source.height.toFloat(),
            target.width.toFloat(), target.height.toFloat(),
            viewport,
        )?.pageToViewport(pageX, pageY)
    }
}
