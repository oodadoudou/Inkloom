package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.BrushGroupStore
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.brushpresetv5.BrushPresetV5Import
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
    ).map(BrushPresetV5Import::convertDecodedSource)

    private val preloadRule = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                    .putString("language", "ENGLISH")
                    .putInt("studioWorkspaceVersion", 2)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("brushGridLayout", false)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showPageRail", false)
                    .putString("brushPanelDock", "RIGHT")
                    .remove("workspaceLayouts")
                    .commit()
                val scrollingFixtures = if (description.methodName in setOf("ordinaryListAndGridSwipesOnlyScrollBrushes", "partlyClippedHandleUsesTheActualPointerDropPosition", "partlyClippedGridHandleUsesTheActualPointerDropPosition")) List(30) {
                    brush("scroll-$it", "Scroll %02d".format(it), "Source", it + 2)
                }.map(BrushPresetV5Import::convertDecodedSource) else emptyList()
                app.inkloom.settings.BrushUsageStore(context).save(app.inkloom.settings.BrushUsageState())
                BrushMetadataStore(context).save(sourceBrushes + scrollingFixtures)
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
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onAllNodesWithText("Create").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onAllNodesWithContentDescription("Brushes").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Brush group $group").fetchSemanticsNodes().isNotEmpty()
        }
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

    @Test fun explicitManagementSelectsAndMovesABrushToAnotherGroup() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Select multiple brushes").performClick()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performClick()
        composeRule.onNodeWithText("Move").performClick()
        composeRule.onAllNodesWithText("Target").onLast().performClick()
        composeRule.onNodeWithText("Move selected").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().firstOrNull { it.id == "batch-a" }?.groupName == "Target" }
        assertEquals("Target", store.load().single { it.id == "batch-a" }.groupName)
    }

    @Test fun explicitManagementMovesMultipleSelectedBrushes() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Select multiple brushes").performClick()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performClick()
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

    @Test fun swipeDeleteRemovesOnlyTheChosenImportedBrush() {
        openBrushes()
        val store = BrushMetadataStore(composeRule.activity)
        val before = store.load()
        val row = windowBounds("Brush preset Batch A")
        windowDrag(Offset(row.left + row.width() * .7f, row.exactCenterY()),
            Offset(row.left + row.width() * .08f, row.exactCenterY()))
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntil(5_000) { store.load().none { it.id == "batch-a" } }
        assertEquals(before.filterNot { it.id == "batch-a" }, store.load())
    }

    @Test fun ordinaryListAndGridSwipesOnlyScrollBrushes() {
        openBrushes()
        val store = BrushMetadataStore(composeRule.activity)
        val before = store.load()
        repeat(2) { layout ->
            if (layout == 1) composeRule.onNodeWithContentDescription("Grid view").performClick()
            composeRule.onNodeWithContentDescription("Brush preset Batch A").assertIsDisplayed()
            val card = windowBounds("Brush preset Scroll 02")
            val list = windowBounds("Brush presets")
            val start = Offset(card.left + card.width() * .3f, card.exactCenterY())
            val end = Offset(start.x, maxOf(list.top + 30f, start.y - list.height() * .55f))
            windowDrag(start, end)
            assertEquals("Scrolling must not activate management", 0,
                composeRule.onAllNodesWithContentDescription("Exit brush management").fetchSemanticsNodes().size)
            assertEquals(0, composeRule.onAllNodesWithText("Brush Editor", substring = true).fetchSemanticsNodes().size)
            assertEquals("Scrolling must not reorder or move brush records", before, store.load())
            assertTrue("The list must actually scroll", runCatching {
                composeRule.onNodeWithContentDescription("Brush preset Batch A").assertIsDisplayed()
            }.isFailure)
        }
    }

    @Test fun partlyClippedHandleUsesTheActualPointerDropPosition() = checkClippedHandle(grid = false)

    @Test fun partlyClippedGridHandleUsesTheActualPointerDropPosition() = checkClippedHandle(grid = true)

    private fun checkClippedHandle(grid: Boolean) {
        openBrushes()
        if (grid) composeRule.onNodeWithContentDescription("Grid view").performClick()
        val handle = composeRule.onNodeWithContentDescription("Move brush Batch A")
        val original = handle.fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithContentDescription("Brush presets")
        val scroll = original.top - list.fetchSemanticsNode().boundsInRoot.top + original.height / 2f
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, scroll) }
        composeRule.waitForIdle()
        val visible = composeRule.onNodeWithContentDescription("Move brush Batch A").fetchSemanticsNode().boundsInRoot
        assertTrue("The fixture must expose only part of the handle", visible.height > 0f && visible.height < original.height)
        handleDrag("Brush group Target")
        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().any { it.name == "Batch A copy" && it.groupName == "Target" && it.id != "batch-a" } }
        assertEquals("Source", store.load().single { it.id == "batch-a" }.groupName)
        assertEquals("Source", store.load().single { it.id == "batch-b" }.groupName)
    }

    @Test fun handleLongPressDragCopiesTheBrushToItsGroupTarget() = checkHandleGroupDrop(grid = false)

    @Test fun gridHandleLongPressDragCopiesTheBrushToItsGroupTarget() = checkHandleGroupDrop(grid = true)

    private fun checkHandleGroupDrop(grid: Boolean) {
        openBrushes()
        if (grid) composeRule.onNodeWithContentDescription("Grid view").performClick()
        handleDrag("Brush group Target")
        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().any { it.name == "Batch A copy" && it.groupName == "Target" && it.id != "batch-a" } }
        assertEquals("Source", store.load().single { it.id == "batch-a" }.groupName)
        assertEquals("Source", store.load().single { it.id == "batch-b" }.groupName)
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Exit brush management").fetchSemanticsNodes().size)
    }

    @Test fun handleCancellationLeavesRecordsUntouchedAndPeerDropReorders() = checkHandlePeerDrop(grid = false)

    @Test fun gridHandleCancellationLeavesRecordsUntouchedAndPeerDropReorders() = checkHandlePeerDrop(grid = true)

    private fun checkHandlePeerDrop(grid: Boolean) {
        openBrushes()
        if (grid) composeRule.onNodeWithContentDescription("Grid view").performClick()
        val store = BrushMetadataStore(composeRule.activity)
        val before = store.load()
        fun drag(cancel: Boolean) {
            handleDrag("Brush preset Batch B", cancel = cancel)
        }
        handleDrag(null)
        assertEquals("Dropping outside targets must not move records", before, store.load())
        drag(cancel = true)
        assertEquals(before, store.load())
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Exit brush management").fetchSemanticsNodes().size)
        drag(cancel = false)
        composeRule.waitUntil(5_000) {
            store.load().filter { it.groupName == "Source" }.minByOrNull { it.sortOrder }?.id == "batch-b"
        }
        assertEquals(setOf("batch-a", "batch-b"), store.load().filter { it.groupName == "Source" }.map { it.id }.toSet())
        val first = windowBounds("Brush preset Batch B")
        val second = windowBounds("Brush preset Batch A")
        assertTrue("The visible cards must follow the saved reorder", if (grid) {
            first.top < second.top || (first.top == second.top && first.left < second.left)
        } else first.top < second.top)
    }

    private fun handleDrag(targetLabel: String?, cancel: Boolean = false) {
        val handle = composeRule.onNodeWithContentDescription("Move brush Batch A")
        val origin = handle.fetchSemanticsNode().boundsInRoot
        val target = targetLabel?.let {
            composeRule.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.center
        } ?: Offset(30f, 300f)
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(target - origin.center, 300)
            if (cancel) cancel() else up()
        }
        composeRule.waitForIdle()
    }

    private fun windowBounds(label: String): android.graphics.Rect {
        var result: android.graphics.Rect? = null
        composeRule.waitUntil(5_000) {
            if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.contentDescription?.toString() == label) result = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
            result != null
        }
        return requireNotNull(result)
    }

    private fun windowDrag(start: Offset, end: Offset, hold: Boolean = false, cancel: Boolean = false) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, point: Offset) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = 1f; size = .01f }),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, start)
        if (hold) SystemClock.sleep(700)
        repeat(20) {
            SystemClock.sleep(10)
            send(MotionEvent.ACTION_MOVE, start + (end - start) * ((it + 1) / 20f))
        }
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, end)
        composeRule.waitForIdle()

    }

    @Test fun heldBrushSurvivesCategorySwitchAndMovesIntoItsList() {
        openBrushes()
        val row = composeRule.onNodeWithContentDescription("Brush preset Batch A")
        val bounds = row.fetchSemanticsNode().boundsInRoot
        val target = composeRule.onNodeWithContentDescription("Brush group Target").fetchSemanticsNode().boundsInRoot.center
        row.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(target - bounds.center, 300)
        }
        composeRule.mainClock.advanceTimeBy(650)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Brush preset Target C").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Brush preset Target C").performTouchInput { moveTo(center); up() }
        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().single { it.id == "batch-a" }.groupName == "Target" }
        assertEquals(1, store.load().count { it.name == "Batch A" })
        composeRule.onNodeWithText("Brush Studio").assertDoesNotExist()
    }

    @Test fun wholeRowLongPressDragCopiesToAnotherGroup() {
        openBrushes()
        val row = composeRule.onNodeWithContentDescription("Brush preset Batch A")
        val bounds = row.fetchSemanticsNode().boundsInRoot
        val target = composeRule.onNodeWithContentDescription("Brush group Target").fetchSemanticsNode().boundsInRoot.center
        row.performTouchInput {
            down(Offset(width * .35f, height * .7f))
            advanceEventTime(700)
            moveBy(target - (bounds.topLeft + Offset(bounds.width * .35f, bounds.height * .7f)), 300)
            up()
        }
        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().any { it.name == "Batch A copy" && it.groupName == "Target" } }
        composeRule.onNodeWithText("Brush Studio").assertDoesNotExist()
    }

    @Test fun stationaryLongPressDoesNotEditAndSelectedTapOpensStudio() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performTouchInput { longClick() }
        composeRule.onNodeWithText("Brush Studio").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performClick()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performClick()
        composeRule.onNodeWithText("Brush Studio").assertIsDisplayed()
        composeRule.onNodeWithText("Drawing pad").assertIsDisplayed()
    }

    @Test fun longPressGroupRenamesItsWholeSubtree() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush group actions Source").performClick()
        composeRule.onNodeWithText("Rename group").performClick()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Favorites")
        composeRule.onNodeWithText("Rename").performClick()

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) { store.load().count { it.groupName.startsWith("Favorites") } == 3 }
        assertEquals(setOf("Favorites", "Favorites/Ink/Pens"), store.load().filter { it.id.startsWith("batch") || it.id == "nested-d" }.mapTo(linkedSetOf(), ImportedBrush::groupName))
    }

    @Test fun longPressDragReordersBrushSetTabs() {
        openBrushes()
        val source = composeRule.onNodeWithContentDescription("Brush group Source")
        val targetBounds = composeRule.onNodeWithContentDescription("Brush group Target").fetchSemanticsNode().boundsInRoot
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        source.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, targetBounds.center.y - sourceBounds.center.y), 300)
            up()
        }

        val store = BrushMetadataStore(composeRule.activity)
        composeRule.waitUntil(5_000) {
            app.inkloom.importer.BrushGroupOperations.treePaths(store.load()).firstOrNull() == "Target"
        }
        assertEquals("Target", app.inkloom.importer.BrushGroupOperations.treePaths(store.load()).first())
    }

    @Test fun groupManagerCreatesAndPersistsAnEmptyNestedGroup() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Manage brush groups").performClick()
        composeRule.onNodeWithContentDescription("New brush group path").performTextInput("Empty/Erasers")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertTrue("Empty/Erasers" in BrushGroupStore(composeRule.activity).load())
    }

    @Test fun creatingAGroupWhilePencilIsSelectedCreatesItInsidePencil() {
        openBrushes()
        composeRule.onNodeWithText("Pencil").performClick()
        composeRule.onNodeWithContentDescription("Manage brush groups").performClick()
        composeRule.onNodeWithContentDescription("New brush group path").performTextInput("Sketching")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("Done").performClick()

        val store = BrushGroupStore(composeRule.activity)
        // The full Android 15 matrix can leave Room/file IO briefly contended after the large
        // real-brush corpus tests. Wait for the durable write rather than assuming an idle device.
        composeRule.waitUntil(15_000) { "Pencil/Sketching" in store.load() }
        assertTrue("Pencil/Sketching" in store.load())
        composeRule.waitUntil(15_000) {
            runCatching { composeRule.onNodeWithText("Sketching").assertIsDisplayed(); true }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Sketching").assertIsDisplayed().performClick()
    }

    @Test fun selectedCustomBrushCommitsEveryRapidPenContact() {
        openBrushes()
        composeRule.onNodeWithContentDescription("Brush preset Batch A").performClick()
        val view = findInkCanvasView()!!
        composeRule.waitUntil(5_000) { view.brushId == "batch-a" }
        composeRule.onNodeWithContentDescription("Close brushes panel").performClick()
        val v5GesturesBefore = view.v5DryGestureCount

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(6) { dispatchStylusStroke(view, it) }
        }
        assertTrue(
            "Every rapid pen contact must enter exactly one realtime paint transaction",
            view.v5DryGestureCount - v5GesturesBefore >= 6,
        )
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
