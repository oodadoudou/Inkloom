package app.inkloom.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PanelResizingTest {
    private val width = 1280f
    private val height = 900f
    private val original = EditorPanelLayout(open = setOf(EditorPanel.PAGES, EditorPanel.BRUSHES, EditorPanel.LAYERS))
        .let { layout -> layout.copy(floating = layout.floating.map { it.copy(rect = PanelRect(150f, 150f, 380f, 420f)) }) }

    @Test fun sideHandlesDoNotStealClicksFromAdjacentToolbarOrCanvas() {
        val panel = PanelRect(88f, 16f, 160f, 640f)
        for (grip in listOf(PanelGrip.LEFT, PanelGrip.RIGHT, PanelGrip.TOP, PanelGrip.BOTTOM)) {
            val handle = PanelResizing.handle(panel, grip, width, height)
            assertTrue(handle.x >= panel.x && handle.right <= panel.right)
            assertTrue(handle.y >= panel.y && handle.bottom <= panel.bottom)
        }
        assertTrue(PanelResizing.handle(panel, PanelGrip.TOP, width, height).bottom < panel.y + 16f)
        // Color panels begin at a 6 dp inset, including accessibility-expanded swatches.
        assertTrue(PanelResizing.handle(panel, PanelGrip.LEFT, width, height).right <= panel.x + 6f)
        // Layer visibility and search controls are centered 15/14 dp from the frame.
        assertTrue(PanelResizing.handle(panel, PanelGrip.LEFT, width, height).right < panel.x + 15f)
        assertTrue(PanelResizing.handle(panel, PanelGrip.RIGHT, width, height).x > panel.right - 14f)
    }

    @Test fun everyEdgeAndCornerChangesOnlyItsAxesAndKeepsOppositeEdgesFixed() {
        for (id in listOf("pages", "brushes", "layers")) for (grip in PanelGrip.entries) {
            val result = PanelResizing.floating(original, id, grip, grip.x * 70f, grip.y * 60f, width, height)
            val a = original.floating.first { it.node.id == id }.rect
            val b = result.floating.first { it.node.id == id }.rect
            assertEquals(a.width + if (grip.x != 0) 70f else 0f, b.width, .01f)
            assertEquals(a.height + if (grip.y != 0) 60f else 0f, b.height, .01f)
            if (grip.x < 0) assertEquals(a.right, b.right) else assertEquals(a.x, b.x)
            if (grip.y < 0) assertEquals(a.bottom, b.bottom) else assertEquals(a.y, b.y)
            assertTrue(EditorPanelLayouts.isValid(result))
        }
    }

    @Test fun clampingDoesNotMoveTheOppositeEdgeAndReverseMotionIsImmediate() {
        val min = PanelResizing.floating(original, "layers", PanelGrip.TOP_LEFT, 2000f, 2000f, width, height).floating[2].rect
        assertEquals(530f, min.right); assertEquals(570f, min.bottom)
        assertEquals(72f, min.width); assertEquals(72f, min.height)
        val reverse = PanelResizing.floating(original, "layers", PanelGrip.TOP_LEFT, 10f, 10f, width, height).floating[2].rect
        assertEquals(370f, reverse.width); assertEquals(410f, reverse.height)
        val max = PanelResizing.floating(original, "layers", PanelGrip.TOP_LEFT, -2000f, -2000f, width, height).floating[2].rect
        assertEquals(0f, max.x); assertEquals(0f, max.y); assertEquals(530f, max.right); assertEquals(570f, max.bottom)
    }

    @Test fun continuousPerimeterMapsEverySideAndCornerWithoutClaimingTheInterior() {
        assertEquals(PanelGrip.TOP_LEFT, PanelResizing.gripAt(8f, 8f, 300f, 400f))
        assertEquals(PanelGrip.TOP_RIGHT, PanelResizing.gripAt(292f, 8f, 300f, 400f))
        assertEquals(PanelGrip.BOTTOM_LEFT, PanelResizing.gripAt(8f, 392f, 300f, 400f))
        assertEquals(PanelGrip.BOTTOM_RIGHT, PanelResizing.gripAt(292f, 392f, 300f, 400f))
        assertEquals(PanelGrip.LEFT, PanelResizing.gripAt(8f, 200f, 300f, 400f))
        assertEquals(PanelGrip.RIGHT, PanelResizing.gripAt(292f, 200f, 300f, 400f))
        assertEquals(PanelGrip.TOP, PanelResizing.gripAt(150f, 8f, 300f, 400f))
        assertEquals(PanelGrip.BOTTOM, PanelResizing.gripAt(150f, 392f, 300f, 400f))
        assertNull(PanelResizing.gripAt(150f, 200f, 300f, 400f))
    }

    @Test fun allDockSidesResizeAcrossAndAlongTheirEdgeAndRoundTrip() {
        for (edge in listOf(PanelEdge.LEFT, PanelEdge.RIGHT, PanelEdge.TOP, PanelEdge.BOTTOM)) {
            val docked = EditorPanelLayouts.move(original, "layers", null, PanelDropTarget("canvas", edge), original.floating[2].rect)
            fun rect(l: EditorPanelLayout) = EditorPanelLayouts.slots(l, width, height).first { it.node.id == "layers" }.rect
            val horizontal = edge in setOf(PanelEdge.LEFT, PanelEdge.RIGHT)
            val grip = when (edge) { PanelEdge.LEFT -> PanelGrip.RIGHT; PanelEdge.RIGHT -> PanelGrip.LEFT; PanelEdge.TOP -> PanelGrip.BOTTOM; else -> PanelGrip.TOP }
            val larger = PanelResizing.dock(docked, "layers", grip, grip.x * 90f, grip.y * 90f, width, height)
            assertEquals(90f, if (horizontal) rect(larger).width - rect(docked).width else rect(larger).height - rect(docked).height, .1f)
            val shorter = PanelResizing.dock(larger, "layers", if (horizontal) PanelGrip.BOTTOM else PanelGrip.RIGHT,
                if (horizontal) 0f else -180f, if (horizontal) -180f else 0f, width, height)
            assertEquals(180f, if (horizontal) rect(larger).height - rect(shorter).height else rect(larger).width - rect(shorter).width, .1f)
            assertTrue(EditorPanelLayouts.isValid(shorter))
            assertEquals(shorter, Json.decodeFromString<EditorPanelLayout>(Json.encodeToString(EditorPanelLayout.serializer(), shorter)))
            val regrow = PanelResizing.dock(shorter, "layers", if (horizontal) PanelGrip.BOTTOM else PanelGrip.RIGHT,
                if (horizontal) 0f else 70f, if (horizontal) 70f else 0f, width, height)
            assertEquals(70f, if (horizontal) rect(regrow).height - rect(shorter).height else rect(regrow).width - rect(shorter).width, .1f)
            val detached = EditorPanelLayouts.move(shorter, "layers", null, null, PanelRect(100f, 100f, 300f, 300f))
            assertTrue(detached.dock is PanelNode.Canvas)
        }
    }

    @Test fun minimumClampedSplitStartsAtItsActualBoundaryNotItsStoredFraction() {
        val dock = EditorPanelLayouts.move(original, "brushes", null, PanelDropTarget("canvas", PanelEdge.LEFT), original.floating[1].rect)
        val split = dock.dock as PanelNode.Split
        // A shared split stored below its minimum, and a dock whose own width is below it, both start at the minimum.
        for (narrow in listOf(dock.copy(dock = split.copy(fraction = .01f, dockSize = null)), dock.copy(dock = split.copy(dockSize = 10f)))) {
            val moved = PanelResizing.divider(narrow, split.id, 40f, 0f, width, height)
            val first = EditorPanelLayouts.slots(moved, width, height).first { it.node.id == "brushes" }.rect
            assertEquals(112f, first.width, .01f)
        }
    }

    @Test fun lockingDisablesGeometryChangesButNotSelectionOrVisibility() {
        val locked = original.copy(locked = true)
        assertEquals(locked, PanelResizing.floating(locked, "layers", PanelGrip.BOTTOM_RIGHT, 90f, 90f, width, height))
        assertEquals(locked, EditorPanelLayouts.move(locked, "layers", null, PanelDropTarget("canvas", PanelEdge.LEFT), original.floating[2].rect))
        assertTrue(EditorPanelLayouts.select(locked, EditorPanel.PROPERTIES).locked)
    }

    @Test fun emptyDockExtentCanReceiveAnotherPanelAndHiddenGroupsLeaveNoPhantomSpace() {
        val dock = EditorPanelLayouts.move(original, "layers", null, PanelDropTarget("canvas", PanelEdge.LEFT), original.floating[2].rect)
        val shorter = PanelResizing.dock(dock, "layers", PanelGrip.BOTTOM, 0f, -300f, width, height)
        val slots = EditorPanelLayouts.slots(shorter, width, height)
        val space = slots.first { it.node is PanelNode.Space }
        val target = EditorPanelLayouts.target(slots, emptySet(), space.rect.x + 2f, space.rect.y + 40f,
            original.floating.first().rect, 0f, 10f, width, height)
        assertEquals(space.node.id, target?.nodeId)
        val filled = EditorPanelLayouts.move(shorter, "pages", null, target, original.floating.first().rect)
        assertTrue(EditorPanelLayouts.isValid(filled))
        assertTrue(EditorPanel.PAGES in EditorPanelLayouts.members(filled.dock))
        assertFalse(EditorPanelLayouts.slots(filled, width, height).any { it.node is PanelNode.Space })
        val hidden = shorter.copy(open = shorter.open - EditorPanel.LAYERS)
        assertEquals(width, EditorPanelLayouts.slots(hidden, width, height).first { it.node is PanelNode.Canvas }.rect.width)
    }

    @Test fun temporarySmallViewportDoesNotOverwriteSavedDimensionsOrLosePanels() {
        val before = Json.encodeToString(EditorPanelLayout.serializer(), original)
        val compact = EditorPanelLayouts.slots(original, 500f, 260f)
        assertTrue(compact.all { it.rect.x >= 0 && it.rect.y >= 0 && it.rect.right <= 500.01f && it.rect.bottom <= 260.01f })
        assertEquals(before, Json.encodeToString(EditorPanelLayout.serializer(), original))
        val restored = EditorPanelLayouts.slots(original, width, height)
        assertEquals(original.floating[0].rect, restored.first { it.node.id == "pages" }.rect)
    }
}
