package app.inkloom.importer

import app.inkloom.model.BrushFormat
import app.inkloom.model.Compatibility
import app.inkloom.model.ImportedBrush
import app.inkloom.model.SystemBrushCatalog
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushToolPackageMigrationTest {
    @Test fun explicitToolAndLiquifyModeSurvivePortableRoundTrip() = roundTrip(legacy = false)
    @Test fun versionOneExplicitEraserAndFactoryToolsMigrateAtImport() = roundTrip(legacy = true)

    private fun roundTrip(legacy: Boolean) {
        val root = Files.createTempDirectory("inkloom-tool-package-").toFile()
        try {
            for (id in listOf("system-hard-eraser", "system-blend-soft", "system-liquify-twirl-left", "system-hb-pencil")) {
                val original = SystemBrushCatalog.byId(id)!!.copy(name = "Neutral", groupName = "Erasers", tags = emptyList())
                val codec = InkloomBrushPackage(File(root, "assets"))
                val current = File(root, "$id.inkbrush")
                current.outputStream().use { codec.write(original, it) }
                val source = if (legacy) {
                    val contents = ZipFile(current).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() } }
                    val manifest = Json.parseToJsonElement(contents.getValue("manifest.json").decodeToString()).jsonObject
                    val brush = manifest.getValue("brush").jsonObject
                    val oldBrush = JsonObject((brush - "toolBinding") + ("isEraser" to JsonPrimitive(id == "system-hard-eraser")))
                    val oldManifest = JsonObject(manifest + ("schemaVersion" to JsonPrimitive(1)) + ("brush" to oldBrush))
                    File(root, "$id-v1.inkbrush").also { file ->
                        ZipOutputStream(file.outputStream()).use { zip ->
                            for ((name, bytes) in contents) {
                                zip.putNextEntry(ZipEntry(name))
                                zip.write(if (name == "manifest.json") oldManifest.toString().encodeToByteArray() else bytes)
                                zip.closeEntry()
                            }
                        }
                    }
                } else current
                val fallback = ImportedBrush(name = "Import", format = BrushFormat.INKLOOM, compatibility = Compatibility.COMPLETE, sizeBytes = source.length(), report = emptyList())
                val imported = codec.read(source, id, fallback)
                assertEquals(imported.report.joinToString(), Compatibility.COMPLETE, imported.compatibility)
                assertEquals(id, original.toolBinding, imported.toolBinding)
                assertTrue(imported.report.any { it.contains("schema ${if (legacy) 1 else 2}") })
            }
        } finally { root.deleteRecursively() }
    }
}
