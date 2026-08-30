package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LayerIds
import app.inkloom.document.PaperPattern
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasBackgroundIsolationTest {
    @Test fun dryBrushLayerRasterIsIdenticalAcrossPaperPatternsAndColors() {
        val configurations = listOf(
            Color.WHITE to PaperPattern.PLAIN,
            0xFFFFFBF2.toInt() to PaperPattern.RULED,
            0xFFF4F8F2.toInt() to PaperPattern.GRID,
            0xFFF6F3EA.toInt() to PaperPattern.DOT,
            0xFFF7F4E8.toInt() to PaperPattern.CORNELL,
        )
        val rasters = configurations.map { (paperColor, pattern) ->
            renderDryStroke(paperColor, pattern)
        }

        rasters.drop(1).forEach { candidate ->
            assertArrayEquals(
                "Page backgrounds are presentation-only and must not alter dry-brush tile bytes",
                rasters.first(),
                candidate,
            )
        }
    }

    @Test fun opaqueDryStrokeCoversGridInsteadOfBlendingOrCreatingHoles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(320, 320)
        val frame = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        lateinit var view: InkCanvasView
        try {
            instrumentation.runOnMainSync {
                view = configuredView(surface, 0xFFF4F8F2.toInt(), PaperPattern.GRID)
                drawStroke(view)
                view.awaitV5StrokeWorkerIdleForTest()
                view.draw(Canvas(frame))
            }
            instrumentation.runOnMainSync { }

            assertEquals("The paint layer stores the authored opaque color", Color.BLACK, surface.pixelAt(160, 160))
            assertEquals("The final frame must show paint over the grid", Color.BLACK, frame.getPixel(160, 160))
        } finally {
            frame.recycle()
        }
    }

    private fun renderDryStroke(paperColor: Int, pattern: PaperPattern): IntArray {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(320, 320)
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = configuredView(surface, paperColor, pattern)
            drawStroke(view)
        }
        view.awaitV5StrokeWorkerIdleForTest()
        instrumentation.runOnMainSync { }
        return IntArray(320 * 320) { index -> surface.pixelAt(index % 320, index / 320) }
    }

    private fun configuredView(surface: PixelTileSurface, paperColor: Int, pattern: PaperPattern) =
        InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            layout(0, 0, 320, 320)
            activeLayerId = LayerIds.INK
            pagePaperColor = paperColor
            pagePaperPattern = pattern
            setRasterLayerSurfaces(mapOf(LayerIds.INK to surface))
            applyBrushConfiguration(
                EditorTool.PEN,
                AppSettings(),
                null,
                40f,
                1f,
                Color.BLACK,
                EraserPreset.NORMAL_FREE,
            )
        }

    private fun drawStroke(view: InkCanvasView) {
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 60f, 160f, 1L))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 160f, 160f, 5L))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 260f, 160f, 9L))
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            touchMajor = 1f
        })
        return MotionEvent.obtain(
            1L,
            time,
            action,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
    }
}
