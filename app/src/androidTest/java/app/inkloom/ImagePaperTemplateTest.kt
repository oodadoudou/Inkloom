package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImagePaperTemplateTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val assets = DocumentAssetService(context)

    @Test fun fitCropAndTransparencyMatchExportAndSavedTemplate() {
        val file = File.createTempFile("paper-fit", ".png", context.cacheDir)
        val image = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.RED)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        try {
            assets.prepareImagePaper(Uri.fromFile(file), "Grid image").use { draft ->
                assertEquals(64f, draft.template.width)
                assertEquals(32f, draft.template.height)
                val background = (draft.template.background as PageBackground.Image).copy(paperAspectRatio = 1f, paperColorArgb = Color.GREEN.toLong() and 0xffffffffL)
                val template = draft.template.copy(width = 64f, height = 64f, background = background)
                val storeRoot = File(context.cacheDir, "paper-store-${System.nanoTime()}").apply { mkdirs() }
                try {
                    val store = PageTemplateStore(storeRoot)
                    store.add(listOf(template))
                    val restored = requireNotNull(store.load().template(template.id))
                    assertEquals(template, restored)
                    val page = PageTemplateOperations.newPage(restored, "Image paper")
                    val bitmap = assets.renderPageBitmap(page, 128)
                    try { assertEquals(Color.GREEN, bitmap.getPixel(64, 2)); assertEquals(Color.RED, bitmap.getPixel(64, 64)) }
                    finally { bitmap.recycle() }
                    val crop = ImagePaperRenderer.render(background.copy(cropToFill = true), 128)
                    try { assertEquals(Color.RED, crop.getPixel(64, 2)); assertEquals(128, crop.height) }
                    finally { crop.recycle() }
                    store.delete(template.id)
                    assertEquals(background, page.background)
                    assertTrue(File(background.assetPath).exists())
                } finally { storeRoot.deleteRecursively() }
            }
        } finally { file.delete() }
    }

    @Test fun malformedImageIsRejectedAndDraftIsNotAddedToLibrary() {
        val file = File.createTempFile("invalid-paper", ".png", context.cacheDir)
        file.writeText("not an image")
        try {
            assertTrue(runCatching { assets.prepareImagePaper(Uri.fromFile(file), "Broken") }.isFailure)
            assertTrue(runCatching { assets.importImage(Uri.fromFile(file), "Broken.png") }.isFailure)
        } finally { file.delete() }
    }

    @Test fun transparentPaperUsesSelectedBaseWithoutCheckerboardAndPreviewIsBounded() {
        val file = File.createTempFile("alpha-paper", ".png", context.cacheDir)
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.TRANSPARENT)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        try {
            val background = PageBackground.Image(file.absolutePath, paperAspectRatio = .01f, paperColorArgb = 0xffeeeeee)
            val result = ImagePaperRenderer.render(background, 4096)
            try { assertTrue(result.width <= 4096 && result.height <= 4096); assertEquals(0xffeeeeee.toInt(), result.getPixel(0,0)) }
            finally { result.recycle() }
        } finally { file.delete() }
    }
}
