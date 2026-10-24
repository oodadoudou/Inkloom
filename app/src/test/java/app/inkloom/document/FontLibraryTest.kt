package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class FontLibraryTest {
    @get:Rule val folder = TemporaryFolder()

    /** A minimal sfnt with a 'name' table (family, Windows UTF-16) and an 'OS/2' table carrying [fsType]. */
    private fun font(family: String, fsType: Int, typographic: String? = null): ByteArray {
        val names = listOfNotNull(1 to family, typographic?.let { 16 to it })
        val strings = names.map { it.second.toByteArray(Charsets.UTF_16BE) }
        val name = ByteArrayOutputStream().also { out -> DataOutputStream(out).run {
            writeShort(0); writeShort(names.size); writeShort(6 + names.size * 12)
            var offset = 0
            names.forEachIndexed { i, (id, _) -> writeShort(3); writeShort(1); writeShort(0x409); writeShort(id); writeShort(strings[i].size); writeShort(offset); offset += strings[i].size }
            strings.forEach { write(it) }
        } }.toByteArray()
        val os2 = ByteArray(78).also { it[8] = (fsType shr 8).toByte(); it[9] = fsType.toByte() }
        val tables = listOf(0x4F532F32 to os2, 0x6E616D65 to name)
        return ByteArrayOutputStream().also { out -> DataOutputStream(out).run {
            writeInt(0x00010000); writeShort(tables.size); writeShort(0); writeShort(0); writeShort(0)
            var offset = 12 + tables.size * 16
            tables.forEach { (tag, data) -> writeInt(tag); writeInt(0); writeInt(offset); writeInt(data.size); offset += data.size }
            tables.forEach { write(it.second) }
        } }.toByteArray()
    }

    @Test fun nameAndLicenceAreReadFromTheFont() {
        assertEquals(FontFileInfo("Brush Script", FontEmbedding.INSTALLABLE), FontFileReader.read(font("Brush Script", 0)))
        assertEquals(FontEmbedding.RESTRICTED, FontFileReader.read(font("Locked", 0x0002)).embedding)
        assertEquals(FontEmbedding.PREVIEW_AND_PRINT, FontFileReader.read(font("Print", 0x0004)).embedding)
        assertEquals(FontEmbedding.EDITABLE, FontFileReader.read(font("Edit", 0x0008)).embedding)
        assertEquals("the typographic family wins", "Source Han", FontFileReader.read(font("Source Han Bold", 0, typographic = "Source Han")).familyName)
    }

    @Test fun notAFontIsRejected() {
        assertTrue(runCatching { FontFileReader.read("not a font at all".toByteArray()) }.isFailure)
    }

    @Test fun fontsAreAddedOnceFoundByReferenceAndRemoved() {
        val library = FontLibrary(folder.root)
        val bytes = font("Comic Hand", 0)
        val added = library.add("comic.ttf", bytes)
        assertEquals("Comic Hand", added.name)
        assertEquals("adding the same file again returns it", added, library.add("comic.ttf", bytes))
        assertEquals(added, library.find(TextFonts.IMPORTED + added.id))
        assertNull(library.find(TextFonts.SYSTEM + "serif"))
        library.remove(added.id)
        assertNull(library.find(TextFonts.IMPORTED + added.id))
        assertEquals(emptyList<ImportedFont>(), library.list())
    }
}
