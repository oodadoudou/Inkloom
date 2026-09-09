package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Reveal only; deletion requires a separate tap. Vertical scroll is never consumed here. */
@Composable
internal fun LayerRowActions(actions: List<Pair<String, () -> Unit>>, onSelectSecondary: () -> Unit = {}, destructiveLabel: String? = null, content: @Composable () -> Unit) {
    var offset by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds().semantics {
        customActions = actions.map { (label, action) -> CustomAccessibilityAction(label) { action(); true } }
    }) {
        val revealWidth = minOf(maxWidth, (actions.size * 68).dp)
        val widthPx = with(LocalDensity.current) { revealWidth.toPx() }
        val selectThreshold = with(LocalDensity.current) { 40.dp.toPx() }
        Box(Modifier.fillMaxWidth().offset { IntOffset(offset.roundToInt(), 0) }
            .draggable(rememberDraggableState { offset = (offset + it).coerceIn(-widthPx, selectThreshold * 1.5f) }, Orientation.Horizontal,
                onDragStopped = { velocity ->
                    if (offset >= selectThreshold) { onSelectSecondary(); offset = 0f }
                    else offset = if (velocity < -400 || (velocity < 400 && offset < -widthPx / 3)) -widthPx else 0f
                })) { content() }
        if (offset < 0) Box(Modifier.matchParentSize()) {
            Box(Modifier.align(Alignment.CenterEnd).width(with(LocalDensity.current) { (-offset).toDp() })
                .fillMaxHeight().clipToBounds().background(Color(0xFFEDF0EE))) {
                Row(Modifier.align(Alignment.CenterEnd).requiredWidth(revealWidth).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    actions.forEach { (label, action) -> TextButton({ offset = 0f; action() }, Modifier.weight(1f).fillMaxHeight().background(if (label == destructiveLabel) Color(0xFFC93636) else Color.Transparent)) {
                        Text(label, color = if (label == destructiveLabel) Color.White else Color(0xFF242424), fontSize = 12.sp, maxLines = 1)
                    } }
                }
            }
        }
    }
}
