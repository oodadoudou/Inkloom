package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.ReferenceImageStore
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** T14: Sub view reference images sit beside the canvas and give the drawing colour to the eyedropper. */
class SubViewWindowTest {
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
            // A reference picture: red on the left half, blue on the right.
            val picture = androidx.core.graphics.createBitmap(200, 100).apply {
                for (x in 0 until 200) for (y in 0 until 100) setPixel(x, y, if (x < 100) 0xFFE01010.toInt() else 0xFF1030E0.toInt())
            }
            val bytes = java.io.ByteArrayOutputStream().also { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            ReferenceImageStore(context.filesDir).add("Swatch", "png", bytes.inputStream())
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun eyedropperTakesTheDrawingColourFromAReferenceImage() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Views").performClick()
        compose.onNodeWithText("Views…").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show Sub view view").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Swatch · 1/1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Obtain color").performClick()
        val image = compose.onNodeWithContentDescription("Sub view image")
        image.performTouchInput { click(Offset(center.x - width * .2f, center.y)) }
        compose.waitUntil(5_000) { ink(view) == 0xFFE01010.toInt() }
        image.performTouchInput { click(Offset(center.x + width * .2f, center.y)) }
        compose.waitUntil(5_000) { ink(view) == 0xFF1030E0.toInt() }
        compose.onNodeWithContentDescription("Remove image").performClick()
        compose.onNodeWithText("No images").assertIsDisplayed()
        assertEquals(emptyList<Any>(), ReferenceImageStore(compose.activity.filesDir).list())
    }

    private fun ink(view: InkCanvasView): Int { var value = 0; instrumentation.runOnMainSync { value = view.inkColor }; return value }

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
