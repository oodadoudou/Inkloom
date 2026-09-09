package app.inkloom

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.BrushOutput
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.model.SystemBrushCatalog
import app.inkloom.settings.AppSettings
import app.inkloom.ui.BrushDrawingPadView
import org.junit.Assert.*
import org.junit.Test

/** Actual stylus input crosses the Android window dispatcher into the production studio pad. */
class BrushDrawingPadTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun previewColorRecolorsExistingMarksWithoutChangingCoverageOrThePreset() {
        withPad(BrushOutput.PAINT) { pad, preset ->
            stroke(pad)
            waitUntil { pad.retainedStrokeCount == 1 && pixels(pad).any { it != 0 } }
            val original = pixels(pad)
            val version = pad.publishedReplayGeneration
            instrumentation.runOnMainSync { pad.configure(preset, AppSettings(), android.graphics.Color.RED) }
            waitUntil { pad.publishedReplayGeneration > version }
            val red = pixels(pad)
            assertArrayEquals(original.map { it ushr 24 }.toIntArray(), red.map { it ushr 24 }.toIntArray())
            assertTrue(red.any { android.graphics.Color.red(it) > 0 && android.graphics.Color.alpha(it) > 0 })
            assertTrue(red.all { android.graphics.Color.green(it) == 0 && android.graphics.Color.blue(it) == 0 })
        }
    }

    @Test fun changingOpacityReplaysExistingInputAndRestoringSettingsRestoresExactPixels() {
        withPad(BrushOutput.PAINT) { pad, preset ->
            stroke(pad)
            waitUntil { pad.retainedStrokeCount == 1 && pixels(pad).any { it != 0 } }
            val original = pixels(pad)
            val before = pad.publishedReplayGeneration
            instrumentation.runOnMainSync {
                pad.configure(preset.copy(deposition = preset.deposition.copy(opacity = 0f)), AppSettings())
            }
            waitUntil { pad.publishedReplayGeneration > before && pixels(pad).all { it == 0 } }
            val emptyVersion = pad.publishedReplayGeneration
            instrumentation.runOnMainSync { pad.configure(preset, AppSettings()) }
            waitUntil { pad.publishedReplayGeneration > emptyVersion }
            assertArrayEquals("Replaying preserved pressure, coordinates and seed", original, pixels(pad))
            assertEquals(1, pad.retainedStrokeCount)
        }
    }

    @Test fun eraserPadStartsWithPigmentAndReplaysErasingAgainstThatSameBackdrop() {
        withPad(BrushOutput.ERASE) { pad, preset ->
            val seed = pixels(pad)
            assertTrue("Eraser must have a visible test substrate", seed.all { it != 0 })
            stroke(pad)
            waitUntil { pad.retainedStrokeCount == 1 && !pixels(pad).contentEquals(seed) }
            val erased = pixels(pad)
            val version = pad.publishedReplayGeneration
            instrumentation.runOnMainSync {
                pad.configure(preset.copy(deposition = preset.deposition.copy(opacity = 0f)), AppSettings())
            }
            waitUntil { pad.publishedReplayGeneration > version }
            assertArrayEquals(seed, pixels(pad))
            val restored = pad.publishedReplayGeneration
            instrumentation.runOnMainSync { pad.configure(preset, AppSettings()) }
            waitUntil { pad.publishedReplayGeneration > restored }
            assertArrayEquals(erased, pixels(pad))
        }
    }

    @Test fun latestRapidSettingWinsWithoutDroppingTheNextPhysicalStroke() {
        withPad(BrushOutput.PAINT) { pad, preset ->
            stroke(pad)
            waitUntil { pad.retainedStrokeCount == 1 && pixels(pad).any { it != 0 } }
            instrumentation.runOnMainSync {
                repeat(12) { index -> pad.configure(preset.copy(deposition = preset.deposition.copy(
                    opacity = if (index % 2 == 0) 0f else .5f)), AppSettings()) }
                pad.configure(preset, AppSettings())
            }
            // New contact arrives while the coalesced replay is pending.
            stroke(pad, y = .65f)
            waitUntil { pad.retainedStrokeCount == 2 }
            val before = pad.publishedReplayGeneration
            instrumentation.runOnMainSync {
                pad.configure(preset.copy(deposition = preset.deposition.copy(opacity = 0f)), AppSettings())
            }
            waitUntil { pad.publishedReplayGeneration > before && pixels(pad).all { it == 0 } }
            val empty = pad.publishedReplayGeneration
            instrumentation.runOnMainSync { pad.configure(preset, AppSettings()) }
            waitUntil { pad.publishedReplayGeneration > empty }
            val result = pixels(pad)
            assertTrue("First contact remains", (350..400).any { row -> result.sliceArray(row * 900 until (row + 1) * 900).any { it != 0 } })
            assertTrue("Contact made during replay remains", (760..800).any { row -> result.sliceArray(row * 900 until (row + 1) * 900).any { it != 0 } })
        }
    }

    private fun withPad(output: BrushOutput, action: (BrushDrawingPadView, BrushPresetV5) -> Unit) {
        val base = BrushPresetV5Import.requirePreset(SystemBrushCatalog.brushes.first { it.id == "system-hb-pencil" })
        val preset = base.copy(output = output, tip = base.tip.copy(size = 100f),
            deposition = base.deposition.copy(opacity = 1f, flow = 1f))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var pad: BrushDrawingPadView
            scenario.onActivity { activity ->
                pad = BrushDrawingPadView(activity)
                activity.setContentView(pad)
                pad.configure(preset, AppSettings())
            }
            waitUntil { pad.width > 0 && pad.publishedReplayGeneration > 0 }
            action(pad, preset)
        }
    }

    private fun pixels(pad: BrushDrawingPadView): IntArray {
        var pixels = IntArray(0)
        instrumentation.runOnMainSync {
            val snapshot = requireNotNull(pad.canvas.activeRasterSnapshotForTest())
            val bitmap = app.inkloom.engine.v5.android.PixelTileBitmapAdapter.render(snapshot)
            try { pixels = IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            } } finally { bitmap.recycle() }
        }
        return pixels
    }

    private fun stroke(pad: BrushDrawingPadView, y: Float = .32f) {
        val position = IntArray(2)
        instrumentation.runOnMainSync { pad.canvas.getLocationOnScreen(position) }
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, t: Float) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    x = position[0] + pad.canvas.width * (.2f + .6f * t)
                    this.y = position[1] + pad.canvas.height * y
                    pressure = if (action == MotionEvent.ACTION_UP) 0f else .3f + .7f * t
                    setAxisValue(MotionEvent.AXIS_TILT, .2f)
                }), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        repeat(24) { i -> SystemClock.sleep(8); send(MotionEvent.ACTION_MOVE, (i + 1) / 24f) }
        send(MotionEvent.ACTION_UP, 1f)
        instrumentation.waitForIdleSync()
        pad.canvas.awaitStudioPaintIdle()
        instrumentation.waitForIdleSync()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition() && SystemClock.uptimeMillis() < end) SystemClock.sleep(32)
        assertTrue("Studio replay did not settle", condition())
    }
}
