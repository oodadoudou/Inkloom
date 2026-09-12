package app.inkloom

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import app.inkloom.document.CoverImageDecoder
import org.junit.Assert.*
import org.junit.Test

class CoverImageDecoderTest {
    @Test fun rejectingACorruptCoverDoesNotDeleteASharedContentAddressedAsset() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = java.io.File.createTempFile("broken-cover", ".image", context.cacheDir)
        val bytes = "Not an image".toByteArray()
        source.writeBytes(bytes)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val retained = java.io.File(context.filesDir, "document-assets/$hash.image").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                app.inkloom.document.DocumentAssetService(context).importCoverImage(android.net.Uri.fromFile(source))
            }
            assertTrue("Validation cannot delete an asset already owned elsewhere", retained.isFile)
            assertArrayEquals(bytes, retained.readBytes())
        } finally { source.delete(); retained.delete() }
    }

    @Test fun photoOrientationAndLargeDimensionsProduceABoundedUprightPreview() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File.createTempFile("cover-exif", ".jpg", context.cacheDir)
        val source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(source)
            canvas.drawColor(Color.RED)
            canvas.drawRect(800f, 0f, 1600f, 800f, android.graphics.Paint().apply { color = Color.BLUE })
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val preview = CoverImageDecoder.decode(file, 400)
            try {
                assertEquals(200, preview.width); assertEquals(400, preview.height)
                assertTrue(Color.red(preview.getPixel(100, 50)) > 240)
                assertTrue(Color.blue(preview.getPixel(100, 350)) > 240)
                assertTrue(preview.allocationByteCount <= 200 * 400 * 4)
            } finally { preview.recycle() }
        } finally { source.recycle(); file.delete() }
    }
}
