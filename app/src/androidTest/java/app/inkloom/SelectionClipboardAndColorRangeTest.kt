package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import app.inkloom.engine.v5.selection.SparseAlphaMask
import app.inkloom.engine.v5.surface.PixelTileFormat
import app.inkloom.engine.v5.surface.RasterSelectionContent
import app.inkloom.ui.TransparentImageClipboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionClipboardAndColorRangeTest {
    @Test fun transparentImageRoundTripsThroughAndroidClipboard() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.TRANSPARENT)
            setPixel(1, 0, 0x80800000.toInt())
            setPixel(2, 1, Color.BLUE)
        }
        val source = requireNotNull(RasterSelectionContent.fromBitmap(
            bitmap, 300, 200, PixelTileFormat.PREMULTIPLIED_SRGB_8888, 30, 40,
        ))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(TransparentImageClipboard.publish(activity, source))
                assertTrue(TransparentImageClipboard.ownsPrimaryClip(activity))
                val imported = requireNotNull(TransparentImageClipboard.read(
                    activity, 600, 400, PixelTileFormat.PREMULTIPLIED_SRGB_8888,
                ))
                val restored = RasterSelectionContent.toBitmap(imported)
                assertEquals(0, Color.alpha(restored.getPixel(0, 0)))
                assertEquals(128, Color.alpha(restored.getPixel(1, 0)))
                assertTrue("Translucent clipboard colors must not darken or brighten", kotlin.math.abs(128 - Color.red(restored.getPixel(1, 0))) <= 1)
                val destination = app.inkloom.engine.v5.surface.PixelTileSurface(10, 10)
                val edit = requireNotNull(RasterSelectionContent.paste(destination, imported, 2, 3))
                assertEquals(0x80400000.toInt(), destination.pixelAt(3, 3))
                edit.record.close()
                assertEquals(Color.BLUE, restored.getPixel(2, 1))
                restored.recycle()
            }
        }
        bitmap.recycle()
    }

    @Test fun externalTextClipboardDoesNotResurrectOldNativeImagePayload() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val surface = app.inkloom.engine.v5.surface.PixelTileSurface(64, 64)
                surface.beginWrite("fixture").use { it.setPixel(10, 10, Color.RED); it.commit().close() }
                val mask = app.inkloom.engine.v5.surface.MaskTileSurface(64, 64)
                mask.beginWrite("fixture selection").use { it.setAlpha(10, 10, 255); it.commit().close() }
                val view = app.inkloom.ui.InkCanvasView(activity).apply {
                    layout(0, 0, 400, 400); pageModelWidth = 64f; pageModelHeight = 64f
                    setRasterLayerSurfaces(mapOf(app.inkloom.document.LayerIds.INK to surface))
                    bindDocumentSelectionState(app.inkloom.engine.v5.selection.DocumentSelectionRuntime(64)
                        .stateFor("clipboard fixture", "page", 64, 64))
                    assertTrue(replaceSelectionFromMask(mask.snapshot()))
                }
                assertTrue(view.copySelectionToClipboard())
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("fixture text", "unrelated text"))
                org.junit.Assert.assertFalse(view.pasteSelectionFromClipboard(atViewCenter = true))
                assertEquals(0, surface.pixelAt(32, 32))
                assertEquals(Color.RED, surface.pixelAt(10, 10))
            }
        }
    }

    @Test fun colorRangeSelectsMatchingDisconnectedPixelsAndCanIncludeAlpha() {
        val bitmap = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, Color.RED)
            setPixel(1, 0, Color.BLUE)
            setPixel(2, 0, Color.RED)
            setPixel(3, 0, 0x40ff0000)
        }
        val rgb = requireNotNull(SparseAlphaMask.fromColorRange(bitmap, Color.RED, .01f, false, 4f, 1f))
        assertNotNull(rgb.surface.snapshot().nonZeroBounds())
        assertEquals(255, rgb.surface.alphaAt(0, 0))
        assertEquals(0, rgb.surface.alphaAt(1, 0))
        assertEquals(255, rgb.surface.alphaAt(2, 0))
        assertEquals(255, rgb.surface.alphaAt(3, 0))
        val rgba = requireNotNull(SparseAlphaMask.fromColorRange(bitmap, Color.RED, .01f, true, 4f, 1f))
        assertEquals(0, rgba.surface.alphaAt(3, 0))
        bitmap.recycle()
    }

    @Test fun fullyTransparentSystemImageIsRejected() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        assertNull(RasterSelectionContent.fromBitmap(bitmap, 10, 10, PixelTileFormat.PREMULTIPLIED_SRGB_8888))
        bitmap.recycle()
    }
}
