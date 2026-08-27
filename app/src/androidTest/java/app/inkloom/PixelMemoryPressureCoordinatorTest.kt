package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.android.MaskTileCanvasRenderer
import app.inkloom.engine.v5.android.PixelMemoryPressure
import app.inkloom.engine.v5.android.PixelMemoryPressureCoordinator
import app.inkloom.engine.v5.android.PixelMemoryTrimResult
import app.inkloom.engine.v5.android.PixelMemoryTrimmable
import app.inkloom.engine.v5.surface.MaskTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelMemoryPressureCoordinatorTest {
    @Test fun presentationOwnersTrimBeforeDecodedResourceOwners() {
        val order = mutableListOf<String>()
        fun owner(name: String, priority: Int) = object : PixelMemoryTrimmable {
            override val memoryTrimPriority = priority
            override fun trimPixelMemory(pressure: PixelMemoryPressure): PixelMemoryTrimResult {
                order += name
                return PixelMemoryTrimResult(releasedCpuBytes = priority.toLong())
            }
        }
        val resource = owner("resource", 100)
        val presentation = owner("presentation", 10)
        val resourceRegistration = PixelMemoryPressureCoordinator.register(resource)
        val presentationRegistration = PixelMemoryPressureCoordinator.register(presentation)
        try {
            val summary = PixelMemoryPressureCoordinator.trim(PixelMemoryPressure.MODERATE)
            assertEquals(listOf("presentation", "resource"), order.filter { it in setOf("presentation", "resource") })
            assertEquals(0, summary.failedOwnerCount)
            assertTrue(summary.releasedCpuBytes >= 110L)
        } finally {
            resourceRegistration.close()
            presentationRegistration.close()
        }
    }

    @Test fun maskProjectionCacheIsDiscardedAndRebuiltWithoutLosingSelectionTruth() {
        val surface = MaskTileSurface(128, 128, tileSize = 64).also { mask ->
            mask.beginWrite("selection").run { setAlpha(20, 20, 255); commit().close() }
        }
        val output = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        MaskTileCanvasRenderer.draw(surface.snapshot(), Canvas(output), RectF(0f, 0f, 128f, 128f))
        assertTrue(MaskTileCanvasRenderer.retainedTileCount > 0)

        MaskTileCanvasRenderer.trimPixelMemory(PixelMemoryPressure.CRITICAL)
        assertEquals(0, MaskTileCanvasRenderer.retainedTileCount)
        assertEquals(255, surface.alphaAt(20, 20))
        MaskTileCanvasRenderer.draw(surface.snapshot(), Canvas(output), RectF(0f, 0f, 128f, 128f))
        assertTrue(MaskTileCanvasRenderer.retainedTileCount > 0)
        output.recycle()
    }
}
