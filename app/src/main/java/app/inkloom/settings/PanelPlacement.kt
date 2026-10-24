package app.inkloom.settings

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where a floating window opens. A window the user has placed opens where they left it; one still at
 * its default spot moves to the freest nearby spot instead of opening on top of the windows already
 * open, so opening several palettes never piles them up at one point.
 */
object PanelPlacement {
    private const val gap = 8f
    /** Default windows keep clear of the tool rail at the left of the canvas. */
    private const val railClearance = 120f
    /** The workspace width the default window spots were chosen for. */
    const val referenceWidth = 1280f

    /**
     * A window still at its default spot keeps its distance from the side it belongs to: windows on
     * the right of the reference workspace stay at the right of a wider one (a smaller interface
     * size gives the workspace more room), windows on the left stay at the left.
     */
    fun anchored(layout: EditorPanelLayout, width: Float): EditorPanelLayout {
        val defaults = EditorPanelLayout().floating.associate { it.node.id to it.rect }
        return layout.copy(floating = layout.floating.map { root ->
            val default = defaults[root.node.id]
            if (default == null || root.rect != default || default.x + default.width / 2f <= referenceWidth / 2f) root
            else root.copy(rect = default.copy(x = (default.x + width - referenceWidth).coerceAtLeast(0f)))
        })
    }

    fun openWhereFree(layout: EditorPanelLayout, before: Set<EditorPanel>, panel: EditorPanel, width: Float, height: Float): EditorPanelLayout {
        val original = layout.floating.firstOrNull { panel in EditorPanelLayouts.members(it.node) } ?: return layout
        val members = EditorPanelLayouts.members(original.node)
        if (members.any { it in before } || members.any { it in layout.placed } || original.node.id in layout.minimized) return layout
        val anchored = anchored(layout, width).floating.first { it.node.id == original.node.id }
        val root = original.copy(rect = anchored.rect)
        val obstacles = EditorPanelLayouts.slots(layout, width, height)
            .filter { (it.node is PanelNode.Tabs || it.collapsed) && it.rootId != root.node.id }.map { it.rect }
        val start = root.rect.bounded(width, height)
        val current = overlap(start, obstacles)
        fun placed(rect: PanelRect) = layout.copy(floating = layout.floating.map { if (it === original) it.copy(rect = rect) else it })
        if (current <= 0f) return if (root.rect == original.rect) layout else placed(root.rect)
        val minX = min(start.x, railClearance)
        val xs = (listOf(start.x) + obstacles.flatMap { listOf(it.x - start.width - gap, it.right + gap) })
            .map { it.coerceIn(minX, max(minX, width - start.width)) }
        val ys = (listOf(start.y, 12f) + obstacles.flatMap { listOf(it.y, it.bottom + gap) })
            .map { it.coerceIn(0f, max(0f, height - start.height)) }
        val best = xs.flatMap { x -> ys.map { y -> start.copy(x = x, y = y) } }
            .minWith(compareBy<PanelRect> { overlap(it, obstacles) }.thenBy { abs(it.x - start.x) + abs(it.y - start.y) })
        if (overlap(best, obstacles) >= current) return if (root.rect == original.rect) layout else placed(root.rect)
        return placed(root.rect.copy(x = best.x, y = best.y))
    }

    private fun overlap(rect: PanelRect, others: List<PanelRect>): Float = others.sumOf { other ->
        val w = min(rect.right, other.right) - max(rect.x, other.x)
        val h = min(rect.bottom, other.bottom) - max(rect.y, other.y)
        if (w > 0f && h > 0f) (w * h).toDouble() else 0.0
    }.toFloat()
}
