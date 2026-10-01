package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class RasterFilterWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH")
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun previewCancelApplyAndUndoShareOneActualWindowHistory() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val rect = IntArray(2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.getLocationOnScreen(rect) }
        val y = rect[1] + view.height / 2f
        val x1 = rect[0] + view.width * .25f
        val x2 = rect[0] + view.width * .75f
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x1, y, down)
        repeat(16) { index -> event(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * (index + 1) / 16f, y, down) }
        event(MotionEvent.ACTION_UP, x2, y, down)
        compose.waitUntil(15_000) { fingerprint(view) != 0 }
        val original = fingerprint(view)

        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Image filter…").performClick()
        compose.waitUntil(15_000) { fingerprint(view) != original }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(15_000) { fingerprint(view) == original }

        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Image filter…").performClick()
        compose.waitUntil(15_000) { fingerprint(view) != original }
        val filtered = fingerprint(view)
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000) { fingerprint(view) == filtered && !view.selectionLayerTransferPending }
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(15_000) { fingerprint(view) == original }
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.waitUntil(15_000) { fingerprint(view) == filtered }
    }

    private fun fingerprint(view: InkCanvasView): Int {
        val snapshot = view.activeRasterSnapshotForTest() ?: return 0
        return snapshot.copyPremultipliedPixels(PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight)).contentHashCode()
    }

    private fun canvas(): InkCanvasView {
        var result: InkCanvasView? = null
        compose.waitUntil(5_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
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

    private fun event(action: Int, x: Float, y: Float, down: Long) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y;
                pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(motion, true)) }
        finally { motion.recycle() }
    }
}
