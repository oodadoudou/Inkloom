package app.inkloom.document

/** Procreate's independent Shape and Grain filtering choices. */
object ProcreateFiltering {
    const val NONE = 0
    const val CLASSIC = 1
    const val IMPROVED = 2

    fun normalize(mode: Int): Int = mode.coerceIn(NONE, IMPROVED)

    /** Archived brushes store the on/off switch separately from the classic/improved selector. */
    fun fromArchive(enabled: Boolean, archivedMode: Int): Int = when {
        !enabled -> NONE
        archivedMode <= 0 -> CLASSIC
        else -> IMPROVED
    }

    fun shapeAntiAlias(mode: Int): Int = when (normalize(mode)) {
        NONE -> CspAntiAlias.NONE
        CLASSIC -> CspAntiAlias.MEDIUM
        else -> CspAntiAlias.STRONG
    }
}
