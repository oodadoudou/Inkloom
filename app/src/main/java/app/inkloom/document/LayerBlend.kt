package app.inkloom.document

import android.graphics.BlendMode

/** Maps an Inkloom layer blend mode to the closest Android [BlendMode].
 *  Kept out of [DocumentModels] so the model layer stays free of Android types. */
fun LayerBlendMode.toBlendMode(): BlendMode = when (this) {
    LayerBlendMode.NORMAL -> BlendMode.SRC_OVER
    LayerBlendMode.MULTIPLY -> BlendMode.MULTIPLY
    LayerBlendMode.SCREEN -> BlendMode.SCREEN
    LayerBlendMode.ADD -> BlendMode.PLUS
    LayerBlendMode.OVERLAY -> BlendMode.OVERLAY
    LayerBlendMode.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
    LayerBlendMode.HARD_LIGHT -> BlendMode.HARD_LIGHT
    LayerBlendMode.COLOR_DODGE -> BlendMode.COLOR_DODGE
    LayerBlendMode.COLOR_BURN -> BlendMode.COLOR_BURN
    LayerBlendMode.LIGHTEN -> BlendMode.LIGHTEN
    LayerBlendMode.DARKEN -> BlendMode.DARKEN
    LayerBlendMode.DIFFERENCE -> BlendMode.DIFFERENCE
    LayerBlendMode.EXCLUSION -> BlendMode.EXCLUSION
    LayerBlendMode.HUE -> BlendMode.HUE
    LayerBlendMode.SATURATION -> BlendMode.SATURATION
    LayerBlendMode.COLOR -> BlendMode.COLOR
    LayerBlendMode.LUMINOSITY -> BlendMode.LUMINOSITY
}
