package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import app.inkloom.document.LiquifyMode
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import kotlin.math.hypot

/**
 * One atomic V3 Liquify gesture.
 *
 * Input appends to a sparse displacement field. Every live frame then samples the immutable
 * gesture-start pixel generation, so pixels are never recursively resampled and Reconstruct can
 * reduce the actual warp toward zero.
 */
class RasterTileLiquifySession private constructor(
    surface: PixelTileSurface,
    private val selection: RasterSelectionMask,
) : AutoCloseable {
    constructor(surface: PixelTileSurface) : this(
        surface,
        RasterSelectionMask(null, surface.logicalWidth, surface.logicalHeight),
    )

    internal constructor(surface: PixelTileSurface, selectionMask: MaskTileSurfaceSnapshot?) : this(
        surface,
        RasterSelectionMask(selectionMask, surface.logicalWidth, surface.logicalHeight),
    )

    private val edits = RasterTileEditSession(surface, "raster liquify")
    private val displacement = SparseDisplacementField(surface.logicalWidth, surface.logicalHeight)
    private var warpBitmap: Bitmap? = null
    private var warpCanvas: Canvas? = null
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private var vertices = FloatArray(0)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val compositePaint = Paint()
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val localRect = Rect()
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val width = surface.logicalWidth
    private val height = surface.logicalHeight
    private val tileSize = surface.tileSize
    private var completed = false

    val dirtyBounds: PixelRect? get() = edits.dirtyBounds
    val touchedTileCount: Int get() = edits.touchedTileCount
    val workingByteSize: Long get() = edits.workingByteSize
    internal val displacementTileCount: Int get() = displacement.tileCount
    internal val displacementByteSize: Long get() = displacement.retainedByteSize
    internal val processedStampCount: Int get() = displacement.processedStampCount
    internal val lastRoiPlan: RasterRoiPlan? get() = edits.lastRoiPlan
    fun previewSnapshot(): PixelTileSurfaceSnapshot = edits.previewSnapshot()

    fun deform(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radiusPixels: Float,
        strength: Float,
        hardness: Float,
        mode: LiquifyMode,
    ): Rect? {
        check(!completed) { "Raster tile liquify session is complete" }
        if (radiusPixels <= .25f) return null
        val radius = radiusPixels.coerceIn(.5f, minOf(width, height).toFloat())
        val safeStrength = strength.coerceIn(0f, 1f)
        if (safeStrength <= 0f) return null
        val changedField = displacement.appendSegment(
            previousX,
            previousY,
            currentX,
            currentY,
            radius,
            safeStrength,
            hardness,
            mode,
        ) ?: return null
        val destination = changedField
            .expand(FIELD_RENDER_PAD)
            .intersect(PixelRect(0, 0, width, height))
        if (destination.isEmpty) return null
        val surfaces = ensureSurfaces(destination.width, destination.height) ?: return null
        val meshWidth = ((destination.width / MESH_CELL_PIXELS) + 1).coerceIn(MIN_MESH_SEGMENTS, MAX_MESH_SEGMENTS)
        val meshHeight = ((destination.height / MESH_CELL_PIXELS) + 1).coerceIn(MIN_MESH_SEGMENTS, MAX_MESH_SEGMENTS)
        ensureVertexCapacity((meshWidth + 1) * (meshHeight + 1) * 2)
        populateVertices(destination, meshWidth, meshHeight)

        val operation: (Bitmap, PixelRect, Bitmap) -> Unit = { original, _, current ->
            surfaces.warp.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            surfaces.coverage.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            surfaces.warp.drawBitmapMesh(original, meshWidth, meshHeight, vertices, 0, null, 0, bitmapPaint)

            val hardRadius = radius * hardness.coerceIn(0f, 1f)
            val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
            val pathPad = hypot(currentX - previousX, currentY - previousY).coerceAtMost(radius)
            coveragePaint.strokeWidth = hardRadius.coerceAtLeast(.5f) * 2f + pathPad
            coveragePaint.alpha = 255
            coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
            surfaces.coverage.drawLine(
                previousX - destination.left,
                previousY - destination.top,
                currentX - destination.left,
                currentY - destination.top,
                coveragePaint,
            )
            coveragePaint.maskFilter = null
            coveragePaint.alpha = 255

            localRect.set(0, 0, destination.width, destination.height)
            selection.apply(
                requireNotNull(coverageBitmap),
                Rect(destination.left, destination.top, destination.right, destination.bottom),
            )
            surfaces.warp.drawBitmap(requireNotNull(coverageBitmap), localRect, localRect, maskPaint)
            Canvas(current).apply {
                drawBitmap(requireNotNull(coverageBitmap), localRect, localRect, clearPaint)
                drawBitmap(requireNotNull(warpBitmap), localRect, localRect, compositePaint)
            }
        }
        val roi = RasterRoiPlan.create(
            destination,
            width,
            height,
            tileSize = tileSize,
            readCore = destination,
            abyssPolicy = RasterAbyssPolicy.CLAMP_TO_EDGE,
        )
        val changed = edits.editWithRoi(roi, RasterSampleGeneration.ORIGINAL, operation) ?: return null
        return Rect(changed.left, changed.top, changed.right, changed.bottom)
    }

    fun commit(): PixelTileTransactionRecord {
        check(!completed) { "Raster tile liquify session is complete" }
        completed = true
        return edits.commit()
    }

    fun restoreOriginal() {
        if (completed) return
        completed = true
        edits.cancel()
    }

    private fun populateVertices(destination: PixelRect, meshWidth: Int, meshHeight: Int) {
        var index = 0
        for (row in 0..meshHeight) {
            val localY = destination.height * row / meshHeight.toFloat()
            val globalY = destination.top + localY
            for (column in 0..meshWidth) {
                val localX = destination.width * column / meshWidth.toFloat()
                val globalX = destination.left + localX
                val offset = displacement.displacementAt(globalX, globalY)
                vertices[index++] = localX + offset.first
                vertices[index++] = localY + offset.second
            }
        }
    }

    private fun ensureSurfaces(requiredWidth: Int, requiredHeight: Int): Surfaces? {
        if ((warpBitmap?.width ?: 0) >= requiredWidth && (warpBitmap?.height ?: 0) >= requiredHeight &&
            warpBitmap?.isRecycled == false && coverageBitmap?.isRecycled == false
        ) return Surfaces(requireNotNull(warpCanvas), requireNotNull(coverageCanvas))
        warpBitmap?.recycle()
        coverageBitmap?.recycle()
        val scratchWidth = roundScratch(requiredWidth)
        val scratchHeight = roundScratch(requiredHeight)
        val warp = runCatching { Bitmap.createBitmap(scratchWidth, scratchHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: return null
        val coverage = runCatching { Bitmap.createBitmap(scratchWidth, scratchHeight, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (coverage == null) {
            warp.recycle()
            return null
        }
        warpBitmap = warp
        coverageBitmap = coverage
        warpCanvas = Canvas(warp)
        coverageCanvas = Canvas(coverage)
        return Surfaces(requireNotNull(warpCanvas), requireNotNull(coverageCanvas))
    }

    private fun ensureVertexCapacity(size: Int) {
        if (vertices.size < size) vertices = FloatArray(size)
    }

    private fun cachedBlur(radius: Float): BlurMaskFilter {
        val bucket = (radius.coerceAtMost(256f) * 4f + .5f).toInt().coerceAtLeast(1)
        return blurFilters[bucket] ?: BlurMaskFilter(bucket / 4f, BlurMaskFilter.Blur.NORMAL).also { blurFilters[bucket] = it }
    }

    override fun close() {
        if (!completed) restoreOriginal()
        edits.close()
        warpBitmap?.recycle()
        coverageBitmap?.recycle()
        vertices = FloatArray(0)
        blurFilters.clear()
    }

    private fun PixelRect.expand(amount: Int): PixelRect = PixelRect(
        (left - amount).coerceAtLeast(0),
        (top - amount).coerceAtLeast(0),
        (right + amount).coerceAtMost(this@RasterTileLiquifySession.width),
        (bottom + amount).coerceAtMost(this@RasterTileLiquifySession.height),
    )

    private fun roundScratch(value: Int): Int = ((value + SCRATCH_QUANTUM - 1) / SCRATCH_QUANTUM) * SCRATCH_QUANTUM

    private data class Surfaces(val warp: Canvas, val coverage: Canvas)

    private companion object {
        const val MESH_CELL_PIXELS = 8
        const val MIN_MESH_SEGMENTS = 8
        const val MAX_MESH_SEGMENTS = 64
        const val FIELD_RENDER_PAD = 6
        const val SCRATCH_QUANTUM = 64
    }
}
