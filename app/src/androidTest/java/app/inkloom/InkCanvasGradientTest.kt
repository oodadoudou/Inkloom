package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientDefinition
import app.inkloom.document.GradientKind
import app.inkloom.document.InkStroke
import app.inkloom.document.PageElement
import app.inkloom.document.StrokePoint
import app.inkloom.settings.SelectionShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasGradientTest {
    @Test fun stylusDragCommitsGradientDirectionAndColors() {
        var result: FloatArray? = null
        var committedDefinition: GradientDefinition? = null
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.GRADIENT
            inkColor = android.graphics.Color.RED
            gradientDefinition = GradientDefinition(
                kind = GradientKind.RADIAL,
                stops = listOf(GradientColorStop(0f, 0xFFFF0000L), GradientColorStop(1f, 0xFF0000FFL)),
            )
            onGradientCommitted = { sx, sy, ex, ey, definition, _ ->
                result = floatArrayOf(sx, sy, ex, ey)
                committedDefinition = definition
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatch(view, MotionEvent.ACTION_DOWN, 100f, 200f, 10L)
            dispatch(view, MotionEvent.ACTION_MOVE, 800f, 700f, 20L)
            dispatch(view, MotionEvent.ACTION_UP, 800f, 700f, 30L)
        }

        val gradient = requireNotNull(result)
        assertEquals(.1f, gradient[0], .001f)
        assertEquals(.2f, gradient[1], .001f)
        assertEquals(.8f, gradient[2], .001f)
        assertEquals(.7f, gradient[3], .001f)
        assertEquals(GradientKind.RADIAL, committedDefinition!!.kind)
        assertTrue(committedDefinition!!.stops.first().colorArgb != committedDefinition!!.stops.last().colorArgb)
    }

    @Test fun configuredAngleSnapConstrainsTheCommittedHandle() {
        var result: FloatArray? = null
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.GRADIENT
            gradientAngleSnapEnabled = true
            gradientAngleSnapDegrees = 45f
            onGradientCommitted = { sx, sy, ex, ey, _, _ -> result = floatArrayOf(sx, sy, ex, ey) }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatch(view, MotionEvent.ACTION_DOWN, 200f, 300f, 10L)
            dispatch(view, MotionEvent.ACTION_MOVE, 800f, 520f, 20L)
            dispatch(view, MotionEvent.ACTION_UP, 800f, 520f, 30L)
        }

        val gradient = requireNotNull(result)
        assertEquals(gradient[1], gradient[3], .001f)
    }

    @Test fun committedGradientStartHandleRemainsEditable() {
        var changed: PageElement.Shape? = null
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.SELECT
            pageModelWidth = 1_000f
            pageModelHeight = 1_000f
            editableGradient = PageElement.Shape(
                x = 0f,
                y = 0f,
                width = 1_000f,
                height = 1_000f,
                fillArgb = 0xFF111111L,
                gradientEndArgb = 0xFFFFFFFFL,
                gradientStartX = .2f,
                gradientStartY = .3f,
                gradientEndX = .8f,
                gradientEndY = .7f,
                strokeWidth = 0f,
                gradientStops = listOf(GradientColorStop(0f, 0xFF111111L), GradientColorStop(1f, 0xFFFFFFFFL)),
            )
            onGradientHandleChanged = { changed = it }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            dispatch(view, MotionEvent.ACTION_DOWN, 200f, 300f, 10L)
            dispatch(view, MotionEvent.ACTION_MOVE, 400f, 150f, 20L)
            dispatch(view, MotionEvent.ACTION_MOVE, 420f, 120f, 30L)
            dispatch(view, MotionEvent.ACTION_UP, 420f, 120f, 40L)
        }

        val after = requireNotNull(changed)
        assertEquals(.42f, after.gradientStartX, .01f)
        assertEquals(.12f, after.gradientStartY, .01f)
        assertEquals(.8f, after.gradientEndX, .01f)
        assertEquals(.7f, after.gradientEndY, .01f)
    }

    @Test fun committedGradientIncludesFeatheredGeometryOnlySelectionMask() {
        var committedMask: app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot? = null
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1_000, 1_000)
            tool = EditorTool.GRADIENT
            selectionShape = app.inkloom.settings.SelectionShape.RECTANGLE
            setDocumentStrokes(listOf(InkStroke(id = "ink", points = listOf(StrokePoint(.18f, .18f), StrokePoint(.22f, .22f)), width = 24f)), "gradient-selection")
            onGradientCommitted = { _, _, _, _, _, mask ->
                committedMask = mask
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.dispatchStylusSelection(MotionEvent.ACTION_DOWN, 400f, 400f, 10L)
            view.dispatchStylusSelection(MotionEvent.ACTION_MOVE, 700f, 700f, 20L)
            view.dispatchStylusSelection(MotionEvent.ACTION_UP, 700f, 700f, 30L)
            assertTrue(view.featherSelection(24f))
            dispatch(view, MotionEvent.ACTION_DOWN, 100f, 200f, 40L)
            dispatch(view, MotionEvent.ACTION_MOVE, 800f, 700f, 50L)
            dispatch(view, MotionEvent.ACTION_UP, 800f, 700f, 60L)
        }

        val mask = requireNotNull(committedMask)
        assertTrue(mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight / 2) > 240)
        assertEquals(0, mask.alphaAt(mask.logicalWidth / 10, mask.logicalHeight / 10))
        val featherAlpha = mask.alphaAt((mask.logicalWidth * .39f).toInt(), mask.logicalHeight / 2)
        assertTrue("feather edge must be partially transparent: $featherAlpha", featherAlpha in 1..254)
    }

    @Test fun magicWandGradientMaskRetainsTheFloodFilledIrregularRegion() {
        var committedMask: app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot? = null
        val background = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            Canvas(this).apply {
                val white = Paint().apply { color = Color.WHITE }
                drawRect(100f, 100f, 300f, 800f, white)
                drawRect(100f, 600f, 800f, 800f, white)
            }
        }
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1_000, 1_000)
            pageModelWidth = 1_000f
            pageModelHeight = 1_000f
            setPageBackground(background)
            setDocumentStrokes(emptyList(), "magic-gradient")
            selectionShape = SelectionShape.MAGIC_WAND
            onGradientCommitted = { _, _, _, _, _, mask -> committedMask = mask }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.tool = EditorTool.SELECT
            dispatch(view, MotionEvent.ACTION_DOWN, 200f, 200f, 10L)
            dispatch(view, MotionEvent.ACTION_UP, 200f, 200f, 20L)
            view.tool = EditorTool.GRADIENT
            dispatch(view, MotionEvent.ACTION_DOWN, 100f, 100f, 30L)
            dispatch(view, MotionEvent.ACTION_MOVE, 900f, 900f, 40L)
            dispatch(view, MotionEvent.ACTION_UP, 900f, 900f, 50L)
        }
        val mask = requireNotNull(committedMask)
        assertTrue(mask.alphaAt(mask.logicalWidth / 5, mask.logicalHeight / 5) > 200)
        assertEquals(0, mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight / 2))
        assertTrue(mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight * 7 / 10) > 200)
        background.recycle()
    }

    @Test fun liveGradientPreviewIsClippedToTheActiveSelection() {
        val view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1_000, 1_000)
            pageModelWidth = 1_000f
            pageModelHeight = 1_000f
            setDocumentStrokes(emptyList(), "gradient-preview-selection")
            selectionShape = SelectionShape.RECTANGLE
        }
        val rendered = Bitmap.createBitmap(1_000, 1_000, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.tool = EditorTool.SELECT
            dispatch(view, MotionEvent.ACTION_DOWN, 300f, 300f, 10L)
            dispatch(view, MotionEvent.ACTION_MOVE, 700f, 700f, 20L)
            dispatch(view, MotionEvent.ACTION_UP, 700f, 700f, 30L)
            view.tool = EditorTool.GRADIENT
            dispatch(view, MotionEvent.ACTION_DOWN, 350f, 500f, 40L)
            dispatch(view, MotionEvent.ACTION_MOVE, 650f, 500f, 50L)
            view.draw(Canvas(rendered))
        }
        try {
            assertTrue(Color.alpha(rendered.getPixel(500, 500)) > 0)
            assertEquals(0, Color.alpha(rendered.getPixel(100, 100)))
        } finally {
            rendered.recycle()
        }
    }

    private fun dispatch(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f }
        MotionEvent.obtain(0L, time, action, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0).also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }

    private fun InkCanvasView.dispatchStylusSelection(action: Int, x: Float, y: Float, time: Long) {
        val previous = tool
        tool = EditorTool.SELECT
        dispatch(this, action, x, y, time)
        tool = previous
    }
}
