package app.inkloom.brushpresetv5

/** Only offers material controls for material blocks that the source preset actually owns. */
internal object BrushMaterialOptions {
    private fun grain(id: String, label: String, range: ClosedFloatingPointRange<Float>,
        get: (TextureGrainSettings) -> Float, set: (TextureGrainSettings, Float) -> TextureGrainSettings) =
        BrushOptionDescriptor(id, label, BrushOptionGroup.PAPER, range, range.start, null,
            get = { get(requireNotNull(it.textureGrain)) },
            set = { preset, value -> preset.copy(textureGrain = set(requireNotNull(preset.textureGrain), value)) })

    private fun wet(id: String, label: String, get: (WetMixSettings) -> Float,
        set: (WetMixSettings, Float) -> WetMixSettings) =
        BrushOptionDescriptor(id, label, BrushOptionGroup.WET_MIX, 0f..1f, 0f, null,
            get = { get(requireNotNull(it.wetMix)) },
            set = { preset, value -> preset.copy(wetMix = set(requireNotNull(preset.wetMix), value)) })

    val grain = listOf(
        grain("textureDepth", "Depth", 0f..1f, { it.density }, { p, v -> p.copy(density = v) }),
        grain("textureScale", "Scale", 1f..4000f, { it.scale }, { p, v -> p.copy(scale = v) }),
        grain("textureMovement", "Movement", 0f..1f, { it.movement }, { p, v -> p.copy(movement = v) }),
        grain("textureZoom", "Zoom", 0f..1f, { it.zoom }, { p, v -> p.copy(zoom = v) }),
        grain("textureRotation", "Rotation", -180f..180f, { it.rotationDegrees }, { p, v -> p.copy(rotationDegrees = v) }),
        grain("textureBrightness", "Brightness", -100f..100f, { it.brightness }, { p, v -> p.copy(brightness = v) }),
        grain("textureContrast", "Contrast", -100f..100f, { it.contrast }, { p, v -> p.copy(contrast = v) }),
        grain("textureReverseEnabled", "Invert texture", 0f..1f, { if (it.reversed) 1f else 0f }, { p, v -> p.copy(reversed = v >= .5f) }),
        grain("textureEmphasizeEnabled", "Emphasize texture", 0f..1f, { if (it.emphasized) 1f else 0f }, { p, v -> p.copy(emphasized = v >= .5f) }),
        grain("textureOffsetJitterEnabled", "Offset jitter", 0f..1f, { if (it.offsetJitter) 1f else 0f }, { p, v -> p.copy(offsetJitter = v >= .5f) }),
    )
    val wet = listOf(
        wet("wetDilution", "Dilution", { it.dilution }, { p, v -> p.copy(dilution = v) }),
        wet("wetCharge", "Charge", { it.charge }, { p, v -> p.copy(charge = v) }),
        wet("wetAttack", "Attack", { it.attack }, { p, v -> p.copy(attack = v) }),
        wet("wetPull", "Pull", { it.pull }, { p, v -> p.copy(pull = v) }),
    )
    val all = grain + wet

    fun forPreset(preset: BrushPresetV5): List<BrushOptionDescriptor> {
        val backend = CompiledBrushProgramV5.compile(preset).backend
        val hasGrainConsumer = backend in setOf(RenderBackend.ASSET_STAMP, RenderBackend.PROCEDURAL_STAMP, RenderBackend.WET_PAINT)
        return (if (preset.textureGrain != null && hasGrainConsumer) grain else emptyList()) +
            (if (preset.wetMix != null && backend == RenderBackend.WET_PAINT && preset.dual == null) wet else emptyList())
    }
}
