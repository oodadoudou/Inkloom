package app.inkloom.settings

import app.inkloom.ui.PalettePopups
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSP's palette dock display (hide, icons with pop-ups) and minimizing a palette shown by itself. */
class PaletteDockTest {
    private val width = 1280f
    private val height = 760f
    private val painting = BuiltInWorkspaces.snapshot(BuiltInWorkspace.PAINTING).layout
    private fun slots(layout: EditorPanelLayout) = EditorPanelLayouts.slots(layout, width, height)
    private fun rect(layout: EditorPanelLayout, id: String) = slots(layout).first { it.node.id == id }.rect

    @Test fun eachDockColumnHasAHeaderAboveItsPalettes() {
        val columns = EditorPanelLayouts.columns(painting.dock)
        assertEquals(listOf(PanelEdge.RIGHT), columns.map { it.second })
        val column = slots(painting).single { it.column != null }
        assertEquals(DockDisplay.NORMAL, column.column!!.display)
        val header = column.column.header!!
        assertEquals(0f, header.y, .01f)
        assertEquals(EditorPanelLayouts.dockHeaderHeight, column.rect.y, .01f)
        assertEquals(column.rect.x, header.x, .01f)
        assertEquals(column.rect.width, header.width, .01f)
        // The palettes inside start below the header.
        assertEquals(EditorPanelLayouts.dockHeaderHeight, rect(painting, "colors").y, .01f)
    }

    @Test fun iconAndHiddenDocksKeepAFixedStripAndGiveTheRestToTheCanvas() {
        val columnId = EditorPanelLayouts.columns(painting.dock).single().first.id
        val icons = EditorPanelLayouts.setDockDisplay(painting, "layers", DockDisplay.ICONS)
        assertEquals(setOf(columnId), icons.iconDocks)
        val strip = slots(icons).single { it.collapsed }
        assertEquals(EditorPanelLayouts.iconDockWidth, strip.rect.width, .01f)
        assertEquals(width, strip.rect.right, .01f)
        assertEquals(0f, strip.rect.y, .01f)
        // Its palettes are not laid out: only the strip shows, and the canvas grows into the space.
        assertTrue(slots(icons).none { it.node.id == "colors" || it.node.id == "layers" })
        assertEquals(width - EditorPanelLayouts.iconDockWidth - 4f, rect(icons, "canvas").width, .01f)
        assertTrue(EditorPanelLayouts.inCollapsedDock(icons, EditorPanel.WHEEL))
        assertTrue(EditorPanelLayouts.inCollapsedDock(icons, EditorPanel.LAYERS))
        assertFalse(EditorPanelLayouts.inCollapsedDock(icons, EditorPanel.BRUSHES))
        assertEquals(listOf(EditorPanel.WHEEL, EditorPanel.LAYERS), EditorPanelLayouts.members(strip.node))

        val hidden = EditorPanelLayouts.setDockDisplay(icons, columnId, DockDisplay.HIDDEN)
        assertEquals(emptySet<String>(), hidden.iconDocks)
        assertEquals(EditorPanelLayouts.hiddenDockWidth, slots(hidden).single { it.collapsed }.rect.width, .01f)
        assertEquals(DockDisplay.HIDDEN, EditorPanelLayouts.dockDisplay(hidden, "colors"))

        // Showing it again restores the palettes exactly where they were.
        val restored = EditorPanelLayouts.setDockDisplay(hidden, "colors", DockDisplay.NORMAL)
        assertEquals(slots(painting), slots(restored))
        assertTrue(EditorPanelLayouts.isValid(restored))
    }

    @Test fun dockDisplayIsADisplayChoiceThatALockedLayoutAllowsAndThatSurvivesSaving() {
        val locked = painting.copy(locked = true)
        val icons = EditorPanelLayouts.setDockDisplay(locked, "layers", DockDisplay.ICONS)
        assertEquals(1, icons.iconDocks.size)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        assertEquals(icons, json.decodeFromString<EditorPanelLayout>(json.encodeToString(EditorPanelLayout.serializer(), icons)))
        // Layouts saved before these settings existed load with every dock shown in full.
        val old = json.encodeToString(EditorPanelLayout.serializer(), painting).replace(Regex(",\"(iconDocks|hiddenDocks|minimized)\":\\[]"), "")
        assertFalse(old.contains("iconDocks"))
        assertEquals(painting, json.decodeFromString<EditorPanelLayout>(old))
        // A floating window is not a dock.
        assertEquals(painting, EditorPanelLayouts.setDockDisplay(painting, "brushes", DockDisplay.ICONS))
    }

    @Test fun aCollapsedDockIsNeitherADropTargetNorResizable() {
        val icons = EditorPanelLayouts.setDockDisplay(painting, "layers", DockDisplay.ICONS)
        val all = slots(icons)
        val strip = all.single { it.collapsed }
        assertNull(EditorPanelLayouts.target(all, emptySet(), strip.rect.x + 20f, 300f, PanelRect(0f, 0f, 200f, 200f), 0f, 0f, width, height))
        val canvasSplit = all.first { it.node is PanelNode.Split }
        assertEquals(icons, PanelResizing.divider(icons, canvasSplit.node.id, -120f, 0f, width, height))
    }

    @Test fun displayChoicesAreDroppedWhenTheirDockOrWindowIsGone() {
        val columnId = EditorPanelLayouts.columns(painting.dock).single().first.id
        val icons = EditorPanelLayouts.setDockDisplay(painting, "layers", DockDisplay.ICONS)
        // Taking layers out turns the column into the colours group alone: a different dock.
        val moved = EditorPanelLayouts.move(icons, "layers", null, null, PanelRect(100f, 100f, 260f, 300f))
        assertFalse(columnId in moved.iconDocks)
        assertTrue(moved.iconDocks.isEmpty())
        val docked = EditorPanelLayouts.move(moved, "pages", null, PanelDropTarget("canvas", PanelEdge.LEFT), PanelRect(0f, 0f, 200f, 500f))
        assertEquals(DockDisplay.NORMAL, EditorPanelLayouts.dockDisplay(docked, "pages"))
    }

    @Test fun aWindowShownByItselfMinimizesToItsTitleBarAndKeepsItsSize() {
        val layout = EditorPanelLayout().copy(open = setOf(EditorPanel.LAYERS, EditorPanel.BRUSHES))
        val stored = layout.floating.first { it.node.id == "layers" }.rect
        val minimized = EditorPanelLayouts.toggleMinimized(layout, "layers")
        assertEquals(setOf("layers"), minimized.minimized)
        val slot = slots(minimized).first { it.node.id == "layers" }
        assertEquals(EditorPanelLayouts.titleHeight, slot.rect.height, .01f)
        assertTrue(EditorPanelLayouts.minimizable(slot))
        assertEquals(stored, minimized.floating.first { it.node.id == "layers" }.rect)
        assertEquals(layout, EditorPanelLayouts.toggleMinimized(minimized, "layers"))
        // Docked palettes use the dock's own controls instead.
        assertEquals(painting, EditorPanelLayouts.toggleMinimized(painting, "layers"))
        assertFalse(EditorPanelLayouts.minimizable(slots(painting).first { it.node.id == "layers" }))
        // Docking it shows it in full again.
        val docked = EditorPanelLayouts.move(minimized, "layers", null, PanelDropTarget("canvas", PanelEdge.RIGHT), stored)
        assertTrue(docked.minimized.isEmpty())
    }

    @Test fun theMinimizeButtonTakesItsShareOfTheTitleRowForTabsAndDropPreview() {
        val tabs = PanelNode.Tabs("t", listOf(EditorPanel.LAYERS, EditorPanel.PROPERTIES))
        val plain = EditorPanelLayouts.tabWidth(tabs, 300f)
        val alone = EditorPanelLayouts.tabWidth(tabs, 300f, minimizable = true)
        assertEquals(EditorPanelLayouts.minimizeWidth / 2f, plain - alone, .01f)
        val x = EditorPanelLayouts.tabInsertionX(tabs, 1, 0f, 300f, minimizable = true)
        assertEquals(1, EditorPanelLayouts.tabInsertionIndex(tabs, x, 0f, 300f, minimizable = true))
    }

    @Test fun aPopUpOpensBesideItsIconTowardTheCanvasAndStaysInTheWindow() {
        val right = PanelRect(width - 48f, 0f, 48f, height)
        val popup = PalettePopups.place(right, PanelEdge.RIGHT, 1, 300f, 400f, width, height)
        assertEquals(right.x - 4f, popup.right, .01f)
        assertEquals(2 * PalettePopups.controlSize + 4f + PalettePopups.iconSize - 8f, popup.y, .01f)
        val left = PalettePopups.place(PanelRect(0f, 0f, 48f, height), PanelEdge.LEFT, 0, 300f, 400f, width, height)
        assertEquals(52f, left.x, .01f)
        val bottom = PalettePopups.place(PanelRect(0f, height - 48f, width, 48f), PanelEdge.BOTTOM, 2, 300f, 400f, width, height)
        assertEquals(height - 48f - 4f, bottom.bottom, .01f)
        // A late icon near the bottom keeps the pop-up on screen.
        val low = PalettePopups.place(right, PanelEdge.RIGHT, 30, 300f, 400f, width, height)
        assertEquals(height, low.bottom, .01f)
        // A hidden dock has no icons: the pop-up opens near the strip's top.
        assertEquals(8f, PalettePopups.place(PanelRect(width - 20f, 0f, 20f, height), PanelEdge.RIGHT, -1, 300f, 400f, width, height).y, .01f)
    }

    @Test fun aDockKeepsItsOwnWidthWhenTheWorkspaceGetsWiderAndRemembersAResize() {
        // Painting docks colours and layers in a 320 dp column beside the canvas.
        fun column(layout: EditorPanelLayout, w: Float) = EditorPanelLayouts.slots(layout, w, height).single { it.column != null }.rect
        assertEquals(320f, column(painting, 1280f).width, .01f)
        assertEquals(320f, column(painting, 1900f).width, .01f)
        assertEquals(1900f, column(painting, 1900f).right, .01f)
        val split = slots(painting).first { it.node is PanelNode.Split && EditorPanelLayouts.containsCanvas(it.node) }.node.id
        val wider = PanelResizing.divider(painting, split, -80f, 0f, width, height)
        assertEquals(400f, column(wider, 1280f).width, .1f)
        assertEquals(400f, column(wider, 1900f).width, .1f)
        // A narrow window still leaves the canvas its minimum width.
        assertTrue(column(painting, 450f).width <= 450f - EditorPanelLayouts.minimumWidth(PanelNode.Canvas()) - 4f + .01f)
        // Layouts saved before docks had their own size keep sharing by fraction.
        val legacy = EditorPanelLayouts.update(painting, split) { (it as PanelNode.Split).copy(dockSize = null) }
        assertEquals(1900f * .25f - 2f, column(legacy, 1900f).width, .5f)
        // A bottom dock keeps its height.
        val bottom = EditorPanelLayouts.move(EditorPanelLayout().copy(open = setOf(EditorPanel.LAYERS)), "layers", null,
            PanelDropTarget("canvas", PanelEdge.BOTTOM), PanelRect(0f, 0f, 400f, 240f))
        assertEquals(240f, EditorPanelLayouts.slots(bottom, width, 1100f).single { it.column != null }.let { it.rect.height + it.column!!.header!!.height }, .01f)
    }

    @Test fun aTabPaletteExpandsItsIconDockForNowWithoutChangingTheSavedDisplay() {
        val icons = EditorPanelLayouts.setTabPalette(EditorPanelLayouts.setDockDisplay(painting, "layers", DockDisplay.ICONS), EditorPanel.WHEEL, true)
        assertEquals(setOf(EditorPanel.WHEEL), icons.tabPalettes)
        val column = EditorPanelLayouts.dockColumnOf(icons, EditorPanel.WHEEL)!!.id
        // Expanded, the dock lays out as in full and its palettes are no longer collapsed; the saved display stays icons.
        assertEquals(EditorPanelLayouts.slots(painting, width, height), EditorPanelLayouts.slots(icons, width, height, expanded = column))
        assertFalse(EditorPanelLayouts.inCollapsedDock(icons, EditorPanel.LAYERS, expanded = column))
        assertTrue(EditorPanelLayouts.inCollapsedDock(icons, EditorPanel.LAYERS))
        assertEquals(DockDisplay.ICONS, EditorPanelLayouts.dockDisplay(icons, "layers"))
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        assertEquals(icons, json.decodeFromString<EditorPanelLayout>(json.encodeToString(EditorPanelLayout.serializer(), icons)))
        assertTrue(EditorPanelLayouts.setTabPalette(icons, EditorPanel.WHEEL, false).tabPalettes.isEmpty())
    }

    @Test fun resizingTheBottomDockStillMovesItsBoundaryBelowTheHeader() {
        val docked = EditorPanelLayouts.move(EditorPanelLayout().copy(open = setOf(EditorPanel.LAYERS)), "layers", null,
            PanelDropTarget("canvas", PanelEdge.BOTTOM), PanelRect(0f, 0f, 400f, 240f))
        val before = rect(docked, "layers")
        val header = slots(docked).single { it.column != null }.column!!.header!!
        assertEquals(before.y, header.bottom, .01f)
        val taller = PanelResizing.dock(docked, "layers", PanelGrip.TOP, 0f, -60f, width, height)
        assertEquals(60f, rect(taller, "layers").height - before.height, .1f)
    }
}
