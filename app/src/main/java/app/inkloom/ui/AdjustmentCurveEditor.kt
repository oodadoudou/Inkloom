package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inkloom.document.AdjustmentCurvePoint
import app.inkloom.engine.v5.composite.PreparedToneCurve
import app.inkloom.settings.text
import kotlin.math.roundToInt

/** The graph and numeric controls edit the same points used by the raster kernel. */
@Composable
internal fun AdjustmentCurveEditor(
    points: List<AdjustmentCurvePoint>, smooth: Boolean,
    onChange: (List<AdjustmentCurvePoint>) -> Unit, onSmooth: (Boolean) -> Unit,
) {
    val language = LocalUiLanguage.current
    val normalized = remember(points) { PreparedToneCurve.normalize(points) }
    val current by rememberUpdatedState(normalized)
    val change by rememberUpdatedState(onChange)
    var selected by remember { mutableIntStateOf(0) }
    selected = selected.coerceIn(normalized.indices)
    val point = normalized[selected]
    val foreground = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val curve = remember(normalized, smooth) { PreparedToneCurve(normalized, smooth) }
    Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = language.text("Tone curve graph", "色调曲线图") }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val padding = 12.dp.toPx(); val hitRadius = 22.dp.toPx()
                val graphWidth = (size.width - padding * 2).coerceAtLeast(1f)
                val graphHeight = (size.height - padding * 2).coerceAtLeast(1f)
                fun pixel(point: AdjustmentCurvePoint) = Offset(padding + point.input * graphWidth,
                    padding + (1f - point.output) * graphHeight)
                val before = current
                var editing = before
                var index = before.indices.minByOrNull { (pixel(before[it]) - down.position).getDistance() } ?: 0
                if ((pixel(before[index]) - down.position).getDistance() > hitRadius) {
                    if (before.size >= 16) return@awaitEachGesture
                    val input = ((down.position.x - padding) / graphWidth).coerceIn(.001f, .999f)
                    if (before.any { kotlin.math.abs(it.input - input) < .001f }) return@awaitEachGesture
                    val added = AdjustmentCurvePoint(input, (1f - (down.position.y - padding) / graphHeight).coerceIn(0f, 1f))
                    editing = (before + added).sortedBy { it.input }; index = editing.indexOf(added)
                    change(editing)
                }
                selected = index; down.consume()
                var finished = false
                var outside = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val contact = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.any { it.id != down.id && it.pressed }) break
                        if (!contact.pressed) {
                            if (contact.isConsumed) break
                            if (outside && index in 1 until editing.lastIndex) {
                                editing = editing.filterIndexed { i, _ -> i != index }
                                selected = (index - 1).coerceAtLeast(0); change(editing)
                            }
                            contact.consume(); finished = true; break
                        }
                        outside = contact.position.x < padding - hitRadius || contact.position.x > size.width - padding + hitRadius ||
                            contact.position.y < padding - hitRadius || contact.position.y > size.height - padding + hitRadius
                        val input = if (index == 0 || index == editing.lastIndex) editing[index].input else
                            ((contact.position.x - padding) / graphWidth).coerceIn(editing[index - 1].input + .000025f, editing[index + 1].input - .000025f)
                        val output = (1f - (contact.position.y - padding) / graphHeight).coerceIn(0f, 1f)
                        editing = editing.toMutableList().apply { this[index] = AdjustmentCurvePoint(input, output) }
                        change(editing); contact.consume()
                    }
                } finally { if (!finished) { change(before); selected = selected.coerceIn(before.indices) } }
            }
        }) {
        val padding = 12.dp.toPx(); val width = size.width - 2 * padding; val height = size.height - 2 * padding
        drawRect(surface)
        for (step in 0..4) {
            val t = step / 4f
            drawLine(grid, Offset(padding + width * t, padding), Offset(padding + width * t, padding + height))
            drawLine(grid, Offset(padding, padding + height * t), Offset(padding + width, padding + height * t))
        }
        drawLine(grid, Offset(padding, padding + height), Offset(padding + width, padding))
        val path = Path()
        for (step in 0..256) {
            val x = step / 256f; val position = Offset(padding + width * x, padding + height * (1f - curve.sample(x)))
            if (step == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
        }
        drawPath(path, foreground, style = Stroke(1.5.dp.toPx()))
        normalized.forEachIndexed { index, point ->
            val center = Offset(padding + width * point.input, padding + height * (1f - point.output))
            drawCircle(surface, 5.dp.toPx(), center)
            drawCircle(foreground, 5.dp.toPx(), center, style = Stroke(if (index == selected) 2.5.dp.toPx() else 1.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        @Composable fun number(label: String, value: Float, enabled: Boolean, apply: (Float) -> Unit) {
            var text by remember(selected, value) { mutableStateOf((value * 255).roundToInt().toString()) }
            OutlinedTextField(text, { raw ->
                text = raw.filter(Char::isDigit).take(3)
                text.toIntOrNull()?.takeIf { it in 0..255 }?.let { apply(it / 255f) }
            }, label = { Text(label) }, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label curve value" })
        }
        number(language.text("Input", "输入"), point.input, selected in 1 until normalized.lastIndex) { input ->
            val next = input.coerceIn(normalized[selected - 1].input + .000025f, normalized[selected + 1].input - .000025f)
            onChange(normalized.toMutableList().apply { this[selected] = point.copy(input = next) })
        }
        number(language.text("Output", "输出"), point.output, true) { output ->
            onChange(normalized.toMutableList().apply { this[selected] = point.copy(output = output) })
        }
        IconButton({
            onChange(normalized.filterIndexed { index, _ -> index != selected }); selected = (selected - 1).coerceAtLeast(0)
        }, enabled = selected in 1 until normalized.lastIndex) {
            Icon(Icons.Outlined.DeleteOutline, language.text("Delete curve point", "删除曲线点"))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(!smooth, { onSmooth(false) }, label = { Text(language.text("Linear", "线性")) })
        FilterChip(smooth, { onSmooth(true) }, label = { Text(language.text("Smooth", "平滑")) })
        TextButton({ selected = 0; onChange(listOf(AdjustmentCurvePoint(0f, 0f), AdjustmentCurvePoint(1f, 1f))) }) {
            Text(language.text("Reset channel", "重置此通道"))
        }
    }
}
