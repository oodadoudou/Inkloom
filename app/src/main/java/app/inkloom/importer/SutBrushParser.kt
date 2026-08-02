package app.inkloom.importer

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.document.BrushSecondaryDynamics
import java.io.File

class SutBrushParser(private val assetDirectory: File) {
    init { assetDirectory.mkdirs() }

    fun enrich(source: File, hash: String, fallback: ImportedBrush): ImportedBrush = runCatching {
        val database = SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        database.use { db ->
            val columns = db.rawQuery("PRAGMA table_info(Variant)", null).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) } }
            val desired = listOf("Opacity", "BrushSize", "BrushSizeUnit", "FlickerReduction", "FlickerReductionBySpeed", "FlickerReductionBySpeedType", "BrushFlow", "BrushHardness", "BrushInterval", "BrushThickness", "BrushVerticalThicknes", "BrushRotation", "BrushRotationEffector", "BrushRotationRandomScale", "BrushPatternOrderType", "BrushRibbon", "TextureDensity", "TextureReverseDensity", "TextureStressDensity", "TextureScale2", "TextureRotate", "TextureBrightness", "TextureContrast", "BrushUseWaterColor", "BrushMixColor", "BrushMixAlpha", "BrushUseSpray", "BrushSpraySize", "BrushSpraySizeUnit", "BrushSpraySizeSyncBrushSize", "BrushSprayDensity", "BrushSprayBias", "BrushRotationInSpray", "BrushRotationEffectorInSpray", "BrushRotationRandomInSpray", "UseDualBrush", "DualBrushCompositeMode", "SyncDualBrushSize", "DualSize", "DualFlow", "DualHardness", "DualInterval", "DualThickness", "DualVerticalThicknes", "DualRotation", "DualRotationEffector", "DualRotationRandomScale", "DualRibbon", "DualUseSpray", "DualSpraySize", "DualSpraySizeUnit", "DualSpraySizeSyncBrushSize", "DualSprayDensity", "DualSprayBias", "DualRotationInSpray", "DualRotationEffectorInSpray", "DualRotationRandomInSpray", "DualPatternOrderType", "BrushInOutType", "BrushUseIn", "BrushInLength", "BrushInLengthUnit", "BrushInRatio", "BrushUseOut", "BrushOutLength", "BrushOutLengthUnit", "BrushOutRatio", "BrushUseWaterEdge", "BrushWaterEdgeRadius", "BrushWaterEdgeRadiusUnit", "BrushWaterEdgeAlphaPower", "BrushWaterEdgeValuePower", "BrushWaterEdgeBlur", "BrushWaterEdgeBlurUnit").filter(columns::contains)
            val values = db.rawQuery("SELECT n.NodeName, ${desired.joinToString { "v.$it" }} FROM Node n JOIN Variant v ON v.VariantID=n.NodeVariantID LIMIT 1", null).use { cursor ->
                check(cursor.moveToFirst()) { "SUT has no active brush variant" }
                desired.associateWith { name -> cursor.number(name) } + ("NodeName" to cursor.getString(cursor.getColumnIndexOrThrow("NodeName")))
            }
            val sizeEffector = db.effector("BrushSizeEffector", columns)
            val opacityEffector = db.effector("BrushOpacityEffector", columns)
            val flowEffector = db.effector("BrushFlowEffector", columns)
            val spacingEffector = db.effector("BrushIntervalEffector", columns)
            val thicknessEffector = db.effector("BrushThicknessEffector", columns)
            val tips = extractMaterialPreviews(db, "BrushPatternImageArray", hash, "tip")
            val dualTips = extractMaterialPreviews(db, "DualPatternImageArray", hash, "dual-tip")
            val tip = tips.firstOrNull()
            val grain = extractMaterialPreviews(db, "TextureImage", hash, "grain").firstOrNull()
            val watercolor = values.float("BrushUseWaterColor") > 0f
            val dual = values.float("UseDualBrush") > 0f
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
                grainScale = values.float("TextureScale2", 100f).coerceIn(1f, 4000f),
                grainRotationDegrees = values.float("TextureRotate").coerceIn(-360f, 360f),
                grainBrightness = values.float("TextureBrightness").coerceIn(-100f, 100f),
                grainContrast = values.float("TextureContrast").coerceIn(-100f, 100f),
                grainReversed = values.float("TextureReverseDensity") > 0f,
                grainEmphasized = values.float("TextureStressDensity") > 0f,
                defaultSize = SutUnitConverter.referencePixels(values.float("BrushSize", 8f), values.float("BrushSizeUnit").toInt()),
                stabilization = (values.float("FlickerReduction") / 100f).coerceIn(0f, .9f),
                stabilizationBySpeed = values.float("FlickerReductionBySpeed") > 0f && values.float("FlickerReduction") in .01f..<30f,
                stabilizationBySpeedMode = values.float("FlickerReductionBySpeedType", 1f).toInt().coerceIn(0, 1),
                defaultOpacity = (values.float("Opacity", 100f) / 100f).coerceIn(.01f, 1f),
                spacing = (values.float("BrushInterval", 12f) / 100f).coerceIn(.01f, 2f),
                flow = (values.float("BrushFlow", 100f) / 100f).coerceIn(0f, 1f),
                hardness = (values.float("BrushHardness", 100f) / 100f).coerceIn(0f, 1f),
                thickness = (values.float("BrushThickness", 100f) / 100f).coerceIn(.05f, 2f),
                thicknessVertical = values.float("BrushVerticalThicknes").toInt() != 0,
                rotationDegrees = values.float("BrushRotation"),
                followStylusOrientation = rotation.mode == SutRotationMode.DIRECTION_OF_PEN,
                followStrokeDirection = rotation.mode == SutRotationMode.DIRECTION_OF_LINE,
                randomRotationDegrees = if (rotation.randomEnabled) (values.float("BrushRotationRandomScale") / 100f * 180f).coerceIn(0f, 180f) else 0f,
                watercolor = watercolor,
                colorMix = (values.float("BrushMixColor") / 100f).coerceIn(0f, 1f),
                paintMix = (values.float("BrushMixAlpha") / 100f).coerceIn(0f, 1f),
                spray = values.float("BrushUseSpray") > 0f,
                sprayParticleSize = values.float("BrushSpraySize", 100f).coerceIn(.01f, 4000f),
                sprayParticleSizeUnit = values.float("BrushSpraySizeUnit").toInt(),
                sprayParticleSizeSyncBrushSize = values.float("BrushSpraySizeSyncBrushSize", 1f) > 0f,
                sprayDensity = values.float("BrushSprayDensity", 1f).coerceIn(1f, 100f),
                sprayBias = values.float("BrushSprayBias").coerceIn(-100f, 100f),
                sprayParticleRotationDegrees = values.float("BrushRotationInSpray").coerceIn(-360f, 360f),
                sprayParticleFollowStylusOrientation = sprayRotation.mode == SutRotationMode.DIRECTION_OF_PEN,
                sprayParticleFollowStrokeDirection = sprayRotation.mode == SutRotationMode.DIRECTION_OF_LINE,
                sprayParticleRandomRotationDegrees = if (sprayRotation.randomEnabled) (values.float("BrushRotationRandomInSpray") / 100f * 180f).coerceIn(0f, 180f) else 0f,
                ribbon = values.float("BrushRibbon") > 0f,
                tipSequenceMode = values.float("BrushPatternOrderType").toInt().coerceIn(0, 5),
                dualBrush = dual,
                dualSizeScale = if (values.float("SyncDualBrushSize") > 0f) (values.float("DualSize", 100f) / 100f).coerceIn(.01f, 8f) else (values.float("DualSize", values.float("BrushSize", 8f)) / values.float("BrushSize", 8f).coerceAtLeast(.5f)).coerceIn(.01f, 8f),
                dualSpacing = (values.float("DualInterval", 12f) / 100f).coerceIn(.01f, 2f),
                dualFlow = (values.float("DualFlow", 100f) / 100f).coerceIn(0f, 1f),
                dualHardness = (values.float("DualHardness", 100f) / 100f).coerceIn(0f, 1f),
                dualThickness = (values.float("DualThickness", 100f) / 100f).coerceIn(.05f, 2f),
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
                dualSprayDensity = values.float("DualSprayDensity", 1f).coerceIn(1f, 100f),
                dualSprayBias = values.float("DualSprayBias").coerceIn(-100f, 100f),
                dualSprayParticleRotationDegrees = values.float("DualRotationInSpray").coerceIn(-360f, 360f),
                dualSprayParticleFollowStylusOrientation = dualSprayRotation.mode == SutRotationMode.DIRECTION_OF_PEN,
                dualSprayParticleFollowStrokeDirection = dualSprayRotation.mode == SutRotationMode.DIRECTION_OF_LINE,
                dualSprayParticleRandomRotationDegrees = if (dualSprayRotation.randomEnabled) (values.float("DualRotationRandomInSpray") / 100f * 180f).coerceIn(0f, 180f) else 0f,
                dualTipSequenceMode = values.float("DualPatternOrderType").toInt().coerceIn(0, 5),
                dualCompositeMode = values.float("DualBrushCompositeMode").toInt(),
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
                parserVersion = CURRENT_VERSION,
                report = buildList {
                    add("CSP SQLite variant and tool name decoded")
                    add(if (tips.isNotEmpty()) "${tips.size} CSP brush-tip material preview${if (tips.size == 1) "" else "s"} extracted in source order; transparent preview padding removed before stamping" else "No embedded brush-tip thumbnail was available")
                    if (tips.isNotEmpty()) add("The protected CSP C2F material layer is retained inside the original SUT; Inkloom uses its normalized preview raster until that proprietary layer encoding can be decoded safely")
                    if (grain != null) add("Referenced CSP paper texture mapped at ${values.float("TextureDensity").toInt()}% density, ${values.float("TextureScale2", 100f)}% scale, ${values.float("TextureRotate")}° rotation, brightness ${values.float("TextureBrightness").toInt()}, contrast ${values.float("TextureContrast").toInt()}, invert ${values.float("TextureReverseDensity") > 0f}, emphasize ${values.float("TextureStressDensity") > 0f}")
                    add("Size${if (values.float("BrushSizeUnit").toInt() == SutUnitConverter.MILLIMETERS) " converted from mm to the 1600 px reference page" else ""}, opacity, flow, hardness, spacing, thickness and rotation mapped")
                    if (values.float("FlickerReduction") > 0f) add("CSP stabilization ${values.float("FlickerReduction").toInt()} mapped${if (values.float("FlickerReductionBySpeed") > 0f && values.float("FlickerReduction") < 30f) " with speed adjustment mode ${values.float("FlickerReductionBySpeedType", 1f).toInt()}" else ""}")
                    add(when (rotation.mode) { SutRotationMode.FIXED -> "CSP tip angle mode mapped: fixed"; SutRotationMode.DIRECTION_OF_PEN -> "CSP tip angle mode mapped: direction of pen"; SutRotationMode.DIRECTION_OF_LINE -> "CSP tip angle mode mapped: direction of line"; SutRotationMode.PEN_TWIST -> "CSP tip angle uses pen-barrel twist, which Android MotionEvent does not expose on this engine"; SutRotationMode.UNKNOWN -> "Unknown CSP tip angle mode ${rotation.rawMode}; fixed angle retained" })
                    if (rotation.randomEnabled) add("CSP tip random-angle source mapped at ${values.float("BrushRotationRandomScale").toInt()}% influence")
                    if (rotation.unsupportedDynamicsFlags != 0) add("Additional CSP angle dynamics flags 0x${rotation.unsupportedDynamicsFlags.toString(16)} retained in the source; their combined strengths are not guessed")
                    val decodedCurves = listOfNotNull(sizeEffector?.pressureCurve?.takeIf { sizeEffector.pressureEnabled }?.let { "size ${it.size}-point" }, opacityEffector?.pressureCurve?.takeIf { opacityEffector.pressureEnabled }?.let { "opacity ${it.size}-point" }, flowEffector?.pressureCurve?.takeIf { flowEffector.pressureEnabled }?.let { "flow ${it.size}-point" }, spacingEffector?.pressureCurve?.takeIf { spacingEffector.pressureEnabled }?.let { "spacing ${it.size}-point" }, thicknessEffector?.pressureCurve?.takeIf { thicknessEffector.pressureEnabled }?.let { "thickness ${it.size}-point" })
                    if (decodedCurves.isNotEmpty()) add("CSP pressure dynamics decoded: ${decodedCurves.joinToString()}")
                    val secondarySources = listOf("size" to sizeEffector, "opacity" to opacityEffector, "flow" to flowEffector, "spacing" to spacingEffector, "thickness" to thicknessEffector).mapNotNull { (label, effector) -> effector?.sourceSummary()?.takeIf(String::isNotBlank)?.let { "$label $it" } }
                    if (secondarySources.isNotEmpty()) add("CSP secondary dynamics decoded: ${secondarySources.joinToString()}")
                    if (watercolor) add("Watercolor/color-mixing parameters detected; wet pigment simulation is approximated")
                    if (dual) add(if (dualTips.isNotEmpty()) "CSP Dual Brush mapped with ${dualTips.size} referenced secondary material preview(s), independent size/flow/spacing/hardness/thickness/rotation and retained composite mode ${values.float("DualBrushCompositeMode").toInt()}" else "Dual Brush detected, but no referenced secondary-tip thumbnail could be extracted; main-tip fallback retained")
                    if (values.float("BrushUseSpray") > 0f) add("Spray behavior mapped with particle size/unit, brush-size linking, density, center bias and independent particle direction${if (sprayRotation.randomEnabled) "/random angle" else ""}")
                    if (values.float("DualUseSpray") > 0f) add("Dual spray mapped with particle size/unit, brush-size linking, density, center bias and independent particle direction${if (dualSprayRotation.randomEnabled) "/random angle" else ""}")
                    if (values.float("BrushRibbon") > 0f) add("CSP Ribbon detected; source aspect ratio, tangent alignment and continuous placement enabled")
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
        val materials = database.rawQuery("SELECT OriginalPath, FileData FROM MaterialFile WHERE FileData IS NOT NULL", null).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0).orEmpty() to cursor.getBlob(1)) } }
        return materials.mapNotNull { (path, tar) ->
            val position = listOf(path.encodeToByteArray(), path.toByteArray(Charsets.UTF_16BE), path.toByteArray(Charsets.UTF_16LE)).map { encoded -> reference.indexOf(encoded) }.filter { it >= 0 }.minOrNull() ?: return@mapNotNull null
            val png = tarEntry(tar, "thumbnail/thumbnail.png") ?: return@mapNotNull null
            Triple(position, path, SutMaterialThumbnailNormalizer.normalize(png).png)
        }.distinctBy { it.second }.sortedBy { it.first }.mapIndexed { index, (_, _, png) ->
            val numberedSuffix = if (index == 0) suffix else "$suffix-${index + 1}"
            File(assetDirectory, "$hash-sut-v$CURRENT_VERSION-$numberedSuffix.png").also { target -> if (!target.exists()) target.writeBytes(png) }
        }
    }

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
    private fun SutEffector?.secondaryDynamics(): BrushSecondaryDynamics = this?.let { effector -> BrushSecondaryDynamics(tiltEnabled = effector.tiltEnabled, tiltMinimum = effector.tiltMinimum, velocityEnabled = effector.velocityEnabled, velocityMinimum = effector.velocityMinimum, velocityCurve = effector.velocityCurve, randomEnabled = effector.randomEnabled, randomMinimum = effector.randomMinimum) } ?: BrushSecondaryDynamics()
    private fun SutEffector.sourceSummary(): String = buildList { if (tiltEnabled) add("tilt"); if (velocityEnabled) add("velocity"); if (randomEnabled) add("random") }.joinToString("+")
    companion object {
        const val CURRENT_VERSION = 15
    }
}
