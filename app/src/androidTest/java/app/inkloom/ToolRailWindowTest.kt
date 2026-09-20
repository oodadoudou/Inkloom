package app.inkloom

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class ToolRailWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH").putInt("studioWorkspaceVersion", 2).commit()
            context.filesDir.resolve("library.json").delete()
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun verticalRailExposesEveryToolAndActivatesPaintBucket() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        listOf("Brush", "Marker", "Eraser tool", "Select", "Eyedropper", "Paint bucket",
            "Gradient", "Blend", "Liquify", "Correct line", "Shape", "Text", "Note",
            "Link", "Image", "Laser pointer").forEach { label ->
            compose.onNodeWithContentDescription(label).assertExists()
        }
        compose.onNodeWithContentDescription("Paint bucket").performClick()
        assertEquals(EditorTool.FILL, canvas().tool)
    }

    private fun canvas(): InkCanvasView {
        fun find(view: View): InkCanvasView? {
            if (view is InkCanvasView) return view
            if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it))?.let { result -> return result } }
            return null
        }
        return requireNotNull(find(compose.activity.window.decorView))
    }
}
