package app.inkloom.document

import android.graphics.Typeface
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Resolves a text object's font reference to an Android typeface, shared by the canvas and every export. */
object TextTypefaces {
    private val cache = ConcurrentHashMap<String, Result<Typeface>>()

    /** The reference's typeface, or null when it names an imported font that is no longer there. */
    fun base(root: File, reference: String?): Typeface? = when {
        reference == null -> Typeface.DEFAULT
        reference.startsWith(TextFonts.SYSTEM) -> Typeface.create(reference.removePrefix(TextFonts.SYSTEM), Typeface.NORMAL)
        reference.startsWith(TextFonts.IMPORTED) -> cache.getOrPut(reference) {
            runCatching { Typeface.createFromFile(requireNotNull(FontLibrary(root).find(reference)) { "Font is missing" }.let { FontLibrary(root).file(it) }) }
        }.getOrNull()
        else -> Typeface.DEFAULT
    }

    /** The typeface with bold and italic applied; a missing font falls back to the default. */
    fun styled(root: File, reference: String?, bold: Boolean, italic: Boolean): Typeface =
        Typeface.create(base(root, reference) ?: Typeface.DEFAULT, when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL })

    fun isMissing(root: File, reference: String?): Boolean = reference != null && base(root, reference) == null

    /** Call after fonts are added or removed. */
    fun forget() = cache.clear()
}
