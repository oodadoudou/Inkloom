package app.inkloom

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.settings.BrushUsageState
import app.inkloom.settings.BrushUsageStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class BrushStudioWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "SIMPLIFIED_CHINESE").putInt("studioWorkspaceVersion", 2)
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false).putBoolean("showPageRail", false).commit()
            context.getSharedPreferences("inkloom_brush_usage", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_brushes", Context.MODE_PRIVATE).edit().clear().commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun appLanguageOwnsStudioAndCustomCurveSurvivesSave() {
        tap("新建笔记"); tap("创建"); tap("笔刷")
        tap("笔刷类别 铅笔")
        val brush = bounds("笔刷预设 HB Pencil")
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, brush.exactCenterX(), brush.exactCenterY(), down)
        SystemClock.sleep(700)
        compose.mainClock.advanceTimeBy(750)
        compose.waitForIdle()
        event(MotionEvent.ACTION_UP, brush.exactCenterX(), brush.exactCenterY(), down)
        bounds("笔刷工作室"); val pad = bounds("试笔板")
        tap("试笔板设置")
        bounds("预览大小滑块")
        tap("预览颜色 红色")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        val livePad = drawingPad()
        val drawingBounds = Rect()
        instrumentation.runOnMainSync { assertTrue(livePad.canvas.getGlobalVisibleRect(drawingBounds)) }
        val strokeTime = SystemClock.uptimeMillis()
        val x = drawingBounds.left + drawingBounds.width() * .2f
        val y = drawingBounds.top + drawingBounds.height() * .35f
        event(MotionEvent.ACTION_DOWN, x, y, strokeTime, stylus = true)
        repeat(20) { index ->
            SystemClock.sleep(8)
            event(MotionEvent.ACTION_MOVE, x + drawingBounds.width() * .5f * (index + 1) / 20f, y, strokeTime, stylus = true)
        }
        event(MotionEvent.ACTION_UP, x + drawingBounds.width() * .5f, y, strokeTime, stylus = true)
        compose.waitUntil(5_000) { livePad.retainedStrokeCount == 1 }
        livePad.canvas.awaitStudioPaintIdle()
        compose.waitForIdle()
        capture("studio-drawing-pad-red")
        tap("清空")
        compose.waitUntil(5_000) { drawingPad() !== livePad && drawingPad().retainedStrokeCount == 0 }
        capture("studio-properties")
        assertTrue("The studio must follow the app language on an English emulator", find("Brush Studio").isEmpty())
        val pressure = bounds("压力")
        assertTrue("Drawing pad must sit to the right of categories", pad.left > pressure.right)
        tap("形状")
        bounds("程序生成形状预览")
        capture("studio-shape-source")
        tap("颗粒")
        bounds("程序纸张颗粒预览")
        capture("studio-grain-source")
        tap("防抖修正")
        val stabilization = bounds("此笔刷防抖 滑块")
        val adjustment = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, stabilization.left + stabilization.width() * .15f, stabilization.exactCenterY(), adjustment)
        repeat(20) { index ->
            SystemClock.sleep(8)
            event(MotionEvent.ACTION_MOVE, stabilization.left + stabilization.width() * (.15f + .6f * (index + 1) / 20f), stabilization.exactCenterY(), adjustment)
        }
        event(MotionEvent.ACTION_UP, stabilization.left + stabilization.width() * .75f, stabilization.exactCenterY(), adjustment)
        compose.waitForIdle()
        capture("studio-stabilization")
        tap("压力")
        val curve = bounds("可编辑的笔刷压力曲线")
        capture("studio-pressure")
        val started = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, curve.exactCenterX(), curve.exactCenterY(), started)
        repeat(16) { index ->
            SystemClock.sleep(8)
            event(MotionEvent.ACTION_MOVE, curve.exactCenterX(),
                curve.exactCenterY() - curve.height() * .25f * (index + 1) / 16f, started)
        }
        event(MotionEvent.ACTION_UP, curve.exactCenterX(), curve.top + curve.height() * .25f, started)
        tap("完成"); tap("保存")
        val store = app.inkloom.importer.BrushMetadataStore(context)
        compose.waitUntil(5_000) { store.load().any { it.derivedFromFactoryId == "system-hb-pencil" } }
        val saved = store.load().first { it.derivedFromFactoryId == "system-hb-pencil" }
        val preset = app.inkloom.brushpresetv5.BrushPresetV5Import.requirePreset(saved)
        assertTrue("Pencil stabilization must persist independently in its preset", (preset.stroke?.smoothing ?: 0f) > 0f)
        assertTrue("Custom curve must reach the saved compiled preset", preset.dynamics.pressure.widthCurve.any {
            it.x in .35f.. .65f && it.y > .65f
        })
        assertTrue(find("笔刷工作室").isEmpty())
    }

    private fun find(label: String): List<Rect> {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val result = mutableListOf<Rect>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
            node ?: return
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                result += Rect().also(node::getBoundsInScreen)
            }
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        return result.filterNot(Rect::isEmpty)
    }

    private fun capture(name: String) {
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = requireNotNull(context.getExternalFilesDir(null)).resolve("$name.png")
        try { file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cp ${file.absolutePath} /data/local/tmp/inkloom-$name.png")).use { it.readBytes() }
    }

    private fun bounds(label: String, row: Rect? = null): Rect {
        var result: Rect? = null
        compose.waitUntil(5_000) {
            result = find(label).firstOrNull { row == null || it.centerY() in row.top..row.bottom }
            result != null
        }
        return requireNotNull(result)
    }

    private fun tap(label: String, row: Rect? = null) {
        val target = bounds(label, row); val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, target.exactCenterX(), target.exactCenterY(), down)
        event(MotionEvent.ACTION_UP, target.exactCenterX(), target.exactCenterY(), down)
        compose.waitForIdle()
    }

    private fun drawingPad(): app.inkloom.ui.BrushDrawingPadView {
        var result: app.inkloom.ui.BrushDrawingPadView? = null
        instrumentation.runOnMainSync {
            fun visit(view: android.view.View) {
                if (view is app.inkloom.ui.BrushDrawingPadView) result = view
                else if (view is android.view.ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::visit)
        }
        return requireNotNull(result)
    }

    private fun event(action: Int, x: Float, y: Float, down: Long, stylus: Boolean = false) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = if (stylus) MotionEvent.TOOL_TYPE_STYLUS else MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f }),
            0, 0, 1f, 1f, 0, 0, if (stylus) InputDevice.SOURCE_STYLUS else InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
}
