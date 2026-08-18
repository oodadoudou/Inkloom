package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialPressurePolicyTest {
    @Test
    fun useReportedPassesTheRawDownPressureThrough() {
        assertEquals(.7f, PressureCalibration.resolveInitialPressure(.7f, InitialPressurePolicy.USE_REPORTED, .5f), .0001f)
        assertEquals(0f, PressureCalibration.resolveInitialPressure(0f, InitialPressurePolicy.USE_REPORTED, .5f), .0001f)
    }

    @Test
    fun waitForFirstNonzeroReplacesAZeroDownWithTheFirstValidPressure() {
        assertEquals(0f, PressureCalibration.resolveInitialPressure(0f, InitialPressurePolicy.WAIT_FOR_FIRST_NONZERO, 0f), .0001f)
        assertEquals(.42f, PressureCalibration.resolveInitialPressure(0f, InitialPressurePolicy.WAIT_FOR_FIRST_NONZERO, .42f), .0001f)
        assertEquals(.3f, PressureCalibration.resolveInitialPressure(.3f, InitialPressurePolicy.WAIT_FOR_FIRST_NONZERO, .42f), .0001f)
    }

    @Test
    fun bootstrapFromFirstValidMoveUsesTheMovePressureUnconditionally() {
        assertEquals(.6f, PressureCalibration.resolveInitialPressure(.1f, InitialPressurePolicy.BOOTSTRAP_FROM_FIRST_VALID_MOVE, .6f), .0001f)
        assertEquals(0f, PressureCalibration.resolveInitialPressure(.1f, InitialPressurePolicy.BOOTSTRAP_FROM_FIRST_VALID_MOVE, 0f), .0001f)
    }

    @Test
    fun calibratedContactMinimumFloorsAZeroDown() {
        assertEquals(
            PressureCalibration.CALIBRATED_CONTACT_MINIMUM_PRESSURE,
            PressureCalibration.resolveInitialPressure(0f, InitialPressurePolicy.CALIBRATED_CONTACT_MINIMUM, .05f),
            .0001f,
        )
        assertTrue(PressureCalibration.resolveInitialPressure(0f, InitialPressurePolicy.CALIBRATED_CONTACT_MINIMUM, .1f) >= .08f)
        assertEquals(.6f, PressureCalibration.resolveInitialPressure(.6f, InitialPressurePolicy.CALIBRATED_CONTACT_MINIMUM, .05f), .0001f)
    }
}
