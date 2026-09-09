package app.inkloom.ui

import app.inkloom.brushpresetv5.BrushFamily
import app.inkloom.brushpresetv5.BrushOptionDescriptor
import app.inkloom.brushpresetv5.BrushOptionGroup
import app.inkloom.brushpresetv5.PressureCurveShape
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

internal fun BrushOptionDescriptor.localizedLabel(language: UiLanguage): String {
    if (id.startsWith("dualTexture")) return language.text(label, "第二颗粒 · " + copy(id=id.removePrefix("dual").replaceFirstChar(Char::lowercaseChar)).localizedLabel(language))
    return language.text(label, when (id) {
    "dualSize" -> "第二笔尖大小比例"
    "dualHardness" -> "第二笔尖硬度"
    "dualAspect" -> "第二笔尖宽高比"
    "dualRotation" -> "第二笔尖旋转"
    "dualRandomRotation" -> "第二笔尖旋转抖动"
    "dualFollowDirectionEnabled" -> "跟随笔画方向"
    "dualFollowStylusEnabled" -> "跟随笔倾方向"
    "dualSpacing" -> "第二笔刷间距"
    "dualFlow" -> "第二笔刷流量"
    "dualComposite" -> "双笔刷混合模式"
    "size" -> "大小"
    "opacity" -> "不透明度"
    "flow" -> "流量"
    "hardness" -> "硬度"
    "aspect" -> "宽高比"
    "rotation" -> "旋转"
    "spacing" -> "间距"
    "pressureSizeEnabled" -> "压力控制大小"
    "pressureSizeMinimum" -> "最小大小"
    "pressureOpacityEnabled" -> "压力控制不透明度"
    "pressureOpacityMinimum" -> "最低不透明度"
    "pressureFlowEnabled" -> "压力控制流量"
    "pressureFlowMinimum" -> "最低流量"
    "pressureSizeCurveShape" -> "大小压力曲线"
    "pressureOpacityCurveShape" -> "不透明度压力曲线"
    "pressureFlowCurveShape" -> "流量压力曲线"
    "tiltWidthEnabled" -> "倾斜控制宽度"
    "tiltWidthStart" -> "宽度起始倾斜量"
    "tiltWidthMaximum" -> "最大倾斜宽度"
    "tiltGrainEnabled" -> "倾斜控制颗粒"
    "tiltGrainStart" -> "颗粒起始倾斜量"
    "tiltGrainMaximum" -> "最大倾斜颗粒量"
    "paperGrainScale" -> "纸纹缩放"
    "paperGrainDensity" -> "纸纹密度"
    "paperGrainStrength" -> "纸纹强度"
    "motionFiltering" -> "运动过滤"
    "motionExpression" -> "转折保留"
    "smoothing" -> "此笔刷防抖"
    "taperStartEnabled" -> "起笔渐细"
    "taperEndEnabled" -> "收笔渐细"
    "textureDepth" -> "深度"
    "textureScale" -> "缩放"
    "textureMovement" -> "移动"
    "textureZoom" -> "随笔刷大小缩放"
    "textureRotation" -> "旋转"
    "textureBrightness" -> "亮度"
    "textureContrast" -> "对比度"
    "textureReverseEnabled" -> "反转纹理"
    "textureEmphasizeEnabled" -> "强调纹理"
    "textureOffsetJitterEnabled" -> "偏移抖动"
    "wetDilution" -> "稀释"
    "wetCharge" -> "蓄色"
    "wetAttack" -> "着色"
    "wetPull" -> "拖色"
    else -> error("Missing brush editor translation: $id")
})
}

internal fun BrushOptionGroup.localizedLabel(language: UiLanguage): String = when (this) {
    BrushOptionGroup.BASIC -> language.text("Properties", "属性")
    BrushOptionGroup.TIP -> language.text("Shape", "形状")
    BrushOptionGroup.SPACING -> language.text("Stroke path", "描边路径")
    BrushOptionGroup.PRESSURE -> language.text("Pressure", "压力")
    BrushOptionGroup.TILT -> language.text("Tilt", "倾斜")
    BrushOptionGroup.PAPER -> language.text("Grain", "颗粒")
    BrushOptionGroup.STROKE -> language.text("Stabilization", "防抖修正")
    BrushOptionGroup.DUAL_SHAPE -> language.text("2 · Shape", "第二笔尖")
    BrushOptionGroup.DUAL_STROKE -> language.text("2 · Stroke", "第二描边")
    BrushOptionGroup.DUAL_GRAIN -> language.text("2 · Grain", "第二颗粒")
    BrushOptionGroup.WET_MIX -> language.text("Wet mix", "湿混")
}

internal fun BrushFamily.localizedLabel(language: UiLanguage): String = language.text(label, when (this) {
    BrushFamily.INK -> "墨水"
    BrushFamily.PENCIL -> "铅笔"
    BrushFamily.AIRBRUSH -> "喷枪"
    BrushFamily.DECORATION -> "装饰"
    BrushFamily.WET_PAINT -> "湿画"
})

internal fun PressureCurveShape.localizedLabel(language: UiLanguage): String = language.text(label, when (this) {
    PressureCurveShape.LINEAR -> "线性"
    PressureCurveShape.SOFT -> "柔和"
    PressureCurveShape.HARD -> "硬朗"
    PressureCurveShape.CUSTOM -> "自定义"
})
