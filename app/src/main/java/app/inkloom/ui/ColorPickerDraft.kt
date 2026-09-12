package app.inkloom.ui

import android.graphics.Color

/** Picker coordinates retain intent even when RGB cannot represent hue (gray) or saturation (black). */
internal data class ColorPickerDraft private constructor(
    val color: Int,
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val hlsSaturation: Float,
) {
    val lightness get() = value * (1f - saturation * .5f)

    fun withHsv(
        hue: Float = this.hue,
        saturation: Float = this.saturation,
        value: Float = this.value,
    ): ColorPickerDraft {
        val h = hue.coerceIn(0f, 360f)
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        return ColorPickerDraft(Color.HSVToColor(Color.alpha(color), floatArrayOf(h, s, v)), h, s, v, hlsSaturation(s, v, hlsSaturation))
    }

    fun withHls(hue: Float = this.hue, lightness: Float = this.lightness, saturation: Float = hlsSaturation): ColorPickerDraft {
        val h = hue.coerceIn(0f, 360f)
        val l = lightness.coerceIn(0f, 1f)
        val s = saturation.coerceIn(0f, 1f)
        val chroma = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val v = l + chroma * .5f
        val hsvS = if (v > 0f) chroma / v else this.saturation
        return ColorPickerDraft(Color.HSVToColor(Color.alpha(color), floatArrayOf(h, hsvS, v)), h, hsvS, v, s)
    }

    /** External colors replace defined channels, retaining the coordinates undefined by that RGB. */
    fun withColor(color: Int): ColorPickerDraft {
        if (color == this.color) return this
        val hsv = FloatArray(3).also { Color.colorToHSV(color, it) }
        return ColorPickerDraft(
            color,
            if (hsv[1] > 0f && hsv[2] > 0f) hsv[0] else hue,
            if (hsv[2] > 0f) hsv[1] else saturation,
            hsv[2],
            hlsSaturation(hsv[1], hsv[2], hlsSaturation),
        )
    }

    companion object {
        fun fromColor(color: Int): ColorPickerDraft {
            val hsv = FloatArray(3).also { Color.colorToHSV(color, it) }
            return ColorPickerDraft(color, hsv[0], hsv[1], hsv[2], hlsSaturation(hsv[1], hsv[2], 0f))
        }
        private fun hlsSaturation(s: Float, v: Float, undefined: Float): Float {
            val l = v * (1f - s * .5f)
            val denominator = 1f - kotlin.math.abs(2f * l - 1f)
            return if (denominator > .000001f) (v * s / denominator).coerceIn(0f, 1f) else undefined
        }
    }
}

/** Transient picker colors survive viewport/IME recomposition without a preference write per sample. */
internal class DrawingColorPreview {
    var foreground: Int? = null
    var secondary: Int? = null
    fun clear() { foreground = null; secondary = null }
}
