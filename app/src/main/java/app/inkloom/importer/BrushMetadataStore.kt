package app.inkloom.importer

import android.content.Context
import app.inkloom.document.CspTextureComposite
import app.inkloom.document.CspAntiAlias
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.BrushSemanticMapping
import app.inkloom.model.SemanticFidelity
import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushTaperMode
import org.json.JSONArray
import org.json.JSONObject

class BrushMetadataStore(
    context: Context,
    preferenceName: String = DEFAULT_PREFERENCES,
    private val key: String = DEFAULT_KEY,
) {
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)

    fun load(): List<ImportedBrush> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val reportArray = item.optJSONArray("report") ?: JSONArray()
                val tagsArray = item.optJSONArray("tags") ?: JSONArray()
                val tipPathsArray = item.optJSONArray("tipPreviewPaths") ?: JSONArray()
                val dualTipPathsArray = item.optJSONArray("dualTipPreviewPaths") ?: JSONArray()
                val semanticArray = item.optJSONArray("semanticMappings") ?: JSONArray()
                val semanticMappings = buildList {
                    for (mappingIndex in 0 until semanticArray.length()) {
                        val mapping = semanticArray.optJSONObject(mappingIndex) ?: continue
                        val fidelity = runCatching { SemanticFidelity.valueOf(mapping.optString("fidelity")) }.getOrNull() ?: continue
                        add(
                            BrushSemanticMapping(
                                sourceSemantic = mapping.optString("sourceSemantic"),
                                targetRuntime = mapping.optString("targetRuntime"),
                                fidelity = fidelity,
                                note = mapping.optString("note"),
                            ),
                        )
                    }
                }
                fun curve(key: String): List<BrushCurvePoint> {
                    val points = item.optJSONArray(key) ?: return emptyList()
                    return buildList { for (pointIndex in 0 until points.length()) { val pair = points.optJSONArray(pointIndex) ?: continue; if (pair.length() >= 2) add(BrushCurvePoint(pair.optDouble(0).toFloat(), pair.optDouble(1).toFloat())) } }
                }
                fun secondary(key: String): BrushSecondaryDynamics {
                    val value = item.optJSONObject(key) ?: return BrushSecondaryDynamics()
                    val velocityPoints = value.optJSONArray("velocityCurve")
                    val velocityCurve = buildList { if (velocityPoints != null) for (pointIndex in 0 until velocityPoints.length()) { val pair = velocityPoints.optJSONArray(pointIndex) ?: continue; if (pair.length() >= 2) add(BrushCurvePoint(pair.optDouble(0).toFloat(), pair.optDouble(1).toFloat())) } }
                    return BrushSecondaryDynamics(
                        tiltEnabled = value.optBoolean("tiltEnabled", false),
                        tiltMinimum = value.optDouble("tiltMinimum", 0.0).toFloat(),
                        velocityEnabled = value.optBoolean("velocityEnabled", false),
                        velocityMinimum = value.optDouble("velocityMinimum", 0.0).toFloat(),
                        velocityScale = value.optDouble("velocityScale", 1.0).toFloat(),
                        velocityCurve = velocityCurve,
                        randomEnabled = value.optBoolean("randomEnabled", false),
                        randomMinimum = value.optDouble("randomMinimum", 0.0).toFloat(),
                    )
                }
                fun colorDynamics(key: String): BrushColorDynamics {
                    val value = item.optJSONObject(key) ?: return BrushColorDynamics()
                    fun points(name: String): List<BrushCurvePoint> { val source = value.optJSONArray(name) ?: return emptyList(); return buildList { for (pointIndex in 0 until source.length()) { val pair = source.optJSONArray(pointIndex) ?: continue; if (pair.length() >= 2) add(BrushCurvePoint(pair.optDouble(0).toFloat(), pair.optDouble(1).toFloat())) } } }
                    return BrushColorDynamics(pressureEnabled = value.optBoolean("pressureEnabled"), pressureMinimum = value.optDouble("pressureMinimum").toFloat(), pressureCurve = points("pressureCurve"), tiltEnabled = value.optBoolean("tiltEnabled"), tiltMinimum = value.optDouble("tiltMinimum").toFloat(), velocityEnabled = value.optBoolean("velocityEnabled"), velocityMinimum = value.optDouble("velocityMinimum").toFloat(), velocityScale = value.optDouble("velocityScale", 1.0).toFloat(), velocityCurve = points("velocityCurve"), randomEnabled = value.optBoolean("randomEnabled"), randomMinimum = value.optDouble("randomMinimum", -1.0).toFloat())
                }
                fun parameterDynamics(key: String): BrushParameterDynamics {
                    val value = item.optJSONObject(key) ?: return BrushParameterDynamics()
                    val points = value.optJSONArray("pressureCurve")
                    val pressureCurve = buildList { if (points != null) for (pointIndex in 0 until points.length()) { val pair = points.optJSONArray(pointIndex) ?: continue; if (pair.length() >= 2) add(BrushCurvePoint(pair.optDouble(0).toFloat(), pair.optDouble(1).toFloat())) } }
                    return BrushParameterDynamics(
                        pressureEnabled = value.optBoolean("pressureEnabled", false),
                        pressureMinimum = value.optDouble("pressureMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                        pressureCurve = pressureCurve,
                        secondary = value.optJSONObject("secondary")?.let { nested ->
                            val velocityPoints = nested.optJSONArray("velocityCurve")
                            val velocityCurve = buildList { if (velocityPoints != null) for (pointIndex in 0 until velocityPoints.length()) { val pair = velocityPoints.optJSONArray(pointIndex) ?: continue; if (pair.length() >= 2) add(BrushCurvePoint(pair.optDouble(0).toFloat(), pair.optDouble(1).toFloat())) } }
                            BrushSecondaryDynamics(
                                tiltEnabled = nested.optBoolean("tiltEnabled", false),
                                tiltMinimum = nested.optDouble("tiltMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                                velocityEnabled = nested.optBoolean("velocityEnabled", false),
                                velocityMinimum = nested.optDouble("velocityMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                                velocityScale = nested.optDouble("velocityScale", 1.0).toFloat().coerceIn(0f, 10f),
                                velocityCurve = velocityCurve,
                                randomEnabled = nested.optBoolean("randomEnabled", false),
                                randomMinimum = nested.optDouble("randomMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                            )
                        } ?: BrushSecondaryDynamics(),
                    )
                }
                val legacyTipPath = item.optString("tipPreviewPath").takeIf { it.isNotBlank() }
                val tipPaths = buildList { for (pathIndex in 0 until tipPathsArray.length()) tipPathsArray.optString(pathIndex).takeIf(String::isNotBlank)?.let(::add) }.ifEmpty { listOfNotNull(legacyTipPath) }
                val dualTipPaths = buildList { for (pathIndex in 0 until dualTipPathsArray.length()) dualTipPathsArray.optString(pathIndex).takeIf(String::isNotBlank)?.let(::add) }
                val taperMode = runCatching { BrushTaperMode.valueOf(item.optString("taperMode", BrushTaperMode.PERCENTAGE.name)) }.getOrDefault(BrushTaperMode.PERCENTAGE)
                fun taperLength(key: String, fallback: Float): Float = item.optDouble(key, fallback.toDouble()).toFloat().let { value -> if (taperMode == BrushTaperMode.DISTANCE) value.coerceIn(.1f, 4000f) else value.coerceIn(.001f, 1f) }
                add(
                    ImportedBrush(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        format = BrushFormat.valueOf(item.getString("format")),
                        compatibility = Compatibility.valueOf(item.getString("compatibility")),
                        sizeBytes = item.getLong("sizeBytes"),
                        report = buildList { for (reportIndex in 0 until reportArray.length()) add(reportArray.getString(reportIndex)) },
                        sourcePath = item.optString("sourcePath").takeIf { it.isNotBlank() },
                        sha256 = item.optString("sha256").takeIf { it.isNotBlank() },
                        tipPreviewPath = legacyTipPath,
                        tipPreviewPaths = tipPaths,
                        dualTipPreviewPaths = dualTipPaths,
                        grainPreviewPath = item.optString("grainPreviewPath").takeIf { it.isNotBlank() },
                        grainDensity = item.optDouble("grainDensity", 0.0).toFloat(),
                        grainDensityDynamics = parameterDynamics("grainDensityDynamics"),
                        grainScale = item.optDouble("grainScale", 100.0).toFloat().coerceIn(1f, 4000f),
                        grainMovement = item.optDouble("grainMovement", 0.0).toFloat().coerceIn(0f, 1f),
                        grainZoom = item.optDouble("grainZoom", 0.0).toFloat().coerceIn(0f, 1f),
                        grainRotationDegrees = item.optDouble("grainRotationDegrees", 0.0).toFloat().coerceIn(-360f, 360f),
                        grainBrightness = item.optDouble("grainBrightness", 0.0).toFloat().coerceIn(-100f, 100f),
                        grainContrast = item.optDouble("grainContrast", 0.0).toFloat().coerceIn(-100f, 100f),
                        grainReversed = item.optBoolean("grainReversed", false),
                        grainEmphasized = item.optBoolean("grainEmphasized", false),
                        grainOffsetJitter = item.optBoolean("grainOffsetJitter", false),
                        grainCompositeMode = item.optInt("grainCompositeMode", CspTextureComposite.MULTIPLY).coerceIn(0, 64),
                        defaultSize = item.optDouble("defaultSize", 8.0).toFloat(),
                        streamLine = item.optDouble("streamLine", 0.0).toFloat().coerceIn(0f, 1f),
                        streamLinePressure = item.optDouble("streamLinePressure", 0.0).toFloat().coerceIn(0f, 1f),
                        procreateStabilization = item.optDouble("procreateStabilization", 0.0).toFloat().coerceIn(0f, 1f),
                        motionFiltering = item.optDouble("motionFiltering", 0.0).toFloat().coerceIn(0f, 1f),
                        motionFilteringExpression = item.optDouble("motionFilteringExpression", 0.0).toFloat().coerceIn(0f, 1f),
                        stabilization = item.optDouble("stabilization", 0.0).toFloat().coerceIn(0f, .9f),
                        stabilizationBySpeed = item.optBoolean("stabilizationBySpeed", false),
                        stabilizationBySpeedMode = item.optInt("stabilizationBySpeedMode", 1).coerceIn(0, 1),
                        spacing = item.optDouble("spacing", .12).toFloat(),
                        spacingMode = app.inkloom.document.CspBrushGap.normalize(item.optInt("spacingMode", app.inkloom.document.CspBrushGap.FIXED)),
                        flow = item.optDouble("flow", 1.0).toFloat(),
                        hardness = item.optDouble("hardness", 1.0).toFloat(),
                        antiAliasMode = item.optInt("antiAliasMode", CspAntiAlias.MEDIUM).coerceIn(CspAntiAlias.NONE, CspAntiAlias.STRONG),
                        blur = item.optDouble("blur", 0.0).toFloat().coerceIn(0f, 400f),
                        blurUnit = item.optInt("blurUnit", 0).coerceIn(0, 2),
                        blurLinkedToSize = item.optBoolean("blurLinkedToSize", false),
                        thickness = item.optDouble("thickness", 1.0).toFloat(),
                        thicknessVertical = item.optBoolean("thicknessVertical", false),
                        rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat(),
                        followStylusOrientation = item.optBoolean("followStylusOrientation", false),
                        followStrokeDirection = item.optBoolean("followStrokeDirection", false),
                        randomRotationDegrees = item.optDouble("randomRotationDegrees", 0.0).toFloat(),
                        strokeRotationJitterDegrees = item.optDouble("strokeRotationJitterDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        stampCount = item.optInt("stampCount", 1).coerceIn(1, 16),
                        stampCountJitter = item.optDouble("stampCountJitter", 0.0).toFloat().coerceIn(0f, 1f),
                        continuousPlot = item.optBoolean("continuousPlot", false),
                        colorJitterHueDegrees = item.optDouble("colorJitterHueDegrees", 0.0).toFloat().coerceIn(-180f, 180f),
                        colorJitterSaturation = item.optDouble("colorJitterSaturation", 0.0).toFloat().coerceIn(-100f, 100f),
                        colorJitterValue = item.optDouble("colorJitterValue", 0.0).toFloat().coerceIn(-100f, 100f),
                        colorJitterTarget = item.optInt("colorJitterTarget", 0).coerceIn(0, 3),
                        colorJitterHueDynamics = colorDynamics("colorJitterHueDynamics"),
                        colorJitterSaturationDynamics = colorDynamics("colorJitterSaturationDynamics"),
                        colorJitterValueDynamics = colorDynamics("colorJitterValueDynamics"),
                        watercolor = item.optBoolean("watercolor", false),
                        colorMix = item.optDouble("colorMix", 0.0).toFloat(),
                        colorMixPressureEnabled = item.optBoolean("colorMixPressureEnabled", false),
                        colorMixPressureMinimum = item.optDouble("colorMixPressureMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                        colorMixPressureCurve = curve("colorMixPressureCurve"),
                        colorMixSecondaryDynamics = secondary("colorMixSecondaryDynamics"),
                        paintMix = item.optDouble("paintMix", 0.0).toFloat(),
                        paintMixPressureEnabled = item.optBoolean("paintMixPressureEnabled", false),
                        paintMixPressureMinimum = item.optDouble("paintMixPressureMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                        paintMixPressureCurve = curve("paintMixPressureCurve"),
                        paintMixSecondaryDynamics = secondary("paintMixSecondaryDynamics"),
                        spray = item.optBoolean("spray", false),
                        sprayParticleSize = item.optDouble("sprayParticleSize", 100.0).toFloat().coerceIn(.01f, 4000f),
                        sprayParticleSizeUnit = item.optInt("sprayParticleSizeUnit", 0),
                        sprayParticleSizeSyncBrushSize = item.optBoolean("sprayParticleSizeSyncBrushSize", true),
                        sprayParticleSizePressureEnabled = item.optBoolean("sprayParticleSizePressureEnabled", false),
                        sprayParticleSizePressureMinimum = item.optDouble("sprayParticleSizePressureMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                        sprayParticleSizePressureCurve = curve("sprayParticleSizePressureCurve"),
                        sprayParticleSizeSecondaryDynamics = secondary("sprayParticleSizeSecondaryDynamics"),
                        sprayDensity = item.optDouble("sprayDensity", 1.0).toFloat().coerceIn(1f, 100f),
                        sprayDensityPressureEnabled = item.optBoolean("sprayDensityPressureEnabled", false),
                        sprayDensityPressureMinimum = item.optDouble("sprayDensityPressureMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                        sprayDensityPressureCurve = curve("sprayDensityPressureCurve"),
                        sprayDensitySecondaryDynamics = secondary("sprayDensitySecondaryDynamics"),
                        sprayBias = item.optDouble("sprayBias", 0.0).toFloat().coerceIn(-100f, 100f),
                        spraySpread = item.optDouble("spraySpread", 1.0).toFloat().coerceIn(0f, 4f),
                        sprayParticleRotationDegrees = item.optDouble("sprayParticleRotationDegrees", 0.0).toFloat().coerceIn(-360f, 360f),
                        sprayParticleFollowStylusOrientation = item.optBoolean("sprayParticleFollowStylusOrientation", false),
                        sprayParticleFollowStrokeDirection = item.optBoolean("sprayParticleFollowStrokeDirection", false),
                        sprayParticleRandomRotationDegrees = item.optDouble("sprayParticleRandomRotationDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        ribbon = item.optBoolean("ribbon", false),
                        tipSequenceMode = item.optInt("tipSequenceMode", 0).coerceIn(0, 5),
                        tipFlipHorizontalMode = item.optInt("tipFlipHorizontalMode", 0).coerceIn(0, 3),
                        tipFlipVerticalMode = item.optInt("tipFlipVerticalMode", 0).coerceIn(0, 3),
                        blendTipsWithDarken = item.optBoolean("blendTipsWithDarken", false),
                        dualBrush = item.optBoolean("dualBrush", false),
                        dualSizeScale = item.optDouble("dualSizeScale", 1.0).toFloat().coerceIn(.01f, 8f),
                        dualSizeDynamics = parameterDynamics("dualSizeDynamics"),
                        dualSpacing = item.optDouble("dualSpacing", .12).toFloat().coerceIn(.01f, 2f),
                        dualSpacingMode = app.inkloom.document.CspBrushGap.normalize(item.optInt("dualSpacingMode", app.inkloom.document.CspBrushGap.FIXED)),
                        dualSpacingDynamics = parameterDynamics("dualSpacingDynamics"),
                        dualFlow = item.optDouble("dualFlow", 1.0).toFloat().coerceIn(0f, 1f),
                        dualFlowDynamics = parameterDynamics("dualFlowDynamics"),
                        dualHardness = item.optDouble("dualHardness", 1.0).toFloat().coerceIn(0f, 1f),
                        dualAntiAliasMode = item.optInt("dualAntiAliasMode", CspAntiAlias.MEDIUM).coerceIn(CspAntiAlias.NONE, CspAntiAlias.STRONG),
                        dualThickness = item.optDouble("dualThickness", 1.0).toFloat().coerceIn(.05f, 2f),
                        dualThicknessDynamics = parameterDynamics("dualThicknessDynamics"),
                        dualThicknessVertical = item.optBoolean("dualThicknessVertical", false),
                        dualRotationDegrees = item.optDouble("dualRotationDegrees", 0.0).toFloat(),
                        dualFollowStylusOrientation = item.optBoolean("dualFollowStylusOrientation", false),
                        dualFollowStrokeDirection = item.optBoolean("dualFollowStrokeDirection", false),
                        dualRandomRotationDegrees = item.optDouble("dualRandomRotationDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        dualStrokeRotationJitterDegrees = item.optDouble("dualStrokeRotationJitterDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        dualStampCount = item.optInt("dualStampCount", 1).coerceIn(1, 16),
                        dualStampCountJitter = item.optDouble("dualStampCountJitter", 0.0).toFloat().coerceIn(0f, 1f),
                        dualRibbon = item.optBoolean("dualRibbon", false),
                        dualSpray = item.optBoolean("dualSpray", false),
                        dualSprayParticleSize = item.optDouble("dualSprayParticleSize", 100.0).toFloat().coerceIn(.01f, 4000f),
                        dualSprayParticleSizeUnit = item.optInt("dualSprayParticleSizeUnit", 0),
                        dualSprayParticleSizeSyncBrushSize = item.optBoolean("dualSprayParticleSizeSyncBrushSize", true),
                        dualSprayParticleSizeDynamics = parameterDynamics("dualSprayParticleSizeDynamics"),
                        dualSprayDensity = item.optDouble("dualSprayDensity", 1.0).toFloat().coerceIn(1f, 100f),
                        dualSprayDensityDynamics = parameterDynamics("dualSprayDensityDynamics"),
                        dualSprayBias = item.optDouble("dualSprayBias", 0.0).toFloat().coerceIn(-100f, 100f),
                        dualSpraySpread = item.optDouble("dualSpraySpread", 1.0).toFloat().coerceIn(0f, 4f),
                        dualSprayParticleRotationDegrees = item.optDouble("dualSprayParticleRotationDegrees", 0.0).toFloat().coerceIn(-360f, 360f),
                        dualSprayParticleFollowStylusOrientation = item.optBoolean("dualSprayParticleFollowStylusOrientation", false),
                        dualSprayParticleFollowStrokeDirection = item.optBoolean("dualSprayParticleFollowStrokeDirection", false),
                        dualSprayParticleRandomRotationDegrees = item.optDouble("dualSprayParticleRandomRotationDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        dualTipSequenceMode = item.optInt("dualTipSequenceMode", 0).coerceIn(0, 5),
                        dualTipFlipHorizontalMode = item.optInt("dualTipFlipHorizontalMode", 0).coerceIn(0, 3),
                        dualTipFlipVerticalMode = item.optInt("dualTipFlipVerticalMode", 0).coerceIn(0, 3),
                        dualBlendTipsWithDarken = item.optBoolean("dualBlendTipsWithDarken", false),
                        dualCompositeMode = item.optInt("dualCompositeMode", 0),
                        dualApplyRgb = item.optBoolean("dualApplyRgb", false),
                        taperMode = taperMode,
                        taperStartEnabled = item.optBoolean("taperStartEnabled", false),
                        taperStartLength = taperLength("taperStartLength", .2f),
                        taperStartLengthUnit = item.optInt("taperStartLengthUnit", 0),
                        taperStartMinimum = item.optDouble("taperStartMinimum", .3).toFloat().coerceIn(0f, 1f),
                        taperEndEnabled = item.optBoolean("taperEndEnabled", false),
                        taperEndLength = taperLength("taperEndLength", .2f),
                        taperEndLengthUnit = item.optInt("taperEndLengthUnit", 0),
                        taperEndMinimum = item.optDouble("taperEndMinimum", .3).toFloat().coerceIn(0f, 1f),
                        waterEdgeEnabled = item.optBoolean("waterEdgeEnabled", false),
                        waterEdgeRadius = item.optDouble("waterEdgeRadius", 3.0).toFloat().coerceIn(0f, 400f),
                        waterEdgeRadiusUnit = item.optInt("waterEdgeRadiusUnit", 0),
                        waterEdgeOpacity = item.optDouble("waterEdgeOpacity", .2).toFloat().coerceIn(0f, 1f),
                        waterEdgeDarkness = item.optDouble("waterEdgeDarkness", 0.0).toFloat().coerceIn(0f, 1f),
                        waterEdgeBlur = item.optDouble("waterEdgeBlur", 0.0).toFloat().coerceIn(0f, 400f),
                        waterEdgeBlurUnit = item.optInt("waterEdgeBlurUnit", 0),
                        favorite = item.optBoolean("favorite", false),
                        hidden = item.optBoolean("hidden", false),
                        isEraser = item.optBoolean("isEraser", false),
                        groupName = BrushGroupOperations.normalize(item.optString("groupName", "")),
                        tags = buildList { for (tagIndex in 0 until tagsArray.length()) add(tagsArray.getString(tagIndex)) },
                        lastUsedAt = item.optLong("lastUsedAt", 0L),
                        sortOrder = item.optInt("sortOrder", 0).coerceIn(0, 100_000),
                        defaultOpacity = item.optDouble("defaultOpacity", 1.0).toFloat(),
                        sizePressureEnabled = item.optBoolean("sizePressureEnabled", true),
                        sizePressureMinimum = item.optDouble("sizePressureMinimum", .35).toFloat(),
                        sizePressureCurve = curve("sizePressureCurve"),
                        sizeSecondaryDynamics = secondary("sizeSecondaryDynamics"),
                        opacityPressureEnabled = item.optBoolean("opacityPressureEnabled", false),
                        opacityPressureMinimum = item.optDouble("opacityPressureMinimum", 0.0).toFloat(),
                        opacityPressureCurve = curve("opacityPressureCurve"),
                        opacitySecondaryDynamics = secondary("opacitySecondaryDynamics"),
                        flowPressureEnabled = item.optBoolean("flowPressureEnabled", false),
                        flowPressureMinimum = item.optDouble("flowPressureMinimum", 0.0).toFloat(),
                        flowPressureCurve = curve("flowPressureCurve"),
                        flowSecondaryDynamics = secondary("flowSecondaryDynamics"),
                        spacingPressureEnabled = item.optBoolean("spacingPressureEnabled", false),
                        spacingPressureMinimum = item.optDouble("spacingPressureMinimum", 0.0).toFloat(),
                        spacingPressureCurve = curve("spacingPressureCurve"),
                        spacingSecondaryDynamics = secondary("spacingSecondaryDynamics"),
                        thicknessPressureEnabled = item.optBoolean("thicknessPressureEnabled", false),
                        thicknessPressureMinimum = item.optDouble("thicknessPressureMinimum", 0.0).toFloat(),
                        thicknessPressureCurve = curve("thicknessPressureCurve"),
                        thicknessSecondaryDynamics = secondary("thicknessSecondaryDynamics"),
                        blurPressureEnabled = item.optBoolean("blurPressureEnabled", false),
                        blurPressureMinimum = item.optDouble("blurPressureMinimum", 0.0).toFloat().coerceIn(0f, 1f),
                        blurPressureCurve = curve("blurPressureCurve"),
                        blurSecondaryDynamics = secondary("blurSecondaryDynamics"),
                        parserVersion = item.optInt("parserVersion", 0),
                        semanticMappings = semanticMappings,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList()).map { brush ->
        if (brush.semanticMappings.isEmpty()) BrushSemanticMapper.apply(brush) else brush
    }

    fun save(brushes: List<ImportedBrush>) {
        val array = JSONArray()
        brushes.map(BrushSemanticMapper::apply).forEach { brush ->
            array.put(
                JSONObject()
                    .put("id", brush.id)
                    .put("name", brush.name)
                    .put("format", brush.format.name)
                    .put("compatibility", brush.compatibility.name)
                    .put("sizeBytes", brush.sizeBytes)
                    .put("report", JSONArray(brush.report))
                    .put("sourcePath", brush.sourcePath)
                    .put("sha256", brush.sha256)
                    .put("tipPreviewPath", brush.tipPreviewPath)
                    .put("tipPreviewPaths", JSONArray(brush.tipPreviewPaths))
                    .put("dualTipPreviewPaths", JSONArray(brush.dualTipPreviewPaths))
                    .put("grainPreviewPath", brush.grainPreviewPath)
                    .put("grainDensity", brush.grainDensity)
                    .put("grainDensityDynamics", parameterDynamicsJson(brush.grainDensityDynamics))
                    .put("grainScale", brush.grainScale)
                    .put("grainMovement", brush.grainMovement)
                    .put("grainZoom", brush.grainZoom)
                    .put("grainRotationDegrees", brush.grainRotationDegrees)
                    .put("grainBrightness", brush.grainBrightness)
                    .put("grainContrast", brush.grainContrast)
                    .put("grainReversed", brush.grainReversed)
                    .put("grainEmphasized", brush.grainEmphasized)
                    .put("grainOffsetJitter", brush.grainOffsetJitter)
                    .put("grainCompositeMode", brush.grainCompositeMode)
                    .put("defaultSize", brush.defaultSize)
                    .put("streamLine", brush.streamLine)
                    .put("streamLinePressure", brush.streamLinePressure)
                    .put("procreateStabilization", brush.procreateStabilization)
                    .put("motionFiltering", brush.motionFiltering)
                    .put("motionFilteringExpression", brush.motionFilteringExpression)
                    .put("stabilization", brush.stabilization)
                    .put("stabilizationBySpeed", brush.stabilizationBySpeed)
                    .put("stabilizationBySpeedMode", brush.stabilizationBySpeedMode)
                    .put("spacing", brush.spacing)
                    .put("spacingMode", brush.spacingMode)
                    .put("flow", brush.flow)
                    .put("hardness", brush.hardness)
                    .put("antiAliasMode", brush.antiAliasMode)
                    .put("blur", brush.blur)
                    .put("blurUnit", brush.blurUnit)
                    .put("blurLinkedToSize", brush.blurLinkedToSize)
                    .put("thickness", brush.thickness)
                    .put("thicknessVertical", brush.thicknessVertical)
                    .put("rotationDegrees", brush.rotationDegrees)
                    .put("followStylusOrientation", brush.followStylusOrientation)
                    .put("followStrokeDirection", brush.followStrokeDirection)
                    .put("randomRotationDegrees", brush.randomRotationDegrees)
                    .put("strokeRotationJitterDegrees", brush.strokeRotationJitterDegrees)
                    .put("stampCount", brush.stampCount)
                    .put("stampCountJitter", brush.stampCountJitter)
                    .put("continuousPlot", brush.continuousPlot)
                    .put("colorJitterHueDegrees", brush.colorJitterHueDegrees)
                    .put("colorJitterSaturation", brush.colorJitterSaturation)
                    .put("colorJitterValue", brush.colorJitterValue)
                    .put("colorJitterTarget", brush.colorJitterTarget)
                    .put("colorJitterHueDynamics", colorDynamicsJson(brush.colorJitterHueDynamics))
                    .put("colorJitterSaturationDynamics", colorDynamicsJson(brush.colorJitterSaturationDynamics))
                    .put("colorJitterValueDynamics", colorDynamicsJson(brush.colorJitterValueDynamics))
                    .put("watercolor", brush.watercolor)
                    .put("colorMix", brush.colorMix)
                    .put("colorMixPressureEnabled", brush.colorMixPressureEnabled)
                    .put("colorMixPressureMinimum", brush.colorMixPressureMinimum)
                    .put("colorMixPressureCurve", curveJson(brush.colorMixPressureCurve))
                    .put("colorMixSecondaryDynamics", secondaryJson(brush.colorMixSecondaryDynamics))
                    .put("paintMix", brush.paintMix)
                    .put("paintMixPressureEnabled", brush.paintMixPressureEnabled)
                    .put("paintMixPressureMinimum", brush.paintMixPressureMinimum)
                    .put("paintMixPressureCurve", curveJson(brush.paintMixPressureCurve))
                    .put("paintMixSecondaryDynamics", secondaryJson(brush.paintMixSecondaryDynamics))
                    .put("spray", brush.spray)
                    .put("sprayParticleSize", brush.sprayParticleSize)
                    .put("sprayParticleSizeUnit", brush.sprayParticleSizeUnit)
                    .put("sprayParticleSizeSyncBrushSize", brush.sprayParticleSizeSyncBrushSize)
                    .put("sprayParticleSizePressureEnabled", brush.sprayParticleSizePressureEnabled)
                    .put("sprayParticleSizePressureMinimum", brush.sprayParticleSizePressureMinimum)
                    .put("sprayParticleSizePressureCurve", curveJson(brush.sprayParticleSizePressureCurve))
                    .put("sprayParticleSizeSecondaryDynamics", secondaryJson(brush.sprayParticleSizeSecondaryDynamics))
                    .put("sprayDensity", brush.sprayDensity)
                    .put("sprayDensityPressureEnabled", brush.sprayDensityPressureEnabled)
                    .put("sprayDensityPressureMinimum", brush.sprayDensityPressureMinimum)
                    .put("sprayDensityPressureCurve", curveJson(brush.sprayDensityPressureCurve))
                    .put("sprayDensitySecondaryDynamics", secondaryJson(brush.sprayDensitySecondaryDynamics))
                    .put("sprayBias", brush.sprayBias)
                    .put("spraySpread", brush.spraySpread)
                    .put("sprayParticleRotationDegrees", brush.sprayParticleRotationDegrees)
                    .put("sprayParticleFollowStylusOrientation", brush.sprayParticleFollowStylusOrientation)
                    .put("sprayParticleFollowStrokeDirection", brush.sprayParticleFollowStrokeDirection)
                    .put("sprayParticleRandomRotationDegrees", brush.sprayParticleRandomRotationDegrees)
                    .put("ribbon", brush.ribbon)
                    .put("tipSequenceMode", brush.tipSequenceMode)
                    .put("tipFlipHorizontalMode", brush.tipFlipHorizontalMode)
                    .put("tipFlipVerticalMode", brush.tipFlipVerticalMode)
                    .put("blendTipsWithDarken", brush.blendTipsWithDarken)
                    .put("dualBrush", brush.dualBrush)
                    .put("dualSizeScale", brush.dualSizeScale)
                    .put("dualSizeDynamics", parameterDynamicsJson(brush.dualSizeDynamics))
                    .put("dualSpacing", brush.dualSpacing)
                    .put("dualSpacingMode", brush.dualSpacingMode)
                    .put("dualSpacingDynamics", parameterDynamicsJson(brush.dualSpacingDynamics))
                    .put("dualFlow", brush.dualFlow)
                    .put("dualFlowDynamics", parameterDynamicsJson(brush.dualFlowDynamics))
                    .put("dualHardness", brush.dualHardness)
                    .put("dualAntiAliasMode", brush.dualAntiAliasMode)
                    .put("dualThickness", brush.dualThickness)
                    .put("dualThicknessDynamics", parameterDynamicsJson(brush.dualThicknessDynamics))
                    .put("dualThicknessVertical", brush.dualThicknessVertical)
                    .put("dualRotationDegrees", brush.dualRotationDegrees)
                    .put("dualFollowStylusOrientation", brush.dualFollowStylusOrientation)
                    .put("dualFollowStrokeDirection", brush.dualFollowStrokeDirection)
                    .put("dualRandomRotationDegrees", brush.dualRandomRotationDegrees)
                    .put("dualStrokeRotationJitterDegrees", brush.dualStrokeRotationJitterDegrees)
                    .put("dualStampCount", brush.dualStampCount)
                    .put("dualStampCountJitter", brush.dualStampCountJitter)
                    .put("dualRibbon", brush.dualRibbon)
                    .put("dualSpray", brush.dualSpray)
                    .put("dualSprayParticleSize", brush.dualSprayParticleSize)
                    .put("dualSprayParticleSizeUnit", brush.dualSprayParticleSizeUnit)
                    .put("dualSprayParticleSizeSyncBrushSize", brush.dualSprayParticleSizeSyncBrushSize)
                    .put("dualSprayParticleSizeDynamics", parameterDynamicsJson(brush.dualSprayParticleSizeDynamics))
                    .put("dualSprayDensity", brush.dualSprayDensity)
                    .put("dualSprayDensityDynamics", parameterDynamicsJson(brush.dualSprayDensityDynamics))
                    .put("dualSprayBias", brush.dualSprayBias)
                    .put("dualSpraySpread", brush.dualSpraySpread)
                    .put("dualSprayParticleRotationDegrees", brush.dualSprayParticleRotationDegrees)
                    .put("dualSprayParticleFollowStylusOrientation", brush.dualSprayParticleFollowStylusOrientation)
                    .put("dualSprayParticleFollowStrokeDirection", brush.dualSprayParticleFollowStrokeDirection)
                    .put("dualSprayParticleRandomRotationDegrees", brush.dualSprayParticleRandomRotationDegrees)
                    .put("dualTipSequenceMode", brush.dualTipSequenceMode)
                    .put("dualTipFlipHorizontalMode", brush.dualTipFlipHorizontalMode)
                    .put("dualTipFlipVerticalMode", brush.dualTipFlipVerticalMode)
                    .put("dualBlendTipsWithDarken", brush.dualBlendTipsWithDarken)
                    .put("dualCompositeMode", brush.dualCompositeMode)
                    .put("dualApplyRgb", brush.dualApplyRgb)
                    .put("taperMode", brush.taperMode.name)
                    .put("taperStartEnabled", brush.taperStartEnabled)
                    .put("taperStartLength", brush.taperStartLength)
                    .put("taperStartLengthUnit", brush.taperStartLengthUnit)
                    .put("taperStartMinimum", brush.taperStartMinimum)
                    .put("taperEndEnabled", brush.taperEndEnabled)
                    .put("taperEndLength", brush.taperEndLength)
                    .put("taperEndLengthUnit", brush.taperEndLengthUnit)
                    .put("taperEndMinimum", brush.taperEndMinimum)
                    .put("waterEdgeEnabled", brush.waterEdgeEnabled)
                    .put("waterEdgeRadius", brush.waterEdgeRadius)
                    .put("waterEdgeRadiusUnit", brush.waterEdgeRadiusUnit)
                    .put("waterEdgeOpacity", brush.waterEdgeOpacity)
                    .put("waterEdgeDarkness", brush.waterEdgeDarkness)
                    .put("waterEdgeBlur", brush.waterEdgeBlur)
                    .put("waterEdgeBlurUnit", brush.waterEdgeBlurUnit)
                    .put("favorite", brush.favorite)
                    .put("hidden", brush.hidden)
                    .put("isEraser", brush.isEraser)
                    .put("groupName", BrushGroupOperations.normalize(brush.groupName))
                    .put("tags", JSONArray(brush.tags))
                    .put("lastUsedAt", brush.lastUsedAt)
                    .put("sortOrder", brush.sortOrder)
                    .put("defaultOpacity", brush.defaultOpacity)
                    .put("sizePressureEnabled", brush.sizePressureEnabled)
                    .put("sizePressureMinimum", brush.sizePressureMinimum)
                    .put("sizePressureCurve", curveJson(brush.sizePressureCurve))
                    .put("sizeSecondaryDynamics", secondaryJson(brush.sizeSecondaryDynamics))
                    .put("opacityPressureEnabled", brush.opacityPressureEnabled)
                    .put("opacityPressureMinimum", brush.opacityPressureMinimum)
                    .put("opacityPressureCurve", curveJson(brush.opacityPressureCurve))
                    .put("opacitySecondaryDynamics", secondaryJson(brush.opacitySecondaryDynamics))
                    .put("flowPressureEnabled", brush.flowPressureEnabled)
                    .put("flowPressureMinimum", brush.flowPressureMinimum)
                    .put("flowPressureCurve", curveJson(brush.flowPressureCurve))
                    .put("flowSecondaryDynamics", secondaryJson(brush.flowSecondaryDynamics))
                    .put("spacingPressureEnabled", brush.spacingPressureEnabled)
                    .put("spacingPressureMinimum", brush.spacingPressureMinimum)
                    .put("spacingPressureCurve", curveJson(brush.spacingPressureCurve))
                    .put("spacingSecondaryDynamics", secondaryJson(brush.spacingSecondaryDynamics))
                    .put("thicknessPressureEnabled", brush.thicknessPressureEnabled)
                    .put("thicknessPressureMinimum", brush.thicknessPressureMinimum)
                    .put("thicknessPressureCurve", curveJson(brush.thicknessPressureCurve))
                    .put("thicknessSecondaryDynamics", secondaryJson(brush.thicknessSecondaryDynamics))
                    .put("blurPressureEnabled", brush.blurPressureEnabled)
                    .put("blurPressureMinimum", brush.blurPressureMinimum)
                    .put("blurPressureCurve", curveJson(brush.blurPressureCurve))
                    .put("blurSecondaryDynamics", secondaryJson(brush.blurSecondaryDynamics))
                    .put("parserVersion", brush.parserVersion)
                    .put("semanticMappings", semanticMappingsJson(brush.semanticMappings)),
            )
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private companion object {
        const val DEFAULT_PREFERENCES = "inkloom_brushes"
        const val DEFAULT_KEY = "imported_brushes"
    }
    private fun curveJson(points: List<BrushCurvePoint>): JSONArray = JSONArray().apply { points.forEach { put(JSONArray().put(it.x).put(it.y)) } }
    private fun secondaryJson(value: BrushSecondaryDynamics): JSONObject = JSONObject()
        .put("tiltEnabled", value.tiltEnabled)
        .put("tiltMinimum", value.tiltMinimum)
        .put("velocityEnabled", value.velocityEnabled)
        .put("velocityMinimum", value.velocityMinimum)
        .put("velocityScale", value.velocityScale)
        .put("velocityCurve", curveJson(value.velocityCurve))
        .put("randomEnabled", value.randomEnabled)
        .put("randomMinimum", value.randomMinimum)
    private fun colorDynamicsJson(value: BrushColorDynamics): JSONObject = JSONObject()
        .put("pressureEnabled", value.pressureEnabled).put("pressureMinimum", value.pressureMinimum).put("pressureCurve", curveJson(value.pressureCurve))
        .put("tiltEnabled", value.tiltEnabled).put("tiltMinimum", value.tiltMinimum)
        .put("velocityEnabled", value.velocityEnabled).put("velocityMinimum", value.velocityMinimum).put("velocityScale", value.velocityScale).put("velocityCurve", curveJson(value.velocityCurve))
        .put("randomEnabled", value.randomEnabled).put("randomMinimum", value.randomMinimum)
    private fun parameterDynamicsJson(value: BrushParameterDynamics): JSONObject = JSONObject()
        .put("pressureEnabled", value.pressureEnabled)
        .put("pressureMinimum", value.pressureMinimum)
        .put("pressureCurve", curveJson(value.pressureCurve))
        .put("secondary", secondaryJson(value.secondary))
    private fun semanticMappingsJson(value: List<BrushSemanticMapping>): JSONArray = JSONArray().apply {
        value.forEach { mapping ->
            put(
                JSONObject()
                    .put("sourceSemantic", mapping.sourceSemantic)
                    .put("targetRuntime", mapping.targetRuntime)
                    .put("fidelity", mapping.fidelity.name)
                    .put("note", mapping.note),
            )
        }
    }
}
