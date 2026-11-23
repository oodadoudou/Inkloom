package app.inkloom.engine.v5.material

import kotlin.math.floor

/** Immutable procedural paper sampled in document pixels, never from the page background. */
class DocumentPaperGrain(
    scale: Float,
    private val density: Float,
    private val strength: Float,
    private val seed: Int,
    private val tiltEnabled: Boolean = false,
    private val tiltStart: Float = 0f,
    private val tiltMaximum: Float = 0f,
) {
    init {
        require(scale.isFinite() && scale > 0f)
        require(density.isFinite() && density in 0f..1f)
        require(strength.isFinite() && strength in 0f..1f)
        require(tiltStart.isFinite() && tiltStart in 0f..1f)
        require(tiltMaximum.isFinite() && tiltMaximum >= 0f)
    }
    private val wavelength = (scale * .04f).coerceAtLeast(1f)

    fun strengthAt(tiltRadians: Float): Float {
        if (!tiltEnabled) return strength
        val normalized = (tiltRadians / (Math.PI * .5).toFloat()).coerceIn(0f, 1f)
        val amount = ((normalized - tiltStart) / (1f - tiltStart).coerceAtLeast(.001f)).coerceIn(0f, 1f)
        return (strength * (1f + amount * tiltMaximum)).coerceIn(0f, 1f)
    }

    fun coverageAt(x: Float, y: Float, attenuation: Float): Float {
        if (attenuation <= 0f || density >= 1f) return 1f
        if (density <= 0f) return 1f - attenuation
        val px = x / wavelength
        val py = y / wavelength
        val ix = floor(px).toInt()
        val iy = floor(py).toInt()
        val fx = px - ix
        val fy = py - iy
        val top = noise(ix, iy) * (1f - fx) + noise(ix + 1, iy) * fx
        val bottom = noise(ix, iy + 1) * (1f - fx) + noise(ix + 1, iy + 1) * fx
        val height = top * (1f - fy) + bottom * fy
        val filled = ((height + density - .9f) / .2f).coerceIn(0f, 1f)
        return 1f - attenuation * (1f - filled)
    }

    private fun noise(x: Int, y: Int): Float {
        var value = x * 0x1f1f1f1f + y * 0x45d9f3b + seed
        value = value xor (value ushr 16)
        value *= 0x45d9f3b
        value = value xor (value ushr 16)
        return (value and 0xffff) / 65535f
    }
}
