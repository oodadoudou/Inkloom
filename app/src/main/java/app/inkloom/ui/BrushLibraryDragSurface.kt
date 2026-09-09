package app.inkloom.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/** The library, not a recycled row, owns a lifted brush while categories/rows change. */
@Composable
internal fun Modifier.brushLibraryDragSurface(
    hit: (Offset) -> String?,
    start: (String, Offset) -> Unit,
    move: (Offset) -> Unit,
    finish: () -> Unit,
    cancel: () -> Unit,
): Modifier {
    val currentHit = rememberUpdatedState(hit)
    val currentStart = rememberUpdatedState(start)
    val currentMove = rememberUpdatedState(move)
    val currentFinish = rememberUpdatedState(finish)
    val currentCancel = rememberUpdatedState(cancel)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val id = currentHit.value(down.position) ?: return@awaitEachGesture
            // Observe before child click/swipe handlers. Only claim after a stationary hold;
            // ordinary scrolling and short taps retain their normal child event path.
            val aborted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var stopped = false
                while (!stopped) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    stopped = change == null || !change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                }
                true
            }
            if (aborted == true) return@awaitEachGesture
            currentStart.value(id, down.position)
            var completed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    currentMove.value(change.position)
                    if (!change.pressed) {
                        currentFinish.value()
                        completed = true
                        break
                    }
                }
            } finally {
                if (!completed) currentCancel.value()
            }
        }
    }
}
