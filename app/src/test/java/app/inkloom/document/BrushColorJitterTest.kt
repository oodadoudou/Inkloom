package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushColorJitterTest {
    @Test fun signedRandomJitterIsDeterministicPerStampAndPreservesAlpha() {
        val stroke = InkStroke(
            renderSeed = 421,
            points = listOf(StrokePoint(.2f, .3f, timeMs = 19)),
            colorArgb = 0x7F336699,
            colorJitterHueDegrees = 35f,
            colorJitterSaturation = 20f,
            colorJitterValue = 12f,
            colorJitterHueDynamics = BrushColorDynamics(randomEnabled = true, randomMinimum = -1f),
            colorJitterSaturationDynamics = BrushColorDynamics(randomEnabled = true, randomMinimum = -1f),
            colorJitterValueDynamics = BrushColorDynamics(randomEnabled = true, randomMinimum = -1f),
        )
        val point = stroke.points.single()

        val first = BrushColorJitter.argb(stroke, point, 3)
        val repeated = BrushColorJitter.argb(stroke, point, 3)
        val next = BrushColorJitter.argb(stroke, point, 4)

        assertEquals(first, repeated)
        assertNotEquals(first, next)
        assertEquals(0x7f, first ushr 24 and 0xff)
        assertNotEquals(stroke.colorArgb.toInt(), first)
    }

    @Test fun pressureCurveAndSignedRandomCanMoveInBothDirections() {
        val dynamics = BrushColorDynamics(
            pressureEnabled = true,
            pressureMinimum = .2f,
            pressureCurve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)),
            randomEnabled = true,
            randomMinimum = -1f,
        )
        val values = (0 until 64).map { index -> BrushColorJitter.amount(dynamics, StrokePoint(.5f, .5f, pressure = 1f, timeMs = 9), 77, index, 5) }

        assertTrue(values.any { it < 0f })
        assertTrue(values.any { it > 0f })
    }

    @Test fun disabledJitterReturnsOriginalColorExactly() {
        val stroke = InkStroke(points = listOf(StrokePoint(0f, 0f)), colorArgb = 0xFF102030)
        assertEquals(stroke.colorArgb.toInt(), BrushColorJitter.argb(stroke, stroke.points.single(), 0))
    }

    @Test fun colorDynamicsHonorCspVelocityScale() {
        val point = StrokePoint(0f, 0f, velocity = .2f)
        val unscaled = BrushColorDynamics(velocityEnabled = true, velocityScale = 1f)
        val amplified = BrushColorDynamics(velocityEnabled = true, velocityScale = 5f)

        assertEquals(.2f, BrushColorJitter.amount(unscaled, point, 1, 0, 2), .0001f)
        assertEquals(1f, BrushColorJitter.amount(amplified, point, 1, 0, 2), .0001f)
    }

    @Test fun colorTiltDynamicsHonorTheirTriggerAngle() {
        val dynamics = BrushColorDynamics(tiltEnabled = true, tiltMinimum = .1f, tiltStart = .5f)
        val below = StrokePoint(0f, 0f, tilt = (Math.PI * .2).toFloat())
        val flat = StrokePoint(0f, 0f, tilt = (Math.PI / 2).toFloat())

        assertEquals(.1f, BrushColorJitter.amount(dynamics, below, 1, 0, 2), .0001f)
        assertEquals(1f, BrushColorJitter.amount(dynamics, flat, 1, 0, 2), .0001f)
    }

    @Test fun ProcreateStrokeJitterIsConstantWhileStampJitterVaries() {
        val point = StrokePoint(.5f, .5f, timeMs = 3)
        val strokeJitter = InkStroke(
            points = listOf(point), renderSeed = 917, colorArgb = 0xFF336699,
            procreateColorDynamics = ProcreateColorDynamics(strokeHue = 1f, strokeSaturation = 1f),
        )
        val stampJitter = strokeJitter.copy(procreateColorDynamics = ProcreateColorDynamics(stampHue = 1f, stampSaturation = 1f))

        assertEquals(BrushColorJitter.argb(strokeJitter, point, 1), BrushColorJitter.argb(strokeJitter, point, 19))
        assertNotEquals(BrushColorJitter.argb(stampJitter, point, 1), BrushColorJitter.argb(stampJitter, point, 19))
    }

    @Test fun ProcreatePressureAndTiltTransitionTowardSecondaryColorAndPreserveAlpha() {
        val uprightLight = StrokePoint(.5f, .5f, pressure = 0f, tilt = 0f)
        val flatHeavy = StrokePoint(.5f, .5f, pressure = 1f, tilt = (Math.PI / 2).toFloat())
        val pressureStroke = InkStroke(
            points = listOf(uprightLight, flatHeavy), colorArgb = 0x7FFF0000, secondaryColorArgb = 0xFF0000FF,
            procreateColorDynamics = ProcreateColorDynamics(pressureSecondary = 1f),
        )
        val tiltStroke = pressureStroke.copy(procreateColorDynamics = ProcreateColorDynamics(tiltSecondary = 1f, tiltStart = .5f))

        assertEquals(0x7FFF0000, BrushColorJitter.argb(pressureStroke, uprightLight, 0))
        assertEquals(0x7F0000FF, BrushColorJitter.argb(pressureStroke, flatHeavy, 1))
        assertEquals(0x7FFF0000, BrushColorJitter.argb(tiltStroke, uprightLight, 0))
        assertEquals(0x7F0000FF, BrushColorJitter.argb(tiltStroke, flatHeavy, 1))
    }
}
