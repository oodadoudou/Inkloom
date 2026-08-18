package app.inkloom.document

object BrushDynamics {
    fun pressure(enabled: Boolean, minimum: Float, curve: List<BrushCurvePoint>, input: Float): Float {
        if (!enabled) return 1f
        val value = curve(curve, input.coerceIn(0f, 1f))
        return (minimum.coerceIn(0f, 1f) + (1f - minimum.coerceIn(0f, 1f)) * value).coerceIn(.01f, 1.5f)
    }

    fun curve(points: List<BrushCurvePoint>, input: Float): Float {
        if (points.isEmpty()) return input.coerceIn(0f, 1f)
        if (input <= points.first().x) return points.first().y
        if (input >= points.last().x) return points.last().y
        val endIndex = points.indexOfFirst { it.x >= input }.coerceAtLeast(1)
        val start = points[endIndex - 1]
        val end = points[endIndex]
        val amount = ((input - start.x) / (end.x - start.x).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
        return start.y + (end.y - start.y) * amount
    }

    fun secondary(dynamics: BrushSecondaryDynamics, point: StrokePoint, strokeSeed: Int, salt: Int): Float {
        var factor = 1f
        // A not-yet-established sensor must stay neutral (factor 1), never a numeric zero fed
        // through a response curve as an extreme value. The first modeled sample of a stroke is
        // not established (no history), so velocity/tilt dynamics cannot pop its first dab.
        if (dynamics.tiltEnabled && point.hasSensor(BrushSensorFlags.TILT)) {
            val tiltInput = BrushTilt.response(point, dynamics.tiltStart)
            factor *= ranged(dynamics.tiltMinimum, curve(dynamics.tiltCurve, tiltInput))
        }
        if (dynamics.velocityEnabled && point.hasSensor(BrushSensorFlags.VELOCITY)) {
            val velocityInput = (point.velocity * dynamics.velocityScale.coerceIn(0f, 10f)).coerceIn(0f, 1f)
            factor *= ranged(dynamics.velocityMinimum, curve(dynamics.velocityCurve, velocityInput))
        }
        if (dynamics.randomEnabled) {
            factor *= ranged(dynamics.randomMinimum, deterministicRandom(strokeSeed, point.timeMs, salt))
        }
        return factor.coerceIn(.001f, 1.5f)
    }

    fun deterministicRandom(strokeSeed: Int, timeMs: Long, salt: Int): Float {
        var value = strokeSeed.toLong() xor (timeMs * 0x9E3779B1L) xor (salt.toLong() shl 32)
        value = value xor (value ushr 33)
        value *= -49064778989728563L
        value = value xor (value ushr 33)
        return ((value ushr 40) and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()
    }

    private fun ranged(minimum: Float, input: Float): Float {
        val floor = minimum.coerceIn(0f, 1f)
        return floor + (1f - floor) * input.coerceIn(0f, 1f)
    }

}
