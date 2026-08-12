package app.inkloom.document

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

enum class LiquifyMode {
    PUSH,
    PUSH_LEFT,
    PUSH_RIGHT,
    TWIRL_CLOCKWISE,
    TWIRL_COUNTERCLOCKWISE,
    PINCH,
    EXPAND,
    CRYSTALS,
    EDGE,
    RECONSTRUCT,
}

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
                            LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT -> {
                                val direction = if (mode == LiquifyMode.PUSH_LEFT) 1f else -1f
                                point.x - deltaY / aspect * falloff * direction to
                                    point.y + deltaX * aspect * falloff * direction
                            }
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
                            LiquifyMode.CRYSTALS -> {
                                // Procreate's Crystals pushes outward unevenly. Quantizing the
                                // polar direction creates stable sharp shards instead of temporal
                                // random noise that would flicker between input frames.
                                val angle = atan2(dy, dx)
                                val shardAngle = (angle / CRYSTAL_SECTOR_RADIANS).toInt() * CRYSTAL_SECTOR_RADIANS
                                val distanceScale = falloff * CRYSTAL_EXPANSION
                                point.x + cos(shardAngle) * distanceScale to
                                    point.y + sin(shardAngle) * distanceScale * aspect
                            }
                            LiquifyMode.EDGE -> {
                                // Edge folds the two sides toward the line traced by the pen. The
                                // stroke tangent is the fold line; only its perpendicular component
                                // is removed. With no movement, retain the last geometry unchanged.
                                val tangentLength = hypot(deltaX.toDouble(), (deltaY / aspect).toDouble()).toFloat()
                                if (tangentLength <= .000001f) point.x to point.y else {
                                    val tangentX = deltaX / tangentLength
                                    val tangentY = deltaY / aspect / tangentLength
                                    val perpendicular = dx * -tangentY + dy * tangentX
                                    point.x + tangentY * perpendicular * falloff to
                                        point.y - tangentX * perpendicular * falloff * aspect
                                }
                            }
                            LiquifyMode.RECONSTRUCT -> point.x to point.y
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
                            LiquifyMode.PUSH_LEFT, LiquifyMode.PUSH_RIGHT -> {
                                val direction = if (mode == LiquifyMode.PUSH_LEFT) 1f else -1f
                                mask.copy(
                                    x = (mask.x - deltaY / aspect * falloff * direction).coerceIn(0f, 1f),
                                    y = (mask.y + deltaX * aspect * falloff * direction).coerceIn(0f, 1f),
                                )
                            }
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
                            LiquifyMode.CRYSTALS -> {
                                val angle = atan2(dy, dx)
                                val shardAngle = (angle / CRYSTAL_SECTOR_RADIANS).toInt() * CRYSTAL_SECTOR_RADIANS
                                val distanceScale = falloff * CRYSTAL_EXPANSION
                                mask.copy(
                                    x = (mask.x + cos(shardAngle) * distanceScale).coerceIn(0f, 1f),
                                    y = (mask.y + sin(shardAngle) * distanceScale * aspect).coerceIn(0f, 1f),
                                )
                            }
                            LiquifyMode.EDGE -> {
                                val tangentLength = hypot(deltaX.toDouble(), (deltaY / aspect).toDouble()).toFloat()
                                if (tangentLength <= .000001f) mask else {
                                    val tangentX = deltaX / tangentLength
                                    val tangentY = deltaY / aspect / tangentLength
                                    val perpendicular = dx * -tangentY + dy * tangentX
                                    mask.copy(
                                        x = (mask.x + tangentY * perpendicular * falloff).coerceIn(0f, 1f),
                                        y = (mask.y - tangentX * perpendicular * falloff * aspect).coerceIn(0f, 1f),
                                    )
                                }
                            }
                            LiquifyMode.RECONSTRUCT -> mask
                        }
                    }
                },
            )
        }
    }

    /** Restores only the touched portion toward the gesture-start snapshot. This mirrors
     * Procreate's Reconstruct mode without discarding unaffected Liquify work. */
    fun reconstruct(
        current: List<InkStroke>,
        original: List<InkStroke>,
        centerX: Float,
        centerY: Float,
        radius: Float,
        strength: Float,
        hardness: Float,
        pageWidth: Float,
        pageHeight: Float,
        eligible: (InkStroke) -> Boolean = { true },
    ): List<InkStroke> {
        val originals = original.associateBy(InkStroke::id)
        val safeRadius = radius.coerceAtLeast(.0001f)
        val aspect = pageWidth.coerceAtLeast(1f) / pageHeight.coerceAtLeast(1f)
        fun influence(x: Float, y: Float): Float {
            val distance = hypot((x - centerX).toDouble(), ((y - centerY) / aspect).toDouble()).toFloat()
            if (distance >= safeRadius) return 0f
            return (1f - distance / safeRadius).pow(1f + hardness.coerceIn(0f, 1f) * 4f) * strength.coerceIn(0f, 1f)
        }
        return current.map { stroke ->
            val source = originals[stroke.id]
            if (!eligible(stroke) || source == null) stroke else stroke.copy(
                points = stroke.points.mapIndexed { index, point ->
                    val target = source.points.getOrNull(index) ?: return@mapIndexed point
                    val amount = influence(point.x, point.y)
                    point.copy(
                        x = point.x + (target.x - point.x) * amount,
                        y = point.y + (target.y - point.y) * amount,
                    )
                },
                masks = stroke.masks.mapIndexed { index, mask ->
                    val target = source.masks.getOrNull(index) ?: return@mapIndexed mask
                    val amount = influence(mask.x, mask.y)
                    mask.copy(
                        x = mask.x + (target.x - mask.x) * amount,
                        y = mask.y + (target.y - mask.y) * amount,
                    )
                },
            )
        }
    }

    private const val CRYSTAL_SECTOR_RADIANS = (Math.PI / 8.0).toFloat()
    private const val CRYSTAL_EXPANSION = .035f
}
