package app.inkloom.settings

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class EditorPanel { PAGES, BRUSHES, LAYERS, WHEEL, PALETTES, MIX, NEARBY }

@Serializable
data class PanelRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    fun contains(px: Float, py: Float) = px in x..right && py in y..bottom
    fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
    fun bounded(w: Float, h: Float): PanelRect {
        val width = width.coerceIn(minOf(160f, w), w.coerceAtLeast(1f))
        val minHeight = minOf(120f, h.coerceAtLeast(1f))
        val top = y.coerceIn(0f, (h-minHeight).coerceAtLeast(0f))
        val height = height.coerceIn(minHeight, (h-top).coerceAtLeast(minHeight))
        return PanelRect(x.coerceIn(0f, (w-width).coerceAtLeast(0f)), top, width, height)
    }
}

@Serializable
sealed interface PanelNode {
    val id: String
    @Serializable data class Canvas(override val id: String = "canvas") : PanelNode
    @Serializable data class Tabs(override val id: String, val panels: List<EditorPanel>, val active: EditorPanel = panels.first()) : PanelNode
    @Serializable data class Split(override val id: String, val horizontal: Boolean, val fraction: Float,
        val first: PanelNode, val second: PanelNode) : PanelNode
}

@Serializable
data class FloatingPanels(val node: PanelNode, val rect: PanelRect)

@Serializable
data class EditorPanelLayout(
    val version: Int = 1,
    val dock: PanelNode = PanelNode.Canvas(),
    val floating: List<FloatingPanels> = listOf(
        FloatingPanels(PanelNode.Tabs("pages", listOf(EditorPanel.PAGES)), PanelRect(12f, 12f, 180f, 560f)),
        FloatingPanels(PanelNode.Tabs("brushes", listOf(EditorPanel.BRUSHES)), PanelRect(720f, 12f, 400f, 600f)),
        FloatingPanels(PanelNode.Tabs("layers", listOf(EditorPanel.LAYERS)), PanelRect(800f, 80f, 310f, 500f)),
        FloatingPanels(PanelNode.Tabs("colors", listOf(EditorPanel.WHEEL, EditorPanel.PALETTES, EditorPanel.MIX, EditorPanel.NEARBY)), PanelRect(850f, 12f, 280f, 340f)),
    ),
    val open: Set<EditorPanel> = emptySet(),
    val placed: Set<EditorPanel> = emptySet(),
    val nextId: Int = 1,
)

enum class PanelEdge { LEFT, RIGHT, TOP, BOTTOM, TAB }
data class PanelDropTarget(val nodeId: String, val edge: PanelEdge, val tabIndex: Int = 0)
data class PanelSlot(val node: PanelNode, val rect: PanelRect, val rootId: String, val floating: Boolean)

/** Small immutable layout transactions: a panel has one owner, preview never edits the document. */
object EditorPanelLayouts {
    fun isValid(layout: EditorPanelLayout): Boolean {
        val panels = members(layout.dock) + layout.floating.flatMap { members(it.node) }
        return layout.version == 1 && containsCanvas(layout.dock) &&
            layout.floating.none { containsCanvas(it.node) } &&
            panels.size == EditorPanel.entries.size && panels.toSet().size == panels.size &&
            layout.open.all { it in panels } && layout.placed.all { it in panels }
    }
    fun members(node: PanelNode): List<EditorPanel> = when (node) {
        is PanelNode.Canvas -> emptyList()
        is PanelNode.Tabs -> node.panels
        is PanelNode.Split -> members(node.first) + members(node.second)
    }
    fun find(node: PanelNode, id: String): PanelNode? = when {
        node.id == id -> node
        node is PanelNode.Split -> find(node.first, id) ?: find(node.second, id)
        else -> null
    }
    fun node(layout: EditorPanelLayout, id: String): PanelNode? = find(layout.dock,id)
        ?: layout.floating.firstNotNullOfOrNull { find(it.node,id) }
    fun replace(node: PanelNode, id: String, replacement: (PanelNode) -> PanelNode?): PanelNode? {
        if (node.id == id) return replacement(node)
        if (node !is PanelNode.Split) return node
        val first = replace(node.first, id, replacement)
        val second = replace(node.second, id, replacement)
        return when { first == null -> second; second == null -> first; else -> node.copy(first = first, second = second) }
    }
    fun update(layout: EditorPanelLayout, id: String, replacement: (PanelNode) -> PanelNode?): EditorPanelLayout = layout.copy(
        dock = replace(layout.dock, id, replacement) ?: PanelNode.Canvas(),
        floating = layout.floating.mapNotNull { root -> replace(root.node,id,replacement)?.let { root.copy(node = it) } },
    )
    fun select(layout: EditorPanelLayout, panel: EditorPanel): EditorPanelLayout {
        fun selected(node: PanelNode): PanelNode = when(node) {
            is PanelNode.Tabs -> if(panel in node.panels) node.copy(active = panel) else node
            is PanelNode.Split -> node.copy(first = selected(node.first), second = selected(node.second))
            else -> node
        }
        return layout.copy(dock = selected(layout.dock), floating = layout.floating.map { it.copy(node = selected(it.node)) })
    }
    fun visible(node: PanelNode, open: Set<EditorPanel>): PanelNode? = when(node) {
        is PanelNode.Canvas -> node
        is PanelNode.Tabs -> node.panels.filter { it in open }.takeIf { it.isNotEmpty() }?.let {
            node.copy(panels = it, active = node.active.takeIf(it::contains) ?: it.first())
        }
        is PanelNode.Split -> {
            val a = visible(node.first,open); val b = visible(node.second,open)
            when { a == null -> b; b == null -> a; else -> node.copy(first=a, second=b) }
        }
    }
    fun minimumWidth(node: PanelNode): Float = when(node) {
        is PanelNode.Canvas -> 200f
        is PanelNode.Tabs -> node.panels.maxOf { when(it) { EditorPanel.PAGES -> 160f; EditorPanel.BRUSHES -> 320f; else -> 240f } }
        is PanelNode.Split -> if(node.horizontal) minimumWidth(node.first)+minimumWidth(node.second)+4f else maxOf(minimumWidth(node.first),minimumWidth(node.second))
    }
    fun minimumHeight(node: PanelNode): Float = when(node) {
        is PanelNode.Canvas -> 160f
        is PanelNode.Tabs -> 160f
        is PanelNode.Split -> if(!node.horizontal) minimumHeight(node.first)+minimumHeight(node.second)+4f else maxOf(minimumHeight(node.first),minimumHeight(node.second))
    }
    private fun cut(total: Float, fraction: Float, first: Float, second: Float): Float =
        if (total >= first+second+4f) (total*fraction).coerceIn(first+2f,total-second-2f)
        else total*first/(first+second)
    fun slots(layout: EditorPanelLayout, width: Float, height: Float): List<PanelSlot> = buildList {
        fun place(node: PanelNode, rect: PanelRect, rootId: String, floating: Boolean) {
            add(PanelSlot(node,rect,rootId,floating))
            if(node is PanelNode.Split) {
                val f = node.fraction.coerceIn(.15f,.85f)
                if(node.horizontal) {
                    val cut = cut(rect.width,f,minimumWidth(node.first),minimumWidth(node.second))
                    place(node.first,rect.copy(width=cut-2f),rootId,floating)
                    place(node.second,PanelRect(rect.x+cut+2f,rect.y,rect.width-cut-2f,rect.height),rootId,floating)
                } else {
                    val cut=cut(rect.height,f,minimumHeight(node.first),minimumHeight(node.second))
                    place(node.first,rect.copy(height=cut-2f),rootId,floating)
                    place(node.second,PanelRect(rect.x,rect.y+cut+2f,rect.width,rect.height-cut-2f),rootId,floating)
                }
            }
        }
        visible(layout.dock,layout.open)?.let { place(it,PanelRect(0f,0f,width,height),layout.dock.id,false) }
        layout.floating.forEach { root -> visible(root.node,layout.open)?.let { place(it,root.rect.bounded(width,height),root.node.id,true) } }
    }

    /** Null target means float. Empty source tabs/splits collapse, other groups remain untouched. */
    fun move(layout: EditorPanelLayout, sourceId: String, panel: EditorPanel?, target: PanelDropTarget?, floatingRect: PanelRect): EditorPanelLayout {
        val source = node(layout,sourceId) ?: return layout
        if(panel != null && (source !is PanelNode.Tabs || panel !in source.panels)) return layout
        val payload = if(panel == null) source else PanelNode.Tabs(if(source is PanelNode.Tabs && source.panels.size == 1) source.id else "moved-${layout.nextId}",listOf(panel))
        if(members(payload).isEmpty() || containsCanvas(payload)) return layout
        if (target?.edge == PanelEdge.TAB && node(layout, target.nodeId) !is PanelNode.Tabs) return layout
        if(target?.nodeId == sourceId && panel == null) return layout
        if(target?.nodeId == sourceId && target.edge == PanelEdge.TAB && source is PanelNode.Tabs && panel != null) {
            val remaining = source.panels - panel
            val insertion = target.tabIndex.coerceIn(0,remaining.size)
            return update(layout,sourceId) { source.copy(panels = remaining.toMutableList().apply { add(insertion,panel) },active=panel) }
        }
        // Do not insert a group into itself or its descendants.
        if(target != null && panel == null && find(source,target.nodeId) != null) return layout
        var result = update(layout,sourceId) {
            if(panel == null) null else if(it is PanelNode.Tabs) (it.panels-panel).takeIf(List<EditorPanel>::isNotEmpty)?.let { remaining ->
                it.copy(panels=remaining,active=it.active.takeIf(remaining::contains) ?: remaining.first())
            } else it
        }
        val destination = target?.let { node(result,it.nodeId) }
        if(destination == null || target == null) {
            result = result.copy(floating=result.floating+FloatingPanels(payload,floatingRect))
        } else if(target.edge == PanelEdge.TAB && destination is PanelNode.Tabs && payload is PanelNode.Tabs) {
            result=update(result,destination.id) {
                val panels=destination.panels.toMutableList().apply { addAll(target.tabIndex.coerceIn(0,size),payload.panels) }.distinct()
                destination.copy(panels=panels,active=payload.active)
            }
        } else {
            val horizontal=target.edge in setOf(PanelEdge.LEFT,PanelEdge.RIGHT)
            val before=target.edge in setOf(PanelEdge.LEFT,PanelEdge.TOP)
            val includesCanvas = containsCanvas(destination)
            val fraction=if(includesCanvas) { if(before) .25f else .75f } else .5f
            val split=PanelNode.Split("split-${layout.nextId}",horizontal,fraction,
                if(before) payload else destination,if(before) destination else payload)
            result=update(result,destination.id) { split }
        }
        return result.copy(placed=result.placed+members(payload),nextId=layout.nextId+1,
            floating=result.floating.map { root -> root.copy(rect=root.rect.copy(width=maxOf(root.rect.width,minimumWidth(root.node)),height=maxOf(root.rect.height,minimumHeight(root.node)))) })
    }
    fun containsCanvas(node: PanelNode): Boolean = when(node) {
        is PanelNode.Canvas -> true
        is PanelNode.Split -> containsCanvas(node.first)||containsCanvas(node.second)
        else -> false
    }

    /** Narrow boundaries and real title strips only. Ordinary content overlap is not a target. */
    fun target(slots: List<PanelSlot>, excludedIds: Set<String>, pointerX: Float, pointerY: Float,
        dragged: PanelRect, dx: Float, dy: Float, width: Float, height: Float, tolerance: Float = 12f): PanelDropTarget? {
        val candidates=slots.asReversed().filter { it.node.id !in excludedIds }
        // Explicit pointer targets win over an unrelated edge of a large moving window.
        for(slot in candidates) {
            val r=slot.rect
            if(!r.contains(pointerX,pointerY) || slot.node is PanelNode.Split) continue
            if(slot.node is PanelNode.Tabs && pointerY <= r.y+32f) {
                val index=((pointerX-r.x)/55f).toInt().coerceIn(0,slot.node.panels.size)
                return PanelDropTarget(slot.node.id,PanelEdge.TAB,index)
            }
            val edges=listOf(PanelEdge.LEFT to abs(pointerX-r.x), PanelEdge.RIGHT to abs(pointerX-r.right),
                PanelEdge.TOP to abs(pointerY-r.y),PanelEdge.BOTTOM to abs(pointerY-r.bottom))
            val edge=edges.minByOrNull { it.second }!!
            if(edge.second <= tolerance) return PanelDropTarget(slot.node.id,edge.first)
            // Floating content in front blocks targets belonging to windows behind it.
            if(slot.floating) return null
        }
        val canvas=candidates.firstOrNull { it.node is PanelNode.Canvas } ?: return null
        val r=canvas.rect
        val edgeCandidates=buildList {
            if(dx < 0 && abs(dragged.x-r.x)<=tolerance && dragged.bottom>r.y && dragged.y<r.bottom) add(PanelEdge.LEFT to abs(dragged.x-r.x))
            if(dx > 0 && abs(dragged.right-r.right)<=tolerance && dragged.bottom>r.y && dragged.y<r.bottom) add(PanelEdge.RIGHT to abs(dragged.right-r.right))
            if(dy > 0 && abs(dragged.bottom-r.bottom)<=tolerance && dragged.right>r.x && dragged.x<r.right) add(PanelEdge.BOTTOM to abs(dragged.bottom-r.bottom))
        }
        return edgeCandidates.minByOrNull { it.second }?.let { PanelDropTarget(canvas.node.id,it.first) }
    }
}
