package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.LiquifyMode
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasBrushSwitchTest {
    @Test
    fun everyBrushAndToolSwitchStartsAUsableInputStream() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val strokes = mutableListOf<InkStroke>()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            onStrokeCommitted = strokes::add
        }
        val first = brush("first", "First")
        val second = brush("second", "Second")

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), first, 16f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 180f)

            // Reproduce a palette tap interrupting the previous stream before its UP arrives.
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 420f, 20L))
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), second, 22f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 30L, 520f)

            view.applyBrushConfiguration(EditorTool.HIGHLIGHTER, AppSettings(), null, 28f, .35f, Color.YELLOW, EraserPreset.NORMAL_FREE)
            drawStroke(view, 60L, 760f)
        }

        assertEquals(listOf("first", "second", "system-highlighter"), strokes.map(InkStroke::brushId))
    }

    @Test
    fun eraserModeCannotPaintWithAStaleNormalBrushDescriptor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val committed = mutableListOf<InkStroke>()
        val replacements = mutableListOf<InkStroke>()
        val original = InkStroke(
            id = "target",
            width = 18f,
            points = listOf(app.inkloom.document.StrokePoint(.1f, .5f), app.inkloom.document.StrokePoint(.9f, .5f)),
        )
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            setDocumentStrokes(listOf(original), "eraser-stale-brush")
            onStrokeCommitted = committed::add
            onStrokesReplaced = { _, added, _ -> replacements += added }
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.ERASER, AppSettings(eraserSize = 80f), brush("normal", "Ink pen"), 80f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 600f)
        }

        assertTrue("Eraser mode must never commit black paint", committed.isEmpty())
        assertTrue("The existing stroke should receive an eraser mask", replacements.any { it.id == original.id && it.masks.isNotEmpty() })
    }

    @Test
    fun palmDownBeforeStylusStartsAtThePenAndNeverCreatesTopLeftArtifacts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val committed = mutableListOf<InkStroke>()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush("palm-safe", "Palm safe"), 18f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            onStrokeCommitted = committed::add
        }

        instrumentation.runOnMainSync {
            view.onTouchEvent(fingerEvent(MotionEvent.ACTION_DOWN, 28f, 34f, 1L))
            view.onTouchEvent(palmAndStylusEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 720f, 640f, 2L))
            view.onTouchEvent(palmAndStylusEvent(MotionEvent.ACTION_MOVE, 900f, 700f, 3L))
            view.onTouchEvent(palmAndStylusEvent(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 980f, 740f, 4L))
        }

        assertEquals(1, committed.size)
        assertTrue(committed.single().points.all { it.x > .5f && it.y > .45f })
    }

    @Test
    fun everyFactoryLiquifyPresetMapsToItsExplicitMode() {
        val expected = mapOf(
            "system-liquify-push" to LiquifyMode.PUSH,
            "system-liquify-push-left" to LiquifyMode.PUSH_LEFT,
            "system-liquify-push-right" to LiquifyMode.PUSH_RIGHT,
            "system-liquify-twirl-left" to LiquifyMode.TWIRL_COUNTERCLOCKWISE,
            "system-liquify-twirl-right" to LiquifyMode.TWIRL_CLOCKWISE,
            "system-liquify-pinch" to LiquifyMode.PINCH,
            "system-liquify-expand" to LiquifyMode.EXPAND,
            "system-liquify-crystals" to LiquifyMode.CRYSTALS,
            "system-liquify-edge" to LiquifyMode.EDGE,
            "system-liquify-reconstruct" to LiquifyMode.RECONSTRUCT,
        )
        val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            expected.forEach { (id, mode) ->
                val brush = requireNotNull(SystemBrushCatalog.byId(id))
                view.applyBrushConfiguration(EditorTool.LIQUIFY, AppSettings(), brush, brush.defaultSize, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
                assertEquals(id, mode, view.liquifyMode)
            }
        }
    }

    private fun brush(id: String, name: String) = ImportedBrush(
        id = id,
        name = name,
        format = BrushFormat.IMAGE,
        compatibility = Compatibility.COMPLETE,
        sizeBytes = 0L,
        report = emptyList(),
    )

    private fun drawStroke(view: InkCanvasView, startTime: Long, y: Float) {
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, y, startTime))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 600f, y, startTime + 4))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, y, startTime + 8))
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .75f; size = .05f })
        return MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
    }

    private fun fingerEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; touchMajor = 20f })
        return MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun palmAndStylusEvent(action: Int, stylusX: Float, stylusY: Float, time: Long): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 42; toolType = MotionEvent.TOOL_TYPE_STYLUS },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = 28f; y = 34f; pressure = 1f; touchMajor = 70f },
            MotionEvent.PointerCoords().apply { x = stylusX; y = stylusY; pressure = .7f; touchMajor = 1f },
        )
        return MotionEvent.obtain(1L, time, action, 2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS, 0)
    }
}
