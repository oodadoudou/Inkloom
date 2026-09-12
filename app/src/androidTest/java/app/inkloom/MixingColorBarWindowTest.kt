package app.inkloom

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.ColorSetLibrary
import app.inkloom.settings.ColorSetLibraryStore
import app.inkloom.settings.MixingColorBarStore
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Real window taps/long presses make every edit; stores and canvas are read-only evidence. */
class MixingColorBarWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("color-mixing-board.bin").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun wheelRoundTripKeepsDefaultsAndReplacesTheSelectedIdentityOnly() {
        val notebook = "Independent mixing colors ${SystemClock.uptimeMillis()}"
        openNewNotebook(notebook)
        val mainPaletteBefore = saveMainPaletteColor()
        tap("Mix")
        val defaults = requireNotNull(mixingBar().activeSet)
        assertEquals(DEFAULT_COLORS, defaults.swatches.map { it.color })
        assertEquals(9, defaults.swatches.map { it.id }.distinct().size)
        DEFAULT_COLORS.forEach { bounds(colorLabel(it)) }

        val customColor = chooseWheelColor(.8f, .3f)
        tap("Mix")
        tap("Add mixing color")
        val withCustom = requireNotNull(mixingBar().activeSet)
        assertEquals("Adding a custom color must preserve all nine starter colors", defaults.swatches, withCustom.swatches.take(9))
        assertEquals(10, withCustom.swatches.size)
        val custom = withCustom.swatches.last()
        assertEquals(customColor, custom.color)
        assertEquals(custom.id, withCustom.selectedSwatchId)
        assertEquals(mainPaletteBefore, mainPalette())

        val firstId = defaults.swatches.first().id
        tap(colorLabel(DEFAULT_COLORS.first()))
        assertEquals(firstId, mixingBar().activeSet?.selectedSwatchId)
        val replacementColor = chooseWheelColor(.55f, .15f)
        assertNotEquals(customColor, replacementColor)
        tap("Mix")
        bounds("Color mixing board")
        assertEquals("Switching tabs must not reset the selected color-bar identity", firstId, mixingBar().activeSet?.selectedSwatchId)
        tap("Mixing palette menu")
        tap("Replace selected color")
        val replaced = requireNotNull(mixingBar().activeSet)
        assertEquals(withCustom.swatches.map { it.id }, replaced.swatches.map { it.id })
        assertEquals(firstId, replaced.selectedSwatchId)
        assertEquals(replacementColor, replaced.swatches.first().color)
        assertEquals("Unselected colors must remain byte-for-byte unchanged", withCustom.swatches.drop(1), replaced.swatches.drop(1))
        assertEquals(mainPaletteBefore, mainPalette())

        // Long press the appended swatch rather than calling the model's delete operation.
        longPress(colorLabel(customColor))
        tap("Delete color")
        val deleted = requireNotNull(mixingBar().activeSet)
        assertEquals(replaced.swatches.filterNot { it.id == custom.id }, deleted.swatches)
        assertEquals(firstId, deleted.selectedSwatchId)
        assertEquals(mainPaletteBefore, mainPalette())

        closeColorPanel()
        tap("Gallery")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        tap("Notebook $notebook. Long press to move.")
        tap("Colors")
        // Mix is the last open tab, and its separate library must survive Activity recreation.
        bounds("Color mixing board")
        bounds(colorLabel(replacementColor))
        assertEquals(deleted, mixingBar().activeSet)
        assertEquals(mainPaletteBefore, mainPalette())
        tap("Palettes")
        bounds("Color tile 1")
        assertEquals(mainPaletteBefore, mainPalette())
    }

    @Test fun confirmedResetAffectsOnlyTheColorBarAndRetainsPaintedMixingBoard() {
        openNewNotebook("Reset mixing colors ${SystemClock.uptimeMillis()}")
        val view = canvas()
        val documentRevision = view.activeRasterSnapshotForTest()?.revision
        val mainPaletteBefore = saveMainPaletteColor()
        tap("Mix")
        val initial = requireNotNull(mixingBar().activeSet)
        assertEquals(DEFAULT_COLORS, initial.swatches.map { it.color })

        val customColor = chooseWheelColor(.8f, .3f)
        tap("Mix")
        tap("Add mixing color")
        val customId = requireNotNull(mixingBar().activeSet).swatches.last().id

        // Context replacement must edit the held tile without first picking its old color.
        val replacementColor = chooseWheelColor(.65f, .15f)
        tap("Mix")
        longPress(colorLabel(customColor))
        tap("Replace with current")
        val edited = requireNotNull(mixingBar().activeSet)
        assertEquals(customId, edited.swatches.last().id)
        assertEquals(replacementColor, edited.swatches.last().color)
        assertEquals(initial.swatches, edited.swatches.take(9))
        assertEquals(mainPaletteBefore, mainPalette())

        // Paint and pick through the Android window before resetting the separate color bar.
        SystemClock.sleep(500)
        tap("Paint on mixing board")
        val area = bounds("Color mixing board")
        val center = Offset(area.exactCenterX(), area.exactCenterY())
        stroke(Offset(area.left + area.width() * .2f, center.y), Offset(area.left + area.width() * .8f, center.y))
        tap("Pick from mixing board")
        tapAt(center)
        compose.waitForIdle()
        val paintedColor = view.inkColor
        assertTrue("The sampled stroke must contain the selected violet pigment", android.graphics.Color.blue(paintedColor) > android.graphics.Color.green(paintedColor) + 20)

        tap("Mixing palette menu")
        tap("Reset color bar")
        bounds("Replace the mixing colors with the original colors?")
        tap("Cancel")
        assertEquals("Cancel must retain every custom color and its selection", edited, mixingBar().activeSet)
        assertEquals(mainPaletteBefore, mainPalette())

        tap("Mixing palette menu")
        tap("Reset color bar")
        tap("Reset")
        val reset = requireNotNull(mixingBar().activeSet)
        assertEquals(initial.swatches, reset.swatches)
        assertNull(reset.selectedSwatchId)
        assertEquals(mainPaletteBefore, mainPalette())

        // Pick a different foreground, then sample the same board point to detect accidental clearing.
        tap(colorLabel(DEFAULT_COLORS.first()))
        assertNotEquals(paintedColor, view.inkColor)
        tap("Pick from mixing board")
        tapAt(center)
        compose.waitForIdle()
        assertEquals("Resetting colors must not clear the independent mixing board", paintedColor, view.inkColor)
        assertEquals("Color-bar edits must not write to the notebook's raster history", documentRevision, view.activeRasterSnapshotForTest()?.revision)
        val beforeReopen = mixingBar()
        closeColorPanel()
        tap("Colors")
        bounds("Color mixing board")
        assertEquals(beforeReopen, mixingBar())
        assertEquals(mainPaletteBefore, mainPalette())
    }

    private fun mainPalette(): ColorSetLibrary = ColorSetLibraryStore(context).load()
    private fun mixingBar(): ColorSetLibrary = MixingColorBarStore(context).load()

    private fun saveMainPaletteColor(): ColorSetLibrary {
        tap("Colors")
        tap("Palettes")
        tap("Save current color")
        return mainPalette().also { assertEquals(1, it.activeSet?.swatches?.size) }
    }

    private fun chooseWheelColor(saturation: Float, verticalFraction: Float): Int {
        tap("Wheel")
        val ring = bounds("Hue ring")
        tapAt(ColorWheelTestCoordinates.hue(ring, 270f))
        compose.waitForIdle()
        val field = bounds("Saturation and brightness field")
        tapAt(ColorWheelTestCoordinates.triangle(field, saturation, 1f - verticalFraction))
        compose.waitForIdle()
        return canvas().inkColor
    }

    private fun openNewNotebook(name: String) {
        tap("New Note")
        compose.onNodeWithText("Title").performTextReplacement(name)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        tap("Create")
    }

    private fun closeColorPanel() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { findBounds("Color mixing board") == null }
    }

    private fun longPress(label: String) {
        val paletteLeft = bounds("Color mixing board").left
        val target = bounds(label)
        val point = Offset(target.exactCenterX(), target.exactCenterY())
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, down, MotionEvent.TOOL_TYPE_FINGER)
        try {
            val holdDuration = ViewConfiguration.getLongPressTimeout().toLong() + 150L
            // Native input supplies real event times, while combinedClickable's coroutine
            // delay belongs to Compose's test clock. Advance both before releasing contact.
            instrumentation.waitForIdleSync()
            SystemClock.sleep(holdDuration)
            compose.mainClock.advanceTimeBy(holdDuration)
            compose.waitForIdle()
            event(MotionEvent.ACTION_UP, point, down, MotionEvent.TOOL_TYPE_FINGER)
        } catch (failure: Throwable) {
            event(MotionEvent.ACTION_CANCEL, point, down, MotionEvent.TOOL_TYPE_FINGER)
            throw failure
        }
        compose.waitForIdle()
        assertTrue("A nested color menu must remain beside its palette, not jump to the screen origin",
            bounds("Replace with current").left >= paletteLeft - 16)
    }

    private fun tap(label: String) {
        val target = bounds(label)
        tapAt(Offset(target.exactCenterX(), target.exactCenterY()))
        compose.waitForIdle()
    }

    private fun tapAt(point: Offset) {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, down, MotionEvent.TOOL_TYPE_FINGER)
        SystemClock.sleep(8)
        event(MotionEvent.ACTION_UP, point, down, MotionEvent.TOOL_TYPE_FINGER)
    }

    private fun stroke(start: Offset, end: Offset) {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, down, MotionEvent.TOOL_TYPE_STYLUS)
        repeat(24) { step ->
            SystemClock.sleep(8)
            event(MotionEvent.ACTION_MOVE, start + (end - start) * ((step + 1) / 24f), down, MotionEvent.TOOL_TYPE_STYLUS)
        }
        event(MotionEvent.ACTION_UP, end, down, MotionEvent.TOOL_TYPE_STYLUS)
        compose.waitForIdle()
    }

    private fun bounds(label: String): Rect {
        var result: Rect? = null
        try {
            compose.waitUntil(5_000) { result = findBounds(label); result != null }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Missing visible mixing-color target: $label. ${visibleLabels()}", timeout)
        }
        return requireNotNull(result) { "Missing visible mixing-color target: $label" }
    }

    private fun findBounds(label: String): Rect? {
        clearAccessibilityCache()
        fun visit(node: AccessibilityNodeInfo?): Rect? {
            if (node == null) return null
            if (!node.refresh()) return null
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
                val rectangle = Rect().also(node::getBoundsInScreen)
                if (!rectangle.isEmpty) return rectangle
            }
            repeat(node.childCount) { index -> visit(node.getChild(index))?.let { return it } }
            return null
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun clearAccessibilityCache() {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
    }

    private fun visibleLabels(): String {
        clearAccessibilityCache()
        val labels = linkedSetOf<String>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || !node.refresh()) return
            if (node.isVisibleToUser) {
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let(labels::add)
                node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(labels::add)
            }
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        visit(root)
        return "AX window=${root?.windowId} package=${root?.packageName}; visible=${labels.take(80)}"
    }

    private fun event(action: Int, point: Offset, down: Long, type: Int) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = type }),
            arrayOf(MotionEvent.PointerCoords().apply {
                x = point.x; y = point.y
                pressure = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) 0f else .7f
                size = .01f
            }),
            0, 0, 1f, 1f, 0, 0,
            if (type == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Android window rejected mixing-color input", instrumentation.uiAutomation.injectInputEvent(motion, true)) }
        finally { motion.recycle() }
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

    private fun colorLabel(color: Int) = "Mixing color #%06X".format(color and 0xffffff)

    companion object {
        private val DEFAULT_COLORS = listOf(
            0xffe53935.toInt(), 0xffff9800.toInt(), 0xffffeb3b.toInt(),
            0xff43a047.toInt(), 0xff00838f.toInt(), 0xff1e88e5.toInt(),
            0xff8e24aa.toInt(), 0xff111111.toInt(), 0xffffffff.toInt(),
        )
    }
}
