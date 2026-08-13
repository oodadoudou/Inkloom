package app.inkloom.document

/** Resolves CSP paper-texture density input dynamics for both canvas and export. */
object GrainDynamics {
    fun density(stroke: InkStroke, point: StrokePoint): Float = maxOf(
        stroke.grainDensityMinimum.coerceIn(0f, stroke.grainDensity.coerceIn(0f, 1f)),
        stroke.grainDensity *
            BrushDynamics.pressure(
                stroke.grainDensityDynamics.pressureEnabled,
                stroke.grainDensityDynamics.pressureMinimum,
                stroke.grainDensityDynamics.pressureCurve,
                point.pressure,
            ) *
            BrushDynamics.secondary(stroke.grainDensityDynamics.secondary, point, stroke.renderSeed, DENSITY_SALT)
    ).coerceIn(0f, 1f)

    fun isDynamic(stroke: InkStroke): Boolean = stroke.grainDensityDynamics.let {
        it.pressureEnabled || it.secondary.tiltEnabled || it.secondary.velocityEnabled || it.secondary.randomEnabled
    }

    fun dualDensity(stroke: InkStroke, point: StrokePoint): Float = maxOf(
        stroke.dualGrainDensityMinimum.coerceIn(0f, stroke.dualGrainDensity.coerceIn(0f, 1f)),
        stroke.dualGrainDensity *
            BrushDynamics.pressure(
                stroke.dualGrainDensityDynamics.pressureEnabled,
                stroke.dualGrainDensityDynamics.pressureMinimum,
                stroke.dualGrainDensityDynamics.pressureCurve,
                point.pressure,
            ) *
            BrushDynamics.secondary(stroke.dualGrainDensityDynamics.secondary, point, stroke.renderSeed, DUAL_DENSITY_SALT)
    ).coerceIn(0f, 1f)

    fun isDualDynamic(stroke: InkStroke): Boolean = stroke.dualGrainDensityDynamics.let {
        it.pressureEnabled || it.secondary.tiltEnabled || it.secondary.velocityEnabled || it.secondary.randomEnabled
    }

    private const val DENSITY_SALT = 163
    private const val DUAL_DENSITY_SALT = 167
}
