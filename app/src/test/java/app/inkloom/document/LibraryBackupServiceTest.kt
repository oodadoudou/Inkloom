package app.inkloom.document

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBackupServiceTest {
    @Test
    fun roundTripRestoresPortableLibraryWithoutOverwritingIds() {
        val sourceRoot = Files.createTempDirectory("inkloom-backup-source-").toFile()
        val restoreRoot = Files.createTempDirectory("inkloom-backup-target-").toFile()
        try {
            val documentAsset = File(sourceRoot, "document-assets/source.pdf").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val coverAsset = File(sourceRoot, "document-assets/cover.png").apply { writeBytes(byteArrayOf(10, 11, 12)) }
            val tip = File(sourceRoot, "brush-assets/tip.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(4, 5, 6)) }
            val brushSource = File(sourceRoot, "brush-sources/source.brush").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(7, 8, 9)) }
            val brush = ImportedBrush(
                id = "brush-1",
                name = "Portable pencil",
                format = BrushFormat.PROCREATE,
                compatibility = Compatibility.HIGH,
                sizeBytes = brushSource.length(),
                report = listOf("Test"),
                sourcePath = brushSource.absolutePath,
                tipPreviewPath = tip.absolutePath,
                tipPreviewPaths = listOf(tip.absolutePath),
            )
            val sticky = PageElement.Sticky(
                x = .2f,
                y = .2f,
                text = "nested ink",
                strokes = listOf(InkStroke(points = listOf(StrokePoint(.1f, .1f)), brushId = brush.id, tipAssetPath = tip.absolutePath)),
            )
            val page = InkPage(
                background = PageBackground.Pdf(documentAsset.absolutePath, 0),
                strokes = listOf(InkStroke(points = listOf(StrokePoint(.1f, .2f)), brushId = brush.id, tipAssetPaths = listOf(tip.absolutePath))),
                elements = listOf(sticky),
            )
            val folder = InkFolder(id = "folder-1", name = "School")
            val document = InkDocument(id = "document-1", title = "Lecture", folderId = folder.id, coverStyle = CoverStyle.CUSTOM_IMAGE, coverImagePath = coverAsset.absolutePath, pages = listOf(page))
            val bytes = ByteArrayOutputStream().also { LibraryBackupService(sourceRoot).export(listOf(document), listOf(folder), listOf(brush), it) }.toByteArray()

            val restored = LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(bytes))

            assertEquals(1, restored.documents.size)
            assertEquals(2, restored.folders.size)
            assertEquals(1, restored.brushes.size)
            assertNotEquals(document.id, restored.documents.single().id)
            assertNotEquals(folder.id, restored.documents.single().folderId)
            assertNotEquals(brush.id, restored.brushes.single().id)
            val restoredPage = restored.documents.single().pages.single()
            assertEquals(CoverStyle.CUSTOM_IMAGE, restored.documents.single().coverStyle)
            assertTrue(File(restored.documents.single().coverImagePath!!).isFile)
            assertTrue(File((restoredPage.background as PageBackground.Pdf).assetPath).isFile)
            assertEquals(restored.brushes.single().id, restoredPage.strokes.single().brushId)
            assertTrue(File(restoredPage.strokes.single().tipAssetPaths.single()).isFile)
            assertEquals(restored.brushes.single().id, (restoredPage.elements.single() as PageElement.Sticky).strokes.single().brushId)
            assertFalse(restored.documents.single().trashedAt != null)
        } finally {
            sourceRoot.deleteRecursively()
            restoreRoot.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreRejectsPathTraversal() {
        val root = Files.createTempDirectory("inkloom-backup-malicious-").toFile()
        try {
            val bytes = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("../escape"))
                    zip.write(byteArrayOf(1))
                    zip.closeEntry()
                }
            }.toByteArray()
            LibraryBackupService(root).restore(ByteArrayInputStream(bytes))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreRejectsTamperedAsset() {
        val sourceRoot = Files.createTempDirectory("inkloom-backup-hash-source-").toFile()
        val restoreRoot = Files.createTempDirectory("inkloom-backup-hash-target-").toFile()
        try {
            val tip = File(sourceRoot, "brush-assets/tip.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val brush = ImportedBrush(name = "Hash", format = BrushFormat.IMAGE, compatibility = Compatibility.COMPLETE, sizeBytes = 3, report = emptyList(), tipPreviewPath = tip.absolutePath)
            val valid = ByteArrayOutputStream().also { LibraryBackupService(sourceRoot).export(emptyList(), emptyList(), listOf(brush), it) }.toByteArray()
            val rewritten = ByteArrayOutputStream()
            java.util.zip.ZipInputStream(ByteArrayInputStream(valid)).use { input ->
                ZipOutputStream(rewritten).use { output ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        output.putNextEntry(ZipEntry(entry.name))
                        val bytes = input.readBytes()
                        output.write(if (entry.name.startsWith("brush-assets/")) byteArrayOf(9, 9, 9) else bytes)
                        output.closeEntry()
                    }
                }
            }
            LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(rewritten.toByteArray()))
        } finally {
            sourceRoot.deleteRecursively()
            restoreRoot.deleteRecursively()
        }
    }
}
