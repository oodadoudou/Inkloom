package app.inkloom.document

import kotlin.math.roundToInt

object RenderedColorSampling {
    fun opaqueOverWhite(argb: Int): Int {
        val alpha = ((argb ushr 24) and 0xFF) / 255f
        if (alpha >= .999f) return argb or 0xFF000000.toInt()
        fun channel(value: Int): Int = (value * alpha + 255f * (1f - alpha)).roundToInt().coerceIn(0, 255)
        val red = channel((argb ushr 16) and 0xFF)
        val green = channel((argb ushr 8) and 0xFF)
        val blue = channel(argb and 0xFF)
        return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }
}
