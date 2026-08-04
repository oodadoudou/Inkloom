package app.inkloom.document

/** Resolves CSP spraying-effect dynamics identically for the live canvas and exports. */
object SprayDynamics {
    fun particleSize(stroke: InkStroke, point: StrokePoint): Float = dynamicValue(
        base = stroke.sprayParticleSize,
        pressureEnabled = stroke.sprayParticleSizePressureEnabled,
        pressureMinimum = stroke.sprayParticleSizePressureMinimum,
        pressureCurve = stroke.sprayParticleSizePressureCurve,
        secondary = stroke.sprayParticleSizeSecondaryDynamics,
        point = point,
        strokeSeed = stroke.renderSeed,
        salt = PARTICLE_SIZE_SALT,
    ).coerceIn(.01f, 4000f)

    fun density(stroke: InkStroke, point: StrokePoint): Float = dynamicValue(
        base = stroke.sprayDensity,
        pressureEnabled = stroke.sprayDensityPressureEnabled,
        pressureMinimum = stroke.sprayDensityPressureMinimum,
        pressureCurve = stroke.sprayDensityPressureCurve,
        secondary = stroke.sprayDensitySecondaryDynamics,
        point = point,
        strokeSeed = stroke.renderSeed,
        salt = DENSITY_SALT,
    ).coerceIn(1f, 100f)

    fun dualParticleSize(stroke: InkStroke, point: StrokePoint): Float = dynamicValue(
        base = stroke.dualSprayParticleSize,
        dynamics = stroke.dualSprayParticleSizeDynamics,
        point = point,
        strokeSeed = stroke.renderSeed,
        salt = DUAL_PARTICLE_SIZE_SALT,
    ).coerceIn(.01f, 4000f)

    fun dualDensity(stroke: InkStroke, point: StrokePoint): Float = dynamicValue(
        base = stroke.dualSprayDensity,
        dynamics = stroke.dualSprayDensityDynamics,
        point = point,
        strokeSeed = stroke.renderSeed,
        salt = DUAL_DENSITY_SALT,
    ).coerceIn(1f, 100f)

    private fun dynamicValue(base: Float, dynamics: BrushParameterDynamics, point: StrokePoint, strokeSeed: Int, salt: Int): Float = dynamicValue(
        base = base,
        pressureEnabled = dynamics.pressureEnabled,
        pressureMinimum = dynamics.pressureMinimum,
        pressureCurve = dynamics.pressureCurve,
        secondary = dynamics.secondary,
        point = point,
        strokeSeed = strokeSeed,
        salt = salt,
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
    ): Float = base *
        BrushDynamics.pressure(pressureEnabled, pressureMinimum, pressureCurve, point.pressure) *
        BrushDynamics.secondary(secondary, point, strokeSeed, salt)

    private const val PARTICLE_SIZE_SALT = 107
    private const val DENSITY_SALT = 131
    private const val DUAL_PARTICLE_SIZE_SALT = 149
    private const val DUAL_DENSITY_SALT = 157
}
