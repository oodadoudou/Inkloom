package app.inkloom.brushpresetv5

/**
 * Declarative description of a single editable brush option. The Brush Editor renders panels purely
 * from these descriptors + the preset's family capabilities, so adding future families (Airbrush,
 * Wet Paint, SUT/ABR imports) never requires growing large `when` blocks.
 *
 * A descriptor binds:
 * - an id / label / group (for the UI),
 * - a [valueRange] + [defaultValue] (for a slider),
 * - a [capability] it belongs to (null = part of the common contract: size/opacity/flow),
 * - a [get] / [set] adapter pair that reads/writes the value on a [BrushPresetV5].
 */
data class BrushOptionDescriptor(
    val id: String,
    val label: String,
    val group: BrushOptionGroup,
    val valueRange: ClosedFloatingPointRange<Float>,
    val defaultValue: Float,
    val capability: BrushCapability?,
    val get: (BrushPresetV5) -> Float,
    val set: (BrushPresetV5, Float) -> BrushPresetV5,
) {
    /** Whether this option is meaningful for a family (common options always are). */
    fun isAvailableFor(family: BrushFamily): Boolean =
        capability == null || BrushCapabilities.supports(family, capability)

    /**
     * Applies [set] only when the option is meaningful for the preset's family; otherwise the
     * value is a no-op so an unsupported (hidden) option can never mutate the preset.
     */
    fun apply(preset: BrushPresetV5, value: Float): BrushPresetV5 =
        if (isAvailableFor(preset.identity.family)) set(preset, value) else preset
}

enum class BrushOptionGroup { BASIC, TIP, SPACING, PRESSURE, TILT, PAPER, STROKE }

/**
 * The static option catalog. Editor panels are derived from
 * [BrushOptionDescriptors.forFamily] — common options always appear, capability-gated options only
 * for families that declare them.
 */
object BrushOptionDescriptors {

    private val COMMON = listOf(
        BrushOptionDescriptor(
            id = "size", label = "Size", group = BrushOptionGroup.BASIC,
            valueRange = 1f..200f, defaultValue = 5f, capability = null,
            get = { it.tip.size },
            set = { p, v -> p.copy(tip = p.tip.copy(size = v)) },
        ),
        BrushOptionDescriptor(
            id = "opacity", label = "Opacity", group = BrushOptionGroup.BASIC,
            valueRange = 0f..1f, defaultValue = 1f, capability = null,
            get = { it.deposition.opacity },
            set = { p, v -> p.copy(deposition = p.deposition.copy(opacity = v)) },
        ),
        BrushOptionDescriptor(
            id = "flow", label = "Flow", group = BrushOptionGroup.BASIC,
            valueRange = 0f..1f, defaultValue = 1f, capability = null,
            get = { it.deposition.flow },
            set = { p, v -> p.copy(deposition = p.deposition.copy(flow = v)) },
        ),
    )

    private val TIP = listOf(
        BrushOptionDescriptor(
            id = "hardness", label = "Hardness", group = BrushOptionGroup.TIP,
            valueRange = 0.01f..1f, defaultValue = 1f, capability = BrushCapability.TIP_TEXTURE,
            get = { it.tip.hardness },
            set = { p, v -> p.copy(tip = p.tip.copy(hardness = v)) },
        ),
        BrushOptionDescriptor(
            id = "aspect", label = "Aspect Ratio", group = BrushOptionGroup.TIP,
            valueRange = 0.1f..4f, defaultValue = 1f, capability = BrushCapability.TILT_WIDTH,
            get = { it.tip.aspect },
            set = { p, v -> p.copy(tip = p.tip.copy(aspect = v)) },
        ),
        BrushOptionDescriptor(
            id = "rotation", label = "Rotation", group = BrushOptionGroup.TIP,
            valueRange = -180f..180f, defaultValue = 0f, capability = BrushCapability.TIP_TEXTURE,
            get = { it.tip.rotationDegrees },
            set = { p, v -> p.copy(tip = p.tip.copy(rotationDegrees = v)) },
        ),
    )

    private val SPACING = listOf(
        BrushOptionDescriptor(
            id = "spacing", label = "Spacing", group = BrushOptionGroup.SPACING,
            valueRange = 0.01f..1f, defaultValue = .12f, capability = BrushCapability.STAMP_SPACING,
            get = { it.spacing?.value ?: .12f },
            set = { p, v -> p.copy(spacing = (p.spacing ?: SpacingSettings()).copy(value = v)) },
        ),
    )

    private val PRESSURE = listOf(
        // Pressure -> Size
        BrushOptionDescriptor(
            id = "pressureSizeEnabled", label = "Pressure → Size", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.PRESSURE_WIDTH,
            get = { if (it.dynamics.pressure.widthEnabled) 1f else 0f },
            set = { p, v ->
                val on = v >= .5f
                p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(widthEnabled = on)))
            },
        ),
        BrushOptionDescriptor(
            id = "pressureSizeMinimum", label = "Size Minimum", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..1f, defaultValue = .35f, capability = BrushCapability.PRESSURE_WIDTH,
            get = { it.dynamics.pressure.widthMinimum },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(widthMinimum = v))) },
        ),
        // Pressure -> Opacity (deposition)
        BrushOptionDescriptor(
            id = "pressureOpacityEnabled", label = "Pressure → Opacity", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.PRESSURE_DEPOSITION,
            get = { if (it.dynamics.pressure.depositionEnabled) 1f else 0f },
            set = { p, v ->
                val on = v >= .5f
                p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(depositionEnabled = on)))
            },
        ),
        BrushOptionDescriptor(
            id = "pressureOpacityMinimum", label = "Opacity Minimum", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.PRESSURE_DEPOSITION,
            get = { it.dynamics.pressure.depositionMinimum },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(depositionMinimum = v))) },
        ),
        // Pressure -> Flow
        BrushOptionDescriptor(
            id = "pressureFlowEnabled", label = "Pressure → Flow", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.PRESSURE_DEPOSITION,
            get = { if (it.dynamics.pressure.flowEnabled) 1f else 0f },
            set = { p, v ->
                val on = v >= .5f
                p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(flowEnabled = on)))
            },
        ),
        BrushOptionDescriptor(
            id = "pressureFlowMinimum", label = "Flow Minimum", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.PRESSURE_DEPOSITION,
            get = { it.dynamics.pressure.flowMinimum },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(flowMinimum = v))) },
        ),
        // Curve shape selectors (Linear / Soft / Hard / Custom) for each pressure channel.
        BrushOptionDescriptor(
            id = "pressureSizeCurveShape", label = "Size Curve", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..3f, defaultValue = 0f, capability = BrushCapability.PRESSURE_WIDTH,
            get = { EditorCurves.shapeOf(it.dynamics.pressure.widthCurve).ordinal.toFloat() },
            set = { p, v ->
                val shape = PressureCurveShape.entries[v.toInt().coerceIn(0, 3)]
                p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(widthCurve = EditorCurves.controlPoints(shape))))
            },
        ),
        BrushOptionDescriptor(
            id = "pressureOpacityCurveShape", label = "Opacity Curve", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..3f, defaultValue = 0f, capability = BrushCapability.PRESSURE_DEPOSITION,
            get = { EditorCurves.shapeOf(it.dynamics.pressure.depositionCurve).ordinal.toFloat() },
            set = { p, v ->
                val shape = PressureCurveShape.entries[v.toInt().coerceIn(0, 3)]
                p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(depositionCurve = EditorCurves.controlPoints(shape))))
            },
        ),
        BrushOptionDescriptor(
            id = "pressureFlowCurveShape", label = "Flow Curve", group = BrushOptionGroup.PRESSURE,
            valueRange = 0f..3f, defaultValue = 0f, capability = BrushCapability.PRESSURE_DEPOSITION,
            get = { EditorCurves.shapeOf(it.dynamics.pressure.flowCurve).ordinal.toFloat() },
            set = { p, v ->
                val shape = PressureCurveShape.entries[v.toInt().coerceIn(0, 3)]
                p.copy(dynamics = p.dynamics.copy(pressure = p.dynamics.pressure.copy(flowCurve = EditorCurves.controlPoints(shape))))
            },
        ),
    )

    private val TILT = listOf(
        BrushOptionDescriptor(
            id = "tiltWidthEnabled", label = "Tilt → Width", group = BrushOptionGroup.TILT,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.TILT_WIDTH,
            get = { if (it.dynamics.tilt.widthEnabled) 1f else 0f },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(tilt = p.dynamics.tilt.copy(widthEnabled = v >= .5f))) },
        ),
        BrushOptionDescriptor(
            id = "tiltWidthStart", label = "Width Tilt Start", group = BrushOptionGroup.TILT,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.TILT_WIDTH,
            get = { it.dynamics.tilt.widthStart },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(tilt = p.dynamics.tilt.copy(widthStart = v))) },
        ),
        BrushOptionDescriptor(
            id = "tiltWidthMaximum", label = "Width Tilt Max", group = BrushOptionGroup.TILT,
            valueRange = 0f..3f, defaultValue = 1f, capability = BrushCapability.TILT_WIDTH,
            get = { it.dynamics.tilt.widthMaximum },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(tilt = p.dynamics.tilt.copy(widthMaximum = v))) },
        ),
        BrushOptionDescriptor(
            id = "tiltGrainEnabled", label = "Tilt → Grain", group = BrushOptionGroup.TILT,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.TILT_GRAIN,
            get = { if (it.dynamics.tilt.grainEnabled) 1f else 0f },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(tilt = p.dynamics.tilt.copy(grainEnabled = v >= .5f))) },
        ),
        BrushOptionDescriptor(
            id = "tiltGrainStart", label = "Grain Tilt Start", group = BrushOptionGroup.TILT,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.TILT_GRAIN,
            get = { it.dynamics.tilt.grainStart },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(tilt = p.dynamics.tilt.copy(grainStart = v))) },
        ),
        BrushOptionDescriptor(
            id = "tiltGrainMaximum", label = "Grain Tilt Max", group = BrushOptionGroup.TILT,
            valueRange = 0f..2f, defaultValue = 1f, capability = BrushCapability.TILT_GRAIN,
            get = { it.dynamics.tilt.grainMaximum },
            set = { p, v -> p.copy(dynamics = p.dynamics.copy(tilt = p.dynamics.tilt.copy(grainMaximum = v))) },
        ),
    )

    private val PAPER = listOf(
        BrushOptionDescriptor(
            id = "paperGrainScale", label = "Paper Grain Scale", group = BrushOptionGroup.PAPER,
            valueRange = 1f..400f, defaultValue = 100f, capability = BrushCapability.PAPER_GRAIN,
            get = { it.paperGrain?.scale ?: 100f },
            set = { p, v -> p.copy(paperGrain = (p.paperGrain ?: PaperGrainSettings()).copy(scale = v)) },
        ),
        BrushOptionDescriptor(
            id = "paperGrainDensity", label = "Paper Grain Density", group = BrushOptionGroup.PAPER,
            valueRange = 0f..1f, defaultValue = .5f, capability = BrushCapability.PAPER_GRAIN,
            get = { it.paperGrain?.density ?: 0f },
            set = { p, v -> p.copy(paperGrain = (p.paperGrain ?: PaperGrainSettings()).copy(density = v)) },
        ),
        BrushOptionDescriptor(
            id = "paperGrainStrength", label = "Paper Grain Strength", group = BrushOptionGroup.PAPER,
            valueRange = 0f..1f, defaultValue = 1f, capability = BrushCapability.PAPER_GRAIN,
            get = { it.paperGrain?.strength ?: 1f },
            set = { p, v -> p.copy(paperGrain = (p.paperGrain ?: PaperGrainSettings()).copy(strength = v)) },
        ),
    )

    private val STROKE = listOf(
        BrushOptionDescriptor(
            id = "smoothing", label = "Smoothing", group = BrushOptionGroup.STROKE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.TAPER,
            get = { it.stroke?.smoothing ?: 0f },
            set = { p, v -> p.copy(stroke = (p.stroke ?: StrokeSettings()).copy(smoothing = v)) },
        ),
        BrushOptionDescriptor(
            id = "taperStartEnabled", label = "Taper Start", group = BrushOptionGroup.STROKE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.TAPER,
            get = { if (it.stroke?.taperStartEnabled == true) 1f else 0f },
            set = { p, v -> p.copy(stroke = (p.stroke ?: StrokeSettings()).copy(taperStartEnabled = v >= .5f)) },
        ),
        BrushOptionDescriptor(
            id = "taperEndEnabled", label = "Taper End", group = BrushOptionGroup.STROKE,
            valueRange = 0f..1f, defaultValue = 0f, capability = BrushCapability.TAPER,
            get = { if (it.stroke?.taperEndEnabled == true) 1f else 0f },
            set = { p, v -> p.copy(stroke = (p.stroke ?: StrokeSettings()).copy(taperEndEnabled = v >= .5f)) },
        ),
    )

    private val ALL: Map<String, BrushOptionDescriptor> =
        (COMMON + TIP + SPACING + PRESSURE + TILT + PAPER + STROKE).associateBy { it.id }

    /** All options meaningful for a family, grouped in a stable panel order. */
    fun forFamily(family: BrushFamily): List<BrushOptionDescriptor> =
        listOf(
            COMMON, TIP, SPACING, PRESSURE, TILT, PAPER, STROKE,
        ).flatten().filter { it.isAvailableFor(family) }

    /** The panels (groups with their options) for a family, capability-driven. */
    fun panels(family: BrushFamily): List<Pair<BrushOptionGroup, List<BrushOptionDescriptor>>> {
        val available = forFamily(family).filter { it.group != BrushOptionGroup.PRESSURE }
        val pressureOptions = PRESSURE.filter { it.isAvailableFor(family) }
        val orderedGroups = listOf(
            BrushOptionGroup.BASIC, BrushOptionGroup.TIP, BrushOptionGroup.SPACING,
            BrushOptionGroup.PRESSURE, BrushOptionGroup.TILT, BrushOptionGroup.PAPER, BrushOptionGroup.STROKE,
        )
        return orderedGroups.mapNotNull { group ->
            val options = if (group == BrushOptionGroup.PRESSURE) pressureOptions else available.filter { it.group == group }
            if (options.isEmpty()) null else group to options
        }
    }

    fun byId(id: String): BrushOptionDescriptor? = ALL[id]

    fun option(id: String): BrushOptionDescriptor = requireNotNull(byId(id)) { "Unknown option: $id" }
}
