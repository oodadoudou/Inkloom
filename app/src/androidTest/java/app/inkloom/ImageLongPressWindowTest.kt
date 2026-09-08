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
            center("Apply transform")
        }
        hold()
        tap("Properties")
        tap("Width slider")
        tap("Save")
        tap("Apply transform")
        val store = app.inkloom.document.FileDocumentStore(instrumentation.targetContext.filesDir)
        fun saved() = store.get("image-hold-fixture")!!.pages.first().elements.first() as app.inkloom.document.PageElement.Image
        compose.waitUntil(5_000) { saved().width > 720f }
        val changed = saved()
        assertTrue(java.io.File(changed.assetPath).isFile)
        hold(); tap("Cancel transform")
        assertEquals(changed, saved())
        assertEquals("Holding image must cancel provisional ink", initial.revision, view.activeRasterSnapshotForTest()?.revision)
        stroke(pagePoint(view, .1f, .7f), pagePoint(view, .5f, .7f))
        compose.waitUntil(5_000) { (view.activeRasterSnapshotForTest()?.revision ?: 0L) > initial.revision }
    }

    @Test fun directCornerResizeIsDraftedAndOneUndoRestoresTheOriginalImage() = exerciseCorner(MotionEvent.TOOL_TYPE_STYLUS)
    @Test fun fingerCanResizeAnImageAfterCanvasZoom() = exerciseCorner(MotionEvent.TOOL_TYPE_FINGER)

    private fun exerciseCorner(pointerType: Int) {
        tap("Notebook Image hold regression. Long press to move.")
        val view = canvas()
        val store = app.inkloom.document.FileDocumentStore(instrumentation.targetContext.filesDir)
        fun saved() = store.get("image-hold-fixture")!!.pages.first().elements.first() as app.inkloom.document.PageElement.Image
        val original = saved()
        val source = java.io.File(original.assetPath).readBytes()
        val holdPoint = pagePoint(view, .35f, .3f)
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, holdPoint, time, MotionEvent.TOOL_TYPE_STYLUS)
        SystemClock.sleep(700)
        event(MotionEvent.ACTION_UP, holdPoint, time, MotionEvent.TOOL_TYPE_STYLUS)
        center("Apply transform")
        val session = requireNotNull(view.imageCanvasTransform)
        SystemClock.sleep(500)
        pinch(view)
        compose.waitForIdle()
        compose.waitUntil(5_000) { view.viewportScale > 1.3f }
        assertEquals("Navigation must preserve the image draft", original, session.current)
        // Locate the actual blue image on the composed Android screenshot; do not use
        // the input mapper as its own coordinate oracle after Compose graphicsLayer zoom.
        var start = Offset.Zero
        compose.waitUntil(5_000) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            var right = -1; var bottom = -1
            for (y in 0 until screenshot.height) for (x in 0 until screenshot.width) {
                val color = screenshot.getPixel(x, y)
                if (android.graphics.Color.blue(color) > 245 && android.graphics.Color.red(color) < 8 && android.graphics.Color.green(color) < 8) {
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            screenshot.recycle()
            start = Offset(right + 1f, bottom + 1f)
            right > 0 && bottom > 0
        }
        stroke(start, start + Offset(110f, 60f), pointerType)
        assertTrue("Rendered corner $start must resize the image at zoom ${view.viewportScale}: ${session.current}", session.current.width > original.width + 20f)
        assertEquals("Dragging must not write a draft to the document", original, saved())
        assertEquals(original.x, session.current.x, .0001f)
        assertEquals(original.y, session.current.y, .0001f)
        val resized = session.current
        compose.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val resolver = instrumentation.targetContext.contentResolver
        val screenshotUri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "inkloom-image-handles.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/InkloomQA")
            }))
        resolver.openOutputStream(screenshotUri)!!.use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        tap("Apply transform")
        compose.waitUntil(5_000) { saved().width == resized.width }
        tap("Undo")
        compose.waitUntil(5_000) { saved() == original }
        tap("Redo")
        compose.waitUntil(5_000) { saved().width == resized.width }
        assertTrue(source.contentEquals(java.io.File(original.assetPath).readBytes()))
        assertEquals(0, view.activeRasterSnapshotForTest()?.tileCount)
    }

    private fun pinch(view: InkCanvasView) {
        val time = SystemClock.uptimeMillis()
        fun packet(action: Int, first: Offset, second: Offset? = null) {
            val points = listOfNotNull(first, second)
            val motion = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, points.size,
                Array(points.size) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } },
                Array(points.size) { index -> MotionEvent.PointerCoords().apply { x = points[index].x; y = points[index].y; pressure = 1f; touchMajor = 8f } },
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) } finally { motion.recycle() }
            SystemClock.sleep(20)
        }
        val a = pagePoint(view, .35f, .6f); val b = pagePoint(view, .65f, .6f)
        packet(MotionEvent.ACTION_DOWN, a)
        packet(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), a, b)
        val c = pagePoint(view, .2f, .6f); val d = pagePoint(view, .8f, .6f)
        repeat(8) { index -> val t = (index + 1) / 8f; packet(MotionEvent.ACTION_MOVE, a + (c - a) * t, b + (d - b) * t) }
        packet(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), c, d)
        packet(MotionEvent.ACTION_UP, c)
    }

    private fun pagePoint(view: InkCanvasView, x: Float, y: Float): Offset {
        val point = floatArrayOf(x * view.width, y * view.height)
        instrumentation.runOnMainSync {
            val matrix = android.graphics.Matrix()
            view.transformMatrixToGlobal(matrix)
            matrix.mapPoints(point)
        }
        return Offset(point[0], point[1])
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

    private fun stroke(start: Offset, end: Offset, pointerType: Int = MotionEvent.TOOL_TYPE_STYLUS) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, start, time, pointerType)
        repeat(24) { index ->
            SystemClock.sleep(8)
            val fraction = (index + 1) / 24f
            event(MotionEvent.ACTION_MOVE, start + (end - start) * fraction, time, pointerType)
        }
        event(MotionEvent.ACTION_UP, end, time, pointerType)
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
