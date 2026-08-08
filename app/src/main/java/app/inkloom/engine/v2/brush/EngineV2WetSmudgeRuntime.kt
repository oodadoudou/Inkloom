package app.inkloom.engine.v2.brush

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max

/**
 * Per-contact feedback buffer for WetSmudgeEngine. The mutable buffer is disposable; editable
 * stroke segments remain the document truth. Every deposit is visible to the next pickup sample.
 */
internal class EngineV2WetSmudgeRuntime(
    source: Bitmap,
    startX: Float,
    startY: Float,
    private val radiusPx: Float,
    private val pickup: Float,
    private val load: Float,
    private val deposit: Float,
    brushColor: Int,
) : AutoCloseable {
    private val feedback = source.copy(Bitmap.Config.ARGB_8888, true).also { source.recycle() }
    private val canvas = Canvas(feedback)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = radiusPx.coerceAtLeast(.5f) * 2f
        alpha = (deposit.coerceIn(.01f, 1f) * 255f).toInt().coerceIn(1, 255)
    }
    private var lastX = startX.coerceIn(0f, 1f)
    private var lastY = startY.coerceIn(0f, 1f)
    private var reservoir = mix(sample(lastX, lastY), brushColor, load.coerceIn(0f, 1f))

    val color: Int get() = reservoir

    fun move(x: Float, y: Float, brushColor: Int): Int {
        val targetX = x.coerceIn(0f, 1f)
        val targetY = y.coerceIn(0f, 1f)
        reservoir = mix(reservoir, sample(targetX, targetY), pickup.coerceIn(0f, 1f))
        reservoir = mix(reservoir, brushColor, load.coerceIn(0f, 1f))
        paint.color = reservoir
        canvas.drawLine(lastX * feedback.width, lastY * feedback.height, targetX * feedback.width, targetY * feedback.height, paint)
        lastX = targetX
        lastY = targetY
        return reservoir
    }

    private fun sample(normalizedX: Float, normalizedY: Float): Int {
        val centerX = normalizedX * (feedback.width - 1).coerceAtLeast(0)
        val centerY = normalizedY * (feedback.height - 1).coerceAtLeast(0)
        val radius = radiusPx.coerceIn(1f, max(feedback.width, feedback.height).toFloat())
        var alpha = 0L
        var red = 0L
        var green = 0L
        var blue = 0L
        var weight = 0L
        // A bounded 5x5 kernel keeps pickup cost independent of large brush diameter.
        for (row in -2..2) for (column in -2..2) {
            val px = (centerX + column * radius / 2f).toInt().coerceIn(0, feedback.width - 1)
            val py = (centerY + row * radius / 2f).toInt().coerceIn(0, feedback.height - 1)
            val color = feedback.getPixel(px, py)
            val sampleWeight = (3 - max(kotlin.math.abs(column), kotlin.math.abs(row))).toLong()
            alpha += Color.alpha(color) * sampleWeight
            red += Color.red(color) * sampleWeight
            green += Color.green(color) * sampleWeight
            blue += Color.blue(color) * sampleWeight
            weight += sampleWeight
        }
        return Color.argb((alpha / weight).toInt(), (red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
    }

    override fun close() {
        if (!feedback.isRecycled) feedback.recycle()
    }

    companion object {
        internal fun mix(first: Int, second: Int, amount: Float): Int {
            val t = amount.coerceIn(0f, 1f)
            fun channel(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255)
            return Color.argb(
                channel(Color.alpha(first), Color.alpha(second)),
                channel(Color.red(first), Color.red(second)),
                channel(Color.green(first), Color.green(second)),
                channel(Color.blue(first), Color.blue(second)),
            )
        }
    }
}
