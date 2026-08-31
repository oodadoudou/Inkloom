package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.ProcreateFiltering
import app.inkloom.document.ProcreateRenderingMode
import app.inkloom.document.ProcreateBrushBlend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProcreateBrushParserTest {
    @Test fun keyedArchiveReadsNamesCurvesAndCoreDynamics() {
        val archive = ProcreateKeyedArchive(Base64.getDecoder().decode(SYNTHETIC_ARCHIVE))

        assertEquals("Synthetic Procreate", archive.string("name"))
        assertEquals(4, archive.int("version"))
        assertEquals(.5f, archive.float("paintSize"), .0001f)
        assertTrue(archive.boolean("shapeAzimuth"))
        assertEquals(.1f, archive.curve("dynamicsPressureSizeCurve").first().y, .0001f)
        assertEquals(.9f, archive.curve("dynamicsPressureSizeCurve").last().y, .0001f)
    }

    @Test fun syntheticBrushMapsShapeGrainPressureSpeedWetMixAndTaper() {
        val root = temporaryDirectory("synthetic")
        try {
            val source = File(root, "synthetic.brush")
            writeBrush(source)

            val brush = ProcreateBrushParser(File(root, "assets")).enrich(source, "fixture", fallback(source)).single()
            val sourceMetadata = ProcreateKeyedArchive(Base64.getDecoder().decode(SYNTHETIC_ARCHIVE))

            assertEquals("Synthetic Procreate", brush.name)
            assertEquals(BrushFormat.PROCREATE, brush.format)
            assertEquals(Compatibility.BASIC, brush.compatibility)
            assertEquals(20f, brush.defaultSize, .0001f)
            assertEquals(.6f, brush.defaultOpacity, .0001f)
            assertEquals(.2f, brush.spacing, .0001f)
            assertEquals(.3f, brush.streamLine, .0001f)
            assertEquals(0f, brush.stabilization, .0001f)
            assertEquals(.6f, brush.thickness, .0001f)
            assertEquals(sourceMetadata.float("shapeRotation") * 360f, brush.rotationDegrees, .001f)
            assertEquals((sourceMetadata.float("shapeAngle") / (Math.PI / 2.0).toFloat()).coerceIn(-1f, 1f), brush.shapeTouchRotation, .001f)
            assertEquals(sourceMetadata.boolean("oriented"), brush.shapeOrientToScreen)
            assertTrue(brush.followStylusOrientation)
            assertFalse(brush.followStrokeDirection)
            assertTrue(brush.sizePressureEnabled)
            assertEquals(.2f, brush.sizePressureMinimum, .0001f)
            assertTrue(brush.sizeSecondaryDynamics.velocityEnabled)
            assertTrue(brush.sizeSecondaryDynamics.randomEnabled)
            assertFalse(brush.spray)
            assertEquals(0f, brush.spraySpread, .0001f)
            assertEquals(Math.toDegrees(.5).toFloat(), brush.randomRotationDegrees, .001f)
            assertTrue(brush.watercolor)
            assertTrue(brush.waterEdgeEnabled)
            assertTrue(brush.taperStartEnabled)
            assertTrue(brush.taperEndEnabled)
            assertEquals(sourceMetadata.float("taperStartLength"), brush.procreateTaper.touchStartLength, .0001f)
            assertEquals(sourceMetadata.float("taperEndLength"), brush.procreateTaper.touchEndLength, .0001f)
            assertEquals(sourceMetadata.float("taperSize"), brush.procreateTaper.touchSize, .0001f)
            assertEquals(sourceMetadata.float("taperOpacity"), brush.procreateTaper.touchOpacity, .0001f)
            assertEquals(sourceMetadata.float("taperShape"), brush.procreateTaper.touchTip, .0001f)
            assertEquals(sourceMetadata.float("taperPressure"), brush.procreateTaper.pressureResponse, .0001f)
            assertTrue(File(brush.tipPreviewPath!!).isFile)
            assertTrue(File(brush.grainPreviewPath!!).isFile)
            assertEquals(sourceMetadata.float("textureScale", 1f) * 100f, brush.grainScale, .0001f)
            assertEquals(sourceMetadata.float("textureMovement", 1f), brush.grainMovement, .0001f)
            assertEquals(sourceMetadata.float("textureZoom", 1f), brush.grainZoom, .0001f)
            assertEquals(sourceMetadata.float("dynamicsJitterHue"), brush.procreateColorDynamics.stampHue, .0001f)
            assertEquals(sourceMetadata.float("dynamicsJitterStrokeHue"), brush.procreateColorDynamics.strokeHue, .0001f)
            assertEquals(sourceMetadata.float("dynamicsPressureSecondaryColor"), brush.procreateColorDynamics.pressureSecondary, .0001f)
            assertEquals(sourceMetadata.float("dynamicsTiltSecondaryColor"), brush.procreateColorDynamics.tiltSecondary, .0001f)
            assertEquals(sourceMetadata.float("dynamicsMix"), brush.procreateWetMix.dilution, .0001f)
            assertEquals(sourceMetadata.float("dynamicsLoad"), brush.procreateWetMix.charge, .0001f)
            assertEquals(sourceMetadata.float("dynamicsPressureMix"), brush.procreateWetMix.attack, .0001f)
            assertEquals(sourceMetadata.float("dynamicsWetAccumulation"), brush.procreateWetMix.pull, .0001f)
            assertEquals(sourceMetadata.float("dynamicsSmudgeAccumulation"), brush.procreateWetMix.grade, .0001f)
            assertEquals(sourceMetadata.float("dynamicsBlur"), brush.procreateWetMix.blur, .0001f)
            assertEquals(sourceMetadata.float("dynamicsBlurJitter"), brush.procreateWetMix.blurJitter, .0001f)
            assertEquals(sourceMetadata.float("dynamicsWetnessJitter"), brush.procreateWetMix.wetnessJitter, .0001f)
            assertEquals(sourceMetadata.float("dynamicsMixSoftening"), brush.procreateWetMix.legacyMixSoftening, .0001f)
            assertEquals(CspAntiAlias.MEDIUM, brush.antiAliasMode)
            assertEquals(ProcreateFiltering.CLASSIC, brush.grainFilteringMode)
            assertEquals(ProcreateRenderingMode.INTENSE_GLAZE, brush.procreateRenderingMode)
            assertEquals(sourceMetadata.int("blendMode"), brush.procreateBrushBlendMode)
            assertEquals(sourceMetadata.int("burntEdgesBlendMode", ProcreateBrushBlend.MULTIPLY), brush.procreateBurntEdgeBlendMode)
            assertEquals(sourceMetadata.boolean("blendGammaCorrect"), brush.procreateLuminanceBlending)
            assertFalse(brush.alphaThresholdEnabled)
            assertEquals(0f, brush.alphaThreshold, .0001f)
            assertEquals(ProcreateBrushParser.CURRENT_VERSION, brush.parserVersion)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun dualBrushRetainsIndependentSecondaryGrainAssetAndSettings() {
        val root = temporaryDirectory("dual-grain")
        try {
            val source = File(root, "dual.brush")
            writeBrush(source, includeDual = true)

            val brush = ProcreateBrushParser(File(root, "assets")).enrich(source, "dual-grain-fixture", fallback(source)).single()
            val metadata = ProcreateKeyedArchive(Base64.getDecoder().decode(SYNTHETIC_ARCHIVE))

            assertTrue(brush.dualBrush)
            assertTrue(File(brush.dualGrainPreviewPath!!).isFile)
            assertEquals(metadata.float("grainDepth"), brush.dualGrainDensity, .0001f)
            assertEquals(metadata.float("textureScale", 1f) * 100f, brush.dualGrainScale, .0001f)
            assertEquals(metadata.float("textureMovement", 1f), brush.dualGrainMovement, .0001f)
            assertEquals(metadata.float("textureZoom", 1f), brush.dualGrainZoom, .0001f)
            assertEquals(ProcreateFiltering.CLASSIC, brush.dualGrainFilteringMode)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun alphaThresholdSwitchAndCutoffAreReadFromTheArchive() {
        val root = temporaryDirectory("alpha-threshold")
        try {
            val source = File(root, "threshold.brush")
            writeBrush(source, THRESHOLD_ARCHIVE)

            val brush = ProcreateBrushParser(File(root, "assets")).enrich(source, "threshold-fixture", fallback(source)).single()

            assertTrue(brush.alphaThresholdEnabled)
            assertEquals(.42f, brush.alphaThreshold, .0001f)
            assertTrue(brush.report.any { it.contains("Alpha Threshold") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun speedSpacingFollowsProcreateDirectionInsteadOfSizeDirection() {
        val increasing = ProcreateBrushParser(File("unused")).spacingDynamics(.75f, 0f)
        val decreasing = ProcreateBrushParser(File("unused")).spacingDynamics(-.75f, 0f)
        val slow = app.inkloom.document.StrokePoint(0f, 0f, velocity = 0f)
        val fast = app.inkloom.document.StrokePoint(0f, 0f, velocity = 1f)

        assertTrue(app.inkloom.document.BrushDynamics.secondary(increasing, fast, 1, 2) > app.inkloom.document.BrushDynamics.secondary(increasing, slow, 1, 2))
        assertTrue(app.inkloom.document.BrushDynamics.secondary(decreasing, fast, 1, 2) < app.inkloom.document.BrushDynamics.secondary(decreasing, slow, 1, 2))
    }

    @Test fun pressureAndTiltRoundnessSquashTowardTheirAuthoredMinimums() {
        val dynamics = ProcreateBrushParser(File("unused")).roundnessDynamics(
            pressureAmount = 1f,
            pressureMinimum = .3f,
            pressureCurve = emptyList(),
            tiltAmount = 1f,
            tiltMinimum = .4f,
            tiltStart = .2f,
        )

        assertTrue(dynamics.pressureEnabled)
        assertEquals(1f, app.inkloom.document.BrushDynamics.pressure(true, dynamics.pressureMinimum, dynamics.pressureCurve, 0f), .0001f)
        assertEquals(.3f, app.inkloom.document.BrushDynamics.pressure(true, dynamics.pressureMinimum, dynamics.pressureCurve, 1f), .0001f)
        assertTrue(dynamics.secondary.tiltEnabled)
        assertEquals(1f, app.inkloom.document.BrushDynamics.secondary(dynamics.secondary, app.inkloom.document.StrokePoint(0f, 0f, tilt = 0f), 1, 2), .0001f)
        assertEquals(.4f, app.inkloom.document.BrushDynamics.secondary(dynamics.secondary, app.inkloom.document.StrokePoint(0f, 0f, tilt = (Math.PI / 2).toFloat()), 1, 2), .0001f)
    }

    @Test fun signedTiltDynamicsPreserveTheirResponseDirection() {
        val parser = ProcreateBrushParser(File("unused"))
        val positive = parser.secondaryDynamics(.75f, 0f, 0f)
        val negative = parser.secondaryDynamics(-.75f, 0f, 0f)
        val upright = app.inkloom.document.StrokePoint(0f, 0f, tilt = 0f)
        val flat = app.inkloom.document.StrokePoint(0f, 0f, tilt = (Math.PI / 2).toFloat())

        assertTrue(app.inkloom.document.BrushDynamics.secondary(positive, flat, 1, 2) > app.inkloom.document.BrushDynamics.secondary(positive, upright, 1, 2))
        assertTrue(app.inkloom.document.BrushDynamics.secondary(negative, flat, 1, 2) < app.inkloom.document.BrushDynamics.secondary(negative, upright, 1, 2))
    }

    @Test fun shapeFlipSwitchesMapToPerStampRandomMirroring() {
        val parser = ProcreateBrushParser(File("unused"))

        assertEquals(app.inkloom.document.BrushTipFlip.OFF, parser.procreateFlipMode(false))
        assertEquals(app.inkloom.document.BrushTipFlip.RANDOM, parser.procreateFlipMode(true))
    }

    @Test fun unsafeZipPathFailsWithoutExtractingAnything() {
        val root = temporaryDirectory("unsafe")
        try {
            val source = File(root, "unsafe.brush")
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../Shape.png")); zip.write(byteArrayOf(1)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("Brush.archive")); zip.write(Base64.getDecoder().decode(SYNTHETIC_ARCHIVE)); zip.closeEntry()
            }

            val brush = ProcreateBrushParser(File(root, "assets")).enrich(source, "unsafe", fallback(source)).single()

            assertTrue(brush.report.any { it.contains("unsafe path") })
            assertEquals(Compatibility.FAILED, brush.compatibility)
            assertFalse(File(root, "Shape.png").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun brushsetKeepsOneTerminalRecordPerPayloadAndStableSourceIds() {
        val root = temporaryDirectory("conservation")
        try {
            val source = File(root, "mixed.brushset")
            val good = "11111111-1111-1111-1111-111111111111"
            val bad = "22222222-2222-2222-2222-222222222222"
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("$good/Brush.archive")); zip.write(Base64.getDecoder().decode(SYNTHETIC_ARCHIVE)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("$good/Shape.png")); zip.write(SYNTHETIC_PNG); zip.closeEntry()
                zip.putNextEntry(ZipEntry("$bad/Brush.archive")); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry()
            }
            val parser = ProcreateBrushParser(File(root, "assets"))
            val first = parser.enrich(source, "stable-hash", fallback(source))
            val second = parser.enrich(source, "stable-hash", fallback(source))

            assertEquals(2, first.size)
            assertEquals(first.map(ImportedBrush::id), second.map(ImportedBrush::id))
            assertEquals(listOf("mixed", "mixed"), first.map(ImportedBrush::groupName))
            assertEquals(listOf(0, 1), first.map(ImportedBrush::sortOrder))
            assertEquals(1, first.count { it.compatibility == Compatibility.FAILED })
            assertTrue(first.single { it.compatibility == Compatibility.FAILED }.report.any { it.contains("brush 2/2") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun parsesAuthorizedRealBrushsetWhenConfigured() {
        val sampleRoot = System.getProperty("procreate.samples.dir")?.let(::File)
        assumeTrue(sampleRoot != null && sampleRoot.isDirectory)
        val source = sampleRoot!!.walkTopDown().firstOrNull {
            it.isFile && it.extension.equals("brushset", true) && it.name.contains("速写笔全家桶")
        }
        assumeTrue(source != null)
        val root = temporaryDirectory("real")
        try {
            val brushes = ProcreateBrushParser(File(root, "assets")).enrich(source!!, "real-sample", fallback(source))

            assertEquals(30, brushes.size)
            assertEquals("白川柏川 挚爱线稿笔", brushes.first().name)
            assertTrue(brushes.all { it.format == BrushFormat.PROCREATE })
            assertTrue(brushes.all { it.tipPreviewPath?.let(::File)?.isFile == true })
            assertTrue(brushes.all { it.grainPreviewPath?.let(::File)?.isFile == true })
            assertTrue(brushes.any { it.dualBrush && it.dualTipPreviewPaths.isNotEmpty() })
            assertTrue(brushes.any { it.dualBrush && it.dualGrainPreviewPath?.let(::File)?.isFile == true })
            assertTrue(brushes.any { it.sizePressureEnabled })
            assertTrue(brushes.any { it.opacityPressureEnabled })
            assertTrue(brushes.any { it.watercolor })
            assertTrue(brushes.any { it.taperStartEnabled })
            assertTrue(brushes.any { it.taperEndEnabled })
            assertTrue(brushes.any { brush -> brush.report.any { it.contains("Pressure and Touch Taper") } })
            assertTrue(brushes.any { it.strokePathJitter > 0f })
            assertTrue(brushes.any { it.strokePathLongitudinalJitter > 0f })
            assertTrue(brushes.any { it.randomRotationDegrees > 0f })
            assertTrue(brushes.any { it.strokeRotationJitterDegrees > 0f })
            assertTrue(brushes.any { it.stampCount > 1 })
            assertTrue(brushes.any { it.stampCountJitter > 0f })
            assertTrue(brushes.any { it.tiltGradation > 0f })
            assertTrue(brushes.any { it.tiltSizeCompression })
            assertTrue(brushes.any { it.strokeFalloff > 0f })
            assertTrue(brushes.any { it.grainStrokeRotation != 0f })
            assertTrue(brushes.any { it.grainDensityMinimum > 0f })
            assertTrue(brushes.any { it.grainDensityDynamics.secondary.randomEnabled })
            assertTrue(brushes.none { it.spray && (it.strokePathJitter > 0f || it.strokePathLongitudinalJitter > 0f) })
            assertEquals(23, brushes.count { it.grainOffsetJitter })
            assertTrue(brushes.all { it.grainFilteringMode in ProcreateFiltering.NONE..ProcreateFiltering.IMPROVED })
            assertTrue(brushes.map(ImportedBrush::grainFilteringMode).distinct().size >= 2)
            assertEquals(3, brushes.map(ImportedBrush::procreateRenderingMode).distinct().size)
            assertTrue(brushes.all { it.alphaThreshold in 0f..1f })
            assertTrue(brushes.all { it.procreateBrushBlendMode in ProcreateBrushBlend.artisticModes })
            assertTrue(brushes.all { it.procreateBurntEdgeBlendMode in ProcreateBrushBlend.artisticModes })
            assertTrue(brushes.any { it.procreateBrushBlendMode == ProcreateBrushBlend.MULTIPLY })
            assertTrue(brushes.any { it.procreateRenderingMode == ProcreateRenderingMode.INTENSE_GLAZE })
            assertTrue(brushes.any { it.procreateRenderingMode == ProcreateRenderingMode.UNIFORM_BLENDING })
            assertTrue(brushes.any { it.procreateRenderingMode == ProcreateRenderingMode.INTENSE_BLENDING })
            assertTrue(brushes.any { brush -> brush.report.any { it.contains("stroke-path jitter") } })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun parsesAuthorizedRealBrushTaperWhenConfigured() {
        val sampleRoot = System.getProperty("procreate.samples.dir")?.let(::File)
        assumeTrue(sampleRoot != null && sampleRoot.isDirectory)
        val source = sampleRoot!!.walkTopDown().firstOrNull { it.isFile && it.extension.equals("brush", true) }
        assumeTrue(source != null)
        val root = temporaryDirectory("real-taper")
        try {
            val brush = ProcreateBrushParser(File(root, "assets")).enrich(source!!, "real-taper", fallback(source)).single()

            assertFalse("Expected an authored Taper program in ${source.name}", brush.procreateTaper.isDisabled())
            assertTrue(brush.procreateTaper.pressureStartLength > 0f || brush.procreateTaper.pressureEndLength > 0f)
            assertTrue("Expected the authorized brush to retain signed Touch Rotation", brush.shapeTouchRotation != 0f)
            assertEquals("Orient to Screen must not masquerade as Follow Stroke", false, brush.followStrokeDirection)
            assertTrue(brush.report.any { it.contains("Pressure and Touch Taper") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun auditsEveryAuthorizedProcreateBrushSemanticWhenConfigured() {
        val sampleRoot = System.getProperty("procreate.samples.dir")?.let(::File)
        assumeTrue(sampleRoot != null && sampleRoot.isDirectory)
        val sources = sampleRoot!!.walkTopDown().filter { source ->
            source.isFile && source.extension.lowercase() in setOf("brush", "brushset")
        }.sortedBy(File::getAbsolutePath).toList()
        assumeTrue(sources.isNotEmpty())
        val root = temporaryDirectory("semantic-corpus")
        try {
            val clusters = linkedMapOf<String, Int>()
            var discovered = 0
            var usable = 0
            var rejected = 0
            var failed = 0
            sources.forEachIndexed { index, source ->
                val parsed = ProcreateBrushParser(File(root, "assets-$index"))
                    .enrich(source, "corpus-${source.absolutePath.hashCode()}", fallback(source))
                discovered += parsed.size
                parsed.forEach { brush ->
                    BrushSemanticMapper.describe(brush)
                        .filter { it.executionDisposition != app.inkloom.model.SemanticExecutionDisposition.SUPPORTED_AND_EXECUTED }
                        .map { it.sourceSemantic.substringAfter('.') }
                        .distinct()
                        .forEach { semantic -> clusters[semantic] = (clusters[semantic] ?: 0) + 1 }
                    val terminal = app.inkloom.brushpresetv5.BrushPresetV5Import.convertOrReject(brush)
                    when {
                        terminal.v5PresetJson != null -> usable += 1
                        terminal.report.any { it.contains("parsing failed", ignoreCase = true) } -> failed += 1
                        else -> rejected += 1
                    }
                }
            }
            println(
                "PROCREATE_SEMANTIC_CORPUS files=${sources.size} brushes=$discovered usable=$usable " +
                    "rejected=$rejected failed=$failed clusters=${clusters.entries.sortedByDescending { it.value }}",
            )
            assertEquals("Authorized corpus source-file golden changed", 11, sources.size)
            assertEquals("Authorized corpus source-brush golden changed", 391, discovered)
            assertEquals("Every discovered brush needs exactly one terminal state", discovered, usable + rejected + failed)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun admitsMissingNamedSourceResourcesOnlyAsExplicitApproximationWhenConfigured() {
        val sampleRoot = System.getProperty("procreate.samples.dir")?.let(::File)
        assumeTrue(sampleRoot != null && sampleRoot.isDirectory)
        val sources = sampleRoot!!.walkTopDown().filter { source ->
            source.isFile && source.extension.lowercase() in setOf("brush", "brushset")
        }.sortedBy(File::getAbsolutePath).toList()
        assumeTrue(sources.isNotEmpty())
        val root = temporaryDirectory("approximated-corpus")
        try {
            val terminal = sources.flatMapIndexed { index, source ->
                ProcreateBrushParser(
                    assetDirectory = File(root, "assets-$index"),
                    allowProceduralApproximation = true,
                ).enrich(source, "approx-${source.absolutePath.hashCode()}", fallback(source))
                    .map(app.inkloom.brushpresetv5.BrushPresetV5Import::convertOrReject)
            }
            val approximated = terminal.filter { brush ->
                brush.v5PresetJson != null && brush.report.any { it.startsWith("APPROXIMATED_RESOURCE:") }
            }
            assertEquals(391, terminal.size)
            assertTrue("The real corpus must exercise explicit source-resource approximation", approximated.isNotEmpty())
            assertTrue(approximated.all { brush ->
                brush.semanticMappings.filter { it.sourceSemantic in setOf("PROCREATE.shape", "PROCREATE.grain.basic") }
                    .any { it.fidelity == app.inkloom.model.SemanticFidelity.APPROXIMATE }
            })
            assertTrue(approximated.all { brush ->
                brush.report.none { it.contains("extracted as the real", ignoreCase = true) }
            })
            assertTrue(terminal.none { brush ->
                brush.v5PresetJson != null &&
                    brush.report.any { it.startsWith("APPROXIMATED_RESOURCE:") } &&
                    brush.semanticMappings.none { it.fidelity == app.inkloom.model.SemanticFidelity.APPROXIMATE }
            })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun fallback(source: File) = ImportedBrush(
        name = source.nameWithoutExtension,
        format = BrushFormat.PROCREATE,
        compatibility = Compatibility.BASIC,
        sizeBytes = source.length(),
        report = emptyList(),
    )

    private fun writeBrush(target: File, archive: String = SYNTHETIC_ARCHIVE, includeDual: Boolean = false) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Brush.archive")); zip.write(Base64.getDecoder().decode(archive)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("Shape.png")); zip.write(SYNTHETIC_PNG); zip.closeEntry()
            zip.putNextEntry(ZipEntry("Grain.png")); zip.write(SYNTHETIC_PNG); zip.closeEntry()
            if (includeDual) {
                zip.putNextEntry(ZipEntry("Sub01/Brush.archive")); zip.write(Base64.getDecoder().decode(archive)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("Sub01/Shape.png")); zip.write(SYNTHETIC_PNG); zip.closeEntry()
                zip.putNextEntry(ZipEntry("Sub01/Grain.png")); zip.write(SYNTHETIC_PNG); zip.closeEntry()
            }
        }
    }

    private fun temporaryDirectory(label: String): File =
        File(System.getProperty("java.io.tmpdir"), "inkloom-procreate-$label-${System.nanoTime()}").apply { mkdirs() }

    private companion object {
        val SYNTHETIC_PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M/wHwAF/gL+X7o9WQAAAABJRU5ErkJggg==")
        const val THRESHOLD_ARCHIVE = "YnBsaXN0MDDUAQIDBAUGBwpYJHZlcnNpb25ZJGFyY2hpdmVyVCR0b3BYJG9iamVjdHMSAAGGoF8QD05TS2V5ZWRBcmNoaXZlctEICVRyb290gAGiCwxVJG51bGzUDQ4PEBESExRUbmFtZVd2ZXJzaW9uXmFscGhhVGhyZXNob2xkXxAUYWxwaGFUaHJlc2hvbGRBbW91bnRfEBFUaHJlc2hvbGQgZml4dHVyZRAECSM/2uFHrhR64QgRGiQpMjdJTFFTVlxlanKBmKyurwAAAAAAAAEBAAAAAAAAABUAAAAAAAAAAAAAAAAAAAC4"
        val SYNTHETIC_ARCHIVE =
            "YnBsaXN0MDDUAQIDBAUGBwpYJHZlcnNpb25ZJGFyY2hpdmVyVCR0b3BYJG9iamVjdHMSAAGGoF8QD05TS2V5ZWRBcmNoaXZlctEI" +
            "CVRyb290gAGtCwxOT1JVWFlaXF5hYlUkbnVsbN8QKg0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4" +
            "OTo7PD0+P0BBQTlCQzlERT5BO0ZHSElKOkRJS0lGPjo5Pz5JO0xNQVRuYW1lV3ZlcnNpb25ZcGFpbnRTaXplV21heFNpemVccGFp" +
            "bnRPcGFjaXR5Wm1heE9wYWNpdHlbcGxvdFNwYWNpbmddcGxvdFNtb290aGluZ15zaGFwZVJvdW5kbmVzc1pzaGFwZUFuZ2xlXHNo" +
            "YXBlQXppbXV0aFhvcmllbnRlZFxzaGFwZVNjYXR0ZXJaZ3JhaW5EZXB0aFx0ZXh0dXJlU2NhbGVbdGV4dHVyZVpvb21fEA90ZXh0" +
            "dXJlUm90YXRpb25fEBF0ZXh0dXJlQnJpZ2h0bmVzc18QD3RleHR1cmVDb250cmFzdF8QD3RleHR1cmVJbnZlcnRlZF8QFGR5bmFt" +
            "aWNzUHJlc3N1cmVTaXplV21pblNpemVfEBlkeW5hbWljc1ByZXNzdXJlU2l6ZUN1cnZlXxAXZHluYW1pY3NQcmVzc3VyZU9wYWNp" +
            "dHlabWluT3BhY2l0eV8QHGR5bmFtaWNzUHJlc3N1cmVPcGFjaXR5Q3VydmVfEBFkeW5hbWljc1NwZWVkU2l6ZV8QEmR5bmFtaWNz" +
            "Sml0dGVyU2l6ZV8QEGR5bmFtaWNzVGlsdFNpemVfEBJkeW5hbWljc0dsYXplZEZsb3ded2V0RWRnZXNBbW91bnRfEBBidXJudEVk" +
            "Z2VzQW1vdW50W2R5bmFtaWNzTWl4XxATZHluYW1pY3NQcmVzc3VyZU1peF8QF2R5bmFtaWNzV2V0QWNjdW11bGF0aW9uXxAaZHlu" +
            "YW1pY3NTbXVkZ2VBY2N1bXVsYXRpb25fEBB0YXBlclN0YXJ0TGVuZ3RoXnRhcGVyRW5kTGVuZ3RoWXRhcGVyU2l6ZVlibGVuZE1v" +
            "ZGVdZHVhbEJsZW5kTW9kZVtzaGFwZUZpbHRlcoACEAQjP+AAAAAAAAAjP9mZmZmZmZojP+mZmZmZmZojP+gAAAAAAAAjP5R64Ueu" +
            "FHsjP9MzMzMzMzMjP+MzMzMzMzMjP/kh+1RD1vQJIz/mZmZmZmZmI0AAAAAAAAAAIz/QAAAAAAAAI7/JmZmZmZmaIz+5mZmZmZma" +
            "gAMjv+AAAAAAAAAjP8mZmZmZmZqACCM/5MzMzMzMzRAAEAJfEBNTeW50aGV0aWMgUHJvY3JlYXRl0VBRVnBvaW50c4AE0VNUWk5T" +
            "Lm9iamVjdHOABaJWV4AGgAdfEBR7MC4wMDAwMDAsIDAuMTAwMDAwfV8QFHsxLjAwMDAwMCwgMC45MDAwMDB90VBbgAnRU12ACqJf" +
            "YIALgAxfEBR7MC4wMDAwMDAsIDAuMDAwMDAwfV8QFHsxLjAwMDAwMCwgMS4wMDAwMDB9AAgAEQAaACQAKQAyADcASQBMAFEAUwBh" +
            "AGcAvgDDAMsA1QDdAOoA9QEBAQ8BHgEpATYBPwFMAVcBZAFwAYIBlgGoAboB0QHZAfUCDwIaAjkCTQJiAnUCigKZAqwCuALOAugD" +
            "BQMYAycDMQM7A0kDVQNXA1kDYgNrA3QDfQOGA48DmAOhA6IDqwO0A70DxgPPA9ED2gPjA+UD7gPwA/IECAQLBBIEFAQXBCIEJAQn" +
            "BCkEKwRCBFkEXAReBGEEYwRmBGgEagSBAAAAAAAAAgEAAAAAAAAAYwAAAAAAAAAAAAAAAAAABJg="
    }
}
