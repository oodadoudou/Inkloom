package app.inkloom.document

import kotlin.math.hypot
import kotlin.math.pow

/**
 * Vector-document blend operation. It mixes and drags existing editable pigment without
 * creating a new painted stroke, matching the defining behavior of professional blend tools.
 */
object VectorBlend {
    fun apply(
        strokes: List<InkStroke>,
        centerX: Float,
        centerY: Float,
        deltaX: Float,
        deltaY: Float,
        radius: Float,
        strength: Float,
        hardness: Float,
        pageWidth: Float,
        pageHeight: Float,
        eligible: (InkStroke) -> Boolean = { true },
    ): List<InkStroke> {
        val safeRadius = radius.coerceAtLeast(.0001f)
        val aspect = pageWidth.coerceAtLeast(1f) / pageHeight.coerceAtLeast(1f)
        fun influence(point: StrokePoint): Float {
            val dx = point.x - centerX
            val dy = (point.y - centerY) / aspect
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (distance >= safeRadius) return 0f
            return (1f - distance / safeRadius).pow(1f + hardness.coerceIn(0f, 1f) * 4f) * strength.coerceIn(0f, 1f)
        }
        val affected = strokes.filter(eligible).filter { stroke -> stroke.points.any { influence(it) > 0f } }
        if (affected.isEmpty()) return strokes
        data class Pigment(val point: StrokePoint, val color: Long)
        // Sample only pigment that is physically under the blend tip. Sampling an entire long
        // stroke both diluted local colors and turned a local operation into O(page points).
        val allPigment = affected.flatMap { stroke ->
            stroke.points.mapNotNull { point ->
                point.takeIf { influence(it) > 0f }?.let { Pigment(it, it.pigmentArgb ?: stroke.colorArgb) }
            }
        }
        val sampleStride = (allPigment.size / MAX_PIGMENT_SAMPLES).coerceAtLeast(1)
        val pigmentSamples = allPigment.filterIndexed { index, _ -> index % sampleStride == 0 }.take(MAX_PIGMENT_SAMPLES)
        fun mixedPigment(point: StrokePoint, fallback: Long): Long {
            var totalWeight = 0f
            val channels = FloatArray(4)
            pigmentSamples.forEach { sample ->
                val dx = sample.point.x - point.x
                val dy = (sample.point.y - point.y) / aspect
                val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (distance >= safeRadius) return@forEach
                val weight = (1f - distance / safeRadius).pow(2f)
                totalWeight += weight
                channels[0] += (sample.color shr 24 and 0xFF) * weight
                channels[1] += (sample.color shr 16 and 0xFF) * weight
                channels[2] += (sample.color shr 8 and 0xFF) * weight
                channels[3] += (sample.color and 0xFF) * weight
            }
            if (totalWeight <= .0001f) return fallback
            fun channel(index: Int) = (channels[index] / totalWeight).toLong().coerceIn(0, 255)
            return (channel(0) shl 24) or (channel(1) shl 16) or (channel(2) shl 8) or channel(3)
        }
        val affectedIds = affected.mapTo(hashSetOf(), InkStroke::id)
        return strokes.map { stroke ->
            if (stroke.id !in affectedIds) return@map stroke
            stroke.copy(
                points = stroke.points.map pointMap@ { point ->
                    val local = influence(point)
                    if (local <= 0f) return@pointMap point
                    val original = point.pigmentArgb ?: stroke.colorArgb
                    val mixed = mixedPigment(point, original)
                    point.copy(
                        x = (point.x + deltaX * local * .34f).coerceIn(0f, 1f),
                        y = (point.y + deltaY * local * .34f).coerceIn(0f, 1f),
                        pigmentArgb = blendColor(original, mixed, (local * .82f).coerceIn(0f, 1f)),
                    )
                },
                masks = stroke.masks.map { mask ->
                    val local = influence(StrokePoint(mask.x, mask.y))
                    if (local <= 0f) mask else mask.copy(
                        x = (mask.x + deltaX * local * .22f).coerceIn(0f, 1f),
                        y = (mask.y + deltaY * local * .22f).coerceIn(0f, 1f),
                    )
                },
            )
        }
    }

    private fun blendColor(from: Long, to: Long, amount: Float): Long {
        fun channel(shift: Int): Long {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).toLong().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val MAX_PIGMENT_SAMPLES = 512
}
