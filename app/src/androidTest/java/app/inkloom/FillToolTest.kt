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
import app.inkloom.document.FillSubTool
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.settings.ReferenceTarget
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FillToolTest {
    @Test fun manualFillSubToolsCommitRasterMasks() {
        FillSubTool.entries.filterNot { it == FillSubTool.BUCKET }.forEach { subTool ->
            var committed = false
            val view = canvas {
                tool = EditorTool.FILL
                fillSubTool = subTool
                fingerDrawingEnabled = true
                pagePaperColor = Color.WHITE
                onFillCommitted = { mask, _, _, width, height, _, _ ->
                    committed = mask.width > 0 && mask.height > 0 && width > 0f && height > 0f
                    mask.recycle()
                }
            }
            send(view, 0L, MotionEvent.ACTION_DOWN, 40f, 40f)
            send(view, 20L, MotionEvent.ACTION_MOVE, 160f, 40f)
            send(view, 40L, MotionEvent.ACTION_MOVE, 160f, 160f)
            send(view, 60L, MotionEvent.ACTION_MOVE, 40f, 160f)
            send(view, 80L, MotionEvent.ACTION_UP, 40f, 40f)
            assertTrue("$subTool must commit a reusable fill mask", committed)
        }
    }

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
        val color = PixelTileSurface(200, 200)
        val lineArt = PixelTileSurface(200, 200).also { surface ->
            surface.beginWrite("V5 reference border").use { transaction ->
                for (offset in -4..4) {
                    for (coordinate in 50..150) {
                        transaction.setPixel(coordinate, 50 + offset, Color.BLACK)
                        transaction.setPixel(coordinate, 150 + offset, Color.BLACK)
                        transaction.setPixel(50 + offset, coordinate, Color.BLACK)
                        transaction.setPixel(150 + offset, coordinate, Color.BLACK)
                    }
                }
                transaction.commit().close()
            }
        }
        val view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            pageModelWidth = 200f
            pageModelHeight = 200f
            activeLayerId = "color"
            referenceTarget = ReferenceTarget.REFERENCE_LAYERS
            referenceLayerIds = setOf("line-art")
            layerOrder = listOf("color", "line-art")
            setRasterLayerSurfaces(mapOf("color" to color, "line-art" to lineArt))
            onFillCommitted = { mask, _, _, width, _, _, _ ->
                committedWidth = width
                mask.recycle()
            }
        }

        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)

        assertTrue("V5 reference line art should keep the fill inside its boundary", committedWidth in 80f..120f)
    }

    @Test fun fillSelectionCommitsTheSelectionMaskInsteadOfFloodingAroundInk() {
        var committed = false
        val view = canvas {
            onSparseFillCommitted = { mask, _, _ ->
                committed = mask.logicalWidth > 1 && mask.logicalHeight > 1 &&
                    mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight / 2) > 0
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertTrue(view.selectAllEditableStrokes())
            assertTrue(view.fillSelection())
        }
        assertTrue("Selection launcher fill must commit the exact selection mask", committed)
    }

    @Test fun bucketTapWithActiveSelectionFillsTheWholeSelectionRegardlessOfSeed() {
        var committedMask: app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot? = null
        val view = canvas {
            pageModelWidth = 200f
            pageModelHeight = 200f
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            selectionShape = app.inkloom.settings.SelectionShape.RECTANGLE
            onSparseFillCommitted = { mask, _, _ ->
                committedMask = mask
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.tool = EditorTool.SELECT
            sendDirect(view, 0L, MotionEvent.ACTION_DOWN, 50f, 50f)
            sendDirect(view, 20L, MotionEvent.ACTION_MOVE, 150f, 150f)
            sendDirect(view, 40L, MotionEvent.ACTION_UP, 150f, 150f)
            view.tool = EditorTool.FILL
            sendDirect(view, 60L, MotionEvent.ACTION_DOWN, 10f, 10f)
            sendDirect(view, 80L, MotionEvent.ACTION_UP, 10f, 10f)
        }
        val mask = requireNotNull(committedMask)
        assertEquals(200, mask.logicalWidth)
        assertEquals(200, mask.logicalHeight)
        assertTrue(mask.alphaAt(mask.logicalWidth / 2, mask.logicalHeight / 2) > 0)
        assertEquals(0, mask.alphaAt(mask.logicalWidth / 20, mask.logicalHeight / 20))
    }

    @Test fun committedBucketFillIsRemovedAndRestoredAsOneHistoryAction() {
        var visibleFills = 0
        lateinit var view: InkCanvasView
        view = canvas {
            tool = EditorTool.FILL
            fingerDrawingEnabled = true
            pagePaperColor = Color.WHITE
            onFillCommitted = { mask, _, _, _, _, _, _ ->
                mask.recycle()
                visibleFills++
                view.recordExternalEdit(undo = { visibleFills-- }, redo = { visibleFills++ })
            }
        }
        send(view, 0L, MotionEvent.ACTION_DOWN, 100f, 100f)
        send(view, 50L, MotionEvent.ACTION_UP, 100f, 100f)
        assertEquals(1, visibleFills)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(0, visibleFills)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        assertEquals(1, visibleFills)
    }

    private fun sendDirect(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0).also {
            try { view.onTouchEvent(it) } finally { it.recycle() }
        }
    }
}
