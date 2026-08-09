package app.inkloom.settings

import android.content.Context

/** Persists the live size of every brush preset, including built-in erasers. */
class BrushPresetSizeStore(context: Context) {
    private val preferences = context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE)

    fun load(brushId: String, fallback: Float): Float =
        preferences.getFloat(key(brushId), fallback).takeIf(Float::isFinite)?.coerceIn(.5f, 400f) ?: fallback

    fun save(brushId: String, size: Float): Float {
        val normalized = size.takeIf(Float::isFinite)?.coerceIn(.5f, 400f) ?: .5f
        preferences.edit().putFloat(key(brushId), normalized).apply()
        return normalized
    }

    private fun key(brushId: String): String = "size:${brushId.trim().ifBlank { "unknown" }}"
}
