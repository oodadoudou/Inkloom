package app.inkloom

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.model.EditorTool
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.BrushMasterSettingsStore
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.UiLanguage
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class StylusButtonActivationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                AppSettingsStore(context).save(
                    AppSettings(
                        language = UiLanguage.ENGLISH,
                        penSize = 19f,
                        penOpacity = .6f,
                        eraserSize = 41f,
                        stylusPrimaryButton = StylusButtonAction.ERASER,
                        stylusPrimaryTrigger = ShortcutTrigger.HOLD,
                    ),
                )
                // Brush sizes are preset-scoped. Seed the selected G Pen through the same store
                // used by the editor instead of relying on the retired global pen-size fallback.
                BrushMasterSettingsStore(context).save(EditorTool.PEN, "system-g-pen", 19f, .43f)
                try {
                    base.evaluate()
                } finally {
                    context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear().commit()
                    context.getSharedPreferences("brush_preset_sizes", Context.MODE_PRIVATE).edit().clear().commit()
                }
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(settingsRule).around(composeRule)

    @Test fun heldPenButtonTemporarilyUsesEraserAndRestoresPenPreset() {
        composeRule.onNodeWithText("New Note").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()
        val canvas = findCanvas(composeRule.activity.window.decorView) ?: error("Ink canvas not found")

        dispatch(canvas, MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_STYLUS_PRIMARY)
        composeRule.waitForIdle()
        assertEquals(EditorTool.ERASER, canvas.tool)
        assertEquals(41f, canvas.eraserWidth, .001f)

        dispatch(canvas, MotionEvent.ACTION_UP, 0)
        // The hold release is deliberately owned by the first frame that presents the committed
        // erase transaction. Waiting only for Compose idleness can observe the protected interim
        // eraser state before the AndroidView draw barrier runs.
        composeRule.waitUntil(5_000) { canvas.tool == EditorTool.PEN }
        assertEquals(EditorTool.PEN, canvas.tool)
        assertEquals(19f, canvas.baseWidth, .001f)
        assertEquals(.43f, canvas.inkOpacity, .001f)
    }

    private fun dispatch(view: InkCanvasView, action: Int, buttonState: Int) {
        val event = MotionEvent.obtain(
            0L,
            if (action == MotionEvent.ACTION_DOWN) 0L else 40L,
            action,
            1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { x = 160f; y = 160f; pressure = .6f; size = .05f }),
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { view.onTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }

    private fun findCanvas(view: View): InkCanvasView? {
        if (view is InkCanvasView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findCanvas(view.getChildAt(index))?.let { return it }
        return null
    }
}
