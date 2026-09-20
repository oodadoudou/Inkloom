package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class ColorWindowInputContractTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("color-mixing-board.bin").delete()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    private fun huePoint(ring: android.graphics.Rect, hue: Float): Offset = huePoint(android.graphics.RectF(ring), hue)
    private fun huePoint(ring: android.graphics.RectF, hue: Float): Offset {
        val radians = Math.toRadians((hue + 210f).toDouble())
        return Offset(ring.centerX() + kotlin.math.cos(radians).toFloat() * ring.width() * .43f,
            ring.centerY() + kotlin.math.sin(radians).toFloat() * ring.height() * .43f)
    }
    private fun trianglePoint(field: android.graphics.Rect, saturation: Float, value: Float): Offset = trianglePoint(android.graphics.RectF(field), saturation, value)
    private fun trianglePoint(field: android.graphics.RectF, saturation: Float, value: Float): Offset =
        Offset(field.left + field.width() * saturation * value,
            field.top + field.height() * (1f - value + saturation * value * .5f))

    @Test fun trianglePixelsMatchPickedColorsAndHsvModeSurvivesClosing() {
        tap("New Note"); tap("Create"); val view = canvas(); tap("Colors")
        assertTrue(compose.onAllNodesWithContentDescription("Use HSV square").fetchSemanticsNodes().isNotEmpty())
        tapAt(huePoint(bounds("Hue ring"), 0f)); compose.waitForIdle()
        val field = bounds("Saturation and brightness field")
        for ((s, v) in listOf(.5f to .8f, .7f to .55f, .2f to .7f)) {
            val point = trianglePoint(field, s, v)
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val rendered = screenshot.getPixel(point.x.toInt(), point.y.toInt()); screenshot.recycle()
            tapAt(point); compose.waitForIdle()
            for (shift in listOf(16, 8, 0)) assertTrue("Displayed triangle color must match selected RGB",
                kotlin.math.abs((rendered ushr shift and 255) - (view.inkColor ushr shift and 255)) <= 4)
        }
        val original = view.inkColor
        tap("Use HSV square"); assertEquals(original, view.inkColor)
        val square = bounds("Saturation and brightness field")
        assertTrue(kotlin.math.abs(square.width() - square.height()) <= 1)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); compose.waitForIdle(); tap("Colors")
        assertTrue(compose.onAllNodesWithContentDescription("Use HLS triangle").fetchSemanticsNodes().isNotEmpty())
        assertEquals(original, view.inkColor)
        assertTrue("RGB is the initial channel display", hasText("R"))
        tap("Color channel values"); assertTrue(hasText("H"))
        tap("Color channel values"); assertTrue(hasText("R"))
        tap("Edit secondary color"); compose.waitForIdle(); assertTrue("Subcolor hit target", hasText("Secondary color"))
        val secondaryField = bounds("Saturation and brightness field")
        tapAt(Offset(secondaryField.left + secondaryField.width() * .7f, secondaryField.top + secondaryField.height() * .4f))
        compose.waitForIdle()
        assertEquals("Editing the subcolor does not change the foreground", original, view.inkColor)
        org.junit.Assert.assertNotEquals(android.graphics.Color.WHITE, view.secondaryInkColor)
        tap("Edit primary color"); assertEquals(original, view.inkColor)
    }

    @Test fun cancelledHueDragRestoresPreviewWithoutPersistingAColor() {
        tap("New Note"); tap("Create"); val view = canvas(); tap("Colors")
        tapAt(trianglePoint(bounds("Saturation and brightness field"), .8f, .7f)); compose.waitForIdle()
        val before = view.inkColor
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("inkloom_drawing_colors", Context.MODE_PRIVATE)
        val persisted = preferences.all.toMap()
        val trace = app.inkloom.engine.v5.diagnostics.ColorPickerInteractionTrace
        trace.reset(); trace.enabled = true
        val down = SystemClock.uptimeMillis(); val point = huePoint(bounds("Hue ring"), 180f)
        try {
            event(MotionEvent.ACTION_DOWN, point, down, MotionEvent.TOOL_TYPE_STYLUS); compose.waitForIdle()
            org.junit.Assert.assertNotEquals(before, view.inkColor)
            event(MotionEvent.ACTION_CANCEL, point, down, MotionEvent.TOOL_TYPE_STYLUS); compose.waitForIdle()
            assertEquals(before, view.inkColor)
            assertEquals(persisted, preferences.all)
            assertEquals(0L, trace.snapshot().workspaceCommits)
        } finally { trace.enabled = false; trace.reset() }
    }

    @Test fun numericColorPreviewCanCancelAndCommitsOnceToTheSelectedRole() {
        tap("New Note"); tap("Create"); val view = canvas(); tap("Colors")
        val original = view.inkColor
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("inkloom_drawing_colors", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        val trace = app.inkloom.engine.v5.diagnostics.ColorPickerInteractionTrace
        trace.reset(); trace.enabled = true
        try {
            tap("Edit color values")
            compose.onNodeWithContentDescription("Color value R").performTextReplacement("75")
            compose.waitForIdle()
            compose.waitUntil(5_000) { view.inkColor != original }
            assertEquals(before, prefs.all)
            tap("Cancel")
            compose.waitUntil(5_000) { !hasText("Color values") }
            assertEquals(original, view.inkColor)
            assertTrue(hasText("Primary color"))
            assertEquals(0L, trace.snapshot().workspaceCommits)
            tap("Edit secondary color"); tap("Edit color values")
            compose.onNodeWithContentDescription("Color value R").performTextReplacement("0")
            compose.onNodeWithContentDescription("Color value G").performTextReplacement("0")
            compose.onNodeWithContentDescription("Color value B").performTextReplacement("255")
            compose.waitForIdle()
            assertEquals(original, view.inkColor)
            tap("Done")
            assertEquals(original, view.inkColor)
            assertEquals(android.graphics.Color.BLUE, view.secondaryInkColor)
            assertEquals(1L, trace.snapshot().workspaceCommits)
        } finally { trace.enabled = false; trace.reset() }
    }

    @Test fun numericKeyboardCancelAndDoneRestoreThePaletteGeometry() {
        tap("New Note"); tap("Create"); tap("Colors")
        val ring = bounds("Hue ring")
        for (action in listOf("Cancel", "Done")) {
            tap("Edit color values")
            tap("Color value R") // Open the real IME, not only the semantics text-edit action.
            compose.onNodeWithContentDescription("Color value R").performTextReplacement("75")
            SystemClock.sleep(500)
            tap(action)
            compose.waitUntil(5_000) { !hasText("Color values") }
            compose.waitUntil(5_000) { bounds("Hue ring") == ring }
            tap("Mix"); tap("Wheel")
            assertEquals("Returning from text input must retain the original wheel bounds", ring, bounds("Hue ring"))
        }
    }

    @Test fun everyHueArcCanBePickedAndImmediateFieldGestureUsesTheNewHue() {
        tap("New Note"); tap("Create"); val view = canvas(); tap("Colors")
        for (hue in 0..330 step 30) {
            tapAt(huePoint(bounds("Hue ring"), hue.toFloat()))
            // Deliberately no compose frame between successive contacts.
            tapAt(trianglePoint(bounds("Saturation and brightness field"), .8f, .7f))
            compose.waitForIdle()
            val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(view.inkColor, it) }
            val difference = kotlin.math.abs(hsv[0] - hue)
            assertTrue("Arc $hue returned ${hsv[0]}", minOf(difference, 360 - difference) < 2f)
        }
    }

    @Test fun toolbarSwapWorksWithoutOpeningColorPanelAndKeepsBrushSelection() {
        tap("New Note")
        tap("Create")
        val view = canvas()
        val foreground = view.inkColor
        val background = view.secondaryInkColor
        val brush = view.brushId
        tap("Quick swap foreground and background colors")
        assertEquals(background, view.inkColor)
        assertEquals(foreground, view.secondaryInkColor)
        assertEquals(brush, view.brushId)
        assertTrue("Shortcut must not open the color editor", !hasText("Primary color"))
        tap("Quick swap foreground and background colors")
        assertEquals(foreground, view.inkColor)
        assertEquals(background, view.secondaryInkColor)
    }

    @Test fun firstHueDownFromDefaultGraySurvivesSaturationAndKeepsPencil() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        tap("Colors")
        assertHueSelectionSurvivesNeutralColor(canvas)
    }

    @Test fun firstHueFromBlackIsUsedWhenSaturationAndBrightnessIncrease() {
        verifyNeutralEndpoint(1f)
    }

    @Test fun firstHueFromWhiteIsUsedWhenSaturationIncreases() {
        verifyNeutralEndpoint(0f)
    }

    private fun verifyNeutralEndpoint(verticalFraction: Float) {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        tap("Colors")
        val field = bounds("Saturation and brightness field")
        // Start inside the field, then drag just beyond its edge to exercise the exact clamped endpoint.
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, Offset(field.exactCenterX(), field.exactCenterY()), down, MotionEvent.TOOL_TYPE_FINGER)
        val endpoint = Offset(field.left - 2f, if (verticalFraction == 0f) field.top - 2f else field.bottom + 2f)
        event(MotionEvent.ACTION_MOVE, endpoint, down, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_UP, endpoint, down, MotionEvent.TOOL_TYPE_FINGER)
        compose.waitForIdle()
        assertEquals(if (verticalFraction == 0f) android.graphics.Color.WHITE else android.graphics.Color.BLACK, canvas.inkColor)
        assertHueSelectionSurvivesNeutralColor(canvas)
    }

    private fun assertHueSelectionSurvivesNeutralColor(canvas: InkCanvasView) {
        tap("Color channel values") // Explicitly select HLS when inspecting undefined hue intent.
        val originalColor = canvas.inkColor
        val ring = bounds("Hue ring")
        val contact = huePoint(ring, 270f)
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, contact, time, MotionEvent.TOOL_TYPE_FINGER)
        try {
            compose.waitUntil(2_000) { hasText("270°") }
            assertEquals("Choosing hue must not increase saturation or brightness", originalColor, canvas.inkColor)
        } finally {
            event(MotionEvent.ACTION_UP, contact, time, MotionEvent.TOOL_TYPE_FINGER)
        }
        compose.waitForIdle()
        assertEquals("system-hb-pencil", canvas.brushId)
        val field = bounds("Saturation and brightness field")
        tapAt(trianglePoint(field, .8f, .7f))
        compose.waitForIdle()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(canvas.inkColor, hsv)
        assertEquals("SV must use the hue selected while RGB had no hue", 270f, hsv[0], 1f)
        assertTrue(hsv[1] > .7f)
        assertEquals("system-hb-pencil", canvas.brushId)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        val location = IntArray(2)
        instrumentation.runOnMainSync { canvas.getLocationOnScreen(location) }
        val start = Offset(location[0] + canvas.width * .15f, location[1] + canvas.height * .4f)
        stroke(start, Offset(location[0] + canvas.width * .55f, start.y))
        compose.waitUntil(10_000) { canvas.lastBrushExecutionFingerprint != null }
        assertEquals("The next actual stroke must execute the selected pencil", "system-hb-pencil", canvas.lastBrushExecutionFingerprint?.presetId)
        val snapshot = requireNotNull(canvas.activeRasterSnapshotForTest())
        val pixels = snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
        assertTrue("The selected violet must reach committed pixels", pixels.any { pixel ->
            val alpha = pixel ushr 24 and 255
            alpha > 20 && android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) && android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel)
        })
    }

    @Test fun colorPairSurvivesLeavingTheEditorAndStaysIndependentPerNotebook() {
        val title = "Color recovery ${SystemClock.uptimeMillis()}"
        tap("New Note")
        compose.onNodeWithText("Title").performTextReplacement(title)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        tap("Create")
        tap("Brushes")
        tap("Brush category Pencil")
        tap("Brush preset HB Pencil")
        tap("Close brushes panel")
        tap("Colors")
        val ring = bounds("Hue ring")
        tapAt(huePoint(ring, 270f))
        compose.waitForIdle()
        val field = bounds("Saturation and brightness field")
        tapAt(trianglePoint(field, .8f, .7f))
        compose.waitForIdle()
        val violet = canvas().inkColor
        tap("Swap foreground and background colors")
        assertEquals(android.graphics.Color.WHITE, canvas().inkColor)
        assertEquals(violet, canvas().secondaryInkColor)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        tap("Gallery")
        tap("Notebook $title. Long press to move.")
        val restored = canvas()
        assertEquals("Foreground must survive disposing EditorScreen", android.graphics.Color.WHITE, restored.inkColor)
        assertEquals("Secondary must survive disposing EditorScreen", violet, restored.secondaryInkColor)
        assertEquals("system-hb-pencil", restored.brushId)
        tap("Colors")
        tap("Swap foreground and background colors")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        val location = IntArray(2)
        instrumentation.runOnMainSync { restored.getLocationOnScreen(location) }
        val start = Offset(location[0] + restored.width * .15f, location[1] + restored.height * .4f)
        stroke(start, Offset(location[0] + restored.width * .55f, start.y))
        compose.waitUntil(10_000) { restored.lastBrushExecutionFingerprint != null }
        assertEquals("system-hb-pencil", restored.lastBrushExecutionFingerprint?.presetId)
        val snapshot = requireNotNull(restored.activeRasterSnapshotForTest())
        val pixels = snapshot.copyPremultipliedPixels(app.inkloom.engine.v5.surface.PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight))
        assertTrue("Restored secondary swapped to foreground must reach actual violet pixels", pixels.any {
            (it ushr 24 and 255) > 20 && android.graphics.Color.blue(it) > android.graphics.Color.red(it) && android.graphics.Color.red(it) > android.graphics.Color.green(it)
        })
        tap("Gallery")
        tap("New Note")
        compose.onNodeWithText("Title").performTextReplacement("Other $title")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        tap("Create")
        assertEquals("A new notebook must not inherit another notebook's foreground", 0xff111111.toInt(), canvas().inkColor)
        assertEquals(android.graphics.Color.WHITE, canvas().secondaryInkColor)
        tap("Gallery")
        tap("Notebook $title. Long press to move.")
        assertEquals(violet, canvas().inkColor)
        assertEquals(android.graphics.Color.WHITE, canvas().secondaryInkColor)
    }

    @Test fun saturatedHueDragKeepsWorkspaceWritesAtTheGestureBoundary() {
        tap("New Note")
        tap("Create")
        val canvas = canvas()
        tap("Colors")
        val field = bounds("Saturation and brightness field")
        tapAt(trianglePoint(field, .8f, .7f))
        compose.waitForIdle()
        val brushBefore = canvas.brushId
        val trace = app.inkloom.engine.v5.diagnostics.ColorPickerInteractionTrace
        val context = ApplicationProvider.getApplicationContext<Context>()
        val colorPreferences = context.getSharedPreferences("inkloom_drawing_colors", Context.MODE_PRIVATE)
        val persistedBeforeDrag = colorPreferences.all.toMap()
        var colorWrites = 0
        val colorListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> colorWrites++ }
        colorPreferences.registerOnSharedPreferenceChangeListener(colorListener)
        val sampleMethods = InstrumentationRegistry.getArguments().getString("colorMethodTrace") == "true"
        val allocatedBefore = android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
        val ioBefore = java.io.File("/proc/self/io").readText()
        trace.reset()
        trace.enabled = true
        if (sampleMethods) android.os.Debug.startMethodTracingSampling(
            java.io.File(context.filesDir, "color-drag.trace").absolutePath, 16 * 1024 * 1024, 1000,
        )
        try {
            val ring = bounds("Hue ring")
            val down = SystemClock.uptimeMillis()
            fun point(index: Int): Offset {
                val angle = Math.toRadians((270 + index * 2).toDouble())
                return Offset(ring.exactCenterX() + kotlin.math.cos(angle).toFloat() * ring.width() * .43f,
                    ring.exactCenterY() + kotlin.math.sin(angle).toFloat() * ring.height() * .43f)
            }
            event(MotionEvent.ACTION_DOWN, point(0), down, MotionEvent.TOOL_TYPE_FINGER)
            repeat(30) { index ->
                SystemClock.sleep(16)
                event(MotionEvent.ACTION_MOVE, point(index + 1), down, MotionEvent.TOOL_TYPE_FINGER)
            }
            assertEquals("MOVE must not persist a workspace color", 0L, trace.snapshot().workspaceCommits)
            assertEquals("MOVE must not change persistent color values", persistedBeforeDrag, colorPreferences.all)
            assertEquals(0, colorWrites)
            event(MotionEvent.ACTION_UP, point(30), down, MotionEvent.TOOL_TYPE_FINGER)
            compose.waitForIdle()
            val snapshot = trace.snapshot()
            assertEquals(1L, snapshot.workspaceCommits)
            assertEquals("UP must persist the color once", 1, colorWrites)
            assertTrue("The committed endpoint must differ from the color before the drag", persistedBeforeDrag != colorPreferences.all)
            assertTrue(snapshot.pointerSamples > 1)
            assertEquals(0L, snapshot.hueRingAllocations)
            assertEquals(brushBefore, canvas.brushId)
            val allocatedAfter = android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
            android.util.Log.i("InkloomColorProfile", "snapshot=$snapshot allocatedBytes=${if (allocatedBefore != null && allocatedAfter != null) allocatedAfter - allocatedBefore else null}; ioBefore=$ioBefore; ioAfter=${java.io.File("/proc/self/io").readText()}; methodSampling=$sampleMethods")
        } finally {
            colorPreferences.unregisterOnSharedPreferenceChangeListener(colorListener)
            if (sampleMethods) android.os.Debug.stopMethodTracing()
            trace.enabled = false
            trace.reset()
        }
    }

    @Test fun neutralHueSurvivesSwitchingToNearbyColors() {
        tap("New Note"); tap("Create"); val view = canvas()
        tap("Colors"); tap("Color channel values")
        val ring = bounds("Hue ring")
        tapAt(huePoint(ring, 270f))
        compose.waitForIdle()
        tap("Nearby"); tap("Nearby color 1, 7")
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(view.inkColor, it) }
        assertEquals("Hidden neutral hue must be shared across tabs", 270f, hsv[0], 1f)
        tap("Wheel")
        assertTrue(hasText("270°"))
    }

    @Test fun nearbyColorsKeepReferenceAndRememberTheOpenTab() {
        tap("New Note"); tap("Create"); val view = canvas()
        tap("Colors"); tap("Nearby")
        compose.onNodeWithContentDescription("Nearby color 4, 4").assertIsSelected()
        compose.onNodeWithContentDescription("Nearby color 4, 1").assertIsNotSelected()
        val original = view.inkColor
        tap("Nearby color 1, 7")
        val selected = view.inkColor
        org.junit.Assert.assertNotEquals(original, selected)
        tap("Nearby color 1, 7")
        assertEquals("Picking twice must not shift the grid", selected, view.inkColor)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        tap("Colors")
        assertTrue(hasText("Saturation × brightness (HSV)"))
        tap("Use current color as reference")
        tap("Nearby color 4, 4")
        assertEquals(selected, view.inkColor)
    }

    @Test fun savedSwatchesCanBePickedWithoutChangingTheBrush() {
        tap("New Note"); tap("Create"); val view = canvas()
        val original = view.inkColor; val brush = view.brushId
        tap("Colors"); tap("Palettes"); tap("Save current color")
        tap("Nearby"); tap("Nearby color 1, 7")
        org.junit.Assert.assertNotEquals(original, view.inkColor)
        tap("Palettes"); tap("Color tile 1")
        assertEquals(original, view.inkColor); assertEquals(brush, view.brushId)
        assertEquals(listOf(original), app.inkloom.settings.ColorSetLibraryStore(ApplicationProvider.getApplicationContext()).load().activeSet?.swatches?.map { it.color })
    }

    @Test fun starterPalettesAreOptionalAndKeepColorsBrushAndUserSwatchesUntilPicked() {
        tap("New Note"); tap("Create"); val view = canvas()
        val color = view.inkColor; val secondary = view.secondaryInkColor; val brush = view.brushId
        val store = app.inkloom.settings.ColorSetLibraryStore(ApplicationProvider.getApplicationContext())
        tap("Colors"); tap("Palettes"); tap("Save current color")
        val original = store.load()
        tap("Color set menu"); tap("Add starter color set"); tap("Cancel")
        assertEquals(original, store.load())
        tap("Color set menu"); tap("Add starter color set"); tap("Add Line art")
        assertEquals("Line art", store.load().activeSet!!.name)
        assertEquals(12, store.load().activeSet!!.swatches.size)
        assertEquals(original.sets, store.load().sets.take(original.sets.size))
        assertEquals(color, view.inkColor); assertEquals(secondary, view.secondaryInkColor)
        assertEquals(brush, view.brushId)
        tap("Color tile 5")
        assertEquals(0xff322522.toInt(), view.inkColor); assertEquals(secondary, view.secondaryInkColor)
        tap("Mix"); tap("Palettes")
        val saved = store.load()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        tap("Colors"); tap("Palettes")
        assertEquals(saved, store.load())
        tap("Color set menu"); tap("Add starter color set"); tap("Add Sketch")
        assertEquals("Sketch", store.load().activeSet!!.name)
        tap("Color tile 1")
        assertEquals(0xffc6e1f4.toInt(), view.inkColor)
        assertEquals(brush, view.brushId)
        val ready = store.load()
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(ready, store.load())
    }

    @Test fun mixingBoardSupportsUndoPickAndReopenWithoutChangingNotebookPixels() {
        tap("New Note"); tap("Create"); val canvas = canvas()
        val revision = canvas.activeRasterSnapshotForTest()?.revision
        tap("Colors"); tap("Mix")
        val area = bounds("Color mixing board")
        // Wait for the independent background restore to complete before the first contact.
        SystemClock.sleep(500)
        val start = Offset(area.left + area.width() * .2f, area.exactCenterY())
        val end = Offset(area.left + area.width() * .7f, area.exactCenterY())
        stroke(start, end); compose.waitForIdle()
        tap("Undo mixing"); tap("Redo mixing")
        tap("Pick from mixing board"); tapAt(Offset(area.exactCenterX(), area.exactCenterY()))
        compose.waitForIdle()
        val picked = canvas.inkColor
        assertTrue("Picking the painted line should be dark", android.graphics.Color.red(picked) < 80)
        assertEquals("Mixing must never touch document history", revision, canvas.activeRasterSnapshotForTest()?.revision)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        tap("Colors")
        SystemClock.sleep(500)
        tap("Pick from mixing board")
        val restored = bounds("Color mixing board")
        tapAt(Offset(restored.exactCenterX(), restored.exactCenterY())); compose.waitForIdle()
        assertEquals("Scratchpad must survive closing its popup", picked, canvas.inkColor)
        tap("Clear mixing board"); tap("Undo mixing")
        tapAt(Offset(restored.exactCenterX(), restored.exactCenterY())); compose.waitForIdle()
        assertEquals("Clear can be undone", picked, canvas.inkColor)
    }

    @Test fun mixingHistorySurvivesClosingColorWindowAndActivityRecreation() {
        tap("New Note"); tap("Create")
        val revision = canvas().activeRasterSnapshotForTest()?.revision
        tap("Colors"); tap("Mix")
        SystemClock.sleep(500)
        val area = bounds("Color mixing board")
        stroke(Offset(area.left + area.width() * .2f, area.exactCenterY()),
            Offset(area.exactCenterX(), area.exactCenterY()))
        tap("Blend on mixing board")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Color mixing board").fetchSemanticsNodes().isEmpty()
        }
        tap("Colors")
        compose.onNodeWithContentDescription("Blend on mixing board").assertIsSelected()
        tap("Undo mixing")
        tap("Pick from mixing board")
        val point = bounds("Color mixing board").let { Offset(it.exactCenterX(), it.exactCenterY()) }
        tapAt(point); compose.waitForIdle()
        assertEquals("Closing a palette must not discard the preceding mixing stroke's undo", 0xffeeeeea.toInt(), canvas().inkColor)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        tap("Colors")
        tap("Redo mixing"); tap("Pick from mixing board")
        val restored = bounds("Color mixing board")
        tapAt(Offset(restored.exactCenterX(), restored.exactCenterY())); compose.waitForIdle()
        assertTrue("Redo must survive activity recreation", android.graphics.Color.red(canvas().inkColor) < 80)
        assertEquals(revision, canvas().activeRasterSnapshotForTest()?.revision)
    }

    @Test fun mixingWheelRoundTripKeepsPanelTargetsAndUndoSession() {
        tap("New Note"); tap("Create"); val canvas = canvas()
        val revision = canvas.activeRasterSnapshotForTest()?.revision
        tap("Colors"); tap("Mix")
        SystemClock.sleep(500)
        val mixTab = center("Mix")
        val area = bounds("Color mixing board")
        val point = Offset(area.exactCenterX(), area.exactCenterY())
        stroke(Offset(area.left + area.width() * .2f, point.y), point)
        val control = bounds("Mixing brush size")
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        try {
            assertEquals("The native mixing background must not paint over the color bar or command icons",
                screenshot.getPixel(area.right - 8, area.top - 10),
                screenshot.getPixel(control.left + 1, control.top + 1))
        } finally { screenshot.recycle() }
        tap("Blend on mixing board")
        repeat(3) {
            tap("Wheel")
            val ring = bounds("Hue ring")
            tapAt(huePoint(ring, 270f))
            val field = bounds("Saturation and brightness field")
            tapAt(trianglePoint(field, .8f, .7f))
            compose.waitForIdle()
            assertTrue("Picking a new saturated color must reach the active drawing color: ${canvas.inkColor.toUInt().toString(16)}",
                android.graphics.Color.blue(canvas.inkColor) > android.graphics.Color.red(canvas.inkColor) &&
                    android.graphics.Color.red(canvas.inkColor) > android.graphics.Color.green(canvas.inkColor))
            assertEquals("Tab must not move when the color interface changes", mixTab, center("Mix"))
            // Reuse the physical pen target, rather than searching for a relocated tab.
            tapAt(mixTab); compose.waitForIdle()
            assertTrue("Switching and picking colors must keep the panel open", hasText("Primary color"))
            compose.onNodeWithContentDescription("Blend on mixing board").assertIsSelected()
        }
        tap("Undo mixing")
        tap("Pick from mixing board"); tapAt(point); compose.waitForIdle()
        assertEquals("Undo must survive leaving the mixing tab", 0xffeeeeea.toInt(), canvas.inkColor)
        tap("Redo mixing"); tapAt(point); compose.waitForIdle()
        assertTrue(android.graphics.Color.red(canvas.inkColor) < 80)
        assertEquals(revision, canvas.activeRasterSnapshotForTest()?.revision)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Color mixing board").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun mixingPinchAndBackgroundChangesDoNotAlterPaintHistory() {
        tap("New Note"); tap("Create"); val view = canvas()
        tap("Colors"); tap("Mix"); SystemClock.sleep(500)
        val area = bounds("Color mixing board")
        val middle = Offset(area.exactCenterX(), area.exactCenterY())
        stroke(middle - Offset(100f, 0f), middle + Offset(100f, 0f))
        val now = SystemClock.uptimeMillis()
        fun fingers(action: Int, span: Float, count: Int = 2) {
            val properties = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(count) { i -> MotionEvent.PointerCoords().apply { x = middle.x + if (i == 0) -span else span; y = middle.y; pressure = .5f; size = .05f } }
            val motion = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, count, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
        }
        fingers(MotionEvent.ACTION_DOWN, 40f, 1)
        fingers(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 40f)
        repeat(12) { SystemClock.sleep(10); fingers(MotionEvent.ACTION_MOVE, 40f + it * 8f) }
        fingers(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 128f)
        fingers(MotionEvent.ACTION_UP, 128f, 1)
        tap("Mixing palette menu"); tap("Reset view")
        tap("Mixing palette menu"); tap("Black")
        tap("Pick from mixing board"); tapAt(middle); compose.waitForIdle()
        val painted = view.inkColor
        tap("Undo mixing"); tapAt(middle); compose.waitForIdle()
        assertEquals("Pinching must not add a paint transaction", android.graphics.Color.BLACK, view.inkColor)
        tap("Redo mixing"); tapAt(middle); compose.waitForIdle()
        assertEquals("Background changes must not tint pigment", painted, view.inkColor)
    }

    @Test fun mixingPickerMatchesDisplayedTranslucentColor() {
        tap("New Note"); tap("Create"); val view = canvas()
        tap("Colors"); tap("Mix"); SystemClock.sleep(500)
        tap("Mixing palette menu"); tap("White")
        val area = bounds("Color mixing board")
        val point = Offset(area.exactCenterX(), area.exactCenterY())
        tapAt(point); compose.waitForIdle()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val displayed = screenshot.getPixel(point.x.toInt(), point.y.toInt())
        screenshot.recycle()
        tap("Pick from mixing board"); tapAt(point); compose.waitForIdle()
        for (shift in listOf(0, 8, 16)) assertTrue("Picker must sample the displayed pigment/background composite", kotlin.math.abs((displayed ushr shift and 255) - (view.inkColor ushr shift and 255)) <= 2)
    }

    @Test fun detachedMixAndWheelShareColorsWithoutLosingTheMixingSession() {
        tap("New Note"); tap("Create"); val canvas=canvas()
        tap("Colors"); tap("Mix"); SystemClock.sleep(500)
        val before=bounds("Color mixing board")
        stroke(Offset(before.left+before.width()*.2f,before.exactCenterY()),Offset(before.exactCenterX(),before.exactCenterY()))
        val grip=center("Drag mix panel")
        val close=bounds("Close mix panel")
        val end=Offset(close.right-3f,close.bottom+80f)
        val down=SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN,grip,down,MotionEvent.TOOL_TYPE_FINGER)
        SystemClock.sleep(500)
        for(i in 1..30) { event(MotionEvent.ACTION_MOVE,grip+(end-grip)*(i/30f),down,MotionEvent.TOOL_TYPE_FINGER); SystemClock.sleep(16) }
        event(MotionEvent.ACTION_UP,end,down,MotionEvent.TOOL_TYPE_FINGER);compose.waitForIdle()
        compose.onNodeWithContentDescription("Hue ring").assertExists()
        compose.onNodeWithContentDescription("Color mixing board").assertExists()
        // Accessibility bounds may be clipped by the adjacent floating panel. Target the
        // real rendered circle/triangle geometry, preserving sub-pixel screen coordinates.
        fun fullBounds(label: String): android.graphics.RectF {
            val node = compose.onNodeWithContentDescription(label).fetchSemanticsNode()
            val p = node.positionOnScreen
            return android.graphics.RectF(p.x, p.y, p.x + node.size.width, p.y + node.size.height)
        }
        val ring=fullBounds("Hue ring")
        tapAt(huePoint(ring,270f));compose.waitForIdle()
        tapAt(trianglePoint(fullBounds("Saturation and brightness field"),.8f,.7f));compose.waitForIdle()
        val hsv=FloatArray(3);android.graphics.Color.colorToHSV(canvas.inkColor,hsv)
        assertEquals(270f,hsv[0],1f)
        tap("Undo mixing");tap("Redo mixing");tap("Pick from mixing board")
        val area=bounds("Color mixing board")
        tapAt(Offset(area.exactCenterX(),area.exactCenterY()));compose.waitForIdle()
        assertTrue("Reparenting the native mixing view retains its pigment",android.graphics.Color.red(canvas.inkColor)<80)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/inkloom-detached-colors.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
    }

    private fun hasText(text: String): Boolean {
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            node.refresh()
            if (node.text?.toString() == text) return true
            return (0 until node.childCount).any { visit(node.getChild(it)) }
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun bounds(label: String): android.graphics.Rect {
        var result: android.graphics.Rect? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                // Movable panel contents can reparent between snapshots; discard cached nodes.
                if (node == null || !node.refresh()) return
                if (node.contentDescription?.toString() == label) {
                    result = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                } else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            result != null
        }
        return requireNotNull(result)
    }

    private fun tap(node: String) {
        tapAt(center(node))
        compose.waitForIdle()
    }

    private fun center(label: String): Offset {
        var result: Offset? = null
        try { compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                // Movable panel contents can reparent between snapshots; discard cached nodes.
                if (node == null || !node.refresh()) return
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    result = Offset(rect.exactCenterX(), rect.exactCenterY())
                } else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            result != null
        }
        } catch (failure: Throwable) {
            instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/color-window-failure.png").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
            throw AssertionError("Missing window target: $label", failure)
        }
        return requireNotNull(result)
    }

    private fun tapAt(point: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_FINGER)
    }

    private fun stroke(start: Offset, end: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, time, MotionEvent.TOOL_TYPE_STYLUS)
        repeat(24) { index ->
            SystemClock.sleep(8)
            val fraction = (index + 1) / 24f
            event(MotionEvent.ACTION_MOVE, start + (end - start) * fraction, time, MotionEvent.TOOL_TYPE_STYLUS)
        }
        event(MotionEvent.ACTION_UP, end, time, MotionEvent.TOOL_TYPE_STYLUS)
    }

    private fun event(action: Int, point: Offset, down: Long, type: Int) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = type }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0,
            if (type == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Window rejected input", instrumentation.uiAutomation.injectInputEvent(motion, true)) }
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
}
