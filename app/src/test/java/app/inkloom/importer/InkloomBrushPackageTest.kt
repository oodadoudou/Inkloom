package app.inkloom.importer

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushTaperMode
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspAntiAlias
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
                grainDensityMinimum = .18f,
                grainDensityDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .45f),
                grainScale = 215f,
                grainMovement = .35f,
                grainZoom = .7f,
                grainStrokeRotation = .75f,
                grainOffsetJitter = true,
                grainCompositeMode = CspTextureComposite.HEIGHT,
                grainFilteringMode = app.inkloom.document.ProcreateFiltering.IMPROVED,
                procreateRenderingMode = app.inkloom.document.ProcreateRenderingMode.HEAVY_GLAZE,
                defaultSize = 37f,
                defaultOpacity = .72f,
                spacing = .18f,
                spacingMode = app.inkloom.document.CspBrushGap.NARROW,
                stabilization = .4f,
                streamLine = .33f,
                streamLinePressure = .21f,
                procreateStabilization = .42f,
                motionFiltering = .57f,
                motionFilteringExpression = .28f,
                tiltGradation = .61f,
                tiltGradationStart = .23f,
                tiltBleed = .48f,
                tiltBleedStart = .31f,
                tiltSizeCompression = true,
                strokeFalloff = .63f,
                strokePathJitter = .43f,
                strokePathLongitudinalJitter = .27f,
                dualStrokePathJitter = .19f,
                dualStrokePathLongitudinalJitter = .31f,
                shapeRoundnessHorizontalJitter = .37f,
                shapeRoundnessVerticalJitter = .48f,
                dualShapeRoundnessHorizontalJitter = .59f,
                dualShapeRoundnessVerticalJitter = .68f,
                antiAliasMode = CspAntiAlias.WEAK,
                blur = 9.5f,
                blurUnit = 2,
                blurLinkedToSize = true,
                blurPressureEnabled = true,
                blurPressureMinimum = .15f,
                blurPressureCurve = listOf(BrushCurvePoint(0f, .15f), BrushCurvePoint(1f, 1f)),
                blurSecondaryDynamics = BrushSecondaryDynamics(tiltEnabled = true, tiltStart = .32f, tiltCurve = listOf(BrushCurvePoint(0f, 1f), BrushCurvePoint(1f, 0f)), velocityEnabled = true, velocityMinimum = .3f, velocityScale = 5f),
                randomRotationDegrees = 45f,
                strokeRotationJitterDegrees = 180f,
                spray = true,
                sprayParticleSizePressureEnabled = true,
                sprayParticleSizePressureMinimum = .2f,
                sprayParticleSizePressureCurve = listOf(BrushCurvePoint(0f, .1f), BrushCurvePoint(1f, 1f)),
                sprayParticleSizeSecondaryDynamics = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .3f),
                sprayDensity = 14f,
                sprayDensityPressureEnabled = true,
                sprayDensityPressureMinimum = .4f,
                sprayDensitySecondaryDynamics = BrushSecondaryDynamics(randomEnabled = true, randomMinimum = .7f),
                spraySpread = 2.25f,
                tipFlipHorizontalMode = 2,
                tipFlipVerticalMode = 3,
                blendTipsWithDarken = true,
                dualBrush = true,
                dualSpacingMode = app.inkloom.document.CspBrushGap.WIDE,
                dualCompositeMode = 7,
                dualApplyRgb = true,
                dualTipFlipHorizontalMode = 1,
                dualTipFlipVerticalMode = 2,
                dualBlendTipsWithDarken = true,
                dualSizeScale = .7f,
                dualSizeDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .2f, pressureCurve = listOf(BrushCurvePoint(0f, .1f), BrushCurvePoint(1f, 1f))),
                dualFlowDynamics = BrushParameterDynamics(secondary = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .3f)),
                dualSpacingDynamics = BrushParameterDynamics(secondary = BrushSecondaryDynamics(tiltEnabled = true, tiltMinimum = .4f)),
                dualThicknessDynamics = BrushParameterDynamics(secondary = BrushSecondaryDynamics(randomEnabled = true, randomMinimum = .5f)),
                dualAntiAliasMode = CspAntiAlias.STRONG,
                dualStrokeRotationJitterDegrees = 90f,
                dualSpray = true,
                dualSprayParticleSizeDynamics = BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .6f),
                dualSprayDensityDynamics = BrushParameterDynamics(secondary = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .7f)),
                taperMode = BrushTaperMode.DISTANCE,
                taperStartEnabled = true,
                taperStartLength = 42f,
                colorJitterHueDegrees = 17f,
                colorJitterSaturation = 8f,
                colorJitterValue = -5f,
                colorJitterTarget = 1,
                colorJitterHueDynamics = app.inkloom.document.BrushColorDynamics(randomEnabled = true, randomMinimum = -1f),
                procreateColorDynamics = app.inkloom.document.ProcreateColorDynamics(
                    stampHue = .11f, strokeSecondary = .22f, pressureHue = -.33f,
                    pressureHueCurve = listOf(BrushCurvePoint(0f, .1f), BrushCurvePoint(1f, .9f)),
                    pressureSecondary = .44f, tiltBrightness = -.55f, tiltStart = .36f,
                ),
                watercolor = true,
                colorMix = .65f,
                colorMixPressureEnabled = true,
                colorMixPressureMinimum = .2f,
                colorMixPressureCurve = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)),
                paintMix = .75f,
                paintMixPressureEnabled = true,
                paintMixPressureMinimum = .3f,
                paintMixSecondaryDynamics = BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .4f),
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
            assertEquals(original.streamLine, restored.streamLine, .0001f)
            assertEquals(original.streamLinePressure, restored.streamLinePressure, .0001f)
            assertEquals(original.procreateStabilization, restored.procreateStabilization, .0001f)
            assertEquals(original.motionFiltering, restored.motionFiltering, .0001f)
            assertEquals(original.motionFilteringExpression, restored.motionFilteringExpression, .0001f)
            assertEquals(original.tiltGradation, restored.tiltGradation, .0001f)
            assertEquals(original.tiltGradationStart, restored.tiltGradationStart, .0001f)
            assertEquals(original.tiltBleed, restored.tiltBleed, .0001f)
            assertEquals(original.tiltBleedStart, restored.tiltBleedStart, .0001f)
            assertTrue(restored.tiltSizeCompression)
            assertEquals(original.strokeFalloff, restored.strokeFalloff, .0001f)
            assertEquals(original.strokePathJitter, restored.strokePathJitter, .0001f)
            assertEquals(original.strokePathLongitudinalJitter, restored.strokePathLongitudinalJitter, .0001f)
            assertEquals(original.dualStrokePathJitter, restored.dualStrokePathJitter, .0001f)
            assertEquals(original.dualStrokePathLongitudinalJitter, restored.dualStrokePathLongitudinalJitter, .0001f)
            assertEquals(original.shapeRoundnessHorizontalJitter, restored.shapeRoundnessHorizontalJitter, .0001f)
            assertEquals(original.shapeRoundnessVerticalJitter, restored.shapeRoundnessVerticalJitter, .0001f)
            assertEquals(original.dualShapeRoundnessHorizontalJitter, restored.dualShapeRoundnessHorizontalJitter, .0001f)
            assertEquals(original.dualShapeRoundnessVerticalJitter, restored.dualShapeRoundnessVerticalJitter, .0001f)
            assertEquals(app.inkloom.document.CspBrushGap.NARROW, restored.spacingMode)
            assertEquals(CspAntiAlias.WEAK, restored.antiAliasMode)
            assertEquals(CspAntiAlias.STRONG, restored.dualAntiAliasMode)
            assertEquals(original.blur, restored.blur, .0001f)
            assertEquals(original.blurUnit, restored.blurUnit)
            assertTrue(restored.blurLinkedToSize)
            assertTrue(restored.blurPressureEnabled)
            assertEquals(original.blurPressureMinimum, restored.blurPressureMinimum, .0001f)
            assertEquals(original.blurPressureCurve, restored.blurPressureCurve)
            assertEquals(original.blurSecondaryDynamics, restored.blurSecondaryDynamics)
            assertEquals(original.blurSecondaryDynamics.tiltCurve, restored.blurSecondaryDynamics.tiltCurve)
            assertEquals(5f, restored.blurSecondaryDynamics.velocityScale, .0001f)
            assertEquals(.32f, restored.blurSecondaryDynamics.tiltStart, .0001f)
            assertEquals(original.grainScale, restored.grainScale, .0001f)
            assertEquals(original.grainDensityMinimum, restored.grainDensityMinimum, .0001f)
            assertEquals(original.grainMovement, restored.grainMovement, .0001f)
            assertEquals(original.grainZoom, restored.grainZoom, .0001f)
            assertEquals(original.grainStrokeRotation, restored.grainStrokeRotation, .0001f)
            assertTrue(restored.grainOffsetJitter)
            assertEquals(CspTextureComposite.HEIGHT, restored.grainCompositeMode)
            assertEquals(app.inkloom.document.ProcreateFiltering.IMPROVED, restored.grainFilteringMode)
            assertEquals(app.inkloom.document.ProcreateRenderingMode.HEAVY_GLAZE, restored.procreateRenderingMode)
            assertEquals(180f, restored.strokeRotationJitterDegrees, .0001f)
            assertEquals(original.sizePressureCurve, restored.sizePressureCurve)
            assertEquals(original.sizeSecondaryDynamics, restored.sizeSecondaryDynamics)
            assertEquals(original.tags, restored.tags)
            assertTrue(restored.dualBrush)
            assertEquals(app.inkloom.document.CspBrushGap.WIDE, restored.dualSpacingMode)
            assertEquals(7, restored.dualCompositeMode)
            assertTrue(restored.dualApplyRgb)
            assertEquals(2, restored.tipFlipHorizontalMode)
            assertEquals(3, restored.tipFlipVerticalMode)
            assertTrue(restored.blendTipsWithDarken)
            assertEquals(1, restored.dualTipFlipHorizontalMode)
            assertEquals(2, restored.dualTipFlipVerticalMode)
            assertTrue(restored.dualBlendTipsWithDarken)
            assertTrue(restored.watercolor)
            assertEquals(17f, restored.colorJitterHueDegrees, .0001f)
            assertEquals(8f, restored.colorJitterSaturation, .0001f)
            assertEquals(-5f, restored.colorJitterValue, .0001f)
            assertEquals(1, restored.colorJitterTarget)
            assertEquals(-1f, restored.colorJitterHueDynamics.randomMinimum, .0001f)
            assertEquals(original.procreateColorDynamics, restored.procreateColorDynamics)
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
