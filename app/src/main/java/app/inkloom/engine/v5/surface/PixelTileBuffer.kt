package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.NativeRasterOps

internal sealed interface PixelTileBuffer {
    val format: PixelTileFormat
    val size: Int
    val byteSize: Long get() = size.toLong() * format.bytesPerPixel
    fun copyBuffer(): PixelTileBuffer
    fun contentEquals(other: PixelTileBuffer): Boolean
    fun nonTransparentPixelCount(): Int
    fun premultipliedArgbAt(index: Int): Int
    fun premultipliedLinearAt(index: Int): LinearRgba
}

internal class Argb8888TileBuffer(val values: IntArray) : PixelTileBuffer {
    override val format: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_SRGB_8888
    override val size: Int get() = values.size
    override fun copyBuffer(): PixelTileBuffer = Argb8888TileBuffer(values.copyOf())
    override fun contentEquals(other: PixelTileBuffer): Boolean =
        other is Argb8888TileBuffer && values.contentEquals(other.values)
    override fun nonTransparentPixelCount(): Int =
        NativeRasterOps.nonTransparentPixelCount(values).takeIf { it >= 0 }
            ?: values.count { it ushr 24 != 0 }
    override fun premultipliedArgbAt(index: Int): Int = values[index]
    override fun premultipliedLinearAt(index: Int): LinearRgba =
        PackedRgbaF16.premultiplied(PackedRgbaF16.fromPremultipliedArgb8888(values[index]))
}

internal class RgbaF16TileBuffer(val values: LongArray) : PixelTileBuffer {
    override val format: PixelTileFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16
    override val size: Int get() = values.size
    override fun copyBuffer(): PixelTileBuffer = RgbaF16TileBuffer(values.copyOf())
    override fun contentEquals(other: PixelTileBuffer): Boolean =
        other is RgbaF16TileBuffer && values.contentEquals(other.values)
    override fun nonTransparentPixelCount(): Int = values.count { !PackedRgbaF16.isTransparent(it) }
    override fun premultipliedArgbAt(index: Int): Int = PackedRgbaF16.toPremultipliedArgb8888(values[index])
    override fun premultipliedLinearAt(index: Int): LinearRgba = PackedRgbaF16.premultiplied(values[index])
}

internal fun emptyPixelTileBuffer(format: PixelTileFormat, size: Int): PixelTileBuffer = when (format) {
    PixelTileFormat.PREMULTIPLIED_SRGB_8888 -> Argb8888TileBuffer(IntArray(size))
    PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16 -> RgbaF16TileBuffer(LongArray(size))
}
