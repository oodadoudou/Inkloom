package app.inkloom.document

object StrokeStabilization {
    const val INCREASE_WHEN_SLOW = 0
    const val REDUCE_WHEN_FAST = 1

    fun smoothing(base: Float, adjustBySpeed: Boolean, mode: Int, velocity: Float): Float {
        val value = base.coerceIn(0f, .9f)
        if (!adjustBySpeed || value <= 0f) return value
        val speed = velocity.coerceIn(0f, 1f)
        return when (mode) {
            INCREASE_WHEN_SLOW -> (value + (1f - speed) * value * .6f).coerceIn(0f, .9f)
            else -> (value * (1f - speed * .75f)).coerceIn(0f, .9f)
        }
    }
}
