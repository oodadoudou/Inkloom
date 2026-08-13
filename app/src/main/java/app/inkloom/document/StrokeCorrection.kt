package app.inkloom.document

import kotlin.math.hypot

enum class CorrectLineMode { SMOOTH, SIMPLIFY, ADJUST_WIDTH, REDRAW_WIDTH, CONNECT, CONTROL_POINTS }

/** Non-destructive vector equivalents of CSP's line-correction sub tools. */
object StrokeCorrection {
    fun apply(stroke: InkStroke, mode: CorrectLineMode, amount: Float, pageWidth: Float, pageHeight: Float): InkStroke {
        val strength = amount.coerceIn(0f, 1f)
        return when (mode) {
            CorrectLineMode.SMOOTH -> stroke.copy(points = smooth(stroke.points, 1 + (strength * 7f).toInt()))
            CorrectLineMode.SIMPLIFY -> stroke.copy(points = simplify(stroke.points, (1f + strength * 200f), pageWidth, pageHeight))
            CorrectLineMode.ADJUST_WIDTH -> stroke.copy(width = (stroke.width * (.35f + strength * 1.65f)).coerceIn(.5f, 800f))
            CorrectLineMode.REDRAW_WIDTH -> stroke.copy(points = stroke.points.map { point -> point.copy(pressure = (point.pressure * (.35f + strength * 1.65f)).coerceIn(.01f, 1f)) })
            CorrectLineMode.CONNECT, CorrectLineMode.CONTROL_POINTS -> stroke
        }
    }

    private fun smooth(input: List<StrokePoint>, iterations: Int): List<StrokePoint> {
        if (input.size < 3) return input
        val size = input.size
        var x = FloatArray(size) { input[it].x }
        var y = FloatArray(size) { input[it].y }
        var pressure = FloatArray(size) { input[it].pressure }
        var nextX = x.copyOf()
        var nextY = y.copyOf()
        var nextPressure = pressure.copyOf()
        repeat(iterations) {
            for (index in 1 until size - 1) {
                nextX[index] = x[index - 1] * .25f + x[index] * .5f + x[index + 1] * .25f
                nextY[index] = y[index - 1] * .25f + y[index] * .5f + y[index + 1] * .25f
                nextPressure[index] = pressure[index - 1] * .2f + pressure[index] * .6f + pressure[index + 1] * .2f
            }
            x = nextX.also { nextX = x }
            y = nextY.also { nextY = y }
            pressure = nextPressure.also { nextPressure = pressure }
        }
        return input.mapIndexed { index, point -> point.copy(x = x[index], y = y[index], pressure = pressure[index]) }
    }

    private fun simplify(input: List<StrokePoint>, tolerancePx: Float, pageWidth: Float, pageHeight: Float): List<StrokePoint> {
        if (input.size < 3) return input
        val keep = BooleanArray(input.size)
        keep[0] = true
        keep[input.lastIndex] = true
        val stack = IntArray(input.size * 2)
        var stackSize = 0
        stack[stackSize++] = 0
        stack[stackSize++] = input.lastIndex
        while (stackSize > 0) {
            val end = stack[--stackSize]
            val start = stack[--stackSize]
            var furthestIndex = -1
            var furthestDistance = 0f
            for (index in start + 1 until end) {
                val distance = segmentDistance(input[index], input[start], input[end], pageWidth, pageHeight)
                if (distance > furthestDistance) { furthestDistance = distance; furthestIndex = index }
            }
            if (furthestIndex >= 0 && furthestDistance > tolerancePx) {
                keep[furthestIndex] = true
                stack[stackSize++] = start
                stack[stackSize++] = furthestIndex
                stack[stackSize++] = furthestIndex
                stack[stackSize++] = end
            }
        }
        return buildList { input.forEachIndexed { index, point -> if (keep[index]) add(point) } }
    }

    private fun segmentDistance(point: StrokePoint, start: StrokePoint, end: StrokePoint, pageWidth: Float, pageHeight: Float): Float {
        val sx = start.x * pageWidth; val sy = start.y * pageHeight
        val ex = end.x * pageWidth; val ey = end.y * pageHeight
        val px = point.x * pageWidth; val py = point.y * pageHeight
        val lengthSquared = (ex - sx) * (ex - sx) + (ey - sy) * (ey - sy)
        if (lengthSquared <= .0001f) return hypot((px - sx).toDouble(), (py - sy).toDouble()).toFloat()
        val t = (((px - sx) * (ex - sx) + (py - sy) * (ey - sy)) / lengthSquared).coerceIn(0f, 1f)
        return hypot((px - (sx + (ex - sx) * t)).toDouble(), (py - (sy + (ey - sy) * t)).toDouble()).toFloat()
    }
}
