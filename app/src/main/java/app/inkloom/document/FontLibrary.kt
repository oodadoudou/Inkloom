package app.inkloom.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/** What a font's licence (OpenType OS/2 fsType) allows when a document uses it. */
enum class FontEmbedding { INSTALLABLE, EDITABLE, PREVIEW_AND_PRINT, RESTRICTED, UNKNOWN }

/** A font file the user added; text refers to it as `font:<id>`. */
@Serializable
data class ImportedFont(val id: String, val name: String, val file: String, val embedding: FontEmbedding)

/** Name and licence read from a TrueType / OpenType file (the first font of a collection). */
data class FontFileInfo(val familyName: String?, val embedding: FontEmbedding)

object FontFileReader {
    /** Reads the family name and fsType; fails when the bytes are not an sfnt font. */
    fun read(bytes: ByteArray): FontFileInfo {
        val data = ByteBuffer.wrap(bytes)
        require(bytes.size >= 12) { "Not a font file" }
        var offset = 0
        if (data.getInt(0) == 0x74746366) offset = data.getInt(12) // 'ttcf': use the first font
        val version = data.getInt(offset)
        require(version == 0x00010000 || version == 0x4F54544F || version == 0x74727565) { "Choose a TrueType or OpenType font (.ttf, .otf, .ttc)" }
        val tables = (data.getShort(offset + 4).toInt() and 0xFFFF)
        var name: String? = null
        var embedding = FontEmbedding.UNKNOWN
        for (i in 0 until tables) {
            val record = offset + 12 + i * 16
            if (record + 16 > bytes.size) break
            val tag = data.getInt(record); val start = data.getInt(record + 8)
            if (start < 0 || start >= bytes.size) continue
            when (tag) {
                0x6E616D65 -> name = familyName(data, start) // 'name'
                0x4F532F32 -> if (start + 10 <= bytes.size) embedding = embedding(data.getShort(start + 8).toInt() and 0xFFFF) // 'OS/2'
            }
        }
        return FontFileInfo(name?.trim()?.takeIf { it.isNotEmpty() }, embedding)
    }

    private fun embedding(fsType: Int): FontEmbedding = when {
        fsType and 0x0008 != 0 -> FontEmbedding.EDITABLE
        fsType and 0x0004 != 0 -> FontEmbedding.PREVIEW_AND_PRINT
        fsType and 0x0002 != 0 -> FontEmbedding.RESTRICTED
        else -> FontEmbedding.INSTALLABLE
    }

    /** Name ID 16 (typographic family) or 1 (family), Windows Unicode first, then Macintosh Roman. */
    private fun familyName(data: ByteBuffer, table: Int): String? {
        if (table + 6 > data.limit()) return null
        val count = data.getShort(table + 2).toInt() and 0xFFFF
        val strings = table + (data.getShort(table + 4).toInt() and 0xFFFF)
        var best: Pair<Int, String>? = null
        for (i in 0 until count) {
            val record = table + 6 + i * 12
            if (record + 12 > data.limit()) break
            val platform = data.getShort(record).toInt() and 0xFFFF
            val nameId = data.getShort(record + 6).toInt() and 0xFFFF
            if (nameId != 1 && nameId != 16) continue
            val length = data.getShort(record + 8).toInt() and 0xFFFF
            val at = strings + (data.getShort(record + 10).toInt() and 0xFFFF)
            if (at < 0 || at + length > data.limit()) continue
            val bytes = ByteArray(length) { data.get(at + it) }
            val text = when (platform) { 0, 3 -> String(bytes, Charsets.UTF_16BE); 1 -> String(bytes, Charsets.ISO_8859_1); else -> continue }
            val rank = (if (nameId == 16) 0 else 2) + (if (platform == 1) 1 else 0)
            if (best == null || rank < best.first) best = rank to text
        }
        return best?.second
    }
}

/**
 * Fonts the user imports for text objects, kept by the app. Notebooks store only a reference,
 * so a notebook opened where the font is missing shows its text in the default font.
 */
class FontLibrary(root: File) {
    private val directory = File(root, "fonts")
    private val index = File(directory, "index.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized fun list(): List<ImportedFont> = runCatching {
        if (!index.isFile) emptyList() else json.decodeFromString(ListSerializer(ImportedFont.serializer()), index.readText())
            .filter { File(directory, it.file).isFile }
    }.getOrDefault(emptyList())

    /** Adds a font file under its family name; adding the same file again returns the existing entry. */
    @Synchronized fun add(fallbackName: String, bytes: ByteArray): ImportedFont {
        require(bytes.size <= MAX_BYTES) { "Fonts must be smaller than ${MAX_BYTES / (1024 * 1024)} MB" }
        val info = FontFileReader.read(bytes)
        val fonts = list()
        require(fonts.size < MAX_FONTS) { "Up to $MAX_FONTS fonts can be added; remove some first" }
        val name = (info.familyName ?: fallbackName).take(80).ifBlank { "Font" }
        fonts.firstOrNull { it.name == name && file(it).readBytes().contentEquals(bytes) }?.let { return it }
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val file = "$id.font"
        val partial = File(directory, "$file.part")
        partial.writeBytes(bytes)
        check(partial.renameTo(File(directory, file))) { "Could not save the font" }
        val font = ImportedFont(id, name, file, info.embedding)
        write(fonts + font)
        return font
    }

    @Synchronized fun remove(id: String): List<ImportedFont> {
        val fonts = list()
        val removed = fonts.firstOrNull { it.id == id } ?: return fonts
        write(fonts - removed)
        file(removed).delete()
        return fonts - removed
    }

    fun file(font: ImportedFont): File = File(directory, font.file)

    fun find(reference: String?): ImportedFont? =
        reference?.removePrefix(TextFonts.IMPORTED)?.takeIf { reference.startsWith(TextFonts.IMPORTED) }?.let { id -> list().firstOrNull { it.id == id } }

    private fun write(fonts: List<ImportedFont>) {
        directory.mkdirs()
        val temporary = File(directory, "index.json.tmp")
        temporary.writeText(json.encodeToString(ListSerializer(ImportedFont.serializer()), fonts))
        check(temporary.renameTo(index)) { "Could not save the font list" }
    }

    companion object {
        const val MAX_FONTS = 100
        const val MAX_BYTES = 40 * 1024 * 1024
    }
}

/** Font references stored on text objects: `system:<family>` for Android's families, `font:<id>` for imported ones. */
object TextFonts {
    const val SYSTEM = "system:"
    const val IMPORTED = "font:"
    /** Android's built-in families, in menu order; null is the default sans-serif. */
    val systemFamilies = listOf("sans-serif", "sans-serif-condensed", "serif", "monospace", "cursive")
}
