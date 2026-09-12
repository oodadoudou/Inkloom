package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import app.inkloom.document.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** All gestures cross Android's window dispatcher. Canvas access below is read-only evidence. */
class NotebookCreationWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun windowRoot(): android.view.accessibility.AccessibilityNodeInfo? {
        // The system picker and IME switch windows outside Compose's idle synchronization.
        if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("color-mixing-board.bin").delete()
            context.filesDir.resolve("library.json").delete()
            context.filesDir.resolve("page-templates.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo()
        tap(label)
        compose.onNodeWithText(label).performTextReplacement(value)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    @Test fun customPaperKeepsItsColorsAndDensityInMyTemplatesAndInheritedPages() {
        val store = PageTemplateStore(compose.activity.filesDir)
        val documents = FileDocumentStore(compose.activity.filesDir)
        val initialIds = documents.list().map { it.id }.toSet()
        fun created() = documents.list().filter { it.id !in initialIds }
        tap("New Note")
        compose.onNodeWithText("Custom paper").performScrollTo(); tap("Custom paper")
        fill("Template name", "Cancel paper")
        tap("Cancel")
        assertTrue(store.load().customTemplates.isEmpty())
        compose.onNodeWithText("Custom paper").performScrollTo(); tap("Custom paper")
        fill("Template name", "Sketch dots")
        fill("Width (px)", "1400"); fill("Height (px)", "1800")
        compose.onNodeWithText("Dots").performScrollTo(); tap("Dots")
        compose.onNodeWithText("Spacious").performScrollTo(); tap("Spacious")
        fill("Paper color (RGB hex)", "E8F0DC")
        tap("Save template")
        val expected = PageBackground.Paper(0xffe8f0dcL, PaperPattern.DOT, PaperDensity.SPACIOUS)
        assertEquals(expected, store.load().customTemplates.single().background)
        compose.onNodeWithText("My templates").performScrollTo()
        compose.onNodeWithText("My templates").assertIsSelected()
        compose.onNodeWithContentDescription("New note template Sketch dots").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Presets").performScrollTo()
        tap("Presets"); assertTrue(!hasText("Sketch dots"))
        tap("My templates")
        compose.onNodeWithContentDescription("New note template Sketch dots").performScrollTo().assertIsDisplayed()
        tap("Create")
        fun pages() = created().single().pages
        assertEquals(expected, pages().single().background)
        assertEquals(1400f, pages().single().width); assertEquals(1800f, pages().single().height)
        tap("Add page using current template")
        compose.waitUntil(5_000) { pages().size == 2 }
        pages().forEach { assertEquals(expected, it.background); assertEquals(1400f, it.width) }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(expected, pages().last().background)
        tap("Gallery"); tap("New Note")
        compose.onNodeWithText("My templates").performScrollTo(); tap("My templates")
        tap("New note template Sketch dots")
        tap("Create")
        assertEquals(2, created().size)
        created().forEach { assertEquals(expected, it.pages.first().background) }
    }

    @Test fun coverFilePickerCancelImportReuseAndDeleteKeepExistingNotebooks() {
        val context = compose.activity
        val resolver = context.contentResolver
        val name = "Inkloom-cover-${SystemClock.uptimeMillis()}"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        val bitmap = android.graphics.Bitmap.createBitmap(64, 96, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xffbc7856.toInt())
        requireNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        val templates = PageTemplateStore(context.filesDir)
        val documents = FileDocumentStore(context.filesDir)
        val before = documents.list().map { it.id }.toSet()
        fun created() = documents.list().filter { it.id !in before }
        try {
            tap("New Note"); tap("Import cover image")
            nativeNode { it.contentDescription?.toString() == "Show roots" }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); compose.waitForIdle()
            assertTrue(templates.load().customCovers.isEmpty()); assertTrue(created().isEmpty())
            assertTrue(hasText("Create notebook"))
            tap("Import cover image")
            nativeNode { it.contentDescription?.toString() == "Show roots" && clickNative(it) }
            nativeNode { it.text?.toString() == "Downloads" && it.isVisibleToUser && clickNative(it) }
            nativeNode { it.contentDescription?.toString() == "Search" && clickNative(it) }
            instrumentation.sendStringSync(name)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
            // Use the provider's stable list row while image thumbnails are still loading.
            nativeNode { (it.contentDescription?.toString() == "List view" && clickNative(it)) || it.contentDescription?.toString() == "Grid view" }
            nativeNode { it.text?.toString() == "$name.png" && clickNative(it) }
            nativeNode { (it.text?.toString()?.equals("Open", ignoreCase = true) == true && clickNative(it)) || it.packageName?.toString() == "app.inkloom" }
            compose.waitUntil(10_000) { templates.load().customCovers.size == 1 || hasText("Could not import this cover image") }
            assertEquals("Cover import failed after returning from the system picker", 1, templates.load().customCovers.size)
            compose.waitForIdle()
            assertTrue("Importing a cover must not create a notebook", created().isEmpty())
            val cover = templates.load().customCovers.single()
            val preview = bounds("Use cover $name")
            compose.waitUntil(5_000) {
                val shot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try { shot.getPixel(preview.centerX(), preview.centerY()) == 0xffbc7856.toInt() } finally { shot.recycle() }
            }
            tap("Create")
            canvas()
            assertEquals(cover.assetPath, created().single().coverImagePath)
            tap("Gallery"); tap("New Note")
            compose.onNodeWithText("My covers").performScrollTo(); tap("My covers")
            compose.onNodeWithText("My covers").assertIsSelected()
            val coverNode = compose.onNodeWithContentDescription("Use cover $name")
            coverNode.performScrollTo().assertIsDisplayed()
            tapAt(coverNode.fetchSemanticsNode().boundsInWindow.center)
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Use cover $name").assertIsSelected()
            // The action row must remain reachable while the title keyboard is open.
            compose.onNodeWithText("Title").performScrollTo(); tap("Title")
            compose.onNodeWithText("Title").performTextReplacement("Second cover notebook")
            tap("Create")
            canvas()
            compose.waitUntil(5_000) { created().size == 2 }
            assertEquals(2, created().size)
            created().forEach { assertEquals(cover.assetPath, it.coverImagePath); assertEquals(CoverStyle.CUSTOM_IMAGE, it.coverStyle) }
            tap("Gallery"); tap("New Note")
            compose.onNodeWithText("My covers").performScrollTo(); tap("My covers")
            val deleteNode = compose.onNodeWithContentDescription("Delete cover $name")
            deleteNode.performScrollTo().assertIsDisplayed()
            tapAt(deleteNode.fetchSemanticsNode().boundsInWindow.center)
            compose.waitForIdle(); tap("Delete")
            assertTrue(templates.load().customCovers.isEmpty())
            assertTrue(java.io.File(cover.assetPath).isFile)
            created().forEach { assertEquals(cover.assetPath, it.coverImagePath) }
            tap("Cancel"); compose.activityRule.scenario.recreate(); compose.waitForIdle()
            assertTrue(templates.load().customCovers.isEmpty())
            created().forEach { assertTrue(java.io.File(requireNotNull(it.coverImagePath)).isFile) }
        } catch (failure: Throwable) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("screencap -p /data/local/tmp/inkloom-cover-failed.png")).use { it.readBytes() }
            throw failure
        } finally { resolver.delete(uri, null, null) }
    }

    private fun nativeNode(predicate: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean): android.view.accessibility.AccessibilityNodeInfo {
        var found: android.view.accessibility.AccessibilityNodeInfo? = null
        try {
            compose.waitUntil(10_000) {
                fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                    if (node == null || found != null) return
                    if (predicate(node)) found = node else repeat(node.childCount) { visit(node.getChild(it)) }
                }
                visit(windowRoot())
                found != null
            }
        } catch (failure: Throwable) {
            val labels = mutableListOf<String>()
            fun inspect(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (!node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()) labels += "${node.text} / ${node.contentDescription} / ${node.isClickable}"
                repeat(node.childCount) { inspect(node.getChild(it)) }
            }
            inspect(windowRoot())
            throw AssertionError("System picker contents: ${labels.joinToString(" | ")}", failure)
        }
        return requireNotNull(found)
    }
    private fun clickNative(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        var current: android.view.accessibility.AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
        }
        return false
    }

    private fun hasText(text: String): Boolean {
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == text) return true
            return (0 until node.childCount).any { visit(node.getChild(it)) }
        }
        return visit(windowRoot())
    }

    private fun bounds(label: String): android.graphics.Rect {
        var result: android.graphics.Rect? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.contentDescription?.toString() == label) {
                    result = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                } else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(windowRoot())
            result != null
        }
        return requireNotNull(result)
    }

    private fun tap(node: String) {
        // A tab can resize this dialog after Compose is idle but before accessibility has
        // published its new screen bounds. Wait for the real window before injecting input.
        instrumentation.uiAutomation.waitForIdle(100, 3_000)
        tapAt(center(node))
        compose.waitForIdle()
    }

    private fun center(label: String): Offset {
        var result: Offset? = null
        compose.waitUntil(5_000) {
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                node.refresh()
                if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    result = Offset(rect.exactCenterX(), rect.exactCenterY())
                } else repeat(node.childCount) { visit(node.getChild(it)) }
            }
            visit(windowRoot())
            result != null
        }
        return requireNotNull(result)
    }

    private fun tapAt(point: Offset) {
        val time = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, point, time, MotionEvent.TOOL_TYPE_FINGER)
        event(MotionEvent.ACTION_UP, point, time, MotionEvent.TOOL_TYPE_FINGER)
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
