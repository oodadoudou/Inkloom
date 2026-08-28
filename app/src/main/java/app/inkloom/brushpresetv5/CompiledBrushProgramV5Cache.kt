package app.inkloom.brushpresetv5

/**
 * Single-selection cache at the UI/runtime boundary. Compose may re-apply an AndroidView update
 * for unrelated state, but decoding and compiling a brush is selection work, never frame or
 * contact work. The cache deliberately owns only one immutable program so changing a preset
 * cannot leave a stale executable graph reachable.
 */
class CompiledBrushProgramV5Cache {
    private var encodedSource: String? = null
    private var presetSource: BrushPresetV5? = null
    private var compiled: CompiledBrushProgramV5? = null

    fun resolveEncoded(encoded: String?): CompiledBrushProgramV5? {
        if (encoded == null) {
            clear()
            return null
        }
        if (encodedSource == encoded) return compiled
        val preset = BrushPresetV5Codec.decodeOrNull(encoded)
        encodedSource = encoded
        presetSource = preset
        compiled = preset?.let(CompiledBrushProgramV5::compile)
        return compiled
    }

    fun resolvePreset(preset: BrushPresetV5): CompiledBrushProgramV5 {
        if (encodedSource == null && presetSource == preset) return requireNotNull(compiled)
        encodedSource = null
        presetSource = preset
        return CompiledBrushProgramV5.compile(preset).also { compiled = it }
    }

    fun clear() {
        encodedSource = null
        presetSource = null
        compiled = null
    }
}
