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
        var points = input
        repeat(iterations) {
            points = points.mapIndexed { index, point ->
                if (index == 0 || index == points.lastIndex) point else {
                    val previous = points[index - 1]
                    val next = points[index + 1]
                    point.copy(
                        x = previous.x * .25f + point.x * .5f + next.x * .25f,
                        y = previous.y * .25f + point.y * .5f + next.y * .25f,
                        pressure = previous.pressure * .2f + point.pressure * .6f + next.pressure * .2f,
                    )
                }
            }
        }
        return points
    }

    private fun simplify(input: List<StrokePoint>, tolerancePx: Float, pageWidth: Float, pageHeight: Float): List<StrokePoint> {
        if (input.size < 3) return input
        val first = input.first()
        val last = input.last()
        var furthestIndex = -1
        var furthestDistance = 0f
        for (index in 1 until input.lastIndex) {
            val distance = segmentDistance(input[index], first, last, pageWidth, pageHeight)
            if (distance > furthestDistance) { furthestDistance = distance; furthestIndex = index }
        }
        if (furthestDistance <= tolerancePx || furthestIndex < 0) return listOf(first, last)
        val left = simplify(input.subList(0, furthestIndex + 1), tolerancePx, pageWidth, pageHeight)
        val right = simplify(input.subList(furthestIndex, input.size), tolerancePx, pageWidth, pageHeight)
        return left.dropLast(1) + right
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
