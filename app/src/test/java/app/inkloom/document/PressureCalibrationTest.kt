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
}
