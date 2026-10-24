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
    /**
     * [dockSize] is set on a split between the canvas and a palette dock: the dock's own width (or
     * height for a bottom dock) in dp, kept when the window or interface size changes, as CSP's docks
     * are. Other splits share their space by [fraction].
     */
    @Serializable data class Split(override val id: String, val horizontal: Boolean, val fraction: Float,
        val first: PanelNode, val second: PanelNode, val dockSize: Float? = null) : PanelNode
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
    /** Palette docks shown as icons (CSP's simple view) or hidden, by dock column id. */
    val iconDocks: Set<String> = emptySet(),
    val hiddenDocks: Set<String> = emptySet(),
    /** Floating palettes showing only their title bar. */
    val minimized: Set<String> = emptySet(),
    /** Palettes that an icon dock opens by expanding the whole dock (CSP's "display palette as tab"); the others pop up. */
    val tabPalettes: Set<EditorPanel> = emptySet(),
)

enum class PanelEdge { LEFT, RIGHT, TOP, BOTTOM, TAB }
data class PanelDropTarget(val nodeId: String, val edge: PanelEdge, val tabIndex: Int = 0)

/** CSP's palette dock display: full palettes, icons only, or hidden to a thin strip. */
enum class DockDisplay { NORMAL, ICONS, HIDDEN }
/** A palette dock column (or bottom/top row) on [side] of the canvas; [header] holds its display buttons. */
data class DockColumn(val id: String, val side: PanelEdge, val display: DockDisplay, val header: PanelRect?)
data class PanelSlot(val node: PanelNode, val rect: PanelRect, val rootId: String, val floating: Boolean, val column: DockColumn? = null) {
    /** An icon or hidden dock: its palettes are not laid out, only the strip. */
    val collapsed get() = column != null && column.display != DockDisplay.NORMAL
}

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
    /** Minimize button of a palette shown by itself. */
    const val minimizeWidth = 26f
    /** Bar above each dock column with its hide and icon buttons. */
    const val dockHeaderHeight = 22f
    const val iconDockWidth = 48f
    const val hiddenDockWidth = 20f

    /** Below this width a panel shows its tabs as icons; content otherwise stays visible and scrolls. */
    fun compactThreshold(panel: EditorPanel): Float = when (panel) {
        EditorPanel.PAGES -> 104f
        EditorPanel.BRUSHES -> 160f
        EditorPanel.LAYERS, EditorPanel.SUBTOOLS, EditorPanel.PROPERTIES -> 140f
        EditorPanel.WHEEL, EditorPanel.PALETTES, EditorPanel.MIX, EditorPanel.NEARBY -> 120f
        EditorPanel.NAVIGATOR, EditorPanel.SUBVIEW -> 110f
    }

    /** Tabs share the title row equally, as CSP's stacked palettes do, so every tab stays reachable without scrolling. */
    fun tabWidth(tabs: PanelNode.Tabs, panelWidth: Float, minimizable: Boolean = false): Float =
        ((panelWidth - groupGripWidth - closeWidth - if (minimizable) minimizeWidth else 0f) / tabs.panels.size.coerceAtLeast(1)).coerceAtLeast(1f)
    fun tabInsertionIndex(tabs: PanelNode.Tabs, pointerX: Float, panelX: Float, panelWidth: Float, minimizable: Boolean = false): Int {
        val width = tabWidth(tabs, panelWidth, minimizable)
        return ((pointerX - panelX - groupGripWidth + width / 2f) / width).toInt().coerceIn(0, tabs.panels.size)
    }
    fun tabInsertionX(tabs: PanelNode.Tabs, index: Int, panelX: Float, panelWidth: Float, minimizable: Boolean = false): Float =
        panelX + groupGripWidth + tabWidth(tabs, panelWidth, minimizable) * index.coerceIn(0, tabs.panels.size)
    /** CSP offers minimize only on a palette shown by itself: a floating window that is one tab group. */
    fun minimizable(slot: PanelSlot) = slot.floating && slot.rootId == slot.node.id && slot.node is PanelNode.Tabs

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
    /** [expanded]: an icon dock shown in full for now because a palette in it opens as a tab. */
    fun slots(layout: EditorPanelLayout, width: Float, height: Float, expanded: String? = null): List<PanelSlot> = buildList {
        val full = columns(layout.dock)
        fun display(column: PanelNode): DockDisplay = full.firstOrNull { find(it.first, column.id) != null }?.first?.id.let { id ->
            when (id) { expanded -> DockDisplay.NORMAL; in layout.hiddenDocks -> DockDisplay.HIDDEN; in layout.iconDocks -> DockDisplay.ICONS; else -> DockDisplay.NORMAL }
        }
        fun place(node: PanelNode, outer: PanelRect, rootId: String, floating: Boolean, column: DockColumn? = null) {
            val header = if (column?.display == DockDisplay.NORMAL) outer.copy(height = minOf(dockHeaderHeight, outer.height / 2f)) else null
            val rect = header?.let { outer.copy(y = it.bottom, height = outer.height - it.height) } ?: outer
            add(PanelSlot(node, rect, rootId, floating, column?.copy(header = header)))
            if (node !is PanelNode.Split || (column != null && column.display != DockDisplay.NORMAL)) return
            val f = node.fraction.coerceIn(0f,1f)
            val total = if (node.horizontal) rect.width else rect.height
            // The canvas's neighbour in the dock is a palette dock; a collapsed one keeps its strip's fixed size.
            val canvasFirst = containsCanvas(node.first)
            val docked = if (floating || !containsCanvas(node)) null else if (canvasFirst) node.second else node.first
            val shown = docked?.let(::display) ?: DockDisplay.NORMAL
            val strip = when (shown) { DockDisplay.ICONS -> iconDockWidth; DockDisplay.HIDDEN -> hiddenDockWidth; DockDisplay.NORMAL -> null }
            val fixed = node.dockSize?.takeIf { docked != null && it.isFinite() && it > 0f }
            val cut = when {
                strip != null && total > strip + 4f -> if (canvasFirst) total - strip - 2f else strip + 2f
                fixed != null && docked != null -> {
                    val canvas = if (canvasFirst) node.first else node.second
                    val dockMinimum = if (node.horizontal) minimumWidth(docked) else minimumHeight(docked)
                    val canvasMinimum = if (node.horizontal) minimumWidth(canvas) else minimumHeight(canvas)
                    val size = fixed.coerceAtMost(total - canvasMinimum - 4f).coerceAtLeast(minOf(dockMinimum, total / 2f))
                    if (canvasFirst) total - size - 2f else size + 2f
                }
                node.horizontal -> cut(total,f,minimumWidth(node.first),minimumWidth(node.second))
                else -> cut(total,f,minimumHeight(node.first),minimumHeight(node.second))
            }
            val side = when { node.horizontal && canvasFirst -> PanelEdge.RIGHT; node.horizontal -> PanelEdge.LEFT; canvasFirst -> PanelEdge.BOTTOM; else -> PanelEdge.TOP }
            fun columnOf(child: PanelNode) = if (docked != null && child.id == docked.id) DockColumn(child.id, side, shown, null) else null
            if(node.horizontal) {
                place(node.first,rect.copy(width=cut-2f),rootId,floating,columnOf(node.first))
                place(node.second,PanelRect(rect.x+cut+2f,rect.y,rect.width-cut-2f,rect.height),rootId,floating,columnOf(node.second))
            } else {
                place(node.first,rect.copy(height=cut-2f),rootId,floating,columnOf(node.first))
                place(node.second,PanelRect(rect.x,rect.y+cut+2f,rect.width,rect.height-cut-2f),rootId,floating,columnOf(node.second))
            }
        }
        visible(layout.dock,layout.open)?.let { place(it,PanelRect(0f,0f,width,height),layout.dock.id,false) }
        layout.floating.forEach { root -> visible(root.node,layout.open)?.let { node ->
            val rect = root.rect.bounded(width,height)
            place(node, if (root.node.id in layout.minimized && node is PanelNode.Tabs) rect.copy(height = titleHeight) else rect, root.node.id, true)
        } }
    }

    /** CSP's palette docks: every non-canvas child of a split holding the canvas, with the side of the canvas it sits on. */
    fun columns(dock: PanelNode): List<Pair<PanelNode, PanelEdge>> = buildList {
        var node = dock
        while (node is PanelNode.Split && containsCanvas(node)) {
            val canvasFirst = containsCanvas(node.first)
            add((if (canvasFirst) node.second else node.first) to when {
                node.horizontal && canvasFirst -> PanelEdge.RIGHT; node.horizontal -> PanelEdge.LEFT
                canvasFirst -> PanelEdge.BOTTOM; else -> PanelEdge.TOP
            })
            node = if (canvasFirst) node.first else node.second
        }
    }
    /** The dock column holding node [id] (any palette group inside it), if that node is docked. */
    fun column(layout: EditorPanelLayout, id: String): PanelNode? = columns(layout.dock).firstOrNull { find(it.first, id) != null }?.first
    fun dockDisplay(layout: EditorPanelLayout, id: String): DockDisplay = when (column(layout, id)?.id) {
        null -> DockDisplay.NORMAL
        in layout.hiddenDocks -> DockDisplay.HIDDEN
        in layout.iconDocks -> DockDisplay.ICONS
        else -> DockDisplay.NORMAL
    }
    /** Shows the dock column holding [id] in full, as icons or hidden; it is a display choice, so a locked layout allows it. */
    fun setDockDisplay(layout: EditorPanelLayout, id: String, display: DockDisplay): EditorPanelLayout {
        val column = column(layout, id)?.id ?: return layout
        return layout.copy(
            iconDocks = if (display == DockDisplay.ICONS) layout.iconDocks + column else layout.iconDocks - column,
            hiddenDocks = if (display == DockDisplay.HIDDEN) layout.hiddenDocks + column else layout.hiddenDocks - column)
    }
    /** Whether [panel] sits in an icon or hidden dock (other than the [expanded] one), where it opens as a pop-up or tab. */
    fun inCollapsedDock(layout: EditorPanelLayout, panel: EditorPanel, expanded: String? = null): Boolean =
        dockColumnOf(layout, panel)?.let { it.id != expanded && dockDisplay(layout, it.id) != DockDisplay.NORMAL } == true
    /** The dock column holding [panel], if it is docked. */
    fun dockColumnOf(layout: EditorPanelLayout, panel: EditorPanel): PanelNode? {
        fun owner(node: PanelNode): PanelNode.Tabs? = when (node) {
            is PanelNode.Tabs -> node.takeIf { panel in it.panels }
            is PanelNode.Split -> owner(node.first) ?: owner(node.second)
            else -> null
        }
        return owner(layout.dock)?.let { column(layout, it.id) }
    }
    fun setTabPalette(layout: EditorPanelLayout, panel: EditorPanel, tab: Boolean): EditorPanelLayout =
        layout.copy(tabPalettes = if (tab) layout.tabPalettes + panel else layout.tabPalettes - panel)
    fun toggleMinimized(layout: EditorPanelLayout, rootId: String): EditorPanelLayout =
        if (layout.floating.none { it.node.id == rootId && it.node is PanelNode.Tabs }) layout
        else layout.copy(minimized = if (rootId in layout.minimized) layout.minimized - rootId else layout.minimized + rootId)
    /** Display choices follow their dock or window; once it is gone they are dropped so a later one does not inherit them. */
    fun pruned(layout: EditorPanelLayout): EditorPanelLayout {
        val columns = columns(layout.dock).mapTo(mutableSetOf()) { it.first.id }
        val windows = layout.floating.filter { it.node is PanelNode.Tabs }.mapTo(mutableSetOf()) { it.node.id }
        return layout.copy(iconDocks = layout.iconDocks.intersect(columns), hiddenDocks = layout.hiddenDocks.intersect(columns),
            minimized = layout.minimized.intersect(windows))
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
            // A new palette dock beside the canvas is as wide (or tall) as the window that made it.
            val dockSize=if(includesCanvas) (if(horizontal) floatingRect.width else floatingRect.height).takeIf { it.isFinite() && it > 0f } else null
            val split=PanelNode.Split("split-${layout.nextId}",horizontal,fraction,
                if(before) payload else destination,if(before) destination else payload,dockSize)
            result=update(result,destination.id) { split }
        }
        return pruned(result.copy(placed=result.placed+members(payload),nextId=layout.nextId+1,
            floating=result.floating.map { root -> root.copy(rect=root.rect.copy(width=maxOf(root.rect.width,minimumWidth(root.node)),height=maxOf(root.rect.height,minimumHeight(root.node)))) }))
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
            if(!r.contains(pointerX,pointerY) || slot.node is PanelNode.Split || slot.collapsed) continue
            when (val node = slot.node) {
                is PanelNode.Tabs -> {
                    if (pointerY <= r.y + titleHeight) return PanelDropTarget(node.id, PanelEdge.TAB, tabInsertionIndex(node, pointerX, r.x, r.width, minimizable(slot)))
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
