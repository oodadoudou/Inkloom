package app.inkloom.settings

import android.content.Context
import app.inkloom.model.EditorTool

/** Last-used controls belong to a tool and preset, separately from authored brush parameters. */
class BrushMasterSettingsStore(context: Context) {
    // Preserve existing installations' per-preset sizes. New keys also distinguish the tool role.
    private val preferences = context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE)

    init {
        // Publish all role-specific replacements in one transaction; authored/new values win.
        val edit=preferences.edit(); var changed=false
        val roles=EditorTool.entries.map { it.name }.toSet()
        preferences.all.forEach { (oldKey, value) ->
            if(oldKey.startsWith("size:") && oldKey.removePrefix("size:").substringBefore(':') !in roles && value is Float && value.isFinite()) {
                val brushId=oldKey.removePrefix("size:")
                EditorTool.entries.forEach { tool -> val newKey=key("size",tool,brushId)
                    if(!preferences.contains(newKey)) edit.putFloat(newKey,value.coerceIn(.5f,400f))
                }
                edit.remove(oldKey);changed=true
            }
        }
        if(changed) check(edit.commit()) { "Could not migrate brush sizes" }
    }

    fun loadSize(tool: EditorTool, brushId: String, fallback: Float): Float {
        val default = normalize(fallback, .5f, 400f, .5f)
        return normalize(read(key("size", tool, brushId), default), .5f, 400f, default)
    }

    fun loadOpacity(tool: EditorTool, brushId: String, fallback: Float): Float =
        normalize(read(key("opacity", tool, brushId), fallback), .05f, 1f,
            normalize(fallback, .05f, 1f, 1f))

    fun saveSize(tool: EditorTool, brushId: String, size: Float): Float =
        save(key("size", tool, brushId), normalize(size, .5f, 400f, .5f))

    fun saveOpacity(tool: EditorTool, brushId: String, opacity: Float): Float =
        save(key("opacity", tool, brushId), normalize(opacity, .05f, 1f, .05f))

    fun save(tool: EditorTool, brushId: String, size: Float, opacity: Float) {
        val normalizedSize = normalize(size, .5f, 400f, .5f)
        val normalizedOpacity = normalize(opacity, .05f, 1f, .05f)
        val sizeKey = key("size", tool, brushId)
        val opacityKey = key("opacity", tool, brushId)
        val editor = preferences.edit()
        var changed = false
        if (read(sizeKey, Float.NaN) != normalizedSize) { editor.putFloat(sizeKey, normalizedSize); changed = true }
        if (read(opacityKey, Float.NaN) != normalizedOpacity) { editor.putFloat(opacityKey, normalizedOpacity); changed = true }
        if (changed) editor.apply()
    }

    private fun save(key: String, value: Float): Float {
        if (read(key, Float.NaN) != value) preferences.edit().putFloat(key, value).apply()
        return value
    }

    private fun read(key: String, fallback: Float): Float =
        try { preferences.getFloat(key, fallback) } catch (_: ClassCastException) { fallback }

    private fun id(brushId: String): String = brushId.trim().ifBlank { "unknown" }
    private fun key(control: String, tool: EditorTool, brushId: String) = "$control:${tool.name}:${id(brushId)}"
    private fun normalize(value: Float, min: Float, max: Float, fallback: Float): Float =
        value.takeIf(Float::isFinite)?.coerceIn(min, max) ?: fallback
}
