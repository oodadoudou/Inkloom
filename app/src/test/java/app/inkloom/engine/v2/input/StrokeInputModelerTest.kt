package app.inkloom.engine.v2.input

import app.inkloom.document.StrokeStabilization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeInputModelerTest {
    private val modeler = StrokeInputModeler()

    @Test
    fun firstSampleIsAcceptedWithoutChangingItsCoordinates() {
        assertTrue(append(.25f, .75f, 10L))
        assertEquals(.25f, modeler.x, 0.00001f)
        assertEquals(.75f, modeler.y, 0.00001f)
        assertEquals(.05f, modeler.pressure, 0.00001f)
    }

    @Test
    fun subPixelNoiseDoesNotCreateDocumentPoints() {
        assertTrue(append(.5f, .5f, 10L))
        assertFalse(append(.5001f, .5001f, 11L))
        assertEquals(10L, modeler.timeMs)
    }

    @Test
    fun resetStartsAnIndependentContactStream() {
        assertTrue(append(.1f, .1f, 10L))
        modeler.reset()
        assertTrue(append(.9f, .9f, 20L))
        assertEquals(.9f, modeler.x, 0.00001f)
        assertEquals(0f, modeler.velocity, 0.00001f)
    }

    @Test
    fun stabilizationFiltersMovementAndVelocityRemainsBounded() {
        assertTrue(append(.1f, .1f, 10L, stabilization = .8f))
        assertTrue(append(.9f, .9f, 11L, stabilization = .8f))
        assertTrue(modeler.x in .1f..0.9f)
        assertTrue(modeler.velocity in 0f..1f)
    }

    private fun append(
        x: Float,
        y: Float,
        timeMs: Long,
        stabilization: Float = 0f,
    ) = modeler.append(
        rawX = x,
        rawY = y,
        adjustedPressure = 0f,
        sampleTimeMs = timeMs,
        sampleTilt = .2f,
        sampleOrientation = .3f,
        viewportWidthPx = 1_200,
        viewportHeightPx = 1_600,
        stabilization = stabilization,
        stabilizationBySpeed = false,
        stabilizationBySpeedMode = StrokeStabilization.REDUCE_WHEN_FAST,
    )
}
