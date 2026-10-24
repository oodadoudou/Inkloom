package app.inkloom.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import android.view.PointerIcon as AndroidPointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.inkloom.settings.*
import app.inkloom.ui.theme.*

internal class EditorPanelState(context: Context) {
    private val store = EditorWorkspaceStore(context)
    private val initial = store.load()
    var layout by mutableStateOf(initial.layout)
    var namedLayouts by mutableStateOf(initial.namedLayouts)
        private set

    private fun updateNamedLayouts(next: NamedPanelLayouts) {
        if (next == namedLayouts) return
        namedLayouts = store.updateNamedLayouts(next).namedLayouts
    }
    fun backup() = store.load()
    fun restoreBackup(backup: PanelWorkspaceBackup) {
        val restored = store.restore(backup)
        layout = restored.layout
        namedLayouts = restored.namedLayouts
    }

    fun saveNamedLayout(name: String, settings: AppSettings) =
        updateNamedLayouts(namedLayouts.save(name, layout, ToolRailLayout.capture(settings)))
    fun renameNamedLayout(oldName: String, newName: String) = updateNamedLayouts(namedLayouts.rename(oldName, newName))
    fun deleteNamedLayout(name: String) = updateNamedLayouts(namedLayouts.delete(name))
    fun restoreNamedLayout(name: String): NamedPanelLayout? {
        val saved = namedLayouts.entries.firstOrNull { it.name == name } ?: return null
        commit(saved.layout)
        return saved
    }
    fun commit(next: EditorPanelLayout) {
        layout = store.updateLayout(next).layout
    }
    var activeTabId: String? = null
    /** The palette open as a pop-up from an icon or hidden dock (CSP's simple view); tapping outside closes it. */
    var popup by mutableStateOf<EditorPanel?>(null)
    fun raise(rootId: String, tabId: String) {
        activeTabId = tabId
        val root = layout.floating.firstOrNull { it.node.id == rootId } ?: return
        if (layout.floating.lastOrNull() != root) commit(layout.copy(floating = layout.floating - root + root))
    }
    fun show(panel: EditorPanel, open: Boolean) {
        if (!open && popup == panel) popup = null
        val next = if(open) layout.open+panel else layout.open-panel
        if(next != layout.open) commit(layout.copy(open=next))
    }
    private fun collapsed(panel: EditorPanel) = EditorPanelLayouts.inCollapsedDock(layout, panel)
    /** Visible on screen: in a full dock or window, or as the current pop-up of a collapsed dock. */
    fun shown(panel: EditorPanel) = panel in layout.open && (!collapsed(panel) || popup == panel)
    /**
     * A toolbar button closing its palette: a palette in an icon or hidden dock only closes its
     * pop-up and keeps its icon, as in CSP; elsewhere the palette closes.
     */
    fun hide(panel: EditorPanel) {
        if (panel in layout.open && collapsed(panel)) { if (popup == panel) popup = null } else show(panel, false)
    }
    fun setDockDisplay(id: String, display: DockDisplay) {
        popup = null
        commit(EditorPanelLayouts.setDockDisplay(layout, id, display))
    }
    private fun owner(panel: EditorPanel): PanelNode.Tabs? {
        fun find(node: PanelNode): PanelNode.Tabs? = when (node) {
            is PanelNode.Tabs -> node.takeIf { panel in it.panels }
            is PanelNode.Split -> find(node.first) ?: find(node.second)
            is PanelNode.Canvas, is PanelNode.Space -> null
        }
        return find(layout.dock) ?: layout.floating.firstNotNullOfOrNull { find(it.node) }
    }
    fun isActive(panel: EditorPanel): Boolean = panel in layout.open && (if (collapsed(panel)) popup == panel else owner(panel)?.let {
        it.active == panel && it.id == activeTabId
    } == true)
    fun focus(panel: EditorPanel) {
        if (panel !in layout.open) return
        val tabId = owner(panel)?.id ?: return
        activeTabId = tabId
        if (collapsed(panel)) { popup = panel; return }
        val selected = EditorPanelLayouts.select(layout, panel)
        val root = selected.floating.firstOrNull { EditorPanelLayouts.find(it.node, tabId) != null }
        commit(if (root == null || selected.floating.lastOrNull() == root) selected
            else selected.copy(floating = selected.floating - root + root))
    }
    /** The colour chips: opening shows the Color Wheel alone; the other colour palettes open from Views. */
    fun colors(open: Boolean) {
        val colors=EditorPanel.entries.filter { it.ordinal >= EditorPanel.WHEEL.ordinal }.toSet()
        if(!open) commit(layout.copy(open=layout.open-colors))
        else {
            show(EditorPanel.WHEEL, true)
            focus(EditorPanel.WHEEL)
        }
    }
    fun closeTopPanel(onClose: (EditorPanel) -> Unit): Boolean {
        if (popup != null) { popup = null; return true }
        val slots = EditorPanelLayouts.slots(layout, 1000f, 800f).filterNot(PanelSlot::collapsed)
        val tabs = (slots.firstOrNull { it.node.id == activeTabId && it.node is PanelNode.Tabs }
            ?: slots.lastOrNull { it.node is PanelNode.Tabs })?.node as? PanelNode.Tabs ?: return false
        show(tabs.active, false)
        onClose(tabs.active)
        return true
    }
}

@Composable
internal fun rememberEditorPanelState(): EditorPanelState {
    val context=LocalContext.current.applicationContext
    return remember(context) { EditorPanelState(context) }
}

private data class PanelDrag(
    val original: EditorPanelLayout, val sourceId: String, val panel: EditorPanel?,
    val start: PanelRect, val x: Float, val y: Float,
    val dx: Float=0f, val dy: Float=0f, val target: PanelDropTarget?=null,
)

private data class PanelResize(
    val original: EditorPanelLayout, val id: String, val grip: PanelGrip?,
    val floating: Boolean, val start: Offset,
)

/** One render host per panel group. During a drag its existing contents move, never a second copy. */
@Composable
internal fun EditorPanels(
    state: EditorPanelState,
    onClose: (EditorPanel) -> Unit,
    onReleaseInput: () -> Unit = {},
    backDismissEnabled: Boolean = true,
    onBackBeforePanels: () -> Boolean = { false },
    canvas: @Composable () -> Unit,
    content: @Composable (EditorPanel) -> Unit,
) {
    val density=LocalDensity.current.density
    val context=LocalContext.current
    val language=LocalUiLanguage.current
    fun title(panel: EditorPanel) = editorPanelTitle(panel, language)
    fun icon(panel: EditorPanel) = editorPanelIcon(panel)
    var drag by remember { mutableStateOf<PanelDrag?>(null) }
    var resize by remember { mutableStateOf<PanelResize?>(null) }
    val gestureDown = remember { arrayOf(Offset.Zero) }
    val dragFocus = remember { FocusRequester() }
    val gestureOrigin = remember { arrayOfNulls<EditorPanelLayout>(1) }
    val currentClose by rememberUpdatedState(onClose)
    val holder=rememberSaveableStateHolder()
    val currentContent by rememberUpdatedState(content)
    val bodies = remember {
        EditorPanel.entries.associateWith { panel -> movableContentOf {
            holder.SaveableStateProvider(panel.name) { currentContent(panel) }
        } }
    }
    val releaseInput by rememberUpdatedState(onReleaseInput)
    fun cancel() { val original=drag?.original ?: return; state.commit(original); drag=null; releaseInput() }
    fun cancelResize() {
        resize?.let { state.commit(it.original) }
        resize = null
        releaseInput()
    }
    fun closeCurrent(tabs: PanelNode.Tabs) {
        state.show(tabs.active,false)
        currentClose(tabs.active); releaseInput()
    }
    BackHandler(backDismissEnabled && drag == null && resize == null && state.layout.open.isNotEmpty()) {
        if (!onBackBeforePanels()) {
            if (state.closeTopPanel(currentClose)) releaseInput()
        }
    }
    BackHandler(drag != null || resize != null) { if (resize != null) cancelResize() else cancel() }
    DisposableEffect(Unit) { onDispose {
        resize?.let { state.commit(it.original) }; resize=null; drag=null
    } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Workspace).focusRequester(dragFocus).focusable().onPreviewKeyEvent {
        if(it.key==Key.Escape && (drag!=null || resize!=null)) {
            if (resize != null) cancelResize() else cancel(); true
        } else false
    }) {
        val w=maxWidth.value; val h=maxHeight.value
        LaunchedEffect(w,h) { cancel(); if (resize != null) cancelResize() }
        val layout=state.layout
        val slots=EditorPanelLayouts.slots(layout,w,h)
        val currentSlots by rememberUpdatedState(slots)
        
        fun begin(slot: PanelSlot, panel: EditorPanel?) {
            if (state.layout.locked) return
            dragFocus.requestFocus()
            // Every title grip belongs to its complete group; only an explicit
            // per-tab grip may detach one leaf from that group.
            val sourceId=if (panel == null) EditorPanelLayouts.groupSourceId(state.layout,slot.node.id) ?: return else slot.node.id
            val shown=currentSlots.firstOrNull { it.node.id==sourceId }?.rect ?: slot.rect
            // A short window trims only the displayed size; dragging must not persist that trim.
            val stored=state.layout.floating.firstOrNull { it.node.id==sourceId }?.rect
            val rect=stored?.let { shown.copy(width=it.width,height=it.height) } ?: shown
            drag=PanelDrag(gestureOrigin[0] ?: state.layout,sourceId,panel,rect,gestureDown[0].x/density,gestureDown[0].y/density)
            if (panel != null) state.layout = EditorPanelLayouts.select(state.layout, panel)
        }
        fun move(point: Offset, delta: Offset) {
            val old=drag ?: return
            val dx=point.x/density-old.x; val dy=point.y/density-old.y
            val moved=old.start.moved(dx,dy)
            val source=EditorPanelLayouts.node(old.original,old.sourceId) ?: return
            val excluded=currentSlots.filter { EditorPanelLayouts.find(source,it.node.id)!=null }.mapTo(mutableSetOf()) { it.node.id }
            // Keep a multi-tab source as a valid tab-reordering target.
            if(old.panel != null && source is PanelNode.Tabs && source.panels.size>1) excluded.remove(source.id)
            var target=EditorPanelLayouts.target(currentSlots,excluded,old.x+dx,old.y+dy,moved,
                delta.x/density,delta.y/density,w,h)
            // A tab's own group takes it back only in its title row (reordering); anywhere else
            // over that group it comes loose and floats, so a short drag never splits the group with itself.
            if (target != null && target.nodeId == old.sourceId) {
                val own = currentSlots.firstOrNull { it.node.id == old.sourceId }?.rect
                if (target.edge != PanelEdge.TAB || own == null || old.y + dy > own.y + EditorPanelLayouts.titleHeight) target = null
            }
            drag=old.copy(dx=dx,dy=dy,target=target)
        }
        fun finish() {
            val d=drag ?: return
            if (!EditorPanelLayouts.intentionalDrag(d.panel,d.dx,d.dy)) { cancel(); return }
            val rect=d.start.moved(d.dx,d.dy).placedWithin(w,h)
            state.commit(EditorPanelLayouts.move(d.original,d.sourceId,d.panel,d.target,rect))
            drag=null; releaseInput()
        }
        fun geometry(rect: PanelRect)=Modifier.offset { androidx.compose.ui.unit.IntOffset((rect.x*density).toInt(),(rect.y*density).toInt()) }
            .size(rect.width.coerceAtLeast(1f).dp,rect.height.coerceAtLeast(1f).dp)
        Box(Modifier.fillMaxSize().pointerInput(w,h) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                gestureOrigin[0] = state.layout
                gestureDown[0] = down.position
                val pendingPerimeterResize = if(!state.layout.locked) state.layout.floating.asReversed().filter { it.node.id !in state.layout.minimized }.firstNotNullOfOrNull { root ->
                    val visible=EditorPanelLayouts.visible(root.node,state.layout.open) ?: return@firstNotNullOfOrNull null
                    val rect=root.rect.bounded(w,h)
                    // The title row owns move/detach/close; a held grip must never turn into a
                    // perimeter resize. Its edges still resize through the explicit handles.
                    if(!rect.contains(down.position.x/density,down.position.y/density) ||
                        down.position.y/density-rect.y < EditorPanelLayouts.titleHeight) null else
                        PanelResizing.gripAt(down.position.x/density-rect.x,down.position.y/density-rect.y,rect.width,rect.height)
                            ?.let { Triple(root.node.id,it,state.layout) }
                } else null
                var perimeterCandidate=pendingPerimeterResize
                var last=down.position
                try {
                    while(true) {
                        val event=awaitPointerEvent(PointerEventPass.Final)
                        if (event.changes.count { it.pressed } > 1 && (resize != null || drag != null)) {
                            if (resize != null) cancelResize() else cancel()
                            break
                        }
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        if(resize==null && drag==null && perimeterCandidate!=null && change.pressed) {
                            val elapsed=change.uptimeMillis-down.uptimeMillis
                            val travel=(change.position-down.position).getDistance()
                            if(elapsed>=viewConfiguration.longPressTimeoutMillis) {
                                val (rootId,grip,original)=perimeterCandidate!!
                                resize=PanelResize(original,rootId,grip,true,down.position)
                                perimeterCandidate=null
                            } else if(travel>viewConfiguration.touchSlop || change.isConsumed) perimeterCandidate=null
                        }
                        val resizing = resize
                        if (resizing != null) {
                            // Compose synthesizes an already-consumed up for ACTION_CANCEL.
                            // A normal release is unconsumed because resize handles only claim down.
                            if (!change.pressed && change.isConsumed) { cancelResize(); break }
                            val delta = (change.position - resizing.start) / density
                            state.layout = when {
                                resizing.grip == null -> PanelResizing.divider(resizing.original, resizing.id, delta.x, delta.y, w, h)
                                resizing.floating -> PanelResizing.floating(resizing.original, resizing.id, resizing.grip, delta.x, delta.y, w, h)
                                else -> PanelResizing.dock(resizing.original, resizing.id, resizing.grip, delta.x, delta.y, w, h)
                            }
                            change.consume()
                            if (!change.pressed) {
                                state.commit(state.layout); resize = null; releaseInput(); break
                            }
                        }
                        if(!change.pressed) break
                        if(drag!=null) move(change.position,change.position-last)
                        last=change.position
                    }
                } finally {
                    if (resize != null) cancelResize()
                }
            }
        }) {
        val canvasRect=slots.firstOrNull { it.node is PanelNode.Canvas }?.rect ?: PanelRect(0f,0f,w,h)
        Box(geometry(canvasRect)) { canvas() }
        slots.filter { it.node is PanelNode.Tabs && !it.collapsed }.forEach { slot ->
            val tabs=slot.node as PanelNode.Tabs
            key(tabs.id) {
                val currentSlot by rememberUpdatedState(slot)
                val d=drag
                val individual = d?.sourceId == tabs.id && d.panel != null && tabs.panels.size > 1
                val moving=!individual && d!=null && (d.sourceId==tabs.id || (d.panel==null && EditorPanelLayouts.find(EditorPanelLayouts.node(d.original,d.sourceId) ?: tabs,tabs.id)!=null))
                val rect=if(moving) slot.rect.moved(d!!.dx,d.dy) else slot.rect
                val active=if(individual && tabs.active==d?.panel) tabs.panels.first { it != d.panel } else tabs.active
                val compact = rect.width < EditorPanelLayouts.compactThreshold(active) || rect.height < 80f
                val minimizable = EditorPanelLayouts.minimizable(slot)
                val minimized = minimizable && tabs.id in layout.minimized
                Column(geometry(rect).testTag("editor-panel-${tabs.id}").zIndex(if(moving) 100f else if(slot.floating) 5f+layout.floating.indexOfFirst { it.node.id==slot.rootId } else 1f)
                    .background(Paper).border(1.dp,OutlineSoft).pointerInput(slot.rootId) {
                        awaitEachGesture { awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);state.raise(slot.rootId,tabs.id) }
                    }) {
                    Row(Modifier.fillMaxWidth().height(EditorPanelLayouts.titleHeight.dp).background(Paper),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.width(EditorPanelLayouts.groupGripWidth.dp).fillMaxHeight()
                            .semantics { contentDescription=language.text("Move panel group","移动面板组合") }
                            .pointerInput(tabs.id,slot.rootId) {
                                detectDragGestures(onDragStart={ begin(currentSlot,null) },onDragEnd={ finish() },onDragCancel={ cancel() }) { change,_ -> change.consume() }
                            },contentAlignment=androidx.compose.ui.Alignment.Center) {
                            Icon(Icons.Outlined.DragIndicator,null,Modifier.size(17.dp),tint=MutedInk)
                        }
                        Row(Modifier.weight(1f)) {
                            tabs.panels.forEachIndexed { index,panel ->
                                // CSP: tap a tab to bring it forward, drag it to move or detach that palette.
                                Box(Modifier
                                    .width(EditorPanelLayouts.tabWidth(tabs, rect.width, minimizable).dp)
                                    .fillMaxHeight().graphicsLayer { alpha=if(individual && panel==d?.panel) 0f else 1f }.background(if(panel==active) SageContainer else Color.Transparent)
                                    .semantics { contentDescription=language.text("Drag ${title(panel).lowercase()} panel","拖动${title(panel)}面板") }
                                    .then(if (layout.locked) Modifier else Modifier.pointerInput(tabs.id,panel) {
                                        detectDragGestures(onDragStart={ begin(currentSlot,panel) },onDragEnd={ finish() },onDragCancel={ cancel() }) { change,_ -> change.consume() }
                                    })
                                    .clickable { state.commit(EditorPanelLayouts.select(state.layout,panel)) }
                                    .padding(horizontal=6.dp), contentAlignment=androidx.compose.ui.Alignment.CenterStart) {
                                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                        Icon(icon(panel), language.text("Move ${title(panel).lowercase()} tab","移动${title(panel)}标签"),
                                            Modifier.size(15.dp), tint=MutedInk)
                                        Spacer(Modifier.width(4.dp))
                                        Text(title(panel),fontSize=11.sp,lineHeight=14.sp,color=Ink,maxLines=1,
                                            overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        // CSP: a palette shown by itself folds to its title bar.
                        if (minimizable) IconButton(onClick={ state.commit(EditorPanelLayouts.toggleMinimized(state.layout,tabs.id)) },
                            modifier=Modifier.size(EditorPanelLayouts.minimizeWidth.dp,30.dp)) {
                            Icon(if (minimized) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                if (minimized) language.text("Restore ${title(active).lowercase()} panel","展开${title(active)}面板")
                                else language.text("Minimize ${title(active).lowercase()} panel","最小化${title(active)}面板"),Modifier.size(18.dp),tint=Ink)
                        }
                        IconButton(onClick={ closeCurrent(tabs) },modifier=Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Close,language.text("Close ${title(active).lowercase()} panel","关闭${title(active)}面板"),Modifier.size(16.dp),tint=Ink)
                        }
                    }
                    if (minimized) Unit
                    else if(compact) {
                        Column(Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                            horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                            tabs.panels.forEach { panel ->
                                IconButton(onClick={state.commit(EditorPanelLayouts.select(state.layout,panel))},
                                    modifier=Modifier.size(46.dp).background(if(panel==active) SageContainer else Color.Transparent)
                                        .semantics { contentDescription=title(panel) }) {
                                    Icon(icon(panel),title(panel),Modifier.size(21.dp),tint=Ink)
                                }
                            }
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        bodies.getValue(active).invoke()
                    }
                }
            }
        }
        val popup = state.popup?.takeIf { it in layout.open && EditorPanelLayouts.inCollapsedDock(layout, it) }
        slots.forEach { slot -> val column = slot.column ?: return@forEach
            key("dock-${column.id}") {
                if (column.display == DockDisplay.NORMAL) column.header?.let { header ->
                    // Above the docked palettes' resize handles (≤ 1.7) so its buttons always get the tap.
                    PaletteDockHeader(column.side, language, geometry(header).zIndex(1.9f)) { state.setDockDisplay(column.id, it) }
                } else PaletteDockStrip(column.side, column.display, EditorPanelLayouts.members(slot.node), popup, language,
                    geometry(slot.rect).zIndex(if (popup != null) 297f else 1.7f), ::title, ::icon,
                    { state.setDockDisplay(column.id, it) }, { state.popup = it; it?.let { panel -> state.focus(panel) } })
            }
        }
        popup?.let { panel ->
            slots.firstOrNull { it.collapsed && panel in EditorPanelLayouts.members(it.node) }?.let { strip ->
                val column = strip.column!!
                // The pop-up has the size the palette has in the expanded dock.
                val expanded = EditorPanelLayouts.slots(EditorPanelLayouts.setDockDisplay(layout, column.id, DockDisplay.NORMAL), w, h)
                    .firstOrNull { (it.node as? PanelNode.Tabs)?.panels?.contains(panel) == true }?.rect
                val index = if (column.display == DockDisplay.ICONS) EditorPanelLayouts.members(strip.node).indexOf(panel) else -1
                val rect = PalettePopups.place(strip.rect, column.side, index, maxOf(expanded?.width ?: 300f, 260f),
                    maxOf(expanded?.height ?: 420f, 360f).coerceAtMost(h - 16f), w, h)
                key("popup") {
                    PalettePopup(panel, title(panel), icon(panel), language, geometry(rect), { state.popup = null }) { bodies.getValue(panel).invoke() }
                }
            }
        }
        drag?.let { d ->
            val source=EditorPanelLayouts.node(d.original,d.sourceId) as? PanelNode.Tabs
            val visibleSource = currentSlots.firstOrNull { it.node.id == d.sourceId }?.node as? PanelNode.Tabs
            // A hidden sibling cannot replace the dragged tab in the render tree.
            // Move the one visible instance instead of mounting a second saveable body.
            if(d.panel != null && source != null && source.panels.size > 1 && visibleSource != null && visibleSource.panels.size > 1) {
                Column(geometry(d.start.moved(d.dx,d.dy)).zIndex(100f).background(Paper).border(1.dp,OutlineSoft)) {
                    Box(Modifier.fillMaxWidth().height(32.dp).padding(horizontal=8.dp), contentAlignment=androidx.compose.ui.Alignment.CenterStart) {
                        Text(title(d.panel), fontSize=11.sp,lineHeight=14.sp,color=Ink,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { bodies.getValue(d.panel).invoke() }
                }
            }
        }
        fun resizeInput(id: String, grip: PanelGrip?, floating: Boolean) = Modifier.pointerInput(id, grip, floating) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                if (!state.layout.locked) {
                    down.consume()
                    dragFocus.requestFocus()
                    resize = PanelResize(state.layout, id, grip, floating, gestureDown[0])
                    // The stable parent owns every subsequent move and the release/cancel.
                    // Handles themselves can move, change size or become a new split mid-gesture.
                }
            }
        }
        fun cursor(grip: PanelGrip?) = PointerIcon(AndroidPointerIcon.getSystemIcon(
            context, when {
                grip == null -> AndroidPointerIcon.TYPE_ALL_SCROLL
                grip.corner && grip.x == grip.y -> AndroidPointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
                grip.corner -> AndroidPointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
                grip.x != 0 -> AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
                else -> AndroidPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
            }))
        if (drag == null && !layout.locked) {
            val roots = layout.floating.filter { it.node.id !in layout.minimized }.mapNotNull { root ->
                EditorPanelLayouts.visible(root.node, layout.open)?.let {
                    PanelSlot(it, root.rect.bounded(w,h), root.node.id, true)
                }
            } + slots.filter { !it.floating && it.node is PanelNode.Tabs && !it.collapsed }
            roots.forEach { slot -> key("resize-${slot.rootId}-${slot.node.id}") {
                val rect = slot.rect
                val resizePanel = (slot.node as? PanelNode.Tabs)?.active ?: EditorPanelLayouts.members(slot.node).first()
                PanelGrip.entries.forEach { grip -> key(grip) {
                    val r = PanelResizing.handle(rect, grip, w, h)
                    val edgeLabel = language.text(grip.name.lowercase().replace('_', '-') + if (grip.corner) " corner" else " edge",
                        when (grip) {
                            PanelGrip.LEFT -> "左边缘"; PanelGrip.RIGHT -> "右边缘"; PanelGrip.TOP -> "上边缘"; PanelGrip.BOTTOM -> "下边缘"
                            PanelGrip.TOP_LEFT -> "左上角"; PanelGrip.TOP_RIGHT -> "右上角"; PanelGrip.BOTTOM_LEFT -> "左下角"; PanelGrip.BOTTOM_RIGHT -> "右下角"
                        })
                    Box(geometry(r).zIndex((if (slot.floating) 5.5f + layout.floating.indexOfFirst { it.node.id == slot.rootId } else 1.5f) + if (grip.corner) .2f else 0f)
                        .semantics { contentDescription = language.text("Resize ${title(resizePanel).lowercase()} panel · $edgeLabel", "调整${title(resizePanel)}面板大小 · $edgeLabel") }
                        .pointerHoverIcon(cursor(grip)).then(resizeInput(if (slot.floating) slot.rootId else slot.node.id, grip, slot.floating))
                        .drawBehind {
                            if (grip.corner && slot.floating) {
                                val center = Offset(size.width / 2, size.height / 2)
                                drawLine(OutlineSoft, center + Offset(-3.dp.toPx(), 3.dp.toPx()), center + Offset(3.dp.toPx(), -3.dp.toPx()), 1.dp.toPx())
                            }
                        })
                } }
            } }
            // An icon or hidden dock keeps its strip's fixed width.
            slots.filter { slot -> slot.node is PanelNode.Split && !slot.collapsed && (slot.node as PanelNode.Split).let { split ->
                slots.none { it.collapsed && (it.node.id == split.first.id || it.node.id == split.second.id) } } }.forEach { slot -> key("divider-${slot.node.id}") {
                val split = slot.node as PanelNode.Split
                val r = slot.rect
                val firstRect = slots.first { it.node.id == split.first.id }.rect
                val divider = if (split.horizontal) PanelRect(firstRect.right - 7f, r.y + 24f, 18f, (r.height - 48f).coerceAtLeast(1f))
                    else PanelRect(r.x + 24f, firstRect.bottom - 7f, (r.width - 48f).coerceAtLeast(1f), 18f)
                Box(geometry(divider).zIndex(if (slot.floating) 5.6f + layout.floating.indexOfFirst { it.node.id == slot.rootId } else 1.6f)
                    .semantics { contentDescription=language.text("Resize panel divider", "调整面板分隔线") }
                    .pointerHoverIcon(cursor(if (split.horizontal) PanelGrip.RIGHT else PanelGrip.BOTTOM))
                    .then(resizeInput(split.id, null, slot.floating))
                    .drawBehind {
                        if (split.horizontal) drawLine(OutlineSoft, Offset(size.width/2, 0f), Offset(size.width/2, size.height), 1.dp.toPx())
                        else drawLine(OutlineSoft, Offset(0f, size.height/2), Offset(size.width, size.height/2), 1.dp.toPx())
                    })
            } }
        }
        drag?.target?.let { target ->
            slots.firstOrNull { it.node.id==target.nodeId }?.let { slot ->
                // CSP's red indicators: the whole palette for stacking, a red band where the panel docks.
                val r=slot.rect
                val canvasSide = slot.node is PanelNode.Canvas
                val reach = if (canvasSide) minOf(drag?.start?.width ?: 280f, r.width * .4f) else r.width / 2f
                val tall = if (canvasSide) minOf(drag?.start?.height ?: 300f, r.height * .4f) else r.height / 2f
                val area=when(target.edge) {
                    PanelEdge.LEFT->PanelRect(r.x,r.y,reach,r.height)
                    PanelEdge.RIGHT->PanelRect(r.right-reach,r.y,reach,r.height)
                    PanelEdge.TOP->PanelRect(r.x,r.y,r.width,tall)
                    PanelEdge.BOTTOM->PanelRect(r.x,r.bottom-tall,r.width,tall)
                    PanelEdge.TAB->r
                }
                val line=when(target.edge) {
                    PanelEdge.LEFT->PanelRect(r.x,r.y,4f,r.height)
                    PanelEdge.RIGHT->PanelRect(r.right-4,r.y,4f,r.height)
                    PanelEdge.TOP->PanelRect(r.x,r.y,r.width,4f)
                    PanelEdge.BOTTOM->PanelRect(r.x,r.bottom-4,r.width,4f)
                    PanelEdge.TAB->PanelRect(EditorPanelLayouts.tabInsertionX(slot.node as PanelNode.Tabs,
                        target.tabIndex,r.x,r.width,EditorPanelLayouts.minimizable(slot)).coerceIn(r.x, (r.right-3).coerceAtLeast(r.x)),r.y,3f,EditorPanelLayouts.titleHeight)
                }
                Box(geometry(area).zIndex(199f).background(DropIndicator.copy(alpha=.22f)).border(2.dp, DropIndicator))
                Box(geometry(line).zIndex(200f).background(DropIndicator).semantics { contentDescription=language.text("Release to place panel","松开以放置面板") })
            }
        }
    }
    }
}

/** CSP marks where a dragged palette will land in red. */
private val DropIndicator = Color(0xFFE5483B)

/** Panel bodies omit their former window chrome when rendered in the shared workspace host. */
internal val LocalEditorPanelHosted=staticCompositionLocalOf { false }
