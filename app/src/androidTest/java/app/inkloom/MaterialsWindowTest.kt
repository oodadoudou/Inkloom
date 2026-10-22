package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Materials: register a layer and a page, then place the image and add a page from the template. */
class MaterialsWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun registeredMaterialsCanBePlacedAndAddedAsPages() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val bounds = pageScreenBounds(view)
        stroke(Offset(bounds.left + bounds.width() * .2f, bounds.top + bounds.height() * .4f),
            Offset(bounds.left + bounds.width() * .5f, bounds.top + bounds.height() * .5f))
        compose.waitUntil(10_000) { !empty(view) && !view.hasPendingPaintPublicationForMerge() }
        SystemClock.sleep(1500) // let the stroke reach the saved page
        openMaterials()
        compose.onNodeWithContentDescription("Material name").performTextReplacement("Swoosh")
        compose.onNodeWithText("Current layer").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Registered \"Swoosh\"").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Material name").performTextReplacement("Layout")
        compose.onNodeWithText("This page").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Layout").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Place").performClick()
        compose.waitUntil(10_000) { model().currentPage()?.layers?.any { it.name == "Swoosh" } == true }
        val page = model().currentPage()!!
        val layer = page.layers.first { it.name == "Swoosh" }
        assertTrue(page.elements.any { it is app.inkloom.document.PageElement.Image && it.layerId == layer.id })

        val pages = model().currentDocument()!!.pages.size
        openMaterials()
        compose.onNodeWithText("Add page").performClick()
        compose.waitUntil(10_000) { model().currentDocument()?.pages?.size == pages + 1 }
        val added = model().currentPage()!!
        assertNotEquals(page.id, added.id)
        assertEquals(page.layers.first { it.id == app.inkloom.document.LayerIds.INK }.rasterTileSurface,
            added.layers.first { it.id == app.inkloom.document.LayerIds.INK }.rasterTileSurface)
    }

    private fun openMaterials() {
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Notebook").performClick()
        compose.onNodeWithText("Materials…").performScrollTo().performClick()
    }

    private fun model() = androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]

    /** The page's on-screen rectangle at the fit-to-window view. */
    private fun pageScreenBounds(view: InkCanvasView): android.graphics.RectF {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        return android.graphics.RectF(location[0].toFloat(), location[1].toFloat(), location[0] + view.width.toFloat(), location[1] + view.height.toFloat())
    }

    private fun stroke(start: Offset, end: Offset) {
        val down = SystemClock.uptimeMillis()
        motion(MotionEvent.ACTION_DOWN, start, down)
        for (i in 1..20) { SystemClock.sleep(10); motion(MotionEvent.ACTION_MOVE, start + (end - start) * (i / 20f), down) }
        motion(MotionEvent.ACTION_UP, end, down)
        compose.waitForIdle()
    }

    private fun motion(action: Int, point: Offset, down: Long) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun empty(view: InkCanvasView): Boolean {
        val snapshot = view.activeRasterSnapshotForTest() ?: return true
        return snapshot.tileInfo.none { it.nonTransparentPixelCount > 0 }
    }

    private fun canvas(): InkCanvasView {
        var result: InkCanvasView? = null
        compose.waitUntil(5_000) {
            instrumentation.runOnMainSync {
                fun visit(view: View) {
                    if (view is InkCanvasView && view.isShown) result = view
                    else if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(compose.activity.window.decorView)
            }
            result != null
        }
        return requireNotNull(result)
    }
}
