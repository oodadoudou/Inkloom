package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderNode
import android.media.ImageReader
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v5.android.PixelTileCanvasRenderer
import app.inkloom.engine.v5.android.PixelMemoryPressure
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.LinearRgba
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelTileCanvasRendererTest {
    @Test fun reusedConversionHandlesEdgeTilesWithoutMutatingEarlierBitmaps() {
        val surface = PixelTileSurface(129, 65, tileSize = 128)
        surface.beginWrite("conversion ownership").use { write ->
            write.setPixel(40, 30, Color.MAGENTA)
            write.setPixel(128, 30, Color.BLUE)
            write.commit().close()
        }
        val snapshot = surface.snapshot()
        val scratch = IntArray(128 * 128) { Color.GREEN }
        val first = app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(
            snapshot, PixelRect(0, 0, 128, 65), conversionScratch = scratch,
        )
        val before = IntArray(128 * 65)
        first.getPixels(before, 0, 128, 0, 0, 128, 65)
        val edge = app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(
            snapshot, PixelRect(128, 0, 129, 65), conversionScratch = scratch,
        )
        assertEquals(Color.BLUE, edge.getPixel(0, 30))
        assertEquals("Old conversion pixels must not fill transparent holes", 0, edge.getPixel(0, 0))
        scratch.fill(Color.YELLOW)
        val after = IntArray(before.size)
        first.getPixels(after, 0, 128, 0, 0, 128, 65)
        org.junit.Assert.assertArrayEquals(before, after)
        assertEquals(Color.MAGENTA, snapshot.pixelAt(40, 30))
        first.recycle(); edge.recycle()
    }

    @Test fun newGenerationsUseBoundedConversionAllocation() {
        val surface = PixelTileSurface(128, 128, tileSize = 128)
        val snapshots = (0..64).map { index ->
            surface.beginWrite("allocation-$index").use { write ->
                write.setPixel(index, 30, Color.RED)
                write.commit().close()
            }
            surface.snapshot()
        }
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val destination = RectF(0f, 0f, 128f, 128f)
        fun allocated(): Long = requireNotNull(android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")) {
            "ART allocation counter is required for the Android allocation gate"
        }.toLong()
        PixelTileCanvasRenderer().use { renderer ->
            renderer.draw(canvas, snapshots.first(), destination)
            val before = allocated()
            snapshots.drop(1).forEach { snapshot -> renderer.draw(canvas, snapshot, destination) }
            val bytes = allocated() - before
            android.util.Log.i("InkloomPerf", "tile_conversion_64_generations_java_bytes=$bytes")
            assertTrue("Tile presentation allocated ${bytes}B for 64 generations", bytes < 64L * 128 * 128 * 4 / 2)
            assertEquals(Color.RED, bitmap.getPixel(64, 30))
            assertEquals("Earlier snapshots stay immutable", 0, snapshots.first().pixelAt(64, 30))
        }
        bitmap.recycle()
    }

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
        val hidden = renderer.trimPixelMemory(PixelMemoryPressure.UI_HIDDEN)
        assertEquals(2, renderer.retainedTileCount)
        assertEquals(0L, hidden.releasedGpuBytes)
        assertEquals(Color.RED, surface.pixelAt(16, 16))

        record()
        renderer.trimPixelMemory(PixelMemoryPressure.MODERATE)
        assertTrue(renderer.retainedTileCount <= 1)
        renderer.trimPixelMemory(PixelMemoryPressure.CRITICAL)
        assertEquals(0, renderer.retainedTileCount)
        assertEquals("Critical pressure also releases conversion storage", 0L, renderer.retainedByteSize)
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

    @Test fun fiftyLayer8192ProjectionRecordsStrictTileDrawsWithinFrameBudget() {
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
        assertEquals(50, renderer.retainedTileCount)
        assertTrue(renderer.retainedByteSize < 4L * 1024L * 1024L)
        assertTrue("50-layer 8K GPU projection p95 was ${p95}ms", p95 < 16.7f)

        renderer.close(); output.discardDisplayList()
    }

    @Test fun hardwareProjectionRetainsOnlyImmutableBitmapGenerations() {
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
        assertEquals(2, renderer.retainedTileCount)
        record()
        assertEquals("Pan/zoom redraw must reuse immutable tile bitmaps", 2, renderer.retainedTileCount)

        surface.beginWrite("replace one GPU tile").use { write ->
            write.editTile(PixelTileKey(0, 0), PixelRect(0, 0, 128, 128)) { pixels, _ -> pixels.fill(premultiplied(Color.GREEN)) }
            write.commit().close()
        }
        record()
        assertEquals("Only current tile generations may stay cached", 2, renderer.retainedTileCount)

        renderer.releaseSurface(surface.surfaceId)
        assertEquals(0, renderer.retainedTileCount)
        renderer.close(); output.discardDisplayList()
    }

    @Test fun fractionalFillMaskDoesNotExposePaperAtTileBoundaries() {
        val surface = app.inkloom.engine.v5.surface.MaskTileSurface(384, 384, tileSize = 128)
        surface.beginWrite("explicit opaque fill tiles").use { write ->
            for (y in 0 until 384) for (x in 0 until 384) write.setAlpha(x, y, 255)
            write.commit().close()
        }
        val outputWidth = 503
        val outputHeight = 497
        val root = RenderNode("Inkloom.V5.TileSeamRegression").apply {
            setPosition(0, 0, outputWidth, outputHeight)
        }
        val recording = root.beginRecording(outputWidth, outputHeight)
        try {
            recording.drawColor(Color.WHITE)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = android.graphics.PorterDuffColorFilter(
                    Color.rgb(52, 67, 58), android.graphics.PorterDuff.Mode.SRC_IN,
                )
            }
            app.inkloom.engine.v5.android.MaskTileCanvasRenderer.draw(
                surface.snapshot(), recording,
                RectF(.35f, .65f, outputWidth - .4f, outputHeight - .2f), paint,
            )
        } finally {
            root.endRecording()
        }

        val bitmap = renderHardware(root, outputWidth, outputHeight)
        try {
            val expected = Color.rgb(52, 67, 58)
            val seamXs = listOf(168, 169, 335, 336)
            val seamYs = listOf(165, 166, 331, 332)
            seamXs.forEach { x ->
                for (y in 4 until outputHeight - 4) {
                    assertFalse("Paper leaked at vertical tile boundary ($x,$y)", isNearlyWhite(bitmap.getPixel(x, y)))
                    assertColorNear(expected, bitmap.getPixel(x, y))
                }
            }
            seamYs.forEach { y ->
                for (x in 4 until outputWidth - 4) {
                    assertFalse("Paper leaked at horizontal tile boundary ($x,$y)", isNearlyWhite(bitmap.getPixel(x, y)))
                    assertColorNear(expected, bitmap.getPixel(x, y))
                }
            }
        } finally {
            bitmap.recycle()
            root.discardDisplayList()
        }
    }

    @Test fun fractionalHardwareScaleDoesNotExposePaperAtTileBoundaries() {
        val surface = PixelTileSurface(384, 384, tileSize = 128)
        surface.beginWrite("opaque 3 by 3 seam fixture").use { write ->
            for (row in 0..2) for (column in 0..2) {
                write.editTile(PixelTileKey(column, row), PixelRect(0, 0, 128, 128)) { pixels, _ ->
                    pixels.fill(premultiplied(Color.rgb(52, 67, 58)))
                }
            }
            write.commit().close()
        }
        val outputWidth = 503
        val outputHeight = 497
        val root = RenderNode("Inkloom.V5.TileSeamRegression").apply {
            setPosition(0, 0, outputWidth, outputHeight)
        }
        PixelTileCanvasRenderer(maximumCacheBytes = 9L * 128 * 128 * 4).use { renderer ->
            val recording = root.beginRecording(outputWidth, outputHeight)
            try {
                recording.drawColor(Color.WHITE)
                renderer.draw(
                    recording,
                    surface.snapshot(),
                    RectF(.35f, .65f, outputWidth - .4f, outputHeight - .2f),
                )
            } finally {
                root.endRecording()
            }
        }

        val bitmap = renderHardware(root, outputWidth, outputHeight)
        try {
            val expected = Color.rgb(52, 67, 58)
            val seamXs = listOf(168, 169, 335, 336)
            val seamYs = listOf(165, 166, 331, 332)
            seamXs.forEach { x ->
                for (y in 4 until outputHeight - 4) {
                    assertFalse("Paper leaked at vertical tile boundary ($x,$y)", isNearlyWhite(bitmap.getPixel(x, y)))
                    assertColorNear(expected, bitmap.getPixel(x, y))
                }
            }
            seamYs.forEach { y ->
                for (x in 4 until outputWidth - 4) {
                    assertFalse("Paper leaked at horizontal tile boundary ($x,$y)", isNearlyWhite(bitmap.getPixel(x, y)))
                    assertColorNear(expected, bitmap.getPixel(x, y))
                }
            }
        } finally {
            bitmap.recycle()
            root.discardDisplayList()
        }
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
        assertEquals("Later conversion must not change an earlier frame", Color.RED, first.getPixel(64, 64))
        assertEquals(Color.BLUE, first.getPixel(192, 64))
        assertEquals("The obsolete generation is recycled instead of accumulating", 2, renderer.retainedTileCount)
        assertTrue(renderer.retainedBitmapByteSize <= 2L * 128 * 128 * 4)
        assertEquals(128L * 128 * 4, renderer.retainedConversionByteSize)
        assertEquals(renderer.retainedBitmapByteSize + renderer.retainedConversionByteSize, renderer.retainedByteSize)
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

    private fun renderHardware(root: RenderNode, width: Int, height: Int): Bitmap {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val renderer = HardwareRenderer()
        try {
            renderer.setName("Inkloom tile seam regression")
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(root)
            renderer.start()
            val result = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            assertTrue("HardwareRenderer failed with sync result $result", result >= 0)
            val image = requireNotNull(reader.acquireLatestImage())
            image.use {
                val plane = it.planes.single()
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val rowPadding = plane.rowStride - plane.pixelStride * width
                val padded = Bitmap.createBitmap(
                    width + rowPadding / plane.pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                padded.copyPixelsFromBuffer(plane.buffer)
                Canvas(output).drawBitmap(padded, 0f, 0f, null)
                padded.recycle()
                return output
            }
        } finally {
            renderer.stop()
            renderer.destroy()
            reader.close()
        }
    }

    private fun isNearlyWhite(color: Int): Boolean =
        Color.red(color) > 245 && Color.green(color) > 245 && Color.blue(color) > 245

    private fun assertColorNear(expected: Int, actual: Int) {
        val distance = kotlin.math.abs(Color.red(expected) - Color.red(actual)) +
            kotlin.math.abs(Color.green(expected) - Color.green(actual)) +
            kotlin.math.abs(Color.blue(expected) - Color.blue(actual))
        assertTrue("Tile seam changed $expected to $actual (RGB distance $distance)", distance <= 24)
    }
}
