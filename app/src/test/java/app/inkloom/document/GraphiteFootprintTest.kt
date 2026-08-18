package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PENCIL_RENDERER_V4: pins the graphite-footprint runtime semantics at the pure-Kotlin level:
 *  - tilt->width and tilt->grain are real per-dab factors, gated on an established tilt sensor
 *    (an unknown first sample stays neutral, factor 1);
 *  - the procedural paper grain is deterministic and anchored in document coordinates
 *    (same page position -> same value, independent of stroke or dab order).
 */
class GraphiteFootprintTest {
    private fun stroke(block: InkStroke.() -> InkStroke = { this }): InkStroke = InkStroke(
        id = "graphite-footprint",
        brushId = "system-hb-pencil",
        points = listOf(StrokePoint(.5f, .5f)),
        width = 24f,
        graphiteTiltWidth = 1f,
        graphiteTiltWidthStart = .2f,
        graphiteTiltGrain = 1f,
        graphiteTiltGrainStart = .2f,
        proceduralPaperGrainEnabled = true,
        proceduralPaperGrainScale = 50f,
        proceduralPaperGrainDensity = .3f,
    )

    @Test fun unknownTiltStaysNeutralForWidthAndGrain() {
        val unknown = stroke().copy(graphiteTiltWidth = 1f, graphiteTiltGrain = 1f)
        val point = StrokePoint(.5f, .5f, tilt = 1.3f, sensorFlags = 0)
        assertEquals("Unknown tilt must not widen the footprint", 1f, GraphiteFootprint.tiltWidthFactor(unknown, point), 1e-4f)
        assertEquals("Unknown tilt must not boost grain density", 1f, GraphiteFootprint.tiltGrainDensity(unknown, point), 1e-4f)
    }

    @Test fun establishedTiltWidensFootprintAndBoostsGrain() {
        val point = StrokePoint(.5f, .5f, tilt = (Math.PI / 2).toFloat(), sensorFlags = BrushSensorFlags.ALL)
        val width = GraphiteFootprint.tiltWidthFactor(stroke(), point)
        val grain = GraphiteFootprint.tiltGrainDensity(stroke(), point)
        assertTrue("Established tilt must widen the footprint (width=$width)", width > 1f)
        assertTrue("Established tilt must boost grain density (grain=$grain)", grain > 1f)
    }

    @Test fun disabledFieldsStayNeutral() {
        val plain = stroke().copy(graphiteTiltWidth = 0f, graphiteTiltGrain = 0f)
        val point = StrokePoint(.5f, .5f, tilt = (Math.PI / 2).toFloat(), sensorFlags = BrushSensorFlags.ALL)
        assertEquals(1f, GraphiteFootprint.tiltWidthFactor(plain, point), 1e-4f)
        assertEquals(1f, GraphiteFootprint.tiltGrainDensity(plain, point), 1e-4f)
    }

    @Test fun paperGrainIsDeterministicAndDocumentAnchored() {
        val first = GraphiteFootprint.paperGrainAlpha(stroke(), 123f, 456f)
        val second = GraphiteFootprint.paperGrainAlpha(stroke(), 123f, 456f)
        assertEquals("Same document position must produce identical grain", first, second, 1e-6f)
    }

    @Test fun paperGrainVariesAcrossDocumentCoordinates() {
        val values = (0..63).map { offset ->
            GraphiteFootprint.paperGrainAlpha(stroke(), offset * 7.3f, offset * 11.1f)
        }
        val distinct = values.distinct()
        assertTrue(
            "Document-anchored grain must vary across page positions (distinct=${distinct.size}/64)",
            distinct.size >= 4,
        )
        values.forEach { value ->
            assertTrue("Grain multiplier must stay in (0,1]: $value", value in 0f..1f)
        }
    }

    @Test fun paperGrainIgnoresStrokeAndDabOrder() {
        val a = GraphiteFootprint.paperGrainAlpha(stroke(), 999f, 777f)
        val b = GraphiteFootprint.paperGrainAlpha(stroke().copy(renderSeed = 1), 999f, 777f)
        // renderSeed is excluded from the document-anchored grain field so overlapping dabs in one
        // page share one paper surface regardless of which stroke drew them.
        assertEquals(a, b, 1e-6f)
    }

    @Test fun paperGrainRespectsDensityFloor() {
        val dense = stroke().copy(proceduralPaperGrainDensity = .5f)
        val values = (0..255).map { offset -> GraphiteFootprint.paperGrainAlpha(dense, offset * 3.1f, offset * 7.7f) }
        assertTrue("A dense grain must still deposit ink everywhere", values.all { it > 0f })
        val range = values.maxOrNull()!! - values.minOrNull()!!
        assertTrue("Dense grain must still modulate alpha (range=$range)", range > .05f)
    }

    @Test fun disabledPaperGrainReturnsNeutralAlpha() {
        val plain = stroke().copy(proceduralPaperGrainEnabled = false)
        assertEquals(1f, GraphiteFootprint.paperGrainAlpha(plain, 1f, 1f), 1e-6f)
        assertEquals(1f, GraphiteFootprint.paperGrainAlpha(plain, 4321f, 1234f), 1e-6f)
    }

    @Test fun grainScaleChangesFieldResolution() {
        val small = GraphiteFootprint.paperGrainAlpha(stroke().copy(proceduralPaperGrainScale = 1f), 1f, 1f)
        val large = GraphiteFootprint.paperGrainAlpha(stroke().copy(proceduralPaperGrainScale = 4000f), 1f, 1f)
        assertNotEquals("Scale must change the sampled grain", small, large, 1e-6f)
    }
}
