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
import app.inkloom.ui.theme.Paper
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.SageContainer
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
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlin.math.roundToInt

/** Horizontal reveal coexists with vertical library scrolling and long-press brush editing. */
@Composable
internal fun RecentBrushActions(
    enabled: Boolean,
    pinned: Boolean,
    language: UiLanguage,
    onPin: () -> Unit,
    onFind: (() -> Unit)?,
    onClear: (() -> Unit)?,
    clearText: String? = null,
    content: @Composable () -> Unit,
) {
    if (!enabled) { content(); return }
    val pinLabel = if (pinned) language.text("Unpin", "取消置顶") else language.text("Pin", "置顶")
    val findLabel = language.text("Find", "查找原组")
    val clearLabel = clearText ?: language.text("Clear", "清除记录")
    val actions = listOfNotNull(pinLabel to onPin, onFind?.let { findLabel to it }, onClear?.let { clearLabel to it })
    var offset by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds().semantics {
        customActions = actions.map { (label, action) -> CustomAccessibilityAction(label) { action(); true } }
    }) {
        val revealWidth = minOf(maxWidth, (actions.size * 70).dp)
        val widthPx = with(LocalDensity.current) { revealWidth.toPx() }
        Box(Modifier.fillMaxWidth().offset { IntOffset(offset.roundToInt(), 0) }
            .draggable(rememberDraggableState { delta -> offset = (offset + delta).coerceIn(-widthPx, 0f) },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity -> offset = if (velocity < -400f || (velocity < 400f && offset < -widthPx / 3f)) -widthPx else 0f })) {
            content()
        }
        // Keep revealed actions above the translated row in hit-testing and accessibility.
        // A placement-only offset does not reliably invalidate TalkBack's occlusion cache.
        if (offset < 0f) Box(Modifier.matchParentSize()) {
            Box(Modifier.align(Alignment.CenterEnd).width(with(LocalDensity.current) { (-offset).toDp() })
                .fillMaxHeight().clipToBounds().background(Paper)) {
                Row(Modifier.align(Alignment.CenterEnd).requiredWidth(revealWidth).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically) {
                    actions.forEach { (label, action) ->
                        TextButton(onClick = { offset = 0f; action() }, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Text(label, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
