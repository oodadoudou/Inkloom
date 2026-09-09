package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import app.inkloom.ui.theme.Paper
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.SageContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.brushpresetv5.BrushPresetV5
import app.inkloom.brushpresetv5.EditorCurves
import app.inkloom.document.BrushCurvePoint
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/** Uses the actual imported brush curve evaluator, including non-monotonic authored curves. */
@Composable
internal fun BrushPressureCurve(id: String, preset: BrushPresetV5, language: UiLanguage, onValue: (BrushPresetV5) -> Unit) {
    val pressure = preset.dynamics.pressure
    val stored = when (id) {
        "pressureSizeCurveShape" -> pressure.widthCurve
        "pressureOpacityCurveShape" -> pressure.depositionCurve
        "pressureFlowCurveShape" -> pressure.flowCurve
        else -> error("Unknown pressure channel: $id")
    }
    val points = stored.ifEmpty { listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)) }
    val currentPoints by rememberUpdatedState(points)
    val update by rememberUpdatedState<(List<BrushCurvePoint>) -> Unit>({ changed ->
        val next = when (id) {
            "pressureSizeCurveShape" -> pressure.copy(widthCurve = changed)
            "pressureOpacityCurveShape" -> pressure.copy(depositionCurve = changed)
            else -> pressure.copy(flowCurve = changed)
        }
        onValue(preset.copy(dynamics = preset.dynamics.copy(pressure = next)))
    })
    val description = language.text("Editable brush pressure curve", "可编辑的笔刷压力曲线")
    Canvas(Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f).background(Paper).padding(16.dp)
        .semantics { contentDescription = description }
        .pointerInput(id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun position(p: BrushCurvePoint) = Offset(p.x * size.width, (1f - p.y) * size.height)
                val nearest = currentPoints.indices.minBy { (position(currentPoints[it]) - down.position).getDistanceSquared() }
                val atNode = (position(currentPoints[nearest]) - down.position).getDistance() <= 24.dp.toPx()
                val initial = if (atNode) currentPoints else (currentPoints + BrushCurvePoint(
                    (down.position.x / size.width).coerceIn(.001f, .999f),
                    (1f - down.position.y / size.height).coerceIn(0f, 1f))).sortedBy { it.x }
                val index = if (atNode) nearest else initial.indices.minBy { (position(initial[it]) - down.position).getDistanceSquared() }
                var working = initial
                fun move(to: Offset) {
                    val x = when (index) {
                        0 -> 0f
                        working.lastIndex -> 1f
                        else -> (to.x / size.width).coerceIn(working[index - 1].x, working[index + 1].x)
                    }
                    working = working.toMutableList().also {
                        it[index] = BrushCurvePoint(x, (1f - to.y / size.height).coerceIn(0f, 1f))
                    }
                    update(working)
                }
                down.consume()
                move(down.position)
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    move(change.position)
                } while (change.pressed)
            }
        }) {
        for (i in 0..4) {
            val x = size.width * i / 4f; val y = size.height * i / 4f
            drawLine(Color(0xFFD9DEDA), Offset(x, 0f), Offset(x, size.height))
            drawLine(Color(0xFFD9DEDA), Offset(0f, y), Offset(size.width, y))
        }
        val path = Path()
        for (i in 0..256) {
            val input = i / 256f
            val x = input * size.width; val y = (1f - EditorCurves.evaluate(points, input)) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Ink, style = Stroke(2.dp.toPx()))
        points.forEach { point ->
            val at = Offset(point.x * size.width, (1f - point.y) * size.height)
            drawCircle(Color.White, 8.dp.toPx(), at)
            drawCircle(Ink, 5.dp.toPx(), at)
        }
    }
    Text(language.text("Tap to add a point. Drag points to adjust the response.", "点击添加控制点，拖动控制点调整响应。"),
        color = Color.Gray, fontSize = 11.sp)
}
