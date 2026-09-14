package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class LegacyFillWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val fixture = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            val assets = DocumentAssetService(context)
            val path = assets.saveFillMask(MaskTileSurface(64, 64, initialDefaultAlpha = 255).snapshot())
            val fill = PageElement.Fill(x = 0f, y = 0f, width = 400f, height = 400f,
                colorArgb = 0xFF111111, maskAssetPath = path)
            val store = FileDocumentStore(context.filesDir)
            store.save(InkDocument(id = "legacy-fill-window", title = "Legacy fill", pages = listOf(
                InkPage(width = 400f, height = 400f, elements = listOf(fill)))))
            assets.releaseAssetReservation(path)
            try { base.evaluate() } finally { store.deletePermanently("legacy-fill-window") }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun openingOldFillAllowsEraserUndoAndDurableReopen() {
        compose.onNodeWithContentDescription("Notebook Legacy fill. Long press to move.").performClick()
        val view = canvas()
        val store = FileDocumentStore(instrumentation.targetContext.filesDir)
        assertTrue(requireNotNull(store.get("legacy-fill-window")).pages.single().elements.isEmpty())
        fun centerPixel(canvas: InkCanvasView): Int {
            val pixels = requireNotNull(canvas.activeRasterSnapshotForTest())
            return pixels.pixelAt(pixels.logicalWidth / 2, pixels.logicalHeight / 2)
        }
        assertEquals(0xFF111111.toInt(), centerPixel(view))
        compose.onNodeWithContentDescription("Eraser").performClick()
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        val time = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float) {
            val motion = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = location[0] + view.width * x; y = location[1] + view.height * .5f
                    pressure = if (action == MotionEvent.ACTION_UP) 0f else .9f
                }), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, .35f)
        repeat(16) { i -> event(MotionEvent.ACTION_MOVE, .35f + .3f * (i + 1) / 16f); SystemClock.sleep(10) }
        event(MotionEvent.ACTION_UP, .65f)
        view.awaitV5StrokeWorkerIdleForTest()
        compose.waitUntil(10_000) { centerPixel(view) ushr 24 < 30 }
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(5_000) { centerPixel(view) == 0xFF111111.toInt() }
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.waitUntil(5_000) { centerPixel(view) ushr 24 < 30 }
        compose.waitUntil(10_000) {
            val layer = requireNotNull(FileDocumentStore(instrumentation.targetContext.filesDir).get("legacy-fill-window"))
                .pages.single().layers.first { it.id == LayerIds.INK }
            val ref = layer.rasterTileSurface ?: return@waitUntil false
            val pixels = DocumentAssetService(instrumentation.targetContext).loadRasterTileSurface(ref).snapshot()
            pixels.pixelAt(pixels.logicalWidth / 2, pixels.logicalHeight / 2) ushr 24 < 30
        }
        compose.activityRule.scenario.recreate()
        val restored = canvas()
        compose.waitUntil(10_000) { centerPixel(restored) ushr 24 < 30 }
        assertTrue(requireNotNull(FileDocumentStore(instrumentation.targetContext.filesDir).get("legacy-fill-window"))
            .pages.single().elements.none { it is PageElement.Fill })
    }

    private fun canvas(): InkCanvasView {
        var result: InkCanvasView? = null
        compose.waitUntil(15_000) {
            instrumentation.runOnMainSync {
                fun visit(view: View) {
                    if (view is InkCanvasView && view.isShown) result = view
                    else if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(compose.activity.window.decorView)
            }
            result != null
        }
        compose.waitForIdle()
        return requireNotNull(result)
    }
}
