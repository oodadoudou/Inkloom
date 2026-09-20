package app.inkloom

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
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
    @Test fun dragDockSplitCloseAndReopenKeepOneOwnerAndPosition() {
        lateinit var state:EditorPanelState
        compose.runOnUiThread {
            state=EditorPanelState(compose.activity,AppSettings())
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
        val start=point("Drag layers panel")
        drag(start,start+Offset(80f,100f),cancel=true)
        assertEquals(before,state.layout)
        drag(point("Drag layers panel"),point("Drag layers panel")+Offset(60f,90f))
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
        drag(point("Drag brushes panel"),layerPoint)
        val group=state.layout.floating.first { EditorPanel.LAYERS in EditorPanelLayouts.members(it.node) }
        assertTrue(EditorPanel.BRUSHES in EditorPanelLayouts.members(group.node))
        compose.onAllNodesWithText("Body BRUSHES").assertCountEquals(1)
        // Detach an individual tab into ordinary canvas space.
        val root=compose.onRoot().fetchSemanticsNode()
        val canvasCorner=root.boundsInWindow.bottomRight+root.positionOnScreen-root.positionInWindow-Offset(200f,350f)
        drag(point("Drag brushes panel"),canvasCorner)
        assertEquals("After individual detach: ${state.layout}", 1,EditorPanelLayouts.members(state.layout.floating.last().node).size)
        val all=EditorPanelLayouts.members(state.layout.dock)+state.layout.floating.flatMap { EditorPanelLayouts.members(it.node) }
        assertEquals(EditorPanel.entries.size,all.size);assertEquals(EditorPanel.entries.size,all.toSet().size)
        compose.runOnUiThread { state.commit(state.layout) }
        assertEquals(state.layout,EditorPanelState(compose.activity,AppSettings()).layout)
        val brush = point("Drag brushes panel")
        drag(brush, Offset(5f, brush.y + 100f))
        assertTrue(EditorPanel.BRUSHES in EditorPanelLayouts.members(state.layout.dock))
        compose.onAllNodesWithText("Body BRUSHES").assertCountEquals(1)
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/editor-panels-window.png").close()
    }
    private fun point(label:String):Offset {
        val node=compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode()
        return node.boundsInWindow.center+node.positionOnScreen-node.positionInWindow
    }
    private fun tap(point:Offset) { val time=SystemClock.uptimeMillis();event(0,point,time);event(1,point,time);compose.waitForIdle() }
    private fun drag(a:Offset,b:Offset,cancel:Boolean=false) {
        val time=SystemClock.uptimeMillis();event(0,a,time);SystemClock.sleep(500)
        for(i in 1..24) { event(2,a+(b-a)*(i/24f),time);SystemClock.sleep(16) }
        instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/panel-during-drag.png").close()
        event(if(cancel) 3 else 1,b,time);compose.waitForIdle()
    }
    private fun event(action:Int,p:Offset,down:Long) {
        val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,p.x,p.y,0)
        e.source=InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(e,true)) } finally { e.recycle() }
    }
}
