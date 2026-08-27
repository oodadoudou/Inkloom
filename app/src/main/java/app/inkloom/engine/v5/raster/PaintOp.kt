package app.inkloom.engine.v5.raster

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

