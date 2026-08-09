package app.inkloom.document

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

enum class LiquifyMode { PUSH, TWIRL_CLOCKWISE, TWIRL_COUNTERCLOCKWISE, PINCH, EXPAND }

/** Local, reversible Liquify deformation for Inkloom's vector stroke document model. */
object VectorLiquify {
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
        mode: LiquifyMode,
        eligible: (InkStroke) -> Boolean = { true },
    ): List<InkStroke> {
        val safeRadius = radius.coerceAtLeast(.0001f)
        val safeStrength = strength.coerceIn(0f, 1f)
        val aspect = pageWidth.coerceAtLeast(1f) / pageHeight.coerceAtLeast(1f)
        return strokes.map { stroke ->
            if (!eligible(stroke)) stroke else stroke.copy(
                points = stroke.points.map { point ->
                    val dx = point.x - centerX
                    val dy = (point.y - centerY) / aspect
                    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (distance >= safeRadius) point else {
                        val falloff = (1f - distance / safeRadius).coerceIn(0f, 1f)
                            .pow(1f + hardness.coerceIn(0f, 1f) * 4f) * safeStrength
                        val transformed = when (mode) {
                            LiquifyMode.PUSH -> point.x + deltaX * falloff to point.y + deltaY * falloff
                            LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE -> {
                                val direction = if (mode == LiquifyMode.TWIRL_CLOCKWISE) 1f else -1f
                                val angle = direction * falloff * .18f
                                val c = cos(angle); val s = sin(angle)
                                centerX + dx * c - dy * s to centerY + (dx * s + dy * c) * aspect
                            }
                            LiquifyMode.PINCH, LiquifyMode.EXPAND -> {
                                val direction = if (mode == LiquifyMode.PINCH) -1f else 1f
                                val scale = 1f + direction * falloff * .055f
                                centerX + dx * scale to centerY + dy * scale * aspect
                            }
                        }
                        point.copy(x = transformed.first.coerceIn(0f, 1f), y = transformed.second.coerceIn(0f, 1f))
                    }
                },
                masks = stroke.masks.map { mask ->
                    val dx = mask.x - centerX
                    val dy = (mask.y - centerY) / aspect
                    val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (distance >= safeRadius) mask else {
                        val falloff = (1f - distance / safeRadius).coerceIn(0f, 1f)
                            .pow(1f + hardness.coerceIn(0f, 1f) * 4f) * safeStrength
                        when (mode) {
                            LiquifyMode.PUSH -> mask.copy(x = (mask.x + deltaX * falloff).coerceIn(0f, 1f), y = (mask.y + deltaY * falloff).coerceIn(0f, 1f))
                            LiquifyMode.TWIRL_CLOCKWISE, LiquifyMode.TWIRL_COUNTERCLOCKWISE -> {
                                val direction = if (mode == LiquifyMode.TWIRL_CLOCKWISE) 1f else -1f
                                val angle = direction * falloff * .18f
                                val distanceFromCenter = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                val baseAngle = atan2(dy, dx) + angle
                                mask.copy(x = (centerX + cos(baseAngle) * distanceFromCenter).coerceIn(0f, 1f), y = (centerY + sin(baseAngle) * distanceFromCenter * aspect).coerceIn(0f, 1f))
                            }
                            LiquifyMode.PINCH, LiquifyMode.EXPAND -> {
                                val direction = if (mode == LiquifyMode.PINCH) -1f else 1f
                                val scale = 1f + direction * falloff * .055f
                                mask.copy(x = (centerX + dx * scale).coerceIn(0f, 1f), y = (centerY + dy * scale * aspect).coerceIn(0f, 1f))
                            }
                        }
                    }
                },
            )
        }
    }
}
