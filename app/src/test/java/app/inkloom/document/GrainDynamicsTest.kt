package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainDynamicsTest {
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
}
