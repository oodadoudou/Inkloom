package app.inkloom.importer

import android.content.Context
import org.json.JSONArray

class BrushGroupStore(
    context: Context,
    preferenceName: String = DEFAULT_PREFERENCES,
    private val key: String = DEFAULT_KEY,
) {
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun load(): List<String> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        buildList { for (index in 0 until array.length()) add(array.optString(index)) }
            .map(BrushGroupOperations::normalize)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
    }.getOrDefault(emptyList())

    fun save(groups: List<String>) {
        val normalized = groups.map(BrushGroupOperations::normalize)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
        preferences.edit().putString(key, JSONArray(normalized).toString()).apply()
    }

    private companion object {
        const val DEFAULT_PREFERENCES = "inkloom_brushes"
        const val DEFAULT_KEY = "brush_groups"
    }
}
