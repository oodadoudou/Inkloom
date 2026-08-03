package app.inkloom.document

import android.graphics.BlendMode

/** Native Android equivalents whose equations match CSP's documented mode. */
object CspDualBrushBlendMode {
    fun resolve(code: Int): BlendMode = when (CspDualBrushMode.fromCode(code)) {
        CspDualBrushMode.MULTIPLY -> BlendMode.MULTIPLY
        CspDualBrushMode.ADD_GLOW -> BlendMode.PLUS
        CspDualBrushMode.DARKEN -> BlendMode.DARKEN
        CspDualBrushMode.LIGHTEN -> BlendMode.LIGHTEN
        CspDualBrushMode.SCREEN -> BlendMode.SCREEN
        CspDualBrushMode.OVERLAY -> BlendMode.OVERLAY
        CspDualBrushMode.COLOR_DODGE -> BlendMode.COLOR_DODGE
        CspDualBrushMode.COLOR_BURN -> BlendMode.COLOR_BURN
        else -> BlendMode.SRC_OVER
    }
}
