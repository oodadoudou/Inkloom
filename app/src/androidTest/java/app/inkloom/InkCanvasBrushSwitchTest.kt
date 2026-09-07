package app.inkloom

import app.inkloom.model.BrushToolBinding
import app.inkloom.model.BrushToolRole

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.LiquifyMode
import app.inkloom.document.LayerBlendMode
import app.inkloom.document.LayerMask
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.engine.v5.surface.PixelTileSurface
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
    fun cursorPreviewUsesTheSelectedBrushPressureAndTiltProgram() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext).apply { layout(0, 0, 1600, 1200) }
        val dynamic = brush("dynamic-cursor", "Dynamic cursor").copy(
            sizePressureEnabled = true,
            sizePressureMinimum = .2f,
            sizeSecondaryDynamics = BrushSecondaryDynamics(
                tiltEnabled = true,
                tiltMinimum = .5f,
            ),
        ).v5()
        val fixed = brush("fixed-cursor", "Fixed cursor").copy(sizePressureEnabled = false).v5()

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), dynamic, 100f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEventWithDynamics(MotionEvent.ACTION_DOWN, 400f, 400f, 1L, pressure = .75f, tilt = 0f))
            assertEquals("Pressure (0.8) and upright tilt minimum (0.5) must both affect the cursor", 40f, view.hoverCursorSnapshot()[3], .75f)
            view.onTouchEvent(stylusEventWithDynamics(MotionEvent.ACTION_UP, 400f, 400f, 2L, pressure = .75f, tilt = 0f))

            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), fixed, 100f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEventWithDynamics(MotionEvent.ACTION_DOWN, 600f, 400f, 3L, pressure = .75f, tilt = 0f))
            assertEquals("Switching presets must replace the precompiled cursor program", 100f, view.hoverCursorSnapshot()[3], .75f)
            view.onTouchEvent(stylusEventWithDynamics(MotionEvent.ACTION_UP, 600f, 400f, 4L, pressure = .75f, tilt = 0f))
        }
        settlePublishedStroke(view)
    }

    @Test
    fun cspScreenSizedBrushKeepsPhysicalCursorAndWritesThroughV5AtZoom() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1600, 1200)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1600, 1200)
            viewportScale = 4f
            setRasterLayerSurfaces(mapOf(app.inkloom.document.LayerIds.INK to surface))
        }
        val synchronized = brush("csp-screen-size", "Screen size").copy(sizeSyncViewScale = true, sizePressureEnabled = false).v5()
        val documentSized = brush("csp-document-size", "Document size").copy(sizePressureEnabled = false).v5()
        val synchronizedEraser = brush("csp-screen-eraser", "Screen eraser").copy(sizeSyncViewScale = true, sizePressureEnabled = false, toolBinding = BrushToolBinding(BrushToolRole.ERASE)).v5()

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), synchronized, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 650f, 500f, 1L))
            assertEquals(10f, view.hoverCursorSnapshot()[3], .001f)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 800f, 500f, 5L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, 500f, 9L))

            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), documentSized, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 650f, 700f, 20L))
            assertEquals(40f, view.hoverCursorSnapshot()[3], .001f)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 800f, 700f, 24L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, 700f, 28L))

            view.applyBrushConfiguration(EditorTool.ERASER, AppSettings(), synchronizedEraser, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 800f, 700f, 40L))
            assertEquals(10f, view.hoverCursorSnapshot()[3], .001f)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 800f, 700f, 44L))
        }
        settlePublishedStroke(view)

        assertTrue("Both pens and the eraser must enter V5 after zoom", view.v5DryGestureCount >= 3)
        assertTrue("Zoomed contacts must publish raster generations", surface.revision >= 2L)
    }
    @Test
    fun everyBrushAndToolSwitchStartsAUsableV5InputStream() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            setRasterLayerSurfaces(mapOf(app.inkloom.document.LayerIds.INK to surface))
        }
        val first = brush("first", "First").v5()
        val second = brush("second", "Second").v5()

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), first, 16f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 180f)

            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 420f, 20L))
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), second, 22f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 30L, 520f)

            view.applyBrushConfiguration(EditorTool.HIGHLIGHTER, AppSettings(), null, 28f, .35f, Color.YELLOW, EraserPreset.NORMAL_FREE)
            drawStroke(view, 60L, 760f)
        }
        settlePublishedStroke(view)

        assertEquals("Canceled contact must not publish; three completed contacts must", 3L, surface.revision)
        assertTrue(surface.tileCount > 0)
    }

    @Test
    fun importedCorrectionBrushPaintsThroughV5WithoutPenUpTrajectoryReplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200)
        val corrected = brush("corrected-sut", "Corrected SUT").copy(
            format = BrushFormat.SUT,
            stabilization = .35f,
            streamLine = .6f,
            postCorrectionEnabled = true,
            postCorrection = .8f,
        ).v5()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            setRasterLayerSurfaces(mapOf(app.inkloom.document.LayerIds.INK to surface))
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), corrected, 20f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 200f, 500f, 1L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 450f, 520f, 5L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 700f, 480f, 9L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, 500f, 13L))
        }
        settlePublishedStroke(view)

        assertEquals("One contact publishes exactly one immutable generation", 1L, surface.revision)
        assertEquals(1, view.v5DryGestureCount)
        assertEquals("Every real input sample is emitted once; pen-up does not replay the path", 4, view.v5LastCommittedNormalizedPoints().size)
        assertTrue(surface.tileCount > 0)
    }

    @Test
    fun projectionPropertiesNeverDisableEditingTheLayerPixelSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            layerOpacities = mapOf("paint" to .35f)
            layerBlendModes = mapOf("paint" to LayerBlendMode.MULTIPLY)
            clippedLayers = setOf("paint")
            layerMasks = mapOf("paint" to LayerMask(enabled = true))
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(
                EditorTool.PEN,
                AppSettings(),
                brush("projected-layer", "Projected layer").v5(),
                24f,
                1f,
                Color.BLACK,
                EraserPreset.NORMAL_FREE,
            )
            drawStroke(view, 1L, 600f)
        }
        settlePublishedStroke(view)

        assertEquals(1, view.v5DryGestureCount)
        assertEquals(1L, surface.revision)
        assertTrue(surface.tileCount > 0)
    }

    @Test
    fun complexLayerProjectionDisplaysTheActiveSparseDeltaBeforePenUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200)
        val frame = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            layerOrder = listOf("paint")
            layerOpacities = mapOf("paint" to .5f)
        }

        try {
            instrumentation.runOnMainSync {
                view.applyBrushConfiguration(
                    EditorTool.PEN,
                    AppSettings(),
                    brush("active-projection", "Active projection").v5(),
                    80f,
                    1f,
                    Color.BLACK,
                    EraserPreset.NORMAL_FREE,
                )
                view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 600f, 600f, 1L))
                view.draw(Canvas(frame))
            }

            assertEquals("Committed layer stays immutable before UP", 0L, surface.revision)
            assertTrue("Active delta must be visible through the incremental projection", Color.alpha(frame.getPixel(600, 600)) > 0)
            assertTrue(view.v5RasterProjectionDrawCount > 0)

            instrumentation.runOnMainSync {
                view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 600f, 600f, 5L))
            }
            settlePublishedStroke(view)
            assertEquals(1L, surface.revision)
        } finally {
            frame.recycle()
        }
    }

    @Test
    fun alphaLockedV5PaintPreservesExistingAlphaAndCannotCreateNewCoverage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200).also { pixels ->
            pixels.beginWrite("partial alpha fixture").run {
                setPixel(600, 600, 0x80800000.toInt())
                commit().close()
            }
        }
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            activeLayerId = "paint"
            setRasterLayerSurfaces(mapOf("paint" to surface))
            alphaLockedLayers = setOf("paint")
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(
                EditorTool.PEN,
                AppSettings(),
                brush("alpha-lock-paint", "Alpha lock paint").v5(),
                100f,
                1f,
                Color.BLUE,
                EraserPreset.NORMAL_FREE,
            )
            drawStroke(view, 1L, 600f)
        }
        settlePublishedStroke(view)

        assertEquals(128, Color.alpha(surface.pixelAt(600, 600)))
        assertTrue("The locked pixel colour should still be editable", surface.pixelAt(600, 600) != 0x80800000.toInt())
        assertEquals("Source-atop cannot create pixels outside existing alpha", 0, surface.pixelAt(400, 600))
        assertEquals(1, view.v5DryGestureCount)
    }
    @Test
    fun eraserModeCannotPaintWithAStaleNormalBrushDescriptor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200).also {
            it.beginWrite("eraser target").run { setPixel(600, 600, Color.BLACK); commit().close() }
        }
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            setRasterLayerSurfaces(mapOf(app.inkloom.document.LayerIds.INK to surface))
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.ERASER, AppSettings(eraserSize = 80f), brush("normal", "Ink pen"), 80f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 600f)
        }
        settlePublishedStroke(view)

        assertEquals("V5 eraser must remove destination pixels instead of creating black paint", 0, Color.alpha(surface.pixelAt(600, 600)))
    }

    @Test
    fun palmDownBeforeStylusStartsAtThePenAndNeverCreatesTopLeftArtifacts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            setRasterLayerSurfaces(mapOf(app.inkloom.document.LayerIds.INK to surface))
            applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush("palm-safe", "Palm safe").v5(), 18f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
        }

        instrumentation.runOnMainSync {
            view.onTouchEvent(fingerEvent(MotionEvent.ACTION_DOWN, 28f, 34f, 1L))
            view.onTouchEvent(palmAndStylusEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 720f, 640f, 2L))
            view.onTouchEvent(palmAndStylusEvent(MotionEvent.ACTION_MOVE, 900f, 700f, 3L))
            view.onTouchEvent(palmAndStylusEvent(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 980f, 740f, 4L))
        }
        settlePublishedStroke(view)

        assertEquals(1, view.v5DryGestureCount)
        assertTrue(view.v5LastCommittedNormalizedPoints().all { it.first > .5f && it.second > .45f })
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

    @Test
    fun cspColorMixProgramPaintsAndPublishesThroughV5WetOp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val surface = PixelTileSurface(1200, 1200)
        var committed: app.inkloom.engine.v5.tools.RasterLayerPatch? = null
        val brush = brush("csp-running-color", "Running color").copy(
            watercolor = true,
            cspColorMixMode = app.inkloom.document.CspColorMixMode.RUNNING_COLOR,
            colorMix = .42f,
            paintMix = .63f,
            colorStretch = .71f,
        ).v5()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            setRasterLayerSurfaces(mapOf(app.inkloom.document.LayerIds.INK to surface))
            onRasterLayerCommitted = { commit -> committed = commit.capturePatch() }
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, 40f, 1f, Color.RED, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 600f)
        }
        settlePublishedStroke(view)

        try {
            assertEquals(1L, surface.revision)
            assertTrue(surface.tileCount > 0)
            assertTrue(surface.pixelAt(600, 600) != Color.TRANSPARENT)
            assertTrue(committed != null)
        } finally {
            committed?.close()
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

    private fun ImportedBrush.v5(): ImportedBrush = BrushPresetV5Import.convertDecodedSource(this)

    private fun drawStroke(view: InkCanvasView, startTime: Long, y: Float) {
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, y, startTime))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 600f, y, startTime + 4))
        view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, y, startTime + 8))
    }

    private fun settlePublishedStroke(view: InkCanvasView) {
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
    }

    private fun stylusEvent(action: Int, x: Float, y: Float, time: Long): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .75f; size = .05f })
        return MotionEvent.obtain(1L, time, action, 1, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
    }

    private fun stylusEventWithDynamics(
        action: Int,
        x: Float,
        y: Float,
        time: Long,
        pressure: Float,
        tilt: Float,
    ): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        val coordinates = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            this.pressure = pressure
            this.setAxisValue(MotionEvent.AXIS_TILT, tilt)
            size = .05f
        })
        return MotionEvent.obtain(
            1L,
            time,
            action,
            1,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
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
