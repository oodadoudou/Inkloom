package app.inkloom

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.*
import app.inkloom.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Android long-press/move/cancel dispatch through the shared panel host. */
class EditorPanelsWindowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Test fun closingOneTabKeepsTheOtherPaletteInItsGroup() {
        lateinit var state: EditorPanelState
        compose.runOnUiThread {
            state=EditorPanelState(compose.activity)
            state.layout=EditorPanelLayout(open=setOf(EditorPanel.LAYERS,EditorPanel.PROPERTIES))
            compose.activity.setContent { MaterialTheme {
                EditorPanels(state,{},canvas={ Text("Canvas") },content={ Text("Body ${it.name}") })
            } }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Body LAYERS").assertExists()
        tap(point("Close layers panel"))
        compose.onNodeWithText("Body LAYERS").assertDoesNotExist()
        compose.onNodeWithText("Body PROPERTIES").assertExists()
        assertEquals(setOf(EditorPanel.PROPERTIES), state.layout.open)
    }
    @Test fun dragDockSplitCloseAndReopenKeepOneOwnerAndPosition() {
        lateinit var state:EditorPanelState
        compose.runOnUiThread {
            state=EditorPanelState(compose.activity)
            state.layout=EditorPanelLayout(open=EditorPanel.entries.toSet(),floating=EditorPanelLayout().floating.map { root ->
                root.copy(rect=when(root.node.id) {
                    "pages"->PanelRect(20f,20f,180f,300f)
                    "layers"->PanelRect(250f,20f,280f,300f)
                    "brushes"->PanelRect(560f,20f,330f,400f)
                    else->PanelRect(250f,400f,310f,250f)
                })
            })
            compose.activity.setContent { MaterialTheme {
                EditorPanels(state,{},canvas={ Text("Canvas") },content={ Text("Body ${it.name}") })
            } }
        }
        compose.waitForIdle()
        val before=state.layout
        val start=point("Move layers tab")
        drag(start,start+Offset(80f,100f),cancel=true)
        assertEquals(before,state.layout)
        drag(point("Move layers tab"),point("Move layers tab")+Offset(60f,90f))
        val moved=state.layout.floating.last()
        assertTrue(EditorPanel.LAYERS in EditorPanelLayouts.members(moved.node))
        assertNotEquals(before.floating[2].rect,moved.rect)
        compose.onAllNodesWithText("Body LAYERS").assertCountEquals(1)
        tap(point("Close layers panel"))
        compose.onNodeWithText("Body LAYERS").assertDoesNotExist()
        compose.runOnUiThread { state.show(EditorPanel.LAYERS,true) }
        compose.waitForIdle()
        assertEquals(moved.rect,state.layout.floating.last().rect)
        val layerPoint=point("Drag layers panel")
        drag(point("Move brushes tab"),layerPoint)
        val group=state.layout.floating.first { EditorPanel.LAYERS in EditorPanelLayouts.members(it.node) }
        assertTrue(EditorPanel.BRUSHES in EditorPanelLayouts.members(group.node))
        compose.onAllNodesWithText("Body BRUSHES").assertCountEquals(1)
        // Detach an individual tab into ordinary canvas space.
        val root=compose.onRoot().fetchSemanticsNode()
        val canvasCorner=root.boundsInWindow.bottomRight+root.positionOnScreen-root.positionInWindow-Offset(200f,350f)
        drag(point("Move brushes tab"),canvasCorner)
        assertEquals("After individual detach: ${state.layout}", 1,EditorPanelLayouts.members(state.layout.floating.last().node).size)
        val all=EditorPanelLayouts.members(state.layout.dock)+state.layout.floating.flatMap { EditorPanelLayouts.members(it.node) }
        assertEquals(EditorPanel.entries.size,all.size);assertEquals(EditorPanel.entries.size,all.toSet().size)
        compose.runOnUiThread { state.commit(state.layout) }
        assertEquals(state.layout,EditorPanelState(compose.activity).layout)
        val detachedBrushId = state.layout.floating.last().node.id
        val brush = point(hasContentDescription("Move panel group") and
            hasAnyAncestor(hasTestTag("editor-panel-$detachedBrushId")))
        drag(brush, Offset(5f, brush.y + 100f), holdMillis = 0)
        assertTrue("Brush group should dock from $brush; layout=${state.layout}",
            EditorPanel.BRUSHES in EditorPanelLayouts.members(state.layout.dock))
        compose.onAllNodesWithText("Body BRUSHES").assertCountEquals(1)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/editor-panels-window.png").close()
    }

    @Test fun stackedWindowTitleMovesTheWholeGroupAndOnlyAnExplicitTabGripDetaches() {
        lateinit var state: EditorPanelState
        var start = EditorPanelLayout(open = EditorPanel.entries.toSet())
        start = start.copy(floating = start.floating.map { root -> root.copy(rect = when (root.node.id) {
            "brushes" -> PanelRect(240f, 100f, 310f, 420f)
            "layers" -> PanelRect(600f, 100f, 280f, 340f)
            "colors" -> PanelRect(620f, 470f, 270f, 280f)
            else -> PanelRect(16f, 80f, 180f, 350f)
        }) })
        start = EditorPanelLayouts.move(start, "layers", null,
            PanelDropTarget("brushes", PanelEdge.BOTTOM), PanelRect(600f, 100f, 280f, 340f))
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity); state.layout = start
            compose.activity.setContent { MaterialTheme { ResizeHost(state) } }
        }
        compose.waitForIdle()
        val density = compose.activity.resources.displayMetrics.density
        val rootBefore = state.layout.floating.first { EditorPanel.LAYERS in EditorPanelLayouts.members(it.node) }
        val members = EditorPanelLayouts.members(rootBefore.node)
        val groupGrip = point(hasContentDescription("Move panel group") and
            hasAnyAncestor(hasTestTag("editor-panel-layers")))
        drag(groupGrip, groupGrip + Offset(68f * density, 48f * density), holdMillis = 0)
        val movedRoot = state.layout.floating.first { EditorPanel.LAYERS in EditorPanelLayouts.members(it.node) }
        assertEquals(members, EditorPanelLayouts.members(movedRoot.node))
        assertEquals(rootBefore.rect.x + 68f, movedRoot.rect.x, 3f)
        assertEquals(rootBefore.rect.y + 48f, movedRoot.rect.y, 3f)
        assertEquals(rootBefore.node.id, movedRoot.node.id)

        val beforeSlip = state.layout
        val tabGrip = point("Move layers tab")
        drag(tabGrip, tabGrip + Offset(12f * density, 0f), holdMillis = 0)
        assertEquals("A short accidental tab gesture must not alter the saved layout", beforeSlip, state.layout)

        val label = point("Drag layers panel") + Offset(9f * density, 0f)
        drag(label, label + Offset(56f * density, 0f), holdMillis = 0)
        assertEquals("A tab label swipe must not detach its panel", beforeSlip, state.layout)

        val deliberate = point("Move layers tab")
        drag(deliberate, deliberate + Offset(150f * density, 145f * density), holdMillis = 0)
        val detached = state.layout.floating.last()
        assertEquals(listOf(EditorPanel.LAYERS), EditorPanelLayouts.members(detached.node))
        assertEquals(members.toSet() - EditorPanel.LAYERS,
            EditorPanelLayouts.members(state.layout.floating.first { EditorPanel.BRUSHES in EditorPanelLayouts.members(it.node) }.node).toSet())
        val all = EditorPanelLayouts.members(state.layout.dock) + state.layout.floating.flatMap { EditorPanelLayouts.members(it.node) }
        assertEquals(EditorPanel.entries.size, all.size)
        assertEquals(EditorPanel.entries.toSet(), all.toSet())
    }

    @Test fun dockedStackTitleUndocksTheWholeGroupAndCancelPreservesItsTree() {
        lateinit var state: EditorPanelState
        var start = EditorPanelLayout(open = EditorPanel.entries.toSet())
        start = EditorPanelLayouts.move(start, "layers", null,
            PanelDropTarget("canvas", PanelEdge.RIGHT), PanelRect(0f, 0f, 280f, 450f))
        start = EditorPanelLayouts.move(start, "pages", null,
            PanelDropTarget("layers", PanelEdge.BOTTOM), PanelRect(0f, 0f, 180f, 350f))
        start = start.copy(floating = start.floating.map { root -> root.copy(rect =
            if (EditorPanel.BRUSHES in EditorPanelLayouts.members(root.node)) PanelRect(20f, 40f, 280f, 300f)
            else PanelRect(320f, 40f, 280f, 300f)) })
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity); state.layout = start
            compose.activity.setContent { MaterialTheme { ResizeHost(state) } }
        }
        compose.waitForIdle()
        val density = compose.activity.resources.displayMetrics.density
        val title = point(hasContentDescription("Move panel group") and
            hasAnyAncestor(hasTestTag("editor-panel-pages")))
        drag(title, title + Offset(-210f * density, 75f * density), cancel = true, holdMillis = 0)
        assertEquals(start, state.layout)
        val grip = point(hasContentDescription("Move panel group") and
            hasAnyAncestor(hasTestTag("editor-panel-pages")))
        drag(grip, grip + Offset(-210f * density, 75f * density), holdMillis = 0)
        val detached = state.layout.floating.last()
        assertEquals(setOf(EditorPanel.PAGES, EditorPanel.LAYERS, EditorPanel.PROPERTIES),
            EditorPanelLayouts.members(detached.node).toSet())
        assertFalse(EditorPanel.PAGES in EditorPanelLayouts.members(state.layout.dock))
        assertFalse(EditorPanel.LAYERS in EditorPanelLayouts.members(state.layout.dock))
        assertTrue(EditorPanelLayouts.isValid(state.layout))
    }
    @Test fun floatingPanelsResizeAllEdgesAndCornersWithoutDroppingMotion() {
        lateinit var state: EditorPanelState
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity)
            compose.activity.setContent { MaterialTheme {
                ResizeHost(state)
            } }
        }
        val density = compose.activity.resources.displayMetrics.density
        for ((panel, id) in listOf(EditorPanel.PAGES to "pages", EditorPanel.BRUSHES to "brushes", EditorPanel.LAYERS to "layers")) {
            for (grip in PanelGrip.entries) {
                val original = EditorPanelLayout(open = setOf(panel)).let { l -> l.copy(floating = l.floating.map {
                    it.copy(rect = PanelRect(100f, 100f, 380f, 360f))
                }) }
                compose.runOnUiThread { state.layout = original }; compose.waitForIdle()
                val suffix = grip.name.lowercase().replace('_', '-') + if (grip.corner) " corner" else " edge"
                val a = point("Resize ${panel.name.lowercase()} panel · $suffix")
                drag(a, a + Offset(grip.x * 45f * density, grip.y * 40f * density), stylus = grip.corner)
                val r = state.layout.floating.first { it.node.id == id }.rect
                assertEquals("$panel $grip width", 380f + if (grip.x != 0) 45f else 0f, r.width, 2f)
                assertEquals("$panel $grip height", 360f + if (grip.y != 0) 40f else 0f, r.height, 2f)
                assertEquals(state.layout, EditorPanelState(compose.activity).layout)
                compose.runOnUiThread { state.show(panel, false); state.show(panel, true) }
                assertEquals(r, state.layout.floating.first { it.node.id == id }.rect)
            }
        }
    }

    @Test fun longPressAnywhereOnThePerimeterResizesAndNarrowPanelsAutoCollapse() {
        lateinit var state:EditorPanelState
        val original=EditorPanelLayout(open=setOf(EditorPanel.LAYERS)).let { layout -> layout.copy(floating=layout.floating.map {
            if(it.node.id=="layers") it.copy(rect=PanelRect(100f,100f,380f,360f)) else it
        }) }
        compose.runOnUiThread {
            state=EditorPanelState(compose.activity);state.layout=original
            compose.activity.setContent { MaterialTheme { ResizeHost(state) } }
        }
        compose.waitForIdle()
        val density=compose.activity.resources.displayMetrics.density
        val node=compose.onNodeWithTag("editor-panel-layers").fetchSemanticsNode()
        val bounds=node.boundsInWindow
        val screenOffset=node.positionOnScreen-node.positionInWindow
        val anywhereOnRight=Offset(bounds.right-12f*density,bounds.center.y)+screenOffset
        drag(anywhereOnRight,anywhereOnRight+Offset(60f*density,0f),holdMillis=700)
        assertEquals(440f,state.layout.floating.first { it.node.id=="layers" }.rect.width,3f)

        val right=point("Resize layers panel · right edge")
        drag(right,right-Offset(500f*density,0f))
        assertEquals(72f,state.layout.floating.first { it.node.id=="layers" }.rect.width,3f)
        compose.onNodeWithText("Body LAYERS").assertDoesNotExist()
        compose.onNodeWithContentDescription("Layers").assertIsDisplayed()
        val expand=point("Resize layers panel · right edge")
        drag(expand,expand+Offset(180f*density,0f))
        compose.onNodeWithText("Body LAYERS").assertIsDisplayed()
    }

    @Test fun resizingCancelAndLayoutLockKeepOriginalGeometry() {
        lateinit var state: EditorPanelState
        val original = EditorPanelLayout(open = setOf(EditorPanel.LAYERS)).let { l -> l.copy(floating = l.floating.map {
            it.copy(rect = PanelRect(100f, 100f, 380f, 360f))
        }) }
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity); state.commit(original)
            compose.activity.setContent { MaterialTheme {
                ResizeHost(state)
            } }
        }
        val a = point("Resize layers panel · bottom edge")
        drag(a, a + Offset(0f, 100f), cancel = true)
        assertEquals(original, state.layout)
        assertEquals(original, EditorPanelState(compose.activity).layout)
        compose.runOnUiThread { state.commit(original.copy(locked = true)) }; compose.waitForIdle()
        compose.onNodeWithContentDescription("Resize layers panel · bottom edge").assertDoesNotExist()
        val before = state.layout
        val title = point("Drag layers panel")
        drag(title, title + Offset(60f, 90f))
        // Focusing a locked window may raise it, but cannot move it or rearrange its tabs.
        assertEquals(before.dock, state.layout.dock)
        assertEquals(before.floating.associate { it.node.id to it }, state.layout.floating.associate { it.node.id to it })
        assertTrue(state.layout.locked)
        tap(point("Close layers panel"))
        assertFalse(EditorPanel.LAYERS in state.layout.open)
    }

    @Test fun everyDockSideResizesBothAxesAndKeepsThePanelDocked() {
        lateinit var state: EditorPanelState
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity)
            compose.activity.setContent { MaterialTheme {
                ResizeHost(state)
            } }
        }
        val density = compose.activity.resources.displayMetrics.density
        val bounds = compose.onNodeWithTag("resize-host").fetchSemanticsNode().boundsInRoot
        fun rect() = EditorPanelLayouts.slots(state.layout, bounds.width / density, bounds.height / density).first { it.node.id == "layers" }.rect
        for (edge in listOf(PanelEdge.LEFT, PanelEdge.RIGHT, PanelEdge.TOP, PanelEdge.BOTTOM)) {
            val initial = EditorPanelLayouts.move(EditorPanelLayout(open = setOf(EditorPanel.LAYERS)), "layers", null,
                PanelDropTarget("canvas", edge), PanelRect(100f, 100f, 280f, 300f))
            compose.runOnUiThread { state.layout = initial }; compose.waitForIdle()
            val before = rect()
            val horizontal = edge == PanelEdge.LEFT || edge == PanelEdge.RIGHT
            val inner = when(edge) { PanelEdge.LEFT -> "right"; PanelEdge.RIGHT -> "left"; PanelEdge.TOP -> "bottom"; else -> "top" }
            val a = point("Resize layers panel · $inner edge")
            val sign = if (edge == PanelEdge.LEFT || edge == PanelEdge.TOP) 1 else -1
            drag(a, a + if (horizontal) Offset(sign * 50f * density, 0f) else Offset(0f, sign * 50f * density))
            val widened = rect()
            assertEquals("$edge inner edge", 50f, if(horizontal) widened.width-before.width else widened.height-before.height, 2f)
            val cross = if (horizontal) "bottom" else "right"
            val b = point("Resize layers panel · $cross edge")
            drag(b, b + if (horizontal) Offset(0f, -100f * density) else Offset(-100f * density, 0f))
            val shortened = rect()
            assertEquals("$edge cross axis", 100f, if(horizontal) widened.height-shortened.height else widened.width-shortened.width, 2f)
            assertTrue(EditorPanel.LAYERS in EditorPanelLayouts.members(state.layout.dock))
            assertTrue(EditorPanelLayouts.isValid(state.layout))
            val c = point("Resize layers panel · $cross edge")
            drag(c, c + if(horizontal) Offset(0f, 40f * density) else Offset(40f * density, 0f))
            assertEquals("$edge expand again", 40f, if(horizontal) rect().height-shortened.height else rect().width-shortened.width, 2f)
            val title = point(hasContentDescription("Move panel group") and
                hasAnyAncestor(hasTestTag("editor-panel-layers")))
            val host = compose.onNodeWithTag("resize-host").fetchSemanticsNode()
            val center = host.boundsInWindow.center + host.positionOnScreen - host.positionInWindow
            drag(title, center, holdMillis = 0)
            assertFalse("$edge title must still undock instead of resizing", EditorPanel.LAYERS in EditorPanelLayouts.members(state.layout.dock))
        }
    }

    @Test fun scrolledTabDetachesFromItsActualPointerPosition() {
        lateinit var state: EditorPanelState
        val original = EditorPanelLayout(open = setOf(EditorPanel.WHEEL, EditorPanel.PALETTES, EditorPanel.MIX, EditorPanel.NEARBY))
            .let { l -> l.copy(floating = l.floating.map { it.copy(rect = PanelRect(100f, 100f, 240f, 340f)) }) }
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity); state.layout = original
            compose.activity.setContent { MaterialTheme { ResizeHost(state) } }
        }
        compose.onNodeWithContentDescription("Drag nearby panel").performScrollTo()
        val start = point("Move nearby tab")
        val density = compose.activity.resources.displayMetrics.density
        drag(start, start + Offset(340f * density, 100f * density))
        val moved = state.layout.floating.last()
        assertEquals(listOf(EditorPanel.NEARBY), EditorPanelLayouts.members(moved.node))
        assertEquals("Scrolled tabs must not jump sideways", 440f, moved.rect.x, 3f)
        assertEquals(200f, moved.rect.y, 3f)
        compose.onAllNodesWithText("Body NEARBY").assertCountEquals(1)
    }

    @Test fun swipingCrowdedTabStripScrollsWithoutDetachingAPalette() {
        lateinit var state: EditorPanelState
        val original = EditorPanelLayout(open = setOf(EditorPanel.WHEEL, EditorPanel.PALETTES, EditorPanel.MIX, EditorPanel.NEARBY))
            .let { layout -> layout.copy(floating = layout.floating.map { it.copy(rect = PanelRect(100f, 100f, 240f, 340f)) }) }
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity); state.layout = original
            compose.activity.setContent { MaterialTheme { ResizeHost(state) } }
        }
        val before = state.layout
        val density = compose.activity.resources.displayMetrics.density
        val start = point("Drag wheel panel") + Offset(12f * density, 0f)
        drag(start, start - Offset(150f * density, 0f), holdMillis = 0)
        assertEquals("Scrolling tabs must not move or detach the palette group", before, state.layout)
        compose.onNodeWithContentDescription("Drag nearby panel").assertIsDisplayed()
    }

    @Test fun nestedDockDividerResizesBothNeighborsAndCancelRestoresTheSplit() {
        lateinit var state: EditorPanelState
        var initial = EditorPanelLayout(open = setOf(EditorPanel.PAGES, EditorPanel.LAYERS))
        initial = EditorPanelLayouts.move(initial, "layers", null, PanelDropTarget("canvas", PanelEdge.RIGHT), PanelRect(0f,0f,300f,400f))
        initial = EditorPanelLayouts.move(initial, "pages", null, PanelDropTarget("layers", PanelEdge.BOTTOM), PanelRect(0f,0f,200f,200f))
        compose.runOnUiThread {
            state = EditorPanelState(compose.activity); state.commit(initial)
            compose.activity.setContent { MaterialTheme { ResizeHost(state) } }
        }
        val density = compose.activity.resources.displayMetrics.density
        val host = compose.onNodeWithTag("resize-host").fetchSemanticsNode().boundsInRoot
        fun slots() = EditorPanelLayouts.slots(state.layout, host.width/density, host.height/density)
        fun heights() = slots().filter { it.node.id in setOf("layers", "pages") }.associate { it.node.id to it.rect.height }
        val before = heights()
        val edge = point("Resize layers panel · bottom edge")
        drag(edge, edge + Offset(0f, 60f*density))
        val after = heights()
        assertEquals(60f, after.getValue("layers")-before.getValue("layers"), 2f)
        assertEquals(-60f, after.getValue("pages")-before.getValue("pages"), 2f)
        val saved = state.layout
        val again = point("Resize layers panel · bottom edge")
        drag(again, again + Offset(0f, -40f*density), cancel = true)
        assertEquals(saved, state.layout)
    }

    @Composable private fun ResizeHost(state: EditorPanelState) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxSize().testTag("resize-host")) {
                EditorPanels(state, {}, canvas = { Text("Canvas") }, content = { Text("Body ${it.name}") })
            }
        }
    }

    private fun point(label:String):Offset {
        val node=compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
        return node.boundsInWindow.center+node.positionOnScreen-node.positionInWindow
    }
    private fun point(matcher: SemanticsMatcher): Offset {
        val node=compose.onNode(matcher).assertIsDisplayed().fetchSemanticsNode()
        return node.boundsInWindow.center+node.positionOnScreen-node.positionInWindow
    }
    private fun tap(point:Offset) { val time=SystemClock.uptimeMillis();event(0,point,time);event(1,point,time);compose.waitForIdle() }
    private fun drag(a:Offset,b:Offset,cancel:Boolean=false,stylus:Boolean=false,holdMillis:Long=650) {
        val time=SystemClock.uptimeMillis();event(0,a,time,stylus);SystemClock.sleep(holdMillis)
        for(i in 1..24) { event(2,a+(b-a)*(i/24f),time,stylus);SystemClock.sleep(16) }
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/panel-during-drag.png").close()
        event(if(cancel) 3 else 1,b,time,stylus);compose.waitForIdle()
    }
    private fun event(action:Int,p:Offset,down:Long,stylus:Boolean=false) {
        val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,
            arrayOf(MotionEvent.PointerProperties().apply { id=0;toolType=if(stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { x=p.x;y=p.y;pressure=1f }),
            0,0,1f,1f,0,0,if(stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN,0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(e,true)) } finally { e.recycle() }
    }
}
