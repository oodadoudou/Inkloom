package app.inkloom.document

import android.graphics.BlendMode

/**
 * Procreate brush archives store their Rendering > Blend Mode fields using the CoreGraphics
 * `CGBlendMode` raw-value table. The first sixteen values are the artistic blend modes; values
 * outside that range are retained in the document model but deliberately fall back to Normal.
 *
 * This is separate from [LayerBlendMode]: a brush mode composites one completed brushstroke,
 * while a layer mode composites the complete layer.
 */
object ProcreateBrushBlend {
    const val NORMAL = 0
    const val MULTIPLY = 1
    const val SCREEN = 2
    const val OVERLAY = 3
    const val DARKEN = 4
    const val LIGHTEN = 5
    const val COLOR_DODGE = 6
    const val COLOR_BURN = 7
    const val SOFT_LIGHT = 8
    const val HARD_LIGHT = 9
    const val DIFFERENCE = 10
    const val EXCLUSION = 11
    const val HUE = 12
    const val SATURATION = 13
    const val COLOR = 14
    const val LUMINOSITY = 15

    val artisticModes: IntRange = NORMAL..LUMINOSITY

    fun normalize(raw: Int): Int = raw.takeIf { it in artisticModes } ?: NORMAL

    fun resolve(raw: Int): BlendMode = when (normalize(raw)) {
        MULTIPLY -> BlendMode.MULTIPLY
        SCREEN -> BlendMode.SCREEN
        OVERLAY -> BlendMode.OVERLAY
        DARKEN -> BlendMode.DARKEN
        LIGHTEN -> BlendMode.LIGHTEN
        COLOR_DODGE -> BlendMode.COLOR_DODGE
        COLOR_BURN -> BlendMode.COLOR_BURN
        SOFT_LIGHT -> BlendMode.SOFT_LIGHT
        HARD_LIGHT -> BlendMode.HARD_LIGHT
        DIFFERENCE -> BlendMode.DIFFERENCE
        EXCLUSION -> BlendMode.EXCLUSION
        HUE -> BlendMode.HUE
        SATURATION -> BlendMode.SATURATION
        COLOR -> BlendMode.COLOR
        LUMINOSITY -> BlendMode.LUMINOSITY
        else -> BlendMode.SRC_OVER
    }

    fun label(raw: Int): String = when (normalize(raw)) {
        MULTIPLY -> "Multiply"
        SCREEN -> "Screen"
        OVERLAY -> "Overlay"
        DARKEN -> "Darken"
        LIGHTEN -> "Lighten"
        COLOR_DODGE -> "Color Dodge"
        COLOR_BURN -> "Color Burn"
        SOFT_LIGHT -> "Soft Light"
        HARD_LIGHT -> "Hard Light"
        DIFFERENCE -> "Difference"
        EXCLUSION -> "Exclusion"
        HUE -> "Hue"
        SATURATION -> "Saturation"
        COLOR -> "Color"
        LUMINOSITY -> "Luminosity"
        else -> "Normal"
    }
}
