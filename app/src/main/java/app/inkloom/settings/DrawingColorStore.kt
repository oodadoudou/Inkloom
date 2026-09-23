package app.inkloom.settings

import android.content.Context
import androidx.core.content.edit

/** Drawing colors belong to the notebook workspace, independently of the selected brush. */
data class DrawingColors(
    val foreground: Int = 0xff111111.toInt(),
    val background: Int = 0xffffffff.toInt(),
)

/** Recent committed drawing colors, newest first; previews never enter this list. */
object DrawingColorHistory {
    const val LIMIT = 10

    fun record(history: List<Int>, color: Int): List<Int> =
        (listOf(color) + history.filterNot { it == color }).take(LIMIT)
}

class DrawingColorStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_drawing_colors", Context.MODE_PRIVATE)

    private fun historyKey(documentId: String) = "history:$documentId"

    fun load(documentId: String): DrawingColors {
        val packed = preferences.getLong(documentId, pack(DrawingColors()))
        return DrawingColors((packed ushr 32).toInt(), packed.toInt())
    }

    /** Call at a committed color change, never for pointer previews. One value keeps swaps whole. */
    fun save(documentId: String, colors: DrawingColors) {
        if (load(documentId) != colors) preferences.edit { putLong(documentId, pack(colors)) }
    }

    fun loadHistory(documentId: String): List<Int> =
        ColorPaletteCodec.decode(preferences.getString(historyKey(documentId), null)).take(DrawingColorHistory.LIMIT)

    fun recordColor(documentId: String, color: Int): List<Int> {
        val updated = DrawingColorHistory.record(loadHistory(documentId), color)
        preferences.edit { putString(historyKey(documentId), ColorPaletteCodec.encode(updated)) }
        return updated
    }

    fun clearHistory(documentId: String) {
        preferences.edit { remove(historyKey(documentId)) }
    }

    fun remove(documentId: String) {
        preferences.edit { remove(documentId); remove(historyKey(documentId)) }
    }

    private fun pack(colors: DrawingColors): Long =
        (colors.foreground.toLong() shl 32) or (colors.background.toLong() and 0xffffffffL)
}
