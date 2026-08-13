package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainDynamicsTest {
    @Test fun movingGrainRotationCanFollowOrInvertStrokeDirection() {
        assertEquals(90f, GrainBehavior.rotationDegrees(0f, 1f, 87f), .0001f)
        assertEquals(-90f, GrainBehavior.rotationDegrees(0f, -1f, 87f), .0001f)
        assertEquals(30f, GrainBehavior.rotationDegrees(31f, 0f, 140f), .0001f)
    }

    @Test fun pressureAndVelocityControlTextureDensityIndependently() {
        val stroke = InkStroke(
            points = emptyList(),
            grainDensity = .8f,
            grainDensityDynamics = BrushParameterDynamics(
                pressureEnabled = true,
                pressureMinimum = .25f,
                secondary = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .5f),
            ),
        )

        assertTrue(GrainDynamics.isDynamic(stroke))
        assertEquals(.1f, GrainDynamics.density(stroke, StrokePoint(0f, 0f, pressure = 0f, velocity = 0f)), .001f)
        assertEquals(.8f, GrainDynamics.density(stroke, StrokePoint(0f, 0f, pressure = 1f, velocity = 1f)), .001f)
        assertFalse(GrainDynamics.isDynamic(stroke.copy(grainDensityDynamics = BrushParameterDynamics())))
    }

    @Test fun absoluteDepthMinimumSurvivesCombinedDynamics() {
        val stroke = InkStroke(
            points = emptyList(),
            grainDensity = .8f,
            grainDensityMinimum = .3f,
            grainDensityDynamics = BrushParameterDynamics(
                pressureEnabled = true,
                secondary = BrushSecondaryDynamics(randomEnabled = true, randomMinimum = 0f),
            ),
        )

        assertEquals(.3f, GrainDynamics.density(stroke, StrokePoint(0f, 0f, pressure = 0f)), .001f)
        assertEquals(.8f, GrainDynamics.density(stroke.copy(grainDensityMinimum = 2f), StrokePoint(0f, 0f, pressure = 0f)), .001f)
    }

    @Test fun secondaryBrushTextureUsesItsOwnDensityAndDynamics() {
        val stroke = InkStroke(
            points = emptyList(),
            grainDensity = .95f,
            dualGrainDensity = .6f,
            dualGrainDensityMinimum = .2f,
            dualGrainDensityDynamics = BrushParameterDynamics(
                pressureEnabled = true,
                pressureMinimum = 0f,
            ),
        )

        assertTrue(GrainDynamics.isDualDynamic(stroke))
        assertEquals(.2f, GrainDynamics.dualDensity(stroke, StrokePoint(0f, 0f, pressure = 0f)), .001f)
        assertEquals(.6f, GrainDynamics.dualDensity(stroke, StrokePoint(0f, 0f, pressure = 1f)), .001f)
        assertFalse(GrainDynamics.isDualDynamic(stroke.copy(dualGrainDensityDynamics = BrushParameterDynamics())))
    }
}
