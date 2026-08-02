package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class BrushStampRotationTest {
    @Test fun combinesOnlyEnabledDirectionSources() {
        val angle = BrushStampRotation.degrees(
            fixedDegrees = 15f,
            followStylusOrientation = true,
            followStrokeDirection = true,
            stylusOrientationRadians = (Math.PI / 2).toFloat(),
            strokeDirectionDegrees = 30f,
            randomRotationDegrees = 0f,
            randomUnit = .9f,
        )

        assertEquals(135f, angle, .0001f)
    }

    @Test fun mapsRandomInfluenceSymmetricallyAroundTheFixedAngle() {
        assertEquals(-70f, BrushStampRotation.degrees(20f, false, false, 1f, 90f, 90f, 0f), .0001f)
        assertEquals(20f, BrushStampRotation.degrees(20f, false, false, 1f, 90f, 90f, .5f), .0001f)
        assertEquals(110f, BrushStampRotation.degrees(20f, false, false, 1f, 90f, 90f, 1f), .0001f)
    }

    @Test fun sprayUsesIndependentParticleRotationInsteadOfNormalTipRotation() {
        val stroke = InkStroke(
            points = listOf(StrokePoint(.5f, .5f)),
            rotationDegrees = 90f,
            followStylusOrientation = true,
            followStrokeDirection = true,
            randomRotationDegrees = 180f,
            spray = true,
            sprayParticleRotationDegrees = 17f,
            sprayParticleFollowStrokeDirection = true,
            sprayParticleRandomRotationDegrees = 40f,
        )

        assertEquals(47f, BrushStampRotation.degrees(stroke, false, 1f, 30f, .5f), .0001f)
        assertEquals(true, BrushStampRotation.followsStroke(stroke, false))
    }

    @Test fun dualSprayUsesItsOwnParticleRotationSources() {
        val stroke = InkStroke(
            points = listOf(StrokePoint(.5f, .5f)),
            dualBrush = true,
            dualRotationDegrees = 90f,
            dualFollowStrokeDirection = true,
            dualRandomRotationDegrees = 180f,
            dualSpray = true,
            dualSprayParticleRotationDegrees = -25f,
            dualSprayParticleFollowStylusOrientation = true,
            dualSprayParticleRandomRotationDegrees = 90f,
        )

        assertEquals(65f, BrushStampRotation.degrees(stroke, true, (Math.PI / 2).toFloat(), 30f, .5f), .0001f)
        assertEquals(false, BrushStampRotation.followsStroke(stroke, true))
    }
}
