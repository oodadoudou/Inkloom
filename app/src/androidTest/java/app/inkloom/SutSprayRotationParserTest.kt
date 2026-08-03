package app.inkloom

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.SutBrushParser
import app.inkloom.document.CspTextureComposite
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SutSprayRotationParserTest {
    @Test fun userProvidedSutCorpusReparsesWithVersionedNormalizedMaterialTips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val required = arguments.getString("requireSutSamples") == "true"
        val sampleRoots = listOf(
            File(context.filesDir, "sut-samples"),
            File(context.getExternalFilesDir(null), "sut-samples"),
        )
        val samples = sampleRoots.asSequence()
            .flatMap { root -> root.walkTopDown().asSequence() }
            .filter { it.isFile && it.extension.equals("sut", true) }
            .distinctBy(File::getName)
            .toList()
        if (samples.isEmpty() && !required) return
        assertTrue("Expected the pushed user SUT corpus; found ${samples.size} under ${sampleRoots.joinToString()}", samples.size >= 10)
        val assetRoot = File(context.cacheDir, "sut-real-assets-${System.nanoTime()}").apply { mkdirs() }
        try {
            val parser = SutBrushParser(assetRoot)
            val parsed = samples.map { source ->
                parser.enrich(
                    source,
                    source.name.hashCode().toUInt().toString(16),
                    ImportedBrush(
                        name = source.nameWithoutExtension,
                        format = BrushFormat.SUT,
                        compatibility = Compatibility.EXTRACTED,
                        sizeBytes = source.length(),
                        report = emptyList(),
                    ),
                )
            }
            parsed.forEach { brush ->
                assertEquals("Failed to parse ${brush.name}: ${brush.report.joinToString()}", 20, brush.parserVersion)
                assertFalse(brush.report.any { it.startsWith("SUT parameter parsing failed") })
                brush.tipPreviewPaths.forEach { path -> assertTrue(path.contains("-sut-v20-")) }
            }
            assertTrue("Expected every supplied SUT brush to preserve CSP BrushBlur", parsed.all { it.blur > 0f })
            assertTrue("Expected the supplied size-linked BrushBlur preset", parsed.any { it.blurLinkedToSize })
            assertTrue("Expected the supplied millimetre BrushBlur preset", parsed.any { it.blurUnit == 2 })
            assertTrue("Expected parsed BrushBlur diagnostics", parsed.all { brush -> brush.report.any { it.contains("brush blur", ignoreCase = true) } })
            assertTrue("Expected embedded CSP material previews", parsed.any { it.tipPreviewPaths.isNotEmpty() })
            val paintingBrush = parsed.firstOrNull { it.name.contains("Painting brush", ignoreCase = true) }
            assertTrue("Expected the supplied CSP Dual Brush sample", paintingBrush?.dualBrush == true)
            assertEquals(7, paintingBrush?.dualCompositeMode)
            assertFalse(paintingBrush?.dualApplyRgb ?: true)
            assertEquals(2, paintingBrush?.dualTipFlipHorizontalMode)
            assertEquals(CspTextureComposite.HEIGHT, paintingBrush?.grainCompositeMode)
            assertTrue(paintingBrush?.report?.any { it.contains("Screen composite mode") } == true)
            assertTrue(paintingBrush?.report?.any { it.contains("secondary brush-tip flip modes mapped") } == true)
            assertTrue(paintingBrush?.report?.any { it.contains("Height texture composition mapped with documented edge-only behavior") } == true)
            assertTrue("Expected a supplied Compare-texture sample", parsed.any { it.grainCompositeMode == CspTextureComposite.COMPARE })
            assertTrue("Compare must not be reported as exact", parsed.any { brush -> brush.report.any { it.contains("Compare texture mode retained with conservative density comparison") } })
            val silkPen = parsed.firstOrNull { it.name.contains("丝丝笔") }
            assertTrue("Expected the supplied Darken-overlap pen", silkPen?.blendTipsWithDarken == true)
            assertTrue(silkPen?.report?.any { it.contains("isolated Darken composition") } == true)
            val continuousBrushes = parsed.filter(ImportedBrush::continuousPlot)
            assertTrue("Expected supplied CSP Continuous spraying brushes", continuousBrushes.size >= 2)
            assertTrue(continuousBrushes.all { brush -> brush.report.any { it.contains("Continuous spraying mapped") } })

            val normalizedTipSizes = parsed.flatMap { brush ->
                brush.tipPreviewPaths.mapNotNull { path ->
                    BitmapFactory.decodeFile(path)?.let { bitmap ->
                        (bitmap.width to bitmap.height).also { bitmap.recycle() }
                    }
                }
            }
            assertTrue(
                "Expected the normalized 252 x 150 flat-watercolor material preview",
                (252 to 150) in normalizedTipSizes,
            )
        } finally {
            assetRoot.deleteRecursively()
        }
    }

    @Test fun sutRandomAnglesRequireTheirFlagsAndSprayUsesIndependentDirection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sut-spray-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "synthetic.sut")
            createSyntheticSut(source)
            val fallback = ImportedBrush(name = "Fallback", format = BrushFormat.SUT, compatibility = Compatibility.EXTRACTED, sizeBytes = source.length(), report = emptyList())

            val brush = SutBrushParser(File(root, "assets")).enrich(source, "sample", fallback)

            assertEquals("Particle test", brush.name)
            assertTrue(brush.continuousPlot)
            assertEquals(0f, brush.randomRotationDegrees, .0001f)
            assertEquals(17f, brush.sprayParticleRotationDegrees, .0001f)
            assertFalse(brush.sprayParticleFollowStylusOrientation)
            assertTrue(brush.sprayParticleFollowStrokeDirection)
            assertEquals(72f, brush.sprayParticleRandomRotationDegrees, .0001f)
            assertEquals(0f, brush.dualRandomRotationDegrees, .0001f)
            assertEquals(-25f, brush.dualSprayParticleRotationDegrees, .0001f)
            assertTrue(brush.dualSprayParticleFollowStylusOrientation)
            assertFalse(brush.dualSprayParticleFollowStrokeDirection)
            assertEquals(90f, brush.dualSprayParticleRandomRotationDegrees, .0001f)
            assertEquals(CspTextureComposite.HEIGHT, brush.grainCompositeMode)
            assertEquals(20, brush.parserVersion)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun importedBrushStoreRoundTripsParticleDirectionSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("inkloom_brushes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val original = ImportedBrush(
                id = "particle-brush",
                name = "Particles",
                format = BrushFormat.SUT,
                compatibility = Compatibility.HIGH,
                sizeBytes = 42,
                report = emptyList(),
                spray = true,
                sprayParticleRotationDegrees = 33f,
                sprayParticleFollowStrokeDirection = true,
                sprayParticleRandomRotationDegrees = 54f,
                tipFlipHorizontalMode = 2,
                blendTipsWithDarken = true,
                continuousPlot = true,
                blur = 7.5f,
                blurUnit = 2,
                blurLinkedToSize = true,
                blurPressureEnabled = true,
                blurPressureMinimum = .2f,
                blurPressureCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, .2f), app.inkloom.document.BrushCurvePoint(1f, 1f)),
                blurSecondaryDynamics = app.inkloom.document.BrushSecondaryDynamics(tiltEnabled = true, tiltMinimum = .4f),
                grainCompositeMode = CspTextureComposite.HEIGHT,
                dualBrush = true,
                dualCompositeMode = 7,
                dualApplyRgb = true,
                dualTipFlipVerticalMode = 3,
                dualBlendTipsWithDarken = true,
                dualSpray = true,
                dualSprayParticleRotationDegrees = -12f,
                dualSprayParticleFollowStylusOrientation = true,
                dualSprayParticleRandomRotationDegrees = 81f,
            )
            val store = BrushMetadataStore(context)

            store.save(listOf(original))
            val restored = store.load().single()

            assertEquals(33f, restored.sprayParticleRotationDegrees, .0001f)
            assertTrue(restored.sprayParticleFollowStrokeDirection)
            assertEquals(54f, restored.sprayParticleRandomRotationDegrees, .0001f)
            assertEquals(2, restored.tipFlipHorizontalMode)
            assertTrue(restored.blendTipsWithDarken)
            assertTrue(restored.continuousPlot)
            assertEquals(7.5f, restored.blur, .0001f)
            assertEquals(2, restored.blurUnit)
            assertTrue(restored.blurLinkedToSize)
            assertTrue(restored.blurPressureEnabled)
            assertEquals(.2f, restored.blurPressureMinimum, .0001f)
            assertEquals(2, restored.blurPressureCurve.size)
            assertTrue(restored.blurSecondaryDynamics.tiltEnabled)
            assertEquals(.4f, restored.blurSecondaryDynamics.tiltMinimum, .0001f)
            assertEquals(CspTextureComposite.HEIGHT, restored.grainCompositeMode)
            assertEquals(-12f, restored.dualSprayParticleRotationDegrees, .0001f)
            assertTrue(restored.dualSprayParticleFollowStylusOrientation)
            assertEquals(81f, restored.dualSprayParticleRandomRotationDegrees, .0001f)
            assertEquals(7, restored.dualCompositeMode)
            assertTrue(restored.dualApplyRgb)
            assertEquals(3, restored.dualTipFlipVerticalMode)
            assertTrue(restored.dualBlendTipsWithDarken)
        } finally {
            context.getSharedPreferences("inkloom_brushes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun createSyntheticSut(file: File) {
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.use { db ->
            db.execSQL("CREATE TABLE Node (NodeName TEXT NOT NULL, NodeVariantID INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE Variant (VariantID INTEGER PRIMARY KEY, Opacity REAL, BrushSize REAL, BrushContinuousPlot INTEGER, TextureCompositeMode INTEGER, BrushUseSpray INTEGER, BrushRotation REAL, BrushRotationEffector INTEGER, BrushRotationRandomScale REAL, BrushRotationInSpray REAL, BrushRotationEffectorInSpray INTEGER, BrushRotationRandomInSpray REAL, UseDualBrush INTEGER, DualRotation REAL, DualRotationEffector INTEGER, DualRotationRandomScale REAL, DualUseSpray INTEGER, DualRotationInSpray REAL, DualRotationEffectorInSpray INTEGER, DualRotationRandomInSpray REAL)")
            db.insertOrThrow("Node", null, ContentValues().apply { put("NodeName", "Particle test"); put("NodeVariantID", 1) })
            db.insertOrThrow("Variant", null, ContentValues().apply {
                put("VariantID", 1)
                put("Opacity", 100)
                put("BrushSize", 20)
                put("BrushContinuousPlot", 1)
                put("TextureCompositeMode", CspTextureComposite.HEIGHT)
                put("BrushUseSpray", 1)
                put("BrushRotation", 5)
                put("BrushRotationEffector", 3)
                put("BrushRotationRandomScale", 100)
                put("BrushRotationInSpray", 17)
                put("BrushRotationEffectorInSpray", 0x83)
                put("BrushRotationRandomInSpray", 40)
                put("UseDualBrush", 1)
                put("DualRotation", 8)
                put("DualRotationEffector", 3)
                put("DualRotationRandomScale", 100)
                put("DualUseSpray", 1)
                put("DualRotationInSpray", -25)
                put("DualRotationEffectorInSpray", 0x81)
                put("DualRotationRandomInSpray", 50)
            })
        }
    }
}
