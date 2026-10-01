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
    LayerBlendMode.LINEAR_BURN -> error("LINEAR_BURN requires the pixel compositor")
    LayerBlendMode.SUBTRACT -> error("SUBTRACT requires the pixel compositor")
    LayerBlendMode.GLOW_DODGE -> error("GLOW_DODGE requires the pixel compositor")
    LayerBlendMode.ADD_GLOW -> error("ADD_GLOW requires the pixel compositor")
    LayerBlendMode.VIVID_LIGHT -> error("VIVID_LIGHT requires the pixel compositor")
    LayerBlendMode.LINEAR_LIGHT -> error("LINEAR_LIGHT requires the pixel compositor")
    LayerBlendMode.PIN_LIGHT -> error("PIN_LIGHT requires the pixel compositor")
    LayerBlendMode.HARD_MIX -> error("HARD_MIX requires the pixel compositor")
    LayerBlendMode.DARKER_COLOR -> error("DARKER_COLOR requires the pixel compositor")
    LayerBlendMode.LIGHTER_COLOR -> error("LIGHTER_COLOR requires the pixel compositor")
    LayerBlendMode.DIVIDE -> error("DIVIDE requires the pixel compositor")
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
    LayerBlendMode.LINEAR_BURN -> PixelBlendMode.LINEAR_BURN
    LayerBlendMode.SUBTRACT -> PixelBlendMode.SUBTRACT
    LayerBlendMode.GLOW_DODGE -> PixelBlendMode.GLOW_DODGE
    LayerBlendMode.ADD_GLOW -> PixelBlendMode.ADD_GLOW
    LayerBlendMode.VIVID_LIGHT -> PixelBlendMode.VIVID_LIGHT
    LayerBlendMode.LINEAR_LIGHT -> PixelBlendMode.LINEAR_LIGHT
    LayerBlendMode.PIN_LIGHT -> PixelBlendMode.PIN_LIGHT
    LayerBlendMode.HARD_MIX -> PixelBlendMode.HARD_MIX
    LayerBlendMode.DARKER_COLOR -> PixelBlendMode.DARKER_COLOR
    LayerBlendMode.LIGHTER_COLOR -> PixelBlendMode.LIGHTER_COLOR
    LayerBlendMode.DIVIDE -> PixelBlendMode.DIVIDE
}

/** These modes have no exact Skia saveLayer equivalent. */
val LayerBlendMode.requiresPixelCompositor: Boolean get() = this in setOf(
    LayerBlendMode.LINEAR_BURN, LayerBlendMode.SUBTRACT, LayerBlendMode.GLOW_DODGE, LayerBlendMode.ADD_GLOW, LayerBlendMode.VIVID_LIGHT, LayerBlendMode.LINEAR_LIGHT, LayerBlendMode.PIN_LIGHT, LayerBlendMode.HARD_MIX, LayerBlendMode.DARKER_COLOR, LayerBlendMode.LIGHTER_COLOR, LayerBlendMode.DIVIDE
)
