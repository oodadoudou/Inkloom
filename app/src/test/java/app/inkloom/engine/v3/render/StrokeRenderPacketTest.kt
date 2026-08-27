package app.inkloom.engine.v3.render

import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v5.coordinates.ViewportFrameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StrokeRenderPacketTest {
    @Test fun packetFreezesGeometryAndMaterialIdentity() {
        val mutablePoints = mutableListOf(StrokePoint(.1f, .2f, pressure = .4f, timeMs = 1L))
        val stroke = InkStroke(
            id = "stroke",
            brushId = "pencil",
            points = mutablePoints,
            width = 20f,
            renderSeed = 91,
            proceduralPaperGrainEnabled = true,
            proceduralPaperGrainScale = 37f,
        )
        val first = StrokeRenderPacket.create(7L, ViewportFrameId(12L), listOf(stroke))
        val second = StrokeRenderPacket.create(8L, ViewportFrameId(12L), listOf(stroke))

        assertEquals(first.geometryFingerprint, second.geometryFingerprint)
        assertEquals(first.materialFingerprint, second.materialFingerprint)
        assertEquals(ViewportFrameId(12L), first.transformFrameId)

        mutablePoints += StrokePoint(.8f, .9f, pressure = .8f, timeMs = 2L)
        assertEquals("Packet must own an immutable point snapshot", 1, first.strokes.single().points.size)

        val changedGeometry = StrokeRenderPacket.create(
            9L,
            ViewportFrameId(12L),
            listOf(stroke.copy(points = listOf(StrokePoint(.2f, .2f, pressure = .4f, timeMs = 1L)))),
        )
        val changedMaterial = StrokeRenderPacket.create(
            10L,
            ViewportFrameId(12L),
            listOf(stroke.copy(renderSeed = 92)),
        )
        assertNotEquals(first.geometryFingerprint, changedGeometry.geometryFingerprint)
        assertNotEquals(first.materialFingerprint, changedMaterial.materialFingerprint)
    }
}

