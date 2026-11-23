package app.inkloom.document

import kotlinx.serialization.Serializable

/** Bitmask describing which brush-input sensors are currently established for a sample. The first
 * modeled sample of a stroke has no history: velocity is 0 by construction and tilt/direction are
 * often device defaults, so they are NOT_YET_ESTABLISHED and brush dynamics treat them as neutral
 * instead of passing a numeric zero through a response curve as an extreme value. */
object BrushSensorFlags {
    const val VELOCITY = 1
    const val TILT = 2
    const val ORIENTATION = 4
    const val ALL = VELOCITY or TILT or ORIENTATION
}

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timeMs: Long = 0,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    val velocity: Float = 0f,
    /** Cumulative page-space travel normalized to the 1600 px brush reference width. */
    val pathDistance: Float = 0f,
    /** Local pigment produced by Blend. Null inherits the stroke's base color. */
    val pigmentArgb: Long? = null,
    /** Established brush-input sensors; see [BrushSensorFlags]. Defaults to all-established so
     * imported/replayed points behave as real samples; the live input modeler clears the flags on
     * the first sample only. */
    val sensorFlags: Int = BrushSensorFlags.ALL,
) {
    fun hasSensor(flag: Int): Boolean = sensorFlags and flag != 0
}

@Serializable
data class BrushCurvePoint(val x: Float, val y: Float)

@Serializable
data class BrushSecondaryDynamics(
    val tiltEnabled: Boolean = false,
    val tiltMinimum: Float = 0f,
    /** Normalized stylus tilt at which this parameter begins responding. */
    val tiltStart: Float = 0f,
    /** Optional response curve; empty retains the legacy increasing response. */
    val tiltCurve: List<BrushCurvePoint> = emptyList(),
    val velocityEnabled: Boolean = false,
    val velocityMinimum: Float = 0f,
    /** CSP's velocity multiplier. A value of 1 preserves the normalized input. */
    val velocityScale: Float = 1f,
    val velocityCurve: List<BrushCurvePoint> = emptyList(),
    val randomEnabled: Boolean = false,
    val randomMinimum: Float = 0f,
)

/** Complete CSP dynamics for one brush parameter. Secondary brushes have their
 * own effectors and must not inherit the main tip's pressure/tilt/speed/random input. */
@Serializable
data class BrushParameterDynamics(
    val pressureEnabled: Boolean = false,
    val pressureMinimum: Float = 0f,
    val pressureCurve: List<BrushCurvePoint> = emptyList(),
    val secondary: BrushSecondaryDynamics = BrushSecondaryDynamics(),
)

@Serializable
data class BrushColorDynamics(
    val pressureEnabled: Boolean = false,
    val pressureMinimum: Float = 0f,
    val pressureCurve: List<BrushCurvePoint> = emptyList(),
    val tiltEnabled: Boolean = false,
    val tiltMinimum: Float = 0f,
    /** Normalized stylus tilt at which this parameter begins responding. */
    val tiltStart: Float = 0f,
    val velocityEnabled: Boolean = false,
    val velocityMinimum: Float = 0f,
    /** CSP's velocity multiplier. A value of 1 preserves the normalized input. */
    val velocityScale: Float = 1f,
    val velocityCurve: List<BrushCurvePoint> = emptyList(),
    val randomEnabled: Boolean = false,
    val randomMinimum: Float = -1f,
)

/** Procreate keeps stamp, stroke, pressure, and tilt color programs independent. */
@Serializable
data class ProcreateColorDynamics(
    val stampHue: Float = 0f,
    val stampSaturation: Float = 0f,
    val stampLightness: Float = 0f,
    val stampDarkness: Float = 0f,
    val stampSecondary: Float = 0f,
    val strokeHue: Float = 0f,
    val strokeSaturation: Float = 0f,
    val strokeLightness: Float = 0f,
    val strokeDarkness: Float = 0f,
    val strokeSecondary: Float = 0f,
    val pressureHue: Float = 0f,
    val pressureHueCurve: List<BrushCurvePoint> = emptyList(),
    val pressureSaturation: Float = 0f,
    val pressureSaturationCurve: List<BrushCurvePoint> = emptyList(),
    val pressureBrightness: Float = 0f,
    val pressureBrightnessCurve: List<BrushCurvePoint> = emptyList(),
    val pressureSecondary: Float = 0f,
    val pressureSecondaryCurve: List<BrushCurvePoint> = emptyList(),
    val tiltHue: Float = 0f,
    val tiltSaturation: Float = 0f,
    val tiltBrightness: Float = 0f,
    val tiltSecondary: Float = 0f,
    val tiltStart: Float = 0f,
) {
    fun isDisabled(): Boolean =
        stampHue == 0f && stampSaturation == 0f && stampLightness == 0f && stampDarkness == 0f && stampSecondary == 0f &&
            strokeHue == 0f && strokeSaturation == 0f && strokeLightness == 0f && strokeDarkness == 0f && strokeSecondary == 0f &&
            pressureHue == 0f && pressureSaturation == 0f && pressureBrightness == 0f && pressureSecondary == 0f &&
            tiltHue == 0f && tiltSaturation == 0f && tiltBrightness == 0f && tiltSecondary == 0f
}

/** Procreate Brush Studio > Wet Mix values retained independently from CSP color mixing.
 * Values use the normalized archive range. Grade retains the archive's signed authored direction;
 * [legacyMixSoftening] preserves the older scalar without folding it into Grade. */
@Serializable
data class ProcreateWetMix(
    val dilution: Float = 0f,
    val charge: Float = 0f,
    val attack: Float = 0f,
    val pull: Float = 0f,
    val grade: Float = 0f,
    val blur: Float = 0f,
    val blurJitter: Float = 0f,
    val wetnessJitter: Float = 0f,
    val legacyMixSoftening: Float = 0f,
) {
    fun isDisabled(): Boolean =
        dilution == 0f && charge == 0f && attack == 0f && pull == 0f && grade == 0f &&
            blur == 0f && blurJitter == 0f && wetnessJitter == 0f && legacyMixSoftening == 0f
}

/** Procreate Brush Studio > Taper. Pressure and Touch are separate authored programs; collapsing
 * them loses finger drawing, opacity taper, tip shape, and the shared pressure response. */
@Serializable
data class ProcreateTaper(
    val pressureStartLength: Float = 0f,
    val pressureEndLength: Float = 0f,
    val pressureSize: Float = 0f,
    val pressureOpacity: Float = 0f,
    val pressureTip: Float = 0f,
    val touchStartLength: Float = 0f,
    val touchEndLength: Float = 0f,
    val touchSize: Float = 0f,
    val touchOpacity: Float = 0f,
    val touchTip: Float = 0f,
    val pressureResponse: Float = 0f,
    val pressureLinked: Boolean = false,
    val touchLinked: Boolean = false,
    val classic: Boolean = false,
) {
    fun isDisabled(): Boolean =
        (pressureStartLength == 0f && pressureEndLength == 0f || pressureSize == 0f && pressureOpacity == 0f) &&
            (touchStartLength == 0f && touchEndLength == 0f || touchSize == 0f && touchOpacity == 0f)
}

@Serializable
enum class BrushTaperMode { DISTANCE, PERCENTAGE, FADE }
