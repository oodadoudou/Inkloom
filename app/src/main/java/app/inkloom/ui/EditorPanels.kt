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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.inkloom.settings.*
import app.inkloom.ui.theme.*
import kotlinx.serialization.json.Json

internal class EditorPanelState(context: Context, settings: AppSettings) {
    private val preferences = context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    var layout by mutableStateOf(runCatching {
        val raw = preferences.getString("layout", null) ?: error("New layout")
        require(raw.length < 64*1024)
        val decoded = json.decodeFromString<EditorPanelLayout>(raw)
        require(EditorPanelLayouts.isValid(decoded))
        decoded
    }.getOrElse {
        EditorPanelLayout().let { defaults -> defaults.copy(floating = defaults.floating.map { root ->
            val rect = when(root.node.id) {
                "pages" -> PanelRect(settings.pagePanelXDp,settings.pagePanelYDp,settings.pagePanelWidthDp,settings.pagePanelHeightDp)
                "brushes" -> PanelRect(settings.brushPanelXDp,settings.brushPanelYDp,settings.brushPanelWidthDp,settings.brushPanelHeightDp)
                "layers" -> PanelRect(settings.layerPanelXDp,settings.layerPanelYDp,settings.layerPanelWidthDp,settings.layerPanelHeightDp)
                else -> root.rect
            }
            root.copy(rect=rect)
        }).copy(open = buildSet {
            if (settings.showPageRail) add(EditorPanel.PAGES)
            if (settings.showBrushPanel) add(EditorPanel.BRUSHES)
            if (settings.showLayerPanel) add(EditorPanel.LAYERS)
        }) }
    })
    var namedLayouts by mutableStateOf(runCatching {
        val raw = preferences.getString("named_layouts", null) ?: return@runCatching NamedPanelLayouts()
        require(raw.length < 1024*1024)
        val decoded = json.decodeFromString<NamedPanelLayouts>(raw)
        decoded.copy(entries = decoded.entries.filter { EditorPanelLayouts.isValid(it.layout) }.takeLast(20))
    }.getOrDefault(NamedPanelLayouts()))
        private set

    private fun updateNamedLayouts(next: NamedPanelLayouts) {
        if (next == namedLayouts) return
        namedLayouts = next
        preferences.edit().putString("named_layouts", json.encodeToString(NamedPanelLayouts.serializer(), next)).apply()
    }

    fun backup() = PanelWorkspaceBackup(layout, namedLayouts)
    fun restoreBackup(backup: PanelWorkspaceBackup) {
        backup.validate()
        commit(backup.layout)
        updateNamedLayouts(backup.namedLayouts)
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
        layout=next
        preferences.edit().putString("layout",json.encodeToString(EditorPanelLayout.serializer(),next)).apply()
    }
    var activeTabId: String? = null
    fun raise(rootId: String, tabId: String) {
        activeTabId = tabId
        val root = layout.floating.firstOrNull { it.node.id == rootId } ?: return
        if (layout.floating.lastOrNull() != root) commit(layout.copy(floating = layout.floating - root + root))
    }
    fun show(panel: EditorPanel, open: Boolean) {
        val next = if(open) layout.open+panel else layout.open-panel
        if(next != layout.open) commit(layout.copy(open=next))
    }
    private fun owner(panel: EditorPanel): PanelNode.Tabs? {
        fun find(node: PanelNode): PanelNode.Tabs? = when (node) {
            is PanelNode.Tabs -> node.takeIf { panel in it.panels }
            is PanelNode.Split -> find(node.first) ?: find(node.second)
            is PanelNode.Canvas, is PanelNode.Space -> null
        }
        return find(layout.dock) ?: layout.floating.firstNotNullOfOrNull { find(it.node) }
    }
    fun isActive(panel: EditorPanel): Boolean = panel in layout.open && owner(panel)?.let {
        it.active == panel && it.id == activeTabId
    } == true
    fun focus(panel: EditorPanel) {
        if (panel !in layout.open) return
        val tabId = owner(panel)?.id ?: return
        activeTabId = tabId
        val selected = EditorPanelLayouts.select(layout, panel)
        val root = selected.floating.firstOrNull { EditorPanelLayouts.find(it.node, tabId) != null }
        commit(if (root == null || selected.floating.lastOrNull() == root) selected
            else selected.copy(floating = selected.floating - root + root))
    }
    fun colors(open: Boolean) {
        val colors=EditorPanel.entries.filter { it.ordinal >= EditorPanel.WHEEL.ordinal }.toSet()
        if(!open) commit(layout.copy(open=layout.open-colors))
        else {
            fun activeColor(node: PanelNode): EditorPanel? = when (node) {
                is PanelNode.Tabs -> node.active.takeIf { it in colors }
                is PanelNode.Split -> activeColor(node.first) ?: activeColor(node.second)
                is PanelNode.Canvas, is PanelNode.Space -> null
            }
            val last = layout.floating.asReversed().firstNotNullOfOrNull { activeColor(it.node) }
                ?: activeColor(layout.dock) ?: EditorPanel.WHEEL
            val nextOpen = if (layout.open.intersect(colors).isEmpty()) layout.open + colors else layout.open + last
            if (nextOpen != layout.open) commit(layout.copy(open = nextOpen))
            focus(last)
        }
    }
    fun closeTopPanel(onClose: (EditorPanel) -> Unit): Boolean {
        val slots = EditorPanelLayouts.slots(layout, 1000f, 800f)
        val tabs = (slots.firstOrNull { it.node.id == activeTabId && it.node is PanelNode.Tabs }
            ?: slots.lastOrNull { it.node is PanelNode.Tabs })?.node as? PanelNode.Tabs ?: return false
        show(tabs.active, false)
        onClose(tabs.active)
        return true
    }
}

@Composable
internal fun rememberEditorPanelState(settings: AppSettings): EditorPanelState {
    val context=LocalContext.current.applicationContext
    return remember(context) { EditorPanelState(context,settings) }
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
    fun title(panel: EditorPanel)=language.text(when(panel) {
        EditorPanel.PAGES->"Pages"; EditorPanel.BRUSHES->"Brushes"; EditorPanel.LAYERS->"Layers"
        EditorPanel.SUBTOOLS->"Sub tools"; EditorPanel.PROPERTIES->"Tool property"
        EditorPanel.WHEEL->"Wheel"; EditorPanel.PALETTES->"Palettes"; EditorPanel.MIX->"Mix"; EditorPanel.NEARBY->"Nearby"
    },when(panel) {
        EditorPanel.PAGES->"页面"; EditorPanel.BRUSHES->"笔刷"; EditorPanel.LAYERS->"图层"
        EditorPanel.SUBTOOLS->"子工具"; EditorPanel.PROPERTIES->"工具属性"
        EditorPanel.WHEEL->"色环"; EditorPanel.PALETTES->"色板"; EditorPanel.MIX->"混色"; EditorPanel.NEARBY->"近似色"
    })
    var drag by remember { mutableStateOf<PanelDrag?>(null) }
    var resize by remember { mutableStateOf<PanelResize?>(null) }
    val gestureDown = remember { arrayOf(Offset.Zero) }
    val tabScrolls = remember { mutableMapOf<String, androidx.compose.foundation.ScrollState>() }
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
            val sourceId=if(panel==null && slot.floating) slot.rootId else slot.node.id
            val rect=currentSlots.firstOrNull { it.node.id==sourceId }?.rect ?: slot.rect
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
            if (target?.edge == PanelEdge.TAB) {
                val targetSlot = currentSlots.firstOrNull { it.node.id == target?.nodeId }
                val tabs = targetSlot?.node as? PanelNode.Tabs
                if (targetSlot != null && tabs != null) {
                    val scroll = tabScrolls[tabs.id]
                    val px = old.x + dx
                    if (scroll != null) {
                        if (px < targetSlot.rect.x + 42f) scroll.dispatchRawDelta(-8f * density)
                        else if (px > targetSlot.rect.right - 46f) scroll.dispatchRawDelta(8f * density)
                    }
                    target = target?.copy(tabIndex = EditorPanelLayouts.tabInsertionIndex(tabs,
                        px + (scroll?.value ?: 0) / density, targetSlot.rect.x, targetSlot.rect.width))
                }
            }
            drag=old.copy(dx=dx,dy=dy,target=target)
        }
        fun finish() {
            val d=drag ?: return
            val rect=d.start.moved(d.dx,d.dy).bounded(w,h)
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
                var last=down.position
                try {
                    while(true) {
                        val event=awaitPointerEvent(PointerEventPass.Final)
                        if (event.changes.count { it.pressed } > 1 && resize != null) {
                            cancelResize(); break
                        }
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
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
        slots.filter { it.node is PanelNode.Tabs }.forEach { slot ->
            val tabs=slot.node as PanelNode.Tabs
            key(tabs.id) {
                val currentSlot by rememberUpdatedState(slot)
                val tabScroll = rememberScrollState()
                SideEffect { tabScrolls[tabs.id] = tabScroll }
                DisposableEffect(tabs.id) { onDispose { tabScrolls.remove(tabs.id) } }
                val d=drag
                val individual = d?.sourceId == tabs.id && d.panel != null && tabs.panels.size > 1
                val moving=!individual && d!=null && (d.sourceId==tabs.id || (d.panel==null && EditorPanelLayouts.find(EditorPanelLayouts.node(d.original,d.sourceId) ?: tabs,tabs.id)!=null))
                val rect=if(moving) slot.rect.moved(d!!.dx,d.dy) else slot.rect
                val active=if(individual && tabs.active==d?.panel) tabs.panels.first { it != d.panel } else tabs.active
                Column(geometry(rect).zIndex(if(moving) 100f else if(slot.floating) 5f+layout.floating.indexOfFirst { it.node.id==slot.rootId } else 1f)
                    .background(Paper).border(1.dp,OutlineSoft).pointerInput(slot.rootId) {
                        awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); state.raise(slot.rootId,tabs.id) }
                    }) {
                    Row(Modifier.fillMaxWidth().height(32.dp).background(Paper),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DragIndicator,language.text("Move panel group","移动面板组合"),
                            Modifier.size(22.dp).pointerInput(tabs.id,slot.rootId) {
                                detectDragGestures(onDragStart={ begin(currentSlot,null) },onDragEnd={ finish() },onDragCancel={ cancel() }) { change,delta->change.consume() }
                            }, tint=MutedInk)
                        Row(Modifier.weight(1f).horizontalScroll(tabScroll)) {
                            tabs.panels.forEachIndexed { index,panel ->
                                Text(title(panel),fontSize=11.sp,color=Ink,modifier=Modifier
                                    .width(if(tabs.panels.size==1) (rect.width-60f).coerceAtLeast(55f).dp else EditorPanelLayouts.tabWidth(panel).dp)
                                    .fillMaxHeight().graphicsLayer { alpha=if(individual && panel==d?.panel) 0f else 1f }.background(if(panel==active) SageContainer else Color.Transparent)
                                    .semantics { contentDescription=language.text("Drag ${title(panel).lowercase()} panel","拖动${title(panel)}面板") }
                                    .clickable { state.commit(EditorPanelLayouts.select(state.layout,panel)) }
                                    .pointerInput(tabs.id,panel) {
                                        detectDragGestures(onDragStart={ begin(currentSlot,panel) },onDragEnd={ finish() },onDragCancel={ cancel() }) { change,delta->change.consume() }
                                    }.padding(horizontal=7.dp,vertical=8.dp))
                            }
                        }
                        IconButton(onClick={ closeCurrent(tabs) },modifier=Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Close,language.text("Close ${title(active).lowercase()} panel","关闭${title(active)}面板"),Modifier.size(16.dp),tint=Ink)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        bodies.getValue(active).invoke()
                    }
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
                    Text(title(d.panel), fontSize=11.sp, color=Ink, modifier=Modifier.fillMaxWidth().height(32.dp).padding(8.dp))
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
            val roots = layout.floating.mapNotNull { root ->
                EditorPanelLayouts.visible(root.node, layout.open)?.let {
                    PanelSlot(it, root.rect.bounded(w,h), root.node.id, true)
                }
            } + slots.filter { !it.floating && it.node is PanelNode.Tabs }
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
            slots.filter { it.node is PanelNode.Split }.forEach { slot -> key("divider-${slot.node.id}") {
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
                val r=slot.rect
                val preview=when(target.edge) {
                    PanelEdge.LEFT->PanelRect(r.x,r.y,2f,r.height)
                    PanelEdge.RIGHT->PanelRect(r.right-2,r.y,2f,r.height)
                    PanelEdge.TOP->PanelRect(r.x,r.y,r.width,2f)
                    PanelEdge.BOTTOM->PanelRect(r.x,r.bottom-2,r.width,2f)
                    PanelEdge.TAB->PanelRect(EditorPanelLayouts.tabInsertionX(slot.node as PanelNode.Tabs,
                        target.tabIndex,r.x,r.width).minus((tabScrolls[target.nodeId]?.value ?: 0) / density).coerceIn(r.x, (r.right-2).coerceAtLeast(r.x)),r.y,2f,32f)
                }
                Box(geometry(preview).zIndex(200f).background(Ink).semantics { contentDescription=language.text("Release to place panel","松开以放置面板") })
            }
        }
    }
    }
}

/** Panel bodies omit their former window chrome when rendered in the shared workspace host. */
internal val LocalEditorPanelHosted=staticCompositionLocalOf { false }
