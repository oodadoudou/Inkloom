package app.inkloom.document

import kotlin.math.floor

/**
 * PENCIL_RENDERER_V4 runtime semantics for the stamped graphite footprint, kept deliberately
 * separate from the brush-tip texture.
 *
 *  - [tiltWidthFactor]: tilt widens the footprint along the axis perpendicular to the stroke
 *    (a width/aspect semantic, not a uniform scalar size change). Gated on an established tilt
 *    sensor so an unknown first sample stays neutral (factor 1).
 *  - [tiltGrainDensity]: tilt boosts the procedural paper-grain density (the pencil catches more
 *    paper tooth when laid flat). Also sensor-gated.
 *  - [paperGrainAlpha]: deterministic procedural paper grain anchored in document coordinates.
 *    The grain value is a fixed function of page position, so overlapping dabs at the same
 *    document point share one grain sample while consecutive fixed-spacing dabs land on
 *    different grain — which is what breaks the authored dab-spacing periodicity.
 */
object GraphiteFootprint {
    fun tiltWidthFactor(stroke: InkStroke, point: StrokePoint): Float {
        if (stroke.graphiteTiltWidth <= .0001f) return 1f
        if (!point.hasSensor(BrushSensorFlags.TILT)) return 1f
        val response = BrushTilt.response(point, stroke.graphiteTiltWidthStart)
        if (response <= .0001f) return 1f
        return 1f + stroke.graphiteTiltWidth.coerceIn(0f, 3f) * response
    }

    fun tiltGrainDensity(stroke: InkStroke, point: StrokePoint): Float {
        if (stroke.graphiteTiltGrain <= .0001f) return 1f
        if (!point.hasSensor(BrushSensorFlags.TILT)) return 1f
        val response = BrushTilt.response(point, stroke.graphiteTiltGrainStart)
        if (response <= .0001f) return 1f
        return 1f + stroke.graphiteTiltGrain.coerceIn(0f, 2f) * response
    }

    /** Multiplier applied to the deposited graphite alpha from the paper grain (1 = no grain). */
    fun paperGrainAlpha(stroke: InkStroke, documentX: Float, documentY: Float): Float {
        if (!stroke.proceduralPaperGrainEnabled) return 1f
        val density = stroke.proceduralPaperGrainDensity.coerceIn(0f, 1f)
        if (density <= .0001f) return 1f
        val scale = stroke.proceduralPaperGrainScale.coerceIn(1f, 4000f)
        val value = ProceduralPaperGrain.value(documentX, documentY, scale, PAPER_GRAIN_SALT)
        // Higher grain cells deposit less graphite (more paper tooth). Keep a neutral floor so the
        // stroke never disappears at a single grain cell.
        val floor = 1f - density
        return (floor + density * value).coerceIn(0f, 1f)
    }

    /** The paper grain is a page property, not a stroke property: overlapping dabs at the same
     * document position must share one paper surface regardless of which stroke drew them, so the
     * field is seeded by a fixed salt rather than the stroke's render seed. */
    private const val PAPER_GRAIN_SALT = 0x40E5A7C3L
}

/**
 * Deterministic procedural paper grain anchored in document coordinates. The field is a smooth
 * value-noise function of page position: two samples taken at the same document point return the
 * same value, and the function has no alignment with any stroke path or dab spacing.
 */
object ProceduralPaperGrain {
    fun value(documentX: Float, documentY: Float, scale: Float, seed: Long): Float {
        val safeScale = scale.takeIf(Float::isFinite)?.coerceIn(1f, 4000f) ?: 100f
        val cellX = floor(documentX / safeScale)
        val cellY = floor(documentY / safeScale)
        val fx = (documentX / safeScale - cellX).coerceIn(0f, 1f)
        val fy = (documentY / safeScale - cellY).coerceIn(0f, 1f)
        val sx = smooth(fx)
        val sy = smooth(fy)
        val v00 = hash(cellX.toInt(), cellY.toInt(), seed)
        val v10 = hash(cellX.toInt() + 1, cellY.toInt(), seed)
        val v01 = hash(cellX.toInt(), cellY.toInt() + 1, seed)
        val v11 = hash(cellX.toInt() + 1, cellY.toInt() + 1, seed)
        val top = lerp(v00, v10, sx)
        val bottom = lerp(v01, v11, sx)
        val base = lerp(top, bottom, sy)
        // A fine second octave makes consecutive fixed-spacing dabs land on decorrelated grain, so
        // the authored dab spacing no longer reads as the dominant visible periodicity, while the
        // field stays deterministic and anchored in document coordinates.
        val fineCellX = floor(documentX / (safeScale / FINE_OCTAVE_DIVISOR))
        val fineCellY = floor(documentY / (safeScale / FINE_OCTAVE_DIVISOR))
        val fine = hash(fineCellX.toInt(), fineCellY.toInt(), seed xor FINE_SEED)
        return (base * .5f + fine * .5f).coerceIn(0f, 1f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private fun hash(x: Int, y: Int, seed: Long): Float {
        var h = seed xor (x.toLong() * 0x27d4eb2dL) xor (y.toLong() * 0x9e3779b9L)
        h = h xor (h ushr 33)
        h *= -49064778989728563L
        h = h xor (h ushr 33)
        return ((h ushr 40) and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()
    }

    private const val FINE_OCTAVE_DIVISOR = 4f
    private const val FINE_SEED = 0x51A6C2D9L
}
