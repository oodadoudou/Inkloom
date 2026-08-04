package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokeTileKey
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasTileCacheTest {
    @Test fun longLiveStrokeRendersOnlyItsShortTailPerFrame() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val previewPointCounts = mutableListOf<Int>()
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.PEN
                onLivePreviewRendered = previewPointCounts::add
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 100f, 200f, 10L)
                repeat(250) { point ->
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 100f + point * 3f, 200f + (point % 7), 20L + point)
                }
                view.draw(Canvas(bitmap))

                assertTrue("Live preview replayed ${previewPointCounts.lastOrNull()} points", previewPointCounts.lastOrNull() in 1..96)
                view.dispatchStylus(MotionEvent.ACTION_UP, 850f, 200f, 300L)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun strokeCrossingTileSeamsRendersWithoutPixelGaps() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                setDocumentStrokes(
                    listOf(
                        InkStroke(
                            id = "seam",
                            width = 24f,
                            points = listOf(StrokePoint(.05f, .5f), StrokePoint(.95f, .5f)),
                        ),
                    ),
                    "tile:seam",
                )
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))

                for (x in 64..960 step 16) {
                    assertTrue("Missing upper seam pixel at $x", bitmap.getPixel(x, 511) ushr 24 != 0)
                    assertTrue("Missing lower seam pixel at $x", bitmap.getPixel(x, 512) ushr 24 != 0)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun erasingOnFiveThousandStrokePageRerendersOnlyLocalTiles() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val strokes = List(5_000) { index ->
                val column = index % 100
                val row = index / 100
                InkStroke(
                    id = "stroke-$index",
                    width = 2f,
                    points = listOf(StrokePoint((column + .5f) / 100f, (row + .5f) / 50f)),
                )
            }
            val renderedTiles = mutableListOf<Pair<StrokeTileKey, Int>>()
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.ERASER
                eraserWidth = 5f
                setDocumentStrokes(strokes, "tile:large")
                onTileRendered = { key, count -> renderedTiles += key to count }
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val initialTileCount = renderedTiles.size
                renderedTiles.clear()

                view.dispatchStylus(MotionEvent.ACTION_DOWN, 107.5f, 112.5f, 10L)
                view.dispatchStylus(MotionEvent.ACTION_UP, 107.5f, 112.5f, 20L)
                view.draw(Canvas(bitmap))

                assertTrue("Expected a multi-tile initial cache", initialTileCount > 1)
                assertTrue("Local erase rerendered ${renderedTiles.size} of $initialTileCount tiles", renderedTiles.size in 1 until initialTileCount)
                assertTrue("Local erase replayed ${renderedTiles.sumOf { it.second }} strokes", renderedTiles.sumOf { it.second } < strokes.size)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun movingInkOnFiveThousandStrokePageInvalidatesOldAndNewTilesOnly() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val strokes = List(5_000) { index ->
                val column = index % 100
                val row = index / 100
                InkStroke(
                    id = "move-$index",
                    width = 2f,
                    points = listOf(StrokePoint((column + .5f) / 100f, (row + .5f) / 50f)),
                )
            }
            val renderedTiles = mutableListOf<Pair<StrokeTileKey, Int>>()
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 1024, 1024)
                tool = EditorTool.SELECT
                setDocumentStrokes(strokes, "tile:move-large")
                onTileRendered = { key, count -> renderedTiles += key to count }
            }
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val initialTileCount = renderedTiles.size
                renderedTiles.clear()

                view.drawLasso(.095f, .09f, .115f, .13f)
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 107.5f, 112.5f, 60L)
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 700f, 700f, 70L)
                view.dispatchStylus(MotionEvent.ACTION_UP, 700f, 700f, 80L)
                view.draw(Canvas(bitmap))

                assertTrue("Local move rerendered ${renderedTiles.size} of $initialTileCount tiles", renderedTiles.size in 2 until initialTileCount)
                assertTrue("Local move replayed ${renderedTiles.sumOf { it.second }} strokes", renderedTiles.sumOf { it.second } < strokes.size)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun InkCanvasView.drawLasso(left: Float, top: Float, right: Float, bottom: Float) {
        dispatchStylus(MotionEvent.ACTION_DOWN, left * width, top * height, 10L)
        dispatchStylus(MotionEvent.ACTION_MOVE, right * width, top * height, 20L)
        dispatchStylus(MotionEvent.ACTION_MOVE, right * width, bottom * height, 30L)
        dispatchStylus(MotionEvent.ACTION_MOVE, left * width, bottom * height, 40L)
        dispatchStylus(MotionEvent.ACTION_UP, left * width, top * height, 50L)
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = .8f
            size = .01f
            touchMajor = 1f
        }
        MotionEvent.obtain(
            10L,
            time,
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        ).also { event ->
            try {
                onTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
    }
}
