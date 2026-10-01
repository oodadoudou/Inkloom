package app.inkloom.engine.v5.composite

import app.inkloom.document.AdjustmentCurvePoint
import org.junit.Assert.*
import org.junit.Test

class PreparedToneCurveTest {
    @Test fun channelCurvesComposeWithRgbMasterAndRoundTripExactly() {
        val value = app.inkloom.document.LayerAdjustment(app.inkloom.document.LayerAdjustmentKind.CURVES,
            curve = listOf(AdjustmentCurvePoint(0f, .2f), AdjustmentCurvePoint(1f, .8f)),
            redCurve = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(.5f, 0f), AdjustmentCurvePoint(1f, 1f)),
            greenCurve = listOf(AdjustmentCurvePoint(0f, 1f), AdjustmentCurvePoint(1f, 1f)),
            blueCurve = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(.4f, .8f), AdjustmentCurvePoint(1f, 1f)),
            blueCurveSmooth = true)
        val math = PixelAdjustmentMath.apply(.5f, .2f, .4f, value)
        assertArrayEquals(floatArrayOf(.2f, .8f, .68f), math, .00001f)
        val kernel = PixelAdjustmentKernel(value); val actual = FloatArray(3)
        for (i in 0..100) {
            kernel.applyInto(i / 100f, .2f, .4f, actual)
            assertArrayEquals(PixelAdjustmentMath.apply(i / 100f, .2f, .4f, value), actual, .00001f)
        }
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val encoded = json.encodeToString(app.inkloom.document.LayerAdjustment.serializer(), value)
        assertEquals(value, json.decodeFromString(app.inkloom.document.LayerAdjustment.serializer(), encoded))
        val old = json.decodeFromString(app.inkloom.document.LayerAdjustment.serializer(), """{"kind":"CURVES","curve":[{"input":0.0,"output":0.0},{"input":1.0,"output":1.0}]}""")
        assertFalse(old.curveSmooth)
        assertArrayEquals(floatArrayOf(.3f, .6f, .9f), PixelAdjustmentMath.apply(.3f, .6f, .9f, old), .00001f)
    }

    @Test fun linearSegmentsMatchPointsAndConstantTails() {
        val curve = PreparedToneCurve(listOf(AdjustmentCurvePoint(.2f, .3f), AdjustmentCurvePoint(.8f, .7f)))
        assertEquals(.3f, curve.sample(0f), 0f)
        assertEquals(.3f, curve.sample(.2f), 0f)
        assertEquals(.5f, curve.sample(.5f), .00001f)
        assertEquals(.7f, curve.sample(.8f), 0f)
        assertEquals(.7f, curve.sample(1f), 0f)
    }

    @Test fun smoothInterpolationIsContinuousAndDoesNotOvershootAnySegment() {
        val points = listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(.2f, .8f),
            AdjustmentCurvePoint(.6f, .3f), AdjustmentCurvePoint(1f, 1f))
        val curve = PreparedToneCurve(points, true)
        points.forEach { assertEquals(it.output, curve.sample(it.input), .00001f) }
        for (i in 0 until points.lastIndex) {
            val left = points[i]; val right = points[i + 1]
            for (n in 0..100) {
                val output = curve.sample(left.input + (right.input - left.input) * n / 100f)
                assertTrue(output >= minOf(left.output, right.output) - .00001f)
                assertTrue(output <= maxOf(left.output, right.output) + .00001f)
            }
        }
        assertEquals(curve.sample(.2f - .00001f), curve.sample(.2f + .00001f), .0001f)
    }

    @Test fun corruptPointsCannotProduceNonFinitePixelsOrCollapsedInputIntervals() {
        val curve = PreparedToneCurve(listOf(AdjustmentCurvePoint(Float.NaN, 0f),
            AdjustmentCurvePoint(-1f, 2f), AdjustmentCurvePoint(0f, 1f),
            AdjustmentCurvePoint(Float.MIN_VALUE, 0f), AdjustmentCurvePoint(.8f, .4f),
            AdjustmentCurvePoint(1f, -1f), AdjustmentCurvePoint(Float.POSITIVE_INFINITY, 0f)), true)
        assertTrue(curve.points.zipWithNext().all { (a, b) -> a.input < b.input })
        for (i in -10..110) assertTrue(curve.sample(i / 100f).let { it.isFinite() && it in 0f..1f })
        assertEquals(0f, PreparedToneCurve(emptyList(), true).sample(0f), 0f)
        assertEquals(1f, PreparedToneCurve(emptyList(), true).sample(1f), 0f)
    }
}
