package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlin.math.pow

/** Procreate's six Rendering > Rendering Mode contracts. The archive stores a three-flag transfer
 * program rather than a public enum; the mapping below preserves those flags without treating
 * their private names as Android Porter-Duff operations. */
@Serializable
enum class ProcreateRenderingMode {
    LIGHT_GLAZE,
    UNIFORM_GLAZE,
    INTENSE_GLAZE,
    HEAVY_GLAZE,
    UNIFORM_BLENDING,
    INTENSE_BLENDING,
}

object ProcreateRendering {
    fun fromArchive(maxTransfer: Boolean, modulatedTransfer: Boolean, recursiveMixing: Boolean): ProcreateRenderingMode =
        when {
            recursiveMixing && maxTransfer -> ProcreateRenderingMode.HEAVY_GLAZE
            recursiveMixing && modulatedTransfer -> ProcreateRenderingMode.UNIFORM_BLENDING
            recursiveMixing -> ProcreateRenderingMode.INTENSE_BLENDING
            maxTransfer -> ProcreateRenderingMode.LIGHT_GLAZE
            modulatedTransfer -> ProcreateRenderingMode.UNIFORM_GLAZE
            else -> ProcreateRenderingMode.INTENSE_GLAZE
        }

    fun usesMaximumTransfer(mode: ProcreateRenderingMode): Boolean =
        mode == ProcreateRenderingMode.LIGHT_GLAZE || mode == ProcreateRenderingMode.HEAVY_GLAZE

    fun usesRecursiveMixing(mode: ProcreateRenderingMode): Boolean = when (mode) {
        ProcreateRenderingMode.HEAVY_GLAZE,
        ProcreateRenderingMode.UNIFORM_BLENDING,
        ProcreateRenderingMode.INTENSE_BLENDING -> true
        else -> false
    }

    /** Calibrated source-over transfer from the official lightest-to-strongest ordering. Uniform
     * Glaze is the identity because Procreate documents it as the Photoshop-style transfer. */
    fun depositedAlpha(mode: ProcreateRenderingMode, alpha: Float): Float {
        val normalized = alpha.coerceIn(0f, 1f)
        val exponent = when (mode) {
            ProcreateRenderingMode.LIGHT_GLAZE -> .65f
            ProcreateRenderingMode.UNIFORM_GLAZE -> 1f
            ProcreateRenderingMode.INTENSE_GLAZE -> 1.25f
            ProcreateRenderingMode.HEAVY_GLAZE -> 1.5f
            ProcreateRenderingMode.UNIFORM_BLENDING -> 1.75f
            ProcreateRenderingMode.INTENSE_BLENDING -> 2f
        }
        return 1f - (1f - normalized).pow(exponent)
    }

    /** Relative recursive pigment interaction; zero keeps ordinary glaze brushes out of the
     * pixel mixer. Wet Mix sliders still determine whether any mixing occurs. */
    fun wetMixStrength(mode: ProcreateRenderingMode): Float = when (mode) {
        ProcreateRenderingMode.HEAVY_GLAZE -> .72f
        ProcreateRenderingMode.UNIFORM_BLENDING -> 1f
        ProcreateRenderingMode.INTENSE_BLENDING -> 1.35f
        else -> 0f
    }
}
