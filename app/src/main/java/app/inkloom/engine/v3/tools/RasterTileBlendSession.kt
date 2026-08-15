package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.surface.PixelTileTransactionRecord
import kotlin.math.ceil
import kotlin.math.floor

/** Pigment transport directly over one sparse tile transaction. */
class RasterTileBlendSession(
    surface: PixelTileSurface,
    displayBitmap: Bitmap,
    selectionMask: Bitmap? = null,
) : AutoCloseable {
    private val edits = RasterTileEditSession(surface, displayBitmap, "raster blend")
    private val selection = RasterSelectionMask(selectionMask, surface.logicalWidth, surface.logicalHeight)
    private var transportBitmap: Bitmap? = null
    private var transportCanvas: Canvas? = null
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val depositPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val localRect = Rect()
    private val width = surface.logicalWidth
    private val height = surface.logicalHeight
    private var completed = false

    val dirtyBounds: PixelRect? get() = edits.dirtyBounds
    val touchedTileCount: Int get() = edits.touchedTileCount
    val workingByteSize: Long get() = edits.workingByteSize

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
        val destination = PixelRect(
            floor(currentX - radius - 2f).toInt().coerceIn(0, width),
            floor(currentY - radius - 2f).toInt().coerceIn(0, height),
            ceil(currentX + radius + 2f).toInt().coerceIn(0, width),
            ceil(currentY + radius + 2f).toInt().coerceIn(0, height),
        )
        if (destination.isEmpty) return null
        val deltaX = currentX - previousX
        val deltaY = currentY - previousY
        val sourceBounds = PixelRect(
            floor(destination.left - deltaX - 1f).toInt().coerceIn(0, width),
            floor(destination.top - deltaY - 1f).toInt().coerceIn(0, height),
            ceil(destination.right - deltaX + 1f).toInt().coerceIn(0, width),
            ceil(destination.bottom - deltaY + 1f).toInt().coerceIn(0, height),
        )
        if (sourceBounds.isEmpty) return null
        val changed = edits.editWithSample(destination, sourceBounds) { sample, sampleGlobal, output ->
            val canvases = ensureSurfaces(destination.width, destination.height) ?: return@editWithSample
            val transport = canvases.first
            val coverage = canvases.second
            transport.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            coverage.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            transport.drawBitmap(
                sample,
                sampleGlobal.left - destination.left + deltaX,
                sampleGlobal.top - destination.top + deltaY,
                sourcePaint,
            )
            val localCenterX = currentX - destination.left
            val localCenterY = currentY - destination.top
            val hardRadius = radius * hardness.coerceIn(0f, 1f)
            val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
            coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
            coverage.drawCircle(localCenterX, localCenterY, hardRadius.coerceAtLeast(.5f), coveragePaint)
            coveragePaint.maskFilter = null
            localRect.set(0, 0, destination.width, destination.height)
            transport.drawBitmap(requireNotNull(coverageBitmap), localRect, localRect, maskPaint)
            selection.apply(
                requireNotNull(transportBitmap),
                Rect(destination.left, destination.top, destination.right, destination.bottom),
            )
            depositPaint.alpha = (strength.coerceIn(0f, 1f) * 255f + .5f).toInt().coerceIn(0, 255)
            if (depositPaint.alpha > 0) Canvas(output).drawBitmap(requireNotNull(transportBitmap), localRect, localRect, depositPaint)
        } ?: return null
        return Rect(changed.left, changed.top, changed.right, changed.bottom)
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
        transport?.recycle()
        coverage?.recycle()
        val nextWidth = maxOf(width, transport?.width ?: 0)
        val nextHeight = maxOf(height, transport?.height ?: 0)
        val nextTransport = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val nextCoverage = runCatching { Bitmap.createBitmap(nextWidth, nextHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (nextCoverage == null) { nextTransport.recycle(); return null }
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
        blurFilters.clear()
    }
}
