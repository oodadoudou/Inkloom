package app.inkloom.settings

import app.inkloom.document.GradientDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class NamedGradientPreset(val id: String, val name: String, val definition: GradientDefinition)

/** User gradients are snapshots; editing the current bar never edits a saved preset. */
@Serializable
data class GradientPresetLibrary(
    val presets: List<NamedGradientPreset> = emptyList(),
    val version: Int = 1,
) {
    fun add(name: String, definition: GradientDefinition, id: String = UUID.randomUUID().toString()): GradientPresetLibrary {
        validateName(name)
        require(presets.size < MAX_PRESETS) { "Gradient preset limit reached" }
        require(presets.none { it.id == id }) { "Gradient preset ID already exists" }
        return copy(presets = presets + NamedGradientPreset(id, name.trim(), definition.normalized())).validated()
    }

    fun rename(id: String, name: String): GradientPresetLibrary {
        validateName(name)
        require(presets.any { it.id == id }) { "Gradient preset no longer exists" }
        return copy(presets = presets.map { if (it.id == id) it.copy(name = name.trim()) else it }).validated()
    }

    fun duplicate(id: String, name: String): GradientPresetLibrary =
        add(name, requireNotNull(presets.firstOrNull { it.id == id }) { "Gradient preset no longer exists" }.definition)

    fun replace(id: String, definition: GradientDefinition): GradientPresetLibrary {
        require(presets.any { it.id == id }) { "Gradient preset no longer exists" }
        return copy(presets = presets.map { if (it.id == id) it.copy(definition = definition.normalized()) else it }).validated()
    }

    fun remove(id: String): GradientPresetLibrary {
        require(presets.any { it.id == id }) { "Gradient preset no longer exists" }
        return copy(presets = presets.filterNot { it.id == id })
    }

    /** Import is append-only. New identities prevent collisions with existing user presets. */
    fun append(imported: GradientPresetLibrary): GradientPresetLibrary {
        imported.validated()
        require(presets.size + imported.presets.size <= MAX_PRESETS) { "Gradient preset limit reached" }
        return copy(presets = presets + imported.presets.map { it.copy(id = UUID.randomUUID().toString()) }).validated()
    }

    fun validated(): GradientPresetLibrary = apply {
        require(version == 1) { "Unsupported gradient preset version" }
        require(presets.size <= MAX_PRESETS) { "Gradient preset limit reached" }
        require(presets.map { it.id }.distinct().size == presets.size) { "Duplicate gradient preset IDs" }
        presets.forEach { preset ->
            require(preset.id.isNotBlank() && preset.id.length <= 80) { "Invalid gradient preset ID" }
            validateName(preset.name)
            require(preset.definition.stops.size in 2..8) { "Gradient needs 2 to 8 stops" }
            require(preset.definition.stops.all { it.position.isFinite() && it.position in 0f..1f && it.colorArgb in 0..0xFFFFFFFFL }) { "Invalid gradient stop" }
            require(preset.definition.ellipseRatio.isFinite() && preset.definition.ellipseRatio in .1f..1f) { "Invalid gradient ellipse ratio" }
        }
    }

    companion object {
        const val MAX_PRESETS = 64
        private fun validateName(name: String) {
            require(name.trim().isNotEmpty() && name.length <= 64) { "Gradient name must be 1 to 64 characters" }
        }
    }
}

object GradientPresetLibraryCodec {
    const val MIME_TYPE = "application/vnd.inkloom.gradients+json"
    const val MAX_BYTES = 256 * 1024
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun encode(library: GradientPresetLibrary): ByteArray =
        json.encodeToString(GradientPresetLibrary.serializer(), library.validated()).encodeToByteArray().also {
            require(it.size <= MAX_BYTES) { "Gradient preset file is too large" }
        }

    fun decode(bytes: ByteArray): Result<GradientPresetLibrary> = runCatching {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Gradient preset file has an invalid size" }
        json.decodeFromString(GradientPresetLibrary.serializer(), bytes.toString(Charsets.UTF_8)).validated()
    }
}
