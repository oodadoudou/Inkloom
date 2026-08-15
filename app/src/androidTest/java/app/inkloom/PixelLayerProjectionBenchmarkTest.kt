package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v3.composite.PixelBlendMode
import app.inkloom.engine.v3.composite.PixelLayerProjectionCache
import app.inkloom.engine.v3.composite.PixelLayerProjectionInput
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileKey
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelLayerProjectionBenchmarkTest {
    @Test fun fiftyLayerEightKProjectionRecomposesOnlyChangedTileWithinFrameBudget() {
        val layers = MutableList(50) { index ->
            PixelTileSurface(8192, 8192, tileSize = 64, surfaceId = "benchmark-$index").also { surface ->
                surface.beginWrite("opaque benchmark tile").run {
                    editTile(PixelTileKey(20, 20), PixelRect(0, 0, 64, 64)) { pixels, _ ->
                        pixels.fill(0x80000000.toInt() or ((index * 37 and 0x7f) shl 16) or
                            ((index * 53 and 0x7f) shl 8) or (index * 71 and 0x7f))
                    }
                    commit().close()
                }
            }
        }
        val cache = PixelLayerProjectionCache(8192, 8192, tileSize = 64)
        fun inputs() = layers.mapIndexed { index, surface ->
            PixelLayerProjectionInput(
                layerId = "layer-$index",
                pixels = surface.snapshot(),
                blendMode = if (index % 3 == 0) PixelBlendMode.MULTIPLY else PixelBlendMode.NORMAL,
            )
        }
        cache.update(inputs())

        val unchangedTimes = List(20) {
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val update = cache.update(inputs())
            assertEquals(0, update.recomposedTileCount)
            (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }.sorted()
        val changedTimes = List(20) { iteration ->
            layers.last().beginWrite("change benchmark generation").run {
                setPixel(20 * 64 + iteration, 20 * 64 + iteration, 0x80600020.toInt() + iteration)
                commit().close()
            }
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val update = cache.update(inputs())
            assertEquals(1, update.recomposedTileCount)
            (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }.sorted()
        val unchangedP95 = unchangedTimes[p95Index(unchangedTimes.size)]
        val changedP95 = changedTimes[p95Index(changedTimes.size)]
        android.util.Log.i(
            "InkloomPerf",
            "pixel_layer_projection_50_layer_8k_unchanged_p95_ms=$unchangedP95 changed_tile_p95_ms=$changedP95",
        )

        assertEquals(1, cache.snapshot.tileCount)
        assertTrue("Unchanged projection p95 was ${unchangedP95}ms", unchangedP95 < 4f)
        assertTrue("One changed 50-layer projection tile p95 was ${changedP95}ms", changedP95 < 16.7f)
    }

    @Test fun productionCanvasChangedTileRemainsWithinFrameBudget() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val layers = MutableList(50) { index ->
                PixelTileSurface(8192, 8192, tileSize = 64, surfaceId = "production-$index").also { surface ->
                    surface.beginWrite("production benchmark tile").run {
                        editTile(PixelTileKey(20, 20), PixelRect(0, 0, 64, 64)) { pixels, _ ->
                            pixels.fill(0x80000000.toInt() or ((index * 37 and 0x7f) shl 16) or
                                ((index * 53 and 0x7f) shl 8) or (index * 71 and 0x7f))
                        }
                        commit().close()
                    }
                }
            }
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                pagePaperColor = Color.WHITE
                setDocumentStrokes(emptyList(), "production-projection-benchmark")
                setRasterLayerSurfaces(layers.mapIndexed { index, surface -> "layer-$index" to surface }.toMap())
                layerOrder = layers.indices.map { "layer-$it" }
                layerBlendModes = layers.indices.associate { index ->
                    "layer-$index" to if (index % 3 == 0) app.inkloom.document.LayerBlendMode.MULTIPLY else app.inkloom.document.LayerBlendMode.NORMAL
                }
            }
            val output = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(output))
                val changedTimes = List(20) { iteration ->
                    layers.last().beginWrite("production dirty tile").run {
                        setPixel(20 * 64 + iteration, 20 * 64 + iteration, 0x80600020.toInt() + iteration)
                        commit().close()
                    }
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    view.draw(Canvas(output))
                    assertEquals(1, view.lastRasterProjectionRecomposedTileCount)
                    (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
                }.sorted()
                val p95 = changedTimes[p95Index(changedTimes.size)]
                android.util.Log.i("InkloomPerf", "production_raster_projection_50_layer_8k_changed_tile_p95_ms=$p95")
                assertEquals(0, view.legacyLayerCompositionDrawCount)
                assertTrue("Production projection draw p95 was ${p95}ms", p95 < 16.7f)
            } finally {
                output.recycle()
            }
        }
    }

    /** Nearest-rank p95. The previous floor-without-minus-one formula selected the maximum for a
     * 20-sample window, so a single emulator scheduling pause was incorrectly reported as p95. */
    private fun p95Index(size: Int): Int =
        (kotlin.math.ceil(size * .95).toInt() - 1).coerceIn(0, size - 1)
}
