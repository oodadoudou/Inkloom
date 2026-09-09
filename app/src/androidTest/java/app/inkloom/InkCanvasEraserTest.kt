package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.diagnostics.EraserLifecycleTrace
import app.inkloom.model.EditorTool
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutPhase
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasEraserTest {
    @Test fun holdButtonReleaseFinishesEraseBeforeRestoringPersistentBrush() {
        val surface = opaqueSurface(400, 400)
        var releaseCount = 0
        val view = configuredView(mapOf("paint" to surface)).apply {
            switchInputConfiguration(EditorTool.PEN, null)
            onShortcutAction = { activation ->
                if (activation.action == ShortcutAction.TOGGLE_ERASER) {
                    if (activation.phase == ShortcutPhase.PRESS) {
                        switchInputConfiguration(EditorTool.ERASER, null)
                    } else {
                        releaseCount++
                        switchInputConfiguration(EditorTool.PEN, null)
                    }
                }
            }
        }
        EraserLifecycleTrace.reset()
        EraserLifecycleTrace.enabled = true

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 200f, 10L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 200f, 20L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                view.dispatchStylus(MotionEvent.ACTION_UP, 320f, 200f, 30L, 0)
            }
            settlePublishedStroke(view)

            assertEquals(1, releaseCount)
            assertEquals("The completed temporary eraser gesture must remain erased", 0, Color.alpha(surface.pixelAt(200, 200)))
            val trace = EraserLifecycleTrace.timeline(view.currentStrokeIdForTest())
            val stages = trace.map(EraserLifecycleTrace.Event::stage)
            assertTrue(stages.contains(EraserLifecycleTrace.Stage.RESTORE_DEFERRED))
            assertTrue(stages.contains(EraserLifecycleTrace.Stage.FINISH_QUEUED))
            assertTrue(stages.contains(EraserLifecycleTrace.Stage.COMMITTED))
            assertTrue(stages.contains(EraserLifecycleTrace.Stage.RESTORE_DISPATCHED))
            assertTrue("Temporary-tool restore must never cancel the completed erase", EraserLifecycleTrace.Stage.CANCELLED !in stages)
            assertTrue(
                "Temporary-tool restore must happen after the committed generation is presented: $stages",
                stages.indexOf(EraserLifecycleTrace.Stage.RESTORE_DISPATCHED) > stages.indexOf(EraserLifecycleTrace.Stage.COMMITTED),
            )
        } finally {
            EraserLifecycleTrace.enabled = false
            EraserLifecycleTrace.reset()
        }
    }

    @Test fun independentButtonAndHoverReleasesCannotCancelHeldEraserContact() {
        for (releaseRoute in listOf(1, 2)) for (afterUp in listOf(false, true)) {
            val surface = opaqueSurface(400, 400)
            var releases = 0
            var commits = 0
            val view = configuredView(mapOf("paint" to surface)).apply {
                switchInputConfiguration(EditorTool.PEN, null)
                onRasterLayerCommitted = { commits++ }
                onShortcutAction = { activation ->
                    if (activation.action == ShortcutAction.TOGGLE_ERASER) {
                        if (activation.phase == ShortcutPhase.PRESS) switchInputConfiguration(EditorTool.ERASER, null)
                        else { releases++; switchInputConfiguration(EditorTool.PEN, null) }
                    }
                }
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 200f, 10L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 200f, 200f, 20L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                if (afterUp) {
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 200f, 20L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                    view.dispatchStylus(MotionEvent.ACTION_UP, 320f, 200f, 21L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                }
                view.dispatchStylus(if (releaseRoute == 1) MotionEvent.ACTION_BUTTON_RELEASE else MotionEvent.ACTION_HOVER_MOVE,
                    200f, 200f, 21L, 0, releaseRoute)
                assertEquals("Hardware release must wait for erase commit, route=$releaseRoute", 0, releases)
                if (!afterUp) {
                    view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 200f, 25L, 0)
                    view.dispatchStylus(MotionEvent.ACTION_UP, 320f, 200f, 30L, 0)
                }
            }
            settlePublishedStroke(view)
            assertEquals("One erase commit for route=$releaseRoute", 1, commits)
            assertEquals(1, releases)
            for (x in listOf(120, 200, 280)) assertEquals("Erase survives route=$releaseRoute at x=$x", 0, Color.alpha(surface.pixelAt(x, 200)))
        }
    }

    @Test fun immediateDrawingAfterHeldEraseUsesRestoredPenBeforeAnotherFrame() {
        for (releaseOnUp in listOf(true, false)) {
            val surface = opaqueSurface(400, 400)
            val view = configuredView(mapOf("paint" to surface)).apply {
                installV5TestInk(size = 36f, color = Color.GREEN)
                switchInputConfiguration(EditorTool.PEN, null)
                onShortcutAction = { activation ->
                    if (activation.action == ShortcutAction.TOGGLE_ERASER) {
                        switchInputConfiguration(if (activation.phase == ShortcutPhase.PRESS) EditorTool.ERASER else EditorTool.PEN, null)
                    }
                }
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 300f, 10L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 300f, 20L, MotionEvent.BUTTON_STYLUS_PRIMARY)
                view.dispatchStylus(MotionEvent.ACTION_UP, 320f, 300f, 30L, if (releaseOnUp) 0 else MotionEvent.BUTTON_STYLUS_PRIMARY)
                // No worker wait or frame between erasing and the next ordinary pen contact.
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 100f, 31L, 0)
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 100f, 40L, 0)
                view.dispatchStylus(MotionEvent.ACTION_UP, 320f, 100f, 50L, 0)
            }
            settlePublishedStroke(view)
            assertEquals("Completed erase must survive immediate drawing", 0, Color.alpha(surface.pixelAt(200, 300)))
            assertTrue("The next contact must paint instead of continuing the temporary eraser", Color.green(surface.pixelAt(200, 100)) > 0)
        }
    }

    @Test fun canceledV5EraserContactRollsBackWithoutPublishing() {
        val surface = opaqueSurface(400, 400)
        val initialRevision = surface.revision
        var commits = 0
        val view = configuredView(mapOf("paint" to surface)).apply {
            onRasterLayerCommitted = { commits++ }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 200f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_CANCEL, 320f, 200f, 30L)
        }

        assertEquals(initialRevision, surface.revision)
        assertEquals(255, Color.alpha(surface.pixelAt(200, 200)))
        assertEquals(0, commits)
    }

    @Test fun eraserPublishesOnlyTheActiveRasterLayer() {
        val note = opaqueSurface(400, 400)
        val highlight = opaqueSurface(400, 400)
        val committedLayers = mutableListOf<String>()
        val view = configuredView(mapOf("note" to note, "highlight" to highlight)).apply {
            activeLayerId = "highlight"
            onRasterLayerCommitted = { committedLayers += it.layerId }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 200f, 10L)
            view.dispatchStylus(MotionEvent.ACTION_MOVE, 320f, 200f, 20L)
            view.dispatchStylus(MotionEvent.ACTION_UP, 320f, 200f, 30L)
        }
        settlePublishedStroke(view)

        assertEquals(listOf("highlight"), committedLayers)
        assertEquals(0, Color.alpha(highlight.pixelAt(200, 200)))
        assertEquals(255, Color.alpha(note.pixelAt(200, 200)))
    }

    @Test fun rapidProductionEraserPacketsStayInsideOne120HzFrameAtP95() {
        val surface = opaqueSurface(1_000, 1_000)
        val view = configuredView(mapOf("paint" to surface)).apply { eraserWidth = 72f }
        val packetTimes = ArrayList<Long>(120)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.dispatchStylus(MotionEvent.ACTION_DOWN, 80f, 500f, 10L)
            repeat(120) { index ->
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 84f + index * 7f, 500f + index % 5, 20L + index)
                packetTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
            }
            view.dispatchStylus(MotionEvent.ACTION_UP, 930f, 500f, 200L)
        }
        settlePublishedStroke(view)

        val sorted = packetTimes.drop(8).sorted()
        val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
        android.util.Log.i("InkloomPerf", "v5_production_eraser_input_p95_ms=$p95Ms")
        assertTrue("V5 production eraser p95 was ${p95Ms}ms", p95Ms < 8.33f)
    }

    @Test fun firstAndOneHundredConsecutiveEraseGesturesNeverRestoreCommittedAlpha() {
        val surface = opaqueSurface(512, 256)
        var commits = 0
        val view = configuredView(mapOf("paint" to surface)).apply {
            eraserWidth = 18f
            onRasterLayerCommitted = { commits++ }
        }

        repeat(100) { index ->
            val y = 3f + index * 2.45f
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.dispatchStylus(MotionEvent.ACTION_DOWN, 32f, y, 100L + index * 4L)
                view.dispatchStylus(MotionEvent.ACTION_MOVE, 480f, y, 101L + index * 4L)
                view.dispatchStylus(MotionEvent.ACTION_UP, 480f, y, 102L + index * 4L)
            }
            settlePublishedStroke(view)
            assertEquals("Erase gesture ${index + 1} restored its committed alpha", 0, Color.alpha(surface.pixelAt(256, y.toInt())))
        }
        assertEquals("Every completed erase gesture must commit exactly once", 100, commits)
    }

    @Test fun rejectedPalmLiftDoesNotCancelAnInProgressErase() {
        verifyPalmDuringErase(palmFirst = true)
    }

    @Test fun palmLandingAndLeavingCannotRestartOrFinishThePenStroke() {
        verifyPalmDuringErase(palmFirst = false)
    }

    private fun verifyPalmDuringErase(palmFirst: Boolean) {
        for (penType in listOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER)) {
            val surface = opaqueSurface(400, 400)
            val initialRevision = surface.revision
            var commits = 0
            val view = configuredView(mapOf("paint" to surface)).apply {
                eraserWidth = 24f
                onRasterLayerCommitted = { commits++ }
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                fun packet(action: Int, x: Float, time: Long, palm: Boolean = true, flags: Int = 0, pen: Boolean = true) {
                    val types = if (!pen) listOf(MotionEvent.TOOL_TYPE_FINGER)
                        else if (!palm) listOf(penType)
                        else if (palmFirst) listOf(MotionEvent.TOOL_TYPE_FINGER, penType)
                        else listOf(penType, MotionEvent.TOOL_TYPE_FINGER)
                    val props = types.map { type -> MotionEvent.PointerProperties().apply {
                        id = if (type == MotionEvent.TOOL_TYPE_FINGER) 9 else 31; toolType = type
                    } }.toTypedArray()
                    val coords = types.map { type -> MotionEvent.PointerCoords().apply {
                        this.x = if (type == MotionEvent.TOOL_TYPE_FINGER) 380f else x
                        y = if (type == MotionEvent.TOOL_TYPE_FINGER) 370f else 200f
                        pressure = .8f; touchMajor = if (type == MotionEvent.TOOL_TYPE_FINGER) 80f else 1f
                    } }.toTypedArray()
                    val event = MotionEvent.obtain(10L, time, action, types.size, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, flags)
                    try { view.onTouchEvent(event) } finally { event.recycle() }
                }
                if (palmFirst) {
                    packet(MotionEvent.ACTION_DOWN, 40f, 10L, pen = false)
                    packet(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 40f, 20L)
                } else {
                    packet(MotionEvent.ACTION_DOWN, 40f, 10L, palm = false)
                    packet(MotionEvent.ACTION_MOVE, 130f, 20L, palm = false)
                    packet(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 140f, 30L)
                }
                packet(MotionEvent.ACTION_MOVE, 240f, 40L)
                val palmIndex = if (palmFirst) 0 else 1
                packet(MotionEvent.ACTION_POINTER_UP or (palmIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 240f, 50L, flags = MotionEvent.FLAG_CANCELED)
                packet(MotionEvent.ACTION_MOVE, 350f, 60L, palm = false)
                packet(MotionEvent.ACTION_UP, 350f, 70L, palm = false)
            }
            settlePublishedStroke(view)
            assertEquals("Palm events must not split or cancel pen type $penType", 1, commits)
            assertEquals(initialRevision + 1, surface.revision)
            for (x in listOf(80, 200, 310)) assertEquals("Erase must survive on both sides of palm transition at x=$x", 0, Color.alpha(surface.pixelAt(x, 200)))
        }
    }

    private fun configuredView(surfaces: Map<String, PixelTileSurface>): InkCanvasView {
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, surfaces.values.first().logicalWidth, surfaces.values.first().logicalHeight)
                tool = EditorTool.ERASER
                eraserWidth = 160f
                eraserHardness = 1f
                eraserOpacity = 1f
                activeLayerId = surfaces.keys.first()
                setRasterLayerSurfaces(surfaces)
            }
        }
        return view
    }

    private fun settlePublishedStroke(view: InkCanvasView) {
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val frame = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try { view.draw(Canvas(frame)) } finally { frame.recycle() }
        }
    }

    private fun opaqueSurface(width: Int, height: Int) = PixelTileSurface(width, height).also { surface ->
        surface.beginWrite("seed eraser fixture").use { transaction ->
            for (y in 0 until height) for (x in 0 until width) transaction.setPixel(x, y, Color.BLACK)
            transaction.commit().close()
        }
    }

    private fun InkCanvasView.dispatchStylus(action: Int, x: Float, y: Float, time: Long, buttonState: Int = 0, route: Int = 0) {
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
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        ).also { event -> try { when (route) { 1 -> onGenericMotionEvent(event); 2 -> onHoverEvent(event); else -> onTouchEvent(event) } } finally { event.recycle() } }
    }
}
