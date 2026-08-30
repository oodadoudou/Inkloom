package app.inkloom

import android.graphics.Color
import android.graphics.Bitmap
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.DepositionSettings
import app.inkloom.brushpresetv5.DynamicsSettings
import app.inkloom.brushpresetv5.DualBrushSettings
import app.inkloom.brushpresetv5.DualCompositeMode
import app.inkloom.brushpresetv5.PresetIdentity
import app.inkloom.brushpresetv5.PressureMappings
import app.inkloom.brushpresetv5.TiltMappings
import app.inkloom.brushpresetv5.TipSettings
import app.inkloom.brushpresetv5.TipTextureSettings
import app.inkloom.brushpresetv5.TextureGrainSettings
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.ui.InkCanvasView
import app.inkloom.ui.applyBrushConfiguration
import app.inkloom.ui.applyBrushPresetV5Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrushPresetV5EditorCanvasTest {
    @Test fun importedTextureGrainAndDualStreamsExecuteThroughProductionV5Contact() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val assetRoot = File(context.cacheDir, "v5-dual-${System.nanoTime()}").apply { mkdirs() }
        val primaryTip = writeMask(File(assetRoot, "primary.png"), invert = false)
        val secondaryTip = writeMask(File(assetRoot, "secondary.png"), invert = true)
        val primaryGrain = writeMask(File(assetRoot, "grain.png"), invert = false)
        val secondaryGrain = writeMask(File(assetRoot, "secondary-grain.png"), invert = true)
        val surface = PixelTileSurface(600, 600)
        lateinit var view: InkCanvasView
        try {
            val preset = referenceInk().copy(
                deposition = DepositionSettings(opacity = .8f, flow = .75f),
                tipTexture = TipTextureSettings(procedural = false, assetPaths = listOf(primaryTip.absolutePath)),
                textureGrain = TextureGrainSettings(primaryGrain.absolutePath, density = .55f, scale = 125f),
                dual = DualBrushSettings(
                    tipAssetPaths = listOf(secondaryTip.absolutePath),
                    sizeScale = .7f,
                    flow = .65f,
                    compositeMode = DualCompositeMode.MULTIPLY,
                    textureGrain = TextureGrainSettings(secondaryGrain.absolutePath, density = .4f, scale = 90f),
                ),
            )
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view = InkCanvasView(context).apply {
                    layout(0, 0, 400, 400)
                    activeLayerId = "paint"
                    setRasterLayerSurfaces(mapOf("paint" to surface))
                    applyBrushPresetV5Configuration(preset, AppSettings(), brushColor = Color.BLACK)
                    prepareBrushAssetsNow()
                }
            }

            sendStylus(view, MotionEvent.ACTION_DOWN, 70f, 180f, 1L)
            val moveTimes = ArrayList<Long>(60)
            repeat(60) { index ->
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                sendStylus(view, MotionEvent.ACTION_MOVE, 74f + index * 4f, 180f + (index % 3), 2L + index)
                view.awaitV5StrokeWorkerIdleForTest()
                if (index >= 8) moveTimes += android.os.SystemClock.elapsedRealtimeNanos() - started
            }
            sendStylus(view, MotionEvent.ACTION_UP, 330f, 180f, 70L)

            assertEquals(1L, surface.revision)
            assertTrue(surface.snapshot().tileInfo.sumOf { it.nonTransparentPixelCount } > 0)
            val sorted = moveTimes.sorted()
            val p95Ms = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.lastIndex)] / 1_000_000f
            assertTrue("V5 grain/dual contact P95 was ${p95Ms}ms", p95Ms < 16.7f)
        } finally {
            assetRoot.deleteRecursively()
        }
    }

    @Test fun editorPresetUsesRealV5ContactAndPublishesRasterPixels() {
        val surface = PixelTileSurface(600, 600)
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400)
                activeLayerId = "paint"
                layerOrder = listOf("paint")
                setRasterLayerSurfaces(mapOf("paint" to surface))
                applyBrushPresetV5Configuration(referenceInk(), AppSettings(), brushColor = Color.BLACK)
            }
        }

        sendStylus(view, MotionEvent.ACTION_DOWN, 80f, 200f, 1L)
        sendStylus(view, MotionEvent.ACTION_MOVE, 240f, 200f, 5L)
        assertEquals("Active V5 delta must not publish before UP", 0, surface.tileCount)
        sendStylus(view, MotionEvent.ACTION_UP, 300f, 200f, 9L)

        assertTrue(surface.tileCount > 0)
        assertEquals(1, view.v5DryGestureCount)
        assertTrue(surface.snapshot().tileInfo.sumOf { it.nonTransparentPixelCount } > 0)
    }

    @Test fun semanticallyEqualComposeUpdateDoesNotCancelAnActiveV5Stroke() {
        val surface = PixelTileSurface(600, 600)
        val preset = referenceInk()
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400)
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                applyBrushPresetV5Configuration(preset, AppSettings(), brushColor = Color.BLACK)
            }
        }

        sendStylus(view, MotionEvent.ACTION_DOWN, 60f, 180f, 1L)
        sendStylus(view, MotionEvent.ACTION_MOVE, 180f, 180f, 4L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // This compiles a new immutable object, as AndroidView update does during recomposition.
            view.applyBrushPresetV5Configuration(preset.copy(), AppSettings(), brushColor = Color.BLACK)
        }
        sendStylus(view, MotionEvent.ACTION_MOVE, 280f, 180f, 7L)
        sendStylus(view, MotionEvent.ACTION_UP, 340f, 180f, 10L)

        assertEquals(1L, surface.revision)
        assertTrue(surface.tileCount > 0)
    }

    @Test fun productionSelectionWithoutV5PayloadIsRejectedInsteadOfFallingBack() {
        val surface = PixelTileSurface(600, 600)
        var rejection: String? = null
        lateinit var view: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 400, 400)
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                onPaintCapabilityRejected = { rejection = it }
                applyBrushConfiguration(
                    EditorTool.PEN,
                    AppSettings(),
                    ImportedBrush(
                        id = "retired-payload",
                        name = "Retired payload",
                        format = BrushFormat.INKLOOM,
                        compatibility = Compatibility.FAILED,
                        sizeBytes = 0,
                        report = emptyList(),
                    ),
                    20f,
                    1f,
                    Color.BLACK,
                    EraserPreset.NORMAL_FREE,
                )
            }
        }

        sendStylus(view, MotionEvent.ACTION_DOWN, 60f, 220f, 1L)
        sendStylus(view, MotionEvent.ACTION_UP, 300f, 220f, 5L)

        assertEquals(0L, surface.revision)
        assertTrue("Expected an explicit V5 rejection, received: $rejection", rejection?.contains("V5") == true)
    }

    private fun referenceInk() = BrushPresetV5(
        identity = PresetIdentity("editor-v5", "Editor V5", family = BrushFamily.INK, renderBrushId = "editor-v5"),
        renderSeed = 42,
        tip = TipSettings(size = 22f, hardness = 1f),
        deposition = DepositionSettings(opacity = 1f, flow = 1f),
        dynamics = DynamicsSettings(PressureMappings(widthEnabled = false), TiltMappings()),
    )

    private fun writeMask(file: File, invert: Boolean): File {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val on = ((x / 4 + y / 4) % 2 == 0) xor invert
            bitmap.setPixel(x, y, Color.argb(if (on) 255 else 64, 255, 255, 255))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun sendStylus(view: InkCanvasView, action: Int, x: Float, y: Float, time: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .8f; size = .01f }
        val event = MotionEvent.obtain(
            0L, time, action, 1, arrayOf(properties), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0,
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
        if (action == MotionEvent.ACTION_UP) {
            view.awaitV5StrokeWorkerIdleForTest()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { }
        }
    }
}
