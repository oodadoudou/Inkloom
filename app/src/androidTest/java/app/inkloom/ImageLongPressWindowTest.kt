package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class ImageLongPressWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("library.json").delete()
            val png = context.cacheDir.resolve("image-hold.png")
            android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.BLUE)
                png.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val image = app.inkloom.document.DocumentAssetService(context).importPageImage(android.net.Uri.fromFile(png))
            val store = app.inkloom.document.FileDocumentStore(context.filesDir)
            store.save(app.inkloom.document.InkDocument(id = "image-hold-fixture", title = "Image hold regression", pages = listOf(app.inkloom.document.InkPage(elements = listOf(image)))))
            try { base.evaluate() } finally { store.deletePermanently("image-hold-fixture"); png.delete() }
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun importedImageCanBeTransformedAgainAndCancellationKeepsSource() {
        tap("Notebook Image hold regression. Long press to move.")
        val view = canvas()
        val initial = requireNotNull(view.activeRasterSnapshotForTest())
        assertEquals("Imported image stays separately editable", 0, initial.tileCount)
        fun hold() {
            val point = pagePoint(view, .35f, .3f)
            val time = SystemClock.uptimeMillis()
            event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_STYLUS)
            SystemClock.sleep(700)
            event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_STYLUS)
            center("Transform image")
        }
        hold()
        tap("Width slider")
        tap("Save")
        val store = app.inkloom.document.FileDocumentStore(instrumentation.targetContext.filesDir)
        fun saved() = store.get("image-hold-fixture")!!.pages.first().elements.first() as app.inkloom.document.PageElement.Image
        compose.waitUntil(5_000) { saved().width > 720f }
        val changed = saved()
        assertTrue(java.io.File(changed.assetPath).isFile)
        hold(); tap("Cancel")
        assertEquals(changed, saved())
        assertEquals("Holding image must cancel provisional ink", initial.revision, view.activeRasterSnapshotForTest()?.revision)
        stroke(pagePoint(view, .1f, .7f), pagePoint(view, .5f, .7f))
        compose.waitUntil(5_000) { (view.activeRasterSnapshotForTest()?.revision ?: 0L) > initial.revision }
    }

    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        return Offset(location[0] + x * view.width, location[1] + y * view.height)
    }

    private fun hoverThenTap(point: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_HOVER_ENTER, point, time, MotionEvent.TOOL_TYPE_STYLUS)
        event(MotionEvent.ACTION_HOVER_MOVE, point, time, MotionEvent.TOOL_TYPE_STYLUS)
        SystemClock.sleep(180)
        event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_STYLUS)
        event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_STYLUS)
    }

    private fun tap(node: String) {
        tapAt(center(node))
        compose.waitForIdle()
    }

    private fun center(label: String): Offset {
        var result: Offset? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    result = Offset(rect.exactCenterX(), rect.exactCenterY())
                } else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(instrumentation.uiAutomation.rootInActiveWindow)
            result != null
        }
        return requireNotNull(result)
    }

    private fun tapAt(point: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_FINGER)
    }

    private fun stroke(start: Offset, end: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, time, MotionEvent.TOOL_TYPE_STYLUS)
        repeat(24) { index ->
            SystemClock.sleep(8)
            val fraction = (index + 1) / 24f
            event(MotionEvent.ACTION_MOVE, start + (end - start) * fraction, time, MotionEvent.TOOL_TYPE_STYLUS)
        }
        event(MotionEvent.ACTION_UP, end, time, MotionEvent.TOOL_TYPE_STYLUS)
    }

    private fun event(action: Int, point: Offset, down: Long, type: Int) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = type }),
            arrayOf(MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0,
            if (type == MotionEvent.TOOL_TYPE_STYLUS) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Window rejected input", instrumentation.uiAutomation.injectInputEvent(motion, true)) }
        finally { motion.recycle() }
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
