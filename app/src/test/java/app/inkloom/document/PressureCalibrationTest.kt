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
        assertEquals(listOf(0f, .9f, .9f, .9f, 1f), normalized)
    }

    @Test fun responseCurveRoundTripsThroughCompactSettingsEncoding() {
        val encoded = PressureResponseCurve.encode(PressureResponseCurve.SOFT)
        assertEquals(PressureResponseCurve.SOFT, PressureResponseCurve.decode(encoded))
        assertEquals(PressureResponseCurve.LINEAR, PressureResponseCurve.decode("broken"))
    }
}
