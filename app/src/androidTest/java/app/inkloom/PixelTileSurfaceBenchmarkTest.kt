package app.inkloom

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v3.tools.RasterTileBlendSession
import app.inkloom.engine.v3.tools.RasterTileEraserSession
import app.inkloom.engine.v3.tools.RasterTileLiquifySession
import app.inkloom.document.LiquifyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Deterministic migration and regression gates. Interactive timings are enforced as pass/fail;
 * structural assertions prevent the replacement tile path from silently becoming page-sized.
 */
@RunWith(AndroidJUnit4::class)
class PixelTileSurfaceBenchmarkTest {
    @Test fun sparsePrivatePreviewFramesStayInsideTheRealtimeBudget() {
        val surface = PixelTileSurface(8192, 8192)
        surface.beginWrite("seed preview pigment").use { seed ->
            for (y in 3970 until 4030) for (x in 3900 until 4300) seed.setPixel(x, y, 0xff7080a0.toInt())
            seed.commit().close()
        }
        val times = ArrayList<Long>(72)
        RasterTileEraserSession(surface).use { session ->
            var x = 3960f
            repeat(72) { index ->
                val next = x + 3f
                val started = SystemClock.elapsedRealtimeNanos()
                session.eraseSegment(x, 4000f, next, 4000f + index % 5, 36f, .7f, .55f)
                val preview = session.previewSnapshot()
                assertEquals(surface.logicalWidth, preview.logicalWidth)
                times += SystemClock.elapsedRealtimeNanos() - started
                x = next
            }
            val sorted = times.drop(8).sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            Log.i(
                TAG,
                "kind=sparse_private_preview canvas=8192 p95_ms=$p95Ms " +
                    "touched_tiles=${session.touchedTileCount} working_bytes=${session.workingByteSize}",
            )
            assertTrue("Sparse private preview p95 was ${p95Ms}ms", p95Ms < 16.7f)
            assertTrue(session.touchedTileCount < 16)
            assertTrue(session.workingByteSize < 1_000_000L)
        }
    }

    @Test fun directTileToolFramesStayLocalAndInsideTheRealtimeBudget() {
        fun fixture(): Pair<Bitmap, PixelTileSurface> {
            val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff7080a0.toInt()) }
            return bitmap to PixelTileBitmapAdapter.import(bitmap)
        }
        fun report(name: String, times: List<Long>, touchedTiles: Int, workingBytes: Long) {
            val sorted = times.drop(8).sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            Log.i(TAG, "kind=direct_tile_tool tool=$name p95_ms=$p95Ms touched_tiles=$touchedTiles working_bytes=$workingBytes")
            assertTrue("Direct tile $name p95 was ${p95Ms}ms", p95Ms < 16.7f)
            assertTrue("Direct tile $name unexpectedly touched the whole surface", touchedTiles < 80)
        }

        fixture().let { (bitmap, surface) ->
            val times = ArrayList<Long>(72)
            RasterTileEraserSession(surface).use { session ->
                var x = 220f
                repeat(72) { index ->
                    val next = x + 3f
                    val started = SystemClock.elapsedRealtimeNanos()
                    session.eraseSegment(x, 440f, next, 440f + index % 5, 72f, .7f, .55f)
                    times += SystemClock.elapsedRealtimeNanos() - started
                    x = next
                }
                report("eraser", times, session.touchedTileCount, session.workingByteSize)
                session.commit().close()
            }
            bitmap.recycle()
        }
        fixture().let { (bitmap, surface) ->
            val times = ArrayList<Long>(72)
            RasterTileBlendSession(surface).use { session ->
                var x = 220f
                repeat(72) { index ->
                    val next = x + 3f
                    val started = SystemClock.elapsedRealtimeNanos()
                    session.blend(x, 440f, next, 440f + index % 5, 72f, .7f, .55f)
                    times += SystemClock.elapsedRealtimeNanos() - started
                    x = next
                }
                report("blend", times, session.touchedTileCount, session.workingByteSize)
                session.commit().close()
            }
            bitmap.recycle()
        }
        fixture().let { (bitmap, surface) ->
            val times = ArrayList<Long>(72)
            RasterTileLiquifySession(surface).use { session ->
                var x = 220f
                repeat(72) { index ->
                    val next = x + 3f
                    val started = SystemClock.elapsedRealtimeNanos()
                    session.deform(x, 440f, next, 440f + index % 5, 72f, .7f, .55f, LiquifyMode.PUSH)
                    times += SystemClock.elapsedRealtimeNanos() - started
                    x = next
                }
                report("liquify", times, session.touchedTileCount, session.workingByteSize)
                session.commit().close()
            }
            bitmap.recycle()
        }
    }

    @Test fun productionBitmapBridgeCostIsRecordedUntilToolsWriteDirtyTilesDirectly() {
        val bitmap = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff304050.toInt())
        }
        val surface = PixelTileSurface(1200, 1800)
        val start = SystemClock.elapsedRealtimeNanos()

        PixelTileBitmapAdapter.replaceRotatedRegion(
            surface,
            PixelRect(0, 0, 1200, 1800),
            rotationQuarterTurns = 0,
            bitmap = bitmap,
        )

        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000f
        Log.i(
            TAG,
            "kind=production_bitmap_bridge width=1200 height=1800 elapsed_ms=$elapsedMs " +
                "resident_tiles=${surface.tileCount} retained_bytes=${surface.retainedByteSize}",
        )
        val renderStart = SystemClock.elapsedRealtimeNanos()
        val rendered = PixelTileBitmapAdapter.render(surface.snapshot())
        val renderMs = (SystemClock.elapsedRealtimeNanos() - renderStart) / 1_000_000f
        Log.i(
            TAG,
            "kind=production_tile_materialize width=1200 height=1800 elapsed_ms=$renderMs " +
                "resident_tiles=${surface.tileCount}",
        )
        assertEquals(150, surface.tileCount)
        assertEquals(0xff304050.toInt(), surface.pixelAt(600, 900))
        assertEquals(0xff304050.toInt(), rendered.getPixel(600, 900))
        rendered.recycle()
        bitmap.recycle()
    }

    @Test fun sparseBrushMatrixMeasuresTileSizeCanvasAndDiameterWithoutPageAllocations() {
        val canvases = intArrayOf(2048, 4096, 8192)
        val diameters = intArrayOf(10, 100, 500, 1000)
        PixelTileSurface.SUPPORTED_TILE_SIZES.sorted().forEach { tileSize ->
            canvases.forEach { canvasSize ->
                diameters.forEach { diameter ->
                    val surface = PixelTileSurface(canvasSize, canvasSize, tileSize)
                    val start = SystemClock.elapsedRealtimeNanos()
                    val transaction = surface.beginWrite("benchmark brush $diameter")
                    paintCircle(
                        transaction = transaction,
                        surface = surface,
                        centerX = canvasSize / 2,
                        centerY = canvasSize / 2,
                        diameter = diameter,
                    )
                    val record = transaction.commit()
                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000f
                    Log.i(
                        TAG,
                        "kind=sparse_brush canvas=$canvasSize diameter=$diameter tile=$tileSize " +
                            "elapsed_ms=$elapsedMs changed_tiles=${record.changedTileCount} " +
                            "surface_bytes=${surface.retainedByteSize} undo_bytes=${record.retainedByteSize}",
                    )
                    assertTrue(record.changedTileCount > 0)
                    assertTrue(surface.retainedByteSize < canvasSize.toLong() * canvasSize * Int.SIZE_BYTES)
                    record.close()
                }
            }
        }
    }

    @Test fun sparseLayerAndUndoMatricesRecordMemoryScaling() {
        intArrayOf(1, 10, 30, 50, 100).forEach { layerCount ->
            val start = SystemClock.elapsedRealtimeNanos()
            val layers = List(layerCount) { index ->
                PixelTileSurface(8192, 8192).also { surface ->
                    surface.beginWrite("layer seed").run {
                        setPixel((index * 37) % 8192, (index * 53) % 8192, 0xff000000.toInt() or index)
                        commit().close()
                    }
                }
            }
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000f
            val bytes = layers.sumOf(PixelTileSurface::retainedByteSize)
            Log.i(TAG, "kind=sparse_layers canvas=8192 layers=$layerCount elapsed_ms=$elapsedMs retained_bytes=$bytes")
            assertEquals(layerCount.toLong() * 128 * 128 * Int.SIZE_BYTES, bytes)
        }

        intArrayOf(100, 500, 1000).forEach { transactionCount ->
            val surface = PixelTileSurface(8192, 8192, tileSize = 64)
            val records = ArrayList<app.inkloom.engine.v5.surface.PixelTileTransactionRecord>(transactionCount)
            val writeStart = SystemClock.elapsedRealtimeNanos()
            repeat(transactionCount) { index ->
                records += surface.beginWrite("undo benchmark $index").run {
                    setPixel(index % 64, index / 64, 0xff000000.toInt() or index)
                    commit()
                }
            }
            val writeMs = (SystemClock.elapsedRealtimeNanos() - writeStart) / 1_000_000f
            val undoStart = SystemClock.elapsedRealtimeNanos()
            records.asReversed().forEach { surface.apply(it, PixelTileSurface.HistoryDirection.UNDO) }
            val undoMs = (SystemClock.elapsedRealtimeNanos() - undoStart) / 1_000_000f
            val redoStart = SystemClock.elapsedRealtimeNanos()
            records.forEach { surface.apply(it, PixelTileSurface.HistoryDirection.REDO) }
            val redoMs = (SystemClock.elapsedRealtimeNanos() - redoStart) / 1_000_000f
            Log.i(
                TAG,
                "kind=tile_undo canvas=8192 transactions=$transactionCount write_ms=$writeMs " +
                    "undo_ms=$undoMs redo_ms=$redoMs retained_record_bytes=${records.sumOf { it.retainedByteSize }}",
            )
            assertEquals(0xff000000.toInt() or (transactionCount - 1), surface.pixelAt((transactionCount - 1) % 64, (transactionCount - 1) / 64))
            records.forEach(AutoCloseable::close)
        }
    }

    private fun paintCircle(
        transaction: app.inkloom.engine.v5.surface.PixelTileWriteTransaction,
        surface: PixelTileSurface,
        centerX: Int,
        centerY: Int,
        diameter: Int,
    ) {
        val radius = diameter / 2f
        val left = floor(centerX - radius).toInt().coerceAtLeast(0)
        val top = floor(centerY - radius).toInt().coerceAtLeast(0)
        val right = ceil(centerX + radius).toInt().coerceAtMost(surface.logicalWidth)
        val bottom = ceil(centerY + radius).toInt().coerceAtMost(surface.logicalHeight)
        val firstColumn = left / surface.tileSize
        val lastColumn = (right - 1) / surface.tileSize
        val firstRow = top / surface.tileSize
        val lastRow = (bottom - 1) / surface.tileSize
        val radiusSquared = radius * radius
        for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
            val tileLeft = column * surface.tileSize
            val tileTop = row * surface.tileSize
            val localLeft = (left - tileLeft).coerceAtLeast(0)
            val localTop = (top - tileTop).coerceAtLeast(0)
            val localRight = (right - tileLeft).coerceAtMost(surface.tileSize)
            val localBottom = (bottom - tileTop).coerceAtMost(surface.tileSize)
            transaction.editTile(
                PixelTileKey(column, row),
                PixelRect(localLeft, localTop, localRight, localBottom),
            ) { pixels, stride ->
                for (localY in localTop until localBottom) {
                    val documentY = tileTop + localY
                    for (localX in localLeft until localRight) {
                        val documentX = tileLeft + localX
                        val dx = documentX + .5f - centerX
                        val dy = documentY + .5f - centerY
                        if (dx * dx + dy * dy <= radiusSquared) {
                            pixels[localY * stride + localX] = 0xff203040.toInt()
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "InkloomEngineBaseline"
    }
}
