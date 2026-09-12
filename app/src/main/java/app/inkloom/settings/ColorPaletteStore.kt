package app.inkloom.settings

object ColorPaletteCodec {
    fun encode(colors: List<Int>): String = colors.distinct().take(32).joinToString(",") { it.toUInt().toString(16).padStart(8, '0') }
    fun decode(value: String?): List<Int> = value.orEmpty().split(',').mapNotNull { token -> token.trim().takeIf { it.length == 8 }?.toUIntOrNull(16)?.toInt() }.distinct().take(32)
}
