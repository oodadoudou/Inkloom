package app.inkloom.engine.v5.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import app.inkloom.engine.v5.android.PixelTileBitmapAdapter
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord

/**
 * One atomic raster-tool gesture over the authoritative sparse tile surface.
 *
 * Android bitmaps are bounded scratch/rendering resources only. Every edit samples the active
 * transaction and writes the changed rectangle back into copy-on-write tiles. Production presents
 * [previewSnapshot] directly; no page-sized mutable display mirror exists.
 */
class RasterTileEditSession(
    private val surface: PixelTileSurface,
    label: String,
) : AutoCloseable {
    private val original: app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
    private val transaction: app.inkloom.engine.v5.surface.PixelTileWriteTransaction
    private val sourcePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    private val fillPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    private var state = State.ACTIVE
    private var dirty: PixelRect? = null
    private val reusableSamples = scratchCache()
    private val reusableOutputs = scratchCache()

    init {
        original = surface.snapshot()
        transaction = surface.beginWrite(label)
    }

    val dirtyBounds: PixelRect? get() = dirty
    val touchedTileCount: Int get() = transaction.touchedTileCount
    val workingByteSize: Long get() = transaction.workingByteSize
    internal var lastRoiPlan: RasterRoiPlan? = null
        private set

    /** Current private gesture pixels for real-time presentation; document truth stays atomic. */
    fun previewSnapshot(): PixelTileSurfaceSnapshot {
        requireActive()
        return transaction.previewSnapshot()
    }

    /** Edits [bounds] in region-local coordinates. */
    fun edit(bounds: PixelRect, operation: (Bitmap) -> Unit): PixelRect? {
        requireActive()
        val plan = RasterRoiPlan.writeOnly(
            bounds,
            surface.logicalWidth,
            surface.logicalHeight,
            surface.tileSize,
        )
        lastRoiPlan = plan
        val clipped = plan.writeBounds
        if (clipped.isEmpty) return null
        val scratch = PixelTileBitmapAdapter.render(transaction, clipped)
        try {
            operation(scratch)
            PixelTileBitmapAdapter.replaceRegion(transaction, clipped, scratch)
        } finally {
            scratch.recycle()
        }
        dirty = dirty?.union(clipped) ?: clipped
        return clipped
    }

    /**
     * Applies a tile-independent kernel. Bitmap work may run on the bounded ROI worker pool, while
     * transaction reads/publication remain ordered so one gesture is still atomic.
     */
    internal fun editIndependent(
        bounds: PixelRect,
        operation: (bitmap: Bitmap, globalBounds: PixelRect) -> Unit,
    ): PixelRect? {
        requireActive()
        val plan = RasterRoiPlan.writeOnly(
            bounds,
            surface.logicalWidth,
            surface.logicalHeight,
            surface.tileSize,
            dependency = RasterDependency.INDEPENDENT,
        )
        lastRoiPlan = plan
        if (plan.writeBounds.isEmpty) return null
        plan.writeUnits.chunked(RasterRoiScheduler.maxConcurrency).forEach { batch ->
            val scratch = batch.associateWith { unit ->
                PixelTileBitmapAdapter.render(transaction, unit.writeBounds)
            }
            try {
                RasterRoiScheduler.map(plan, batch) { unit ->
                    operation(requireNotNull(scratch[unit]), unit.writeBounds)
                    unit
                }
                batch.forEach { unit ->
                    val bitmap = requireNotNull(scratch[unit])
                    PixelTileBitmapAdapter.replaceRegion(transaction, unit.writeBounds, bitmap)
                    dirty = dirty?.union(unit.writeBounds) ?: unit.writeBounds
                }
            } finally {
                scratch.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            }
        }
        return plan.writeBounds
    }

    internal fun editWithRoi(
        plan: RasterRoiPlan,
        sampleGeneration: RasterSampleGeneration = RasterSampleGeneration.CURRENT,
        operation: (sample: Bitmap, sampleGlobalBounds: PixelRect, output: Bitmap) -> Unit,
    ): PixelRect? {
        requireActive()
        require(plan.logicalBounds == PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight)) {
            "ROI plan belongs to another logical surface"
        }
        val requestedRead = requireNotNull(plan.requestedReadBounds) { "Sampled edit requires a read ROI" }
        lastRoiPlan = plan
        if (plan.writeBounds.isEmpty) return null
        val sample = materializeRead(plan, sampleGeneration)
        val output = PixelTileBitmapAdapter.render(transaction, plan.writeBounds)
        try {
            operation(sample, requestedRead, output)
            PixelTileBitmapAdapter.replaceRegion(transaction, plan.writeBounds, output)
        } finally {
            sample.recycle()
            output.recycle()
        }
        dirty = dirty?.union(plan.writeBounds) ?: plan.writeBounds
        return plan.writeBounds
    }

    /** Allocation-stable sampled edit for high-frequency tools whose ROI remains on-page. */
    internal fun editWithReusableRoi(
        plan: RasterRoiPlan,
        sampleGeneration: RasterSampleGeneration = RasterSampleGeneration.CURRENT,
        operation: (sample: Bitmap, sampleGlobalBounds: PixelRect, output: Bitmap) -> Unit,
    ): PixelRect? {
        requireActive()
        val requestedRead = requireNotNull(plan.requestedReadBounds)
        val clippedRead = plan.clippedReadBounds
        if (clippedRead != requestedRead) return editWithRoi(plan, sampleGeneration, operation)
        lastRoiPlan = plan
        if (plan.writeBounds.isEmpty) return null
        val sample = scratch(reusableSamples, requestedRead.width, requestedRead.height)
        when (sampleGeneration) {
            RasterSampleGeneration.CURRENT -> PixelTileBitmapAdapter.renderInto(
                transaction, requestedRead, sample.bitmap, sample.pixels,
            )
            RasterSampleGeneration.ORIGINAL -> PixelTileBitmapAdapter.renderInto(
                original, requestedRead, sample.bitmap, sample.pixels,
            )
        }
        val output = scratch(reusableOutputs, plan.writeBounds.width, plan.writeBounds.height)
        PixelTileBitmapAdapter.renderInto(transaction, plan.writeBounds, output.bitmap, output.pixels)
        operation(sample.bitmap, requestedRead, output.bitmap)
        PixelTileBitmapAdapter.replaceRegion(transaction, plan.writeBounds, output.bitmap, output.pixels)
        dirty = dirty?.union(plan.writeBounds) ?: plan.writeBounds
        return plan.writeBounds
    }

    /** Materializes only a bounded region of the current private transaction for sampling. */
    internal fun sampleCurrent(bounds: PixelRect): Bitmap? {
        requireActive()
        val clipped = bounds.intersect(PixelRect(0, 0, surface.logicalWidth, surface.logicalHeight))
        if (clipped.isEmpty) return null
        return PixelTileBitmapAdapter.render(transaction, clipped)
    }

    private fun materializeRead(plan: RasterRoiPlan, generation: RasterSampleGeneration): Bitmap {
        val requested = requireNotNull(plan.requestedReadBounds)
        val clipped = plan.clippedReadBounds
        if (clipped == requested) return renderGeneration(generation, requested)
        val output = Bitmap.createBitmap(requested.width, requested.height, Bitmap.Config.ARGB_8888)
        if (clipped == null && plan.abyssPolicy == RasterAbyssPolicy.TRANSPARENT) return output
        val sourceBounds: PixelRect
        val offsetX: Int
        val offsetY: Int
        if (plan.abyssPolicy == RasterAbyssPolicy.CLAMP_TO_EDGE) {
            val horizontal = clampedAxis(requested.left, requested.right, surface.logicalWidth)
            val vertical = clampedAxis(requested.top, requested.bottom, surface.logicalHeight)
            sourceBounds = PixelRect(horizontal.sourceStart, vertical.sourceStart, horizontal.sourceEnd, vertical.sourceEnd)
            offsetX = horizontal.destinationOffset
            offsetY = vertical.destinationOffset
        } else {
            sourceBounds = requireNotNull(clipped)
            offsetX = clipped.left - requested.left
            offsetY = clipped.top - requested.top
        }
        val inside = renderGeneration(generation, sourceBounds)
        try {
            val canvas = Canvas(output)
            canvas.drawBitmap(inside, offsetX.toFloat(), offsetY.toFloat(), sourcePaint)
            if (plan.abyssPolicy == RasterAbyssPolicy.CLAMP_TO_EDGE) {
                extendClampedEdges(canvas, inside, offsetX, offsetY, output.width, output.height)
            }
        } finally {
            inside.recycle()
        }
        return output
    }

    private fun renderGeneration(generation: RasterSampleGeneration, bounds: PixelRect): Bitmap = when (generation) {
        RasterSampleGeneration.CURRENT -> PixelTileBitmapAdapter.render(transaction, bounds)
        RasterSampleGeneration.ORIGINAL -> PixelTileBitmapAdapter.render(original, bounds)
    }

    private fun extendClampedEdges(
        canvas: Canvas,
        inside: Bitmap,
        offsetX: Int,
        offsetY: Int,
        outputWidth: Int,
        outputHeight: Int,
    ) {
        val sourceWidth = inside.width
        val sourceHeight = inside.height
        if (offsetX > 0) canvas.drawBitmap(
            inside,
            Rect(0, 0, 1, sourceHeight),
            Rect(0, offsetY, offsetX, offsetY + sourceHeight),
            sourcePaint,
        )
        val rightStart = offsetX + sourceWidth
        if (rightStart < outputWidth) canvas.drawBitmap(
            inside,
            Rect(sourceWidth - 1, 0, sourceWidth, sourceHeight),
            Rect(rightStart, offsetY, outputWidth, offsetY + sourceHeight),
            sourcePaint,
        )
        if (offsetY > 0) canvas.drawBitmap(
            inside,
            Rect(0, 0, sourceWidth, 1),
            Rect(offsetX, 0, offsetX + sourceWidth, offsetY),
            sourcePaint,
        )
        val bottomStart = offsetY + sourceHeight
        if (bottomStart < outputHeight) canvas.drawBitmap(
            inside,
            Rect(0, sourceHeight - 1, sourceWidth, sourceHeight),
            Rect(offsetX, bottomStart, offsetX + sourceWidth, outputHeight),
            sourcePaint,
        )
        if (offsetX > 0 && offsetY > 0) fillRect(canvas, 0, 0, offsetX, offsetY, inside.getPixel(0, 0))
        if (rightStart < outputWidth && offsetY > 0) {
            fillRect(canvas, rightStart, 0, outputWidth, offsetY, inside.getPixel(sourceWidth - 1, 0))
        }
        if (offsetX > 0 && bottomStart < outputHeight) {
            fillRect(canvas, 0, bottomStart, offsetX, outputHeight, inside.getPixel(0, sourceHeight - 1))
        }
        if (rightStart < outputWidth && bottomStart < outputHeight) {
            fillRect(
                canvas,
                rightStart,
                bottomStart,
                outputWidth,
                outputHeight,
                inside.getPixel(sourceWidth - 1, sourceHeight - 1),
            )
        }
    }

    private fun fillRect(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        fillPaint.color = color
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), fillPaint)
    }

    private fun clampedAxis(start: Int, end: Int, limit: Int): ClampedAxis = when {
        end <= 0 -> ClampedAxis(0, 1, 0)
        start >= limit -> ClampedAxis(limit - 1, limit, end - start - 1)
        else -> ClampedAxis(
            sourceStart = start.coerceAtLeast(0),
            sourceEnd = end.coerceAtMost(limit),
            destinationOffset = (-start).coerceAtLeast(0),
        )
    }

    private data class ClampedAxis(
        val sourceStart: Int,
        val sourceEnd: Int,
        val destinationOffset: Int,
    )

    fun commit(): PixelTileTransactionRecord {
        requireActive()
        state = State.COMMITTED
        return transaction.commit()
    }

    fun cancel() {
        requireActive()
        transaction.cancel()
        state = State.CANCELLED
    }

    override fun close() {
        if (state == State.ACTIVE) cancel()
        reusableSamples.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        reusableOutputs.values.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        reusableSamples.clear()
        reusableOutputs.clear()
    }

    private fun requireActive() {
        check(state == State.ACTIVE) { "Raster tile edit session is $state" }
    }

    private enum class State { ACTIVE, COMMITTED, CANCELLED }

    private data class BitmapScratch(val bitmap: Bitmap, val pixels: IntArray)

    private fun scratch(
        cache: MutableMap<Long, BitmapScratch>,
        width: Int,
        height: Int,
    ): BitmapScratch {
        val key = (width.toLong() shl 32) or height.toLong()
        return cache[key] ?: BitmapScratch(
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888),
            IntArray(width * height),
        ).also { cache[key] = it }
    }

    private fun scratchCache() = object : LinkedHashMap<Long, BitmapScratch>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, BitmapScratch>?): Boolean {
            val remove = size > MAX_REUSABLE_SCRATCHES
            if (remove) eldest?.value?.bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            return remove
        }
    }

    private companion object {
        const val MAX_REUSABLE_SCRATCHES = 12
    }
}

internal enum class RasterSampleGeneration { CURRENT, ORIGINAL }
