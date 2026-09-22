package app.inkloom.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImportedColorSet(val name: String, val swatches: List<ColorSetSwatch>, val sourceFormat: String)

enum class ColorSetImportMode { APPEND, REPLACE }

/** Bounded palette decoder. Unsupported proprietary payloads fail with a visible reason. */
object ColorSetImport {
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 128

    fun decode(displayName: String, bytes: ByteArray): ImportedColorSet {
        require(bytes.isNotEmpty()) { "Color palette file is empty" }
        require(bytes.size <= MAX_FILE_BYTES) { "Color palette file exceeds 16 MB" }
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val baseName = displayName.substringBeforeLast('.').ifBlank { "Imported palette" }
        val decoded = when (extension) {
            "aco" -> ImportedColorSet(baseName, decodeAco(bytes), "ACO")
            "swatches" -> decodeStructured(baseName, bytes, "Procreate .swatches")
            "cls" -> decodeStructured(baseName, bytes, "CSP .cls")
            else -> error("Unsupported color palette extension .$extension; choose .swatches, .aco, or .cls")
        }
        require(decoded.swatches.isNotEmpty()) { "${decoded.sourceFormat} contains no supported RGB, HSV, CMYK, Lab, or grayscale colors" }
        require(decoded.swatches.size <= ColorSetLibrary.MAX_SWATCHES_PER_SET) { "Palette contains too many colors" }
        return decoded.copy(swatches = decoded.swatches.distinctBy { it.color to it.name })
    }

    fun addTo(library: ColorSetLibrary, imported: ImportedColorSet): ColorSetLibrary {
        var uniqueName = imported.name.take(ColorSetLibrary.MAX_NAME_LENGTH).ifBlank { "Imported palette" }
        var suffix = 2
        val base = uniqueName
        while (library.sets.any { it.name.equals(uniqueName, true) }) {
            val marker = " $suffix"
            uniqueName = base.take(ColorSetLibrary.MAX_NAME_LENGTH - marker.length) + marker
            suffix++
        }
        var result = library.createSet(uniqueName)
        val setId = requireNotNull(result.activeSetId)
        imported.swatches.forEachIndexed { index, swatch ->
            result = result.insertSwatch(setId, swatch.color, swatch.name, beforeIndex = index)
        }
        return result.validated()
    }

    /** Decode first, then atomically edit the palette selected when the file picker opened. */
    fun applyTo(library: ColorSetLibrary, imported: ImportedColorSet, mode: ColorSetImportMode, targetSetId: String?): ColorSetLibrary {
        require(imported.swatches.isNotEmpty()) { "Imported palette is empty" }
        require(imported.swatches.size <= ColorSetLibrary.MAX_SWATCHES_PER_SET) { "Palette contains too many colors" }
        if (targetSetId == null) {
            require(mode == ColorSetImportMode.APPEND) { "Choose a color set to replace" }
            return addTo(library, imported)
        }
        val target = requireNotNull(library.sets.firstOrNull { it.id == targetSetId }) { "Selected color set no longer exists" }
        val swatches = imported.swatches.map { it.copy(id = UUID.randomUUID().toString()) }
        val updated = when (mode) {
            ColorSetImportMode.APPEND -> {
                require(target.swatches.size + swatches.size <= ColorSetLibrary.MAX_SWATCHES_PER_SET) { "Color set tile limit reached" }
                target.copy(swatches = target.swatches + swatches)
            }
            ColorSetImportMode.REPLACE -> target.copy(swatches = swatches, selectedSwatchId = null)
        }
        return library.copy(sets = library.sets.map { if (it.id == targetSetId) updated else it }).validated()
    }

    private fun decodeStructured(baseName: String, bytes: ByteArray, format: String): ImportedColorSet {
        if (looksLikeAco(bytes)) return ImportedColorSet(baseName, decodeAco(bytes), "$format (ACO payload)")
        val documents = if (looksLikeZip(bytes)) unzipDocuments(bytes) else listOf(baseName to bytes)
        val swatches = mutableListOf<ColorSetSwatch>()
        var discoveredName: String? = null
        documents.forEach { (name, payload) ->
            if (looksLikeAco(payload)) swatches += decodeAco(payload)
            else if (name.endsWith(".json", true) || payload.firstNonWhitespace() in setOf('{'.code, '['.code)) {
                val root = runCatching { Json.parseToJsonElement(payload.toString(Charsets.UTF_8)) }.getOrNull()
                if (root != null) {
                    discoveredName = discoveredName ?: findPaletteName(root)
                    collectJsonColors(root, swatches)
                }
            } else if (name.endsWith(".xml", true) || payload.toString(Charsets.UTF_8).trimStart().startsWith("<")) {
                collectXmlColors(payload.toString(Charsets.UTF_8), swatches)
            }
        }
        if (swatches.isEmpty() && format.startsWith("CSP")) {
            error("This CSP .cls variant is proprietary or encrypted and has no readable color records")
        }
        return ImportedColorSet(discoveredName?.takeIf(String::isNotBlank) ?: baseName, swatches, format)
    }

    private fun decodeAco(bytes: ByteArray): List<ColorSetSwatch> {
        fun block(offset: Int): Pair<List<ColorSetSwatch>, Int> {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            buffer.position(offset)
            require(buffer.remaining() >= 4) { "ACO header is truncated" }
            val version = buffer.short.toInt() and 0xffff
            require(version == 1 || version == 2) { "Unsupported ACO version $version" }
            val count = buffer.short.toInt() and 0xffff
            require(count <= ColorSetLibrary.MAX_SWATCHES_PER_SET) { "ACO color count exceeds the palette limit" }
            val colors = buildList {
                repeat(count) { index ->
                    require(buffer.remaining() >= 10) { "ACO color record ${index + 1} is truncated" }
                    val space = buffer.short.toInt() and 0xffff
                    val components = IntArray(4) { buffer.short.toInt() and 0xffff }
                    var name = ""
                    if (version == 2) {
                        require(buffer.remaining() >= 4) { "ACO color name ${index + 1} is truncated" }
                        val length = buffer.int
                        require(length in 0..4096 && buffer.remaining() >= length * 2) { "ACO color name ${index + 1} is invalid" }
                        val chars = CharArray(length) { buffer.short.toInt().toChar() }
                        name = chars.concatToString().trimEnd('\u0000')
                    }
                    decodeAcoColor(space, components)?.let { add(ColorSetSwatch(UUID.randomUUID().toString(), it, name)) }
                }
            }
            return colors to buffer.position()
        }
        val (first, end) = block(0)
        return if (end + 4 <= bytes.size && ByteBuffer.wrap(bytes, end, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xffff == 2) {
            block(end).first.ifEmpty { first }
        } else first
    }

    private fun decodeAcoColor(space: Int, c: IntArray): Int? = when (space) {
        0 -> argb(c[0] / 257, c[1] / 257, c[2] / 257)
        1 -> hsv(c[0] / 65535f * 360f, c[1] / 65535f, c[2] / 65535f)
        2, 9 -> {
            val cyan = 1f - c[0] / 65535f
            val magenta = 1f - c[1] / 65535f
            val yellow = 1f - c[2] / 65535f
            val black = 1f - c[3] / 65535f
            argb(((1f - cyan) * (1f - black) * 255f).roundToInt(), ((1f - magenta) * (1f - black) * 255f).roundToInt(), ((1f - yellow) * (1f - black) * 255f).roundToInt())
        }
        7 -> labToArgb(c[0] / 100f, c[1].toShort().toInt() / 100f, c[2].toShort().toInt() / 100f)
        8 -> (255f * (1f - (c[0] / 10000f).coerceIn(0f, 1f))).roundToInt().let { argb(it, it, it) }
        else -> null
    }

    private fun unzipDocuments(bytes: ByteArray): List<Pair<String, ByteArray>> = buildList {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= MAX_ARCHIVE_ENTRIES) { "Palette archive has too many entries" }
                if (!entry.isDirectory) {
                    val payload = zip.readBytesBounded(MAX_FILE_BYTES)
                    if (entry.name.endsWith(".json", true) || entry.name.endsWith(".xml", true) || entry.name.endsWith(".aco", true)) add(entry.name to payload)
                }
                zip.closeEntry()
            }
        }
    }

    private fun collectJsonColors(value: JsonElement?, output: MutableList<ColorSetSwatch>) {
        when (value) {
            is JsonObject -> {
                decodeJsonColor(value)?.let { (color, name) -> output += ColorSetSwatch(UUID.randomUUID().toString(), color, name) }
                value.values.forEach { collectJsonColors(it, output) }
            }
            is JsonArray -> value.forEach { collectJsonColors(it, output) }
            else -> Unit
        }
    }

    private fun decodeJsonColor(value: JsonObject): Pair<Int, String>? {
        val name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty().take(ColorSetLibrary.MAX_NAME_LENGTH)
        fun number(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key -> value[key]?.jsonPrimitive?.doubleOrNull }
        val red = number("red", "r")
        val green = number("green", "g")
        val blue = number("blue", "b")
        if (red != null && green != null && blue != null) {
            val scale = if (max(red, max(green, blue)) <= 1.00001) 255.0 else 1.0
            val alpha = number("alpha", "a")?.let { if (it <= 1.00001) it * 255.0 else it } ?: 255.0
            return argb((red * scale).roundToInt(), (green * scale).roundToInt(), (blue * scale).roundToInt(), alpha.roundToInt()) to name
        }
        val hue = number("hue", "h")
        val saturation = number("saturation", "s")
        val brightness = number("brightness", "value", "v")
        if (hue != null && saturation != null && brightness != null) {
            val degrees = if (abs(hue) <= 1.00001) hue * 360.0 else hue
            return hsv(degrees.toFloat(), saturation.toFloat(), brightness.toFloat(), number("alpha", "a")?.toFloat() ?: 1f) to name
        }
        value["hex"]?.jsonPrimitive?.contentOrNull?.takeIf { it.matches(Regex("#?[0-9a-fA-F]{6,8}")) }?.let { raw ->
            val hex = raw.removePrefix("#")
            return ((if (hex.length == 6) 0xff000000L or hex.toLong(16) else hex.toLong(16)).toInt()) to name
        }
        return null
    }

    private fun collectXmlColors(xml: String, output: MutableList<ColorSetSwatch>) {
        val tag = Regex("<(?:color|swatch)\\b([^>]*)>", RegexOption.IGNORE_CASE)
        val attribute = Regex("([a-zA-Z]+)\\s*=\\s*[\"']([^\"']+)[\"']")
        tag.findAll(xml).forEach { match ->
            val values = attribute.findAll(match.groupValues[1]).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val json = JsonObject(values.mapValues { JsonPrimitive(it.value) })
            decodeJsonColor(json)?.let { (color, name) -> output += ColorSetSwatch(UUID.randomUUID().toString(), color, name) }
        }
    }

    private fun findPaletteName(value: JsonElement?): String? = when (value) {
        is JsonObject -> value["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && value["swatches"] != null }
            ?: value.values.asSequence().mapNotNull(::findPaletteName).firstOrNull()
        is JsonArray -> value.asSequence().mapNotNull(::findPaletteName).firstOrNull()
        else -> null
    }

    private fun hsv(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Int {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val c = value.coerceIn(0f, 1f) * saturation.coerceIn(0f, 1f)
        val x = c * (1f - abs(h % 2f - 1f))
        val m = value.coerceIn(0f, 1f) - c
        val (r, g, b) = when (floor(h).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return argb(((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt(), (alpha.coerceIn(0f, 1f) * 255f).roundToInt())
    }

    private fun labToArgb(l: Float, a: Float, b: Float): Int {
        var y = (l + 16f) / 116f
        var x = a / 500f + y
        var z = y - b / 200f
        fun pivot(value: Float): Float = if (value * value * value > .008856f) value * value * value else (value - 16f / 116f) / 7.787f
        x = .95047f * pivot(x); y = pivot(y); z = 1.08883f * pivot(z)
        fun gamma(value: Float): Float = if (value > .0031308f) 1.055f * Math.pow(value.toDouble(), 1.0 / 2.4).toFloat() - .055f else 12.92f * value
        return argb((gamma(3.2406f * x - 1.5372f * y - .4986f * z) * 255f).roundToInt(),
            (gamma(-.9689f * x + 1.8758f * y + .0415f * z) * 255f).roundToInt(),
            (gamma(.0557f * x - .204f * y + 1.057f * z) * 255f).roundToInt())
    }

    private fun argb(red: Int, green: Int, blue: Int, alpha: Int = 255): Int =
        (alpha.coerceIn(0, 255) shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

    private fun looksLikeZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    private fun looksLikeAco(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1].toInt() in 1..2
    private fun ByteArray.firstNonWhitespace(): Int = firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.and(0xff) ?: -1
    private fun ZipInputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Palette archive entry is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
