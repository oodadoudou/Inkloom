package app.inkloom

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.SutBrushParser
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspAntiAlias
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class SutSprayRotationParserTest {
    @Test fun userProvidedSutCorpusReparsesWithVersionedNormalizedMaterialTips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val required = arguments.getString("requireSutSamples") == "true"
        val sampleRoots = listOf(
            File(context.filesDir, "sut-samples"),
            File(context.getExternalFilesDir(null), "sut-samples"),
            // adb test installs can clear app-owned storage. A world-readable staging directory
            // keeps the explicitly supplied corpus available across that reinstall boundary.
            File("/data/local/tmp/inkloom-sut-v21"),
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
                assertEquals("Failed to parse ${brush.name}: ${brush.report.joinToString()}", 32, brush.parserVersion)
                assertFalse(brush.report.any { it.startsWith("SUT parameter parsing failed") })
                brush.tipPreviewPaths.forEach { path -> assertTrue(path.contains("-sut-v32-")) }
            }
            assertTrue("Expected a real CSP Blend preset", parsed.any { it.cspColorMixMode == app.inkloom.document.CspColorMixMode.BLEND && it.watercolor })
            assertTrue("Expected a real CSP Running-color preset", parsed.any { it.cspColorMixMode == app.inkloom.document.CspColorMixMode.RUNNING_COLOR && it.watercolor })
            assertTrue("Expected a real CSP Smear preset", parsed.any { it.cspColorMixMode == app.inkloom.document.CspColorMixMode.SMEAR && it.watercolor })
            assertTrue("Expected multiple CSP AntiAlias modes in the supplied corpus", parsed.map(ImportedBrush::antiAliasMode).distinct().size >= 3)
            assertTrue(parsed.all { it.antiAliasMode in CspAntiAlias.NONE..CspAntiAlias.STRONG })
            assertTrue("CSP BrushBlur belongs to Running color and must not soften the tip shape", parsed.all { it.blur == 0f && !it.blurLinkedToSize })
            assertTrue("Expected the supplied Running-color blur preset", parsed.any { it.colorMixBlur > 0f })
            assertTrue("Expected the supplied Automatic Running-color blur preset", parsed.any { it.colorMixBlurAutomatic })
            assertTrue("Expected the supplied millimetre Running-color blur preset", parsed.any { it.colorMixBlurUnit == 2 })
            assertTrue("Expected embedded CSP material previews", parsed.any { it.tipPreviewPaths.isNotEmpty() })
            val paintingBrush = parsed.firstOrNull { it.name.contains("Painting brush", ignoreCase = true) }
            assertTrue("Expected the supplied CSP Dual Brush sample", paintingBrush?.dualBrush == true)
            assertEquals(7, paintingBrush?.dualCompositeMode)
            assertEquals(CspAntiAlias.MEDIUM, paintingBrush?.antiAliasMode)
            assertEquals(CspAntiAlias.MEDIUM, paintingBrush?.dualAntiAliasMode)
            assertFalse(paintingBrush?.dualApplyRgb ?: true)
            assertEquals(2, paintingBrush?.dualTipFlipHorizontalMode)
            assertEquals(CspTextureComposite.HEIGHT, paintingBrush?.grainCompositeMode)
            assertTrue("The supplied painting brush enables CSP TextureForPlot", paintingBrush?.grainApplyPerPlot == true)
            assertTrue(paintingBrush?.report?.any { it.contains("Screen composite mode") } == true)
            assertTrue(paintingBrush?.report?.any { it.contains("secondary brush-tip flip modes mapped") } == true)
            assertTrue("Main size pressure is enabled in the supplied dual brush", paintingBrush?.sizePressureEnabled == true)
            assertFalse("The supplied secondary size effector is disabled and must not inherit main pressure", paintingBrush?.dualSizeDynamics?.pressureEnabled ?: true)
            assertFalse("The supplied secondary flow effector is disabled and must not inherit main pressure", paintingBrush?.dualFlowDynamics?.pressureEnabled ?: true)
            assertTrue(paintingBrush?.report?.any { it.contains("main-tip dynamics are not inherited") } == true)
            assertTrue(paintingBrush?.report?.any { it.contains("Height Texture Mode mapped") } == true)
            assertEquals(7f, paintingBrush?.colorJitterHueDegrees ?: -1f, .001f)
            assertEquals(6f, paintingBrush?.colorJitterSaturation ?: -1f, .001f)
            assertEquals(0f, paintingBrush?.colorJitterValue ?: -1f, .001f)
            assertTrue(paintingBrush?.colorJitterHueDynamics?.randomEnabled == true)
            assertEquals(-1f, paintingBrush?.colorJitterHueDynamics?.randomMinimum ?: 0f, .001f)
            assertFalse("CSP stores stale 7/6 values while Change brush tip color is disabled", paintingBrush?.colorJitterPerTipEnabled ?: true)
            assertTrue(paintingBrush?.report?.any { it.contains("stale stored color values") } == true)
            assertTrue("Expected a supplied Compare-texture sample", parsed.any { it.grainCompositeMode == CspTextureComposite.COMPARE })
            val silkPen = parsed.firstOrNull { it.name.contains("丝丝笔") }
            assertTrue("Expected the supplied Darken-overlap pen", silkPen?.blendTipsWithDarken == true)
            assertTrue("Expected the supplied pen to enable Change brush tip color", silkPen?.colorJitterPerTipEnabled == true)
            assertTrue(silkPen?.report?.any { it.contains("isolated Darken composition") } == true)
            val continuousBrushes = parsed.filter(ImportedBrush::continuousPlot)
            assertTrue("Expected supplied CSP Continuous spraying brushes", continuousBrushes.size >= 2)
            assertTrue(continuousBrushes.all { brush -> brush.report.any { it.contains("Continuous spraying mapped") } })
            val automaticGapBrushes = parsed.filter { it.spacingMode != app.inkloom.document.CspBrushGap.FIXED }
            assertTrue("Expected supplied CSP automatic-gap brushes", automaticGapBrushes.size >= 6)
            assertTrue(automaticGapBrushes.any { it.spacingMode == app.inkloom.document.CspBrushGap.WIDE })
            assertTrue(automaticGapBrushes.any { it.spacingMode == app.inkloom.document.CspBrushGap.NORMAL })
            assertTrue(automaticGapBrushes.any { it.spacingMode == app.inkloom.document.CspBrushGap.NARROW })
            assertTrue(automaticGapBrushes.all { brush -> brush.report.any { it.contains("automatic brush-tip gap mapped") } })
            val particleDynamicsBrush = parsed.firstOrNull { it.sprayParticleSizePressureEnabled && it.sprayParticleSizeSecondaryDynamics.velocityEnabled }
            assertTrue("Expected the supplied pressure+velocity particle-size dynamics", particleDynamicsBrush != null)
            assertEquals(.32f, particleDynamicsBrush?.sprayParticleSizePressureMinimum ?: -1f, .001f)
            assertEquals(.37f, particleDynamicsBrush?.sprayParticleSizeSecondaryDynamics?.velocityMinimum ?: -1f, .001f)
            val densityDynamicsBrush = parsed.firstOrNull { it.sprayDensitySecondaryDynamics.velocityEnabled }
            assertTrue("Expected the supplied velocity particle-density dynamics", densityDynamicsBrush != null)
            assertTrue(densityDynamicsBrush?.report?.any { it.contains("spray density velocity") } == true)
            val wetMixBrush = parsed.firstOrNull { it.name.contains("커스텀 평평 수채붓") }
            assertTrue("Expected the supplied custom flat watercolor brush", wetMixBrush != null)
            assertTrue(wetMixBrush?.watercolor == true)
            assertEquals(.26f, wetMixBrush?.colorMix ?: -1f, .001f)
            assertEquals(.25f, wetMixBrush?.paintMix ?: -1f, .001f)
            assertTrue(wetMixBrush?.colorMixPressureEnabled == true)
            assertTrue(wetMixBrush?.paintMixPressureEnabled == true)
            assertEquals(2, wetMixBrush?.colorMixPressureCurve?.size)
            assertEquals(2, wetMixBrush?.paintMixPressureCurve?.size)
            assertTrue(wetMixBrush?.report?.any { it.contains("color mix 2-point") && it.contains("paint load 2-point") } == true)
            val legacyWetBrush = parsed.firstOrNull { it.name == "かすれベタ平筆" }
            assertTrue("Expected the supplied legacy-flag flat brush", legacyWetBrush != null)
            assertTrue("BrushWaterColor must enable wet rendering", legacyWetBrush?.watercolor == true)
            assertEquals(1f, legacyWetBrush?.colorMix ?: -1f, .001f)
            assertEquals(1f, legacyWetBrush?.paintMix ?: -1f, .001f)
            assertTrue(legacyWetBrush?.report?.any { it.contains("legacy BrushWaterColor") } == true)

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
            assertEquals(CspAntiAlias.WEAK, brush.antiAliasMode)
            assertEquals(CspAntiAlias.STRONG, brush.dualAntiAliasMode)
            assertTrue(brush.dualSizeDynamics.pressureEnabled)
            assertEquals(.25f, brush.dualSizeDynamics.pressureMinimum, .001f)
            assertTrue(brush.dualFlowDynamics.secondary.velocityEnabled)
            assertEquals(.35f, brush.dualFlowDynamics.secondary.velocityMinimum, .001f)
            assertTrue(brush.dualSpacingDynamics.secondary.tiltEnabled)
            assertEquals(.4f, brush.dualSpacingDynamics.secondary.tiltMinimum, .001f)
            assertTrue(brush.dualThicknessDynamics.secondary.randomEnabled)
            assertEquals(.45f, brush.dualThicknessDynamics.secondary.randomMinimum, .001f)
            assertTrue(brush.dualSprayParticleSizeDynamics.pressureEnabled)
            assertEquals(.3f, brush.dualSprayParticleSizeDynamics.pressureMinimum, .001f)
            assertTrue(brush.dualSprayDensityDynamics.secondary.velocityEnabled)
            assertEquals(.55f, brush.dualSprayDensityDynamics.secondary.velocityMinimum, .001f)
            assertTrue(brush.grainDensityDynamics.secondary.tiltEnabled)
            assertEquals(.65f, brush.grainDensityDynamics.secondary.tiltMinimum, .001f)
            assertEquals(32, brush.parserVersion)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun legacyBrushWaterColorFlagEnablesWetBrushRendering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sut-watercolor-alias-${System.nanoTime()}").apply { mkdirs() }
        try {
            val source = File(root, "legacy-watercolor.sut")
            SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
                db.execSQL("CREATE TABLE Node (NodeName TEXT NOT NULL, NodeVariantID INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE Variant (VariantID INTEGER PRIMARY KEY, Opacity REAL, BrushSize REAL, BrushWaterColor INTEGER, BrushMixColor REAL, BrushMixAlpha REAL)")
                db.insertOrThrow("Node", null, ContentValues().apply { put("NodeName", "Legacy watercolor"); put("NodeVariantID", 1) })
                db.insertOrThrow("Variant", null, ContentValues().apply {
                    put("VariantID", 1)
                    put("Opacity", 100)
                    put("BrushSize", 20)
                    put("BrushWaterColor", 1)
                    put("BrushMixColor", 72)
                    put("BrushMixAlpha", 43)
                })
            }
            val fallback = ImportedBrush(name = "Fallback", format = BrushFormat.SUT, compatibility = Compatibility.EXTRACTED, sizeBytes = source.length(), report = emptyList())

            val brush = SutBrushParser(File(root, "assets")).enrich(source, "legacy-watercolor", fallback)

            assertTrue(brush.watercolor)
            assertEquals(.72f, brush.colorMix, .001f)
            assertEquals(.43f, brush.paintMix, .001f)
            assertEquals(32, brush.parserVersion)
            assertTrue(brush.report.any { it.contains("legacy BrushWaterColor") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun sutColorMixingInfersBlendRunningColorAndSmearFromTheirPersistedPrograms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "sut-color-modes-${System.nanoTime()}").apply { mkdirs() }
        fun create(name: String, amount: Int, density: Int, stretch: Int, blur: Float, automatic: Boolean, perceptual: Boolean): ImportedBrush {
            val source = File(root, "$name.sut")
            SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
                db.execSQL("CREATE TABLE Node (NodeName TEXT NOT NULL, NodeVariantID INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE Variant (VariantID INTEGER PRIMARY KEY, Opacity REAL, BrushSize REAL, BrushUseWaterColor INTEGER, BrushUseWaterColor2 INTEGER, BrushWaterColor INTEGER, BrushMixColor REAL, BrushMixAlpha REAL, BrushMixColorExtension REAL, BrushBlur REAL, BrushBlurLinkSize INTEGER, BrushBlurUnit INTEGER, BrushColorMixingMode INTEGER, BrushLMSLinearity INTEGER)")
                db.insertOrThrow("Node", null, ContentValues().apply { put("NodeName", name); put("NodeVariantID", 1) })
                db.insertOrThrow("Variant", null, ContentValues().apply {
                    put("VariantID", 1); put("Opacity", 100); put("BrushSize", 20)
                    put("BrushUseWaterColor", 1); put("BrushUseWaterColor2", 1); put("BrushWaterColor", 0)
                    put("BrushMixColor", amount); put("BrushMixAlpha", density); put("BrushMixColorExtension", stretch)
                    put("BrushBlur", blur); put("BrushBlurLinkSize", if (automatic) 1 else 0); put("BrushBlurUnit", 0)
                    put("BrushColorMixingMode", if (perceptual) 1 else 0); put("BrushLMSLinearity", if (perceptual) 5 else 0)
                })
            }
            val fallback = ImportedBrush(name = name, format = BrushFormat.SUT, compatibility = Compatibility.EXTRACTED, sizeBytes = source.length(), report = emptyList())
            return SutBrushParser(File(root, "assets")).enrich(source, name, fallback)
        }
        try {
            val blend = create("Blend", 70, 50, 20, 0f, false, true)
            val running = create("Running", 20, 40, 30, 18f, true, false)
            val smear = create("Smear", 20, 0, 60, 0f, false, false)
            assertEquals(app.inkloom.document.CspColorMixMode.BLEND, blend.cspColorMixMode)
            assertEquals(app.inkloom.document.CspColorMixMode.RUNNING_COLOR, running.cspColorMixMode)
            assertEquals(app.inkloom.document.CspColorMixMode.SMEAR, smear.cspColorMixMode)
            assertEquals(.2f, blend.colorStretch, .001f)
            assertEquals(18f, running.colorMixBlur, .001f)
            assertTrue(running.colorMixBlurAutomatic)
            assertEquals(app.inkloom.document.CspPigmentMixingMode.PERCEPTUAL, blend.cspPigmentMixingMode)
            assertEquals(5, blend.cspBrightnessCorrection)
            assertTrue(blend.report.any { it.contains("Blend mode") })
            assertTrue(running.report.any { it.contains("Running color") })
            assertTrue(smear.report.any { it.contains("Smear mode") })
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
                streamLine = .31f,
                streamLinePressure = .22f,
                procreateStabilization = .43f,
                motionFiltering = .54f,
                motionFilteringExpression = .27f,
                tiltGradation = .62f,
                tiltGradationStart = .24f,
                tiltBleed = .47f,
                tiltBleedStart = .34f,
                tiltSizeCompression = true,
                strokeFalloff = .64f,
                strokePathJitter = .44f,
                strokePathLongitudinalJitter = .28f,
                dualStrokePathJitter = .2f,
                dualStrokePathLongitudinalJitter = .32f,
                shapeRoundnessHorizontalJitter = .38f,
                shapeRoundnessVerticalJitter = .49f,
                shapeTouchRotation = -.63f,
                shapeOrientToScreen = true,
                dualShapeRoundnessHorizontalJitter = .58f,
                dualShapeRoundnessVerticalJitter = .69f,
                dualShapeTouchRotation = .74f,
                dualShapeOrientToScreen = true,
                sprayParticleRotationDegrees = 33f,
                sprayParticleFollowStrokeDirection = true,
                sprayParticleRandomRotationDegrees = 54f,
                watercolor = true,
                colorMix = .6f,
                colorMixPressureEnabled = true,
                colorMixPressureMinimum = .2f,
                colorMixPressureCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, 0f), app.inkloom.document.BrushCurvePoint(1f, 1f)),
                paintMix = .7f,
                paintMixPressureEnabled = true,
                paintMixPressureMinimum = .3f,
                paintMixSecondaryDynamics = app.inkloom.document.BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .4f, velocityScale = 5f),
                cspColorMixMode = app.inkloom.document.CspColorMixMode.RUNNING_COLOR,
                colorStretch = .63f,
                cspPigmentMixingMode = app.inkloom.document.CspPigmentMixingMode.PERCEPTUAL,
                cspBrightnessCorrection = 5,
                colorMixBlur = 27f,
                colorMixBlurUnit = 2,
                colorMixBlurAutomatic = false,
                colorMixBlurDynamics = app.inkloom.document.BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .36f),
                sprayParticleSizePressureEnabled = true,
                sprayParticleSizePressureMinimum = .25f,
                sprayParticleSizePressureCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, .1f), app.inkloom.document.BrushCurvePoint(1f, 1f)),
                sprayParticleSizeSecondaryDynamics = app.inkloom.document.BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .35f),
                sprayDensityPressureEnabled = true,
                sprayDensityPressureMinimum = .4f,
                sprayDensitySecondaryDynamics = app.inkloom.document.BrushSecondaryDynamics(randomEnabled = true, randomMinimum = .6f),
                tipFlipHorizontalMode = 2,
                blendTipsWithDarken = true,
                continuousPlot = true,
                spacingMode = app.inkloom.document.CspBrushGap.NARROW,
                dualSpacingMode = app.inkloom.document.CspBrushGap.WIDE,
                colorJitterHueDegrees = 14f,
                colorJitterSaturation = 9f,
                colorJitterTarget = 1,
                colorJitterPerTipEnabled = false,
                colorJitterHueDynamics = app.inkloom.document.BrushColorDynamics(velocityEnabled = true, velocityScale = 3f, randomEnabled = true, randomMinimum = -1f),
                colorJitterSubColorMix = .42f,
                colorJitterSubColorMixDynamics = app.inkloom.document.BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .19f),
                colorJitterPerStrokeEnabled = true,
                colorJitterStrokeHueDegrees = 61f,
                colorJitterStrokeSaturation = 37f,
                colorJitterStrokeValue = 23f,
                colorJitterStrokeSubColorMix = .58f,
                procreateColorDynamics = app.inkloom.document.ProcreateColorDynamics(stampHue = .13f, strokeSecondary = .24f, pressureBrightness = -.35f, pressureSecondaryCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, .2f), app.inkloom.document.BrushCurvePoint(1f, .8f)), tiltSecondary = .46f, tiltStart = .27f),
                procreateWetMix = app.inkloom.document.ProcreateWetMix(dilution = .12f, charge = .23f, attack = .34f, pull = .45f, grade = -.56f, blur = .67f, blurJitter = .78f, wetnessJitter = .89f, legacyMixSoftening = .31f),
                procreateTaper = app.inkloom.document.ProcreateTaper(
                    pressureStartLength = .11f,
                    pressureEndLength = .22f,
                    pressureSize = .33f,
                    pressureOpacity = .44f,
                    pressureTip = .55f,
                    touchStartLength = .66f,
                    touchEndLength = .77f,
                    touchSize = .88f,
                    touchOpacity = .21f,
                    touchTip = .32f,
                    pressureResponse = .43f,
                    pressureLinked = true,
                    touchLinked = true,
                    classic = true,
                ),
                blur = 7.5f,
                blurUnit = 2,
                blurLinkedToSize = true,
                blurPressureEnabled = true,
                blurPressureMinimum = .2f,
                blurPressureCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, .2f), app.inkloom.document.BrushCurvePoint(1f, 1f)),
                blurSecondaryDynamics = app.inkloom.document.BrushSecondaryDynamics(tiltEnabled = true, tiltMinimum = .4f, tiltStart = .36f, tiltCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, 1f), app.inkloom.document.BrushCurvePoint(1f, 0f))),
                antiAliasMode = CspAntiAlias.NONE,
                grainCompositeMode = CspTextureComposite.HEIGHT,
                grainApplyPerPlot = false,
                grainFilteringMode = app.inkloom.document.ProcreateFiltering.IMPROVED,
                procreateRenderingMode = app.inkloom.document.ProcreateRenderingMode.HEAVY_GLAZE,
                procreateBrushBlendMode = app.inkloom.document.ProcreateBrushBlend.HARD_LIGHT,
                procreateBurntEdgeBlendMode = app.inkloom.document.ProcreateBrushBlend.COLOR_BURN,
                procreateLuminanceBlending = true,
                alphaThresholdEnabled = true,
                alphaThreshold = .37f,
                grainStrokeRotation = -.73f,
                grainDensityMinimum = .23f,
                grainDensityDynamics = app.inkloom.document.BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .57f),
                dualBrush = true,
                dualSizeDynamics = app.inkloom.document.BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .22f, pressureCurve = listOf(app.inkloom.document.BrushCurvePoint(0f, .2f), app.inkloom.document.BrushCurvePoint(1f, 1f))),
                dualFlowDynamics = app.inkloom.document.BrushParameterDynamics(secondary = app.inkloom.document.BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .31f)),
                dualSpacingDynamics = app.inkloom.document.BrushParameterDynamics(secondary = app.inkloom.document.BrushSecondaryDynamics(tiltEnabled = true, tiltMinimum = .41f)),
                dualThicknessDynamics = app.inkloom.document.BrushParameterDynamics(secondary = app.inkloom.document.BrushSecondaryDynamics(randomEnabled = true, randomMinimum = .51f)),
                dualCompositeMode = 7,
                dualApplyRgb = true,
                dualAntiAliasMode = CspAntiAlias.STRONG,
                dualTipFlipVerticalMode = 3,
                dualBlendTipsWithDarken = true,
                dualSpray = true,
                dualSprayParticleSizeDynamics = app.inkloom.document.BrushParameterDynamics(pressureEnabled = true, pressureMinimum = .61f),
                dualSprayDensityDynamics = app.inkloom.document.BrushParameterDynamics(secondary = app.inkloom.document.BrushSecondaryDynamics(velocityEnabled = true, velocityMinimum = .71f)),
                dualSprayParticleRotationDegrees = -12f,
                dualSprayParticleFollowStylusOrientation = true,
                dualSprayParticleRandomRotationDegrees = 81f,
            )
            val store = BrushMetadataStore(context)

            store.save(listOf(original))
            val restored = store.load().single()

            assertEquals(33f, restored.sprayParticleRotationDegrees, .0001f)
            assertEquals(.31f, restored.streamLine, .0001f)
            assertEquals(.22f, restored.streamLinePressure, .0001f)
            assertEquals(.43f, restored.procreateStabilization, .0001f)
            assertEquals(.54f, restored.motionFiltering, .0001f)
            assertEquals(.27f, restored.motionFilteringExpression, .0001f)
            assertEquals(.62f, restored.tiltGradation, .0001f)
            assertEquals(.24f, restored.tiltGradationStart, .0001f)
            assertEquals(.47f, restored.tiltBleed, .0001f)
            assertEquals(.34f, restored.tiltBleedStart, .0001f)
            assertTrue(restored.tiltSizeCompression)
            assertEquals(.64f, restored.strokeFalloff, .0001f)
            assertEquals(.44f, restored.strokePathJitter, .0001f)
            assertEquals(.28f, restored.strokePathLongitudinalJitter, .0001f)
            assertEquals(.2f, restored.dualStrokePathJitter, .0001f)
            assertEquals(.32f, restored.dualStrokePathLongitudinalJitter, .0001f)
            assertEquals(.38f, restored.shapeRoundnessHorizontalJitter, .0001f)
            assertEquals(.49f, restored.shapeRoundnessVerticalJitter, .0001f)
            assertEquals(-.63f, restored.shapeTouchRotation, .0001f)
            assertTrue(restored.shapeOrientToScreen)
            assertEquals(.74f, restored.dualShapeTouchRotation, .0001f)
            assertTrue(restored.dualShapeOrientToScreen)
            assertEquals(.58f, restored.dualShapeRoundnessHorizontalJitter, .0001f)
            assertEquals(.69f, restored.dualShapeRoundnessVerticalJitter, .0001f)
            assertTrue(restored.sprayParticleFollowStrokeDirection)
            assertEquals(54f, restored.sprayParticleRandomRotationDegrees, .0001f)
            assertTrue(restored.colorMixPressureEnabled)
            assertEquals(.2f, restored.colorMixPressureMinimum, .0001f)
            assertEquals(2, restored.colorMixPressureCurve.size)
            assertTrue(restored.paintMixPressureEnabled)
            assertEquals(.3f, restored.paintMixPressureMinimum, .0001f)
            assertTrue(restored.paintMixSecondaryDynamics.velocityEnabled)
            assertEquals(5f, restored.paintMixSecondaryDynamics.velocityScale, .0001f)
            assertEquals(app.inkloom.document.CspColorMixMode.RUNNING_COLOR, restored.cspColorMixMode)
            assertEquals(.63f, restored.colorStretch, .0001f)
            assertEquals(app.inkloom.document.CspPigmentMixingMode.PERCEPTUAL, restored.cspPigmentMixingMode)
            assertEquals(5, restored.cspBrightnessCorrection)
            assertEquals(27f, restored.colorMixBlur, .0001f)
            assertEquals(2, restored.colorMixBlurUnit)
            assertFalse(restored.colorMixBlurAutomatic)
            assertTrue(restored.colorMixBlurDynamics.pressureEnabled)
            assertEquals(.36f, restored.colorMixBlurDynamics.pressureMinimum, .0001f)
            assertTrue(restored.sprayParticleSizePressureEnabled)
            assertEquals(.25f, restored.sprayParticleSizePressureMinimum, .0001f)
            assertEquals(2, restored.sprayParticleSizePressureCurve.size)
            assertTrue(restored.sprayParticleSizeSecondaryDynamics.velocityEnabled)
            assertEquals(.35f, restored.sprayParticleSizeSecondaryDynamics.velocityMinimum, .0001f)
            assertTrue(restored.sprayDensityPressureEnabled)
            assertEquals(.4f, restored.sprayDensityPressureMinimum, .0001f)
            assertTrue(restored.sprayDensitySecondaryDynamics.randomEnabled)
            assertEquals(2, restored.tipFlipHorizontalMode)
            assertTrue(restored.blendTipsWithDarken)
            assertTrue(restored.continuousPlot)
            assertEquals(app.inkloom.document.CspBrushGap.NARROW, restored.spacingMode)
            assertEquals(app.inkloom.document.CspBrushGap.WIDE, restored.dualSpacingMode)
            assertEquals(14f, restored.colorJitterHueDegrees, .0001f)
            assertEquals(9f, restored.colorJitterSaturation, .0001f)
            assertEquals(1, restored.colorJitterTarget)
            assertFalse(restored.colorJitterPerTipEnabled)
            assertTrue(restored.colorJitterHueDynamics.randomEnabled)
            assertEquals(3f, restored.colorJitterHueDynamics.velocityScale, .0001f)
            assertEquals(-1f, restored.colorJitterHueDynamics.randomMinimum, .0001f)
            assertEquals(.42f, restored.colorJitterSubColorMix, .0001f)
            assertTrue(restored.colorJitterSubColorMixDynamics.pressureEnabled)
            assertEquals(.19f, restored.colorJitterSubColorMixDynamics.pressureMinimum, .0001f)
            assertTrue(restored.colorJitterPerStrokeEnabled)
            assertEquals(61f, restored.colorJitterStrokeHueDegrees, .0001f)
            assertEquals(37f, restored.colorJitterStrokeSaturation, .0001f)
            assertEquals(23f, restored.colorJitterStrokeValue, .0001f)
            assertEquals(.58f, restored.colorJitterStrokeSubColorMix, .0001f)
            assertEquals(original.procreateColorDynamics, restored.procreateColorDynamics)
            assertEquals(7.5f, restored.blur, .0001f)
            assertEquals(2, restored.blurUnit)
            assertTrue(restored.blurLinkedToSize)
            assertEquals(.36f, restored.blurSecondaryDynamics.tiltStart, .0001f)
            assertEquals(2, restored.blurSecondaryDynamics.tiltCurve.size)
            assertTrue(restored.blurPressureEnabled)
            assertEquals(.2f, restored.blurPressureMinimum, .0001f)
            assertEquals(2, restored.blurPressureCurve.size)
            assertTrue(restored.blurSecondaryDynamics.tiltEnabled)
            assertEquals(CspAntiAlias.NONE, restored.antiAliasMode)
            assertEquals(.4f, restored.blurSecondaryDynamics.tiltMinimum, .0001f)
            assertEquals(CspTextureComposite.HEIGHT, restored.grainCompositeMode)
            assertFalse(restored.grainApplyPerPlot)
            assertEquals(app.inkloom.document.ProcreateFiltering.IMPROVED, restored.grainFilteringMode)
            assertEquals(app.inkloom.document.ProcreateRenderingMode.HEAVY_GLAZE, restored.procreateRenderingMode)
            assertEquals(app.inkloom.document.ProcreateBrushBlend.HARD_LIGHT, restored.procreateBrushBlendMode)
            assertEquals(.12f, restored.procreateWetMix.dilution, .0001f)
            assertEquals(.23f, restored.procreateWetMix.charge, .0001f)
            assertEquals(.34f, restored.procreateWetMix.attack, .0001f)
            assertEquals(.45f, restored.procreateWetMix.pull, .0001f)
            assertEquals(-.56f, restored.procreateWetMix.grade, .0001f)
            assertEquals(.67f, restored.procreateWetMix.blur, .0001f)
            assertEquals(.78f, restored.procreateWetMix.blurJitter, .0001f)
            assertEquals(.89f, restored.procreateWetMix.wetnessJitter, .0001f)
            assertEquals(.31f, restored.procreateWetMix.legacyMixSoftening, .0001f)
            assertEquals(original.procreateTaper, restored.procreateTaper)
            assertEquals(app.inkloom.document.ProcreateBrushBlend.COLOR_BURN, restored.procreateBurntEdgeBlendMode)
            assertTrue(restored.procreateLuminanceBlending)
            assertTrue(restored.alphaThresholdEnabled)
            assertEquals(.37f, restored.alphaThreshold, .0001f)
            assertEquals(-.73f, restored.grainStrokeRotation, .0001f)
            assertEquals(.23f, restored.grainDensityMinimum, .0001f)
            assertTrue(restored.grainDensityDynamics.pressureEnabled)
            assertEquals(.57f, restored.grainDensityDynamics.pressureMinimum, .0001f)
            assertEquals(-12f, restored.dualSprayParticleRotationDegrees, .0001f)
            assertTrue(restored.dualSprayParticleFollowStylusOrientation)
            assertEquals(81f, restored.dualSprayParticleRandomRotationDegrees, .0001f)
            assertEquals(7, restored.dualCompositeMode)
            assertTrue(restored.dualApplyRgb)
            assertEquals(CspAntiAlias.STRONG, restored.dualAntiAliasMode)
            assertEquals(3, restored.dualTipFlipVerticalMode)
            assertTrue(restored.dualBlendTipsWithDarken)
            assertTrue(restored.dualSizeDynamics.pressureEnabled)
            assertEquals(.22f, restored.dualSizeDynamics.pressureMinimum, .0001f)
            assertEquals(2, restored.dualSizeDynamics.pressureCurve.size)
            assertTrue(restored.dualFlowDynamics.secondary.velocityEnabled)
            assertEquals(.31f, restored.dualFlowDynamics.secondary.velocityMinimum, .0001f)
            assertTrue(restored.dualSpacingDynamics.secondary.tiltEnabled)
            assertEquals(.41f, restored.dualSpacingDynamics.secondary.tiltMinimum, .0001f)
            assertTrue(restored.dualThicknessDynamics.secondary.randomEnabled)
            assertEquals(.51f, restored.dualThicknessDynamics.secondary.randomMinimum, .0001f)
            assertTrue(restored.dualSprayParticleSizeDynamics.pressureEnabled)
            assertEquals(.61f, restored.dualSprayParticleSizeDynamics.pressureMinimum, .0001f)
            assertTrue(restored.dualSprayDensityDynamics.secondary.velocityEnabled)
            assertEquals(.71f, restored.dualSprayDensityDynamics.secondary.velocityMinimum, .0001f)
        } finally {
            context.getSharedPreferences("inkloom_brushes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun createSyntheticSut(file: File) {
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.use { db ->
            db.execSQL("CREATE TABLE Node (NodeName TEXT NOT NULL, NodeVariantID INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE Variant (VariantID INTEGER PRIMARY KEY, Opacity REAL, BrushSize REAL, AntiAlias INTEGER, DualAntiAlias INTEGER, BrushContinuousPlot INTEGER, TextureCompositeMode INTEGER, TextureDensityEffector BLOB, BrushUseSpray INTEGER, BrushRotation REAL, BrushRotationEffector INTEGER, BrushRotationRandomScale REAL, BrushRotationInSpray REAL, BrushRotationEffectorInSpray INTEGER, BrushRotationRandomInSpray REAL, UseDualBrush INTEGER, DualSizeEffector BLOB, DualFlowEffector BLOB, DualIntervalEffector BLOB, DualThicknessEffector BLOB, DualRotation REAL, DualRotationEffector INTEGER, DualRotationRandomScale REAL, DualUseSpray INTEGER, DualSpraySizeEffector BLOB, DualSprayDensityEffector BLOB, DualRotationInSpray REAL, DualRotationEffectorInSpray INTEGER, DualRotationRandomInSpray REAL)")
            db.insertOrThrow("Node", null, ContentValues().apply { put("NodeName", "Particle test"); put("NodeVariantID", 1) })
            db.insertOrThrow("Variant", null, ContentValues().apply {
                put("VariantID", 1)
                put("Opacity", 100)
                put("BrushSize", 20)
                put("AntiAlias", CspAntiAlias.WEAK)
                put("DualAntiAlias", CspAntiAlias.STRONG)
                put("BrushContinuousPlot", 1)
                put("TextureCompositeMode", CspTextureComposite.HEIGHT)
                put("TextureDensityEffector", effector(0x20, tiltMinimum = 65))
                put("BrushUseSpray", 1)
                put("BrushRotation", 5)
                put("BrushRotationEffector", 3)
                put("BrushRotationRandomScale", 100)
                put("BrushRotationInSpray", 17)
                put("BrushRotationEffectorInSpray", 0x83)
                put("BrushRotationRandomInSpray", 40)
                put("UseDualBrush", 1)
                put("DualSizeEffector", effector(0x10, pressureMinimum = 25))
                put("DualFlowEffector", effector(0x40, velocityMinimum = 35))
                put("DualIntervalEffector", effector(0x20, tiltMinimum = 40))
                put("DualThicknessEffector", effector(0x80, randomMinimum = 45))
                put("DualRotation", 8)
                put("DualRotationEffector", 3)
                put("DualRotationRandomScale", 100)
                put("DualUseSpray", 1)
                put("DualSpraySizeEffector", effector(0x10, pressureMinimum = 30))
                put("DualSprayDensityEffector", effector(0x40, velocityMinimum = 55))
                put("DualRotationInSpray", -25)
                put("DualRotationEffectorInSpray", 0x81)
                put("DualRotationRandomInSpray", 50)
            })
        }
    }

    private fun effector(sourceFlags: Int, pressureMinimum: Int = 0, tiltMinimum: Int = 0, velocityMinimum: Int = 0, randomMinimum: Int = 0): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN)
            .putInt(44).putInt(240).putInt(sourceFlags)
            .putInt(pressureMinimum).putInt(tiltMinimum).putInt(velocityMinimum).putInt(randomMinimum)
            .putInt(0).putInt(0).putInt(0).putInt(500)
            .array()
}
