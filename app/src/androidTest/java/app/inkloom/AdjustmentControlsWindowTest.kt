package app.inkloom

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.*
import app.inkloom.ui.InkloomViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class AdjustmentControlsWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private lateinit var model: InkloomViewModel
    private fun createNote() {
        compose.onNodeWithText("New Note").performClick(); compose.onNodeWithText("Create").performClick()
        compose.runOnIdle { model = androidx.lifecycle.ViewModelProvider(compose.activity)[InkloomViewModel::class.java] }
        compose.onNodeWithContentDescription("Layers").performClick()
    }
    private fun add(label: String) {
        compose.onNodeWithContentDescription("Add adjustment layer").performClick()
        compose.onNodeWithText(label).performClick()
    }
    private fun adjustment(kind: LayerAdjustmentKind): InkLayer = requireNotNull(model.currentPage()).layers.last { it.adjustment?.kind == kind }
    private fun progress(label: String, value: Float) = compose.onNodeWithContentDescription("$label adjustment")
        .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }

    @Test fun gradientMapEditsManyStopsAndPickerCancelApplyUndoAndReopen() {
        createNote(); add("Gradient Map")
        compose.onNodeWithText("Add color stop").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Gradient stop position 2").performSemanticsAction(SemanticsActions.SetProgress) { it(.3f) }
        compose.onNodeWithContentDescription("Gradient stop color 2").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithContentDescription("Hex color").performTextReplacement("FF0000")
        compose.onAllNodesWithText("Apply").onLast().performClick()
        compose.onNodeWithText("Add color stop").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Delete gradient stop 3").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        val value = adjustment(LayerAdjustmentKind.GRADIENT_MAP).adjustment!!
        assertEquals(3, value.gradient.size)
        assertEquals(.3f, value.gradient[1].position, .001f)
        assertEquals(0xFFFF0000L, value.gradient[1].colorArgb)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        assertEquals(2, adjustment(LayerAdjustmentKind.GRADIENT_MAP).adjustment!!.gradient.size)
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        val name = adjustment(LayerAdjustmentKind.GRADIENT_MAP).name
        compose.onNodeWithContentDescription("Edit adjustment $name").performScrollTo().performClick()
        compose.onNodeWithText("Reset gradient").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(value, adjustment(LayerAdjustmentKind.GRADIENT_MAP).adjustment)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(value, adjustment(LayerAdjustmentKind.GRADIENT_MAP).adjustment)
    }

    @Test fun nativeCurveGraphAddsDragsDeletesAndNumericChannelsPersistWithOneUndo() {
        createNote(); add("Curves")
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun bounds(): android.graphics.Rect {
            compose.onNodeWithContentDescription("Tone curve graph").performScrollTo()
            var found: android.graphics.Rect? = null
            compose.waitUntil(5_000) {
                if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
                fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                    if (node == null) return
                    if (node.contentDescription?.toString() == "Tone curve graph") found = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                    else repeat(node.childCount) { visit(node.getChild(it)) }
                }
                visit(instrumentation.uiAutomation.rootInActiveWindow); found != null
            }
            return found!!
        }
        fun gesture(start: android.graphics.PointF, end: android.graphics.PointF = start, cancel: Boolean = false) {
            val down = android.os.SystemClock.uptimeMillis()
            fun event(action: Int, point: android.graphics.PointF) {
                val motion = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, 1,
                    arrayOf(android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = android.view.MotionEvent.TOOL_TYPE_STYLUS }),
                    arrayOf(android.view.MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = .6f }),
                    0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_STYLUS, 0)
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
            }
            event(android.view.MotionEvent.ACTION_DOWN, start)
            repeat(8) { n ->
                android.os.SystemClock.sleep(16)
                val t = (n + 1) / 8f
                event(android.view.MotionEvent.ACTION_MOVE, android.graphics.PointF(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t))
            }
            event(if (cancel) android.view.MotionEvent.ACTION_CANCEL else android.view.MotionEvent.ACTION_UP, end)
            compose.waitForIdle()
        }
        fun at(bounds: android.graphics.Rect, x: Float, y: Float) = android.graphics.PointF(bounds.left + bounds.width() * x, bounds.top + bounds.height() * y)
        var graph = bounds()
        gesture(at(graph, .4f, .3f))
        compose.onNodeWithContentDescription("Input curve value").performTextReplacement("102")
        compose.onNodeWithContentDescription("Output curve value").performTextReplacement("180")
        compose.onNodeWithText("Smooth", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Red", substring = false).performScrollTo().performClick()
        graph = bounds(); gesture(at(graph, .5f, .4f))
        compose.onNodeWithContentDescription("Input curve value").performTextReplacement("128")
        compose.onNodeWithContentDescription("Output curve value").performTextReplacement("100")
        compose.onNodeWithText("Blue", substring = false).performScrollTo().performClick()
        graph = bounds(); gesture(at(graph, .5f, .5f))
        gesture(at(graph, .5f, .5f), at(graph, .6f, .2f), cancel = true)
        // Delete by dragging outside, then add and remove using the explicit control as well.
        gesture(at(graph, .5f, .5f), android.graphics.PointF(graph.exactCenterX(), graph.top - 60f))
        gesture(at(graph, .5f, .4f))
        compose.onNodeWithContentDescription("Delete curve point").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        val value = adjustment(LayerAdjustmentKind.CURVES).adjustment!!
        assertTrue(value.curveSmooth)
        assertEquals(3, value.curve.size)
        assertEquals(102f / 255f, value.curve[1].input, .0001f)
        assertEquals(180f / 255f, value.curve[1].output, .0001f)
        assertEquals(3, value.redCurve.size)
        assertEquals(100f / 255f, value.redCurve[1].output, .0001f)
        assertEquals(2, value.blueCurve.size)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        assertEquals(LayerAdjustment(LayerAdjustmentKind.CURVES), adjustment(LayerAdjustmentKind.CURVES).adjustment)
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(value, adjustment(LayerAdjustmentKind.CURVES).adjustment)
    }

    @Test fun levelsKeepIndependentChannelsAndResetCancelUndoAndReopen() {
        createNote(); add("Levels")
        compose.onNodeWithText("Red", substring = false).performClick(); progress("Output white", .6f)
        compose.onNodeWithText("Green", substring = false).performClick(); progress("Gamma", 2f)
        compose.onNodeWithText("Blue", substring = false).performClick(); progress("Input black", .1f)
        compose.onNodeWithText("RGB", substring = false).performClick(); progress("Output black", .2f)
        compose.onNodeWithText("Apply").performClick()
        val value = adjustment(LayerAdjustmentKind.LEVELS).adjustment!!
        assertEquals(.6f, value.redLevels.outputWhite, .001f)
        assertEquals(2f, value.greenLevels.inputGamma, .001f)
        assertEquals(.1f, value.blueLevels.inputBlack, .001f)
        assertEquals(.2f, value.outputBlack, .001f)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        assertEquals(LayerAdjustment(LayerAdjustmentKind.LEVELS), adjustment(LayerAdjustmentKind.LEVELS).adjustment)
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        val name = adjustment(LayerAdjustmentKind.LEVELS).name
        compose.onNodeWithContentDescription("Edit adjustment $name").performScrollTo().performClick()
        compose.onNodeWithText("Red", substring = false).performClick()
        compose.onNodeWithText("Reset channel").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(value, adjustment(LayerAdjustmentKind.LEVELS).adjustment)
        compose.onNodeWithContentDescription("Edit adjustment $name").performScrollTo().performClick()
        compose.onNodeWithText("Green", substring = false).performClick()
        compose.onNodeWithText("Reset channel").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        val reset = adjustment(LayerAdjustmentKind.LEVELS).adjustment!!
        assertEquals(AdjustmentLevels(), reset.greenLevels)
        assertEquals(value.redLevels, reset.redLevels)
        assertEquals(value.blueLevels, reset.blueLevels)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(reset, adjustment(LayerAdjustmentKind.LEVELS).adjustment)
    }

    @Test fun correctionMenuCreatesRealKindsAndPersistsParameters() {
        createNote()
        add("Invert"); compose.onNodeWithText("Apply").performClick()
        assertNotNull(adjustment(LayerAdjustmentKind.INVERT))
        add("Posterize"); progress("Levels per channel", 5.6f); compose.onNodeWithText("Apply").performClick()
        assertEquals(6, adjustment(LayerAdjustmentKind.POSTERIZE).adjustment!!.posterizeLevels)
        add("Threshold"); progress("Threshold", .7f); compose.onNodeWithText("Apply").performClick()
        assertEquals(.7f, adjustment(LayerAdjustmentKind.THRESHOLD).adjustment!!.threshold, .001f)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithText("Gallery").assertIsDisplayed()
        assertEquals(6, adjustment(LayerAdjustmentKind.POSTERIZE).adjustment!!.posterizeLevels)
        assertEquals(.7f, adjustment(LayerAdjustmentKind.THRESHOLD).adjustment!!.threshold, .001f)
        assertNotNull(adjustment(LayerAdjustmentKind.INVERT))
    }

    @Test fun luminosityPreviewsCanCancelAndCommitAsOneUndoThenReopen() {
        createNote(); add("Hue / Saturation / Luminosity")
        progress("Luminosity", .6f)
        assertEquals("Preview must leave saved metadata untouched", 0f,
            adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, 0f)
        compose.onNodeWithText("Apply").performClick()
        val id = adjustment(LayerAdjustmentKind.HUE_SATURATION).id
        assertEquals(.6f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, .001f)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        assertEquals(0f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, 0f)
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        val name = adjustment(LayerAdjustmentKind.HUE_SATURATION).name
        compose.onNodeWithContentDescription("Edit adjustment $name").performScrollTo().performClick()
        progress("Luminosity", -.4f); compose.onNodeWithText("Cancel").performClick()
        assertEquals(.6f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, .001f)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(id, adjustment(LayerAdjustmentKind.HUE_SATURATION).id)
        assertEquals(.6f, adjustment(LayerAdjustmentKind.HUE_SATURATION).adjustment!!.luminosity, .001f)
    }
}
