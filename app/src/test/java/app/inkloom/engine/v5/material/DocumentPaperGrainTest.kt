package app.inkloom.engine.v5.material

import org.junit.Assert.*
import org.junit.Test

class DocumentPaperGrainTest {
    @Test fun scaleAndSeedChangePaperWhileDensityAndStrengthRemainBounded() {
        fun samples(scale: Float = 100f, density: Float = .5f, strength: Float = .8f, seed: Int = 42): FloatArray {
            val paper = DocumentPaperGrain(scale, density, strength, seed)
            return FloatArray(512) { i -> paper.coverageAt(i % 32 + .5f, i / 32 + .5f, paper.strengthAt(0f)) }
        }
        val baseline = samples()
        assertArrayEquals(baseline, samples(), 0f)
        assertFalse(baseline.contentEquals(samples(scale = 200f)))
        assertFalse(baseline.contentEquals(samples(seed = 43)))
        assertTrue(samples(density = .9f).sum() > samples(density = .1f).sum())
        assertTrue(samples(strength = 0f).all { it == 1f })
        assertTrue(samples(density = 1f).all { it == 1f })
        assertTrue(baseline.all { it in .19f..1f })
    }

    @Test fun tiltStartsAtTheAuthoredThresholdAndNeverChangesThePaperCoordinates() {
        val paper = DocumentPaperGrain(100f, .4f, .5f, 42, true, .5f, 1f)
        assertEquals(.5f, paper.strengthAt(0f), 0f)
        assertEquals(.5f, paper.strengthAt((Math.PI / 4).toFloat()), .0001f)
        assertEquals(1f, paper.strengthAt((Math.PI / 2).toFloat()), .0001f)
        for (x in 0..50) assertTrue(paper.coverageAt(x + .5f, 20f, paper.strengthAt(0f)) >=
            paper.coverageAt(x + .5f, 20f, paper.strengthAt((Math.PI / 2).toFloat())))
    }
}
