package app.inkloom.document

/** CSP Color Jitter > Change target. Values follow the order stored by CSP SUT variants. */
object CspColorChangeTarget {
    const val MAIN_AND_SUB = 1
    const val MAIN = 2
    const val SUB = 3

    /** Zero was emitted by Inkloom before the target was decoded; preserve it as the CSP default. */
    fun normalize(value: Int): Int = when (value) {
        MAIN, SUB -> value
        else -> MAIN_AND_SUB
    }

    fun changesMain(value: Int): Boolean = normalize(value) != SUB
    fun changesSub(value: Int): Boolean = normalize(value) != MAIN
}
