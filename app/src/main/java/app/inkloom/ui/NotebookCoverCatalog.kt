package app.inkloom.ui

import app.inkloom.document.CoverStyle
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

internal object NotebookCoverCatalog {
    val creationStyles = listOf(
        CoverStyle.MINIMAL,
        CoverStyle.DARK,
        CoverStyle.PAPER,
        CoverStyle.GRID,
        CoverStyle.SOLID,
    )

    fun displayName(style: CoverStyle, language: UiLanguage): String {
        val (english, chinese) = when (style) {
            CoverStyle.MINIMAL -> "Minimal" to "极简"
            CoverStyle.DARK -> "Dark" to "深色"
            CoverStyle.PAPER -> "Paper" to "纸张"
            CoverStyle.GRID -> "Grid" to "网格"
            CoverStyle.SOLID -> "Solid" to "纯色"
            CoverStyle.BLACK -> "Classic black" to "经典黑"
            CoverStyle.WHITE -> "Classic white" to "经典白"
            CoverStyle.BLACK_BAND -> "Black band" to "黑色腰封"
            CoverStyle.WHITE_GRID -> "White grid" to "白色网格"
            CoverStyle.CUSTOM_IMAGE -> "Custom image" to "自定义图片"
        }
        return language.text(english, chinese)
    }

    fun defaultArgb(style: CoverStyle): Long = when (style) {
        CoverStyle.MINIMAL -> 0xFFF5F1E8
        CoverStyle.DARK, CoverStyle.BLACK, CoverStyle.BLACK_BAND -> 0xFF202020
        CoverStyle.PAPER -> 0xFFECE4D5
        CoverStyle.GRID, CoverStyle.WHITE_GRID -> 0xFFF1F1EE
        CoverStyle.SOLID -> 0xFF60646C
        CoverStyle.WHITE, CoverStyle.CUSTOM_IMAGE -> 0xFFF4F4F4
    }
}
