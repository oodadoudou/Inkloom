package app.inkloom.model

import app.inkloom.document.LiquifyMode
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.BrushPresetV5Import
import app.inkloom.brushpresetv5.WetMixSettings
import app.inkloom.brushpresetv5.ProceduralTipKind
import app.inkloom.brushpresetv5.SpacingMode
import app.inkloom.brushpresetv5.SpacingSettings
import app.inkloom.brushpresetv5.StampSettings
import app.inkloom.brushpresetv5.TipTextureSettings

/**
 * First-party brush presets. These stay separate from imported/user brushes so selecting,
 * resizing, grouping, and deleting user content can never mutate the factory catalog.
 */
object SystemBrushCatalog {
    val brushes: List<ImportedBrush> = listOf(
        // CSP-style inking tools: a responsive G pen plus general painting and monoline pens.
        systemBrush(
            id = "system-g-pen",
            name = "G Pen",
            group = "Pen",
            size = 6f,
            hardness = 1f,
            spacing = .045f,
            sizePressureMinimum = .06f,
            tags = listOf("pen", "inking", "g-pen"),
        ),
        systemBrush(
            id = "system-painting-pen",
            name = "Painting Pen",
            group = "Pen",
            size = 11f,
            hardness = .96f,
            spacing = .05f,
            flow = .92f,
            sizePressureMinimum = .12f,
            opacityPressureEnabled = true,
            opacityPressureMinimum = .55f,
            tags = listOf("pen", "painting"),
        ),
        systemBrush(id = "system-studio-pen", name = "Studio Pen", group = "Pen", size = 9f, hardness = 1f, spacing = .055f, sizePressureMinimum = .12f, tags = listOf("pen", "inking")),
        systemBrush(id = "system-monoline", name = "Monoline", group = "Pen", size = 18f, hardness = 1f, spacing = .05f, sizePressureEnabled = false, tags = listOf("pen", "monoline")),
        // Line-art pens after the store's most-used inking pens: a clean liner that stays thin under
        // light pressure, and an inky G pen that swells quickly and lifts off softly.
        systemBrush(id = "system-clean-liner", name = "Clean Liner", group = "Pen", size = 5f, hardness = 1f, spacing = .04f, sizePressureMinimum = .03f, tags = listOf("pen", "inking", "lineart")),
        systemBrush(id = "system-ink-g-pen", name = "Ink G Pen", group = "Pen", size = 7f, hardness = .96f, spacing = .04f, sizePressureMinimum = .04f, opacityPressureEnabled = true, opacityPressureMinimum = .82f, tags = listOf("pen", "inking", "g-pen")),

        // Deterministic procedural graphite tips make these visibly different, not just renamed
        // copies of one round line renderer. Graphite tilt-to-width, tilt-to-grain and procedural
        // paper grain are compiled into native V5 programs and covered by V5 golden tests.
        systemBrush(id = "system-technical-pencil", name = "Technical Pencil", group = "Pencil", size = 4.5f, hardness = .9f, spacing = 1f, flow = .82f, sizePressureMinimum = .42f, opacityPressureEnabled = true, opacityPressureMinimum = .35f, graphiteTiltWidth = .12f, graphiteTiltWidthStart = .55f, graphiteTiltGrain = .12f, graphiteTiltGrainStart = .5f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 42f, proceduralPaperGrainDensity = .82f, proceduralPaperGrainStrength = .18f, tags = listOf("pencil", "mechanical", "hard")),
        systemBrush(id = "system-h-pencil", name = "H Pencil", group = "Pencil", size = 5f, hardness = .8f, spacing = .5f, flow = .74f, sizePressureMinimum = .35f, opacityPressureEnabled = true, opacityPressureMinimum = .25f, graphiteTiltWidth = .22f, graphiteTiltWidthStart = .45f, graphiteTiltGrain = .22f, graphiteTiltGrainStart = .42f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 58f, proceduralPaperGrainDensity = .72f, proceduralPaperGrainStrength = .3f, tags = listOf("pencil", "graphite", "hard")),
        systemBrush(id = "system-hb-pencil", name = "HB Pencil", group = "Pencil", size = 7f, hardness = .64f, spacing = .26f, flow = .68f, sizePressureMinimum = .3f, opacityPressureEnabled = true, opacityPressureMinimum = .2f, graphiteTiltWidth = .38f, graphiteTiltWidthStart = .36f, graphiteTiltGrain = .38f, graphiteTiltGrainStart = .34f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 74f, proceduralPaperGrainDensity = .62f, proceduralPaperGrainStrength = .48f, tags = listOf("pencil", "graphite", "hb")),
        systemBrush(id = "system-2b-pencil", name = "2B Pencil", group = "Pencil", size = 9f, hardness = .48f, spacing = .2f, flow = .63f, sizePressureMinimum = .25f, opacityPressureEnabled = true, opacityPressureMinimum = .16f, thickness = .92f, graphiteTiltWidth = .52f, graphiteTiltWidthStart = .3f, graphiteTiltGrain = .52f, graphiteTiltGrainStart = .28f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 88f, proceduralPaperGrainDensity = .54f, proceduralPaperGrainStrength = .58f, tags = listOf("pencil", "graphite", "2b")),
        // A waxy cream pencil for soft sketching and a gritty pencil for line art.
        systemBrush(id = "system-cream-pencil", name = "Cream Pencil", group = "Pencil", size = 10f, hardness = .5f, spacing = .14f, flow = .7f, sizePressureMinimum = .45f, opacityPressureEnabled = true, opacityPressureMinimum = .18f, graphiteTiltWidth = .3f, graphiteTiltWidthStart = .35f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 60f, proceduralPaperGrainDensity = .6f, proceduralPaperGrainStrength = .3f, tags = listOf("pencil", "sketch", "soft")),
        systemBrush(id = "system-lineart-pencil", name = "Lineart Pencil", group = "Pencil", size = 4f, hardness = .86f, spacing = .3f, flow = .85f, sizePressureMinimum = .3f, opacityPressureEnabled = true, opacityPressureMinimum = .3f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 48f, proceduralPaperGrainDensity = .74f, proceduralPaperGrainStrength = .58f, tags = listOf("pencil", "lineart", "gritty")),
        systemBrush(id = "system-6b-pencil", name = "6B Pencil", group = "Pencil", size = 13f, hardness = .3f, spacing = .18f, flow = .56f, sizePressureMinimum = .2f, opacityPressureEnabled = true, opacityPressureMinimum = .1f, thickness = .84f, graphiteTiltWidth = .68f, graphiteTiltWidthStart = .22f, graphiteTiltGrain = .68f, graphiteTiltGrainStart = .2f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 112f, proceduralPaperGrainDensity = .46f, proceduralPaperGrainStrength = .7f, tags = listOf("pencil", "graphite", "6b", "soft")),

        // Wax crayon and soft pastel on the procedural chalk stick, with strong paper tooth.
        systemBrush(id = "system-wax-crayon", name = "Wax Crayon", group = "Crayon", size = 16f, opacity = .9f, hardness = .8f, spacing = .12f, flow = .9f, sizePressureMinimum = .55f, opacityPressureEnabled = true, opacityPressureMinimum = .35f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 55f, proceduralPaperGrainDensity = .55f, proceduralPaperGrainStrength = .75f, tags = listOf("crayon", "wax", "texture")),
        systemBrush(id = "system-soft-pastel", name = "Soft Pastel", group = "Crayon", size = 34f, opacity = .8f, hardness = .35f, spacing = .1f, flow = .6f, sizePressureMinimum = .5f, opacityPressureEnabled = true, opacityPressureMinimum = .2f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 75f, proceduralPaperGrainDensity = .5f, proceduralPaperGrainStrength = .7f, tags = listOf("crayon", "pastel", "texture")),

        // Photoshop's legacy No. 19 is the 19 px round “Airbrush Pen Opacity Flow” preset.
        systemBrush(
            id = "system-ps19",
            name = "Classic PS No. 19",
            group = "Brush",
            size = 19f,
            hardness = 1f,
            spacing = .01f,
            flow = 1f,
            sizePressureMinimum = .02f,
            opacityPressureEnabled = true,
            opacityPressureMinimum = .08f,
            flowPressureEnabled = true,
            flowPressureMinimum = .18f,
            tags = listOf("brush", "photoshop", "ps19", "round"),
        ),
        systemBrush(id = "system-dry-painting-brush", name = "Dry Painting Brush", group = "Brush", size = 30f, opacity = .88f, hardness = .42f, spacing = .075f, flow = .54f, sizePressureMinimum = .2f, opacityPressureEnabled = true, opacityPressureMinimum = .28f, thickness = .36f, tags = listOf("brush", "painting", "dry", "bristle")),
        systemBrush(id = "system-watercolor", name = "Round Watercolor", group = "Brush", size = 42f, opacity = .62f, hardness = .42f, watercolor = true, colorMix = .58f, paintMix = .18f, tags = listOf("brush", "watercolor")),
        systemBrush(id = "system-oil-paint", name = "Thick Oil", group = "Brush", size = 48f, hardness = .78f, colorMix = .35f, paintMix = .7f, thickness = .72f, tags = listOf("brush", "oil")),
        // Gouache: opaque, low mixing; the flat one is a broad square stick turned across the stroke.
        systemBrush(id = "system-gouache", name = "Gouache", group = "Brush", size = 30f, hardness = .8f, paintMix = .9f, colorMix = .1f, sizePressureMinimum = .4f, tags = listOf("brush", "gouache", "opaque")),
        systemBrush(id = "system-flat-gouache", name = "Flat Gouache", group = "Brush", size = 34f, hardness = .85f, spacing = .04f, paintMix = .9f, colorMix = .12f, thickness = .38f, sizePressureMinimum = .5f, tags = listOf("brush", "gouache", "flat")),
        systemBrush(id = "system-marker", name = "Studio Marker", group = "Brush", size = 34f, opacity = .68f, hardness = .7f, sizePressureEnabled = false, tags = listOf("brush", "marker")),

        // Watercolour: a bleeding wet wash, a wash that dries with dark edges, and triangle and square
        // dabs that shift hue a little from dab to dab.
        systemBrush(id = "system-wet-wash", name = "Wet Wash", group = "Watercolor", size = 60f, opacity = .6f, hardness = .2f, watercolor = true, colorMix = .72f, paintMix = .3f, sizePressureMinimum = .5f, tags = listOf("watercolor", "wet", "wash")),
        systemBrush(id = "system-watercolor-edge", name = "Watercolor Edge", group = "Watercolor", size = 48f, opacity = .7f, hardness = .55f, spacing = .08f, flow = .09f, sizePressureMinimum = .55f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 90f, proceduralPaperGrainDensity = .5f, proceduralPaperGrainStrength = .25f, tags = listOf("watercolor", "edge")),
        systemBrush(id = "system-triangle-watercolor", name = "Triangle Watercolor", group = "Watercolor", size = 40f, opacity = .6f, hardness = .7f, spacing = .55f, flow = .6f, sizePressureMinimum = .75f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 90f, proceduralPaperGrainDensity = .5f, proceduralPaperGrainStrength = .2f, tags = listOf("watercolor", "triangle")),
        systemBrush(id = "system-square-watercolor", name = "Square Watercolor", group = "Watercolor", size = 40f, opacity = .6f, hardness = .7f, spacing = .55f, flow = .6f, sizePressureMinimum = .75f, proceduralPaperGrainEnabled = true, proceduralPaperGrainScale = 90f, proceduralPaperGrainDensity = .5f, proceduralPaperGrainStrength = .2f, tags = listOf("watercolor", "square")),

        systemBrush(id = "system-soft-airbrush", name = "Soft Airbrush", group = "Airbrush", size = 92f, opacity = .35f, hardness = .08f, blur = .35f, spacing = .12f, tags = listOf("airbrush", "soft")),
        systemBrush(id = "system-spray-airbrush", name = "Droplet Airbrush", group = "Airbrush", size = 76f, opacity = .42f, spray = true, sprayParticleSize = 7f, sprayDensity = 1.8f, spraySpread = 1.15f, sprayParticleRandomRotationDegrees = 180f, tags = listOf("airbrush", "spray")),
        systemBrush(id = "system-decoration-sparkle", name = "Sparkle Scatter", group = "Decoration", size = 54f, spacing = .34f, spray = true, sprayParticleSize = 10f, sprayDensity = 1.4f, spraySpread = .9f, stampCount = 3, randomRotationDegrees = 180f, tags = listOf("decoration", "effect")),
        systemBrush(id = "system-decoration-hatching", name = "Cross Hatching", group = "Decoration", size = 18f, spacing = .18f, stampCount = 4, stampCountJitter = .3f, strokeRotationJitterDegrees = 90f, tags = listOf("decoration", "hatching")),

        systemBrush(id = "system-hard-eraser", name = "Hard Eraser", group = "Erasers", size = 24f, hardness = 1f, toolBinding = BrushToolBinding(BrushToolRole.ERASE), tags = listOf("eraser", "hard")),
        systemBrush(id = "system-soft-eraser", name = "Soft Eraser", group = "Erasers", size = 68f, hardness = .12f, blur = .28f, toolBinding = BrushToolBinding(BrushToolRole.ERASE), tags = listOf("eraser", "soft")),
        systemBrush(id = "system-blend-smear", toolBinding = BrushToolBinding(BrushToolRole.SMUDGE), name = "Finger Smear", group = "Blend", size = 56f, colorMix = .82f, paintMix = 0f, tags = listOf("blend", "smear")),
        systemBrush(id = "system-blend-copy-stamp", toolBinding = BrushToolBinding(BrushToolRole.CLONE), name = "Copy Stamp", group = "Blend", size = 48f, hardness = .6f, tags = listOf("blend", "clone")),
        systemBrush(id = "system-textured-blender", toolBinding = BrushToolBinding(BrushToolRole.SMUDGE), name = "Textured Blender", group = "Blend", size = 48f, hardness = .55f, colorMix = .6f, tags = listOf("blend", "texture")),
        systemBrush(id = "system-blend-soft", toolBinding = BrushToolBinding(BrushToolRole.SMUDGE), name = "Soft Blend", group = "Blend", size = 82f, hardness = .08f, blur = .48f, colorMix = .62f, tags = listOf("blend", "blur")),
        systemBrush(id = "system-liquify-push", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.PUSH), name = "Push", group = "Liquify", size = 120f, flow = .72f, hardness = .55f, tags = listOf("liquify", "push")),
        systemBrush(id = "system-liquify-push-left", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.PUSH_LEFT), name = "Push Left", group = "Liquify", size = 120f, flow = .72f, hardness = .55f, tags = listOf("liquify", "push-left")),
        systemBrush(id = "system-liquify-push-right", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.PUSH_RIGHT), name = "Push Right", group = "Liquify", size = 120f, flow = .72f, hardness = .55f, tags = listOf("liquify", "push-right")),
        systemBrush(id = "system-liquify-twirl-right", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.TWIRL_CLOCKWISE), name = "Twirl Right", group = "Liquify", size = 140f, flow = .68f, hardness = .45f, tags = listOf("liquify", "twirl-right")),
        systemBrush(id = "system-liquify-twirl-left", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.TWIRL_COUNTERCLOCKWISE), name = "Twirl Left", group = "Liquify", size = 140f, flow = .68f, hardness = .45f, tags = listOf("liquify", "twirl-left")),
        systemBrush(id = "system-liquify-pinch", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.PINCH), name = "Pinch", group = "Liquify", size = 150f, flow = .62f, hardness = .42f, tags = listOf("liquify", "pinch")),
        systemBrush(id = "system-liquify-expand", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.EXPAND), name = "Expand", group = "Liquify", size = 150f, flow = .62f, hardness = .42f, tags = listOf("liquify", "expand")),
        systemBrush(id = "system-liquify-crystals", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.CRYSTALS), name = "Crystals", group = "Liquify", size = 135f, flow = .58f, hardness = .72f, tags = listOf("liquify", "crystals")),
        systemBrush(id = "system-liquify-edge", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.EDGE), name = "Edge", group = "Liquify", size = 130f, flow = .65f, hardness = .58f, tags = listOf("liquify", "edge")),
        systemBrush(id = "system-liquify-reconstruct", toolBinding = BrushToolBinding(BrushToolRole.LIQUIFY, LiquifyMode.RECONSTRUCT), name = "Reconstruct", group = "Liquify", size = 140f, flow = .7f, hardness = .5f, tags = listOf("liquify", "reconstruct")),
    ).map(::createFactoryRecord)

    fun byId(id: String): ImportedBrush? = brushes.firstOrNull { it.id == id }

    /** Factory material semantics are authored explicitly instead of inferred from display tags. */
    private fun createFactoryRecord(source: ImportedBrush): ImportedBrush {
        val record = BrushPresetV5Import.createFactoryRecord(source)
        authored(source.id)?.let { author ->
            return BrushPresetV5Import.writePreset(record, author(BrushPresetV5Import.requirePreset(record)), BrushFormat.SYSTEM)
        }
        if (source.id != "system-dry-painting-brush") return record
        val preset = BrushPresetV5Import.requirePreset(record).copy(
            tip = BrushPresetV5Import.requirePreset(record).tip.copy(rotationDegrees = 90f),
            spacing = SpacingSettings(SpacingMode.RELATIVE, source.spacing),
            tipTexture = TipTextureSettings(
                procedural = true,
                proceduralKind = ProceduralTipKind.BRISTLE,
                toothStrength = .82f,
            ),
            stamp = StampSettings(
                followStrokeDirection = true,
                transferMode = app.inkloom.brushpresetv5.DabTransferMode.UNIFORM_GLAZE,
            ),
        )
        return BrushPresetV5Import.writePreset(record, preset, BrushFormat.SYSTEM)
    }

    private fun curve(vararg points: Pair<Float, Float>) = points.map { (x, y) -> app.inkloom.document.BrushCurvePoint(x, y) }

    private fun proceduralTip(kind: ProceduralTipKind) = TipTextureSettings(procedural = true, proceduralKind = kind)

    private fun jitteredDabs(preset: BrushPresetV5, kind: ProceduralTipKind, rotation: Float) = preset.copy(
        tipTexture = proceduralTip(kind),
        stamp = (preset.stamp ?: StampSettings()).copy(randomRotationDegrees = rotation,
            colorJitter = app.inkloom.brushpresetv5.ColorJitterSettings(hueDegrees = 12f, saturation = .08f, value = .06f)),
    )

    private fun chalkStick(preset: BrushPresetV5, tooth: Float) = preset.copy(
        tipTexture = TipTextureSettings(procedural = true, proceduralKind = ProceduralTipKind.CHALK, toothStrength = tooth),
        stamp = (preset.stamp ?: StampSettings()).copy(randomRotationDegrees = 180f),
    )

    /**
     * Settings the flat factory fields cannot express: pressure curves and tip kinds. A function,
     * not a map, because [brushes] is built before later properties of this object are initialized.
     */
    private fun authored(id: String): ((BrushPresetV5) -> BrushPresetV5)? = mapOf<String, (BrushPresetV5) -> BrushPresetV5>(
        // The raster engine draws no stroke taper, so these pens taper through pressure: almost no
        // width at the lightest touch, which a pen stroke passes through as it lands and lifts.
        "system-clean-liner" to { preset ->
            preset.copy(dynamics = preset.dynamics.copy(pressure = preset.dynamics.pressure.copy(
                widthCurve = curve(0f to 0f, .35f to .16f, .7f to .6f, 1f to 1f))))
        },
        "system-ink-g-pen" to { preset ->
            preset.copy(dynamics = preset.dynamics.copy(pressure = preset.dynamics.pressure.copy(
                widthCurve = curve(0f to 0f, .2f to .32f, .6f to .82f, 1f to 1f))))
        },
        "system-cream-pencil" to { preset ->
            preset.copy(tipTexture = TipTextureSettings(procedural = true, proceduralKind = ProceduralTipKind.GRAPHITE, toothStrength = .45f))
        },
        "system-lineart-pencil" to { preset ->
            preset.copy(tipTexture = TipTextureSettings(procedural = true, proceduralKind = ProceduralTipKind.GRAPHITE, toothStrength = .9f))
        },
        "system-wax-crayon" to { preset -> chalkStick(preset, tooth = .55f) },
        "system-wet-wash" to { preset -> preset.copy(wetMix = WetMixSettings(dilution = .55f, charge = .42f, attack = .55f, pull = .6f)) },
        "system-watercolor-edge" to { preset -> preset.copy(tipTexture = proceduralTip(ProceduralTipKind.WATERCOLOR_RING)) },
        "system-triangle-watercolor" to { preset -> jitteredDabs(preset, ProceduralTipKind.TRIANGLE, rotation = 180f) },
        "system-square-watercolor" to { preset -> jitteredDabs(preset, ProceduralTipKind.SQUARE, rotation = 12f) },
        "system-gouache" to { preset -> preset.copy(wetMix = WetMixSettings(dilution = .08f, charge = .92f, attack = .92f, pull = .16f)) },
        "system-flat-gouache" to { preset ->
            preset.copy(
                wetMix = WetMixSettings(dilution = .08f, charge = .9f, attack = .9f, pull = .2f),
                tipTexture = proceduralTip(ProceduralTipKind.SQUARE),
                // Turned a quarter so the squashed side runs along the stroke: broad across, thin along.
                tip = preset.tip.copy(rotationDegrees = 90f),
                stamp = (preset.stamp ?: StampSettings()).copy(followStrokeDirection = true),
            )
        },
        "system-textured-blender" to { preset -> preset.copy(tipTexture = TipTextureSettings(procedural = true, proceduralKind = ProceduralTipKind.CHALK, toothStrength = .5f)) },
        "system-soft-pastel" to { preset -> chalkStick(preset, tooth = .7f) },
    )[id]

    private fun systemBrush(
        id: String,
        name: String,
        group: String,
        size: Float,
        opacity: Float = 1f,
        hardness: Float = 1f,
        spacing: Float = .12f,
        flow: Float = 1f,
        blur: Float = 0f,
        stabilization: Float = 0f,
        stabilizationBySpeed: Boolean = false,
        sizePressureEnabled: Boolean = true,
        sizePressureMinimum: Float = .35f,
        opacityPressureEnabled: Boolean = false,
        opacityPressureMinimum: Float = 0f,
        flowPressureEnabled: Boolean = false,
        flowPressureMinimum: Float = 0f,
        thickness: Float = 1f,
        watercolor: Boolean = false,
        colorMix: Float = 0f,
        paintMix: Float = 0f,
        spray: Boolean = false,
        sprayParticleSize: Float = 100f,
        sprayDensity: Float = 1f,
        spraySpread: Float = 1f,
        sprayParticleRandomRotationDegrees: Float = 0f,
        stampCount: Int = 1,
        stampCountJitter: Float = 0f,
        randomRotationDegrees: Float = 0f,
        strokeRotationJitterDegrees: Float = 0f,
        graphiteTiltWidth: Float = 0f,
        graphiteTiltWidthStart: Float = 0f,
        graphiteTiltGrain: Float = 0f,
        graphiteTiltGrainStart: Float = 0f,
        proceduralPaperGrainEnabled: Boolean = false,
        proceduralPaperGrainScale: Float = 100f,
        proceduralPaperGrainDensity: Float = .5f,
        proceduralPaperGrainStrength: Float = 1f,
        toolBinding: BrushToolBinding = BrushToolBinding(),
        tags: List<String> = emptyList(),
    ) = ImportedBrush(
        id = id,
        name = name,
        format = BrushFormat.SYSTEM,
        compatibility = Compatibility.COMPLETE,
        sizeBytes = 0L,
        report = emptyList(),
        groupName = group,
        defaultSize = size,
        defaultOpacity = opacity,
        hardness = hardness,
        spacing = spacing,
        flow = flow,
        blur = blur,
        stabilization = stabilization,
        stabilizationBySpeed = stabilizationBySpeed,
        sizePressureEnabled = sizePressureEnabled,
        sizePressureMinimum = sizePressureMinimum,
        opacityPressureEnabled = opacityPressureEnabled,
        opacityPressureMinimum = opacityPressureMinimum,
        flowPressureEnabled = flowPressureEnabled,
        flowPressureMinimum = flowPressureMinimum,
        thickness = thickness,
        watercolor = watercolor,
        colorMix = colorMix,
        paintMix = paintMix,
        spray = spray,
        sprayParticleSize = sprayParticleSize,
        sprayDensity = sprayDensity,
        spraySpread = spraySpread,
        sprayParticleRandomRotationDegrees = sprayParticleRandomRotationDegrees,
        stampCount = stampCount,
        stampCountJitter = stampCountJitter,
        randomRotationDegrees = randomRotationDegrees,
        strokeRotationJitterDegrees = strokeRotationJitterDegrees,
        graphite = ImportedBrushGraphite(
            tiltWidth = graphiteTiltWidth,
            tiltWidthStart = graphiteTiltWidthStart,
            tiltGrain = graphiteTiltGrain,
            tiltGrainStart = graphiteTiltGrainStart,
            paperGrainEnabled = proceduralPaperGrainEnabled,
            paperGrainScale = proceduralPaperGrainScale,
            paperGrainDensity = proceduralPaperGrainDensity,
            paperGrainStrength = proceduralPaperGrainStrength,
        ),
        toolBinding = toolBinding,
        tags = tags,
    )
}
