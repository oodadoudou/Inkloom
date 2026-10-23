package app.inkloom

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.ml.SuperResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** ED-10: the bundled Real-ESRGAN model enlarges line art on the device, keeping transparency. */
class SuperResolutionTest {
    @Test fun lineArtIsEnlargedWithItsTransparency() {
        val size = 120
        // A dark 3-pixel diagonal on a transparent layer, plus a red block.
        val pixels = IntArray(size * size) { i ->
            val x = i % size; val y = i / size
            when {
                abs(x - y) <= 1 -> 0xFF101010.toInt()
                x in 80..110 && y in 10..40 -> 0xFFE03030.toInt()
                else -> 0
            }
        }
        val source = PixelTileSurface(size, size).also { s ->
            s.beginWrite("fixture").run { replacePremultipliedPixels(PixelRect(0, 0, size, size), pixels); commit().close() }
        }
        val started = SystemClock.uptimeMillis()
        val enlarged = SuperResolution(InstrumentationRegistry.getInstrumentation().targetContext).use { it.enlarge(source.snapshot(), 240, 240) { false } }
        val elapsed = SystemClock.uptimeMillis() - started
        val out = enlarged.snapshot()
        assertEquals(240, out.logicalWidth); assertEquals(240, out.logicalHeight)
        assertEquals("transparent stays transparent", 0, out.pixelAt(20, 200) ushr 24)
        val onLine = out.pixelAt(120, 120)
        assertTrue("the line stays dark and opaque: ${Integer.toHexString(onLine)}", onLine ushr 24 > 200 && (onLine ushr 16 and 255) < 80)
        val block = out.pixelAt(190, 50)
        assertTrue("the block keeps its colour: ${Integer.toHexString(block)}", block ushr 24 > 240 && (block ushr 16 and 255) > 180 && (block and 255) < 90)
        // Detail: the enlarged line edge is sharper than a 2× bilinear blur would be.
        val across = (110..130).map { out.pixelAt(it, 120) ushr 24 }
        val transition = across.count { it in 40..215 }
        assertTrue("edge pixels $across", transition <= 6)
        assertTrue("finished in $elapsed ms", elapsed < 60_000)
    }
}
