package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.tools.NativeRasterOps
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePremultipliedTileCompositeTest {
    @Test fun nativePixelHashUsesStableArgbByteOrder() {
        val pixels = intArrayOf(0x00000000, 0xff123456.toInt(), 0x80402010.toInt())
        var expected = 14695981039346656037UL
        pixels.forEach { pixel ->
            for (shift in 24 downTo 0 step 8) {
                expected = (expected xor ((pixel ushr shift) and 0xff).toULong()) * 1099511628211UL
            }
        }
        assertTrue(NativeRasterOps.hashPremultipliedPixels(pixels)?.toULong() == expected)
    }

    @Test fun nativeUnpremultiplyMatchesAndroidColorContract() {
        val actual = intArrayOf(0x00000000, 0xff123456.toInt(), 0x80402010.toInt(), 0x40080402)
        val expected = actual.map { pixel ->
            val alpha = pixel ushr 24 and 0xff
            if (alpha == 0 || alpha == 255) pixel else {
                fun channel(value: Int) = ((value * 255 + alpha / 2) / alpha).coerceAtMost(255)
                (alpha shl 24) or
                    (channel(pixel ushr 16 and 0xff) shl 16) or
                    (channel(pixel ushr 8 and 0xff) shl 8) or
                    channel(pixel and 0xff)
            }
        }.toIntArray()

        assertTrue(NativeRasterOps.unpremultiplyInPlace(actual))
        assertArrayEquals(expected, actual)
    }

    @Test fun nativeTileCompositeMatchesReferenceForEveryActiveDeltaBlend() {
        val destinationSeed = intArrayOf(
            0x00000000,
            0xff203040.toInt(),
            0x80402010.toInt(),
            0x40201008,
            0xff102030.toInt(),
            0x80800000.toInt(),
            0x40002000,
            0x20000010,
            0x7f3f2010,
            0xff102030.toInt(),
            0x80402010.toInt(),
            0x40201008,
        )
        val source = intArrayOf(
            0x00000000,
            0xffffffff.toInt(),
            0x80400000.toInt(),
            0x20000800,
            0xff204060.toInt(),
            0x80400000.toInt(),
            0x40002000,
            0x20000010,
            0x7f3f2010,
            0xff102030.toInt(),
            0x80402010.toInt(),
            0x40201008,
        )

        repeat(3) { blendMode ->
            val expected = destinationSeed.copyOf()
            for (y in 1 until 3) for (x in 1 until 4) {
                val index = y * 4 + x
                expected[index] = reference(expected[index], source[index], blendMode)
            }
            val actual = destinationSeed.copyOf()
            assertTrue(
                NativeRasterOps.compositePremultipliedTile(
                    actual,
                    source,
                    stride = 4,
                    left = 1,
                    top = 1,
                    right = 4,
                    bottom = 3,
                    blendMode = blendMode,
                ),
            )
            assertArrayEquals("blendMode=$blendMode", expected, actual)
        }
    }

    private fun reference(destination: Int, source: Int, mode: Int): Int {
        val sourceAlpha = source ushr 24 and 0xff
        return when (mode) {
            0 -> {
                if (sourceAlpha == 0) destination else if (sourceAlpha == 255) source else {
                    val inverse = 255 - sourceAlpha
                    val destinationAlpha = destination ushr 24 and 0xff
                    ((sourceAlpha + destinationAlpha * inverse / 255).coerceAtMost(255) shl 24) or
                        ((((source ushr 16) and 0xff) + ((destination ushr 16) and 0xff) * inverse / 255).coerceAtMost(255) shl 16) or
                        ((((source ushr 8) and 0xff) + ((destination ushr 8) and 0xff) * inverse / 255).coerceAtMost(255) shl 8) or
                        (((source and 0xff) + (destination and 0xff) * inverse / 255).coerceAtMost(255))
                }
            }
            1 -> {
                val keep = 255 - sourceAlpha
                ((((destination ushr 24) and 0xff) * keep / 255) shl 24) or
                    ((((destination ushr 16) and 0xff) * keep / 255) shl 16) or
                    ((((destination ushr 8) and 0xff) * keep / 255) shl 8) or
                    ((destination and 0xff) * keep / 255)
            }
            else -> {
                val destinationAlpha = destination ushr 24 and 0xff
                if (destinationAlpha == 0 || sourceAlpha == 0) destination else {
                    val inverse = 255 - sourceAlpha
                    (destinationAlpha shl 24) or
                        (((((source ushr 16) and 0xff) * destinationAlpha + ((destination ushr 16) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 16) or
                        (((((source ushr 8) and 0xff) * destinationAlpha + ((destination ushr 8) and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha) shl 8) or
                        ((((source and 0xff) * destinationAlpha + (destination and 0xff) * inverse) / 255).coerceAtMost(destinationAlpha))
                }
            }
        }
    }
}
