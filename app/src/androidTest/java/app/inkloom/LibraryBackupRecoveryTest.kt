package app.inkloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkDocument
import app.inkloom.document.InkPage
import app.inkloom.document.LibraryBackupIntegrityException
import app.inkloom.document.LibraryBackupService
import app.inkloom.document.PageBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LibraryBackupRecoveryTest {
    @Test fun missingAssetIsRejectedBeforePublicationAndIntactNotebookCanBeRecovered() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val source = File(cache, "backup-source-${System.nanoTime()}").apply { mkdirs() }
        val target = File(cache, "backup-target-${System.nanoTime()}").apply { mkdirs() }
        try {
            val image = File(source, "background.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val documents = listOf(
                InkDocument(title = "Missing image", pages = listOf(InkPage(background = PageBackground.Image(image.absolutePath)))),
                InkDocument(title = "Plain paper"),
            )
            val valid = ByteArrayOutputStream().also { LibraryBackupService(source).export(documents, emptyList(), emptyList(), it) }.toByteArray()
            val missing = ByteArrayOutputStream()
            ZipInputStream(ByteArrayInputStream(valid)).use { input -> ZipOutputStream(missing).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name.startsWith("document-assets/")) continue
                    output.putNextEntry(ZipEntry(entry.name)); output.write(input.readBytes()); output.closeEntry()
                }
            } }
            val service = LibraryBackupService(target)
            assertTrue(runCatching { service.restore(ByteArrayInputStream(missing.toByteArray())) }.exceptionOrNull() is LibraryBackupIntegrityException)
            assertTrue(File(target, "document-assets").walkTopDown().none(File::isFile))
            val recovered = service.restore(ByteArrayInputStream(missing.toByteArray()), recoverIntactItems = true)
            assertEquals(listOf("Plain paper"), recovered.documents.map(InkDocument::title))
            assertEquals(1, recovered.skippedItems.size)
        } finally { source.deleteRecursively(); target.deleteRecursively() }
    }
}
