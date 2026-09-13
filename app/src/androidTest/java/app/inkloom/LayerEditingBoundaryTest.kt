package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Test

class LayerEditingBoundaryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val paint = InkLayer(id = "paint", name = "Paint", parentId = "group")
    private val group = InkLayer(id = "group", name = "Group", role = LayerRole.FOLDER)
    private val other = InkLayer(id = "other", name = "Other")

    @Test fun allContentToolsAndSelectionCommandsRejectNonWritableTargetsWithoutTouchingPixels() {
        val a = PixelTileSurface(64, 64, tileSize = 64)
        val b = PixelTileSurface(64, 64, tileSize = 64)
        a.beginWrite("opaque fixture").run { editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { p, _ -> p.fill(Color.RED) }; commit().close() }
        val mask = MaskTileSurface(64, 64, tileSize = 64, initialDefaultAlpha = 255).snapshot()
        val rejections = mutableListOf<String>()
        instrumentation.runOnMainSync {
            val view = configured(a, b).apply { onPaintCapabilityRejected = { rejections += it } }
            view.selectAllEditableStrokes()
            assertTrue(view.copySelectionToClipboard())
            for ((layers, target) in listOf(
                listOf(group, paint.copy(locked = true), other) to paint.id,
                listOf(group.copy(visible = false), paint, other) to paint.id,
                listOf(group, paint, other) to group.id,
                listOf(group, paint, other) to "missing",
                listOf(group, paint, other, InkLayer(id = "paper", name = "Paper", role = LayerRole.BACKGROUND)) to "paper",
            )) {
                view.setDocumentLayers(layers); view.activeLayerId = target
                for (tool in listOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.FILL,
                    EditorTool.SHAPE, EditorTool.GRADIENT, EditorTool.SMUDGE, EditorTool.LIQUIFY, EditorTool.CORRECT_LINE)) {
                    view.tool = tool
                    val before = rejections.size
                    gesture(view)
                    assertEquals("One explicit rejection for $tool / $target", before + 1, rejections.size)
                    assertEquals(target, view.activeLayerId)
                }
                assertFalse(view.cutSelectionToClipboard())
                assertFalse(view.pasteSelectionFromClipboard())
                assertFalse(view.paintFillIntoActiveRaster(mask, Color.BLUE.toUInt().toLong(), 1f, 0))
                assertFalse(view.setSelectionColorFill(true))
                assertFalse(view.enterFreeTransform())
                view.deleteSelection()
                assertEquals(Color.RED, a.pixelAt(32, 32)); assertEquals(0L, b.revision)
            }
            view.setDocumentLayers(listOf(group, paint, other)); view.selectLayerContent(paint.id)
            assertTrue(view.paintFillIntoActiveRaster(mask, Color.BLUE.toUInt().toLong(), 1f, 0))
            assertEquals(Color.BLUE, a.pixelAt(32, 32)); assertEquals(0L, b.revision)
        }
    }

    @Test fun maskTargetCannotLeakAcrossLayerSwitchOrBypassNestedLock() {
        val a = PixelTileSurface(64, 64, tileSize = 64)
        val b = PixelTileSurface(64, 64, tileSize = 64)
        var commits = 0
        var rejected = 0
        instrumentation.runOnMainSync {
            val view = configured(a, b).apply {
                layerMasks = mapOf(paint.id to LayerMask())
                layerMaskSurfaces = mapOf(paint.id to MaskTileSurface(64, 64, tileSize = 64).snapshot())
                onLayerMaskSurfaceCommitted = { _, _ -> commits++ }
                onPaintCapabilityRejected = { rejected++ }
            }
            assertTrue(view.toggleLayerMaskEditing(paint.id))
            view.setDocumentLayers(listOf(group.copy(locked = true), paint, other))
            gesture(view)
            assertEquals(0, commits); assertEquals(1, rejected)
            assertFalse(view.fillLayerMask(paint.id, 255)); assertEquals(2, rejected)
            view.setDocumentLayers(listOf(group, paint, other))
            assertTrue(view.fillLayerMask(paint.id, 255)); assertEquals(1, commits)
            view.selectLayerContent(other.id)
            assertFalse(view.isEditingLayerMask(paint.id))
            assertTrue(view.toggleLayerMaskEditing(paint.id))
            assertEquals(paint.id, view.activeLayerId)
            view.selectLayerContent(paint.id)
            assertFalse(view.isEditingLayerMask(paint.id))
        }
    }

    @Test fun shapesCommitToTheSelectedLayer() {
        instrumentation.runOnMainSync {
            val view = configured(PixelTileSurface(64, 64), PixelTileSurface(64, 64))
            var result: PageElement.Shape? = null
            view.onShapeCommitted = { result = it }
            view.selectLayerContent(other.id); view.tool = EditorTool.SHAPE
            gesture(view)
            assertEquals(other.id, requireNotNull(result).layerId)
        }
    }

    private fun configured(a: PixelTileSurface, b: PixelTileSurface) = InkCanvasView(instrumentation.targetContext).apply {
        layout(0, 0, 256, 256); pageModelWidth = 64f; pageModelHeight = 64f
        activeLayerId = paint.id; setRasterLayerSurfaces(mapOf(paint.id to a, other.id to b))
        layerOrder = listOf(paint.id, other.id); setDocumentLayers(listOf(group, paint, other))
        installV5TestInk(); tool = EditorTool.PEN
    }

    private fun gesture(view: InkCanvasView) {
        for ((index, action) in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).withIndex()) {
            val event = MotionEvent.obtain(0, 10L + index * 10L, action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
                arrayOf(MotionEvent.PointerCoords().apply { x = 80f + index * 40; y = 128f; pressure = .7f; size = .1f }),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
        }
    }
}
