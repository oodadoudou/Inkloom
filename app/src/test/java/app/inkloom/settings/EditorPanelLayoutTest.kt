package app.inkloom.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class EditorPanelLayoutTest {
    private val rect = PanelRect(200f,100f,360f,400f)
    private val initial = EditorPanelLayout(open=EditorPanel.entries.toSet())
    private fun valid(layout: EditorPanelLayout) {
        val all=EditorPanelLayouts.members(layout.dock)+layout.floating.flatMap { EditorPanelLayouts.members(it.node) }
        assertEquals(EditorPanel.entries.toSet(),all.toSet()); assertEquals(EditorPanel.entries.size,all.size)
        assertTrue(EditorPanelLayouts.containsCanvas(layout.dock))
        assertFalse(layout.floating.any { EditorPanelLayouts.containsCanvas(it.node) })
    }
    @Test fun detachCombineCloseRestoreAndSerializePreserveEveryPanel() {
        val docked=EditorPanelLayouts.move(initial,"layers",null,PanelDropTarget("canvas",PanelEdge.RIGHT),rect)
        valid(docked)
        val combined=EditorPanelLayouts.move(docked,"brushes",EditorPanel.BRUSHES,PanelDropTarget("layers",PanelEdge.TAB),rect)
        valid(combined)
        assertEquals(listOf(EditorPanel.BRUSHES,EditorPanel.LAYERS,EditorPanel.PROPERTIES),(EditorPanelLayouts.node(combined,"layers") as PanelNode.Tabs).panels)
        val hidden=combined.copy(open=combined.open-setOf(EditorPanel.LAYERS,EditorPanel.BRUSHES,EditorPanel.PROPERTIES))
        assertEquals(1000f,EditorPanelLayouts.slots(hidden,1000f,800f).first { it.node is PanelNode.Canvas }.rect.width)
        val restored=Json.decodeFromString(EditorPanelLayout.serializer(),Json.encodeToString(EditorPanelLayout.serializer(),combined))
        assertEquals(combined,restored)
        val detached=EditorPanelLayouts.move(restored,"layers",EditorPanel.BRUSHES,null,rect)
        valid(detached); assertEquals(rect,detached.floating.last().rect)
    }
    @Test fun titleReorderAndFloatingStackCannotLoseOrDuplicatePanels() {
        val reordered=EditorPanelLayouts.move(initial,"colors",EditorPanel.WHEEL,PanelDropTarget("colors",PanelEdge.TAB,3),rect)
        assertEquals(EditorPanel.WHEEL,(EditorPanelLayouts.node(reordered,"colors") as PanelNode.Tabs).panels.last())
        val stacked=EditorPanelLayouts.move(reordered,"layers",null,PanelDropTarget("colors",PanelEdge.BOTTOM),rect)
        valid(stacked)
        val root=stacked.floating.first { EditorPanel.LAYERS in EditorPanelLayouts.members(it.node) }
        assertEquals(stacked,EditorPanelLayouts.move(stacked,root.node.id,null,PanelDropTarget("colors",PanelEdge.TAB),rect))
        valid(EditorPanelLayouts.move(stacked,root.node.id,null,null,rect.moved(30f,50f)))
    }
    @Test fun interiorOverlapDoesNotDockButRealBottomEdgeDoes() {
        val slots=listOf(PanelSlot(PanelNode.Canvas(),PanelRect(0f,0f,1000f,800f),"canvas",false))
        assertNull(EditorPanelLayouts.target(slots,emptySet(),500f,500f,rect,10f,10f,1000f,800f))
        assertEquals(PanelEdge.BOTTOM,EditorPanelLayouts.target(slots,emptySet(),500f,500f,PanelRect(200f,490f,300f,300f),0f,10f,1000f,800f)?.edge)
        val floating=PanelSlot(PanelNode.Tabs("target",listOf(EditorPanel.MIX)),rect,"target",true)
        assertEquals(PanelEdge.TAB,EditorPanelLayouts.target(slots+floating,emptySet(),250f,110f,rect,0f,1f,1000f,800f)?.edge)
        assertNull(EditorPanelLayouts.target(slots+floating,emptySet(),250f,200f,rect,0f,1f,1000f,800f))
    }
    @Test fun hiddenTabsRetainOrderAndActiveChoice() {
        val selected=EditorPanelLayouts.select(initial,EditorPanel.MIX)
        val hidden=selected.copy(open=selected.open-EditorPanel.MIX)
        assertEquals(EditorPanel.WHEEL,(EditorPanelLayouts.slots(hidden,1000f,800f).first { it.node.id=="colors" }.node as PanelNode.Tabs).active)
        assertEquals(EditorPanel.MIX,(EditorPanelLayouts.slots(selected,1000f,800f).first { it.node.id=="colors" }.node as PanelNode.Tabs).active)
    }    @Test fun keyboardShrinksContentsWithoutMovingTheTitleAndRestoresSavedSize() {
        val saved=PanelRect(300f,18f,288f,620f)
        val compact=saved.bounded(1280f,300f)
        assertEquals(18f,compact.y); assertEquals(282f,compact.height)
        assertEquals(saved,saved.bounded(1280f,900f))
    }
    @Test fun mixedWidthTabsUseTheSameGeometryForHitTestingAndDropPreview() {
        val tabs = PanelNode.Tabs("tools", listOf(EditorPanel.BRUSHES, EditorPanel.SUBTOOLS, EditorPanel.PROPERTIES))
        val secondStart = EditorPanelLayouts.tabInsertionX(tabs, 1, 100f, 330f)
        val thirdStart = EditorPanelLayouts.tabInsertionX(tabs, 2, 100f, 330f)
        assertEquals(188f, secondStart)
        assertEquals(266f, thirdStart)
        assertEquals(1, EditorPanelLayouts.tabInsertionIndex(tabs, secondStart + 20f, 100f, 330f))
        assertEquals(2, EditorPanelLayouts.tabInsertionIndex(tabs, thirdStart + 20f, 100f, 330f))
        assertEquals(3, EditorPanelLayouts.tabInsertionIndex(tabs, 360f, 100f, 330f))
    }

    @Test fun panelContentCollapsesBeforeItCanBlockAUserResize() {
        assertEquals(72f, EditorPanelLayouts.minimumWidth(PanelNode.Tabs("brush", listOf(EditorPanel.BRUSHES))))
        assertEquals(72f, EditorPanelLayouts.minimumHeight(PanelNode.Tabs("layers", listOf(EditorPanel.LAYERS))))
        assertTrue(EditorPanelLayouts.compactThreshold(EditorPanel.BRUSHES) > EditorPanelLayouts.minimumWidth(PanelNode.Tabs("brush", listOf(EditorPanel.BRUSHES))))
    }

    @Test fun everyVisibleBlockEdgeIsAFullSplitTargetAndTitleRemainsATabTarget() {
        val target = PanelSlot(PanelNode.Tabs("target", listOf(EditorPanel.LAYERS)), PanelRect(100f,100f,300f,400f), "target", true)
        val dragged = PanelRect(500f,100f,200f,200f)
        assertEquals(PanelEdge.LEFT, EditorPanelLayouts.target(listOf(target), emptySet(), 126f, 300f, dragged, -1f, 0f, 900f,700f)?.edge)
        assertEquals(PanelEdge.RIGHT, EditorPanelLayouts.target(listOf(target), emptySet(), 374f, 300f, dragged, 1f, 0f, 900f,700f)?.edge)
        assertEquals(PanelEdge.TOP, EditorPanelLayouts.target(listOf(target), emptySet(), 250f, 140f, dragged, 0f, -1f, 900f,700f)?.edge)
        assertEquals(PanelEdge.BOTTOM, EditorPanelLayouts.target(listOf(target), emptySet(), 250f, 460f, dragged, 0f, 1f, 900f,700f)?.edge)
        assertEquals(PanelEdge.TAB, EditorPanelLayouts.target(listOf(target), emptySet(), 250f, 112f, dragged, 0f, 1f, 900f,700f)?.edge)
    }

}
