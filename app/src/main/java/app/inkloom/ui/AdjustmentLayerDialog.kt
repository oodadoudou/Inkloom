package app.inkloom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inkloom.document.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import java.util.Locale
import kotlin.math.roundToInt

internal fun adjustmentName(kind: LayerAdjustmentKind, language: UiLanguage): String = when (kind) {
    LayerAdjustmentKind.BRIGHTNESS_CONTRAST -> language.text("Brightness / Contrast", "亮度／对比度")
    LayerAdjustmentKind.HUE_SATURATION -> language.text("Hue / Saturation / Luminosity", "色相／饱和度／明度")
    LayerAdjustmentKind.LEVELS -> language.text("Levels", "色阶")
    LayerAdjustmentKind.CURVES -> language.text("Curves", "曲线")
    LayerAdjustmentKind.COLOR_BALANCE -> language.text("Color Balance", "色彩平衡")
    LayerAdjustmentKind.GRADIENT_MAP -> language.text("Gradient Map", "渐变映射")
    LayerAdjustmentKind.POSTERIZE -> language.text("Posterize", "色调分离")
    LayerAdjustmentKind.INVERT -> language.text("Invert", "反转")
    LayerAdjustmentKind.THRESHOLD -> language.text("Threshold", "二值化")
}

@Composable
internal fun AdjustmentLayerDialog(layer: InkLayer, onDismiss: () -> Unit, onPreview: (LayerAdjustment) -> Unit, onApply: (LayerAdjustment) -> Unit) {
    val language = LocalUiLanguage.current
    var channel by remember(layer.id) { mutableIntStateOf(0) }
    val original = remember(layer.id) { layer.adjustment ?: LayerAdjustment() }
    var value by remember(layer.id) { mutableStateOf(original) }
    // CSP/Photoshop-style before/after comparison; Apply always commits the edited values.
    var previewing by remember(layer.id) { mutableStateOf(true) }
    fun update(next: LayerAdjustment) { value = next; if (previewing) onPreview(next) }
    @Composable fun parameter(label: String, current: Float, range: ClosedFloatingPointRange<Float>, update: (Float) -> Unit) {
        Text("$label  ${String.format(Locale.ROOT, "%.2f", current)}", style = MaterialTheme.typography.labelMedium)
        BrushParameterSlider(current.coerceIn(range.start, range.endInclusive), update, valueRange = range,
            modifier = Modifier.fillMaxWidth().height(34.dp).semantics { contentDescription = "$label adjustment" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(adjustmentName(value.kind, language)) },
        text = {
            Column(Modifier.widthIn(min = 270.dp, max = 430.dp).heightIn(max = 490.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).toggleable(previewing, role = Role.Checkbox) { checked ->
                        previewing = checked; onPreview(if (checked) value else original)
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(previewing, onCheckedChange = null)
                        Text(language.text("Preview", "预览"))
                    }
                    TextButton({ update(LayerAdjustment(kind = value.kind)) }) { Text(language.text("Reset", "重置")) }
                }
                when (value.kind) {
                    LayerAdjustmentKind.BRIGHTNESS_CONTRAST -> {
                        parameter(language.text("Brightness", "亮度"), value.brightness, -1f..1f) { update(value.copy(brightness = it)) }
                        parameter(language.text("Contrast", "对比度"), value.contrast, -.95f..0.95f) { update(value.copy(contrast = it)) }
                    }
                    LayerAdjustmentKind.HUE_SATURATION -> {
                        parameter(language.text("Hue", "色相"), value.hueDegrees, -180f..180f) { update(value.copy(hueDegrees = it)) }
                        parameter(language.text("Saturation", "饱和度"), value.saturation, -1f..1f) { update(value.copy(saturation = it)) }
                        parameter(language.text("Luminosity", "明度"), value.luminosity, -1f..1f) { update(value.copy(luminosity = it)) }
                    }
                    LayerAdjustmentKind.POSTERIZE -> {
                        parameter(language.text("Levels per channel", "每通道级数"), value.posterizeLevels.toFloat(), 2f..20f) {
                            update(value.copy(posterizeLevels = it.roundToInt().coerceIn(2, 20)))
                        }
                    }
                    LayerAdjustmentKind.INVERT -> Text(language.text("Invert RGB, preserving transparency.", "反转 RGB，保留透明度。"))
                    LayerAdjustmentKind.THRESHOLD -> {
                        parameter(language.text("Threshold", "阈值"), value.threshold, 0f..1f) { update(value.copy(threshold = it)) }
                    }
                    LayerAdjustmentKind.LEVELS -> {
                        val levels = when (channel) {
                            1 -> value.redLevels; 2 -> value.greenLevels; 3 -> value.blueLevels
                            else -> AdjustmentLevels(value.inputBlack, value.inputGamma, value.inputWhite, value.outputBlack, value.outputWhite)
                        }
                        fun change(next: AdjustmentLevels) {
                            update(when (channel) {
                                1 -> value.copy(redLevels = next); 2 -> value.copy(greenLevels = next); 3 -> value.copy(blueLevels = next)
                                else -> value.copy(inputBlack = next.inputBlack, inputGamma = next.inputGamma,
                                    inputWhite = next.inputWhite, outputBlack = next.outputBlack, outputWhite = next.outputWhite)
                            })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("RGB", language.text("Red", "红"), language.text("Green", "绿"), language.text("Blue", "蓝")).forEachIndexed { index, label ->
                                FilterChip(selected = channel == index, onClick = { channel = index },
                                    label = { Text(label) }, modifier = Modifier.weight(1f))
                            }
                        }
                        parameter(language.text("Input black", "输入黑场"), levels.inputBlack, 0f..(levels.inputWhite - .01f).coerceAtLeast(0f)) { change(levels.copy(inputBlack = it)) }
                        parameter(language.text("Gamma", "伽马"), levels.inputGamma, .2f..4f) { change(levels.copy(inputGamma = it)) }
                        parameter(language.text("Input white", "输入白场"), levels.inputWhite, (levels.inputBlack + .01f).coerceAtMost(1f)..1f) { change(levels.copy(inputWhite = it)) }
                        parameter(language.text("Output black", "输出黑场"), levels.outputBlack, 0f..levels.outputWhite) { change(levels.copy(outputBlack = it)) }
                        parameter(language.text("Output white", "输出白场"), levels.outputWhite, levels.outputBlack..1f) { change(levels.copy(outputWhite = it)) }
                        TextButton({ change(AdjustmentLevels()) }) { Text(language.text("Reset channel", "重置此通道")) }
                    }
                    LayerAdjustmentKind.CURVES -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("RGB", language.text("Red", "红"), language.text("Green", "绿"), language.text("Blue", "蓝")).forEachIndexed { index, label ->
                                FilterChip(selected = channel == index, onClick = { channel = index },
                                    label = { Text(label) }, modifier = Modifier.weight(1f))
                            }
                        }
                        val points = when (channel) { 1 -> value.redCurve; 2 -> value.greenCurve; 3 -> value.blueCurve; else -> value.curve }
                        val smooth = when (channel) { 1 -> value.redCurveSmooth; 2 -> value.greenCurveSmooth; 3 -> value.blueCurveSmooth; else -> value.curveSmooth }
                        key(channel) {
                            AdjustmentCurveEditor(points, smooth, onChange = { next ->
                                update(when (channel) {
                                    1 -> value.copy(redCurve = next); 2 -> value.copy(greenCurve = next); 3 -> value.copy(blueCurve = next)
                                    else -> value.copy(curve = next)
                                })
                            }, onSmooth = { next ->
                                update(when (channel) {
                                    1 -> value.copy(redCurveSmooth = next); 2 -> value.copy(greenCurveSmooth = next); 3 -> value.copy(blueCurveSmooth = next)
                                    else -> value.copy(curveSmooth = next)
                                })
                            })
                        }
                    }
                    LayerAdjustmentKind.COLOR_BALANCE -> {
                        parameter(language.text("Shadows · Red", "暗部 · 红"), value.shadowsRed, -.5f..0.5f) { update(value.copy(shadowsRed = it)) }
                        parameter(language.text("Shadows · Green", "暗部 · 绿"), value.shadowsGreen, -.5f..0.5f) { update(value.copy(shadowsGreen = it)) }
                        parameter(language.text("Shadows · Blue", "暗部 · 蓝"), value.shadowsBlue, -.5f..0.5f) { update(value.copy(shadowsBlue = it)) }
                        parameter(language.text("Midtones · Red", "中间调 · 红"), value.midtonesRed, -.5f..0.5f) { update(value.copy(midtonesRed = it)) }
                        parameter(language.text("Midtones · Green", "中间调 · 绿"), value.midtonesGreen, -.5f..0.5f) { update(value.copy(midtonesGreen = it)) }
                        parameter(language.text("Midtones · Blue", "中间调 · 蓝"), value.midtonesBlue, -.5f..0.5f) { update(value.copy(midtonesBlue = it)) }
                        parameter(language.text("Highlights · Red", "亮部 · 红"), value.highlightsRed, -.5f..0.5f) { update(value.copy(highlightsRed = it)) }
                        parameter(language.text("Highlights · Green", "亮部 · 绿"), value.highlightsGreen, -.5f..0.5f) { update(value.copy(highlightsGreen = it)) }
                        parameter(language.text("Highlights · Blue", "亮部 · 蓝"), value.highlightsBlue, -.5f..0.5f) { update(value.copy(highlightsBlue = it)) }
                    }
                    LayerAdjustmentKind.GRADIENT_MAP -> {
                        AdjustmentGradientEditor(value.gradient) { update(value.copy(gradient = it)) }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onApply(value) }) { Text(language.text("Apply", "应用")) } },
        dismissButton = { TextButton(onDismiss) { Text(language.text("Cancel", "取消")) } },
    )
}
