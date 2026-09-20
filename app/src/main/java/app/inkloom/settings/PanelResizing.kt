package app.inkloom.settings

import kotlin.math.abs

enum class PanelGrip(val x: Int, val y: Int) {
    LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1),
    TOP_LEFT(-1, -1), TOP_RIGHT(1, -1), BOTTOM_LEFT(-1, 1), BOTTOM_RIGHT(1, 1);
    val corner get() = x != 0 && y != 0
}

/** Each preview is derived from the frozen layout and total root-coordinate displacement. */
object PanelResizing {
    fun floating(layout: EditorPanelLayout, id: String, grip: PanelGrip,
        dx: Float, dy: Float, width: Float, height: Float): EditorPanelLayout {
        if (layout.locked) return layout
        val root = layout.floating.firstOrNull { it.node.id == id } ?: return layout
        val node = EditorPanelLayouts.visible(root.node, layout.open) ?: return layout
        val b = root.rect.bounded(width, height)
        val minW = EditorPanelLayouts.minimumWidth(node).coerceAtMost(b.width)
        val minH = EditorPanelLayouts.minimumHeight(node).coerceAtMost(b.height)
        val left = if (grip.x < 0) (b.x + dx).coerceIn(0f, b.right - minW) else b.x
        val right = if (grip.x > 0) (b.right + dx).coerceIn(b.x + minW, width) else b.right
        val top = if (grip.y < 0) (b.y + dy).coerceIn(0f, b.bottom - minH) else b.y
        val bottom = if (grip.y > 0) (b.bottom + dy).coerceIn(b.y + minH, height) else b.bottom
        return layout.copy(floating = layout.floating.map {
            if (it.node.id == id) it.copy(rect = PanelRect(left, top, right - left, bottom - top)) else it
        })
    }

    fun divider(layout: EditorPanelLayout, id: String, dx: Float, dy: Float,
        width: Float, height: Float): EditorPanelLayout {
        if (layout.locked) return layout
        val slots = EditorPanelLayouts.slots(layout, width, height)
        val slot = slots.firstOrNull { it.node.id == id } ?: return layout
        val split = slot.node as? PanelNode.Split ?: return layout
        val first = slots.first { it.node.id == split.first.id }.rect
        val total = if (split.horizontal) slot.rect.width else slot.rect.height
        val initial = if (split.horizontal) first.width + 2f else first.height + 2f
        val minFirst = if (split.horizontal) EditorPanelLayouts.minimumWidth(split.first) else EditorPanelLayouts.minimumHeight(split.first)
        val minSecond = if (split.horizontal) EditorPanelLayouts.minimumWidth(split.second) else EditorPanelLayouts.minimumHeight(split.second)
        if (total < minFirst + minSecond + 4f) return layout
        val cut = (initial + if (split.horizontal) dx else dy).coerceIn(minFirst + 2f, total - minSecond - 2f)
        return EditorPanelLayouts.update(layout, id) { (it as PanelNode.Split).copy(fraction = cut / total) }
    }

    /** Dock edges resize their shared split; an exposed cross-axis edge creates empty dock space. */
    fun dock(layout: EditorPanelLayout, id: String, grip: PanelGrip, dx: Float, dy: Float,
        width: Float, height: Float): EditorPanelLayout {
        if (layout.locked) return layout
        var next = layout
        if (grip.x != 0) next = dockAxis(next, id, grip.x, true, dx, width, height)
        if (grip.y != 0) next = dockAxis(next, id, grip.y, false, dy, width, height)
        return next
    }

    private fun dockAxis(layout: EditorPanelLayout, id: String, side: Int, horizontal: Boolean,
        delta: Float, width: Float, height: Float): EditorPanelLayout {
        val slots = EditorPanelLayouts.slots(layout, width, height).filterNot(PanelSlot::floating)
        val leaf = slots.firstOrNull { it.node.id == id } ?: return layout
        val edge = if (horizontal) { if (side < 0) leaf.rect.x else leaf.rect.right }
            else if (side < 0) leaf.rect.y else leaf.rect.bottom
        val parents = slots.filter { it.node is PanelNode.Split && EditorPanelLayouts.find(it.node, id) != null }.asReversed()
        for (parent in parents) {
            val split = parent.node as PanelNode.Split
            if (split.horizontal != horizontal) continue
            val first = slots.first { it.node.id == split.first.id }.rect
            val cut = if (horizontal) first.right + 2f else first.bottom + 2f
            if (abs(edge - cut) <= 2.1f)
                return divider(layout, split.id, if (horizontal) delta else 0f, if (horizontal) 0f else delta, width, height)
        }
        // Keep the attachment edge fixed. Only the orthogonal extent can leave free dock space.
        val anchor = parents.firstOrNull { EditorPanelLayouts.containsCanvas(it.node) }?.node as? PanelNode.Split ?: return layout
        if (anchor.horizontal == horizontal || delta * side >= 0f) return layout
        val total = if (horizontal) leaf.rect.width else leaf.rect.height
        val minimum = if (horizontal) EditorPanelLayouts.minimumWidth(leaf.node) else EditorPanelLayouts.minimumHeight(leaf.node)
        if (total < minimum + 28f) return layout
        val size = (total + delta * side).coerceIn(minimum, total - 28f)
        val space = PanelNode.Space("space-${layout.nextId}")
        val before = side < 0
        val cut = if (before) total - size - 2f else size + 2f
        return EditorPanelLayouts.update(layout, id) { node ->
            PanelNode.Split("extent-${layout.nextId}", horizontal, cut / total,
                if (before) space else node, if (before) node else space)
        }.copy(nextId = layout.nextId + 1)
    }

    /** Wide invisible hit bands with separate corners; the visible border remains one pixel. */
    fun handle(b: PanelRect, grip: PanelGrip, width: Float, height: Float): PanelRect {
        val thickness = if (grip.corner) 24f else 12f
        val x = when { grip.x < 0 -> b.x - if (grip.corner) thickness / 2 else 0f
            grip.x > 0 -> b.right - if (grip.corner) thickness / 2 else thickness
            else -> b.x + 24f }
        val y = when { grip.y < 0 -> b.y - if (grip.corner) thickness / 2 else 0f
            grip.y > 0 -> b.bottom - if (grip.corner) thickness / 2 else thickness
            else -> b.y + 32f }
        val w = if (grip.x == 0) (b.width - 48f).coerceAtLeast(1f) else thickness
        // A top edge flush with the workspace must leave the title's center available for dragging.
        val h = if (grip.y == 0) (b.height - 56f).coerceAtLeast(1f)
            else if (grip.y < 0 && (!grip.corner || b.y < thickness / 2)) 12f else thickness
        // Side bands stay inside their panel so adjacent canvas tools keep their clicks.
        // Shift screen-edge corners inward instead of losing half of their hit area.
        return PanelRect(x.coerceIn(0f, (width - w).coerceAtLeast(0f)),
            y.coerceIn(0f, (height - h).coerceAtLeast(0f)), w, h)
    }
}
