package app.inkloom.settings

import android.content.Context

object ColorPaletteCodec {
    fun encode(colors: List<Int>): String = colors.distinct().take(32).joinToString(",") { it.toUInt().toString(16).padStart(8, '0') }
    fun decode(value: String?): List<Int> = value.orEmpty().split(',').mapNotNull { token -> token.trim().takeIf { it.length == 8 }?.toUIntOrNull(16)?.toInt() }.distinct().take(32)
}

class ColorPaletteStore(context: Context) {
    private val preferences = context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE)

    fun load(): List<Int> = ColorPaletteCodec.decode(preferences.getString(KEY, null))

    fun add(color: Int): List<Int> = save((load().filterNot { it == color } + color).takeLast(32))

    fun remove(color: Int): List<Int> = save(load().filterNot { it == color })

    private fun save(colors: List<Int>): List<Int> = colors.also { preferences.edit().putString(KEY, ColorPaletteCodec.encode(it)).apply() }

    private companion object { const val KEY = "colors" }
}
