package app.inkloom.document

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Build
import android.util.LruCache
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

@Serializable
enum class GradientKind { LINEAR, RADIAL, ELLIPSE, ANGULAR, REFLECTED, DIAMOND }

@Serializable
enum class GradientEdgeMode { CLAMP, REPEAT, MIRROR, TRANSPARENT }

@Serializable
data class GradientColorStop(val position: Float, val colorArgb: Long)

data class GradientDefinition(
    val kind: GradientKind = GradientKind.LINEAR,
    val edgeMode: GradientEdgeMode = GradientEdgeMode.CLAMP,
    val reverse: Boolean = false,
    val dither: Boolean = true,
    val ellipseRatio: Float = .6f,
    val stops: List<GradientColorStop>,
) {
    fun normalized(): GradientDefinition = copy(
        ellipseRatio = ellipseRatio.takeIf(Float::isFinite)?.coerceIn(.1f, 1f) ?: .6f,
        stops = GradientColors.normalize(stops),
    )
}

object GradientColors {
    fun normalize(stops: List<GradientColorStop>): List<GradientColorStop> {
        val safe = stops.mapNotNull { stop ->
            stop.position.takeIf(Float::isFinite)?.let { GradientColorStop(it.coerceIn(0f, 1f), stop.colorArgb and 0xFFFFFFFFL) }
        }.sortedBy(GradientColorStop::position)
        if (safe.size >= 2) return safe
        val color = safe.firstOrNull()?.colorArgb ?: 0xFF111111L
        return listOf(GradientColorStop(0f, color), GradientColorStop(1f, 0xFFFFFFFFL))
    }

    fun resolved(definition: GradientDefinition): List<GradientColorStop> {
        val stops = definition.normalized().stops
        return if (!definition.reverse) stops else stops.map { GradientColorStop(1f - it.position, it.colorArgb) }.sortedBy(GradientColorStop::position)
    }

    fun sample(stops: List<GradientColorStop>, rawPosition: Float, edgeMode: GradientEdgeMode): Int {
        val normalized = normalize(stops)
        val position = when (edgeMode) {
            GradientEdgeMode.CLAMP -> rawPosition.coerceIn(0f, 1f)
            GradientEdgeMode.REPEAT -> rawPosition - floor(rawPosition)
            GradientEdgeMode.MIRROR -> {
                val wrapped = rawPosition - floor(rawPosition / 2f) * 2f
                if (wrapped <= 1f) wrapped else 2f - wrapped
            }
            GradientEdgeMode.TRANSPARENT -> if (rawPosition !in 0f..1f) return 0 else rawPosition
        }
        val rightIndex = normalized.indexOfFirst { it.position >= position }.takeIf { it >= 0 } ?: normalized.lastIndex
        if (rightIndex == 0) return normalized.first().colorArgb.toInt()
        val left = normalized[rightIndex - 1]
        val right = normalized[rightIndex]
        val amount = ((position - left.position) / (right.position - left.position).coerceAtLeast(.000001f)).coerceIn(0f, 1f)
        val leftColor = left.colorArgb.toInt()
        val rightColor = right.colorArgb.toInt()
        val alpha = lerp(leftColor ushr 24 and 0xFF, rightColor ushr 24 and 0xFF, amount)
        val red = lerp(leftColor ushr 16 and 0xFF, rightColor ushr 16 and 0xFF, amount)
        val green = lerp(leftColor ushr 8 and 0xFF, rightColor ushr 8 and 0xFF, amount)
        val blue = lerp(leftColor and 0xFF, rightColor and 0xFF, amount)
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
    }

    private fun lerp(start: Int, end: Int, amount: Float) = (start + (end - start) * amount).toInt().coerceIn(0, 255)
}

object GradientStopCodec {
    fun encode(stops: List<GradientColorStop>): String = GradientColors.normalize(stops).joinToString(";") { stop ->
        "${stop.position},${stop.colorArgb.toString(16)}"
    }

    fun decode(encoded: String?): List<GradientColorStop> {
        val parsed = encoded.orEmpty().split(';').mapNotNull { token ->
            val parts = token.split(',', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val position = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val color = parts[1].toLongOrNull(16) ?: return@mapNotNull null
            GradientColorStop(position, color)
        }
        return GradientColors.normalize(parsed)
    }
}

object GradientShaderFactory {
    private const val DIAMOND_BITMAP_SIZE = 256
    private val diamondCache = object : LruCache<String, Bitmap>(24) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    fun create(definition: GradientDefinition, startX: Float, startY: Float, endX: Float, endY: Float): Shader {
        val normalized = definition.normalized()
        val stops = GradientColors.resolved(normalized)
        val colors = stops.map { it.colorArgb.toInt() }.toIntArray()
        val positions = stops.map(GradientColorStop::position).toFloatArray()
        val dx = endX - startX
        val dy = endY - startY
        val radius = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val tileMode = tileMode(normalized.edgeMode)
        return when (normalized.kind) {
            GradientKind.LINEAR -> LinearGradient(startX, startY, endX.takeUnless { it == startX && endY == startY } ?: startX + 1f, endY, colors, positions, tileMode)
            GradientKind.RADIAL -> RadialGradient(startX, startY, radius, colors, positions, tileMode)
            GradientKind.ELLIPSE -> RadialGradient(startX, startY, radius, colors, positions, tileMode).also { shader ->
                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                shader.setLocalMatrix(Matrix().apply {
                    postTranslate(-startX, -startY)
                    postRotate(-angle)
                    postScale(1f, normalized.ellipseRatio)
                    postRotate(angle)
                    postTranslate(startX, startY)
                })
            }
            GradientKind.ANGULAR -> SweepGradient(startX, startY, colors, positions).also { shader ->
                shader.setLocalMatrix(Matrix().apply { postRotate(Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat(), startX, startY) })
            }
            GradientKind.REFLECTED -> {
                val reflectedStops = buildList {
                    stops.asReversed().forEach { add(GradientColorStop((1f - it.position) * .5f, it.colorArgb)) }
                    stops.drop(1).forEach { add(GradientColorStop(.5f + it.position * .5f, it.colorArgb)) }
                }.sortedBy(GradientColorStop::position)
                LinearGradient(
                    startX - dx,
                    startY - dy,
                    startX + dx,
                    startY + dy,
                    reflectedStops.map { it.colorArgb.toInt() }.toIntArray(),
                    reflectedStops.map(GradientColorStop::position).toFloatArray(),
                    tileMode,
                )
            }
            GradientKind.DIAMOND -> diamondShader(normalized, startX, startY, dx, dy, radius)
        }
    }

    private fun diamondShader(definition: GradientDefinition, startX: Float, startY: Float, dx: Float, dy: Float, radius: Float): Shader {
        val resolved = GradientColors.resolved(definition)
        val key = buildString {
            append(definition.edgeMode.name).append(':').append(definition.reverse).append(':')
            resolved.forEach { append(it.position).append('@').append(it.colorArgb).append(';') }
        }
        val bitmap = synchronized(diamondCache) {
            diamondCache.get(key) ?: Bitmap.createBitmap(DIAMOND_BITMAP_SIZE, DIAMOND_BITMAP_SIZE, Bitmap.Config.ARGB_8888).also { created ->
                val pixels = IntArray(DIAMOND_BITMAP_SIZE * DIAMOND_BITMAP_SIZE)
                val center = (DIAMOND_BITMAP_SIZE - 1) / 2f
                for (y in 0 until DIAMOND_BITMAP_SIZE) for (x in 0 until DIAMOND_BITMAP_SIZE) {
                    val amount = (abs(x - center) + abs(y - center)) / center
                    pixels[y * DIAMOND_BITMAP_SIZE + x] = GradientColors.sample(resolved, amount, definition.edgeMode)
                }
                created.setPixels(pixels, 0, DIAMOND_BITMAP_SIZE, 0, 0, DIAMOND_BITMAP_SIZE, DIAMOND_BITMAP_SIZE)
                diamondCache.put(key, created)
            }
        }
        val shader = BitmapShader(bitmap, tileMode(definition.edgeMode), tileMode(definition.edgeMode))
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        shader.setLocalMatrix(Matrix().apply {
            postTranslate(-DIAMOND_BITMAP_SIZE / 2f, -DIAMOND_BITMAP_SIZE / 2f)
            postScale(radius * 2f / DIAMOND_BITMAP_SIZE, radius * 2f / DIAMOND_BITMAP_SIZE)
            postRotate(angle)
            postTranslate(startX, startY)
        })
        return shader
    }

    private fun tileMode(mode: GradientEdgeMode): Shader.TileMode = when (mode) {
        GradientEdgeMode.CLAMP -> Shader.TileMode.CLAMP
        GradientEdgeMode.REPEAT -> Shader.TileMode.REPEAT
        GradientEdgeMode.MIRROR -> Shader.TileMode.MIRROR
        GradientEdgeMode.TRANSPARENT -> if (Build.VERSION.SDK_INT >= 31) Shader.TileMode.DECAL else Shader.TileMode.CLAMP
    }
}

fun PageElement.Shape.gradientDefinition(): GradientDefinition? {
    if (gradientEndArgb == null && gradientStops.size < 2) return null
    val fallbackStart = fillArgb ?: colorArgb
    val stops = gradientStops.takeIf { it.size >= 2 } ?: listOf(
        GradientColorStop(0f, fallbackStart),
        GradientColorStop(1f, gradientEndArgb ?: fallbackStart),
    )
    return GradientDefinition(
        kind = gradientKind,
        edgeMode = gradientEdgeMode,
        reverse = gradientReverse,
        dither = gradientDither,
        ellipseRatio = gradientEllipseRatio,
        stops = stops,
    ).normalized()
}
