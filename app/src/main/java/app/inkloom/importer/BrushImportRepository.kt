package app.inkloom.importer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.inkloom.model.ImportedBrush
import java.io.File
import java.security.MessageDigest

class BrushImportRepository(context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val sourceDirectory = File(context.filesDir, "brush-sources").apply { mkdirs() }
    private val sutParser = SutBrushParser(File(context.filesDir, "brush-assets"))
    private val abrParser = AbrBrushParser(File(context.filesDir, "abr-assets"))
    private val procreateParser = ProcreateBrushParser(File(context.filesDir, "procreate-assets"))
    private val inkloomPackage = InkloomBrushPackage(File(context.filesDir, "inkloom-brush-assets"))

    fun import(uri: Uri): Result<ImportedBrush> = importAll(uri).map { it.first() }

    fun importAll(uri: Uri): Result<List<ImportedBrush>> = runCatching {
        val metadata = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val name = cursor.getString(0) ?: "Imported Brush"
                    val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                    name to size
                }
            } ?: (uri.lastPathSegment ?: "Imported Brush") to -1L
        val fallbackSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val size = metadata.second.takeIf { it > 0 } ?: fallbackSize
        require(size > 0) { "The selected file has no readable size" }
        val probed = resolver.openInputStream(uri)?.use { BrushFileProbe.probe(metadata.first, size, it) }
            ?: error("The selected file could not be opened")
        val digest = MessageDigest.getInstance("SHA-256")
        val safeName = metadata.first.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val temporary = File(sourceDirectory, ".${System.nanoTime()}-$safeName.tmp")
        resolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= BrushFileProbe.MAX_IMPORT_BYTES) { "Brush file exceeds the 256 MB safety limit" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("The selected file could not be copied")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val target = File(sourceDirectory, "$hash-$safeName")
        if (target.exists()) temporary.delete() else check(temporary.renameTo(target)) { "Could not retain the original brush source" }
        val retained = probed.copy(sourcePath = target.absolutePath, sha256 = hash, report = probed.report + "Original source retained with SHA-256 integrity hash")
        when (retained.format) {
            app.inkloom.model.BrushFormat.SUT -> listOf(sutParser.enrich(target, hash, retained))
            app.inkloom.model.BrushFormat.ABR -> abrParser.enrich(target, hash, retained)
            app.inkloom.model.BrushFormat.PROCREATE -> procreateParser.enrich(target, hash, retained)
            app.inkloom.model.BrushFormat.INKLOOM -> listOf(inkloomPackage.read(target, hash, retained))
            else -> listOf(retained)
        }
    }

    fun export(brush: ImportedBrush, uri: Uri): Result<Unit> = runCatching {
        resolver.openOutputStream(uri, "w")?.use { output -> inkloomPackage.write(brush, output) }
            ?: error("The selected destination could not be opened")
    }

    fun refreshMetadata(brush: ImportedBrush): ImportedBrush {
        return refreshMetadataAll(brush).first()
    }

    fun refreshMetadataAll(brush: ImportedBrush): List<ImportedBrush> {
        val source = brush.sourcePath?.let(::File) ?: return listOf(brush)
        val hash = brush.sha256 ?: return listOf(brush)
        if (!source.isFile) return listOf(brush)
        return when {
            brush.format == app.inkloom.model.BrushFormat.SUT && brush.parserVersion < SutBrushParser.CURRENT_VERSION -> listOf(sutParser.enrich(source, hash, brush))
            brush.format == app.inkloom.model.BrushFormat.ABR && brush.parserVersion < AbrBrushParser.CURRENT_VERSION -> abrParser.enrich(source, hash, brush)
            brush.format == app.inkloom.model.BrushFormat.PROCREATE && brush.parserVersion < ProcreateBrushParser.CURRENT_VERSION -> procreateParser.enrich(source, hash, brush)
            brush.format == app.inkloom.model.BrushFormat.INKLOOM && brush.parserVersion < InkloomBrushPackage.CURRENT_VERSION -> listOf(inkloomPackage.read(source, hash, brush))
            else -> listOf(brush)
        }
    }
}
