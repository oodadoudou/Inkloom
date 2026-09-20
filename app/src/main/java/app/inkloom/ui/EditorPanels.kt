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
    fun colors(open: Boolean) {
        val colors=EditorPanel.entries.filter { it.ordinal >= EditorPanel.WHEEL.ordinal }.toSet()
        if(!open) commit(layout.copy(open=layout.open-colors))
        else if(layout.open.intersect(colors).isEmpty()) commit(layout.copy(open=layout.open+colors))
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
    fun closeCurrent(tabs: PanelNode.Tabs) {
        state.show(tabs.active,false)
        currentClose(tabs.active); releaseInput()
    }
    BackHandler(backDismissEnabled && drag == null && state.layout.open.isNotEmpty()) {
        if (!onBackBeforePanels()) {
            if (state.closeTopPanel(currentClose)) releaseInput()
        }
    }
    BackHandler(drag != null) { cancel() }
    DisposableEffect(Unit) { onDispose { drag=null } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Workspace).focusRequester(dragFocus).focusable().onPreviewKeyEvent {
        if(it.key==Key.Escape && drag!=null) { cancel(); true } else false
    }) {
        val w=maxWidth.value; val h=maxHeight.value
        LaunchedEffect(w,h) { cancel() }
        val layout=state.layout
        val slots=EditorPanelLayouts.slots(layout,w,h)
        val currentSlots by rememberUpdatedState(slots)
        
        fun begin(slot: PanelSlot, panel: EditorPanel?, pointer: Offset) {
            dragFocus.requestFocus()
            val sourceId=if(panel==null && slot.floating) slot.rootId else slot.node.id
            val rect=currentSlots.firstOrNull { it.node.id==sourceId }?.rect ?: slot.rect
            drag=PanelDrag(gestureOrigin[0] ?: state.layout,sourceId,panel,rect,slot.rect.x+pointer.x/density,slot.rect.y+pointer.y/density)
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
            val target=EditorPanelLayouts.target(currentSlots,excluded,old.x+dx,old.y+dy,moved,
                delta.x/density,delta.y/density,w,h)
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
                var last=down.position
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Final)
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    if(!change.pressed) break
                    if(drag!=null) move(change.position,change.position-last)
                    last=change.position
                }
            }
        }) {
        val canvasRect=slots.firstOrNull { it.node is PanelNode.Canvas }?.rect ?: PanelRect(0f,0f,w,h)
        Box(geometry(canvasRect)) { canvas() }
        slots.filter { it.node is PanelNode.Tabs }.forEach { slot ->
            val tabs=slot.node as PanelNode.Tabs
            key(tabs.id) {
                val currentSlot by rememberUpdatedState(slot)
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
                                detectDragGestures(onDragStart={ begin(currentSlot,null,it) },onDragEnd={ finish() },onDragCancel={ cancel() }) { change,delta->change.consume() }
                            }, tint=MutedInk)
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                            tabs.panels.forEachIndexed { index,panel ->
                                Text(title(panel),fontSize=11.sp,color=Ink,modifier=Modifier
                                    .width(if(tabs.panels.size==1) (rect.width-60f).coerceAtLeast(55f).dp else EditorPanelLayouts.tabWidth(panel).dp)
                                    .fillMaxHeight().graphicsLayer { alpha=if(individual && panel==d?.panel) 0f else 1f }.background(if(panel==active) SageContainer else Color.Transparent)
                                    .semantics { contentDescription=language.text("Drag ${title(panel).lowercase()} panel","拖动${title(panel)}面板") }
                                    .clickable { state.commit(EditorPanelLayouts.select(state.layout,panel)) }
                                    .pointerInput(tabs.id,panel) {
                                        detectDragGestures(onDragStart={ begin(currentSlot,panel,it+Offset(EditorPanelLayouts.tabInsertionX(tabs,index,0f,rect.width)*density,0f)) },onDragEnd={ finish() },onDragCancel={ cancel() }) { change,delta->change.consume() }
                                    }.padding(horizontal=7.dp,vertical=8.dp))
                            }
                        }
                        IconButton(onClick={ closeCurrent(tabs) },modifier=Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Close,language.text("Close ${title(active).lowercase()} panel","关闭${title(active)}面板"),Modifier.size(16.dp),tint=Ink)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        bodies.getValue(active).invoke()
                    }
                }
            }
        }
        drag?.let { d ->
            val source=EditorPanelLayouts.node(d.original,d.sourceId) as? PanelNode.Tabs
            if(d.panel != null && source != null && source.panels.size > 1) {
                Column(geometry(d.start.moved(d.dx,d.dy)).zIndex(100f).background(Paper).border(1.dp,OutlineSoft)) {
                    Text(title(d.panel), fontSize=11.sp, color=Ink, modifier=Modifier.fillMaxWidth().height(32.dp).padding(8.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) { bodies.getValue(d.panel).invoke() }
                }
            }
        }
        if (drag == null) layout.floating.forEach { root ->
            val visible = EditorPanelLayouts.visible(root.node, layout.open)
            if (visible != null) {
                val rect = root.rect.bounded(w,h)
                val resizePanel = EditorPanelLayouts.members(visible).first()
                        listOf(PanelEdge.LEFT,PanelEdge.RIGHT,PanelEdge.TOP,PanelEdge.BOTTOM).forEach { edge ->
                            val r=when(edge) {
                                PanelEdge.LEFT->PanelRect(rect.x-3,rect.y+32,6f,(rect.height-32).coerceAtLeast(1f))
                                PanelEdge.RIGHT->PanelRect(rect.right-3,rect.y+32,6f,(rect.height-32).coerceAtLeast(1f))
                                PanelEdge.TOP->PanelRect(rect.x+8,rect.y-3,(rect.width-16).coerceAtLeast(1f),6f)
                                else->PanelRect(rect.x+8,rect.bottom-3,(rect.width-16).coerceAtLeast(1f),6f)
                            }
                            Box(geometry(r).zIndex(5.5f + layout.floating.indexOf(root)).semantics { contentDescription=language.text("Resize ${title(resizePanel).lowercase()} panel","调整${title(resizePanel)}面板大小") }.pointerInput(root.node.id,edge,w,h) {
                                var original:EditorPanelLayout?=null
                                detectDragGestures(onDragStart={ original=state.layout },onDragEnd={ state.commit(state.layout) },onDragCancel={ original?.let { state.layout=it } }) { change,delta->
                                    change.consume()
                                    val current=state.layout.floating.first { it.node.id==root.node.id }
                                    val b=current.rect.bounded(w,h); val dx=delta.x/density; val dy=delta.y/density
                                    val minWidth=EditorPanelLayouts.minimumWidth(root.node).coerceAtMost(w)
                                    val minHeight=EditorPanelLayouts.minimumHeight(root.node).coerceAtMost(h)
                                    val next=when(edge) {
                                        PanelEdge.LEFT->b.copy(x=(b.x+dx).coerceAtMost(b.right-minWidth),width=(b.width-dx).coerceAtLeast(minWidth))
                                        PanelEdge.RIGHT->b.copy(width=(b.width+dx).coerceAtLeast(minWidth))
                                        PanelEdge.TOP->b.copy(y=(b.y+dy).coerceAtMost(b.bottom-minHeight),height=(b.height-dy).coerceAtLeast(minHeight))
                                        else->b.copy(height=(b.height+dy).coerceAtLeast(minHeight))
                                    }.bounded(w,h)
                                    state.layout=state.layout.copy(floating=state.layout.floating.map { if(it.node.id==root.node.id) it.copy(rect=next) else it })
                                }
                            })
                        }
            }
        }
        slots.filter { it.node is PanelNode.Split }.forEach { slot ->
            val split=slot.node as PanelNode.Split
            val r=slot.rect
            val firstRect=slots.first { it.node.id==split.first.id }.rect
            val divider=if(split.horizontal) PanelRect(firstRect.right-1,r.y,6f,r.height)
                else PanelRect(r.x,firstRect.bottom-1,r.width,6f)
            Box(geometry(divider).zIndex(if(slot.floating) 5.4f + layout.floating.indexOfFirst { it.node.id == slot.rootId } else 1.4f).background(OutlineSoft).semantics { contentDescription=language.text("Resize panel divider","调整面板分隔线") }.pointerInput(split.id,w,h) {
                var original:EditorPanelLayout?=null
                detectDragGestures(onDragStart={ original=state.layout },onDragEnd={state.commit(state.layout)},onDragCancel={original?.let {state.layout=it}}) { change,delta->
                    change.consume()
                    state.layout=EditorPanelLayouts.update(state.layout,split.id) { n ->
                        if(n is PanelNode.Split) n.copy(fraction=(n.fraction+(if(n.horizontal) delta.x/density/r.width else delta.y/density/r.height)).coerceIn(.15f,.85f)) else n
                    }
                }
            })
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
                        target.tabIndex,r.x,r.width).coerceAtMost(r.right-2),r.y,2f,32f)
                }
                Box(geometry(preview).zIndex(200f).background(Ink).semantics { contentDescription=language.text("Release to place panel","松开以放置面板") })
            }
        }
    }
    }
}

/** Panel bodies omit their former window chrome when rendered in the shared workspace host. */
internal val LocalEditorPanelHosted=staticCompositionLocalOf { false }
