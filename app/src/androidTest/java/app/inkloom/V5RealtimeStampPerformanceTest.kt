package app.inkloom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.brushpresetv5.TextureGrainSettings
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V5RealtimeStampPerformanceTest {
    @Test
    fun importedTipConsumesThreeHundredHertzIncrementallyWithoutLegacyReplay() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tip = File(context.cacheDir, "v5-performance-tip.png")
        val grain = File(context.cacheDir, "v5-performance-grain.png")
        Bitmap.createBitmap(
            IntArray(32 * 32) { index ->
                val x = index % 32
                val y = index / 32
                if ((x - 16) * (x - 16) + (y - 16) * (y - 16) < 240) Color.BLACK else Color.WHITE
            },
            32,
            32,
            Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        Bitmap.createBitmap(
            IntArray(32 * 32) { index ->
                val x = index % 32
                val y = index / 32
                if ((x / 3 + y / 5) % 2 == 0) Color.BLACK else Color.WHITE
            },
            32,
            32,
            Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            grain.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        val surface = PixelTileSurface(2048, 1536)
        val display = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        var commits = 0
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(context).apply {
                layout(0, 0, 1024, 768)
                tool = EditorTool.PEN
                activeLayerId = "paint"
                installV5TestInk(
                    size = 18f,
                    tipAssetPaths = listOf(tip.absolutePath),
                    textureGrain = TextureGrainSettings(
                        assetPath = grain.absolutePath,
                        density = .7f,
                        scale = 86f,
                        movement = .72f,
                        zoom = .35f,
                        rotationDegrees = 17f,
                    ),
                )
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onRasterLayerCommitted = { commits++ }
            }
        }

        val downTime = SystemClock.uptimeMillis()
        dispatch(view, MotionEvent.ACTION_DOWN, 80f, 260f, downTime, downTime, .45f)
        val times = ArrayList<Float>(240)
        repeat(240) { index ->
            val x = 84f + index * 3.4f
            val y = 260f + kotlin.math.sin(index * .12f) * 16f
            val started = SystemClock.elapsedRealtimeNanos()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                dispatchDirect(view, MotionEvent.ACTION_MOVE, x, y, downTime, downTime + 1L + index * 3L, .3f + index % 7 * .1f)
                display.eraseColor(Color.TRANSPARENT)
                view.draw(Canvas(display))
            }
            times += (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000f
        }
        assertTrue("Imported V5 delta must be visible before UP", hasInk(display))

        val lastX = 84f + 239 * 3.4f
        val lastY = 260f + kotlin.math.sin(239 * .12f) * 16f
        dispatch(view, MotionEvent.ACTION_UP, lastX, lastY, downTime, downTime + 730L, .8f)
        val ordered = times.sorted()
        val p95 = ordered[(ordered.size * .95f).toInt().coerceIn(0, ordered.lastIndex)]
        val p99 = ordered[(ordered.size * .99f).toInt().coerceIn(0, ordered.lastIndex)]
        android.util.Log.i("InkloomPerf", "v5_stamp_input_present_p95_ms=$p95 p99_ms=$p99")
        assertTrue("V5 stamp input-to-present p95 was ${p95}ms", p95 < 16.7f)
        assertTrue("V5 stamp input-to-present p99 was ${p99}ms", p99 < 33.4f)
        assertEquals(1, commits)
        assertTrue(surface.tileCount > 0)
        display.recycle()
        tip.delete()
        grain.delete()
    }

    private fun dispatch(
        view: InkCanvasView,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long,
        pressure: Float,
    ) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        dispatchDirect(view, action, x, y, downTime, eventTime, pressure)
    }

    private fun dispatchDirect(
        view: InkCanvasView,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long,
        pressure: Float,
    ) {
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                this.pressure = pressure
                size = .04f
            }),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun hasInk(bitmap: Bitmap): Boolean {
        for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) return true
        }
        return false
    }
}
