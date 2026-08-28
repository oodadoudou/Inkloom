package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.stroke.PaintBatch

enum class PaintOpKind { DRY_INK, PENCIL, ERASER, AIRBRUSH, DECORATION, WET, SMUDGE, LIQUIFY }

data class PaintOpCapabilities(
    val kind: PaintOpKind,
    val destinationSampling: Boolean,
    val evolvesAfterInput: Boolean,
    val maximumHaloPixels: Int,
) {
    init {
        require(maximumHaloPixels >= 0)
        if (kind in setOf(PaintOpKind.DRY_INK, PaintOpKind.PENCIL, PaintOpKind.ERASER)) {
            require(!evolvesAfterInput) { "$kind cannot evolve after input" }
        }
    }
}

data class PaintResult(
    val transactionId: Long,
    val batchOrdinal: Long,
    val changed: Boolean,
    /** Exact document-pixel damage produced by this batch; null means no pixels changed. */
    val dirtyBounds: PixelRect? = null,
    val documentGeneration: Long? = null,
)

interface PaintOp {
    val capabilities: PaintOpCapabilities
    fun begin(context: PaintOpContext): PaintOpSession
}

data class PaintOpContext(
    val transactionId: Long,
    val backdropGeneration: Long,
    val brushRuntimeId: String,
) {
    init {
        require(transactionId > 0L)
        require(backdropGeneration >= 0L)
        require(brushRuntimeId.isNotBlank())
    }
}

interface PaintOpSession {
    fun apply(batch: PaintBatch): PaintResult
    fun finish(batch: PaintBatch): PaintResult
    fun cancel()
}

/**
 * A gesture-local paint session whose preview is the exact sparse transaction that publication
 * commits. Implementations must not construct a second render product in [publish].
 */
interface PublishablePaintOpSession : PaintOpSession {
    val presentationKind: PaintPresentationKind get() = PaintPresentationKind.COMPOSITE_REPLACEMENT
    fun previewSnapshot(): PixelTileSurfaceSnapshot
    fun publish(): PublishedPaintDelta
}

enum class PaintPresentationKind { COMPOSITE_REPLACEMENT, SOURCE_DELTA, ERASE_COVERAGE }
