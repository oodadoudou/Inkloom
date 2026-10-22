package app.inkloom.settings

import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientDefinition
import app.inkloom.document.GradientKind

/**
 * Clip Studio Paint's standard gradient set. The first two follow the current colours, as CSP's
 * "drawing colour to background colour" and "drawing colour to transparent" sub tools do, so they
 * are built at the moment they are chosen; the rest are fixed colour sets. Built-ins are never
 * stored in the user library and cannot be edited there; saving one copies it.
 */
object BuiltInGradients {
    const val ID_PREFIX = "builtin:"

    fun all(mainArgb: Int, secondaryArgb: Int): List<NamedGradientPreset> = listOf(
        preset("main-to-secondary", "Drawing colour to background colour", argb(mainArgb), argb(secondaryArgb)),
        preset("main-to-transparent", "Drawing colour to transparent", argb(mainArgb), argb(mainArgb) and 0x00FFFFFFL),
        preset("sky", "Sky", 0xFF2E6FD8, 0xFF7FB8F0, 0xFFE8F4FF),
        preset("sunset", "Sunset", 0xFF3A2470, 0xFFC2457A, 0xFFF39A4C, 0xFFFFE08A),
        preset("night-sky", "Night sky", 0xFF05060F, 0xFF141B46, 0xFF3A2C6E),
        preset("rainbow", "Rainbow", 0xFFFF0000, 0xFFFF8A00, 0xFFFFE600, 0xFF2BD12B, 0xFF00C8FF, 0xFF2A4BFF, 0xFF9B2BFF),
        preset("black-to-white", "Black to white", 0xFF000000, 0xFFFFFFFF),
    )

    fun isBuiltIn(id: String?): Boolean = id?.startsWith(ID_PREFIX) == true

    private fun argb(color: Int): Long = color.toUInt().toLong()

    private fun preset(key: String, name: String, vararg colors: Long): NamedGradientPreset {
        val stops = colors.mapIndexed { index, color -> GradientColorStop(index / (colors.size - 1f), color) }
        return NamedGradientPreset(ID_PREFIX + key, name, GradientDefinition(kind = GradientKind.LINEAR, stops = stops).normalized())
    }
}
