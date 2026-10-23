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

    private fun wobble(i: Int) = sin(i * 1.7f) * 2.5f

    @Test fun heldLoopsBecomeCirclesAndEllipses() {
        val circle = List(121) { i -> val a = i / 120f * 2f * PI.toFloat() * 1.02f; sample(300f + (100f + wobble(i)) * cos(a), 300f + (100f + wobble(i)) * sin(a), i) }
        val fit = requireNotNull(StrokeRegularizer.fit(circle))
        assertEquals(StrokeRegularizer.Kind.CIRCLE, fit.kind)
        val shape = fit.shape as HeldShape.Ellipse
        assertEquals(300f, shape.cx, 3f); assertEquals(300f, shape.cy, 3f); assertEquals(100f, shape.a, 4f)
        assertTrue("every sample is on the circle", fit.samples.all { abs(hypot(it.x - shape.cx, it.y - shape.cy) - shape.a) < .01f })
        assertEquals("the loop closes", fit.samples.first().x, fit.samples.last().x, .01f)

        val ellipse = List(121) { i -> val a = i / 120f * 2f * PI.toFloat(); sample(300f + 160f * cos(a) + wobble(i), 300f + 70f * sin(a), i) }
        val oval = requireNotNull(StrokeRegularizer.fit(ellipse))
        assertEquals(StrokeRegularizer.Kind.ELLIPSE, oval.kind)
        val e = oval.shape as HeldShape.Ellipse
        assertEquals(160f, maxOf(e.a, e.b), 8f); assertEquals(70f, minOf(e.a, e.b), 6f)
        assertEquals("starts where the pen started", 460f, oval.samples.first().x, 6f)
    }

    @Test fun heldArcsStayCircular() {
        val arc = List(61) { i -> val a = PI.toFloat() * (1f + i / 60f * .8f); sample(400f + (150f + wobble(i)) * cos(a), 400f + (150f + wobble(i)) * sin(a), i) }
        val fit = requireNotNull(StrokeRegularizer.fit(arc))
        assertEquals(StrokeRegularizer.Kind.ARC, fit.kind)
        val shape = fit.shape as HeldShape.Arc
        assertEquals(150f, shape.radius, 4f)
        assertEquals(arc.first().x, fit.samples.first().x, 3f); assertEquals(arc.last().y, fit.samples.last().y, 4f)
    }

    @Test fun shapesAreReshapedByTheirHandles() {
        val arc = requireNotNull(HeldShape.Arc.through(0f to 100f, 100f to 0f, 200f to 100f))
        assertEquals(100f, arc.radius, .01f)
        val raised = arc.withHandle(1, 100f, 50f) as HeldShape.Arc
        assertEquals("the arc still passes the moved middle", 50f, raised.point(.5f).second, .1f)
        assertEquals(0f, raised.point(0f).first, .1f); assertEquals(200f, raised.point(1f).first, .1f)

        val circle = HeldShape.Ellipse(0f, 0f, 50f, 50f, 0f, 0f, 1f, circle = true)
        assertEquals(80f, (circle.withHandle(1, 0f, 80f) as HeldShape.Ellipse).b, .01f)
        val ellipse = HeldShape.Ellipse(0f, 0f, 100f, 40f, 0f, 0f, 1f, circle = false)
        val turned = ellipse.withHandle(1, 0f, 120f) as HeldShape.Ellipse
        assertEquals(120f, turned.a, .01f); assertEquals(PI.toFloat() / 2f, turned.rotation, .001f)
        assertEquals(40f, turned.b, .01f)
        val curve = HeldShape.Quadratic(0f, 0f, 50f, 100f, 100f, 0f)
        assertEquals(30f, curve.withHandle(1, 50f, 30f).point(.5f).second, .01f)
        val samples = curve.samples(List(10) { sample(it * 10f, 0f, it) })
        assertEquals(100f, samples.last().x, .01f)
    }
}
