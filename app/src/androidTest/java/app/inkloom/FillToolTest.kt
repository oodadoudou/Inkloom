package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.PageElement
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.model.EditorTool
import app.inkloom.settings.ReferenceTarget
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FillToolTest {
    private fun canvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 200, 200); block() }
        }
        return result
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float, toolType: Int = MotionEvent.TOOL_TYPE_FINGER) {
        val props = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        val event = MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) } } finally { event.recycle() }
    }

    @Test fun tappingWithTheFillToolCommitsAFillElement() {
        var committed: PageElement.Fill? = null
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = android.graphics.Color.WHITE
            onFillCommitted = { _, x, y, _, _, _, _ -> committed = PageElement.Fill(x = x, y = y, width = 10f, height = 10f, maskAssetPath = "test") }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertTrue("A fill must be committed on tap", committed != null)
        assertTrue("Fill x should be within the page", (committed?.x ?: -1f) in 0f..1f)
    }

    @Test fun draggingAdjustsSensitivityAndCommitsOneFill() {
        var commitCount = 0
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = android.graphics.Color.WHITE
            onFillCommitted = { _, _, _, _, _, _, _ -> commitCount++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_MOVE, 160f, 100f)
        send(view, 100L, MotionEvent.ACTION_UP, 160f, 100f)
        assertEquals("Dragging then lifting commits exactly one fill", 1, commitCount)
    }

    @Test fun fingerCannotFillWhenFingerDrawingIsDisabled() {
        var commitCount = 0
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = false
            pagePaperColor = Color.WHITE
            onFillCommitted = { _, _, _, _, _, _, _ -> commitCount++ }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals(0, commitCount)
    }

    @Test fun fillPreviewUsesCanvasCoordinates() {
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            inkColor = Color.BLACK
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        val output = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.draw(Canvas(output)) }
        assertEquals("The preview should cover the touched canvas region", Color.BLACK, output.getPixel(100, 100))
        send(view, 50L, MotionEvent.ACTION_CANCEL, 100f, 100f)
        output.recycle()
    }

    @Test fun referenceTargetConfinesFillToMarkedLineArtLayer() {
        var committedWidth = 0f
        val border = InkStroke(
            id = "border",
            layerId = "line-art",
            width = 14f,
            colorArgb = 0xFF000000,
            points = listOf(
                StrokePoint(.25f, .25f), StrokePoint(.75f, .25f), StrokePoint(.75f, .75f),
                StrokePoint(.25f, .75f), StrokePoint(.25f, .25f),
            ),
        )
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            activeLayerId = "color"
            referenceTarget = ReferenceTarget.REFERENCE_LAYERS
            referenceLayerIds = setOf("line-art")
            layerOrder = listOf("color", "line-art")
            setDocumentStrokes(listOf(border), "reference-fill")
            onFillCommitted = { _, _, _, width, _, _, _ -> committedWidth = width }
        }

        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)

        assertTrue("Reference line art should keep the fill inside its boundary", committedWidth in 400f..1_200f)
    }
}
