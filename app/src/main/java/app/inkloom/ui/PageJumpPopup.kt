package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.inkloom.document.InkPage
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*

/** Prefer the gap above the movable navigator; keep the popup inside safe window edges. */
internal class PageJumpPosition(private val navigator: IntRect, private val gap: Int,
    private val topInset: Int = 0, private val bottomInset: Int = 0) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)
        val maxY = (windowSize.height - bottomInset - popupContentSize.height - gap).coerceAtLeast(topInset + gap)
        val x = (navigator.center.x - popupContentSize.width / 2).coerceIn(gap, maxX)
        val above = navigator.top - popupContentSize.height - gap
        val y = if (above >= topInset + gap) above else if (navigator.bottom + gap <= maxY) navigator.bottom + gap
            else above.coerceIn(topInset + gap, maxY)
        return IntOffset(x, y)
    }
}

@Composable
internal fun PageJumpPopup(pages: List<InkPage>, selectedPageId: String, navigator: IntRect,
    language: UiLanguage, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.roundToPx() }
    val top = WindowInsets.safeDrawing.getTop(density)
    val bottom = WindowInsets.safeDrawing.getBottom(density)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = pages.indexOfFirst { it.id == selectedPageId }.coerceAtLeast(0))
    LaunchedEffect(selectedPageId, pages.map { it.id }) {
        val index = pages.indexOfFirst { it.id == selectedPageId }
        if (index >= 0 && list.layoutInfo.visibleItemsInfo.none { it.key == selectedPageId }) list.scrollToItem(index)
    }
    val window = LocalWindowInfo.current.containerSize
    val width = with(density) { window.width.toDp() }
    val height = with(density) { (window.height - top - bottom).toDp() - 100.dp }.coerceIn(100.dp, 400.dp)
    Popup(popupPositionProvider = remember(navigator, gap, top, bottom) { PageJumpPosition(navigator, gap, top, bottom) },
        onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(shape = RoundedCornerShape(12.dp), color = Paper, shadowElevation = 6.dp,
            modifier = Modifier.width(minOf(280.dp, (width - 24.dp).coerceAtLeast(72.dp)))
                .semantics { contentDescription = language.text("Page jump panel", "页面跳转面板") }) {
            Column(Modifier.padding(6.dp)) {
                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(language.text("Go to page", "跳转页面"), Modifier.weight(1f).padding(start = 6.dp), color = Ink)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, language.text("Close page jump", "关闭页面跳转"), Modifier.size(17.dp))
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = height), state = list) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        Text("${index + 1}  ${page.title.ifBlank { language.text("Page", "页面") }}",
                            Modifier.fillMaxWidth().background(if (page.id == selectedPageId) SageContainer else Paper, RoundedCornerShape(4.dp))
                                .semantics { contentDescription = language.text("Go to page ${index + 1}", "跳转到第 ${index + 1}"); selected = page.id == selectedPageId }
                                .clickable { onSelect(page.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                            color = if (page.id == selectedPageId) Ink else MutedInk)
                    }
                }
            }
        }
    }
}
