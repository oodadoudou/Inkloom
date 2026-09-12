package app.inkloom

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import app.inkloom.ui.ColorMixingSession
import app.inkloom.ui.ColorMixingView
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.color.MixingBoard
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises the actual Android production blend engine, never a JVM mock raster. */
class MixingBoardRasterTest {
    @Test fun aBlendTapMixesTheLoadedFootprintOnceAndCanBeUndone() {
        val board = MixingBoard(100, 80)
        val initial = IntArray(8000) { if (it % 100 < 50) Color.BLUE else Color.GREEN }
        board.restore(initial)
        board.begin(50f, 40f, MixingBoard.Tool.BLEND, Color.RED, 24f)
        val preview = board.snapshot()
        assertFalse("A deliberate tap blends both pigments under the brush", initial.contentEquals(preview))
        repeat(10) { board.move(50f, 40f) }
        assertArrayEquals("Stationary packets do not keep adding deposits", preview, board.snapshot())
        assertTrue(board.finish())
        assertArrayEquals(preview, board.snapshot())
        board.undo(); assertArrayEquals(initial, board.snapshot())
    }

    @Test fun blendCarriesExistingColorInsteadOfPaintingTheSelectedColor() {
        val board = MixingBoard(80, 40)
        board.begin(10f, 20f, MixingBoard.Tool.PAINT, 0xffff0000.toInt(), 10f)
        board.move(35f, 20f); board.finish()
        board.begin(45f, 20f, MixingBoard.Tool.PAINT, 0xff0000ff.toInt(), 10f)
        board.move(70f, 20f); board.finish()
        board.begin(25f, 20f, MixingBoard.Tool.BLEND, 0xff00ff00.toInt(), 10f)
        board.move(60f, 20f); board.finish()
        val mixed = board.sample(48f, 20f)
        assertTrue("Expected carried red at 48: ${Integer.toHexString(mixed)}", (mixed ushr 16 and 255) > 10)
        assertTrue((mixed and 255) > 10)
        assertTrue((mixed ushr 8 and 255) < 30)
    }
    @Test fun blendSamplesTheFootprintEvenWhenTheCenterHasNoPigment() {
        val board = MixingBoard(160, 80)
        val initial = IntArray(160 * 80)
        for (y in 20..30) for (x in 10..55) initial[y * 160 + x] = Color.RED
        board.restore(initial)
        board.begin(35f, 39f, MixingBoard.Tool.BLEND, Color.GREEN, 24f)
        board.move(105f, 39f); assertTrue(board.finish())
        val carried = board.snapshot().filterIndexed { i, _ -> i % 160 in 75..110 && i / 160 in 20..35 }
        save(board, "mix-footprint.png")
        assertTrue("Pigment beside the contact center is picked up and carried; maximum alpha ${carried.maxOf { Color.alpha(it) }}", carried.any { Color.alpha(it) > 10 && Color.red(it) > 220 })
        assertTrue("The selected green color is never injected by Blend", carried.all { Color.green(it) == 0 })
    }

    @Test fun transparentEdgesDoNotTurnPigmentBlackAndCancelRestoresExactly() {
        val board = MixingBoard(180, 90)
        paint(board, 25f, 35f, 55f, 35f, Color.CYAN, 20f)
        val initial = board.snapshot()
        board.begin(45f, 35f, MixingBoard.Tool.BLEND, Color.RED, 24f)
        board.move(135f, 35f)
        val preview = board.snapshot()
        assertFalse(initial.contentEquals(preview))
        preview.filter { Color.alpha(it) > 12 }.forEach {
            assertTrue("Transparent black must not contaminate carried cyan", Color.green(it) > 245 && Color.blue(it) > 245)
        }
        board.cancel(); assertArrayEquals(initial, board.snapshot())
        board.begin(45f, 35f, MixingBoard.Tool.BLEND, Color.RED, 24f)
        board.move(135f, 35f); assertTrue(board.finish())
        val mixed = board.snapshot()
        board.undo(); assertArrayEquals(initial, board.snapshot())
        board.redo(); assertArrayEquals(mixed, board.snapshot())
    }

    @Test fun sparseAndDenseBlendHaveTheSameResultWithoutStationaryBuildup() {
        fun render(step: Int): IntArray {
            val board = referenceBoard()
            board.begin(90f, 100f, MixingBoard.Tool.BLEND, Color.RED, 28f)
            for (x in 90 + step..246 step step) board.move(x.toFloat(), 100f)
            board.move(246f, 100f)
            val before = board.snapshot()
            repeat(30) { board.move(246f, 100f) }
            assertArrayEquals("Stationary input must not continue applying paint", before, board.snapshot())
            board.finish()
            return board.snapshot()
        }
        val sparse = render(156); val dense = render(3)
        val largest = sparse.indices.maxOf { i -> (0..3).maxOf { channel -> kotlin.math.abs((sparse[i] ushr (channel*8) and 255) - (dense[i] ushr (channel*8) and 255)) } }
        assertTrue("Input packet density cannot change the mixture: max channel error $largest", largest <= 2)
    }

    @Test fun blueGreenMixProducesGradualTransitionAndExportsRasterEvidence() {
        val board = referenceBoard()
        save(board, "mix-before.png")
        val initial = board.snapshot()
        val durations = mutableListOf<Long>()
        repeat(3) { pass ->
            board.begin(90f, 98f + pass * 6, MixingBoard.Tool.BLEND, Color.RED, 32f)
            for (x in 93..243 step 3) {
                val start = System.nanoTime()
                board.move(x.toFloat(), 98f + pass * 6)
                durations.add(System.nanoTime() - start)
            }
            board.finish()
        }
        val mixed = board.snapshot()
        save(board, "mix-after.png")
        assertFalse(initial.contentEquals(mixed))
        val line = (135..210).map { board.sample(it.toFloat(), 105f) }
        assertTrue("A range of intermediate cyan/green colors must exist", line.map { Color.blue(it) / 4 }.distinct().size >= 10)
        assertTrue("Both source pigments contribute to the overlap", line.count { Color.green(it) > 100 && Color.blue(it) in 25..220 } >= 20)
        assertTrue("Mixing never erases pigment coverage", mixed.indices.all { Color.alpha(mixed[it]) >= Color.alpha(initial[it]) })
        board.undo(); board.redo(); assertArrayEquals(mixed, board.snapshot())
        val sorted = durations.drop(6).sorted()
        val p95 = sorted[(sorted.size * .95).toInt()] / 1_000_000.0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = File(context.getExternalFilesDir(null), "mixing190-timing.json")
        metrics.writeText("""{"Android15Emulator":true,"samples":${sorted.size},"blendMoveP95Ms":$p95,"maximumMs":${sorted.last()/1_000_000.0},"hardware":"DEVICE_PENDING"}""")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${metrics.absolutePath} /data/local/tmp/inkloom-190-mixing-timing.json",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    @Test fun stylusPressureAndZeroPressureLiftUseTheRealPaletteInputPath() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "mix-pressure-${SystemClock.uptimeMillis()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        lateinit var session: ColorMixingSession
        lateinit var view: ColorMixingView
        try {
            instrumentation.runOnMainSync {
                session = ColorMixingSession(isolated)
                view = ColorMixingView(context, session)
                view.layout(0, 0, 384, 256)
                view.brushRadius = 18f
            }
            val deadline = SystemClock.uptimeMillis() + 3000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { ready = view.ready }
                if (!ready) SystemClock.sleep(10)
            }
            assertTrue(ready)
            instrumentation.runOnMainSync {
                fun input(action: Int, x: Float, y: Float, p: Float) {
                    val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_STYLUS })
                    val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = p })
                    val now = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(now, now, action, 1, properties, coordinates, 0, 0, 1f, 1f, 3, 0, InputDevice.SOURCE_STYLUS, 0)
                    try {
                        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_EXIT) view.onHoverEvent(event)
                        else assertTrue(view.onTouchEvent(event))
                    } finally { event.recycle() }
                }
                input(MotionEvent.ACTION_DOWN, 25f, 70f, .1f)
                input(MotionEvent.ACTION_MOVE, 320f, 70f, .1f)
                val beforeLift = view.board.snapshot()
                input(MotionEvent.ACTION_UP, 320f, 70f, 0f)
                assertArrayEquals("Zero-pressure lift preserves the visible contact", beforeLift, view.board.snapshot())
                input(MotionEvent.ACTION_HOVER_ENTER, 100f, 70f, 0f)
                input(MotionEvent.ACTION_HOVER_EXIT, 100f, 70f, 0f)
                assertArrayEquals("Hover is presentation only", beforeLift, view.board.snapshot())
                input(MotionEvent.ACTION_DOWN, 25f, 170f, 1f)
                input(MotionEvent.ACTION_MOVE, 320f, 170f, 1f)
                input(MotionEvent.ACTION_UP, 320f, 170f, 0f)
                val light = (40..100).count { Color.alpha(view.board.sample(160f, it.toFloat())) > 10 }
                val firm = (140..200).count { Color.alpha(view.board.sample(160f, it.toFloat())) > 10 }
                assertTrue("Historical pen pressure must affect the palette brush", firm > light * 2)
                session.undo(); assertArrayEquals(beforeLift, view.board.snapshot())
            }
        } finally {
            instrumentation.runOnMainSync { session.close() }
            directory.deleteRecursively()
        }
    }

    private fun referenceBoard() = MixingBoard(320, 220).also {
        paint(it, 85f, 92f, 128f, 140f, 0xff77aaff.toInt(), 46f)
        paint(it, 195f, 88f, 232f, 145f, 0xff00df15.toInt(), 46f)
    }
    private fun paint(board: MixingBoard, x: Float, y: Float, endX: Float, endY: Float, color: Int, radius: Float) {
        board.begin(x, y, MixingBoard.Tool.PAINT, color, radius)
        board.move(endX, endY); board.finish()
    }
    private fun save(board: MixingBoard, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "mixing190").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(board.snapshot(), board.width, board.height, Bitmap.Config.ARGB_8888)
        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${File(output, name).absolutePath} /data/local/tmp/inkloom-190-$name",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }
}
