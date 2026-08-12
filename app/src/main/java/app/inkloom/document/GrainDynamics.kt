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

    private const val DENSITY_SALT = 163
}
