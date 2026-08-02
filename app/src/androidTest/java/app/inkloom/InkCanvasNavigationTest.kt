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
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasNavigationTest {
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
}
