package app.inkloom.settings

import android.content.Context
import androidx.core.content.edit

/** Drawing colors belong to the notebook workspace, independently of the selected brush. */
data class DrawingColors(
    val foreground: Int = 0xff111111.toInt(),
    val background: Int = 0xffffffff.toInt(),
)

class DrawingColorStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_drawing_colors", Context.MODE_PRIVATE)

    fun load(documentId: String): DrawingColors {
        val packed = preferences.getLong(documentId, pack(DrawingColors()))
        return DrawingColors((packed ushr 32).toInt(), packed.toInt())
    }

    /** Call at a committed color change, never for pointer previews. One value keeps swaps whole. */
    fun save(documentId: String, colors: DrawingColors) {
        if (load(documentId) != colors) preferences.edit { putLong(documentId, pack(colors)) }
    }

    fun remove(documentId: String) {
        preferences.edit { remove(documentId) }
    }

    private fun pack(colors: DrawingColors): Long =
        (colors.foreground.toLong() shl 32) or (colors.background.toLong() and 0xffffffffL)
}
