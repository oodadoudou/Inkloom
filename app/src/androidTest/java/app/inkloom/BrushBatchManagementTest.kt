package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.BrushGroupStore
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class BrushBatchManagementTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val sourceBrushes = listOf(
        brush("batch-a", "Batch A", "Source", 0),
        brush("batch-b", "Batch B", "Source", 1),
        brush("target-c", "Target C", "Target", 0),
        brush("nested-d", "Nested Ink", "Source/Ink/Pens", 0),
    )

    private val preloadRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                    .putString("language", "ENGLISH")
                    .putInt("studioWorkspaceVersion", 2)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showPageRail", false)
                    .putString("brushPanelDock", "RIGHT")
                    .remove("workspaceLayouts")
                    .commit()
                BrushMetadataStore(context).save(sourceBrushes)
                BrushGroupStore(context).save(emptyList())
                try {
                    base.evaluate()
                } finally {
                    BrushMetadataStore(context).save(emptyList())
                    BrushGroupStore(context).save(emptyList())
                }
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(preloadRule).around(composeRule)

    private fun openBrushes(group: String = "Source") {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Brush group $group").performClick()
    }

    @Test fun popoverKeepsSystemAndImportedGroupsAndSwitchesListGridLayout() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Grid view").performClick()
        composeRule.onNodeWithContentDescription("List view").assertIsDisplayed()
        composeRule.onNodeWithText("Pencil").performClick()
        composeRule.onNodeWithContentDescription("Brush preset Technical Pencil").assertIsDisplayed()
    }

    @Test fun nestedGroupsExpandAndCollapseAsAnAccordion() {
        openBrushes()
        composeRule.onNodeWithText("Ink").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Collapse group Source").performClick()
        assertEquals(0, composeRule.onAllNodesWithText("Ink").fetchSemanticsNodes().size)
        composeRule.onNodeWithContentDescription("Expand group Source").performClick()
        composeRule.onNodeWithText("Ink").assertIsDisplayed()
    }

    @Test fun longPressBrushSelectsAndMovesItToAnotherGroup() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performTouchInput { longClick() }
        composeRule.onNodeWithText("Move").performClick()
        composeRule.onAllNodesWithText("Target").onLast().performClick()
        composeRule.onNodeWithText("Move selected").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().firstOrNull { it.id == "batch-a" }?.groupName == "Target" }
        assertEquals("Target", store.load().single { it.id == "batch-a" }.groupName)
    }

    @Test fun longPressEntersManagementAndMovesMultipleSelectedBrushes() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Brush management: 1 selected").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Brush preset Batch B").performClick()
        composeRule.onNodeWithContentDescription("Brush management: 2 selected").assertIsDisplayed()
        composeRule.onNodeWithText("Move").performClick()
        composeRule.onAllNodesWithText("Target").onLast().performClick()
        composeRule.onNodeWithText("Move selected").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) {
            store.load().filter { it.id in setOf("batch-a", "batch-b") }.all { it.groupName == "Target" }
        }
        assertEquals(
            setOf("batch-a", "batch-b"),
            store.load().filter { it.groupName == "Target" && it.id.startsWith("batch-") }.mapTo(linkedSetOf(), ImportedBrush::id),
        )
    }

    @Test fun longPressGroupRenamesItsWholeSubtree() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush group Source").performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename group").performClick()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Favorites")
        composeRule.onNodeWithText("Rename").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().count { it.groupName.startsWith("Favorites") } == 3 }
        assertEquals(setOf("Favorites", "Favorites/Ink/Pens"), store.load().filter { it.id.startsWith("batch") || it.id == "nested-d" }.mapTo(linkedSetOf(), ImportedBrush::groupName))
    }

    @Test fun groupManagerCreatesAndPersistsAnEmptyNestedGroup() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Manage brush groups").performClick()
        composeRule.onNodeWithContentDescription("New brush group path").performTextInput("Empty/Erasers")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertTrue("Empty/Erasers" in BrushGroupStore(composeRule.activity).load())
    }

    @Test fun selectedCustomBrushCommitsEveryRapidPenContact() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performClick()
        val view = findInkCanvasView()!!
        composeRule.waitUntil(5_000) { view.brushId == "batch-a" }
        composeRule.onNodeWithContentDescription("Close brushes panel").performClick()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(6) { dispatchStylusStroke(view, it) }
        }
        assertEquals(6, (reflectedField(view, "strokes") as? List<*>)?.size)
    }

    private fun findInkCanvasView(): InkCanvasView? {
        var found: InkCanvasView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun walk(view: android.view.View) {
                if (view is InkCanvasView && view.isShown) found = view
                else if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
            walk(composeRule.activity.window.decorView)
        }
        return found
    }

    private fun dispatchStylusStroke(view: InkCanvasView, index: Int) {
        val downTime = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun event(action: Int, x: Float, y: Float, elapsed: Long) = MotionEvent.obtain(
            downTime, downTime + elapsed, action, 1, properties,
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .75f; size = .05f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
        val y = view.height * (.2f + index * .09f)
        listOf(
            event(MotionEvent.ACTION_DOWN, view.width * .25f, y, 0),
            event(MotionEvent.ACTION_MOVE, view.width * .5f, y + 4f, 4),
            event(MotionEvent.ACTION_UP, view.width * .7f, y + 7f, 8),
        ).forEach { motion -> try { view.dispatchTouchEvent(motion) } finally { motion.recycle() } }
    }

    private fun reflectedField(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching { type.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field.get(target)
            }
            type = type.superclass
        }
        return null
    }

    private companion object {
        fun brush(id: String, name: String, group: String, order: Int) = ImportedBrush(
            id = id,
            name = name,
            format = BrushFormat.IMAGE,
            compatibility = Compatibility.COMPLETE,
            sizeBytes = 1L,
            report = emptyList(),
            groupName = group,
            sortOrder = order,
        )
    }
}
