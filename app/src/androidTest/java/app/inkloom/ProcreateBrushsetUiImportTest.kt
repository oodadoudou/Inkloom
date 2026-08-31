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
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.diagnostics.BrushExecutionFingerprint
import app.inkloom.engine.v5.surface.PixelRect
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

    @Test fun realBrushsetImportsSelectsRendersAndPersistsThroughVisibleUi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        val view = waitForCanvas()
        composeRule.onNodeWithContentDescription("Brushes").performClick()
        composeRule.onNodeWithContentDescription("Add brush or group").performClick()
        val importLabel = if (composeRule.onAllNodesWithText("Import brushes").fetchSemanticsNodes().isNotEmpty()) {
            "Import brushes"
        } else {
            "Import from files"
        }
        composeRule.onNodeWithText(importLabel).performClick()

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
        instrumentation.runOnMainSync { dispatchCanonicalStroke(restored, .86f) }
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
        composeRule.onNodeWithContentDescription("Brush preset $name").performClick()
        composeRule.waitUntil(10_000) { view.brushId == expectedId }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.baseWidth = 46f
            dispatchCanonicalStroke(view, row)
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

    private fun dispatchCanonicalStroke(view: InkCanvasView, row: Float) {
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
        val y = view.height * row
        listOf(
            event(MotionEvent.ACTION_DOWN, view.width * .18f, y, 0, .18f, .05f),
            event(MotionEvent.ACTION_MOVE, view.width * .35f, y - 15f, 8, .42f, .25f),
            event(MotionEvent.ACTION_MOVE, view.width * .52f, y + 12f, 16, .72f, .55f),
            event(MotionEvent.ACTION_UP, view.width * .68f, y, 24, 1f, .78f),
        ).forEach { motion -> try { view.dispatchTouchEvent(motion) } finally { motion.recycle() } }
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
