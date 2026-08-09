package app.inkloom.engine.v2.input

import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import app.inkloom.document.ViewportState
import kotlin.math.cos
import kotlin.math.sin

/** Maps complete pointer packets from the stable editor viewport into page-local coordinates.
 * MotionEvent.transform also preserves alignment for every pointer and historical sample. */
internal class PointerCoordinateMapper {
    private val sourceToTarget = Matrix()
    private val viewportTransform = Matrix()
    private val viewportToPage = Matrix()
    private val viewportValues = FloatArray(9)

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
        sourceToTarget.reset()
        sourceToTarget.setTranslate(
            (target.width - source.width) / 2f,
            (target.height - source.height) / 2f,
        )
        event.transform(sourceToTarget)
        viewport?.let {
            if (!configureViewportInverse(target.width.toFloat(), target.height.toFloat(), it)) return false
            event.transform(viewportToPage)
        }
        return true
    }

    private fun configureViewportInverse(width: Float, height: Float, viewport: ViewportState): Boolean {
        val safeWidth = width.coerceAtLeast(1f)
        val safeHeight = height.coerceAtLeast(1f)
        val centerX = safeWidth / 2f
        val centerY = safeHeight / 2f
        val scale = viewport.scale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val rotation = Math.toRadians((viewport.rotationDegrees.takeIf(Float::isFinite) ?: 0f).toDouble())
        val cosine = cos(rotation).toFloat()
        val sine = sin(rotation).toFloat()
        val a = scale * cosine
        val b = -scale * sine
        val d = scale * sine
        val e = scale * cosine
        val panX = viewport.panX.takeIf(Float::isFinite) ?: 0f
        val panY = viewport.panY.takeIf(Float::isFinite) ?: 0f
        viewportValues[0] = a
        viewportValues[1] = b
        viewportValues[2] = centerX + panX - a * centerX - b * centerY
        viewportValues[3] = d
        viewportValues[4] = e
        viewportValues[5] = centerY + panY - d * centerX - e * centerY
        viewportValues[6] = 0f
        viewportValues[7] = 0f
        viewportValues[8] = 1f
        viewportTransform.setValues(viewportValues)
        return viewportTransform.invert(viewportToPage)
    }
}
