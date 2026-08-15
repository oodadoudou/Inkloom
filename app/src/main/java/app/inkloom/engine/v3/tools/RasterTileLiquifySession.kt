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
import app.inkloom.engine.v3.surface.PixelRect
import app.inkloom.engine.v3.surface.PixelTileSurface
import app.inkloom.engine.v3.surface.PixelTileTransactionRecord
import app.inkloom.engine.v3.surface.MaskTileSurfaceSnapshot
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Bounded Skia mesh warp backed by one atomic sparse-tile gesture transaction. */
class RasterTileLiquifySession private constructor(
    surface: PixelTileSurface,
    displayBitmap: Bitmap,
    private val selection: RasterSelectionMask,
) : AutoCloseable {
    constructor(surface: PixelTileSurface, displayBitmap: Bitmap) : this(
        surface,
        displayBitmap,
        RasterSelectionMask(null, surface.logicalWidth, surface.logicalHeight),
    )

    internal constructor(surface: PixelTileSurface, displayBitmap: Bitmap, selectionMask: MaskTileSurfaceSnapshot?) : this(
        surface,
        displayBitmap,
        RasterSelectionMask(selectionMask, surface.logicalWidth, surface.logicalHeight),
    )

    private val edits = RasterTileEditSession(surface, displayBitmap, "raster liquify")
    private var warpBitmap: Bitmap? = null
    private var warpCanvas: Canvas? = null
    private var coverageBitmap: Bitmap? = null
    private var coverageCanvas: Canvas? = null
    private var vertices = FloatArray(0)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val compositePaint = Paint()
    private val coveragePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val localRect = Rect()
    private val blurFilters = object : LinkedHashMap<Int, BlurMaskFilter>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlurMaskFilter>): Boolean = size > 24
    }
    private val width = surface.logicalWidth
    private val height = surface.logicalHeight
    private var completed = false

    val dirtyBounds: PixelRect? get() = edits.dirtyBounds
    val touchedTileCount: Int get() = edits.touchedTileCount
    val workingByteSize: Long get() = edits.workingByteSize

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
        val deltaX = currentX - previousX
        val deltaY = currentY - previousY
        val movingMode = mode in setOf(LiquifyMode.PUSH, LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT, LiquifyMode.EDGE)
        val deltaLength = hypot(deltaX, deltaY)
        if (movingMode && deltaLength <= .01f) return null
        val safeStrength = strength.coerceIn(0f, 1f)
        val displacement = when (mode) {
            LiquifyMode.PUSH, LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT, LiquifyMode.EDGE -> deltaLength
            LiquifyMode.CRYSTALS -> radius * .13f * safeStrength
            else -> radius * .22f * safeStrength
        }
        val pad = ceil(radius + displacement + 3f).toInt()
        val destination = PixelRect(
            (floor(currentX).toInt() - pad).coerceIn(0, width),
            (floor(currentY).toInt() - pad).coerceIn(0, height),
            (ceil(currentX).toInt() + pad).coerceIn(0, width),
            (ceil(currentY).toInt() + pad).coerceIn(0, height),
        )
        if (destination.isEmpty) return null
        val surfaces = ensureSurfaces(destination.width, destination.height) ?: return null
        val meshWidth = ((destination.width / MESH_CELL_PIXELS) + 1).coerceIn(MIN_MESH_SEGMENTS, MAX_MESH_SEGMENTS)
        val meshHeight = ((destination.height / MESH_CELL_PIXELS) + 1).coerceIn(MIN_MESH_SEGMENTS, MAX_MESH_SEGMENTS)
        ensureVertexCapacity((meshWidth + 1) * (meshHeight + 1) * 2)
        populateVertices(
            Rect(destination.left, destination.top, destination.right, destination.bottom),
            meshWidth,
            meshHeight,
            currentX,
            currentY,
            deltaX,
            deltaY,
            radius,
            safeStrength,
            hardness,
            mode,
        )
        val operation: (Bitmap, PixelRect, Bitmap) -> Unit = { sample, _, current ->
            surfaces.warp.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            surfaces.coverage.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            surfaces.warp.drawBitmapMesh(sample, meshWidth, meshHeight, vertices, 0, null, 0, bitmapPaint)
            val localCenterX = currentX - destination.left
            val localCenterY = currentY - destination.top
            val hardRadius = radius * hardness.coerceIn(0f, 1f)
            val featherRadius = (radius - hardRadius).coerceAtLeast(0f)
            coveragePaint.alpha = if (mode == LiquifyMode.RECONSTRUCT) (safeStrength * 255f + .5f).toInt() else 255
            coveragePaint.maskFilter = if (featherRadius <= .01f) null else cachedBlur(featherRadius)
            surfaces.coverage.drawCircle(localCenterX, localCenterY, hardRadius.coerceAtLeast(.5f), coveragePaint)
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
        val changed = if (mode == LiquifyMode.RECONSTRUCT) {
            edits.editWithOriginalSample(destination, destination, operation)
        } else {
            edits.editWithSample(destination, destination, operation)
        } ?: return null
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

    private fun populateVertices(
        destination: Rect,
        meshWidth: Int,
        meshHeight: Int,
        centerX: Float,
        centerY: Float,
        deltaX: Float,
        deltaY: Float,
        radius: Float,
        strength: Float,
        hardness: Float,
        mode: LiquifyMode,
    ) {
        val exponent = 1f + hardness.coerceIn(0f, 1f) * 4f
        val deltaLength = hypot(deltaX, deltaY).coerceAtLeast(.0001f)
        val tangentX = deltaX / deltaLength
        val tangentY = deltaY / deltaLength
        var index = 0
        for (row in 0..meshHeight) {
            val localY = destination.height() * row / meshHeight.toFloat()
            val globalY = destination.top + localY
            for (column in 0..meshWidth) {
                val localX = destination.width() * column / meshWidth.toFloat()
                val globalX = destination.left + localX
                val dx = globalX - centerX
                val dy = globalY - centerY
                val distance = hypot(dx, dy)
                val falloff = if (distance >= radius) 0f else (1f - distance / radius).coerceIn(0f, 1f).pow(exponent) * strength
                var outputX = localX
                var outputY = localY
                if (falloff > .0001f && mode != LiquifyMode.RECONSTRUCT) when (mode) {
                    LiquifyMode.PUSH -> { outputX += deltaX * falloff; outputY += deltaY * falloff }
                    LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT -> {
                        val direction = if (mode == LiquifyMode.PUSH_LEFT) 1f else -1f
                        outputX -= deltaY * falloff * direction
                        outputY += deltaX * falloff * direction
                    }
                    LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE -> {
                        val direction = if (mode == LiquifyMode.TWIRL_CLOCKWISE) 1f else -1f
                        val angle = direction * falloff * .28f
                        val cosine = cos(angle)
                        val sine = sin(angle)
                        outputX = centerX - destination.left + dx * cosine - dy * sine
                        outputY = centerY - destination.top + dx * sine + dy * cosine
                    }
                    LiquifyMode.PINCH, LiquifyMode.EXPAND -> {
                        val scale = (if (mode == LiquifyMode.PINCH) 1f - falloff * .12f else 1f + falloff * .12f).coerceAtLeast(.2f)
                        outputX = centerX - destination.left + dx * scale
                        outputY = centerY - destination.top + dy * scale
                    }
                    LiquifyMode.CRYSTALS -> {
                        val sector = (Math.PI / 8.0).toFloat()
                        val shard = (atan2(dy, dx) / sector).toInt() * sector
                        val push = radius * .13f * falloff
                        outputX += cos(shard) * push
                        outputY += sin(shard) * push
                    }
                    LiquifyMode.EDGE -> {
                        val perpendicular = dx * -tangentY + dy * tangentX
                        outputX += tangentY * perpendicular * falloff
                        outputY -= tangentX * perpendicular * falloff
                    }
                    LiquifyMode.RECONSTRUCT -> Unit
                }
                vertices[index++] = outputX
                vertices[index++] = outputY
            }
        }
    }

    private fun ensureSurfaces(width: Int, height: Int): Surfaces? {
        if (warpBitmap?.width == width && warpBitmap?.height == height && warpBitmap?.isRecycled == false &&
            coverageBitmap?.width == width && coverageBitmap?.height == height && coverageBitmap?.isRecycled == false
        ) return Surfaces(requireNotNull(warpCanvas), requireNotNull(coverageCanvas))
        warpBitmap?.recycle()
        coverageBitmap?.recycle()
        val warp = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val coverage = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (coverage == null) { warp.recycle(); return null }
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

    private data class Surfaces(val warp: Canvas, val coverage: Canvas)

    private companion object {
        const val MESH_CELL_PIXELS = 10
        const val MIN_MESH_SEGMENTS = 8
        const val MAX_MESH_SEGMENTS = 32
    }
}
