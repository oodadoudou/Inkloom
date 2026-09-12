package app.inkloom.engine.v5.tools

import app.inkloom.document.LiquifyMode
import app.inkloom.engine.v5.surface.PixelRect
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sparse two-channel forward-displacement field, with isolated copies for cancelable contacts.
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

    private val tiles = PrimitiveTileMap()
    private var stamps = 0
    private var maximumDisplacement = 0f

    val tileCount: Int get() = tiles.size
    val retainedByteSize: Long get() = tiles.size.toLong() * tileNodes * tileNodes * 2 * Float.SIZE_BYTES
    val processedStampCount: Int get() = stamps

    /** Copy only allocated field tiles at pen-down; no whole-canvas allocation or per-MOVE copy. */
    fun copy(): SparseDisplacementField = SparseDisplacementField(logicalWidth, logicalHeight, nodeSpacing, tileNodes).also { result ->
        tiles.forEach { key, value -> result.tiles.getOrPut(key) { value.copyOf() } }
        result.stamps = stamps
        result.maximumDisplacement = maximumDisplacement
    }

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
        val output = FloatArray(2)
        writeDisplacementAt(x, y, output, 0)
        return output[0] to output[1]
    }

    /** Allocation-free mesh sampling for the realtime liquify renderer. */
    fun writeDisplacementAt(x: Float, y: Float, output: FloatArray, offset: Int) {
        require(offset >= 0 && offset + 1 < output.size)
        if (!x.isFinite() || !y.isFinite()) {
            output[offset] = 0f
            output[offset + 1] = 0f
            return
        }
        val gx = (x / nodeSpacing).coerceIn(0f, maxGridX().toFloat())
        val gy = (y / nodeSpacing).coerceIn(0f, maxGridY().toFloat())
        val x0 = floor(gx).toInt()
        val y0 = floor(gy).toInt()
        val x1 = (x0 + 1).coerceAtMost(maxGridX())
        val y1 = (y0 + 1).coerceAtMost(maxGridY())
        val tx = gx - x0
        val ty = gy - y0
        val p00x = nodeComponent(x0, y0, 0)
        val p00y = nodeComponent(x0, y0, 1)
        val p10x = nodeComponent(x1, y0, 0)
        val p10y = nodeComponent(x1, y0, 1)
        val p01x = nodeComponent(x0, y1, 0)
        val p01y = nodeComponent(x0, y1, 1)
        val p11x = nodeComponent(x1, y1, 0)
        val p11y = nodeComponent(x1, y1, 1)
        val topX = p00x + (p10x - p00x) * tx
        val topY = p00y + (p10y - p00y) * tx
        val bottomX = p01x + (p11x - p01x) * tx
        val bottomY = p01y + (p11y - p01y) * tx
        output[offset] = topX + (bottomX - topX) * ty
        output[offset + 1] = topY + (bottomY - topY) * ty
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
            val oldX = nodeComponent(gridX, gridY, 0)
            val oldY = nodeComponent(gridX, gridY, 1)
            val deformedX = sourceX + oldX
            val deformedY = sourceY + oldY
            val relativeX = deformedX - centerX
            val relativeY = deformedY - centerY
            val distance = hypot(relativeX, relativeY)
            if (distance >= radius) continue
            val falloff = (1f - distance / radius).coerceIn(0f, 1f).pow(exponent) * strength
            if (falloff <= .0001f) continue
            var nextX: Float
            var nextY: Float
            when (mode) {
                LiquifyMode.PUSH -> {
                    nextX = oldX + deltaX * falloff
                    nextY = oldY + deltaY * falloff
                }
                LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT -> {
                    val direction = if (mode == LiquifyMode.PUSH_LEFT) 1f else -1f
                    nextX = oldX - deltaY * falloff * direction
                    nextY = oldY + deltaX * falloff * direction
                }
                LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE -> {
                    val direction = if (mode == LiquifyMode.TWIRL_CLOCKWISE) 1f else -1f
                    val angle = direction * falloff * .28f
                    val cosine = cos(angle)
                    val sine = sin(angle)
                    val targetX = centerX + relativeX * cosine - relativeY * sine
                    val targetY = centerY + relativeX * sine + relativeY * cosine
                    nextX = targetX - sourceX
                    nextY = targetY - sourceY
                }
                LiquifyMode.PINCH, LiquifyMode.EXPAND -> {
                    val scale = (if (mode == LiquifyMode.PINCH) 1f - falloff * .12f else 1f + falloff * .12f)
                        .coerceAtLeast(.2f)
                    nextX = centerX + relativeX * scale - sourceX
                    nextY = centerY + relativeY * scale - sourceY
                }
                LiquifyMode.CRYSTALS -> {
                    val sector = (Math.PI / 8.0).toFloat()
                    val shard = (atan2(relativeY, relativeX) / sector).toInt() * sector
                    val push = radius * .13f * falloff
                    nextX = oldX + cos(shard) * push
                    nextY = oldY + sin(shard) * push
                }
                LiquifyMode.EDGE -> {
                    val perpendicular = relativeX * -tangentY + relativeY * tangentX
                    nextX = oldX + tangentY * perpendicular * falloff
                    nextY = oldY - tangentX * perpendicular * falloff
                }
                LiquifyMode.RECONSTRUCT -> {
                    val retain = (1f - falloff).coerceIn(0f, 1f)
                    nextX = oldX * retain
                    nextY = oldY * retain
                }
            }
            if (kotlin.math.abs(nextX - oldX) < EPSILON &&
                kotlin.math.abs(nextY - oldY) < EPSILON
            ) continue
            setNode(gridX, gridY, nextX, nextY)
            maximumDisplacement = maxOf(maximumDisplacement, hypot(nextX, nextY))
            left = minOf(left, sourceX + minOf(oldX, nextX), sourceX)
            top = minOf(top, sourceY + minOf(oldY, nextY), sourceY)
            right = maxOf(right, sourceX + maxOf(oldX, nextX), sourceX)
            bottom = maxOf(bottom, sourceY + maxOf(oldY, nextY), sourceY)
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
        val tile = tiles[tileKey(gridX, gridY)] ?: return 0f to 0f
        val index = ((gridY % tileNodes) * tileNodes + gridX % tileNodes) * 2
        return tile[index] to tile[index + 1]
    }

    private fun nodeComponent(gridX: Int, gridY: Int, component: Int): Float {
        val tile = tiles[tileKey(gridX, gridY)] ?: return 0f
        return tile[((gridY % tileNodes) * tileNodes + gridX % tileNodes) * 2 + component]
    }

    private fun setNode(gridX: Int, gridY: Int, x: Float, y: Float) {
        val key = tileKey(gridX, gridY)
        val tile = tiles.getOrPut(key) { FloatArray(tileNodes * tileNodes * 2) }
        val index = ((gridY % tileNodes) * tileNodes + gridX % tileNodes) * 2
        tile[index] = if (kotlin.math.abs(x) < EPSILON) 0f else x
        tile[index + 1] = if (kotlin.math.abs(y) < EPSILON) 0f else y
    }

    private fun tileKey(gridX: Int, gridY: Int): Long =
        (gridY / tileNodes).toLong() shl 32 or (gridX / tileNodes).toLong().and(0xffffffffL)

    /** Gesture-local primitive map; avoids boxing thousands of lookup keys per input frame. */
    private class PrimitiveTileMap {
        private var keys = LongArray(16) { EMPTY }
        private var values = arrayOfNulls<FloatArray>(16)
        var size: Int = 0
            private set

        fun forEach(action: (Long, FloatArray) -> Unit) {
            keys.indices.forEach { index ->
                if (keys[index] != EMPTY) action(keys[index], requireNotNull(values[index]))
            }
        }

        operator fun get(key: Long): FloatArray? {
            var slot = slot(key, keys.size)
            while (true) {
                val stored = keys[slot]
                if (stored == EMPTY) return null
                if (stored == key) return values[slot]
                slot = (slot + 1) and (keys.size - 1)
            }
        }

        fun getOrPut(key: Long, create: () -> FloatArray): FloatArray {
            get(key)?.let { return it }
            if ((size + 1) * 10 >= keys.size * 7) resize(keys.size * 2)
            var slot = slot(key, keys.size)
            while (keys[slot] != EMPTY) slot = (slot + 1) and (keys.size - 1)
            return create().also {
                keys[slot] = key
                values[slot] = it
                size++
            }
        }

        private fun resize(capacity: Int) {
            val oldKeys = keys
            val oldValues = values
            keys = LongArray(capacity) { EMPTY }
            values = arrayOfNulls(capacity)
            oldKeys.indices.forEach { index ->
                val key = oldKeys[index]
                if (key == EMPTY) return@forEach
                var slot = slot(key, capacity)
                while (keys[slot] != EMPTY) slot = (slot + 1) and (capacity - 1)
                keys[slot] = key
                values[slot] = oldValues[index]
            }
        }

        private fun slot(key: Long, capacity: Int): Int {
            var mixed = key xor (key ushr 33)
            mixed *= -49064778989728563L
            mixed = mixed xor (mixed ushr 33)
            return mixed.toInt() and (capacity - 1)
        }

        private companion object { const val EMPTY = Long.MIN_VALUE }
    }

    private fun maxGridX(): Int = ceil((logicalWidth - 1) / nodeSpacing.toFloat()).toInt()
    private fun maxGridY(): Int = ceil((logicalHeight - 1) / nodeSpacing.toFloat()).toInt()

    private companion object {
        // Bilinear 12 px nodes preserve sub-node continuity while avoiding the redundant 4 px
        // field that was resampled onto a much coarser presentation mesh every MOVE.
        const val DEFAULT_NODE_SPACING = 12
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
