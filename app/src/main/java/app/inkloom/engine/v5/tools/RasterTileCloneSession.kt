package app.inkloom.engine.v5.tools

import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PackedRgbaF16
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.engine.v5.surface.PixelTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.PixelTileTransactionRecord
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One copy-stamp (clone) stroke: pixels [offsetX], [offsetY] away are painted under the brush.
 * They come from the layer as it was when the stroke began, so the stroke never re-samples its
 * own output; each pixel keeps the strongest coverage it has had, so overlapping dabs do not
 * build up past the stroke's opacity. Selection and transparency lock limit the result.
 */
class RasterTileCloneSession internal constructor(
    surface: PixelTileSurface,
    selectionMask: MaskTileSurfaceSnapshot?,
    private val preserveDestinationAlpha: Boolean,
    private val offsetX: Int,
    private val offsetY: Int,
) : AutoCloseable {
    constructor(surface: PixelTileSurface, offsetX: Int, offsetY: Int, preserveDestinationAlpha: Boolean = false) :
        this(surface, null, preserveDestinationAlpha, offsetX, offsetY)

    // An empty mask means no selection, as for the other raster tools.
    private val selection = selectionMask?.takeUnless(MaskTileSurfaceSnapshot::isEmpty)
    private val source: PixelTileSurfaceSnapshot = surface.snapshot()
    private val write = surface.beginWrite("clone stamp")
    private val width = surface.logicalWidth
    private val height = surface.logicalHeight
    private val tile = surface.tileSize
    private val high = surface.pixelFormat.isHighPrecision
    private val coverage = HashMap<PixelTileKey, FloatArray>()
    private var active = true

    var dirtyBounds: PixelRect? = null
        private set
    val changed: Boolean get() = dirtyBounds != null

    fun previewSnapshot(): PixelTileSurfaceSnapshot = write.previewSnapshot()

    /** Stamps along [fromX], [fromY] → [toX], [toY] in layer pixels; returns the area that changed. */
    fun stroke(fromX: Float, fromY: Float, toX: Float, toY: Float, radius: Float, opacity: Float, hardness: Float): PixelRect? {
        check(active) { "Clone stroke has finished" }
        val r = radius.coerceIn(.5f, 2000f)
        val spacing = max(1f, r * .2f)
        val steps = max(1, ceil(hypot(toX - fromX, toY - fromY) / spacing).toInt())
        var changedArea: PixelRect? = null
        for (step in (if (fromX == toX && fromY == toY) 0 else 1)..steps) {
            val t = step / steps.toFloat()
            stamp(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, r, opacity.coerceIn(0f, 1f), hardness.coerceIn(0f, 1f))
                ?.let { changedArea = changedArea?.union(it) ?: it }
        }
        changedArea?.let { dirtyBounds = dirtyBounds?.union(it) ?: it }
        return changedArea
    }

    private fun stamp(cx: Float, cy: Float, radius: Float, opacity: Float, hardness: Float): PixelRect? {
        val area = PixelRect(floor(cx - radius).toInt(), floor(cy - radius).toInt(), ceil(cx + radius).toInt() + 1, ceil(cy + radius).toInt() + 1)
            .intersect(PixelRect(0, 0, width, height))
        if (area.isEmpty || opacity <= 0f) return null
        val inner = radius * hardness
        val sourceArea = PixelRect(area.left + offsetX, area.top + offsetY, area.right + offsetX, area.bottom + offsetY)
        val sourceInside = sourceArea.intersect(PixelRect(0, 0, width, height))
        val sourcePixels = if (high || sourceInside.isEmpty) null else source.copyPremultipliedPixels(sourceInside)
        val originalPixels = if (high) null else source.copyPremultipliedPixels(area)
        var touched: PixelRect? = null
        for (row in area.top / tile..(area.bottom - 1) / tile) for (column in area.left / tile..(area.right - 1) / tile) {
            val key = PixelTileKey(column, row)
            val tileArea = area.intersect(PixelRect(column * tile, row * tile, (column + 1) * tile, (row + 1) * tile))
            if (tileArea.isEmpty) continue
            val covered = coverage.getOrPut(key) { FloatArray(tile * tile) }
            // Coverage first: only pixels whose coverage grows are rewritten.
            var grew = false
            val grown = BooleanArray(tileArea.width * tileArea.height)
            for (y in tileArea.top until tileArea.bottom) for (x in tileArea.left until tileArea.right) {
                val distance = hypot(x + .5f - cx, y + .5f - cy)
                if (distance >= radius) continue
                val falloff = if (distance <= inner) 1f else 1f - ((distance - inner) / (radius - inner).coerceAtLeast(1e-3f)).let { it * it * (3f - 2f * it) }
                val weight = selection?.alphaAt((x.toLong() * selection.logicalWidth / width).toInt(), (y.toLong() * selection.logicalHeight / height).toInt())?.div(255f) ?: 1f
                val value = falloff * opacity * weight
                val i = (y - row * tile) * tile + x - column * tile
                if (value > covered[i] + 1e-4f) { covered[i] = value; grown[(y - tileArea.top) * tileArea.width + x - tileArea.left] = true; grew = true }
            }
            if (!grew) continue
            val local = PixelRect(tileArea.left - column * tile, tileArea.top - row * tile, tileArea.right - column * tile, tileArea.bottom - row * tile)
            if (high) write.editTileF16(key, local) { pixels, stride ->
                for (y in tileArea.top until tileArea.bottom) for (x in tileArea.left until tileArea.right) {
                    if (!grown[(y - tileArea.top) * tileArea.width + x - tileArea.left]) continue
                    val c = covered[(y - row * tile) * tile + x - column * tile]
                    val o = source.linearPixelAt(x, y); val s = source.linearPixelAt(x + offsetX, y + offsetY)
                    val scale = if (preserveDestinationAlpha) (if (s.alpha > 0f) o.alpha / s.alpha else 0f) else 1f
                    val alpha = if (preserveDestinationAlpha) o.alpha else o.alpha + (s.alpha - o.alpha) * c
                    pixels[(y - row * tile) * stride + x - column * tile] = PackedRgbaF16.packPremultiplied(
                        o.red + (s.red * scale - o.red) * c, o.green + (s.green * scale - o.green) * c, o.blue + (s.blue * scale - o.blue) * c, alpha)
                }
            } else write.editTile(key, local) { pixels, stride ->
                for (y in tileArea.top until tileArea.bottom) for (x in tileArea.left until tileArea.right) {
                    if (!grown[(y - tileArea.top) * tileArea.width + x - tileArea.left]) continue
                    val c = covered[(y - row * tile) * tile + x - column * tile]
                    val o = originalPixels!![(y - area.top) * area.width + x - area.left]
                    val sx = x + offsetX; val sy = y + offsetY
                    val s = if (sourcePixels != null && sx in sourceInside.left until sourceInside.right && sy in sourceInside.top until sourceInside.bottom)
                        sourcePixels[(sy - sourceInside.top) * sourceInside.width + sx - sourceInside.left] else 0
                    pixels[(y - row * tile) * stride + x - column * tile] = mix(o, s, c)
                }
            }
            val changedTile = PixelRect(tileArea.left, tileArea.top, tileArea.right, tileArea.bottom)
            touched = touched?.union(changedTile) ?: changedTile
        }
        return touched
    }

    /** Premultiplied 8-bit mix of the original towards the source by [c]; a locked alpha keeps the original coverage. */
    private fun mix(original: Int, source: Int, c: Float): Int {
        val oa = original ushr 24 and 255; val sa = source ushr 24 and 255
        fun channel(shift: Int, sourceScale: Float): Int {
            val o = original ushr shift and 255; val s = (source ushr shift and 255) * sourceScale
            return (o + (s - o) * c).roundToInt()
        }
        val scale = if (preserveDestinationAlpha) (if (sa > 0) oa / sa.toFloat() else 0f) else 1f
        val alpha = if (preserveDestinationAlpha) oa else (oa + (sa - oa) * c).roundToInt().coerceIn(0, 255)
        val r = min(channel(16, scale), alpha); val g = min(channel(8, scale), alpha); val b = min(channel(0, scale), alpha)
        return (alpha shl 24) or (r.coerceAtLeast(0) shl 16) or (g.coerceAtLeast(0) shl 8) or b.coerceAtLeast(0)
    }

    fun commit(): PixelTileTransactionRecord { check(active); active = false; return write.commit() }

    fun cancel() { if (active) { active = false; write.cancel() } }

    override fun close() = cancel()
}
