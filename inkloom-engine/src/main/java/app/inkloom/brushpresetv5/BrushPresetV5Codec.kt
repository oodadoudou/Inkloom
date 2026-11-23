package app.inkloom.brushpresetv5

import kotlinx.serialization.json.Json

/** Durable codec for Brush Preset Format V5. Legacy schemas are rejected, never migrated. */
object BrushPresetV5Codec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun encode(preset: BrushPresetV5): String =
        json.encodeToString(BrushPresetV5.serializer(), preset)

    fun decodeOrNull(value: String?): BrushPresetV5? = value?.takeIf(String::isNotBlank)?.let { raw ->
        runCatching { json.decodeFromString(BrushPresetV5.serializer(), raw) }
            .getOrNull()
            ?.takeIf { it.schemaVersion == 5 }
    }
}
