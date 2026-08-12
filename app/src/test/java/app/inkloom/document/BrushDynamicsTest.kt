package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushDynamicsTest {
    private val curve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(.5f, .25f), BrushCurvePoint(1f, 1f))

    @Test fun interpolatesImportedPressureCurves() {
        assertEquals(0f, BrushDynamics.curve(curve, 0f), .0001f)
        assertEquals(.125f, BrushDynamics.curve(curve, .25f), .0001f)
        assertEquals(.625f, BrushDynamics.curve(curve, .75f), .0001f)
        assertEquals(1f, BrushDynamics.curve(curve, 1f), .0001f)
    }

    @Test fun appliesMinimumAndHonorsDisabledDynamics() {
        assertEquals(.4f, BrushDynamics.pressure(true, .4f, curve, 0f), .0001f)
        assertEquals(.775f, BrushDynamics.pressure(true, .4f, curve, .75f), .0001f)
        assertEquals(1f, BrushDynamics.pressure(false, 0f, curve, .1f), .0001f)
    }

    @Test fun combinesTiltAndVelocityWithTheirMinimums() {
        val dynamics = BrushSecondaryDynamics(
            tiltEnabled = true,
            tiltMinimum = .5f,
            velocityEnabled = true,
            velocityMinimum = .2f,
            velocityCurve = listOf(BrushCurvePoint(0f, 1f), BrushCurvePoint(1f, 0f)),
        )
        val point = StrokePoint(0f, 0f, tilt = (Math.PI / 4).toFloat(), velocity = .75f)
        assertEquals(.3f, BrushDynamics.secondary(dynamics, point, 1, 2), .0001f)
    }

    @Test fun cspVelocityScaleChangesTheCurveInputBeforeEvaluation() {
        val point = StrokePoint(0f, 0f, velocity = .2f)
        val unscaled = BrushSecondaryDynamics(velocityEnabled = true, velocityScale = 1f)
        val amplified = BrushSecondaryDynamics(velocityEnabled = true, velocityScale = 5f)

        assertEquals(.2f, BrushDynamics.secondary(unscaled, point, 1, 2), .0001f)
        assertEquals(1f, BrushDynamics.secondary(amplified, point, 1, 2), .0001f)
    }

    @Test fun tiltDoesNotRespondUntilTheImportedTriggerAngle() {
        val dynamics = BrushSecondaryDynamics(tiltEnabled = true, tiltMinimum = .2f, tiltStart = .5f)
        val below = StrokePoint(0f, 0f, tilt = (Math.PI * .2).toFloat())
        val halfway = StrokePoint(0f, 0f, tilt = (Math.PI * .375).toFloat())
        val flat = StrokePoint(0f, 0f, tilt = (Math.PI / 2).toFloat())

        assertEquals(.2f, BrushDynamics.secondary(dynamics, below, 1, 2), .0001f)
        assertEquals(.6f, BrushDynamics.secondary(dynamics, halfway, 1, 2), .0001f)
        assertEquals(1f, BrushDynamics.secondary(dynamics, flat, 1, 2), .0001f)
    }

    @Test fun tiltCurveCanReverseTheResponseForShapeRoundness() {
        val dynamics = BrushSecondaryDynamics(
            tiltEnabled = true,
            tiltMinimum = .25f,
            tiltCurve = listOf(BrushCurvePoint(0f, 1f), BrushCurvePoint(1f, 0f)),
        )
        val upright = StrokePoint(0f, 0f, tilt = 0f)
        val flat = StrokePoint(0f, 0f, tilt = (Math.PI / 2).toFloat())

        assertEquals(1f, BrushDynamics.secondary(dynamics, upright, 1, 2), .0001f)
        assertEquals(.25f, BrushDynamics.secondary(dynamics, flat, 1, 2), .0001f)
    }

    @Test fun randomDynamicsAreDeterministicPerStrokePoint() {
        val first = BrushDynamics.deterministicRandom(42, 1200L, 7)
        assertEquals(first, BrushDynamics.deterministicRandom(42, 1200L, 7), 0f)
        org.junit.Assert.assertNotEquals(first, BrushDynamics.deterministicRandom(43, 1200L, 7))
    }
}
