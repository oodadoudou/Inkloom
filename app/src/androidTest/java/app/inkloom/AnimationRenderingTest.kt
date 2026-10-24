package app.inkloom

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.*
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Onion skin on the real canvas and animated GIF export through the real page renderer. */
class AnimationRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = DocumentAssetService(instrumentation.targetContext)
    // Element positions are page fractions; sizes are page units (the page is 64 wide).
    private fun shape(layerId: String, x: Float) = PageElement.Shape(x = x, y = .25f, width = 24f, height = 32f,
        layerId = layerId, kind = ShapeKind.RECTANGLE, colorArgb = 0xff000000, fillArgb = 0xff000000, strokeWidth = 0f)
    private fun page(onion: OnionSkin?): InkPage {
        val folder = InkLayer(id = "walk", name = "Walk", role = LayerRole.FOLDER)
        val first = InkLayer(id = "c1", name = "Cel 1", parentId = folder.id, visible = false)
        val second = InkLayer(id = "c2", name = "Cel 2", parentId = folder.id)
        return InkPage(width = 64f, height = 64f, layers = listOf(folder.copy(animation = AnimationFolder(frameRate = 4,
            cels = listOf(AnimationCel(first.id, 1), AnimationCel(second.id, 2)), current = second.id, onionSkin = onion)), first, second),
            elements = listOf(shape(first.id, .0625f), shape(second.id, .5625f)))
    }

    @Test fun theCanvasShowsThePreviousCelFaintlyInBlueOnlyWhileOnionSkinIsOn() {
        fun render(page: InkPage): Bitmap {
            val onion = AnimationOnionSkin.cels(page.layers)
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            // As PaperCanvas does, an onion-skin cel's objects are drawn as if the cel were shown.
            val shown = page.copy(layers = page.layers.map { if (it.id in onion) it.copy(visible = true) else it })
            val elements = page.layers.filter { it.role != LayerRole.FOLDER && (it.visible || it.id in onion) }.associate {
                it.id to app.inkloom.ui.LayerElementBitmaps(assets.renderElementLayerBitmap(shown, it.id, 64), null) }
            try { instrumentation.runOnMainSync {
                InkCanvasView(instrumentation.targetContext).apply {
                    layout(0, 0, 64, 64); pagePaperColor = Color.WHITE
                    setDocumentLayers(page.layers)
                    layerOrder = page.layers.filter { it.role != LayerRole.FOLDER && LayerHierarchy.effectiveVisible(page.layers, it) }.map { it.id }
                    onionSkin = onion
                    displayLayerOrder = page.layers.filter { it.role != LayerRole.FOLDER && (it.id in onion || LayerHierarchy.effectiveVisible(page.layers, it)) }.map { it.id }
                    layerOpacities = page.layers.associate { it.id to it.opacity }
                    setLayerElementBitmaps(elements)
                }.draw(Canvas(bitmap))
            } } finally { elements.values.forEach { it.base?.recycle() } }
            return bitmap
        }
        val off = render(page(null))
        val on = render(page(OnionSkin(before = 1, after = 0, opacity = .5f)))
        try {
            // The current cel draws as it is either way.
            assertEquals(Color.BLACK, off.getPixel(48, 32)); assertEquals(Color.BLACK, on.getPixel(48, 32))
            // The hidden previous cel: paper without onion skin, a light blue with it.
            assertEquals(Color.WHITE, off.getPixel(16, 32))
            val tinted = on.getPixel(16, 32)
            assertTrue("onion pixel ${Integer.toHexString(tinted)}", Color.blue(tinted) > Color.red(tinted) + 40 && Color.blue(tinted) > Color.green(tinted) + 20 && Color.red(tinted) > 60)
        } finally { off.recycle(); on.recycle() }
    }

    @Test fun anAnimatedGifHasOneFramePerCelHeldForItsDurationWithoutOnionSkin() {
        val out = ByteArrayOutputStream()
        AnimationExport.writeGif(assets, page(OnionSkin()), "walk", 64, out)
        val bytes = out.toByteArray()
        @Suppress("DEPRECATION") val movie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
        // 1 and 2 frames at 4 fps: 250 + 500 ms.
        assertEquals(750, movie.duration())
        assertEquals(64, movie.width())
        @Suppress("DEPRECATION") fun frameAt(ms: Int): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { movie.setTime(ms); movie.draw(Canvas(it), 0f, 0f) }
        val first = frameAt(100); val second = frameAt(500)
        try {
            assertTrue(Color.red(first.getPixel(16, 32)) < 40); assertTrue(Color.red(first.getPixel(48, 32)) > 200)
            assertTrue(Color.red(second.getPixel(48, 32)) < 40); assertTrue("no onion skin in exports", Color.red(second.getPixel(16, 32)) > 200 && Color.blue(second.getPixel(16, 32)) > 200)
        } finally { first.recycle(); second.recycle() }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).also { assertEquals(64, it.width); it.recycle() }
    }

    @Test fun anMp4HasEveryAnimationFrameAtTheFrameRate() {
        val file = java.io.File(instrumentation.targetContext.cacheDir, "animation-test.mp4").apply { delete() }
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_CREATE or android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_TRUNCATE).use {
            AnimationVideoExport.write(assets, page(OnionSkin()), "walk", 160, it.fileDescriptor)
        }
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            assertEquals("video/avc", android.media.MediaExtractor().run { setDataSource(file.path); getTrackFormat(0).getString(android.media.MediaFormat.KEY_MIME).also { release() } })
            assertEquals(160, retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt())
            // 1 + 2 frames at 4 fps.
            assertEquals(3, retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)!!.toInt())
            val first = retriever.getFrameAtIndex(0)!!; val last = retriever.getFrameAtIndex(2)!!
            try {
                // Cel 1's shape is at the left, cel 2's at the right (the frames are scaled from 64 px).
                assertTrue(Color.red(first.getPixel(first.width / 4, first.height / 2)) < 80)
                assertTrue(Color.red(last.getPixel(first.width / 4, first.height / 2)) > 180)
                assertTrue(Color.red(last.getPixel(first.width * 3 / 4, first.height / 2)) < 80)
            } finally { first.recycle(); last.recycle() }
        } finally { retriever.release(); file.delete() }
    }
}
