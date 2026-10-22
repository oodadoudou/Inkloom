package app.inkloom.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Names an icon-only control while the pen or a mouse hovers over it, and on long-press unless
 * the control already uses long-press for something else ([longPressShows] false).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HoverTooltip(description: String, longPressShows: Boolean = true, content: @Composable () -> Unit) {
    val tooltipState = rememberTooltipState()
    val tooltipScope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description, fontSize = 11.sp) } },
        state = tooltipState,
        enableUserInput = longPressShows,
    ) {
        Box(Modifier.pointerInput(tooltipState) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Enter, PointerEventType.Move -> if (event.changes.none { it.pressed } && !tooltipState.isVisible) {
                            tooltipScope.launch { tooltipState.show() }
                        }
                        PointerEventType.Exit, PointerEventType.Press -> tooltipState.dismiss()
                        else -> Unit
                    }
                }
            }
        }) { content() }
    }
}
