package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test fun signedTouchRotationInterpolatesWithLocalStrokeDirection() {
        val followsHalf = InkStroke(
            points = listOf(StrokePoint(0f, 0f)),
            inputKind = StrokeInputKind.TOUCH,
            rotationDegrees = 15f,
            shapeTouchRotation = .5f,
        )
        val inverse = followsHalf.copy(shapeTouchRotation = -1f)

        assertEquals(55f, BrushStampRotation.degrees(followsHalf, false, 0f, 80f, .5f), .0001f)
        assertEquals(-65f, BrushStampRotation.degrees(inverse, false, 0f, 80f, .5f), .0001f)
        assertTrue(BrushStampRotation.followsStroke(followsHalf, false))
    }

    @Test fun stylusAzimuthOverridesTouchRotationButFingerUsesIt() {
        val program = InkStroke(
            points = listOf(StrokePoint(0f, 0f)),
            rotationDegrees = 10f,
            shapeTouchRotation = -.75f,
            followStylusOrientation = true,
        )
        val stylus = BrushStampRotation.degrees(program, false, (Math.PI / 2).toFloat(), 40f, .5f)
        val touch = BrushStampRotation.degrees(program.copy(inputKind = StrokeInputKind.TOUCH), false, (Math.PI / 2).toFloat(), 40f, .5f)

        assertEquals(100f, stylus, .0001f)
        assertEquals(-20f, touch, .0001f)
        assertEquals(false, BrushStampRotation.followsStroke(program, false))
        assertTrue(BrushStampRotation.followsStroke(program.copy(inputKind = StrokeInputKind.TOUCH), false))
    }

    @Test fun orientToScreenAdjustmentIsBakedIntoTheStroke() {
        val stroke = InkStroke(
            points = listOf(StrokePoint(0f, 0f)),
            rotationDegrees = 25f,
            shapeScreenRotationDegrees = -70f,
        )

        assertEquals(-45f, BrushStampRotation.degrees(stroke, false, 0f, 0f, .5f), .0001f)
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

    @Test fun perStrokeJitterIsStableAcrossEveryStamp() {
        val stroke = InkStroke(
            renderSeed = 9182,
            points = listOf(StrokePoint(0f, 0f)),
            strokeRotationJitterDegrees = 180f,
        )

        val first = BrushStampRotation.degrees(stroke, false, 0f, 0f, .1f)
        val second = BrushStampRotation.degrees(stroke, false, 0f, 0f, .9f)

        assertEquals(first, second, .0001f)
        assertTrue(first in -180f..180f)
        assertNotEquals(first, BrushStampRotation.degrees(stroke.copy(renderSeed = 9183), false, 0f, 0f, .1f))
    }
}
