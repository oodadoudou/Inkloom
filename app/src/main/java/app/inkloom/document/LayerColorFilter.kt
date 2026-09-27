package app.inkloom.document

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/** Android fallback for layers containing page objects; sparse raster projection uses PixelLayerColor. */
fun LayerColorReplacement.toColorFilter(): ColorMatrixColorFilter {
    val dark = darkArgb.toInt()
    val light = lightArgb.toInt()
    val values = FloatArray(20)
    for (row in 0..2) {
        val shift = 16 - 8 * row
        val base = dark ushr shift and 0xff
        val diff = (light ushr shift and 0xff) - base
        values[row * 5] = .2126f * diff / 255f
        values[row * 5 + 1] = .7152f * diff / 255f
        values[row * 5 + 2] = .0722f * diff / 255f
        values[row * 5 + 4] = base.toFloat()
    }
    values[18] = 1f
    return ColorMatrixColorFilter(ColorMatrix(values))
}
