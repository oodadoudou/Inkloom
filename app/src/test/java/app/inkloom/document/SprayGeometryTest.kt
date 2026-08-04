package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SprayGeometryTest {
    @Test fun linkedParticleSizeTracksBrushDiameter() {
        assertEquals(62f, SprayGeometry.particleDiameter(50f, 124f, 0, true, 1600f), .001f)
    }

    @Test fun densityAddsAControlledNumberOfParticles() {
        assertEquals(1, SprayGeometry.particlesPerPlot(1f))
        assertEquals(3, SprayGeometry.particlesPerPlot(50f))
        assertEquals(5, SprayGeometry.particlesPerPlot(100f))
    }

    @Test fun positiveBiasMovesTheSameSampleTowardCenter() {
        val neutral = SprayGeometry.offset(100f, .25f, .5f, 0f)
        val centered = SprayGeometry.offset(100f, .25f, .5f, 100f)
        assertTrue(kotlin.math.hypot(centered.x, centered.y) < kotlin.math.hypot(neutral.x, neutral.y))
    }

    @Test fun cspParticleSizeCombinesPressureAndVelocity() {
        val stroke = InkStroke(
            points = emptyList(),
            sprayParticleSize = 100f,
            sprayParticleSizePressureEnabled = true,
            sprayParticleSizePressureMinimum = .2f,
            sprayParticleSizeSecondaryDynamics = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .4f),
        )

        assertEquals(8f, SprayDynamics.particleSize(stroke, StrokePoint(0f, 0f, pressure = 0f, velocity = 0f)), .001f)
        assertEquals(100f, SprayDynamics.particleSize(stroke, StrokePoint(0f, 0f, pressure = 1f, velocity = 1f)), .001f)
    }

    @Test fun cspParticleDensityUsesImportedVelocityCurveAndBounds() {
        val stroke = InkStroke(
            points = emptyList(),
            sprayDensity = 80f,
            sprayDensitySecondaryDynamics = BrushSecondaryDynamics(
                velocityEnabled = true,
                velocityMinimum = .25f,
                velocityCurve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)),
            ),
        )

        assertEquals(20f, SprayDynamics.density(stroke, StrokePoint(0f, 0f, velocity = 0f)), .001f)
        assertEquals(80f, SprayDynamics.density(stroke, StrokePoint(0f, 0f, velocity = 1f)), .001f)
    }

    @Test fun dualSprayUsesIndependentParticleDynamics() {
        val stroke = InkStroke(
            points = emptyList(),
            dualSprayParticleSize = 200f,
            dualSprayParticleSizeDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .25f),
            dualSprayDensity = 80f,
            dualSprayDensityDynamics = BrushParameterDynamics(
                secondary = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .5f),
            ),
        )

        assertEquals(50f, SprayDynamics.dualParticleSize(stroke, StrokePoint(0f, 0f, pressure = 0f)), .001f)
        assertEquals(200f, SprayDynamics.dualParticleSize(stroke, StrokePoint(0f, 0f, pressure = 1f)), .001f)
        assertEquals(40f, SprayDynamics.dualDensity(stroke, StrokePoint(0f, 0f, velocity = 0f)), .001f)
        assertEquals(80f, SprayDynamics.dualDensity(stroke, StrokePoint(0f, 0f, velocity = 1f)), .001f)
    }
}
