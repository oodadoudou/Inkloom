package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.OutlineSoft
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Separate category splitter; its hit target never overlaps category reorder rows. */
@Composable
internal fun BrushLibrarySplitLayout(
    savedWidth: Float,
    onWidth: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(Dp, @Composable () -> Unit) -> Unit,
) {
    val density=LocalDensity.current.density
    val save by rememberUpdatedState(onWidth)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maximum=(maxWidth.value-144f).coerceIn(80f,360f)
        val minimum=minOf(96f,maximum)
        var width by remember(savedWidth) { mutableFloatStateOf(savedWidth) }
        val visibleWidth=width.coerceIn(minimum,maximum)
        var dragging by remember { mutableStateOf(false) }
        val drag=rememberDraggableState { delta -> width=(width.coerceIn(minimum,maximum)+delta/density).coerceIn(minimum,maximum) }
        Row(Modifier.fillMaxSize()) {
            content(visibleWidth.dp) {
                Box(Modifier.width(24.dp).fillMaxHeight().semantics {
                    contentDescription=label
                    stateDescription="${visibleWidth.toInt()} dp"
                    progressBarRangeInfo=ProgressBarRangeInfo(visibleWidth,minimum..maximum)
                    setProgress { value ->
                        if(!value.isFinite()) false else { width=value.coerceIn(minimum,maximum);save(width);true }
                    }
                }.draggable(drag,Orientation.Horizontal,
                    onDragStarted={width=visibleWidth;dragging=true},
                    onDragStopped={dragging=false;save(width)}),contentAlignment=Alignment.Center) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(if (dragging) Ink else OutlineSoft))
                }
            }
        }
    }
}
