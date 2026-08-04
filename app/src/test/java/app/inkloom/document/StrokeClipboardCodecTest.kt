package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeClipboardCodecTest {
    @Test fun roundTripPreservesCompleteBrushSnapshot() {
        val source = InkStroke(
            id = "source",
            renderSeed = 7_777,
            points = listOf(StrokePoint(.2f, .3f, pressure = .4f), StrokePoint(.7f, .8f, pressure = .9f)),
            width = 33f,
            brushId = "imported-sut",
            grainDensity = .72f,
            grainDensityDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .27f),
            dualBrush = true,
            dualCompositeMode = 7,
            dualApplyRgb = true,
            dualSizeDynamics = BrushParameterDynamics(
                pressureEnabled = true,
                pressureMinimum = .18f,
                pressureCurve = listOf(BrushCurvePoint(0f, .1f), BrushCurvePoint(1f, 1f)),
            ),
            dualSpacingDynamics = BrushParameterDynamics(
                secondary = BrushSecondaryDynamics(tiltEnabled = true, tiltMinimum = .33f),
            ),
            dualFlowDynamics = BrushParameterDynamics(
                secondary = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .44f),
            ),
            dualThicknessDynamics = BrushParameterDynamics(
                secondary = BrushSecondaryDynamics(randomEnabled = true, randomMinimum = .55f),
            ),
            watercolor = true,
        )

        val encoded = StrokeClipboardCodec.encode(listOf(source))!!
        assertTrue(encoded.contains("\"version\":1"))
        val restored = StrokeClipboardCodec.decode(encoded)!!.strokes.single()

        assertEquals(source, restored)
    }

    @Test fun pasteRegeneratesIdsTargetsActiveLayerAndKeepsGroupGeometry() {
        val first = InkStroke(id = "a", renderSeed = 11, layerId = "old", points = listOf(StrokePoint(.85f, .2f), StrokePoint(.95f, .3f)), masks = listOf(StrokeMaskDab(.9f, .25f, .04f, .02f, 35f)))
        val second = InkStroke(id = "b", renderSeed = 22, layerId = "other", points = listOf(StrokePoint(.4f, .7f)))
        val ids = ArrayDeque(listOf("copy-a", "copy-b"))

        val pasted = StrokeClipboardCodec.materialize(StrokeClipboardPayload(strokes = listOf(first, second)), "target", nextId = ids::removeFirst)

        assertEquals(listOf("copy-a", "copy-b"), pasted.map(InkStroke::id))
        assertEquals(listOf("target", "target"), pasted.map(InkStroke::layerId))
        assertEquals(listOf(11, 22), pasted.map(InkStroke::renderSeed))
        assertEquals(.88f, pasted.first().points.first().x, .0001f)
        assertEquals(.98f, pasted.first().points.last().x, .0001f)
        assertEquals(.43f, pasted.last().points.single().x, .0001f)
        assertEquals(.93f, pasted.first().masks.single().x, .0001f)
        assertEquals(.28f, pasted.first().masks.single().y, .0001f)
        assertEquals(.04f, pasted.first().masks.single().radiusX, .0001f)
        assertEquals(.02f, pasted.first().masks.single().radiusY, .0001f)
        assertEquals(35f, pasted.first().masks.single().rotationDegrees, .0001f)
    }

    @Test fun rejectsOversizedMalformedAndOutOfBoundsPayloads() {
        assertNull(StrokeClipboardCodec.decode("not-json"))
        assertNull(StrokeClipboardCodec.decode("x".repeat(2_000_001)))
        val invalid = """{"version":1,"strokes":[{"points":[{"x":2.0,"y":0.2}]}]}"""
        assertNull(StrokeClipboardCodec.decode(invalid))
        assertTrue(StrokeClipboardCodec.materialize(StrokeClipboardPayload(strokes = emptyList()), "target").isEmpty())
    }
}
