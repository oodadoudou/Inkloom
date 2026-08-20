package app.inkloom.engine.v3.diagnostics

import app.inkloom.engine.v3.render.StrokeRenderPacket
import java.util.concurrent.atomic.AtomicInteger

internal object PaintingTruthDiagnostics {
    data class Snapshot(
        val geometryMismatchCount: Int,
        val materialMismatchCount: Int,
        val transformMismatchCount: Int,
        val presentationGapCount: Int,
        val fullCanvasInvalidationCount: Int,
    ) {
        val totalMismatchCount: Int
            get() = geometryMismatchCount + materialMismatchCount + transformMismatchCount
    }

    private val geometryMismatch = AtomicInteger()
    private val materialMismatch = AtomicInteger()
    private val transformMismatch = AtomicInteger()
    private val presentationGap = AtomicInteger()
    private val fullCanvasInvalidation = AtomicInteger()

    fun compareHandoff(rendered: StrokeRenderPacket?, committed: StrokeRenderPacket) {
        if (rendered?.geometryFingerprint != committed.geometryFingerprint) geometryMismatch.incrementAndGet()
        if (rendered?.materialFingerprint != committed.materialFingerprint) materialMismatch.incrementAndGet()
        if (rendered?.transformFrameId != committed.transformFrameId) transformMismatch.incrementAndGet()
    }

    fun recordTransformMismatch() {
        transformMismatch.incrementAndGet()
    }

    fun recordPresentationGap() {
        presentationGap.incrementAndGet()
    }

    fun recordFullCanvasInvalidation() {
        fullCanvasInvalidation.incrementAndGet()
    }

    fun snapshot(): Snapshot = Snapshot(
        geometryMismatch.get(),
        materialMismatch.get(),
        transformMismatch.get(),
        presentationGap.get(),
        fullCanvasInvalidation.get(),
    )

    fun reset() {
        geometryMismatch.set(0)
        materialMismatch.set(0)
        transformMismatch.set(0)
        presentationGap.set(0)
        fullCanvasInvalidation.set(0)
    }
}
