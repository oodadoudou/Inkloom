package app.inkloom.ui

import app.inkloom.document.HoldRepeat
import app.inkloom.document.MultiFingerTapGesture
import app.inkloom.document.QuickPinchGesture
import app.inkloom.document.ScrubGesture
import app.inkloom.settings.AppSettings

/** The finger gestures painters bring from Procreate, each of which can be turned off in Settings. */
internal data class FingerGestureSettings(
    val touchHoldEyedropper: Boolean = true,
    val touchHoldDelayMillis: Long = 400L,
    val fourFingerTapFullScreen: Boolean = true,
    val quickPinchToFit: Boolean = true,
    val threeFingerScrubClear: Boolean = true,
    val holdToRepeatUndo: Boolean = true,
) {
    companion object {
        fun from(settings: AppSettings) = FingerGestureSettings(
            touchHoldEyedropper = settings.touchHoldEyedropper,
            touchHoldDelayMillis = settings.touchHoldEyedropperDelayMillis,
            fourFingerTapFullScreen = settings.fourFingerTapFullScreen,
            quickPinchToFit = settings.quickPinchToFit,
            threeFingerScrubClear = settings.threeFingerScrubClear,
            holdToRepeatUndo = settings.holdToRepeatUndo,
        )
    }
}

/**
 * Procreate's gesture controls on top of the canvas's finger navigation, which keeps panning,
 * zooming, rotating and the undo/redo taps. Behaviour follows the Procreate Handbook (Gestures):
 * touch and hold for the eyedropper, four-finger tap for full screen, quick pinch to fit,
 * three-finger scrub to clear the layer, and holding the undo/redo tap for rapid undo or redo.
 */
internal class FingerGestureControls(movementSlop: Float, scrubLeg: Float, private val host: Host) {
    interface Host {
        fun postDelayed(action: Runnable, delayMillis: Long): Boolean
        fun removeCallbacks(action: Runnable): Boolean
        /** The still finger becomes an eyedropper; false when it cannot (the contact is kept as navigation). */
        fun beginHoldEyedropper(): Boolean
        fun undoGesture()
        fun redoGesture()
        fun toggleFullScreen()
        fun fitToScreen()
        fun clearLayer()
    }

    var settings = FingerGestureSettings()
        set(value) { field = value; cancel() }

    private val fourFingerTap = MultiFingerTapGesture(movementSlop, maximumTapDurationMs = 300L)
    private val stillFingers = MultiFingerTapGesture(movementSlop, maximumTapDurationMs = Long.MAX_VALUE)
    private val quickPinch = QuickPinchGesture()
    private val scrub = ScrubGesture(scrubLeg)
    private var holdPending = false
    private var repeatFingers = 0
    private var repeated = 0

    private val holdEyedropper = Runnable {
        if (!holdPending) return@Runnable
        holdPending = false
        host.beginHoldEyedropper()
    }

    private val repeatStep: Runnable = object : Runnable {
        override fun run() {
            if (repeatFingers == 0 || !stillFingers.isEligible) return
            if (repeatFingers == 2) host.undoGesture() else host.redoGesture()
            host.postDelayed(this, HoldRepeat.intervalMs(repeated++))
        }
    }

    /** One finger down on the canvas that could be a tap (not a palm, not right after the pen). */
    fun fingerDown(eligible: Boolean) {
        cancelHold()
        if (!eligible || !settings.touchHoldEyedropper) return
        holdPending = true
        host.postDelayed(holdEyedropper, settings.touchHoldDelayMillis)
    }

    /** The single finger travelled past the touch slop: it is panning, not holding. */
    fun fingerMoved() = cancelHold()

    fun pointersDown(count: Int, timeMs: Long, focusX: Float, focusY: Float, span: Float) {
        cancelHold()
        stopRepeat()
        fourFingerTap.cancel()
        quickPinch.cancel()
        scrub.cancel()
        when (count) {
            2 -> {
                if (settings.quickPinchToFit) quickPinch.start(timeMs, span)
                startRepeat(2, timeMs, focusX, focusY)
            }
            3 -> {
                if (settings.threeFingerScrubClear) scrub.start(timeMs, focusX)
                startRepeat(3, timeMs, focusX, focusY)
            }
            4 -> if (settings.fourFingerTapFullScreen) fourFingerTap.start(timeMs, focusX, focusY)
        }
    }

    /** [transforming]: the fingers pinch or turn, so it is not a still tap or hold. */
    fun pointersMoved(count: Int, timeMs: Long, focusX: Float, focusY: Float, transforming: Boolean) {
        stillFingers.move(focusX, focusY, transforming)
        if (!stillFingers.isEligible) stopRepeat()
        if (count == 4) fourFingerTap.move(focusX, focusY, transforming)
        if (count == 3 && scrub.move(timeMs, focusX)) host.clearLayer()
    }

    /** The first finger of [count] lifts; [span] is the fingers' spread as it lifts. */
    fun pointerUp(count: Int, timeMs: Long, span: Float, rotationDegrees: Float) {
        stopRepeat()
        scrub.cancel()
        if (count == 4 && fourFingerTap.finish(timeMs)) host.toggleFullScreen()
        if (count == 2 && quickPinch.finish(timeMs, span, rotationDegrees)) host.fitToScreen()
        fourFingerTap.cancel()
        quickPinch.cancel()
    }

    fun cancel() {
        cancelHold()
        stopRepeat()
        fourFingerTap.cancel()
        quickPinch.cancel()
        scrub.cancel()
        repeated = 0
    }

    private fun startRepeat(fingers: Int, timeMs: Long, focusX: Float, focusY: Float) {
        repeated = 0
        if (!settings.holdToRepeatUndo) return
        stillFingers.start(timeMs, focusX, focusY)
        repeatFingers = fingers
        host.postDelayed(repeatStep, HoldRepeat.START_DELAY_MS)
    }

    private fun stopRepeat() {
        repeatFingers = 0
        stillFingers.cancel()
        host.removeCallbacks(repeatStep)
    }

    private fun cancelHold() {
        holdPending = false
        host.removeCallbacks(holdEyedropper)
    }
}
