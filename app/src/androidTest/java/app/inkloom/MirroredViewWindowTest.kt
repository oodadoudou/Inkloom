package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Flip view horizontally is view only: pen input follows the mirror and the artwork is never flipped. */
class MirroredViewWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun aStrokeOnTheRightOfAFlippedViewLandsOnTheLeftOfThePage() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        // Mirroring is about the page centre, so the page keeps the same rectangle on screen.
        val bounds = pageScreenBounds(view)
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Flip view horizontally").performClick()
        compose.onNodeWithContentDescription("View flipped horizontally. Tap to restore").assertIsDisplayed()

        // A short horizontal stroke across 70–85 % of the visible page from the left of the screen.
        val y = bounds.top + bounds.height() * .5f
        stroke(Offset(bounds.left + bounds.width() * .70f, y), Offset(bounds.left + bounds.width() * .85f, y))
        compose.waitUntil(10_000) { !empty(view) }
        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        fun inkIn(from: Float, to: Float) = (0 until 40).any { step ->
            val x = (snapshot.logicalWidth * (from + (to - from) * step / 40f)).toInt()
            (-4..4).any { dy -> snapshot.pixelAt(x, snapshot.logicalHeight / 2 + dy) != 0 }
        }
        assertTrue("Ink lands on the mirrored side of the page", inkIn(.15f, .30f))
        assertFalse("Nothing is drawn where the pen visually was", inkIn(.70f, .85f))

        compose.onNodeWithContentDescription("View flipped horizontally. Tap to restore").performClick()
        compose.onNodeWithContentDescription("View flipped horizontally. Tap to restore").assertDoesNotExist()
        assertFalse(view.viewportState().mirrored)
    }

    /** The page's on-screen rectangle at the unflipped fit-to-window view. */
    private fun pageScreenBounds(view: InkCanvasView): android.graphics.RectF {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        return android.graphics.RectF(location[0].toFloat(), location[1].toFloat(), location[0] + view.width.toFloat(), location[1] + view.height.toFloat())
    }

    private fun stroke(start: Offset, end: Offset) {
        val down = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, start, down)
        for (i in 1..20) { SystemClock.sleep(10); motion(MotionEvent.ACTION_MOVE, start + (end - start) * (i / 20f), down) }
        motion(MotionEvent.ACTION_UP, end, down)
        compose.waitForIdle()
    }

    private fun motion(action: Int, point: Offset, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun empty(view: InkCanvasView): Boolean {
        val snapshot = view.activeRasterSnapshotForTest() ?: return true
        return snapshot.tileInfo.none { it.nonTransparentPixelCount > 0 }
    }

    private fun canvas(): InkCanvasView {
        var result: InkCanvasView? = null
        compose.waitUntil(5_000) {
            instrumentation.runOnMainSync {
                fun visit(view: View) {
                    if (view is InkCanvasView && view.isShown) result = view
                    else if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(compose.activity.window.decorView)
            }
            result != null
        }
        return requireNotNull(result)
    }
}
