package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production-raster contract: factory families are semantics, not names or thumbnails. */
@RunWith(AndroidJUnit4::class)
class ReferenceBrushDistinctnessTest {
    @Test fun gPenGraphiteAndPaintingBrushProduceMateriallyDifferentRasterProfiles() {
        val ink = render("system-g-pen")
        val graphite = render("system-hb-pencil")
        val painting = render("system-dry-painting-brush")

        assertTrue("G Pen and Pencil collapsed to the same raster", mismatchRatio(ink, graphite) > .12f)
        assertTrue("G Pen and Painting Brush collapsed to the same raster", mismatchRatio(ink, painting) > .08f)
        assertTrue("Pencil and Painting Brush collapsed to the same raster", mismatchRatio(graphite, painting) > .1f)

        val inkInterior = alphaAverage(ink, 160, 440, 112, 128)
        val pencilInterior = alphaAverage(graphite, 160, 440, 112, 128)
        assertTrue("G Pen must retain a dense clean ribbon: $inkInterior", inkInterior > 160f)
        assertTrue("HB Pencil must expose graphite deposition gaps: $pencilInterior", pencilInterior < inkInterior - 25f)
        assertTrue(
            "Painting Brush needs a broader soft-alpha edge profile than G Pen",
            intermediateAlphaCount(painting) > intermediateAlphaCount(ink) * 2,
        )
    }

    private fun render(brushId: String): IntArray {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(WIDTH, HEIGHT)
        val brush = requireNotNull(SystemBrushCatalog.byId(brushId))
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, WIDTH, HEIGHT)
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { }
                applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            }
            view.send(MotionEvent.ACTION_DOWN, 120f, 120f, 10L)
            view.send(MotionEvent.ACTION_MOVE, 480f, 120f, 20L)
            view.send(MotionEvent.ACTION_UP, 480f, 120f, 30L)
        }
        view.awaitV5StrokeWorkerIdleForTest()
        return surface.snapshot().copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, WIDTH, HEIGHT))
    }

    private fun mismatchRatio(first: IntArray, second: IntArray): Float {
        var different = 0
        var relevant = 0
        first.indices.forEach { index ->
            val firstAlpha = first[index] ushr 24
            val secondAlpha = second[index] ushr 24
            if (firstAlpha > 0 || secondAlpha > 0) {
                relevant++
                if (kotlin.math.abs(firstAlpha - secondAlpha) >= 16) different++
            }
        }
        return different.toFloat() / relevant.coerceAtLeast(1)
    }

    private fun alphaAverage(pixels: IntArray, left: Int, right: Int, top: Int, bottom: Int): Float {
        var total = 0L
        var count = 0
        for (y in top until bottom) for (x in left until right) {
            total += pixels[y * WIDTH + x] ushr 24
            count++
        }
        return total.toFloat() / count.coerceAtLeast(1)
    }

    private fun intermediateAlphaCount(pixels: IntArray): Int = pixels.count { pixel ->
        (pixel ushr 24) in 12..230
    }

    private fun InkCanvasView.send(action: Int, x: Float, y: Float, time: Long) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = .75f; size = .01f
            setAxisValue(MotionEvent.AXIS_TILT, .55f)
        })
        MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            .also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
    }

    private companion object {
        const val WIDTH = 600
        const val HEIGHT = 240
    }
}
