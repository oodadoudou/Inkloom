package app.inkloom

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutBinding
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.StylusButtonAction
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasNavigationTest {
    @Test fun enabledSingleFingerDoubleTapOnBlankCanvasTriggersUndoOnce() {
        val view = canvas().apply {
            fingerDrawingEnabled = false
            singleFingerDoubleTapUndo = true
        }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_080L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_220L, MotionEvent.ACTION_DOWN, point(0, 304f, 302f))
        send(view, 1_300L, MotionEvent.ACTION_UP, point(0, 304f, 302f))

        assertEquals(1, undoCount)
    }

    @Test fun singleFingerDoubleTapUndoIsDisabledByDefault() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_080L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_220L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_300L, MotionEvent.ACTION_UP, point(0, 300f, 300f))

        assertEquals(0, undoCount)
    }

    @Test fun dragAndSecondPointerCancelSingleFingerDoubleTapUndo() {
        val view = canvas().apply {
            fingerDrawingEnabled = false
            singleFingerDoubleTapUndo = true
        }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_080L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_180L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_220L, MotionEvent.ACTION_MOVE, point(0, 360f, 300f))
        send(view, 1_260L, MotionEvent.ACTION_UP, point(0, 360f, 300f))

        send(view, 1_400L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_440L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_540L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_560L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 300f, 300f), point(1, 500f, 300f))
        send(view, 1_600L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 300f, 300f), point(1, 500f, 300f))
        send(view, 1_620L, MotionEvent.ACTION_UP, point(0, 300f, 300f))

        assertEquals(0, undoCount)
    }

    @Test fun twoFingerTranslationPansWithoutChangingSpan() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        var panX = 0f
        var panY = 0f
        view.onViewportPan = { dx, dy -> panX += dx; panY += dy }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 300f, 100f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 140f, 125f), point(1, 340f, 125f))
        send(view, 50L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 140f, 125f), point(1, 340f, 125f))
        send(view, 60L, MotionEvent.ACTION_UP, point(0, 140f, 125f))

        assertTrue("Two-finger translation should pan horizontally", panX >= 39f)
        assertTrue("Two-finger translation should pan vertically", panY >= 24f)
    }

    @Test fun secondFingerCancelsAnInProgressFingerStrokeBeforeNavigation() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = true
        }
        var committed = 0
        view.onStrokeCommitted = { committed++ }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_MOVE, point(0, 150f, 150f))
        send(view, 20L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 150f, 150f), point(1, 300f, 150f))
        send(view, 40L, MotionEvent.ACTION_MOVE, point(0, 180f, 170f), point(1, 330f, 170f))
        send(view, 60L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 180f, 170f), point(1, 330f, 170f))
        send(view, 70L, MotionEvent.ACTION_UP, point(0, 180f, 170f))

        assertEquals("The interrupted finger stroke must not leave ink", 0, committed)
    }

    @Test fun optionalTwoFingerRotationUsesAStableAnchorWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = false
            twoFingerRotationEnabled = true
        }
        var committed = 0
        var rotation = 0f
        view.onStrokeCommitted = { committed++ }
        view.onViewportRotate = { rotation += it }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 300f, 100f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 200f, 0f), point(1, 200f, 200f))
        send(view, 50L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 200f, 0f), point(1, 200f, 200f))
        send(view, 60L, MotionEvent.ACTION_MOVE, point(0, 230f, 30f))
        send(view, 70L, MotionEvent.ACTION_UP, point(0, 230f, 30f))

        assertEquals("Navigation gestures must never leave ink", 0, committed)
        assertEquals(90f, rotation, .001f)
    }

    @Test fun twoFingerRotationDoesNothingWhenTheSettingIsOff() {
        val view = canvas().apply { twoFingerRotationEnabled = false }
        var rotation = 0f
        view.onViewportRotate = { rotation += it }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 300f, 100f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 200f, 0f), point(1, 200f, 200f))

        assertEquals(0f, rotation, .001f)
    }

    @Test fun threeFingerTapTriggersRedoWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = false
            threeFingerTapRedo = true
        }
        var redoCount = 0
        var committed = 0
        view.onRedoGesture = { redoCount++ }
        view.onStrokeCommitted = { committed++ }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 20L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f))
        send(view, 40L, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f), point(2, 160f, 210f))
        send(view, 100L, MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f), point(2, 160f, 210f))
        send(view, 120L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f))
        send(view, 140L, MotionEvent.ACTION_UP, point(0, 100f, 100f))

        assertEquals(1, redoCount)
        assertEquals("Three-finger navigation must never leave ink", 0, committed)
    }

    @Test fun holdShortcutEmitsPressAndReleaseForTemporaryToolSwitching() {
        val view = canvas().apply {
            shortcutBindings = listOf(ShortcutBinding(ShortcutAction.TOGGLE_ERASER, ShortcutChord(KeyEvent.KEYCODE_E), ShortcutTrigger.HOLD))
        }
        val phases = mutableListOf<ShortcutPhase>()
        view.onShortcutAction = { phases += it.phase }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.onKeyDown(KeyEvent.KEYCODE_E, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E))
            view.onKeyUp(KeyEvent.KEYCODE_E, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_E))
        }

        assertEquals(listOf(ShortcutPhase.PRESS, ShortcutPhase.RELEASE), phases)
    }

    @Test fun stylusUndoButtonFiresOncePerPressAndNeverCommitsInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.UNDO
        }
        var undoCount = 0
        var committed = 0
        view.onUndoGesture = { undoCount++ }
        view.onStrokeCommitted = { committed++ }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals(1, undoCount)
        assertEquals("A stylus command button must never leave ink", 0, committed)
    }

    @Test fun secondaryStylusButtonCanTriggerRedoOnce() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            secondaryButtonAction = StylusButtonAction.REDO
        }
        var redoCount = 0
        view.onRedoGesture = { redoCount++ }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_SECONDARY)
        sendStylus(view, 20L, MotionEvent.ACTION_UP, 120f, 160f)

        assertEquals(1, redoCount)
    }

    @Test fun stylusHandButtonTemporarilyPansAndNeverCommitsInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.HAND
        }
        var panX = 0f
        var committed = 0
        view.onViewportPan = { dx, _ -> panX += dx }
        view.onStrokeCommitted = { committed++ }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 220f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 220f, 160f)

        assertTrue("The held stylus Hand button must pan", panX >= 99f)
        assertEquals("Stylus Hand navigation must never leave ink", 0, committed)
    }

    @Test fun pressingStylusHandButtonMidContactReanchorsWithoutJumping() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.HAND
        }
        var panX = 0f
        var committed = 0
        view.onViewportPan = { dx, _ -> panX += dx }
        view.onStrokeCommitted = { committed++ }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 40L, MotionEvent.ACTION_MOVE, 240f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 60L, MotionEvent.ACTION_UP, 240f, 160f)

        assertEquals("The first Hand-button event must establish a new anchor", 40f, panX, .001f)
        assertEquals("Switching to Hand mid-contact must cancel the partial stroke", 0, committed)
    }

    @Test fun forcedHandNavigationPansWithAStylusWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            forceNavigation = true
        }
        var committed = 0
        var panX = 0f
        var panY = 0f
        view.onStrokeCommitted = { committed++ }
        view.onViewportPan = { dx, dy -> panX += dx; panY += dy }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals("Hand navigation must never leave stylus ink", 0, committed)
        assertTrue("Stylus hand navigation should pan horizontally", panX >= 79f)
        assertTrue("Stylus hand navigation should pan vertically", panY >= 69f)
    }

    @Test fun eyedropperSamplesTheRenderedPageWithoutCommittingInk() {
        val sampledRed = android.graphics.Color.rgb(201, 32, 44)
        val view = canvas().apply {
            tool = EditorTool.EYEDROPPER
            pagePaperColor = sampledRed
        }
        var sampled: Int? = null
        var committed = 0
        view.onColorSampled = { sampled = it }
        view.onStrokeCommitted = { committed++ }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_UP, 120f, 160f)

        assertEquals(sampledRed, sampled)
        assertEquals("Eyedropper input must never leave ink", 0, committed)
    }

    @Test fun ctrlAltStylusDragAdjustsBrushSizeWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            baseWidth = 10f
        }
        var adjusted = 10f
        var committed = 0
        view.onBrushSizeAdjusted = { adjusted = it }
        view.onStrokeCommitted = { committed++ }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
            view.onKeyDown(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT))
        }
        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 300f, 160f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 300f, 160f)

        assertEquals(20f, adjusted, .001f)
        assertEquals("Brush-size drag must never leave ink", 0, committed)
    }

    @Test fun cancellingBrushSizeDragRestoresTheStartingSize() {
        val view = canvas().apply { tool = EditorTool.ERASER; eraserWidth = 18f }
        var adjusted = 18f
        view.onBrushSizeAdjusted = { adjusted = it }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
            view.onKeyDown(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT))
        }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 300f, 160f)
        sendStylus(view, 40L, MotionEvent.ACTION_CANCEL, 300f, 160f)

        assertEquals(18f, adjusted, .001f)
        assertEquals(18f, view.eraserWidth, .001f)
    }

    @Test fun forcedRotateNavigationRotatesWithAStylusWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            forceNavigation = true
            rotateNavigation = true
        }
        var committed = 0
        var rotation = 0f
        view.onStrokeCommitted = { committed++ }
        view.onViewportRotate = { rotation += it }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals("Rotate navigation must never leave stylus ink", 0, committed)
        assertEquals(20f, rotation, .001f)
    }

    @Test fun unmappedToolboxKeysStillReportRawDiagnosticFields() {
        val view = canvas()
        var keyCode = -1
        var scanCode = -1
        var phase: ShortcutPhase? = null
        var matched = true
        view.onKeyInputDiagnostic = {
            keyCode = it.keyCode
            scanCode = it.scanCode
            phase = it.phase
            matched = it.matchedAction != null
        }

        view.onKeyDown(KeyEvent.KEYCODE_F12, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12))

        assertEquals(KeyEvent.KEYCODE_F12, keyCode)
        assertEquals(0, scanCode)
        assertEquals(ShortcutPhase.PRESS, phase)
        assertFalse("Unmapped hardware input must remain visible in diagnostics", matched)
    }

    private fun canvas(): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 1000, 1000) }
        }
        return result
    }

    private fun point(id: Int, x: Float, y: Float): Pair<MotionEvent.PointerProperties, MotionEvent.PointerCoords> =
        MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } to
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }

    private fun send(view: InkCanvasView, time: Long, action: Int, vararg points: Pair<MotionEvent.PointerProperties, MotionEvent.PointerCoords>) {
        val event = MotionEvent.obtain(
            0L,
            time,
            action,
            points.size,
            points.map { it.first }.toTypedArray(),
            points.map { it.second }.toTypedArray(),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }

    private fun sendStylus(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float, buttonState: Int = 0) {
        val event = MotionEvent.obtain(
            0L,
            time,
            action,
            1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .6f; size = .05f }),
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }
}
