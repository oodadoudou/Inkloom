package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.inkloom.settings.DockDisplay
import app.inkloom.settings.EditorPanel
import app.inkloom.settings.PanelEdge
import app.inkloom.settings.PanelRect
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.MutedInk
import app.inkloom.ui.theme.OutlineSoft
import app.inkloom.ui.theme.Paper
import app.inkloom.ui.theme.SageContainer

/** Where a pop-up palette opens beside its dock strip: next to its icon, toward the canvas, inside the window. */
internal object PalettePopups {
    /** Hide and expand buttons at the start of an icon strip, then one button per palette. */
    const val controlSize = 36f
    const val iconSize = 44f

    fun place(strip: PanelRect, side: PanelEdge, index: Int, width: Float, height: Float, windowWidth: Float, windowHeight: Float): PanelRect {
        val w = width.coerceAtMost(windowWidth); val h = height.coerceAtMost(windowHeight)
        // A hidden dock has no icons ([index] < 0): the pop-up opens at the start of its strip.
        val along = if (index < 0) 8f else 2 * controlSize + 4f + index * iconSize - 8f
        val x = when (side) {
            PanelEdge.RIGHT -> strip.x - w - 4f
            PanelEdge.LEFT -> strip.right + 4f
            else -> strip.x + along
        }
        val y = when (side) {
            PanelEdge.BOTTOM -> strip.y - h - 4f
            PanelEdge.TOP -> strip.bottom + 4f
            else -> strip.y + along
        }
        return PanelRect(x.coerceIn(0f, (windowWidth - w).coerceAtLeast(0f)), y.coerceIn(0f, (windowHeight - h).coerceAtLeast(0f)), w, h)
    }
}

private fun hideIcon(side: PanelEdge): ImageVector = when (side) {
    PanelEdge.LEFT -> Icons.Outlined.KeyboardDoubleArrowLeft
    PanelEdge.BOTTOM -> Icons.Outlined.KeyboardDoubleArrowDown
    PanelEdge.TOP -> Icons.Outlined.KeyboardDoubleArrowUp
    else -> Icons.Outlined.KeyboardDoubleArrowRight
}
private fun showIcon(side: PanelEdge): ImageVector = when (side) {
    PanelEdge.LEFT -> Icons.Outlined.KeyboardDoubleArrowRight
    PanelEdge.BOTTOM -> Icons.Outlined.KeyboardDoubleArrowUp
    PanelEdge.TOP -> Icons.Outlined.KeyboardDoubleArrowDown
    else -> Icons.Outlined.KeyboardDoubleArrowLeft
}
private fun iconsIcon(side: PanelEdge): ImageVector = when (side) {
    PanelEdge.LEFT -> Icons.Outlined.ChevronLeft
    PanelEdge.BOTTOM -> Icons.Outlined.ExpandMore
    PanelEdge.TOP -> Icons.Outlined.ExpandLess
    else -> Icons.Outlined.ChevronRight
}
private fun expandIcon(side: PanelEdge): ImageVector = when (side) {
    PanelEdge.LEFT -> Icons.Outlined.ChevronRight
    PanelEdge.BOTTOM -> Icons.Outlined.ExpandLess
    PanelEdge.TOP -> Icons.Outlined.ExpandMore
    else -> Icons.Outlined.ChevronLeft
}

@Composable
private fun DockButton(icon: ImageVector, label: String, size: Float, onClick: () -> Unit, selected: Boolean = false) {
    IconButton(onClick = onClick, modifier = Modifier.size(size.dp).background(if (selected) SageContainer else Color.Transparent)) {
        Icon(icon, label, Modifier.size(if (size < 30f) 16.dp else 20.dp), tint = Ink)
    }
}

/** CSP's bar above a palette dock: the double arrow hides the dock, the single arrow shows it as icons. */
@Composable
internal fun PaletteDockHeader(side: PanelEdge, language: UiLanguage, modifier: Modifier, onDisplay: (DockDisplay) -> Unit) {
    val hide = language.text("Hide palette dock", "隐藏面板停靠栏")
    val icons = language.text("Show palette dock as icons", "停靠栏显示为图标")
    Row(modifier.background(Paper).drawBehind { drawLine(OutlineSoft, Offset(0f, size.height - .5f), Offset(size.width, size.height - .5f), 1.dp.toPx()) },
        horizontalArrangement = if (side == PanelEdge.LEFT) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (side == PanelEdge.LEFT) {
            DockButton(hideIcon(side), hide, 22f, { onDisplay(DockDisplay.HIDDEN) })
            DockButton(iconsIcon(side), icons, 22f, { onDisplay(DockDisplay.ICONS) })
        } else {
            DockButton(iconsIcon(side), icons, 22f, { onDisplay(DockDisplay.ICONS) })
            DockButton(hideIcon(side), hide, 22f, { onDisplay(DockDisplay.HIDDEN) })
        }
    }
}

/**
 * A collapsed palette dock. As icons it lists its palettes; tapping one opens it as a pop-up, or for a
 * palette set to display as a tab, expands the dock with it in front. Hidden, it is a thin strip that shows the dock again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PaletteDockStrip(
    side: PanelEdge, display: DockDisplay, panels: List<EditorPanel>, popup: EditorPanel?, language: UiLanguage, modifier: Modifier,
    title: (EditorPanel) -> String, icon: (EditorPanel) -> ImageVector, onDisplay: (DockDisplay) -> Unit, onOpen: (EditorPanel) -> Unit,
    tabPanels: Set<EditorPanel> = emptySet(), onTabMode: (EditorPanel, Boolean) -> Unit = { _, _ -> },
) {
    val vertical = side == PanelEdge.LEFT || side == PanelEdge.RIGHT
    val frame = modifier.background(Paper).border(1.dp, OutlineSoft).clipToBounds()
    if (display == DockDisplay.HIDDEN) {
        val show = language.text("Show palette dock", "显示面板停靠栏")
        Box(frame.clickable { onDisplay(DockDisplay.NORMAL) }.semantics { contentDescription = show },
            contentAlignment = if (vertical) Alignment.TopCenter else Alignment.CenterStart) {
            Icon(showIcon(side), null, Modifier.padding(2.dp).size(16.dp), tint = MutedInk)
        }
        return
    }
    @Composable fun controls() {
        DockButton(expandIcon(side), language.text("Expand palette dock", "展开面板停靠栏"), PalettePopups.controlSize, { onDisplay(DockDisplay.NORMAL) })
        DockButton(hideIcon(side), language.text("Hide palette dock", "隐藏面板停靠栏"), PalettePopups.controlSize, { onDisplay(DockDisplay.HIDDEN) })
    }
    // Tap opens the palette; a long press chooses how, as CSP's icon menu does.
    @Composable fun icons() = panels.forEach { panel ->
        var menu by remember { mutableStateOf(false) }
        Box {
            Box(Modifier.size(PalettePopups.iconSize.dp).background(if (popup == panel) SageContainer else Color.Transparent)
                .combinedClickable(onClick = { onOpen(panel) }, onLongClick = { menu = true })
                .semantics { contentDescription = language.text("${title(panel)} palette", "${title(panel)}面板") },
                contentAlignment = Alignment.Center) { Icon(icon(panel), null, Modifier.size(20.dp), tint = Ink) }
            DropdownMenu(menu, { menu = false }) {
                val tab = panel in tabPanels
                StudioMenuItem(text = { Text(language.text("Display as pop-up", "以弹出方式显示")) }, onClick = { onTabMode(panel, false); menu = false },
                    trailingIcon = { if (!tab) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) })
                StudioMenuItem(text = { Text(language.text("Display as tab", "以标签方式显示")) }, onClick = { onTabMode(panel, true); menu = false },
                    trailingIcon = { if (tab) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) })
            }
        }
    }
    if (vertical) Column(frame, horizontalAlignment = Alignment.CenterHorizontally) {
        controls()
        Spacer(Modifier.height(4.dp).fillMaxWidth().drawBehind { drawLine(OutlineSoft, Offset(4.dp.toPx(), size.height / 2), Offset(size.width - 4.dp.toPx(), size.height / 2), 1.dp.toPx()) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { icons() }
    } else Row(frame, verticalAlignment = Alignment.CenterVertically) {
        controls()
        Spacer(Modifier.width(4.dp).fillMaxHeight().drawBehind { drawLine(OutlineSoft, Offset(size.width / 2, 4.dp.toPx()), Offset(size.width / 2, size.height - 4.dp.toPx()), 1.dp.toPx()) })
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { icons() }
    }
}

/** A palette opened from a collapsed dock. A tap anywhere outside it closes it, as CSP's pop-up palettes do. */
@Composable
internal fun PalettePopup(
    panel: EditorPanel, title: String, icon: ImageVector, language: UiLanguage, modifier: Modifier, onClose: () -> Unit, body: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().zIndex(290f).semantics { contentDescription = language.text("Close pop-up palette", "关闭弹出面板") }
        .pointerInput(panel) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume(); onClose() } })
    Column(modifier.zIndex(296f).shadow(6.dp).background(Paper).border(1.dp, OutlineSoft)) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), tint = MutedInk)
            Spacer(Modifier.width(6.dp))
            Text(title, Modifier.weight(1f), fontSize = 11.sp, lineHeight = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Close, language.text("Close $title pop-up", "关闭${title}弹出面板"), Modifier.size(16.dp), tint = Ink)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { body() }
    }
}
