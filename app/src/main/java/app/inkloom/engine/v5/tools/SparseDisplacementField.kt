package app.inkloom.engine.v5.tools

import app.inkloom.document.LiquifyMode
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Gesture-local sparse two-channel forward-displacement field.
 *
 * The field is deliberately independent of Android Bitmap/Canvas. Nodes are stored at a bounded
 * document-pixel spacing and interpolated for preview. Missing tiles are the identity transform.
 */
internal class SparseDisplacementField(
    private val logicalWidth: Int,
    private val logicalHeight: Int,
    val nodeSpacing: Int = DEFAULT_NODE_SPACING,
    private val tileNodes: Int = DEFAULT_TILE_NODES,
) {
    init {
        require(logicalWidth > 0 && logicalHeight > 0)
        require(nodeSpacing in 1..16)
        require(tileNodes in 8..128)
    }

    private val tiles = linkedMapOf<PixelTileKey, FloatArray>()
    private var stamps = 0
    private var maximumDisplacement = 0f

    val tileCount: Int get() = tiles.size
    val retainedByteSize: Long get() = tiles.size.toLong() * tileNodes * tileNodes * 2 * Float.SIZE_BYTES
    val processedStampCount: Int get() = stamps

    /** Appends only this input segment and returns the union of its old/new output extent. */
    fun appendSegment(
        previousX: Float,
        previousY: Float,
        currentX: Float,
        currentY: Float,
        radius: Float,
        strength: Float,
        hardness: Float,
        mode: LiquifyMode,
    ): PixelRect? {
        if (radius <= .25f || strength <= 0f) return null
        val dx = currentX - previousX
        val dy = currentY - previousY
        val moving = mode in MOVING_MODES
        val distance = hypot(dx, dy)
        if (moving && distance <= .01f) return null
        val step = (radius * STAMP_SPACING_FACTOR).coerceAtLeast(nodeSpacing.toFloat())
        val count = if (moving) ceil(distance / step).toInt().coerceAtLeast(1) else 1
        var dirty: PixelRect? = null
        for (index in 1..count) {
            val t = index / count.toFloat()
            val priorT = (index - 1) / count.toFloat()
            val centerX = previousX + dx * t
            val centerY = previousY + dy * t
            val stampDx = dx * (t - priorT)
            val stampDy = dy * (t - priorT)
            val changed = stamp(
                centerX,
                centerY,
                stampDx,
                stampDy,
                radius,
                strength.coerceIn(0f, 1f),
                hardness.coerceIn(0f, 1f),
                mode,
            )
            if (changed != null) dirty = dirty?.union(changed) ?: changed
            stamps++
        }
        return dirty
    }

    fun displacementAt(x: Float, y: Float): Pair<Float, Float> {
        if (!x.isFinite() || !y.isFinite()) return 0f to 0f
        val gx = (x / nodeSpacing).coerceIn(0f, maxGridX().toFloat())
        val gy = (y / nodeSpacing).coerceIn(0f, maxGridY().toFloat())
        val x0 = floor(gx).toInt()
        val y0 = floor(gy).toInt()
        val x1 = (x0 + 1).coerceAtMost(maxGridX())
        val y1 = (y0 + 1).coerceAtMost(maxGridY())
        val tx = gx - x0
        val ty = gy - y0
        val p00 = nodeAt(x0, y0)
        val p10 = nodeAt(x1, y0)
        val p01 = nodeAt(x0, y1)
        val p11 = nodeAt(x1, y1)
        val topX = p00.first + (p10.first - p00.first) * tx
        val topY = p00.second + (p10.second - p00.second) * tx
        val bottomX = p01.first + (p11.first - p01.first) * tx
        val bottomY = p01.second + (p11.second - p01.second) * tx
        return topX + (bottomX - topX) * ty to topY + (bottomY - topY) * ty
    }

    private fun stamp(
        centerX: Float,
        centerY: Float,
        deltaX: Float,
        deltaY: Float,
        radius: Float,
        strength: Float,
        hardness: Float,
        mode: LiquifyMode,
    ): PixelRect? {
        // A source node can already have moved into the brush from outside its original radius.
        // Search one bounded displacement halo so subsequent stamps operate in deformed space.
        val searchRadius = radius + maximumDisplacement.coerceAtMost(radius)
        val minGridX = floor((centerX - searchRadius) / nodeSpacing).toInt().coerceIn(0, maxGridX())
        val maxGridX = ceil((centerX + searchRadius) / nodeSpacing).toInt().coerceIn(0, maxGridX())
        val minGridY = floor((centerY - searchRadius) / nodeSpacing).toInt().coerceIn(0, maxGridY())
        val maxGridY = ceil((centerY + searchRadius) / nodeSpacing).toInt().coerceIn(0, maxGridY())
        val exponent = 1f + hardness * 4f
        val deltaLength = hypot(deltaX, deltaY).coerceAtLeast(.0001f)
        val tangentX = deltaX / deltaLength
        val tangentY = deltaY / deltaLength
        var left = logicalWidth.toFloat()
        var top = logicalHeight.toFloat()
        var right = 0f
        var bottom = 0f
        var changed = false
        for (gridY in minGridY..maxGridY) for (gridX in minGridX..maxGridX) {
            val sourceX = (gridX * nodeSpacing).toFloat().coerceAtMost(logicalWidth - 1f)
            val sourceY = (gridY * nodeSpacing).toFloat().coerceAtMost(logicalHeight - 1f)
            val old = nodeAt(gridX, gridY)
            val deformedX = sourceX + old.first
            val deformedY = sourceY + old.second
            val relativeX = deformedX - centerX
            val relativeY = deformedY - centerY
            val distance = hypot(relativeX, relativeY)
            if (distance >= radius) continue
            val falloff = (1f - distance / radius).coerceIn(0f, 1f).pow(exponent) * strength
            if (falloff <= .0001f) continue
            val next = when (mode) {
                LiquifyMode.PUSH -> old.first + deltaX * falloff to old.second + deltaY * falloff
                LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT -> {
                    val direction = if (mode == LiquifyMode.PUSH_LEFT) 1f else -1f
                    old.first - deltaY * falloff * direction to old.second + deltaX * falloff * direction
                }
                LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE -> {
                    val direction = if (mode == LiquifyMode.TWIRL_CLOCKWISE) 1f else -1f
                    val angle = direction * falloff * .28f
                    val cosine = cos(angle)
                    val sine = sin(angle)
                    val targetX = centerX + relativeX * cosine - relativeY * sine
                    val targetY = centerY + relativeX * sine + relativeY * cosine
                    targetX - sourceX to targetY - sourceY
                }
                LiquifyMode.PINCH, LiquifyMode.EXPAND -> {
                    val scale = (if (mode == LiquifyMode.PINCH) 1f - falloff * .12f else 1f + falloff * .12f)
                        .coerceAtLeast(.2f)
                    centerX + relativeX * scale - sourceX to centerY + relativeY * scale - sourceY
                }
                LiquifyMode.CRYSTALS -> {
                    val sector = (Math.PI / 8.0).toFloat()
                    val shard = (atan2(relativeY, relativeX) / sector).toInt() * sector
                    val push = radius * .13f * falloff
                    old.first + cos(shard) * push to old.second + sin(shard) * push
                }
                LiquifyMode.EDGE -> {
                    val perpendicular = relativeX * -tangentY + relativeY * tangentX
                    old.first + tangentY * perpendicular * falloff to old.second - tangentX * perpendicular * falloff
                }
                LiquifyMode.RECONSTRUCT -> {
                    val retain = (1f - falloff).coerceIn(0f, 1f)
                    old.first * retain to old.second * retain
                }
            }
            if (kotlin.math.abs(next.first - old.first) < EPSILON &&
                kotlin.math.abs(next.second - old.second) < EPSILON
            ) continue
            setNode(gridX, gridY, next.first, next.second)
            maximumDisplacement = maxOf(maximumDisplacement, hypot(next.first, next.second))
            left = minOf(left, sourceX + minOf(old.first, next.first), sourceX)
            top = minOf(top, sourceY + minOf(old.second, next.second), sourceY)
            right = maxOf(right, sourceX + maxOf(old.first, next.first), sourceX)
            bottom = maxOf(bottom, sourceY + maxOf(old.second, next.second), sourceY)
            changed = true
        }
        if (!changed) return null
        val pad = nodeSpacing + 2
        return PixelRect(
            (floor(left).toInt() - pad).coerceIn(0, logicalWidth),
            (floor(top).toInt() - pad).coerceIn(0, logicalHeight),
            (ceil(right).toInt() + pad + 1).coerceIn(0, logicalWidth),
            (ceil(bottom).toInt() + pad + 1).coerceIn(0, logicalHeight),
        )
    }

    private fun nodeAt(gridX: Int, gridY: Int): Pair<Float, Float> {
        val key = PixelTileKey(gridX / tileNodes, gridY / tileNodes)
        val tile = tiles[key] ?: return 0f to 0f
        val index = ((gridY % tileNodes) * tileNodes + gridX % tileNodes) * 2
        return tile[index] to tile[index + 1]
    }

    private fun setNode(gridX: Int, gridY: Int, x: Float, y: Float) {
        val key = PixelTileKey(gridX / tileNodes, gridY / tileNodes)
        val tile = tiles.getOrPut(key) { FloatArray(tileNodes * tileNodes * 2) }
        val index = ((gridY % tileNodes) * tileNodes + gridX % tileNodes) * 2
        tile[index] = if (kotlin.math.abs(x) < EPSILON) 0f else x
        tile[index + 1] = if (kotlin.math.abs(y) < EPSILON) 0f else y
    }

    private fun maxGridX(): Int = ceil((logicalWidth - 1) / nodeSpacing.toFloat()).toInt()
    private fun maxGridY(): Int = ceil((logicalHeight - 1) / nodeSpacing.toFloat()).toInt()

    private companion object {
        const val DEFAULT_NODE_SPACING = 4
        const val DEFAULT_TILE_NODES = 64
        const val STAMP_SPACING_FACTOR = .12f
        const val EPSILON = .0001f
        val MOVING_MODES = setOf(
            LiquifyMode.PUSH,
            LiquifyMode.PUSH_LEFT,
            LiquifyMode.PUSH_RIGHT,
            LiquifyMode.EDGE,
        )
    }
}
