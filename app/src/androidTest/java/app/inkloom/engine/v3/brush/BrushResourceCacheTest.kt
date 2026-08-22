package app.inkloom.engine.v3.brush

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v3.android.PixelMemoryPressure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class BrushResourceCacheTest {
    @Test
    fun memoryPressureReleasesDecodedResourcesAndCacheRemainsReusable() {
        val file = tipFile("pressure-tip.png")
        val cache = BrushResourceCache()
        val request = BrushTipRequest(file.absolutePath, .5f, null, 0f)
        val first = requireNotNull(cache.decodeNow(request))
        val procedural = requireNotNull(cache.proceduralTip("pressure-round") {
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        })

        val result = cache.trimPixelMemory(PixelMemoryPressure.MODERATE)
        assertTrue(first.isRecycled)
        assertTrue(procedural.isRecycled)
        assertTrue(result.releasedCpuBytes > 0L)
        assertEquals(0, cache.countsForTests()[0])
        assertTrue(cache.decodeNow(request) != null)
        cache.close()
    }

    @Test
    fun equivalentQuantizedRequestsReuseOneDecodedTip() {
        val file = tipFile("equivalent-tip.png")
        val cache = BrushResourceCache()
        val first = BrushTipRequest(file.absolutePath, hardness = .501f, grainPath = null, grainDensity = 0f)
        val equivalent = BrushTipRequest(file.absolutePath, hardness = .539f, grainPath = null, grainDensity = .049f)

        val decoded = requireNotNull(cache.decodeNow(first))
        assertSame(decoded, cache.decodeNow(equivalent))
        assertEquals(1, cache.countsForTests()[0])

        cache.close()
        assertTrue(decoded.isRecycled)
    }

    @Test
    fun pendingLoadsAreBoundedAndAClosedGenerationRejectsLateDecode() {
        val file = tipFile("pending-tip.png")
        val cache = BrushResourceCache(maxPendingLoads = 1)
        val first = BrushTipRequest(file.absolutePath, .5f, null, 0f)
        val second = BrushTipRequest(file.absolutePath, .8f, null, 0f)

        assertTrue(cache.beginLoad(first))
        assertFalse(cache.beginLoad(second))
        cache.close()

        val lateBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        assertFalse(cache.completeLoad(first, lateBitmap))
        assertTrue(lateBitmap.isRecycled)
        assertEquals(0, cache.countsForTests().sum())
    }

    @Test
    fun proceduralTipsAndGrainMaterialsShareTheResourceLifecycle() {
        val file = tipFile("material-tip.png")
        val cache = BrushResourceCache()
        val procedural = requireNotNull(cache.proceduralTip("round") {
            Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        })

        assertSame(procedural, cache.proceduralTip("round") { error("must not recreate") })
        assertTrue(cache.grainMaterial(file.absolutePath) != null)
        assertTrue(cache.grainMaterial(file.absolutePath) === cache.grainMaterial(file.absolutePath))
        assertEquals(intArrayOf(0, 0, 1, 1).toList(), cache.countsForTests().toList())

        cache.close()
        assertTrue(procedural.isRecycled)
    }

    private fun tipFile(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, name).also { file ->
            Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.argb(255, 96, 96, 96))
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
