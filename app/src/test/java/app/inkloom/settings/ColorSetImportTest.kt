package app.inkloom.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ColorSetImportTest {
    @Test fun importsNamedAcoV2RgbHsvAndGrayRecords() {
        val bytes = ByteArrayOutputStream().also { raw ->
            DataOutputStream(raw).use { out ->
                out.writeShort(2); out.writeShort(3)
                aco(out, 0, intArrayOf(65535, 0, 0, 0), "Ink red")
                aco(out, 1, intArrayOf(21845, 65535, 65535, 0), "Green")
                aco(out, 8, intArrayOf(5000, 0, 0, 0), "Gray")
            }
        }.toByteArray()

        val imported = ColorSetImport.decode("Comic.aco", bytes)

        assertEquals("Comic", imported.name)
        assertEquals(listOf("Ink red", "Green", "Gray"), imported.swatches.map { it.name })
        assertEquals(0xffff0000.toInt(), imported.swatches[0].color)
        assertEquals(0xff00ff00.toInt(), imported.swatches[1].color)
        assertEquals(0xff808080.toInt(), imported.swatches[2].color)
    }

    @Test fun importsProcreateSwatchesArchiveAndPreservesPaletteName() {
        val json = """{"name":"Manga palette","swatches":[{"hue":0.0,"saturation":1.0,"brightness":1.0,"alpha":1.0,"name":"Red"},{"red":0.0,"green":0.25,"blue":1.0,"alpha":1.0,"name":"Blue"}]}"""
        val bytes = ByteArrayOutputStream().also { raw ->
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("Swatches.json")); zip.write(json.toByteArray()); zip.closeEntry()
            }
        }.toByteArray()

        val imported = ColorSetImport.decode("Shared.swatches", bytes)
        assertEquals("Manga palette", imported.name)
        assertEquals(listOf("Red", "Blue"), imported.swatches.map { it.name })
        assertEquals(0xffff0000.toInt(), imported.swatches.first().color)
        assertEquals(0xff0040ff.toInt(), imported.swatches.last().color)
    }

    @Test fun readableClsXmlImportsAndOpaqueClsExplainsItsBoundary() {
        val readable = """<colors><color name="Paper" red="240" green="230" blue="210"/><swatch name="Ink" hex="#202020"/></colors>"""
        val imported = ColorSetImport.decode("Comic.cls", readable.toByteArray())
        assertEquals(listOf("Paper", "Ink"), imported.swatches.map { it.name })
        assertEquals(0xfff0e6d2.toInt(), imported.swatches.first().color)
        assertEquals(0xff202020.toInt(), imported.swatches.last().color)

        val failure = runCatching { ColorSetImport.decode("Opaque.cls", byteArrayOf(1, 2, 3, 4)) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("proprietary or encrypted"))
    }

    @Test fun importedSetGetsUniqueNameAndBecomesActive() {
        val initial = ColorSetLibrary().createSet("Manga palette", "existing")
        val imported = ImportedColorSet("Manga palette", listOf(ColorSetSwatch("source", 0xff123456.toInt(), "Shade")), "fixture")
        val result = ColorSetImport.addTo(initial, imported)
        assertEquals("Manga palette 2", result.activeSet?.name)
        assertEquals(0xff123456.toInt(), result.activeSet?.swatches?.single()?.color)
        assertEquals("Shade", result.activeSet?.swatches?.single()?.name)
    }

    private fun aco(out: DataOutputStream, space: Int, components: IntArray, name: String) {
        out.writeShort(space)
        components.forEach(out::writeShort)
        out.writeInt(name.length + 1)
        name.forEach { out.writeShort(it.code) }
        out.writeShort(0)
    }
}
