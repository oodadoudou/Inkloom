package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class GradientFillTest {
    private val stops = listOf(
        GradientColorStop(0f, 0xFF000000L),
        GradientColorStop(.5f, 0xFFFF0000L),
        GradientColorStop(1f, 0xFFFFFFFFL),
    )

    @Test fun multiStopSamplingInterpolatesTheNeighbouringSegment() {
        val color = GradientColors.sample(stops, .25f, GradientEdgeMode.CLAMP)
        assertEquals(127, color ushr 16 and 0xFF)
        assertEquals(0, color ushr 8 and 0xFF)
        assertEquals(0, color and 0xFF)
    }

    @Test fun repeatMirrorClampAndTransparentEdgesRemainDistinct() {
        assertEquals(GradientColors.sample(stops, .25f, GradientEdgeMode.CLAMP), GradientColors.sample(stops, 1.25f, GradientEdgeMode.REPEAT))
        assertEquals(GradientColors.sample(stops, .75f, GradientEdgeMode.CLAMP), GradientColors.sample(stops, 1.25f, GradientEdgeMode.MIRROR))
        assertEquals(stops.last().colorArgb.toInt(), GradientColors.sample(stops, 1.25f, GradientEdgeMode.CLAMP))
        assertEquals(0, GradientColors.sample(stops, 1.25f, GradientEdgeMode.TRANSPARENT))
    }

    @Test fun reverseReflectsPositionsWithoutMutatingStoredStops() {
        val definition = GradientDefinition(reverse = true, stops = stops)
        val resolved = GradientColors.resolved(definition)
        assertEquals(stops.last().colorArgb, resolved.first().colorArgb)
        assertEquals(stops.first().colorArgb, resolved.last().colorArgb)
        assertEquals(0xFF000000L, stops.first().colorArgb)
    }

    @Test fun malformedStopListsNormalizeToAtLeastTwoStops() {
        val normalized = GradientColors.normalize(listOf(GradientColorStop(Float.NaN, 0L)))
        assertEquals(2, normalized.size)
        assertEquals(0f, normalized.first().position, 0f)
        assertEquals(1f, normalized.last().position, 0f)
    }

    @Test fun stopCodecRoundTripsPositionsAndUnsignedColors() {
        assertEquals(stops, GradientStopCodec.decode(GradientStopCodec.encode(stops)))
        assertEquals(2, GradientStopCodec.decode("broken").size)
    }
}
