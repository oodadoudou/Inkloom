package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.tools.NativeRasterOps

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import app.inkloom.document.InkStroke
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * One atomic raster-paint gesture over the authoritative sparse tile surface.
 *
 * The Android bitmap is a display mirror and native scratch source only. Wet pigment transport and
 * the final deterministic brush program write through the same tile transaction, so cancel is
 * byte-exact and commit publishes a single undoable generation without a full-layer bridge.
 */
class RasterTilePaintSession private constructor(
    surface: PixelTileSurface,
    private val preserveDestinationAlpha: Boolean = false,
    private val selection: RasterSelectionMask,
) : AutoCloseable {
    constructor(
        surface: PixelTileSurface,
        preserveDestinationAlpha: Boolean = false,
    ) : this(
        surface,
        preserveDestinationAlpha,
        RasterSelectionMask(null, surface.logicalWidth, surface.logicalHeight),
    )

    internal constructor(
        surface: PixelTileSurface,
        selectionMask: MaskTileSurfaceSnapshot?,
        preserveDestinationAlpha: Boolean = false,
    ) : this(
        surface,
        preserveDestinationAlpha,
        RasterSelectionMask(selectionMask, surface.logicalWidth, surface.logicalHeight),
    )

    private val edits = RasterTileEditSession(surface, "raster paint")
    private var sourceBitmap: Bitmap? = null
    private var sourceCanvas: Canvas? = null
    private var sampleBitmap: Bitmap? = null
    private var sampleCanvas: Canvas? = null
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private val pickupBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val pickupCanvas = Canvas(pickupBitmap)
    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val depositPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val localRect = Rect()
    private val pickupSource = Rect()
    private val pickupDestination = Rect(0, 0, 1, 1)
    private val logicalWidth = surface.logicalWidth
    private val logicalHeight = surface.logicalHeight
    private val tileSize = surface.tileSize
    private var completed = false

    val dirtyBounds: PixelRect? get() = edits.dirtyBounds
    val touchedTileCount: Int get() = edits.touchedTileCount
    val workingByteSize: Long get() = edits.workingByteSize
    internal val lastRoiPlan: RasterRoiPlan? get() = edits.lastRoiPlan
    fun previewSnapshot(): PixelTileSurfaceSnapshot = edits.previewSnapshot()

    /** Bounded native downsample from the current private tile transaction. */
    fun samplePigment(centerX: Float, centerY: Float, radiusPixels: Float): Int {
        if (completed) return Color.TRANSPARENT
        val radius = (radiusPixels * .32f).coerceAtLeast(1f)
        pickupSource.set(
            floor(centerX - radius).toInt().coerceIn(0, logicalWidth - 1),
            floor(centerY - radius).toInt().coerceIn(0, logicalHeight - 1),
            ceil(centerX + radius).toInt().coerceIn(1, logicalWidth),
            ceil(centerY + radius).toInt().coerceIn(1, logicalHeight),
        )
        if (pickupSource.isEmpty) return Color.TRANSPARENT
        val bounds = PixelRect(pickupSource.left, pickupSource.top, pickupSource.right, pickupSource.bottom)
        val source = edits.sampleCurrent(bounds) ?: return Color.TRANSPARENT
        try {
            pickupCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            pickupCanvas.drawBitmap(source, null, pickupDestination, sourcePaint)
        } finally {
            source.recycle()
        }
        return pickupBitmap.getPixel(0, 0)
    }

    fun advanceWetTransport(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radiusPixels: Float,
        pull: Float,
        blur: Float,
        hardness: Float,
        absoluteBlurRadiusPixels: Float = -1f,
    ): Rect? {
        check(!completed) { "Raster tile paint session is complete" }
        if (radiusPixels <= .25f) return null
        val safePull = pull.coerceIn(0f, 1f)
        val safeBlur = blur.coerceIn(0f, 1f)
        if (safePull <= .001f && safeBlur <= .001f) return null
        val radius = radiusPixels.coerceIn(.5f, min(logicalWidth, logicalHeight).toFloat())
        val segmentX = currentX - previousX
        val segmentY = currentY - previousY
        val length = kotlin.math.hypot(segmentX, segmentY).coerceAtLeast(.001f)
        val directionX = segmentX / length
        val directionY = segmentY / length
        val dragDistance = radius * safePull
        val dragX = directionX * dragDistance
        val dragY = directionY * dragDistance
        val blurRadius = if (absoluteBlurRadiusPixels >= 0f) {
            absoluteBlurRadiusPixels.coerceIn(0f, radius * 2f)
        } else {
            radius * safeBlur * .35f
        }
        val pad = radius + blurRadius + 3f
        val destination = PixelRect(
            floor(min(previousX, currentX) - pad).toInt().coerceIn(0, logicalWidth),
            floor(min(previousY, currentY) - pad).toInt().coerceIn(0, logicalHeight),
            ceil(max(previousX, currentX) + pad).toInt().coerceIn(0, logicalWidth),
            ceil(max(previousY, currentY) + pad).toInt().coerceIn(0, logicalHeight),
        )
        if (destination.isEmpty) return null
        val sourceCore = PixelRect(
            floor(destination.left - dragX).toInt(),
            floor(destination.top - dragY).toInt(),
            ceil(destination.right - dragX).toInt(),
            ceil(destination.bottom - dragY).toInt(),
        )
        val roi = RasterRoiPlan.create(
            destination,
            logicalWidth,
            logicalHeight,
            tileSize = tileSize,
            readCore = sourceCore,
            halo = RasterHalo.uniform(1),
            abyssPolicy = RasterAbyssPolicy.TRANSPARENT,
        )
        val changed = edits.editWithRoi(roi) roiEdit@{ transactionSample, sampleGlobal, output ->
            val surfaces = ensureSurfaces(destination.width, destination.height) ?: return@roiEdit
            surfaces.source.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            surfaces.sample.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            surfaces.coverage.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            localRect.set(0, 0, destination.width, destination.height)
            surfaces.source.drawBitmap(
                transactionSample,
                sampleGlobal.left - destination.left + dragX,
                sampleGlobal.top - destination.top + dragY,
                sourcePaint,
            )
            if (blurRadius > .01f) {
                val blurred = runCatching {
                    NativeRasterOps.blur(
                        requireNotNull(sourceBitmap),
                        requireNotNull(sampleBitmap),
                        destination.width,
                        destination.height,
                        blurRadius.coerceAtMost(MAX_BLUR_RADIUS),
                    )
                }.getOrDefault(false)
                if (!blurred) surfaces.sample.drawBitmap(requireNotNull(sourceBitmap), localRect, localRect, sourcePaint)
            } else {
                surfaces.sample.drawBitmap(requireNotNull(sourceBitmap), localRect, localRect, sourcePaint)
            }

            val hardRadius = radius * hardness.coerceIn(0f, 1f)
            val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
            coveragePaint.strokeWidth = (hardRadius * 2f).coerceAtLeast(radius).coerceAtLeast(1f)
            coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
            surfaces.coverage.drawLine(
                previousX - destination.left,
                previousY - destination.top,
                currentX - destination.left,
                currentY - destination.top,
                coveragePaint,
            )
            coveragePaint.maskFilter = null
            surfaces.sample.drawBitmap(requireNotNull(coverageBitmap), localRect, localRect, maskPaint)
            selection.apply(
                requireNotNull(sampleBitmap),
                Rect(destination.left, destination.top, destination.right, destination.bottom),
            )
            depositPaint.alpha = (max(safePull * .82f, safeBlur * .55f) * 255f + .5f).toInt().coerceIn(0, 255)
            if (depositPaint.alpha > 0) {
                depositPaint.xfermode = PorterDuffXfermode(
                    if (preserveDestinationAlpha) PorterDuff.Mode.SRC_ATOP else PorterDuff.Mode.SRC_OVER,
                )
                Canvas(output).drawBitmap(requireNotNull(sampleBitmap), localRect, localRect, depositPaint)
                depositPaint.xfermode = null
            }
        } ?: return null
        return Rect(changed.left, changed.top, changed.right, changed.bottom)
    }

    /** Adds the deterministic committed brush patch to this gesture's existing wet transaction. */
    fun compositePaintPatch(
        paintPatch: Bitmap,
        left: Int,
        top: Int,
        strokes: List<InkStroke>,
        wetComposite: Boolean,
        contentWidth: Int = paintPatch.width,
        contentHeight: Int = paintPatch.height,
    ): Rect? {
        check(!completed) { "Raster tile paint session is complete" }
        if (paintPatch.isRecycled || strokes.isEmpty()) return null
        require(contentWidth in 1..paintPatch.width && contentHeight in 1..paintPatch.height) {
            "Paint patch content must be inside its backing bitmap"
        }
        require(left >= 0 && top >= 0 && left + contentWidth <= logicalWidth && top + contentHeight <= logicalHeight) {
            "Paint patch must be inside the raster surface"
        }
        val bounds = PixelRect(left, top, left + contentWidth, top + contentHeight)
        val changed = edits.edit(bounds) { output ->
            if (wetComposite) {
                RasterWetPaintMixer.composite(
                    output,
                    paintPatch,
                    0,
                    0,
                    strokes,
                    contentWidth = contentWidth,
                    contentHeight = contentHeight,
                )
            } else {
                localRect.set(0, 0, contentWidth, contentHeight)
                Canvas(output).drawBitmap(paintPatch, localRect, localRect, compositePaint)
            }
        } ?: return null
        return Rect(changed.left, changed.top, changed.right, changed.bottom)
    }

    fun commit(): PixelTileTransactionRecord {
        check(!completed) { "Raster tile paint session is complete" }
        completed = true
        return edits.commit()
    }

    fun restoreOriginal() {
        if (completed) return
        completed = true
        edits.cancel()
    }

    private fun ensureSurfaces(width: Int, height: Int): Surfaces? {
        val currentSource = sourceBitmap
        val currentSample = sampleBitmap
        val currentCoverage = coverageBitmap
        if (currentSource != null && currentSample != null && currentCoverage != null &&
            !currentSource.isRecycled && !currentSample.isRecycled && !currentCoverage.isRecycled &&
            currentSource.width >= width && currentSource.height >= height &&
            currentSample.width >= width && currentSample.height >= height &&
            currentCoverage.width >= width && currentCoverage.height >= height
        ) return Surfaces(requireNotNull(sourceCanvas), requireNotNull(sampleCanvas), requireNotNull(coverageCanvas))
        currentSource?.recycle()
        currentSample?.recycle()
        currentCoverage?.recycle()
        val nextWidth = max(width, max(currentSource?.width ?: 0, currentSample?.width ?: 0))
        val nextHeight = max(height, max(currentSource?.height ?: 0, currentSample?.height ?: 0))
        val nextSource = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val nextSample = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (nextSample == null) {
            nextSource.recycle()
            return null
        }
        val nextCoverage = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (nextCoverage == null) {
            nextSource.recycle()
            nextSample.recycle()
            return null
        }
        sourceBitmap = nextSource
        sampleBitmap = nextSample
        coverageBitmap = nextCoverage
        sourceCanvas = Canvas(nextSource)
        sampleCanvas = Canvas(nextSample)
        coverageCanvas = Canvas(nextCoverage)
        return Surfaces(requireNotNull(sourceCanvas), requireNotNull(sampleCanvas), requireNotNull(coverageCanvas))
    }

    private fun cachedBlur(radius: Float): BlurMaskFilter {
        val bucket = (radius.coerceAtMost(MAX_BLUR_RADIUS) * 4f + .5f).toInt().coerceAtLeast(1)
        return blurFilters[bucket] ?: BlurMaskFilter(bucket / 4f, BlurMaskFilter.Blur.NORMAL).also { blurFilters[bucket] = it }
    }

    override fun close() {
        if (!completed) restoreOriginal()
        edits.close()
        sourceBitmap?.recycle()
        sampleBitmap?.recycle()
        coverageBitmap?.recycle()
        pickupBitmap.recycle()
        sourceBitmap = null
        sampleBitmap = null
        coverageBitmap = null
        sourceCanvas = null
        sampleCanvas = null
        coverageCanvas = null
        blurFilters.clear()
    }

    private data class Surfaces(val source: Canvas, val sample: Canvas, val coverage: Canvas)

    private companion object {
        const val MAX_BLUR_RADIUS = 256f
    }
}
