package app.inkloom

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.engine.v5.selection.DocumentSelectionRuntime
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import android.graphics.Color
import android.graphics.Bitmap
import app.inkloom.document.PageElement
import app.inkloom.ui.MixedSelectionSource
import app.inkloom.ui.LayerMaskStrokeHistory
import app.inkloom.document.LayerMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionTransformTest {
    private fun canvas(block: InkCanvasView.() -> Unit): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 400, 400); block() }
        }
        return result
    }

    private fun event(time: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }
        return MotionEvent.obtain(0L, time, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun send(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float) {
        val e = event(time, action, x, y)
        try { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(e) } } finally { e.recycle() }
    }

    @Test fun multipleDragsUseOriginalPixelsAndPublishOneUndoableCommit() {
        val surface = PixelTileSurface(128, 128, 64)
        surface.beginWrite("ink").use { write ->
            for (y in 45 until 55) for (x in 45 until 55) write.setPixel(x, y, Color.RED)
            write.commit().close()
        }
        val mask = MaskTileSurface(128, 128, 64)
        mask.beginWrite("mask").use { write ->
            write.composeAlpha(PixelRect(32, 32, 64, 64), ByteArray(1024) { -1 }, mode = MaskComposeMode.REPLACE)
            write.commit().close()
        }
        var publications = 0
        val view = canvas {
            pageModelWidth = 128f; pageModelHeight = 128f; activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("draft", "page", 128, 128))
            replaceSelectionFromMask(mask.snapshot())
            onRasterLayerCommitted = { publications++ }
            assertTrue(enterFreeTransform())
        }
        // Two contacts form one draft. The second starts at the first contact's new center.
        send(view, 1, MotionEvent.ACTION_DOWN, 150f, 150f)
        send(view, 2, MotionEvent.ACTION_MOVE, 200f, 150f)
        send(view, 3, MotionEvent.ACTION_UP, 200f, 150f)
        send(view, 4, MotionEvent.ACTION_DOWN, 200f, 150f)
        send(view, 5, MotionEvent.ACTION_MOVE, 200f, 200f)
        send(view, 6, MotionEvent.ACTION_UP, 200f, 200f)
        assertEquals(0, publications)
        assertEquals(Color.RED, surface.pixelAt(65, 65))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { assertTrue(view.finishSelectionTransform(apply = true)) }
        assertEquals(1, publications)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(Color.RED, surface.pixelAt(49, 49)); assertEquals(0, surface.pixelAt(65, 65))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        assertEquals(Color.RED, surface.pixelAt(65, 65))
        // A later draft is fully reversible, including a trip entirely outside the page.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.enterFreeTransform() }
        val original = surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 128, 128))
        send(view, 7, MotionEvent.ACTION_DOWN, 200f, 200f)
        send(view, 8, MotionEvent.ACTION_MOVE, 800f, 200f)
        send(view, 9, MotionEvent.ACTION_UP, 800f, 200f)
        assertEquals(0, surface.snapshot().tileCount)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.finishSelectionTransform(apply = false) }
        org.junit.Assert.assertArrayEquals(original, surface.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 128, 128)))
        assertEquals(3, publications)
    }

    @Test fun oneTransformMovesMultipleSelectedLayersAndOneUndoRestoresAll() {
        fun layer(color: Int) = PixelTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("layer fixture").use { write -> write.setPixel(20, 20, color); write.commit().close() }
        }
        val first = layer(Color.RED)
        val second = layer(Color.BLUE)
        val mask = MaskTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("selection").use { write ->
                write.composeAlpha(PixelRect(16, 16, 28, 28), ByteArray(144) { -1 }, mode = MaskComposeMode.REPLACE)
                write.commit().close()
            }
        }
        val published = mutableListOf<String>()
        val view = canvas {
            pageModelWidth = 64f; pageModelHeight = 64f
            activeLayerId = "first"
            selectionTransformLayerIds = setOf("first", "second")
            setRasterLayerSurfaces(linkedMapOf("first" to first, "second" to second))
            bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("multi", "page", 64, 64))
            replaceSelectionFromMask(mask.snapshot())
            onRasterLayerCommitted = { published += it.layerId }
            assertTrue(enterFreeTransform())
        }
        send(view, 1, MotionEvent.ACTION_DOWN, 137.5f, 137.5f)
        send(view, 2, MotionEvent.ACTION_MOVE, 237.5f, 137.5f)
        send(view, 3, MotionEvent.ACTION_UP, 237.5f, 137.5f)
        assertEquals(Color.RED, first.pixelAt(36, 20))
        assertEquals(Color.BLUE, second.pixelAt(36, 20))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { assertTrue(view.finishSelectionTransform(true)) }
        assertEquals(setOf("first", "second"), published.toSet())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(Color.RED, first.pixelAt(20, 20))
        assertEquals(Color.BLUE, second.pixelAt(20, 20))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        assertEquals(Color.RED, first.pixelAt(36, 20))
        assertEquals(Color.BLUE, second.pixelAt(36, 20))
    }

    @Test fun unlinkedMaskTransformsIndependentlyWhileLinkedMaskMovesWithLayerContent() {
        for (linked in listOf(false, true)) {
            val pixels = PixelTileSurface(64, 64, 64).also { surface ->
                surface.beginWrite("pixel").use { write -> write.setPixel(20, 20, Color.RED); write.commit().close() }
            }
            val maskContent = MaskTileSurface(64, 64, 64).also { surface ->
                surface.beginWrite("mask content").use { write -> write.setAlpha(20, 20, 220); write.commit().close() }
            }
            val selection = MaskTileSurface(64, 64, 64).also { surface ->
                surface.beginWrite("selection").use { write ->
                    write.composeAlpha(PixelRect(16, 16, 28, 28), ByteArray(144) { -1 }, mode = MaskComposeMode.REPLACE)
                    write.commit().close()
                }
            }
            var persistentMask = maskContent.snapshot()
            lateinit var view: InkCanvasView
            view = canvas {
                pageModelWidth = 64f; pageModelHeight = 64f; activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to pixels))
                layerMasks = mapOf("paint" to LayerMask(linked = linked))
                layerMaskSurfaces = mapOf("paint" to persistentMask)
                bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("mask-$linked", "page", 64, 64))
                replaceSelectionFromMask(selection.snapshot())
                onLayerMaskStrokeCommitted = { _, after ->
                    val before = persistentMask
                    persistentMask = after
                    layerMaskSurfaces = layerMaskSurfaces + ("paint" to after)
                    LayerMaskStrokeHistory(
                        undo = { persistentMask = before; layerMaskSurfaces = layerMaskSurfaces + ("paint" to before) },
                        redo = { persistentMask = after; layerMaskSurfaces = layerMaskSurfaces + ("paint" to after) },
                        dispose = {},
                    )
                }
                assertTrue(toggleLayerMaskEditing("paint"))
                assertTrue(enterFreeTransform())
                nudgeSelection(8f, 0f)
                assertTrue(finishSelectionTransform(true))
            }
            assertEquals(0, persistentMask.alphaAt(20, 20))
            assertEquals(220, persistentMask.alphaAt(28, 20))
            if (linked) {
                assertEquals(0, pixels.pixelAt(20, 20))
                assertEquals(Color.RED, pixels.pixelAt(28, 20))
            } else {
                assertEquals(Color.RED, pixels.pixelAt(20, 20))
                assertEquals(0, pixels.pixelAt(28, 20))
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
            assertEquals(220, persistentMask.alphaAt(20, 20))
            assertEquals(Color.RED, pixels.pixelAt(20, 20))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
            assertEquals(220, persistentMask.alphaAt(28, 20))
        }
    }

    @Test fun groupMaskTransformsWithoutRequiringAGroupPixelSurface() {
        val maskContent = MaskTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("group mask").use { write -> write.setAlpha(20, 20, 200); write.commit().close() }
        }
        val selection = MaskTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("selection").use { write ->
                write.composeAlpha(PixelRect(16, 16, 28, 28), ByteArray(144) { -1 }, mode = MaskComposeMode.REPLACE)
                write.commit().close()
            }
        }
        var persistent = maskContent.snapshot()
        canvas {
            pageModelWidth = 64f; pageModelHeight = 64f; activeLayerId = "group"
            layerMasks = mapOf("group" to LayerMask(linked = false))
            layerMaskSurfaces = mapOf("group" to persistent)
            bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("group-mask", "page", 64, 64))
            replaceSelectionFromMask(selection.snapshot())
            onLayerMaskStrokeCommitted = { _, after ->
                val before = persistent
                persistent = after
                layerMaskSurfaces = layerMaskSurfaces + ("group" to after)
                LayerMaskStrokeHistory(
                    undo = { persistent = before; layerMaskSurfaces = layerMaskSurfaces + ("group" to before) },
                    redo = { persistent = after; layerMaskSurfaces = layerMaskSurfaces + ("group" to after) },
                    dispose = {},
                )
            }
            assertTrue(toggleLayerMaskEditing("group"))
            assertTrue(enterFreeTransform())
            nudgeSelection(8f, 0f)
            assertTrue(finishSelectionTransform(true))
        }
        assertEquals(0, persistent.alphaAt(20, 20))
        assertEquals(200, persistent.alphaAt(28, 20))
    }

    @Test fun selectedLayersAlignToActiveLayerWithoutFlattening() {
        fun layer(x: Int, color: Int) = PixelTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("alignment fixture").use { write -> write.setPixel(x, 20, color); write.commit().close() }
        }
        val first = layer(10, Color.RED)
        val second = layer(34, Color.BLUE)
        val mask = MaskTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("selection").use { write ->
                write.composeAlpha(PixelRect(0, 0, 64, 64), ByteArray(64 * 64) { -1 }, mode = MaskComposeMode.REPLACE)
                write.commit().close()
            }
        }
        val view = canvas {
            pageModelWidth = 64f; pageModelHeight = 64f; activeLayerId = "first"
            selectionTransformLayerIds = setOf("first", "second")
            setRasterLayerSurfaces(linkedMapOf("first" to first, "second" to second))
            bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("align", "page", 64, 64))
            replaceSelectionFromMask(mask.snapshot())
            assertTrue(enterFreeTransform())
            setSelectionAlignmentReference(app.inkloom.ui.SelectionAlignmentReference.ACTIVE_LAYER)
            alignSelectionToCanvas(app.inkloom.engine.v5.tools.SelectionAlignment.LEFT)
            assertTrue(finishSelectionTransform(true))
        }
        assertEquals(Color.RED, first.pixelAt(10, 20))
        assertEquals(PixelRect(10, 20, 11, 21), app.inkloom.engine.v5.tools.selectionContentBounds(second.snapshot()))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(Color.BLUE, second.pixelAt(34, 20))
    }

    @Test fun selectedLayersDistributeBetweenOuterLayerCenters() {
        fun layer(x: Int, color: Int) = PixelTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("distribution fixture").use { write -> write.setPixel(x, 20, color); write.commit().close() }
        }
        val first = layer(8, Color.RED)
        val middle = layer(14, Color.GREEN)
        val last = layer(48, Color.BLUE)
        val mask = MaskTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("selection").use { write ->
                write.composeAlpha(PixelRect(0, 0, 64, 64), ByteArray(64 * 64) { -1 }, mode = MaskComposeMode.REPLACE)
                write.commit().close()
            }
        }
        canvas {
            pageModelWidth = 64f; pageModelHeight = 64f; activeLayerId = "first"
            selectionTransformLayerIds = setOf("first", "middle", "last")
            setRasterLayerSurfaces(linkedMapOf("first" to first, "middle" to middle, "last" to last))
            bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("distribute", "page", 64, 64))
            replaceSelectionFromMask(mask.snapshot())
            assertTrue(enterFreeTransform())
            distributeSelectionLayers(horizontal = true)
            assertTrue(finishSelectionTransform(true))
        }
        assertEquals(Color.RED, first.pixelAt(8, 20))
        assertEquals(PixelRect(28, 20, 29, 21), app.inkloom.engine.v5.tools.selectionContentBounds(middle.snapshot()))
        assertEquals(Color.BLUE, last.pixelAt(48, 20))
    }

    @Test fun multiLayerTextAndImageRemainEditableAfterOneTransform() {
        val text = PageElement.Text(id = "text", layerId = "first", x = .1f, y = .2f, width = 12f, height = 8f, text = "Editable")
        val image = PageElement.Image(id = "image", layerId = "second", x = .3f, y = .4f, width = 14f, height = 10f, assetPath = "fixture.png")
        val surfaces = linkedMapOf("first" to PixelTileSurface(64, 64, 64), "second" to PixelTileSurface(64, 64, 64))
        val mask = MaskTileSurface(64, 64, 64).also { surface ->
            surface.beginWrite("selection").use { write ->
                write.composeAlpha(PixelRect(0, 0, 64, 64), ByteArray(64 * 64) { -1 }, mode = MaskComposeMode.REPLACE)
                write.commit().close()
            }
        }
        val committed = linkedMapOf<String, app.inkloom.document.LayerElementEdit>()
        val rejections = mutableListOf<String>()
        canvas {
            pageModelWidth = 64f; pageModelHeight = 64f; activeLayerId = "first"
            selectionTransformLayerIds = surfaces.keys
            setRasterLayerSurfaces(surfaces)
            bindDocumentSelectionState(DocumentSelectionRuntime(64).stateFor("structured", "page", 64, 64))
            replaceSelectionFromMask(mask.snapshot())
            onPrepareMixedSelection = { layerId, _ ->
                val element = if (layerId == "first") text else image
                MixedSelectionSource(listOf(element), Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                    setPixel(20, 20, Color.WHITE)
                })
            }
            onRasterLayerCommitted = { commit -> commit.layerElements?.let { committed[commit.layerId] = it } }
            onPaintCapabilityRejected = { rejections += it }
            assertTrue(enterFreeTransform())
            org.junit.Assert.assertFalse(flipSelection(horizontal = true))
            nudgeSelection(5f, 0f)
            assertTrue(finishSelectionTransform(true))
        }
        val movedText = committed.getValue("first").replacement.single()
        val movedImage = committed.getValue("second").replacement.single()
        assertTrue(movedText is PageElement.Text)
        assertTrue(movedImage is PageElement.Image)
        assertEquals("text", movedText.id)
        assertEquals("image", movedImage.id)
        assertTrue(movedText.x > text.x)
        assertTrue(movedImage.x > image.x)
        assertTrue(rejections.single().contains("unsupported", ignoreCase = true))
    }

    @Test fun everyTransformEntryRejectsLockedLayersInBothColorFormatsWithoutMutation() {
        for (highPrecision in listOf(false, true)) {
            val format = if (highPrecision) app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16
                else app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_SRGB_8888
            val surface = PixelTileSurface(64, 64, tileSize = 64, pixelFormat = format)
            val mask = MaskTileSurface(64, 64, tileSize = 64)
            mask.beginWrite("selection").use { it.setAlpha(30, 30, 255); it.commit().close() }
            val runtime = DocumentSelectionRuntime(tileSize = 64)
            val reasons = mutableListOf<String>()
            val view = canvas {
                activeLayerId = "raster"
                setRasterLayerSurfaces(mapOf("raster" to surface))
                setProtectedLayerIds(setOf("raster"))
                bindDocumentSelectionState(runtime.stateFor("document", "page", 64, 64))
                replaceSelectionFromMask(mask.snapshot())
                onPaintCapabilityRejected = { reasons += it }
                org.junit.Assert.assertFalse(enterFreeTransform())
                org.junit.Assert.assertFalse(flipSelection(true))
                scaleSelection(2f)
                rotateSelection(45f)
            }
            assertEquals(4, reasons.size)
            assertTrue(reasons.all { it.isNotBlank() })
            assertEquals(0L, surface.revision)
            assertTrue(view.hasActiveSelection())
        }
    }

    @Test fun highPrecisionSelectionMovesThroughNativeHandlesWithoutClippingExtendedColor() {
        val surface = PixelTileSurface(64, 64, 64, pixelFormat = app.inkloom.engine.v5.surface.PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
        surface.beginWrite("HDR square").use { write ->
            for (y in 20 until 28) for (x in 20 until 28) write.setLinearPixel(x, y, app.inkloom.engine.v5.surface.LinearRgba(3f, -.2f, .1234f, .7f))
            write.commit().close()
        }
        val original = surface.snapshot().packedF16At(24, 24)
        val mask = MaskTileSurface(64, 64, 64)
        mask.beginWrite("selection").use { write ->
            write.composeAlpha(PixelRect(16, 16, 32, 32), ByteArray(256) { -1 }, mode = MaskComposeMode.REPLACE)
            write.commit().close()
        }
        val runtime = DocumentSelectionRuntime(64)
        val view = canvas {
            activeLayerId = "raster"; pageModelWidth = 64f; pageModelHeight = 64f
            setRasterLayerSurfaces(mapOf("raster" to surface))
            bindDocumentSelectionState(runtime.stateFor("hdr", "page", 64, 64))
            replaceSelectionFromMask(mask.snapshot())
            assertTrue(enterFreeTransform())
        }
        send(view, 1, MotionEvent.ACTION_DOWN, 150f, 150f)
        send(view, 2, MotionEvent.ACTION_MOVE, 250f, 250f)
        send(view, 3, MotionEvent.ACTION_UP, 250f, 250f)
        assertEquals(0L, surface.snapshot().packedF16At(24, 24))
        assertEquals(original, surface.snapshot().packedF16At(40, 40))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        assertEquals(original, surface.snapshot().packedF16At(24, 24))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.redo() }
        assertEquals(original, surface.snapshot().packedF16At(40, 40))
    }

    @Test fun freeTransformMapsDocumentMaskAndRasterResolutions() {
        val surface = PixelTileSurface(64, 64, tileSize = 64)
        surface.beginWrite("raster square").run {
            editTile(PixelTileKey(0, 0), PixelRect(8, 8, 16, 16)) { pixels, stride ->
                for (y in 8 until 16) for (x in 8 until 16) pixels[y * stride + x] = Color.RED
            }
            commit().close()
        }
        val mask = MaskTileSurface(32, 32, tileSize = 64)
        mask.beginWrite("alpha-only selection").run {
            composeAlpha(PixelRect(4, 4, 8, 8), ByteArray(16) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val runtime = DocumentSelectionRuntime(tileSize = 64)
        val view = canvas {
            pageModelWidth = 128f
            pageModelHeight = 128f
            activeLayerId = "raster"
            tool = EditorTool.SELECT
            fingerDrawingEnabled = true
            setRasterLayerSurfaces(mapOf("raster" to surface))
            bindDocumentSelectionState(runtime.stateFor("document", "page", 32, 32))
            replaceSelectionFromMask(mask.snapshot())
            assertTrue(enterFreeTransform())
        }

        send(view, 20L, MotionEvent.ACTION_DOWN, 75f, 75f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 175f, 75f)
        send(view, 50L, MotionEvent.ACTION_UP, 175f, 75f)

        assertEquals(Color.TRANSPARENT, surface.pixelAt(10, 10))
        assertEquals(Color.RED, surface.pixelAt(26, 10))
        val movedMask = view.selectionMaskSurfaceSnapshot()!!
        assertEquals(0, movedMask.alphaAt(5, 5))
        assertEquals(255, movedMask.alphaAt(13, 5))
    }
    @Test fun productionFreeTransformMovesAlphaOnlyRasterSelectionAndKeepsMaskAligned() {
        val surface = PixelTileSurface(64, 64, tileSize = 64)
        surface.beginWrite("raster square").run {
            editTile(PixelTileKey(0, 0), PixelRect(8, 8, 16, 16)) { pixels, stride ->
                for (y in 8 until 16) for (x in 8 until 16) pixels[y * stride + x] = Color.RED
            }
            commit().close()
        }
        val mask = MaskTileSurface(64, 64, tileSize = 64)
        mask.beginWrite("alpha-only selection").run {
            composeAlpha(PixelRect(8, 8, 16, 16), ByteArray(64) { 255.toByte() }, mode = MaskComposeMode.REPLACE)
            commit().close()
        }
        val runtime = DocumentSelectionRuntime(tileSize = 64)
        val view = canvas {
            pageModelWidth = 64f
            pageModelHeight = 64f
            activeLayerId = "raster"
            tool = EditorTool.SELECT
            fingerDrawingEnabled = true
            setRasterLayerSurfaces(mapOf("raster" to surface))
            bindDocumentSelectionState(runtime.stateFor("document", "page", 64, 64))
            replaceSelectionFromMask(mask.snapshot())
            assertTrue(enterFreeTransform())
        }

        send(view, 20L, MotionEvent.ACTION_DOWN, 75f, 75f)
        send(view, 40L, MotionEvent.ACTION_MOVE, 175f, 75f)
        send(view, 50L, MotionEvent.ACTION_UP, 175f, 75f)

        assertEquals(Color.TRANSPARENT, surface.pixelAt(10, 10))
        assertEquals(Color.RED, surface.pixelAt(26, 10))
        val movedMask = view.selectionMaskSurfaceSnapshot()!!
        assertEquals(0, movedMask.alphaAt(10, 10))
        assertEquals(255, movedMask.alphaAt(26, 10))
    }
}
