package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import app.inkloom.engine.v3.android.PixelTileBitmapAdapter
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.surface.PixelTileTransactionRecord

/**
 * One atomic raster-tool gesture over the authoritative sparse tile surface.
 *
 * Android bitmaps are bounded scratch/rendering resources only. Every edit samples the active
 * transaction, writes the changed rectangle back into copy-on-write tiles, and mirrors that small
 * rectangle to [displayBitmap] for immediate Canvas rendering.
 */
class RasterTileEditSession(
    private val surface: PixelTileSurface,
    private val displayBitmap: Bitmap,
    label: String,
    private val mirrorWrites: Boolean = true,
    private val publishDirtyOnCommit: Boolean = !mirrorWrites,
) : AutoCloseable {
    private val original: app.inkloom.engine.v3.surface.PixelTileSurfaceSnapshot
    private val transaction: app.inkloom.engine.v3.surface.PixelTileWriteTransaction
    private val displayCanvas: Canvas?
    private val sourcePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    private var state = State.ACTIVE
    private var dirty: PixelRect? = null

    init {
        require(!displayBitmap.isRecycled) { "Display bitmap has been recycled" }
        require(displayBitmap.width == surface.logicalWidth && displayBitmap.height == surface.logicalHeight) {
            "Display bitmap and tile surface dimensions must match"
        }
        original = surface.snapshot()
        transaction = surface.beginWrite(label)
        displayCanvas = if (mirrorWrites || publishDirtyOnCommit) Canvas(displayBitmap) else null
    }

    val dirtyBounds: PixelRect? get() = dirty
    val touchedTileCount: Int get() = transaction.touchedTileCount
    val workingByteSize: Long get() = transaction.workingByteSize

    /** Edits [bounds] in region-local coordinates and publishes it to the display mirror. */
    fun edit(bounds: PixelRect, operation: (Bitmap) -> Unit): PixelRect? {
        requireActive()
        val clipped = bounds.intersect(PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight))
        if (clipped.isEmpty) return null
        val scratch = PixelTileBitmapAdapter.render(transaction, clipped)
        try {
            operation(scratch)
            PixelTileBitmapAdapter.replaceRegion(transaction, clipped, scratch)
            if (mirrorWrites) displayCanvas?.drawBitmap(
                scratch, null, Rect(clipped.left, clipped.top, clipped.right, clipped.bottom), sourcePaint,
            )
        } finally {
            scratch.recycle()
        }
        dirty = dirty?.union(clipped) ?: clipped
        return clipped
    }

    /**
     * Samples [sampleBounds] from the transaction's current generation and edits [destination].
     * This is the storage-neutral primitive used by pigment transport and displacement tools.
     */
    fun editWithSample(
        destination: PixelRect,
        sampleBounds: PixelRect,
        operation: (sample: Bitmap, sampleGlobalBounds: PixelRect, output: Bitmap) -> Unit,
    ): PixelRect? {
        requireActive()
        val logical = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        val clippedDestination = destination.intersect(logical)
        val clippedSample = sampleBounds.intersect(logical)
        if (clippedDestination.isEmpty || clippedSample.isEmpty) return null
        val sample = PixelTileBitmapAdapter.render(transaction, clippedSample)
        val output = PixelTileBitmapAdapter.render(transaction, clippedDestination)
        try {
            operation(sample, clippedSample, output)
            PixelTileBitmapAdapter.replaceRegion(transaction, clippedDestination, output)
            if (mirrorWrites) displayCanvas?.drawBitmap(
                output, null,
                Rect(clippedDestination.left, clippedDestination.top, clippedDestination.right, clippedDestination.bottom),
                sourcePaint,
            )
        } finally {
            sample.recycle()
            output.recycle()
        }
        dirty = dirty?.union(clippedDestination) ?: clippedDestination
        return clippedDestination
    }

    /** Same as [editWithSample], but samples the immutable gesture-start generation. */
    fun editWithOriginalSample(
        destination: PixelRect,
        sampleBounds: PixelRect,
        operation: (sample: Bitmap, sampleGlobalBounds: PixelRect, output: Bitmap) -> Unit,
    ): PixelRect? {
        requireActive()
        val logical = PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)
        val clippedDestination = destination.intersect(logical)
        val clippedSample = sampleBounds.intersect(logical)
        if (clippedDestination.isEmpty || clippedSample.isEmpty) return null
        val sample = PixelTileBitmapAdapter.render(original, clippedSample)
        val output = PixelTileBitmapAdapter.render(transaction, clippedDestination)
        try {
            operation(sample, clippedSample, output)
            PixelTileBitmapAdapter.replaceRegion(transaction, clippedDestination, output)
            if (mirrorWrites) displayCanvas?.drawBitmap(
                output, null,
                Rect(clippedDestination.left, clippedDestination.top, clippedDestination.right, clippedDestination.bottom),
                sourcePaint,
            )
        } finally {
            sample.recycle()
            output.recycle()
        }
        dirty = dirty?.union(clippedDestination) ?: clippedDestination
        return clippedDestination
    }

    fun commit(): PixelTileTransactionRecord {
        requireActive()
        state = State.COMMITTED
        val record = transaction.commit()
        if (publishDirtyOnCommit) dirty?.takeUnless(PixelRect::isEmpty)?.let { changed ->
            val published = PixelTileBitmapAdapter.render(surface.snapshot(), changed)
            try {
                displayCanvas?.drawBitmap(
                    published, null,
                    Rect(changed.left, changed.top, changed.right, changed.bottom),
                    sourcePaint,
                )
            } finally {
                published.recycle()
            }
        }
        return record
    }

    fun cancel() {
        requireActive()
        transaction.cancel()
        if (mirrorWrites) dirty?.takeUnless(PixelRect::isEmpty)?.let { changed ->
            val restored = PixelTileBitmapAdapter.render(original, changed)
            try {
                displayCanvas?.drawBitmap(
                    restored,
                    null,
                    Rect(changed.left, changed.top, changed.right, changed.bottom),
                    sourcePaint,
                )
            } finally {
                restored.recycle()
            }
        }
        state = State.CANCELLED
    }

    override fun close() {
        if (state == State.ACTIVE) cancel()
    }

    private fun requireActive() {
        check(state == State.ACTIVE) { "Raster tile edit session is $state" }
    }

    private enum class State { ACTIVE, COMMITTED, CANCELLED }
}
