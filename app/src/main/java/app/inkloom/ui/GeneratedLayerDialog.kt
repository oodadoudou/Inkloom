package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientDefinition
import app.inkloom.document.GradientEdgeMode
import app.inkloom.document.GradientKind
import app.inkloom.document.LayerGenerator
import app.inkloom.document.ToneDotShape
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** CSP Layer > New layer > Fill, Gradient and Tone. */
internal enum class GeneratedLayerKind { FILL, GRADIENT, TONE }

internal fun defaultGenerator(kind: GeneratedLayerKind, drawing: Int, background: Int): LayerGenerator = when (kind) {
    GeneratedLayerKind.FILL -> LayerGenerator.Fill(drawing.toLong() and 0xFFFFFFFFL)
    GeneratedLayerKind.GRADIENT -> LayerGenerator.Gradient(GradientDefinition(stops = listOf(
        GradientColorStop(0f, drawing.toLong() and 0xFFFFFFFFL), GradientColorStop(1f, background.toLong() and 0xFFFFFFFFL))))
    GeneratedLayerKind.TONE -> LayerGenerator.Tone(colorArgb = drawing.toLong() and 0xFFFFFFFFL)
}

internal fun generatedLayerName(generator: LayerGenerator, language: UiLanguage): String = when (generator) {
    is LayerGenerator.Fill -> language.text("Fill", "填充")
    is LayerGenerator.Gradient -> language.text("Gradient", "渐变")
    is LayerGenerator.Tone -> language.text("Tone", "网点")
}

@Composable
internal fun GeneratedLayerDialog(initial: LayerGenerator, editing: Boolean, language: UiLanguage, busy: Boolean,
    onDismiss: () -> Unit, onApply: (LayerGenerator) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var value by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var pickingColor by remember { mutableStateOf(0) }
    var shapeMenu by remember { mutableStateOf(false) }
    picking?.let { onPicked ->
        StandardColorPickerDialog(pickingColor, language, onConfirm = { onPicked(it); picking = null }, onDismiss = { picking = null })
    }
    @Composable fun colorRow(label: String, argb: Long, onColor: (Long) -> Unit) =
        StudioButton("$label…", { pickingColor = argb.toInt(); picking = { onColor(it.toLong() and 0xFFFFFFFFL) } }) {
            Box(Modifier.size(14.dp).background(Color(argb.toInt())))
        }
    val title = when (value) {
        is LayerGenerator.Fill -> if (editing) tr("Fill layer", "填充图层") else tr("New fill layer", "新建填充图层")
        is LayerGenerator.Gradient -> if (editing) tr("Gradient layer", "渐变图层") else tr("New gradient layer", "新建渐变图层")
        is LayerGenerator.Tone -> if (editing) tr("Tone layer", "网点图层") else tr("New tone layer", "新建网点图层")
    }
    StudioDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (val current = value) {
                is LayerGenerator.Fill -> colorRow(tr("Color", "颜色"), current.colorArgb) { value = current.copy(colorArgb = it) }
                is LayerGenerator.Gradient -> {
                    val d = current.definition
                    fun names(kind: GradientKind) = when (kind) {
                        GradientKind.LINEAR -> tr("Linear", "线性"); GradientKind.RADIAL -> tr("Circle", "圆形")
                        GradientKind.ELLIPSE -> tr("Ellipse", "椭圆"); GradientKind.ANGULAR -> tr("Angle", "角度")
                        GradientKind.REFLECTED -> tr("Reflected", "对称"); GradientKind.DIAMOND -> tr("Diamond", "菱形")
                    }
                    StudioDropdownField(names(d.kind), shapeMenu, { shapeMenu = it }, label = tr("Shape", "形状")) {
                        GradientKind.entries.forEach { kind -> StudioMenuItem(text = { Text(names(kind)) }, onClick = {
                            value = current.copy(definition = d.copy(kind = kind)); shapeMenu = false }) }
                    }
                    StudioSegmented(listOf(tr("Clamp", "延伸"), tr("Repeat", "重复"), tr("Mirror", "镜像"), tr("None", "透明")),
                        GradientEdgeMode.entries.indexOf(d.edgeMode), { value = current.copy(definition = d.copy(edgeMode = GradientEdgeMode.entries[it])) },
                        label = tr("Edges", "边缘"))
                    StudioCheckRow(tr("Reverse", "反转"), d.reverse, { value = current.copy(definition = d.copy(reverse = it)) })
                    AdjustmentGradientEditor(d.stops) { value = current.copy(definition = d.copy(stops = it)) }
                    // Direction and length about the gradient's centre, in page fractions.
                    val cx = (current.startX + current.endX) / 2f; val cy = (current.startY + current.endY) / 2f
                    val angle = Math.toDegrees(atan2((current.endY - current.startY).toDouble(), (current.endX - current.startX).toDouble())).toFloat()
                    val length = hypot(current.endX - current.startX, current.endY - current.startY)
                    fun placed(centerX: Float = cx, centerY: Float = cy, degrees: Float = angle, size: Float = length): LayerGenerator.Gradient {
                        val r = Math.toRadians(degrees.toDouble()); val hx = (cos(r) * size / 2).toFloat(); val hy = (sin(r) * size / 2).toFloat()
                        return current.copy(startX = centerX - hx, startY = centerY - hy, endX = centerX + hx, endY = centerY + hy)
                    }
                    StudioSliderRow(tr("Angle", "角度"), angle, -180f..180f, { value = placed(degrees = it) }, unit = "°")
                    StudioSliderRow(tr("Length", "长度"), length * 100f, 5f..200f, { value = placed(size = it / 100f) }, unit = "%")
                    StudioSliderRow(tr("Center X", "中心 X"), cx * 100f, 0f..100f, { value = placed(centerX = it / 100f) }, unit = "%")
                    StudioSliderRow(tr("Center Y", "中心 Y"), cy * 100f, 0f..100f, { value = placed(centerY = it / 100f) }, unit = "%")
                }
                is LayerGenerator.Tone -> {
                    StudioSliderRow(tr("Lines per inch", "网线数"), current.linesPerInch, 5f..120f, { value = current.copy(linesPerInch = it.roundToInt().toFloat()) }, unit = "lpi")
                    StudioSliderRow(tr("Density", "浓度"), current.density * 100f, 0f..100f, { value = current.copy(density = it / 100f) }, unit = "%")
                    StudioSegmented(listOf(tr("Circle", "圆"), tr("Square", "方"), tr("Diamond", "菱形"), tr("Line", "线"), tr("Cross", "十字")),
                        ToneDotShape.entries.indexOf(current.dot), { value = current.copy(dot = ToneDotShape.entries[it]) }, label = tr("Dot", "网点形状"))
                    StudioSliderRow(tr("Angle", "角度"), current.angleDegrees, -90f..90f, { value = current.copy(angleDegrees = it) }, unit = "°")
                    colorRow(tr("Color", "颜色"), current.colorArgb) { value = current.copy(colorArgb = it) }
                }
            }
        }
    }, confirmButton = { TextButton({ onApply(value) }, enabled = !busy) { Text(if (editing) tr("Apply", "应用") else tr("Create", "创建")) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(tr("Cancel", "取消")) } })
}
