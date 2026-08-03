package app.inkloom.document

object WetMixDynamics {
    /**
     * Deterministic glaze approximation shared by the live canvas and exports.
     * CSP's density-of-paint value raises opacity while amount-of-paint raises the
     * ratio of the drawing color to existing pigment. Normal alpha compositing is
     * used as a deterministic glaze approximation; fluid transport is not claimed.
     */
    fun alphaMultiplier(watercolor: Boolean, colorMix: Float, paintMix: Float): Float {
        if (!watercolor) return 1f
        val pigmentDensity = .35f + .55f * paintMix.coerceIn(0f, 1f)
        val drawingColorRatio = .65f + .35f * colorMix.coerceIn(0f, 1f)
        return (pigmentDensity * drawingColorRatio).coerceIn(.2f, .9f)
    }

    fun alphaMultiplier(stroke: InkStroke, point: StrokePoint): Float = alphaMultiplier(
        watercolor = stroke.watercolor,
        colorMix = colorMix(stroke, point),
        paintMix = paintMix(stroke, point),
    )

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
}
