package app.inkloom.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.motionEventSpy
import app.inkloom.document.*
import kotlinx.coroutines.delay

/** The list owns the contact, so recycling the picked-up row cannot cancel an edge scroll. */
internal class LayerPanelDrag {
    val rows = mutableMapOf<String, Rect>()
    var viewport = Rect.Zero
    var origin by mutableStateOf<String?>(null)
        private set
    var drop by mutableStateOf<LayerDrop?>(null)
        private set
    var expanded by mutableStateOf(emptySet<String>())
        private set
    var point = Offset.Zero
        private set
    private var primary = ""
    private var selected = emptySet<String>()
    private var hoverStart = 0L
    private var lastHover: String? = null
    var page: () -> InkPage = { error("Layer drag is not bound") }
    var selection: () -> Pair<String, Set<String>> = { "" to emptySet() }
    var pick: (String) -> Unit = {}
    var commit: (String, Set<String>, LayerDrop) -> Unit = { _, _, _ -> }
    var expand: (String) -> Unit = {}

    fun start(local: Offset) {
        val source = rows.entries.firstOrNull { it.value.contains(viewport.topLeft + local) }?.key ?: return
        val current = page()
        if (current.layers.none { it.id == source && it.role != LayerRole.BACKGROUND }) return
        val (active, secondary) = selection()
        primary = if (source in secondary) active else source
        selected = if (source in secondary) secondary + active else setOf(source)
        if (source !in secondary && source != active) pick(source)
        origin = source
        point = viewport.topLeft + local
    }

    fun move(local: Offset) { if (origin != null) { point = viewport.topLeft + local; locate() } }

    fun locate() {
        if (origin == null || !viewport.contains(point)) { drop = null; return }
        val current = page()
        val hit = rows.entries.firstOrNull { it.value.contains(point) }
        val target = current.layers.firstOrNull { it.id == hit?.key }
        val next = if (target != null && hit != null) {
            val fraction = (point.y - hit.value.top) / hit.value.height
            val position = if (target.role == LayerRole.FOLDER && fraction in .25f.. .75f) LayerDropPosition.INSIDE
                else if (fraction < .5f) LayerDropPosition.ABOVE else LayerDropPosition.BELOW
            LayerDrop(target.id, position).takeIf {
                try { LayerOrderEdit.create(current, primary, selected, it); true }
                catch (_: LayerMoveRejected) { false }
            }
        } else null
        drop = next
        val hovered = next?.takeIf { it.position == LayerDropPosition.INSIDE }?.targetId
        if (hovered != lastHover) { lastHover = hovered; hoverStart = SystemClock.uptimeMillis() }
        if (hovered != null && SystemClock.uptimeMillis() - hoverStart >= 600 &&
            current.layers.any { it.id == hovered && it.collapsed }) expanded = expanded + hovered
    }

    fun finish() {
        val destination = drop
        if (origin != null && destination != null) {
            commit(primary, selected, destination)
            // Only the destination's visible ancestry is retained after a successful drop.
            var id: String? = if (destination.position == LayerDropPosition.INSIDE) destination.targetId
                else page().layers.firstOrNull { it.id == destination.targetId }?.parentId
            val seen = hashSetOf<String>()
            while (id != null && seen.add(id)) {
                val node = page().layers.firstOrNull { it.id == id } ?: break
                if (node.collapsed) expand(node.id)
                id = node.parentId
            }
        }
        cancel()
    }

    fun cancel() { origin = null; drop = null; expanded = emptySet(); lastHover = null }


}

@Composable
internal fun rememberLayerPanelDrag(
    page: InkPage, activeId: String, selected: Set<String>, state: LazyListState,
    edgePx: Float, pick: (String) -> Unit, commit: (String, Set<String>, LayerDrop) -> Unit, expand: (String) -> Unit,
): LayerPanelDrag {
    val currentPage by rememberUpdatedState(page)
    val currentSelection by rememberUpdatedState(activeId to selected)
    val currentPick by rememberUpdatedState(pick)
    val currentCommit by rememberUpdatedState(commit)
    val currentExpand by rememberUpdatedState(expand)
    val drag = remember(page.id) { LayerPanelDrag() }
    SideEffect {
        drag.page = { currentPage }; drag.selection = { currentSelection }
        drag.pick = { currentPick(it) }; drag.commit = { a, b, c -> currentCommit(a, b, c) }; drag.expand = { currentExpand(it) }
    }
    LaunchedEffect(drag.origin) {
        while (drag.origin != null) {
            val bounds = drag.viewport
            val y = drag.point.y
            if (drag.point.x in bounds.left..bounds.right) {
                val amount = when {
                    y in bounds.top..(bounds.top + edgePx) -> -((bounds.top + edgePx - y) / edgePx).coerceIn(0f, 1f) * edgePx / 4
                    y in (bounds.bottom - edgePx)..bounds.bottom -> ((y - bounds.bottom + edgePx) / edgePx).coerceIn(0f, 1f) * edgePx / 4
                    else -> 0f
                }
                if (amount != 0f) state.scrollBy(amount)
            }
            drag.locate()
            delay(16)
        }
    }
    DisposableEffect(drag) { onDispose { drag.cancel() } }
    return drag
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun Modifier.layerPanelDrag(drag: LayerPanelDrag): Modifier = motionEventSpy { motion ->
    // This Android interop observer only cancels; pointerInput still owns all dragging.
    if (motion.actionMasked == MotionEvent.ACTION_CANCEL ||
        (android.os.Build.VERSION.SDK_INT >= 33 && motion.flags and MotionEvent.FLAG_CANCELED != 0)) drag.cancel()
}.pointerInput(drag) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        drag.start(down.position)
        if (drag.origin == null) return@awaitEachGesture
        held.consume()
        try {
            while (true) {
                // A list-level owner sees movement before child swipe actions and scrolling.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.any { it.id != down.id && it.pressed }) break
                if (!change.pressed) {
                    // Compose synthetic cancellation marks its release consumed without a MotionEvent.
                    if (!change.isConsumed) drag.finish()
                    change.consume(); break
                }
                change.consume()
                drag.move(change.position)
            }
        } finally { drag.cancel() }
    }
}
