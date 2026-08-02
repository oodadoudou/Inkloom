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
}
