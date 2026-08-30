package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.RectF
import android.graphics.RenderNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.android.PixelTileCanvasRenderer
import app.inkloom.engine.v5.android.PixelMemoryPressure
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.LinearRgba
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelTileCanvasRendererTest {
    @Test fun memoryPressureDropsOnlyRebuildableProjectionCaches() {
        val surface = PixelTileSurface(256, 128, tileSize = 128)
        surface.beginWrite("pressure fixture").run {
            setPixel(16, 16, Color.RED)
            setPixel(144, 16, Color.BLUE)
            commit().close()
        }
        val renderer = PixelTileCanvasRenderer(maximumCacheBytes = 4L * 128 * 128 * 4)
        val output = RenderNode("Inkloom.V5.PressureTest").apply { setPosition(0, 0, 256, 128) }
        fun record() {
            val canvas = output.beginRecording()
            try { renderer.draw(canvas, surface.snapshot(), RectF(0f, 0f, 256f, 128f)) }
            finally { output.endRecording() }
        }

        record()
        assertEquals(2, renderer.retainedTileCount)
        assertEquals(2, renderer.retainedHardwareTileCount)
        val hidden = renderer.trimPixelMemory(PixelMemoryPressure.UI_HIDDEN)
        assertEquals(2, renderer.retainedTileCount)
        assertEquals(0, renderer.retainedHardwareTileCount)
        assertTrue(hidden.releasedGpuBytes > 0L)
        assertEquals(Color.RED, surface.pixelAt(16, 16))

        record()
        renderer.trimPixelMemory(PixelMemoryPressure.MODERATE)
        assertTrue(renderer.retainedTileCount <= 1)
        renderer.trimPixelMemory(PixelMemoryPressure.CRITICAL)
        assertEquals(0, renderer.retainedTileCount)
        record()
        assertEquals("A trimmed renderer must reconstruct from authoritative tiles", 2, renderer.retainedTileCount)

        renderer.close()
        output.discardDisplayList()
    }

    @Test fun f16SparseTileProjectionRetainsLinearExtendedColorAndEightByteCacheAccounting() {
        val surface = PixelTileSurface(
            logicalWidth = 128,
            logicalHeight = 128,
            tileSize = 64,
            pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16,
        )
        surface.beginWrite("linear projection").run {
            setLinearPixel(32, 32, LinearRgba(2f, .25f, .5f, 1f))
            commit().close()
        }
        val output = Bitmap.createBitmap(
            128,
            128,
            Bitmap.Config.RGBA_F16,
            true,
            ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB),
        )
        PixelTileCanvasRenderer(maximumCacheBytes = 64L * 64 * 8).use { renderer ->
            renderer.draw(Canvas(output), surface.snapshot(), RectF(0f, 0f, 128f, 128f))
            assertEquals(2f, output.getColor(32, 32).red(), .01f)
            assertEquals(64L * 64 * 8, renderer.retainedByteSize)
            assertEquals(1, renderer.retainedTileCount)
        }
        output.recycle()
    }

    @Test fun fiftyLayer8192ProjectionReusesGpuTilesWithinFrameBudget() {
        val surfaces = List(50) { index ->
            PixelTileSurface(8192, 8192, tileSize = 128).also { surface ->
                val key = PixelTileKey(index % 10, index / 10)
                surface.beginWrite("sparse layer $index").use { write ->
                    write.editTile(key, PixelRect(0, 0, 128, 128)) { pixels, _ ->
                        pixels.fill(premultiplied(Color.argb(180, index * 37 % 255, index * 71 % 255, index * 19 % 255)))
                    }
                    write.commit().close()
                }
            }
        }
        val snapshots = surfaces.map(PixelTileSurface::snapshot)
        val renderer = PixelTileCanvasRenderer(maximumCacheBytes = 8L * 1024L * 1024L)
        val output = RenderNode("Inkloom.V5.FiftyLayerPixelProjection").apply { setPosition(0, 0, 8192, 8192) }
        fun record(): Float {
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val canvas = output.beginRecording(8192, 8192)
            try {
                snapshots.forEach { snapshot -> renderer.draw(canvas, snapshot, RectF(0f, 0f, 8192f, 8192f)) }
            } finally {
                output.endRecording()
            }
            return (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }

        record() // cold bitmap upload/display-list recording is intentionally outside steady-state.
        val frameTimes = List(30) { record() }.sorted()
        val p95 = frameTimes[(frameTimes.size * .95f).toInt().coerceAtMost(frameTimes.lastIndex)]
        android.util.Log.i("InkloomPerf", "pixel_tile_gpu_50_layer_8k_p95_ms=$p95")
        assertEquals(50, renderer.retainedHardwareTileCount)
        assertEquals(50, renderer.hardwareNodeBuildCount)
        assertTrue(renderer.retainedByteSize < 4L * 1024L * 1024L)
        assertTrue(renderer.approximateGpuDisplayListBytes in 1 until 16L * 1024L * 1024L)
        assertTrue("50-layer 8K GPU projection p95 was ${p95}ms", p95 < 16.7f)

        renderer.close(); output.discardDisplayList()
    }

    @Test fun hardwareProjectionRetainsOneRenderNodePerImmutableTileGeneration() {
        val surface = PixelTileSurface(256, 128, tileSize = 128)
        surface.beginWrite("initial GPU tiles").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.RED)) }
            write.editTile(PixelTileKey(1, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.BLUE)) }
            write.commit().close()
        }
        val renderer = PixelTileCanvasRenderer(maximumCacheBytes = 4L * 128 * 128 * 4)
        val output = RenderNode("Inkloom.V5.PixelTileProjectionTest").apply { setPosition(0, 0, 512, 256) }
        fun record() {
            val canvas = output.beginRecording(512, 256)
            try {
                assertTrue(canvas.isHardwareAccelerated)
                renderer.draw(canvas, surface.snapshot(), RectF(0f, 0f, 512f, 256f))
            } finally {
                output.endRecording()
            }
        }

        record()
        assertEquals(2, renderer.retainedHardwareTileCount)
        assertEquals(2, renderer.hardwareNodeBuildCount)
        assertTrue(renderer.approximateGpuDisplayListBytes > 0L)
        record()
        assertEquals("Pan/zoom redraw must reuse immutable tile display lists", 2, renderer.hardwareNodeBuildCount)

        surface.beginWrite("replace one GPU tile").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.GREEN)) }
            write.commit().close()
        }
        record()
        assertEquals("Only the changed tile generation may be re-recorded", 3, renderer.hardwareNodeBuildCount)
        assertEquals(2, renderer.retainedHardwareTileCount)

        renderer.releaseSurface(surface.surfaceId)
        assertEquals(0, renderer.retainedHardwareTileCount)
        assertEquals(0L, renderer.approximateGpuDisplayListBytes)
        renderer.close(); output.discardDisplayList()
    }

    @Test fun drawsSparseTilesIntoScaledDestinationWithoutMaterializingThePage() {
        val surface = PixelTileSurface(1024, 768, tileSize = 128)
        surface.beginWrite("two sparse regions").use { write ->
            write.editTile(PixelTileKey(1, 2), PixelRect(0, 0, 128, 128)) { pixels, _ ->
                pixels.fill(premultiplied(Color.RED))
            }
            write.editTile(PixelTileKey(6, 4), PixelRect(0, 0, 128, 128)) { pixels, _ ->
                pixels.fill(premultiplied(Color.BLUE))
            }
            write.commit().close()
        }
        val output = Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888)
        PixelTileCanvasRenderer(maximumCacheBytes = 2L * 128 * 128 * 4).use { renderer ->
            renderer.draw(Canvas(output), surface.snapshot(), RectF(0f, 0f, 512f, 384f))
            assertEquals(Color.RED, output.getPixel(96, 160))
            assertEquals(Color.BLUE, output.getPixel(416, 288))
            assertEquals(Color.TRANSPARENT, output.getPixel(16, 16))
            assertEquals("Only allocated sparse tiles may enter the Android cache", 2, renderer.retainedTileCount)
        }
        output.recycle()
    }

    @Test fun clippedDamageQueriesOnlyIntersectingTileCoordinates() {
        val surface = PixelTileSurface(8192, 8192, tileSize = 128)
        surface.beginWrite("large committed document").use { write ->
            repeat(64) { index -> write.setPixel(index * 128 + 2, index * 128 + 2, Color.BLACK) }
            for (row in 6..7) for (column in 4..5) write.setPixel(column * 128 + 2, row * 128 + 2, Color.BLACK)
            write.commit().close()
        }
        val renderer = PixelTileCanvasRenderer(maximumCacheBytes = 4L * 128 * 128 * 4)
        val output = RenderNode("Inkloom.V5.ClippedDamage").apply { setPosition(0, 0, 8192, 8192) }
        val canvas = output.beginRecording(8192, 8192)
        try {
            canvas.clipRect(512, 768, 768, 1024)
            renderer.draw(canvas, surface.snapshot(), RectF(0f, 0f, 8192f, 8192f))
        } finally {
            output.endRecording()
        }

        assertEquals("A 256px damage clip spans exactly four 128px tiles", 4, renderer.lastCandidateTileCount)
        assertEquals(4, renderer.lastDrawnTileCount)
        assertEquals("Offscreen committed tiles must not enter the frame cache", 4, renderer.retainedTileCount)
        output.discardDisplayList()
        renderer.close()
    }

    @Test fun changedGenerationReplacesOnlyItsCachedTile() {
        val surface = PixelTileSurface(256, 128, tileSize = 128)
        surface.beginWrite("initial").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.RED)) }
            write.editTile(PixelTileKey(1, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.BLUE)) }
            write.commit().close()
        }
        val renderer = PixelTileCanvasRenderer(maximumCacheBytes = 4L * 128 * 128 * 4)
        val first = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(first), surface.snapshot(), RectF(0f, 0f, 256f, 128f))
        surface.beginWrite("change one tile").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.GREEN)) }
            write.commit().close()
        }
        val second = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(second), surface.snapshot(), RectF(0f, 0f, 256f, 128f))

        assertEquals(Color.GREEN, second.getPixel(64, 64))
        assertEquals(Color.BLUE, second.getPixel(192, 64))
        assertEquals("The obsolete generation is recycled instead of accumulating", 2, renderer.retainedTileCount)
        assertTrue(renderer.retainedByteSize <= 2L * 128 * 128 * 4)
        first.recycle(); second.recycle(); renderer.close()
    }

    private fun premultiplied(color: Int): Int {
        val alpha = Color.alpha(color)
        return Color.argb(
            alpha,
            (Color.red(color) * alpha + 127) / 255,
            (Color.green(color) * alpha + 127) / 255,
            (Color.blue(color) * alpha + 127) / 255,
        )
    }
}
