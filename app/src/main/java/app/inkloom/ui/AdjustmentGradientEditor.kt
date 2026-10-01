package app.inkloom.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inkloom.document.GradientColorStop
import app.inkloom.document.GradientColors
import app.inkloom.document.GradientEdgeMode
import app.inkloom.settings.text
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AdjustmentGradientEditor(values: List<GradientColorStop>, onChange: (List<GradientColorStop>) -> Unit) {
    val language = LocalUiLanguage.current
    val stops = GradientColors.normalize(values)
    var colorIndex by remember { mutableStateOf<Int?>(null) }
    val opaque = stops.map { it.position to Color((it.colorArgb or 0xFF000000L).toInt()) }.toTypedArray()
    Box(Modifier.fillMaxWidth().height(30.dp).background(Brush.horizontalGradient(*opaque)))
    Spacer(Modifier.height(8.dp))
    stops.forEachIndexed { index, stop ->
        val inner = index in 1 until stops.lastIndex
        val minimum = if (inner) stops[index - 1].position + .001f else stop.position
        val maximum = if (inner) stops[index + 1].position - .001f else stop.position
        val movable = inner && minimum < maximum
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Color((stop.colorArgb or 0xFF000000L).toInt()))
                .semantics { contentDescription = language.text("Gradient stop color ${index + 1}", "渐变色标颜色 ${index + 1}") }
                .combinedClickable(onClick = { colorIndex = index }, onLongClick = { colorIndex = index }))
            Text("${(stop.position * 100).roundToInt()}%", Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall)
            BrushParameterSlider(stop.position.coerceIn(0f, 1f), { position ->
                onChange(stops.toMutableList().apply { this[index] = stop.copy(position = position.coerceIn(minimum, maximum)) })
            }, enabled = movable, valueRange = if (movable) minimum..maximum else 0f..1f,
                modifier = Modifier.weight(1f).height(36.dp).semantics {
                    contentDescription = language.text("Gradient stop position ${index + 1}", "渐变色标位置 ${index + 1}")
                })
            IconButton({ onChange(stops.filterIndexed { i, _ -> i != index }) }, enabled = inner, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.DeleteOutline, language.text("Delete gradient stop ${index + 1}", "删除渐变色标 ${index + 1}"))
            }
        }
    }
    TextButton({
        val gap = stops.zipWithNext().maxByOrNull { (a, b) -> b.position - a.position } ?: return@TextButton
        val position = (gap.first.position + gap.second.position) / 2f
        val color = GradientColors.sample(stops, position, GradientEdgeMode.CLAMP).toUInt().toLong() or 0xFF000000L
        onChange((stops + GradientColorStop(position, color)).sortedBy { it.position })
    }, enabled = stops.size < 16 && stops.zipWithNext().any { (a, b) -> b.position - a.position > .004f }) {
        Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
        Text(language.text("Add color stop", "添加色标"))
    }
    TextButton({ onChange(listOf(GradientColorStop(0f, 0xFF000000L), GradientColorStop(1f, 0xFFFFFFFFL))) }) {
        Text(language.text("Reset gradient", "重置渐变"))
    }
    colorIndex?.let { index ->
        val stop = stops.getOrNull(index)
        if (stop != null) StandardColorPickerDialog((stop.colorArgb or 0xFF000000L).toInt(), language,
            onConfirm = { color ->
                onChange(stops.toMutableList().apply { this[index] = stop.copy(colorArgb = color.toUInt().toLong() or 0xFF000000L) })
                colorIndex = null
            }, onDismiss = { colorIndex = null })
    }
}
