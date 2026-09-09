package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureCalibrationTest {
    @Test fun mapsDeadZoneAndFullPressurePoint() {
        assertEquals(0f, PressureCalibration.apply(.1f, .15f, .8f, 1f), .0001f)
        assertEquals(1f, PressureCalibration.apply(.8f, .15f, .8f, 1f), .0001f)
        assertEquals(.5f, PressureCalibration.apply(.475f, .15f, .8f, 1f), .0001f)
    }

    @Test fun sensitivityChangesCurveWithoutChangingEndpoints() {
        val softer = PressureCalibration.apply(.5f, 0f, 1f, 2f)
        val harder = PressureCalibration.apply(.5f, 0f, 1f, .5f)
        assertTrue(softer > .5f)
        assertTrue(harder < .5f)
        assertEquals(0f, PressureCalibration.apply(0f, 0f, 1f, 2f), .0001f)
        assertEquals(1f, PressureCalibration.apply(1f, 0f, 1f, .5f), .0001f)
    }

    @Test fun editableResponseCurveChangesMidPressureAndRemainsMonotonic() {
        val soft = PressureCalibration.apply(.5f, 0f, 1f, 1f, PressureResponseCurve.SOFT)
        val firm = PressureCalibration.apply(.5f, 0f, 1f, 1f, PressureResponseCurve.FIRM)

        assertTrue(soft > .5f)
        assertTrue(firm < .5f)
        val normalized = PressureResponseCurve.normalize(listOf(1f, .9f, .1f, .8f, 0f))
        assertEquals(listOf(0f, 0f, .25f, .9f, .5f, .9f, .75f, .9f, 1f, 1f), normalized)
    }

    @Test fun freelyPositionedKnotsAreInterpolatedAndRemainMonotone() {
        val curve = listOf(0f, 0f, .13f, .34f, .68f, .82f, 1f, 1f)
        assertEquals(curve, PressureResponseCurve.decode(PressureResponseCurve.encode(curve)))
        assertEquals(.34f, PressureResponseCurve.apply(.13f, curve), .00001f)
        assertEquals(.82f, PressureResponseCurve.apply(.68f, curve), .00001f)
        val samples = (0..1000).map { PressureResponseCurve.apply(it / 1000f, curve) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - .000001f })
    }

    @Test fun responseCurveRoundTripsThroughCompactSettingsEncoding() {
        val encoded = PressureResponseCurve.encode(PressureResponseCurve.SOFT)
        assertEquals(PressureResponseCurve.SOFT, PressureResponseCurve.decode(encoded))
        assertEquals(PressureResponseCurve.LINEAR, PressureResponseCurve.decode("broken"))
    }
    @Test fun smoothCurveIsMonotonicAndHasContinuousTangentsAtKnots() {
        for (curve in listOf(PressureResponseCurve.SOFT, PressureResponseCurve.FIRM, listOf(0f, .9f, .9f, .9f, 1f))) {
            val samples = (0..1000).map { PressureResponseCurve.apply(it / 1000f, curve) }
            assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - .000001f })
            assertEquals(0f, samples.first(), .000001f)
            assertEquals(1f, samples.last(), .000001f)
            for (knot in listOf(.25f, .5f, .75f)) {
                val epsilon = .0005f
                val at = PressureResponseCurve.apply(knot, curve)
                val before = (at - PressureResponseCurve.apply(knot - epsilon, curve)) / epsilon
                val after = (PressureResponseCurve.apply(knot + epsilon, curve) - at) / epsilon
                assertEquals("Tangent must not jump at $knot", before, after, .04f)
            }
        }
    }
}
