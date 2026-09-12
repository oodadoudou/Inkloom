package app.inkloom.engine.v5.tools

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

enum class RasterBlendMode { SMEAR, MIX }

/** Pigment transport directly over one sparse tile transaction. */
class RasterTileBlendSession private constructor(
    surface: PixelTileSurface,
    private val selection: RasterSelectionMask,
    private val preserveDestinationAlpha: Boolean,
    private val mode: RasterBlendMode,
) : AutoCloseable {
    constructor(
        surface: PixelTileSurface,
        preserveDestinationAlpha: Boolean = false,
        mode: RasterBlendMode = RasterBlendMode.SMEAR,
    ) : this(
        surface,
        RasterSelectionMask(null, surface.logicalWidth, surface.logicalHeight),
        preserveDestinationAlpha,
        mode,
    )

    internal constructor(
        surface: PixelTileSurface,
        selectionMask: MaskTileSurfaceSnapshot?,
        preserveDestinationAlpha: Boolean = false,
        mode: RasterBlendMode = RasterBlendMode.SMEAR,
    ) : this(
        surface,
        RasterSelectionMask(selectionMask, surface.logicalWidth, surface.logicalHeight),
        preserveDestinationAlpha,
        mode,
    )

    private val edits = RasterTileEditSession(surface, "raster blend")
    private var transportBitmap: Bitmap? = null
    private var transportCanvas: Canvas? = null
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private val reservoir = PigmentBucketReservoir()
    private val reservoirPixels = IntArray(reservoir.bucketCount)
    private val reservoirBitmap = Bitmap.createBitmap(reservoir.columns, reservoir.rows, Bitmap.Config.ARGB_8888)
    private var pickupPixels = IntArray(0)
    private var pickupDistance = Float.POSITIVE_INFINITY
    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val depositPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val reservoirPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mixingMask = RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.WHITE, 0xeeffffff.toInt(), 0x80ffffff.toInt(), 0x17ffffff, Color.TRANSPARENT),
        floatArrayOf(0f, .25f, .5f, .75f, 1f), Shader.TileMode.CLAMP)
    private val mixingMatrix = Matrix()
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val localRect = Rect()
    private val width = surface.logicalWidth
    private val height = surface.logicalHeight
    private val tileSize = surface.tileSize
    private var completed = false

    val dirtyBounds: PixelRect? get() = edits.dirtyBounds
    val touchedTileCount: Int get() = edits.touchedTileCount
    val workingByteSize: Long get() = edits.workingByteSize
    internal val pigmentBucketCount: Int get() = reservoir.bucketCount
    internal val pigmentReservoirByteSize: Long get() = reservoir.retainedByteSize
    internal val lastRoiPlan: RasterRoiPlan? get() = edits.lastRoiPlan
    fun previewSnapshot(): PixelTileSurfaceSnapshot = edits.previewSnapshot()

    fun blend(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radiusPixels: Float,
        strength: Float,
        hardness: Float,
    ): Rect? {
        check(!completed) { "Raster tile blend session is complete" }
        if (radiusPixels <= .25f) return null
        val radius = radiusPixels.coerceIn(.5f, minOf(width, height).toFloat())
        val deltaX = currentX - previousX
        val deltaY = currentY - previousY
        val distance = hypot(deltaX, deltaY)
        val targetChunkLength = maxOf(8f, radius * .75f)
        val chunkCount = kotlin.math.ceil(distance / targetChunkLength).toInt().coerceIn(1, MAX_SEGMENT_CHUNKS)
        var dirty: Rect? = null
        var chunkStartX = previousX
        var chunkStartY = previousY
        for (chunk in 1..chunkCount) {
            val t = chunk / chunkCount.toFloat()
            val chunkEndX = previousX + deltaX * t
            val chunkEndY = previousY + deltaY * t
            blendSegment(chunkStartX, chunkStartY, chunkEndX, chunkEndY, radius, strength, hardness)?.let { changed ->
                dirty = dirty?.apply { union(changed) } ?: Rect(changed)
            }
            chunkStartX = chunkEndX
            chunkStartY = chunkEndY
        }
        return dirty
    }

    /** One bounded capsule of pigment transport. Long input packets are split by [blend] so this
     * operation never allocates a gesture- or page-sized scratch surface. */
    private fun blendSegment(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radius: Float,
        strength: Float,
        hardness: Float,
    ): Rect? {
        val destination = PixelRect(
            floor(minOf(previousX, currentX) - radius - 2f).toInt().coerceIn(0, width),
            floor(minOf(previousY, currentY) - radius - 2f).toInt().coerceIn(0, height),
            ceil(maxOf(previousX, currentX) + radius + 2f).toInt().coerceIn(0, width),
            ceil(maxOf(previousY, currentY) + radius + 2f).toInt().coerceIn(0, height),
        )
        if (destination.isEmpty) return null
        val deltaX = currentX - previousX
        val deltaY = currentY - previousY
        pickupDistance += hypot(deltaX, deltaY)
        val shouldPickup = !reservoir.isInitialized || pickupDistance >= (radius * PICKUP_SPACING_FACTOR).coerceAtLeast(2f)
        val sourceCore = PixelRect(
            floor(destination.left - deltaX).toInt(),
            floor(destination.top - deltaY).toInt(),
            ceil(destination.right - deltaX).toInt(),
            ceil(destination.bottom - deltaY).toInt(),
        )
        val roi = RasterRoiPlan.create(
            destination,
            width,
            height,
            tileSize = tileSize,
            readCore = sourceCore,
            halo = RasterHalo.uniform(1),
            abyssPolicy = RasterAbyssPolicy.TRANSPARENT,
        )
        val changed = edits.editWithReusableRoi(roi) roiEdit@{ sample, sampleGlobal, output ->
            val canvases = ensureSurfaces(destination.width, destination.height) ?: return@roiEdit
            val transport = canvases.first
            val coverage = canvases.second
            transport.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            coverage.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            // Selection constrains both pickup and deposition. Pigment outside an active selection
            // must not leak into it through the persistent reservoir.
            selection.apply(
                sample,
                Rect(sampleGlobal.left, sampleGlobal.top, sampleGlobal.right, sampleGlobal.bottom),
            )
            if (shouldPickup) {
                pickupReservoir(sample, sampleGlobal, previousX, previousY, radius, strength)
                pickupDistance = 0f
            }
            if (mode == RasterBlendMode.SMEAR) transport.drawBitmap(
                sample,
                sampleGlobal.left - destination.left + deltaX,
                sampleGlobal.top - destination.top + deltaY,
                sourcePaint,
            )
            if (reservoir.hasPigment) {
                reservoir.copyArgb(reservoirPixels, if (mode == RasterBlendMode.MIX) .9f else 0f)
                reservoirBitmap.setPixels(
                    reservoirPixels,
                    0,
                    reservoir.columns,
                    0,
                    0,
                    reservoir.columns,
                    reservoir.rows,
                )
                val reservoirDestination = Rect(
                    floor(currentX - radius - destination.left).toInt(),
                    floor(currentY - radius - destination.top).toInt(),
                    ceil(currentX + radius - destination.left).toInt(),
                    ceil(currentY + radius - destination.top).toInt(),
                )
                reservoirPaint.alpha = if (mode == RasterBlendMode.MIX) 255 else (strength.coerceIn(0f, 1f) * RESERVOIR_DEPOSIT_ALPHA * 255f + .5f)
                    .toInt().coerceIn(0, 255)
                transport.drawBitmap(reservoirBitmap, null, reservoirDestination, reservoirPaint)
                reservoirPaint.alpha = 255
            }
            val localPreviousX = previousX - destination.left
            val localPreviousY = previousY - destination.top
            val localCenterX = currentX - destination.left
            val localCenterY = currentY - destination.top
            val hardRadius = radius * hardness.coerceIn(0f, 1f)
            val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
            if (mode == RasterBlendMode.MIX) {
                // Compact smooth coverage has no clipped Gaussian tail at ROI edges.
                // It also avoids transporting quantized transparent edges repeatedly.
                mixingMatrix.setScale(radius, radius)
                mixingMatrix.postTranslate(localCenterX, localCenterY)
                mixingMask.setLocalMatrix(mixingMatrix)
                coveragePaint.style = Paint.Style.FILL
                coveragePaint.shader = mixingMask
                coverage.drawCircle(localCenterX, localCenterY, radius, coveragePaint)
                coveragePaint.shader = null
            } else {
                coveragePaint.style = Paint.Style.STROKE
                coveragePaint.strokeCap = Paint.Cap.ROUND
                coveragePaint.strokeJoin = Paint.Join.ROUND
                coveragePaint.strokeWidth = hardRadius.coerceAtLeast(.5f) * 2f
                coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
                coverage.drawLine(localPreviousX, localPreviousY, localCenterX, localCenterY, coveragePaint)
                coveragePaint.maskFilter = null
            }
            localRect.set(0, 0, destination.width, destination.height)
            transport.drawBitmap(requireNotNull(coverageBitmap), localRect, localRect, maskPaint)
            selection.apply(
                requireNotNull(transportBitmap),
                Rect(destination.left, destination.top, destination.right, destination.bottom),
            )
            depositPaint.alpha = (strength.coerceIn(0f, 1f) * 255f + .5f).toInt().coerceIn(0, 255)
            if (depositPaint.alpha > 0) {
                depositPaint.xfermode = PorterDuffXfermode(
                    if (preserveDestinationAlpha) PorterDuff.Mode.SRC_ATOP else PorterDuff.Mode.SRC_OVER,
                )
                Canvas(output).drawBitmap(requireNotNull(transportBitmap), localRect, localRect, depositPaint)
                depositPaint.xfermode = null
            }
        } ?: return null
        return Rect(changed.left, changed.top, changed.right, changed.bottom)
    }

    private fun pickupReservoir(
        sample: Bitmap,
        sampleGlobal: PixelRect,
        centerX: Float,
        centerY: Float,
        radius: Float,
        strength: Float,
    ) {
        val pickupRadius = (radius * if (mode == RasterBlendMode.MIX) 1f else PICKUP_RADIUS_FACTOR).coerceAtLeast(1f)
        val left = floor(centerX - pickupRadius).toInt().coerceIn(sampleGlobal.left, sampleGlobal.right - 1)
        val top = floor(centerY - pickupRadius).toInt().coerceIn(sampleGlobal.top, sampleGlobal.bottom - 1)
        val right = ceil(centerX + pickupRadius).toInt().coerceIn(left + 1, sampleGlobal.right)
        val bottom = ceil(centerY + pickupRadius).toInt().coerceIn(top + 1, sampleGlobal.bottom)
        val pickupWidth = right - left
        val pickupHeight = bottom - top
        val required = pickupWidth * pickupHeight
        if (pickupPixels.size < required) pickupPixels = IntArray(required)
        sample.getPixels(
            pickupPixels,
            0,
            pickupWidth,
            left - sampleGlobal.left,
            top - sampleGlobal.top,
            pickupWidth,
            pickupHeight,
        )
        // Strong smudge carries pigment farther; low strength refreshes from the canvas sooner.
        val pickupRate = if (mode == RasterBlendMode.MIX) .10f + (1f - strength.coerceIn(0f, 1f)) * .12f
            else (PICKUP_RATE_MAX - strength.coerceIn(0f, 1f) * PICKUP_RATE_RANGE)
                .coerceIn(PICKUP_RATE_MIN, PICKUP_RATE_MAX)
        reservoir.pickup(pickupPixels, pickupWidth, pickupHeight, pickupRate)
    }

    fun commit(): PixelTileTransactionRecord {
        check(!completed) { "Raster tile blend session is complete" }
        completed = true
        return edits.commit()
    }

    fun restoreOriginal() {
        if (completed) return
        completed = true
        edits.cancel()
    }

    private fun ensureSurfaces(width: Int, height: Int): Pair<Canvas, Canvas>? {
        val transport = transportBitmap
        val coverage = coverageBitmap
        if (transport != null && coverage != null && !transport.isRecycled && !coverage.isRecycled &&
            transport.width >= width && transport.height >= height && coverage.width >= width && coverage.height >= height
        ) return requireNotNull(transportCanvas) to requireNotNull(coverageCanvas)
        val nextWidth = maxOf(width, transport?.width ?: 0)
        val nextHeight = maxOf(height, transport?.height ?: 0)
        val nextTransport = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val nextCoverage = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (nextCoverage == null) { nextTransport.recycle(); return null }
        // Keep the current pair valid until both replacements exist, including failed allocation.
        transport?.recycle()
        coverage?.recycle()
        transportBitmap = nextTransport
        coverageBitmap = nextCoverage
        return Canvas(nextTransport).also { transportCanvas = it } to Canvas(nextCoverage).also { coverageCanvas = it }
    }

    private fun cachedBlur(radius: Float): BlurMaskFilter {
        val bucket = (radius.coerceAtMost(256f) * 4f + .5f).toInt().coerceAtLeast(1)
        return blurFilters[bucket] ?: BlurMaskFilter(bucket / 4f, BlurMaskFilter.Blur.NORMAL).also { blurFilters[bucket] = it }
    }

    override fun close() {
        if (!completed) restoreOriginal()
        edits.close()
        transportBitmap?.recycle()
        coverageBitmap?.recycle()
        reservoirBitmap.recycle()
        pickupPixels = IntArray(0)
        blurFilters.clear()
    }

    private companion object {
        const val PICKUP_SPACING_FACTOR = .14f
        const val PICKUP_RADIUS_FACTOR = .72f
        const val PICKUP_RATE_MIN = .18f
        const val PICKUP_RATE_MAX = .42f
        const val PICKUP_RATE_RANGE = .24f
        const val RESERVOIR_DEPOSIT_ALPHA = .72f
        const val MAX_SEGMENT_CHUNKS = 64
    }
}
