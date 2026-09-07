package app.inkloom.ui

import android.graphics.Color

/** Picker coordinates retain intent even when RGB cannot represent hue (gray) or saturation (black). */
internal data class ColorPickerDraft private constructor(
    val color: Int,
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun withHsv(
        hue: Float = this.hue,
        saturation: Float = this.saturation,
        value: Float = this.value,
    ): ColorPickerDraft {
        val h = hue.coerceIn(0f, 360f)
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        return ColorPickerDraft(Color.HSVToColor(Color.alpha(color), floatArrayOf(h, s, v)), h, s, v)
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
        )
    }

    companion object {
        fun fromColor(color: Int): ColorPickerDraft {
            val hsv = FloatArray(3).also { Color.colorToHSV(color, it) }
            return ColorPickerDraft(color, hsv[0], hsv[1], hsv[2])
        }
    }
}
