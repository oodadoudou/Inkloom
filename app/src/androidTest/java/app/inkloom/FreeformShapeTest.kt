package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.PageBackground
import app.inkloom.document.PageElement
import app.inkloom.document.ShapeKind
import app.inkloom.document.ShapeVertex
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FreeformShapeTest {
    @Test fun heldGesturePublishesQuickShapeThroughEditableCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var ordinaryCommits = 0
            var editableShape: PageElement.Shape? = null
            val view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 1_000, 1_000)
                tool = EditorTool.SHAPE
                shapeKind = ShapeKind.FREEFORM
                pageModelWidth = 1_000f
                pageModelHeight = 1_000f
                inkColor = Color.BLACK
                baseWidth = 8f
                onShapeCommitted = { ordinaryCommits++ }
                onQuickShapeCommitted = { editableShape = it }
            }

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 120f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 500f, 500f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 880f, 800f, 30L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 880f, 800f, 30L + 650L)

            assertEquals(0, ordinaryCommits)
            assertEquals(ShapeKind.LINE, requireNotNull(editableShape).kind)
        }
    }

    @Test fun stylusGestureCreatesRetainedNormalizedPolygon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var committed: PageElement.Shape? = null
            val view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 1_000, 1_000)
                tool = EditorTool.SHAPE
                shapeKind = ShapeKind.FREEFORM
                pageModelWidth = 1_000f
                pageModelHeight = 1_000f
                inkColor = Color.BLACK
                baseWidth = 8f
                onShapeCommitted = { committed = it }
            }

            view.dispatchStylus(MotionEvent.ACTION_DOWN, 200f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 800f, 220f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 720f, 800f, 30L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 220f, 700f, 40L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 200f, 200f, 50L)

            val shape = requireNotNull(committed)
            assertEquals(ShapeKind.FREEFORM, shape.kind)
            assertTrue(shape.path.size >= 4)
            assertTrue(shape.path.all { it.x in 0f..1f && it.y in 0f..1f })
            assertEquals(8f, shape.strokeWidth)
        }
    }

    @Test fun flattenedExportFillsTheSameFreeformPolygon() {
        val service = DocumentAssetService(ApplicationProvider.getApplicationContext())
        val polygon = PageElement.Shape(
            x = .2f,
            y = .2f,
            width = 600f,
            height = 600f,
            kind = ShapeKind.FREEFORM,
            colorArgb = 0xFF000000,
            fillArgb = 0xFF000000,
            strokeWidth = 6f,
            path = listOf(ShapeVertex(.5f, 0f), ShapeVertex(1f, .5f), ShapeVertex(.5f, 1f), ShapeVertex(0f, .5f)),
        )
        val page = InkPage(width = 1_000f, height = 1_000f, background = PageBackground.Paper(0xFFFFFFFF), elements = listOf(polygon))

        val bitmap = service.renderPageBitmap(page, 1_000)
        try {
            assertEquals(Color.BLACK, bitmap.getPixel(500, 500))
            assertEquals(Color.WHITE, bitmap.getPixel(225, 225))
        } finally {
            bitmap.recycle()
        }
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f; touchMajor = 1f }
        MotionEvent.obtain(10L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also { event ->
            try { onTouchEvent(event) } finally { event.recycle() }
        }
    }
}
