package app.inkloom

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performImeAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.ColorSetLibrary
import app.inkloom.settings.ColorSetLibraryStore
import app.inkloom.settings.DrawingColorStore
import app.inkloom.document.FileDocumentStore
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Color set writes and the reorder gesture use the real window; the store is read-only evidence. */
class ColorSetWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().clear().commit()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun recentColorTileRestoresTheActiveChannelAndClearKeepsCurrentColor() {
        val title = "Color history ${SystemClock.uptimeMillis()}"
        openNewNotebook(title)
        val documentId = FileDocumentStore(context.filesDir).list().first { it.title == title }.id
        val colors = DrawingColorStore(context)
        tap("Colors")
        tap("Palettes")
        fun choose(hex: String) {
            tap("Edit current color")
            compose.onNodeWithContentDescription("Hex color").performTextReplacement(hex)
            // Typing opens the keyboard and the imePadding dialog slides; a click injected while it
            // moves can miss Apply. Close the keyboard like a user and tap the settled button.
            compose.onNodeWithContentDescription("Hex color").performImeAction()
            settledTap("Apply color")
            compose.waitUntil(5_000) { findBounds("Choose color") == null }
        }
        choose("#314159")
        choose("#A04080")
        compose.waitUntil(5_000) { colors.loadHistory(documentId).size == 2 }
        tap("Recent color 2")
        assertEquals(0xff314159.toInt(), colors.load(documentId).foreground)
        tap("Wheel")
        tap("Edit secondary color")
        tap("Recent color 2")
        assertEquals(0xffa04080.toInt(), colors.load(documentId).background)
        val beforeClear = colors.load(documentId)
        tap("Clear color history")
        assertEquals(emptyList<Int>(), colors.loadHistory(documentId))
        assertEquals(beforeClear, colors.load(documentId))
    }

    @Test fun numericAlphaCommitsOnlyToTheSelectedDrawingColor() {
        val title = "Color alpha ${SystemClock.uptimeMillis()}"
        openNewNotebook(title)
        val documentId = FileDocumentStore(context.filesDir).list().first { it.title == title }.id
        val colors = DrawingColorStore(context)
        tap("Colors")
        val before = colors.load(documentId)
        tap("Edit color values")
        compose.onNodeWithContentDescription("Color value A").performTextReplacement("128")
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(5_000) { colors.load(documentId).foreground ushr 24 == 128 }
        assertEquals(before.foreground and 0x00ffffff, colors.load(documentId).foreground and 0x00ffffff)
        assertEquals(before.background, colors.load(documentId).background)

        tap("Edit secondary color")
        tap("Edit color values")
        compose.onNodeWithContentDescription("Color value A").performTextReplacement("64")
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(5_000) { colors.load(documentId).background ushr 24 == 64 }
        assertEquals(128, colors.load(documentId).foreground ushr 24)
    }

    @Test fun paletteAndNearbyCurrentColorSquaresOpenPrecisePickerForTheActiveColor() {
        val title = "Quick color ${SystemClock.uptimeMillis()}"
        openNewNotebook(title)
        val documentId = FileDocumentStore(context.filesDir).list().first { it.title == title }.id
        val colors = DrawingColorStore(context)
        val startingBackground = colors.load(documentId).background
        tap("Colors")
        tap("Palettes")
        tap("Edit current color")
        bounds("Choose color")
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/inkloom-quick-color-picker.png").close()
        compose.onNodeWithContentDescription("Hex color").performTextReplacement("#314159")
        compose.onNodeWithText("Apply").performClick()
        try { compose.waitUntil(5_000) { colors.load(documentId).foreground == 0xff314159.toInt() } }
        catch (failure: Throwable) { throw AssertionError("picker wrote foreground=${colors.load(documentId).foreground.toUInt().toString(16)} background=${colors.load(documentId).background.toUInt().toString(16)}; ${visibleLabels()}", failure) }
        assertEquals(startingBackground, colors.load(documentId).background)

        tap("Wheel")
        tap("Edit secondary color")
        tap("Nearby")
        compose.onNodeWithContentDescription("Edit current color").performTouchInput { longClick() }
        bounds("Choose color")
        compose.onNodeWithContentDescription("Hex color").performTextReplacement("#A04080")
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(5_000) { colors.load(documentId).background == 0xffa04080.toInt() }
        assertEquals(0xff314159.toInt(), colors.load(documentId).foreground)

        compose.onNodeWithContentDescription("Nearby color 4, 4").performTouchInput { longClick() }
        bounds("Choose color")
        tap("Cancel")
        assertEquals(0xffa04080.toInt(), colors.load(documentId).background)
        tap("Mix")
        tap("Edit current color")
        bounds("Choose color")
        tap("Cancel")
    }

    @Test fun importingFileOrImageOffersAppendReplaceAndCancelBeforeOpeningPicker() {
        openNewNotebook("Palette import choice ${SystemClock.uptimeMillis()}")
        tap("Colors")
        tap("Palettes")
        tap("Color set menu")
        tap("Import color set from file")
        bounds("Append colors")
        bounds("Replace colors")
        tap("Cancel")
        tap("Color set menu")
        tap("New color set from image")
        bounds("Append colors")
        bounds("Replace colors")
        tap("Cancel")
    }

    @Test fun heldSavedColorOpensPrecisePickerAndKeepsItsTileIdentity() {
        val title = "Edit saved swatch ${SystemClock.uptimeMillis()}"
        openNewNotebook(title)
        tap("Colors")
        tap("Palettes")
        tap("Save current color")
        val original = requireNotNull(library().activeSet).swatches.single()
        compose.onNodeWithContentDescription("Color tile 1").performTouchInput { longClick() }
        bounds("Choose color")
        compose.onNodeWithContentDescription("Hex color").performTextReplacement("#537A91")
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(5_000) { library().activeSet?.swatches?.singleOrNull()?.color == 0xff537a91.toInt() }
        assertEquals(original.id, library().activeSet?.swatches?.single()?.id)
    }

    @Test fun namedSetsPreserveDuplicateTilesAndRealLongPressOrderAfterReopening() {
        val suffix = SystemClock.uptimeMillis()
        val notebookName = "Color set recovery $suffix"
        openNewNotebook(notebookName)
        tap("Colors")
        tap("Palettes")
        createSet("Study $suffix")
        val firstSetId = requireNotNull(library().activeSetId)

        tap("Save current color")
        val originalTile = requireNotNull(library().activeSet).swatches.single()
        tap("Save current color")
        val beforeDrag = requireNotNull(library().activeSet)
        assertEquals("Adding the same color must retain two separate tiles", 2, beforeDrag.swatches.size)
        assertEquals(originalTile.color, beforeDrag.swatches[0].color)
        assertEquals(originalTile.color, beforeDrag.swatches[1].color)
        assertEquals(2, beforeDrag.swatches.map { it.id }.distinct().size)
        assertTrue(beforeDrag.swatches.any { it.id == originalTile.id })
        val draggedId = beforeDrag.swatches.first().id
        val expectedOrder = beforeDrag.swatches.map { it.id }.reversed()

        longPressDrag("Color tile 1", "Color tile 2")
        compose.waitUntil(10_000) { library().activeSet?.swatches?.map { it.id } == expectedOrder }
        val reordered = requireNotNull(library().activeSet)
        assertEquals("Reordering equal colors must operate on tile IDs", expectedOrder, reordered.swatches.map { it.id })
        assertEquals("The moved tile remains selected", draggedId, reordered.selectedSwatchId)

        val renamed = "Illustration $suffix"
        tap("Color set menu")
        tap("Rename color set")
        fillNameAndSave(renamed)
        assertEquals(firstSetId, library().activeSetId)
        assertEquals(renamed, library().activeSet?.name)
        assertEquals(expectedOrder, library().activeSet?.swatches?.map { it.id })

        createSet("Reference $suffix")
        val secondSetId = requireNotNull(library().activeSetId)
        assertNotEquals("Two named sets need independent identity", firstSetId, secondSetId)
        assertTrue(requireNotNull(library().activeSet).swatches.isEmpty())
        tap("Save current color")
        assertEquals(1, library().activeSet?.swatches?.size)
        assertEquals(expectedOrder, library().sets.first { it.id == firstSetId }.swatches.map { it.id })
        tap("Choose color set")
        tap(renamed)
        assertEquals(firstSetId, library().activeSetId)

        closeColorPanel()
        tap("Gallery")
        // Recreate the Activity to discard its palette composition and retained in-memory draft.
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        tap("Notebook $notebookName. Long press to move.")
        tap("Colors")
        tap("Palettes")
        val restored = library()
        assertEquals(firstSetId, restored.activeSetId)
        assertEquals(renamed, restored.activeSet?.name)
        assertEquals(expectedOrder, restored.activeSet?.swatches?.map { it.id })
        assertEquals(draggedId, restored.activeSet?.selectedSwatchId)
        assertEquals(1, restored.sets.first { it.id == secondSetId }.swatches.size)
        bounds("Color tile 1")
        bounds("Color tile 2")
        tap("Choose color set")
        tap("Reference $suffix")
        assertEquals(secondSetId, library().activeSetId)
        assertEquals(1, library().activeSet?.swatches?.size)
    }

    @Test fun replaceAndDeleteASelectedDuplicateAffectOnlyItsIdentity() {
        val suffix = SystemClock.uptimeMillis()
        openNewNotebook("Color tile editing $suffix")
        tap("Colors")
        tap("Palettes")
        createSet("Duplicates $suffix")
        repeat(2) { tap("Save current color") }
        val before = requireNotNull(library().activeSet)
        val selectedId = before.swatches.first().id
        val otherId = before.swatches.last().id
        val initialColor = before.swatches.last().color
        tap("Color tile 1")
        assertEquals(selectedId, library().activeSet?.selectedSwatchId)

        tap("Wheel")
        val field = bounds("Saturation and brightness field")
        tapAt(ColorWheelTestCoordinates.triangle(field, .8f, .7f))
        compose.waitForIdle()
        tap("Palettes")
        tap("Replace selected color")
        val replaced = requireNotNull(library().activeSet)
        assertEquals(before.swatches.map { it.id }, replaced.swatches.map { it.id })
        assertNotEquals("Replacement must consume the current color", initialColor, replaced.selectedSwatch?.color)
        assertEquals("The equal-colored peer must remain untouched", initialColor, replaced.swatches.first { it.id == otherId }.color)

        tap("Delete selected color")
        val afterDelete = requireNotNull(library().activeSet)
        assertEquals(listOf(otherId), afterDelete.swatches.map { it.id })
        assertEquals(initialColor, afterDelete.swatches.single().color)
        assertEquals(otherId, afterDelete.selectedSwatchId)
        closeColorPanel()
        tap("Colors")
        tap("Palettes")
        assertEquals(afterDelete, library().activeSet)
        bounds("Color tile 1")
    }

    @Test fun paletteFileImportIsReachableFromTheColorSetMenu() {
        openNewNotebook("Palette import ${SystemClock.uptimeMillis()}")
        tap("Colors")
        tap("Palettes")
        tap("Color set menu")
        bounds("Import color set from file")
        bounds("New color set from image")
    }

    @Test fun harmonyAndIntermediateColorToolsAreReachable() {
        openNewNotebook("Color relations ${SystemClock.uptimeMillis()}")
        tap("Colors")
        tap("Nearby")
        tap("Harmony")
        bounds("Harmony mode")
        bounds("Harmony color 1")
        tap("Intermediate")
        bounds("Intermediate corner 1")
        bounds("Intermediate color 4, 4")
    }

    @Test fun colorSwapSitsBesideSwatchesAndUnusedSixDotHandleIsAbsent() {
        val title = "Wheel swap ${SystemClock.uptimeMillis()}"
        openNewNotebook(title)
        val documentId = FileDocumentStore(context.filesDir).list().first { it.title == title }.id
        tap("Colors")
        val pair = bounds("Edit secondary color")
        val swap = bounds("Swap foreground and background colors")
        assertTrue("Swap must be next to the two color squares", swap.left in pair.right - 2..pair.right + 80)
        assertTrue("The hosted wheel has no independent width drag; its dead six-dot handle must disappear",
            findBounds("Resize color picker") == null)
        val before = DrawingColorStore(context).load(documentId)
        tap("Swap foreground and background colors")
        val after = DrawingColorStore(context).load(documentId)
        assertEquals(before.foreground, after.background)
        assertEquals(before.background, after.foreground)
    }

    @Test fun wheelNearbyMixAndPaletteWriteTheCurrentlySelectedDrawingColor() {
        val title = "Active color ${SystemClock.uptimeMillis()}"
        openNewNotebook(title)
        val documentId = FileDocumentStore(context.filesDir).list().first { it.title == title }.id
        val colors = DrawingColorStore(context)
        val original = colors.load(documentId)
        tap("Colors")
        val field = bounds("Saturation and brightness field")
        tapAt(ColorWheelTestCoordinates.triangle(field, .8f, .7f))
        compose.waitUntil(5_000) { colors.load(documentId).foreground != original.foreground }
        assertEquals(original.background, colors.load(documentId).background)
        val wheelForeground = colors.load(documentId).foreground

        tap("Edit secondary color")
        bounds("Secondary color")
        tap("Nearby")
        bounds("Secondary color")
        tap("Nearby color 4, 7")
        assertTrue("Nearby selection originalForeground=${wheelForeground.toUInt().toString(16)} foreground=${colors.load(documentId).foreground.toUInt().toString(16)} background=${colors.load(documentId).background.toUInt().toString(16)}",
            colors.load(documentId).background != original.background)
        val secondary = colors.load(documentId).background
        val primary = colors.load(documentId).foreground
        tap("Wheel")
        tap("Edit primary color")
        tap("Mix")
        tap("Mixing color #E53935")
        compose.waitUntil(5_000) { colors.load(documentId).foreground == 0xffe53935.toInt() }
        assertEquals(secondary, colors.load(documentId).background)
        tap("Palettes")
        createSet("Color routing $title")
        tap("Save current color")
        tap("Wheel")
        tap("Edit secondary color")
        tap("Palettes")
        tap("Color tile 1")
        compose.waitUntil(5_000) { colors.load(documentId).background == 0xffe53935.toInt() }
        assertEquals(0xffe53935.toInt(), colors.load(documentId).foreground)
        assertNotEquals(primary, colors.load(documentId).foreground)

        tap("Eyedropper")
        val canvas = requireNotNull(findCanvas(compose.activity.window.decorView))
        val location = IntArray(2).also(canvas::getLocationOnScreen)
        val samplePoint = Offset(location[0] + canvas.width / 2f, location[1] + canvas.height / 2f)
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, samplePoint, down, MotionEvent.TOOL_TYPE_STYLUS)
        event(MotionEvent.ACTION_UP, samplePoint, down, MotionEvent.TOOL_TYPE_STYLUS)
        assertTrue("Eyedropper result ${colors.load(documentId).background.toUInt().toString(16)} tool=${canvas.tool}",
            colors.load(documentId).background != 0xffe53935.toInt())
        assertEquals(0xffe53935.toInt(), colors.load(documentId).foreground)
    }

    private fun findCanvas(root: android.view.View): InkCanvasView? {
        if (root is InkCanvasView) return root
        if (root is android.view.ViewGroup) repeat(root.childCount) { index ->
            findCanvas(root.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun library(): ColorSetLibrary = ColorSetLibraryStore(context).load()

    private fun openNewNotebook(name: String) {
        tap("New Note")
        compose.onNodeWithText("Title").performTextReplacement(name)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        tap("Create")
    }

    private fun createSet(name: String) {
        val paletteLeft = bounds("Choose color set").left
        tap("Color set menu")
        assertTrue("The set menu must use its popup's screen origin", bounds("New color set").left >= paletteLeft - 16)
        tap("New color set")
        fillNameAndSave(name)
        compose.waitUntil(5_000) { library().activeSet?.name == name }
    }

    private fun fillNameAndSave(name: String) {
        // "Name" also labels the dialog title. Focus the actual native editable node.
        val input = bounds("Name", editableOnly = true)
        tapAt(Offset(input.exactCenterX(), input.exactCenterY()))
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextReplacement(name)
        compose.waitForIdle()
        // Submit the visible dialog action while the keyboard is open, as a real user would.
        tap("Save")
        compose.mainClock.advanceTimeBy(500L)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        clearAccessibilityCache()
    }

    private fun closeColorPanel() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        try { compose.waitUntil(5_000) { findBounds("Choose color set") == null } }
        catch (failure: Throwable) { captureFailureScreenshot(); throw AssertionError(visibleLabels(), failure) }
    }

    private fun longPressDrag(source: String, destination: String) {
        val fromBounds = bounds(source)
        val toBounds = bounds(destination)
        val from = Offset(fromBounds.exactCenterX(), fromBounds.exactCenterY())
        val to = Offset(toBounds.exactCenterX(), toBounds.exactCenterY())
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, from, down, MotionEvent.TOOL_TYPE_FINGER)
        try {
            instrumentation.waitForIdleSync()
            val holdDuration = ViewConfiguration.getLongPressTimeout().toLong() + 150L
            SystemClock.sleep(holdDuration)
            compose.mainClock.advanceTimeBy(holdDuration)
            repeat(18) { step ->
                SystemClock.sleep(16)
                val fraction = (step + 1) / 18f
                event(MotionEvent.ACTION_MOVE, from + (to - from) * fraction, down, MotionEvent.TOOL_TYPE_FINGER)
            }
            SystemClock.sleep(80)
            event(MotionEvent.ACTION_UP, to, down, MotionEvent.TOOL_TYPE_FINGER)
        } catch (failure: Throwable) {
            event(MotionEvent.ACTION_CANCEL, to, down, MotionEvent.TOOL_TYPE_FINGER)
            throw failure
        }
        compose.waitForIdle()
    }

    /** The colour chips open only the Color Wheel; the other colour palettes open from Actions › Views › Views…. */
    private fun showColorView(name: String) {
        if (compose.onAllNodesWithContentDescription("Drag ${name.lowercase()} panel").fetchSemanticsNodes().isNotEmpty()) return
        tap("Actions"); tap("Actions Views")
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show $name view").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        // Tabs share the title row (no scrolling); the colour palettes other than the wheel open from Views.
        if (label in setOf("Palettes", "Mix", "Nearby")) showColorView(label)
        val rectangle = bounds(label)
        tapAt(Offset(rectangle.exactCenterX(), rectangle.exactCenterY()))
        compose.waitForIdle()
    }

    private fun tapAt(point: Offset) {
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, down, MotionEvent.TOOL_TYPE_FINGER)
        SystemClock.sleep(8)
        event(MotionEvent.ACTION_UP, point, down, MotionEvent.TOOL_TYPE_FINGER)
    }

    private fun bounds(label: String, editableOnly: Boolean = false): Rect {
        var result: Rect? = null
        try {
            compose.waitUntil(5_000) {
                result = findBounds(label, editableOnly)
                result != null
            }
        } catch (timeout: ComposeTimeoutException) {
            captureFailureScreenshot()
            throw AssertionError("Missing visible color-set target: $label (editable=$editableOnly). ${visibleLabels()}", timeout)
        }
        return requireNotNull(result) { "Missing visible color-set target: $label" }
    }

    /** Taps [label] once the keyboard is gone and its bounds are unchanged across two reads. */
    private fun settledTap(label: String) {
        var settled: Rect? = null
        compose.waitUntil(5_000) {
            val keyboard = instrumentation.uiAutomation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            val first = findBounds(label); SystemClock.sleep(120); val second = findBounds(label)
            (!keyboard && first != null && first == second).also { if (it) settled = second }
        }
        val target = requireNotNull(settled)
        tapAt(Offset(target.exactCenterX(), target.exactCenterY())); compose.waitForIdle()
    }

    private fun findBounds(label: String, editableOnly: Boolean = false): Rect? {
        clearAccessibilityCache()
        fun visit(node: AccessibilityNodeInfo?): Rect? {
            if (node == null) return null
            if (!node.refresh()) return null
            val matches = if (editableOnly) node.isEditable else node.text?.toString() == label || node.contentDescription?.toString() == label
            if (node.isVisibleToUser && matches) {
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
                val rectangle = Rect().also(node::getBoundsInScreen)
                labels.add("class=${node.className} text=${node.text} desc=${node.contentDescription} bounds=$rectangle")
            }
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val rootBounds = Rect().also { root?.getBoundsInScreen(it) }
        visit(root)
        val diagnostic = "AX window=${root?.windowId} package=${root?.packageName} rootBounds=$rootBounds; visible=${labels.take(128)}"
        android.util.Log.e("InkloomColorSetWindow", diagnostic)
        return diagnostic
    }

    private fun captureFailureScreenshot() {
        runCatching {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/inkloom188-color-set-failure.png",
            )
            // Reading to EOF waits for screencap to finish before ActivityScenario tears down.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            android.util.Log.e("InkloomColorSetWindow", "Screenshot: /sdcard/Download/inkloom188-color-set-failure.png")
        }.onFailure { android.util.Log.e("InkloomColorSetWindow", "Could not capture failure screenshot", it) }
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
        try { assertTrue("Android window rejected color-set input", instrumentation.uiAutomation.injectInputEvent(motion, true)) }
        finally { motion.recycle() }
    }
}
