package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.CanvasGuideState
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.document.PageElement
import app.inkloom.document.ShapeKind
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasGuideTest {
    @Test fun stylusStrokeStartingNearGridLineLocksOnlyNearestAxis() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val surface = PixelTileSurface(1_000, 1_000)
            val view = guideCanvas(CanvasGuideState(gridSnapEnabled = true, gridSpacing = 100f), surface)

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 103f, 248f, 10L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 700f, 500f, 20L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, 900f, 800f, 30L)

            assertTrue(Color.alpha(surface.pixelAt(100, 500)) > 0)
            assertTrue(Color.alpha(surface.pixelAt(100, 800)) > 0)
            assertEquals(0, Color.alpha(surface.pixelAt(700, 500)))
        }
    }

    @Test fun fingerDragsVerticalAlignmentGuideAndPersistsOnRelease() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val changes = mutableListOf<CanvasGuideState>()
            val view = guideCanvas(CanvasGuideState(alignmentGuidesEnabled = true, verticalGuideX = .3f, horizontalGuideY = .7f)).apply { onCanvasGuideChanged = changes::add }

            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_DOWN, 300f, 100f, 100L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_MOVE, 720f, 100f, 120L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP, 720f, 100f, 140L)

            assertEquals(.72f, changes.single().verticalGuideX, .001f)
            assertEquals(.7f, changes.single().horizontalGuideY, .001f)
        }
    }

    @Test fun shapeEndpointsSnapToNearbyGridIntersections() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var committed: PageElement.Shape? = null
            val view = guideCanvas(CanvasGuideState(gridSnapEnabled = true, gridSpacing = 100f)).apply {
                tool = EditorTool.SHAPE
                shapeKind = ShapeKind.RECTANGLE
                onShapeCommitted = { committed = it }
            }

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 103f, 198f, 10L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_MOVE, 398f, 602f, 20L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_UP, 398f, 602f, 30L)

            val shape = requireNotNull(committed)
            assertEquals(.1f, shape.x, .0001f)
            assertEquals(.2f, shape.y, .0001f)
            assertEquals(300f, shape.width, .001f)
            assertEquals(400f, shape.height, .001f)
        }
    }

    @Test fun recentStylusContactPreventsPalmFromMovingAlignmentGuide() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val changes = mutableListOf<CanvasGuideState>()
            val view = guideCanvas(CanvasGuideState(alignmentGuidesEnabled = true, verticalGuideX = .3f)).apply { onCanvasGuideChanged = changes::add }
            val original = view.canvasGuideState

            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_DOWN, 800f, 800f, 1_000L)
            view.send(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.ACTION_CANCEL, 800f, 800f, 1_010L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_DOWN, 300f, 100f, 1_020L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_MOVE, 700f, 100f, 1_030L)
            view.send(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.ACTION_UP, 700f, 100f, 1_040L)

            assertEquals(original, view.canvasGuideState)
            assertTrue(changes.isEmpty())
        }
    }

    private fun guideCanvas(state: CanvasGuideState, surface: PixelTileSurface = PixelTileSurface(1_000, 1_000)) = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
        layout(0, 0, 1_000, 1_000)
        pageModelWidth = 1_000f
        pageModelHeight = 1_000f
        tool = EditorTool.PEN
        fingerDrawingEnabled = false
        canvasGuideState = state
        activeLayerId = "paint"
        baseWidth = 18f
        setRasterLayerSurfaces(mapOf("paint" to surface))
        installV5TestInk(size = 18f)
    }

    private fun InkCanvasView.send(toolType: Int, action: Int, x: Float, y: Float, time: Long, pressure: Float = .7f) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = .01f; touchMajor = if (toolType == MotionEvent.TOOL_TYPE_FINGER) 8f else 1f }
        MotionEvent.obtain(
            10L, time, action, 1, arrayOf(properties), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0,
            if (toolType == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS,
            0,
        ).also { event -> try { onTouchEvent(event) } finally { event.recycle() } }
    }
}
