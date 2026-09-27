package app.inkloom.engine.v5.composite

import app.inkloom.document.LayerColorReplacement
import app.inkloom.engine.v5.surface.PackedRgbaF16

/** Per-layer view transform. All methods preserve premultiplied alpha and never edit source tiles. */
object PixelLayerColor {
    fun replaceRgba8(source: Int, color: LayerColorReplacement): Int {
        val alpha = source ushr 24 and 0xff
        if (alpha == 0) return 0
        val red = ((source ushr 16 and 0xff) * 255f / alpha).coerceIn(0f, 255f)
        val green = ((source ushr 8 and 0xff) * 255f / alpha).coerceIn(0f, 255f)
        val blue = ((source and 0xff) * 255f / alpha).coerceIn(0f, 255f)
        val gray = ((.2126f * red + .7152f * green + .0722f * blue) / 255f).coerceIn(0f, 1f)
        val dark = color.darkArgb.toInt()
        val light = color.lightArgb.toInt()
        fun channel(shift: Int): Int {
            val d = dark ushr shift and 0xff
            val l = light ushr shift and 0xff
            return ((d + (l - d) * gray) * alpha / 255f + .5f).toInt().coerceIn(0, alpha)
        }
        return (alpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun replaceF16(source: Long, color: LayerColorReplacement): Long {
        val alpha = PackedRgbaF16.alpha(source)
        if (alpha <= 0f) return 0L
        val straight = PackedRgbaF16.straight(source)
        val gray = (.2126f * straight.red + .7152f * straight.green + .0722f * straight.blue).coerceIn(0f, 1f)
        fun linear(argb: Long): Long = PackedRgbaF16.fromPremultipliedArgb8888(argb.toInt() or (0xff shl 24))
        val dark = linear(color.darkArgb)
        val light = linear(color.lightArgb)
        fun channel(d: Float, l: Float) = (d + (l - d) * gray) * alpha
        return PackedRgbaF16.packPremultiplied(
            channel(PackedRgbaF16.premultipliedRed(dark), PackedRgbaF16.premultipliedRed(light)),
            channel(PackedRgbaF16.premultipliedGreen(dark), PackedRgbaF16.premultipliedGreen(light)),
            channel(PackedRgbaF16.premultipliedBlue(dark), PackedRgbaF16.premultipliedBlue(light)),
            alpha,
        )
    }
}
