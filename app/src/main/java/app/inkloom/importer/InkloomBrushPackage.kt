package app.inkloom.importer

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushColorDynamics
import app.inkloom.document.BrushParameterDynamics
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushTaperMode
import app.inkloom.document.ProcreateColorDynamics
import app.inkloom.document.ProcreateFiltering
import app.inkloom.document.CspAntiAlias
import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class InkloomBrushManifest(
    val schemaVersion: Int,
    val brush: ImportedBrush,
)

class InkloomBrushPackage(private val assetDirectory: File) {
    init { assetDirectory.mkdirs() }

    fun write(brush: ImportedBrush, output: OutputStream) {
        val sourcePaths = linkedSetOf<String>().apply {
            brush.tipPreviewPath?.let(::add)
            addAll(brush.tipPreviewPaths)
            addAll(brush.dualTipPreviewPaths)
            brush.grainPreviewPath?.let(::add)
        }.filter(String::isNotBlank)
        var expandedBytes = 0L
        val assets = sourcePaths.mapIndexed { index, path ->
            val file = File(path)
            require(file.isFile) { "Brush asset is missing: ${file.name}" }
            require(file.length() in 1..MAX_ASSET_BYTES) { "Brush asset exceeds the safety limit" }
            expandedBytes += file.length()
            require(expandedBytes <= MAX_EXPANDED_BYTES) { "Brush assets exceed the package safety limit" }
            val extension = file.extension.lowercase().takeIf { it in IMAGE_EXTENSIONS } ?: "bin"
            file.canonicalPath to ("assets/${(index + 1).toString().padStart(3, '0')}.$extension" to file)
        }.toMap()
        fun portablePath(path: String?): String? = path?.takeIf(String::isNotBlank)?.let { assets[File(it).canonicalPath]?.first }
        val portable = sanitize(
            brush.copy(
                format = BrushFormat.INKLOOM,
                compatibility = Compatibility.COMPLETE,
                sourcePath = null,
                sha256 = null,
                tipPreviewPath = portablePath(brush.tipPreviewPath),
                tipPreviewPaths = brush.tipPreviewPaths.mapNotNull(::portablePath),
                dualTipPreviewPaths = brush.dualTipPreviewPaths.mapNotNull(::portablePath),
                grainPreviewPath = portablePath(brush.grainPreviewPath),
                favorite = false,
                hidden = false,
                lastUsedAt = 0L,
                parserVersion = CURRENT_VERSION,
                report = (brush.report + "Portable Inkloom preset schema $CURRENT_VERSION").distinct(),
            ),
        )
        val manifest = JSON.encodeToString(InkloomBrushManifest(CURRENT_VERSION, portable)).encodeToByteArray()
        require(manifest.size <= MAX_MANIFEST_BYTES) { "Brush manifest exceeds the safety limit" }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_PATH))
            zip.write(manifest)
            zip.closeEntry()
            assets.values.forEach { (path, file) ->
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun read(source: File, hash: String, fallback: ImportedBrush): ImportedBrush = runCatching {
        ZipFile(source).use { zip ->
            val entries = zip.entries().asSequence().toList()
            validate(entries)
            val byName = entries.associateBy(ZipEntry::getName)
            val manifestEntry = byName[MANIFEST_PATH] ?: error("Inkloom brush manifest is missing")
            val manifest = JSON.decodeFromString<InkloomBrushManifest>(readBounded(zip, manifestEntry, MAX_MANIFEST_BYTES).decodeToString())
            require(manifest.schemaVersion == CURRENT_VERSION) { "Unsupported Inkloom brush schema ${manifest.schemaVersion}" }
            val referenced = linkedSetOf<String>().apply {
                manifest.brush.tipPreviewPath?.let(::add)
                addAll(manifest.brush.tipPreviewPaths)
                addAll(manifest.brush.dualTipPreviewPaths)
                manifest.brush.grainPreviewPath?.let(::add)
            }
            referenced.forEach { path -> require(path.startsWith("assets/") && path.count { it == '/' } == 1 && byName[path] != null) { "Inkloom brush references an invalid asset" } }
            val outputDirectory = File(assetDirectory, hash).apply { mkdirs() }
            val materialized = referenced.sorted().mapIndexed { index, path ->
                val extension = path.substringAfterLast('.', "bin").lowercase().takeIf { it in IMAGE_EXTENSIONS } ?: "bin"
                path to extract(zip, byName.getValue(path), File(outputDirectory, "asset-${index + 1}.$extension"))
            }.toMap()
            fun local(path: String?): String? = path?.let { materialized[it]?.absolutePath }
            sanitize(
                manifest.brush.copy(
                    id = fallback.id,
                    format = BrushFormat.INKLOOM,
                    compatibility = Compatibility.COMPLETE,
                    sizeBytes = fallback.sizeBytes,
                    sourcePath = fallback.sourcePath,
                    sha256 = fallback.sha256,
                    tipPreviewPath = local(manifest.brush.tipPreviewPath),
                    tipPreviewPaths = manifest.brush.tipPreviewPaths.mapNotNull(::local),
                    dualTipPreviewPaths = manifest.brush.dualTipPreviewPaths.mapNotNull(::local),
                    grainPreviewPath = local(manifest.brush.grainPreviewPath),
                    favorite = false,
                    hidden = false,
                    lastUsedAt = 0L,
                    parserVersion = CURRENT_VERSION,
                    report = (manifest.brush.report + "Portable Inkloom preset verified and materialized from schema $CURRENT_VERSION").distinct(),
                ),
            )
        }
    }.getOrElse { failure ->
        fallback.copy(
            format = BrushFormat.INKLOOM,
            compatibility = Compatibility.FAILED,
            parserVersion = CURRENT_VERSION,
            report = (fallback.report + "Inkloom brush package rejected safely: ${failure.message ?: failure::class.java.simpleName}").distinct(),
        )
    }

    private fun validate(entries: List<ZipEntry>) {
        require(entries.size in 1..MAX_ENTRIES) { "Inkloom brush archive has too many entries" }
        require(entries.map(ZipEntry::getName).distinct().size == entries.size) { "Inkloom brush archive contains duplicate paths" }
        var expandedBytes = 0L
        entries.forEach { entry ->
            require(isSafePath(entry.name)) { "Inkloom brush archive contains an unsafe path" }
            require(entry.size in 0..MAX_ENTRY_BYTES) { "Inkloom brush entry is too large" }
            expandedBytes += entry.size
            require(expandedBytes <= MAX_EXPANDED_BYTES) { "Inkloom brush archive expands beyond the safety limit" }
            if (entry.compressedSize > 0 && entry.size > 1_048_576L) {
                require(entry.size / entry.compressedSize.coerceAtLeast(1L) <= MAX_COMPRESSION_RATIO) { "Inkloom brush archive has a suspicious compression ratio" }
            }
        }
    }

    private fun isSafePath(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && '\\' !in name && name.split('/').none { it == ".." || it.isBlank() }

    private fun extract(zip: ZipFile, entry: ZipEntry, target: File): File {
        if (target.isFile && target.length() == entry.size) return target
        require(entry.size in 1..MAX_ASSET_BYTES) { "Inkloom brush asset exceeds the safety limit" }
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.part")
        try {
            zip.getInputStream(entry).use { input ->
                temporary.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ASSET_BYTES) { "Inkloom brush asset exceeds the safety limit" }
                        output.write(buffer, 0, count)
                    }
                    require(total == entry.size) { "Inkloom brush asset is truncated" }
                }
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Could not retain Inkloom brush asset" }
            return target
        } finally {
            temporary.delete()
        }
    }

    private fun readBounded(zip: ZipFile, entry: ZipEntry, limit: Long): ByteArray {
        require(entry.size in 1..limit) { "Inkloom brush manifest exceeds the safety limit" }
        return zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream(entry.size.toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "Inkloom brush manifest exceeds the safety limit" }
                output.write(buffer, 0, count)
            }
            require(total == entry.size) { "Inkloom brush manifest is truncated" }
            output.toByteArray()
        }
    }

    private fun sanitize(source: ImportedBrush): ImportedBrush = source.copy(
        name = source.name.trim().ifBlank { "Imported Brush" }.take(120),
        report = source.report.map { it.take(500) }.distinct().take(64),
        groupName = BrushGroupOperations.normalize(source.groupName),
        tags = source.tags.map(String::trim).filter(String::isNotBlank).distinct().take(24),
        grainDensity = finite(source.grainDensity, 0f).coerceIn(0f, 1f),
        grainDensityMinimum = finite(source.grainDensityMinimum, 0f).coerceIn(0f, finite(source.grainDensity, 0f).coerceIn(0f, 1f)),
        grainDensityDynamics = parameterDynamics(source.grainDensityDynamics),
        grainScale = finite(source.grainScale, 100f).coerceIn(1f, 4000f),
        grainMovement = finite(source.grainMovement, 0f).coerceIn(0f, 1f),
        grainZoom = finite(source.grainZoom, 0f).coerceIn(0f, 1f),
        grainRotationDegrees = finite(source.grainRotationDegrees, 0f).coerceIn(-360f, 360f),
        grainStrokeRotation = finite(source.grainStrokeRotation, 0f).coerceIn(-1f, 1f),
        grainBrightness = finite(source.grainBrightness, 0f).coerceIn(-100f, 100f),
        grainContrast = finite(source.grainContrast, 0f).coerceIn(-100f, 100f),
        grainCompositeMode = source.grainCompositeMode.coerceIn(0, 64),
        grainFilteringMode = ProcreateFiltering.normalize(source.grainFilteringMode),
        defaultSize = finite(source.defaultSize, 8f).coerceIn(.5f, 400f),
        streamLine = finite(source.streamLine, 0f).coerceIn(0f, 1f),
        streamLinePressure = finite(source.streamLinePressure, 0f).coerceIn(0f, 1f),
        procreateStabilization = finite(source.procreateStabilization, 0f).coerceIn(0f, 1f),
        motionFiltering = finite(source.motionFiltering, 0f).coerceIn(0f, 1f),
        motionFilteringExpression = finite(source.motionFilteringExpression, 0f).coerceIn(0f, 1f),
        tiltGradation = finite(source.tiltGradation, 0f).coerceIn(0f, 1f),
        tiltGradationStart = finite(source.tiltGradationStart, 0f).coerceIn(0f, .99f),
        tiltBleed = finite(source.tiltBleed, 0f).coerceIn(0f, 1f),
        tiltBleedStart = finite(source.tiltBleedStart, 0f).coerceIn(0f, .99f),
        stabilization = finite(source.stabilization, 0f).coerceIn(0f, .9f),
        stabilizationBySpeedMode = source.stabilizationBySpeedMode.coerceIn(0, 1),
        spacing = finite(source.spacing, .12f).coerceIn(.01f, 2f),
        strokeFalloff = finite(source.strokeFalloff, 0f).coerceIn(0f, 1f),
        strokePathJitter = finite(source.strokePathJitter, 0f).coerceIn(0f, 4f),
        strokePathLongitudinalJitter = finite(source.strokePathLongitudinalJitter, 0f).coerceIn(0f, 4f),
        spacingMode = app.inkloom.document.CspBrushGap.normalize(source.spacingMode),
        flow = finite(source.flow, 1f).coerceIn(0f, 1f),
        hardness = finite(source.hardness, 1f).coerceIn(0f, 1f),
        antiAliasMode = CspAntiAlias.normalize(source.antiAliasMode),
        blur = finite(source.blur, 0f).coerceIn(0f, 400f),
        blurUnit = source.blurUnit.coerceIn(0, 2),
        thickness = finite(source.thickness, 1f).coerceIn(.05f, 2f),
        shapeRoundnessHorizontalJitter = finite(source.shapeRoundnessHorizontalJitter, 0f).coerceIn(0f, 1f),
        shapeRoundnessVerticalJitter = finite(source.shapeRoundnessVerticalJitter, 0f).coerceIn(0f, 1f),
        rotationDegrees = finite(source.rotationDegrees, 0f).coerceIn(-360f, 360f),
        randomRotationDegrees = finite(source.randomRotationDegrees, 0f).coerceIn(0f, 180f),
        colorJitterHueDegrees = finite(source.colorJitterHueDegrees, 0f).coerceIn(-180f, 180f),
        colorJitterSaturation = finite(source.colorJitterSaturation, 0f).coerceIn(-100f, 100f),
        colorJitterValue = finite(source.colorJitterValue, 0f).coerceIn(-100f, 100f),
        colorJitterTarget = source.colorJitterTarget.coerceIn(0, 3),
        colorJitterHueDynamics = colorDynamics(source.colorJitterHueDynamics),
        colorJitterSaturationDynamics = colorDynamics(source.colorJitterSaturationDynamics),
        colorJitterValueDynamics = colorDynamics(source.colorJitterValueDynamics),
        procreateColorDynamics = procreateColorDynamics(source.procreateColorDynamics),
        colorMix = finite(source.colorMix, 0f).coerceIn(0f, 1f),
        colorMixPressureMinimum = finite(source.colorMixPressureMinimum, 0f).coerceIn(0f, 1f),
        colorMixPressureCurve = curve(source.colorMixPressureCurve),
        colorMixSecondaryDynamics = secondary(source.colorMixSecondaryDynamics),
        paintMix = finite(source.paintMix, 0f).coerceIn(0f, 1f),
        paintMixPressureMinimum = finite(source.paintMixPressureMinimum, 0f).coerceIn(0f, 1f),
        paintMixPressureCurve = curve(source.paintMixPressureCurve),
        paintMixSecondaryDynamics = secondary(source.paintMixSecondaryDynamics),
        sprayParticleSize = finite(source.sprayParticleSize, 100f).coerceIn(.01f, 4000f),
        sprayParticleSizePressureMinimum = finite(source.sprayParticleSizePressureMinimum, 0f).coerceIn(0f, 1f),
        sprayParticleSizePressureCurve = curve(source.sprayParticleSizePressureCurve),
        sprayParticleSizeSecondaryDynamics = secondary(source.sprayParticleSizeSecondaryDynamics),
        sprayDensity = finite(source.sprayDensity, 1f).coerceIn(1f, 100f),
        sprayDensityPressureMinimum = finite(source.sprayDensityPressureMinimum, 0f).coerceIn(0f, 1f),
        sprayDensityPressureCurve = curve(source.sprayDensityPressureCurve),
        sprayDensitySecondaryDynamics = secondary(source.sprayDensitySecondaryDynamics),
        sprayBias = finite(source.sprayBias, 0f).coerceIn(-100f, 100f),
        spraySpread = finite(source.spraySpread, 1f).coerceIn(0f, 4f),
        sprayParticleRotationDegrees = finite(source.sprayParticleRotationDegrees, 0f).coerceIn(-360f, 360f),
        sprayParticleRandomRotationDegrees = finite(source.sprayParticleRandomRotationDegrees, 0f).coerceIn(0f, 180f),
        tipSequenceMode = source.tipSequenceMode.coerceIn(0, 5),
        tipFlipHorizontalMode = source.tipFlipHorizontalMode.coerceIn(0, 3),
        tipFlipVerticalMode = source.tipFlipVerticalMode.coerceIn(0, 3),
        blendTipsWithDarken = source.blendTipsWithDarken,
        dualSizeScale = finite(source.dualSizeScale, 1f).coerceIn(.01f, 8f),
        dualSizeDynamics = parameterDynamics(source.dualSizeDynamics),
        dualSpacing = finite(source.dualSpacing, .12f).coerceIn(.01f, 2f),
        dualSpacingMode = app.inkloom.document.CspBrushGap.normalize(source.dualSpacingMode),
        dualSpacingDynamics = parameterDynamics(source.dualSpacingDynamics),
        dualFlow = finite(source.dualFlow, 1f).coerceIn(0f, 1f),
        dualFlowDynamics = parameterDynamics(source.dualFlowDynamics),
        dualHardness = finite(source.dualHardness, 1f).coerceIn(0f, 1f),
        dualAntiAliasMode = CspAntiAlias.normalize(source.dualAntiAliasMode),
        dualThickness = finite(source.dualThickness, 1f).coerceIn(.05f, 2f),
        dualShapeRoundnessHorizontalJitter = finite(source.dualShapeRoundnessHorizontalJitter, 0f).coerceIn(0f, 1f),
        dualShapeRoundnessVerticalJitter = finite(source.dualShapeRoundnessVerticalJitter, 0f).coerceIn(0f, 1f),
        dualThicknessDynamics = parameterDynamics(source.dualThicknessDynamics),
        dualRotationDegrees = finite(source.dualRotationDegrees, 0f).coerceIn(-360f, 360f),
        dualRandomRotationDegrees = finite(source.dualRandomRotationDegrees, 0f).coerceIn(0f, 180f),
        dualStrokePathJitter = finite(source.dualStrokePathJitter, 0f).coerceIn(0f, 4f),
        dualStrokePathLongitudinalJitter = finite(source.dualStrokePathLongitudinalJitter, 0f).coerceIn(0f, 4f),
        dualSprayParticleSize = finite(source.dualSprayParticleSize, 100f).coerceIn(.01f, 4000f),
        dualSprayParticleSizeDynamics = parameterDynamics(source.dualSprayParticleSizeDynamics),
        dualSprayDensity = finite(source.dualSprayDensity, 1f).coerceIn(1f, 100f),
        dualSprayDensityDynamics = parameterDynamics(source.dualSprayDensityDynamics),
        dualSprayBias = finite(source.dualSprayBias, 0f).coerceIn(-100f, 100f),
        dualSpraySpread = finite(source.dualSpraySpread, 1f).coerceIn(0f, 4f),
        dualSprayParticleRotationDegrees = finite(source.dualSprayParticleRotationDegrees, 0f).coerceIn(-360f, 360f),
        dualSprayParticleRandomRotationDegrees = finite(source.dualSprayParticleRandomRotationDegrees, 0f).coerceIn(0f, 180f),
        dualTipSequenceMode = source.dualTipSequenceMode.coerceIn(0, 5),
        dualTipFlipHorizontalMode = source.dualTipFlipHorizontalMode.coerceIn(0, 3),
        dualTipFlipVerticalMode = source.dualTipFlipVerticalMode.coerceIn(0, 3),
        dualBlendTipsWithDarken = source.dualBlendTipsWithDarken,
        dualCompositeMode = source.dualCompositeMode.coerceIn(-1024, 1024),
        dualApplyRgb = source.dualApplyRgb,
        taperStartLength = taperLength(source.taperMode, source.taperStartLength, .2f),
        taperStartLengthUnit = source.taperStartLengthUnit.coerceIn(0, 2),
        taperStartMinimum = finite(source.taperStartMinimum, .3f).coerceIn(0f, 1f),
        taperEndLength = taperLength(source.taperMode, source.taperEndLength, .2f),
        taperEndLengthUnit = source.taperEndLengthUnit.coerceIn(0, 2),
        taperEndMinimum = finite(source.taperEndMinimum, .3f).coerceIn(0f, 1f),
        waterEdgeRadius = finite(source.waterEdgeRadius, 3f).coerceIn(0f, 400f),
        waterEdgeRadiusUnit = source.waterEdgeRadiusUnit.coerceIn(0, 2),
        waterEdgeOpacity = finite(source.waterEdgeOpacity, .2f).coerceIn(0f, 1f),
        waterEdgeDarkness = finite(source.waterEdgeDarkness, 0f).coerceIn(0f, 1f),
        waterEdgeBlur = finite(source.waterEdgeBlur, 0f).coerceIn(0f, 400f),
        waterEdgeBlurUnit = source.waterEdgeBlurUnit.coerceIn(0, 2),
        defaultOpacity = finite(source.defaultOpacity, 1f).coerceIn(.01f, 1f),
        sizePressureMinimum = finite(source.sizePressureMinimum, .35f).coerceIn(0f, 1f),
        sizePressureCurve = curve(source.sizePressureCurve),
        sizeSecondaryDynamics = secondary(source.sizeSecondaryDynamics),
        opacityPressureMinimum = finite(source.opacityPressureMinimum, 0f).coerceIn(0f, 1f),
        opacityPressureCurve = curve(source.opacityPressureCurve),
        opacitySecondaryDynamics = secondary(source.opacitySecondaryDynamics),
        flowPressureMinimum = finite(source.flowPressureMinimum, 0f).coerceIn(0f, 1f),
        flowPressureCurve = curve(source.flowPressureCurve),
        flowSecondaryDynamics = secondary(source.flowSecondaryDynamics),
        spacingPressureMinimum = finite(source.spacingPressureMinimum, 0f).coerceIn(0f, 1f),
        spacingPressureCurve = curve(source.spacingPressureCurve),
        spacingSecondaryDynamics = secondary(source.spacingSecondaryDynamics),
        thicknessPressureMinimum = finite(source.thicknessPressureMinimum, 0f).coerceIn(0f, 1f),
        thicknessPressureCurve = curve(source.thicknessPressureCurve),
        thicknessSecondaryDynamics = secondary(source.thicknessSecondaryDynamics),
        blurPressureMinimum = finite(source.blurPressureMinimum, 0f).coerceIn(0f, 1f),
        blurPressureCurve = curve(source.blurPressureCurve),
        blurSecondaryDynamics = secondary(source.blurSecondaryDynamics),
    )

    private fun curve(points: List<BrushCurvePoint>): List<BrushCurvePoint> = points
        .filter { it.x.isFinite() && it.y.isFinite() }
        .map { BrushCurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
        .distinctBy(BrushCurvePoint::x)
        .sortedBy(BrushCurvePoint::x)
        .take(32)

    private fun secondary(value: BrushSecondaryDynamics): BrushSecondaryDynamics = value.copy(
        tiltMinimum = finite(value.tiltMinimum, 0f).coerceIn(0f, 1f),
        tiltStart = finite(value.tiltStart, 0f).coerceIn(0f, .99f),
        tiltCurve = curve(value.tiltCurve),
        velocityMinimum = finite(value.velocityMinimum, 0f).coerceIn(0f, 1f),
        velocityScale = finite(value.velocityScale, 1f).coerceIn(0f, 10f),
        velocityCurve = curve(value.velocityCurve),
        randomMinimum = finite(value.randomMinimum, 0f).coerceIn(0f, 1f),
    )

    private fun parameterDynamics(value: BrushParameterDynamics): BrushParameterDynamics = value.copy(
        pressureMinimum = finite(value.pressureMinimum, 0f).coerceIn(0f, 1f),
        pressureCurve = curve(value.pressureCurve),
        secondary = secondary(value.secondary),
    )

    private fun colorDynamics(value: BrushColorDynamics): BrushColorDynamics = value.copy(
        pressureMinimum = finite(value.pressureMinimum, 0f).coerceIn(0f, 1f), pressureCurve = curve(value.pressureCurve),
        tiltMinimum = finite(value.tiltMinimum, 0f).coerceIn(0f, 1f), tiltStart = finite(value.tiltStart, 0f).coerceIn(0f, .99f), velocityMinimum = finite(value.velocityMinimum, 0f).coerceIn(0f, 1f), velocityScale = finite(value.velocityScale, 1f).coerceIn(0f, 10f), velocityCurve = curve(value.velocityCurve),
        randomMinimum = finite(value.randomMinimum, -1f).coerceIn(-1f, 1f),
    )

    private fun procreateColorDynamics(value: ProcreateColorDynamics): ProcreateColorDynamics = value.copy(
        stampHue = finite(value.stampHue, 0f).coerceIn(0f, 1f), stampSaturation = finite(value.stampSaturation, 0f).coerceIn(0f, 1f), stampLightness = finite(value.stampLightness, 0f).coerceIn(0f, 1f), stampDarkness = finite(value.stampDarkness, 0f).coerceIn(0f, 1f), stampSecondary = finite(value.stampSecondary, 0f).coerceIn(0f, 1f),
        strokeHue = finite(value.strokeHue, 0f).coerceIn(0f, 1f), strokeSaturation = finite(value.strokeSaturation, 0f).coerceIn(0f, 1f), strokeLightness = finite(value.strokeLightness, 0f).coerceIn(0f, 1f), strokeDarkness = finite(value.strokeDarkness, 0f).coerceIn(0f, 1f), strokeSecondary = finite(value.strokeSecondary, 0f).coerceIn(0f, 1f),
        pressureHue = finite(value.pressureHue, 0f).coerceIn(-1f, 1f), pressureHueCurve = curve(value.pressureHueCurve), pressureSaturation = finite(value.pressureSaturation, 0f).coerceIn(-1f, 1f), pressureSaturationCurve = curve(value.pressureSaturationCurve), pressureBrightness = finite(value.pressureBrightness, 0f).coerceIn(-1f, 1f), pressureBrightnessCurve = curve(value.pressureBrightnessCurve), pressureSecondary = finite(value.pressureSecondary, 0f).coerceIn(-1f, 1f), pressureSecondaryCurve = curve(value.pressureSecondaryCurve),
        tiltHue = finite(value.tiltHue, 0f).coerceIn(-1f, 1f), tiltSaturation = finite(value.tiltSaturation, 0f).coerceIn(-1f, 1f), tiltBrightness = finite(value.tiltBrightness, 0f).coerceIn(-1f, 1f), tiltSecondary = finite(value.tiltSecondary, 0f).coerceIn(-1f, 1f), tiltStart = finite(value.tiltStart, 0f).coerceIn(0f, .99f),
    )

    private fun taperLength(mode: BrushTaperMode, value: Float, fallback: Float): Float =
        if (mode == BrushTaperMode.DISTANCE) finite(value, fallback).coerceIn(.1f, 4000f) else finite(value, fallback).coerceIn(.001f, 1f)

    private fun finite(value: Float, fallback: Float): Float = value.takeIf(Float::isFinite) ?: fallback

    companion object {
        const val CURRENT_VERSION = 1
        private const val MANIFEST_PATH = "manifest.json"
        private const val MAX_ENTRIES = 128
        private const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L
        private const val MAX_ASSET_BYTES = 32L * 1024L * 1024L
        private const val MAX_EXPANDED_BYTES = 128L * 1024L * 1024L
        private const val MAX_MANIFEST_BYTES = 1L * 1024L * 1024L
        private const val MAX_COMPRESSION_RATIO = 200L
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
        private val JSON = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    }
}
