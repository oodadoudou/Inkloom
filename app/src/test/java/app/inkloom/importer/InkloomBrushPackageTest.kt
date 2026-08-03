package app.inkloom.importer

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushTaperMode
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkloomBrushPackageTest {
    @Test fun portablePresetRoundTripsFullSettingsAndMaterializesDistinctAssets() {
        val root = temporaryDirectory("roundtrip")
        try {
            val shape = File(root, "shape.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val dual = File(root, "dual.png").apply { writeBytes(byteArrayOf(5, 6, 7)) }
            val grain = File(root, "grain.png").apply { writeBytes(byteArrayOf(8, 9)) }
            val source = File(root, "commercial-source.brushset").apply { writeText("must not be embedded") }
            val original = ImportedBrush(
                id = "original-id",
                name = "Portable textured brush",
                format = BrushFormat.PROCREATE,
                compatibility = Compatibility.BASIC,
                sizeBytes = source.length(),
                report = listOf("Converted from an authorized source"),
                groupName = "Textured sketching",
                sourcePath = source.absolutePath,
                sha256 = "source-hash",
                tipPreviewPath = shape.absolutePath,
                tipPreviewPaths = listOf(shape.absolutePath),
                dualTipPreviewPaths = listOf(dual.absolutePath),
                grainPreviewPath = grain.absolutePath,
                grainDensity = .64f,
                grainScale = 215f,
                grainOffsetJitter = true,
                defaultSize = 37f,
                defaultOpacity = .72f,
                spacing = .18f,
                stabilization = .4f,
                randomRotationDegrees = 45f,
                strokeRotationJitterDegrees = 180f,
                spray = true,
                sprayDensity = 14f,
                spraySpread = 2.25f,
                tipFlipHorizontalMode = 2,
                tipFlipVerticalMode = 3,
                blendTipsWithDarken = true,
                dualBrush = true,
                dualCompositeMode = 7,
                dualApplyRgb = true,
                dualTipFlipHorizontalMode = 1,
                dualTipFlipVerticalMode = 2,
                dualBlendTipsWithDarken = true,
                dualSizeScale = .7f,
                dualStrokeRotationJitterDegrees = 90f,
                taperMode = BrushTaperMode.DISTANCE,
                taperStartEnabled = true,
                taperStartLength = 42f,
                watercolor = true,
                waterEdgeEnabled = true,
                waterEdgeOpacity = .35f,
                sizePressureCurve = listOf(BrushCurvePoint(0f, .1f), BrushCurvePoint(1f, .95f)),
                sizeSecondaryDynamics = BrushSecondaryDynamics(tiltEnabled = true, tiltMinimum = .25f, randomEnabled = true, randomMinimum = .8f),
                tags = listOf("sketch", "texture"),
                favorite = true,
                hidden = true,
                lastUsedAt = 1234L,
            )
            val packageFile = File(root, "portable.inkbrush")
            InkloomBrushPackage(File(root, "write-assets")).write(original, packageFile.outputStream())

            ZipFile(packageFile).use { zip ->
                val names = zip.entries().asSequence().map(ZipEntry::getName).toList()
                assertEquals(listOf("manifest.json", "assets/001.png", "assets/002.png", "assets/003.png"), names)
                assertFalse(names.any { it.contains(source.name) })
            }

            val fallback = ImportedBrush(
                id = "new-library-id",
                name = "Fallback",
                format = BrushFormat.INKLOOM,
                compatibility = Compatibility.COMPLETE,
                sizeBytes = packageFile.length(),
                report = emptyList(),
                sourcePath = packageFile.absolutePath,
                sha256 = "package-hash",
            )
            val restored = InkloomBrushPackage(File(root, "read-assets")).read(packageFile, "package-hash", fallback)

            assertEquals("new-library-id", restored.id)
            assertEquals(BrushFormat.INKLOOM, restored.format)
            assertEquals(Compatibility.COMPLETE, restored.compatibility)
            assertEquals(original.name, restored.name)
            assertEquals(original.defaultSize, restored.defaultSize, .0001f)
            assertEquals(original.defaultOpacity, restored.defaultOpacity, .0001f)
            assertEquals(original.spacing, restored.spacing, .0001f)
            assertEquals(original.grainScale, restored.grainScale, .0001f)
            assertTrue(restored.grainOffsetJitter)
            assertEquals(180f, restored.strokeRotationJitterDegrees, .0001f)
            assertEquals(original.sizePressureCurve, restored.sizePressureCurve)
            assertEquals(original.sizeSecondaryDynamics, restored.sizeSecondaryDynamics)
            assertEquals(original.tags, restored.tags)
            assertTrue(restored.dualBrush)
            assertEquals(7, restored.dualCompositeMode)
            assertTrue(restored.dualApplyRgb)
            assertEquals(2, restored.tipFlipHorizontalMode)
            assertEquals(3, restored.tipFlipVerticalMode)
            assertTrue(restored.blendTipsWithDarken)
            assertEquals(1, restored.dualTipFlipHorizontalMode)
            assertEquals(2, restored.dualTipFlipVerticalMode)
            assertTrue(restored.dualBlendTipsWithDarken)
            assertTrue(restored.watercolor)
            assertTrue(restored.taperStartEnabled)
            assertEquals(packageFile.absolutePath, restored.sourcePath)
            assertEquals("package-hash", restored.sha256)
            assertFalse(restored.favorite)
            assertFalse(restored.hidden)
            assertEquals(0L, restored.lastUsedAt)
            assertEquals(shape.readBytes().toList(), File(restored.tipPreviewPath!!).readBytes().toList())
            assertEquals(dual.readBytes().toList(), File(restored.dualTipPreviewPaths.single()).readBytes().toList())
            assertEquals(grain.readBytes().toList(), File(restored.grainPreviewPath!!).readBytes().toList())
            assertEquals(
                original,
                restored.copy(
                    id = original.id,
                    format = original.format,
                    compatibility = original.compatibility,
                    sizeBytes = original.sizeBytes,
                    report = original.report,
                    sourcePath = original.sourcePath,
                    sha256 = original.sha256,
                    tipPreviewPath = original.tipPreviewPath,
                    tipPreviewPaths = original.tipPreviewPaths,
                    dualTipPreviewPaths = original.dualTipPreviewPaths,
                    grainPreviewPath = original.grainPreviewPath,
                    favorite = original.favorite,
                    hidden = original.hidden,
                    lastUsedAt = original.lastUsedAt,
                    parserVersion = original.parserVersion,
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun unsafeArchiveIsRejectedBeforeAnyAssetIsExtracted() {
        val root = temporaryDirectory("unsafe")
        try {
            val source = File(root, "unsafe.inkbrush")
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../escaped.png")); zip.write(byteArrayOf(1)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write("{}".encodeToByteArray()); zip.closeEntry()
            }
            val assets = File(root, "assets")
            val fallback = ImportedBrush(name = "Unsafe", format = BrushFormat.INKLOOM, compatibility = Compatibility.COMPLETE, sizeBytes = source.length(), report = emptyList())

            val result = InkloomBrushPackage(assets).read(source, "unsafe", fallback)

            assertEquals(Compatibility.FAILED, result.compatibility)
            assertTrue(result.report.any { it.contains("unsafe path") })
            assertFalse(File(root, "escaped.png").exists())
            assertFalse(File(assets, "unsafe").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun temporaryDirectory(label: String): File =
        File(System.getProperty("java.io.tmpdir"), "inkloom-brush-package-$label-${System.nanoTime()}").apply { mkdirs() }
}
