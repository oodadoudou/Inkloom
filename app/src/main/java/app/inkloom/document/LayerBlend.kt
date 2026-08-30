package app.inkloom.document

import android.graphics.BlendMode
import app.inkloom.engine.v5.composite.PixelBlendMode

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

/** Platform-independent V5 mapping shared by live projection and export. */
fun LayerBlendMode.toPixelBlendMode(): PixelBlendMode = when (this) {
    LayerBlendMode.NORMAL -> PixelBlendMode.NORMAL
    LayerBlendMode.MULTIPLY -> PixelBlendMode.MULTIPLY
    LayerBlendMode.SCREEN -> PixelBlendMode.SCREEN
    LayerBlendMode.ADD -> PixelBlendMode.ADD
    LayerBlendMode.OVERLAY -> PixelBlendMode.OVERLAY
    LayerBlendMode.SOFT_LIGHT -> PixelBlendMode.SOFT_LIGHT
    LayerBlendMode.HARD_LIGHT -> PixelBlendMode.HARD_LIGHT
    LayerBlendMode.COLOR_DODGE -> PixelBlendMode.COLOR_DODGE
    LayerBlendMode.COLOR_BURN -> PixelBlendMode.COLOR_BURN
    LayerBlendMode.LIGHTEN -> PixelBlendMode.LIGHTEN
    LayerBlendMode.DARKEN -> PixelBlendMode.DARKEN
    LayerBlendMode.DIFFERENCE -> PixelBlendMode.DIFFERENCE
    LayerBlendMode.EXCLUSION -> PixelBlendMode.EXCLUSION
    LayerBlendMode.HUE -> PixelBlendMode.HUE
    LayerBlendMode.SATURATION -> PixelBlendMode.SATURATION
    LayerBlendMode.COLOR -> PixelBlendMode.COLOR
    LayerBlendMode.LUMINOSITY -> PixelBlendMode.LUMINOSITY
}
