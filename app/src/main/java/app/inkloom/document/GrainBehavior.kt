package app.inkloom.document

data class GrainOffset(val x: Float, val y: Float)

object GrainBehavior {
    fun offset(renderSeed: Int, enabled: Boolean): GrainOffset {
        if (!enabled) return GrainOffset(0f, 0f)
        val random = java.util.Random(renderSeed.toLong() xor OFFSET_SEED)
        return GrainOffset(random.nextInt(PHASES) / PHASES.toFloat(), random.nextInt(PHASES) / PHASES.toFloat())
    }

    private const val OFFSET_SEED = 0x4A72C9E1L
    private const val PHASES = 64
}
