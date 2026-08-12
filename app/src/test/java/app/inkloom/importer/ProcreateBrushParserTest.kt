package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.ProcreateFiltering
import app.inkloom.document.ProcreateRenderingMode
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
            assertEquals(90f, brush.rotationDegrees, .001f)
            assertTrue(brush.followStylusOrientation)
            assertTrue(brush.followStrokeDirection)
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
            assertTrue(File(brush.tipPreviewPath!!).isFile)
            assertTrue(File(brush.grainPreviewPath!!).isFile)
            assertEquals(sourceMetadata.float("textureScale", 1f) * 100f, brush.grainScale, .0001f)
            assertEquals(sourceMetadata.float("textureMovement", 1f), brush.grainMovement, .0001f)
            assertEquals(sourceMetadata.float("textureZoom", 1f), brush.grainZoom, .0001f)
            assertEquals(sourceMetadata.float("dynamicsJitterHue"), brush.procreateColorDynamics.stampHue, .0001f)
            assertEquals(sourceMetadata.float("dynamicsJitterStrokeHue"), brush.procreateColorDynamics.strokeHue, .0001f)
            assertEquals(sourceMetadata.float("dynamicsPressureSecondaryColor"), brush.procreateColorDynamics.pressureSecondary, .0001f)
            assertEquals(sourceMetadata.float("dynamicsTiltSecondaryColor"), brush.procreateColorDynamics.tiltSecondary, .0001f)
            assertEquals(CspAntiAlias.MEDIUM, brush.antiAliasMode)
            assertEquals(ProcreateFiltering.CLASSIC, brush.grainFilteringMode)
            assertEquals(ProcreateRenderingMode.INTENSE_GLAZE, brush.procreateRenderingMode)
            assertFalse(brush.alphaThresholdEnabled)
            assertEquals(0f, brush.alphaThreshold, .0001f)
            assertEquals(ProcreateBrushParser.CURRENT_VERSION, brush.parserVersion)
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
            assertFalse(File(root, "Shape.png").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun parsesAuthorizedRealBrushsetWhenConfigured() {
        val sampleRoot = System.getProperty("procreate.samples.dir")?.let(::File)
        assumeTrue(sampleRoot != null && sampleRoot.isDirectory)
        val source = sampleRoot!!.walkTopDown().firstOrNull { it.isFile && it.extension.equals("brushset", true) }
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
            assertTrue(brushes.any { it.sizePressureEnabled })
            assertTrue(brushes.any { it.opacityPressureEnabled })
            assertTrue(brushes.any { it.watercolor })
            assertTrue(brushes.any { it.taperStartEnabled })
            assertTrue(brushes.any { it.taperEndEnabled })
            assertTrue(brushes.any { brush -> brush.report.any { it.contains("Apple Pencil start/end taper") } })
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
            assertTrue(brushes.any { it.procreateRenderingMode == ProcreateRenderingMode.INTENSE_GLAZE })
            assertTrue(brushes.any { it.procreateRenderingMode == ProcreateRenderingMode.UNIFORM_BLENDING })
            assertTrue(brushes.any { it.procreateRenderingMode == ProcreateRenderingMode.INTENSE_BLENDING })
            assertTrue(brushes.any { brush -> brush.report.any { it.contains("stroke-path jitter") } })
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

    private fun writeBrush(target: File, archive: String = SYNTHETIC_ARCHIVE) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Brush.archive")); zip.write(Base64.getDecoder().decode(archive)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("Shape.png")); zip.write(SYNTHETIC_PNG); zip.closeEntry()
            zip.putNextEntry(ZipEntry("Grain.png")); zip.write(SYNTHETIC_PNG); zip.closeEntry()
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
