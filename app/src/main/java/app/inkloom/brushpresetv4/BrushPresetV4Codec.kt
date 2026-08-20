package app.inkloom.brushpresetv4

import kotlinx.serialization.json.Json

/** Durable codec used by the production brush library compatibility record. */
object BrushPresetV4Codec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun encode(preset: BrushPresetV4): String =
        json.encodeToString(BrushPresetV4.serializer(), preset)

    fun decodeOrNull(value: String?): BrushPresetV4? = value?.takeIf(String::isNotBlank)?.let { raw ->
        runCatching { json.decodeFromString(BrushPresetV4.serializer(), raw) }.getOrNull()
    }
}
