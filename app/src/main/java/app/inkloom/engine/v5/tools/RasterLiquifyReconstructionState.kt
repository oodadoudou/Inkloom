package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.PixelTileSurface

/**
 * Original pixels and cumulative warp while Liquify remains the current tool.
 * Pixel history stays per contact; foreign edits/undo/rebinding invalidate this transient baseline.
 */
class RasterLiquifyReconstructionState(private val surface: PixelTileSurface) {
    internal val original = surface.snapshot()
    internal var displacement = SparseDisplacementField(surface.logicalWidth, surface.logicalHeight)
        private set
    private var expectedRevision = surface.revision

    fun matches(candidate: PixelTileSurface): Boolean =
        candidate === surface && candidate.revision == expectedRevision

    internal fun accept(field: SparseDisplacementField) {
        displacement = field
        expectedRevision = surface.revision
    }
}
