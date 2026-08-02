package app.inkloom.document

object WetMixDynamics {
    /**
     * Deterministic glaze approximation shared by the live canvas and exports.
     * Paint load increases deposited pigment while color mixing keeps more of the
     * underlying mark visible. This does not pretend to simulate fluid transport.
     */
    fun alphaMultiplier(watercolor: Boolean, colorMix: Float, paintMix: Float): Float {
        if (!watercolor) return 1f
        val pigmentLoad = .35f + .55f * paintMix.coerceIn(0f, 1f)
        val underlyingColorRetention = 1f - .25f * colorMix.coerceIn(0f, 1f)
        return (pigmentLoad * underlyingColorRetention).coerceIn(.2f, .9f)
    }
}
