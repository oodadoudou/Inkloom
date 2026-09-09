package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.tools.NativeRasterOps
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativePremultipliedTileCompositeTest {
    @Test fun fusedBitmapWriteMatchesAndroidSetPixelsForEveryAlphaAndChannel() {
        val input = IntArray(65536) { index ->
            val a = index / 256; val value = index % 256
            (a shl 24) or (value shl 16) or ((255 - value) shl 8) or ((value * 73) % 256)
        }
        val preserved = input.copyOf()
        val straight = input.copyOf()
        assertTrue(NativeRasterOps.unpremultiplyInPlace(straight))
        val expected = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        val actual = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            expected.setPixels(straight, 0, 256, 0, 0, 256, 256)
            assertTrue(NativeRasterOps.writePremultipliedBitmap(actual, input))
            val expectedPixels = IntArray(input.size); val actualPixels = IntArray(input.size)
            expected.getPixels(expectedPixels, 0, 256, 0, 0, 256, 256)
            actual.getPixels(actualPixels, 0, 256, 0, 0, 256, 256)
            assertArrayEquals(expectedPixels, actualPixels)
            assertArrayEquals(preserved, input)
        } finally { expected.recycle(); actual.recycle() }
    }

    @Test fun nativePigmentPickupMatchesExactFloatReference() {
        for ((width, height) in listOf(1 to 1, 3 to 5, 26 to 26, 37 to 29, 256 to 64)) {
            val pixels = IntArray(width * height) { i -> i * 1664525 + 1013904223 }
            for (buckets in listOf(2, 8, 32)) {
                val expected = FloatArray(buckets * buckets * 4)
                for (row in 0 until buckets) for (col in 0 until buckets) {
                    val top = kotlin.math.floor(row * height / buckets.toFloat()).toInt().coerceIn(0, height - 1)
                    val bottom = kotlin.math.ceil((row + 1) * height / buckets.toFloat()).toInt().coerceIn(top + 1, height)
                    val left = kotlin.math.floor(col * width / buckets.toFloat()).toInt().coerceIn(0, width - 1)
                    val right = kotlin.math.ceil((col + 1) * width / buckets.toFloat()).toInt().coerceIn(left + 1, width)
                    val index = (row * buckets + col) * 4
                    for (y in top until bottom) for (x in left until right) {
                        val color = pixels[y * width + x]
                        val a = (color ushr 24) / 255f
                        expected[index] += (color ushr 16 and 255) / 255f * a
                        expected[index + 1] += (color ushr 8 and 255) / 255f * a
                        expected[index + 2] += (color and 255) / 255f * a
                        expected[index + 3] += a
                    }
                    val scale = 1f / ((bottom - top) * (right - left))
                    repeat(4) { expected[index + it] *= scale }
                }
                val actual = FloatArray(expected.size)
                assertTrue(NativeRasterOps.samplePigmentBuckets(pixels, width, height, buckets, buckets, actual))
                assertArrayEquals("$width x $height, $buckets buckets", expected, actual, 0f)
            }
        }
    }

    @Test fun nativePremultiplyMatchesEveryAlphaAndChannelValue() {
        val actual = IntArray(65536) { i ->
            val alpha = i / 256; val channel = i % 256
            (alpha shl 24) or (channel shl 16) or ((255 - channel) shl 8) or ((channel * 73) % 256)
        }
        val expected = actual.map { pixel ->
            val alpha = pixel ushr 24
            if (alpha == 0 || alpha == 255) pixel else {
                fun channel(shift: Int) = (((pixel ushr shift and 255) * alpha + 127) / 255) shl shift
                (alpha shl 24) or channel(16) or channel(8) or channel(0)
            }
        }.toIntArray()
        assertTrue(NativeRasterOps.premultiplyInPlace(actual))
        assertArrayEquals(expected, actual)
    }

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

    @Test fun nativeMaterializationProducesCompleteOwnedTileForEveryBlend() {
        val backdrop = intArrayOf(
            0x00000000, 0xff203040.toInt(), 0x80402010.toInt(), 0x40201008,
            0xff102030.toInt(), 0x80800000.toInt(), 0x40002000, 0x20000010,
            0x7f3f2010, 0xff102030.toInt(), 0x80402010.toInt(), 0x40201008,
            0xff101010.toInt(), 0x00000000, 0xffeeeeee.toInt(), 0x80101010.toInt(),
        )
        val contribution = intArrayOf(
            0xffffffff.toInt(), 0xffffffff.toInt(), 0x80400000.toInt(), 0x20000800,
            0xff204060.toInt(), 0x80400000.toInt(), 0x40002000, 0x20000010,
            0x7f3f2010, 0xff102030.toInt(), 0x80402010.toInt(), 0x40201008,
            0xffffffff.toInt(), 0xffffffff.toInt(), 0xffffffff.toInt(), 0xffffffff.toInt(),
        )

        repeat(3) { blendMode ->
            val expected = backdrop.copyOf()
            for (y in 1 until 3) for (x in 1 until 4) {
                val index = y * 4 + x
                expected[index] = reference(backdrop[index], contribution[index], blendMode)
            }
            val actual = contribution.copyOf()
            val status = NativeRasterOps.materializePremultipliedTile(
                actual,
                backdrop,
                stride = 4,
                left = 1,
                top = 1,
                right = 4,
                bottom = 3,
                blendMode = blendMode,
            )
            assertTrue(status >= 0)
            assertTrue(status and NativeRasterOps.MATERIALIZE_CHANGED_FLAG != 0)
            assertTrue(
                status and NativeRasterOps.MATERIALIZE_CHANGED_FLAG.inv() ==
                    expected.count { it ushr 24 != 0 },
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
