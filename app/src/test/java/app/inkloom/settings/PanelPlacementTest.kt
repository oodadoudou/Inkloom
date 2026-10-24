package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Opening a window that is still at its default spot avoids the windows already open. */
class PanelPlacementTest {
    private val width = 1280f
    private val height = 720f

    private fun open(layout: EditorPanelLayout, panel: EditorPanel) =
        PanelPlacement.openWhereFree(layout.copy(open = layout.open + panel), layout.open, panel, width, height)
    private fun rect(layout: EditorPanelLayout, panel: EditorPanel) = layout.floating.first { panel in EditorPanelLayouts.members(it.node) }.rect
    private fun overlap(a: PanelRect, b: PanelRect): Float =
        max(0f, min(a.right, b.right) - max(a.x, b.x)) * max(0f, min(a.bottom, b.bottom) - max(a.y, b.y))
    private fun totalOverlap(layout: EditorPanelLayout): Float {
        val rects = layout.floating.filter { root -> EditorPanelLayouts.members(root.node).any { it in layout.open } }.map { it.rect }
        return rects.indices.sumOf { i -> (i + 1 until rects.size).sumOf { j -> overlap(rects[i], rects[j]).toDouble() } }.toFloat()
    }

    @Test fun aDefaultWindowOpensBesideTheOneAlreadyOpenInsteadOfOnTopOfIt() {
        val brushes = open(EditorPanelLayout(), EditorPanel.BRUSHES)
        assertEquals(EditorPanelLayout().floating.first { it.node.id == "brushes" }.rect, rect(brushes, EditorPanel.BRUSHES))
        val both = open(brushes, EditorPanel.LAYERS)
        val layers = rect(both, EditorPanel.LAYERS)
        assertEquals(0f, overlap(layers, rect(both, EditorPanel.BRUSHES)), .01f)
        // Only the position changes; it stays in the window and clear of the tool rail.
        assertEquals(270f, layers.width, .01f)
        assertEquals(500f, layers.height, .01f)
        assertTrue(layers.x >= 120f && layers.right <= width && layers.bottom <= height)
        assertTrue(EditorPanelLayouts.isValid(both))
    }

    @Test fun openingSeveralDefaultWindowsPilesThemUpFarLessThanTheirDefaultSpots() {
        val defaults = EditorPanelLayout().copy(open = setOf(EditorPanel.BRUSHES, EditorPanel.LAYERS, EditorPanel.WHEEL, EditorPanel.NAVIGATOR))
        var placed = EditorPanelLayout()
        listOf(EditorPanel.BRUSHES, EditorPanel.LAYERS, EditorPanel.WHEEL, EditorPanel.NAVIGATOR).forEach { placed = open(placed, it) }
        assertTrue("${totalOverlap(placed)} vs ${totalOverlap(defaults)}", totalOverlap(placed) < totalOverlap(defaults) / 3f)
    }

    @Test fun windowsTheUserPlacedOrThatAreAlreadyShowingStayPut() {
        val brushes = open(EditorPanelLayout(), EditorPanel.BRUSHES)
        val placedLayers = brushes.copy(placed = setOf(EditorPanel.LAYERS))
        assertEquals(rect(placedLayers, EditorPanel.LAYERS), rect(open(placedLayers, EditorPanel.LAYERS), EditorPanel.LAYERS))
        // Showing another tab of a window already on screen does not move that window.
        val layers = open(EditorPanelLayout(), EditorPanel.LAYERS)
        val withBrushes = layers.copy(open = layers.open + EditorPanel.BRUSHES)
        val properties = PanelPlacement.openWhereFree(withBrushes.copy(open = withBrushes.open + EditorPanel.PROPERTIES), withBrushes.open, EditorPanel.PROPERTIES, width, height)
        assertEquals(rect(withBrushes, EditorPanel.LAYERS), rect(properties, EditorPanel.PROPERTIES))
        // A window with free space where it is, or a docked palette, is left alone.
        val alone = EditorPanelLayout().copy(open = setOf(EditorPanel.PAGES))
        assertSame(alone, PanelPlacement.openWhereFree(alone, emptySet(), EditorPanel.PAGES, width, height))
        val painting = BuiltInWorkspaces.snapshot(BuiltInWorkspace.PAINTING).layout
        assertSame(painting, PanelPlacement.openWhereFree(painting, setOf(EditorPanel.LAYERS), EditorPanel.WHEEL, width, height))
    }

    @Test fun onAWiderWorkspaceDefaultWindowsStayAtTheSideTheyBelongTo() {
        val wide = 1900f
        val layers = PanelPlacement.openWhereFree(EditorPanelLayout().copy(open = setOf(EditorPanel.LAYERS)), emptySet(), EditorPanel.LAYERS, wide, height)
        val default = rect(EditorPanelLayout(), EditorPanel.LAYERS)
        assertEquals(wide - (PanelPlacement.referenceWidth - default.x), rect(layers, EditorPanel.LAYERS).x, .01f)
        // Left-hand windows keep their place; a window the user moved or resized is not re-anchored.
        val pages = PanelPlacement.openWhereFree(EditorPanelLayout().copy(open = setOf(EditorPanel.PAGES)), emptySet(), EditorPanel.PAGES, wide, height)
        assertEquals(rect(EditorPanelLayout(), EditorPanel.PAGES), rect(pages, EditorPanel.PAGES))
        val resized = EditorPanelLayout().let { l -> l.copy(open = setOf(EditorPanel.LAYERS), floating = l.floating.map {
            if (it.node.id == "layers") it.copy(rect = it.rect.copy(width = 300f)) else it }) }
        assertEquals(800f, rect(PanelPlacement.openWhereFree(resized, emptySet(), EditorPanel.LAYERS, wide, height), EditorPanel.LAYERS).x, .01f)
        // At the reference width nothing moves.
        val reference = EditorPanelLayout().copy(open = setOf(EditorPanel.LAYERS))
        assertSame(reference, PanelPlacement.openWhereFree(reference, emptySet(), EditorPanel.LAYERS, PanelPlacement.referenceWidth, height))
    }

    @Test fun aWindowOpeningInFreeSpaceKeepsClearOfThePageNavigator() {
        var layout = EditorPanelLayout()
        listOf(EditorPanel.BRUSHES, EditorPanel.LAYERS, EditorPanel.WHEEL, EditorPanel.NAVIGATOR).forEach { layout = open(layout, it) }
        val navigator = PanelRect(width - 320f, height - 80f, 320f, 80f)
        listOf(EditorPanel.LAYERS, EditorPanel.WHEEL, EditorPanel.NAVIGATOR).forEach { assertEquals(0f, overlap(rect(layout, it), navigator), .01f) }
    }

    @Test fun aFloatingWindowAvoidsDockedPalettesToo() {
        val painting = BuiltInWorkspaces.snapshot(BuiltInWorkspace.PAINTING).layout
        val brushes = open(painting, EditorPanel.BRUSHES)
        val docked = EditorPanelLayouts.slots(brushes, width, height).filter { it.node is PanelNode.Tabs && !it.floating }.map { it.rect }
        val window = EditorPanelLayouts.slots(brushes, width, height).first { it.node.id == "brushes" }.rect
        val before = docked.sumOf { overlap(EditorPanelLayout().floating.first { it.node.id == "brushes" }.rect.bounded(width, height), it).toDouble() }
        val after = docked.sumOf { overlap(window, it).toDouble() }
        assertTrue("$after vs $before", after < before)
    }
}
