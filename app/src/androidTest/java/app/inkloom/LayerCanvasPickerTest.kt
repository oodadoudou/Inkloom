package app.inkloom

import android.graphics.Color
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.LayerMask
import app.inkloom.engine.v5.surface.MaskTileSurface
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.EditorTool
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerCanvasPickerTest {
    @Test fun overlappingLayersAreOfferedTopFirstAndPickContactCannotPaint() {
        val lower = PixelTileSurface(64, 64, 64)
        val upper = PixelTileSurface(64, 64, 64)
        listOf(lower, upper).forEach { surface ->
            surface.beginWrite("pick fixture").run {
                editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(Color.BLUE) }
                commit().close()
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                layout(0, 0, 256, 256)
                tool = EditorTool.PEN
                layerOrder = listOf("lower", "upper")
                setRasterLayerSurfaces(mapOf("lower" to lower, "upper" to upper))
                layerPickMode = true
            }
            assertEquals(listOf("upper", "lower"), view.layerCandidatesAt(.5f, .5f))
            var offered = emptyList<String>()
            view.onLayerPickCandidates = { offered = it }
            val before = lower.revision to upper.revision
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                val event = MotionEvent.obtain(0, 10, action, 128f, 128f, 0)
                event.source = InputDevice.SOURCE_STYLUS
                try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
            }
            assertEquals(listOf("upper", "lower"), offered)
            assertEquals(before, lower.revision to upper.revision)
            view.layerMasks = mapOf("upper" to LayerMask())
            view.layerMaskSurfaces = mapOf("upper" to MaskTileSurface(64, 64, 64, initialDefaultAlpha = 0).snapshot())
            assertEquals(listOf("lower"), view.layerCandidatesAt(.5f, .5f))
        }
    }
}
