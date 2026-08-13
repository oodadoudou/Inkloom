package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.LiquifyMode
import app.inkloom.document.ProcreateTaper
import app.inkloom.document.StrokeInputKind
import app.inkloom.engine.v2.render.EngineV2ActiveStrokeSurface
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasBrushSwitchTest {
    @Test
    fun cspScreenSizedBrushKeepsPhysicalCursorAndCommitsInverseZoomDiameter() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val committed = mutableListOf<InkStroke>()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1600, 1200)
            viewportScale = 4f
            onStrokeCommitted = committed::add
        }
        val synchronized = brush("csp-screen-size", "Screen size").copy(sizeSyncViewScale = true, sizePressureEnabled = false)
        val documentSized = brush("csp-document-size", "Document size").copy(sizePressureEnabled = false)
        val synchronizedEraser = brush("csp-screen-eraser", "Screen eraser").copy(sizeSyncViewScale = true, sizePressureEnabled = false, isEraser = true)

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), synchronized, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 400f, 1L))
            val synchronizedCursor = view.hoverCursorSnapshot()
            assertEquals("Local cursor must be inverse-scaled before the 4x viewport transform", 10f, synchronizedCursor[3], .001f)
            assertEquals(40f, synchronizedCursor[3] * view.viewportScale, .001f)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 600f, 400f, 5L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, 400f, 9L))

            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), documentSized, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 250f, 700f, 20L))
            val ordinaryCursor = view.hoverCursorSnapshot()
            assertEquals(40f, ordinaryCursor[3], .001f)
            assertEquals(160f, ordinaryCursor[3] * view.viewportScale, .001f)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 600f, 700f, 24L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 950f, 700f, 28L))

            view.applyBrushConfiguration(EditorTool.ERASER, AppSettings(), synchronizedEraser, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 800f, 900f, 40L))
            val eraserCursor = view.hoverCursorSnapshot()
            assertEquals(10f, eraserCursor[3], .001f)
            assertEquals(40f, eraserCursor[3] * view.viewportScale, .001f)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 800f, 900f, 44L))
        }

        assertEquals(2, committed.size)
        assertEquals(10f, committed[0].width, .001f)
        assertEquals(40f, committed[1].width, .001f)
    }

    @Test
    fun cspPostCorrectionProgramActivatesPerBrushWithoutGlobalLeakage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val view = InkCanvasView(instrumentation.targetContext)
        val settings = AppSettings(postCorrection = .81f, postCorrectionAdjustBySpeed = false, postCorrectionAdjustByScale = true)
        val disabled = brush("disabled-correction", "Disabled correction").copy(
            postCorrectionEnabled = false,
            postCorrection = .62f,
            postCorrectionBezier = true,
            correctionSharpAngles = true,
        )
        val enabled = brush("enabled-correction", "Enabled correction").copy(
            postCorrectionEnabled = true,
            postCorrection = .37f,
            postCorrectionAdjustBySpeed = true,
            postCorrectionAdjustByScale = false,
            postCorrectionBezier = true,
            correctionSharpAngles = true,
        )

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, settings, disabled, 12f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            assertEquals("A disabled imported SUT must not inherit the app-wide correction", 0f, view.postCorrection, .0001f)
            assertFalse("A stale sharp-angle flag must not reshape a correction-disabled brush", view.correctionSharpAngles)
            assertFalse("A stale Bezier flag must not reshape a correction-disabled brush", view.postCorrectionBezier)

            view.applyBrushConfiguration(EditorTool.PEN, settings, enabled, 12f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            assertEquals(.37f, view.postCorrection, .0001f)
            assertTrue(view.postCorrectionAdjustBySpeed)
            assertFalse(view.postCorrectionAdjustByScale)
            assertTrue(view.postCorrectionBezier)
            assertTrue(view.correctionSharpAngles)

            val system = requireNotNull(SystemBrushCatalog.byId("system-g-pen"))
            view.applyBrushConfiguration(EditorTool.PEN, settings, system, 12f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            assertEquals("System brushes retain the user's global fallback", .81f, view.postCorrection, .0001f)
        }
    }

    @Test
    fun zeroCorrectionPaperTraceCommitsTheExactFinalPreviewPath() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var callbackStroke: InkStroke? = null
        val surface = RecordingActiveSurface()
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1_200, 1_200)
            activeStrokeSurface = surface
            onStrokeCommitted = { callbackStroke = it }
        }
        val noCorrection = brush("paper-trace", "Paper trace").copy(
            stabilization = 0f,
            streamLine = 0f,
            procreateStabilization = 0f,
            motionFiltering = 0f,
            postCorrectionEnabled = false,
            // Real SUT files can retain these values while their master switch is disabled.
            postCorrection = .95f,
            postCorrectionBezier = true,
            correctionSharpAngles = true,
        )

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), noCorrection, 12f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 143.25f, 211.5f, 1L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 391.75f, 517.25f, 5L))
            view.flushEngineV2ActiveFrame()
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 927.5f, 836.75f, 9L))
        }

        val committedToSurface = requireNotNull(surface.committed.singleOrNull()).single()
        val committedToDocument = requireNotNull(callbackStroke)
        assertEquals("Pen-up must commit the exact point sequence shown in the final preview", surface.rendered.last().single().points, committedToSurface.points)
        assertEquals(committedToSurface.points, committedToDocument.points)
        assertEquals(143.25f / 1_200f, committedToDocument.points.first().x, .00001f)
        assertEquals(211.5f / 1_200f, committedToDocument.points.first().y, .00001f)
        assertEquals(927.5f / 1_200f, committedToDocument.points.last().x, .00001f)
        assertEquals(836.75f / 1_200f, committedToDocument.points.last().y, .00001f)
    }

    @Test
    fun stylusGestureCommitsTheAuthoredBezierPostCorrectionAtPenUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var committed: InkStroke? = null
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            onStrokeCommitted = { committed = it }
        }
        val brush = brush("csp-bezier-correction", "CSP Bezier correction").copy(
            postCorrectionEnabled = true,
            postCorrection = .8f,
            postCorrectionBezier = true,
            correctionSharpAngles = true,
        )

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, 12f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_DOWN, 180f, 600f, 1L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 360f, 600f, 4L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 600f, 600f, 7L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_MOVE, 600f, 840f, 10L))
            view.onTouchEvent(stylusEvent(MotionEvent.ACTION_UP, 600f, 1_020f, 13L))
        }

        val stroke = requireNotNull(committed)
        assertEquals(180f / 1200f, stroke.points.first().x, .0001f)
        assertEquals(600f / 1200f, stroke.points.first().y, .0001f)
        assertEquals(600f / 1200f, stroke.points.last().x, .0001f)
        assertEquals(1_020f / 1200f, stroke.points.last().y, .0001f)
        assertTrue("Bezier correction should materialize intermediate curve samples", stroke.points.size >= 6)
        assertTrue("Sharp-angle correction must retain the authored corner", stroke.points.any { kotlin.math.abs(it.x - .5f) < .02f && kotlin.math.abs(it.y - .5f) < .02f })
    }

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

    @Test
    fun grainMovementAndZoomSurviveBrushActivationAndStrokeCommit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var committed: InkStroke? = null
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            viewportRotationDegrees = 37f
            onStrokeCommitted = { committed = it }
        }
        val brush = brush("grain-contract", "Grain contract").copy(
            grainScale = 275f,
            grainMovement = .2f,
            grainZoom = .65f,
            grainCompositeMode = app.inkloom.document.CspTextureComposite.HEIGHT,
            grainApplyPerPlot = false,
            grainFilteringMode = app.inkloom.document.ProcreateFiltering.IMPROVED,
            procreateRenderingMode = app.inkloom.document.ProcreateRenderingMode.INTENSE_BLENDING,
            procreateBrushBlendMode = app.inkloom.document.ProcreateBrushBlend.OVERLAY,
            procreateBurntEdgeBlendMode = app.inkloom.document.ProcreateBrushBlend.COLOR_BURN,
            procreateLuminanceBlending = true,
            alphaThresholdEnabled = true,
            alphaThreshold = .48f,
            procreateWetMix = app.inkloom.document.ProcreateWetMix(dilution = .12f, charge = .23f, attack = .34f, pull = .45f, grade = -.56f, blur = .67f, blurJitter = .78f, wetnessJitter = .89f, legacyMixSoftening = .31f),
            procreateTaper = ProcreateTaper(
                pressureStartLength = .2f,
                pressureEndLength = .3f,
                pressureSize = .7f,
                pressureOpacity = .4f,
                pressureTip = .6f,
                pressureResponse = .5f,
                pressureLinked = true,
            ),
            shapeTouchRotation = -.64f,
            shapeOrientToScreen = true,
            dualShapeTouchRotation = .43f,
            dualShapeOrientToScreen = true,
        )

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, 40f, 1f, Color.BLACK, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 600f)
        }

        assertEquals(.2f, requireNotNull(committed).grainMovement, .0001f)
        assertEquals(.65f, requireNotNull(committed).grainZoom, .0001f)
        assertEquals(275f, requireNotNull(committed).grainScale, .0001f)
        assertEquals(app.inkloom.document.CspTextureComposite.HEIGHT, requireNotNull(committed).grainCompositeMode)
        assertFalse(requireNotNull(committed).grainApplyPerPlot)
        assertEquals(app.inkloom.document.ProcreateFiltering.IMPROVED, requireNotNull(committed).grainFilteringMode)
        assertEquals(app.inkloom.document.ProcreateRenderingMode.INTENSE_BLENDING, requireNotNull(committed).procreateRenderingMode)
        assertEquals(app.inkloom.document.ProcreateBrushBlend.OVERLAY, requireNotNull(committed).procreateBrushBlendMode)
        assertEquals(app.inkloom.document.ProcreateBrushBlend.COLOR_BURN, requireNotNull(committed).procreateBurntEdgeBlendMode)
        assertTrue(requireNotNull(committed).procreateLuminanceBlending)
        assertTrue(requireNotNull(committed).alphaThresholdEnabled)
        assertEquals(.48f, requireNotNull(committed).alphaThreshold, .0001f)
        assertEquals(brush.procreateWetMix, requireNotNull(committed).procreateWetMix)
        assertEquals(brush.procreateTaper, requireNotNull(committed).procreateTaper)
        assertEquals(StrokeInputKind.STYLUS, requireNotNull(committed).inputKind)
        assertEquals(-.64f, requireNotNull(committed).shapeTouchRotation, .0001f)
        assertEquals(.43f, requireNotNull(committed).dualShapeTouchRotation, .0001f)
        assertEquals(-37f, requireNotNull(committed).shapeScreenRotationDegrees, .0001f)
        assertEquals(-37f, requireNotNull(committed).dualShapeScreenRotationDegrees, .0001f)
    }

    @Test
    fun pressureAndTouchTaperProgramsFollowTheActualInputTool() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val committed = mutableListOf<InkStroke>()
        val taper = ProcreateTaper(
            pressureStartLength = .2f,
            pressureSize = .7f,
            touchStartLength = .4f,
            touchOpacity = .8f,
        )
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            fingerDrawingEnabled = true
            onStrokeCommitted = committed::add
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(
                EditorTool.PEN,
                AppSettings(fingerDrawing = true),
                brush("input-taper", "Input taper").copy(procreateTaper = taper),
                40f,
                1f,
                Color.BLACK,
                EraserPreset.NORMAL_FREE,
            )
            drawStroke(view, 1L, 400f)
            // The production palm-rejection contract intentionally ignores finger contacts for
            // 700 ms after stylus input. This later gesture verifies Touch Taper after that window.
            view.onTouchEvent(fingerEvent(MotionEvent.ACTION_DOWN, 250f, 800f, 1_000L))
            view.onTouchEvent(fingerEvent(MotionEvent.ACTION_MOVE, 600f, 800f, 1_004L))
            view.onTouchEvent(fingerEvent(MotionEvent.ACTION_UP, 950f, 800f, 1_008L))
        }

        assertEquals(2, committed.size)
        assertEquals(listOf(StrokeInputKind.STYLUS, StrokeInputKind.TOUCH), committed.map(InkStroke::inputKind))
        assertEquals(listOf(taper, taper), committed.map(InkStroke::procreateTaper))
    }

    @Test
    fun primarySecondaryColorsAndColorProgramsSurviveActivationAndCommit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var committed: InkStroke? = null
        val dynamics = app.inkloom.document.ProcreateColorDynamics(stampSecondary = .4f, pressureHue = .2f, tiltSecondary = .3f)
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            onStrokeCommitted = { committed = it }
        }
        val brush = brush("color-contract", "Color contract").copy(
            procreateColorDynamics = dynamics,
            colorJitterPerTipEnabled = false,
            colorJitterTarget = app.inkloom.document.CspColorChangeTarget.SUB,
            colorJitterSubColorMix = .35f,
            colorJitterSubColorMixDynamics = app.inkloom.document.BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .2f),
            colorJitterPerStrokeEnabled = true,
            colorJitterStrokeHueDegrees = 45f,
            colorJitterStrokeSaturation = 25f,
            colorJitterStrokeValue = 15f,
            colorJitterStrokeSubColorMix = .4f,
        )

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, 40f, 1f, Color.RED, EraserPreset.NORMAL_FREE, Color.BLUE)
            drawStroke(view, 1L, 600f)
        }

        assertEquals(Color.RED.toUInt().toLong(), requireNotNull(committed).colorArgb)
        assertEquals(Color.BLUE.toUInt().toLong(), requireNotNull(committed).secondaryColorArgb)
        assertEquals(dynamics, requireNotNull(committed).procreateColorDynamics)
        assertEquals(false, requireNotNull(committed).colorJitterPerTipEnabled)
        assertEquals(app.inkloom.document.CspColorChangeTarget.SUB, requireNotNull(committed).colorJitterTarget)
        assertEquals(.35f, requireNotNull(committed).colorJitterSubColorMix, .0001f)
        assertTrue(requireNotNull(committed).colorJitterSubColorMixDynamics.pressureEnabled)
        assertTrue(requireNotNull(committed).colorJitterPerStrokeEnabled)
        assertEquals(45f, requireNotNull(committed).colorJitterStrokeHueDegrees, .0001f)
        assertEquals(.4f, requireNotNull(committed).colorJitterStrokeSubColorMix, .0001f)
    }

    @Test
    fun cspColorMixProgramSurvivesActivationAndStrokeCommit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var committed: InkStroke? = null
        val blurDynamics = app.inkloom.document.BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .27f)
        val brush = brush("csp-running-color", "Running color").copy(
            watercolor = true,
            cspColorMixMode = app.inkloom.document.CspColorMixMode.RUNNING_COLOR,
            colorMix = .42f,
            paintMix = .63f,
            colorStretch = .71f,
            cspPigmentMixingMode = app.inkloom.document.CspPigmentMixingMode.PERCEPTUAL,
            cspBrightnessCorrection = 5,
            colorMixBlur = 18f,
            colorMixBlurUnit = 2,
            colorMixBlurAutomatic = false,
            colorMixBlurDynamics = blurDynamics,
        )
        val view = InkCanvasView(instrumentation.targetContext).apply {
            layout(0, 0, 1200, 1200)
            onStrokeCommitted = { committed = it }
        }

        instrumentation.runOnMainSync {
            view.applyBrushConfiguration(EditorTool.PEN, AppSettings(), brush, 40f, 1f, Color.RED, EraserPreset.NORMAL_FREE)
            drawStroke(view, 1L, 600f)
        }

        val stroke = requireNotNull(committed)
        assertTrue(stroke.watercolor)
        assertEquals(brush.cspColorMixMode, stroke.cspColorMixMode)
        assertEquals(.42f, stroke.colorMix, .0001f)
        assertEquals(.63f, stroke.paintMix, .0001f)
        assertEquals(.71f, stroke.colorStretch, .0001f)
        assertEquals(brush.cspPigmentMixingMode, stroke.cspPigmentMixingMode)
        assertEquals(5, stroke.cspBrightnessCorrection)
        assertEquals(18f, stroke.colorMixBlur, .0001f)
        assertEquals(2, stroke.colorMixBlurUnit)
        assertFalse(stroke.colorMixBlurAutomatic)
        assertEquals(blurDynamics, stroke.colorMixBlurDynamics)
    }

    private fun brush(id: String, name: String) = ImportedBrush(
        id = id,
        name = name,
        format = BrushFormat.IMAGE,
        compatibility = Compatibility.COMPLETE,
        sizeBytes = 0L,
        report = emptyList(),
    )

    private class RecordingActiveSurface : EngineV2ActiveStrokeSurface {
        val rendered = mutableListOf<List<InkStroke>>()
        val committed = mutableListOf<List<InkStroke>>()
        override fun render(strokes: List<InkStroke>) { rendered += strokes.map { it.copy(points = it.points.toList()) } }
        override fun commit(strokes: List<InkStroke>) { committed += strokes.map { it.copy(points = it.points.toList()) } }
        override fun cancel() = Unit
    }

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
