package app.inkloom.settings

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
/** Colour panels come last: code treats every panel from [WHEEL] on as a colour panel. */
enum class EditorPanel { PAGES, BRUSHES, LAYERS, SUBTOOLS, PROPERTIES, NAVIGATOR, SUBVIEW, WHEEL, PALETTES, MIX, NEARBY }

@Serializable
data class PanelRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    fun contains(px: Float, py: Float) = px in x..right && py in y..bottom
    fun moved(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
    /** Moving keeps the stored size; only the position is pulled back into the window. */
    fun placedWithin(w: Float, h: Float) = copy(
        x = x.coerceIn(0f, (w - width).coerceAtLeast(0f)), y = y.coerceIn(0f, (h - height).coerceAtLeast(0f)))
    fun bounded(w: Float, h: Float): PanelRect {
        val width = width.coerceIn(minOf(72f, w), w.coerceAtLeast(1f))
        val minHeight = minOf(72f, h.coerceAtLeast(1f))
        val top = y.coerceIn(0f, (h-minHeight).coerceAtLeast(0f))
        val height = height.coerceIn(minHeight, (h-top).coerceAtLeast(minHeight))
        return PanelRect(x.coerceIn(0f, (w-width).coerceAtLeast(0f)), top, width, height)
    }
}

@Serializable
sealed interface PanelNode {
    val id: String
    @Serializable data class Space(override val id: String) : PanelNode
    @Serializable data class Canvas(override val id: String = "canvas") : PanelNode
    @Serializable data class Tabs(override val id: String, val panels: List<EditorPanel>, val active: EditorPanel = panels.first()) : PanelNode
    @Serializable data class Split(override val id: String, val horizontal: Boolean, val fraction: Float,
        val first: PanelNode, val second: PanelNode) : PanelNode
}

@Serializable
data class FloatingPanels(val node: PanelNode, val rect: PanelRect)

@Serializable
data class EditorPanelLayout(
    val version: Int = 2,
    val dock: PanelNode = PanelNode.Canvas(),
    // Pages opens beside the left tool rail instead of covering it. Brushes, colors and layers
    // stay on the right so the left of the page remains drawable while those windows are open;
    // colors is wide enough to show all four of its tabs.
    val floating: List<FloatingPanels> = listOf(
        FloatingPanels(PanelNode.Tabs("pages", listOf(EditorPanel.PAGES)), PanelRect(128f, 12f, 180f, 560f)),
        FloatingPanels(PanelNode.Tabs("brushes", listOf(EditorPanel.BRUSHES, EditorPanel.SUBTOOLS)), PanelRect(720f, 12f, 340f, 600f)),
        FloatingPanels(PanelNode.Tabs("layers", listOf(EditorPanel.LAYERS, EditorPanel.PROPERTIES)), PanelRect(800f, 80f, 270f, 500f)),
        // Colours stay the front-most window by default.
        FloatingPanels(PanelNode.Tabs("navigator", listOf(EditorPanel.NAVIGATOR)), PanelRect(880f, 364f, 280f, 340f)),
        FloatingPanels(PanelNode.Tabs("subview", listOf(EditorPanel.SUBVIEW)), PanelRect(128f, 180f, 296f, 380f)),
        FloatingPanels(PanelNode.Tabs("colors", listOf(EditorPanel.WHEEL, EditorPanel.PALETTES, EditorPanel.MIX, EditorPanel.NEARBY)), PanelRect(850f, 12f, 324f, 340f)),
    ),
    val open: Set<EditorPanel> = emptySet(),
    val placed: Set<EditorPanel> = emptySet(),
    val nextId: Int = 1,
    val locked: Boolean = false,
)

enum class PanelEdge { LEFT, RIGHT, TOP, BOTTOM, TAB }
data class PanelDropTarget(val nodeId: String, val edge: PanelEdge, val tabIndex: Int = 0)
data class PanelSlot(val node: PanelNode, val rect: PanelRect, val rootId: String, val floating: Boolean)

/** Small immutable layout transactions: a panel has one owner, preview never edits the document. */
object EditorPanelLayouts {
    const val groupGripWidth = 30f
    /** Title row owning the group grip, tab grips and close button. */
    const val titleHeight = 32f

    /** A title grip owns the complete window or the highest docked group without canvas. */
    fun groupSourceId(layout: EditorPanelLayout, tabId: String): String? {
        layout.floating.firstOrNull { find(it.node, tabId) != null }?.let { return it.node.id }
        fun dockGroup(node: PanelNode): String? {
            if (find(node, tabId) == null) return null
            if (!containsCanvas(node)) return node.id
            return (node as? PanelNode.Split)?.let { dockGroup(it.first) ?: dockGroup(it.second) }
        }
        return dockGroup(layout.dock)
    }

    fun intentionalDrag(panel: EditorPanel?, dx: Float, dy: Float): Boolean {
        val minimum = if (panel == null) 16f else 22f
        return dx * dx + dy * dy >= minimum * minimum
    }

    /** Close button at the right of every title row. */
    const val closeWidth = 30f

    /** Below this width a panel shows its tabs as icons; content otherwise stays visible and scrolls. */
    fun compactThreshold(panel: EditorPanel): Float = when (panel) {
        EditorPanel.PAGES -> 104f
        EditorPanel.BRUSHES -> 160f
        EditorPanel.LAYERS, EditorPanel.SUBTOOLS, EditorPanel.PROPERTIES -> 140f
        EditorPanel.WHEEL, EditorPanel.PALETTES, EditorPanel.MIX, EditorPanel.NEARBY -> 120f
        EditorPanel.NAVIGATOR, EditorPanel.SUBVIEW -> 110f
    }

    /** Tabs share the title row equally, as CSP's stacked palettes do, so every tab stays reachable without scrolling. */
    fun tabWidth(tabs: PanelNode.Tabs, panelWidth: Float): Float =
        ((panelWidth - groupGripWidth - closeWidth) / tabs.panels.size.coerceAtLeast(1)).coerceAtLeast(1f)
    fun tabInsertionIndex(tabs: PanelNode.Tabs, pointerX: Float, panelX: Float, panelWidth: Float): Int {
        val width = tabWidth(tabs, panelWidth)
        return ((pointerX - panelX - groupGripWidth + width / 2f) / width).toInt().coerceIn(0, tabs.panels.size)
    }
    fun tabInsertionX(tabs: PanelNode.Tabs, index: Int, panelX: Float, panelWidth: Float): Float =
        panelX + groupGripWidth + tabWidth(tabs, panelWidth) * index.coerceIn(0, tabs.panels.size)

    fun isValid(layout: EditorPanelLayout): Boolean {
        val ids = mutableSetOf<String>()
        var canvases = 0
        fun valid(node: PanelNode, depth: Int): Boolean {
            if (depth > 24 || node.id.isBlank() || !ids.add(node.id)) return false
            return when (node) {
                is PanelNode.Canvas -> { canvases++; true }
                is PanelNode.Space -> true
                is PanelNode.Tabs -> node.panels.isNotEmpty() && node.active in node.panels
                is PanelNode.Split -> node.fraction.isFinite() && node.fraction in 0f..1f && valid(node.first, depth + 1) && valid(node.second, depth + 1)
            }
        }
        if (!valid(layout.dock, 0) || layout.nextId < 1 || layout.floating.any {
            !valid(it.node, 0) || with(it.rect) { !x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0 }
        } || canvases != 1) return false
        val panels = members(layout.dock) + layout.floating.flatMap { members(it.node) }
        return layout.version == 2 && containsCanvas(layout.dock) &&
            layout.floating.none { containsCanvas(it.node) || members(it.node).isEmpty() } &&
            panels.size == EditorPanel.entries.size && panels.toSet().size == panels.size &&
            layout.open.all { it in panels } && layout.placed.all { it in panels }
    }
    /**
     * A layout saved before a panel existed gets that panel in a window of its own, closed and
     * placed as in the default layout, instead of being rejected and reset.
     */
    fun withAllPanels(layout: EditorPanelLayout): EditorPanelLayout {
        val present = (members(layout.dock) + layout.floating.flatMap { members(it.node) }).toSet()
        val missing = EditorPanel.entries.filter { it !in present }
        if (missing.isEmpty()) return layout
        val ids = mutableSetOf<String>()
        fun collect(node: PanelNode) { ids += node.id; if (node is PanelNode.Split) { collect(node.first); collect(node.second) } }
        collect(layout.dock); layout.floating.forEach { collect(it.node) }
        val defaults = EditorPanelLayout().floating
        var nextId = layout.nextId
        val added = missing.map { panel ->
            var id: String
            do { id = "added-${nextId++}" } while (id in ids)
            ids += id
            FloatingPanels(PanelNode.Tabs(id, listOf(panel)), defaults.firstOrNull { panel in members(it.node) }?.rect ?: PanelRect(120f, 120f, 260f, 300f))
        }
        return layout.copy(floating = layout.floating + added, nextId = nextId)
    }
    fun members(node: PanelNode): List<EditorPanel> = when (node) {
        is PanelNode.Canvas, is PanelNode.Space -> emptyList()
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
        return when {
            first == null -> second?.takeUnless { it is PanelNode.Space }
            second == null -> first.takeUnless { it is PanelNode.Space }
            else -> node.copy(first = first, second = second)
        }
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
        is PanelNode.Canvas, is PanelNode.Space -> node
        is PanelNode.Tabs -> node.panels.filter { it in open }.takeIf { it.isNotEmpty() }?.let {
            node.copy(panels = it, active = node.active.takeIf(it::contains) ?: it.first())
        }
        is PanelNode.Split -> {
            val a = visible(node.first,open); val b = visible(node.second,open)
            when { a == null -> b?.takeUnless { it is PanelNode.Space }; b == null -> a.takeUnless { it is PanelNode.Space }; else -> node.copy(first=a, second=b) }
        }
    }
    fun minimumWidth(node: PanelNode): Float = when(node) {
        is PanelNode.Space -> 24f
        is PanelNode.Canvas -> 200f
        // Content switches to its compact tab rail below its useful width. The
        // content's ideal size must never prevent the user from narrowing a dock.
        is PanelNode.Tabs -> 72f
        is PanelNode.Split -> if(node.horizontal) minimumWidth(node.first)+minimumWidth(node.second)+4f else maxOf(minimumWidth(node.first),minimumWidth(node.second))
    }
    fun minimumHeight(node: PanelNode): Float = when(node) {
        is PanelNode.Space -> 24f
        is PanelNode.Canvas -> 160f
        is PanelNode.Tabs -> 72f
        is PanelNode.Split -> if(!node.horizontal) minimumHeight(node.first)+minimumHeight(node.second)+4f else maxOf(minimumHeight(node.first),minimumHeight(node.second))
    }
    private fun cut(total: Float, fraction: Float, first: Float, second: Float): Float =
        if (total >= first+second+4f) (total*fraction).coerceIn(first+2f,total-second-2f)
        else total*first/(first+second)
    fun slots(layout: EditorPanelLayout, width: Float, height: Float): List<PanelSlot> = buildList {
        fun place(node: PanelNode, rect: PanelRect, rootId: String, floating: Boolean) {
            add(PanelSlot(node,rect,rootId,floating))
            if(node is PanelNode.Split) {
                val f = node.fraction.coerceIn(0f,1f)
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
        if (layout.locked) return layout
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
        } else if(destination is PanelNode.Space) {
            result = update(result, destination.id) { payload }
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

    /** How far from the canvas sides (left, right, bottom) a finger docks a panel to the window side. */
    const val windowDockZone = 64f

    /**
     * CSP's drop rules, by where the finger is: over a palette's title bar the palette stacks as a tab;
     * over its body the nearest edge within 30% places it beside, above or below (the middle stacks it);
     * near the canvas's left, right or bottom side it docks to that side of the window; elsewhere it floats.
     * [dragged], [dx] and [dy] are kept for callers that track the moving window.
     */
    fun target(slots: List<PanelSlot>, excludedIds: Set<String>, pointerX: Float, pointerY: Float,
        dragged: PanelRect, dx: Float, dy: Float, width: Float, height: Float, tolerance: Float = 12f): PanelDropTarget? {
        val candidates=slots.asReversed().filter { it.node.id !in excludedIds }
        for(slot in candidates) {
            val r=slot.rect
            if(!r.contains(pointerX,pointerY) || slot.node is PanelNode.Split) continue
            when (val node = slot.node) {
                is PanelNode.Tabs -> {
                    if (pointerY <= r.y + titleHeight) return PanelDropTarget(node.id, PanelEdge.TAB, tabInsertionIndex(node, pointerX, r.x, r.width))
                    val fx = ((pointerX - r.x) / r.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val fy = ((pointerY - r.y - titleHeight) / (r.height - titleHeight).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val nearest = listOf(PanelEdge.LEFT to fx, PanelEdge.RIGHT to 1f - fx, PanelEdge.TOP to fy, PanelEdge.BOTTOM to 1f - fy).minBy { it.second }
                    return if (nearest.second < .3f) PanelDropTarget(node.id, nearest.first) else PanelDropTarget(node.id, PanelEdge.TAB, node.panels.size)
                }
                is PanelNode.Space -> {
                    val edges = listOf(PanelEdge.LEFT to abs(pointerX - r.x), PanelEdge.RIGHT to abs(pointerX - r.right),
                        PanelEdge.TOP to abs(pointerY - r.y), PanelEdge.BOTTOM to abs(pointerY - r.bottom))
                    return PanelDropTarget(node.id, edges.minBy { it.second }.first)
                }
                is PanelNode.Canvas -> {
                    val zone = minOf(windowDockZone, r.width * .2f, r.height * .2f)
                    val sides = listOf(PanelEdge.LEFT to pointerX - r.x, PanelEdge.RIGHT to r.right - pointerX, PanelEdge.BOTTOM to r.bottom - pointerY)
                    return sides.filter { it.second <= zone }.minByOrNull { it.second }?.let { PanelDropTarget(node.id, it.first) }
                }
                is PanelNode.Split -> Unit
            }
        }
        return null
    }
}
