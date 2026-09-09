package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasContentDescription
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.diagnostics.BrushExecutionFingerprint
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File

/** Real file picker -> import ledger -> visible card -> production raster black-box gate. */
class ProcreateBrushsetUiImportTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val cleanRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit()
                    .clear()
                    .putString("language", "ENGLISH")
                    .putInt("studioWorkspaceVersion", 2)
                    .putBoolean("showBrushPanel", false)
                    .putBoolean("showLayerPanel", false)
                    .putBoolean("showPageRail", false)
                    .commit()
                context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
                context.filesDir.resolve("library.json").delete()
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(cleanRule).around(composeRule)

    @Test fun realBrushsetImportsSelectsRendersAndPersistsThroughVisibleUi() = assertVisibleImport(null)

    @Test fun ordinaryBrushsetImportedUnderErasersStillPaintsAndPersists() = assertVisibleImport("Erasers")

    @Test fun folderPickerPreservesNestedGroupsAndReimportDoesNotDuplicate() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        composeRule.onNodeWithText("New Note").performClick();composeRule.onNodeWithText("Create").performClick()
        fun importFolder() {
            composeRule.onNodeWithContentDescription("Brushes").performClick()
            composeRule.onNodeWithContentDescription("Add brush or group").performClick()
            composeRule.onNodeWithText("Import folder").performClick()
            waitForAccessibilityNode(5_000) { it.contentDescription?.toString()=="Show roots" }?.let(::clickNodeOrAncestor)
            waitForAccessibilityNode(3_000) { it.text?.toString() in setOf("Downloads","Download") && it.isVisibleToUser && clickNodeOrAncestor(it) }
            // A repeated tree grant can reopen directly inside the previously selected folder.
            waitForAccessibilityNode(3_000) { it.text?.toString()=="Inkloom-Folder-Test" && clickNodeOrAncestor(it) }
            assertTrue(waitForAccessibilityNode(5_000) { it.text?.toString()?.equals("Use this folder",true)==true && clickNodeOrAncestor(it) } != null)
            waitForAccessibilityNode(5_000) { it.text?.toString()?.equals("Allow",true)==true }?.let(::clickNodeOrAncestor)
            composeRule.waitUntil(120_000) { composeRule.onAllNodesWithText("Folder import result").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(composeRule.onAllNodesWithText("Broken.sut", substring = true).fetchSemanticsNodes().isNotEmpty())
            composeRule.onNodeWithText("Done").performClick()
            composeRule.onNodeWithContentDescription("Close brushes panel").performClick()
        }
        importFolder()
        val context=ApplicationProvider.getApplicationContext<Context>()
        val brushes=app.inkloom.importer.BrushMetadataStore(context).load()
        val groups=app.inkloom.importer.BrushGroupStore(context).load()
        assertTrue(groups.containsAll(listOf("Inkloom-Folder-Test","Inkloom-Folder-Test/Ink","Inkloom-Folder-Test/Paint/Soft","Inkloom-Folder-Test/Empty")))
        assertTrue(brushes.any { it.groupName=="Inkloom-Folder-Test/Ink" })
        assertTrue(brushes.any { it.groupName=="Inkloom-Folder-Test/Paint/Soft" })
        val count=brushes.size
        importFolder()
        assertEquals(count,app.inkloom.importer.BrushMetadataStore(context).load().size)
        composeRule.activityRule.scenario.recreate();composeRule.waitForIdle()
        assertEquals(groups,app.inkloom.importer.BrushGroupStore(context).load())
    }

    private fun assertVisibleImport(targetGroup: String?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        val view = waitForCanvas()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        if (targetGroup != null) {
            composeRule.onNodeWithContentDescription("Brush category $targetGroup").performClick()
        }
        composeRule.onNodeWithContentDescription("Add brush or group").performClick()
        val importLabel = if (composeRule.onAllNodesWithText("Import brushes").fetchSemanticsNodes().isNotEmpty()) {
            "Import brushes"
        } else {
            "Import from files"
        }
        composeRule.onNodeWithText(importLabel).performClick()

        // DocumentsUI remembers the last directory per caller. Navigate through its real UI
        // so a previous manual import cannot silently change this fixture's starting location.
        val roots = waitForAccessibilityNode(3_000) { it.contentDescription?.toString() == "Show roots" }
        assertTrue("System picker roots control missing", roots != null && clickNodeOrAncestor(roots))
        // The drawer list can rebind while providers finish loading. A node found in one
        // accessibility snapshot may already be stale when clicked; wait for an accepted click.
        val downloads = waitForAccessibilityNode(3_000) {
            it.text?.toString() == "Downloads" && it.viewIdResourceName == "android:id/title" &&
                it.isVisibleToUser && clickNodeOrAncestor(it)
        }
        assertTrue("Downloads root did not accept a click", downloads != null)
        val fileNode = waitForAccessibilityNode(10_000) { it.text?.contains("Inkloom-P0-Test") == true }
        assertTrue("System picker did not show staged real brushset", fileNode != null)
        assertTrue("Staged brushset row was not clickable", clickNodeOrAncestor(fileNode!!))
        waitForAccessibilityNode(3_000) { it.text?.toString()?.equals("Open", ignoreCase = true) == true }
            ?.let(::clickNodeOrAncestor)
        assertTrue(
            "System picker did not return the selected brushset to Inkloom",
            waitForAccessibilityNode(15_000) { it.packageName?.toString()?.startsWith("app.inkloom") == true } != null,
        )

        composeRule.waitUntil(120_000) {
            composeRule.onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithContentDescription("Brush group Inkloom-P0-Test")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty()) {
            val report = mutableListOf<String>()
            fun collect(node: AccessibilityNodeInfo?) {
                if (node == null) return
                node.text?.toString()?.let(report::add)
                repeat(node.childCount) { collect(node.getChild(it)) }
            }
            collect(instrumentation.uiAutomation.rootInActiveWindow)
            report.joinToString(" | ").chunked(3_000).forEach { android.util.Log.i("InkloomImportUI", it) }
            composeRule.onNodeWithText("OK").performClick()
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("Brush group Inkloom-P0-Test")
                .fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithContentDescription("Close brushes panel").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithContentDescription("Brushes").performClick()
        }
        composeRule.onNodeWithContentDescription("Brush group Inkloom-P0-Test").performClick()

        val sketch = selectAndDraw(view, "吹石-速写笔", "56640da5-113d-3a6c-98bb-8a8ba2032c7a", .32f)
        val ink = selectAndDraw(view, "吹石-飞墨签字笔", "67744578-f755-31d2-9ce3-4c778a681998", .54f)
        val paint = selectAndDraw(view, "吹石-方头软刷", "2b104bec-e90c-3432-b2d3-576cecf0278f", .76f)
        assertNotEquals(sketch.compiledProgramHash, ink.compiledProgramHash)
        assertNotEquals(sketch.compiledProgramHash, paint.compiledProgramHash)
        assertNotEquals(ink.compiledProgramHash, paint.compiledProgramHash)
        assertTrue(listOf(sketch, ink, paint).all { it.committedTileHash.isNotBlank() })

        val snapshot = requireNotNull(view.activeRasterSnapshotForTest())
        val pixels = snapshot.copyPremultipliedPixels(PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
        val artifact = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
            .resolve("procreate-ui-import-raster.png")
        Bitmap.createBitmap(pixels, snapshot.logicalWidth, snapshot.logicalHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            try { artifact.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        shell("cp ${artifact.absolutePath} /data/local/tmp/inkloom-procreate-ui-import-raster.png")

        composeRule.activityRule.scenario.recreate()
        val restored = waitForCanvas()
        composeRule.waitUntil(10_000) { restored.brushId == paint.presetId }
        assertEquals(EditorTool.PEN, restored.tool)
        instrumentation.runOnMainSync { dispatchBehaviorPattern(restored, .88f) }
        restored.awaitV5StrokeWorkerIdleForTest()
        composeRule.waitUntil(10_000) { restored.lastBrushExecutionFingerprint?.presetId == paint.presetId }
        assertEquals(paint.presetId, restored.lastBrushExecutionFingerprint?.presetId)
    }

    private fun selectAndDraw(
        view: InkCanvasView,
        name: String,
        expectedId: String,
        row: Float,
    ): BrushExecutionFingerprint {
        // Drawing auto-closes the library. Reopen it through the same user-facing control.
        if (composeRule.onAllNodesWithContentDescription("Close brushes panel").fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Brush presets")
            .performScrollToNode(hasContentDescription("Brush preset $name"))
        composeRule.onNodeWithContentDescription("Brush preset $name").performClick()
        composeRule.waitUntil(10_000) { view.brushId == expectedId }
        assertEquals("Library group must not turn a paint brush into an eraser", EditorTool.PEN, view.tool)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.baseWidth = 46f
            dispatchBehaviorPattern(view, row)
        }
        view.awaitV5StrokeWorkerIdleForTest()
        composeRule.waitUntil(10_000) { view.lastBrushExecutionFingerprint?.presetId == expectedId }
        return requireNotNull(view.lastBrushExecutionFingerprint)
    }

    private fun waitForCanvas(): InkCanvasView {
        composeRule.waitUntil(10_000) { findCanvas() != null }
        return requireNotNull(findCanvas())
    }

    private fun findCanvas(): InkCanvasView? {
        var result: InkCanvasView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun walk(view: android.view.View) {
                if (view is InkCanvasView && view.isShown) result = view
                else if (view is ViewGroup) repeat(view.childCount) { walk(view.getChildAt(it)) }
            }
            walk(composeRule.activity.window.decorView)
        }
        return result
    }

    private fun dispatchBehaviorPattern(view: InkCanvasView, row: Float) {
        val downTime = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun event(action: Int, x: Float, y: Float, elapsed: Long, pressure: Float, tilt: Float) = MotionEvent.obtain(
            downTime, downTime + elapsed, action, 1, properties,
            arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x; this.y = y; this.pressure = pressure; size = .02f
                setAxisValue(MotionEvent.AXIS_TILT, tilt)
            }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
        fun stroke(points: List<FloatArray>) {
            points.forEachIndexed { index, point ->
                val action = when (index) {
                    0 -> MotionEvent.ACTION_DOWN
                    points.lastIndex -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                event(action, point[0], point[1], point[2].toLong(), point[3], point[4]).also { motion ->
                    try { view.dispatchTouchEvent(motion) } finally { motion.recycle() }
                }
            }
        }
        val w = view.width.toFloat()
        val y = view.height * row
        stroke(listOf(
            floatArrayOf(w * .08f, y, 0f, .65f, .10f),
            floatArrayOf(w * .085f, y, 4f, .65f, .10f),
        ))
        stroke(listOf(
            floatArrayOf(w * .14f, y, 10f, .12f, .12f),
            floatArrayOf(w * .24f, y - 10f, 18f, .35f, .12f),
            floatArrayOf(w * .34f, y + 8f, 26f, .68f, .12f),
            floatArrayOf(w * .44f, y, 34f, 1f, .12f),
        ))
        stroke(listOf(
            floatArrayOf(w * .49f, y, 40f, .72f, .02f),
            floatArrayOf(w * .58f, y - 10f, 48f, .72f, .28f),
            floatArrayOf(w * .67f, y + 8f, 56f, .72f, .55f),
            floatArrayOf(w * .76f, y, 64f, .72f, .82f),
        ))
        repeat(2) { pass ->
            val offset = pass * 5f
            stroke(listOf(
                floatArrayOf(w * .80f, y - 10f + offset, 70f + pass * 18f, .58f, .40f),
                floatArrayOf(w * .86f, y + 10f + offset, 78f + pass * 18f, .58f, .40f),
                floatArrayOf(w * .92f, y - 4f + offset, 86f + pass * 18f, .58f, .40f),
            ))
        }
    }

    private fun waitForAccessibilityNode(
        timeoutMillis: Long,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            val root = automation.rootInActiveWindow
            if (root != null) {
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.add(root)
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    if (predicate(node)) return node
                    repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
                }
            }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        return null
    }

    private fun clickNodeOrAncestor(start: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = start
        repeat(6) {
            val current = node ?: return false
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = current.parent
        }
        return false
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
}
