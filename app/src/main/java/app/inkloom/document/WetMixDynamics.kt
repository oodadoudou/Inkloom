package app.inkloom.document

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max

object WetMixDynamics {
    /**
     * Deterministic glaze approximation shared by the live canvas and exports.
     * CSP's density-of-paint value raises opacity while amount-of-paint raises the
     * ratio of the drawing color to existing pigment. Normal alpha compositing is
     * used as a deterministic glaze approximation; fluid transport is not claimed.
     */
    fun alphaMultiplier(watercolor: Boolean, colorMix: Float, paintMix: Float): Float {
        if (!watercolor) return 1f
        return (.08f + .92f * paintMix.coerceIn(0f, 1f)).coerceIn(.08f, 1f)
    }

    fun alphaMultiplier(stroke: InkStroke, point: StrokePoint): Float {
        if (stroke.watercolor && stroke.procreateWetMix.isDisabled() && CspColorMixMode.normalize(stroke.cspColorMixMode) == CspColorMixMode.SMEAR) {
            return 1f
        }
        if (stroke.procreateWetMix.isDisabled()) return alphaMultiplier(
            watercolor = stroke.watercolor,
            colorMix = colorMix(stroke, point),
            paintMix = paintMix(stroke, point),
        )
        val wet = stroke.procreateWetMix
        val dilution = effectiveDilution(stroke, point)
        val load = reservoir(stroke, point)
        val attack = wet.attack.coerceIn(0f, 1f)
        val pressureAdhesion = 1f - attack + attack * (.25f + .75f * point.pressure.coerceIn(0f, 1f))
        val pigment = (1f - dilution * .78f).coerceIn(.12f, 1f)
        val adhesion = (.42f + .58f * attack) * pressureAdhesion
        val gradeNoise = if (wet.grade >= 0f) 1f else {
            val roughness = -wet.grade.coerceIn(-1f, 0f)
            (1f - roughness * randomUnit(stroke, point, GRADE_SALT) * .72f).coerceAtLeast(.12f)
        }
        return (pigment * (.04f + .96f * load) * adhesion * gradeNoise).coerceIn(.01f, 1f)
    }

    /** Attack controls how much of the authored shape adheres under pressure. */
    fun sizeMultiplier(stroke: InkStroke, point: StrokePoint): Float {
        val attack = stroke.procreateWetMix.attack.coerceIn(0f, 1f)
        if (stroke.procreateWetMix.isDisabled() || attack <= .001f) return 1f
        return (1f - attack + attack * (.3f + .7f * point.pressure.coerceIn(0f, 1f))).coerceIn(.3f, 1f)
    }

    fun effectiveDilution(stroke: InkStroke, point: StrokePoint): Float {
        val wet = stroke.procreateWetMix
        val base = wet.dilution.coerceIn(0f, 1f)
        if (wet.wetnessJitter <= .001f) return base
        val signed = randomUnit(stroke, point, WETNESS_SALT) * 2f - 1f
        return (base + signed * wet.wetnessJitter.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    /** Charge is replenished at every new stroke and consumed by physical path distance. */
    fun reservoir(stroke: InkStroke, point: StrokePoint): Float {
        val wet = stroke.procreateWetMix
        if (wet.isDisabled()) return 1f
        val diameterTravel = point.pathDistance.coerceAtLeast(0f) * REFERENCE_WIDTH / stroke.width.coerceAtLeast(.5f)
        val capacityDiameters = .08f + wet.charge.coerceIn(0f, 1f) * 22f
        val dilutionDrain = .25f + effectiveDilution(stroke, point) * 1.75f
        return exp((-diameterTravel / capacityDiameters * dilutionDrain).toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun pullStrength(stroke: InkStroke, point: StrokePoint): Float =
        (stroke.procreateWetMix.pull.coerceIn(0f, 1f) * (.2f + .8f * effectiveDilution(stroke, point))).coerceIn(0f, 1f)

    fun blurAmount(stroke: InkStroke, point: StrokePoint): Float {
        val wet = stroke.procreateWetMix
        val jitter = randomUnit(stroke, point, BLUR_SALT) * wet.blurJitter.coerceIn(0f, 1f)
        return (wet.blur + jitter).coerceIn(0f, 1f)
    }

    fun colorMix(stroke: InkStroke, point: StrokePoint): Float = dynamicValue(
        base = stroke.colorMix,
        pressureEnabled = stroke.colorMixPressureEnabled,
        pressureMinimum = stroke.colorMixPressureMinimum,
        pressureCurve = stroke.colorMixPressureCurve,
        secondary = stroke.colorMixSecondaryDynamics,
        point = point,
        strokeSeed = stroke.renderSeed,
        salt = COLOR_MIX_SALT,
    )

    fun paintMix(stroke: InkStroke, point: StrokePoint): Float = dynamicValue(
        base = stroke.paintMix,
        pressureEnabled = stroke.paintMixPressureEnabled,
        pressureMinimum = stroke.paintMixPressureMinimum,
        pressureCurve = stroke.paintMixPressureCurve,
        secondary = stroke.paintMixSecondaryDynamics,
        point = point,
        strokeSeed = stroke.renderSeed,
        salt = PAINT_MIX_SALT,
    )

    private fun dynamicValue(
        base: Float,
        pressureEnabled: Boolean,
        pressureMinimum: Float,
        pressureCurve: List<BrushCurvePoint>,
        secondary: BrushSecondaryDynamics,
        point: StrokePoint,
        strokeSeed: Int,
        salt: Int,
    ): Float = (base *
        BrushDynamics.pressure(pressureEnabled, pressureMinimum, pressureCurve, point.pressure) *
        BrushDynamics.secondary(secondary, point, strokeSeed, salt)).coerceIn(0f, 1f)

    private const val COLOR_MIX_SALT = 149
    private const val PAINT_MIX_SALT = 167
    private const val WETNESS_SALT = 181
    private const val BLUR_SALT = 191
    private const val GRADE_SALT = 199
    private const val REFERENCE_WIDTH = 1600f

    private fun randomUnit(stroke: InkStroke, point: StrokePoint, salt: Int): Float {
        val spacing = max(stroke.spacing.coerceAtLeast(.01f) * stroke.width.coerceAtLeast(.5f), .5f)
        val stamp = floor(point.pathDistance.coerceAtLeast(0f) * REFERENCE_WIDTH / spacing).toInt()
        var value = stroke.renderSeed xor (stamp * -0x61c88647) xor salt
        value = value xor (value ushr 16)
        value *= -0x7a143595
        value = value xor (value ushr 15)
        return (value ushr 8 and 0x00FFFFFF) / 16777215f
    }
}
