package app.inkloom

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

class RasterFilterWindowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val clean = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("inkloom_editor_panels", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("inkloom_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("language", "ENGLISH")
                .putBoolean("showBrushPanel", false).putBoolean("showLayerPanel", false)
                .putBoolean("showPageRail", false).commit()
            resetNotebookTestLibrary(context)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(clean).around(compose)

    @Test fun lineArtConversionsUsePickerPreviewCancelUndoAndReopen() {
        compose.onNodeWithText("New Note").performClick(); compose.onNodeWithText("Create").performClick()
        val view = canvas()
        fun pagePoint(x: Float, y: Float): FloatArray {
            val point=floatArrayOf(x*view.width,y*view.height)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                android.graphics.Matrix().also { view.transformMatrixToGlobal(it);it.mapPoints(point) }
            }
            return point
        }
        val start=pagePoint(.25f,.5f);val end=pagePoint(.75f,.5f)
        val x1=start[0];val x2=end[0];val y=start[1]
        val down=SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN,x1,y,down)
        repeat(16){event(MotionEvent.ACTION_MOVE,x1+(x2-x1)*(it+1)/16f,y,down)}
        event(MotionEvent.ACTION_UP,x2,y,down)
        compose.waitUntil(15_000){view.activeRasterSnapshotForTest()?.tileCount?.let{it>0}==true && !view.hasPendingPaintPublicationForMerge()}
        val original=fingerprint(view)
        val originalSurface=view.activeRasterSnapshotForTest()!!
        val allPixels=originalSurface.copyPremultipliedPixels(PixelRect(0,0,originalSurface.logicalWidth,originalSurface.logicalHeight))
        val marker=allPixels.indices.maxBy { allPixels[it] ushr 24 }
        val markerX=marker%originalSurface.logicalWidth;val markerY=marker/originalSurface.logicalWidth
        assertTrue(allPixels[marker] ushr 24 > 0)
        fun middle(): Int = view.activeRasterSnapshotForTest()!!.pixelAt(markerX,markerY)
        fun open() {
            compose.onNodeWithContentDescription("Actions").performClick()
            compose.onNodeWithContentDescription("Actions Canvas").performClick()
            compose.onNodeWithText("Image filter…").performClick()
            compose.onNodeWithText("Gaussian blur").performClick()
            compose.onNodeWithText("Convert to drawing color").performScrollTo().performClick()
        }
        fun chooseRed() {
            compose.onNodeWithText("Drawing color…").performClick()
            compose.onNodeWithContentDescription("Hex color").performTextReplacement("#FF0000")
            compose.onNodeWithContentDescription("Apply color").performClick()
            try { compose.waitUntil(15_000){middle() ushr 24 > 0 && middle() ushr 16 and 255 > 0 && middle() and 0xffff == 0} }
            catch (failure: Throwable) { throw AssertionError("Recolor pixel ${middle().toUInt().toString(16)} at $markerX,$markerY; original ${allPixels[marker].toUInt().toString(16)}; pending ${view.selectionLayerTransferPending}",failure) }
        }
        open();chooseRed()
        val red=fingerprint(view)
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(15_000){fingerprint(view)==original && !view.selectionLayerTransferPending}
        open();chooseRed()
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000){fingerprint(view)==red && !view.selectionLayerTransferPending}
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(15_000){fingerprint(view)==original}
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.waitUntil(15_000){fingerprint(view)==red}
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Image filter…").performClick()
        compose.onNodeWithText("Gaussian blur").performClick()
        compose.onNodeWithText("Brightness to opacity").performScrollTo().performClick()
        compose.waitUntil(15_000){middle() ushr 24 > 0 && middle() and 0xffffff == 0}
        val opacity=fingerprint(view)
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000){!view.selectionLayerTransferPending && fingerprint(view)==opacity}
        val snapshot=view.activeRasterSnapshotForTest()!!
        val pixel=snapshot.pixelAt(markerX,markerY)
        assertTrue(pixel ushr 24 > 0);assertEquals(0,pixel and 0xffffff)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(15_000){fingerprint(view)==red}
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.waitUntil(15_000){fingerprint(view)==opacity}
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        val reopened=canvas()
        compose.waitUntil(15_000){fingerprint(reopened)==opacity}
    }

    @Test fun filterWindowRejectsMaskTargetInsteadOfEditingItsContent() {
        compose.onNodeWithText("New Note").performClick();compose.onNodeWithText("Create").performClick()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Add layer").performClick()
        compose.onNodeWithContentDescription("Active layer mask").performClick()
        compose.onNodeWithText("Mask selection").performClick()
        compose.waitForIdle()
        val view=canvas()
        lateinit var model: app.inkloom.ui.InkloomViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model=androidx.lifecycle.ViewModelProvider(compose.activity)[app.inkloom.ui.InkloomViewModel::class.java]
        }
        val id=model.currentPage()!!.layers.first { it.name=="Layer 1" }.id
        compose.waitUntil(10_000){view.layerMaskSurfaces.containsKey(id)}
        compose.waitUntil(10_000){view.isEditingLayerMask(id)}
        val original=fingerprint(view)
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Image filter…").performClick()
        compose.waitForIdle()
        assertTrue(view.isEditingLayerMask(id));assertFalse(view.selectionLayerTransferPending)
        assertEquals(original,fingerprint(view))
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(view.isEditingLayerMask(id))
    }

    @Test fun previewCancelApplyAndUndoShareOneActualWindowHistory() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val rect = IntArray(2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.getLocationOnScreen(rect) }
        val y = rect[1] + view.height / 2f
        val x1 = rect[0] + view.width * .25f
        val x2 = rect[0] + view.width * .75f
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x1, y, down)
        repeat(16) { index -> event(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * (index + 1) / 16f, y, down) }
        event(MotionEvent.ACTION_UP, x2, y, down)
        compose.waitUntil(15_000) { fingerprint(view) != 0 }
        val original = fingerprint(view)

        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Image filter…").performClick()
        compose.waitUntil(15_000) { fingerprint(view) != original }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(15_000) { fingerprint(view) == original }

        compose.onNodeWithContentDescription("Actions").performClick()
        compose.onNodeWithContentDescription("Actions Canvas").performClick()
        compose.onNodeWithText("Image filter…").performClick()
        compose.waitUntil(15_000) { fingerprint(view) != original }
        val filtered = fingerprint(view)
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000) { fingerprint(view) == filtered && !view.selectionLayerTransferPending }
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(15_000) { fingerprint(view) == original }
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.waitUntil(15_000) { fingerprint(view) == filtered }
    }

    @Test fun fixedSharpenFiltersPreviewCancelApplyAndShareWindowHistory() {
        compose.onNodeWithText("New Note").performClick()
        compose.onNodeWithText("Create").performClick()
        val view = canvas()
        val rect = IntArray(2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.getLocationOnScreen(rect) }
        val y = rect[1] + view.height / 2f
        val x1 = rect[0] + view.width * .25f
        val x2 = rect[0] + view.width * .75f
        val down = SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN, x1, y, down)
        repeat(16) { index -> event(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * (index + 1) / 16f, y, down) }
        event(MotionEvent.ACTION_UP, x2, y, down)
        compose.waitUntil(15_000) { fingerprint(view) != 0 && !view.hasPendingPaintPublicationForMerge() }
        fun open(kind: String) {
            compose.onNodeWithContentDescription("Actions").performClick()
            compose.onNodeWithContentDescription("Actions Canvas").performClick()
            compose.onNodeWithText("Image filter…").performClick()
            compose.onNodeWithText("Gaussian blur").performClick()
            compose.onNodeWithText(kind).performScrollTo().performClick()
        }
        // Uniform ink has no color edge to sharpen; deterministic noise gives it one.
        // Results are read after Apply because the dialog first previews its default filter.
        fun applied(vararg excluded: Int): Int {
            compose.onNodeWithText("Apply").performClick()
            var result = 0
            compose.waitUntil(15_000) {
                val first = fingerprint(view); SystemClock.sleep(200)
                result = fingerprint(view)
                !view.selectionLayerTransferPending && first == result && result !in excluded
            }
            return result
        }
        val ink = fingerprint(view)
        open("Noise"); val noisy = applied(ink)
        open("Sharpen")
        compose.onNodeWithText("Fixed strength; use Unsharp mask for adjustable sharpening.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(15_000) { fingerprint(view) == noisy && !view.selectionLayerTransferPending }
        open("Sharpen"); val sharpened = applied(noisy)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(15_000) { fingerprint(view) == noisy }
        open("Sharpen more"); val stronger = applied(noisy, sharpened)
        compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
        compose.waitUntil(15_000) { fingerprint(view) == noisy }
        compose.onAllNodesWithContentDescription("Redo").onFirst().performClick()
        compose.waitUntil(15_000) { fingerprint(view) == stronger }
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        val reopened = canvas()
        compose.waitUntil(15_000) { fingerprint(reopened) == stronger }
    }

    private fun fingerprint(view: InkCanvasView): Int {
        val snapshot = view.activeRasterSnapshotForTest() ?: return 0
        return snapshot.copyPremultipliedPixels(PixelRect(0, 0, snapshot.logicalWidth, snapshot.logicalHeight)).contentHashCode()
    }

    private fun canvas(): InkCanvasView {
        var result: InkCanvasView? = null
        compose.waitUntil(5_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
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

    private fun event(action: Int, x: Float, y: Float, down: Long) {
        val motion = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }),
            arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y;
                pressure = if (action == MotionEvent.ACTION_UP) 0f else .7f; size = .01f }),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try { assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(motion, true)) }
        finally { motion.recycle() }
    }
}
