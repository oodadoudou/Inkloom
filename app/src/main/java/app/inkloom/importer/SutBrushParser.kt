package app.inkloom.importer

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspAntiAlias
import app.inkloom.document.CspBrushGap
import app.inkloom.document.CspColorMixMode
import app.inkloom.document.CspPigmentMixingMode
import app.inkloom.document.CspBrightnessCorrection
import java.io.File
import java.io.FileInputStream
import java.io.ByteArrayOutputStream

class SutBrushParser(private val assetDirectory: File) {
    init { assetDirectory.mkdirs() }

    fun enrich(source: File, hash: String, fallback: ImportedBrush): ImportedBrush = runCatching {
        val database = SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        database.use { db ->
            val columns = db.rawQuery("PRAGMA table_info(Variant)", null).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) } }
            val desired = listOf("Opacity", "BrushSize", "BrushSizeUnit", "FlickerReduction", "FlickerReductionBySpeed", "FlickerReductionBySpeedType", "BrushUseRevision", "BrushRevision", "BrushRevisionBySpeed", "BrushRevisionByViewScale", "BrushRevisionBezier", "BrushSharpenCorner", "BrushFlow", "BrushHardness", "BrushBlurLinkSize", "BrushBlur", "BrushBlurUnit", "BrushBlurEffector", "BrushInterval", "BrushAutoIntervalType", "BrushContinuousPlot", "BrushThickness", "BrushVerticalThicknes", "BrushRotation", "BrushRotationEffector", "BrushRotationRandomScale", "BrushPatternOrderType", "BrushPatternReverseHorizontal", "BrushPatternReverseVertical", "BrushBlendPatternByDarken", "BrushRibbon", "BrushChangeColorTarget", "BrushChangePatternColor", "BrushHueChange", "BrushSaturationChange", "BrushValueChange", "BrushSubColor", "BrushChangeStrokeColor", "BrushStrokeHueChange", "BrushStrokeSaturationChange", "BrushStrokeValueChange", "BrushStrokeSubColor", "TextureDensity", "TextureCompositeMode", "TextureForPlot", "TextureReverseDensity", "TextureStressDensity", "TextureScale2", "TextureRotate", "TextureBrightness", "TextureContrast", "BrushUseWaterColor", "BrushUseWaterColor2", "BrushWaterColor", "BrushColorMixingMode", "BrushLMSLinearity", "BrushMixColor", "BrushMixAlpha", "BrushMixColorExtension", "BrushUseSpray", "BrushSpraySize", "BrushSpraySizeUnit", "BrushSpraySizeSyncBrushSize", "BrushSprayDensity", "BrushSprayBias", "BrushRotationInSpray", "BrushRotationEffectorInSpray", "BrushRotationRandomInSpray", "UseDualBrush", "DualBrushCompositeMode", "ChangeRGBByDual", "SyncDualBrushSize", "DualSize", "DualFlow", "DualHardness", "DualInterval", "DualAutoIntervalType", "DualThickness", "DualVerticalThicknes", "DualRotation", "DualRotationEffector", "DualRotationRandomScale", "DualPatternReverseHorizontal", "DualPatternReverseVertical", "DualBlendPatternByDarken", "DualRibbon", "DualUseSpray", "DualSpraySize", "DualSpraySizeUnit", "DualSpraySizeSyncBrushSize", "DualSprayDensity", "DualSprayBias", "DualRotationInSpray", "DualRotationEffectorInSpray", "DualRotationRandomInSpray", "DualPatternOrderType", "BrushInOutType", "BrushUseIn", "BrushInLength", "BrushInLengthUnit", "BrushInRatio", "BrushUseOut", "BrushOutLength", "BrushOutLengthUnit", "BrushOutRatio", "BrushUseWaterEdge", "BrushWaterEdgeRadius", "BrushWaterEdgeRadiusUnit", "BrushWaterEdgeAlphaPower", "BrushWaterEdgeValuePower", "BrushWaterEdgeBlur", "BrushWaterEdgeBlurUnit").filter(columns::contains)
            val numericDesired = (desired + listOf("BrushSizeSyncViewScale", "AntiAlias", "DualAntiAlias").filter(columns::contains)).filterNot { it == "BrushBlurEffector" }
            val values = db.rawQuery("SELECT n.NodeName, ${numericDesired.joinToString { "v.$it" }} FROM Node n JOIN Variant v ON v.VariantID=n.NodeVariantID LIMIT 1", null).use { cursor ->
                check(cursor.moveToFirst()) { "SUT has no active brush variant" }
                numericDesired.associateWith { name -> cursor.number(name) } + ("NodeName" to cursor.getString(cursor.getColumnIndexOrThrow("NodeName")))
            }
            val sizeEffector = db.effector("BrushSizeEffector", columns)
            val opacityEffector = db.effector("BrushOpacityEffector", columns)
            val flowEffector = db.effector("BrushFlowEffector", columns)
            val spacingEffector = db.effector("BrushIntervalEffector", columns)
            val thicknessEffector = db.effector("BrushThicknessEffector", columns)
            val blurEffector = db.effector("BrushBlurEffector", columns)
            val colorMixEffector = db.effector("BrushMixColorEffector", columns)
            val paintMixEffector = db.effector("BrushMixAlphaEffector", columns)
            val sprayParticleSizeEffector = db.effector("BrushSpraySizeEffector", columns)
            val sprayDensityEffector = db.effector("BrushSprayDensityEffector", columns)
            val textureDensityEffector = db.effector("TextureDensityEffector", columns)
            val hueEffector = db.effector("BrushHueChangeEffector", columns)
            val saturationEffector = db.effector("BrushSaturationChangeEffector", columns)
            val valueEffector = db.effector("BrushValueChangeEffector", columns)
            val subColorEffector = db.effector("BrushSubColorEffector", columns)
            val dualSizeEffector = db.effector("DualSizeEffector", columns)
            val dualFlowEffector = db.effector("DualFlowEffector", columns)
            val dualSpacingEffector = db.effector("DualIntervalEffector", columns)
            val dualThicknessEffector = db.effector("DualThicknessEffector", columns)
            val dualSprayParticleSizeEffector = db.effector("DualSpraySizeEffector", columns)
            val dualSprayDensityEffector = db.effector("DualSprayDensityEffector", columns)
            val tips = extractMaterialPreviews(db, "BrushPatternImageArray", hash, "tip")
            val dualTips = extractMaterialPreviews(db, "DualPatternImageArray", hash, "dual-tip")
            val tip = tips.firstOrNull()
            val grain = extractMaterialPreviews(db, "TextureImage", hash, "grain").firstOrNull()
            val colorMixAmount = (values.float("BrushMixColor") / 100f).coerceIn(0f, 1f)
            val paintDensity = (values.float("BrushMixAlpha") / 100f).coerceIn(0f, 1f)
            val stretch = (values.float("BrushMixColorExtension") / 100f).coerceIn(0f, 1f)
            val runningBlur = values.float("BrushBlur").coerceIn(0f, 400f)
            val runningBlurAutomatic = values.float("BrushBlurLinkSize") > 0f
            // BrushUseWaterColor2 is a second-generation enable bit, not a mode selector.
            val colorMixEnabled = values.float("BrushUseWaterColor") > 0f ||
                values.float("BrushUseWaterColor2") > 0f || values.float("BrushWaterColor") > 0f
            val colorMixMode = CspColorMixMode.inferFromSut(
                colorMixEnabled, colorMixAmount, paintDensity, stretch, runningBlur, runningBlurAutomatic,
            )
            val watercolor = colorMixMode != null
            val dual = values.float("UseDualBrush") > 0f
            val perTipColorEnabled = if ("BrushChangePatternColor" in columns) {
                values.float("BrushChangePatternColor") > 0f
            } else {
                values.float("BrushHueChange") != 0f || values.float("BrushSaturationChange") != 0f ||
                    values.float("BrushValueChange") != 0f || values.float("BrushSubColor") != 0f
            }
            val perStrokeColorEnabled = values.float("BrushChangeStrokeColor") > 0f
            val rotationFlags = values.float("BrushRotationEffector").toInt()
            val rotation = SutRotationDecoder.decode(rotationFlags)
            val dualRotation = SutRotationDecoder.decode(values.float("DualRotationEffector").toInt())
            val sprayRotation = SutRotationDecoder.decode(values.float("BrushRotationEffectorInSpray").toInt())
            val dualSprayRotation = SutRotationDecoder.decode(values.float("DualRotationEffectorInSpray").toInt())
            val taperTargets = SutStartingEndingTargetDecoder.decode(db.blob("BrushInOutTarget", columns))
            val sizeTaperTarget = taperTargets.firstOrNull { it.parameterId == SutStartingEndingTargetDecoder.BRUSH_SIZE_PARAMETER_ID }
            val taper = SutTaperDecoder.decode(values.float("BrushInOutType").toInt(), values.float("BrushUseIn") > 0f, values.float("BrushInLength", 20f), values.float("BrushInRatio", 30f), values.float("BrushInLengthUnit").toInt(), values.float("BrushUseOut") > 0f, values.float("BrushOutLength", 20f), values.float("BrushOutRatio", 30f), values.float("BrushOutLengthUnit").toInt(), sizeTaperTarget?.enabled == true, sizeTaperTarget?.minimumPercent ?: 0f)
            fallback.copy(
                name = values["NodeName"] as? String ?: fallback.name,
                compatibility = if (watercolor || dual || tips.isNotEmpty()) Compatibility.BASIC else Compatibility.HIGH,
                tipPreviewPath = tip?.absolutePath,
                tipPreviewPaths = tips.map(File::getAbsolutePath),
                dualTipPreviewPaths = dualTips.map(File::getAbsolutePath),
                grainPreviewPath = grain?.absolutePath,
                grainDensity = (values.float("TextureDensity") / 100f).coerceIn(0f, 1f),
                grainDensityDynamics = textureDensityEffector.parameterDynamics(),
                grainScale = values.float("TextureScale2", 100f).coerceIn(1f, 4000f),
                grainRotationDegrees = values.float("TextureRotate").coerceIn(-360f, 360f),
                grainBrightness = values.float("TextureBrightness").coerceIn(-100f, 100f),
                grainContrast = values.float("TextureContrast").coerceIn(-100f, 100f),
                grainReversed = values.float("TextureReverseDensity") > 0f,
                grainEmphasized = values.float("TextureStressDensity") > 0f,
                grainCompositeMode = values.float("TextureCompositeMode", CspTextureComposite.MULTIPLY.toFloat()).toInt().coerceIn(0, 64),
                grainApplyPerPlot = values.float("TextureForPlot", 1f) > 0f,
                defaultSize = SutUnitConverter.referencePixels(values.float("BrushSize", 8f), values.float("BrushSizeUnit").toInt()),
                sizeSyncViewScale = values.float("BrushSizeSyncViewScale") > 0f,
                stabilization = (values.float("FlickerReduction") / 100f).coerceIn(0f, .9f),
                stabilizationBySpeed = values.float("FlickerReductionBySpeed") > 0f && values.float("FlickerReduction") in .01f..<30f,
                stabilizationBySpeedMode = values.float("FlickerReductionBySpeedType", 1f).toInt().coerceIn(0, 1),
                postCorrectionEnabled = values.float("BrushUseRevision") > 0f,
                postCorrection = (values.float("BrushRevision") / 100f).coerceIn(0f, 1f),
                postCorrectionAdjustBySpeed = values.float("BrushRevisionBySpeed") > 0f,
                postCorrectionAdjustByScale = values.float("BrushRevisionByViewScale", 1f) > 0f,
                postCorrectionBezier = values.float("BrushRevisionBezier") > 0f,
                correctionSharpAngles = values.float("BrushSharpenCorner") > 0f,
                defaultOpacity = (values.float("Opacity", 100f) / 100f).coerceIn(.01f, 1f),
                spacing = (values.float("BrushInterval", 12f) / 100f).coerceIn(.01f, 2f),
                spacingMode = CspBrushGap.normalize(values.float("BrushAutoIntervalType").toInt()),
                flow = (values.float("BrushFlow", 100f) / 100f).coerceIn(0f, 1f),
                hardness = (values.float("BrushHardness", 100f) / 100f).coerceIn(0f, 1f),
                antiAliasMode = CspAntiAlias.normalize(values.float("AntiAlias", CspAntiAlias.MEDIUM.toFloat()).toInt()),
                // BrushBlur is CSP Running color's blur radius, not a tip-shape blur.
                blur = 0f,
                blurUnit = 0,
                blurLinkedToSize = false,
                thickness = (values.float("BrushThickness", 100f) / 100f).coerceIn(.05f, 2f),
                thicknessVertical = values.float("BrushVerticalThicknes").toInt() != 0,
                rotationDegrees = values.float("BrushRotation"),
                followStylusOrientation = rotation.mode == SutRotationMode.DIRECTION_OF_PEN,
                followStrokeDirection = rotation.mode == SutRotationMode.DIRECTION_OF_LINE,
                randomRotationDegrees = if (rotation.randomEnabled) (values.float("BrushRotationRandomScale") / 100f * 180f).coerceIn(0f, 180f) else 0f,
                continuousPlot = values.float("BrushContinuousPlot") > 0f,
                colorJitterHueDegrees = values.float("BrushHueChange").coerceIn(-180f, 180f),
                colorJitterSaturation = values.float("BrushSaturationChange").coerceIn(-100f, 100f),
                colorJitterValue = values.float("BrushValueChange").coerceIn(-100f, 100f),
                colorJitterTarget = values.float("BrushChangeColorTarget").toInt().coerceIn(0, 3),
                colorJitterPerTipEnabled = perTipColorEnabled,
                colorJitterHueDynamics = hueEffector.colorDynamics(),
                colorJitterSaturationDynamics = saturationEffector.colorDynamics(),
                colorJitterValueDynamics = valueEffector.colorDynamics(),
                colorJitterSubColorMix = (values.float("BrushSubColor") / 100f).coerceIn(0f, 1f),
                colorJitterSubColorMixDynamics = subColorEffector.parameterDynamics(),
                colorJitterPerStrokeEnabled = perStrokeColorEnabled,
                colorJitterStrokeHueDegrees = values.float("BrushStrokeHueChange").coerceIn(-180f, 180f),
                colorJitterStrokeSaturation = values.float("BrushStrokeSaturationChange").coerceIn(-100f, 100f),
                colorJitterStrokeValue = values.float("BrushStrokeValueChange").coerceIn(-100f, 100f),
                colorJitterStrokeSubColorMix = (values.float("BrushStrokeSubColor") / 100f).coerceIn(0f, 1f),
                watercolor = watercolor,
                cspColorMixMode = colorMixMode ?: CspColorMixMode.BLEND,
                colorMix = colorMixAmount,
                colorMixPressureEnabled = colorMixEffector?.pressureEnabled == true,
                colorMixPressureMinimum = colorMixEffector?.pressureMinimum ?: 0f,
                colorMixPressureCurve = colorMixEffector?.pressureCurve.orEmpty(),
                colorMixSecondaryDynamics = colorMixEffector.secondaryDynamics(),
                paintMix = paintDensity,
                paintMixPressureEnabled = paintMixEffector?.pressureEnabled == true,
                paintMixPressureMinimum = paintMixEffector?.pressureMinimum ?: 0f,
                paintMixPressureCurve = paintMixEffector?.pressureCurve.orEmpty(),
                paintMixSecondaryDynamics = paintMixEffector.secondaryDynamics(),
                colorStretch = stretch,
                cspPigmentMixingMode = CspPigmentMixingMode.normalize(values.float("BrushColorMixingMode").toInt()),
                cspBrightnessCorrection = CspBrightnessCorrection.normalize(values.float("BrushLMSLinearity", CspBrightnessCorrection.NEUTRAL.toFloat()).toInt()),
                colorMixBlur = runningBlur,
                colorMixBlurUnit = values.float("BrushBlurUnit").toInt().coerceIn(0, 2),
                colorMixBlurAutomatic = runningBlurAutomatic,
                colorMixBlurDynamics = blurEffector.parameterDynamics(),
                spray = values.float("BrushUseSpray") > 0f,
                sprayParticleSize = values.float("BrushSpraySize", 100f).coerceIn(.01f, 4000f),
                sprayParticleSizeUnit = values.float("BrushSpraySizeUnit").toInt(),
                sprayParticleSizeSyncBrushSize = values.float("BrushSpraySizeSyncBrushSize", 1f) > 0f,
                sprayParticleSizePressureEnabled = sprayParticleSizeEffector?.pressureEnabled == true,
                sprayParticleSizePressureMinimum = sprayParticleSizeEffector?.pressureMinimum ?: 0f,
                sprayParticleSizePressureCurve = sprayParticleSizeEffector?.pressureCurve.orEmpty(),
                sprayParticleSizeSecondaryDynamics = sprayParticleSizeEffector.secondaryDynamics(),
                sprayDensity = values.float("BrushSprayDensity", 1f).coerceIn(1f, 100f),
                sprayDensityPressureEnabled = sprayDensityEffector?.pressureEnabled == true,
                sprayDensityPressureMinimum = sprayDensityEffector?.pressureMinimum ?: 0f,
                sprayDensityPressureCurve = sprayDensityEffector?.pressureCurve.orEmpty(),
                sprayDensitySecondaryDynamics = sprayDensityEffector.secondaryDynamics(),
                sprayBias = values.float("BrushSprayBias").coerceIn(-100f, 100f),
                sprayParticleRotationDegrees = values.float("BrushRotationInSpray").coerceIn(-360f, 360f),
                sprayParticleFollowStylusOrientation = sprayRotation.mode == SutRotationMode.DIRECTION_OF_PEN,
                sprayParticleFollowStrokeDirection = sprayRotation.mode == SutRotationMode.DIRECTION_OF_LINE,
                sprayParticleRandomRotationDegrees = if (sprayRotation.randomEnabled) (values.float("BrushRotationRandomInSpray") / 100f * 180f).coerceIn(0f, 180f) else 0f,
                ribbon = values.float("BrushRibbon") > 0f,
                tipSequenceMode = values.float("BrushPatternOrderType").toInt().coerceIn(0, 5),
                tipFlipHorizontalMode = values.float("BrushPatternReverseHorizontal").toInt().coerceIn(0, 3),
                tipFlipVerticalMode = values.float("BrushPatternReverseVertical").toInt().coerceIn(0, 3),
                blendTipsWithDarken = values.float("BrushBlendPatternByDarken") > 0f,
                dualBrush = dual,
                dualSizeScale = if (values.float("SyncDualBrushSize") > 0f) (values.float("DualSize", 100f) / 100f).coerceIn(.01f, 8f) else (values.float("DualSize", values.float("BrushSize", 8f)) / values.float("BrushSize", 8f).coerceAtLeast(.5f)).coerceIn(.01f, 8f),
                dualSizeDynamics = dualSizeEffector.parameterDynamics(),
                dualSpacing = (values.float("DualInterval", 12f) / 100f).coerceIn(.01f, 2f),
                dualSpacingMode = CspBrushGap.normalize(values.float("DualAutoIntervalType").toInt()),
                dualSpacingDynamics = dualSpacingEffector.parameterDynamics(),
                dualFlow = (values.float("DualFlow", 100f) / 100f).coerceIn(0f, 1f),
                dualFlowDynamics = dualFlowEffector.parameterDynamics(),
                dualHardness = (values.float("DualHardness", 100f) / 100f).coerceIn(0f, 1f),
                dualAntiAliasMode = CspAntiAlias.normalize(values.float("DualAntiAlias", CspAntiAlias.MEDIUM.toFloat()).toInt()),
                dualThickness = (values.float("DualThickness", 100f) / 100f).coerceIn(.05f, 2f),
                dualThicknessDynamics = dualThicknessEffector.parameterDynamics(),
                dualThicknessVertical = values.float("DualVerticalThicknes").toInt() != 0,
                dualRotationDegrees = values.float("DualRotation"),
                dualFollowStylusOrientation = dualRotation.mode == SutRotationMode.DIRECTION_OF_PEN,
                dualFollowStrokeDirection = dualRotation.mode == SutRotationMode.DIRECTION_OF_LINE,
                dualRandomRotationDegrees = if (dualRotation.randomEnabled) (values.float("DualRotationRandomScale") / 100f * 180f).coerceIn(0f, 180f) else 0f,
                dualRibbon = values.float("DualRibbon") > 0f,
                dualSpray = values.float("DualUseSpray") > 0f,
                dualSprayParticleSize = values.float("DualSpraySize", 100f).coerceIn(.01f, 4000f),
                dualSprayParticleSizeUnit = values.float("DualSpraySizeUnit").toInt(),
                dualSprayParticleSizeSyncBrushSize = values.float("DualSpraySizeSyncBrushSize", 1f) > 0f,
                dualSprayParticleSizeDynamics = dualSprayParticleSizeEffector.parameterDynamics(),
                dualSprayDensity = values.float("DualSprayDensity", 1f).coerceIn(1f, 100f),
                dualSprayDensityDynamics = dualSprayDensityEffector.parameterDynamics(),
                dualSprayBias = values.float("DualSprayBias").coerceIn(-100f, 100f),
                dualSprayParticleRotationDegrees = values.float("DualRotationInSpray").coerceIn(-360f, 360f),
                dualSprayParticleFollowStylusOrientation = dualSprayRotation.mode == SutRotationMode.DIRECTION_OF_PEN,
                dualSprayParticleFollowStrokeDirection = dualSprayRotation.mode == SutRotationMode.DIRECTION_OF_LINE,
                dualSprayParticleRandomRotationDegrees = if (dualSprayRotation.randomEnabled) (values.float("DualRotationRandomInSpray") / 100f * 180f).coerceIn(0f, 180f) else 0f,
                dualTipSequenceMode = values.float("DualPatternOrderType").toInt().coerceIn(0, 5),
                dualTipFlipHorizontalMode = values.float("DualPatternReverseHorizontal").toInt().coerceIn(0, 3),
                dualTipFlipVerticalMode = values.float("DualPatternReverseVertical").toInt().coerceIn(0, 3),
                dualBlendTipsWithDarken = values.float("DualBlendPatternByDarken") > 0f,
                dualCompositeMode = values.float("DualBrushCompositeMode").toInt(),
                dualApplyRgb = values.float("ChangeRGBByDual") > 0f,
                taperMode = taper.mode,
                taperStartEnabled = taper.startEnabled,
                taperStartLength = taper.startLength,
                taperStartLengthUnit = taper.startLengthUnit,
                taperStartMinimum = taper.startMinimum,
                taperEndEnabled = taper.endEnabled,
                taperEndLength = taper.endLength,
                taperEndLengthUnit = taper.endLengthUnit,
                taperEndMinimum = taper.endMinimum,
                waterEdgeEnabled = values.float("BrushUseWaterEdge") > 0f,
                waterEdgeRadius = values.float("BrushWaterEdgeRadius", 3f).coerceIn(0f, 400f),
                waterEdgeRadiusUnit = values.float("BrushWaterEdgeRadiusUnit").toInt(),
                waterEdgeOpacity = (values.float("BrushWaterEdgeAlphaPower", 20f) / 100f).coerceIn(0f, 1f),
                waterEdgeDarkness = (values.float("BrushWaterEdgeValuePower") / 100f).coerceIn(0f, 1f),
                waterEdgeBlur = values.float("BrushWaterEdgeBlur").coerceIn(0f, 400f),
                waterEdgeBlurUnit = values.float("BrushWaterEdgeBlurUnit").toInt(),
                sizePressureEnabled = sizeEffector?.pressureEnabled == true,
                sizePressureMinimum = sizeEffector?.pressureMinimum ?: 0f,
                sizePressureCurve = sizeEffector?.pressureCurve.orEmpty(),
                sizeSecondaryDynamics = sizeEffector.secondaryDynamics(),
                opacityPressureEnabled = opacityEffector?.pressureEnabled == true,
                opacityPressureMinimum = opacityEffector?.pressureMinimum ?: 0f,
                opacityPressureCurve = opacityEffector?.pressureCurve.orEmpty(),
                opacitySecondaryDynamics = opacityEffector.secondaryDynamics(),
                flowPressureEnabled = flowEffector?.pressureEnabled == true,
                flowPressureMinimum = flowEffector?.pressureMinimum ?: 0f,
                flowPressureCurve = flowEffector?.pressureCurve.orEmpty(),
                flowSecondaryDynamics = flowEffector.secondaryDynamics(),
                spacingPressureEnabled = spacingEffector?.pressureEnabled == true,
                spacingPressureMinimum = spacingEffector?.pressureMinimum ?: 0f,
                spacingPressureCurve = spacingEffector?.pressureCurve.orEmpty(),
                spacingSecondaryDynamics = spacingEffector.secondaryDynamics(),
                thicknessPressureEnabled = thicknessEffector?.pressureEnabled == true,
                thicknessPressureMinimum = thicknessEffector?.pressureMinimum ?: 0f,
                thicknessPressureCurve = thicknessEffector?.pressureCurve.orEmpty(),
                thicknessSecondaryDynamics = thicknessEffector.secondaryDynamics(),
                blurPressureEnabled = false,
                blurPressureMinimum = 0f,
                blurPressureCurve = emptyList(),
                blurSecondaryDynamics = BrushSecondaryDynamics(),
                parserVersion = CURRENT_VERSION,
                report = buildList {
                    add("CSP SQLite variant and tool name decoded")
                    add(if (tips.isNotEmpty()) "${tips.size} CSP brush-tip material preview${if (tips.size == 1) "" else "s"} extracted in source order; transparent preview padding removed before stamping" else "No embedded brush-tip thumbnail was available")
                    if (tips.isNotEmpty()) add("The protected CSP C2F material layer is retained inside the original SUT; Inkloom uses its normalized preview raster until that proprietary layer encoding can be decoded safely")
                    if (grain != null) add("Referenced CSP paper texture mapped at ${values.float("TextureDensity").toInt()}% density${textureDensityEffector?.parameterSourceSummary()?.takeIf(String::isNotBlank)?.let { " with $it dynamics" }.orEmpty()}, ${values.float("TextureScale2", 100f)}% scale, ${values.float("TextureRotate")}° rotation, brightness ${values.float("TextureBrightness").toInt()}, contrast ${values.float("TextureContrast").toInt()}, invert ${values.float("TextureReverseDensity") > 0f}, emphasize ${values.float("TextureStressDensity") > 0f}")
                    val textureMode = values.float("TextureCompositeMode").toInt()
                    if (grain != null && CspTextureComposite.isDocumented(textureMode)) add("CSP ${CspTextureComposite.displayName(textureMode)} Texture Mode mapped from real-corpus mode $textureMode using its documented blend and edge behavior")
                    else if (grain != null && textureMode > 0) add("CSP texture mode $textureMode retained with legacy density modulation until its raster equation is verified")
                    if (grain != null) add(if (values.float("TextureForPlot", 1f) > 0f) "CSP Apply by each plot enabled: texture repeats and accumulates independently on every plotted brush tip" else "CSP Apply by each plot disabled: texture is composited once across the combined stroke")
                    add("Size${if (values.float("BrushSizeUnit").toInt() == SutUnitConverter.MILLIMETERS) " converted from mm to the 1600 px reference page" else ""}, opacity, flow, hardness, spacing, thickness and rotation mapped")
                    if (values.float("BrushSizeSyncViewScale") > 0f) add("CSP Specify by size on screen enabled: the authored diameter is divided by the pen-down canvas scale so its physical footprint stays equal to 100% view")
                    if (values.float("BrushAutoIntervalType") > 0f) add("CSP ${CspBrushGap.displayName(values.float("BrushAutoIntervalType").toInt())} automatic brush-tip gap mapped; stale Fixed interval ${values.float("BrushInterval")}% is not used while this mode is active")
                    add("CSP main anti-alias mode ${CspAntiAlias.normalize(values.float("AntiAlias", CspAntiAlias.MEDIUM.toFloat()).toInt())} mapped before material grain composition")
                    if (watercolor && values.float("BrushBlur") > 0f) add("CSP Running color blur stored at ${values.float("BrushBlur")} ${if (values.float("BrushBlurUnit").toInt() == SutUnitConverter.MILLIMETERS) "mm" else "reference px"}${if (values.float("BrushBlurLinkSize") > 0f) ", Automatic relative to the live brush diameter" else ", Fixed radius"}")
                    if (values.float("FlickerReduction") > 0f) add("CSP stabilization ${values.float("FlickerReduction").toInt()} mapped${if (values.float("FlickerReductionBySpeed") > 0f && values.float("FlickerReduction") < 30f) " with speed adjustment mode ${values.float("FlickerReductionBySpeedType", 1f).toInt()}" else ""}")
                    if (values.float("BrushUseRevision") > 0f) add("CSP Post correction ${values.float("BrushRevision").toInt()} mapped with ${if (values.float("BrushRevisionBezier") > 0f) "quadratic Bezier" else "spline"} correction${if (values.float("BrushRevisionBySpeed") > 0f) ", speed adjustment" else ""}${if (values.float("BrushRevisionByViewScale") > 0f) ", canvas-scale adjustment" else ""}${if (values.float("BrushSharpenCorner") > 0f) ", and sharp-angle preservation" else ""}")
                    else if (values.float("BrushRevision") > 0f) add("CSP Post correction is disabled; stale stored amount ${values.float("BrushRevision").toInt()} is retained for editing but is not applied")
                    if (values.float("BrushUseRevision") <= 0f && values.float("BrushSharpenCorner") > 0f) add("CSP Sharp angles is enabled independently from Post correction")
                    add(when (rotation.mode) { SutRotationMode.FIXED -> "CSP tip angle mode mapped: fixed"; SutRotationMode.DIRECTION_OF_PEN -> "CSP tip angle mode mapped: direction of pen"; SutRotationMode.DIRECTION_OF_LINE -> "CSP tip angle mode mapped: direction of line"; SutRotationMode.PEN_TWIST -> "CSP tip angle uses pen-barrel twist, which Android MotionEvent does not expose on this engine"; SutRotationMode.UNKNOWN -> "Unknown CSP tip angle mode ${rotation.rawMode}; fixed angle retained" })
                    if (rotation.randomEnabled) add("CSP tip random-angle source mapped at ${values.float("BrushRotationRandomScale").toInt()}% influence")
                    if (rotation.unsupportedDynamicsFlags != 0) add("Additional CSP angle dynamics flags 0x${rotation.unsupportedDynamicsFlags.toString(16)} retained in the source; their combined strengths are not guessed")
                    val decodedCurves = listOfNotNull(sizeEffector?.pressureCurve?.takeIf { sizeEffector.pressureEnabled }?.let { "size ${it.size}-point" }, opacityEffector?.pressureCurve?.takeIf { opacityEffector.pressureEnabled }?.let { "opacity ${it.size}-point" }, flowEffector?.pressureCurve?.takeIf { flowEffector.pressureEnabled }?.let { "flow ${it.size}-point" }, spacingEffector?.pressureCurve?.takeIf { spacingEffector.pressureEnabled }?.let { "spacing ${it.size}-point" }, thicknessEffector?.pressureCurve?.takeIf { thicknessEffector.pressureEnabled }?.let { "thickness ${it.size}-point" }, blurEffector?.pressureCurve?.takeIf { blurEffector.pressureEnabled }?.let { "blur ${it.size}-point" }, colorMixEffector?.pressureCurve?.takeIf { colorMixEffector.pressureEnabled }?.let { "color mix ${it.size}-point" }, paintMixEffector?.pressureCurve?.takeIf { paintMixEffector.pressureEnabled }?.let { "paint load ${it.size}-point" }, sprayParticleSizeEffector?.pressureCurve?.takeIf { sprayParticleSizeEffector.pressureEnabled }?.let { "spray particle size ${it.size}-point" }, sprayDensityEffector?.pressureCurve?.takeIf { sprayDensityEffector.pressureEnabled }?.let { "spray density ${it.size}-point" })
                    if (decodedCurves.isNotEmpty()) add("CSP pressure dynamics decoded: ${decodedCurves.joinToString()}")
                    val secondarySources = listOf("size" to sizeEffector, "opacity" to opacityEffector, "flow" to flowEffector, "spacing" to spacingEffector, "thickness" to thicknessEffector, "blur" to blurEffector, "color mix" to colorMixEffector, "paint load" to paintMixEffector, "spray particle size" to sprayParticleSizeEffector, "spray density" to sprayDensityEffector).mapNotNull { (label, effector) -> effector?.sourceSummary()?.takeIf(String::isNotBlank)?.let { "$label $it" } }
                    if (secondarySources.isNotEmpty()) add("CSP secondary dynamics decoded: ${secondarySources.joinToString()}")
                    if (watercolor) add("CSP Color mixing enabled by ${buildList { if (values.float("BrushUseWaterColor") > 0f) add("BrushUseWaterColor"); if (values.float("BrushUseWaterColor2") > 0f) add("BrushUseWaterColor2"); if (values.float("BrushWaterColor") > 0f) add("legacy BrushWaterColor") }.joinToString("+")} in ${when (colorMixMode) { CspColorMixMode.RUNNING_COLOR -> "Running color"; CspColorMixMode.SMEAR -> "Smear"; else -> "Blend" }} mode: amount ${values.float("BrushMixColor")}, density ${values.float("BrushMixAlpha")}, color stretch ${values.float("BrushMixColorExtension")}, ${if (values.float("BrushColorMixingMode") > 0f) "Perceptual" else "Standard"} pigment mixing")
                    else if (stretch != 0f || runningBlur != 0f) add("CSP Color mixing is disabled; stale stretch and Running-color blur values are retained for editing but are not rendered")
                    if (colorMixMode == CspColorMixMode.RUNNING_COLOR) add("CSP Running color blur mapped as ${if (values.float("BrushBlurLinkSize") > 0f) "Automatic brush-size-relative" else "Fixed ${values.float("BrushBlur")} (unit ${values.float("BrushBlurUnit").toInt()})"}")
                    if (dual) {
                        val compositeCode = values.float("DualBrushCompositeMode").toInt()
                        val composite = app.inkloom.document.CspDualBrushMode.fromCode(compositeCode)
                        add(if (dualTips.isNotEmpty()) "CSP Dual Brush mapped with ${dualTips.size} referenced secondary material preview(s), independent size/flow/spacing/hardness/thickness/rotation and ${composite?.displayName ?: "unknown $compositeCode"} composite mode${if (values.float("ChangeRGBByDual") > 0f) "; secondary RGB flag retained" else " using alpha-only combination"}" else "Dual Brush detected, but no referenced secondary-tip thumbnail could be extracted; main-tip fallback retained")
                        add("CSP secondary anti-alias mode ${CspAntiAlias.normalize(values.float("DualAntiAlias", CspAntiAlias.MEDIUM.toFloat()).toInt())} retained independently from the main tip")
                        val dualDynamics = listOf("size" to dualSizeEffector, "flow" to dualFlowEffector, "spacing" to dualSpacingEffector, "thickness" to dualThicknessEffector).mapNotNull { (label, effector) ->
                            effector?.parameterSourceSummary()?.takeIf(String::isNotBlank)?.let { "$label $it" }
                        }
                        add(if (dualDynamics.isEmpty()) "CSP secondary size, flow, spacing and thickness effectors are independent and disabled; main-tip dynamics are not inherited" else "CSP secondary dynamics decoded independently: ${dualDynamics.joinToString()}")
                    }
                    if (values.float("BrushUseSpray") > 0f) add("Spray behavior mapped with particle size/unit, brush-size linking, density, center bias and independent particle direction${if (sprayRotation.randomEnabled) "/random angle" else ""}")
                    if (values.float("DualUseSpray") > 0f) {
                        val dualSprayDynamics = listOf("particle size" to dualSprayParticleSizeEffector, "density" to dualSprayDensityEffector).mapNotNull { (label, effector) -> effector?.parameterSourceSummary()?.takeIf(String::isNotBlank)?.let { "$label $it" } }
                        add("Dual spray mapped with particle size/unit, brush-size linking, density, center bias and independent particle direction${if (dualSprayRotation.randomEnabled) "/random angle" else ""}${if (dualSprayDynamics.isEmpty()) "" else "; dynamics: ${dualSprayDynamics.joinToString()}"}")
                    }
                    if (values.float("BrushRibbon") > 0f) add("CSP Ribbon detected; source aspect ratio, tangent alignment and continuous placement enabled")
                    if (values.float("BrushContinuousPlot") > 0f) add("CSP Continuous spraying mapped: holding the stylus deposits time-based stamps so slow strokes build density")
                    if (perTipColorEnabled) add("CSP Change brush tip color mapped: hue ${values.float("BrushHueChange")}, saturation ${values.float("BrushSaturationChange")}, brightness ${values.float("BrushValueChange")}, sub-color blend ${values.float("BrushSubColor")}; target ${values.float("BrushChangeColorTarget").toInt()} and deterministic pressure, tilt, velocity and random effectors retained")
                    else if (values.float("BrushHueChange") != 0f || values.float("BrushSaturationChange") != 0f || values.float("BrushValueChange") != 0f || values.float("BrushSubColor") != 0f) add("CSP Change brush tip color is disabled; stale stored color values are retained for editing but are not rendered")
                    if (perStrokeColorEnabled) add("CSP Randomize per stroke mapped: hue ${values.float("BrushStrokeHueChange")}, saturation ${values.float("BrushStrokeSaturationChange")}, brightness ${values.float("BrushStrokeValueChange")}, sub-color blend ${values.float("BrushStrokeSubColor")}; one deterministic result is held for the entire stroke")
                    if (values.float("BrushPatternReverseHorizontal") > 0f || values.float("BrushPatternReverseVertical") > 0f) add("CSP brush-tip flip modes mapped: horizontal ${values.float("BrushPatternReverseHorizontal").toInt()}, vertical ${values.float("BrushPatternReverseVertical").toInt()}")
                    if (values.float("BrushBlendPatternByDarken") > 0f) add("CSP brush-tip overlaps mapped with isolated Darken composition")
                    if (dual && (values.float("DualPatternReverseHorizontal") > 0f || values.float("DualPatternReverseVertical") > 0f)) add("CSP secondary brush-tip flip modes mapped: horizontal ${values.float("DualPatternReverseHorizontal").toInt()}, vertical ${values.float("DualPatternReverseVertical").toInt()}")
                    if (dual && values.float("DualBlendPatternByDarken") > 0f) add("CSP secondary brush-tip overlaps mapped with isolated Darken composition")
                    if (taper.startEnabled) add("CSP starting taper mapped in ${taper.mode.name.lowercase()} mode: ${taper.startLength} effective length (unit ${taper.startLengthUnit}), ${(taper.startMinimum * 100).toInt()}% brush-size minimum")
                    if (taper.endEnabled) add("CSP ending taper mapped in ${taper.mode.name.lowercase()} mode: ${taper.endLength} effective length (unit ${taper.endLengthUnit}), ${(taper.endMinimum * 100).toInt()}% brush-size minimum")
                    if ((values.float("BrushUseIn") > 0f || values.float("BrushUseOut") > 0f) && sizeTaperTarget?.enabled != true) add("CSP Starting/Ending ranges retained, but brush-size target is disabled; no size taper was invented")
                    if (values.float("BrushUseWaterEdge") > 0f) add("CSP Watercolor edge mapped: radius ${values.float("BrushWaterEdgeRadius")} (unit ${values.float("BrushWaterEdgeRadiusUnit").toInt()}), opacity ${values.float("BrushWaterEdgeAlphaPower").toInt()}%, darkness ${values.float("BrushWaterEdgeValuePower").toInt()}%")
                    if (tips.size > 1) add("CSP multi-tip repeat method ${values.float("BrushPatternOrderType").toInt()} retained")
                    add("Original SUT retained for future parser upgrades")
                },
            )
        }
    }.getOrElse { failure -> fallback.copy(report = fallback.report + "SUT parameter parsing failed: ${failure.message}") }

    private fun extractMaterialPreviews(database: SQLiteDatabase, referenceColumn: String, hash: String, suffix: String): List<File> {
        val tableExists = database.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='MaterialFile'", null).use(Cursor::moveToFirst)
        if (!tableExists) return emptyList()
        val reference = runCatching { database.rawQuery("SELECT v.$referenceColumn FROM Node n JOIN Variant v ON v.VariantID=n.NodeVariantID LIMIT 1", null).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0) && cursor.getType(0) == Cursor.FIELD_TYPE_BLOB) cursor.getBlob(0) else null } }.getOrNull() ?: return emptyList()
        // Several production SUTs contain multi-megabyte material TARs. Reading FileData through
        // Cursor.getBlob overflows Android's CursorWindow and rejects the entire brush. Query only
        // the small metadata in the window, then stream each BLOB through SQLite's file descriptor.
        val materials = database.rawQuery("SELECT rowid, OriginalPath FROM MaterialFile WHERE FileData IS NOT NULL", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1).orEmpty()) }
        }
        return materials.mapNotNull { (rowId, path) ->
            val position = listOf(path.encodeToByteArray(), path.toByteArray(Charsets.UTF_16BE), path.toByteArray(Charsets.UTF_16LE)).map { encoded -> reference.indexOf(encoded) }.filter { it >= 0 }.minOrNull() ?: return@mapNotNull null
            val tar = database.readMaterialBlob(rowId) ?: return@mapNotNull null
            val png = tarEntry(tar, "thumbnail/thumbnail.png") ?: return@mapNotNull null
            Triple(position, path, SutMaterialThumbnailNormalizer.normalize(png).png)
        }.distinctBy { it.second }.sortedBy { it.first }.mapIndexed { index, (_, _, png) ->
            val numberedSuffix = if (index == 0) suffix else "$suffix-${index + 1}"
            File(assetDirectory, "$hash-sut-v$CURRENT_VERSION-$numberedSuffix.png").also { target -> if (!target.exists()) target.writeBytes(png) }
        }
    }

    private fun SQLiteDatabase.readMaterialBlob(rowId: Long): ByteArray? = runCatching {
        compileStatement("SELECT FileData FROM MaterialFile WHERE rowid=?").use { statement ->
            statement.bindLong(1, rowId)
            statement.simpleQueryForBlobFileDescriptor()?.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_MATERIAL_ARCHIVE_BYTES) { "SUT material archive exceeds safety limit" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
        }
    }.getOrNull()

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        return (0..size - needle.size).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1
    }

    private fun tarEntry(tar: ByteArray, wanted: String): ByteArray? {
        var offset = 0
        while (offset + 512 <= tar.size) {
            val name = tar.copyOfRange(offset, offset + 100).takeWhile { it != 0.toByte() }.toByteArray().decodeToString()
            if (name.isBlank()) break
            val sizeText = tar.copyOfRange(offset + 124, offset + 136).takeWhile { it != 0.toByte() && it != ' '.code.toByte() }.toByteArray().decodeToString().trim()
            val size = sizeText.toLongOrNull(8)?.toInt() ?: return null
            val dataStart = offset + 512
            if (dataStart + size > tar.size) return null
            if (name == wanted) return tar.copyOfRange(dataStart, dataStart + size)
            offset = dataStart + ((size + 511) / 512) * 512
        }
        return null
    }

    private fun Cursor.number(name: String): Number? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getDouble(it) }
    private fun Map<String, Any?>.float(name: String, fallback: Float = 0f): Float = (this[name] as? Number)?.toFloat() ?: fallback
    private fun SQLiteDatabase.effector(column: String, columns: Set<String>): SutEffector? {
        if (column !in columns) return null
        val blob = rawQuery("SELECT v.$column FROM Node n JOIN Variant v ON v.VariantID=n.NodeVariantID LIMIT 1", null).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getBlob(0) else null }
        return SutEffectorDecoder.decode(blob)
    }
    private fun SQLiteDatabase.blob(column: String, columns: Set<String>): ByteArray? {
        if (column !in columns) return null
        return rawQuery("SELECT v.$column FROM Node n JOIN Variant v ON v.VariantID=n.NodeVariantID LIMIT 1", null).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getBlob(0) else null }
    }
    private fun SutEffector?.secondaryDynamics(): BrushSecondaryDynamics = this?.let { effector -> BrushSecondaryDynamics(tiltEnabled = effector.tiltEnabled, tiltMinimum = effector.tiltMinimum, velocityEnabled = effector.velocityEnabled, velocityMinimum = effector.velocityMinimum, velocityScale = effector.velocityScale, velocityCurve = effector.velocityCurve, randomEnabled = effector.randomEnabled, randomMinimum = effector.randomMinimum) } ?: BrushSecondaryDynamics()
    private fun SutEffector?.parameterDynamics(): BrushParameterDynamics = this?.let { effector -> BrushParameterDynamics(pressureEnabled = effector.pressureEnabled, pressureMinimum = effector.pressureMinimum, pressureCurve = effector.pressureCurve, secondary = effector.secondaryDynamics()) } ?: BrushParameterDynamics()
    private fun SutEffector?.colorDynamics(): BrushColorDynamics = this?.let { effector -> BrushColorDynamics(pressureEnabled = effector.pressureEnabled, pressureMinimum = effector.pressureMinimum, pressureCurve = effector.pressureCurve, tiltEnabled = effector.tiltEnabled, tiltMinimum = effector.tiltMinimum, velocityEnabled = effector.velocityEnabled, velocityMinimum = effector.velocityMinimum, velocityScale = effector.velocityScale, velocityCurve = effector.velocityCurve, randomEnabled = effector.randomEnabled, randomMinimum = effector.randomMinimumSigned) } ?: BrushColorDynamics()
    private fun SutEffector.sourceSummary(): String = buildList { if (tiltEnabled) add("tilt"); if (velocityEnabled) add("velocity"); if (randomEnabled) add("random") }.joinToString("+")
    private fun SutEffector.parameterSourceSummary(): String = buildList { if (pressureEnabled) add("pressure"); sourceSummary().takeIf(String::isNotBlank)?.let(::add) }.joinToString("+")
    companion object {
        const val CURRENT_VERSION = 34
        private const val MAX_MATERIAL_ARCHIVE_BYTES = 64 * 1024 * 1024
    }
}
