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
        val affected = strokes.filter(eligible).mapNotNull { stroke ->
            stroke.points.maxOfOrNull(::influence)?.takeIf { it > 0f }?.let { stroke to it }
        }
        if (affected.isEmpty()) return strokes
        val totalWeight = affected.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(.0001f)
        fun channel(shift: Int): Int = (affected.sumOf { (stroke, weight) ->
            (((stroke.colorArgb shr shift) and 0xFF).toInt() * weight).toDouble()
        } / totalWeight).toInt().coerceIn(0, 255)
        val mixed = (channel(24).toLong() shl 24) or (channel(16).toLong() shl 16) or (channel(8).toLong() shl 8) or channel(0).toLong()
        val weights = affected.associate { it.first.id to it.second }
        return strokes.map { stroke ->
            val weight = weights[stroke.id] ?: return@map stroke
            val colorMix = (weight * .32f).coerceIn(0f, 1f)
            stroke.copy(
                colorArgb = blendColor(stroke.colorArgb, mixed, colorMix),
                points = stroke.points.map { point ->
                    val local = influence(point)
                    point.copy(
                        x = (point.x + deltaX * local * .22f).coerceIn(0f, 1f),
                        y = (point.y + deltaY * local * .22f).coerceIn(0f, 1f),
                    )
                },
                masks = stroke.masks.map { mask ->
                    val local = influence(StrokePoint(mask.x, mask.y))
                    mask.copy(x = (mask.x + deltaX * local * .22f).coerceIn(0f, 1f), y = (mask.y + deltaY * local * .22f).coerceIn(0f, 1f))
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
}
