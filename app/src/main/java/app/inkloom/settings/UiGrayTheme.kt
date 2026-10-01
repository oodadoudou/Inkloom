package app.inkloom.settings

/** Neutral UI tones only. Document paper, pixels and exported colors never use these values. */
enum class UiGrayTheme(val workspace: Int, val surface: Int, val raised: Int,
    val selected: Int, val text: Int, val muted: Int, val divider: Int) {
    DARK(0x36, 0x41, 0x49, 0x55, 0xEC, 0xC4, 0x62),
    MEDIUM(0x55, 0x60, 0x68, 0x70, 0xFA, 0xE4, 0x8A),
    SOFT(0x66, 0x70, 0x76, 0x76, 0xFF, 0xF4, 0x98),
    LIGHT(0xC6, 0xD4, 0xDE, 0xAE, 0x25, 0x44, 0x98);

    fun argb(tone: Int): Int = (0xFF shl 24) or (tone shl 16) or (tone shl 8) or tone
    companion object {
        val DEFAULT = MEDIUM
        fun decode(name: String?): UiGrayTheme = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
