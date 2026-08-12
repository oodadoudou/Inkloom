package app.inkloom.document

import kotlin.math.cos
import kotlin.math.sin

/** Deterministic, axis-independent Stroke Path jitter with no mutable RNG on the input path. */
object StrokePathJitter {
    /** Packs the two Float offsets into one Long, avoiding a per-dab object/array allocation. */
    fun offset(
        renderSeed: Int,
        sequenceIndex: Int,
        directionDegrees: Float,
        diameter: Float,
        lateralAmount: Float,
        longitudinalAmount: Float,
        dual: Boolean = false,
    ): Long {
        if (safeAmount(lateralAmount) <= EPSILON && safeAmount(longitudinalAmount) <= EPSILON) return 0L
        val direction = Math.toRadians(directionDegrees.toDouble())
        val longitudinal = signedUnit(renderSeed, sequenceIndex, LONGITUDINAL_SALT, dual) * safeDiameter(diameter) * safeAmount(longitudinalAmount)
        val lateral = signedUnit(renderSeed, sequenceIndex, LATERAL_SALT, dual) * safeDiameter(diameter) * safeAmount(lateralAmount)
        val x = (cos(direction) * longitudinal - sin(direction) * lateral).toFloat()
        val y = (sin(direction) * longitudinal + cos(direction) * lateral).toFloat()
        return (x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xFFFFFFFFL)
    }

    fun unpackX(offset: Long): Float = Float.fromBits((offset shr 32).toInt())
    fun unpackY(offset: Long): Float = Float.fromBits(offset.toInt())

    fun x(
        renderSeed: Int,
        sequenceIndex: Int,
        directionDegrees: Float,
        diameter: Float,
        lateralAmount: Float,
        longitudinalAmount: Float,
        dual: Boolean = false,
    ): Float = unpackX(offset(renderSeed, sequenceIndex, directionDegrees, diameter, lateralAmount, longitudinalAmount, dual))

    fun y(
        renderSeed: Int,
        sequenceIndex: Int,
        directionDegrees: Float,
        diameter: Float,
        lateralAmount: Float,
        longitudinalAmount: Float,
        dual: Boolean = false,
    ): Float = unpackY(offset(renderSeed, sequenceIndex, directionDegrees, diameter, lateralAmount, longitudinalAmount, dual))

    fun maximumRadius(diameter: Float, lateralAmount: Float, longitudinalAmount: Float): Float =
        safeDiameter(diameter) * kotlin.math.hypot(safeAmount(lateralAmount).toDouble(), safeAmount(longitudinalAmount).toDouble()).toFloat()

    private fun signedUnit(seed: Int, index: Int, salt: Int, dual: Boolean): Float =
        BrushDynamics.deterministicRandom(seed xor if (dual) DUAL_SEED else 0, index.toLong(), salt) * 2f - 1f

    private fun safeAmount(value: Float): Float = value.takeIf(Float::isFinite)?.coerceIn(0f, 4f) ?: 0f
    private fun safeDiameter(value: Float): Float = value.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f

    private const val LONGITUDINAL_SALT = 0x5131
    private const val LATERAL_SALT = 0x6A47
    private const val DUAL_SEED = 0x3D7194A5
    private const val EPSILON = .0001f
}
