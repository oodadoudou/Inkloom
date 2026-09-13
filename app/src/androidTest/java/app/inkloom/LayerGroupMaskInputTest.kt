package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.*
import app.inkloom.model.EditorTool
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutPhase
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Test

class LayerGroupMaskInputTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val folder = InkLayer(id = "folder", name = "Folder", role = LayerRole.FOLDER)
    private fun create(defaultAlpha: Int = 0): InkCanvasView {
        lateinit var view: InkCanvasView
        instrumentation.runOnMainSync {
            view = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 256, 256); pageModelWidth = 256f; pageModelHeight = 256f
                activeLayerId = folder.id; setDocumentLayers(listOf(folder))
                setProtectedLayerIds(setOf(folder.id))
                installV5TestInk(size = 25f); tool = EditorTool.PEN
                eraserWidth = 40f; eraserHardness = 1f; eraserOpacity = 1f; eraserPressureSize = false
                layerMasks = mapOf(folder.id to LayerMask())
                layerMaskSurfaces = mapOf(folder.id to MaskTileSurface(256, 256, tileSize = 64, initialDefaultAlpha = defaultAlpha).snapshot())
                assertTrue(toggleLayerMaskEditing(folder.id))
            }
        }
        return view
    }

    @Test fun consecutiveContactsQueuedBeforePublicationPreserveEveryMaskStrokeAndUndo() {
        val view = create()
        var commits = 0
        view.onLayerMaskSurfaceCommitted = { _, _ -> commits++ }
        instrumentation.runOnMainSync {
            repeat(20) { index ->
                val y = 10f + index * 12f; val t = 10L + index * 4
                event(view, MotionEvent.ACTION_DOWN, 15f, y, t)
                event(view, MotionEvent.ACTION_MOVE, 240f, y, t + 1)
                event(view, MotionEvent.ACTION_UP, 240f, y, t + 2)
            }
        }
        settle(view)
        assertEquals(20, commits)
        val full = requireNotNull(view.layerMaskSurfaces[folder.id])
        repeat(20) { assertTrue("Lost contact $it", full.alphaAt(128, 10 + it * 12) > 0) }
        instrumentation.runOnMainSync { repeat(20) { view.undo() } }
        assertEquals(0, requireNotNull(view.layerMaskSurfaces[folder.id]).tileCount)
        instrumentation.runOnMainSync { repeat(20) { view.redo() } }
        assertArrayEquals(full.copyAlpha(PixelRect(0, 0, 256, 256)), requireNotNull(view.layerMaskSurfaces[folder.id]).copyAlpha(PixelRect(0, 0, 256, 256)))
    }

    @Test fun immediateUndoAfterUpCannotBeOverwrittenByQueuedMaskPreviews() {
        val view = create()
        instrumentation.runOnMainSync {
            event(view, MotionEvent.ACTION_DOWN, 20f, 128f, 10)
            event(view, MotionEvent.ACTION_MOVE, 235f, 128f, 20)
            event(view, MotionEvent.ACTION_UP, 235f, 128f, 30)
            view.undo()
            assertEquals(0, requireNotNull(view.layerMaskSurfaces[folder.id]).tileCount)
        }
        settle(view)
        assertEquals(0, requireNotNull(view.layerMaskSurfaces[folder.id]).tileCount)
        instrumentation.runOnMainSync { view.redo() }
        assertTrue(requireNotNull(view.layerMaskSurfaces[folder.id]).alphaAt(128, 128) > 0)
    }

    @Test fun heldStylusButtonReleaseCannotRevertMaskEraseAndNextContactRestoresPaint() {
        val view = create(255)
        var releases = 0
        view.onShortcutAction = { activation ->
            if (activation.action == ShortcutAction.TOGGLE_ERASER) {
                if (activation.phase == ShortcutPhase.PRESS) view.switchInputConfiguration(EditorTool.ERASER, null)
                else { releases++; view.switchInputConfiguration(EditorTool.PEN, null) }
            }
        }
        instrumentation.runOnMainSync {
            event(view, MotionEvent.ACTION_DOWN, 20f, 128f, 10, MotionEvent.BUTTON_STYLUS_PRIMARY)
            event(view, MotionEvent.ACTION_MOVE, 128f, 128f, 20, MotionEvent.BUTTON_STYLUS_PRIMARY)
            event(view, MotionEvent.ACTION_BUTTON_RELEASE, 128f, 128f, 21, 0)
            assertEquals(0, releases)
            event(view, MotionEvent.ACTION_MOVE, 235f, 128f, 22)
            event(view, MotionEvent.ACTION_UP, 235f, 128f, 23)
        }
        settle(view)
        assertEquals(1, releases)
        val erased = requireNotNull(view.layerMaskSurfaces[folder.id])
        for (x in 30..220) assertEquals("Erase restored at $x", 0, erased.alphaAt(x, 128))
        instrumentation.runOnMainSync {
            event(view, MotionEvent.ACTION_DOWN, 20f, 128f, 30)
            event(view, MotionEvent.ACTION_MOVE, 235f, 128f, 31)
            event(view, MotionEvent.ACTION_UP, 235f, 128f, 32)
        }
        settle(view)
        assertTrue(requireNotNull(view.layerMaskSurfaces[folder.id]).alphaAt(128, 128) > 0)
    }

    @Test fun maskUsesAuthoredBrushCoverageAndFolderLockCancelsContact() {
        val asset = instrumentation.targetContext.cacheDir.resolve("group-mask-authored-tip.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0..31) for (x in 0..31) bitmap.setPixel(x, y, if (x < 6 || y in 12..16) Color.BLACK else Color.TRANSPARENT)
        asset.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val view = create()
        val raster = PixelTileSurface(256, 256, tileSize = 64)
        lateinit var paint: InkCanvasView
        instrumentation.runOnMainSync {
            view.installV5TestInk(size = 65f, opacity = .6f, tipAssetPaths = listOf(asset.absolutePath), smoothing = .25f)
            paint = InkCanvasView(instrumentation.targetContext).apply {
                layout(0, 0, 256, 256); activeLayerId = "paint"; pageModelWidth = 256f; pageModelHeight = 256f
                setRasterLayerSurfaces(mapOf("paint" to raster)); tool = EditorTool.PEN
                installV5TestInk(size = 65f, opacity = .6f, tipAssetPaths = listOf(asset.absolutePath), smoothing = .25f)
            }
            for (target in listOf(view, paint)) {
                event(target, MotionEvent.ACTION_DOWN, 20f, 128f, 10)
                event(target, MotionEvent.ACTION_MOVE, 128f, 132f, 20)
                event(target, MotionEvent.ACTION_MOVE, 235f, 120f, 30)
                event(target, MotionEvent.ACTION_UP, 235f, 120f, 40)
            }
        }
        settle(view); settle(paint)
        val mask = requireNotNull(view.layerMaskSurfaces[folder.id])
        for (y in 0..255) for (x in 0..255) assertEquals("Authored coverage differs at $x,$y", Color.alpha(raster.pixelAt(x, y)), mask.alphaAt(x, y))
        instrumentation.runOnMainSync {
            event(view, MotionEvent.ACTION_DOWN, 20f, 60f, 50)
            event(view, MotionEvent.ACTION_MOVE, 235f, 60f, 60)
            view.setDocumentLayers(listOf(folder.copy(locked = true)))
            event(view, MotionEvent.ACTION_UP, 235f, 60f, 70)
        }
        settle(view)
        assertArrayEquals(mask.copyAlpha(PixelRect(0, 0, 256, 256)), requireNotNull(view.layerMaskSurfaces[folder.id]).copyAlpha(PixelRect(0, 0, 256, 256)))
        asset.delete()
    }

    private fun settle(view: InkCanvasView) { view.awaitV5StrokeWorkerIdleForTest(); instrumentation.waitForIdleSync() }
    private fun event(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long, buttons: Int = 0) {
        val motion = MotionEvent.obtain(0, time, action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f; setAxisValue(MotionEvent.AXIS_TILT, .35f); orientation = .4f }),
            0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try {
            if (action == MotionEvent.ACTION_BUTTON_RELEASE) {
                view.onGenericMotionEvent(motion)
            } else view.onTouchEvent(motion)
        } finally { motion.recycle() }
    }
}
