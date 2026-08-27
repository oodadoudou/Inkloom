package app.inkloom.engine.v5.surface

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTileFormatTest {
    @Test fun halfFloatPreservesFiniteExtendedRangeAndSubnormals() {
        listOf(-4f, -0.25f, 0f, 0.00002f, 0.5f, 1f, 4f, 1024f).forEach { value ->
            val restored = HalfFloat.unpack(HalfFloat.pack(value))
            val tolerance = maxOf(0.000001f, abs(value) / 900f)
            assertTrue("$value became $restored", abs(value - restored) <= tolerance)
        }
    }

    @Test fun rgbaF16StoresPremultipliedLinearHdrWithoutClipping() {
        val packed = PackedRgbaF16.packStraight(LinearRgba(2.5f, .5f, -.1f, .4f))
        val premultiplied = PackedRgbaF16.premultiplied(packed)
        val straight = PackedRgbaF16.straight(packed)

        assertEquals(1f, premultiplied.red, .002f)
        assertEquals(.2f, premultiplied.green, .002f)
        assertEquals(-.04f, premultiplied.blue, .002f)
        assertEquals(.4f, premultiplied.alpha, .002f)
        assertEquals(2.5f, straight.red, .006f)
        assertEquals(.5f, straight.green, .003f)
        assertEquals(-.1f, straight.blue, .003f)
    }

    @Test fun rgba8CompatibilityConversionRetainsPremultipliedColor() {
        val premultiplied = 0x80643219.toInt()
        val restored = PackedRgbaF16.toPremultipliedArgb8888(
            PackedRgbaF16.fromPremultipliedArgb8888(premultiplied),
        )
        assertEquals(0x80, restored ushr 24)
        assertTrue(abs((restored ushr 16 and 0xff) - 0x64) <= 1)
        assertTrue(abs((restored ushr 8 and 0xff) - 0x32) <= 1)
        assertTrue(abs((restored and 0xff) - 0x19) <= 1)
    }
}
