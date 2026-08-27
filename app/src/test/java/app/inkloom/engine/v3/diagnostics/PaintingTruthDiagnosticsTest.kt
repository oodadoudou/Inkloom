package app.inkloom.engine.v3.diagnostics

import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.engine.v5.coordinates.ViewportFrameId
import app.inkloom.engine.v3.render.StrokeRenderPacket
import org.junit.Assert.assertEquals
import org.junit.Test

class PaintingTruthDiagnosticsTest {
    @Test fun exactPacketHandoffDoesNotReportAMismatch() {
        PaintingTruthDiagnostics.reset()
        val packet = packet(1L, 1f, 7)
        PaintingTruthDiagnostics.compareHandoff(packet, packet)
        assertEquals(0, PaintingTruthDiagnostics.snapshot().totalMismatchCount)
    }

    @Test fun geometryMaterialAndTransformDivergenceAreCountedSeparately() {
        PaintingTruthDiagnostics.reset()
        PaintingTruthDiagnostics.compareHandoff(packet(1L, 1f, 7), packet(2L, 2f, 8, 2L))
        val result = PaintingTruthDiagnostics.snapshot()
        assertEquals(1, result.geometryMismatchCount)
        assertEquals(1, result.materialMismatchCount)
        assertEquals(1, result.transformMismatchCount)
    }

    private fun packet(sequence: Long, x: Float, seed: Int, frame: Long = 1L) = StrokeRenderPacket.create(
        sequence,
        ViewportFrameId(frame),
        listOf(InkStroke(id = "s", points = listOf(StrokePoint(x, .5f)), renderSeed = seed)),
    )
}
