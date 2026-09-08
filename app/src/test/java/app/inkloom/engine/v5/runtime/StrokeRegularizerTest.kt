package app.inkloom.engine.v5.runtime

import app.inkloom.engine.v5.input.InputTool
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class StrokeRegularizerTest {
    private fun sample(x: Float, y: Float, index: Int) = RawDocumentSample(x, y, index * 10_000_000L,
        index * 10_000_000L, .2f + index / 100f, .3f, .4f, InputTool.STYLUS)

    @Test fun jitteredLineBecomesStraightAndRetainsSensorValues() {
        val raw = List(31) { i -> sample(100f + i * 10f, 200f + if (i in 1..29) sin(i.toFloat()) * 3f else 0f, i) }
        val fit = requireNotNull(StrokeRegularizer.fit(raw))
        assertEquals(StrokeRegularizer.Kind.LINE, fit.kind)
        assertTrue(fit.samples.all { abs(it.y - 200f) < .001f })
        assertEquals(raw.first().x, fit.samples.first().x)
        assertEquals(raw.last().x, fit.samples.last().x)
        assertTrue(fit.samples.all { it.tiltRadians == .3f && it.orientationRadians == .4f })
        assertTrue(fit.samples.map { it.pressure }.distinct().size > 10)
        assertTrue(fit.samples.zipWithNext().all { it.first.eventTimeNanos <= it.second.eventTimeNanos })
    }

    @Test fun curvedStrokeRemainsACurveInsteadOfBeingStraightened() {
        val raw = List(51) { i -> val t = i / 50f; sample(100f + t * 300f, 200f - 300f * t * (1f - t), i) }
        val fit = requireNotNull(StrokeRegularizer.fit(raw))
        assertEquals(StrokeRegularizer.Kind.CURVE, fit.kind)
        assertTrue(fit.samples.minOf { it.y } < 140f)
        assertEquals(raw.first().y, fit.samples.first().y, .001f)
        assertEquals(raw.last().y, fit.samples.last().y, .001f)
        val adjusted = StrokeRegularizer.moveEndpoint(fit.samples, 100f, 500f)
        assertEquals(100f, adjusted.last().x, .001f)
        assertEquals(500f, adjusted.last().y, .001f)
        assertEquals(fit.samples.first(), adjusted.first())
    }

    @Test fun smallMarksAndScribblesAreNotReplaced() {
        assertNull(StrokeRegularizer.fit(List(10) { sample(it.toFloat(), 10f, it) }))
        assertNull(StrokeRegularizer.fit(List(101) { i -> sample(100f + 120f * sin(i * .3f), 150f + 90f * cos(i * .5f), i) }))
    }
}
