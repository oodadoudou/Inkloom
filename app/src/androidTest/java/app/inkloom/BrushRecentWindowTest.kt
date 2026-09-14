package app.inkloom

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.BrushUsageState
import app.inkloom.settings.BrushUsageStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class BrushRecentWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brush_usage", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun currentCategorySwipePinSurvivesClosingAndUnpinRestoresOrder() {
        tap("New Note"); tap("Create"); tap("Brushes"); tap("Brush category Pencil")
        val store = BrushUsageStore(context)
        swipeAndTap("Brush preset HB Pencil", "Pin")
        compose.waitUntil(5_000) { "system-hb-pencil" in store.load().categoryPins["Pencil"].orEmpty() }
        assertTrue(store.load().pinnedIds.isEmpty())
        tap("Close brushes panel"); tap("Brushes")
        bounds("Pinned brush")
        swipeAndTap("Brush preset HB Pencil", "Unpin")
        compose.waitUntil(5_000) { store.load().categoryPins["Pencil"].isNullOrEmpty() }
        bounds("Brush preset HB Pencil")
    }

    @Test fun swipePinFindAndClearKeepFactoryBrushAndSurviveReopening() {
        tap("New Note"); tap("Create"); tap("Brushes")
        tap("Brush category Pencil"); tap("Brush preset HB Pencil")
        tap("Brush category Recent")
        val store = BrushUsageStore(context)
        compose.waitUntil(5_000) { store.load().recentIds.firstOrNull() == "system-hb-pencil" }
        swipeAndTap("Brush preset HB Pencil", "Pin")
        compose.waitUntil(5_000) { "system-hb-pencil" in store.load().pinnedIds }
        // The brush is already active; a second tap intentionally opens Studio.
        tap("Close brushes panel"); tap("Brushes")
        compose.waitUntil(5_000) { store.load().category == BrushUsageState.RECENT_CATEGORY }
        bounds("Pinned brush")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        if (find("Close brushes panel").isEmpty()) tap("Brushes")
        bounds("Pinned brush")
        swipeAndTap("Brush preset HB Pencil", "Find")
        compose.waitUntil(5_000) { store.load().category == "Pencil" }
        bounds("Brush preset HB Pencil")
        tap("Brush category Recent")
        swipeAndTap("Brush preset HB Pencil", "Clear")
        compose.waitUntil(5_000) { "system-hb-pencil" !in store.load().pinnedIds && "system-hb-pencil" !in store.load().recentIds }
        tap("Brush category Pencil")
        bounds("Brush preset HB Pencil") // Clear never deletes the source preset.
    }

    private fun find(label: String): List<Rect> {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val result = mutableListOf<Rect>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            node ?: return
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                result += Rect().also(node::getBoundsInScreen)
            }
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        return result.filterNot(Rect::isEmpty)
    }

    private fun bounds(label: String, row: Rect? = null): Rect {
        var result: Rect? = null
        compose.waitUntil(5_000) {
            result = find(label).firstOrNull { row == null || it.centerY() in row.top..row.bottom }
            result != null
        }
        return requireNotNull(result)
    }

    private fun tap(label: String, row: Rect? = null) {
        val target = bounds(label, row); val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, target.exactCenterX(), target.exactCenterY(), down)
        event(MotionEvent.ACTION_UP, target.exactCenterX(), target.exactCenterY(), down)
        compose.waitForIdle()
    }

    private fun swipeAndTap(label: String, action: String) {
        val row = bounds(label); val down = SystemClock.uptimeMillis()
        // The outer edge belongs to the user-resizable floating panel.
        val start = row.left + row.width() * .85f; val end = row.left + row.width() * .08f
        event(MotionEvent.ACTION_DOWN, start, row.exactCenterY(), down)
        repeat(24) { i ->
            SystemClock.sleep(8)
            event(MotionEvent.ACTION_MOVE, start + (end - start) * (i + 1) / 24f, row.exactCenterY(), down)
        }
        event(MotionEvent.ACTION_UP, end, row.exactCenterY(), down)
        compose.waitForIdle()
        tap(action, row)
    }

    private fun event(action: Int, x: Float, y: Float, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
}
