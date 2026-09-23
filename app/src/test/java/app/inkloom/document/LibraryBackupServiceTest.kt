package app.inkloom.document

import app.inkloom.engine.v5.storage.PixelTileArchiveStore
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsBackupCodec
import app.inkloom.settings.ColorSetLibrary
import app.inkloom.settings.ColorSetSwatch
import app.inkloom.settings.NamedColorSet
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
    fun namedColorSetsLargerThanLegacyLimitSurviveActualZipExportAndRestore() {
        val sourceRoot = Files.createTempDirectory("inkloom-color-backup-source-").toFile()
        val restoreRoot = Files.createTempDirectory("inkloom-color-backup-target-").toFile()
        try {
            val colors = ColorSetLibrary(listOf(NamedColorSet(
                "colors", "完整命名色组",
                List(2048) { index -> ColorSetSwatch("tile-$index", 0xff998877.toInt(), "色".repeat(120)) },
                "tile-1500",
            )), "colors")
            val preferences = AppSettingsBackupCodec.encode(AppSettings(), colorSetLibrary = colors)
            assertTrue("Fixture must cross the old outer service limit", preferences.size > 512 * 1024)
            val backup = ByteArrayOutputStream().also { output ->
                LibraryBackupService(sourceRoot).export(emptyList(), emptyList(), emptyList(), output, preferences = preferences)
            }.toByteArray()
            val restored = LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(backup))
            val restoredPreferences = requireNotNull(restored.preferences)
            assertTrue(preferences.contentEquals(restoredPreferences))
            assertEquals(colors, AppSettingsBackupCodec.decode(restoredPreferences).getOrThrow().colorSetLibrary)
        } finally {
            sourceRoot.deleteRecursively()
            restoreRoot.deleteRecursively()
        }
    }

    @Test
    fun roundTripRestoresPortableLibraryWithoutOverwritingIds() {
        val sourceRoot = Files.createTempDirectory("inkloom-backup-source-").toFile()
        val restoreRoot = Files.createTempDirectory("inkloom-backup-target-").toFile()
        try {
            val documentAsset = File(sourceRoot, "document-assets/source.pdf").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val coverAsset = File(sourceRoot, "document-assets/cover.png").apply { writeBytes(byteArrayOf(10, 11, 12)) }
            val gradientMask = File(sourceRoot, "document-assets/gradient-mask.png").apply { writeBytes(byteArrayOf(13, 14, 15)) }
            val layerMaskAsset = File(sourceRoot, "document-assets/layer-mask.v5").apply { writeBytes(byteArrayOf(19, 20, 21)) }
            val layerMaskReference = MaskTileSurfaceRef(
                assetPath = layerMaskAsset.absolutePath,
                surfaceId = "backup-mask",
                logicalWidth = 512,
                logicalHeight = 512,
                tileSize = 128,
                revision = 1,
                defaultAlpha = 255,
                tileCount = 1,
            )
            val tileStore = PixelTileArchiveStore(File(sourceRoot, "document-assets/pixel-tiles"))
            val sparseSurface = PixelTileSurface(4096, 4096, surfaceId = "backup-surface").also { surface ->
                surface.beginWrite("backup fixture").run {
                    setPixel(301, 207, 0xff556677.toInt())
                    commit().close()
                }
            }
            val sparseManifest = tileStore.save(sparseSurface.snapshot())
            val sparseMetadata = tileStore.inspect(sparseManifest)
            val sparseReference = RasterTileSurfaceRef(
                manifestAssetPath = sparseManifest.absolutePath,
                surfaceId = sparseMetadata.surfaceId,
                logicalWidth = sparseMetadata.logicalWidth,
                logicalHeight = sparseMetadata.logicalHeight,
                tileSize = sparseMetadata.tileSize,
                revision = sparseMetadata.revision,
            )
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
                text = "sticky note",
            )
            val maskedGradient = PageElement.Shape(
                x = 0f,
                y = 0f,
                width = 1600f,
                height = 2263f,
                fillArgb = 0xFF112233L,
                gradientEndArgb = 0xFFFFFFFFL,
                maskAssetPath = gradientMask.absolutePath,
            )
            val page = InkPage(
                background = PageBackground.Pdf(documentAsset.absolutePath, 0),
                layers = defaultInkLayers() +
                    InkLayer(name = "Sparse Paint", rasterTileSurface = sparseReference, mask = LayerMask(surface = layerMaskReference)),
                elements = listOf(sticky, maskedGradient),
            )
            val folder = InkFolder(id = "folder-1", name = "School")
            val document = InkDocument(id = "document-1", title = "Lecture", folderId = folder.id, coverStyle = CoverStyle.CUSTOM_IMAGE, coverImagePath = coverAsset.absolutePath, pages = listOf(page))
            val template = PageTemplate(id = "template-1", name = "Lecture sheet", width = 1200f, height = 1800f, background = PageBackground.Pdf(documentAsset.absolutePath, 0))
            val templates = PageTemplateLibrary(listOf(template), setOf(template.id), mapOf(document.id to template.id), customCovers = listOf(NotebookCoverTemplate(id = "custom-cover", name = "Reusable cover", assetPath = coverAsset.absolutePath)))
            val preferences = AppSettingsBackupCodec.encode(AppSettings())
            val bytes = ByteArrayOutputStream().also { LibraryBackupService(sourceRoot).export(listOf(document), listOf(folder), listOf(brush), it, listOf("Drawing/Ink", "Empty/Erasers"), preferences, templates) }.toByteArray()
            val manifestText = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }
                    .first { it.name == "manifest.json" }
                zip.readBytes().decodeToString()
            }
            assertTrue(manifestText.contains("\"formatVersion\": 5"))
            assertTrue(manifestText.contains("\"engineVersion\": 5"))

            val restored = LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(bytes))

            assertEquals(1, restored.documents.size)
            assertEquals(2, restored.folders.size)
            assertEquals(1, restored.brushes.size)
            assertEquals(listOf("Drawing/Ink", "Empty/Erasers"), restored.brushGroups)
            assertTrue(preferences.contentEquals(restored.preferences))
            assertEquals(restored.brushes.single().id, restored.brushIdMappings[brush.id])
            assertNotEquals(document.id, restored.documents.single().id)
            assertNotEquals(folder.id, restored.documents.single().folderId)
            assertNotEquals(brush.id, restored.brushes.single().id)
            val restoredPage = restored.documents.single().pages.single()
            assertEquals(CoverStyle.CUSTOM_IMAGE, restored.documents.single().coverStyle)
            assertTrue(File(restored.documents.single().coverImagePath!!).isFile)
            assertTrue(File((restoredPage.background as PageBackground.Pdf).assetPath).isFile)
            val restoredSparse = requireNotNull(restoredPage.layers.first { it.name == "Sparse Paint" }.rasterTileSurface)
            val restoredLayerMask = requireNotNull(restoredPage.layers.first { it.name == "Sparse Paint" }.mask?.surface)
            assertTrue(File(restoredLayerMask.assetPath).readBytes().contentEquals(layerMaskAsset.readBytes()))
            val restoredTileStore = PixelTileArchiveStore(File(restoreRoot, "document-assets/pixel-tiles"))
            assertEquals(
                0xff556677.toInt(),
                restoredTileStore.load(File(restoredSparse.manifestAssetPath)).pixelAt(301, 207),
            )
            assertEquals("sticky note", restoredPage.elements.filterIsInstance<PageElement.Sticky>().single().text)
            val restoredGradient = restoredPage.elements.filterIsInstance<PageElement.Shape>().single()
            assertTrue(File(requireNotNull(restoredGradient.maskAssetPath)).isFile)
            assertTrue(File(restoredGradient.maskAssetPath).readBytes().contentEquals(gradientMask.readBytes()))
            val restoredTemplate = restored.pageTemplates.customTemplates.single()
            assertNotEquals(template.id, restoredTemplate.id)
            assertTrue(File((restoredTemplate.background as PageBackground.Pdf).assetPath).isFile)
            assertTrue(restoredTemplate.id in restored.pageTemplates.favoriteIds)
            assertEquals(restoredTemplate.id, restored.pageTemplates.notebookDefaults[restored.documents.single().id])
            val restoredCover = restored.pageTemplates.customCovers.single()
            assertNotEquals("custom-cover", restoredCover.id)
            assertEquals("Reusable cover", restoredCover.name)
            assertEquals(restored.documents.single().coverImagePath, restoredCover.assetPath)
            assertTrue(File(restoredCover.assetPath).readBytes().contentEquals(coverAsset.readBytes()))
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

    @Test fun corruptLaterAssetDoesNotPublishEarlierValidAssets() {
        val sourceRoot = Files.createTempDirectory("inkloom-backup-atomic-source-").toFile()
        val restoreRoot = Files.createTempDirectory("inkloom-backup-atomic-target-").toFile()
        try {
            val first = File(sourceRoot, "brush-assets/first.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val second = File(sourceRoot, "brush-assets/second.png").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val brushes = listOf(first, second).map { asset -> ImportedBrush(name = asset.name, format = BrushFormat.IMAGE, compatibility = Compatibility.COMPLETE, sizeBytes = 3, report = emptyList(), tipPreviewPath = asset.absolutePath) }
            val valid = ByteArrayOutputStream().also { LibraryBackupService(sourceRoot).export(emptyList(), emptyList(), brushes, it) }.toByteArray()
            val rewritten = ByteArrayOutputStream()
            var assetCount = 0
            java.util.zip.ZipInputStream(ByteArrayInputStream(valid)).use { input -> ZipOutputStream(rewritten).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    output.putNextEntry(ZipEntry(entry.name))
                    val bytes = input.readBytes()
                    output.write(if (entry.name.startsWith("brush-assets/") && ++assetCount == 2) byteArrayOf(9, 9, 9) else bytes)
                    output.closeEntry()
                }
            } }
            val failed = runCatching { LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(rewritten.toByteArray())) }
            org.junit.Assert.assertTrue(failed.isFailure)
            org.junit.Assert.assertTrue(File(restoreRoot, "brush-assets").listFiles().isNullOrEmpty())
            val recovered = LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(rewritten.toByteArray()), recoverIntactItems = true)
            assertEquals(1, recovered.brushes.size)
            assertEquals(1, recovered.skippedItems.size)
            assertEquals(1, File(restoreRoot, "brush-assets").listFiles().orEmpty().size)
        } finally { sourceRoot.deleteRecursively(); restoreRoot.deleteRecursively() }
    }

    @Test fun recoverySkipsDamagedNotebookAndKeepsIntactNotebook() {
        val sourceRoot = Files.createTempDirectory("inkloom-backup-recover-source-").toFile()
        val restoreRoot = Files.createTempDirectory("inkloom-backup-recover-target-").toFile()
        try {
            val image = File(sourceRoot, "document-assets/background.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            val damaged = InkDocument(id = "damaged", title = "Damaged", pages = listOf(InkPage(background = PageBackground.Image(image.absolutePath))))
            val intact = InkDocument(id = "intact", title = "Intact", pages = listOf(InkPage()))
            val backup = ByteArrayOutputStream().also { LibraryBackupService(sourceRoot).export(listOf(damaged, intact), emptyList(), emptyList(), it) }.toByteArray()
            val rewritten = ByteArrayOutputStream()
            java.util.zip.ZipInputStream(ByteArrayInputStream(backup)).use { input -> ZipOutputStream(rewritten).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name.startsWith("document-assets/")) continue
                    output.putNextEntry(ZipEntry(entry.name)); output.write(input.readBytes()); output.closeEntry()
                }
            } }
            val failed = runCatching { LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(rewritten.toByteArray())) }
            assertTrue(failed.exceptionOrNull() is LibraryBackupIntegrityException)
            val recovered = LibraryBackupService(restoreRoot).restore(ByteArrayInputStream(rewritten.toByteArray()), recoverIntactItems = true)
            assertEquals(listOf("Intact"), recovered.documents.map(InkDocument::title))
            assertEquals(1, recovered.skippedItems.size)
        } finally { sourceRoot.deleteRecursively(); restoreRoot.deleteRecursively() }
    }
}
