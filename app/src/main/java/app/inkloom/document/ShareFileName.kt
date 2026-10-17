package app.inkloom.document

/** A file name other apps can save: the notebook title with unsafe characters replaced. */
object ShareFileName {
    const val MAX_BASE_LENGTH = 80
    private val unsafe = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun of(title: String, extension: String): String {
        val base = title.replace(unsafe, "_").trim().take(MAX_BASE_LENGTH).trim()
            .takeUnless { it.isEmpty() || it.all { char -> char == '.' } } ?: "Inkloom"
        return "$base.$extension"
    }
}
