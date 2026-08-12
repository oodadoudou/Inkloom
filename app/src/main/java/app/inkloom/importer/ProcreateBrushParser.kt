package app.inkloom.importer

import app.inkloom.document.BrushCurvePoint
import app.inkloom.document.BrushSecondaryDynamics
import app.inkloom.document.BrushTaperMode
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.max

class ProcreateBrushParser(private val assetDirectory: File) {
    init { assetDirectory.mkdirs() }

    fun enrich(source: File, hash: String, fallback: ImportedBrush): List<ImportedBrush> = runCatching {
        ZipFile(source).use { archive ->
            val entries = archive.entries().asSequence().toList()
            validate(entries)
            val byName = entries.associateBy(ZipEntry::getName)
            val brushPrefixes = brushPrefixes(archive, byName)
            require(brushPrefixes.isNotEmpty()) { "Procreate container has no Brush.archive payload" }
            val outputDirectory = File(assetDirectory, hash).apply { mkdirs() }
            brushPrefixes.mapIndexed { index, prefix ->
                val archiveEntry = byName[path(prefix, "Brush.archive")]
                    ?: error("Procreate brush ${prefix.ifBlank { "root" }} has no Brush.archive")
                val metadata = ProcreateKeyedArchive(readBounded(archive, archiveEntry, MAX_METADATA_BYTES))
                val shape = byName[path(prefix, "Shape.png")]?.let { extract(archive, it, File(outputDirectory, "brush-${index + 1}-shape.png")) }
                val grain = byName[path(prefix, "Grain.png")]?.let { extract(archive, it, File(outputDirectory, "brush-${index + 1}-grain.png")) }
                val dualPrefix = path(prefix, "Sub01")
                val dualArchive = byName[path(dualPrefix, "Brush.archive")]?.let { ProcreateKeyedArchive(readBounded(archive, it, MAX_METADATA_BYTES)) }
                val dualShape = byName[path(dualPrefix, "Shape.png")]?.let { extract(archive, it, File(outputDirectory, "brush-${index + 1}-dual-shape.png")) }
                mapBrush(
                    fallback = fallback.copy(id = if (index == 0) fallback.id else UUID.randomUUID().toString()),
                    metadata = metadata,
                    shape = shape,
                    grain = grain,
                    dualMetadata = dualArchive,
                    dualShape = dualShape,
                    position = index + 1,
                    total = brushPrefixes.size,
                )
            }
        }
    }.getOrElse { failure ->
        listOf(
            fallback.copy(
                compatibility = Compatibility.BASIC,
                parserVersion = CURRENT_VERSION,
                report = (fallback.report + "Procreate parsing failed safely: ${failure.message ?: failure::class.java.simpleName}").distinct(),
            ),
        )
    }

    private fun brushPrefixes(archive: ZipFile, byName: Map<String, ZipEntry>): List<String> {
        val manifest = byName["brushset.plist"]
        if (manifest != null) {
            val xml = readBounded(archive, manifest, MAX_MANIFEST_BYTES).decodeToString()
            val ordered = UUID_PATTERN.findAll(xml).map(MatchResult::value).distinct().filter { byName.containsKey("$it/Brush.archive") }.toList()
            if (ordered.isNotEmpty()) return ordered
        }
        if (byName.containsKey("Brush.archive")) return listOf("")
        return byName.keys.asSequence()
            .filter { it.count { character -> character == '/' } == 1 && it.endsWith("/Brush.archive") }
            .map { it.substringBefore('/') }
            .filter { UUID_PATTERN.matches(it) }
            .sorted()
            .toList()
    }

    private fun mapBrush(
        fallback: ImportedBrush,
        metadata: ProcreateKeyedArchive,
        shape: File?,
        grain: File?,
        dualMetadata: ProcreateKeyedArchive?,
        dualShape: File?,
        position: Int,
        total: Int,
    ): ImportedBrush {
        val pressureSize = metadata.float("dynamicsPressureSize")
        val pressureOpacity = metadata.float("dynamicsPressureOpacity")
        val speedSize = metadata.float("dynamicsSpeedSize")
        val speedOpacity = metadata.float("dynamicsSpeedOpacity")
        val jitterSize = metadata.float("dynamicsJitterSize")
        val jitterOpacity = metadata.float("dynamicsJitterOpacity")
        val tiltSize = metadata.float("dynamicsTiltSize")
        val tiltOpacity = metadata.float("dynamicsTiltOpacity")
        val wetEdges = metadata.float("wetEdgesAmount").coerceIn(0f, 1f)
        val burntEdges = metadata.float("burntEdgesAmount").coerceIn(0f, 1f)
        val wetMix = max(metadata.float("dynamicsMix"), metadata.float("dynamicsPressureMix")).coerceIn(0f, 1f)
        val paintLoad = metadata.float("dynamicsLoad").coerceIn(0f, 1f)
        val wetAccumulation = metadata.float("dynamicsWetAccumulation").coerceIn(0f, 1f)
        val smudgeAccumulation = metadata.float("dynamicsSmudgeAccumulation").coerceIn(0f, 1f)
        val watercolor = wetMix > .001f || wetEdges > .01f || burntEdges > .01f
        val plotJitter = max(abs(metadata.float("plotJitter")), abs(metadata.float("plotJitterLongitudinal"))).coerceIn(0f, 4f)
        val scatterSpread = plotJitter
        val randomRotation = Math.toDegrees(abs(metadata.float("shapeScatter")).toDouble()).toFloat().coerceIn(0f, 180f)
        val strokeRotationJitter = if (metadata.boolean("shapeRandomise")) 180f else 0f
        val stampCount = (metadata.float("shapeCount", .0625f) * 16f).toInt().coerceIn(1, 16)
        val stampCountJitter = metadata.float("shapeCountJitter").coerceIn(0f, 1f)
        val shapeRotation = Math.toDegrees(metadata.float("shapeAngle").toDouble()).toFloat().coerceIn(-360f, 360f)
        val followsStylus = metadata.boolean("shapeAzimuth")
        val followsStroke = metadata.boolean("oriented")
        val spacingJitter = metadata.float("plotSpacingJitter")
        val genericTaperStartLength = metadata.float("taperStartLength").coerceIn(0f, 1f)
        val genericTaperEndLength = metadata.float("taperEndLength").coerceIn(0f, 1f)
        val genericTaperSize = metadata.float("taperSize").coerceIn(0f, 1f)
        val pencilTaperStartLength = metadata.float("pencilTaperStartLength").coerceIn(0f, 1f)
        val pencilTaperEndLength = metadata.float("pencilTaperEndLength").coerceIn(0f, 1f)
        val pencilTaperSize = metadata.float("pencilTaperSize").coerceIn(0f, 1f)
        val taperStartLength = max(genericTaperStartLength, pencilTaperStartLength)
        val taperEndLength = max(genericTaperEndLength, pencilTaperEndLength)
        val taperSize = max(genericTaperSize, pencilTaperSize)
        val stabilization = combinedStrength(
            metadata.float("plotSmoothing"),
            metadata.float("plotMovingAverageStabilization"),
            metadata.float("plotFFTSmoothingAmount"),
        ).coerceIn(0f, .9f)
        val dualMapped = dualMetadata?.let { mapSecondary(it) }
        val compatibility = if (watercolor || dualMetadata != null || metadata.int("blendMode") !in 0..1) Compatibility.BASIC else Compatibility.HIGH
        return fallback.copy(
            name = metadata.string("name").orEmpty().trim().ifBlank { fallback.name },
            compatibility = compatibility,
            tipPreviewPath = shape?.absolutePath,
            tipPreviewPaths = listOfNotNull(shape?.absolutePath),
            dualTipPreviewPaths = listOfNotNull(dualShape?.absolutePath),
            grainPreviewPath = grain?.absolutePath,
            grainDensity = metadata.float("grainDepth", if (grain != null) 1f else 0f).coerceIn(0f, 1f),
            grainScale = (metadata.float("textureScale", 1f) * 100f).coerceIn(1f, 4000f),
            grainMovement = metadata.float("textureMovement", 1f).coerceIn(0f, 1f),
            grainZoom = metadata.float("textureZoom", 1f).coerceIn(0f, 1f),
            grainRotationDegrees = (metadata.float("textureRotation") * 360f).coerceIn(-360f, 360f),
            grainBrightness = (metadata.float("textureBrightness") * 100f).coerceIn(-100f, 100f),
            grainContrast = (metadata.float("textureContrast") * 100f).coerceIn(-100f, 100f),
            grainReversed = metadata.boolean("textureInverted"),
            grainOffsetJitter = metadata.boolean("textureOffsetJitter"),
            defaultSize = (metadata.float("paintSize", .25f).coerceAtLeast(.01f) * metadata.float("maxSize", .32f).coerceAtLeast(.01f) * 100f).coerceIn(.5f, 400f),
            stabilization = stabilization,
            defaultOpacity = (metadata.float("paintOpacity", 1f) * metadata.float("maxOpacity", 1f)).coerceIn(.01f, 1f),
            spacing = (metadata.float("plotSpacing") * 10f).coerceIn(.01f, 2f),
            spacingSecondaryDynamics = secondaryDynamics(0f, 0f, spacingJitter),
            flow = metadata.float("dynamicsGlazedFlow", 1f).coerceIn(0f, 1f),
            hardness = if (metadata.boolean("shapeFilter", true)) .85f else 1f,
            thickness = metadata.float("shapeRoundness", 1f).coerceIn(.05f, 1f),
            rotationDegrees = shapeRotation,
            followStylusOrientation = followsStylus,
            followStrokeDirection = followsStroke,
            randomRotationDegrees = randomRotation,
            strokeRotationJitterDegrees = strokeRotationJitter,
            stampCount = stampCount,
            stampCountJitter = stampCountJitter,
            watercolor = watercolor,
            colorMix = wetMix,
            paintMix = max(paintLoad, max(wetAccumulation, smudgeAccumulation)),
            spray = scatterSpread > .01f,
            sprayParticleSize = 100f,
            sprayParticleSizeSyncBrushSize = true,
            sprayDensity = 1f,
            sprayBias = 0f,
            spraySpread = scatterSpread,
            sprayParticleRotationDegrees = shapeRotation,
            sprayParticleFollowStylusOrientation = followsStylus,
            sprayParticleFollowStrokeDirection = followsStroke,
            sprayParticleRandomRotationDegrees = randomRotation,
            dualBrush = dualMetadata != null && dualShape != null,
            dualSizeScale = dualMapped?.sizeScale ?: 1f,
            dualSpacing = dualMapped?.spacing ?: .12f,
            dualFlow = dualMapped?.flow ?: 1f,
            dualHardness = dualMapped?.hardness ?: 1f,
            dualThickness = dualMapped?.thickness ?: 1f,
            dualRotationDegrees = dualMapped?.rotation ?: 0f,
            dualFollowStylusOrientation = dualMapped?.followStylus ?: false,
            dualFollowStrokeDirection = dualMapped?.followStroke ?: false,
            dualRandomRotationDegrees = dualMapped?.randomRotation ?: 0f,
            dualStrokeRotationJitterDegrees = dualMapped?.strokeRotationJitter ?: 0f,
            dualStampCount = dualMapped?.stampCount ?: 1,
            dualStampCountJitter = dualMapped?.stampCountJitter ?: 0f,
            dualSpray = dualMapped?.spray ?: false,
            dualSprayDensity = dualMapped?.density ?: 1f,
            dualSpraySpread = dualMapped?.spread ?: 1f,
            dualSprayParticleRotationDegrees = dualMapped?.rotation ?: 0f,
            dualSprayParticleFollowStylusOrientation = dualMapped?.followStylus ?: false,
            dualSprayParticleFollowStrokeDirection = dualMapped?.followStroke ?: false,
            dualSprayParticleRandomRotationDegrees = dualMapped?.randomRotation ?: 0f,
            dualCompositeMode = metadata.int("dualBlendMode"),
            taperMode = BrushTaperMode.PERCENTAGE,
            taperStartEnabled = taperStartLength > .0001f && taperSize > .0001f,
            taperStartLength = taperStartLength.coerceAtLeast(.001f),
            taperStartMinimum = (1f - taperSize).coerceIn(0f, 1f),
            taperEndEnabled = taperEndLength > .0001f && taperSize > .0001f,
            taperEndLength = taperEndLength.coerceAtLeast(.001f),
            taperEndMinimum = (1f - taperSize).coerceIn(0f, 1f),
            waterEdgeEnabled = wetEdges > .01f || burntEdges > .01f,
            waterEdgeRadius = (3f + wetEdges * 20f).coerceIn(0f, 400f),
            waterEdgeOpacity = wetEdges,
            waterEdgeDarkness = burntEdges,
            waterEdgeBlur = (wetEdges * 8f).coerceIn(0f, 400f),
            sizePressureEnabled = abs(pressureSize) > DYNAMIC_EPSILON,
            sizePressureMinimum = pressureMinimum(pressureSize, metadata.float("minSize")),
            sizePressureCurve = pressureCurve(pressureSize, metadata.curve("dynamicsPressureSizeCurve")),
            sizeSecondaryDynamics = secondaryDynamics(tiltSize, speedSize, jitterSize),
            opacityPressureEnabled = abs(pressureOpacity) > DYNAMIC_EPSILON,
            opacityPressureMinimum = pressureMinimum(pressureOpacity, metadata.float("minOpacity")),
            opacityPressureCurve = pressureCurve(pressureOpacity, metadata.curve("dynamicsPressureOpacityCurve")),
            opacitySecondaryDynamics = secondaryDynamics(tiltOpacity, speedOpacity, jitterOpacity),
            parserVersion = CURRENT_VERSION,
            report = buildList {
                add("Procreate brush $position/$total decoded from NSKeyedArchive version ${metadata.int("version")}")
                add(if (shape != null) "Procreate Shape.png extracted as the real stamp source" else "Shape source was missing; line fallback retained")
                if (grain != null) add("Procreate Grain.png extracted with independent movement, scale, zoom, rotation, brightness, contrast, invert and per-stroke offset settings")
                add("Stroke spacing, smoothing, size/opacity limits, shape angle, roundness, azimuth and direction behavior mapped")
                if (pencilTaperStartLength > DYNAMIC_EPSILON || pencilTaperEndLength > DYNAMIC_EPSILON) add("Procreate Apple Pencil start/end taper lengths and size floor mapped")
                if (metadata.float("plotMovingAverageStabilization") > DYNAMIC_EPSILON || metadata.float("plotFFTSmoothingAmount") > DYNAMIC_EPSILON) add("Procreate StreamLine, stabilization and motion filtering combined into Inkloom stabilization")
                val sources = buildList { if (abs(pressureSize) > DYNAMIC_EPSILON || abs(pressureOpacity) > DYNAMIC_EPSILON) add("pressure"); if (abs(speedSize) > DYNAMIC_EPSILON || abs(speedOpacity) > DYNAMIC_EPSILON) add("speed"); if (abs(tiltSize) > DYNAMIC_EPSILON || abs(tiltOpacity) > DYNAMIC_EPSILON) add("tilt"); if (abs(jitterSize) > DYNAMIC_EPSILON || abs(jitterOpacity) > DYNAMIC_EPSILON) add("random jitter") }
                if (sources.isNotEmpty()) add("Procreate dynamics mapped: ${sources.joinToString()}")
                if (randomRotation > DYNAMIC_EPSILON) add("Procreate Shape Scatter mapped to independent per-stamp rotation")
                if (strokeRotationJitter > DYNAMIC_EPSILON) add("Procreate Randomized mapped to one deterministic starting rotation per stroke")
                if (stampCount > 1 || stampCountJitter > DYNAMIC_EPSILON) add("Procreate Shape Count $stampCount and Count Jitter mapped as repeated stamps at each plot point")
                if (scatterSpread > DYNAMIC_EPSILON) add("Procreate stroke-path jitter mapped as positional spread")
                if (metadata.boolean("textureOffsetJitter")) add("Procreate Grain Offset Jitter mapped to a deterministic texture origin for each stroke")
                if (dualMetadata != null) add(if (dualShape != null) "Procreate Sub01 Dual Brush extracted with independent shape and core dynamics; combine mode ${metadata.int("dualBlendMode")} retained" else "Procreate Sub01 metadata found, but its shape source was missing")
                if (watercolor) add("Procreate Wet Mix/rendering parameters detected; Inkloom maps load, mix, accumulation and edge behavior to its deterministic wet-brush approximation")
                if (metadata.int("blendMode") !in 0..1) add("Procreate blend mode ${metadata.int("blendMode")} is retained in the source but rendered source-over until independently verified")
                if (metadata.boolean("shapeRoll") || metadata.boolean("plotJitterRoll")) add("Apple Pencil Pro barrel-roll behavior is retained in the source; Android MotionEvent does not expose an equivalent channel on this engine")
                add("Original Procreate package retained with SHA-256 integrity hash")
            },
        )
    }

    private fun mapSecondary(metadata: ProcreateKeyedArchive): SecondaryBrush {
        val spread = max(abs(metadata.float("plotJitter")), abs(metadata.float("plotJitterLongitudinal"))).coerceIn(0f, 4f)
        return SecondaryBrush(
            sizeScale = (metadata.float("maxSize", .32f) / .32f).coerceIn(.01f, 8f),
            spacing = (metadata.float("plotSpacing") * 10f).coerceIn(.01f, 2f),
            flow = metadata.float("dynamicsGlazedFlow", 1f).coerceIn(0f, 1f),
            hardness = if (metadata.boolean("shapeFilter", true)) .85f else 1f,
            thickness = metadata.float("shapeRoundness", 1f).coerceIn(.05f, 1f),
            rotation = Math.toDegrees(metadata.float("shapeAngle").toDouble()).toFloat().coerceIn(-360f, 360f),
            followStylus = metadata.boolean("shapeAzimuth"),
            followStroke = metadata.boolean("oriented"),
            randomRotation = Math.toDegrees(abs(metadata.float("shapeScatter")).toDouble()).toFloat().coerceIn(0f, 180f),
            strokeRotationJitter = if (metadata.boolean("shapeRandomise")) 180f else 0f,
            stampCount = (metadata.float("shapeCount", .0625f) * 16f).toInt().coerceIn(1, 16),
            stampCountJitter = metadata.float("shapeCountJitter").coerceIn(0f, 1f),
            spray = spread > DYNAMIC_EPSILON,
            spread = spread,
            density = 1f,
        )
    }

    private fun pressureMinimum(amount: Float, explicitMinimum: Float): Float =
        max(explicitMinimum.coerceIn(0f, 1f), 1f - abs(amount).coerceIn(0f, 1f))

    private fun combinedStrength(vararg values: Float): Float =
        1f - values.fold(1f) { remaining, value -> remaining * (1f - value.coerceIn(0f, 1f)) }

    private fun pressureCurve(amount: Float, source: List<BrushCurvePoint>): List<BrushCurvePoint> {
        val base = source.ifEmpty { LINEAR_CURVE }
        return if (amount >= 0f) base else base.map { point -> point.copy(y = 1f - point.y) }
    }

    private fun secondaryDynamics(tilt: Float, speed: Float, jitter: Float): BrushSecondaryDynamics = BrushSecondaryDynamics(
        tiltEnabled = abs(tilt) > DYNAMIC_EPSILON,
        tiltMinimum = 1f - abs(tilt).coerceIn(0f, 1f),
        velocityEnabled = abs(speed) > DYNAMIC_EPSILON,
        velocityMinimum = 1f - abs(speed).coerceIn(0f, 1f),
        velocityCurve = if (speed >= 0f) DESCENDING_CURVE else LINEAR_CURVE,
        randomEnabled = abs(jitter) > DYNAMIC_EPSILON,
        randomMinimum = 1f - abs(jitter).coerceIn(0f, 1f),
    )

    private fun validate(entries: List<ZipEntry>) {
        require(entries.size in 1..MAX_ENTRIES) { "Procreate archive has too many entries" }
        require(entries.map(ZipEntry::getName).distinct().size == entries.size) { "Procreate archive contains duplicate paths" }
        var expandedBytes = 0L
        entries.forEach { entry ->
            require(isSafePath(entry.name)) { "Procreate archive contains an unsafe path" }
            require(entry.size in 0..MAX_ENTRY_BYTES) { "Procreate archive entry is too large" }
            expandedBytes += entry.size
            require(expandedBytes <= MAX_EXPANDED_BYTES) { "Procreate archive expands beyond the safety limit" }
            if (entry.compressedSize > 0 && entry.size > 1_048_576L) {
                require(entry.size / entry.compressedSize.coerceAtLeast(1L) <= MAX_COMPRESSION_RATIO) { "Procreate archive has a suspicious compression ratio" }
            }
        }
    }

    private fun isSafePath(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && '\\' !in name && name.split('/').none { it == ".." }

    private fun extract(archive: ZipFile, entry: ZipEntry, target: File): File {
        if (target.isFile && target.length() == entry.size) return target
        require(entry.size in 1..MAX_IMAGE_BYTES) { "Procreate image asset exceeds the safety limit" }
        val temporary = File(target.parentFile, ".${target.name}.${System.nanoTime()}.part")
        try {
            archive.getInputStream(entry).use { input ->
                temporary.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_IMAGE_BYTES) { "Procreate image asset exceeds the safety limit" }
                        output.write(buffer, 0, count)
                    }
                    require(total == entry.size) { "Procreate image asset is truncated" }
                }
            }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Could not retain Procreate image asset" }
            return target
        } finally {
            temporary.delete()
        }
    }

    private fun readBounded(archive: ZipFile, entry: ZipEntry, limit: Long): ByteArray {
        require(entry.size in 1..limit) { "Procreate metadata exceeds the safety limit" }
        return archive.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream(entry.size.toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "Procreate metadata exceeds the safety limit" }
                output.write(buffer, 0, count)
            }
            require(total == entry.size) { "Procreate metadata is truncated" }
            output.toByteArray()
        }
    }

    private fun path(prefix: String, child: String): String = if (prefix.isBlank()) child else "$prefix/$child"

    private data class SecondaryBrush(
        val sizeScale: Float,
        val spacing: Float,
        val flow: Float,
        val hardness: Float,
        val thickness: Float,
        val rotation: Float,
        val followStylus: Boolean,
        val followStroke: Boolean,
        val randomRotation: Float,
        val strokeRotationJitter: Float,
        val stampCount: Int,
        val stampCountJitter: Float,
        val spray: Boolean,
        val spread: Float,
        val density: Float,
    )

    companion object {
        const val CURRENT_VERSION = 7
        private const val MAX_ENTRIES = 4_096
        private const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L
        private const val MAX_EXPANDED_BYTES = 384L * 1024L * 1024L
        private const val MAX_IMAGE_BYTES = 16L * 1024L * 1024L
        private const val MAX_METADATA_BYTES = 4L * 1024L * 1024L
        private const val MAX_MANIFEST_BYTES = 1L * 1024L * 1024L
        private const val MAX_COMPRESSION_RATIO = 200L
        private const val DYNAMIC_EPSILON = .0005f
        private val UUID_PATTERN = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")
        private val LINEAR_CURVE = listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f))
        private val DESCENDING_CURVE = listOf(BrushCurvePoint(0f, 1f), BrushCurvePoint(1f, 0f))
    }
}

internal class ProcreateKeyedArchive(bytes: ByteArray) {
    private val objects: List<Any?>
    private val root: Map<String, Any?>

    init {
        val plist = BinaryPlistReader(bytes).read() as? Map<*, *> ?: error("Procreate archive root is not a dictionary")
        objects = plist["\$objects"] as? List<*> ?: error("Procreate archive has no object table")
        require(objects.size <= 20_000) { "Procreate archive object table is too large" }
        val top = plist["\$top"] as? Map<*, *> ?: error("Procreate archive has no top object")
        val unresolvedRoot = resolve(top["root"]) as? Map<*, *> ?: error("Procreate brush settings are missing")
        root = unresolvedRoot.entries.associate { (key, value) ->
            (key as? String ?: error("Procreate brush setting key is not text")) to value
        }
    }

    fun string(key: String): String? = resolve(root[key]) as? String
    fun float(key: String, fallback: Float = 0f): Float = (resolve(root[key]) as? Number)?.toFloat()?.takeIf(Float::isFinite) ?: fallback
    fun int(key: String, fallback: Int = 0): Int = (resolve(root[key]) as? Number)?.toInt() ?: fallback
    fun boolean(key: String, fallback: Boolean = false): Boolean = resolve(root[key]) as? Boolean ?: fallback

    fun curve(key: String): List<BrushCurvePoint> {
        val curve = resolve(root[key]) as? Map<*, *> ?: return emptyList()
        val pointsContainer = resolve(curve["points"]) as? Map<*, *> ?: return emptyList()
        val values = resolve(pointsContainer["NS.objects"]) as? List<*> ?: return emptyList()
        return values.mapNotNull { item ->
            val text = resolve(item) as? String ?: return@mapNotNull null
            val match = POINT_PATTERN.matchEntire(text.trim()) ?: return@mapNotNull null
            val x = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val y = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            BrushCurvePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }.distinctBy(BrushCurvePoint::x).sortedBy(BrushCurvePoint::x).take(32)
    }

    private fun resolve(value: Any?): Any? = when (value) {
        is PlistUid -> objects.getOrNull(value.value)
        else -> value
    }

    private companion object {
        val POINT_PATTERN = Regex("\\{\\s*([-+0-9.eE]+)\\s*,\\s*([-+0-9.eE]+)\\s*}")
    }
}
