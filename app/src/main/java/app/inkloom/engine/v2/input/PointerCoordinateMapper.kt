package app.inkloom.engine.v2.input

import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import app.inkloom.document.ViewportState
import kotlin.math.cos
import kotlin.math.sin

/** Maps a complete pointer packet between View-local coordinate systems through their real
 * global matrices. MotionEvent.transform also maps every pointer and historical sample. */
internal class PointerCoordinateMapper {
    private val sourceToGlobal = Matrix()
    private val targetToGlobal = Matrix()
    private val globalToTarget = Matrix()
    private val viewportToPage = Matrix()
    private val viewportValues = FloatArray(9)

    fun map(
        event: MotionEvent,
        source: View,
        target: View,
        viewport: ViewportState? = null,
    ): Boolean {
        sourceToGlobal.reset()
        source.transformMatrixToGlobal(sourceToGlobal)
        targetToGlobal.reset()
        target.transformMatrixToGlobal(targetToGlobal)
        if (!targetToGlobal.invert(globalToTarget)) return false
        event.transform(sourceToGlobal)
        event.transform(globalToTarget)
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
        targetToGlobal.setValues(viewportValues)
        return targetToGlobal.invert(viewportToPage)
    }
}
