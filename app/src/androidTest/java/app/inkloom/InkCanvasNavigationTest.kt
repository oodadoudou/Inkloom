package app.inkloom

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.document.ViewportGeometry
import app.inkloom.document.ViewportState
import app.inkloom.document.ViewportTransform
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutActivation
import app.inkloom.settings.ShortcutBinding
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.ReferenceTarget
import app.inkloom.ui.InkCanvasView
import app.inkloom.engine.v5.surface.PixelTileSurface
import java.util.IdentityHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasNavigationTest {
    private val paintSurfaces = IdentityHashMap<InkCanvasView, PixelTileSurface>()
    @Test fun oneStationaryTwoFingerTapTriggersOneUndo() {
        val view = canvas().apply {
            fingerDrawingEnabled = false
            twoFingerDoubleTapUndo = true
        }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_020L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 300f, 300f), point(1, 500f, 300f))
        // Real digitizers commonly emit a stationary MOVE packet before the fingers lift.
        send(view, 1_060L, MotionEvent.ACTION_MOVE, point(0, 302f, 301f), point(1, 502f, 301f))
        send(view, 1_100L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 302f, 301f), point(1, 502f, 301f))
        send(view, 1_120L, MotionEvent.ACTION_UP, point(0, 302f, 301f))

        assertEquals(1, undoCount)
    }

    @Test fun pinchDoesNotTriggerTwoFingerUndo() {
        val view = canvas().apply { twoFingerDoubleTapUndo = true }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_020L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 300f, 300f), point(1, 500f, 300f))
        send(view, 1_060L, MotionEvent.ACTION_MOVE, point(0, 240f, 300f), point(1, 560f, 300f))
        send(view, 1_100L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 240f, 300f), point(1, 560f, 300f))

        assertEquals(0, undoCount)
    }

    @Test fun smallTwoFingerPanAndLateSecondContactDoNotUndo() {
        val view=canvas().apply { twoFingerDoubleTapUndo=true }
        var undoCount=0
        view.onUndoGesture={undoCount++}
        send(view,1_000L,MotionEvent.ACTION_DOWN,point(0,300f,300f))
        send(view,1_020L,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),point(0,300f,300f),point(1,500f,300f))
        send(view,1_060L,MotionEvent.ACTION_MOVE,point(0,314f,300f),point(1,514f,300f))
        send(view,1_100L,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),point(0,314f,300f),point(1,514f,300f))
        send(view,2_000L,MotionEvent.ACTION_DOWN,point(0,300f,300f))
        send(view,2_180L,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),point(0,300f,300f),point(1,500f,300f))
        send(view,2_200L,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),point(0,300f,300f),point(1,500f,300f))
        assertEquals(0,undoCount)
    }

    @Test fun partiallyObscuredStylusEventsRemainDrawable() {
        val view = canvas().apply { tool = EditorTool.PEN }
        obscuredStylusEvent(MotionEvent.ACTION_DOWN, 120f, 160f).also { event ->
            try { assertTrue(view.onFilterTouchEventForSecurity(event)) } finally { event.recycle() }
        }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, flags = MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f, flags = MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f, flags = MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)

        assertEquals(1L, paintSurface(view).revision)
    }

    @Test fun floatingWindowFocusLossDoesNotCancelDeliveredStylusStream() {
        val view = canvas().apply { tool = EditorTool.PEN }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onWindowFocusChanged(false) }
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals(1L, paintSurface(view).revision)
    }
    @Test fun enabledSingleFingerDoubleTapOnBlankCanvasTriggersUndoOnce() {
        val view = canvas().apply {
            fingerDrawingEnabled = false
            singleFingerDoubleTapUndo = true
            twoFingerDoubleTapUndo = false
        }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_080L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_220L, MotionEvent.ACTION_DOWN, point(0, 304f, 302f))
        send(view, 1_300L, MotionEvent.ACTION_UP, point(0, 304f, 302f))

        assertEquals(1, undoCount)
    }

    @Test fun singleFingerDoubleTapUndoIsDisabledByDefault() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_080L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_220L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_300L, MotionEvent.ACTION_UP, point(0, 300f, 300f))

        assertEquals(0, undoCount)
    }

    @Test fun dragAndSecondPointerCancelSingleFingerDoubleTapUndo() {
        val view = canvas().apply {
            fingerDrawingEnabled = false
            singleFingerDoubleTapUndo = true
            twoFingerDoubleTapUndo = false
        }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_080L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_180L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_220L, MotionEvent.ACTION_MOVE, point(0, 360f, 300f))
        send(view, 1_260L, MotionEvent.ACTION_UP, point(0, 360f, 300f))

        send(view, 1_400L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_440L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        send(view, 1_540L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_560L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 300f, 300f), point(1, 500f, 300f))
        send(view, 1_600L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 300f, 300f), point(1, 500f, 300f))
        send(view, 1_620L, MotionEvent.ACTION_UP, point(0, 300f, 300f))

        assertEquals(0, undoCount)
    }

    @Test fun twoFingerTranslationPansWithoutChangingSpan() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        var panX = 0f
        var panY = 0f
        view.onViewportPan = { dx, dy -> panX += dx; panY += dy }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 300f, 100f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 140f, 125f), point(1, 340f, 125f))
        send(view, 50L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 140f, 125f), point(1, 340f, 125f))
        send(view, 60L, MotionEvent.ACTION_UP, point(0, 140f, 125f))

        assertTrue("Two-finger translation should pan horizontally", panX >= 39f)
        assertTrue("Two-finger translation should pan vertically", panY >= 24f)
    }

    @Test fun pinchPanAndMinimumZoomRemainFiniteRecoverableAndInkFree() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        val geometry = ViewportGeometry(1000f, 1000f, 700f, 900f)
        var state = ViewportState(1f, 0f, 0f)
        view.onViewportScale = { factor, focusX, focusY ->
            state = ViewportTransform.zoomAroundScreen(state, factor, focusX - 500f, focusY - 500f, geometry = geometry)
        }
        view.onViewportPan = { dx, dy -> state = ViewportTransform.panByScreen(state, dx, dy, geometry = geometry) }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 400f, 500f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 350f, 500f), point(1, 650f, 500f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 250f, 470f), point(1, 750f, 470f))
        send(view, 50L, MotionEvent.ACTION_MOVE, point(0, 210f, 520f), point(1, 790f, 520f))

        assertTrue("Pinch must enlarge the page; state=$state", state.scale > 1.05f)
        assertTrue(state.scale.isFinite() && state.panX.isFinite() && state.panY.isFinite())

        send(view, 70L, MotionEvent.ACTION_MOVE, point(0, 300f, 500f), point(1, 700f, 500f))
        send(view, 90L, MotionEvent.ACTION_MOVE, point(0, 380f, 500f), point(1, 620f, 500f))
        send(view, 110L, MotionEvent.ACTION_MOVE, point(0, 430f, 500f), point(1, 570f, 500f))
        send(view, 130L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 430f, 500f), point(1, 570f, 500f))
        send(view, 140L, MotionEvent.ACTION_MOVE, point(0, 470f, 530f))
        send(view, 150L, MotionEvent.ACTION_UP, point(0, 470f, 530f))

        assertTrue(state.scale.isFinite() && state.panX.isFinite() && state.panY.isFinite())
        state = ViewportTransform.zoomAroundScreen(state, .01f, -320f, 180f, geometry = geometry)
        assertEquals(ViewportTransform.MINIMUM_SCALE, state.scale, .001f)
        assertTrue(state.panX.isFinite() && state.panY.isFinite())
        view.viewportScale = state.scale
        assertEquals(state.scale, view.viewportScale, .001f)
        assertEquals("Navigation sequence must never commit ink", 0L, paintSurface(view).revision)
    }

    @Test fun twoFingerScreenTranslationIsNotMultipliedAtHighZoom() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        var state = ViewportState(scale = 4f, panX = 10f, panY = -5f)
        view.onViewportScale = { factor, focusX, focusY ->
            state = ViewportTransform.zoomAroundScreen(state, factor, focusX - 500f, focusY - 500f)
        }
        view.onViewportPan = { dx, dy -> state = ViewportTransform.panByScreen(state, dx, dy) }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 350f, 500f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 350f, 500f), point(1, 650f, 500f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 390f, 525f), point(1, 690f, 525f))
        send(view, 50L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 390f, 525f), point(1, 690f, 525f))
        send(view, 60L, MotionEvent.ACTION_UP, point(0, 390f, 525f))

        assertEquals(4f, state.scale, .001f)
        assertEquals("A 40 px finger move must pan exactly 40 px at 4x zoom", 50f, state.panX, .001f)
        assertEquals("A 25 px finger move must pan exactly 25 px at 4x zoom", 20f, state.panY, .001f)
    }

    @Test fun directStylusContactAlreadyUsesPageLocalCoordinates() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            viewportScale = 2f
            viewportPanX = 100f
            viewportPanY = -40f
        }
        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 700f, 660f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 720f, 680f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 720f, 680f)

        val first = view.v5LastCommittedNormalizedPoints().first()
        assertEquals("View dispatch already maps the contact into page-local X", .70f, first.first, .002f)
        assertEquals("View dispatch already maps the contact into page-local Y", .66f, first.second, .002f)
    }

    @Test fun stylusWritesAfterAnExternalPinchWasInterrupted() {
        val state = ViewportState(scale = 2.4f, panX = 130f, panY = -90f, rotationDegrees = 17f)
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = false
            externalViewportGestures = true
            viewportScale = state.scale
            viewportPanX = state.panX
            viewportPanY = state.panY
            viewportRotationDegrees = state.rotationDegrees
        }

        // This intentionally omits the final finger UP. Compose may consume it while owning
        // the gesture, so the following stylus DOWN is the recovery boundary we must honour.
        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 300f, 500f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 300f, 500f), point(1, 700f, 500f))

        val targetX = .61f
        val targetY = .37f
        sendStylus(view, 40L, MotionEvent.ACTION_DOWN, targetX * 1000f, targetY * 1000f)
        sendStylus(view, 60L, MotionEvent.ACTION_MOVE, targetX * 1000f + 9f, targetY * 1000f + 7f)
        sendStylus(view, 80L, MotionEvent.ACTION_UP, targetX * 1000f + 9f, targetY * 1000f + 7f)

        val strokePoint = view.v5LastCommittedNormalizedPoints().first()
        assertEquals("A stylus DOWN after pinch must begin a stroke", targetX, strokePoint.first, .003f)
        assertEquals("The recovered stream must preserve page-local coordinates", targetY, strokePoint.second, .003f)
    }

    @Test fun nativePinchCompletesAndTheNextStylusStrokeUsesTheNewViewport() {
        val geometry = ViewportGeometry(1000f, 1000f, 1000f, 1000f)
        var state = ViewportState(scale = 1f, panX = 0f, panY = 0f)
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = false
            externalViewportGestures = false
        }
        fun synchronize(next: ViewportState) {
            state = next
            view.viewportScale = next.scale
            view.viewportPanX = next.panX
            view.viewportPanY = next.panY
            view.viewportRotationDegrees = next.rotationDegrees
        }
        view.onViewportScale = { factor, focusX, focusY ->
            synchronize(ViewportTransform.zoomAroundScreen(state, factor, focusX - 500f, focusY - 500f, geometry = geometry))
        }
        view.onViewportPan = { dx, dy -> synchronize(ViewportTransform.panByScreen(state, dx, dy, geometry = geometry)) }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 350f, 500f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 350f, 500f), point(1, 650f, 500f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 250f, 520f), point(1, 750f, 520f))
        send(view, 50L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 250f, 520f), point(1, 750f, 520f))
        send(view, 60L, MotionEvent.ACTION_UP, point(0, 250f, 520f))

        assertTrue("Pinch must update the viewport before stylus input", state.scale > 1.2f)
        val targetX = .52f
        val targetY = .48f
        sendStylus(view, 80L, MotionEvent.ACTION_DOWN, targetX * 1000f, targetY * 1000f)
        sendStylus(view, 100L, MotionEvent.ACTION_MOVE, targetX * 1000f + 6f, targetY * 1000f + 4f)
        sendStylus(view, 120L, MotionEvent.ACTION_UP, targetX * 1000f + 6f, targetY * 1000f + 4f)

        val strokePoint = view.v5LastCommittedNormalizedPoints().first()
        assertEquals("Stylus must write immediately after pinch", targetX, strokePoint.first, .003f)
        assertEquals("The post-pinch local stroke must remain writable", targetY, strokePoint.second, .003f)
    }

    @Test fun secondFingerCancelsAnInProgressFingerStrokeBeforeNavigation() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = true
        }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_MOVE, point(0, 150f, 150f))
        send(view, 20L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 150f, 150f), point(1, 300f, 150f))
        send(view, 40L, MotionEvent.ACTION_MOVE, point(0, 180f, 170f), point(1, 330f, 170f))
        send(view, 60L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 180f, 170f), point(1, 330f, 170f))
        send(view, 70L, MotionEvent.ACTION_UP, point(0, 180f, 170f))

        assertEquals("The interrupted finger stroke must not leave ink", 0L, paintSurface(view).revision)
    }

    @Test fun remainingFingerAfterPinchStaysNavigationUntilEveryFingerIsUp() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = true
        }
        var panX = 0f
        var panY = 0f
        view.onViewportPan = { dx, dy -> panX += dx; panY += dy }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_MOVE, point(0, 130f, 100f))
        send(view, 20L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 130f, 100f), point(1, 330f, 100f))
        send(view, 40L, MotionEvent.ACTION_MOVE, point(0, 110f, 110f), point(1, 370f, 110f))
        send(view, 60L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 110f, 110f), point(1, 370f, 110f))
        send(view, 80L, MotionEvent.ACTION_MOVE, point(0, 180f, 150f))
        send(view, 100L, MotionEvent.ACTION_UP, point(0, 180f, 150f))

        assertEquals("Neither the interrupted stroke nor the remaining navigation finger may leave ink", 0L, paintSurface(view).revision)
        assertTrue("The remaining finger should continue panning after POINTER_UP", panX >= 79f)
        assertTrue("The remaining finger should continue panning vertically", panY >= 49f)

        send(view, 120L, MotionEvent.ACTION_DOWN, point(0, 240f, 260f))
        send(view, 140L, MotionEvent.ACTION_MOVE, point(0, 300f, 320f))
        send(view, 160L, MotionEvent.ACTION_UP, point(0, 300f, 320f))
        assertEquals("A fresh finger stream must write immediately after the completed pinch", 1L, paintSurface(view).revision)
    }

    @Test fun twoFingerNavigationOverridesAnActiveAlignmentGuideDrag() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = true
            canvasGuideState = app.inkloom.document.CanvasGuideState(alignmentGuidesEnabled = true, verticalGuideX = .5f, horizontalGuideY = .5f)
        }
        var scale = 1f
        view.onViewportScale = { factor, _, _ -> scale *= factor }

        // The first finger intentionally lands on the vertical guide handle.
        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 500f, 200f))
        send(view, 20L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 500f, 200f), point(1, 700f, 200f))
        send(view, 40L, MotionEvent.ACTION_MOVE, point(0, 400f, 220f), point(1, 800f, 220f))
        send(view, 60L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 400f, 220f), point(1, 800f, 220f))
        send(view, 80L, MotionEvent.ACTION_UP, point(0, 400f, 220f))

        assertTrue("A guide hit must never block a later two-finger pinch", scale > 1.9f)
        assertEquals(0L, paintSurface(view).revision)
        assertEquals(.5f, view.canvasGuideState.verticalGuideX, .001f)
    }

    @Test fun rapidPinchSamplesAccumulateFromTheLatestViewportWithoutRecomposition() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        fun synchronize(next: ViewportState) {
            view.viewportScale = next.scale
            view.viewportPanX = next.panX
            view.viewportPanY = next.panY
            view.viewportRotationDegrees = next.rotationDegrees
        }
        view.onViewportScale = { factor, focusX, focusY ->
            val current = ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees)
            synchronize(ViewportTransform.zoomAroundScreen(current, factor, focusX - 500f, focusY - 500f))
        }
        view.onViewportPan = { dx, dy ->
            val current = ViewportState(view.viewportScale, view.viewportPanX, view.viewportPanY, view.viewportRotationDegrees)
            synchronize(ViewportTransform.panByScreen(current, dx, dy))
        }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 400f, 500f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 400f, 500f), point(1, 600f, 500f))
        send(view, 20L, MotionEvent.ACTION_MOVE, point(0, 350f, 500f), point(1, 650f, 500f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 275f, 500f), point(1, 725f, 500f))
        send(view, 40L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 275f, 500f), point(1, 725f, 500f))
        send(view, 50L, MotionEvent.ACTION_UP, point(0, 275f, 500f))

        assertEquals("200px → 300px → 450px must accumulate to exactly 2.25x", 2.25f, view.viewportScale, .001f)
    }

    @Test fun optionalTwoFingerRotationUsesAStableAnchorWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = false
            twoFingerRotationEnabled = true
        }
        var rotation = 0f
        view.onViewportRotate = { rotation += it }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 300f, 100f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 200f, 0f), point(1, 200f, 200f))
        send(view, 50L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 200f, 0f), point(1, 200f, 200f))
        send(view, 60L, MotionEvent.ACTION_MOVE, point(0, 230f, 30f))
        send(view, 70L, MotionEvent.ACTION_UP, point(0, 230f, 30f))

        assertEquals("Navigation gestures must never leave ink", 0L, paintSurface(view).revision)
        assertEquals(90f, rotation, .001f)
    }

    @Test fun twoFingerRotationDoesNothingWhenTheSettingIsOff() {
        val view = canvas().apply { twoFingerRotationEnabled = false }
        var rotation = 0f
        view.onViewportRotate = { rotation += it }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 10L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 300f, 100f))
        send(view, 30L, MotionEvent.ACTION_MOVE, point(0, 200f, 0f), point(1, 200f, 200f))

        assertEquals(0f, rotation, .001f)
    }

    @Test fun threeFingerTapTriggersRedoWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            fingerDrawingEnabled = false
            threeFingerTapRedo = true
        }
        var redoCount = 0
        view.onRedoGesture = { redoCount++ }

        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 20L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f))
        send(view, 40L, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f), point(2, 160f, 210f))
        send(view, 100L, MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f), point(2, 160f, 210f))
        send(view, 120L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), point(0, 100f, 100f), point(1, 220f, 100f))
        send(view, 140L, MotionEvent.ACTION_UP, point(0, 100f, 100f))

        assertEquals(1, redoCount)
        assertEquals("Three-finger navigation must never leave ink", 0L, paintSurface(view).revision)
    }

    @Test fun threeFingerTapWithDigitizerJitterStillRedoes() {
        val view = canvas().apply { fingerDrawingEnabled = false; threeFingerTapRedo = true }
        var redoCount = 0
        view.onRedoGesture = { redoCount++ }
        send(view, 0L, MotionEvent.ACTION_DOWN, point(0, 100f, 100f))
        send(view, 20L, pointerDown(1), point(0, 100f, 100f), point(1, 220f, 100f))
        send(view, 40L, pointerDown(2), point(0, 100f, 100f), point(1, 220f, 100f), point(2, 160f, 210f))
        send(view, 70L, MotionEvent.ACTION_MOVE, point(0, 101f, 101f), point(1, 221f, 100f), point(2, 160f, 211f))
        send(view, 100L, pointerUp(2), point(0, 101f, 101f), point(1, 221f, 100f), point(2, 160f, 211f))
        send(view, 120L, pointerUp(1), point(0, 101f, 101f), point(1, 221f, 100f))
        send(view, 140L, MotionEvent.ACTION_UP, point(0, 101f, 101f))
        assertEquals(1, redoCount)
    }

    @Test fun fourFingerTapTogglesFullScreenWithoutRedoOrUndo() {
        val view = canvas().apply { fingerDrawingEnabled = false }
        val actions = mutableListOf<ShortcutAction>()
        var undoOrRedo = 0
        view.onShortcutAction = { actions += it.action }
        view.onUndoGesture = { undoOrRedo++ }; view.onRedoGesture = { undoOrRedo++ }
        val fingers = arrayOf(point(0, 100f, 100f), point(1, 220f, 100f), point(2, 340f, 110f), point(3, 460f, 140f))
        send(view, 0L, MotionEvent.ACTION_DOWN, fingers[0])
        for (count in 2..4) send(view, 10L * count, pointerDown(count - 1), *fingers.copyOfRange(0, count))
        send(view, 120L, pointerUp(3), *fingers)
        send(view, 130L, pointerUp(2), *fingers.copyOfRange(0, 3))
        send(view, 140L, pointerUp(1), *fingers.copyOfRange(0, 2))
        send(view, 150L, MotionEvent.ACTION_UP, fingers[0])
        assertEquals(listOf(ShortcutAction.TOGGLE_PANELS), actions)
        assertEquals(0, undoOrRedo)
    }

    @Test fun aQuickPinchFitsThePageAndASlowPinchOnlyZooms() {
        val view = canvas().apply { fingerDrawingEnabled = false; twoFingerRotationEnabled = false }
        val actions = mutableListOf<ShortcutAction>()
        view.onShortcutAction = { actions += it.action }
        fun pinch(start: Long, duration: Long) {
            send(view, start, MotionEvent.ACTION_DOWN, point(0, 300f, 500f))
            send(view, start + 10L, pointerDown(1), point(0, 300f, 500f), point(1, 700f, 500f))
            send(view, start + duration / 2, MotionEvent.ACTION_MOVE, point(0, 380f, 500f), point(1, 620f, 500f))
            send(view, start + duration, MotionEvent.ACTION_MOVE, point(0, 450f, 500f), point(1, 550f, 500f))
            send(view, start + duration + 10L, pointerUp(1), point(0, 450f, 500f), point(1, 550f, 500f))
            send(view, start + duration + 20L, MotionEvent.ACTION_UP, point(0, 450f, 500f))
        }
        pinch(1_000L, 150L)
        assertEquals(listOf(ShortcutAction.FIT_PAGE), actions)
        pinch(5_000L, 700L)
        assertEquals("a slow pinch is ordinary zooming", listOf(ShortcutAction.FIT_PAGE), actions)
    }

    @Test fun threeFingerScrubClearsTheLayerAsOneUndoStep() {
        val view = canvas().apply { tool = EditorTool.PEN; fingerDrawingEnabled = false }
        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 100f, 300f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 400f, 300f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 600f, 300f)
        assertTrue(paintSurface(view).tileCount > 0)
        val y = arrayOf(500f, 520f, 640f)
        fun at(dx: Float) = arrayOf(point(0, 300f + dx, y[0]), point(1, 420f + dx, y[1]), point(2, 360f + dx, y[2]))
        send(view, 5_000L, MotionEvent.ACTION_DOWN, at(0f)[0])
        send(view, 5_010L, pointerDown(1), *at(0f).copyOfRange(0, 2))
        send(view, 5_020L, pointerDown(2), *at(0f))
        listOf(150f, 0f, 150f, 0f, 150f).forEachIndexed { index, dx -> send(view, 5_080L + index * 60L, MotionEvent.ACTION_MOVE, *at(dx)) }
        send(view, 5_500L, pointerUp(2), *at(150f))
        send(view, 5_510L, pointerUp(1), *at(150f).copyOfRange(0, 2))
        send(view, 5_520L, MotionEvent.ACTION_UP, at(150f)[0])
        settlePublishedStroke(view)
        assertEquals(0, paintSurface(view).tileCount)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.undo() }
        settlePublishedStroke(view)
        assertTrue("undo brings the layer back", paintSurface(view).tileCount > 0)
    }

    @Test fun touchAndHoldSamplesWithARingAndNeverPans() {
        val view = canvas().apply { tool = EditorTool.PEN; fingerDrawingEnabled = false }
        repeat(6) { row ->
            sendStylus(view, row * 60L, MotionEvent.ACTION_DOWN, 100f, 296f + row * 2f)
            sendStylus(view, row * 60L + 20L, MotionEvent.ACTION_MOVE, 400f, 296f + row * 2f)
            sendStylus(view, row * 60L + 40L, MotionEvent.ACTION_UP, 600f, 296f + row * 2f)
        }
        val sampled = mutableListOf<Int>()
        var panned = 0f
        view.onColorSampled = { sampled += it }
        view.onViewportPan = { dx, dy -> panned += dx + dy }
        send(view, 5_000L, MotionEvent.ACTION_DOWN, point(0, 350f, 300f))
        send(view, 5_050L, MotionEvent.ACTION_MOVE, point(0, 352f, 301f))
        Thread.sleep(700L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
        assertTrue("holding still picks a colour", sampled.isNotEmpty())
        assertTrue("the ring shows while held", view.eyedropperRingColors() != null)
        send(view, 5_800L, MotionEvent.ACTION_MOVE, point(0, 450f, 301f))
        send(view, 5_900L, MotionEvent.ACTION_UP, point(0, 450f, 301f))
        assertEquals(null, view.eyedropperRingColors())
        assertEquals("sampling never moves the canvas", 0f, panned, .001f)
    }

    @Test fun holdingTwoFingersKeepsUndoingUntilTheyLift() {
        val view = canvas().apply { fingerDrawingEnabled = false; twoFingerDoubleTapUndo = true }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }
        send(view, 1_000L, MotionEvent.ACTION_DOWN, point(0, 300f, 300f))
        send(view, 1_020L, pointerDown(1), point(0, 300f, 300f), point(1, 500f, 300f))
        Thread.sleep(1_300L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
        send(view, 2_400L, pointerUp(1), point(0, 300f, 300f), point(1, 500f, 300f))
        send(view, 2_410L, MotionEvent.ACTION_UP, point(0, 300f, 300f))
        val held = undoCount
        assertTrue("rapid undo repeats, got $held", held >= 3)
        Thread.sleep(400L)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
        assertEquals("lifting stops it", held, undoCount)
    }

    @Test fun deleteWithNothingSelectedClearsTheLayer() {
        val view = canvas().apply { tool = EditorTool.PEN }
        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 100f, 300f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 400f, 300f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 600f, 300f)
        assertTrue(paintSurface(view).tileCount > 0)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.deleteSelection() }
        settlePublishedStroke(view)
        assertEquals(0, paintSurface(view).tileCount)
    }

    @Test fun holdShortcutEmitsPressAndReleaseForTemporaryToolSwitching() {
        val view = canvas().apply {
            shortcutBindings = listOf(ShortcutBinding(ShortcutAction.TOGGLE_ERASER, ShortcutChord(KeyEvent.KEYCODE_E), ShortcutTrigger.HOLD))
        }
        val phases = mutableListOf<ShortcutPhase>()
        view.onShortcutAction = { phases += it.phase }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.onKeyDown(KeyEvent.KEYCODE_E, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E))
            view.onKeyUp(KeyEvent.KEYCODE_E, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_E))
        }

        assertEquals(listOf(ShortcutPhase.PRESS, ShortcutPhase.RELEASE), phases)
    }

    @Test fun stylusUndoButtonFiresOncePerPressAndNeverCommitsInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.UNDO
        }
        var undoCount = 0
        view.onUndoGesture = { undoCount++ }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals(1, undoCount)
        assertEquals("A stylus command button must never leave ink", 0L, paintSurface(view).revision)
    }

    @Test fun heldStylusEraserButtonEmitsOnePressAndOneReleaseWithoutInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.ERASER
            primaryButtonTrigger = ShortcutTrigger.HOLD
        }
        val activations = mutableListOf<ShortcutActivation>()
        view.onShortcutAction = activations::add

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals(listOf(ShortcutPhase.PRESS, ShortcutPhase.RELEASE), activations.map(ShortcutActivation::phase))
        assertTrue(activations.all { it.action == ShortcutAction.TOGGLE_ERASER && it.trigger == ShortcutTrigger.HOLD })
        assertEquals(0L, paintSurface(view).revision)
    }

    @Test fun tapStylusButtonSwitchesOnceAndNeverEmitsRelease() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            secondaryButtonAction = StylusButtonAction.LASSO
            secondaryButtonTrigger = ShortcutTrigger.PRESS
        }
        val activations = mutableListOf<ShortcutActivation>()
        view.onShortcutAction = activations::add

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_SECONDARY)
        sendStylus(view, 20L, MotionEvent.ACTION_UP, 120f, 160f)

        assertEquals(listOf(ShortcutActivation(ShortcutAction.SELECT_TOOL, ShortcutPhase.PRESS, ShortcutTrigger.PRESS)), activations)
    }

    @Test fun pressingEraserButtonMidStrokeCancelsPartialInkBeforeSwitching() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.ERASER
            primaryButtonTrigger = ShortcutTrigger.HOLD
        }
        val activations = mutableListOf<ShortcutActivation>()
        view.onShortcutAction = activations::add

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 40L, MotionEvent.ACTION_CANCEL, 200f, 230f, MotionEvent.BUTTON_STYLUS_PRIMARY)

        assertEquals(0L, paintSurface(view).revision)
        assertEquals(listOf(ShortcutPhase.PRESS, ShortcutPhase.RELEASE), activations.map(ShortcutActivation::phase))
    }

    @Test fun focusLossKeepsHeldStylusToolUntilTheDeliveredRelease() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.EYEDROPPER
            primaryButtonTrigger = ShortcutTrigger.HOLD
        }
        val activations = mutableListOf<ShortcutActivation>()
        view.onShortcutAction = activations::add

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onWindowFocusChanged(false) }
        assertEquals(listOf(ShortcutPhase.PRESS), activations.map(ShortcutActivation::phase))
        sendStylus(view, 20L, MotionEvent.ACTION_UP, 120f, 160f)

        assertEquals(listOf(ShortcutPhase.PRESS, ShortcutPhase.RELEASE), activations.map(ShortcutActivation::phase))
        assertEquals(0L, paintSurface(view).revision)
    }

    @Test fun secondaryStylusButtonCanTriggerRedoOnce() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            secondaryButtonAction = StylusButtonAction.REDO
        }
        var redoCount = 0
        view.onRedoGesture = { redoCount++ }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_SECONDARY)
        sendStylus(view, 20L, MotionEvent.ACTION_UP, 120f, 160f)

        assertEquals(1, redoCount)
    }

    @Test fun stylusHandButtonTemporarilyPansAndNeverCommitsInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.HAND
        }
        var panX = 0f
        view.onViewportPan = { dx, _ -> panX += dx }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 220f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 220f, 160f)

        assertTrue("The held stylus Hand button must pan", panX >= 99f)
        assertEquals("Stylus Hand navigation must never leave ink", 0L, paintSurface(view).revision)
    }

    @Test fun pressingStylusHandButtonMidContactReanchorsWithoutJumping() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            primaryButtonAction = StylusButtonAction.HAND
        }
        var panX = 0f
        view.onViewportPan = { dx, _ -> panX += dx }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 40L, MotionEvent.ACTION_MOVE, 240f, 160f, MotionEvent.BUTTON_STYLUS_PRIMARY)
        sendStylus(view, 60L, MotionEvent.ACTION_UP, 240f, 160f)

        assertEquals("The first Hand-button event must establish a new anchor", 40f, panX, .001f)
        assertEquals("Switching to Hand mid-contact must cancel the partial stroke", 0L, paintSurface(view).revision)
    }

    @Test fun forcedHandNavigationPansWithAStylusWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            forceNavigation = true
        }
        var panX = 0f
        var panY = 0f
        view.onViewportPan = { dx, dy -> panX += dx; panY += dy }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals("Hand navigation must never leave stylus ink", 0L, paintSurface(view).revision)
        assertTrue("Stylus hand navigation should pan horizontally", panX >= 79f)
        assertTrue("Stylus hand navigation should pan vertically", panY >= 69f)
    }

    @Test fun eyedropperSamplesTheRenderedPageWithoutCommittingInk() {
        val sampledRed = android.graphics.Color.rgb(201, 32, 44)
        val view = canvas().apply {
            tool = EditorTool.EYEDROPPER
            pagePaperColor = sampledRed
        }
        var sampled: Int? = null
        view.onColorSampled = { sampled = it }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_UP, 120f, 160f)

        assertEquals(sampledRed, sampled)
        assertEquals("Eyedropper input must never leave ink", 0L, paintSurface(view).revision)
    }

    @Test fun eyedropperSampleSourceAndPaperSwitchChangeTheActualPixel() {
        val view = canvas().apply {
            tool = EditorTool.EYEDROPPER
            pagePaperColor = android.graphics.Color.WHITE
        }
        val reference = PixelTileSurface(1000, 1000)
        paintSurface(view).beginWrite("sample active").use { write ->
            for (y in 100..140) for (x in 100..140) write.setPixel(x, y, 0xFFFF0000.toInt())
            for (y in 300..340) for (x in 300..340) write.setPixel(x, y, 0x80800000.toInt())
            write.commit().close()
        }
        reference.beginWrite("sample reference").use { write ->
            for (y in 200..240) for (x in 200..240) write.setPixel(x, y, 0xFF0000FF.toInt())
            write.commit().close()
        }
        view.setRasterLayerSurfaces(mapOf("paint" to paintSurface(view), "reference" to reference))
        view.layerOrder = listOf("paint", "reference")
        view.referenceLayerIds = setOf("reference")
        var sampled: Int? = null
        view.onColorSampled = { sampled = it }
        fun pick(x: Float, y: Float): Int? {
            sampled = null
            sendStylus(view, 0L, MotionEvent.ACTION_DOWN, x, y)
            sendStylus(view, 20L, MotionEvent.ACTION_UP, x, y)
            return sampled
        }

        view.eyedropperReferenceTarget = ReferenceTarget.ALL_VISIBLE
        assertEquals(android.graphics.Color.WHITE, pick(500f, 500f))
        view.eyedropperIncludePaper = false
        assertEquals(null, pick(500f, 500f))
        assertEquals(0xFF0000FF.toInt(), pick(220f, 220f))

        view.eyedropperReferenceTarget = ReferenceTarget.ACTIVE_LAYER
        assertEquals(0xFFFF0000.toInt(), pick(120f, 120f))
        assertEquals(null, pick(220f, 220f))
        val translucent = requireNotNull(pick(320f, 320f))
        assertEquals(128, translucent ushr 24)
        assertTrue(android.graphics.Color.red(translucent) > 240)

        view.eyedropperReferenceTarget = ReferenceTarget.REFERENCE_LAYERS
        assertEquals(0xFF0000FF.toInt(), pick(220f, 220f))
        assertEquals(null, pick(120f, 120f))
        assertEquals("Sampling must never create a paint transaction", 1L, paintSurface(view).revision)
    }

    @Test fun ctrlAltStylusDragAdjustsBrushSizeWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            baseWidth = 10f
        }
        var adjusted = 10f
        view.onBrushSizeAdjusted = { adjusted = it }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
            view.onKeyDown(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT))
        }
        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 300f, 160f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 300f, 160f)

        assertEquals(20f, adjusted, .001f)
        assertEquals("Brush-size drag must never leave ink", 0L, paintSurface(view).revision)
    }

    @Test fun cancellingBrushSizeDragRestoresTheStartingSize() {
        val view = canvas().apply { tool = EditorTool.ERASER; eraserWidth = 18f }
        var adjusted = 18f
        view.onBrushSizeAdjusted = { adjusted = it }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
            view.onKeyDown(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT))
        }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 300f, 160f)
        sendStylus(view, 40L, MotionEvent.ACTION_CANCEL, 300f, 160f)

        assertEquals(18f, adjusted, .001f)
        assertEquals(18f, view.eraserWidth, .001f)
    }

    @Test fun forcedRotateNavigationRotatesWithAStylusWithoutCommittingInk() {
        val view = canvas().apply {
            tool = EditorTool.PEN
            forceNavigation = true
            rotateNavigation = true
        }
        var rotation = 0f
        view.onViewportRotate = { rotation += it }

        sendStylus(view, 0L, MotionEvent.ACTION_DOWN, 120f, 160f)
        sendStylus(view, 20L, MotionEvent.ACTION_MOVE, 200f, 230f)
        sendStylus(view, 40L, MotionEvent.ACTION_UP, 200f, 230f)

        assertEquals("Rotate navigation must never leave stylus ink", 0L, paintSurface(view).revision)
        assertEquals(20f, rotation, .001f)
    }

    @Test fun unmappedToolboxKeysStillReportRawDiagnosticFields() {
        val view = canvas()
        var keyCode = -1
        var scanCode = -1
        var phase: ShortcutPhase? = null
        var matched = true
        view.onKeyInputDiagnostic = {
            keyCode = it.keyCode
            scanCode = it.scanCode
            phase = it.phase
            matched = it.matchedAction != null
        }

        view.onKeyDown(KeyEvent.KEYCODE_F12, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12))

        assertEquals(KeyEvent.KEYCODE_F12, keyCode)
        assertEquals(0, scanCode)
        assertEquals(ShortcutPhase.PRESS, phase)
        assertFalse("Unmapped hardware input must remain visible in diagnostics", matched)
    }

    private fun canvas(): InkCanvasView {
        lateinit var result: InkCanvasView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val surface = PixelTileSurface(1000, 1000)
            result = InkCanvasView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, 1000, 1000)
                activeLayerId = "paint"
                setRasterLayerSurfaces(mapOf("paint" to surface))
                installV5TestInk()
            }
            paintSurfaces[result] = surface
        }
        return result
    }

    private fun paintSurface(view: InkCanvasView): PixelTileSurface = requireNotNull(paintSurfaces[view])
    private fun pointerDown(index: Int) = MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    private fun pointerUp(index: Int) = MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun point(id: Int, x: Float, y: Float): Pair<MotionEvent.PointerProperties, MotionEvent.PointerCoords> =
        MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } to
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = .1f; touchMajor = 8f }

    private fun send(view: InkCanvasView, time: Long, action: Int, vararg points: Pair<MotionEvent.PointerProperties, MotionEvent.PointerCoords>) {
        sendAt(view, 0L, time, action, *points)
    }

    private fun sendAt(view: InkCanvasView, downTime: Long, time: Long, action: Int, vararg points: Pair<MotionEvent.PointerProperties, MotionEvent.PointerCoords>) {
        val event = MotionEvent.obtain(
            downTime,
            time,
            action,
            points.size,
            points.map { it.first }.toTypedArray(),
            points.map { it.second }.toTypedArray(),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
        if (action == MotionEvent.ACTION_UP) settlePublishedStroke(view)
    }

    private fun sendStylus(view: InkCanvasView, time: Long, action: Int, x: Float, y: Float, buttonState: Int = 0, flags: Int = 0) {
        val event = stylusEvent(time, action, x, y, buttonState, flags)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
        if (action == MotionEvent.ACTION_UP) settlePublishedStroke(view)
    }

    private fun settlePublishedStroke(view: InkCanvasView) {
        view.awaitV5StrokeWorkerIdleForTest()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { }
    }

    private fun obscuredStylusEvent(action: Int, x: Float, y: Float): MotionEvent =
        stylusEvent(0L, action, x, y, flags = MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)

    private fun stylusEvent(time: Long, action: Int, x: Float, y: Float, buttonState: Int = 0, flags: Int = 0): MotionEvent =
        MotionEvent.obtain(
            0L,
            time,
            action,
            1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .6f; size = .05f }),
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            flags,
        )

}
