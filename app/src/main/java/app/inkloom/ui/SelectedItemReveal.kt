package app.inkloom.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first

/** Selection changes and reopening may reveal an item; ordinary scrolling never retriggers this. */
@Composable
internal fun RevealSelectedItem(state: LazyListState, scope: Any?, key: Any?, index: Int, enabled: Boolean = true) {
    val canReveal by rememberUpdatedState(enabled)
    LaunchedEffect(scope, key) {
        if (!canReveal || key == null || index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        if (!canReveal) return@LaunchedEffect
        val layout = state.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
        if (item == null || item.offset < layout.viewportStartOffset || item.offset + item.size > layout.viewportEndOffset) {
            state.scrollToItem(index)
        }
    }
}

@Composable
internal fun RevealSelectedItem(state: LazyGridState, scope: Any?, key: Any?, index: Int, enabled: Boolean = true) {
    val canReveal by rememberUpdatedState(enabled)
    LaunchedEffect(scope, key) {
        if (!canReveal || key == null || index < 0) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        if (!canReveal) return@LaunchedEffect
        val layout = state.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
        if (item == null || item.offset.y < layout.viewportStartOffset || item.offset.y + item.size.height > layout.viewportEndOffset) {
            state.scrollToItem(index)
        }
    }
}
