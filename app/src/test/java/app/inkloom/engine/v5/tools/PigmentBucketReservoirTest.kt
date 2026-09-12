package app.inkloom.engine.v5.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PigmentBucketReservoirTest {
    @Test fun mixingAveragesTheLoadedFootprintWithoutChangingSmearBuckets() {
        val reservoir = PigmentBucketReservoir(2, 2)
        reservoir.pickup(intArrayOf(0xffff0000.toInt(), 0xff0000ff.toInt(), 0xffff0000.toInt(), 0xff0000ff.toInt()), 2, 2, 1f)
        val output = IntArray(4)
        reservoir.copyArgb(output, 1f)
        assertTrue(output.all { it == 0xff800080.toInt() })
        reservoir.copyArgb(output)
        assertEquals(0xffff0000.toInt(), output[0])
        assertEquals(0xff0000ff.toInt(), output[1])
    }

    @Test fun mixingTransparentBucketsPreservesPigmentHue() {
        val reservoir = PigmentBucketReservoir(2, 2)
        reservoir.pickup(intArrayOf(0xff00ffff.toInt(), 0, 0, 0), 2, 2, 1f)
        val output = IntArray(4)
        reservoir.copyArgb(output, 1f)
        assertTrue(output.all { it == 0x4000ffff })
    }

    @Test fun bucketsPreserveSpatiallyDifferentPigmentInsteadOfAveragingOneColor() {
        val reservoir = PigmentBucketReservoir(columns = 4, rows = 4)
        val pixels = IntArray(16 * 16) { index ->
            if (index / 16 < 8) 0xffff0000.toInt() else 0xff0000ff.toInt()
        }
        reservoir.pickup(pixels, 16, 16, 1f)
        val output = IntArray(reservoir.bucketCount)
        reservoir.copyArgb(output)
        assertEquals(0xffff0000.toInt(), output.first())
        assertEquals(0xff0000ff.toInt(), output.last())
        assertTrue(output.take(8).all { it == 0xffff0000.toInt() })
        assertTrue(output.takeLast(8).all { it == 0xff0000ff.toInt() })
    }

    @Test fun transparentPickupAttenuatesCarriedAlphaWithoutInventingColor() {
        val reservoir = PigmentBucketReservoir(columns = 2, rows = 2)
        reservoir.pickup(IntArray(16) { 0xff28a0e0.toInt() }, 4, 4, 1f)
        val before = IntArray(4).also(reservoir::copyArgb)
        reservoir.pickup(IntArray(16), 4, 4, .5f)
        val after = IntArray(4).also(reservoir::copyArgb)
        assertTrue((after[0] ushr 24) < (before[0] ushr 24))
        assertTrue("Carried hue remains blue", (after[0] and 0xff) > ((after[0] ushr 16) and 0xff))
    }

    @Test fun repeatedPickupUsesPersistentBucketState() {
        val reservoir = PigmentBucketReservoir(columns = 2, rows = 2)
        reservoir.pickup(IntArray(16) { 0xffff0000.toInt() }, 4, 4, 1f)
        reservoir.pickup(IntArray(16) { 0xff0000ff.toInt() }, 4, 4, .25f)
        val output = IntArray(4).also(reservoir::copyArgb)
        val color = output[0]
        assertTrue(((color ushr 16) and 0xff) > (color and 0xff))
        assertTrue((color and 0xff) > 0)
        assertEquals(4L * 8 * Float.SIZE_BYTES, reservoir.retainedByteSize)
    }
}
