package app.inkloom.importer

import android.content.Context
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.document.BrushCurvePoint
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
                        velocityCurve = velocityCurve,
                        randomEnabled = value.optBoolean("randomEnabled", false),
                        randomMinimum = value.optDouble("randomMinimum", 0.0).toFloat(),
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
                        grainScale = item.optDouble("grainScale", 100.0).toFloat().coerceIn(1f, 4000f),
                        grainRotationDegrees = item.optDouble("grainRotationDegrees", 0.0).toFloat().coerceIn(-360f, 360f),
                        grainBrightness = item.optDouble("grainBrightness", 0.0).toFloat().coerceIn(-100f, 100f),
                        grainContrast = item.optDouble("grainContrast", 0.0).toFloat().coerceIn(-100f, 100f),
                        grainReversed = item.optBoolean("grainReversed", false),
                        grainEmphasized = item.optBoolean("grainEmphasized", false),
                        defaultSize = item.optDouble("defaultSize", 8.0).toFloat(),
                        stabilization = item.optDouble("stabilization", 0.0).toFloat().coerceIn(0f, .9f),
                        stabilizationBySpeed = item.optBoolean("stabilizationBySpeed", false),
                        stabilizationBySpeedMode = item.optInt("stabilizationBySpeedMode", 1).coerceIn(0, 1),
                        spacing = item.optDouble("spacing", .12).toFloat(),
                        flow = item.optDouble("flow", 1.0).toFloat(),
                        hardness = item.optDouble("hardness", 1.0).toFloat(),
                        thickness = item.optDouble("thickness", 1.0).toFloat(),
                        thicknessVertical = item.optBoolean("thicknessVertical", false),
                        rotationDegrees = item.optDouble("rotationDegrees", 0.0).toFloat(),
                        followStylusOrientation = item.optBoolean("followStylusOrientation", false),
                        followStrokeDirection = item.optBoolean("followStrokeDirection", false),
                        randomRotationDegrees = item.optDouble("randomRotationDegrees", 0.0).toFloat(),
                        watercolor = item.optBoolean("watercolor", false),
                        colorMix = item.optDouble("colorMix", 0.0).toFloat(),
                        paintMix = item.optDouble("paintMix", 0.0).toFloat(),
                        spray = item.optBoolean("spray", false),
                        sprayParticleSize = item.optDouble("sprayParticleSize", 100.0).toFloat().coerceIn(.01f, 4000f),
                        sprayParticleSizeUnit = item.optInt("sprayParticleSizeUnit", 0),
                        sprayParticleSizeSyncBrushSize = item.optBoolean("sprayParticleSizeSyncBrushSize", true),
                        sprayDensity = item.optDouble("sprayDensity", 1.0).toFloat().coerceIn(1f, 100f),
                        sprayBias = item.optDouble("sprayBias", 0.0).toFloat().coerceIn(-100f, 100f),
                        sprayParticleRotationDegrees = item.optDouble("sprayParticleRotationDegrees", 0.0).toFloat().coerceIn(-360f, 360f),
                        sprayParticleFollowStylusOrientation = item.optBoolean("sprayParticleFollowStylusOrientation", false),
                        sprayParticleFollowStrokeDirection = item.optBoolean("sprayParticleFollowStrokeDirection", false),
                        sprayParticleRandomRotationDegrees = item.optDouble("sprayParticleRandomRotationDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        ribbon = item.optBoolean("ribbon", false),
                        tipSequenceMode = item.optInt("tipSequenceMode", 0).coerceIn(0, 5),
                        dualBrush = item.optBoolean("dualBrush", false),
                        dualSizeScale = item.optDouble("dualSizeScale", 1.0).toFloat().coerceIn(.01f, 8f),
                        dualSpacing = item.optDouble("dualSpacing", .12).toFloat().coerceIn(.01f, 2f),
                        dualFlow = item.optDouble("dualFlow", 1.0).toFloat().coerceIn(0f, 1f),
                        dualHardness = item.optDouble("dualHardness", 1.0).toFloat().coerceIn(0f, 1f),
                        dualThickness = item.optDouble("dualThickness", 1.0).toFloat().coerceIn(.05f, 2f),
                        dualThicknessVertical = item.optBoolean("dualThicknessVertical", false),
                        dualRotationDegrees = item.optDouble("dualRotationDegrees", 0.0).toFloat(),
                        dualFollowStylusOrientation = item.optBoolean("dualFollowStylusOrientation", false),
                        dualFollowStrokeDirection = item.optBoolean("dualFollowStrokeDirection", false),
                        dualRandomRotationDegrees = item.optDouble("dualRandomRotationDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        dualRibbon = item.optBoolean("dualRibbon", false),
                        dualSpray = item.optBoolean("dualSpray", false),
                        dualSprayParticleSize = item.optDouble("dualSprayParticleSize", 100.0).toFloat().coerceIn(.01f, 4000f),
                        dualSprayParticleSizeUnit = item.optInt("dualSprayParticleSizeUnit", 0),
                        dualSprayParticleSizeSyncBrushSize = item.optBoolean("dualSprayParticleSizeSyncBrushSize", true),
                        dualSprayDensity = item.optDouble("dualSprayDensity", 1.0).toFloat().coerceIn(1f, 100f),
                        dualSprayBias = item.optDouble("dualSprayBias", 0.0).toFloat().coerceIn(-100f, 100f),
                        dualSprayParticleRotationDegrees = item.optDouble("dualSprayParticleRotationDegrees", 0.0).toFloat().coerceIn(-360f, 360f),
                        dualSprayParticleFollowStylusOrientation = item.optBoolean("dualSprayParticleFollowStylusOrientation", false),
                        dualSprayParticleFollowStrokeDirection = item.optBoolean("dualSprayParticleFollowStrokeDirection", false),
                        dualSprayParticleRandomRotationDegrees = item.optDouble("dualSprayParticleRandomRotationDegrees", 0.0).toFloat().coerceIn(0f, 180f),
                        dualTipSequenceMode = item.optInt("dualTipSequenceMode", 0).coerceIn(0, 5),
                        dualCompositeMode = item.optInt("dualCompositeMode", 0),
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
                        groupName = BrushGroupOperations.normalize(item.optString("groupName", "")),
                        tags = buildList { for (tagIndex in 0 until tagsArray.length()) add(tagsArray.getString(tagIndex)) },
                        lastUsedAt = item.optLong("lastUsedAt", 0L),
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
                        parserVersion = item.optInt("parserVersion", 0),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(brushes: List<ImportedBrush>) {
        val array = JSONArray()
        brushes.forEach { brush ->
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
                    .put("grainScale", brush.grainScale)
                    .put("grainRotationDegrees", brush.grainRotationDegrees)
                    .put("grainBrightness", brush.grainBrightness)
                    .put("grainContrast", brush.grainContrast)
                    .put("grainReversed", brush.grainReversed)
                    .put("grainEmphasized", brush.grainEmphasized)
                    .put("defaultSize", brush.defaultSize)
                    .put("stabilization", brush.stabilization)
                    .put("stabilizationBySpeed", brush.stabilizationBySpeed)
                    .put("stabilizationBySpeedMode", brush.stabilizationBySpeedMode)
                    .put("spacing", brush.spacing)
                    .put("flow", brush.flow)
                    .put("hardness", brush.hardness)
                    .put("thickness", brush.thickness)
                    .put("thicknessVertical", brush.thicknessVertical)
                    .put("rotationDegrees", brush.rotationDegrees)
                    .put("followStylusOrientation", brush.followStylusOrientation)
                    .put("followStrokeDirection", brush.followStrokeDirection)
                    .put("randomRotationDegrees", brush.randomRotationDegrees)
                    .put("watercolor", brush.watercolor)
                    .put("colorMix", brush.colorMix)
                    .put("paintMix", brush.paintMix)
                    .put("spray", brush.spray)
                    .put("sprayParticleSize", brush.sprayParticleSize)
                    .put("sprayParticleSizeUnit", brush.sprayParticleSizeUnit)
                    .put("sprayParticleSizeSyncBrushSize", brush.sprayParticleSizeSyncBrushSize)
                    .put("sprayDensity", brush.sprayDensity)
                    .put("sprayBias", brush.sprayBias)
                    .put("sprayParticleRotationDegrees", brush.sprayParticleRotationDegrees)
                    .put("sprayParticleFollowStylusOrientation", brush.sprayParticleFollowStylusOrientation)
                    .put("sprayParticleFollowStrokeDirection", brush.sprayParticleFollowStrokeDirection)
                    .put("sprayParticleRandomRotationDegrees", brush.sprayParticleRandomRotationDegrees)
                    .put("ribbon", brush.ribbon)
                    .put("tipSequenceMode", brush.tipSequenceMode)
                    .put("dualBrush", brush.dualBrush)
                    .put("dualSizeScale", brush.dualSizeScale)
                    .put("dualSpacing", brush.dualSpacing)
                    .put("dualFlow", brush.dualFlow)
                    .put("dualHardness", brush.dualHardness)
                    .put("dualThickness", brush.dualThickness)
                    .put("dualThicknessVertical", brush.dualThicknessVertical)
                    .put("dualRotationDegrees", brush.dualRotationDegrees)
                    .put("dualFollowStylusOrientation", brush.dualFollowStylusOrientation)
                    .put("dualFollowStrokeDirection", brush.dualFollowStrokeDirection)
                    .put("dualRandomRotationDegrees", brush.dualRandomRotationDegrees)
                    .put("dualRibbon", brush.dualRibbon)
                    .put("dualSpray", brush.dualSpray)
                    .put("dualSprayParticleSize", brush.dualSprayParticleSize)
                    .put("dualSprayParticleSizeUnit", brush.dualSprayParticleSizeUnit)
                    .put("dualSprayParticleSizeSyncBrushSize", brush.dualSprayParticleSizeSyncBrushSize)
                    .put("dualSprayDensity", brush.dualSprayDensity)
                    .put("dualSprayBias", brush.dualSprayBias)
                    .put("dualSprayParticleRotationDegrees", brush.dualSprayParticleRotationDegrees)
                    .put("dualSprayParticleFollowStylusOrientation", brush.dualSprayParticleFollowStylusOrientation)
                    .put("dualSprayParticleFollowStrokeDirection", brush.dualSprayParticleFollowStrokeDirection)
                    .put("dualSprayParticleRandomRotationDegrees", brush.dualSprayParticleRandomRotationDegrees)
                    .put("dualTipSequenceMode", brush.dualTipSequenceMode)
                    .put("dualCompositeMode", brush.dualCompositeMode)
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
                    .put("groupName", BrushGroupOperations.normalize(brush.groupName))
                    .put("tags", JSONArray(brush.tags))
                    .put("lastUsedAt", brush.lastUsedAt)
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
                    .put("parserVersion", brush.parserVersion),
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
        .put("velocityCurve", curveJson(value.velocityCurve))
        .put("randomEnabled", value.randomEnabled)
        .put("randomMinimum", value.randomMinimum)
}
