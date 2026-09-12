package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.inkloom.ui.theme.*

/** A full-size input target with a fine visual track; cancellation never publishes the preview. */
@Composable
internal fun LayerOpacitySlider(value: Float, label: String, onAction: (LayerOpacityAction) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(onAction)
    fun discrete(next: Float): Boolean {
        if (!next.isFinite()) return false
        latest(LayerOpacityAction.Preview(next.coerceIn(0f, 1f)))
        latest(LayerOpacityAction.Commit)
        return true
    }
    Canvas(modifier.height(40.dp).semantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
        setProgress { discrete(it) }
    }.onKeyEvent {
        if (it.type != KeyEventType.KeyDown) false
        else when (it.key) {
            Key.DirectionLeft, Key.DirectionDown -> discrete(value - .01f)
            Key.DirectionRight, Key.DirectionUp -> discrete(value + .01f)
            Key.MoveHome -> discrete(0f)
            Key.MoveEnd -> discrete(1f)
            else -> false
        }
    }.focusable().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var committed = false
            fun update(x: Float) {
                val inset = 10.dp.toPx()
                val span = (size.width - inset * 2).coerceAtLeast(1f)
                latest(LayerOpacityAction.Preview(((x - inset) / span).coerceIn(0f, 1f)))
            }
            try {
                down.consume(); update(down.position.x)
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    if (!change.pressed) {
                        update(change.position.x); latest(LayerOpacityAction.Commit); committed = true; break
                    }
                    change.consume(); update(change.position.x)
                }
            } finally { if (!committed) latest(LayerOpacityAction.Cancel) }
        }
    }) {
        val inset = 10.dp.toPx()
        val start = Offset(inset, center.y); val end = Offset(size.width - inset, center.y)
        val thumb = Offset(start.x + (end.x - start.x) * value, center.y)
        drawLine(OutlineSoft, start, end, 3.dp.toPx(), StrokeCap.Round)
        drawLine(MutedInk, start, thumb, 3.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White, 7.dp.toPx(), thumb)
    }
}
