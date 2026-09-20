package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.ToolbarDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class ToolRailWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun verticalRailExposesEveryToolAndActivatesPaintBucket() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        listOf("Brush", "Marker", "Eraser tool", "Select", "Eyedropper", "Paint bucket",
            "Gradient", "Blend", "Liquify", "Correct line", "Shape", "Text", "Note",
            "Link", "Image", "Laser pointer").forEach { label ->
            compose.onNodeWithContentDescription(label).assertExists()
        }
        compose.onNodeWithContentDescription("Paint bucket").performClick()
        assertEquals(EditorTool.FILL, canvas().tool)
    }

    @Test fun draggingRailToRightEdgeDocksAndCancelRestoresPlacement() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun handle(): Offset {
            val node = compose.onAllNodesWithContentDescription("Drag toolbar").onFirst().fetchSemanticsNode()
            return node.boundsInWindow.center + node.positionOnScreen - node.positionInWindow
        }
        fun drag(end: Offset, cancel: Boolean = false) {
            val start = handle()
            val down = SystemClock.uptimeMillis()
            fun packet(action: Int, point: Offset) {
                val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
                motion.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) }
                finally { motion.recycle() }
            }
            packet(MotionEvent.ACTION_DOWN, start)
            for (index in 1..24) {
                packet(MotionEvent.ACTION_MOVE, start + (end - start) * (index / 24f))
                SystemClock.sleep(16)
            }
            packet(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, end)
            compose.waitForIdle()
        }
        val screenWidth = compose.activity.resources.displayMetrics.widthPixels.toFloat()
        drag(Offset(screenWidth - 35f, handle().y))
        assertEquals(ToolbarDock.RIGHT, AppSettingsStore(compose.activity).load().toolbarDock)
        drag(Offset(screenWidth / 2f, handle().y), cancel = true)
        assertEquals(ToolbarDock.RIGHT, AppSettingsStore(compose.activity).load().toolbarDock)
    }

    private fun canvas(): InkCanvasView {
        fun find(view: View): InkCanvasView? {
            if (view is InkCanvasView) return view
            if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it))?.let { result -> return result } }
            return null
        }
        return requireNotNull(find(compose.activity.window.decorView))
    }
}
