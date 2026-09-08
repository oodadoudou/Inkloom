package app.inkloom.settings

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One local record shared by factory and imported brushes, independent of author settings. */
class BrushUsageStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_brush_usage", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): BrushUsageState = preferences.getString("state", null)?.let { encoded ->
        json.decodeFromString<BrushUsageState>(encoded)
    } ?: BrushUsageState()

    fun save(state: BrushUsageState) {
        preferences.edit().putString("state", json.encodeToString(state)).apply()
    }
}
