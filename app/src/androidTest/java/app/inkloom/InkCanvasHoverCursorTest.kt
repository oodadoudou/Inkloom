package app.inkloom

import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.DepositionSettings
import app.inkloom.brushpresetv5.DynamicsSettings
import app.inkloom.brushpresetv5.PresetIdentity
import app.inkloom.brushpresetv5.PressureMappings
import app.inkloom.brushpresetv5.TiltMappings
import app.inkloom.brushpresetv5.TipSettings
import app.inkloom.brushpresetv5.TipTextureSettings
import app.inkloom.model.EditorTool
import app.inkloom.settings.AppSettings
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushPresetV5Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class InkCanvasHoverCursorTest {
    @Test
    fun stylusHoverUsesCurrentBrushDiameterAndTracksZoomedCoordinates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            viewportScale = 2f
            viewportPanX = 100f
            viewportPanY = -50f
            layout(0, 0, 1600, 1200)
            applyBrushPresetV5Configuration(preset("hover-size", 160f, widthPressure = false), AppSettings())
        }

        instrumentation.runOnMainSync {
            val hover = stylusHover(900f, 550f)
            assertTrue(view.onHoverEvent(hover))
            hover.recycle()
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(1f, state[0], 0f)
        assertEquals(900f, state[1], .01f)
        assertEquals(550f, state[2], .01f)
        assertEquals(160f, state[3], .01f)

        view.baseWidth = 320f
        assertEquals(320f, view.hoverCursorSnapshot()[3], .01f)
    }

    @Test
    fun pressedStylusCursorFollowsEveryRawMoveAndReflectsPressureSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1600, 1200)
            applyBrushPresetV5Configuration(preset("pressure-cursor", 200f, widthMinimum = .25f), AppSettings())
        }

        instrumentation.runOnMainSync {
            stylusContact(MotionEvent.ACTION_DOWN, 300f, 240f, .2f, 1L, 1L).also {
                assertTrue(view.onTouchEvent(it)); it.recycle()
            }
            stylusContact(MotionEvent.ACTION_MOVE, 920f, 710f, .8f, 1L, 12L).also {
                assertTrue(view.onTouchEvent(it)); it.recycle()
            }
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(1f, state[0], 0f)
        assertEquals(920f, state[1], .01f)
        assertEquals(710f, state[2], .01f)
        assertEquals(170f, state[3], .75f)
    }

    @Test
    fun cursorKeepsFollowingStylusWhenPalmIsPointerZeroAndUsesGlobalPressureCurve() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1600, 1200)
            applyBrushPresetV5Configuration(preset("calibrated-cursor", 200f, widthMinimum = .25f), AppSettings())
            pressureMinimumInput = .2f
            pressureMaximumInput = 1f
        }

        instrumentation.runOnMainSync {
            fingerAndStylusMove(fingerX = 30f, fingerY = 40f, stylusX = 980f, stylusY = 760f, stylusPressure = .2f).also {
                assertTrue(view.onTouchEvent(it)); it.recycle()
            }
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(980f, state[1], .01f)
        assertEquals(760f, state[2], .01f)
        assertEquals(50f, state[3], .75f)
    }

    @Test
    fun importedAsymmetricTipCursorMatchesTheRenderedFootprint() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val tip = File(instrumentation.targetContext.cacheDir, "cursor-asymmetric-tip.png")
        Bitmap.createBitmap(40, 100, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.BLACK)
            FileOutputStream(tip).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1600, 1200)
            applyBrushPresetV5Configuration(
                preset("asymmetric-cursor", 160f, widthPressure = false).copy(
                    tipTexture = TipTextureSettings(procedural = false, assetPath = tip.absolutePath, assetPaths = listOf(tip.absolutePath)),
                ),
                AppSettings(),
            )
        }

        instrumentation.runOnMainSync {
            stylusHover(800f, 600f).also { assertTrue(view.onHoverEvent(it)); it.recycle() }
        }

        val state = view.hoverCursorSnapshot()
        assertEquals(64f, state[3], .75f)
        assertEquals(160f, state[4], .75f)
    }

    @Test
    fun cursorRotationComesFromTheSameV5TipAsTheRenderer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            viewportRotationDegrees = 37f
            layout(0, 0, 1600, 1200)
            applyBrushPresetV5Configuration(
                preset("rotated-cursor", 40f, widthPressure = false).copy(tip = TipSettings(size = 40f, rotationDegrees = 30f)),
                AppSettings(),
            )
        }

        instrumentation.runOnMainSync {
            stylusHover(800f, 600f).also { assertTrue(view.onHoverEvent(it)); it.recycle() }
        }
        assertEquals(67f, view.hoverCursorSnapshot()[5], .01f)
    }

    private fun preset(
        id: String,
        size: Float,
        widthPressure: Boolean = true,
        widthMinimum: Float = .35f,
    ) = BrushPresetV5(
        identity = PresetIdentity(id = id, name = id, family = BrushFamily.INK, renderBrushId = id),
        renderSeed = id.hashCode(),
        tip = TipSettings(size = size),
        deposition = DepositionSettings(),
        dynamics = DynamicsSettings(
            pressure = PressureMappings(widthEnabled = widthPressure, widthMinimum = widthMinimum),
            tilt = TiltMappings(),
        ),
    )

    private fun stylusHover(x: Float, y: Float): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 0f
            size = 0f
        })
        return MotionEvent.obtain(
            1L, 2L, MotionEvent.ACTION_HOVER_MOVE, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
    }

    private fun stylusContact(action: Int, x: Float, y: Float, pressure: Float, downTime: Long, eventTime: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = 0f })
        return MotionEvent.obtain(
            downTime, eventTime, action, 1, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
    }

    private fun fingerAndStylusMove(fingerX: Float, fingerY: Float, stylusX: Float, stylusY: Float, stylusPressure: Float): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 7; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 42; toolType = MotionEvent.TOOL_TYPE_STYLUS },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = fingerX; y = fingerY; pressure = 1f },
            MotionEvent.PointerCoords().apply { x = stylusX; y = stylusY; pressure = stylusPressure },
        )
        return MotionEvent.obtain(
            1L, 2L, MotionEvent.ACTION_MOVE, 2, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS, 0,
        )
    }
}
