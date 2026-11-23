package app.inkloom.engine.v5.raster

/** Stable within a stroke and on replay, distinct for successive contacts of the same brush. */
fun strokeGrainPhase(transactionId: Long, seed: Int, axis: Int): Float {
    var value = transactionId xor (seed.toLong() shl 32) xor (axis.toLong() * -7046029254386353131L)
    value = (value xor (value ushr 30)) * -4658895280553007687L
    value = (value xor (value ushr 27)) * -7723592293110705685L
    value = value xor (value ushr 31)
    return (value ushr 40).toFloat() / 16777216f
}
