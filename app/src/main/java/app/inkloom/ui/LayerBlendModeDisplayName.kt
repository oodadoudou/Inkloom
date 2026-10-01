package app.inkloom.ui

import app.inkloom.document.LayerBlendMode
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/** Localized presentation only; the stable enum remains the serialization key. */
object LayerBlendModeDisplayName {
    fun of(layer: app.inkloom.document.InkLayer, language: UiLanguage): String =
        if (layer.role == app.inkloom.document.LayerRole.FOLDER && layer.passThrough) language.text("Through", "穿透") else of(layer.blendMode, language)

    fun of(mode: LayerBlendMode, language: UiLanguage): String {
        val (english, simplifiedChinese) = when (mode) {
            LayerBlendMode.NORMAL -> "Normal" to "正常"
            LayerBlendMode.MULTIPLY -> "Multiply" to "正片叠底"
            LayerBlendMode.SCREEN -> "Screen" to "滤色"
            LayerBlendMode.ADD -> "Add" to "添加"
            LayerBlendMode.OVERLAY -> "Overlay" to "叠加"
            LayerBlendMode.SOFT_LIGHT -> "Soft Light" to "柔光"
            LayerBlendMode.HARD_LIGHT -> "Hard Light" to "强光"
            LayerBlendMode.COLOR_DODGE -> "Color Dodge" to "颜色减淡"
            LayerBlendMode.COLOR_BURN -> "Color Burn" to "颜色加深"
            LayerBlendMode.LIGHTEN -> "Lighten" to "变亮"
            LayerBlendMode.DARKEN -> "Darken" to "变暗"
            LayerBlendMode.DIFFERENCE -> "Difference" to "差值"
            LayerBlendMode.EXCLUSION -> "Exclusion" to "排除"
            LayerBlendMode.HUE -> "Hue" to "色相"
            LayerBlendMode.SATURATION -> "Saturation" to "饱和度"
            LayerBlendMode.COLOR -> "Color" to "颜色"
            LayerBlendMode.LUMINOSITY -> "Luminosity" to "明度"
            LayerBlendMode.LINEAR_BURN -> "Linear Burn" to "线性加深"
            LayerBlendMode.SUBTRACT -> "Subtract" to "减去"
            LayerBlendMode.GLOW_DODGE -> "Glow Dodge" to "发光减淡"
            LayerBlendMode.ADD_GLOW -> "Add (Glow)" to "添加（发光）"
            LayerBlendMode.VIVID_LIGHT -> "Vivid Light" to "亮光"
            LayerBlendMode.LINEAR_LIGHT -> "Linear Light" to "线性光"
            LayerBlendMode.PIN_LIGHT -> "Pin Light" to "点光"
            LayerBlendMode.HARD_MIX -> "Hard Mix" to "实色混合"
            LayerBlendMode.DARKER_COLOR -> "Darker Color" to "深色"
            LayerBlendMode.LIGHTER_COLOR -> "Lighter Color" to "浅色"
            LayerBlendMode.DIVIDE -> "Divide" to "划分"
        }
        return language.text(english, simplifiedChinese)
    }
}
